param(
    [string]$Source = 'C:/Users/typ1d/Desktop/Sugarlicious/Watchfaces/ic_Sugarlicious.svg',
    [string]$SugarWearSource,
    [string]$SugarWearRepository
)
$ErrorActionPreference = 'Stop'
if ($SugarWearSource -and !$SugarWearRepository) { throw 'Specify SugarWearRepository when importing a SugarWear source.' }
& (Join-Path $PSScriptRoot 'Run-MaintenanceTool.ps1') -ToolArguments @(
    'import-app-brand-icons', '--source', $Source, '--product', 'sugarlicious'
)
if ($SugarWearSource) {
    & (Join-Path $PSScriptRoot 'Run-MaintenanceTool.ps1') -RepositoryRoot $SugarWearRepository -ToolArguments @(
        'import-app-brand-icons', '--source', $SugarWearSource, '--product', 'sugarwear', '--repository-root', $SugarWearRepository
    )
}
