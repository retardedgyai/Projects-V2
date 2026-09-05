package dev.projects.server.coreloop

import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block

internal enum class HarborFacilityKind { EXPEDITIONS, WORKSHOP, STORAGE, SUPPLIES, MASTERY }

internal data class HarborFacility(
    val kind: HarborFacilityKind,
    val displayName: String,
    val position: Pos,
    val blockPositions: Set<Vec>,
    val interactionRadius: Double = 3.25,
)

/** A small, reusable social hub. Call once during startup, before players enter the instance. */
internal object HarborScene {
    data class Result(val spawn: Pos, val facilities: List<HarborFacility>, val labels: List<Entity>)

    fun build(instance: InstanceContainer): Result {
        instance.setTime(6000)
        instance.defaultClock()?.pause()
        instance.setWeather(Weather.CLEAR)
        instance.setChunkSupplier(::LightingChunk)
        instance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 34, Block.STONE)
            unit.modifier().fillHeight(34, 36, Block.SAND)
            unit.modifier().fillHeight(36, 39, Block.WATER)
        }
        val loads = (-4..3).flatMap { x -> (-4..3).map { z -> instance.loadChunk(x, z) } }
        CompletableFuture.allOf(*loads.toTypedArray()).join()
        val builder = Builder(instance)
        builder.ground()
        builder.pier()
        builder.buildings()
        builder.furnishings()
        builder.paths()
        builder.landscape()
        val facilities = listOf(
            facility(HarborFacilityKind.EXPEDITIONS, "遠征受付", 0, -15, listOf(-1 to -15, 1 to -15)),
            facility(HarborFacilityKind.WORKSHOP, "鍛冶工房", -16, -6, listOf(-18 to -6, -14 to -6)),
            facility(HarborFacilityKind.STORAGE, "素材倉庫", -15, 10, listOf(-16 to 10, -14 to 10)),
            facility(HarborFacilityKind.SUPPLIES, "交易市場", 16, 9, listOf(15 to 9, 17 to 9)),
            facility(HarborFacilityKind.MASTERY, "熟練の手引き", 16, -9, listOf(15 to -9, 17 to -9)),
        )
        val labels = facilities.map { facility ->
            Entity(EntityType.TEXT_DISPLAY).apply {
                setHasPhysics(false)
                setNoGravity(true)
                editEntityMeta(TextDisplayMeta::class.java) { meta ->
                    meta.setText(Component.text(facility.displayName, NamedTextColor.GOLD))
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setScale(Vec(0.72, 0.72, 0.72))
                    meta.setViewRange(0.55f)
                    meta.setShadow(true)
                    meta.setSeeThrough(false)
                    meta.setBackgroundColor(0x60000000)
                    meta.setBrightness(15, 15)
                }
                setInstance(instance, facility.position.add(0.0, 1.65, 0.0)).join()
            }
        }
        return Result(Pos(0.5, 41.0, 7.5, 180f, 0f), facilities, labels)
    }

    private fun facility(
        kind: HarborFacilityKind,
        name: String,
        x: Int,
        z: Int,
        extra: List<Pair<Int, Int>>,
    ) = HarborFacility(
        kind, name, Pos(x + 0.5, 41.5, z + 0.5),
        (extra + (x to z)).map { (px, pz) -> Vec(px.toDouble(), 41.0, pz.toDouble()) }.toSet(),
    )

    private class Builder(private val instance: InstanceContainer) {
        private fun put(x: Int, y: Int, z: Int, block: Block) = instance.setBlock(x, y, z, block)
        private fun box(x1: Int, x2: Int, y1: Int, y2: Int, z1: Int, z2: Int, block: Block) {
            for (x in x1..x2) for (z in z1..z2) for (y in y1..y2) put(x, y, z, block)
        }

        fun ground() {
            for (x in -37..37) for (z in -35..21) {
                val corner = (abs(x) - 29).coerceAtLeast(0) + (-z - 27).coerceAtLeast(0)
                if (corner > 8 || (z > 17 && abs(x) > 28 - (z - 17) * 2)) continue
                box(x, x, 35, 38, z, z, Block.SANDSTONE)
                put(x, 39, z, Block.DIRT)
                put(x, 40, z, if (z > 15) Block.SMOOTH_SANDSTONE else Block.GRASS_BLOCK)
            }
            // The visible quayside is a retaining wall, rather than a floating grass platform.
            box(-28, 28, 36, 40, 17, 18, Block.SANDSTONE)
            for (x in -28..28) put(x, 40, 17, Block.SMOOTH_SANDSTONE)
        }

        fun pier() {
            box(-3, 3, 40, 40, 18, 34, Block.SPRUCE_PLANKS)
            box(-12, 12, 40, 40, 29, 34, Block.SPRUCE_PLANKS)
            for (z in listOf(21, 27, 33)) for (x in listOf(-3, 3)) {
                box(x, x, 35, 41, z, z, Block.STRIPPED_SPRUCE_LOG)
            }
            for (x in listOf(-11, 11)) {
                box(x, x, 35, 41, 30, 30, Block.STRIPPED_SPRUCE_LOG)
                put(x, 42, 30, Block.LANTERN)
            }
            box(7, 10, 41, 41, 32, 33, Block.BARREL)
            // A moored, block-built skiff gives the dock a purpose without another entity runtime.
            box(-17, -14, 38, 38, 24, 32, Block.DARK_OAK_PLANKS)
            box(-18, -18, 39, 40, 25, 31, Block.SPRUCE_PLANKS)
            box(-13, -13, 39, 40, 25, 31, Block.SPRUCE_PLANKS)
            box(-17, -14, 39, 40, 24, 24, Block.SPRUCE_PLANKS)
            box(-17, -14, 39, 40, 32, 32, Block.SPRUCE_PLANKS)
            box(-17, -14, 39, 39, 27, 27, Block.SPRUCE_SLAB)
            box(-15, -15, 39, 47, 29, 29, Block.SPRUCE_FENCE)
            box(-15, -15, 44, 46, 26, 28, Block.WHITE_WOOL)
        }

        fun buildings() {
            // All public entrances face the short central loop; there are no decorative locked doors.
            openHall(-7, 7, -23, -12, Block.SPRUCE_PLANKS, Block.BRICKS)
            openHall(-24, -11, -15, -3, Block.SMOOTH_SANDSTONE, Block.DARK_OAK_PLANKS)
            openHall(11, 23, -17, -5, Block.SPRUCE_PLANKS, Block.MUD_BRICKS)
            // Storage and provision stalls remain entirely open toward the player route.
            awning(-22, -11, 6, 13, Block.CYAN_WOOL)
            awning(11, 23, 5, 13, Block.WHITE_WOOL)
            // The workshop chimney is visible above its roof from the arrival path.
            box(-23, -21, 41, 49, -14, -12, Block.BRICKS)
            put(-22, 50, -13, Block.CAMPFIRE)
            for (x in listOf(-6, 6)) put(x, 44, -12, Block.LANTERN)
        }

        private fun openHall(x1: Int, x2: Int, z1: Int, z2: Int, wall: Block, roof: Block) {
            box(x1, x2, 40, 40, z1, z2, Block.SPRUCE_PLANKS)
            box(x1, x2, 41, 44, z1, z1, wall)
            box(x1, x1, 41, 44, z1, z2, wall)
            box(x2, x2, 41, 44, z1, z2, wall)
            for (x in listOf(x1, x2)) for (z in listOf(z1, z2)) {
                box(x, x, 41, 45, z, z, Block.STRIPPED_SPRUCE_LOG)
            }
            for (x in x1 - 1..x2 + 1) {
                val edgeDistance = minOf(x - x1 + 1, x2 + 1 - x)
                val roofY = 45 + minOf(edgeDistance, 3)
                box(x, x, roofY, roofY, z1 - 1, z2 + 1, roof)
            }
            val midX = (x1 + x2) / 2
            for (x in midX - 2..midX + 2) put(x, 43, z1, Block.GLASS_PANE)
            put(midX, 44, z1 + 1, Block.LANTERN)
        }

        private fun awning(x1: Int, x2: Int, z1: Int, z2: Int, stripe: Block) {
            box(x1, x2, 40, 40, z1, z2, Block.SPRUCE_PLANKS)
            for (x in listOf(x1, x2)) for (z in listOf(z1, z2)) {
                box(x, x, 41, 44, z, z, Block.SPRUCE_FENCE)
            }
            for (x in x1 - 1..x2 + 1) {
                box(x, x, 45, 45, z1 - 1, z2 + 1, if ((x - x1) % 3 == 0) stripe else Block.WHITE_WOOL)
            }
            put(x1 + 1, 44, z1, Block.LANTERN)
        }

        fun furnishings() {
            put(0, 41, -15, Block.CARTOGRAPHY_TABLE)
            put(-1, 41, -15, Block.CARTOGRAPHY_TABLE)
            put(1, 41, -15, Block.CARTOGRAPHY_TABLE)
            box(-3, 3, 42, 43, -22, -22, Block.STRIPPED_OAK_WOOD)
            for (x in listOf(-5, 5)) {
                box(x, x, 41, 41, -20, -17, Block.SPRUCE_STAIRS.withProperty("facing", if (x < 0) "east" else "west"))
            }
            put(-16, 41, -6, Block.SMITHING_TABLE)
            put(-18, 41, -6, Block.ANVIL)
            put(-14, 41, -6, Block.CRAFTING_TABLE)
            for (x in -20..-18) put(x, 41, -14, Block.FURNACE.withProperty("facing", "south").withProperty("lit", "true"))
            put(-13, 41, -13, Block.GRINDSTONE)
            put(-13, 41, -12, Block.CAULDRON)
            for (x in -16..-14) put(x, 41, 10, Block.BARREL.withProperty("facing", "north"))
            box(-21, -19, 41, 42, 11, 12, Block.BARREL)
            put(-20, 43, 12, Block.LANTERN)
            put(16, 41, 9, Block.SMOKER.withProperty("facing", "north"))
            put(15, 41, 9, Block.BARREL)
            put(17, 41, 9, Block.CRAFTING_TABLE)
            box(20, 22, 41, 41, 10, 11, Block.HAY_BLOCK)
            put(20, 42, 11, Block.MELON)
            put(21, 42, 11, Block.PUMPKIN)
            put(16, 41, -9, Block.LECTERN.withProperty("facing", "south"))
            put(15, 41, -9, Block.BOOKSHELF)
            put(17, 41, -9, Block.BOOKSHELF)
            box(12, 22, 41, 42, -16, -16, Block.BOOKSHELF)
            box(20, 21, 41, 41, -13, -12, Block.SPRUCE_STAIRS.withProperty("facing", "west"))
        }

        fun paths() {
            road(-2, 2, -14, 18)
            road(-22, 23, -1, 3)
            road(-18, -14, -5, 3)
            road(14, 18, -8, 3)
            road(-17, -13, 3, 9)
            road(14, 18, 3, 8)
            for (x in -6..6) for (z in -4..8) {
                if (x * x + (z - 2) * (z - 2) <= 35) put(x, 40, z, paving(x, z))
            }
        }

        private fun road(x1: Int, x2: Int, z1: Int, z2: Int) {
            for (x in x1..x2) for (z in z1..z2) put(x, 40, z, paving(x, z))
        }

        private fun paving(x: Int, z: Int) = when (Math.floorMod(x * 13 + z * 7, 11)) {
            0, 1 -> Block.COBBLESTONE
            2 -> Block.ANDESITE
            else -> Block.STONE_BRICKS
        }

        fun landscape() {
            for ((x, z) in listOf(-8 to 5, 8 to 5, -8 to -4, 8 to -4, -25 to 3, 26 to 3)) {
                put(x, 41, z, Block.STONE_BRICK_WALL)
                box(x, x, 42, 43, z, z, Block.SPRUCE_FENCE)
                put(x, 44, z, Block.LANTERN)
            }
            for ((x, z) in listOf(-30 to -20, -29 to 1, 29 to -24, 30 to 0, -16 to -29, 12 to -29)) {
                box(x, x, 41, 45, z, z, Block.OAK_LOG)
                for (dx in -2..2) for (dz in -2..2) for (dy in 0..2) {
                    if (abs(dx) + abs(dz) + dy <= 4) {
                        put(x + dx, 45 + dy, z + dz, Block.OAK_LEAVES.withProperty("persistent", "true"))
                    }
                }
            }
            for (x in listOf(-10, 9)) for (z in 6..12) {
                put(x, 40, z, Block.COARSE_DIRT)
                if (z % 2 == 0) put(x, 41, z, if (x < 0) Block.AZURE_BLUET else Block.CORNFLOWER)
            }
            // The quayside railing leaves a seven-block entrance to the pier.
            for (x in -27..27) if (abs(x) >= 4) put(x, 41, 18, Block.SANDSTONE_WALL)
            for (x in listOf(-25, 25)) put(x, 42, 18, Block.LANTERN)
        }
    }
}
