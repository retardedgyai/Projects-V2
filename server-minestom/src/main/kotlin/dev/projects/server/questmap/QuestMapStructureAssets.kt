package dev.projects.server.questmap

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Runtime structure catalog. Large nature silhouettes come from the reviewed schematic catalog;
 * small connective details remain procedural so imported assets read as part of the terrain.
 */
internal object QuestMapStructureAssets {
    internal enum class GatheringVisualKind {
        SCHEMATIC,
        ANIMAL_CORPSE,
    }

    internal data class GatheringObject(
        val assetId: String,
        val visualKind: GatheringVisualKind,
        val blocks: Map<BlockVec, Block>,
        val interactionPosition: Pos,
        val interactionWidth: Float,
        val interactionHeight: Float,
    )

    fun treeFamilyId(style: QuestTerrainStyle, variation: Int): Int =
        Math.floorMod(variation xor (style.ordinal * 0x45d9f3b), 5)

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

    fun resolveGatheringObject(plan: QuestMapPlan, node: QuestGatheringNode): GatheringObject {
        val origin = QuestMapPoint(node.blockPosition.blockX(), node.blockPosition.blockZ())
        val rotation = Math.floorMod(plan.seed.toInt() + node.id * 31, 4)
        if (node.discipline == QuestGatheringDiscipline.SKINNING) {
            return GatheringObject(
                assetId = "entity/cow_corpse",
                visualKind = GatheringVisualKind.ANIMAL_CORPSE,
                blocks = emptyMap(),
                interactionPosition = Pos(
                    origin.x + 0.5,
                    plan.heightAt(origin) + 1.05,
                    origin.z + 0.5,
                    (rotation * 90f),
                    0f,
                ),
                interactionWidth = 1.8f,
                interactionHeight = 1.35f,
            )
        }

        val variation = (plan.seed xor (node.id * 2_654_435_761L)).toInt()
        val selection = when (node.discipline) {
            QuestGatheringDiscipline.WOODCUTTING -> QuestMapSchematicCatalog.selectGatheringTree(plan.style, variation)
            QuestGatheringDiscipline.QUARRYING, QuestGatheringDiscipline.MINING ->
                QuestMapSchematicCatalog.selectGatheringBoulder(plan.style, variation)
            QuestGatheringDiscipline.HERBALISM -> QuestMapSchematicCatalog.selectGatheringPlant(plan.style, variation)
            QuestGatheringDiscipline.SKINNING -> error("死体にはschematicを使用しません")
        }
        val placement = selection.asset.resolvePlacement(plan, origin, rotation) { state, voxel ->
            val styled = selection.palette(state, voxel)
            if (node.discipline == QuestGatheringDiscipline.MINING && isMiningAccent(node, voxel)) {
                miningOreState(plan.style, node.quality)
            } else {
                styled
            }
        }
        val minX = placement.blocks.keys.minOf { it.blockX() }
        val maxX = placement.blocks.keys.maxOf { it.blockX() }
        val minZ = placement.blocks.keys.minOf { it.blockZ() }
        val maxZ = placement.blocks.keys.maxOf { it.blockZ() }
        val visualHeight = placement.maxY - placement.minY + 1
        val interactionWidth = when (node.discipline) {
            QuestGatheringDiscipline.WOODCUTTING -> 1.8f
            QuestGatheringDiscipline.QUARRYING, QuestGatheringDiscipline.MINING ->
                maxOf(maxX - minX + 1, maxZ - minZ + 1).toFloat().coerceIn(1.5f, 6f)
            QuestGatheringDiscipline.HERBALISM ->
                maxOf(maxX - minX + 1, maxZ - minZ + 1).toFloat().coerceIn(1.5f, 5f)
            QuestGatheringDiscipline.SKINNING -> error("死体には別の当たり判定を使用します")
        }
        val interactionHeight = when (node.discipline) {
            QuestGatheringDiscipline.WOODCUTTING -> visualHeight.toFloat().coerceIn(3f, 7f)
            QuestGatheringDiscipline.QUARRYING, QuestGatheringDiscipline.MINING -> visualHeight.toFloat().coerceIn(1.5f, 6f)
            QuestGatheringDiscipline.HERBALISM -> visualHeight.toFloat().coerceIn(1.2f, 3.5f)
            QuestGatheringDiscipline.SKINNING -> error("死体には別の当たり判定を使用します")
        }
        return GatheringObject(
            assetId = placement.assetId,
            visualKind = GatheringVisualKind.SCHEMATIC,
            blocks = placement.blocks,
            interactionPosition = Pos(
                origin.x + 0.5,
                placement.minY.toDouble(),
                origin.z + 0.5,
                (rotation * 90f),
                0f,
            ),
            interactionWidth = interactionWidth,
            interactionHeight = interactionHeight,
        )
    }

