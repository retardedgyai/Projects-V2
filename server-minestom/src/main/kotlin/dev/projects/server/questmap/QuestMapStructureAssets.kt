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

        fun proceduralTree(style: QuestTerrainStyle, plan: QuestMapPlan) {
            val random = java.util.Random(assetSeed xor 0x5452454547524F57L)
            val profile = Math.floorMod(assetSeed, 24L).toInt()
            val localHeight = plan.heightAt(originX, originZ)
            val broadleafHighland = style == QuestTerrainStyle.HIGHLANDS && localHeight < 66 && profile in 16..19
            val log = when {
                style == QuestTerrainStyle.VERDANT && profile in 18..20 -> Block.BIRCH_LOG
                style == QuestTerrainStyle.VERDANT && profile >= 21 -> Block.DARK_OAK_LOG
                broadleafHighland -> Block.BIRCH_LOG
                style == QuestTerrainStyle.VERDANT -> Block.OAK_LOG
                style == QuestTerrainStyle.HIGHLANDS -> Block.SPRUCE_LOG
                else -> Block.MANGROVE_LOG
            }
            val leaves = when {
                style == QuestTerrainStyle.VERDANT && profile in 18..20 -> Block.BIRCH_LEAVES
                style == QuestTerrainStyle.VERDANT && profile >= 21 -> Block.DARK_OAK_LEAVES
                broadleafHighland -> Block.BIRCH_LEAVES
                style == QuestTerrainStyle.VERDANT -> Block.OAK_LEAVES
                style == QuestTerrainStyle.HIGHLANDS -> Block.SPRUCE_LEAVES
                else -> Block.MANGROVE_LEAVES
            }
            val baseHeight = when (style) {
                QuestTerrainStyle.VERDANT -> 6 + random.nextInt(8)
                QuestTerrainStyle.HIGHLANDS -> if (broadleafHighland) 6 + random.nextInt(5) else 7 + random.nextInt(7)
                QuestTerrainStyle.SALTMARSH -> 6 + random.nextInt(6)
            }
            val dead = profile in setOf(11, 23)
            val multiStem = profile in setOf(2, 7, 12, 17, 21)
            val windswept = profile in setOf(4, 9, 15)
            val conifer = style == QuestTerrainStyle.HIGHLANDS && !broadleafHighland
            val leanX = when {
                windswept -> 1
                profile == 6 -> -1
                else -> 0
            }
            val leanZ = if (profile == 8) 1 else 0

            if (style == QuestTerrainStyle.SALTMARSH) {
                listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2).forEachIndexed { index, (dx, dz) ->
                    setGrounded(plan, dx, dz, 0, if ((profile + index) and 1 == 0) Block.MANGROVE_ROOTS else Block.MUDDY_MANGROVE_ROOTS)
                    setGrounded(plan, dx / 2, dz / 2, 1, Block.MANGROVE_ROOTS)
                }
            } else if (profile % 3 == 0 || baseHeight >= 11) {
                listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEachIndexed { index, (dx, dz) ->
                    setGrounded(plan, dx, dz, if ((index + profile) and 1 == 0) 0 else -1, log)
                }
                listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).forEach { (dx, dz) ->
                    if (Math.floorMod(dx * 7 + dz * 11 + profile, 3) == 0) setGrounded(plan, dx, dz, 0, Block.ROOTED_DIRT)
                }
            }

            var trunkX = 0
            var trunkZ = 0
            for (y in 0..baseHeight) {
                if (y > baseHeight / 3 && y % 3 == 0) {
                    trunkX += leanX
                    trunkZ += leanZ
                }
                set(trunkX, y, trunkZ, log)
                if (profile == 13 && y < baseHeight / 2) set(trunkX + 1, y, trunkZ, log)
            }
            if (multiStem) {
                val secondX = if (profile and 1 == 0) 1 else -1
                val secondZ = if (profile % 3 == 0) 1 else 0
                column(secondX, secondZ, 0, baseHeight - 2 + random.nextInt(3), log)
            }

            val directions = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
            val branchCount = when (style) {
                QuestTerrainStyle.HIGHLANDS -> if (conifer) 5 + random.nextInt(5) else 3 + random.nextInt(4)
                else -> 3 + random.nextInt(5)
            }
            repeat(branchCount) { branch ->
                val (directionX, directionZ) = directions[Math.floorMod(profile * 3 + branch * 5 + random.nextInt(3), directions.size)]
                val branchY = (baseHeight * (0.48 + random.nextDouble() * 0.42)).toInt()
                val leanStepsAtBranch = if (branchY > baseHeight / 3) (branchY - baseHeight / 3 + 2) / 3 else 0
                val branchBaseX = leanX * leanStepsAtBranch
                val branchBaseZ = leanZ * leanStepsAtBranch
                val branchLength = when (style) {
                    QuestTerrainStyle.HIGHLANDS -> 2 + random.nextInt(3)
                    else -> 2 + random.nextInt(5)
                }
                var endpointX = branchBaseX
                var endpointZ = branchBaseZ
                repeat(branchLength) { step ->
                    endpointX = branchBaseX + directionX * (step + 1)
                    endpointZ = branchBaseZ + directionZ * (step + 1)
                    val branchLog = log.withProperty("axis", if (abs(directionX) >= abs(directionZ)) "x" else "z")
                    set(endpointX, branchY + step / 2, endpointZ, branchLog)
                }
                if (!dead || branch % 2 == 0) {
                    val crownRadius = when (style) {
                        QuestTerrainStyle.VERDANT -> 2 + random.nextInt(3)
                        QuestTerrainStyle.HIGHLANDS -> if (conifer) 1 + random.nextInt(2) else 2 + random.nextInt(3)
                        QuestTerrainStyle.SALTMARSH -> 2 + random.nextInt(3)
                    }
                    leafCrown(
                        endpointX,
                        branchY + branchLength / 2 + 1,
                        endpointZ,
                        crownRadius + if (windswept) 1 else 0,
                        if (conifer) 1 else 2,
                        crownRadius,
                        leaves,
                        clippedCorner = if (dead) 7 else 2 + random.nextInt(3),
                    )
                }
            }

            if (!dead) {
                val crownLayers = when (style) {
                    QuestTerrainStyle.HIGHLANDS -> if (conifer) 3 + random.nextInt(3) else 2 + random.nextInt(2)
                    else -> 2 + random.nextInt(3)
                }
                repeat(crownLayers) { layer ->
                    val offsetX = trunkX + random.nextInt(3) - 1 + if (windswept) layer else 0
                    val offsetZ = trunkZ + random.nextInt(3) - 1
                    val radius = when (style) {
                        QuestTerrainStyle.HIGHLANDS -> if (conifer) (3 - layer / 2).coerceAtLeast(1) else 2 + random.nextInt(3)
                        else -> 2 + random.nextInt(3)
                    }
                    leafCrown(
                        offsetX,
                        baseHeight - 1 + layer * if (conifer) 2 else 1,
                        offsetZ,
                        radius + if (style == QuestTerrainStyle.SALTMARSH) 1 else 0,
                        if (conifer) 1 else 2,
                        radius,
                        leaves,
                        clippedCorner = 2 + random.nextInt(3),
                    )
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

        fun groundedBoulder(plan: QuestMapPlan, style: QuestTerrainStyle) {
            val random = java.util.Random(assetSeed xor 0x424F554C444552L)
            val variation = Math.floorMod(assetSeed, 18L).toInt()
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
            val radiusX = 1 + random.nextInt(3)
            val radiusZ = 1 + random.nextInt(3)
            val layers = 1 + random.nextInt(3) + if (variation >= 14) 1 else 0
            val burial = 1 + random.nextInt(2)
            for (dx in -radiusX..radiusX) {
                for (dz in -radiusZ..radiusZ) {
                    val normalized = (dx * dx).toDouble() / (radiusX * radiusX) + (dz * dz).toDouble() / (radiusZ * radiusZ)
                    val edgeNoise = Math.floorMod(assetSeed xor (dx * 31L) xor (dz * 47L), 7L).toInt()
                    if (normalized > 1.05 || (normalized > 0.72 && edgeNoise <= 1)) continue
                    val baseBlock = if (Math.floorMod(dx + dz + variation, 5) == 0) secondary else primary
                    setAnchored(plan, dx, -burial, dz, baseBlock)
                    setAnchored(plan, dx, 1 - burial, dz, baseBlock)
                    for (layer in 1 until layers) {
                        val layerRadiusX = (radiusX - layer).coerceAtLeast(0)
                        val layerRadiusZ = (radiusZ - layer).coerceAtLeast(0)
                        if (abs(dx) > layerRadiusX || abs(dz) > layerRadiusZ) continue
                        if (Math.floorMod(dx * 5 + dz * 3 + layer + variation, 4) == 0) continue
                        setAnchored(plan, dx, layer + 1 - burial, dz, if ((layer + variation) % 4 == 0) secondary else primary)
                    }
                }
            }
            if (variation in setOf(5, 11, 17)) {
                repeat(2 + variation % 3) { spike -> setAnchored(plan, 0, layers + spike - burial, 0, primary) }
            }
            if (style != QuestTerrainStyle.HIGHLANDS) setAnchored(plan, 0, layers - burial + 1, 0, Block.MOSS_CARPET)
            val satellites = 1 + random.nextInt(3)
            repeat(satellites) { satellite ->
                val dx = (if (satellite and 1 == 0) radiusX + 2 else -radiusX - 2) + random.nextInt(2)
                val dz = random.nextInt(radiusZ * 2 + 3) - radiusZ - 1
                setGrounded(plan, dx, dz, -1, secondary)
                if (random.nextBoolean()) setGrounded(plan, dx, dz, 0, primary)
            }
        }

        fun shrubCluster(plan: QuestMapPlan, style: QuestTerrainStyle) {
            val random = java.util.Random(assetSeed xor 0x5348525542434C55L)
            val leaf = when (style) {
                QuestTerrainStyle.VERDANT -> if ((assetSeed and 1L) == 0L) Block.OAK_LEAVES else Block.AZALEA_LEAVES
                QuestTerrainStyle.HIGHLANDS -> if ((assetSeed and 1L) == 0L) Block.SPRUCE_LEAVES else Block.BIRCH_LEAVES
                QuestTerrainStyle.SALTMARSH -> Block.MANGROVE_LEAVES
            }
            val stems = when (style) {
                QuestTerrainStyle.VERDANT -> Block.OAK_FENCE
                QuestTerrainStyle.HIGHLANDS -> Block.SPRUCE_FENCE
                QuestTerrainStyle.SALTMARSH -> Block.MANGROVE_ROOTS
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
                    2 -> if (style == QuestTerrainStyle.HIGHLANDS) Block.DEAD_BUSH else Block.BROWN_MUSHROOM
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
                    val timber = if (style == QuestTerrainStyle.HIGHLANDS) Block.SPRUCE_SLAB else Block.OAK_SLAB
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
                    setGrounded(plan, 0, 1, 0, if (style == QuestTerrainStyle.HIGHLANDS) Block.SOUL_LANTERN else Block.LANTERN)
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
