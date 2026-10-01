param(
    [Parameter(Mandatory=$true)][string]$LibraryDirectory,
    [string]$CompilerCache=(Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'),
    [string]$Java='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe',
    [switch]$ChecksOnly
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$out=Join-Path $repo '.tools/tree-preview'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$compilerModules=@('org.jetbrains.kotlin/kotlin-compiler-embeddable/2.4.10','org.jetbrains.kotlin/kotlin-stdlib/2.4.10',
    'org.jetbrains.kotlin/kotlin-script-runtime','org.jetbrains.kotlin/kotlin-reflect','org.jetbrains.kotlin/kotlin-daemon-embeddable',
    'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm','org.jetbrains/annotations')
$compiler=@($compilerModules | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $CompilerCache $_) -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue } | ForEach-Object FullName)
$libs=@(Get-ChildItem -LiteralPath $LibraryDirectory -Filter '*.jar' | Where-Object { $_.Name -notlike 'server-minestom-*' -and $_.Name -notlike 'web-ui-lab-*' } | ForEach-Object FullName)
if($compiler.Count -lt 2 -or $libs.Count -lt 10) { throw '既存のKotlin compilerと本編依存jarが必要です。ダウンロードやinstallは行いません。' }
function Compile-Source([string]$name,[string[]]$sources,[string]$cp,[string]$friends='') {
    if($name -notin @('web','server','checks')) { throw 'Unexpected class output directory' }
    $classes=[System.IO.Path]::GetFullPath((Join-Path $out $name))
    $boundary=[System.IO.Path]::GetFullPath($out)+[System.IO.Path]::DirectorySeparatorChar
    if(!$classes.StartsWith($boundary,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Class output is outside the preview workspace' }
    if(Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $arguments=@('-no-stdlib','-no-reflect','-jvm-target','25','-classpath',$cp,'-d',$classes)
    if($friends){$arguments+=('-Xfriend-paths='+$friends)}
    $arguments+=$sources
    $response=Join-Path $out "$name.args"
    $arguments | ForEach-Object { '"'+($_ -replace '\\','/')+'"' } | Set-Content -LiteralPath $response -Encoding utf8
    & $Java '-Xmx1200m' '-XX:ActiveProcessorCount=2' '-cp' ($compiler -join ';') 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler' "@$response"
    if($LASTEXITCODE -ne 0){throw "$name compile failed"}
    return $classes
}
$web=Join-Path $out 'web'
$server=Join-Path $out 'server'
if($ChecksOnly) {
    if(!(Test-Path -LiteralPath $web) -or !(Test-Path -LiteralPath $server)) { throw 'Compile production sources before using -ChecksOnly' }
} else {
    $webSources=@(Get-ChildItem -LiteralPath (Join-Path $repo 'web-ui-lab/src/main/kotlin'),(Join-Path $repo 'assets/ui/polish05-import/native/src/main/kotlin') -Recurse -Filter '*.kt' | ForEach-Object FullName)
    $web=Compile-Source 'web' $webSources ($libs -join ';')
    $serverSources=@(Get-ChildItem -LiteralPath (Join-Path $repo 'server-minestom/src/main/kotlin') -Recurse -Filter '*.kt' | ForEach-Object FullName)
    $server=Compile-Source 'server' $serverSources ((@($web)+$libs) -join ';')
}
$checks=Compile-Source 'checks' @((Join-Path $repo 'server-minestom/src/test/kotlin/dev/projects/server/coreloop/CoreTreePreviewChecks.kt')) ((@($server,$web)+$libs) -join ';') "$server,$web"
$cp=(@($checks,$server,$web,(Join-Path $repo 'server-minestom/src/main/resources'),(Join-Path $repo 'web-ui-lab/src/main/resources'))+$libs) -join ';'
Set-Content -LiteralPath (Join-Path $out 'classpath.txt') -Value $cp -Encoding utf8 -NoNewline
& $Java '-Xmx512m' '-XX:ActiveProcessorCount=2' '-cp' $cp 'dev.projects.server.coreloop.CoreTreePreviewChecks' $repo
if($LASTEXITCODE -ne 0){throw 'Tree preview checks failed'}
Write-Output "TREE_PREVIEW_COMPILE_PASS classes=$out"
