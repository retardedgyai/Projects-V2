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
function Invoke-Intercepted([bool]$Review, [string]$ExpectedError, [bool]$Material = $false) {
    $taskLaunchState.Arguments = @()
    try {
        & $taskLauncher -JavaHome $JavaHome -WeaponArtReview:$Review -WeaponMaterialReview:$Material
        throw 'Expected launcher interception/guard'
    } catch {
        Check ($_.Exception.Message.Contains($ExpectedError)) "Unexpected launcher error: $($_.Exception.Message)"
    }
}

Invoke-Intercepted $false 'TEST_LAUNCH_INTERCEPTED'
Check (($taskLaunchState.Arguments -join ' ') -notmatch '(weapon|material)-playtest-resources') 'Default launch changed its resource source'
Invoke-Intercepted $true 'TEST_LAUNCH_INTERCEPTED'
$taskArgumentString = $taskLaunchState.Arguments -join ' '
Check ($taskArgumentString -match 'weapon-playtest-resources;') 'Review root must precede the installed jars'
Check ($taskLaunchState.Calls -eq 2) 'Both valid cases should reach the intercepted launcher'
Invoke-Intercepted $false 'TEST_LAUNCH_INTERCEPTED' $true
Check (($taskLaunchState.Arguments -join ' ') -match 'material-playtest-resources;') 'Material root must precede installed jars'
Check (($taskLaunchState.Arguments -join ' ') -notmatch 'weapon-playtest-resources') 'Material review must not enable all weapon replacements'
Check ($taskLaunchState.Calls -eq 3) 'Material mode should reach the intercepted launcher'
$taskLaunchState.Mode = 'stale'
Invoke-Intercepted $true '古い武器確認パック'
Invoke-Intercepted $false '古い武器確認パック' $true
Check ($taskLaunchState.Calls -eq 3) 'Stale resources must not launch'
$taskLaunchState.Mode = 'busy'
Invoke-Intercepted $true '使用中'
Invoke-Intercepted $false '使用中' $true
Check ($taskLaunchState.Calls -eq 3) 'Busy server must never be replaced or stopped'
$taskLaunchState.Mode = 'normal'
Invoke-Intercepted $true '同時に選べません' $true
Check ($taskLaunchState.Calls -eq 3) 'Conflicting review modes must not launch'
'8 launch checks passed; Start-Process was mocked; no process was started.'
