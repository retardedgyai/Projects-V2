package dev.projects.server

import dev.projects.server.coreloop.CoreLoopServer
import dev.projects.server.coreloop.WorldInfusionServer

fun main() {
    if (java.lang.Boolean.getBoolean("projects.worldInfusion")) {
        WorldInfusionServer.start()
        return
    }
    CoreLoopServer.start()
}
