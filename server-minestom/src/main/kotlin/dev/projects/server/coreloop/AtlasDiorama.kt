package dev.projects.server.coreloop

import dev.projects.server.questmap.QuestTerrainStyle
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.biome.Biome
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 開拓図: a miniature world of real blocks floating above the clouds, far out over the harbor's sea.
 * Six terrain sectors around a harbor islet; each sector has one islet per tier ring (T1 inside, T4 outside).
 * Generated as part of the hub world, so it is deterministic and costs nothing until a player looks at it.
 */
internal object AtlasDiorama {
    data class Region(val tier: Int, val style: QuestTerrainStyle) {
        val id get() = "T$tier-${style.name}"
    }
    data class Islet(val region: Region?, val x: Double, val z: Double, val radius: Double, val top: Int) {
        val anchor get() = Vec(x, top + 1.0, z)
    }

    const val CX = 4000
    const val CZ = 4000
    private const val BASE_Y = 201
    /** Sector order around the harbor, as angles in the x/z plane. VERDANT and CLIFFLANDS face the camera. */
    private val SECTORS = linkedMapOf(
        QuestTerrainStyle.VERDANT to 240.0, QuestTerrainStyle.CLIFFLANDS to 300.0, QuestTerrainStyle.HIGHLANDS to 0.0,
        QuestTerrainStyle.INFERNAL to 60.0, QuestTerrainStyle.SALTMARSH to 120.0, QuestTerrainStyle.SAKURA_GROVE to 180.0,
    )
    private val RING_RADIUS = doubleArrayOf(15.0, 26.0, 37.0, 48.0)
    private val ISLET_RADIUS = doubleArrayOf(5.2, 6.0, 6.6, 7.4)

    /** Camera north of the map looking south and down; offset to the screen's right so the panel has room. */
    const val PITCH = 52f
    val CAMERA = Pos(CX - 18.0, 284.0, CZ - 62.0, 0f, PITCH)

    val styleOrder get() = SECTORS.keys.toList()

    fun neighbours(style: QuestTerrainStyle): List<QuestTerrainStyle> {
        val order = styleOrder
        val i = order.indexOf(style)
        return listOf(order[(i + order.size - 1) % order.size], order[(i + 1) % order.size])
    }

    val islets: List<Islet> by lazy {
        buildList {
            add(Islet(null, CX + .5, CZ + .5, 7.5, BASE_Y + 1))
            for ((style, angle) in SECTORS) for (tier in 1..4) {
                val a = Math.toRadians(angle)
                val r = RING_RADIUS[tier - 1]
                val top = BASE_Y + tier * 3 + styleLift(style, tier)
                add(Islet(Region(tier, style), CX + .5 + cos(a) * r, CZ + .5 + sin(a) * r, ISLET_RADIUS[tier - 1], top))
            }
        }
    }

    fun islet(region: Region) = islets.first { it.region == region }

    private fun styleLift(style: QuestTerrainStyle, tier: Int) = when (style) {
        QuestTerrainStyle.HIGHLANDS -> 2 + tier
        QuestTerrainStyle.CLIFFLANDS -> 1 + tier
        QuestTerrainStyle.SALTMARSH -> -2
        QuestTerrainStyle.INFERNAL -> tier - 1
        else -> 0
    }

    fun biome(style: QuestTerrainStyle?): RegistryKey<Biome> = when (style) {
        QuestTerrainStyle.VERDANT -> Biome.PLAINS
        QuestTerrainStyle.HIGHLANDS -> Biome.SNOWY_SLOPES
        QuestTerrainStyle.SALTMARSH -> Biome.MANGROVE_SWAMP
        QuestTerrainStyle.CLIFFLANDS -> Biome.WINDSWEPT_HILLS
        QuestTerrainStyle.SAKURA_GROVE -> Biome.CHERRY_GROVE
        QuestTerrainStyle.INFERNAL -> Biome.BADLANDS
        null -> Biome.MEADOW
    }

    // ------------------------------------------------------------------ world data

    private class Canvas {
        val blocks = HashMap<Long, Block>()
        val biomes = HashMap<Long, RegistryKey<Biome>>()
        fun key(x: Int, y: Int, z: Int) = (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)
        fun col(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
        operator fun set(x: Int, y: Int, z: Int, b: Block) { blocks[key(x, y, z)] = b }
        operator fun get(x: Int, y: Int, z: Int): Block? = blocks[key(x, y, z)]
        fun setIfAir(x: Int, y: Int, z: Int, b: Block) { blocks.putIfAbsent(key(x, y, z), b) }
    }

