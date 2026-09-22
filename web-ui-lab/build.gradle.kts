plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(25) }
dependencies {
    implementation("net.minestom:minestom:${property("minestom_version")}")
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
application { mainClass = "dev.projects.webui.WebUiLabKt" }
tasks.named<JavaExec>("run") {
    workingDir = projectDir
    args("ui/forge.html")
}
tasks.register<JavaExec>("uiSmoke") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(tasks.testClasses)
    mainClass = "dev.projects.webui.UiSmokeKt"
    args(projectDir.resolve("ui/forge.html").absolutePath)
}
distributions.main { contents { from("ui") { into("ui") } } }
