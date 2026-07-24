$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
. (Join-Path $PSScriptRoot 'reader-privacy-safe-evidence.ps1')

function Assert-Throws([scriptblock] $Action, [string] $Name) {
    $threw = $false
    try { & $Action } catch { $threw = $true }
    if (-not $threw) { throw "$Name did not throw" }
}

function New-SmokeEvidence([string] $Mode = 'single', [int] $GutterPx = 0) {
    [ordered]@{
        SchemaVersion = 1
        Status = 'complete'
        Package = 'darkaxt.navic.readerdev'
        DeviceSerial = 'emulator-5554'
        StartedUtc = '2026-07-18T12:00:00.0000000Z'
        CompletedUtc = '2026-07-18T12:00:01.0000000Z'
        Assertions = [ordered]@{
            ReaderForeground = $true
            ReaderPublicationReady = $true
            NoConsoleErrors = $true
            NativeLongTap = $true
            NativeSwipe = $true
            NeutralVisualState = $true
            SpreadGeometryValid = $true
            PositiveGutter = $true
            TextureStateValid = $true
        }
        Counts = [ordered]@{
            BridgeEvents = 2
            GestureEvents = 1
            GeometrySamples = 1
            TextureSamples = 0
        }
        Geometry = [ordered]@{
            Mode = $Mode
            ViewportWidthPx = 1080
            ViewportHeightPx = 2400
            GutterPx = $GutterPx
        }
    }
}

function Copy-JsonObject([object] $Value) {
    $Value | ConvertTo-Json -Depth 10 | ConvertFrom-Json
}

function Write-Json([string] $Path, [object] $Value) {
    $Value | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $Path
}

function New-TestRoot([string] $Parent, [string] $Name) {
    $path = Join-Path $Parent $Name
    New-Item -ItemType Directory -Path $path | Out-Null
    return $path
}

function Write-SmokeRoot(
    [string] $Parent,
    [string] $Name,
    [object] $Evidence
) {
    $root = New-TestRoot $Parent $Name
    Write-Json (Join-Path $root 'privacy-safe-smoke.json') $Evidence
    return $root
}

function Write-MatrixRoot(
    [string] $Parent,
    [string] $Name,
    [object[]] $Rows
) {
    $root = New-TestRoot $Parent $Name
    foreach ($row in $Rows) {
        $caseRoot = Join-Path $root ([string]$row.Case)
        if (-not (Test-Path -LiteralPath $caseRoot)) {
            New-Item -ItemType Directory -Path $caseRoot | Out-Null
            Write-Json `
                (Join-Path $caseRoot 'privacy-safe-smoke.json') `
                (New-SmokeEvidence)
        }
    }
    $Rows | Export-Csv `
        -LiteralPath (Join-Path $root 'reader-matrix-summary.csv') `
        -NoTypeInformation
    return $root
}

