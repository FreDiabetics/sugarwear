param(
    [string]$ValidatorCommit = "702e9bdf050df800a6469cd2155c5c123fd54cb7",
    [string[]]$WatchfaceFiles,
    [ValidateRange(0, 5)][int]$FormatVersion = 0
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$cacheName = "aaps-wff-validator-$ValidatorCommit"
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$cacheRoot = [System.IO.Path]::GetFullPath((Join-Path $tempRoot $cacheName))
$validatorRoot = Join-Path $cacheRoot "third_party\wff"
$validatorJar = Join-Path $validatorRoot "specification\validator\build\libs\wff-validator.jar"

function Assert-LastCommandSucceeded([string]$Message) {
    if ($LASTEXITCODE -ne 0) { throw $Message }
}

$cacheHealthy = $false
if (Test-Path (Join-Path $cacheRoot ".git")) {
    & git -C $cacheRoot rev-parse --is-inside-work-tree 2>$null | Out-Null
    $cacheHealthy = $LASTEXITCODE -eq 0
}

if (-not $cacheHealthy) {
    $expectedCacheRoot = Join-Path $tempRoot $cacheName
    if ($cacheRoot -ne $expectedCacheRoot -or (Split-Path $cacheRoot -Leaf) -ne $cacheName) {
        throw "Refusing to repair unexpected validator cache path: $cacheRoot"
    }
    if (Test-Path $cacheRoot) {
        Write-Host "Repairing incomplete WFF validator cache: $cacheRoot"
        Remove-Item -LiteralPath $cacheRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    & git -C $cacheRoot init
    Assert-LastCommandSucceeded "Initializing the official WFF validator cache failed"
    & git -C $cacheRoot remote add origin https://github.com/google/watchface.git
    Assert-LastCommandSucceeded "Configuring the official WFF validator remote failed"
    & git -C $cacheRoot sparse-checkout init --cone
    Assert-LastCommandSucceeded "Initializing sparse checkout for the official WFF validator failed"
    & git -C $cacheRoot sparse-checkout set third_party/wff
    Assert-LastCommandSucceeded "Configuring sparse checkout for the official WFF validator failed"
    & git -C $cacheRoot fetch --depth 1 origin $ValidatorCommit
    Assert-LastCommandSucceeded "Fetching the pinned official WFF validator commit failed"
    & git -C $cacheRoot checkout --detach FETCH_HEAD
    Assert-LastCommandSucceeded "Checking out the pinned official WFF validator commit failed"
}

$actualCommit = (& git -C $cacheRoot rev-parse HEAD).Trim()
Assert-LastCommandSucceeded "Reading the official WFF validator cache commit failed"
if ($actualCommit -ne $ValidatorCommit) {
    throw "Validator cache has commit $actualCommit, expected $ValidatorCommit"
}

if (-not (Test-Path $validatorJar)) {
    $gradle = if ($IsWindows) { Join-Path $projectRoot "gradlew.bat" } else { Join-Path $projectRoot "gradlew" }
    & $gradle -p $validatorRoot :specification:validator:executable-jar
    if ($LASTEXITCODE -ne 0) { throw "Building the official WFF validator failed" }
}

if (-not $WatchfaceFiles) {
    . (Join-Path $projectRoot 'tools/watchface-catalog.ps1')
    $modules = @($ACTIVE_WATCHFACES.Module) + @('test-wff')
    $WatchfaceFiles = @(
        foreach ($module in $modules) {
            Join-Path $projectRoot "watchfaces/$module/src/main/res/raw/watchface.xml"
        }
    )
}
if (-not $WatchfaceFiles) { throw "No WFF watchface.xml files found" }

$batches = @{}
foreach ($path in $WatchfaceFiles) {
    $version = $FormatVersion
    if ($version -eq 0) {
        $absolutePath = (Resolve-Path -LiteralPath $path).Path
        $mainDirectory = Split-Path (Split-Path (Split-Path $absolutePath))
        $manifestPath = Join-Path $mainDirectory 'AndroidManifest.xml'
        [xml]$manifest = Get-Content -LiteralPath $manifestPath -Raw
        $androidNamespace = 'http://schemas.android.com/apk/res/android'
        $property = @(@($manifest.manifest.application.property) | Where-Object {
            $_.GetAttribute('name', $androidNamespace) -eq 'com.google.wear.watchface.format.version'
        })
        if ($property.Count -ne 1) { throw "Missing or ambiguous WFF version: $manifestPath" }
        $version = [int]$property[0].GetAttribute('value', $androidNamespace)
        if ($version -lt 1 -or $version -gt 5) { throw "Unsupported WFF version $version in $manifestPath" }
    }
    $batches[$version] = @($batches[$version]) + @($path)
}
foreach ($version in ($batches.Keys | Sort-Object)) {
    $files = @($batches[$version] | Where-Object { $_ })
    $output = & java -jar $validatorJar $version $files 2>&1
    $exitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Host $_ }
    if ($exitCode -ne 0 -or ($output | Out-String) -match 'FAILED') {
        throw "At least one WFF $version document failed schema validation"
    }
}

