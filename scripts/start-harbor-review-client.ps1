param(
    [Parameter(Mandatory = $true)][string]$SourceArgumentFile,
    [Parameter(Mandatory = $true)][string]$VisibleLauncher,
    [ValidateSet('HarborArrival','HarborMarket','HarborGallery','HarborQuay','HarborShipyard','HarborFoundry','HarborAcademy','HarborHall','HarborOutlook','HarborRear','HarborAlley','HarborArcade','HarborCanopy','HarborBerth','HarborBoarding','HarborCutter','HarborOverview')]
    [string]$View = 'HarborArrival'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$placementScript = Join-Path $PSScriptRoot 'place-harbor-preview.ps1'
$monitorRows = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $placementScript -ListMonitors
$secondaryRows = @($monitorRows | Where-Object { $_ -match '^primary=False ' })
if ($LASTEXITCODE -ne 0 -or $secondaryRows.Count -ne 1) { throw 'Exactly one secondary monitor is required for harbor photography; primary-monitor fallback is disabled' }
if ($secondaryRows[0] -notmatch 'work=(-?\d+),(-?\d+),(-?\d+),(-?\d+)') { throw 'Cannot read secondary monitor bounds' }
$screenLeft=[int]$Matches[1]; $screenTop=[int]$Matches[2]
$screenWidth=[int]$Matches[3]-$screenLeft; $screenHeight=[int]$Matches[4]-$screenTop
$windowWidth=[Math]::Min(1600,$screenWidth-32); $windowHeight=[Math]::Min(939,$screenHeight-32)
$windowX=$screenLeft+[int](($screenWidth-$windowWidth)/2); $windowY=$screenTop+[int](($screenHeight-$windowHeight)/2)
$reviewRoot = Join-Path $projectRoot '.tools/harbor-review-client'
[void](New-Item -ItemType Directory -Path $reviewRoot -Force)
$launchText = [IO.File]::ReadAllText($SourceArgumentFile)
# Retain the known-working Vanilla runtime/classpath/assets. Isolate options, identity and server.
$launchText = [regex]::Replace($launchText, '(?m)(--username\s*\r?\n)[^\r\n]+', "`${1}$View")
$launchText = [regex]::Replace($launchText, '(?m)(--gameDir\s*\r?\n)[^\r\n]+', "`${1}`"$($reviewRoot.Replace('\','/'))`"")
$launchText = [regex]::Replace($launchText, '(?m)(--quickPlayMultiplayer\s*\r?\n)[^\r\n]+', '${1}127.0.0.1:25575')
if ($launchText -notmatch [regex]::Escape($View) -or $launchText -notmatch '127\.0\.0\.1:25575') { throw 'Unexpected source argument layout' }
$reviewArgs = Join-Path $reviewRoot 'vanilla-review.args'
[IO.File]::WriteAllText($reviewArgs, $launchText, [Text.UTF8Encoding]::new($false))
# No modified client code or pack. Consistent exposure, FOV, native textures, and an unobstructed view.
$options = @('version:4903','fov:0.0','graphicsPreset:"custom"','renderDistance:12','simulationDistance:5','maxFps:45',
    'enableVsync:false','guiScale:2','lang:ja_jp','renderClouds:"false"','entityShadows:true',
    'bobView:false','pauseOnLostFocus:false','soundCategory_master:0.0','tutorialStep:none','onboardAccessibility:false')
[IO.File]::WriteAllLines((Join-Path $reviewRoot 'options.txt'), $options, [Text.UTF8Encoding]::new($false))
$env:JAVA_TOOL_OPTIONS = $null
# Use the inspected desktop launcher without stealing keyboard focus from the Creator.
# This generated local copy changes only the Win32 show mode and removes its shared PID-file write.
$launcherText = [IO.File]::ReadAllText($VisibleLauncher)
if (-not $launcherText.Contains('startup.wShowWindow = 1;')) { throw 'Expected desktop launcher show-mode contract is missing' }
$launcherText = $launcherText.Replace('startup.wShowWindow = 1;', 'startup.wShowWindow = 4;')
# Hint the initial native placement; verify/reapply after GLFW creates its visible window.
if (-not $launcherText.Contains('startup.dwFlags = 0x00000001;')) { throw 'Expected startup placement contract is missing' }
$launcherText = $launcherText.Replace('startup.dwFlags = 0x00000001;', "startup.dwFlags = 0x00000007; startup.dwX = $windowX; startup.dwY = $windowY; startup.dwXSize = $windowWidth; startup.dwYSize = $windowHeight;")
$launcherText = [regex]::Replace($launcherText, '(?m)^Set-Content -LiteralPath .*visible-client\.pid.*\r?\n', '')
$reviewLauncher = Join-Path $reviewRoot 'launch-without-focus.ps1'
[IO.File]::WriteAllText($reviewLauncher, $launcherText, [Text.UTF8Encoding]::new($false))
$reviewClientId = & $reviewLauncher -ArgumentFile $reviewArgs -WorkingDirectory $reviewRoot
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $placementScript -PreviewPid $reviewClientId
if ($LASTEXITCODE -ne 0) {
    $client = Get-CimInstance Win32_Process -Filter "ProcessId=$reviewClientId"
    if ($client.CommandLine -match 'harbor-review-client') { Stop-Process -Id $reviewClientId }
    throw 'Secondary monitor placement failed; stopped the isolated review client'
}
$reviewClientId
