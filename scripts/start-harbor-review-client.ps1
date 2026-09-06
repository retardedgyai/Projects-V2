param(
    [Parameter(Mandatory = $true)][string]$SourceArgumentFile,
    [Parameter(Mandatory = $true)][string]$VisibleLauncher,
    [ValidateSet('HarborArrival','HarborMarket','HarborGallery','HarborQuay','HarborShipyard','HarborFoundry','HarborOverview')]
    [string]$View = 'HarborArrival'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
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
$launcherText = [regex]::Replace($launcherText, '(?m)^Set-Content -LiteralPath .*visible-client\.pid.*\r?\n', '')
$reviewLauncher = Join-Path $reviewRoot 'launch-without-focus.ps1'
[IO.File]::WriteAllText($reviewLauncher, $launcherText, [Text.UTF8Encoding]::new($false))
& $reviewLauncher -ArgumentFile $reviewArgs -WorkingDirectory $reviewRoot