    fun placeGatheringObject(
        instance: Instance,
        plan: QuestMapPlan,
        node: QuestGatheringNode,
    ): GatheringObject = resolveGatheringObject(plan, node).also { gathering ->
        gathering.blocks.forEach { (position, block) -> instance.setBlock(position, block) }
    }

    private fun isMiningAccent(node: QuestGatheringNode, voxel: SchematicVoxel): Boolean =
        Math.floorMod(node.id * 97 + voxel.x * 31 + voxel.y * 17 + voxel.z * 43, 11) <=
            if (node.quality == QuestGatheringQuality.RARE) 2 else 1

    private fun miningOreState(style: QuestTerrainStyle, quality: QuestGatheringQuality): String = when {
        style == QuestTerrainStyle.INFERNAL -> "minecraft:nether_gold_ore"
        quality == QuestGatheringQuality.RARE -> "minecraft:emerald_ore"
        style in setOf(QuestTerrainStyle.HIGHLANDS, QuestTerrainStyle.CLIFFLANDS) -> "minecraft:iron_ore"
        else -> "minecraft:copper_ore"
    }

    /**
     * Compose several authored rock silhouettes into grounded geology. A stained apron makes the
     * mass emerge from the terrain instead of reading as an isolated schematic dropped on grass.
     */
    fun placeRockOutcrop(
        instance: Instance,
        plan: QuestMapPlan,
        origin: QuestMapPoint,
        variation: Int,
        rotation: Int,
    ) {
        val offsets = listOf(0 to 0, 4 to 2, -3 to 4, 2 to -4)
        offsets.forEachIndexed { index, (rawX, rawZ) ->
            val (dx, dz) = rotateOffset(rawX, rawZ, rotation)
            val point = QuestMapPoint(origin.x + dx, origin.z + dz)
            if (point.x !in 3 until plan.size - 3 || point.z !in 3 until plan.size - 3) return@forEachIndexed
            if (abs(plan.heightAt(point) - plan.heightAt(origin)) > 3) return@forEachIndexed
            placeBoulder(instance, plan, point, variation + index * 47, rotation + index)
        }
        for (dz in -7..7) {
            for (dx in -7..7) {
                if (dx * dx + dz * dz > 49) continue
                val x = origin.x + dx
                val z = origin.z + dz
                if (x !in 2 until plan.size - 2 || z !in 2 until plan.size - 2) continue
                val hash = Math.floorMod(variation * 31 + dx * 17 + dz * 43, 19)
                if (hash > 7 || abs(plan.heightAt(x, z) - plan.heightAt(origin)) > 4) continue
                val ground = plan.heightAt(x, z)
                val block = when (plan.style) {
                    QuestTerrainStyle.VERDANT -> if (hash < 2) Block.MOSSY_COBBLESTONE else Block.ANDESITE
                    QuestTerrainStyle.HIGHLANDS -> if (hash < 3) Block.TUFF else Block.ANDESITE
                    QuestTerrainStyle.SALTMARSH -> if (hash < 3) Block.MOSSY_COBBLESTONE else Block.MUD
                    QuestTerrainStyle.CLIFFLANDS -> if (hash < 2) Block.CALCITE else if (hash < 5) Block.ANDESITE else Block.STONE
                    QuestTerrainStyle.SAKURA_GROVE -> if (hash < 3) Block.MOSSY_COBBLESTONE else Block.ANDESITE
                    QuestTerrainStyle.INFERNAL -> if (hash < 4) Block.BLACKSTONE else Block.BASALT
                }
                instance.setBlock(x, ground, z, block)
            }
        }
    }

    fun treeFootprint(style: QuestTerrainStyle, variation: Int): Int =
        maxOf(9, QuestMapSchematicCatalog.selectTree(style, variation).asset.footprintRadius + 3)