    private val canvas: Canvas by lazy { Canvas().also(::paintAll) }

    private const val MIN_X = CX - 72
    private const val MAX_X = CX + 72
    private const val MIN_Z = CZ - 72
    private const val MAX_Z = CZ + 72

    /** Called by the hub generator for every unit; writes only the atlas footprint. */
    fun generate(unit: GenerationUnit) {
        val start = unit.absoluteStart(); val end = unit.absoluteEnd()
        if (end.blockX() <= MIN_X || start.blockX() > MAX_X || end.blockZ() <= MIN_Z || start.blockZ() > MAX_Z) return
        val c = canvas
        val m = unit.modifier()
        for (x in start.blockX() until end.blockX()) for (z in start.blockZ() until end.blockZ()) {
            val biome = c.biomes[c.col(x, z)] ?: continue
            var y = 176
            while (y < 248) { if (y >= start.blockY() && y < end.blockY()) m.setBiome(x, y, z, biome); y += 4 }
        }
        for ((k, b) in c.blocks) {
            val x = (k shr 38).toInt(); val z = ((k shl 26) shr 38).toInt(); val y = (k and 0xFFF).toInt()
            if (x >= start.blockX() && x < end.blockX() && z >= start.blockZ() && z < end.blockZ() && y >= start.blockY() && y < end.blockY())
                m.setBlock(x, y, z, b)
        }
    }

    /** Loads the atlas chunks (and the camera's) at startup so the first look is instant. */
    fun preload(instance: Instance): CompletableFuture<Void> {
        val loads = buildList {
            for (cx in Math.floorDiv(MIN_X, 16)..Math.floorDiv(MAX_X, 16))
                for (cz in Math.floorDiv(MIN_Z - 16, 16)..Math.floorDiv(MAX_Z, 16)) add(instance.loadChunk(cx, cz))
        }
        return CompletableFuture.allOf(*loads.toTypedArray())
    }

    // ------------------------------------------------------------------ painting

    private fun hash(vararg v: Int): Double {
        var h = 0x811C9DC5.toInt()
        for (a in v) { h = (h xor a) * 0x01000193 }
        h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
        return (h.toLong() and 0xFFFFFFFFL) / 4294967296.0
    }

    private fun noise(x: Double, z: Double, cell: Double, seed: Int): Double {
        val fx = x / cell; val fz = z / cell
        val i = floor(fx).toInt(); val j = floor(fz).toInt()
        val tx = fx - i; val tz = fz - j
        val sx = tx * tx * (3 - 2 * tx); val sz = tz * tz * (3 - 2 * tz)
        val a = hash(i, j, seed) + (hash(i + 1, j, seed) - hash(i, j, seed)) * sx
        val b = hash(i, j + 1, seed) + (hash(i + 1, j + 1, seed) - hash(i, j + 1, seed)) * sx
        return a + (b - a) * sz
    }

    private fun paintAll(c: Canvas) {
        for (islet in islets) paintIslet(c, islet)
        bridges(c)
    }

    private fun leaves(block: Block) = block.withProperty("persistent", "true")

