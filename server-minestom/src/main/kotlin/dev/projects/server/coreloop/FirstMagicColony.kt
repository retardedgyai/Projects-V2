package dev.projects.server.coreloop

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

internal enum class ColonyFixture { EXIT, DESK, DISTILLER, JARS, CHART, SEALED_DOOR }

/** A private island whose workshop blocks are saved and placed by the player. */
internal class FirstMagicColony private constructor(
    val instance: InstanceContainer,
    private val persist: (Collection<ColonyPlacement>) -> Unit,
) {
    val spawn = Pos(0.5, 41.0, -3.5, 145f, 0f)
    private val labels = mutableListOf<Entity>()
    private val models = mutableListOf<Entity>()
    private val modelReady = mutableMapOf<Entity, CompletableFuture<*>>()
    private val packedViewers = mutableSetOf<UUID>()
    private val placements = linkedMapOf<ColonyPlaceable, ColonyPlacement>()
    private val furniture = mutableMapOf<ColonyPlaceable, Entity>()
    private var packedBlocks = false

    fun showModels(player: Player, packed: Boolean) {
        if (packed) packedViewers += player.uuid else packedViewers -= player.uuid
        packedBlocks = packed
        for (entity in models) {
            if (!packed) entity.removeViewer(player)
            else {
                val ready = modelReady.getValue(entity)
                if (ready.isDone && !ready.isCompletedExceptionally) entity.addViewer(player)
                else ready.whenComplete { _, failure ->
                    if (failure == null) player.scheduler().scheduleNextTick {
                        if (player.isOnline && player.instance === instance && player.uuid in packedViewers && !entity.isRemoved)
                            entity.addViewer(player)
                    }
                }
            }
        }
        placements.values.forEach { instance.setBlock(it.x, it.y, it.z, blockFor(it.kind)) }
    }

    private fun display(model: String, at: Pos, scale: Vec): Entity = Entity(EntityType.ITEM_DISPLAY).apply {
        setNoGravity(true); setHasPhysics(false); setAutoViewable(false)
        editEntityMeta(ItemDisplayMeta::class.java) { meta ->
            meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel("projects:first_magic/$model"))
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
            meta.setScale(scale)
            meta.setViewRange(0.9f)
        }
        modelReady[this] = setInstance(this@FirstMagicColony.instance, at)
        models += this
        for (id in packedViewers) instance.players.firstOrNull { it.uuid == id }?.let { player ->
            modelReady.getValue(this).whenComplete { _, failure -> if (failure == null) player.scheduler().scheduleNextTick {
                if (player.isOnline && player.instance === instance && id in packedViewers && !isRemoved) addViewer(player)
            } }
        }
    }

    private fun model(entity: Entity, name: String) = entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
        meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel("projects:first_magic/$name"))
    }

    fun fixture(point: Point): ColonyFixture? = when {
        point.blockX() in -3..-2 && point.blockZ() in -4..-3 && point.blockY() in 41..42 -> ColonyFixture.EXIT
        point.blockX() in 9..11 && point.blockZ() in 21..22 && point.blockY() in 41..45 -> ColonyFixture.SEALED_DOOR
        else -> placementAt(point)?.kind?.fixture
    }

    fun placementAt(point: Point): ColonyPlacement? = placements.values.firstOrNull {
        it.x == point.blockX() && it.y == point.blockY() && it.z == point.blockZ()
    }

    fun missingItems(): List<ColonyPlaceable> = ColonyPlaceable.entries.filterNot(placements::containsKey)
    fun has(kind: ColonyPlaceable): Boolean = kind in placements

    fun modelPosition(kind: ColonyPlaceable): Pos? = placements[kind]?.let { Pos(it.x + .5, it.y + 1.0, it.z + .5) }

    fun place(kind: ColonyPlaceable, point: Point, facing: BlockFace, save: Boolean = true): Boolean {
        val x = point.blockX(); val y = point.blockY(); val z = point.blockZ()
        if (kind in placements || x !in -21..21 || z !in -20..23 || y !in 41..47 ||
            facing !in listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST) ||
            instance.getBlock(x, y, z) != Block.AIR) return false
        if (kind == ColonyPlaceable.STAR_CHART) {
            val backing = when (facing) {
                BlockFace.NORTH -> BlockVec(x, y, z + 1)
                BlockFace.SOUTH -> BlockVec(x, y, z - 1)
                BlockFace.EAST -> BlockVec(x - 1, y, z)
                else -> BlockVec(x + 1, y, z)
            }
            if (!instance.getBlock(backing).isSolid) return false
        } else if (!instance.getBlock(x, y - 1, z).isSolid) return false
        val placement = ColonyPlacement(kind, x, y, z, facing)
        if (save) persist(placements.values + placement)
        placements[kind] = placement
        render(placement)
        return true
    }

    fun pickUp(point: Point): ColonyPlaceable? {
        val placement = placementAt(point) ?: return null
        if (placements.values.any { it.x == placement.x && it.y == placement.y + 1 && it.z == placement.z }) return null
        if (placements.values.any { other ->
                if (other.kind != ColonyPlaceable.STAR_CHART) false else when (other.facing) {
                    BlockFace.NORTH -> other.x == placement.x && other.y == placement.y && other.z + 1 == placement.z
                    BlockFace.SOUTH -> other.x == placement.x && other.y == placement.y && other.z - 1 == placement.z
                    BlockFace.EAST -> other.x - 1 == placement.x && other.y == placement.y && other.z == placement.z
                    else -> other.x + 1 == placement.x && other.y == placement.y && other.z == placement.z
                }
            }) return null
        persist(placements.values.filterNot { it.kind == placement.kind })
        placements.remove(placement.kind)
        furniture.remove(placement.kind)?.let { entity ->
            models.remove(entity); modelReady.remove(entity); entity.remove()
        }
        instance.setBlock(placement.x, placement.y, placement.z, Block.AIR)
        return placement.kind
    }

    private fun blockFor(kind: ColonyPlaceable): Block = if (packedBlocks) Block.BARRIER else when (kind) {
        ColonyPlaceable.DESK -> Block.LECTERN
        ColonyPlaceable.DISTILLER -> Block.BREWING_STAND
        ColonyPlaceable.SHELF -> Block.BOOKSHELF
        ColonyPlaceable.JAR_EMBER -> Block.ORANGE_STAINED_GLASS
        ColonyPlaceable.JAR_TIDE -> Block.CYAN_STAINED_GLASS
        ColonyPlaceable.JAR_GALE -> Block.LIGHT_BLUE_STAINED_GLASS
        ColonyPlaceable.JAR_STONE -> Block.LIGHT_GRAY_STAINED_GLASS
        ColonyPlaceable.STAR_CHART -> Block.OAK_TRAPDOOR
    }

    private fun render(placement: ColonyPlacement) {
        instance.setBlock(placement.x, placement.y, placement.z, blockFor(placement.kind))
        val yaw = when (placement.facing) {
            BlockFace.NORTH -> 180f
            BlockFace.SOUTH -> 0f
            BlockFace.WEST -> 90f
            else -> -90f
        }
        val entity = display(placement.kind.model,
            Pos(placement.x + .5, placement.y + .5, placement.z + .5, yaw, 0f), Vec(1.0, 1.0, 1.0))
        furniture[placement.kind] = entity
    }

    fun update(state: FirstMagicState) {
        furniture.forEach { (kind, entity) ->
            when (kind) {
                ColonyPlaceable.DESK -> model(entity, if (state.deskRestored) "research_desk" else "research_desk_dormant")
                else -> kind.aspect?.let { aspect ->
                    val amount = state.jar(aspect)
                    model(entity, "jar_${aspect.name.lowercase()}_${if (amount == 0) "empty" else if (amount < 8) "low" else "high"}")
                }
            }
        }
    }

    fun dispose() {
        if (!instance.isRegistered) return
        check(instance.players.isEmpty())
        labels.forEach(Entity::remove)
        models.forEach(Entity::remove)
        packedViewers.clear()
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
        // An observatory wall gives the apparatus a room-like backdrop without closing the yard.
        box(6, 16, 41, 44, 1, 1, Block.STONE_BRICKS)
        for (x in listOf(6, 11, 16)) box(x, x, 41, 45, 1, 1, Block.STRIPPED_DARK_OAK_LOG)
        box(6, 16, 45, 45, 1, 1, Block.DARK_OAK_PLANKS)
        for (x in listOf(7, 15)) put(x, 43, 2, Block.LANTERN)
        // The yard is left clear. The player places every apparatus from their inventory.
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
        fun create(state: FirstMagicState, saved: List<ColonyPlacement> = emptyList(),
                   persist: (Collection<ColonyPlacement>) -> Unit = {}): FirstMagicColony {
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
                return FirstMagicColony(instance, persist).also { colony ->
                    colony.build()
                    saved.forEach { placement ->
                        require(colony.place(placement.kind, BlockVec(placement.x, placement.y, placement.z), placement.facing, save = false)) {
                            "Saved colony placement is obstructed: $placement"
                        }
                    }
                    colony.update(state)
                }
            } catch (failure: Throwable) {
                MinecraftServer.getInstanceManager().unregisterInstance(instance)
                throw failure
            }
        }
    }
}
