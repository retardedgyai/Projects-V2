package dev.projects.server.mob

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

internal data class MobGroundTile(val center: Pos, val width: Double, val depth: Double)

/** Scan lines share the exact analytical hit shape; flat adjacent samples become one background quad. */
internal object MobGroundRaster {
    const val MAX_TILES = 64

    fun tiles(frame: MobAbilityFrame, ground: (Pos) -> Double?): List<MobGroundTile> {
        // Normal terrain uses half-block rows. Jagged rock fields use wider rows, never an unbounded entity count.
        for (step in listOf(0.5, 0.75, 1.0, 1.5, 2.0)) {
            val result = raster(frame, step, ground)
            if (result.size <= MAX_TILES) return result
        }
        // Unusually fragmented terrain is not a valid place to begin an attack with a hidden warning.
        return emptyList()
    }

    private fun raster(frame: MobAbilityFrame, step: Double, ground: (Pos) -> Double?): List<MobGroundTile> {
        val outline = frame.ability.shape.outline(frame.origin, frame.facing)
        if (outline.isEmpty()) return emptyList()
        val minX = floor(outline.minOf { it.x() } / step).toInt()
        val maxX = ceil(outline.maxOf { it.x() } / step).toInt()
        val minZ = floor(outline.minOf { it.z() } / step).toInt()
        val maxZ = ceil(outline.maxOf { it.z() } / step).toInt()
        val result = mutableListOf<MobGroundTile>()
        for (z in minZ until maxZ) {
            var start: Int? = null
            var height = 0.0
            fun finish(end: Int) {
                val first = start ?: return
                result += MobGroundTile(Pos((first + end) * step / 2.0, height, (z + 0.5) * step),
                    (end - first) * step, step)
                start = null
            }
            for (x in minX until maxX) {
                val sample = Pos((x + 0.5) * step, frame.origin.y(), (z + 0.5) * step)
                val y = if (frame.ability.shape.contains(frame.origin, frame.facing, sample)) ground(sample) else null
                if (y == null || (start != null && abs(y - height) > 0.001)) finish(x)
                if (y != null && start == null) { start = x; height = y }
            }
            finish(maxX)
        }
        return result
    }
}

/**
 * Server-only, font-independent red surface. One vanilla space supplies only its ARGB text background.
 * No client mod, shader, resource-pack glyph, invisible interaction entity, or world-block mutation.
 */
internal class MobGroundTelegraph(private val instance: InstanceContainer) {
    companion object {
        const val MAX_ACTIVE = 6
        const val MAX_DISPLAYS = MAX_ACTIVE * MobGroundRaster.MAX_TILES
        const val TRACKING_COLOR: Int = 0x60B8384A
        val LOCKED_COLOR: Int = 0x80C83048.toInt()
        val IMPACT_COLOR: Int = 0xA0FF7070.toInt()
        val TOOTH_COLOR: Int = 0xF0FF2A40.toInt()
        /** Teeth every this many blocks round the edge, each five bars of these widths and this depth. */
        const val TOOTH_SPACING = 1.25
        val TOOTH_ROWS = listOf(.56, .44, .32, .2, .09)
        const val TOOTH_ROW = .12
        /** Teeth start a little inside the true edge, over the stepped fill's rim. */
        const val TOOTH_START = -.12
        const val MAX_TEETH = 20
    }

    private class Surface {
        val entities = mutableListOf<Entity>()
        /** The bright teeth along the edge (Creator 2026-11-07: edges like PROJECT Spellforged's). */
        val teeth = mutableListOf<Entity>()
        var expiresAt = Long.MAX_VALUE
        var tiles = emptyList<MobGroundTile>()
        var color = 0
        @Volatile var failedToSpawn = false
    }

    private val surfaces = mutableMapOf<UUID, Surface>()
    private var disposed = false
    internal val displayCount: Int get() = surfaces.values.sumOf { it.entities.size }
    internal val activeCount: Int get() = surfaces.size

    fun canStart(owner: UUID): Boolean = !disposed && (owner in surfaces || surfaces.size < MAX_ACTIVE)

    /** False means no visible supported surface exists; the caller must cancel, not attack invisibly. */
    fun show(owner: UUID, frame: MobAbilityFrame, impact: Boolean, now: Long): Boolean {
        if (!canStart(owner)) return false
        if (surfaces[owner]?.failedToSpawn == true) { clear(owner); return false }
        val tiles = MobGroundRaster.tiles(frame, ::groundHeight)
        if (tiles.isEmpty()) { clear(owner); return false }
        val surface = surfaces.getOrPut(owner) { Surface() }
        val color = when {
            impact -> IMPACT_COLOR
            frame.phase == MobAbilityPhase.LOCKED -> LOCKED_COLOR
            else -> TRACKING_COLOR
        }
        surface.expiresAt = if (impact) now + 180L else Long.MAX_VALUE
        while (surface.entities.size > tiles.size) surface.entities.removeLast().remove()
        tiles.forEachIndexed { index, tile ->
            val entity = surface.entities.getOrNull(index) ?: Entity(EntityType.TEXT_DISPLAY).also {
                it.setHasPhysics(false)
                it.setNoGravity(true)
                surface.entities += it
                it.editEntityMeta(TextDisplayMeta::class.java) { meta -> configure(meta, tile, color) }
                it.setInstance(instance, tile.center).whenComplete { _, error ->
                    if (error != null) surface.failedToSpawn = true
                    if (disposed || surfaces[owner] !== surface || it !in surface.entities) it.remove()
                }
            }
            if (surface.tiles.getOrNull(index) != tile) {
                // A moving warning sends one metadata batch per tile, not a packet for every property.
                entity.editEntityMeta(TextDisplayMeta::class.java) { configure(it, tile, color) }
                if (entity.instance === instance && entity.position != tile.center) entity.teleport(tile.center)
            } else if (surface.color != color) {
                (entity.entityMeta as TextDisplayMeta).backgroundColor = color
            }
        }
        surface.tiles = tiles
        surface.color = color
        showTeeth(surface, frame)
        return true
    }

