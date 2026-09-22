import java.time.Duration

plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(25) }
repositories {
    maven("https://reposilite.atlasengine.ca/public")
    maven("https://repo.opencollab.dev/main/")
}
dependencies {
    implementation("net.minestom:minestom:${property("minestom_version")}")
    implementation("net.worldseed.multipart:WorldSeedEntityEngine:13.0.0")
    testImplementation(kotlin("test"))
}
tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
application {
    mainClass = "dev.projects.modellab.ModelLabKt"
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
val modelSource = rootProject.file(providers.gradleProperty("modelSource").getOrElse("vendor/scorpius/bbmodel"))
val packOutput = layout.buildDirectory.dir("boss-pack")
val generator = sourceSets.create("generator")
dependencies { add(generator.implementationConfigurationName, "com.google.code.gson:gson:2.14.0") }
val generatedNames = layout.buildDirectory.dir("generated/model-names")
val generateModelNames = tasks.register<JavaExec>("generateModelNames") {
    classpath = generator.runtimeClasspath
    mainClass = "com.yuuki14202028.generator.GenerateWseeAssets"
    args(modelSource.absolutePath, generatedNames.get().asFile.absolutePath)
    inputs.files(fileTree(modelSource) { include("*.bbmodel") })
    outputs.dir(generatedNames)
}
sourceSets.main { java.srcDir(generatedNames) }
tasks.named("compileKotlin") { dependsOn(generateModelNames) }
tasks.named("compileJava") { dependsOn(generateModelNames) }
tasks.register<JavaExec>("buildBossPack") {
    group = "models"
    description = "Build isolated WSEE geometry, animations, mappings and resource pack from bbmodel sources"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.projects.modellab.BuildBossPackKt"
    args(modelSource.absolutePath, packOutput.get().asFile.absolutePath)
    inputs.files(fileTree(modelSource) { include("*.bbmodel") })
    outputs.dir(packOutput)
}
tasks.register<JavaExec>("modelSmoke") {
    group = "verification"
    dependsOn("buildBossPack")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.projects.modellab.ModelSmokeKt"
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(packOutput.get().asFile.absolutePath)
    timeout = Duration.ofSeconds(60)
}
tasks.named<JavaExec>("run") {
    dependsOn("buildBossPack")
    workingDir = projectDir
    args(packOutput.get().asFile.absolutePath)
}
// Protocol bots stay off the server classpath, and cannot target the normal game port.
val loadtest = sourceSets.create("loadtest")
dependencies { add(loadtest.implementationConfigurationName, "org.geysermc.mcprotocollib:protocol:26.2-SNAPSHOT") }
tasks.register<JavaExec>("loadTestBots") {
    group = "verification"
    classpath = loadtest.runtimeClasspath
    mainClass = "dev.projects.modellab.LoadTestBotsKt"
    args(providers.gradleProperty("modelLabBots").getOrElse("0"),
        providers.gradleProperty("modelLabSeconds").getOrElse("60"))
}
