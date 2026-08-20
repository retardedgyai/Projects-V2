pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "projects-v2"

include("protocol", "server-minestom", "client-fabric")
