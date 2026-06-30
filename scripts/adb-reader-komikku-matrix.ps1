param(
    [string] $Package = "darkaxt.navic",
    [string] $DeviceSerial,
    [string] $ApkPath,
    [string] $ExpectedVersionName,
    [string] $ArtifactRoot,
    [string] $EnvFile = "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env",
    [switch] $PrepareReaderLaunch,
    [string] $PrepareStartProgress = "0",
    [string] $PreparePublicationUrl,
    [string] $PrepareResourceHref,
    [string] $PrepareBookId,
    [string] $PrepareTitle,
    [string] $PrepareKind,
    [string] $PrepareFormat,
    [string] $PrepareStartHref,
    [string] $PrepareStartCfi,
    [string] $PrepareWhispersyncSidecarUrl,
    [string] $PrepareWhispersyncArtifactId,
    [string] $PrepareWhispersyncAudiobookId,
    [string] $PrepareWhispersyncAudiobookBookFileId,
    [string] $PrepareWhispersyncAudiobookTitle,
    [ValidateSet("", "next", "previous")]
    [string] $RequireTextureDirection = "",
    [switch] $NoLaunch,
    [switch] $IncludeCoverChecks,
    [switch] $IncludePdfChecks,
    [switch] $IncludeRailEndpointChecks,
    [switch] $OnlyPdfChecks,
    [switch] $ContinueOnFailure
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$smokeScript = Join-Path $scriptRoot "adb-reader-smoke.ps1"
$installReaderDevScript = Join-Path $scriptRoot "install-reader-dev.ps1"
if (-not (Test-Path -LiteralPath $smokeScript -PathType Leaf)) {
    throw "Missing reader smoke script: $smokeScript"
}
if ($PrepareReaderLaunch -and -not (Test-Path -LiteralPath $installReaderDevScript -PathType Leaf)) {
    throw "Missing reader dev install script: $installReaderDevScript"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($ArtifactRoot)) {
    $ArtifactRoot = Join-Path $scriptRoot "..\captures\reader-komikku-matrix\$timestamp"
}
$ArtifactRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArtifactRoot)
New-Item -ItemType Directory -Force -Path $ArtifactRoot | Out-Null

$matrixResults = New-Object System.Collections.Generic.List[object]
$matrixFailures = New-Object System.Collections.Generic.List[object]

function Record-ReaderMatrixResult {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [Parameter(Mandatory = $true)]
        [string] $Status,
        [Parameter(Mandatory = $true)]
        [string] $ArtifactDir,
        [string] $ErrorMessage = ""
    )

    $result = [pscustomobject]@{
        Name = $Name
        Status = $Status
        ArtifactDir = $ArtifactDir
        Error = $ErrorMessage
    }
    $matrixResults.Add($result)
    if ($Status -ne "PASS") {
        $matrixFailures.Add($result)
    }
}