    private fun paintIslet(c: Canvas, islet: Islet) {
        val style = islet.region?.style
        val tier = islet.region?.tier ?: 0
        val seed = (style?.ordinal ?: 9) * 31 + tier * 7
        val rnd = Random(seed)
        val r = islet.radius
        val top = HashMap<Long, Int>()
        val ix0 = floor(islet.x - r - 2).toInt(); val ix1 = floor(islet.x + r + 2).toInt()
        val iz0 = floor(islet.z - r - 2).toInt(); val iz1 = floor(islet.z + r + 2).toInt()
        val amp = when (style) {
            QuestTerrainStyle.HIGHLANDS -> 4.0 + tier; QuestTerrainStyle.CLIFFLANDS -> 3.0 + tier
            QuestTerrainStyle.SALTMARSH -> 1.0; null -> 1.0; else -> 2.0 + tier * .5
        }
        for (x in ix0..ix1) for (z in iz0..iz1) {
            val dx = x + .5 - islet.x; val dz = z + .5 - islet.z
            val d = hypot(dx, dz) / r + (noise(x.toDouble(), z.toDouble(), 3.0, seed) - .5) * .45
            if (d > 1.0) continue
            val edge = 1 - d
            var h = islet.top + ((noise(x.toDouble(), z.toDouble(), 4.0, seed + 1) - .35) * amp * (0.35 + edge)).roundToInt()
            if (style == QuestTerrainStyle.CLIFFLANDS) h = islet.top + Math.floorDiv(h - islet.top, 2) * 2
            if (edge < .12) h = minOf(h, islet.top)
            top[c.col(x, z)] = h
            c.biomes[c.col(x, z)] = biome(style)
            val depth = 3 + (edge.pow(.75) * r * 1.7).toInt() + (noise(x.toDouble(), z.toDouble(), 2.0, seed + 2) * 3).toInt()
            for (y in h - depth..h) c[x, y, z] = body(style, h - y, depth - (h - y), x, y, z)
            // Hanging roots and vines under the rim.
            if (edge < .3 && hash(x, z, seed, 5) < .25) {
                val hang = if (style == QuestTerrainStyle.INFERNAL) Block.WEEPING_VINES_PLANT else Block.HANGING_ROOTS
                c.setIfAir(x, h - depth - 1, z, hang)
            }
        }
        // Water / lava pools on low ground.
        if (style == QuestTerrainStyle.SALTMARSH || style == QuestTerrainStyle.INFERNAL || style == null) {
            for ((k, h) in top) {
                val x = (k shr 32).toInt(); val z = k.toInt()
                val inner = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).all { (a, b) -> (top[c.col(x + a, z + b)] ?: -999) >= h }
                val pool = noise(x.toDouble(), z.toDouble(), 2.5, seed + 3)
                if (inner && pool < if (style == null) .2 else .34) {
                    c[x, h, z] = if (style == QuestTerrainStyle.INFERNAL) Block.LAVA else Block.WATER
                    if (style == QuestTerrainStyle.SALTMARSH && hash(x, z, 7) < .3) c[x, h + 1, z] = Block.LILY_PAD
                }
            }
        }
        // Surface cover, plants and trees.
        val cells = top.entries.sortedBy { it.key }
        for ((k, h) in cells) {
            val x = (k shr 32).toInt(); val z = k.toInt()
            val ground = c[x, h, z] ?: continue
            if (ground == Block.WATER || ground == Block.LAVA) continue
            val roll = hash(x, z, seed, 11)
            val near = hypot(x + .5 - islet.x, z + .5 - islet.z)
            if (style == null) continue
            if (near < 1.6) continue // keep the islet centre clear for its marker
            if (roll < treeChance(style, tier)) { tree(c, style, x, h + 1, z, rnd); continue }
            plant(c, style, tier, x, h + 1, z, ground, hash(x, z, seed, 13))
        }
        if (style == null) harbor(c, islet)
        else landmark(c, islet, style, tier)
    }

    private fun body(style: QuestTerrainStyle?, below: Int, fromBottom: Int, x: Int, y: Int, z: Int): Block {
        val n = hash(x, y, z, 3)
        if (style == QuestTerrainStyle.INFERNAL) return when {
            below == 0 -> if (n < .25) Block.CRIMSON_NYLIUM else if (n < .55) Block.BLACKSTONE else Block.NETHERRACK
            below < 3 -> if (n < .15) Block.MAGMA_BLOCK else Block.NETHERRACK
            fromBottom < 2 -> Block.BASALT
            else -> if (n < .4) Block.BLACKSTONE else Block.NETHERRACK
        }
        if (below == 0) return when (style) {
            QuestTerrainStyle.HIGHLANDS -> if (y >= BASE_Y + 14) Block.SNOW_BLOCK else if (n < .35) Block.STONE else if (n < .45) Block.COARSE_DIRT else Block.GRASS_BLOCK
            QuestTerrainStyle.CLIFFLANDS -> if (n < .3) Block.STONE else if (n < .4) Block.ANDESITE else Block.GRASS_BLOCK
            QuestTerrainStyle.SALTMARSH -> if (n < .4) Block.MUD else if (n < .55) Block.MOSS_BLOCK else Block.GRASS_BLOCK
            QuestTerrainStyle.SAKURA_GROVE -> Block.GRASS_BLOCK
            null -> if (n < .3) Block.SAND else Block.GRASS_BLOCK
            else -> if (n < .06) Block.COARSE_DIRT else Block.GRASS_BLOCK
        }
        if (below < 3) return when (style) {
            QuestTerrainStyle.SALTMARSH -> if (n < .5) Block.MUD else Block.PACKED_MUD
            QuestTerrainStyle.CLIFFLANDS -> if (below == 1) Block.DIRT else Block.CALCITE
            else -> Block.DIRT
        }
        return when {
            fromBottom < 2 -> if (n < .5) Block.TUFF else Block.DEEPSLATE
            style == QuestTerrainStyle.CLIFFLANDS -> if ((y / 2) % 3 == 0) Block.CALCITE else if (n < .5) Block.ANDESITE else Block.STONE
            style == QuestTerrainStyle.HIGHLANDS -> if (n < .2) Block.ANDESITE else if (n < .3) Block.IRON_ORE else Block.STONE
            else -> if (n < .12) Block.ANDESITE else if (n < .16) Block.COAL_ORE else Block.STONE
        }
    }

