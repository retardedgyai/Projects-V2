package dev.projects.server.questmap

import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.abs

/**
 * ProjectS-owned block structure catalog. These assets deliberately live behind one placement
 * boundary so authored Sponge schematics can replace or extend them later without changing the
 * terrain planner.
 */
internal object QuestMapStructureAssets {
    fun placeTree(
        instance: Instance,
        style: QuestTerrainStyle,
        origin: QuestMapPoint,
        groundY: Int,
        variation: Int,
        rotation: Int,
    ) {
        val painter = Painter(instance, origin.x, groundY + 1, origin.z, rotation)
        when (style) {
            QuestTerrainStyle.VERDANT -> painter.verdantTree(Math.floorMod(variation, 6))
            QuestTerrainStyle.HIGHLANDS -> painter.highlandTree(Math.floorMod(variation, 6))
            QuestTerrainStyle.SALTMARSH -> painter.saltmarshTree(Math.floorMod(variation, 6))
        }
    }

    fun placeBoulder(
        instance: Instance,
        style: QuestTerrainStyle,
        origin: QuestMapPoint,
        groundY: Int,
        variation: Int,
        rotation: Int,
    ) {
        Painter(instance, origin.x, groundY + 1, origin.z, rotation).boulder(style, Math.floorMod(variation, 6))
    }

    fun placeFallenLog(
        instance: Instance,
        style: QuestTerrainStyle,
        origin: QuestMapPoint,
        groundY: Int,
        length: Int,
        rotation: Int,
    ) {
        val painter = Painter(instance, origin.x, groundY + 1, origin.z, rotation)
        val log = when (style) {
            QuestTerrainStyle.VERDANT -> Block.STRIPPED_OAK_LOG
            QuestTerrainStyle.HIGHLANDS -> Block.STRIPPED_SPRUCE_LOG
            QuestTerrainStyle.SALTMARSH -> Block.STRIPPED_MANGROVE_LOG
        }
        repeat(length) { offset -> painter.set(offset, 0, 0, log) }
        painter.set(0, -1, 0, Block.MOSS_BLOCK)
        painter.set(length - 1, -1, 0, Block.MOSS_BLOCK)
        if (length >= 4) painter.set(length / 2, 1, 0, Block.BROWN_MUSHROOM)
    }

