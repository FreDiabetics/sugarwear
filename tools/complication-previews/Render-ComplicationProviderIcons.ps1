param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../.."))
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$destination = Join-Path $RepositoryRoot "app-wear/src/main/res/drawable-nodpi"
$canvasSize = 256
$white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$muted = [System.Drawing.Color]::FromArgb(255, 104, 104, 110)
$green = [System.Drawing.Color]::FromArgb(255, 51, 209, 122)
$orange = [System.Drawing.Color]::FromArgb(255, 255, 159, 67)
$red = [System.Drawing.Color]::FromArgb(255, 255, 82, 96)
$transparent = [System.Drawing.Color]::Transparent

function New-Font([float]$size, [System.Drawing.FontStyle]$style = [System.Drawing.FontStyle]::Bold) {
    [System.Drawing.Font]::new("Arial", $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Draw-CenteredText(
    [System.Drawing.Graphics]$graphics,
    [string]$text,
    [System.Drawing.RectangleF]$bounds,
    [float]$size,
    [System.Drawing.Color]$color = $white
) {
    $font = New-Font $size
    $brush = [System.Drawing.SolidBrush]::new($color)
    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $format.Trimming = [System.Drawing.StringTrimming]::EllipsisCharacter
    try {
        $graphics.DrawString($text, $font, $brush, $bounds, $format)
    } finally {
        $format.Dispose()
        $brush.Dispose()
        $font.Dispose()
    }
}

function Draw-TrendArrow([System.Drawing.Graphics]$graphics, [float]$offsetX = 0, [float]$offsetY = 0) {
    $pen = [System.Drawing.Pen]::new($white, 11)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    try {
        $graphics.DrawLine($pen, 158 + $offsetX, 145 + $offsetY, 207 + $offsetX, 96 + $offsetY)
        $graphics.DrawLine($pen, 207 + $offsetX, 96 + $offsetY, 207 + $offsetX, 127 + $offsetY)
        $graphics.DrawLine($pen, 207 + $offsetX, 96 + $offsetY, 176 + $offsetX, 96 + $offsetY)
    } finally {
        $pen.Dispose()
    }
}

function Draw-ProviderGlyph([System.Drawing.Graphics]$graphics, [string]$icon, [float]$centerX, [float]$centerY, [float]$size = 48) {
    $pen = [System.Drawing.Pen]::new($white, [Math]::Max(5, $size * 0.11))
    $pen.StartCap = $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $brush = [System.Drawing.SolidBrush]::new($white)
    try {
        switch ($icon) {
            "Glucose" {
                $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
                try {
                    $path.AddBezier($centerX, $centerY - $size * 0.55, $centerX - $size * 0.35, $centerY - $size * 0.15, $centerX - $size * 0.42, $centerY + $size * 0.12, $centerX, $centerY + $size * 0.48)
                    $path.AddBezier($centerX, $centerY + $size * 0.48, $centerX + $size * 0.42, $centerY + $size * 0.12, $centerX + $size * 0.35, $centerY - $size * 0.15, $centerX, $centerY - $size * 0.55)
                    $graphics.FillPath($brush, $path)
                    $cutoutPen = [System.Drawing.Pen]::new([System.Drawing.Color]::Black, $size * 0.08)
                    try {
                        $graphics.DrawLine($cutoutPen, $centerX, $centerY - $size * 0.28, $centerX, $centerY + $size * 0.30)
                    } finally { $cutoutPen.Dispose() }
                } finally { $path.Dispose() }
            }
            "Iob" {
                $graphics.DrawLine($pen, $centerX - $size * 0.38, $centerY + $size * 0.38, $centerX + $size * 0.28, $centerY - $size * 0.28)
                $graphics.DrawLine($pen, $centerX + $size * 0.10, $centerY - $size * 0.45, $centerX + $size * 0.45, $centerY - $size * 0.10)
                $graphics.DrawLine($pen, $centerX - $size * 0.42, $centerY + $size * 0.18, $centerX - $size * 0.18, $centerY + $size * 0.42)
            }
            "Cob" {
                $graphics.DrawLine($pen, $centerX, $centerY + $size * 0.48, $centerX, $centerY - $size * 0.45)
                foreach ($direction in @(-1, 1)) {
                    foreach ($offset in @(-0.28, -0.05, 0.18)) {
                        $graphics.DrawLine($pen, $centerX, $centerY + $size * $offset, $centerX + $direction * $size * 0.27, $centerY + $size * ($offset - 0.15))
                    }
                }
            }
            "BasalMore" {
                $graphics.DrawLine($pen, $centerX - $size * 0.45, $centerY + $size * 0.22, $centerX + $size * 0.15, $centerY + $size * 0.22)
                $graphics.DrawLine($pen, $centerX + $size * 0.15, $centerY + $size * 0.22, $centerX + $size * 0.15, $centerY - $size * 0.22)
                $graphics.DrawLine($pen, $centerX + $size * 0.15, $centerY - $size * 0.22, $centerX + $size * 0.45, $centerY - $size * 0.22)
            }
        }
    } finally {
        $brush.Dispose()
        $pen.Dispose()
    }
}

function Draw-Gauge(
    [System.Drawing.Graphics]$graphics,
    [float]$progress,
    [System.Drawing.Color]$progressColor = $white
) {
    $bounds = [System.Drawing.RectangleF]::new(17, 17, 222, 222)
    $trackPen = [System.Drawing.Pen]::new($muted, 25)
    $valuePen = [System.Drawing.Pen]::new($progressColor, 25)
    $clampedProgress = [Math]::Max(0.0, [Math]::Min(1.0, [double]$progress))
    $trackPen.StartCap = $trackPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $valuePen.StartCap = $valuePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    try {
        $graphics.DrawArc($trackPen, $bounds, 135, 270)
        $graphics.DrawArc($valuePen, $bounds, 135, 270 * $clampedProgress)
    } finally {
        $trackPen.Dispose()
        $valuePen.Dispose()
    }
}

function Draw-Graph([System.Drawing.Graphics]$graphics, [bool]$large) {
    $top = if ($large) { 45 } else { 66 }
    $bottom = if ($large) { 211 } else { 194 }
    $rangePen = [System.Drawing.Pen]::new($green, 5)
    $dotBrush = [System.Drawing.SolidBrush]::new($white)
    try {
        $graphics.DrawLine($rangePen, 20, $top + 40, 236, $top + 40)
        $graphics.DrawLine($rangePen, 20, $bottom - 36, 236, $bottom - 36)
        $points = @(
            @{ X = 24; Y = $bottom - 22 }, @{ X = 48; Y = $bottom - 35 },
            @{ X = 72; Y = $bottom - 29 }, @{ X = 96; Y = $bottom - 55 },
            @{ X = 120; Y = $bottom - 65 }, @{ X = 144; Y = $bottom - 58 },
            @{ X = 168; Y = $bottom - 83 }, @{ X = 192; Y = $bottom - 73 },
            @{ X = 216; Y = $bottom - 96 }, @{ X = 236; Y = $bottom - 88 }
        )
        foreach ($point in $points) {
            $graphics.FillEllipse($dotBrush, $point.X - 6, $point.Y - 6, 12, 12)
        }
    } finally {
        $rangePen.Dispose()
        $dotBrush.Dispose()
    }
}

function Draw-WeightedElements([System.Drawing.Graphics]$graphics) {
    $bounds = [System.Drawing.RectangleF]::new(20, 20, 216, 216)
    $segments = @(
        @($red, 135, 30),
        @($green, 169, 194),
        @($orange, 367, 38)
    )
    foreach ($segment in $segments) {
        $pen = [System.Drawing.Pen]::new($segment[0], 27)
        $pen.StartCap = $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
        try {
            $graphics.DrawArc($pen, $bounds, $segment[1], $segment[2])
        } finally {
            $pen.Dispose()
        }
    }
    Draw-CenteredText $graphics "100%" ([System.Drawing.RectangleF]::new(42, 76, 172, 104)) 54
}

function Draw-LoopIcon([System.Drawing.Graphics]$graphics) {
    $pen = [System.Drawing.Pen]::new($white, 22)
    try {
        $graphics.DrawEllipse($pen, 55, 55, 146, 146)
    } finally {
        $pen.Dispose()
    }
}

function Render-ProviderIcon([hashtable]$spec) {
    $bitmap = [System.Drawing.Bitmap]::new($canvasSize, $canvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear($transparent)

    try {
        switch ($spec.Kind) {
            "Ranged" {
                Draw-Gauge $graphics $spec.Progress $white
                $size = if ($spec.Text.Length -gt 7) { 35 } else { 52 }
                $bounds = if ($spec.Icon) { [System.Drawing.RectangleF]::new(85, 72, 133, 105) } else { [System.Drawing.RectangleF]::new(38, 72, 180, 105) }
                if ($spec.Icon) { Draw-ProviderGlyph $graphics $spec.Icon 64 126 42 }
                Draw-CenteredText $graphics $spec.Text $bounds $size
                if ($spec.Trend) { Draw-TrendArrow $graphics -18 62 }
            }
            "Goal" {
                Draw-Gauge $graphics $spec.Progress $green
                Draw-CenteredText $graphics $spec.Text ([System.Drawing.RectangleF]::new(42, 76, 172, 104)) 54
            }
            "Weighted" { Draw-WeightedElements $graphics }
            "GraphSmall" { Draw-Graph $graphics $false }
            "GraphLarge" { Draw-Graph $graphics $true }
            "Icon" { Draw-LoopIcon $graphics }
            "TrendIcon" { Draw-TrendArrow $graphics -54 7 }
            default {
                $hasTitle = -not [string]::IsNullOrWhiteSpace($spec.Title)
                $mainBounds = if ($hasTitle) {
                    [System.Drawing.RectangleF]::new(18, 49, 220, 103)
                } else {
                    [System.Drawing.RectangleF]::new(18, 64, 220, 128)
                }
                $mainSize = if ($spec.Text.Length -gt 12) { 27 } elseif ($spec.Text.Length -gt 7) { 38 } else { 60 }
                if ($spec.Trend) {
                    $mainBounds = [System.Drawing.RectangleF]::new(10, $mainBounds.Y, 172, $mainBounds.Height)
                }
                if ($spec.Icon) {
                    Draw-ProviderGlyph $graphics $spec.Icon 50 ($mainBounds.Y + $mainBounds.Height / 2) 44
                    $mainBounds = [System.Drawing.RectangleF]::new(79, $mainBounds.Y, 159, $mainBounds.Height)
                }
                Draw-CenteredText $graphics $spec.Text $mainBounds $mainSize
                if ($spec.Trend) {
                    Draw-TrendArrow $graphics 0 -11
                }
                if ($hasTitle) {
                    $titleSize = if ($spec.Title.Length -gt 14) { 21 } elseif ($spec.Title.Length -gt 8) { 25 } else { 32 }
                    Draw-CenteredText $graphics $spec.Title ([System.Drawing.RectangleF]::new(18, 145, 220, 76)) $titleSize
                }
            }
        }

        $output = Join-Path $destination ("provider_preview_{0}.png" -f $spec.Name)
        $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$specs = @(
    @{ Name = "01a"; Kind = "Text"; Text = "123"; Icon = "Glucose" },
    @{ Name = "01b"; Kind = "Text"; Text = "123"; Icon = "Glucose" },
    @{ Name = "01c"; Kind = "Ranged"; Text = "123"; Progress = 0.377; Icon = "Glucose" },
    @{ Name = "02a"; Kind = "Text"; Text = "123"; Trend = $true },
    @{ Name = "02b"; Kind = "Text"; Text = "123"; Trend = $true },
    @{ Name = "02c"; Kind = "Ranged"; Text = "123"; Progress = 0.377; Trend = $true },
    @{ Name = "03a"; Kind = "Text"; Text = "123"; Title = "+5" },
    @{ Name = "03b"; Kind = "Text"; Text = "123"; Title = "+5" },
    @{ Name = "04a"; Kind = "Text"; Text = "123"; Title = "2m"; Trend = $true },
    @{ Name = "04b"; Kind = "Text"; Text = "123"; Title = "2m"; Trend = $true },
    @{ Name = "05"; Kind = "Text"; Text = "123"; Title = "+5"; Trend = $true },
    @{ Name = "06a"; Kind = "Text"; Text = "123"; Title = "+5 · 2m"; Trend = $true },
    @{ Name = "06b"; Kind = "Text"; Text = "123"; Title = "+5 · 2m"; Trend = $true },
    @{ Name = "07a"; Kind = "GraphSmall" },
    @{ Name = "07b"; Kind = "GraphLarge" },
    @{ Name = "08"; Kind = "TrendIcon" },
    @{ Name = "09"; Kind = "Text"; Text = "+5" },
    @{ Name = "10"; Kind = "Text"; Text = "2m" },
    @{ Name = "11"; Kind = "Text"; Text = "+5"; Title = "2m" },
    @{ Name = "12a"; Kind = "Text"; Text = "—" },
    @{ Name = "12b"; Kind = "Ranged"; Text = "—"; Progress = 0 },
    @{ Name = "13"; Kind = "Text"; Text = "0.80U/h"; Icon = "BasalMore" },
    @{ Name = "14a"; Kind = "Text"; Text = "1.20U"; Icon = "Iob" },
    @{ Name = "14b"; Kind = "Ranged"; Text = "1.20U"; Progress = 0.12; Icon = "Iob" },
    @{ Name = "15a"; Kind = "Text"; Text = "15g"; Icon = "Cob" },
    @{ Name = "15b"; Kind = "Ranged"; Text = "15g"; Progress = 0.10; Icon = "Cob" },
    @{ Name = "16a"; Kind = "Text"; Text = "1.2U · 15g"; Title = "0.80U/h" },
    @{ Name = "16b"; Kind = "Text"; Text = "1.2U · 15g"; Title = "0.80U/h" },
    @{ Name = "17a"; Kind = "Text"; Text = "●" },
    @{ Name = "17b"; Kind = "Icon" },
    @{ Name = "18a"; Kind = "Text"; Text = "120U"; Title = "OK" },
    @{ Name = "18b"; Kind = "Ranged"; Text = "120U"; Progress = 0.40 },
    @{ Name = "19a"; Kind = "Text"; Text = "100%"; Title = "70–180" },
    @{ Name = "19b"; Kind = "Goal"; Text = "100%"; Progress = 1.0 },
    @{ Name = "19c"; Kind = "Weighted" },
    @{ Name = "20"; Kind = "Text"; Text = "16"; Title = "MON" },
    @{ Name = "21"; Kind = "Text"; Text = "80%" },
    @{ Name = "22"; Kind = "Text"; Text = "85%" },
    @{ Name = "23"; Kind = "Text"; Text = "123→" }
)

$specs | ForEach-Object { Render-ProviderIcon $_ }
Write-Host "Rendered $($specs.Count) type-specific complication provider icons."
