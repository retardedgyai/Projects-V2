param([string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot', [int]$Port=25587, [int]$PackPort=25588)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$java=Join-Path $JavaHome 'bin/java.exe'
$lib=Join-Path $projectRoot 'server-minestom/build/install/server-minestom/lib'
if(-not (Test-Path -LiteralPath $java)){throw 'JDK25 is required'}
if(-not (Test-Path -LiteralPath (Join-Path $lib 'server-minestom-0.1.0-SNAPSHOT.jar'))){throw 'Run :server-minestom:installDist first'}
if($Port -eq $PackPort){throw 'Game and pack ports must differ'}
foreach($candidate in @($Port,$PackPort)) {
    $listener=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$candidate)
    try{$listener.Start()}finally{$listener.Stop()}
}
$run=Join-Path $projectRoot 'server-minestom/run/world-foundry'
[void](New-Item -ItemType Directory -Force -Path $run)
$record=Join-Path $run 'owned-process.json'
if(Test-Path -LiteralPath $record){
    $old=Get-Content -LiteralPath $record -Raw | ConvertFrom-Json
    $running=Get-Process -Id $old.ProcessId -ErrorAction SilentlyContinue
    if($running -and $running.StartTime.ToUniversalTime().Ticks -eq ([datetimeoffset]$old.StartTimeUtc).UtcTicks){throw 'This isolated foundry server is already running'}
}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout=Join-Path $run "$stamp.log";$stderr=Join-Path $run "$stamp.error.log"
$arguments=@('-Xmx512m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Dprojects.worldFoundry=true',"-Dprojects.port=$Port","-Dprojects.ui.port=$PackPort",'-cp',"`"$lib\*`"",'dev.projects.server.ProjectSServerKt')
$owned=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $run -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$info=[pscustomobject]@{ProcessId=$owned.Id;StartTimeUtc=$owned.StartTime.ToUniversalTime().ToString('o');JavaPath=$owned.Path;ProjectRoot=$projectRoot;Address="127.0.0.1:$Port";Log=$stdout;ErrorLog=$stderr;SaveDirectory=(Join-Path $run 'config/projects/world-foundry-lab')}
$info|ConvertTo-Json|Set-Content -LiteralPath $record -Encoding UTF8
for($attempt=0;$attempt -lt 60;$attempt++){
    if($owned.HasExited){throw "Owned foundry server exited. Read $stderr"}
    if((Test-Path -LiteralPath $stdout) -and (Select-String -LiteralPath $stdout -SimpleMatch 'PROJECTS_WORLD_FOUNDRY_READY' -Quiet)){$info;return}
    Start-Sleep -Milliseconds 500
}
throw "Startup pending. Inspect recorded owned PID=$($owned.Id), $stdout and $stderr"
