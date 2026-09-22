package dev.projects.modellab

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import java.nio.file.Path

/** Loads the real engine and creates/destroys actors, without a socket or a Minecraft window. */
fun main(args: Array<String>) {
    MinecraftServer.init()
    try {
        val bundle = ModelBundle(Path.of(args.single()).resolve("bundle"))
        bundle.load()
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0, 1, Block.STONE) }
        instance.loadChunk(0, 0).join()
        val process = MinecraftServer.process()
        process.dispatcher().start() // Tick workers only; unlike server.start(), this opens no socket.
        var tick = 0L
        fun step(count: Int) { repeat(count) { process.ticker().tick(++tick * 50_000_000L) } }
        val baseline = instance.entities.size
        fun poses() = instance.entities.sortedBy { it.entityId }.mapNotNull {
            (it.entityMeta as? AbstractDisplayMeta)?.let { meta ->
                "${meta.translation}/${meta.leftRotation.contentToString()}/${meta.scale}"
            }
        }
        var movingAnimations = 0
        for (definition in bundle.definitions.values) {
            BossModelActor(definition, instance, Pos(8.0, 1.0, 8.0)).use { actor ->
                step(2)
                check(instance.entities.size > baseline) { "${definition.id}: no model entities" }
                definition.animations.forEach { (name, seconds) ->
                    actor.play(name)
                    val before = poses()
                    step(3)
                    if (poses() != before) movingAnimations++
                    step(kotlin.math.ceil(seconds * 20).toInt() + 3)
                }
                actor.move(Pos(9.0, 1.0, 8.0, 45f, 0f))
                actor.syncViewers()
                if (definition.id == "piglin_lord.bbmodel") {
                    actor.setBoneVisible("crown", false)
                    step(2)
                    actor.setBoneVisible("crown", true)
                }
            }
            step(3)
            check(instance.entities.size == baseline) { "${definition.id}: entity leak ${instance.entities.size - baseline}" }
        }
        check(movingAnimations > 0) { "Animations never changed native display metadata" }
        println("MODEL SMOKE PASS: ${bundle.definitions.size} models, ${bundle.definitions.values.sumOf { it.animations.size }} animations, $movingAnimations early pose changes, $tick ticks, zero entity leaks; no game launched")
    } finally {
        MinecraftServer.process().dispatcher().shutdown()
        MinecraftServer.stopCleanly()
    }
}
