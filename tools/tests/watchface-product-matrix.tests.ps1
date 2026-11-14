$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $repoRoot 'tools/watchface-catalog.ps1')

$expectedProducts = @('sugarlicious-digital', 'sugarlicious-analog', 'sugarlicious-orbit')
$actualProducts = @($ACTIVE_WATCHFACES.Module)
if (Compare-Object $expectedProducts $actualProducts) {
    throw "Active product watchfaces differ: $($actualProducts -join ', ')"
}

if (Compare-Object @('sugarlicious-digital', 'sugarlicious-analog', 'sugarlicious-orbit') @($SUGARLICIOUS_WATCHFACES.Module)) {
    throw 'Sugarlicious Push catalog must contain only Digital, ApeX and Orbit.'
}
$pushPreparation = Get-Content (Join-Path $repoRoot 'tools/watchface-push/Prepare-WatchFacePushAssets.ps1') -Raw
if ($pushPreparation -match '\$ACTIVE_WATCHFACES' -or $pushPreparation -notmatch '\$SUGARLICIOUS_WATCHFACES') {
    throw 'Push preparation must consume only the Sugarlicious product catalog.'
}

if (Get-Variable LEGACY_WATCHFACES -ErrorAction SilentlyContinue) {
    throw 'Retired watchfaces must not remain in the product catalog.'
}
if (Get-Variable ALL_WATCHFACES -ErrorAction SilentlyContinue) {
    throw 'Release tooling must consume the active product catalog directly.'
}

$settings = Get-Content (Join-Path $repoRoot 'settings.gradle.kts') -Raw
$includedWatchfaces = @([regex]::Matches($settings, '"?:watchfaces:([^"\r\n]+)"') | ForEach-Object { $_.Groups[1].Value })
$expectedIncluded = @('test-wff') + $expectedProducts
if (Compare-Object ($expectedIncluded | Sort-Object) ($includedWatchfaces | Sort-Object)) {
    throw "Included WFF modules differ: $($includedWatchfaces -join ', ')"
}

$moduleDirectories = @(
    Get-ChildItem (Join-Path $repoRoot 'watchfaces') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'build.gradle.kts') } |
        ForEach-Object Name |
        Sort-Object
)
if (Compare-Object ($expectedIncluded | Sort-Object) $moduleDirectories) {
    throw "Retired WFF source modules remain: $($moduleDirectories -join ', ')"
}

$workflow = Get-Content (Join-Path $repoRoot '.github/workflows/build.yml') -Raw
foreach ($module in $expectedIncluded) {
    if ($workflow -notmatch [regex]::Escape(":watchfaces:${module}:assembleRelease")) {
        throw "CI release matrix is missing $module."
    }
}

$screen = Get-Content (Join-Path $repoRoot 'app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWatchScreen.kt') -Raw
if ($screen -match 'LegacyWatchFaceCard|legacyWatchFaceCards') {
    throw 'Retired watchface selection data remains in the Mobile UI.'
}

$controller = Get-Content (Join-Path $repoRoot 'app-wear/src/main/kotlin/app/aapswear/wear/WatchFacePushController.kt') -Raw
if ($controller -match 'legacyFaceSpecs|watchfacepush\.aaps|watchfacepush\.aimico') {
    throw 'Retired watchface deployment metadata remains in the Wear runtime.'
}

Write-Host 'PASS: product watchface matrix contains Digital, ApeX, Orbit, and the validator fixture.'
