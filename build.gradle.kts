plugins {
    kotlin("jvm") version "2.4.10" apply false
}

allprojects {
    group = "dev.projects"
    version = "0.1.0-SNAPSHOT"

    repositories {
        // Minecraft 26.3: Minestom has no release yet, only the snapshot of PR #3369 (see gradle.properties).
        exclusiveContent {
            forRepository {
                maven("https://central.sonatype.com/repository/maven-snapshots/") { name = "SonatypeSnapshots" }
            }
            filter { includeModule("net.minestom", "minestom") }
        }
        mavenCentral()
    }
}
