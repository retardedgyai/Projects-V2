package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/** Session-only accessibility preference; affects this viewer, never server hits or other players. */
internal object CoreCombatPresentation {
    enum class Detail(val label: String, val particleStride: Int) { FULL("華やか", 1), SUBDUED("控えめ", 2), MINIMAL("最小", 4) }
    private val loaded = ConcurrentHashMap.newKeySet<UUID>()
    private val details = ConcurrentHashMap<UUID, Detail>()
    fun pack(player: Player, ready: Boolean) { if (ready) loaded += player.uuid else loaded -= player.uuid }
    fun packed(player: Player) = player.uuid in loaded
    fun detail(player: Player) = details[player.uuid] ?: Detail.FULL
    fun cycle(player: Player): Detail = Detail.entries[(detail(player).ordinal + 1) % Detail.entries.size].also { details[player.uuid] = it }
    fun forget(player: Player) { loaded -= player.uuid; details -= player.uuid }
}

/** A single continuous voxel surface. One item display, not one entity per pixel. */
internal data class CoreCombatMeshPart(
    val shape: String, val palette: String, val offset: Vec = Vec.ZERO,
    val scale: Vec = Vec(1.0, 1.0, 1.0), val yaw: Double = 0.0, val pitch: Double = 0.0, val roll: Double = 0.0,
    val spin: Double = 0.0, val travel: Vec = Vec.ZERO, val secondary: Boolean = false,
)