$testRoot = Join-Path ([IO.Path]::GetTempPath()) (
    'navic-reader-privacy-safe-' + [guid]::NewGuid().ToString('N')
)
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    $singleRoot = Write-SmokeRoot `
        $testRoot 'accepted-single' (New-SmokeEvidence)
    Assert-ReaderPrivacySafeArtifactTree -Root $singleRoot -Kind smoke

    $spreadRoot = Write-SmokeRoot `
        $testRoot 'accepted-spread' (New-SmokeEvidence spread 24)
    Assert-ReaderPrivacySafeArtifactTree -Root $spreadRoot -Kind smoke

    $sealedRoot = Write-SmokeRoot `
        $testRoot 'accepted-sealed' (New-SmokeEvidence)
    $sealedArtifact = Get-Item -LiteralPath (
        Join-Path $sealedRoot 'privacy-safe-smoke.json'
    )
    $completion = [ordered]@{
        Status = 'complete'
        AcceptanceId = '0123456789abcdef0123456789abcdef'
        GitCommit = '0123456789abcdef0123456789abcdef01234567'
        ApkSha256 = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
        DeviceSerial = 'emulator-5554'
        DeviceClass = 'emulator'
        Kind = 'smoke'
        StartedUtc = '2026-07-18T12:00:00.0000000Z'
        CompletedUtc = '2026-07-18T12:00:01.0000000Z'
        RequiredArtifact = 'privacy-safe-smoke.json'
        Artifacts = @([ordered]@{
            Path = 'privacy-safe-smoke.json'
            Sha256 = (Get-FileHash -Algorithm SHA256 $sealedArtifact).Hash
            Bytes = $sealedArtifact.Length
        })
    }
    Write-Json (Join-Path $sealedRoot 'run-complete.json') $completion
    Assert-ReaderPrivacySafeArtifactTree `
        -Root $sealedRoot `
        -Kind smoke `
        -AllowCompletionManifest
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $sealedRoot -Kind smoke
    } 'unexpected completion manifest fixture'

    $matrixRows = @(
        [pscustomobject][ordered]@{
            Package = 'darkaxt.navic.readerdev'
            DeviceSerial = 'emulator-5554'
            Case = 'baseline'
            Status = 'PASS'
            PrivacySafeArtifact = 'baseline/privacy-safe-smoke.json'
        }
        [pscustomobject][ordered]@{
            Package = 'darkaxt.navic.readerdev'
            DeviceSerial = 'emulator-5554'
            Case = 'drag-next'
            Status = 'PASS'
            PrivacySafeArtifact = 'drag-next/privacy-safe-smoke.json'
        }
    )
    $matrixRoot = Write-MatrixRoot $testRoot 'accepted-matrix' $matrixRows
    Assert-ReaderPrivacySafeArtifactTree -Root $matrixRoot -Kind komikku

    $extraLogRoot = Write-SmokeRoot `
        $testRoot 'extra-log' (New-SmokeEvidence)
    'raw' | Set-Content (Join-Path $extraLogRoot 'logcat-full.log')
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $extraLogRoot -Kind smoke
    } 'extra raw log fixture'

    $screenshotRoot = Write-SmokeRoot `
        $testRoot 'screenshot' (New-SmokeEvidence)
    [IO.File]::WriteAllBytes(
        (Join-Path $screenshotRoot 'screen.png'),
        [byte[]](1, 2, 3)
    )
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $screenshotRoot -Kind smoke
    } 'screenshot fixture'

    $unknownJson = Copy-JsonObject (New-SmokeEvidence)
    $unknownJson | Add-Member -NotePropertyName Unexpected -NotePropertyValue 1
    $unknownJsonRoot = Write-SmokeRoot `
        $testRoot 'unknown-json-key' $unknownJson
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $unknownJsonRoot -Kind smoke
    } 'unknown JSON key fixture'

    $falseAssertion = Copy-JsonObject (New-SmokeEvidence)
    $falseAssertion.Assertions.NativeSwipe = $false
    $falseAssertionRoot = Write-SmokeRoot `
        $testRoot 'false-assertion' $falseAssertion
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $falseAssertionRoot -Kind smoke
    } 'false assertion fixture'

    $negativeCount = Copy-JsonObject (New-SmokeEvidence)
    $negativeCount.Counts.BridgeEvents = -1
    $negativeCountRoot = Write-SmokeRoot `
        $testRoot 'negative-count' $negativeCount
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $negativeCountRoot -Kind smoke
    } 'negative count fixture'

    $zeroSpreadRoot = Write-SmokeRoot `
        $testRoot 'zero-spread-gutter' (New-SmokeEvidence spread 0)
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $zeroSpreadRoot -Kind smoke
    } 'zero spread gutter fixture'

    $duplicateCaseRows = @(
        $matrixRows[0]
        [pscustomobject][ordered]@{
            Package = 'darkaxt.navic.readerdev'
            DeviceSerial = 'emulator-5554'
            Case = 'baseline'
            Status = 'PASS'
            PrivacySafeArtifact = 'other/privacy-safe-smoke.json'
        }
    )
    $duplicateCaseRoot = Write-MatrixRoot `
        $testRoot 'duplicate-case' $duplicateCaseRows
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree -Root $duplicateCaseRoot -Kind komikku
    } 'duplicate matrix case fixture'

    $duplicateArtifactRows = @(
        $matrixRows[0]
        [pscustomobject][ordered]@{
            Package = 'darkaxt.navic.readerdev'
            DeviceSerial = 'emulator-5554'
            Case = 'drag-next'
            Status = 'PASS'
            PrivacySafeArtifact = 'baseline/privacy-safe-smoke.json'
        }
    )
    $duplicateArtifactRoot = Write-MatrixRoot `
        $testRoot 'duplicate-artifact' $duplicateArtifactRows
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree `
            -Root $duplicateArtifactRoot `
            -Kind komikku
    } 'duplicate matrix artifact fixture'

    $unknownCompletionRoot = Write-SmokeRoot `
        $testRoot 'unknown-completion-key' (New-SmokeEvidence)
    $unknownCompletion = Copy-JsonObject $completion
    $unknownCompletion | Add-Member `
        -NotePropertyName Unexpected `
        -NotePropertyValue 1
    Write-Json `
        (Join-Path $unknownCompletionRoot 'run-complete.json') `
        $unknownCompletion
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree `
            -Root $unknownCompletionRoot `
            -Kind smoke `
            -AllowCompletionManifest
    } 'unknown completion key fixture'

    $badInventoryRoot = Write-SmokeRoot `
        $testRoot 'bad-completion-inventory' (New-SmokeEvidence)
    $badInventory = Copy-JsonObject $completion
    $badInventory.Artifacts[0].Bytes = 1
    Write-Json `
        (Join-Path $badInventoryRoot 'run-complete.json') `
        $badInventory
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree `
            -Root $badInventoryRoot `
            -Kind smoke `
            -AllowCompletionManifest
    } 'malformed completion inventory fixture'

    $prohibitedString = Copy-JsonObject (New-SmokeEvidence)
    $prohibitedString.Package = 'https://private.example'
    $prohibitedStringRoot = Write-SmokeRoot `
        $testRoot 'prohibited-string' $prohibitedString
    Assert-Throws {
        Assert-ReaderPrivacySafeArtifactTree `
            -Root $prohibitedStringRoot `
            -Kind smoke
    } 'prohibited string fixture'

    Write-Output 'Reader privacy-safe evidence PASS'
} finally {
    Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
}
