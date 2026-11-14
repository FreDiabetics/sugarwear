param([Parameter(Mandatory)][string]$ReferenceApk)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $repo 'watchfaces/sugarlicious-orbit/reference/hands'
[IO.Directory]::CreateDirectory($output) | Out-Null
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ReferenceApk).Path)
try {
    for ($style = 0; $style -lt 15; $style++) {
        foreach ($kind in @('hour', 'minute')) {
            $prefix = if ($kind -eq 'hour') { 'hour' } else { 'min' }
            $source = if ($style -lt 7) { "${prefix}_style_$style" } else { "${prefix}_premium_style_$($style - 7)" }
            foreach ($accent in @($false, $true)) {
                if ($accent -and $style -ne 6) { continue }
                $entryName = $source + $(if ($accent) { '_tint' } else { '' }) + '_transparent.png'
                $entry = $archive.GetEntry("res/drawable-nodpi-v4/$entryName")
                if (!$entry) { throw "Missing reference hand $entryName" }
                $filename = "orbit_${kind}_${style}" + $(if ($accent) { '_accent' } else { '' }) + '.png'
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $output $filename), $true)
            }
        }
    }
} finally { $archive.Dispose() }
Write-Output 'Imported only the 15 requested hand pairs and their two tint layers.'