internal object CoreCombatMeshArt {
    fun parts(effect: CoreSkillEffect): List<CoreCombatMeshPart> {
        if (!effect.valid) return emptyList()
        val radius = effect.radius.coerceIn(1.0, 8.0)
        val yaw = atan2(effect.direction.x(), effect.direction.z())
        val pitch = -atan2(effect.direction.y(), hypot(effect.direction.x(), effect.direction.z()))
        val astral = effect.job == CoreClass.STARWEAVER
        val palette = when {
            astral -> "astral"
            effect.skill.status == CoreSkillStatus.POISON -> "venom"
            effect.skill.element == 1 -> "fire"
            effect.skill.element == 2 -> "ice"
            effect.job == CoreClass.HEALER -> "life"
            effect.job == CoreClass.TEMPLAR || effect.job == CoreClass.WARRIOR -> "gold"
            effect.job == CoreClass.ASSASSIN -> "astral"
            else -> "steel"
        }
        fun part(shape: String, r: Double, y: Double = .4, tilt: Double = 0.0, spin: Double = 0.0, secondary: Boolean = false,
                 offset: Vec = Vec(0.0, y, 0.0), travel: Vec = Vec.ZERO, roll: Double = 0.0) =
            CoreCombatMeshPart(shape, palette, offset, Vec(r * 2, r * 2, r * 2), yaw, tilt, roll, spin, travel, secondary)
        if (effect.phase == CoreSkillVisualPhase.CONTACT) return listOf(part("burst", .65, 1.0, PI / 2))
        if (effect.phase == CoreSkillVisualPhase.PREPARE) {
            if (effect.motif == CoreSkillMotif.STARS || effect.motif == CoreSkillMotif.METEOR) {
                val falls = effect.skill.icon in setOf("starfall", "star_ult", "meteor", "mage_ult")
                return listOf(part("star", .8, if (falls) 6.0 else 1.8, PI / 2, 1.5,
                    travel = Vec(0.0, if (falls) -4.8 else .3, 0.0)),
                    part("rune", radius * .65, .2, spin = -.6, secondary = true))
            }
            // Brief wind-up stays off the player's crosshair. It never looks like an extra hit.
            return listOf(part(if (effect.motif in blades) "crescent" else "rune", .65, .6,
                tilt = if (effect.motif in blades) 1.2 else 0.0, spin = -.5))
        }
        if ((effect.clippedRay || effect.skill.motion == CoreSkillMotion.RAY) && effect.length <= .05) return emptyList()
        if (effect.length > .05) {
            val direction = if (effect.direction.lengthSquared() > 1e-8) effect.direction.normalize() else Vec(0.0, 0.0, 1.0)
            val beam = CoreCombatMeshPart(if (effect.motif == CoreSkillMotif.LIGHTNING) "bolt" else "lance", palette,
                direction.mul(effect.length / 2), Vec(if (astral) 4.5 else 2.6, if (astral) 4.5 else 2.6, effect.length), yaw, pitch)
            return listOf(beam, part("star", if (astral) .8 else .4, tilt = PI / 2, secondary = true,
                offset = direction.mul(effect.length)))
        }
        return when (effect.motif) {
            CoreSkillMotif.SLASH, CoreSkillMotif.VENOM, CoreSkillMotif.WHIRL -> {
                val roll = if (effect.pulse % 2 == 0) -.28 else .28
                val spin = (if (effect.pulse % 2 == 0) 1.0 else -1.0) * if (effect.motif == CoreSkillMotif.WHIRL) PI * 2 else 1.1
                listOf(part("crescent", radius, 1.0, spin = spin, roll = roll),
                    part("crescent", radius * .82, 1.15, spin = spin, secondary = true, roll = roll).copy(yaw = yaw - .18))
            }
            CoreSkillMotif.CLEAVE -> listOf(part("crescent", radius, 1.25, PI / 2, spin = -.7, roll = PI / 2),
                part("burst", radius * .7, .18, secondary = true))
            CoreSkillMotif.THRUST, CoreSkillMotif.ARROW, CoreSkillMotif.NEEDLE, CoreSkillMotif.FIRE, CoreSkillMotif.STAR_BEAM ->
                listOf(CoreCombatMeshPart("lance", palette, Vec(sin(yaw) * radius * .5, 1.0, cos(yaw) * radius * .5),
                    Vec(2.8, 2.8, radius * 1.8), yaw))
            CoreSkillMotif.STARS -> listOf(
                part("star", if (effect.skill.ultimate) 1.9 else 1.25, 1.5, PI / 2, 1.1),
                part("orbit", radius * .85, 1.2, .38, 1.8),
                part("orbit", radius * .65, 1.6, -.65, -2.0, secondary = true),
                part("burst", radius, .2, spin = .3, secondary = true),
            ) + (0..2).map { i ->
                val angle = yaw + i * PI * 2 / 3 + effect.pulse * .7
                CoreCombatMeshPart("lance", "astral", Vec(sin(angle) * .6, 1.5, cos(angle) * .6),
                    Vec(1.6, 1.6, 1.8), angle, -.35, spin = .8,
                    travel = Vec(sin(angle) * radius * .7, -.8, cos(angle) * radius * .7), secondary = true)
            }
            CoreSkillMotif.METEOR -> listOf(part("star", 1.7, 1.0, PI / 2, .9), part("burst", radius, .2),
                part("orbit", radius * .8, .6, spin = 1.1, secondary = true))
            CoreSkillMotif.FROST, CoreSkillMotif.FROST_FAN -> (0..4).map { i ->
                val angle = yaw + (i - 2) * .5
                CoreCombatMeshPart("lance", "ice", Vec(sin(angle) * radius * .65, .65, cos(angle) * radius * .65),
                    Vec(2.8, 2.8, radius * 1.2), angle, -.7, secondary = i % 2 == 1)
            }
            CoreSkillMotif.RAIN, CoreSkillMotif.PILLAR -> (0..2).map { i ->
                val a = i * PI * 2 / 3
                CoreCombatMeshPart("lance", palette, Vec(cos(a) * radius * .4, 2.4, sin(a) * radius * .4),
                    Vec(2.8, 2.8, 5.0), yaw, PI / 2, travel = Vec(0.0, -1.5, 0.0), secondary = i > 0)
            }
            CoreSkillMotif.PULL -> listOf(part("orbit", radius, .4, spin = 2.0),
                part("orbit", radius * .75, 1.2, .5, -2.0, secondary = true), part("star", .6, 1.0, PI / 2))
            CoreSkillMotif.LIGHTNING -> (0..2).map { i -> part("bolt", radius * .75, .5, spin = .2).copy(yaw = yaw + i * PI * 2 / 3) }
            CoreSkillMotif.GUARD -> listOf(part("rune", 1.3, 1.0, PI / 2, offset = Vec(sin(yaw) * 1.1, 1.0, cos(yaw) * 1.1)))
            CoreSkillMotif.WARD, CoreSkillMotif.TRAP -> listOf(part("rune", radius, .2, spin = .4),
                part("orbit", radius * .85, .8, spin = -.7, secondary = true))
            CoreSkillMotif.HEAL -> listOf(part("rune", radius, .2, spin = .5),
                part("orbit", radius * .7, .7, spin = -.5, travel = Vec(0.0, 1.4, 0.0), secondary = true))
            CoreSkillMotif.BLINK -> listOf(part("crescent", 1.4, 1.0, PI / 2, spin = 2.0), part("star", .6, 1.0, PI / 2, secondary = true))
            CoreSkillMotif.SHOCK -> listOf(part("burst", radius, .2), part("orbit", radius, .5, secondary = true))
        }
    }

    private val blades = setOf(CoreSkillMotif.SLASH, CoreSkillMotif.WHIRL, CoreSkillMotif.CLEAVE, CoreSkillMotif.THRUST, CoreSkillMotif.VENOM)

    /** Quaternion for model-local pitch, then roll, then world yaw. */
    fun rotation(yaw: Double, pitch: Double, roll: Double): FloatArray {
        fun multiply(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
            a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1], a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
            a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3], a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2])
        return multiply(multiply(doubleArrayOf(0.0, sin(yaw/2), 0.0, cos(yaw/2)),
            doubleArrayOf(0.0, 0.0, sin(roll/2), cos(roll/2))), doubleArrayOf(sin(pitch/2), 0.0, 0.0, cos(pitch/2))).map { it.toFloat() }.toFloatArray()
    }
}

