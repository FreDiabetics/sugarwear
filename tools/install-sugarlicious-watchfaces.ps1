param(
    [Parameter(Mandatory = $true)]
    [string]$WatchSerial,

    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'watchface-catalog.ps1')

if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) {
    throw "ADB wurde nicht gefunden: $Adb"
}

$connected = & $Adb devices
if ($LASTEXITCODE -ne 0 -or -not ($connected -match "(?m)^$([regex]::Escape($WatchSerial))\s+device(?:\s|$)")) {
    throw "Die Watch '$WatchSerial' ist nicht als aktives ADB-Gerät verbunden."
}

foreach ($face in $ACTIVE_WATCHFACES) {
    $apk =
        Join-Path $projectRoot `
            "app-wear\build\generated\watchfacePushAssets\watchfaces\$($face.Asset)"
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "Watchface-APK fehlt: $apk. Zuerst Prepare-WatchFacePushAssets.ps1 ausführen."
    }

    Write-Host "Installiere Sugarlicious $($face.Name) ..."
    & $Adb -s $WatchSerial install -r $apk
    if ($LASTEXITCODE -ne 0) {
        throw "Installation von Sugarlicious $($face.Name) fehlgeschlagen."
    }
}

Write-Host "Alle sechs Sugarlicious-Watchfaces wurden auf der Watch installiert."
