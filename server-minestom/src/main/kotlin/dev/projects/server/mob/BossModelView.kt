package dev.projects.server.mob

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.worldseed.multipart.GenericModelImpl
import net.worldseed.multipart.ModelEngine
import net.worldseed.multipart.animations.AnimationHandlerImpl
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
 * idle / walk (+ `_enraged`), enrage, death and one `once` clip per ability id, aligned to its telegraph.
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
    }

    private val model = Model(modelId)
    private val animations: AnimationHandlerImpl
    private var enraged = false
    private var dying = false
    private var disposed = false
    private var loop = "idle"
    private var last: Pos = origin
    private var hurtUntil = 0L
    private var revealAt = 0L
    private var foldAt = 0L

    init {
        model.init(instance, origin)
        model.setGlobalRotation(modelYaw(origin.yaw()))
        animations = AnimationHandlerImpl(model)
        hiddenUntilEnraged.forEach { model.setBoneVisible(it, false) }
        animations.playRepeat(loop)
    }

    val partCount: Int get() = if (disposed) 0 else model.getParts().size

    fun show(player: Player) { if (!disposed) model.addViewer(player) }
    fun hide(player: Player) { if (!disposed && !dying) model.removeViewer(player) }

    /** Follows the carrier and switches between standing and walking loops. */
    fun tick(at: Pos, now: Long) {
        if (disposed || dying) return
        if (hurtUntil != 0L && now >= hurtUntil) { hurtUntil = 0L; model.setState("normal") }
        if (revealAt != 0L && now >= revealAt) { revealAt = 0L; reveal() }
        if (foldAt != 0L && now >= foldAt) { foldAt = 0L; hideFolded() }
        val moved = at.distanceSquared(last) > 0.03 * 0.03
        last = at
        model.setPosition(at)
        model.setGlobalRotation(modelYaw(at.yaw()))
        val wanted = (if (moved) "walk" else "idle") + if (enraged && has("idle_enraged")) "_enraged" else ""
        if (wanted != loop && animations.playing == null) {
            loop = wanted
            animations.playRepeat(wanted, 4)
        }
    }

    /** WSEE's generated red "hit" texture state, briefly. */
    fun hurt(now: Long) {
        if (disposed || dying) return
        if (hurtUntil == 0L) model.setState("hit")
        hurtUntil = now + 150
    }

    fun ability(abilityId: String) {
        if (!disposed && !dying && has(abilityId)) animations.playOnce(abilityId, 2) {}
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
        hiddenUntilEnraged.forEach { model.setBoneVisible(it, true) }
        for (viewer in model.viewers.toList()) { model.removeViewer(viewer); model.addViewer(viewer) }
    }

    private fun hideFolded() {
        for (part in model.getParts()) {
            val name = part.name
            if (hiddenOnceEnraged.any { name == it || name.startsWith("${it}_") }) part.setVisible(false)
        }
    }

    /** Plays the death clip, then removes the bones even if the carrier is already gone. */
    fun die() {
        if (disposed || dying) return
        dying = true
        if (has("death")) animations.playOnce("death", false) { dispose() } else dispose()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        animations.destroy()
        model.destroy()
    }

    private fun has(animation: String) = animations.getAnimation(animation) != null

    companion object {
        /** shade_wyrm `enrage`: the fans and horns start growing at 2.15 s, the folded wings are gone by 2.4 s. */
        private const val ENRAGE_REVEAL_MILLIS = 300L
        private const val ENRAGE_FOLD_MILLIS = 2500L
        fun available(modelId: String) = modelId in BossModelLibrary.models()
        /**
         * Entity yaw -> WSEE model rotation. WSEE turns a model by (180 - rotation), so a Blockbench +Z front
         * leads the carrier only with +180 (checked by ShadeWyrmBossTest).
         */
        fun modelYaw(entityYaw: Float): Double = entityYaw + 180.0
    }
}
