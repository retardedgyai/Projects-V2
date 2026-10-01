param(
    [ValidateRange(1024,65535)][int]$Port=18125,
    [string]$Java='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe'
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$out=Join-Path $repo '.tools/tree-preview'
if($Port -in @(25565,25566)) { throw 'Choose a preview port separate from Minecraft' }
if(!(Test-Path -LiteralPath (Join-Path $out 'classpath.txt'))) { throw 'Run compile-tree-preview.ps1 first' }
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is already in use" }
$cp=(Get-Content -LiteralPath (Join-Path $out 'classpath.txt') -Raw -Encoding utf8).Trim()
$export=Join-Path $out 'export'
$arguments=@('-Xmx256m','-XX:ActiveProcessorCount=2','-cp',('"'+$cp+'"'),
    'dev.projects.server.coreloop.CoreTreePreviewWeb',('"'+$repo+'"'),('"'+$export+'"'),$Port)
$start=@{FilePath=$Java; ArgumentList=$arguments; WindowStyle='Hidden'; WorkingDirectory=$repo; PassThru=$true
    RedirectStandardOutput=(Join-Path $out 'preview.stdout.log'); RedirectStandardError=(Join-Path $out 'preview.stderr.log')}
$process=Start-Process @start
Set-Content -LiteralPath (Join-Path $out 'preview.pid') -Value $process.Id -Encoding ascii
for($attempt=0;$attempt -lt 30;$attempt++) {
    if($process.HasExited) { throw "Preview exited: $(Get-Content -LiteralPath (Join-Path $out 'preview.stderr.log') -Raw)" }
    try {
        $state=Invoke-RestMethod -Uri "http://127.0.0.1:$Port/state" -TimeoutSec 1
        if($state.source -eq 'CoreClassTrees / CoreSkillCatalog.modify') { Write-Output "TREE_PREVIEW_READY http://127.0.0.1:$Port/ pid=$($process.Id) save=none"; return }
    } catch { }
    Start-Sleep -Milliseconds 200
}
throw "Preview readiness timed out; inspect $out/preview.stderr.log"