function Write-ReaderMatrixSummary {
    $summaryPath = Join-Path $ArtifactRoot "reader-matrix-summary.csv"
    $failurePath = Join-Path $ArtifactRoot "reader-matrix-failures.txt"

    $matrixResults | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding utf8
    if ($matrixFailures.Count -gt 0) {
        $matrixFailures |
            ForEach-Object { "$($_.Status) $($_.Name): $($_.Error) [$($_.ArtifactDir)]" } |
            Out-File -Encoding utf8 $failurePath
    } else {
        "No matrix failures." | Out-File -Encoding utf8 $failurePath
    }
}

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
        [switch] $RequireNativeShellCover,
        [switch] $RequireNoReaderCenterDispatch,
        [switch] $RequireTextureDiagnostics,
        [switch] $RequirePdfDiagnostics,
        [int] $RequirePdfRendererIndex = -1,
        [string[]] $PostProbeAction = @(),
        [ValidateSet("", "internal-link-native", "phase3-events", "annotation-roundtrip", "selection-payload", "relocation-payload", "runtime-state", "page-box", "visible-page-content", "pdf-visible-page", "font-size", "font-size-publisher-styles", "location-snapshot", "chapter-progress-endpoints", "chapter-progress-current-endpoints", "whispersync-audio-follow", "whispersync-page-scoped-control", "whispersync-companion-progress", "whispersync-char-offset-overlay")]
        [string] $ReaderDevtoolsProbe = "",
        [ValidateSet("", "internal-link-native", "phase3-events", "annotation-roundtrip", "selection-payload", "relocation-payload", "runtime-state", "page-box", "visible-page-content", "pdf-visible-page", "font-size", "font-size-publisher-styles", "location-snapshot", "chapter-progress-endpoints", "chapter-progress-current-endpoints", "whispersync-audio-follow", "whispersync-page-scoped-control", "whispersync-companion-progress", "whispersync-char-offset-overlay")]
        [string] $PostActionReaderDevtoolsProbe = "",
        [ValidateSet("", "start", "end")]
        [string] $RequirePostActionChapterPageEndpoint = "",
        [ValidateSet("", "next", "previous")]
        [string] $RequireTextureDirection = "",
        [switch] $Launch,
        [switch] $InstallApk
    )

    $artifactDir = Join-Path $ArtifactRoot $Name
    $smokeArgs = @{
        Package = $Package
        ArtifactDir = $artifactDir
        CaptureReaderDiagnostics = $true
    }

    if ($InstallApk -and -not [string]::IsNullOrWhiteSpace($ApkPath)) {
        $smokeArgs.ApkPath = $ApkPath
    }
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $smokeArgs.DeviceSerial = $DeviceSerial
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedVersionName)) {
        $smokeArgs.ExpectedVersionName = $ExpectedVersionName
    }
    if (-not $Launch -or $NoLaunch) {
        $smokeArgs.NoLaunch = $true
    }
    if ($TapFraction.Count -gt 0) {
        $smokeArgs.TapFraction = $TapFraction
    }
    if ($SwipeFraction.Count -gt 0) {
        $smokeArgs.SwipeFraction = $SwipeFraction
    }
    if ($LongPressFraction.Count -gt 0) {
        $smokeArgs.LongPressFraction = $LongPressFraction
    }
    if ($ValidateReaderTaps) {
        $smokeArgs.ValidateReaderTaps = $true
    }
    if ($RequireReaderTapAction) {
        $smokeArgs.RequireReaderTapAction = $true
    }
    if ($RequireNativeSwipeAction) {
        $smokeArgs.RequireNativeSwipeAction = $true
    }
    if ($RequireNativeLongTap) {
        $smokeArgs.RequireNativeLongTap = $true
    }
    if ($RequireShellCoverSwipe) {
        $smokeArgs.RequireShellCoverSwipe = $true
    }
    if ($RequireShellCoverCommand) {
        $smokeArgs.RequireShellCoverCommand = $true
    }
    if ($RequireNativeShellCover) {
        $smokeArgs.RequireNativeShellCover = $true
    }
    if ($RequireNoReaderCenterDispatch) {
        $smokeArgs.RequireNoReaderCenterDispatch = $true
    }
    if ($RequireTextureDiagnostics) {
        $smokeArgs.RequireTextureDiagnostics = $true
    }
    if ($RequirePdfDiagnostics) {
        $smokeArgs.RequirePdfDiagnostics = $true
    }
    if ($RequirePdfRendererIndex -ge 0) {
        $smokeArgs.RequirePdfRendererIndex = $RequirePdfRendererIndex
    }
    if ($PostProbeAction.Count -gt 0) {
        $smokeArgs.PostProbeAction = $PostProbeAction
    }
    if (-not [string]::IsNullOrWhiteSpace($ReaderDevtoolsProbe)) {
        $smokeArgs.ReaderDevtoolsProbe = $ReaderDevtoolsProbe
    }
    if (-not [string]::IsNullOrWhiteSpace($PostActionReaderDevtoolsProbe)) {
        $smokeArgs.PostActionReaderDevtoolsProbe = $PostActionReaderDevtoolsProbe
    }
    if (-not [string]::IsNullOrWhiteSpace($RequirePostActionChapterPageEndpoint)) {
        $smokeArgs.RequirePostActionChapterPageEndpoint = $RequirePostActionChapterPageEndpoint
    }
    $stepTextureDirection = if (-not [string]::IsNullOrWhiteSpace($RequireTextureDirection)) {
        $RequireTextureDirection
    } elseif ($RequireTextureDiagnostics -and -not [string]::IsNullOrWhiteSpace($script:RequireTextureDirection)) {
        $script:RequireTextureDirection
    } else {
        ""
    }
    if (-not [string]::IsNullOrWhiteSpace($stepTextureDirection)) {
        $smokeArgs.RequireTextureDirection = $stepTextureDirection
    }

    Write-Host "reader-matrix step: $Name"
    try {
        & $smokeScript @smokeArgs
        if ($LASTEXITCODE -ne 0) {
            throw "adb-reader-smoke exited with code $LASTEXITCODE"
        }
        Record-ReaderMatrixResult -Name $Name -Status "PASS" -ArtifactDir $artifactDir
    } catch {
        $errorMessage = $_.Exception.Message
        Record-ReaderMatrixResult -Name $Name -Status "FAIL" -ArtifactDir $artifactDir -ErrorMessage $errorMessage
        Write-Host "reader-matrix step failed: $Name"
        Write-Host $errorMessage
        if (-not $ContinueOnFailure) {
            Write-ReaderMatrixSummary
            throw "reader-matrix step failed: $Name"
        }
    }
}