    /**
     * Spellforged's warning edge (refs/spellforged, 2026-11-07): along the rim of the dull red fill, bright red teeth,
     * each a stepped fin of five flat bars (wide at the rim, narrow at the tip) pointing out of the danger and leaning
     * along the edge, like a saw. The outward way is found from the shape itself, so every shape gets the same edge.
     */
    private fun showTeeth(surface: Surface, frame: MobAbilityFrame) {
        val bars = mutableListOf<Pos>()
        val widths = mutableListOf<Double>()
        var last: Pos? = null
        for (point in frame.ability.shape.outline(frame.origin, frame.facing)) {
            if (last != null && last.distanceSquared(point) < TOOTH_SPACING * TOOTH_SPACING) continue
            val out = outward(frame, point) ?: continue
            val ground = groundHeight(point) ?: continue
            last = point
            val along = Vec(-out.z(), 0.0, out.x())
            val yaw = Math.toDegrees(kotlin.math.atan2(-out.x(), out.z())).toFloat()
            for ((row, width) in TOOTH_ROWS.withIndex()) {
                // each row steps out from the rim and slides along it, so the tip leans like a saw tooth
                val at = point.add(out.mul(TOOTH_START + (row + .5) * TOOTH_ROW)).add(along.mul((TOOTH_ROWS[0] - width) * .5))
                bars += Pos(at.x(), ground + 0.015, at.z(), yaw, 0f)
                widths += width
            }
            if (bars.size >= MAX_TEETH * TOOTH_ROWS.size) break
        }
        while (surface.teeth.size > bars.size) surface.teeth.removeLast().remove()
        bars.forEachIndexed { index, at ->
            val tile = MobGroundTile(at, widths[index], TOOTH_ROW)
            val entity = surface.teeth.getOrNull(index)
            if (entity == null) {
                val tooth = Entity(EntityType.TEXT_DISPLAY)
                tooth.setHasPhysics(false)
                tooth.setNoGravity(true)
                tooth.editEntityMeta(TextDisplayMeta::class.java) { meta -> configure(meta, tile, TOOTH_COLOR) }
                // listed before it spawns: a loaded chunk completes the spawn at once
                surface.teeth += tooth
                tooth.setInstance(instance, at).whenComplete { _, error ->
                    if (error != null) surface.failedToSpawn = true
                    if (disposed || tooth !in surface.teeth) tooth.remove()
                }
            } else if (entity.instance === instance && entity.position != at) {
                entity.editEntityMeta(TextDisplayMeta::class.java) { configure(it, tile, TOOTH_COLOR) }
                entity.teleport(at)
            }
        }
    }

    /** The way out of the shape at this edge point: the mean of the probe directions that leave it. */
    private fun outward(frame: MobAbilityFrame, point: Pos): Vec? {
        var x = 0.0; var z = 0.0
        for (k in 0 until 16) {
            val a = k * Math.PI / 8
            val dx = kotlin.math.cos(a); val dz = kotlin.math.sin(a)
            if (!frame.ability.shape.contains(frame.origin, frame.facing, point.add(dx * .35, 0.0, dz * .35))) { x += dx; z += dz }
        }
        val length = sqrt(x * x + z * z)
        return if (length < 1e-6) null else Vec(x / length, 0.0, z / length)
    }

    fun tick(now: Long) {
        surfaces.filterValues { it.expiresAt <= now }.keys.toList().forEach(::clear)
    }

    fun clear(owner: UUID) { surfaces.remove(owner)?.let { it.entities.forEach(Entity::remove); it.teeth.forEach(Entity::remove) } }

    fun dispose() {
        disposed = true
        surfaces.keys.toList().forEach(::clear)
    }

    fun groundHeight(point: Pos): Double? {
        val x = floor(point.x()).toInt()
        val z = floor(point.z()).toInt()
        if (instance.getChunkAt(x.toDouble(), z.toDouble()) == null) return null
        val baseY = floor(point.y()).toInt()
        for (y in baseY + 1 downTo baseY - 3) {
            if (instance.getBlock(x, y, z).solid() && !instance.getBlock(x, y + 1, z).solid()) {
                val top = y + 1.0
                // Do not paint a surface on an elevation which this attack cannot hit.
                if (abs(top - point.y()) <= 2.2) return top
            }
        }
        return null
    }

    private fun configure(meta: TextDisplayMeta, tile: MobGroundTile, color: Int) {
        meta.setText(Component.text(" ").font(Key.key("minecraft:default")))
        meta.setLineWidth(8)
        meta.setTextOpacity(0)
        meta.setUseDefaultBackground(false)
        meta.setBackgroundColor(color)
        meta.setSeeThrough(false)
        meta.setShadow(false)
        meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
        meta.setBrightness(15, 15)
        meta.setViewRange(0.75f)
        meta.setPosRotInterpolationDuration(0)
        meta.setTransformationInterpolationDuration(0)
        // Vanilla 26.2: one space background is 5x10 pixels at .025 block/pixel, with an off-center origin.
        // Rotate its front upwards and compensate that origin, so the quad is centered on exactly this tile.
        meta.setScale(Vec(tile.width * 8.0, tile.depth * 4.0, 1.0))
        meta.setLeftRotation(floatArrayOf(-sqrt(0.5).toFloat(), 0f, 0f, sqrt(0.5).toFloat()))
        meta.setTranslation(Vec(-tile.width * 0.1, 0.045, tile.depth * 0.5))
    }
}
