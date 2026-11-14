param(
    [Parameter(Mandatory = $true)]
    [string[]]$ToolArguments,
    [string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
$RepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$argumentFile = [System.IO.Path]::GetTempFileName()
try {
    # Pass each original argument as data, including spaces, quotes and Unicode.
    $json = ConvertTo-Json -InputObject @($ToolArguments) -Compress
    [System.IO.File]::WriteAllText($argumentFile, $json, [System.Text.UTF8Encoding]::new($false))
    $onWindows = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
    $wrapper = Join-Path $RepositoryRoot $(if ($onWindows) { 'gradlew.bat' } else { 'gradlew' })
    & $wrapper -p $RepositoryRoot ':tools:maintenance:run' "-PmaintenanceArgsFile=$argumentFile" '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Maintenance tool failed with exit code $LASTEXITCODE" }
} finally {
    Remove-Item -LiteralPath $argumentFile -Force
}