function Invoke-ReaderMatrixPrepareLaunch {
    $prepareArgs = @{
        EnvFile = $EnvFile
        Package = $Package
        NoBuild = $true
        NoInstall = $true
        RequireReaderLaunch = $true
        StartProgress = $PrepareStartProgress
    }
    if (-not [string]::IsNullOrWhiteSpace($PreparePublicationUrl)) {
        $prepareArgs.PublicationUrl = $PreparePublicationUrl
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareResourceHref)) {
        $prepareArgs.ResourceHref = $PrepareResourceHref
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareBookId)) {
        $prepareArgs.BookId = $PrepareBookId
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareTitle)) {
        $prepareArgs.Title = $PrepareTitle
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareKind)) {
        $prepareArgs.Kind = $PrepareKind
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareFormat)) {
        $prepareArgs.Format = $PrepareFormat
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareStartHref)) {
        $prepareArgs.StartHref = $PrepareStartHref
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareStartCfi)) {
        $prepareArgs.StartCfi = $PrepareStartCfi
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareWhispersyncSidecarUrl)) {
        $prepareArgs.WhispersyncSidecarUrl = $PrepareWhispersyncSidecarUrl
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareWhispersyncArtifactId)) {
        $prepareArgs.WhispersyncArtifactId = $PrepareWhispersyncArtifactId
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareWhispersyncAudiobookId)) {
        $prepareArgs.WhispersyncAudiobookId = $PrepareWhispersyncAudiobookId
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareWhispersyncAudiobookBookFileId)) {
        $prepareArgs.WhispersyncAudiobookBookFileId = $PrepareWhispersyncAudiobookBookFileId
    }
    if (-not [string]::IsNullOrWhiteSpace($PrepareWhispersyncAudiobookTitle)) {
        $prepareArgs.WhispersyncAudiobookTitle = $PrepareWhispersyncAudiobookTitle
    }
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $prepareArgs.DeviceSerial = $DeviceSerial
    }

    Write-Host "reader-matrix prepare: launching readerdev through install-reader-dev.ps1 at progress $PrepareStartProgress"
    & $installReaderDevScript @prepareArgs
    if ($LASTEXITCODE -ne 0) {
        throw "install-reader-dev exited with code $LASTEXITCODE during matrix prepare launch"
    }
}

