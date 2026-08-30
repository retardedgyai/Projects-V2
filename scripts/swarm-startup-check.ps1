[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TargetWorktree,

    [string]$Launcher = "gradlew.bat",

    [string[]]$LauncherArguments = @("--no-daemon", ":server-minestom:run"),

    [ValidateRange(1, 65535)]
    [int]$Port = 25565,

    [ValidateRange(1, 900)]
    [int]$TimeoutSeconds = 120,

    [string]$ReadyPattern = "(?i)(ready|listening on|started)",

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-TcpAccepting {
    param(
        [Parameter(Mandatory = $true)]
        [int]$TargetPort,
        [int]$TimeoutMilliseconds = 400
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync("127.0.0.1", $TargetPort)
        if (-not $connect.Wait($TimeoutMilliseconds)) {
            return $false
        }
        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

$resolvedWorktree = [System.IO.Path]::GetFullPath($TargetWorktree)
if (-not (Test-Path -LiteralPath $resolvedWorktree -PathType Container)) {
    throw "Target worktree does not exist: $resolvedWorktree"
}

$launcherPath = if ([System.IO.Path]::IsPathRooted($Launcher)) {
    [System.IO.Path]::GetFullPath($Launcher)
}
else {
    [System.IO.Path]::GetFullPath((Join-Path $resolvedWorktree $Launcher))
}
if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
    throw "Launcher does not exist: $launcherPath"
}

if (Test-TcpAccepting -TargetPort $Port) {
    throw "Port $Port already accepts TCP before startup; refusing a false-positive probe."
}

$displayArguments = $LauncherArguments -join " "
if ($DryRun) {
    Write-Output "[swarm-startup-check] DRY-RUN"
    Write-Output "worktree=$resolvedWorktree"
    Write-Output "launcher=$launcherPath"
    Write-Output "arguments=$displayArguments"
    Write-Output "port=$Port timeoutSeconds=$TimeoutSeconds"
    Write-Output "readyPattern=$ReadyPattern"
    exit 0
}

$logDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("swarm-startup-check-" + [Guid]::NewGuid().ToString("N"))
[void](New-Item -ItemType Directory -Path $logDirectory)
$stdoutPath = Join-Path $logDirectory "stdout.log"
$stderrPath = Join-Path $logDirectory "stderr.log"
$process = $null
$passed = $false

try {
    $process = Start-Process `
        -FilePath $launcherPath `
        -ArgumentList $LauncherArguments `
        -WorkingDirectory $resolvedWorktree `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru

    Write-Output "[swarm-startup-check] Started PID $($process.Id); logs: $logDirectory"
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $readySeen = $false
    $tcpSeen = $false

    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) {
            $stdout = Get-Content -LiteralPath $stdoutPath -Raw -ErrorAction SilentlyContinue
            $stderr = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
            throw "Launcher PID $($process.Id) exited early with code $($process.ExitCode).`nSTDOUT:`n$stdout`nSTDERR:`n$stderr"
        }

        $combinedLog = @(
            Get-Content -LiteralPath $stdoutPath -Raw -ErrorAction SilentlyContinue
            Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
        ) -join "`n"
        $readySeen = $readySeen -or ($combinedLog -match $ReadyPattern)
        $tcpSeen = $tcpSeen -or (Test-TcpAccepting -TargetPort $Port)

        if ($readySeen -and $tcpSeen) {
            $process.Refresh()
            if (-not $process.HasExited) {
                $passed = $true
                Write-Output "[swarm-startup-check] PASS readyLog=true tcpListen=true processAlive=true PID=$($process.Id)"
                break
            }
        }

        Start-Sleep -Milliseconds 250
    }

    if (-not $passed) {
        throw "Startup timed out after $TimeoutSeconds seconds (readyLog=$readySeen tcpListen=$tcpSeen processAlive=$(-not $process.HasExited)). Logs: $logDirectory"
    }
}
finally {
    if ($null -ne $process) {
        $process.Refresh()
        if (-not $process.HasExited) {
            # PID-scoped by design: never search for or stop java/gradle processes by name.
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(10000) | Out-Null
            Write-Output "[swarm-startup-check] Stopped owned PID $($process.Id)"
        }
    }
}

if (-not $passed) {
    exit 1
}
