$taskCache='C:\Users\xgaiz\.gradle\caches\modules-2\files-2.1'
function TaskJar([string]$relative){$found=@(Get-ChildItem -LiteralPath (Join-Path $taskCache $relative) -Recurse -Filter '*.jar');if($found.Count -ne 1){throw "Expected exactly one cached jar: $relative ($($found.Count))"};return $found[0].FullName}
$taskStandard=TaskJar 'org.jetbrains.kotlin\kotlin-stdlib'
$taskCompilerJars=@((TaskJar 'org.jetbrains.kotlin\kotlin-compiler-embeddable'),$taskStandard,(TaskJar 'org.jetbrains.kotlin\kotlin-script-runtime'),(TaskJar 'org.jetbrains.kotlin\kotlin-daemon-embeddable'))
$taskReflect=@(Get-ChildItem -LiteralPath (Join-Path $taskCache 'org.jetbrains.kotlin\kotlin-reflect') -Recurse -Filter '*.jar');if($taskReflect.Count -gt 0){$taskCompilerJars+=$taskReflect[0].FullName}
$taskCoroutines=@(Get-ChildItem -LiteralPath (Join-Path $taskCache 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm') -Recurse -Filter '*.jar');if($taskCoroutines.Count -gt 0){$taskCompilerJars+=$taskCoroutines[0].FullName}
$taskDependencies=@('it.unimi.dsi\fastutil\8.5.19','space.vectrix.flare\flare\2.0.1','space.vectrix.flare\flare-fastutil\2.0.1','org.jctools\jctools-core-jdk11\4.0.6','org.jctools\jctools-core\4.0.6')
$taskRuntimeJars=@();foreach($relative in $taskDependencies){$taskRuntimeJars+=TaskJar $relative}
$taskProjectClasses='C:\Users\xgaiz\Documents\Codex\Projects-V2-worktrees\new-boss-lab\model-lab\build\classes\kotlin\main'
$taskTargetClassPath=(@($taskProjectClasses,$taskStandard,'.\work\ProbeFullMain.jar','.\work\wsee_lib\*')+$taskRuntimeJars)-join ';'
# Kotlin compiler does not expand wildcards in its target classpath.
$taskKotlinTargetClassPath=(@($taskProjectClasses,$taskStandard,'.\work\ProbeFullMain.jar')+@(Get-ChildItem -LiteralPath .\work\wsee_lib -Filter '*.jar' | ForEach-Object {$_.FullName})+$taskRuntimeJars)-join ';'
$taskCompilerClassPath=($taskCompilerJars+@('.\work\wsee_lib\*'))-join ';'
New-Item -ItemType Directory -Path .\work\compiled_direct_first_step_runtime -Force | Out-Null
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' -Xmx512m -cp $taskCompilerClassPath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 25 -classpath $taskKotlinTargetClassPath -module-name dragon_direct_first_step_isolated -d .\work\compiled_direct_first_step_runtime .\outputs\reentry_direct_first_step\runtime_patch\DragonNumericClip.kt .\outputs\reentry_direct_first_step\runtime_patch\DragonActionController.kt
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Set-Content -LiteralPath .\work\direct_first_step_runtime_classpath.txt -Value ('.\work\compiled_direct_first_step_runtime;'+$taskTargetClassPath) -Encoding utf8
Write-Output 'ISOLATED_DISTANCE_WALK_KOTLIN_COMPILED'