function Invoke-ReaderCoverMatrixSteps {
    if ($IncludeCoverChecks) {
        Invoke-ReaderMatrixStep `
            -Name "baseline-native-cover" `
            -RequireNativeShellCover

        Invoke-ReaderMatrixStep `
            -Name "cover-center-tap-toggle" `
            -TapFraction @("0.50,0.50,700", "0.50,0.50,700") `
            -ValidateReaderTaps `
            -RequireReaderTapAction

        Invoke-ReaderMatrixStep `
            -Name "cover-drag-next" `
            -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
            -RequireShellCoverSwipe `
            -RequireShellCoverCommand
    } else {
        Invoke-ReaderMatrixStep `
            -Name "enter-readable-content" `
            -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000")
    }
}

function Invoke-ReadableContentMatrixSteps {
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
        -Name "drag-next" `
        -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
        -RequireNativeSwipeAction `
        -RequireTextureDiagnostics `
        -RequireTextureDirection "next"

    Invoke-ReaderMatrixStep `
        -Name "texture-next-walk" `
        -TapFraction $ReaderNextWalkTapFractions `
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
        -Name "drag-previous" `
        -SwipeFraction @("0.18,0.52,0.82,0.52,420,1000") `
        -RequireNativeSwipeAction `
        -RequireTextureDiagnostics `
        -RequireTextureDirection "previous"

    Invoke-ReaderMatrixStep `
        -Name "texture-previous-walk" `
        -TapFraction $ReaderPreviousWalkTapFractions `
        -ValidateReaderTaps `
        -RequireReaderTapAction `
        -RequireTextureDiagnostics `
        -RequireTextureDirection "previous"
}

function Invoke-ReaderRailEndpointMatrixSteps {
    if (-not $IncludeRailEndpointChecks) {
        return
    }

    Invoke-ReaderMatrixStep `
        -Name "chapter-rail-native-start" `
        -TapFraction @("0.50,0.50,700") `
        -ReaderDevtoolsProbe "location-snapshot" `
        -PostProbeAction @("tapDescFraction:Chapter page slider,0.0,0.5,1500") `
        -PostActionReaderDevtoolsProbe "location-snapshot" `
        -RequirePostActionChapterPageEndpoint "start"

    Invoke-ReaderMatrixStep `
        -Name "chapter-rail-native-end" `
        -ReaderDevtoolsProbe "location-snapshot" `
        -PostProbeAction @("tapDescFraction:Chapter page slider,1.0,0.5,1500") `
        -PostActionReaderDevtoolsProbe "location-snapshot" `
        -RequirePostActionChapterPageEndpoint "end"
}

if ($PrepareReaderLaunch) {
    Invoke-ReaderMatrixPrepareLaunch
    if (-not $NoLaunch) {
        Write-Host "reader-matrix prepare: prepared launch owns reader state; smoke steps will behave as if -NoLaunch was set"
    }
}

Invoke-ReaderMatrixStep `
    -Name "baseline-current-reader" `
    -Launch:(!$NoLaunch -and !$PrepareReaderLaunch) `
    -InstallApk:(!$NoLaunch -and !$PrepareReaderLaunch)

if (-not $OnlyPdfChecks) {
    Invoke-ReaderCoverMatrixSteps
    Invoke-ReadableContentMatrixSteps
    Invoke-ReaderRailEndpointMatrixSteps
}

if ($IncludePdfChecks -or $OnlyPdfChecks) {
    Invoke-ReaderMatrixStep `
        -Name "pdf-baseline" `
        -RequirePdfDiagnostics `
        -RequirePdfRendererIndex 0 `
        -ReaderDevtoolsProbe "pdf-visible-page"

    Invoke-ReaderMatrixStep `
        -Name "pdf-edge-tap-next" `
        -TapFraction @("0.90,0.50,900") `
        -ValidateReaderTaps `
        -RequireReaderTapAction `
        -RequirePdfDiagnostics `
        -RequirePdfRendererIndex 1 `
        -PostActionReaderDevtoolsProbe "pdf-visible-page"

    Invoke-ReaderMatrixStep `
        -Name "pdf-edge-tap-previous" `
        -TapFraction @("0.10,0.50,900") `
        -ValidateReaderTaps `
        -RequireReaderTapAction `
        -RequirePdfDiagnostics `
        -RequirePdfRendererIndex 0 `
        -PostActionReaderDevtoolsProbe "pdf-visible-page"

    Invoke-ReaderMatrixStep `
        -Name "pdf-drag-next" `
        -SwipeFraction @("0.82,0.52,0.18,0.52,420,1000") `
        -RequireNativeSwipeAction `
        -RequirePdfDiagnostics `
        -RequirePdfRendererIndex 1 `
        -PostActionReaderDevtoolsProbe "pdf-visible-page"

    Invoke-ReaderMatrixStep `
        -Name "pdf-drag-previous" `
        -SwipeFraction @("0.18,0.52,0.82,0.52,420,1000") `
        -RequireNativeSwipeAction `
        -RequirePdfDiagnostics `
        -RequirePdfRendererIndex 0 `
        -PostActionReaderDevtoolsProbe "pdf-visible-page"
}

Write-Host "Komikku reader matrix artifacts: $ArtifactRoot"
Write-ReaderMatrixSummary

if ($matrixFailures.Count -gt 0) {
    throw "Komikku reader matrix failed: $($matrixFailures.Count) step(s). See $ArtifactRoot"
}
