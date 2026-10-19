package dev.projects.server.mob

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket
import net.worldseed.multipart.GenericModelImpl
import net.worldseed.multipart.ModelEngine
import net.worldseed.multipart.animations.AnimationHandlerImpl
import net.worldseed.multipart.model_bones.BoneEntity
import net.worldseed.multipart.model_bones.display_entity.RootBoneEntity
import java.nio.file.Files
import java.nio.file.Path

/** Loads the build-generated WSEE data once from the classpath (see [BossModelPack]). */
internal object BossModelLibrary {
    private var loaded: Set<String>? = null

    /** Model ids ("shade_wyrm") available to the server; empty when the build step did not run. */
    @Synchronized
    fun models(): Set<String> {
        loaded?.let { return it }
        val loader = BossModelLibrary::class.java.classLoader
        val index = loader.getResourceAsStream("${BossModelPack.DATA_ROOT}/index.txt")
            ?.bufferedReader()?.use { it.readLines().filter(String::isNotBlank) }
        if (index == null) {
            System.err.println("BOSS_MODELS_MISSING: run :server-minestom:generateBossModels; bosses fall back to vanilla bodies")
            return emptySet<String>().also { loaded = it }
        }
        // WSEE reads geometry from a filesystem Path, so the classpath copy is extracted once per process.
        val root = Files.createTempDirectory("projects-boss-models").also { it.toFile().deleteOnExit() }
        for (file in index) {
            require(".." !in file && !file.startsWith("/")) { "Unsafe boss model path: $file" }
            val target = root.resolve(file)
            Files.createDirectories(target.parent)
            requireNotNull(loader.getResourceAsStream("${BossModelPack.DATA_ROOT}/$file")) { "Missing boss model data $file" }
                .use { Files.copy(it, target) }
        }
        ModelEngine.setModelMaterial(BossModelPack.CARRIER)
        val mappings = Files.readString(root.resolve("mappings.json"))
        ModelEngine.loadMappings(mappings.reader(), root.resolve("models"))
        // Bind every bone item to the private item definition instead of the vanilla carrier's.
        val keys = com.google.gson.JsonParser.parseString(mappings).asJsonObject.keySet()
        for (key in keys) {
            val slash = key.lastIndexOf('/')
            ModelEngine.getItems(key.substring(0, slash), key.substring(slash + 1))
                ?.replaceAll { _, item -> item.withItemModel(BossModelPack.ITEM_MODEL) }
        }
        return keys.map { it.substringBefore(".bbmodel/") }.toSet().also { loaded = it }
    }
}

/** The invisible server body of a modelled mob. The model follows exactly the players who see this entity. */
internal class ModelCarrier(type: EntityType) : EntityCreature(type) {
    @Volatile var view: BossModelView? = null
    override fun updateNewViewer(player: Player) {
        super.updateNewViewer(player)
        view?.show(player)
    }
    override fun updateOldViewer(player: Player) {
        super.updateOldViewer(player)
        view?.hide(player)
    }
}

/**
 * Plays a Blockbench boss through WSEE. Animation names are the contract with the .bbmodel:
 * idle / walk (+ `_enraged`), enrage, death and one `once` clip per ability id, aligned to its telegraph; once enraged,
 * an ability's or death's `_enraged` twin plays instead when the model has one.
 */
