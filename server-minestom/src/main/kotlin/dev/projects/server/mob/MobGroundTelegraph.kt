package dev.projects.server.mob

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
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
        const val ARROW_MODEL = "projects:core_ui/warn_arrow"
        /** An arrow every this many blocks round the edge, at this scale (its 16 px sprite: 1 block at 1). */
        const val ARROW_SPACING = 1.1
        const val ARROW_SIZE = 1.1
        const val MAX_ARROWS = 24
    }

    private class Surface {
        val entities = mutableListOf<Entity>()
        /** The arrows round the rim (Creator 2026-11-07: an edge like PROJECT Spellforged's). */
        val rim = mutableListOf<Entity>()
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
        showRim(surface, frame)
        return true
    }

    /**
     * Spellforged's warning edge (refs/spellforged, read again on 2026-11-07 from the demo's skeleton strikes and spear
     * throw): red arrows round the rim, evenly spaced, all set by one rule — along a strip they point the way the
     * attack goes, round a ring or a fan they point outward. Each is a flat pixel arrow with thickness (the pack's
     * `core_ui/warn_arrow`, an item/generated model, so its sides show darker).
     */
    private fun showRim(surface: Surface, frame: MobAbilityFrame) {
        val arrows = mutableListOf<Pos>()
        var last: Pos? = null
        val forward = Vec(frame.facing.x(), 0.0, frame.facing.z()).let { if (it.length() < 1e-6) Vec(0.0, 0.0, 1.0) else it.normalize() }
        for (point in frame.ability.shape.outline(frame.origin, frame.facing)) {
            if (last != null && last.distanceSquared(point) < ARROW_SPACING * ARROW_SPACING) continue
            val ground = groundHeight(point) ?: continue
            val radial = Vec(point.x() - frame.origin.x(), 0.0, point.z() - frame.origin.z())
            val way = when (frame.ability.shape) {
                is MobAttackShape.Slam, is MobAttackShape.Cross -> forward
                else -> if (radial.length() < 1e-6) forward else radial.normalize()
            }
            last = point
            val yaw = Math.toDegrees(kotlin.math.atan2(-way.x(), way.z())).toFloat()
            arrows += Pos(point.x(), ground + 0.03, point.z(), yaw, 0f)
            if (arrows.size >= MAX_ARROWS) break
        }
        while (surface.rim.size > arrows.size) surface.rim.removeLast().remove()
        arrows.forEachIndexed { index, at ->
            val entity = surface.rim.getOrNull(index)
            if (entity == null) {
                val arrow = Entity(EntityType.ITEM_DISPLAY)
                arrow.setHasPhysics(false)
                arrow.setNoGravity(true)
                arrow.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel(ARROW_MODEL))
                    meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
                    meta.setBrightness(15, 15)
                    meta.setViewRange(0.75f)
                    meta.setPosRotInterpolationDuration(0)
                    meta.setScale(Vec(ARROW_SIZE, ARROW_SIZE, ARROW_SIZE))
                    // lay the sprite flat, face up, its tip toward the entity's facing
                    meta.setLeftRotation(floatArrayOf(sqrt(0.5).toFloat(), 0f, 0f, sqrt(0.5).toFloat()))
                }
                // listed before it spawns: a loaded chunk completes the spawn at once
                surface.rim += arrow
                arrow.setInstance(instance, at).whenComplete { _, error ->
                    if (error != null) surface.failedToSpawn = true
                    if (disposed || arrow !in surface.rim) arrow.remove()
                }
            } else if (entity.instance === instance && entity.position != at) {
                entity.teleport(at)
            }
        }
    }

    fun tick(now: Long) {
        surfaces.filterValues { it.expiresAt <= now }.keys.toList().forEach(::clear)
    }

    fun clear(owner: UUID) { surfaces.remove(owner)?.let { it.entities.forEach(Entity::remove); it.rim.forEach(Entity::remove) } }

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
