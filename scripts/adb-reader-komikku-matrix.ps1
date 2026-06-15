param(
    [string] $Package = "darkaxt.navic",
    [string] $ApkPath,
    [string] $ExpectedVersionName,
    [string] $ArtifactRoot,
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

function Invoke-ReaderMatrixStep {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [string[]] $TapFraction = @(),
        [string[]] $SwipeFraction = @(),
        [switch] $ValidateReaderTaps,
        [switch] $RequireReaderTapAction,
        [switch] $RequireNativeSwipeAction,
        [switch] $RequireShellCoverSwipe,
        [switch] $RequireShellCoverCommand,
        [switch] $RequireTextureDiagnostics,
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
    if ($ValidateReaderTaps) {
        $args += "-ValidateReaderTaps"
    }
    if ($RequireReaderTapAction) {
        $args += "-RequireReaderTapAction"
    }
    if ($RequireNativeSwipeAction) {
        $args += "-RequireNativeSwipeAction"
    }
    if ($RequireShellCoverSwipe) {
        $args += "-RequireShellCoverSwipe"
    }
    if ($RequireShellCoverCommand) {
        $args += "-RequireShellCoverCommand"
    }
    if ($RequireTextureDiagnostics) {
        $args += "-RequireTextureDiagnostics"
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
    -Name "edge-tap-next" `
    -TapFraction @("0.90,0.50,900") `
    -ValidateReaderTaps `
    -RequireReaderTapAction `
    -RequireTextureDiagnostics

Invoke-ReaderMatrixStep `
    -Name "edge-tap-previous" `
    -TapFraction @("0.10,0.50,900") `
    -ValidateReaderTaps `
    -RequireReaderTapAction `
    -RequireTextureDiagnostics

Invoke-ReaderMatrixStep `
    -Name "drag-next" `
    -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
    -RequireNativeSwipeAction `
    -RequireTextureDiagnostics

Invoke-ReaderMatrixStep `
    -Name "drag-previous" `
    -SwipeFraction @("0.18,0.52,0.82,0.52,420,1000") `
    -RequireNativeSwipeAction `
    -RequireTextureDiagnostics

if ($IncludeCoverChecks) {
    Invoke-ReaderMatrixStep `
        -Name "cover-drag-next" `
        -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
        -RequireShellCoverSwipe `
        -RequireShellCoverCommand
}

Write-Host "Komikku reader matrix artifacts: $ArtifactRoot"
