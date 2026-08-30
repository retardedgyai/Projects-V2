package dev.projects.server.experiment.swarm

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import java.nio.file.Path

private const val DEFAULT_ADDRESS = "127.0.0.1"
private const val DEFAULT_PORT = 25565

fun main() {
    val address = System.getenv("PROJECTS_SWARM_ADDRESS")?.takeIf(String::isNotBlank) ?: DEFAULT_ADDRESS
    val port = System.getenv("PROJECTS_SWARM_PORT")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
    val dataRoot = System.getenv("PROJECTS_SWARM_DATA_ROOT")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: Path.of("data")

    val server = MinecraftServer.init(Auth.Offline())
    val wiring = SwarmSliceWiring(dataRoot.toAbsolutePath().normalize())
    wiring.install()
    server.start(address, port)
    println("SWARM_VSLICE_READY address=$address port=$port data=${wiring.dataDirectory}")
}