    fun boulderFootprint(style: QuestTerrainStyle, variation: Int): Int =
        QuestMapSchematicCatalog.selectBoulder(style, variation).asset.footprintRadius + 2

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
        val selection = QuestMapSchematicCatalog.selectGroundDetail(plan.style, variation)
        selection.asset.place(instance, plan, origin, rotation, selection.palette)
    }

    fun groundDetailFootprint(style: QuestTerrainStyle, variation: Int): Int =
        QuestMapSchematicCatalog.selectGroundDetail(style, variation).asset.footprintRadius

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

    private fun rotateOffset(dx: Int, dz: Int, rotation: Int): Pair<Int, Int> = when (Math.floorMod(rotation, 4)) {
        0 -> dx to dz
        1 -> -dz to dx
        2 -> -dx to -dz
        else -> dz to -dx
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
                            assetSeed xor ((centerX + dx) * 734_287L) xor
                                ((centerY + dy) * 912_271L) xor ((centerZ + dz) * 438_289L),
                            11L,
                        ).toInt()
                        if (normalized <= 1.65 && leafHash >= clippedCorner) {
                            set(centerX + dx, centerY + dy, centerZ + dz, block)
                        }
                    }
                }
            }
        }

        fun signatureTree(plan: QuestMapPlan, style: QuestTerrainStyle, variation: Int) {
            val family = treeFamilyId(style, variation)
            when (style) {
                QuestTerrainStyle.VERDANT -> when (family) {
                    0 -> broadleafTree(plan, Block.DARK_OAK_LOG, Block.OAK_LEAVES, variation, veteran = true)
                    1 -> broadleafTree(plan, Block.OAK_LOG, Block.OAK_LEAVES, variation, veteran = false)
                    2 -> broadleafTree(plan, Block.BIRCH_LOG, Block.BIRCH_LEAVES, variation, veteran = false)
                    3 -> coniferTree(plan, Block.SPRUCE_LOG, Block.SPRUCE_LEAVES, variation, sparse = true)
                    else -> windsweptTree(plan, Block.OAK_LOG, Block.AZALEA_LEAVES, variation)
                }
                QuestTerrainStyle.HIGHLANDS -> when (family) {
                    0, 1 -> coniferTree(plan, Block.SPRUCE_LOG, Block.SPRUCE_LEAVES, variation, sparse = family == 1)
                    2 -> windsweptTree(plan, Block.SPRUCE_LOG, Block.SPRUCE_LEAVES, variation)
                    3 -> snagTree(plan, Block.STRIPPED_SPRUCE_LOG, variation)
                    else -> broadleafTree(plan, Block.BIRCH_LOG, Block.BIRCH_LEAVES, variation, veteran = false)
                }
                QuestTerrainStyle.SALTMARSH -> when (family) {
                    0, 1 -> mangroveTree(plan, variation)
                    2 -> snagTree(plan, Block.STRIPPED_MANGROVE_LOG, variation)
                    3 -> broadleafTree(plan, Block.DARK_OAK_LOG, Block.AZALEA_LEAVES, variation, veteran = false)
                    else -> windsweptTree(plan, Block.MANGROVE_LOG, Block.MANGROVE_LEAVES, variation)
                }
                QuestTerrainStyle.CLIFFLANDS -> when (family) {
                    0, 1 -> windsweptTree(plan, Block.SPRUCE_LOG, Block.SPRUCE_LEAVES, variation)
                    2 -> snagTree(plan, Block.STRIPPED_SPRUCE_LOG, variation)
                    3 -> coniferTree(plan, Block.SPRUCE_LOG, Block.SPRUCE_LEAVES, variation, sparse = true)
                    else -> windsweptTree(plan, Block.OAK_LOG, Block.AZALEA_LEAVES, variation)
                }
                QuestTerrainStyle.SAKURA_GROVE -> when (family) {
                    0, 1 -> broadleafTree(plan, Block.CHERRY_LOG, Block.CHERRY_LEAVES, variation, veteran = family == 0)
                    2 -> broadleafTree(plan, Block.BIRCH_LOG, Block.BIRCH_LEAVES, variation, veteran = false)
                    3 -> windsweptTree(plan, Block.CHERRY_LOG, Block.CHERRY_LEAVES, variation)
                    else -> coniferTree(plan, Block.SPRUCE_LOG, Block.SPRUCE_LEAVES, variation, sparse = true)
                }
                QuestTerrainStyle.INFERNAL -> when (family) {
                    0, 1, 2 -> infernalTree(plan, variation)
                    3 -> snagTree(plan, Block.STRIPPED_CRIMSON_STEM, variation)
                    else -> windsweptTree(plan, Block.WARPED_STEM, Block.WARPED_WART_BLOCK, variation)
                }
            }
        }

        fun rockMass(plan: QuestMapPlan, style: QuestTerrainStyle, variation: Int) {
            val random = java.util.Random(assetSeed xor 0x524F434B4D415353L)
            val radius = 3 + Math.floorMod(variation + style.ordinal, 3)
            val lobes = buildList {
                add(Triple(0 to 0, radius, maxOf(2, radius - 1)))
                add(Triple((radius - 1) to 1, maxOf(2, radius - 2), maxOf(2, radius - 2)))
                if (variation and 1 == 0) add(Triple((-radius + 2) to 2, maxOf(2, radius - 2), 2))
            }
            lobes.forEachIndexed { lobeIndex, (center, radiusX, radiusZ) ->
                for (dz in -radiusZ..radiusZ) {
                    for (dx in -radiusX..radiusX) {
                        val normalized = (dx * dx).toDouble() / (radiusX * radiusX) +
                            (dz * dz).toDouble() / (radiusZ * radiusZ)
                        if (normalized > 1.0) continue
                        val crown = ((1.0 - normalized) * (2.5 + radiusX * 0.55)).roundToInt().coerceAtLeast(1)
                        for (dy in 0..crown) {
                            val hash = Math.floorMod(variation * 31 + dx * 17 + dz * 43 + dy * 11 + lobeIndex * 7, 17)
                            val block = rockBlock(style, hash)
                            setGrounded(plan, center.first + dx, center.second + dz, dy, block)
                        }
                    }
                }
            }
            repeat(3 + random.nextInt(4)) {
                val dx = random.nextInt(radius * 2 + 3) - radius - 1
                val dz = random.nextInt(radius * 2 + 3) - radius - 1
                setGrounded(plan, dx, dz, 0, rockBlock(style, random.nextInt(17)))
            }
        }

        private fun broadleafTree(
            plan: QuestMapPlan,
            log: Block,
            leaves: Block,
            variation: Int,
            veteran: Boolean,
        ) {
            val random = java.util.Random(assetSeed xor 0x42524F41444C4541L)
            val form = Math.floorMod(variation, 4)
            val height = when (form) {
                0 -> 22 + Math.floorMod(variation, 4)
                1 -> 17 + Math.floorMod(variation, 3)
                2 -> 19 + Math.floorMod(variation, 4)
                else -> 15 + Math.floorMod(variation, 4)
            }
            val leanX = if (form == 2) 3 else if (form == 3) -2 else 0
            val leanZ = if (form == 1) 2 else 0
            roots(plan, log, if (veteran) 5 else 4, if (veteran) 7 else 5)
            branch(0, 0, 0, leanX, height - 2, leanZ, log)
            if (veteran) {
                column(1, 0, 0, 6, log.withProperty("axis", "y"))
                column(0, 1, 0, 4, log.withProperty("axis", "y"))
            }
            val directions = eightDirections(Math.floorMod(variation, 8))
            val branchCount = when (form) {
                0 -> 7
                1 -> 5
                2 -> 6
                else -> 8
            }
            directions.take(branchCount).forEachIndexed { index, (directionX, directionZ) ->
                val startY = height / 2 + index % 3 * 2
                val length = when (form) {
                    0 -> 7 + random.nextInt(3)
                    1 -> 5 + random.nextInt(3)
                    2 -> 6 + random.nextInt(3)
                    else -> 5 + random.nextInt(2)
                }
                val endX = leanX + directionX * length
                val endZ = leanZ + directionZ * length
                val endY = startY + 2 + random.nextInt(4)
                val startX = (leanX * startY.toDouble() / height).roundToInt()
                val startZ = (leanZ * startY.toDouble() / height).roundToInt()
                branch(startX, startY, startZ, endX, endY, endZ, log)
                val middleX = (startX + endX) / 2
                val middleZ = (startZ + endZ) / 2
                val middleY = (startY + endY) / 2
                leafCrown(middleX, middleY + 1, middleZ, 2, 1, 2, leaves, clippedCorner = 3)
                val crownRadius = if ((veteran || form == 3) && index % 2 == 0) 4 else 3
                leafCrown(endX, endY + 1, endZ, crownRadius, 2, crownRadius, leaves, clippedCorner = 2)
            }
            val crownRadius = if (veteran || form == 0) 5 else 4
            leafCrown(leanX, height, leanZ, crownRadius, 3, crownRadius, leaves, clippedCorner = 2)
            if (leaves == Block.CHERRY_LEAVES) {
                repeat(12) {
                    val dx = random.nextInt(13) - 6
                    val dz = random.nextInt(13) - 6
                    if (dx * dx + dz * dz <= 38) setGrounded(plan, dx, dz, 0, Block.PINK_PETALS)
                }
            }
        }

        private fun coniferTree(
            plan: QuestMapPlan,
            log: Block,
            leaves: Block,
            variation: Int,
            sparse: Boolean,
        ) {
            val height = 22 + Math.floorMod(variation, 9)
            roots(plan, log, 4, 5)
            column(0, 0, 0, height, log.withProperty("axis", "y"))
            var ring = 0
            for (y in 5 until height - 2 step 3) {
                val radius = (((height - y) * 0.34).roundToInt() + 1).coerceIn(2, 7)
                val directions = eightDirections(Math.floorMod(variation + ring * 2, 8))
                val branchCount = if (sparse) 3 else 4 + ring % 2
                directions.take(branchCount).forEachIndexed { index, (directionX, directionZ) ->
                    val length = (radius - if (index and 1 == 0) 0 else 1).coerceAtLeast(2)
                    val endX = directionX * length
                    val endZ = directionZ * length
                    branch(0, y, 0, endX, y - 1, endZ, log)
                    leafCrown(endX, y, endZ, 2, 1, 2, leaves, clippedCorner = if (sparse) 4 else 2)
                }
                leafCrown(0, y + 1, 0, 2, 1, 2, leaves, clippedCorner = if (sparse) 4 else 2)
                ring++
            }
            leafCrown(0, height, 0, 2, 2, 2, leaves, clippedCorner = 2)
        }

        private fun windsweptTree(
            plan: QuestMapPlan,
            log: Block,
            leaves: Block,
            variation: Int,
        ) {
            val random = java.util.Random(assetSeed xor 0x57494E4453574550L)
            val height = 18 + Math.floorMod(variation, 8)
            roots(plan, log, 5, 6)
            for (y in 0..height) {
                val leanX = (y / 6).coerceAtMost(3)
                set(leanX, y, if (y > height / 2) (y - height / 2) / 9 else 0, log.withProperty("axis", "y"))
            }
            val levels = listOf(height / 2, height / 2 + 4, height - 4, height - 1)
            levels.forEachIndexed { index, y ->
                val trunkX = (y / 6).coerceAtMost(3)
                val length = 6 + index + random.nextInt(3)
                val endX = trunkX + length
                val endZ = (index - 1) * 2 + random.nextInt(3) - 1
                branch(trunkX, y, 0, endX, y + index % 2, endZ, log)
                leafCrown(endX, y + 1, endZ, 3 + index % 2, 2, 3, leaves, clippedCorner = 3)
                if (index > 0) leafCrown(endX - 3, y + 1, endZ, 3, 1, 2, leaves, clippedCorner = 3)
            }
            leafCrown(3, height + 1, 0, 3, 2, 3, leaves, clippedCorner = 3)
        }

        private fun mangroveTree(plan: QuestMapPlan, variation: Int) {
            val random = java.util.Random(assetSeed xor 0x4D414E47524F5645L)
            val log = Block.MANGROVE_LOG
            val leaves = Block.MANGROVE_LEAVES
            val height = 15 + Math.floorMod(variation, 6)
            eightDirections(Math.floorMod(variation, 8)).take(6).forEachIndexed { index, (dx, dz) ->
                val length = 4 + index % 3
                branch(dx * length, 0, dz * length, 0, 5, 0, log)
                setGrounded(plan, dx * length, dz * length, 0, Block.MANGROVE_ROOTS)
            }
            column(0, 0, 4, height, log.withProperty("axis", "y"))
            eightDirections(Math.floorMod(variation + 2, 8)).take(6).forEachIndexed { index, (dx, dz) ->
                val y = height - 5 + index % 3
                val length = 5 + random.nextInt(4)
                branch(0, y, 0, dx * length, y + 2, dz * length, log)
                leafCrown(dx * length, y + 3, dz * length, 4, 2, 4, leaves, clippedCorner = 2)
                if (index and 1 == 0) set(dx * length, y, dz * length, Block.HANGING_ROOTS)
            }
            leafCrown(0, height + 1, 0, 4, 2, 4, leaves, clippedCorner = 2)
        }

        private fun infernalTree(plan: QuestMapPlan, variation: Int) {
            val warped = variation and 1 == 0
            val log = if (warped) Block.WARPED_STEM else Block.CRIMSON_STEM
            val leaves = if (warped) Block.WARPED_WART_BLOCK else Block.NETHER_WART_BLOCK
            windsweptTree(plan, log, leaves, variation)
        }

        private fun snagTree(plan: QuestMapPlan, log: Block, variation: Int) {
            val random = java.util.Random(assetSeed xor 0x534E414754524545L)
            val height = 13 + Math.floorMod(variation, 8)
            roots(plan, log, 4, 5)
            val leanX = Math.floorMod(variation, 3) - 1
            val leanZ = Math.floorMod(variation / 3, 3) - 1
            branch(0, 0, 0, leanX * 2, height, leanZ * 2, log)
            val directions = eightDirections(Math.floorMod(variation, 8))
            directions.take(3 + Math.floorMod(variation, 3)).forEachIndexed { index, (dx, dz) ->
                val startY = 6 + index * 2
                val length = 3 + random.nextInt(4)
                branch(
                    (leanX * startY.toDouble() / height).roundToInt(),
                    startY,
                    (leanZ * startY.toDouble() / height).roundToInt(),
                    dx * length,
                    startY + 2 + random.nextInt(3),
                    dz * length,
                    log,
                )
            }
        }

        private fun roots(plan: QuestMapPlan, log: Block, radius: Int, count: Int) {
            eightDirections(Math.floorMod(assetSeed.toInt(), 8)).take(count).forEachIndexed { index, (dx, dz) ->
                val length = (radius - index % 2).coerceAtLeast(2)
                for (step in 1..length) {
                    setGrounded(plan, dx * step, dz * step, 0, horizontalLog(log, dx, dz))
                    if (step == 1 && index and 1 == 0) setGrounded(plan, dx * step, dz * step, 1, log.withProperty("axis", "y"))
                }
            }
        }

        private fun branch(
            startX: Int,
            startY: Int,
            startZ: Int,
            endX: Int,
            endY: Int,
            endZ: Int,
            log: Block,
        ) {
            val deltaX = endX - startX
            val deltaY = endY - startY
            val deltaZ = endZ - startZ
            val steps = maxOf(abs(deltaX), abs(deltaY), abs(deltaZ)).coerceAtLeast(1)
            val branchLog = if (abs(deltaY) > maxOf(abs(deltaX), abs(deltaZ))) {
                log.withProperty("axis", "y")
            } else {
                horizontalLog(log, deltaX, deltaZ)
            }
            for (step in 0..steps) {
                val progress = step.toDouble() / steps
                set(
                    (startX + deltaX * progress).roundToInt(),
                    (startY + deltaY * progress).roundToInt(),
                    (startZ + deltaZ * progress).roundToInt(),
                    branchLog,
                )
            }
        }

        private fun horizontalLog(log: Block, dx: Int, dz: Int): Block {
            val localAxis = if (abs(dx) >= abs(dz)) "x" else "z"
            val worldAxis = if (Math.floorMod(rotation, 2) == 1) {
                if (localAxis == "x") "z" else "x"
            } else {
                localAxis
            }
            return log.withProperty("axis", worldAxis)
        }

        private fun eightDirections(offset: Int): List<Pair<Int, Int>> {
            val directions = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
            return directions.indices.map { directions[Math.floorMod(it + offset, directions.size)] }
        }

        private fun rockBlock(style: QuestTerrainStyle, hash: Int): Block = when (style) {
            QuestTerrainStyle.VERDANT -> when { hash < 3 -> Block.MOSSY_COBBLESTONE; hash < 8 -> Block.ANDESITE; else -> Block.STONE }
            QuestTerrainStyle.HIGHLANDS -> when { hash < 5 -> Block.TUFF; hash < 10 -> Block.ANDESITE; else -> Block.STONE }
            QuestTerrainStyle.SALTMARSH -> when { hash < 4 -> Block.MOSSY_COBBLESTONE; hash < 8 -> Block.MUD_BRICKS; else -> Block.STONE }
            QuestTerrainStyle.CLIFFLANDS -> when { hash < 3 -> Block.CALCITE; hash < 9 -> Block.ANDESITE; else -> Block.STONE }
            QuestTerrainStyle.SAKURA_GROVE -> when { hash < 4 -> Block.MOSSY_COBBLESTONE; hash < 8 -> Block.CALCITE; else -> Block.STONE }
            QuestTerrainStyle.INFERNAL -> when { hash < 5 -> Block.BASALT; hash < 11 -> Block.BLACKSTONE; else -> Block.NETHERRACK }
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
