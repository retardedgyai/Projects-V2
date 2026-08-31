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
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        variation: Int,
        rotation: Int,
    ) {
        val selection = QuestMapSchematicCatalog.selectTree(plan.style, variation)
        selection.asset.place(instance, plan, origin, rotation, selection.palette)
    }

    fun placeBoulder(
        instance: Instance,
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        variation: Int,
        rotation: Int,
    ) {
        val selection = QuestMapSchematicCatalog.selectBoulder(plan.style, variation)
        selection.asset.place(instance, plan, origin, rotation, selection.palette)
    }

    fun treeFootprint(style: QuestTerrainStyle, variation: Int): Int =
        QuestMapSchematicCatalog.selectTree(style, variation).asset.footprintRadius

    fun boulderFootprint(style: QuestTerrainStyle, variation: Int): Int =
        QuestMapSchematicCatalog.selectBoulder(style, variation).asset.footprintRadius

    fun placeFallenLog(
        instance: Instance,
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        length: Int,
        rotation: Int,
        variation: Int,
    ) {
        val painter = Painter(
            instance,
            origin.x,
            plan.heightAt(origin) + 1,
            origin.z,
            rotation,
            variation.toLong(),
        )
        val baseLog = when (plan.style) {
            QuestTerrainStyle.VERDANT -> Block.STRIPPED_OAK_LOG
            QuestTerrainStyle.HIGHLANDS -> Block.STRIPPED_SPRUCE_LOG
            QuestTerrainStyle.SALTMARSH -> Block.STRIPPED_MANGROVE_LOG
            QuestTerrainStyle.CLIFFLANDS -> Block.STRIPPED_SPRUCE_LOG
            QuestTerrainStyle.SAKURA_GROVE -> Block.STRIPPED_CHERRY_LOG
            QuestTerrainStyle.INFERNAL -> Block.STRIPPED_CRIMSON_STEM
        }
        val log = baseLog.withProperty("axis", if (Math.floorMod(rotation, 2) == 0) "x" else "z")
        repeat(length) { offset -> painter.setGrounded(plan, offset, 0, 0, log) }
        painter.setGrounded(plan, 0, 0, -1, Block.MOSS_BLOCK)
        painter.setGrounded(plan, length - 1, 0, -1, Block.MOSS_BLOCK)
        if (length >= 4) painter.setGrounded(plan, length / 2, 0, 1, Block.BROWN_MUSHROOM)
    }

    fun placeShrubCluster(
        instance: Instance,
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        variation: Int,
        rotation: Int,
    ) {
        Painter(
            instance,
            origin.x,
            plan.heightAt(origin) + 1,
            origin.z,
            rotation,
            variation.toLong(),
        ).shrubCluster(plan, plan.style)
    }

    fun placeRoadsideMarker(
        instance: Instance,
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        variation: Int,
        rotation: Int,
    ) {
        Painter(
            instance,
            origin.x,
            plan.heightAt(origin) + 1,
            origin.z,
            rotation,
            variation.toLong(),
        ).roadsideMarker(plan, plan.style)
    }

    private class Painter(
        private val instance: Instance,
        private val originX: Int,
        private val originY: Int,
        private val originZ: Int,
        private val rotation: Int,
        private val assetSeed: Long,
    ) {
        fun set(dx: Int, dy: Int, dz: Int, block: Block) {
            val (rotatedX, rotatedZ) = rotate(dx, dz)
            instance.setBlock(originX + rotatedX, originY + dy, originZ + rotatedZ, block)
        }

        fun setGrounded(plan: QuestMapPlan, dx: Int, dz: Int, dy: Int, block: Block) {
            val (rotatedX, rotatedZ) = rotate(dx, dz)
            val absoluteX = originX + rotatedX
            val absoluteZ = originZ + rotatedZ
            if (absoluteX !in 0 until plan.size || absoluteZ !in 0 until plan.size) return
            instance.setBlock(absoluteX, plan.heightAt(absoluteX, absoluteZ) + 1 + dy, absoluteZ, block)
        }

        private fun setAnchored(plan: QuestMapPlan, dx: Int, dy: Int, dz: Int, block: Block) {
            val (rotatedX, rotatedZ) = rotate(dx, dz)
            val absoluteX = originX + rotatedX
            val absoluteZ = originZ + rotatedZ
            if (absoluteX !in 0 until plan.size || absoluteZ !in 0 until plan.size) return
            val targetY = originY + dy
            val localSurface = plan.heightAt(absoluteX, absoluteZ)
            if (targetY < localSurface - 1) return
            if (dy <= 0 && targetY > localSurface + 1) {
                for (fillY in localSurface + 1..targetY) instance.setBlock(absoluteX, fillY, absoluteZ, block)
            } else {
                instance.setBlock(absoluteX, targetY, absoluteZ, block)
            }
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
                        val leafHash = Math.floorMod(
                            assetSeed xor (dx * 734_287L) xor (dy * 912_271L) xor (dz * 438_289L),
                            11L,
                        ).toInt()
                        if (normalized <= 1.65 && leafHash >= clippedCorner) {
                            set(centerX + dx, centerY + dy, centerZ + dz, block)
                        }
                    }
                }
            }
        }

        fun shrubCluster(plan: QuestMapPlan, style: QuestTerrainStyle) {
            val random = java.util.Random(assetSeed xor 0x5348525542434C55L)
            val leaf = when (style) {
                QuestTerrainStyle.VERDANT -> if ((assetSeed and 1L) == 0L) Block.OAK_LEAVES else Block.AZALEA_LEAVES
                QuestTerrainStyle.HIGHLANDS -> if ((assetSeed and 1L) == 0L) Block.SPRUCE_LEAVES else Block.BIRCH_LEAVES
                QuestTerrainStyle.SALTMARSH -> Block.MANGROVE_LEAVES
                QuestTerrainStyle.CLIFFLANDS -> Block.SPRUCE_LEAVES
                QuestTerrainStyle.SAKURA_GROVE -> Block.CHERRY_LEAVES
                QuestTerrainStyle.INFERNAL -> if ((assetSeed and 1L) == 0L) Block.NETHER_WART_BLOCK else Block.WARPED_WART_BLOCK
            }
            val stems = when (style) {
                QuestTerrainStyle.VERDANT -> Block.OAK_FENCE
                QuestTerrainStyle.HIGHLANDS -> Block.SPRUCE_FENCE
                QuestTerrainStyle.SALTMARSH -> Block.MANGROVE_ROOTS
                QuestTerrainStyle.CLIFFLANDS -> Block.SPRUCE_FENCE
                QuestTerrainStyle.SAKURA_GROVE -> Block.CHERRY_FENCE
                QuestTerrainStyle.INFERNAL -> Block.CRIMSON_ROOTS
            }
            val centers = listOf(0 to 0, 2 to 1, -2 to 1, 1 to -2)
                .sortedBy { (dx, dz) -> Math.floorMod(assetSeed xor (dx * 31L) xor (dz * 47L), 97L) }
                .take(2 + random.nextInt(3))
            centers.forEachIndexed { index, (dx, dz) ->
                setGrounded(plan, dx, dz, 0, stems)
                setGrounded(plan, dx, dz, 1, leaf)
                listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (leafX, leafZ) ->
                    if (Math.floorMod(index + leafX * 3 + leafZ * 5 + assetSeed.toInt(), 4) != 0) {
                        setGrounded(plan, dx + leafX, dz + leafZ, 1, leaf)
                    }
                }
            }
            repeat(3 + random.nextInt(4)) {
                val dx = random.nextInt(7) - 3
                val dz = random.nextInt(7) - 3
                val detail = when (Math.floorMod(assetSeed + it, 4L).toInt()) {
                    0 -> Block.FERN
                    1 -> Block.MOSS_CARPET
                    2 -> when (style) {
                        QuestTerrainStyle.HIGHLANDS, QuestTerrainStyle.CLIFFLANDS -> Block.DEAD_BUSH
                        QuestTerrainStyle.INFERNAL -> Block.CRIMSON_FUNGUS
                        else -> Block.BROWN_MUSHROOM
                    }
                    else -> Block.SHORT_GRASS
                }
                setGrounded(plan, dx, dz, 0, detail)
            }
        }

        fun roadsideMarker(plan: QuestMapPlan, style: QuestTerrainStyle) {
            when (Math.floorMod(assetSeed, 4L).toInt()) {
                0 -> {
                    // A low cairn reads as a route cue without becoming a random tower.
                    setGrounded(plan, 0, 0, 0, Block.COBBLESTONE)
                    setGrounded(plan, 0, 0, 1, Block.MOSSY_COBBLESTONE)
                    setGrounded(plan, 1, 0, 0, Block.ANDESITE)
                    setGrounded(plan, -1, 1, 0, Block.MOSSY_COBBLESTONE)
                    setGrounded(plan, 0, 0, 2, Block.STONE_BUTTON)
                }
                1 -> {
                    // A grounded rest bench, not a freestanding sign or arch.
                    val timber = when (style) {
                        QuestTerrainStyle.HIGHLANDS, QuestTerrainStyle.CLIFFLANDS -> Block.SPRUCE_SLAB
                        QuestTerrainStyle.SAKURA_GROVE -> Block.CHERRY_SLAB
                        QuestTerrainStyle.INFERNAL -> Block.CRIMSON_SLAB
                        else -> Block.OAK_SLAB
                    }
                    setGrounded(plan, -1, 0, 0, timber)
                    setGrounded(plan, 0, 0, 0, timber)
                    setGrounded(plan, 1, 0, 0, timber)
                    setGrounded(plan, -1, 1, 0, Block.COBBLESTONE)
                    setGrounded(plan, 1, 1, 0, Block.MOSSY_COBBLESTONE)
                    setGrounded(plan, 2, 0, 0, Block.LANTERN)
                }
                2 -> {
                    // A half-buried milestone with a broad base.
                    setGrounded(plan, 0, 0, 0, Block.MOSSY_STONE_BRICKS)
                    setGrounded(plan, 0, 0, 1, Block.CHISELED_STONE_BRICKS)
                    setGrounded(plan, 1, 0, 0, Block.CRACKED_STONE_BRICKS)
                    setGrounded(plan, -1, 0, 0, Block.ANDESITE)
                    setGrounded(plan, -1, 0, 0, Block.MOSS_CARPET)
                }
                else -> {
                    // A collapsed road-edge fragment; its horizontal mass explains its presence.
                    setGrounded(plan, -1, 0, 0, Block.COBBLESTONE)
                    setGrounded(plan, 0, 0, 0, Block.MOSSY_COBBLESTONE)
                    setGrounded(plan, 1, 0, 0, Block.ANDESITE)
                    setGrounded(plan, 1, 0, 1, Block.COBBLESTONE_SLAB)
                    setGrounded(plan, 1, 0, 0, Block.MOSSY_COBBLESTONE)
                    setGrounded(
                        plan,
                        0,
                        1,
                        0,
                        if (style in setOf(QuestTerrainStyle.HIGHLANDS, QuestTerrainStyle.CLIFFLANDS, QuestTerrainStyle.INFERNAL)) Block.SOUL_LANTERN else Block.LANTERN,
                    )
                }
            }
        }

        private fun rotate(dx: Int, dz: Int): Pair<Int, Int> = when (Math.floorMod(rotation, 4)) {
            0 -> dx to dz
            1 -> -dz to dx
            2 -> -dx to -dz
            else -> dz to -dx
        }
    }
}
