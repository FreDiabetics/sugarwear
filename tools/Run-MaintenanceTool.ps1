param([Parameter(Mandatory = $true)][string[]]$ToolArguments)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
& (Join-Path $repositoryRoot 'shared/sugarlicious/tools/Run-MaintenanceTool.ps1') -RepositoryRoot $repositoryRoot -ToolArguments $ToolArguments