    private class Painter(
        private val instance: Instance,
        private val originX: Int,
        private val originY: Int,
        private val originZ: Int,
        private val rotation: Int,
    ) {
        fun set(dx: Int, dy: Int, dz: Int, block: Block) {
            val (rotatedX, rotatedZ) = rotate(dx, dz)
            instance.setBlock(originX + rotatedX, originY + dy, originZ + rotatedZ, block)
        }

        private fun column(dx: Int, dz: Int, fromY: Int, toY: Int, block: Block) {
            for (dy in fromY..toY) set(dx, dy, dz, block)
        }

        private fun leafCrown(
            centerX: Int,
            centerY: Int,
            centerZ: Int,
            radiusX: Int,
            radiusY: Int,
            radiusZ: Int,
            block: Block,
            clippedCorner: Int = 1,
        ) {
            for (dy in -radiusY..radiusY) {
                for (dx in -radiusX..radiusX) {
                    for (dz in -radiusZ..radiusZ) {
                        val normalized = abs(dx).toDouble() / (radiusX + 0.4) +
                            abs(dy).toDouble() / (radiusY + 0.6) +
                            abs(dz).toDouble() / (radiusZ + 0.4)
                        if (normalized <= 1.65 && Math.floorMod(dx * 7 + dy * 11 + dz * 13, 9) >= clippedCorner) {
                            set(centerX + dx, centerY + dy, centerZ + dz, block)
                        }
                    }
                }
            }
        }

        fun verdantTree(variation: Int) {
            val log = Block.OAK_LOG
            val leaves = Block.OAK_LEAVES
            when (variation) {
                0 -> {
                    column(0, 0, 0, 7, log)
                    column(1, 0, 4, 6, log)
                    column(-1, 1, 5, 7, log)
                    leafCrown(0, 7, 0, 4, 2, 3, leaves)
                    leafCrown(-2, 8, 2, 3, 2, 3, leaves)
                }
                1 -> {
                    column(0, 0, 0, 5, log)
                    column(1, 0, 4, 8, log)
                    column(-1, 0, 4, 7, log)
                    leafCrown(2, 8, 0, 3, 2, 3, leaves)
                    leafCrown(-2, 7, 0, 3, 2, 3, leaves)
                }
                2 -> {
                    column(0, 0, 0, 4, log)
                    column(1, 0, 3, 7, log)
                    column(2, 0, 6, 8, log)
                    leafCrown(2, 8, 0, 4, 2, 3, leaves)
                }
                3 -> {
                    column(0, 0, 0, 4, log)
                    leafCrown(0, 5, 0, 3, 2, 3, leaves)
                    set(1, 0, 0, Block.ROOTED_DIRT)
                }
                4 -> {
                    column(0, 0, 0, 9, log)
                    column(1, 1, 0, 4, log)
                    column(-1, -1, 0, 3, log)
                    leafCrown(0, 9, 0, 4, 2, 4, leaves)
                    leafCrown(3, 8, 1, 3, 2, 2, leaves)
                    set(-1, -1, 0, Block.MOSS_BLOCK)
                    set(1, -1, 0, Block.MOSS_BLOCK)
                }
                else -> {
                    column(0, 0, 0, 6, log)
                    column(1, 0, 0, 5, log)
                    leafCrown(0, 7, 0, 5, 2, 3, leaves)
                    leafCrown(2, 8, -1, 3, 2, 3, leaves)
                    set(-1, -1, 0, Block.MOSS_CARPET)
                }
            }
        }

        fun highlandTree(variation: Int) {
            val log = Block.SPRUCE_LOG
            val leaves = Block.SPRUCE_LEAVES
            val height = listOf(10, 13, 8, 11, 15, 9)[variation]
            column(0, 0, 0, height, log)
            if (variation == 3) {
                column(1, 0, height - 5, height - 2, log)
                leafCrown(1, height - 1, 0, 2, 1, 2, leaves, clippedCorner = 3)
                return
            }
            if (variation == 4) column(1, 0, 0, height - 3, log)
            var layerY = 3
            while (layerY <= height) {
                val distanceFromTop = height - layerY
                val radius = when {
                    distanceFromTop > 8 -> 3
                    distanceFromTop > 3 -> 2
                    else -> 1
                }
                leafCrown(0, layerY, 0, radius, 1, radius, leaves, clippedCorner = 2)
                layerY += 2
            }
            set(0, height + 1, 0, leaves)
        }

        fun saltmarshTree(variation: Int) {
            val log = Block.MANGROVE_LOG
            val leaves = Block.MANGROVE_LEAVES
            val height = 6 + variation % 3
            listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2).forEach { (dx, dz) ->
                set(dx, 0, dz, Block.MANGROVE_ROOTS)
                set(dx / 2, 1, dz / 2, Block.MANGROVE_ROOTS)
            }
            column(0, 0, 0, height, log)
            if (variation % 2 == 0) column(1, 0, height - 3, height, log)
            if (variation >= 3) column(-1, 1, height - 2, height + 1, log)
            leafCrown(0, height + 1, 0, 4, 2, 4, leaves)
            leafCrown(2, height, -1, 3, 1, 2, leaves, clippedCorner = 2)
            set(-1, 0, 1, Block.MUDDY_MANGROVE_ROOTS)
        }

        fun boulder(style: QuestTerrainStyle, variation: Int) {
            val primary = when (style) {
                QuestTerrainStyle.VERDANT -> if (variation % 2 == 0) Block.MOSSY_COBBLESTONE else Block.ANDESITE
                QuestTerrainStyle.HIGHLANDS -> if (variation % 2 == 0) Block.TUFF else Block.STONE
                QuestTerrainStyle.SALTMARSH -> if (variation % 2 == 0) Block.MOSSY_COBBLESTONE else Block.MUD_BRICKS
            }
            val secondary = when (style) {
                QuestTerrainStyle.VERDANT -> Block.STONE
                QuestTerrainStyle.HIGHLANDS -> Block.COBBLESTONE
                QuestTerrainStyle.SALTMARSH -> Block.MOSS_BLOCK
            }
            val radiusX = 1 + variation % 3
            val radiusZ = 1 + (variation / 2) % 3
            for (dx in -radiusX..radiusX) {
                for (dz in -radiusZ..radiusZ) {
                    if (abs(dx) + abs(dz) > radiusX + radiusZ - 1) continue
                    set(dx, 0, dz, if (Math.floorMod(dx + dz + variation, 4) == 0) secondary else primary)
                    if (abs(dx) < radiusX && abs(dz) < radiusZ && Math.floorMod(dx * 5 + dz * 3 + variation, 3) == 0) {
                        set(dx, 1, dz, primary)
                    }
                }
            }
            if (variation >= 4) set(0, 2, 0, primary)
            if (style != QuestTerrainStyle.HIGHLANDS) set(0, if (variation >= 4) 3 else 2, 0, Block.MOSS_CARPET)
        }

        private fun rotate(dx: Int, dz: Int): Pair<Int, Int> = when (Math.floorMod(rotation, 4)) {
            0 -> dx to dz
            1 -> -dz to dx
            2 -> -dx to -dz
            else -> dz to -dx
        }
    }
}
