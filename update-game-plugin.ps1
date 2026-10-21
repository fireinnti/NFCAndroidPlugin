param(
    [string]$GamePath = (Join-Path $PSScriptRoot '..\NFCGame')
)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GamePath).Path
if (-not (Test-Path -LiteralPath (Join-Path $game 'project.godot'))) {
    throw "No Godot project found at $game"
}
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat :plugin:assemble
    if ($LASTEXITCODE -ne 0) { throw 'Plugin build failed; game files were not copied.' }
    $destination = Join-Path $game 'addons\GodotAndroidNFCPlugin'
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -Path 'plugin\demo\addons\GodotAndroidNFCPlugin\*' -Destination $destination -Recurse -Force
    Write-Host "Updated NFC plugin in $destination"
} finally {
    Pop-Location
}
