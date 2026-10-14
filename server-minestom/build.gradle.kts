plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":web-ui-lab"))
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
    }).withPropertyName("committedPack").withPathSensitivity(PathSensitivity.RELATIVE)

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
