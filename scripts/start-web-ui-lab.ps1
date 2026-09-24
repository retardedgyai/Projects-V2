param(
    [string]$JavaHome='C:/Users/xgaiz/Documents/Codex/minecraft-runtime/temurin-25/jdk-25.0.4.1+1',
    [int]$Port=25570,
    [int]$PreviewPort=18090,
    [string]$Pack='',
    [int]$PackPort=18091,
    [switch]$OpenOnJoin
)
$ErrorActionPreference='Stop'
$labRepo=Split-Path -Parent $PSScriptRoot
$java=Join-Path $JavaHome 'bin/java.exe'
$lib=Join-Path $labRepo 'web-ui-lab/build/install/web-ui-lab/lib'
$source=Join-Path $labRepo 'web-ui-lab/ui/forge.html'
if(-not(Test-Path -LiteralPath $java) -or -not(Test-Path -LiteralPath $lib)){throw 'Java25 / installDist が必要です。先に :web-ui-lab:installDist を実行してください。'}
if($Port -lt 1024 -or $Port -gt 65535 -or $Port -in @(25565,25566) -or $PreviewPort -lt 1024 -or $PreviewPort -gt 65535 -or $Port -eq $PreviewPort){throw '本編・モデル工房とは別のポートを指定してください。'}
if($Pack) {
    $Pack=(Resolve-Path -LiteralPath $Pack -ErrorAction Stop).Path
    if($PackPort -lt 1024 -or $PackPort -gt 65535 -or $PackPort -in @(25565,25566,$Port,$PreviewPort)){throw '配信パックのポートを別に指定してください。'}
}
foreach($p in (@($Port,$PreviewPort) + $(if($Pack){@($PackPort)}else{@()}))) {
    if(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue){throw "Port $p は使用中です。既存プロセスは停止しません。"}
}
$logDir=Join-Path $labRepo '.tools/web-ui-lab'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$outLog=Join-Path $logDir "$stamp.out.log"
$errLog=Join-Path $logDir "$stamp.err.log"
$jvmArgs=@(
    '-Xms128m','-Xmx512m','-XX:ActiveProcessorCount=2',"-Dprojects.ui.port=$Port","-Dprojects.ui.previewPort=$PreviewPort",("-Dprojects.ui.openOnJoin="+$OpenOnJoin.IsPresent.ToString().ToLowerInvariant()),
    "-Dprojects.ui.packPort=$PackPort"
)
if($Pack){$jvmArgs+=('-Dprojects.ui.pack="'+$Pack+'"')}
$process=Start-Process -FilePath $java -ArgumentList ($jvmArgs+@(
    '-cp',('"'+$lib+'/*"'),'dev.projects.webui.WebUiLabKt',('"'+$source+'"')
)) -WorkingDirectory $labRepo -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
Write-Output "UI laboratory started: PID=$($process.Id), Minecraft=127.0.0.1:$Port, preview=http://127.0.0.1:$PreviewPort"
Write-Output "Logs: $outLog / $errLog"
Write-Output '起動確認はログの UI_LAB_READY を確認してください。Minecraftクライアントや本編サーバーは操作していません。'
