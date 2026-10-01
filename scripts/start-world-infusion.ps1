param(
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Port = 25585,
    [int]$PackPort = 25586,
    [int]$MaxMemoryMb = 512,
    [switch]$ConcurrentInjection,
    [ValidateRange(0.0,1.0)][double]$AuxiliarySupply = 0,
    [switch]$Stop
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot $(if ($ConcurrentInjection) { 'server-minestom/run/world-infusion-confluence' } else { 'server-minestom/run/world-infusion' })
$recordPath = Join-Path $runDirectory 'owned-process.json'
if ($Stop) {
    if (-not (Test-Path -LiteralPath $recordPath)) { Write-Output 'No owned infusion server record'; return }
    $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    if ($record.ProjectRoot -ne $projectRoot) { throw 'Record belongs to a different worktree' }
    $owned = Get-Process -Id $record.ProcessId -ErrorAction SilentlyContinue
    if (-not $owned) { Write-Output 'Owned infusion server is already stopped'; return }
    if ($owned.Path -ne $record.JavaPath -or $owned.StartTime.ToUniversalTime().Ticks -ne ([datetimeoffset]$record.StartTimeUtc).UtcTicks) {
        throw 'PID identity changed; refusing to stop another process'
    }
    Stop-Process -Id $owned.Id
    Write-Output "Stopped owned world-infusion server PID=$($owned.Id)"
    return
}
if (-not $JavaHome) { throw 'Java 25 -JavaHome is required' }
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw "Java missing: $java" }
$libraries = Join-Path $projectRoot 'server-minestom/build/install/server-minestom/lib'
if (-not (Test-Path -LiteralPath (Join-Path $libraries 'server-minestom-0.1.0-SNAPSHOT.jar'))) {
    throw 'Run gradlew.bat :server-minestom:installDist first'
}
if (Test-Path -LiteralPath $recordPath) {
    $existing = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    $running = Get-Process -Id $existing.ProcessId -ErrorAction SilentlyContinue
    if ($running -and $running.StartTime.ToUniversalTime().Ticks -eq ([datetimeoffset]$existing.StartTimeUtc).UtcTicks) {
        throw 'This isolated server is already running. Use -Stop or its recorded address.'
    }
}
[void](New-Item -ItemType Directory -Path $runDirectory -Force)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout = Join-Path $runDirectory "$stamp.log"
$stderr = Join-Path $runDirectory "$stamp.error.log"
$arguments = @("-Xmx${MaxMemoryMb}m", '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8',
    '-Dprojects.worldInfusion=true', "-Dprojects.port=$Port", "-Dprojects.ui.port=$PackPort",
    '-cp', "`"$libraries\*`"", 'dev.projects.server.ProjectSServerKt')
if ($ConcurrentInjection) {
    $supplyText = $AuxiliarySupply.ToString([System.Globalization.CultureInfo]::InvariantCulture)
    $arguments = @('-Dprojects.infusion.concurrent=true', "-Dprojects.infusion.energy=$supplyText") + $arguments
}
$serverProcess = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $runDirectory `
    -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$record = [pscustomobject]@{ ProcessId=$serverProcess.Id; StartTimeUtc=$serverProcess.StartTime.ToUniversalTime().ToString('o');
    JavaPath=$serverProcess.Path; ProjectRoot=$projectRoot; Address="127.0.0.1:$Port"; Log=$stdout; ErrorLog=$stderr;
    SaveDirectory=(Join-Path $runDirectory 'config/projects/world-infusion-lab') }
$record | ConvertTo-Json | Set-Content -LiteralPath $recordPath -Encoding UTF8
for ($attempt=0; $attempt -lt 60; $attempt++) {
    if ($serverProcess.HasExited) { throw "Infusion server exited. $stderr" }
    if ((Test-Path -LiteralPath $stdout) -and (Select-String -LiteralPath $stdout -SimpleMatch 'PROJECTS_WORLD_INFUSION_READY' -Quiet)) {
        $record; return
    }
    Start-Sleep -Milliseconds 500
}
throw "Startup pending. Only owned PID=$($serverProcess.Id). Inspect $stderr / $stdout"
