package dev.projects.server.coreloop

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

internal enum class ColonyFixture { EXIT, DESK, DISTILLER, JARS, SEALED_DOOR }

/** A private fixed prototype: four readable spaces with all important silhouettes in one courtyard. */
internal class FirstMagicColony private constructor(val instance: InstanceContainer) {
    val spawn = Pos(0.5, 41.0, -3.5, 145f, 0f)
    private val labels = mutableListOf<Entity>()

    fun fixture(point: Point): ColonyFixture? = when {
        point.blockX() in -3..-2 && point.blockZ() in -4..-3 && point.blockY() in 41..42 -> ColonyFixture.EXIT
        point.blockX() in 7..9 && point.blockZ() in 3..4 && point.blockY() in 41..43 -> ColonyFixture.DESK
        point.blockX() in 13..15 && point.blockZ() in 3..5 && point.blockY() in 41..44 -> ColonyFixture.DISTILLER
        point.blockX() in 7..13 && point.blockZ() in 11..12 && point.blockY() in 41..44 -> ColonyFixture.JARS
        point.blockX() in 9..11 && point.blockZ() in 21..22 && point.blockY() in 41..45 -> ColonyFixture.SEALED_DOOR
        else -> null
    }

    fun update(state: FirstMagicState) {
        put(8, 42, 4, if (state.deskRestored) Block.AMETHYST_BLOCK else Block.CRACKED_STONE_BRICKS)
        put(8, 43, 4, if (state.deskRestored) Block.LIGHT_BLUE_STAINED_GLASS_PANE else Block.COBWEB)
        put(14, 43, 4, if (state.firstDistillation) Block.CYAN_STAINED_GLASS else Block.GLASS)
        FirstAspect.entries.forEachIndexed { index, aspect ->
            val x = 8 + index
            val amount = state.jar(aspect)
            val colored = when (aspect) {
                FirstAspect.EMBER -> Block.ORANGE_STAINED_GLASS
                FirstAspect.TIDE -> Block.CYAN_STAINED_GLASS
                FirstAspect.GALE -> Block.LIGHT_BLUE_STAINED_GLASS
                FirstAspect.STONE -> Block.WHITE_STAINED_GLASS
            }
            put(x, 42, 12, if (amount > 0) colored else Block.GLASS)
            put(x, 43, 12, if (amount >= 8) colored else Block.GLASS)
            put(x, 44, 12, Block.CUT_COPPER_SLAB)
        }
    }

    fun dispose() {
        if (!instance.isRegistered) return
        check(instance.players.isEmpty())
        labels.forEach(Entity::remove)
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
    }

