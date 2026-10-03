$ErrorActionPreference='Stop'
$fixRoot=$PSScriptRoot
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
$compilerCp=$compilerJars -join ';'
$targetCp=@(Get-ChildItem -LiteralPath $libRoot -Filter '*.jar' | ForEach-Object FullName) -join ';'
$originalJar=Join-Path $libRoot 'server-minestom-0.1.0-SNAPSHOT.jar'
$overlay=Join-Path $fixRoot 'classes'
$regression=Join-Path $fixRoot 'regression-classes'
[void](New-Item -ItemType Directory -Path $overlay,$regression -Force)
& $java -Xmx512m -cp $compilerCp org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 25 -classpath $targetCp "-Xfriend-paths=$originalJar" -module-name dev_projects_server_minestom -d $overlay (Join-Path $fixRoot 'WorldInfusionServer.kt')
if($LASTEXITCODE -ne 0){throw 'Override compilation failed'}
& $java -Xmx512m -cp $compilerCp org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 25 -classpath "$overlay;$targetCp" "-Xfriend-paths=$originalJar,$overlay" -module-name dev_projects_server_minestom -d $regression (Join-Path $fixRoot 'SpawnRegression.kt')
if($LASTEXITCODE -ne 0){throw 'Regression compilation failed'}
& $java -Xmx256m -XX:ActiveProcessorCount=2 -cp "$regression;$libRoot\*" dev.projects.server.coreloop.SpawnRegressionKt (Join-Path $fixRoot 'baseline-test-save') *> (Join-Path $fixRoot 'baseline-regression.log')
if($LASTEXITCODE -eq 0){throw 'Expected pre-fix reproduction to fail'}
& $java -Xmx256m -XX:ActiveProcessorCount=2 -cp "$regression;$overlay;$libRoot\*" dev.projects.server.coreloop.SpawnRegressionKt (Join-Path $fixRoot 'fixed-test-save') *> (Join-Path $fixRoot 'fixed-regression.log')
if($LASTEXITCODE -ne 0){Get-Content (Join-Path $fixRoot 'fixed-regression.log');throw 'Fixed regression failed'}
Get-Content (Join-Path $fixRoot 'fixed-regression.log')