    private fun treeChance(style: QuestTerrainStyle, tier: Int) = when (style) {
        QuestTerrainStyle.VERDANT -> .06; QuestTerrainStyle.HIGHLANDS -> .07; QuestTerrainStyle.SALTMARSH -> .04
        QuestTerrainStyle.CLIFFLANDS -> .025; QuestTerrainStyle.SAKURA_GROVE -> .06; QuestTerrainStyle.INFERNAL -> .05
    } * (if (tier >= 3) 1.2 else 1.0)

    private fun tree(c: Canvas, style: QuestTerrainStyle, x: Int, y: Int, z: Int, rnd: Random) {
        when (style) {
            QuestTerrainStyle.INFERNAL -> {
                val h = 2 + rnd.nextInt(4)
                for (i in 0 until h) c[x, y + i, z] = Block.BASALT.withProperty("axis", "y")
                if (rnd.nextBoolean()) c[x, y + h, z] = Block.FIRE
            }
            QuestTerrainStyle.HIGHLANDS -> {
                val h = 4 + rnd.nextInt(3)
                for (i in 0 until h) c[x, y + i, z] = Block.SPRUCE_LOG
                val leaf = leaves(Block.SPRUCE_LEAVES)
                for ((dy, rad) in listOf(h - 3 to 2, h - 2 to 1, h - 1 to 1, h to 0, h + 1 to 0))
                    for (a in -rad..rad) for (b in -rad..rad) if (abs(a) + abs(b) <= rad + 1 && (a != 0 || b != 0 || dy >= h)) c.setIfAir(x + a, y + dy, z + b, leaf)
            }
            QuestTerrainStyle.SAKURA_GROVE -> {
                val h = 3 + rnd.nextInt(2)
                for (i in 0 until h) c[x, y + i, z] = Block.CHERRY_LOG
                val leaf = leaves(Block.CHERRY_LEAVES)
                for (dy in h - 1..h + 1) {
                    val rad = if (dy == h + 1) 1 else 2
                    for (a in -rad..rad) for (b in -rad..rad)
                        if (a * a + b * b <= rad * rad + 1 && hash(x + a, y + dy, z + b, 21) > .1) c.setIfAir(x + a, y + dy, z + b, leaf)
                }
                for (a in -2..2) for (b in -2..2) if (hash(x + a, z + b, 22) < .3) c.setIfAir(x + a, y, z + b,
                    Block.PINK_PETALS.withProperty("flower_amount", (1 + (hash(x + a, z + b, 23) * 4).toInt().coerceAtMost(3)).toString()))
            }
            QuestTerrainStyle.SALTMARSH -> {
                c[x, y, z] = Block.MANGROVE_ROOTS
                val h = 3 + rnd.nextInt(2)
                for (i in 1..h) c[x, y + i, z] = Block.MANGROVE_LOG
                val leaf = leaves(Block.MANGROVE_LEAVES)
                for (dy in h..h + 1) for (a in -2..2) for (b in -2..2)
                    if (abs(a) + abs(b) <= 3 - (dy - h) && hash(x + a, y + dy, z + b, 24) > .15) c.setIfAir(x + a, y + dy, z + b, leaf)
            }
            else -> {
                val birch = style == QuestTerrainStyle.CLIFFLANDS
                val h = 3 + rnd.nextInt(2)
                for (i in 0 until h) c[x, y + i, z] = if (birch) Block.BIRCH_LOG else Block.OAK_LOG
                val leaf = leaves(if (birch) Block.BIRCH_LEAVES else Block.OAK_LEAVES)
                for (dy in h - 2..h + 1) {
                    val rad = if (dy >= h) 1 else 2
                    for (a in -rad..rad) for (b in -rad..rad)
                        if ((abs(a) < rad || abs(b) < rad || hash(x + a, y + dy, z + b, 25) < .4) && (a != 0 || b != 0 || dy >= h)) c.setIfAir(x + a, y + dy, z + b, leaf)
                }
            }
        }
    }