    private fun put(x: Int, y: Int, z: Int, block: Block) = instance.setBlock(x, y, z, block)
    private fun box(x1: Int, x2: Int, y1: Int, y2: Int, z1: Int, z2: Int, block: Block) {
        for (x in x1..x2) for (z in z1..z2) for (y in y1..y2) put(x, y, z, block)
    }
    private fun sign(title: String, x: Double, y: Double, z: Double, color: NamedTextColor = NamedTextColor.GOLD) {
        val label = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true); setHasPhysics(false)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setText(Component.text(title, color))
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                meta.setScale(Vec(0.67, 0.67, 0.67)); meta.setShadow(true)
                meta.setBackgroundColor(0x740e1217); meta.setViewRange(0.55f)
            }
            setInstance(this@FirstMagicColony.instance, Pos(x, y, z))
        }
        labels += label
    }

    private fun build() {
        // A gentle island edge keeps the player's first view centered on the yard.
        for (x in -26..26) for (z in -24..27) {
            if (abs(x) > 22 || z !in -21..24) continue
            put(x, 40, z, if (Math.floorMod(x * 11 + z * 7, 13) == 0) Block.MOSSY_STONE_BRICKS else Block.GRASS_BLOCK)
            if (abs(x) == 22 || z == -21 || z == 24) put(x, 41, z, Block.OAK_LEAVES)
        }
        // Main hut: bed, chest, desk and a framed threshold.
        box(-7, 3, 40, 40, -17, -5, Block.SPRUCE_PLANKS)
        for (x in -7..3) for (z in -17..-5) if (x == -7 || x == 3 || z == -17 || z == -5) {
            for (y in 41..44) put(x, y, z, if (x in listOf(-7, 3) && z in listOf(-17, -5)) Block.STRIPPED_SPRUCE_LOG else Block.STONE_BRICKS)
        }
        box(-1, 0, 41, 43, -5, -5, Block.AIR)
        box(-8, 4, 45, 45, -18, -4, Block.DARK_OAK_PLANKS)
        put(-5, 41, -14, Block.RED_BED.withProperty("facing", "south"))
        put(1, 41, -14, Block.CHEST.withProperty("facing", "south"))
        put(1, 41, -11, Block.CRAFTING_TABLE)
        put(-3, 41, -4, Block.LECTERN.withProperty("facing", "south"))
        put(-2, 41, -4, Block.BARREL)
        put(-6, 43, -6, Block.LANTERN)
        sign("帰還の帳 / 港へ", -2.2, 43.25, -3.5)
        // Work corner is intentionally modest, but gives the hut a lived-in neighbor.
        box(-17, -9, 40, 40, 0, 8, Block.SPRUCE_PLANKS)
        box(-17, -17, 41, 43, 0, 8, Block.STRIPPED_SPRUCE_LOG)
        box(-17, -9, 41, 41, 8, 8, Block.STONE_BRICKS)
        box(-16, -14, 41, 41, 5, 5, Block.BARREL)
        put(-12, 41, 5, Block.SMITHING_TABLE)
        put(-10, 41, 5, Block.GRINDSTONE)
        put(-15, 42, 8, Block.LANTERN)
        // Clear walking spine; the material change announces the old observation yard.
        for (z in -4..21) for (x in -2..2) put(x, 40, z, Block.STONE_BRICKS)
        for (x in 1..17) for (z in 0..18) put(x, 40, z,
            if (Math.floorMod(x * 7 + z * 5, 17) in 0..2) Block.MOSSY_STONE_BRICKS else Block.STONE_BRICKS)
        for (x in 5..17) for (z in 1..17) {
            val dx = x - 11; val dz = z - 9
            if (dx * dx + dz * dz in 34..46) put(x, 40, z, Block.CHISELED_STONE_BRICKS)
        }
        for (z in listOf(1, 17)) for (x in 5..17) if (x % 4 == 1) put(x, 41, z, Block.STONE_BRICK_WALL)
        for (x in listOf(5, 17)) for (z in 2..16) if (z % 5 == 2) put(x, 41, z, Block.STONE_BRICK_WALL)
        // Desk: broad wood silhouette, paper, ink and a dormant observation disc.
        box(7, 9, 41, 41, 3, 4, Block.DARK_OAK_PLANKS)
        put(7, 42, 3, Block.LECTERN.withProperty("facing", "south"))
        put(9, 42, 3, Block.CANDLE)
        put(8, 42, 4, Block.CRACKED_STONE_BRICKS)
        sign("研究机 / 観測盤", 8.5, 44.3, 3.8)
        // Distiller: copper firebox, glass neck, lateral receiver.
        box(13, 15, 41, 41, 3, 5, Block.CUT_COPPER)
        put(14, 42, 4, Block.BLAST_FURNACE.withProperty("facing", "south"))
        put(14, 43, 4, Block.GLASS)
        put(13, 43, 4, Block.COPPER_GRATE)
        put(15, 42, 4, Block.CAULDRON)
        put(14, 44, 4, Block.COPPER_TRAPDOOR)
        sign("粗末な蒸留器", 14.5, 45.3, 4.3)
        // Four legible jars, each with its own shelf opening and label.
        box(7, 12, 41, 41, 11, 12, Block.DARK_OAK_PLANKS)
        box(7, 12, 44, 44, 11, 11, Block.DARK_OAK_SLAB)
        for (x in 8..11) { put(x, 42, 12, Block.GLASS); put(x, 43, 12, Block.GLASS); put(x, 44, 12, Block.CUT_COPPER_SLAB) }
        sign("Ember   Tide   Gale   Stone", 9.7, 45.1, 12.0, NamedTextColor.AQUA)
        // A single closed threshold and visible ring promise scale beyond this slice.
        box(7, 13, 40, 40, 19, 23, Block.POLISHED_ANDESITE)
        for (x in 7..13) for (z in 19..23) if (abs(x - 10) + abs(z - 21) == 3) put(x, 40, z, Block.CHISELED_STONE_BRICKS)
        for (x in 8..12) put(x, 41, 22, Block.DEEPSLATE_BRICKS)
        box(9, 11, 41, 44, 22, 22, Block.IRON_BARS)
        for (x in listOf(8, 12)) box(x, x, 41, 45, 22, 22, Block.CHISELED_DEEPSLATE)
        put(10, 45, 22, Block.AMETHYST_BLOCK)
        sign("封印区画 / 共鳴が不足している", 10.5, 46.0, 21.5, NamedTextColor.LIGHT_PURPLE)
        for ((x, z) in listOf(-4 to 2, 3 to 2, 4 to 15, 16 to 14)) put(x, 41, z, Block.LANTERN)
    }

    companion object {
        fun create(state: FirstMagicState): FirstMagicColony {
            val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
            instance.setChunkSupplier(::LightingChunk)
            instance.time = 13_000L
            instance.defaultClock()?.pause()
            instance.setWeather(Weather.CLEAR)
            instance.viewDistance(6)
            instance.setGenerator { unit ->
                unit.modifier().fillHeight(0, 39, Block.STONE)
                unit.modifier().fillHeight(39, 40, Block.DIRT)
                unit.modifier().fillHeight(40, 41, Block.GRASS_BLOCK)
            }
            try {
                CompletableFuture.allOf(*(-2..2).flatMap { x -> (-2..2).map { z -> instance.loadChunk(x, z) } }.toTypedArray()).join()
                return FirstMagicColony(instance).also { it.build(); it.update(state) }
            } catch (failure: Throwable) {
                MinecraftServer.getInstanceManager().unregisterInstance(instance)
                throw failure
            }
        }
    }
}
