#requires -Version 7.0

param(
    [string]$Repository = "FreDiabetics/sugarlicious",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSEdition -ne "Core") {
    throw "PowerShell 7+ (pwsh) is required."
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) was not found."
}

gh auth status | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated."
}

$body = @{
    required_status_checks = @{
        strict = $true
        contexts = @("verify")
    }
    enforce_admins = $true
    required_pull_request_reviews = @{
        dismiss_stale_reviews = $true
        require_code_owner_reviews = $false
        required_approving_review_count = 0
        require_last_push_approval = $false
    }
    restrictions = $null
    required_linear_history = $false
    allow_force_pushes = $false
    allow_deletions = $false
    block_creations = $false
    required_conversation_resolution = $true
    lock_branch = $false
    allow_fork_syncing = $true
} | ConvertTo-Json -Depth 8

# Fail locally before calling GitHub if request serialization ever becomes invalid.
$null = $body | ConvertFrom-Json

$temp = [System.IO.Path]::GetTempFileName()
try {
    # gh api --input forwards the request file verbatim. Keep the JSON request
    # explicitly UTF-8 without BOM even though PowerShell 7 already defaults to UTF-8.
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($temp, $body, $utf8NoBom)

    Write-Host "`nEnabling branch protection for $Repository/$Branch ..."

    $apiOutput = gh api --method PUT `
        -H "Accept: application/vnd.github+json" `
        -H "X-GitHub-Api-Version: 2026-03-10" `
        "repos/$Repository/branches/$Branch/protection" `
        --input $temp 2>&1

    $apiExit = $LASTEXITCODE

    if ($apiExit -ne 0) {
        $details = ($apiOutput -join [Environment]::NewLine).Trim()
        throw "Branch protection could not be enabled. GitHub API: $details"
    }

    $protection = gh api `
        -H "Accept: application/vnd.github+json" `
        -H "X-GitHub-Api-Version: 2026-03-10" `
        "repos/$Repository/branches/$Branch/protection" | ConvertFrom-Json

    $contexts = @($protection.required_status_checks.contexts)
    if (-not $protection.enforce_admins.enabled) { throw "Verification failed: admin enforcement is not enabled." }
    if (-not $protection.required_status_checks.strict) { throw "Verification failed: required status checks are not strict." }
    if ($contexts -notcontains "verify") { throw "Verification failed: required status check 'verify' is missing." }
    if ($protection.allow_force_pushes.enabled) { throw "Verification failed: force pushes are still allowed." }
    if ($protection.allow_deletions.enabled) { throw "Verification failed: branch deletion is still allowed." }
    if (-not $protection.required_conversation_resolution.enabled) { throw "Verification failed: conversation resolution is not required." }
    if ($null -eq $protection.required_pull_request_reviews) { throw "Verification failed: pull-request protection is not enabled." }

    Write-Host "`nMAIN PROTECTION ACTIVE"
    Write-Host "- Changes only through pull requests"
    Write-Host "- CI check 'verify' required and branch must be up to date"
    Write-Host "- Admin/owner is subject to the rules"
    Write-Host "- Force pushes blocked"
    Write-Host "- Branch deletion blocked"
    Write-Host "- Review conversations must be resolved"
    Write-Host "- 0 required approvals so a single-maintainer repository is not blocked"
    Write-Host "- CODEOWNERS exists; required approvals can be enabled when a second maintainer is added"
}
finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