    private fun plant(c: Canvas, style: QuestTerrainStyle, tier: Int, x: Int, y: Int, z: Int, ground: Block, roll: Double) {
        if (c[x, y, z] != null) return
        when (style) {
            QuestTerrainStyle.VERDANT -> if (ground == Block.GRASS_BLOCK) c[x, y, z] = when {
                roll < .25 -> Block.SHORT_GRASS; roll < .31 -> Block.DANDELION; roll < .36 -> Block.POPPY
                roll < .40 -> Block.CORNFLOWER; roll < .43 -> Block.OXEYE_DAISY; else -> return
            }
            QuestTerrainStyle.SAKURA_GROVE -> if (ground == Block.GRASS_BLOCK) c[x, y, z] = when {
                roll < .2 -> Block.SHORT_GRASS; roll < .45 -> Block.PINK_PETALS.withProperty("flower_amount", "2"); else -> return
            }
            QuestTerrainStyle.HIGHLANDS -> when {
                ground == Block.SNOW_BLOCK || tier >= 3 && roll < .5 -> c[x, y, z] = Block.SNOW.withProperty("layers", "1")
                ground == Block.GRASS_BLOCK && roll < .3 -> c[x, y, z] = Block.SHORT_GRASS
                ground == Block.GRASS_BLOCK && roll < .34 -> c[x, y, z] = Block.SWEET_BERRY_BUSH.withProperty("age", "3")
            }
            QuestTerrainStyle.CLIFFLANDS -> if (ground == Block.GRASS_BLOCK && roll < .25) c[x, y, z] = if (roll < .05) Block.AZURE_BLUET else Block.SHORT_GRASS
            QuestTerrainStyle.SALTMARSH -> when {
                ground == Block.MOSS_BLOCK && roll < .5 -> c[x, y, z] = Block.MOSS_CARPET
                roll < .2 -> c[x, y, z] = Block.SHORT_GRASS
                roll < .24 -> c[x, y, z] = Block.BLUE_ORCHID
            }
            QuestTerrainStyle.INFERNAL -> when {
                ground == Block.CRIMSON_NYLIUM && roll < .4 -> c[x, y, z] = if (roll < .1) Block.CRIMSON_FUNGUS else Block.CRIMSON_ROOTS
                ground == Block.NETHERRACK && roll < .05 -> c[x, y, z] = Block.FIRE
            }
        }
    }

    /** One readable structure per islet: the region's boss lair, growing grander with the tier. */
    private fun landmark(c: Canvas, islet: Islet, style: QuestTerrainStyle, tier: Int) {
        val x = floor(islet.x).toInt(); val z = floor(islet.z).toInt()
        val y = islet.top
        for (a in -1..1) for (b in -1..1) {
            for (yy in y + 1..y + 6) if (c[x + a, yy, z + b]?.let { it.name().contains("leaves") || it.name().contains("log") } == true) c.blocks.remove(c.key(x + a, yy, z + b))
            c[x + a, y, z + b] = if (style == QuestTerrainStyle.INFERNAL) Block.POLISHED_BLACKSTONE_BRICKS else if ((a + b) % 2 == 0) Block.STONE_BRICKS else Block.MOSSY_STONE_BRICKS
        }
        val pillar = if (style == QuestTerrainStyle.INFERNAL) Block.POLISHED_BLACKSTONE_BRICKS else Block.STONE_BRICKS
        val light = if (style == QuestTerrainStyle.INFERNAL) Block.SHROOMLIGHT else Block.LANTERN
        val h = 1 + tier
        for ((a, b) in listOf(-1 to -1, 1 to 1)) {
            for (i in 1..h) c[x + a, y + i, z + b] = if (i == h && tier >= 3) Block.CHISELED_STONE_BRICKS else pillar
            c[x + a, y + h + 1, z + b] = light
        }
        if (tier >= 4) for (i in 1..h) c[x - 1, y + i, z + 1] = pillar
    }

