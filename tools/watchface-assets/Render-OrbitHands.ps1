param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$arguments = @('render-orbit-hands')
if ($OutputDirectory) { $arguments += @('--output', $OutputDirectory) }
& (Join-Path (Split-Path $PSScriptRoot -Parent) 'Run-MaintenanceTool.ps1') -ToolArguments $arguments
