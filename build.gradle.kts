plugins {
    kotlin("jvm") version "2.4.10" apply false
}

allprojects {
    group = "dev.projects"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
