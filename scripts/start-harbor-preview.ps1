param([string]$JavaHome = $env:JAVA_HOME, [int]$Port = 25575)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Port -eq 25565) { throw 'Do not use the gameplay port for architectural review' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port already in use" }
$classes = Join-Path $projectRoot 'server-minestom/build/classes/kotlin/main'
$resources = Join-Path $projectRoot 'server-minestom/build/resources/main'
$libs = Join-Path $projectRoot 'server-minestom/build/install/server-minestom/lib/*'
if (-not (Test-Path -LiteralPath (Join-Path $classes 'dev/projects/server/coreloop/HarborPreviewServer.class'))) { throw 'Run :server-minestom:classes first' }
$reviewRoot = Join-Path $projectRoot '.tools/harbor-review-server'
[void](New-Item -ItemType Directory -Path $reviewRoot -Force)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = Join-Path $reviewRoot "preview-$stamp.log"
$errorLog = Join-Path $reviewRoot "preview-$stamp.error.log"
$arguments = @('-Xmx1536m','-XX:ActiveProcessorCount=3','-Dfile.encoding=UTF-8',"-Dprojects.harbor.preview.port=$Port",'-cp',"`"$classes;$resources;$libs`"",'dev.projects.server.coreloop.HarborPreviewServer')
$previewProcess = Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') -ArgumentList $arguments -WorkingDirectory $reviewRoot -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errorLog -PassThru
for ($i = 0; $i -lt 60; $i++) {
    if ($previewProcess.HasExited) { throw "Preview exited: $errorLog" }
    if (Select-String -LiteralPath $log -SimpleMatch 'HARBOR_PREVIEW_READY' -Quiet) {
        return [pscustomobject]@{ ProcessId = $previewProcess.Id; Log = $log; ErrorLog = $errorLog }
    }
    Start-Sleep -Milliseconds 500
}
throw "Preview not ready yet; inspect PID $($previewProcess.Id), $log, $errorLog before relaunching"
