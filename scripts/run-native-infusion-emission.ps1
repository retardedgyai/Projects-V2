param([string]$CacheRoot = "$env:USERPROFILE/.gradle/caches", [string]$JavaExe = "$env:JAVA_HOME/bin/java.exe")
$ErrorActionPreference = 'Stop'
$infusionRepoRoot = Split-Path $PSScriptRoot -Parent
$infusionNativeInfo = Get-Content -LiteralPath "$CacheRoot/fabric-loom/26.2/mojang_minecraft_info.json" -Raw | ConvertFrom-Json
$infusionClasspath = @("$CacheRoot/fabric-loom/26.2/minecraft-client.jar")
foreach ($library in $infusionNativeInfo.libraries) {
    $parts = $library.name.Split(':')
    if ($parts.Count -ne 3) { continue }
    $artifactRoot = "$CacheRoot/modules-2/files-2.1/$($parts[0])/$($parts[1])/$($parts[2])"
    if (Test-Path -LiteralPath $artifactRoot) {
        $jar = Get-ChildItem -LiteralPath $artifactRoot -Recurse -File -Filter "$($parts[1])-$($parts[2]).jar" | Select-Object -First 1
        if ($jar) { $infusionClasspath += $jar.FullName }
    }
}
# Keep the vanilla logging library's empty log under ignored evidence, rather than the source tree.
$infusionParserWorking = Join-Path $infusionRepoRoot '.tools/native-model-check'
New-Item -ItemType Directory -Path $infusionParserWorking -Force | Out-Null
Push-Location -LiteralPath $infusionParserWorking
try {
    # Small one-off source-mode parser process, no game/window/server/network or shader replacement.
    & $JavaExe '-Xmx192m' '-XX:ActiveProcessorCount=2' '-cp' ($infusionClasspath -join ';') "$PSScriptRoot/CheckNativeInfusionEmission.java" "$infusionRepoRoot/assets/model-lab/infusion-v5/assets/projects/models/infusion-v5"
    if ($LASTEXITCODE -ne 0) { throw "Native parser exited $LASTEXITCODE" }
} finally { Pop-Location }
