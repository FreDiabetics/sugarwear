param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$faces = @("analog", "orbit", "rings", "graph")
$expectedHashes = @{
    "hour" = "937A5F8E0C1554ACC64337613C15FC69A2A10C0BBF029E86756B25FB0D7A413B"
    "minute" = "FB3FE7F0AE6A9D9F396E48712361119E42DA10E6C339CD6DBE066F0278C7FA1D"
    "second" = "DFFEC182E233D9BB4E09BF805FA14A927B31707C7A73435F6C6E3D77FB19C039"
}

# The user-provided hand layers are the canonical assets. Verify the checked-in renderings on
# every platform; regenerating them from older geometry would silently replace the selected set.
if ($true) {
    foreach ($face in $faces) {
        $destination = Join-Path $root "watchfaces/sugarlicious-$face/src/main/res/drawable-nodpi"
        foreach ($kind in $expectedHashes.Keys) {
            $path = Join-Path $destination "${kind}_hand.png"
            if (-not (Test-Path $path)) {
                throw "Sugarlicious hand asset missing: $path"
            }
            $actualHash = (Get-FileHash $path -Algorithm SHA256).Hash
            if ($actualHash -ne $expectedHashes[$kind]) {
                throw "Sugarlicious $kind hand hash mismatch for $face"
            }
        }
    }
    Write-Host "Verified checked-in Sugarlicious hand geometry for all analog faces."
    return
}

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

$size = 450
$sourceSize = 512.0
$scale = $size / $sourceSize

$white = [Windows.Media.Brushes]::White
$red = [Windows.Media.SolidColorBrush]::new([Windows.Media.Color]::FromArgb(255, 255, 0, 0))
$grey = [Windows.Media.SolidColorBrush]::new([Windows.Media.Color]::FromArgb(255, 188, 188, 188))
$black = [Windows.Media.Brushes]::Black
$secondGeometry = [Windows.Media.Geometry]::Parse(
    "M258,264.25 v31.75 h-4 v-31.75 c-3.73,-0.9 -6.5,-4.25 -6.5,-8.25 s2.77,-7.35 6.5,-8.25 V6 h4 v241.75 c3.73,0.9 6.5,4.25 6.5,8.25 s-2.77,7.35 -6.5,8.25 Z"
)

function Write-HandPng {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [ValidateSet("hour", "minute", "second")]
        [string]$Kind
    )

    $visual = [Windows.Media.DrawingVisual]::new()
    $drawing = $visual.RenderOpen()
    $drawing.PushTransform([Windows.Media.ScaleTransform]::new($scale, $scale))

    if ($Kind -eq "hour" -or $Kind -eq "minute") {
        $drawing.DrawRectangle($white, $null, [Windows.Rect]::new(252.75, 224.44, 6.5, 29.56))
        $top = if ($Kind -eq "hour") { 113.57 } else { 34.47 }
        $height = if ($Kind -eq "hour") { 114.0 } else { 193.1 }
        $drawing.DrawRoundedRectangle(
            $white,
            $null,
            [Windows.Rect]::new(243.0, $top, 26.0, $height),
            13.0,
            13.0
        )
    }

    if ($Kind -eq "minute") {
        $drawing.DrawEllipse($grey, $null, [Windows.Point]::new(256.0, 256.0), 12.0, 12.0)
    }

    if ($Kind -eq "second") {
        $drawing.DrawGeometry($red, $null, $secondGeometry)
        $drawing.DrawEllipse($black, $null, [Windows.Point]::new(256.0, 256.0), 4.0, 4.0)
    }

    $drawing.Pop()
    $drawing.Close()

    $bitmap = [Windows.Media.Imaging.RenderTargetBitmap]::new(
        $size,
        $size,
        96.0,
        96.0,
        [Windows.Media.PixelFormats]::Pbgra32
    )
    $bitmap.Render($visual)

    $encoder = [Windows.Media.Imaging.PngBitmapEncoder]::new()
    $encoder.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Create, [IO.FileAccess]::Write)
    try {
        $encoder.Save($stream)
    } finally {
        $stream.Dispose()
    }
}

foreach ($face in $faces) {
    $destination = Join-Path $root "watchfaces/sugarlicious-$face/src/main/res/drawable-nodpi"
    Write-HandPng -Path (Join-Path $destination "hour_hand.png") -Kind hour
    Write-HandPng -Path (Join-Path $destination "minute_hand.png") -Kind minute
    Write-HandPng -Path (Join-Path $destination "second_hand.png") -Kind second
}

Write-Host "Rendered uploaded Sugarlicious hand geometry for all analog faces."