internal class BossModelView(
    private val modelId: String,
    instance: Instance,
    origin: Pos,
    private val hiddenUntilEnraged: Set<String>,
    /** Bones hidden, with every bone under them, once the enrage clip has shrunk them away. */
    private val hiddenOnceEnraged: Set<String> = emptySet(),
) {
    private class Model(private val id: String) : GenericModelImpl() {
        override fun getId(): String = "$id.bbmodel"
        override fun generateRoot(): BoneEntity = Root(this)
    }

    /**
     * WSEE 13.0.4 (built for 26.2) sends the root armor stand's equipment packet to every new viewer. On 26.3 Minestom
     * returns null for an entity with no equipment, and the null packet throws in the connection's queue, so the
     * model never shows. Send the empty equipment explicitly, as 26.2 did.
     */
    private class Root(model: GenericModelImpl) : RootBoneEntity(model) {
        override fun getEquipmentsPacket(): EntityEquipmentPacket =
            super.getEquipmentsPacket() ?: EntityEquipmentPacket(entityId, mapOf(EquipmentSlot.MAIN_HAND to ItemStack.AIR))
    }

    private val model = Model(modelId)
    private val animations: AnimationHandlerImpl
    private var enraged = false
    private var dying = false
    private var disposed = false
    private var loop = "idle"
    private var lastNow = 0L
    private var pendingLoop = "idle"
    private var pendingSince = 0L
    /** The clip on screen and since when (server millis), for the fight preview dump. */
    internal var shownClip = "idle"; private set
    internal var shownSince = 0L; private set
    private var shownUntil = 0L
    /** Clip lengths (s) from the generated animation file, so the preview knows when a once clip is over. */
    private val lengths: Map<String, Double> by lazy {
        BossModelView::class.java.classLoader.getResourceAsStream("${BossModelPack.DATA_ROOT}/models/$modelId.bbmodel/model.animation.json")
            ?.use { com.google.gson.JsonParser.parseString(it.readBytes().decodeToString()).asJsonObject.getAsJsonObject("animations") }
            ?.entrySet()?.associate { (k, v) -> k to v.asJsonObject["animation_length"].asDouble } ?: emptyMap()
    }
    private fun shown(name: String, once: Boolean = false) {
        shownClip = name; shownSince = lastNow
        shownUntil = if (once) lastNow + ((lengths[name] ?: 0.0) * 1000).toLong() else 0L
    }
    private var last: Pos = origin
    private var hurtUntil = 0L
    private var revealAt = 0L
    private var foldAt = 0L
    /** The texture state between hit flashes: "normal", or "frenzy" once the second form glows. */
    private var baseState = "normal"
    private var glowAt = 0L
    internal var glowing = false; private set

    init {
        model.init(instance, origin)
        model.setGlobalRotation(modelYaw(origin.yaw()))
        animations = AnimationHandlerImpl(model)
        under(hiddenUntilEnraged).forEach { it.setVisible(false) }
        animations.playRepeat(loop)
    }

    val partCount: Int get() = if (disposed) 0 else model.getParts().size
    internal val currentLoop: String get() = loop
    /** The enraged parts (horns, fans) are out / the phase-one ones (wings) are folded away; hit spheres follow. */
    internal var revealed = false; private set
    private var revealedAt = 0L
    /** The fans have grown out (the enrage clip grows them from 2.15 s): only then do they take hits. */
    internal val fansOut: Boolean get() = revealed && lastNow - revealedAt >= FANS_GROWN_MILLIS
    internal var folded = false; private set

    fun show(player: Player) { if (!disposed) model.addViewer(player) }
    fun hide(player: Player) { if (!disposed && !dying) model.removeViewer(player) }

    /** Follows the carrier and switches between standing and walking loops. */
    /** [searching]: it has lost its target and creeps (the `search` loop) instead of walking. */
    fun tick(at: Pos, now: Long, searching: Boolean = false) {
        if (disposed || dying) return
        lastNow = now
        if (shownUntil != 0L && now >= shownUntil) shown(loop)
        if (hurtUntil != 0L && now >= hurtUntil) { hurtUntil = 0L; model.setState(baseState) }
        if (glowAt != 0L && now >= glowAt) { glowAt = 0L; glow() }
        if (revealAt != 0L && now >= revealAt) { revealAt = 0L; reveal() }
        if (foldAt != 0L && now >= foldAt) { foldAt = 0L; hideFolded() }
        val dx = at.x() - last.x(); val dz = at.z() - last.z()
        val moved = dx * dx + dz * dz > 0.03 * 0.03
        last = at
        model.setPosition(at)
        model.setGlobalRotation(modelYaw(at.yaw()))
        val base = if (!moved) "idle" else if (searching && has("search")) "search" else gait(at.yaw(), dx, dz)
        val wanted = base + if (enraged && has("${base}_enraged")) "_enraged" else ""
        // A new gait must hold for GAIT_SETTLE_MILLIS before it replaces the loop, so circling a moving player does
        // not flicker between walk and strafe every tick.
        if (wanted != pendingLoop) { pendingLoop = wanted; pendingSince = now }
        // WSEE's `playing` is the once clip if one runs, else the repeating loop (never null), so this waits for
        // ability clips only.
        if (wanted != loop && now - pendingSince >= GAIT_SETTLE_MILLIS && animations.playing == animations.repeating) {
            loop = wanted
            animations.playRepeat(wanted, 4)
            shown(wanted)
        }
    }

    /**
     * walk, or strafe_l / strafe_r when the carrier moves more sideways than forward (it keeps facing its target).
     * WSEE mirrors Blockbench X, so the model's +X side (`_l`) is the carrier's right.
     */
    private fun gait(yaw: Float, dx: Double, dz: Double): String {
        if (!has("strafe_l")) return "walk"
        val r = Math.toRadians(yaw.toDouble())
        val forward = -kotlin.math.sin(r) * dx + kotlin.math.cos(r) * dz
        val right = -kotlin.math.cos(r) * dx - kotlin.math.sin(r) * dz
        return if (kotlin.math.abs(right) > kotlin.math.abs(forward)) (if (right > 0) "strafe_l" else "strafe_r") else "walk"
    }

    /** A movement clip (hop_back, dash_l, dash_r) while the server carries the body; its enraged twin once enraged. */
    fun maneuver(clip: String) {
        if (disposed || dying) return
        val name = phased(clip)
        if (has(name)) { animations.playOnce(name, 2) {}; shown(name, once = true) }
    }

    val canManeuver: Boolean get() = !disposed && has("hop_back") && has("dash_l") && has("strafe_l")

    /** A broken part (horn tips, fan tatters): hidden for good, with every bone named under it (`<name>_…`). */
    fun hideBones(names: Collection<String>) {
        if (disposed) return
        under(names).forEach { it.setVisible(false) }
    }

    /** The bones named [names] or `<name>_…` (a fan's rays and their tatters sit under `cloak_l_…`). */
    private fun under(names: Collection<String>) =
        model.getParts().filter { part -> names.any { part.name == it || part.name.startsWith("${it}_") } }

    /** WSEE's generated red "hit" texture state, briefly. */
    fun hurt(now: Long) {
        if (disposed || dying) return
        if (hurtUntil == 0L) model.setState("hit")
        hurtUntil = now + 150
    }

    fun ability(abilityId: String) {
        if (disposed || dying) return
        val clip = phased(abilityId)
        if (has(clip)) { animations.playOnce(clip, 2) {}; shown(clip, once = true) }
    }

    /**
     * 第2形態 (2026-10-19): the `awaken` roar, and at its peak the glowing texture (the WSEE state "frenzy" from
     * `shade_wyrm_frenzy.png`); its vein pixels are self-lit by the pack's item shader (alpha 251), so they show in
     * the dark while the body stays dark.
     */
    fun awaken(now: Long) {
        if (disposed || dying || glowing || glowAt != 0L) return
        if (has("awaken")) { animations.playOnce("awaken", 2) {}; lastNow = now; shown("awaken", once = true) }
        glowAt = now + AWAKEN_GLOW_MILLIS
    }

    private fun glow() {
        if (disposed) return
        glowing = true
        baseState = "frenzy"
        if (hurtUntil == 0L) model.setState(baseState)
    }

    /**
     * The enrage clip grows the rage bones from scale 0, so they are shown a moment after it starts (not while
     * WSEE still blends from the loop at full scale), and the bones it shrinks away are hidden after.
     */
    fun enrage(now: Long) {
        if (disposed || dying || enraged) return
        enraged = true
        loop = if (has("idle_enraged")) "idle_enraged" else "idle"
        animations.playRepeat(loop)
        if (has("enrage")) {
            animations.playOnce("enrage", 2) {}
            lastNow = now; shown("enrage", once = true)
            revealAt = now + ENRAGE_REVEAL_MILLIS
            foldAt = now + ENRAGE_FOLD_MILLIS
        } else {
            reveal()
            hideFolded()
        }
    }

    /**
     * WSEE mounts the bone displays on the model's root only when the root spawns for a viewer, so a bone shown
     * later is never mounted and stays where it was spawned. Showing the model again mounts every bone.
     */
    private fun reveal() {
        if (!revealed) revealedAt = lastNow
        revealed = true
        under(hiddenUntilEnraged).forEach { it.setVisible(true) }
        for (viewer in model.viewers.toList()) { model.removeViewer(viewer); model.addViewer(viewer) }
    }

    private fun hideFolded() {
        folded = true
        for (part in model.getParts()) {
            val name = part.name
            if (hiddenOnceEnraged.any { name == it || name.startsWith("${it}_") }) part.setVisible(false)
        }
    }

    /** Plays the death clip, then removes the bones even if the carrier is already gone. */
    fun die() {
        if (disposed || dying) return
        dying = true
        val clip = phased("death")
        if (has(clip)) animations.playOnce(clip, false) { dispose() } else dispose()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        animations.destroy()
        model.destroy()
    }

    /** Where a point on a bone (Blockbench px in the bone's model space) is in the world right now. */
    fun point(bone: String, px: net.minestom.server.coordinate.Point): net.minestom.server.coordinate.Point? =
        if (disposed) null else model.getBonePoint(bone, px.mul(0.25))

    internal fun has(animation: String) = animations.getAnimation(animation) != null

    /** Once enraged, a clip authored in the enraged stance (`<name>_enraged`) replaces the phase-one one. */
    private fun phased(animation: String) = if (enraged && has("${animation}_enraged")) "${animation}_enraged" else animation

    companion object {
        /** shade_wyrm `enrage`: the fans and horns start growing at 2.15 s, the folded wings are gone by 2.4 s. */
        private const val ENRAGE_REVEAL_MILLIS = 300L
        private const val ENRAGE_FOLD_MILLIS = 2500L
        private const val GAIT_SETTLE_MILLIS = 200L
        private const val FANS_GROWN_MILLIS = 1850L
        /** shade_wyrm `awaken`: the roar peaks at 1.3 s; the glow comes in with it. */
        private const val AWAKEN_GLOW_MILLIS = 1300L
        fun available(modelId: String) = modelId in BossModelLibrary.models()
        /**
         * Entity yaw -> WSEE model rotation. WSEE turns a model by (180 - rotation), so a Blockbench +Z front
         * leads the carrier only with +180 (checked by ShadeWyrmBossTest).
         */
        fun modelYaw(entityYaw: Float): Double = entityYaw + 180.0
    }
}
