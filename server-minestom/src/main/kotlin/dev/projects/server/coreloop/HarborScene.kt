package dev.projects.server.coreloop

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
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
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block

internal enum class HarborFacilityKind { EXPEDITIONS, WORKSHOP, STORAGE, SUPPLIES, MASTERY, COLONY, DUNGEON }

internal data class HarborFacility(
    val kind: HarborFacilityKind,
    val displayName: String,
    val position: Pos,
    val blockPositions: Set<Vec>,
    val interactionRadius: Double = 3.25,
)

/**
 * The lobby: 開拓者の町, drawn in scripts/buildkit/towns/first_town.py and shipped as region files (resources/lobby/first_town).
 * Where players arrive, the facilities, their click blocks and the smith come from its rooms.json
 * (towns/first_town_places.py). Call once during startup, before players enter the instance; nothing is saved back.
 */
internal object HarborScene {
    data class Result(val spawn: Pos, val facilities: List<HarborFacility>, val labels: List<Entity>, val scenery: List<Entity>, val smith: Entity)

    private const val MAP = "lobby/first_town"
    /** Below the river bed: whoever falls this far is put back where players arrive. */
    const val FALL_Y = -12.0

    fun build(instance: InstanceContainer): Result {
        instance.setTime(6000)
        instance.defaultClock()?.pause()
        instance.setWeather(Weather.CLEAR)
        instance.setChunkSupplier(::LightingChunk)
        instance.viewDistance(10)
        val folder = ShippedMap.folder(MAP)
        instance.chunkLoader = AnvilLoader(folder)
        // The town spans x -92..104 and z -92..84; its chunks carry no light until relit here.
        val loads = (-6..6).flatMap { x -> (-6..5).map { z -> instance.loadChunk(x, z) } }
        CompletableFuture.allOf(*loads.toTypedArray()).join()
        LightingChunk.relight(instance, instance.chunks)
        val places = JsonParser.parseString(Files.readString(folder.resolve("rooms.json"))).asJsonObject
        fun nums(a: JsonArray) = a.map { it.asDouble }
        val facilities = places.getAsJsonObject("facilities").entrySet().map { (key, value) ->
            val facility = value.asJsonObject
            facility.getAsJsonArray("place")?.forEach { spot ->
                val p = spot.asJsonArray
                val block = requireNotNull(Block.fromKey("minecraft:" + p[3].asString)) { p[3].asString }
                instance.setBlock(p[0].asInt, p[1].asInt, p[2].asInt, if (block.compare(Block.LECTERN)) block.withProperty("facing", "south") else block)
            }
            val (x, y, z) = nums(facility.getAsJsonArray("at"))
            HarborFacility(HarborFacilityKind.valueOf(key), facility["name"].asString, Pos(x + .5, y, z + .5),
                facility.getAsJsonArray("click").map { c -> nums(c.asJsonArray).let { (a, b, d) -> Vec(a, b, d) } }.toSet())
        }
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
        val (sx, sy, sz, syaw) = nums(places.getAsJsonArray("smith"))
        val smith = Entity(EntityType.VILLAGER).apply {
            setHasPhysics(false)
            setNoGravity(true)
            customName = Component.text("鍛冶師  │  話しかける", NamedTextColor.GOLD)
            isCustomNameVisible = true
            setInstance(instance, Pos(sx, sy, sz, syaw.toFloat(), 0f)).join()
        }
        val (x, y, z, yaw) = nums(places.getAsJsonArray("spawn"))
        return Result(Pos(x + .5, y, z + .5, yaw.toFloat(), 0f), facilities, labels, emptyList(), smith)
    }
}
