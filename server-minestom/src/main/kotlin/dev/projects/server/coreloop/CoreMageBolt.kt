package dev.projects.server.coreloop

import dev.projects.server.mob.CombatTarget
import dev.projects.server.mob.rayHit
import dev.projects.server.particle.dust
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import java.util.UUID
import kotlin.math.floor

/**
 * The mage's normal attack as a bolt you can see (2026-10-27, plan https://claude.ai/artifact/PpG325i91uFiJXZkzsR6xx,
 * look "ア": white core, violet glow, diamond sparkles behind, a star burst where it hits). It used to be an
 * instant invisible ray with vanilla flame dots.
 *
 * The server flies it: [SPEED] blocks a tick from the eye along the aim, stopped by blocks, out to [RANGE]; it hits the
 * first target its path passes within [WIDTH] of. Only the picture starts at the staff hand ([hand], to the lower
 * right) and joins the aim line over [CONVERGE] ticks; hits never use the picture.
 */
internal class CoreMageBolt(origin: Pos, val direction: Vec, private val hand: Vec, val multiplier: Double) {
    data class Step(val hit: UUID?, val at: Pos, val ended: Boolean)

    var position: Pos = origin; private set
    private var travelled = 0.0
    var age = 0; private set

    /** Where the sprite is drawn this tick: on the aim line, pulled toward the hand at first. */
    val shown: Pos get() = position.add(hand.mul((1.0 - age.toDouble() / CONVERGE).coerceAtLeast(0.0)))

    /** One tick of flight against [targets]; [solid] says whether a point is inside a block. */
    fun step(targets: List<CombatTarget>, solid: (Point) -> Boolean): Step {
        age++
        val length = minOf(SPEED, RANGE - travelled)
        var blocked: Double? = null
        for (k in 1..floor(length * 4).toInt().coerceAtLeast(1)) {
            val along = minOf(k / 4.0, length)
            if (solid(position.add(direction.mul(along)))) { blocked = (along - .25).coerceAtLeast(0.0); break }
        }
        val reach = blocked ?: length
        val hit = targets.mapNotNull { t -> along(t, reach)?.let { t.id to it } }.minByOrNull { it.second }
        if (hit != null) return Step(hit.first, advance(hit.second), true)
        val at = advance(reach)
        return Step(null, at, blocked != null || travelled >= RANGE - 1e-6)
    }

    private fun advance(by: Double): Pos { position = position.add(direction.mul(by)); travelled += by; return position }

    /** How far along this tick's path the bolt touches [t]: its spheres, or its box as an upright line. */
    private fun along(t: CombatTarget, reach: Double): Double? {
        if (t.parts.isNotEmpty()) return t.parts.rayHit(position, direction, reach, WIDTH)
        val centre = t.position
        fun closest(c: Point) = c.sub(position).let { o -> o.x() * direction.x() + o.y() * direction.y() + o.z() * direction.z() }
        var along = closest(centre)
        // The nearest height of the body to the path, then the nearest point of the path to that.
        val y = (position.y() + direction.y() * along - centre.y()).coerceIn(-t.halfExtent.y(), t.halfExtent.y())
        val point = Vec(centre.x(), centre.y() + y, centre.z())
        along = closest(point)
        if (along !in -WIDTH..reach) return null
        val miss = position.add(direction.mul(along.coerceAtLeast(0.0))).distance(point)
        return if (miss <= WIDTH + t.halfExtent.x().coerceIn(0.0, 3.0)) along.coerceAtLeast(0.0) else null
    }

    companion object {
        const val SPEED = 1.5      // 30 blocks a second: 18 blocks in 0.6 s
        const val RANGE = 18.0
        const val WIDTH = .65      // as the old ray: a loose aim still connects
        const val CONVERGE = 4
    }
}

/**
 * The bolt's sprites, seen by players with the pack. Session-only display entities with a hard lifetime; players
 * without the pack get a few violet dust dots instead. Never affects hits.
 */
internal class CoreMageBoltSprites(private val owner: Player) {
    private class Sprite(val entity: Entity, val kind: String, val frames: Int, val life: Int, var age: Int = 0)
    private val live = mutableListOf<Sprite>()
    private val bolts = HashMap<CoreMageBolt, Sprite>()

    fun launch(bolt: CoreMageBolt) {
        spawn("bolt", BOLT_SCALE, bolt.shown, life = 40, frames = 4)?.let { bolts[bolt] = it }
    }

    /** After the bolt's [step]: move its sprite, leave a sparkle where it was, burst where it ended. */
    fun flew(bolt: CoreMageBolt, from: Pos, ended: Boolean, hit: Boolean) {
        bolts[bolt]?.entity?.teleport(bolt.shown)
        if (bolt.age % 2 == 1) spawn("spark", SPARK_SCALE, from, life = 8, frames = 4)
        fallback(bolt.shown, if (hit) 8 else 1)
        if (!ended) return
        bolts.remove(bolt)?.let { it.entity.remove(); live.remove(it) }
        spawn("burst", if (hit) BURST_SCALE else BURST_SCALE * .7, bolt.position, life = 12, frames = 6)
    }

    fun tick() {
        live.removeAll { s ->
            s.age++
            if (s.age >= s.life || s.entity.isRemoved) { s.entity.remove(); return@removeAll true }
            val frame = if (s.kind == "bolt") s.age / 2 % s.frames else (s.age * s.frames / s.life).coerceAtMost(s.frames - 1)
            s.entity.editEntityMeta(ItemDisplayMeta::class.java) { it.setItemStack(stack(s.kind, frame)) }
            false
        }
    }

    fun clear() { live.forEach { it.entity.remove() }; live.clear(); bolts.clear() }

    private fun viewers(instance: Instance, at: Point) =
        instance.players.filter { CoreCombatPresentation.packed(it) && it.position.distanceSquared(at) < VIEW * VIEW }

    private fun spawn(kind: String, scale: Double, at: Pos, life: Int, frames: Int): Sprite? {
        val instance = owner.instance ?: return null
        if (live.size >= LIMIT || viewers(instance, at).isEmpty()) return null
        val entity = Entity(EntityType.ITEM_DISPLAY)
        entity.setHasPhysics(false); entity.setNoGravity(true); entity.setAutoViewable(false)
        entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(stack(kind, 0))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
            meta.setPosRotInterpolationDuration(1)
            meta.setBrightness(15, 15); meta.setViewRange(1.0f)
            meta.setScale(Vec(scale, scale, scale))
            meta.setRightRotation(CoreCombatMeshArt.vanillaItemCorrection)
        }
        val sprite = Sprite(entity, kind, frames, life)
        live += sprite
        entity.setInstance(instance, at).whenComplete { _, failure ->
            if (failure != null || owner.instance !== instance || sprite !in live) entity.remove()
            else viewers(instance, at).forEach(entity::addViewer)
        }
        return sprite
    }

    private fun fallback(at: Pos, count: Int) {
        val instance = owner.instance ?: return
        val packet = ParticlePacket(dust(0x8f7bff, 1.2f), at, Vec(.06, .06, .06), 0f, count)
        instance.players.filter { !CoreCombatPresentation.packed(it) && it.position.distanceSquared(at) < VIEW * VIEW }
            .forEach { it.sendPacket(packet) }
    }

    private fun stack(kind: String, frame: Int) =
        ItemStack.of(Material.PAPER).withItemModel("projects:combat_vfx/mage_bolt/${kind}_$frame")

    companion object {
        const val BOLT_SCALE = .55
        const val SPARK_SCALE = .35
        const val BURST_SCALE = 1.6
        const val LIMIT = 40
        const val VIEW = 48.0
    }
}
