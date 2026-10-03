param([string]$SourceName='InfusionParticleRoleStudy.kt',[string]$MainClass='InfusionParticleRoleStudyKt',[string]$OutputName='role-trace.json')
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$study=Join-Path $repo '.tools\infusion-particle-role-study'
$classes=Join-Path $study 'classes'
$cacheRoot='C:\Users\xgaiz\.gradle\caches\modules-2\files-2.1'
$libRoot='C:\Users\xgaiz\Documents\Codex\2026-09-30\task\world-infusion-altar\server-minestom\build\install\server-minestom\lib'
$java='C:\Users\xgaiz\Documents\Codex\minecraft-runtime\temurin-25\jdk-25.0.4.1+1\bin\java.exe'
function CachedJar([string]$relative) {
    $found=@(Get-ChildItem -LiteralPath (Join-Path $cacheRoot $relative) -Recurse -Filter '*.jar')
    if($found.Count -eq 0){throw "Missing cached compiler dependency $relative"}
    return $found[0].FullName
}
$compilerJars=@(
    (CachedJar 'org.jetbrains.kotlin\kotlin-compiler-embeddable'),
    (CachedJar 'org.jetbrains.kotlin\kotlin-stdlib'),
    (CachedJar 'org.jetbrains.kotlin\kotlin-script-runtime'),
    (CachedJar 'org.jetbrains.kotlin\kotlin-daemon-embeddable'),
    (CachedJar 'org.jetbrains.kotlin\kotlin-reflect'),
    (CachedJar 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm'),
    (CachedJar 'org.jetbrains\annotations')
)
$targetCp=@(Get-ChildItem -LiteralPath $libRoot -Filter '*.jar' | ForEach-Object FullName) -join ';'
$originalJar=Join-Path $libRoot 'server-minestom-0.1.0-SNAPSHOT.jar'
[void](New-Item -ItemType Directory -Path $classes -Force)
& $java -XX:ActiveProcessorCount=2 -Xmx512m -cp ($compilerJars -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 25 -classpath $targetCp "-Xfriend-paths=$originalJar" -module-name dev_projects_server_minestom -d $classes (Join-Path $PSScriptRoot $SourceName)
if($LASTEXITCODE -ne 0){throw 'Study exporter compilation failed'}
& $java -XX:ActiveProcessorCount=2 -Xmx256m -cp "$classes;$libRoot\*" "dev.projects.server.coreloop.$MainClass" (Join-Path $study $OutputName)
if($LASTEXITCODE -ne 0){throw 'Study export invariants failed'}
