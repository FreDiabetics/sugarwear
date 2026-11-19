param([string]$Configuration = "release")

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
. (Join-Path $PSScriptRoot "watchface-catalog.ps1")
$expectedModules = @($ACTIVE_WATCHFACES.Module) + @('test-wff')
$apks = @(
    foreach ($module in $expectedModules) {
        $outputDirectory = Join-Path $projectRoot "watchfaces/$module/build/outputs/apk/$Configuration"
        $moduleApks = @(Get-ChildItem $outputDirectory -Filter "*.apk" -ErrorAction SilentlyContinue)
        if ($moduleApks.Count -ne 1) {
            throw "Expected exactly one $Configuration APK for $module, found $($moduleApks.Count)."
        }
        $moduleApks[0]
    }
)

if (-not $apks) { throw "No $Configuration watchface APKs found" }
foreach ($apk in $apks) {
    $entries = & jar tf $apk.FullName
    if ($entries | Where-Object { $_ -match "(^|/)classes[0-9]*\.dex$" }) {
        throw "$($apk.FullName) contains executable DEX code"
    }
    $hash = (Get-FileHash $apk.FullName -Algorithm SHA256).Hash
    Write-Host "PASS code-free: $($apk.Name) SHA-256 $hash"
}

