# Launch argument/guard checks. Start-Process is intercepted; no server is started.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$taskLaunchState = @{ Mode = 'normal'; Arguments = @(); Calls = 0 }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskLauncher = Join-Path $PSScriptRoot 'start-core-loop.ps1'

function Get-NetTCPConnection {
    param($LocalPort, $State, $ErrorAction)
    if ($taskLaunchState.Mode -eq 'busy') { [pscustomobject]@{ LocalPort = $LocalPort } }
}
function Get-FileHash {
    param($LiteralPath, $Algorithm)
    if ($taskLaunchState.Mode -eq 'stale') { [pscustomobject]@{ Hash = '0' * 64 } }
    else { Microsoft.PowerShell.Utility\Get-FileHash -LiteralPath $LiteralPath -Algorithm $Algorithm }
}
function Start-Process {
    param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle, $RedirectStandardOutput, $RedirectStandardError, [switch]$PassThru)
    $taskLaunchState.Arguments = $ArgumentList
    $taskLaunchState.Calls++
    throw 'TEST_LAUNCH_INTERCEPTED'
}
function Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Invoke-Intercepted([bool]$Review, [string]$ExpectedError) {
    $taskLaunchState.Arguments = @()
    try {
        & $taskLauncher -JavaHome $JavaHome -WeaponArtReview:$Review
        throw 'Expected launcher interception/guard'
    } catch {
        Check ($_.Exception.Message.Contains($ExpectedError)) "Unexpected launcher error: $($_.Exception.Message)"
    }
}

Invoke-Intercepted $false 'TEST_LAUNCH_INTERCEPTED'
Check (($taskLaunchState.Arguments -join ' ') -notmatch 'weapon-playtest-resources') 'Default launch changed its resource source'
Invoke-Intercepted $true 'TEST_LAUNCH_INTERCEPTED'
$taskArgumentString = $taskLaunchState.Arguments -join ' '
Check ($taskArgumentString -match 'weapon-playtest-resources;') 'Review root must precede the installed jars'
Check ($taskLaunchState.Calls -eq 2) 'Both valid cases should reach the intercepted launcher'
$taskLaunchState.Mode = 'stale'
Invoke-Intercepted $true '古い武器確認パック'
Check ($taskLaunchState.Calls -eq 2) 'Stale resources must not launch'
$taskLaunchState.Mode = 'busy'
Invoke-Intercepted $true '使用中'
Check ($taskLaunchState.Calls -eq 2) 'Busy server must never be replaced or stopped'
'4 launch checks passed; Start-Process was mocked; no process was started.'
