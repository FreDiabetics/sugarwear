param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$FavoriteId
)

# Import the native fixed-time preview, without selecting or editing the face.
# The former hand-drawn PNG renderer is retired: preview.webp is canonical.
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$target = Join-Path $repo 'watchfaces/sugarlicious-orbit/src/main/res/drawable-nodpi/preview.webp'
$temporary = [IO.Path]::GetTempFileName()
$remote = '/data/local/tmp/sugarlicious-orbit-native-preview.webp'
try {
    & $adb -s $Serial shell "content read --uri content://com.google.wear.services.watchface.previews.provider/wf-favorite-previews/$FavoriteId-preview > $remote"
    if ($LASTEXITCODE -ne 0) { throw 'Native preview read failed' }
    & $adb -s $Serial pull $remote $temporary
    if ($LASTEXITCODE -ne 0) { throw 'Native preview transfer failed' }
    $bytes = [IO.File]::ReadAllBytes($temporary)
    if ($bytes.Length -lt 12 -or [Text.Encoding]::ASCII.GetString($bytes, 0, 4) -ne 'RIFF' -or [Text.Encoding]::ASCII.GetString($bytes, 8, 4) -ne 'WEBP') {
        throw 'Native preview is not a valid WebP container; existing asset preserved'
    }
    Copy-Item -LiteralPath $temporary -Destination $target -Force
    Write-Host "Imported native preview for favorite $FavoriteId into $target"
} finally {
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
}
