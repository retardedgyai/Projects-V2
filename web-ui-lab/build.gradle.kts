plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(25) }
dependencies {
    implementation("net.minestom:minestom:${property("minestom_version")}")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
}
kotlin.sourceSets.main { kotlin.srcDir(rootProject.file("assets/ui/polish05-import/native/src/main/kotlin")) }
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
tasks.register<JavaExec>("polish05Smoke") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(tasks.testClasses)
    mainClass = "dev.projects.webui.Polish05SmokeKt"
    args(projectDir.resolve("ui/forge.html").absolutePath,
        rootProject.file("assets/ui/polish05-import").absolutePath,
        projectDir.resolve("ui/polish05-font-map.json").absolutePath)
}
distributions.main { contents { from("ui") { into("ui") } } }
