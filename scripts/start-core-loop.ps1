param(
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Port = 25565,
    [int]$MaxMemoryMb = 3072
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Java 25 の JAVA_HOME または -JavaHome を指定してください。' }
$java = Join-Path $JavaHome 'bin\java.exe'
$libraries = Join-Path $projectRoot 'server-minestom\build\install\server-minestom\lib'
if (-not (Test-Path -LiteralPath $java)) { throw "Java が見つかりません: $java" }
if (-not (Test-Path -LiteralPath (Join-Path $libraries 'server-minestom-0.1.0-SNAPSHOT.jar'))) {
    throw '先に .\gradlew.bat :server-minestom:installDist を実行してください。'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "ポート $Port は使用中です。既存サーバーを確認してから停止してください。"
}
$runDirectory = Join-Path $projectRoot 'server-minestom\run'
[void](New-Item -ItemType Directory -Path $runDirectory -Force)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout = Join-Path $runDirectory "core-loop-$stamp.log"
$stderr = Join-Path $runDirectory "core-loop-$stamp.error.log"
$arguments = @("-Xmx${MaxMemoryMb}m", '-XX:ActiveProcessorCount=4', '-Dfile.encoding=UTF-8',
    "-Dprojects.port=$Port", '-cp', "`"$libraries\*`"", 'dev.projects.server.ProjectSServerKt')
$serverProcess = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $runDirectory `
    -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($serverProcess.HasExited) { throw "サーバーが終了しました。ログ: $stderr" }
    if ((Test-Path -LiteralPath $stdout) -and
        (Select-String -LiteralPath $stdout -SimpleMatch 'PROJECTS_CORE_READY' -Quiet)) {
        [pscustomobject]@{ ProcessId = $serverProcess.Id; Address = "127.0.0.1:$Port"; Log = $stdout; ErrorLog = $stderr }
        return
    }
    Start-Sleep -Milliseconds 500
}
throw "起動完了を30秒以内に確認できませんでした。PID=$($serverProcess.Id) ログ: $stdout / $stderr"
