package dev.projects.server.mob

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

/**
 * Real violet light for the 黒翳竜's effects: invisible light blocks placed where an effect glows and taken away
 * after it. 翳の巣's dimension tints all block light violet (BossArenaFactory.SHADE_NEST), so the floor and the wyrm
 * itself are lit by its dust, its breath and its horns. Only air (or a light this placed) is ever replaced.
 * Kept to a handful of blocks at a time: every change makes clients relight the section.
 */
internal class ShadeWyrmLights(private val instance: Instance) {
    private class Lamp(var level: Int, var until: Long)

    private val lamps = mutableMapOf<BlockVec, Lamp>()
    private val held = mutableMapOf<String, BlockVec>()
    private var now = 0L

    /** A light of [level] at [at] (or the air just above) for [millis]. */
    fun flash(at: Point, level: Int, millis: Long) {
        val spot = airAt(at) ?: return
        val lamp = lamps.getOrPut(spot) { Lamp(0, 0L) }
        lamp.until = maxOf(lamp.until, now + millis)
        if (level > lamp.level) { lamp.level = level; instance.setBlock(spot, light(level)) }
    }

    /** A light that follows something (the horns, a pool) under [key]; null takes it away. */
    fun hold(key: String, at: Point?, level: Int) {
        val spot = at?.let(::airAt)
        val old = held[key]
        if (spot == old) { if (spot != null) lamps[spot]?.until = Long.MAX_VALUE; return }
        old?.let { release(it) }
        if (spot == null) { held.remove(key); return }
        held[key] = spot
        val lamp = lamps.getOrPut(spot) { Lamp(0, 0L) }
        lamp.until = Long.MAX_VALUE
        if (level > lamp.level) { lamp.level = level; instance.setBlock(spot, light(level)) }
    }

    fun tick(nowMillis: Long) {
        now = nowMillis
        lamps.entries.filter { it.value.until <= now }.map { it.key }.forEach(::release)
    }

    fun dispose() {
        lamps.keys.toList().forEach(::release)
        held.clear()
    }

    /** The lights on now, for the preview dump. */
    internal fun snapshot(): Map<BlockVec, Int> = lamps.mapValues { it.value.level }

    private fun release(spot: BlockVec) {
        lamps.remove(spot) ?: return
        if (instance.getBlock(spot).compare(Block.LIGHT)) instance.setBlock(spot, Block.AIR)
    }

    private fun airAt(at: Point): BlockVec? {
        if (!instance.isChunkLoaded(at)) return null
        val base = BlockVec(at.blockX(), at.blockY(), at.blockZ())
        return listOf(base, base.add(0, 1, 0)).firstOrNull { val b = instance.getBlock(it); b.air() || b.compare(Block.LIGHT) }
    }

    private fun light(level: Int) = Block.LIGHT.withProperty("level", level.coerceIn(1, 15).toString())
}