    private fun harbor(c: Canvas, islet: Islet) {
        val x = floor(islet.x).toInt(); val z = floor(islet.z).toInt(); val y = islet.top
        // Lighthouse.
        for (i in 1..7) c[x, y + i, z] = if (i % 3 == 0) Block.RED_CONCRETE else Block.WHITE_CONCRETE
        c[x, y + 8, z] = Block.SEA_LANTERN
        c[x, y + 9, z] = Block.SPRUCE_SLAB
        // Two tiny houses.
        for ((hx, hz) in listOf(x + 3 to z - 2, x - 3 to z + 2)) {
            for (a in 0..1) for (b in 0..1) {
                c[hx + a, y + 1, hz + b] = Block.OAK_PLANKS
                c[hx + a, y + 2, hz + b] = if (a == 0 && b == 1) Block.GLASS_PANE else Block.OAK_PLANKS
                c[hx + a, y + 3, hz + b] = Block.DARK_OAK_SLAB
            }
        }
        // Dock towards the camera (north).
        for (i in 6..9) { c[x, y, z - i] = Block.SPRUCE_PLANKS; c[x, y - 1, z - i] = Block.SPRUCE_FENCE }
        c[x, y + 1, z - 9] = Block.LANTERN
    }

    private fun bridges(c: Canvas) {
        val hub = islets.first()
        for ((style, _) in SECTORS) {
            var prev = hub
            for (tier in 1..4) {
                val next = islet(Region(tier, style))
                bridge(c, prev, next)
                prev = next
            }
        }
        val order = styleOrder
        for (tier in 1..4) for (i in order.indices) {
            if (tier >= 2 && (i + tier) % 2 == 1) continue // fewer ring bridges further out
            bridge(c, islet(Region(tier, order[i])), islet(Region(tier, order[(i + 1) % order.size])))
        }
    }

    private fun bridge(c: Canvas, a: Islet, b: Islet) {
        val dx = b.x - a.x; val dz = b.z - a.z
        val len = hypot(dx, dz)
        val ux = dx / len; val uz = dz / len
        val sx = a.x + ux * (a.radius - 1); val sz = a.z + uz * (a.radius - 1)
        val ex = b.x - ux * (b.radius - 1); val ez = b.z - uz * (b.radius - 1)
        val steps = (hypot(ex - sx, ez - sz) * 2).toInt().coerceAtLeast(1)
        var last: Pair<Int, Int>? = null
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val x = floor(sx + (ex - sx) * t).toInt(); val z = floor(sz + (ez - sz) * t).toInt()
            if (last == x to z) continue
            last = x to z
            val sag = sin(Math.PI * t) * minOf(2.0, len / 12)
            val yf = a.top + (b.top - a.top) * t - sag
            val y = floor(yf).toInt()
            val upper = yf - y >= .5
            if (c[x, y, z] != null && c[x, y, z] != Block.AIR && !upper) continue
            c.setIfAir(x, y, z, Block.SPRUCE_SLAB.withProperty("type", if (upper) "top" else "bottom"))
            if (i % 6 == 3) {
                c.setIfAir(x, y + 1, z, Block.SPRUCE_FENCE)
                c.setIfAir(x, y + 2, z, Block.LANTERN)
            }
        }
    }

    /** Screen projection of a world point onto the UI plane (scene units 800×480), or null when behind the camera. */
    fun project(point: Vec, zoom: Double, distance: Double = 2.2): Pair<Double, Double>? {
        val p = Math.toRadians(PITCH.toDouble())
        val dx = point.x() - CAMERA.x(); val dy = point.y() - CAMERA.y(); val dz = point.z() - CAMERA.z()
        val depth = -dy * sin(p) + dz * cos(p)
        if (depth <= 0.5) return null
        val right = -dx
        val up = dy * cos(p) + dz * sin(p)
        val unit = 0.008 * zoom
        return 400 + right * distance / depth / unit to 240 - up * distance / depth / unit
    }

    fun projectedRadius(islet: Islet, zoom: Double, distance: Double = 2.2): Double {
        val p = Math.toRadians(PITCH.toDouble())
        val dy = islet.top - CAMERA.y(); val dz = islet.z - CAMERA.z()
        val depth = -dy * sin(p) + dz * cos(p)
        return islet.radius * distance / depth / (0.008 * zoom)
    }

    @Suppress("unused") private fun angleOf(x: Double, z: Double) = Math.toDegrees(atan2(z - CZ, x - CX))
}
