plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":protocol"))
    implementation("net.minestom:minestom:${property("minestom_version")}")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "dev.projects.server.ProjectSServerKt"
}

tasks.named<JavaExec>("run") {
    workingDir = file("run")
    doFirst { workingDir.mkdirs() }
}
