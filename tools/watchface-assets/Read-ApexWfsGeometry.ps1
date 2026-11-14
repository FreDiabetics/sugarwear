param([Parameter(Mandatory = $true)][string]$SourceWfs)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $SourceWfs).Path)
try {
    $entry = $archive.GetEntry('honeyface.json')
    if ($null -eq $entry) { throw 'WFS project has no honeyface.json' }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $project = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
} finally { $archive.Dispose() }

function Read-Property($node, $category, $property) {
    $node.categories.$category.properties.$property.value
}

$names = @('Upper Left Complication', 'Upper Right Complication', 'Lower Right Complication',
    'Lower Left Complication', 'Center Left Complication', 'Center Right Complication',
    'Center Bottom Complication', 'Graph Compilation')
$slots = foreach ($name in $names) {
    $slot = $project.scene[0].child | Where-Object name -EQ $name
    if ($null -eq $slot) { throw "Missing WFS slot: $name" }
    $children = foreach ($child in $slot.child) {
        $position = Read-Property $child placement position
        $matrix = Read-Property $child matrix matrix
        [ordered]@{
            name = $child.name
            field = $child.complicationField
            type = $child.type
            x = $position.x + $matrix.e
            y = $position.y + $matrix.f
            width = Read-Property $child dimension width
            height = Read-Property $child dimension height
            rotation = Read-Property $child rotate rotation
            pivot = Read-Property $child rotate pivot
            stroke = Read-Property $child dimension thickness
            color = Read-Property $child color color
            fontSize = Read-Property $child textAppearance textSize
            font = Read-Property $child textAppearance font
            textAlign = Read-Property $child textAppearance textAlign
            textStyle = Read-Property $child textAppearance style
            ellipsis = Read-Property $child textAppearance ellipsis
            curve = $child.categories.curvedText.properties
            range = $child.categories.range.properties
        }
    }
    [ordered]@{ name = $name; children = @($children) }
}
[ordered]@{
    sourceName = [IO.Path]::GetFileName($SourceWfs)
    sourceSha256 = (Get-FileHash -LiteralPath $SourceWfs -Algorithm SHA256).Hash
    sourceCanvas = 450
    targetCanvas = 512
    slots = @($slots)
} | ConvertTo-Json -Depth 20
