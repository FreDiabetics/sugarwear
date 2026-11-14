$ErrorActionPreference = 'Stop'

$root = (Get-Location).Path
. (Join-Path $root 'tools/watchface-catalog.ps1')
$generatedRoot = Join-Path $root 'app-wear/build/generated/watchfacePushAssets'
$generated = Join-Path $generatedRoot 'watchfaces'
$generatedValues = Join-Path $root 'app-wear/build/generated/watchfacePushRes/values'
$toolDir = Join-Path $root 'build/watchface-push/tools'
$gradle =
    if ($env:OS -eq 'Windows_NT') {
        Join-Path $root 'gradlew.bat'
    } else {
        Join-Path $root 'gradlew'
    }

Write-Host 'Building Sugarlicious Watch Face Push packages...'

$buildTasks = @($SUGARLICIOUS_WATCHFACES | ForEach-Object { ":watchfaces:$($_.Module):assembleRelease" })
$buildTasks += 'prepareWatchFaceValidatorCli'
& $gradle @buildTasks

if ($LASTEXITCODE -ne 0) {
    throw 'Watchface build or validator setup failed.'
}

$validator =
    Get-ChildItem $toolDir -Filter 'validator-push-cli-*.jar' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

if (-not $validator) {
    throw "Validator CLI not found in $toolDir"
}

Remove-Item $generatedRoot -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Split-Path $generatedValues -Parent) -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $generated -Force | Out-Null
New-Item -ItemType Directory -Path $generatedValues -Force | Out-Null

foreach ($face in $SUGARLICIOUS_WATCHFACES) {
    $apk =
        Get-ChildItem (Join-Path $root "watchfaces/$($face.Module)/build/outputs/apk/release") -Filter '*.apk' |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

    if (-not $apk) {
        throw "APK missing for $($face.Module)"
    }

    $validationApk = Join-Path $generated "$($face.Out).apk"
    Copy-Item $apk.FullName $validationApk -Force

    Write-Host "Validating $($face.Module)..."

    $validatorOutput =
        & java -jar $validator.FullName `
            "--apk_path=$validationApk" `
            "--package_name=app.sugarlicious" 2>&1 |
            Out-String

    if ($LASTEXITCODE -ne 0) {
        Write-Host $validatorOutput
        throw "Watch Face Push validation failed for $($face.Module)"
    }

    $match =
        [regex]::Match(
            $validatorOutput,
            '(?im)(?:Validation token:|generated token:)\s*([^\r\n]+)'
        )

    if (-not $match.Success) {
        Write-Host $validatorOutput
        throw "Validation token missing for $($face.Module)"
    }

    $tokenPath =
        Join-Path $generated "$($face.Out)_token.txt"

    [System.IO.File]::WriteAllText(
        $tokenPath,
        $match.Groups[1].Value.Trim(),
        (New-Object System.Text.UTF8Encoding($false))
    )

    Write-Host "Prepared $($face.Out)"
}

# Wear OS registers this representative face in the system picker when the marketplace app is
# installed. Once the user activates it, all later variants can replace the same active Push slot.
$defaultApk = Join-Path $generatedRoot 'default_watchface.apk'
$defaultToken = Get-Content (Join-Path $generated 'sugarlicious_digital_token.txt') -Raw
$escapedDefaultToken = [System.Security.SecurityElement]::Escape($defaultToken.Trim())
$defaultTokenResource = Join-Path $generatedValues 'default_watchface_token.xml'

Copy-Item (Join-Path $generated 'sugarlicious_digital.apk') $defaultApk -Force
[System.IO.File]::WriteAllText(
    $defaultTokenResource,
    "<resources>`n    <string name=`"default_wf_token`" translatable=`"false`">$escapedDefaultToken</string>`n</resources>`n",
    (New-Object System.Text.UTF8Encoding($false))
)

Write-Host ''
Write-Host 'OK: Watch Face Push assets prepared.'
Write-Host "Assets: $generated"
Write-Host "Default picker watchface: $defaultApk"
