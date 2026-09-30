param(
    [string]$JavaDirectory = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin',
    [string]$Python = "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe",
    [string]$VanillaArguments = '',
    [string]$CaptureDependencies = '',
    [string]$WindowTitle = '',
    [switch]$CaptureOnly,
    [switch]$StopSession
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$archive = Split-Path $repo -Parent
$session = Join-Path $repo '.tools/native-engine-preview'
$client = Join-Path $session 'client'
$live = Join-Path $session 'live'
$utf8 = [System.Text.UTF8Encoding]::new($false)
if (!$CaptureDependencies) { $CaptureDependencies = Join-Path $archive 'pydeps/native-preview' }
if (!$VanillaArguments) { $VanillaArguments = Join-Path $archive 'client-26.2-polish05/direct-vanilla.args' }
New-Item -ItemType Directory -Force -Path $session,$client,$live,(Join-Path $client 'resourcepacks'),(Join-Path $client 'screenshots') | Out-Null
function Quoted([string]$value) { return '"' + $value.Replace('"','\"') + '"' }
function Forward([string]$value) { return $value.Replace('\','/') }

if ($StopSession) {
    foreach ($name in @('capture','client','host','lab')) {
        $pidFile = Join-Path $session ($name+'.pid')
        if (!(Test-Path -LiteralPath $pidFile)) { continue }
        $ownedId = [int](Get-Content -LiteralPath $pidFile)
        $owned = Get-CimInstance Win32_Process -Filter "ProcessId=$ownedId"
        if (!$owned) { continue }
        $expected = if ($name -eq 'capture') { Join-Path $PSScriptRoot 'capture_native_window.py' } else { Join-Path $session ((@{client='vanilla';host='host';lab='lab'}[$name])+'.args') }
        if (!$owned.CommandLine.Contains($expected)) { throw "PID $ownedId no longer belongs to this preview; it was not stopped." }
        Stop-Process -Id $ownedId
    }
    [System.IO.File]::WriteAllText((Join-Path $live 'capture-status.json'),'{"state":"stopped"}',$utf8)
    Write-Output 'NATIVE_PREVIEW_STOPPED'
    exit
}

if ($CaptureOnly) {
    if (!$WindowTitle.StartsWith('Minecraft')) { throw 'Provide the exact selected Minecraft window title.' }
    $oldWorkerFile = Join-Path $session 'capture.pid'
    if (Test-Path -LiteralPath $oldWorkerFile) {
        $oldWorker = Get-Process -Id ([int](Get-Content -LiteralPath $oldWorkerFile)) -ErrorAction SilentlyContinue
        if ($oldWorker) { throw 'A capture worker is already running; stop this preview session first.' }
    }
    $stopFile = Join-Path $live 'stop'
    if (Test-Path -LiteralPath $stopFile) { Remove-Item -LiteralPath $stopFile }
    $env:PYTHONPATH = $CaptureDependencies
    $worker = Start-Process -FilePath $Python -ArgumentList @(
        (Quoted (Join-Path $PSScriptRoot 'capture_native_window.py')), '--window', (Quoted $WindowTitle), '--output', (Quoted $live)
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $session 'capture.out.log') -RedirectStandardError (Join-Path $session 'capture.err.log')
    Set-Content -LiteralPath (Join-Path $session 'capture.pid') -Value $worker.Id
    Write-Output "NATIVE_CAPTURE_STARTED pid=$($worker.Id)"
    exit
}

foreach ($port in @(25623,18100,18101)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Preview port $port is already in use; existing processes were not stopped." }
}
$library = Join-Path $repo 'web-ui-lab/build/install/web-ui-lab/lib'
if (!(Test-Path $library)) { throw 'Build web-ui-lab:installDist before starting the preview.' }
$pack = Join-Path $PSScriptRoot 'shared-preview/Forge_Calibration_26_2.zip'
if (!(Test-Path $pack)) { throw 'Generate the shared calibration resource pack first.' }
Copy-Item -LiteralPath $pack -Destination (Join-Path $client 'resourcepacks/Forge_Calibration_26_2.zip')
# A dedicated Vanilla game directory keeps existing clients, packs and saves intact.
$options = Get-Content -LiteralPath (Join-Path (Split-Path $VanillaArguments -Parent) 'options.txt')
$options = $options | Where-Object { $_ -notmatch '^(fullscreen|resourcePacks|incompatibleResourcePacks|pauseOnLostFocus):' }
# Windowed preview continues rendering when the browser becomes foreground.
$options += @('fullscreen:false','pauseOnLostFocus:false','resourcePacks:["file/Forge_Calibration_26_2.zip"]','incompatibleResourcePacks:[]')
[System.IO.File]::WriteAllLines((Join-Path $client 'options.txt'),[string[]]$options,$utf8)
$arguments = [string[]](Get-Content -LiteralPath $VanillaArguments)
foreach ($replacement in @(@('--gameDir',(Quoted (Forward $client))),@('--width','1920'),@('--height','1080'),@('--quickPlayMultiplayer','127.0.0.1:25623'))) {
    $index = [Array]::IndexOf($arguments,$replacement[0]); if ($index -lt 0) { throw "Missing Vanilla argument: $($replacement[0])" }
    $arguments[$index+1] = $replacement[1]
}
[System.IO.File]::WriteAllLines((Join-Path $session 'vanilla.args'),$arguments,$utf8)
$classPath = Forward (Join-Path $library '*')
$serverArguments = @('-Xmx1G','-Dprojects.ui.port=25623','-Dprojects.ui.previewPort=18101','-Dprojects.ui.openOnJoin=true',
    ('-Dprojects.ui.rasterManifest='+(Forward (Join-Path $PSScriptRoot 'shared-preview/manifest.json'))),'-Dprojects.ui.rasterFrame=weapon',
    '-cp',(Quoted $classPath),'dev.projects.webui.WebUiLabKt','ui/forge.html')
[System.IO.File]::WriteAllLines((Join-Path $session 'lab.args'),[string[]]$serverArguments,$utf8)
$hostArguments = @('-Xmx256M','-cp',(Quoted $classPath),'dev.projects.webui.NativeEnginePreviewKt',
    (Quoted (Forward $live)),(Quoted (Forward (Join-Path $client 'screenshots'))),
    (Quoted (Forward (Join-Path $PSScriptRoot 'native-preview/index.html'))),'18100')
[System.IO.File]::WriteAllLines((Join-Path $session 'host.args'),[string[]]$hostArguments,$utf8)
foreach ($process in @(@('lab','java.exe',(Join-Path $repo 'web-ui-lab')),@('host','java.exe',$repo))) {
    $name=$process[0]
    $started=Start-Process -FilePath (Join-Path $JavaDirectory $process[1]) -ArgumentList ('@'+(Quoted (Join-Path $session ($name+'.args')))) -WorkingDirectory $process[2] -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $session ($name+'.out.log')) -RedirectStandardError (Join-Path $session ($name+'.err.log'))
    Set-Content -LiteralPath (Join-Path $session ($name+'.pid')) -Value $started.Id
}
$ready=$false
for ($attempt=0; $attempt -lt 60; $attempt++) {
    if ((Get-Content -LiteralPath (Join-Path $session 'lab.out.log') -Raw -ErrorAction SilentlyContinue) -match 'UI_LAB_READY') { $ready=$true; break }
    Start-Sleep -Milliseconds 250
}
if (!$ready) { throw 'The isolated lab did not become ready; inspect its local logs.' }
$started=Start-Process -FilePath (Join-Path $JavaDirectory 'javaw.exe') -ArgumentList ('@'+(Quoted (Join-Path $session 'vanilla.args'))) -WorkingDirectory $client -WindowStyle Hidden -PassThru
Set-Content -LiteralPath (Join-Path $session 'client.pid') -Value $started.Id
Write-Output "NATIVE_PREVIEW_STARTED http://127.0.0.1:18100/ minecraft=127.0.0.1:25623 clientPid=$($started.Id)"
