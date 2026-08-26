$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceDir = Join-Path $projectDir 'src'
$buildDir = Join-Path $projectDir 'build'
$classesDir = Join-Path $buildDir 'classes'
$manifest = Join-Path $buildDir 'MANIFEST.MF'

if (Test-Path -LiteralPath $classesDir) {
    Remove-Item -LiteralPath $classesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$sources = Get-ChildItem -LiteralPath $sourceDir -Recurse -Filter '*.java' | ForEach-Object FullName
if (-not $sources) { throw 'No Java sources found.' }

& javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
Copy-Item -LiteralPath (Join-Path $sourceDir 'log4j2-lootpredictor.xml') -Destination $classesDir

Set-Content -LiteralPath $manifest -Encoding Ascii -Value @(
    'Manifest-Version: 1.0'
    'Main-Class: dev.lootpredictor.Main'
    'Implementation-Title: Minecraft Loot Predictor'
    'Implementation-Version: 1.0.0'
    ''
)

$jarPath = Join-Path $projectDir 'MinecraftLootPredictor.jar'
& jar cfm $jarPath $manifest -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'Jar packaging failed.' }
Write-Host "Built $jarPath"
