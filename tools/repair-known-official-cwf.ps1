param(
    [string]$SourceDirectory = "..\.upstream\AndroidAPSdocs\docs\_static\ExchangeSiteCustomWatchfaces",
    [string]$OutputDirectory = "build\recovered-cwf"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$expectedHashes = @{
    "Digital_G-Watch.zip" = "EEFAB1C50325F87C61A2721CECB048256B246F51614F55D51D7C6EEE227EEBD2"
    "Gota_v2.4.zip" = "32EBD161BDA573105D42649437B4AF3C86553A1ABF49972800161C48736C123C"
    "LuckyLoopKoeln.zip" = "F163641DA8110FB794023D2EA9D42D3625A74ADA3B1ADF96BF88AA5316A62E6A"
    "pzero_v1.0.zip" = "45962688697D7D246F03650A02C70100AD6E88AEF5A64444DAC3A8AADB8B121A"
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Assert-ExpectedSource([string]$name) {
    $path = Join-Path $SourceDirectory $name
    if (-not (Test-Path -LiteralPath $path)) { throw "Official source is missing: $path" }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ($actual -ne $expectedHashes[$name]) {
        throw "Refusing an unknown revision of $name (SHA-256 $actual)"
    }
    return (Resolve-Path -LiteralPath $path).Path
}

function Rewrite-ZipJson([string]$name, [scriptblock]$transform) {
    $source = Assert-ExpectedSource $name
    $destination = Join-Path $OutputDirectory $name
    $inputArchive = [IO.Compression.ZipFile]::OpenRead($source)
    $outputStream = [IO.File]::Create($destination)
    $outputArchive = [IO.Compression.ZipArchive]::new($outputStream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $inputArchive.Entries) {
            $newEntry = $outputArchive.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
            $entryInput = $entry.Open()
            $entryOutput = $newEntry.Open()
            try {
                if ($entry.FullName -ieq "CustomWatchface.json") {
                    $reader = [IO.StreamReader]::new($entryInput)
                    $json = $reader.ReadToEnd()
                    $reader.Dispose()
                    $changed = & $transform $json
                    $writer = [IO.StreamWriter]::new($entryOutput, [Text.UTF8Encoding]::new($false))
                    $writer.Write($changed)
                    $writer.Flush()
                    $writer.Dispose()
                } else {
                    $entryInput.CopyTo($entryOutput)
                }
            } finally {
                $entryOutput.Dispose()
                $entryInput.Dispose()
            }
        }
    } finally {
        $outputArchive.Dispose()
        $outputStream.Dispose()
        $inputArchive.Dispose()
    }
    return $destination
}

# Digital_G-Watch contains a valid ZIP followed by 798 bytes of a second,
# incomplete central directory. Preserve exactly the first archive through its
# end-of-central-directory record; the parser validates the recovered result.
$digitalSource = Assert-ExpectedSource "Digital_G-Watch.zip"
$digitalBytes = [IO.File]::ReadAllBytes($digitalSource)
$eocdEnd = $null
for ($index = 0; $index -le $digitalBytes.Length - 22; $index++) {
    if ($digitalBytes[$index] -eq 0x50 -and $digitalBytes[$index + 1] -eq 0x4B -and
        $digitalBytes[$index + 2] -eq 0x05 -and $digitalBytes[$index + 3] -eq 0x06) {
        $commentLength = [BitConverter]::ToUInt16($digitalBytes, $index + 20)
        $eocdEnd = $index + 22 + $commentLength
        break
    }
}
if ($null -eq $eocdEnd -or $eocdEnd -ge $digitalBytes.Length) {
    throw "The known Digital_G-Watch trailing-data defect was not found"
}
[IO.File]::WriteAllBytes((Join-Path $OutputDirectory "Digital_G-Watch.zip"), $digitalBytes[0..($eocdEnd - 1)])

Rewrite-ZipJson "Gota_v2.4.zip" {
    param($json)
    [regex]::Replace($json, '(?m)^\s*//.*(?:\r?\n|$)', '')
} | Out-Null

Rewrite-ZipJson "LuckyLoopKoeln.zip" {
    param($json)
    $json.Replace('"topmargin": 25030', '"topmargin": 250')
} | Out-Null

Rewrite-ZipJson "pzero_v1.0.zip" {
    param($json)
    [regex]::Replace(
        $json,
        '"background"\s*:\s*\{\s*"visibility"\s*:\s*"gone"',
        '"background": {' + "`n" +
        '        "width": 400,' + "`n" +
        '        "height": 400,' + "`n" +
        '        "topmargin": 0,' + "`n" +
        '        "leftmargin": 0,' + "`n" +
        '        "visibility": "gone"',
        1
    )
} | Out-Null

Get-ChildItem -LiteralPath $OutputDirectory -Filter "*.zip" |
    Sort-Object Name |
    Select-Object Name, Length, @{ Name = "SHA256"; Expression = { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } }
