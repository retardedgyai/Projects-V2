package dev.projects.modellab

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import java.nio.file.Files
import java.nio.file.Path

/** Real WSEE Display metadata at 20 TPS, sockets and game windows remain closed. */
fun main(args: Array<String>) {
    MinecraftServer.init()
    val process = MinecraftServer.process()
    try {
        val directory = Path.of(args.single()).toAbsolutePath().normalize()
        require(directory.fileName.toString() == "boss-pack" && directory.parent.fileName.toString() == "build")
        val bundle = ModelBundle(directory.resolve("bundle")); bundle.load()
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator { it.modifier().fillHeight(0,1,Block.STONE) }
        for (x in -1..1) for (z in -1..1) instance.loadChunk(x,z).join()
        process.dispatcher().start()
        val actors = mutableMapOf<Int,BossModelActor>()
        val frames = JsonArray()
        val teeth = IceFangPlan.path(IceFangPlan.Point(0.0,1.0,0.0),0f) { true }
        val baseline = instance.entities.size
        try {
            for (tick in 0..55) {
                teeth.forEachIndexed { i,tooth ->
                    if (tick == tooth.start) {
                        val pos = Pos(tooth.point.x,tooth.point.y,tooth.point.z,0f,0f)
                        actors[i] = BossModelActor(bundle.definition(IceFangPlan.MODELS[i]),instance,pos,tooth.size).also {
                            it.move(pos); it.play("erupt")
                        }
                    }
                    if (tick >= tooth.start+IceFangPlan.LIFETIME) actors.remove(i)?.close()
                }
                process.ticker().tick((tick+1L)*50_000_000)
                val frame = JsonArray()
                actors.values.forEach { actor -> listOf("root","inner","outer").forEach { bone -> frame.add(actor.displayPose(bone)) } }
                frames.add(frame)
            }
            check(instance.entities.size == baseline) { "Trace left live entities" }
            // WSEE itself adds 180 degrees to Display yaw to cancel its fixed item's X/Z reversal.
            // Record four real native poses so the projector cannot silently omit this transform.
            val headings = JsonArray()
            var probeTick = 56L
            for (yaw in listOf(0f,90f,180f,-90f)) {
                val pos = Pos(0.0,1.0,0.0,yaw,0f)
                BossModelActor(bundle.definition(IceFangPlan.MODEL),instance,pos,1f).use { actor ->
                    actor.move(pos); actor.play("erupt")
                    repeat(6) { process.ticker().tick(++probeTick*50_000_000) }
                    val poses = JsonArray()
                    for (bone in listOf("root","inner","outer")) {
                        val pose = actor.displayPose(bone)
                        val expected = (yaw+540f)%360f
                        val actual = (pose["yaw"].asFloat+360f)%360f
                        check(kotlin.math.abs(actual-expected)<.001f) { "Lost Display heading: $yaw / $pose" }
                        poses.add(pose)
                    }
                    headings.add(JsonObject().apply { addProperty("castYaw",yaw); add("parts",poses) })
                }
            }
            check(instance.entities.size == baseline) { "Heading probe left entities" }
            val output = directory.parent.resolve("previews/ice-fang-native.json")
            Files.createDirectories(output.parent)
            Files.writeString(output, JsonObject().apply {
                addProperty("fps",20); addProperty("source","live WSEE Display metadata; not Minecraft rendering")
                add("frames",frames)
                add("headings",headings)
            }.toString())
            println("ICE FANG TRACE: $output (${frames.size()} frames, 3 actors, zero entity leaks)")
        } finally { actors.values.forEach(BossModelActor::close) }
    } finally { process.dispatcher().shutdown(); MinecraftServer.stopCleanly() }
}
