param(
    [string] $Package = "darkaxt.navic",
    [string] $ApkPath,
    [string] $ExpectedVersionName,
    [string] $ArtifactRoot,
    [ValidateSet("", "next", "previous")]
    [string] $RequireTextureDirection = "",
    [switch] $NoLaunch,
    [switch] $IncludeCoverChecks
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$smokeScript = Join-Path $scriptRoot "adb-reader-smoke.ps1"
if (-not (Test-Path -LiteralPath $smokeScript -PathType Leaf)) {
    throw "Missing reader smoke script: $smokeScript"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($ArtifactRoot)) {
    $ArtifactRoot = Join-Path $scriptRoot "..\captures\reader-komikku-matrix\$timestamp"
}
$ArtifactRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArtifactRoot)
New-Item -ItemType Directory -Force -Path $ArtifactRoot | Out-Null

$ReaderNextWalkTapFractions = @(
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,650",
    "0.90,0.50,900"
)
$ReaderPreviousWalkTapFractions = @(
    "0.10,0.50,650",
    "0.10,0.50,650",
    "0.10,0.50,650",
    "0.10,0.50,650",
    "0.10,0.50,650",
    "0.10,0.50,900"
)

function Invoke-ReaderMatrixStep {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [string[]] $TapFraction = @(),
        [string[]] $SwipeFraction = @(),
        [string[]] $LongPressFraction = @(),
        [switch] $ValidateReaderTaps,
        [switch] $RequireReaderTapAction,
        [switch] $RequireNativeSwipeAction,
        [switch] $RequireNativeLongTap,
        [switch] $RequireShellCoverSwipe,
        [switch] $RequireShellCoverCommand,
        [switch] $RequireNoReaderCenterDispatch,
        [switch] $RequireTextureDiagnostics,
        [ValidateSet("", "next", "previous")]
        [string] $RequireTextureDirection = "",
        [switch] $Launch,
        [switch] $InstallApk
    )

    $artifactDir = Join-Path $ArtifactRoot $Name
    $args = @(
        "-Package", $Package,
        "-ArtifactDir", $artifactDir,
        "-CaptureReaderDiagnostics"
    )

    if ($InstallApk -and -not [string]::IsNullOrWhiteSpace($ApkPath)) {
        $args += @("-ApkPath", $ApkPath)
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedVersionName)) {
        $args += @("-ExpectedVersionName", $ExpectedVersionName)
    }
    if (-not $Launch -or $NoLaunch) {
        $args += "-NoLaunch"
    }
    foreach ($tap in $TapFraction) {
        $args += @("-TapFraction", $tap)
    }
    foreach ($swipe in $SwipeFraction) {
        $args += @("-SwipeFraction", $swipe)
    }
    foreach ($longPress in $LongPressFraction) {
        $args += @("-LongPressFraction", $longPress)
    }
    if ($ValidateReaderTaps) {
        $args += "-ValidateReaderTaps"
    }
    if ($RequireReaderTapAction) {
        $args += "-RequireReaderTapAction"
    }
    if ($RequireNativeSwipeAction) {
        $args += "-RequireNativeSwipeAction"
    }
    if ($RequireNativeLongTap) {
        $args += "-RequireNativeLongTap"
    }
    if ($RequireShellCoverSwipe) {
        $args += "-RequireShellCoverSwipe"
    }
    if ($RequireShellCoverCommand) {
        $args += "-RequireShellCoverCommand"
    }
    if ($RequireNoReaderCenterDispatch) {
        $args += "-RequireNoReaderCenterDispatch"
    }
    if ($RequireTextureDiagnostics) {
        $args += "-RequireTextureDiagnostics"
    }
    $stepTextureDirection = if (-not [string]::IsNullOrWhiteSpace($RequireTextureDirection)) {
        $RequireTextureDirection
    } elseif ($RequireTextureDiagnostics -and -not [string]::IsNullOrWhiteSpace($script:RequireTextureDirection)) {
        $script:RequireTextureDirection
    } else {
        ""
    }
    if (-not [string]::IsNullOrWhiteSpace($stepTextureDirection)) {
        $args += @("-RequireTextureDirection", $stepTextureDirection)
    }

    Write-Host "reader-matrix step: $Name"
    & $smokeScript @args
    if ($LASTEXITCODE -ne 0) {
        throw "reader-matrix step failed: $Name"
    }
}

Invoke-ReaderMatrixStep `
    -Name "baseline-current-reader" `
    -Launch:(!$NoLaunch) `
    -InstallApk:(!$NoLaunch)

Invoke-ReaderMatrixStep `
    -Name "center-tap-toggle" `
    -TapFraction @("0.50,0.50,700", "0.50,0.50,700") `
    -ValidateReaderTaps `
    -RequireReaderTapAction

Invoke-ReaderMatrixStep `
    -Name "native-long-press-center" `
    -LongPressFraction @("0.50,0.50,950,900") `
    -RequireNativeLongTap `
    -RequireNoReaderCenterDispatch

Invoke-ReaderMatrixStep `
    -Name "edge-tap-next" `
    -TapFraction @("0.90,0.50,900") `
    -ValidateReaderTaps `
    -RequireReaderTapAction `
    -RequireTextureDiagnostics `
    -RequireTextureDirection "next"

Invoke-ReaderMatrixStep `
    -Name "edge-tap-previous" `
    -TapFraction @("0.10,0.50,900") `
    -ValidateReaderTaps `
    -RequireReaderTapAction `
    -RequireTextureDiagnostics `
    -RequireTextureDirection "previous"

Invoke-ReaderMatrixStep `
    -Name "drag-next" `
    -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
    -RequireNativeSwipeAction `
    -RequireTextureDiagnostics `
    -RequireTextureDirection "next"

Invoke-ReaderMatrixStep `
    -Name "drag-previous" `
    -SwipeFraction @("0.18,0.52,0.82,0.52,420,1000") `
    -RequireNativeSwipeAction `
    -RequireTextureDiagnostics `
    -RequireTextureDirection "previous"

Invoke-ReaderMatrixStep `
    -Name "texture-next-walk" `
    -TapFraction $ReaderNextWalkTapFractions `
    -ValidateReaderTaps `
    -RequireReaderTapAction `
    -RequireTextureDiagnostics `
    -RequireTextureDirection "next"

Invoke-ReaderMatrixStep `
    -Name "texture-previous-walk" `
    -TapFraction $ReaderPreviousWalkTapFractions `
    -ValidateReaderTaps `
    -RequireReaderTapAction `
    -RequireTextureDiagnostics `
    -RequireTextureDirection "previous"

if ($IncludeCoverChecks) {
    Invoke-ReaderMatrixStep `
        -Name "cover-drag-next" `
        -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
        -RequireShellCoverSwipe `
        -RequireShellCoverCommand
}

Write-Host "Komikku reader matrix artifacts: $ArtifactRoot"