/** Owned and ticked by the combat actor: no scheduled callbacks surviving a map exit. */
internal class CoreCombatMeshes(private val owner: Player) {
    private data class Live(val entity: Entity, val instance: Instance, val part: CoreCombatMeshPart,
                            val duration: Int, val prepare: Boolean, val collapse: Boolean, var age: Int = 0,
                            val cancelled: AtomicBoolean = AtomicBoolean())
    private val live = mutableListOf<Live>()
    internal val size get() = live.size

    fun play(effect: CoreSkillEffect) {
        val instance = owner.instance ?: return
        if (!instance.players.any { CoreCombatPresentation.packed(it) && CoreCombatPresentation.detail(it) != CoreCombatPresentation.Detail.MINIMAL }) return
        for (part in CoreCombatMeshArt.parts(effect)) {
            if (live.size >= 20 || !reserve(instance)) break
            val entity = Entity(EntityType.ITEM_DISPLAY)
            entity.setHasPhysics(false); entity.setNoGravity(true); entity.setAutoViewable(false)
            val item = ItemStack.of(Material.PAPER).withItemModel("projects:combat_vfx/${part.shape}_${part.palette}")
            val record = Live(entity, instance, part, effect.durationTicks, effect.phase == CoreSkillVisualPhase.PREPARE,
                effect.motif == CoreSkillMotif.PULL || effect.phase == CoreSkillVisualPhase.CONTACT ||
                    effect.motif == CoreSkillMotif.STARS && part.shape == "star")
            live += record
            entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.setItemStack(item); meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
                meta.setBrightness(15, 15); meta.setViewRange(1.0f)
                meta.setTransformationInterpolationDuration(1)
                meta.setScale(part.scale.mul(if (record.prepare) .4 else 1.0))
                meta.setTranslation(part.offset)
                meta.setLeftRotation(CoreCombatMeshArt.rotation(part.yaw, part.pitch, part.roll))
            }
            entity.setInstance(instance, Pos(effect.origin.x(), effect.origin.y(), effect.origin.z())).whenComplete { _, failure ->
                // A chunk load can finish after cancellation. Never resurrect an orphan display.
                if (failure != null || record.cancelled.get() || entity.isRemoved || owner.instance !== instance) entity.remove()
            }
        }
    }

    fun tick() {
        val iterator = live.iterator()
        var otherVisible = 0
        while (iterator.hasNext()) {
            val v = iterator.next()
            if (owner.isRemoved || owner.instance !== v.instance || v.entity.isRemoved || v.age >= v.duration) {
                v.cancelled.set(true); v.entity.remove(); release(v.instance); iterator.remove(); continue
            }
            val t = v.age.toDouble() / v.duration
            val fade = if (v.prepare) .4 + .6 * t else if (v.collapse) 1.0 - .75 * t else 1.0 + .18 * t
            // Collapse thickness at the end instead of leaving opaque panels obstructing combat.
            val thickness = if (!v.prepare && t > .5) (1 - t) * 2 else 1.0
            v.entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.setTransformationInterpolationStartDelta(0)
                meta.setScale(Vec(v.part.scale.x() * fade, v.part.scale.y() * thickness, v.part.scale.z() * fade))
                meta.setTranslation(v.part.offset.add(v.part.travel.mul(t)))
                meta.setLeftRotation(CoreCombatMeshArt.rotation(v.part.yaw + v.part.spin * t, v.part.pitch, v.part.roll))
            }
            if (v.entity.instance === v.instance) {
                val allowed = v.instance.players.filter { viewer ->
                    val detail = CoreCombatPresentation.detail(viewer)
                    CoreCombatPresentation.packed(viewer) && detail != CoreCombatPresentation.Detail.MINIMAL &&
                        (!v.part.secondary || detail == CoreCombatPresentation.Detail.FULL && viewer === owner) &&
                        viewer.position.distanceSquared(v.entity.position) <= (if (viewer === owner) 1600.0 else 256.0) &&
                        (viewer === owner || otherVisible < 3)
                }.toSet()
                v.entity.viewers.toList().filter { it !in allowed }.forEach { v.entity.removeViewer(it) }
                allowed.filter { it !in v.entity.viewers }.forEach { v.entity.addViewer(it) }
                if (!v.part.secondary) otherVisible++
            }
            v.age++
        }
    }

    fun cancel() { live.forEach { it.cancelled.set(true); it.entity.remove(); release(it.instance) }; live.clear() }

    companion object {
        // Scene-wide cap prevents a large party multiplying the entity count without bound.
        private val sceneCounts = WeakHashMap<Instance, Int>()
        private fun reserve(instance: Instance): Boolean = synchronized(sceneCounts) {
            val count = sceneCounts[instance] ?: 0
            if (count >= 120) false else { sceneCounts[instance] = count + 1; true }
        }
        private fun release(instance: Instance) = synchronized(sceneCounts) {
            val count = (sceneCounts[instance] ?: 1) - 1
            if (count <= 0) sceneCounts.remove(instance) else sceneCounts[instance] = count
        }
    }
}
