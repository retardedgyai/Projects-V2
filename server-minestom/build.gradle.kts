plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
}

repositories {
    // WorldSeed Entity Engine (Apache-2.0, https://github.com/AtlasEngineCa/WorldSeedEntityEngine) is only
    // published on its author's Reposilite. Restrict that repository to the one group so nothing else resolves there.
    exclusiveContent {
        forRepository { maven("https://reposilite.atlasengine.ca/public") { name = "AtlasEngine" } }
        filter { includeGroup("net.worldseed.multipart") }
    }
}

dependencies {
    implementation(project(":web-ui-lab"))
    // D12: Blockbench bosses are displayed and animated with WSEE (built against Minestom 2026.07.22-26.2).
    implementation("net.worldseed.multipart:WorldSeedEntityEngine:13.0.4")
    implementation("net.minestom:minestom:${property("minestom_version")}")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Build-time part of core-ui-pack (see docs/assets/core-ui-pack.md). These paths are
// written by scripts/gen_resource_pack.py and ignored by git; keep .gitignore in sync.
val corePack = layout.projectDirectory.dir("src/main/resources/core-ui-pack")
val generatedPackDirs = listOf(
    "assets/minecraft",
    "assets/projects/font",
    "assets/projects/menu",
    "assets/projects/items/combat_vfx",
    "assets/projects/items/core_ui",
    "assets/projects/items/forge_materials",
    "assets/projects/models/combat_vfx",
    "assets/projects/models/core_ui",
    "assets/projects/models/item/forge_materials",
    "assets/projects/textures/combat_vfx",
    "assets/projects/textures/gui/core",
    "assets/projects/textures/gui/skills",
    "assets/projects/textures/item/core_ui",
    "assets/projects/textures/item/forge_materials",
)
val generatedPackFiles = listOf("index.txt", "pack.mcmeta")

// Blockbench bosses (docs/development/boss-model-pipeline.md): assets/bosses/*.bbmodel -> WSEE bone models in
// the private worldseed namespace + server-side geometry/animation data. Both outputs are ignored by git.
val bossModelSources = rootProject.layout.projectDirectory.dir("assets/bosses")
val bossPackDir = corePack.dir("assets/worldseed")
val bossDataDir = layout.projectDirectory.dir("src/main/resources/boss-models")
val generateBossModels by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Converts assets/bosses/*.bbmodel with WSEE into pack models and server model data"
    // Compiled classes + libraries only: the full runtime classpath would include processResources (a cycle).
    classpath = files(sourceSets.main.get().output.classesDirs) + configurations.runtimeClasspath.get()
    mainClass = "dev.projects.server.mob.BossModelPack"
    args(bossModelSources.asFile.path, corePack.asFile.path, bossDataDir.asFile.path)
    inputs.files(fileTree(bossModelSources) { include("*.bbmodel") })
        .withPropertyName("bbmodels").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(bossPackDir)
    outputs.dir(bossDataDir)
}

val generateResourcePack by tasks.registering(Exec::class) {
    group = "build"
    description = "Regenerates the generated core-ui-pack assets with scripts/gen_resource_pack.py"
    val repo = rootProject.layout.projectDirectory
    workingDir = repo.asFile
    executable = providers.gradleProperty("pythonExe").getOrElse("python")
    args("scripts/gen_resource_pack.py")

    inputs.files(fileTree(repo.dir("scripts")) { include("*.py") })
        .withPropertyName("scripts").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree(repo.dir("assets")) {
        include("core-ui/**", "combat-vfx/**", "class-armaments/texture-first/sources/**")
    }).withPropertyName("artSources").withPathSensitivity(PathSensitivity.RELATIVE)
    // The menu glyph atlas covers every CJK character used in */src/main/kotlin.
    inputs.files(rootDir.listFiles().orEmpty().map { it.resolve("src/main/kotlin") }.filter { it.isDirectory }
        .map { fileTree(it) { include("**/*.kt") } })
        .withPropertyName("kotlinCharset").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("src/main/resources/combat-art/skill-scenes.psv").withPathSensitivity(PathSensitivity.RELATIVE)
    // Committed pack files are listed in index.txt.
    inputs.files(fileTree(corePack) {
        exclude(generatedPackFiles)
        exclude(generatedPackDirs.map { "$it/**" })
        exclude("assets/worldseed/**")
    }).withPropertyName("committedPack").withPathSensitivity(PathSensitivity.RELATIVE)

    // index.txt lists the boss models too, so the pack index is rebuilt after them.
    dependsOn(generateBossModels)
    inputs.files(generateBossModels.map { it.outputs.files }).withPropertyName("bossModels")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dirs(generatedPackDirs.map { corePack.dir(it) })
    outputs.files(generatedPackFiles.map { corePack.file(it) })
}

tasks.processResources {
    dependsOn(generateResourcePack)
}

application {
    mainClass = "dev.projects.server.ProjectSServerKt"
}

tasks.named<JavaExec>("run") {
    workingDir = file("run")
    systemProperty("projects.devCommands", "true")
    doFirst { workingDir.mkdirs() }
}
