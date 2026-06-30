param(
    [string] $Package = "darkaxt.navic",
    [string] $DeviceSerial,
    [string] $ApkPath,
    [string] $ArtifactDir,
    [string[]] $Tap = @(),
    [string[]] $TapFraction = @(),
    [string[]] $PostProbeTap = @(),
    [string[]] $PostProbeTapFraction = @(),
    [string[]] $PostProbeAction = @(),
    [string[]] $Swipe = @(),
    [string[]] $SwipeFraction = @(),
    [string[]] $LongPress = @(),
    [string[]] $LongPressFraction = @(),
    [ValidateSet("None", "ReaderHorizontalZones")]
    [string] $TapPreset = "None",
    [string] $ExpectedVersionName,
    [int] $LaunchWaitSeconds = 5,
    [int] $CaptureWaitSeconds = 0,
    [switch] $ValidateReaderTaps,
    [switch] $RequireReaderTapAction,
    [switch] $RequireShellCoverSwipe,
    [switch] $RequireShellCoverDragDiagnostic,
    [switch] $RequireShellCoverCommand,
    [switch] $RequireNativeSwipeAction,
    [switch] $RequireNativeLongTap,
    [switch] $RequireContentTapHandled,
    [string[]] $RequireReaderBridgeEvent = @(),
    [string[]] $RequireReaderEngineCommand = @(),
    [string[]] $RequireReaderLog = @(),
    [switch] $RequireNoReaderConsoleErrors,
    [ValidateSet("", "internal-link-native", "phase3-events", "external-link-prompt", "annotation-roundtrip", "history-controls", "selection-payload", "visible-selection-payload", "visible-selection-clear", "relocation-payload", "runtime-state", "page-box", "visible-page-content", "pdf-visible-page", "font-size", "font-size-publisher-styles", "location-snapshot", "chapter-progress-endpoints", "chapter-progress-current-endpoints", "whispersync-audio-follow", "whispersync-page-scoped-control", "whispersync-companion-progress", "whispersync-char-offset-overlay")]
    [string] $ReaderDevtoolsProbe = "",
    [ValidateSet("", "internal-link-native", "phase3-events", "external-link-prompt", "annotation-roundtrip", "history-controls", "selection-payload", "visible-selection-payload", "visible-selection-clear", "relocation-payload", "runtime-state", "page-box", "visible-page-content", "pdf-visible-page", "font-size", "font-size-publisher-styles", "location-snapshot", "chapter-progress-endpoints", "chapter-progress-current-endpoints", "whispersync-audio-follow", "whispersync-page-scoped-control", "whispersync-companion-progress", "whispersync-char-offset-overlay")]
    [string] $PostActionReaderDevtoolsProbe = "",
    [ValidateSet("", "start", "end")]
    [string] $RequirePostActionChapterPageEndpoint = "",
    [switch] $RequireNoReaderCenterDispatch,
    [switch] $RequireTextureDiagnostics,
    [switch] $RequirePdfDiagnostics,
    [int] $RequirePdfRendererIndex = -1,
    [switch] $RequireNativeShellCover,
    [switch] $RequireNeutralReaderVisualState,
    [ValidateSet("", "next", "previous")]
    [string] $RequireTextureDirection = "",
    [switch] $CaptureReaderDiagnostics,
    [switch] $NoLaunch
)

$ErrorActionPreference = "Stop"

if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $env:ANDROID_SERIAL = $DeviceSerial
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,
        [switch] $PassThru
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & adb @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $exitCode`n$($output -join "`n")"
    }
    if ($PassThru) {
        return @($output)
    }
}

function Invoke-AdbExecOutToFile {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,
        [Parameter(Mandatory = $true)]
        [string] $OutputPath
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = "adb"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Arguments = ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + ($_.Replace('"', '\"')) + '"'
        } else {
            $_
        }
    }) -join " "

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $started = $process.Start()
    if (-not $started) {
        throw "Failed to start adb $($Arguments -join ' ')"
    }

    $stderrTask = $process.StandardError.ReadToEndAsync()
    $fileStream = [System.IO.File]::Create($OutputPath)
    try {
        $process.StandardOutput.BaseStream.CopyTo($fileStream)
    } finally {
        $fileStream.Dispose()
    }
    $process.WaitForExit()
    $stderr = $stderrTask.GetAwaiter().GetResult()

    if ($process.ExitCode -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $($process.ExitCode)`n$stderr"
    }
    $outputFile = Get-Item -LiteralPath $OutputPath
    if ($outputFile.Length -le 0) {
        throw "adb $($Arguments -join ' ') produced an empty file: $OutputPath"
    }
}

function Get-AdbScreenSize {
    $wmSize = (Invoke-Adb @("shell", "wm", "size") -PassThru) -join "`n"
    $sizeMatches = [regex]::Matches($wmSize, '(\d+)x(\d+)')
    if ($sizeMatches.Count -le 0) {
        throw "Could not parse adb shell wm size output: $wmSize"
    }
    $effectiveSize = $sizeMatches[$sizeMatches.Count - 1]
    return [pscustomobject]@{
        Width = [int] $effectiveSize.Groups[1].Value
        Height = [int] $effectiveSize.Groups[2].Value
    }
}

function Convert-TapFraction {
    param(
        [Parameter(Mandatory = $true)]
        [string] $TapSpec,
        [Parameter(Mandatory = $true)]
        [int] $Width,
        [Parameter(Mandatory = $true)]
        [int] $Height
    )

    if ($TapSpec -notmatch '^\s*([0-9]*\.?[0-9]+)\s*,\s*([0-9]*\.?[0-9]+)(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid tap fraction '$TapSpec'. Use xFraction,yFraction or xFraction,yFraction,waitMs."
    }

    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $xFraction = [double]::Parse($Matches[1], $culture)
    $yFraction = [double]::Parse($Matches[2], $culture)
    if ($xFraction -lt 0 -or $xFraction -gt 1 -or $yFraction -lt 0 -or $yFraction -gt 1) {
        throw "Tap fractions must be between 0 and 1: $TapSpec"
    }

    $waitMs = if ($Matches[3]) { [int] $Matches[3] } else { 1000 }
    return "{0},{1},{2}" -f `
        [math]::Round($xFraction * $Width), `
        [math]::Round($yFraction * $Height), `
        $waitMs
}

function Convert-SwipeFraction {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SwipeSpec,
        [Parameter(Mandatory = $true)]
        [int] $Width,
        [Parameter(Mandatory = $true)]
        [int] $Height
    )

    if ($SwipeSpec -notmatch '^\s*([0-9]*\.?[0-9]+)\s*,\s*([0-9]*\.?[0-9]+)\s*,\s*([0-9]*\.?[0-9]+)\s*,\s*([0-9]*\.?[0-9]+)(?:\s*,\s*(\d+))?(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid swipe fraction '$SwipeSpec'. Use x1Fraction,y1Fraction,x2Fraction,y2Fraction or x1Fraction,y1Fraction,x2Fraction,y2Fraction,durationMs,waitMs."
    }

    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $x1Fraction = [double]::Parse($Matches[1], $culture)
    $y1Fraction = [double]::Parse($Matches[2], $culture)
    $x2Fraction = [double]::Parse($Matches[3], $culture)
    $y2Fraction = [double]::Parse($Matches[4], $culture)
    foreach ($fraction in @($x1Fraction, $y1Fraction, $x2Fraction, $y2Fraction)) {
        if ($fraction -lt 0 -or $fraction -gt 1) {
            throw "Swipe fractions must be between 0 and 1: $SwipeSpec"
        }
    }

    $durationMs = if ($Matches[5]) { [int] $Matches[5] } else { 350 }
    $waitMs = if ($Matches[6]) { [int] $Matches[6] } else { 1000 }
    return "{0},{1},{2},{3},{4},{5}" -f `
        [math]::Round($x1Fraction * $Width), `
        [math]::Round($y1Fraction * $Height), `
        [math]::Round($x2Fraction * $Width), `
        [math]::Round($y2Fraction * $Height), `
        $durationMs, `
        $waitMs
}

function Convert-LongPressFraction {
    param(
        [Parameter(Mandatory = $true)]
        [string] $LongPressSpec,
        [Parameter(Mandatory = $true)]
        [int] $Width,
        [Parameter(Mandatory = $true)]
        [int] $Height
    )

    if ($LongPressSpec -notmatch '^\s*([0-9]*\.?[0-9]+)\s*,\s*([0-9]*\.?[0-9]+)(?:\s*,\s*(\d+))?(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid long-press fraction '$LongPressSpec'. Use xFraction,yFraction or xFraction,yFraction,durationMs,waitMs."
    }

    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $xFraction = [double]::Parse($Matches[1], $culture)
    $yFraction = [double]::Parse($Matches[2], $culture)
    if ($xFraction -lt 0 -or $xFraction -gt 1 -or $yFraction -lt 0 -or $yFraction -gt 1) {
        throw "Long-press fractions must be between 0 and 1: $LongPressSpec"
    }

    $durationMs = if ($Matches[3]) { [int] $Matches[3] } else { 950 }
    $waitMs = if ($Matches[4]) { [int] $Matches[4] } else { 1000 }
    return "{0},{1},{2},{3}" -f `
        [math]::Round($xFraction * $Width), `
        [math]::Round($yFraction * $Height), `
        $durationMs, `
        $waitMs
}

function Get-ReaderLogIntField {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Line,
        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    $match = [regex]::Match($Line, "(?:^|\s)$([regex]::Escape($Name))=(-?\d+)")
    if (-not $match.Success) {
        return $null
    }
    return [int]::Parse($match.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-ReaderTextureDirectionSamples {
    param(
        [string[]] $Lines = @(),
        [Parameter(Mandatory = $true)]
        [ValidateSet("next", "previous")]
        [string] $Direction
    )

    $samples = New-Object System.Collections.Generic.List[object]
    $expectedSign = if ($Direction -eq "next") { -1 } else { 1 }
    foreach ($line in $Lines) {
        if ($line -notmatch "surface-texture-scroll") {
            continue
        }
        $directionMatch = [regex]::Match($line, "(?:^|\s)dir=([^\s]+)")
        if (-not $directionMatch.Success -or $directionMatch.Groups[1].Value -ne $Direction) {
            continue
        }

        $x = Get-ReaderLogIntField -Line $line -Name "x"
        $y = Get-ReaderLogIntField -Line $line -Name "y"
        if ($null -eq $x) { $x = 0 }
        if ($null -eq $y) { $y = 0 }

        $xAbs = [math]::Abs($x)
        $yAbs = [math]::Abs($y)
        $dominantAxis = if ($xAbs -ge $yAbs) { "x" } else { "y" }
        $dominantOffset = if ($dominantAxis -eq "x") { $x } else { $y }
        if ([math]::Abs($dominantOffset) -le 1) {
            continue
        }

        $samples.Add([pscustomobject]@{
            Direction = $Direction
            Axis = $dominantAxis
            Offset = $dominantOffset
            ExpectedSign = $expectedSign
            WrongTextureDirection = ([math]::Sign($dominantOffset) -ne $expectedSign)
            Line = $line
        })
    }
    return @($samples.ToArray())
}

function Get-ReaderNativeShellCoverVisible {
    param([Parameter(Mandatory = $true)][string] $WindowXmlText)

    return [regex]::IsMatch(
        $WindowXmlText,
        '<node(?=[^>]*NAF="true")(?=[^>]*class="android\.view\.View")(?=[^>]*clickable="true")(?=[^>]*bounds="\[0,0\]\[\d+,\d+\]")[^>]*>'
    )
}

function Get-TextFileRaw {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $text = Get-Content -LiteralPath $Path -Raw
    if ($null -eq $text) {
        return ""
    }
    return [string] $text
}

function Test-TextMatches {
    param(
        [AllowNull()]
        [object] $Text,
        [Parameter(Mandatory = $true)]
        [string] $Pattern
    )

    return ([string] $Text) -match $Pattern
}

function Test-AdbUiExactTextOrDescription {
    param(
        [AllowNull()]
        [object] $WindowXmlText,
        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    $escapedValue = [regex]::Escape([System.Net.WebUtility]::HtmlEncode($Value))
    return ([string] $WindowXmlText) -match "\b(?:text|content-desc)=""$escapedValue"""
}

function Get-ReaderDevtoolsProbeResult {
    param(
        [Parameter(Mandatory = $true)]
        [string] $OutputFileName
    )

    $probeJsonPath = Join-Path $ArtifactDir $OutputFileName
    if (-not (Test-Path -LiteralPath $probeJsonPath -PathType Leaf)) {
        return $null
    }
    $probeJsonText = Get-TextFileRaw -Path $probeJsonPath
    if ([string]::IsNullOrWhiteSpace($probeJsonText)) {
        return $null
    }
    try {
        return ($probeJsonText | ConvertFrom-Json).result
    } catch {
        throw "Reader DevTools probe '$OutputFileName' did not return parseable JSON. See $probeJsonPath"
    }
}

function Assert-NeutralReaderVisualState {
    param(
        [Parameter(Mandatory = $true)]
        [string] $WindowXmlText
    )

    $nativeOverlayLabels = @(
        "History back",
        "History forward",
        "Close history controls",
        "Highlight",
        "Copy",
        "Note",
        "Close selection actions"
    )
    $nativeOverlayHits = @()
    foreach ($label in $nativeOverlayLabels) {
        if (Test-AdbUiExactTextOrDescription -WindowXmlText $WindowXmlText -Value $label) {
            $nativeOverlayHits += $label
        }
    }

    $pageBoxResultCount = 0
    $activeOverlayMarkerCount = 0
    $activeMediaOverlayMarkerCount = 0
    $selectedTextLength = 0
    $probeResults = @()
    $probeResults += Get-ReaderDevtoolsProbeResult -OutputFileName "reader-devtools-probe.json"
    $probeResults += Get-ReaderDevtoolsProbeResult -OutputFileName "reader-devtools-post-action-probe.json"
    foreach ($result in $probeResults) {
        if ($null -eq $result -or $result.probe -ne "page-box") {
            continue
        }
        $pageBoxResultCount += 1
        foreach ($contentRect in @($result.contentRects)) {
            $transientState = $contentRect.transientState
            if ($null -eq $transientState) {
                continue
            }
            if ($null -ne $transientState.activeOverlayMarkerCount) {
                $activeOverlayMarkerCount += [int] $transientState.activeOverlayMarkerCount
            }
            if ($null -ne $transientState.activeMediaOverlayMarkerCount) {
                $activeMediaOverlayMarkerCount += [int] $transientState.activeMediaOverlayMarkerCount
            }
            if ($null -ne $transientState.selectedTextLength) {
                $selectedTextLength += [int] $transientState.selectedTextLength
            }
        }
    }

    $summaryPath = Join-Path $ArtifactDir "reader-neutral-visual-state.txt"
    @(
        "pageBoxProbeResults=$pageBoxResultCount",
        "nativeOverlayHits=$($nativeOverlayHits -join ',')",
        "activeOverlayMarkerCount=$activeOverlayMarkerCount",
        "activeMediaOverlayMarkerCount=$activeMediaOverlayMarkerCount",
        "selectedTextLength=$selectedTextLength"
    ) | Out-File -Encoding utf8 $summaryPath

    if ($nativeOverlayHits.Count -gt 0) {
        throw "Reader visual-state validation failed: native transient overlay controls were visible ($($nativeOverlayHits -join ', ')). See $summaryPath"
    }
    if ($pageBoxResultCount -le 0) {
        throw "Reader visual-state validation failed: -RequireNeutralReaderVisualState requires a page-box DevTools probe. See $ArtifactDir"
    }
    if ($activeOverlayMarkerCount -gt 0 -or $activeMediaOverlayMarkerCount -gt 0 -or $selectedTextLength -gt 0) {
        throw "Reader visual-state validation failed: WebView transient state was active (activeOverlayMarkerCount=$activeOverlayMarkerCount, activeMediaOverlayMarkerCount=$activeMediaOverlayMarkerCount, selectedTextLength=$selectedTextLength). See $summaryPath"
    }
}

function Assert-FocusedAndroidPackage {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Package,
        [Parameter(Mandatory = $true)]
        [string] $ArtifactDir
    )

    $focusLines = @(
        Invoke-Adb @("shell", "dumpsys", "window") -PassThru |
            Select-String -Pattern "mCurrentFocus|mFocusedApp" |
            ForEach-Object { $_.Line.Trim() }
    )
    $focusLines | Out-File -Encoding utf8 (Join-Path $ArtifactDir "focused-window.txt")

    $escapedPackage = [regex]::Escape($Package)
    $packageBoundary = "(?:/|\s|\}|$)"
    $currentFocusPattern = "mCurrentFocus=.*$escapedPackage$packageBoundary"
    $focusedAppPattern = "mFocusedApp=.*$escapedPackage$packageBoundary"
    $currentFocusMatches = @(
        $focusLines |
            Where-Object { $_ -match $currentFocusPattern -or $_ -match $focusedAppPattern }
    )
    if ($currentFocusMatches.Count -le 0) {
        throw "Focused Android window does not belong to package '$Package'. See $ArtifactDir\focused-window.txt"
    }

    Write-Host "Foreground confirmed for $Package"
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found on PATH"
}

$devices = @(
    Invoke-Adb @("devices") -PassThru |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '\bdevice\b' }
)
if ($devices.Count -eq 0) {
    throw "No adb devices are connected"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($ArtifactDir)) {
    $ArtifactDir = Join-Path $PSScriptRoot "..\captures\reader-smoke\$timestamp"
}
$ArtifactDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArtifactDir)
New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null

if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    $resolvedApk = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ApkPath)
    if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
        throw "APK not found: $resolvedApk"
    }
    Invoke-Adb @("install", "-r", $resolvedApk)
}

Invoke-Adb @("logcat", "-c")

if (-not $NoLaunch) {
    $launchOutput = Invoke-Adb @("shell", "monkey", "-p", $Package, "1") -PassThru
    $launchOutput | Out-File -Encoding utf8 (Join-Path $ArtifactDir "launch.txt")
    Start-Sleep -Seconds $LaunchWaitSeconds
}

if ($TapPreset -eq "ReaderHorizontalZones") {
    $TapFraction += @("0.10,0.50,700", "0.50,0.50,700", "0.90,0.50,1000")
}

if ($TapFraction.Count -gt 0) {
    $screenSize = Get-AdbScreenSize
    foreach ($tapFractionSpec in $TapFraction) {
        $Tap += Convert-TapFraction -TapSpec $tapFractionSpec -Width $screenSize.Width -Height $screenSize.Height
    }
}

if ($PostProbeTapFraction.Count -gt 0) {
    $screenSize = Get-AdbScreenSize
    foreach ($tapFractionSpec in $PostProbeTapFraction) {
        $PostProbeTap += Convert-TapFraction -TapSpec $tapFractionSpec -Width $screenSize.Width -Height $screenSize.Height
    }
}

if ($SwipeFraction.Count -gt 0) {
    $screenSize = Get-AdbScreenSize
    foreach ($swipeFractionSpec in $SwipeFraction) {
        $Swipe += Convert-SwipeFraction -SwipeSpec $swipeFractionSpec -Width $screenSize.Width -Height $screenSize.Height
    }
}

if ($LongPressFraction.Count -gt 0) {
    $screenSize = Get-AdbScreenSize
    foreach ($longPressFractionSpec in $LongPressFraction) {
        $LongPress += Convert-LongPressFraction -LongPressSpec $longPressFractionSpec -Width $screenSize.Width -Height $screenSize.Height
    }
}

foreach ($tapSpec in $Tap) {
    if ($tapSpec -notmatch '^\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid tap spec '$tapSpec'. Use x,y or x,y,waitMs."
    }

    $x = $Matches[1]
    $y = $Matches[2]
    $waitMs = if ($Matches[3]) { [int] $Matches[3] } else { 1000 }

    Invoke-Adb @("shell", "input", "tap", $x, $y)
    Start-Sleep -Milliseconds $waitMs
}

foreach ($longPressSpec in $LongPress) {
    if ($longPressSpec -notmatch '^\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid long-press spec '$longPressSpec'. Use x,y or x,y,durationMs,waitMs."
    }

    $x = $Matches[1]
    $y = $Matches[2]
    $durationMs = if ($Matches[3]) { [int] $Matches[3] } else { 950 }
    $waitMs = if ($Matches[4]) { [int] $Matches[4] } else { 1000 }

    Invoke-Adb @("shell", "input", "swipe", $x, $y, $x, $y, $durationMs)
    Start-Sleep -Milliseconds $waitMs
}

foreach ($swipeSpec in $Swipe) {
    if ($swipeSpec -notmatch '^\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid swipe spec '$swipeSpec'. Use x1,y1,x2,y2 or x1,y1,x2,y2,durationMs,waitMs."
    }

    $x1 = $Matches[1]
    $y1 = $Matches[2]
    $x2 = $Matches[3]
    $y2 = $Matches[4]
    $durationMs = if ($Matches[5]) { [int] $Matches[5] } else { 350 }
    $waitMs = if ($Matches[6]) { [int] $Matches[6] } else { 1000 }

    Invoke-Adb @("shell", "input", "swipe", $x1, $y1, $x2, $y2, $durationMs)
    Start-Sleep -Milliseconds $waitMs
}

if ($CaptureWaitSeconds -gt 0) {
    Write-Host "Waiting $CaptureWaitSeconds seconds before capturing reader artifacts..."
    Start-Sleep -Seconds $CaptureWaitSeconds
}

$processId = ((Invoke-Adb @("shell", "pidof", $Package) -PassThru) -join "`n").Trim()
if ([string]::IsNullOrWhiteSpace($processId)) {
    throw "Package is not running: $Package"
}

Invoke-Adb @("shell", "dumpsys", "package", $Package) -PassThru |
    Select-String -Pattern "versionCode|versionName|lastUpdateTime" |
    ForEach-Object { $_.Line.Trim() } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "package-version.txt")

$packageVersionText = Get-TextFileRaw -Path (Join-Path $ArtifactDir "package-version.txt")
if (-not [string]::IsNullOrWhiteSpace($ExpectedVersionName)) {
    if (-not (Test-TextMatches -Text $packageVersionText -Pattern ([regex]::Escape($ExpectedVersionName)))) {
        throw "Installed $Package version did not contain expected versionName '$ExpectedVersionName'. Captured version: $packageVersionText"
    }
}

Assert-FocusedAndroidPackage -Package $Package -ArtifactDir $ArtifactDir

Invoke-Adb @("shell", "cat", "/proc/net/unix") -PassThru |
    Select-String -Pattern "webview_devtools|chrome_devtools" -CaseSensitive:$false |
    ForEach-Object { $_.Line.Trim() } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "webview-devtools-sockets.txt")

function Invoke-ReaderDevtoolsProbe {
    param(
        [string] $ProbeName,
        [Parameter(Mandatory = $true)]
        [string] $OutputFileName
    )

    if ([string]::IsNullOrWhiteSpace($ProbeName)) {
        return
    }

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    $probeScript = Join-Path $repoRoot "tools\reader-harness\src\adb-webview-eval.mjs"
    if (-not (Test-Path -LiteralPath $probeScript -PathType Leaf)) {
        throw "Reader DevTools probe helper was not found: $probeScript"
    }

    $probeArguments = @($probeScript, "--package", $Package, "--probe", $ProbeName)
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $probeArguments += @("--device", $DeviceSerial)
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $probeOutput = & node @probeArguments 2>&1
        $probeExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $probeOutput | Out-File -Encoding utf8 (Join-Path $ArtifactDir $OutputFileName)
    if ($probeExitCode -ne 0) {
        throw "Reader DevTools probe '$ProbeName' failed with exit code $probeExitCode. See $ArtifactDir\$OutputFileName"
    }
}

Invoke-ReaderDevtoolsProbe -ProbeName $ReaderDevtoolsProbe -OutputFileName "reader-devtools-probe.json"

foreach ($tapSpec in $PostProbeTap) {
    if ($tapSpec -notmatch '^\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?\s*$') {
        throw "Invalid post-probe tap spec '$tapSpec'. Use x,y or x,y,waitMs."
    }

    $x = $Matches[1]
    $y = $Matches[2]
    $waitMs = if ($Matches[3]) { [int] $Matches[3] } else { 1000 }

    Invoke-Adb @("shell", "input", "tap", $x, $y)
    Start-Sleep -Milliseconds $waitMs
}

$expandedPostProbeActions = @()
foreach ($postProbeActionSpec in @($PostProbeAction)) {
    foreach ($rawPostProbeActionSpec in @($postProbeActionSpec)) {
        $postProbeActionText = [string] $rawPostProbeActionSpec
        if ([string]::IsNullOrWhiteSpace($postProbeActionText)) {
            continue
        }
        foreach ($postProbeActionPart in ($postProbeActionText -split '\|')) {
            $trimmedPostProbeAction = $postProbeActionPart.Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmedPostProbeAction)) {
                $expandedPostProbeActions += $trimmedPostProbeAction
            }
        }
    }
}
$PostProbeAction = [string[]] $expandedPostProbeActions

function Invoke-PostProbeTapSpec {
    param(
        [Parameter(Mandatory = $true)]
        $ActionLabel,
        [Parameter(Mandatory = $true)]
        $TapSpec
    )

    $actionLabelText = "$ActionLabel"
    $tapSpecText = "$TapSpec"
    $tapParts = @($tapSpecText.Split(",") | ForEach-Object { $_.Trim() })
    if ($tapParts.Count -lt 2 -or $tapParts.Count -gt 3) {
        throw "Invalid post-probe action '$actionLabelText'. Use tap:x,y or tap:x,y,waitMs."
    }

    $parsedX = 0
    $parsedY = 0
    $parsedWaitMs = 1000
    if (-not [int]::TryParse($tapParts[0], [ref] $parsedX) -or
        -not [int]::TryParse($tapParts[1], [ref] $parsedY) -or
        ($tapParts.Count -eq 3 -and -not [int]::TryParse($tapParts[2], [ref] $parsedWaitMs))) {
        throw "Invalid post-probe action '$actionLabelText'. Use tap:x,y or tap:x,y,waitMs."
    }

    $x = [string] $parsedX
    $y = [string] $parsedY
    Invoke-Adb @("shell", "input", "tap", $x, $y)
    Start-Sleep -Milliseconds $parsedWaitMs
}

function Get-AdbUiNodeAttribute {
    param(
        [Parameter(Mandatory = $true)]
        [string] $NodeText,
        [Parameter(Mandatory = $true)]
        [string] $AttributeName
    )

    $attributePattern = "\b$([regex]::Escape($AttributeName))=""([^""]*)"""
    $attributeMatch = [regex]::Match($NodeText, $attributePattern)
    if (-not $attributeMatch.Success) {
        return $null
    }
    return [System.Net.WebUtility]::HtmlDecode($attributeMatch.Groups[1].Value)
}

function Find-AdbUiNodeBounds {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedValue
    )

    $windowDumpText = (Invoke-Adb @("exec-out", "uiautomator", "dump", "/dev/tty") -PassThru) -join "`n"
    $nodeMatches = [regex]::Matches($windowDumpText, '<node\b[^>]*>')
    foreach ($nodeMatch in $nodeMatches) {
        $nodeText = $nodeMatch.Value
        $actualValue = if ($MatcherKind -eq "text") {
            Get-AdbUiNodeAttribute -NodeText $nodeText -AttributeName "text"
        } else {
            Get-AdbUiNodeAttribute -NodeText $nodeText -AttributeName "content-desc"
        }
        if ($actualValue -ne $ExpectedValue) {
            continue
        }

        $bounds = Get-AdbUiNodeAttribute -NodeText $nodeText -AttributeName "bounds"
        $boundsMatch = [regex]::Match($bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
        if (-not $boundsMatch.Success) {
            continue
        }
        $left = [int] $boundsMatch.Groups[1].Value
        $top = [int] $boundsMatch.Groups[2].Value
        $right = [int] $boundsMatch.Groups[3].Value
        $bottom = [int] $boundsMatch.Groups[4].Value
        if ($right -le $left -or $bottom -le $top) {
            continue
        }

        return [pscustomobject]@{
            Left = $left
            Top = $top
            Right = $right
            Bottom = $bottom
            Width = $right - $left
            Height = $bottom - $top
            Bounds = $bounds
            Value = $actualValue
        }
    }

    return $null
}

function Get-AdbUiNodeBounds {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedValue
    )

    $bounds = Find-AdbUiNodeBounds -MatcherKind $MatcherKind -ExpectedValue $ExpectedValue
    if ($null -ne $bounds) {
        return $bounds
    }

    throw "Could not find UI node by $MatcherKind '$ExpectedValue' in post-probe hierarchy."
}

function Get-AdbUiNodeCenter {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedValue
    )

    $bounds = Get-AdbUiNodeBounds -MatcherKind $MatcherKind -ExpectedValue $ExpectedValue
    return [pscustomobject]@{
        X = [int] (($bounds.Left + $bounds.Right) / 2)
        Y = [int] (($bounds.Top + $bounds.Bottom) / 2)
        Bounds = $bounds.Bounds
        Value = $bounds.Value
    }
}

function Get-AdbUiNodeFractionPoint {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedValue,
        [Parameter(Mandatory = $true)]
        [double] $XFraction,
        [Parameter(Mandatory = $true)]
        [double] $YFraction
    )

    $bounds = Get-AdbUiNodeBounds -MatcherKind $MatcherKind -ExpectedValue $ExpectedValue
    $clampedXFraction = [math]::Min(1.0, [math]::Max(0.0, $XFraction))
    $clampedYFraction = [math]::Min(1.0, [math]::Max(0.0, $YFraction))
    return [pscustomobject]@{
        X = [int] [math]::Round($bounds.Left + ($bounds.Width * $clampedXFraction))
        Y = [int] [math]::Round($bounds.Top + ($bounds.Height * $clampedYFraction))
        Bounds = $bounds.Bounds
        Value = $bounds.Value
    }
}

function Invoke-PostProbeUiNodeAction {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ActionLabel,
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $NodeSpec
    )

    $nodeSpecMatch = [regex]::Match($NodeSpec, '^(.*?)(?:\s*,\s*(\d+))?$')
    if (-not $nodeSpecMatch.Success -or [string]::IsNullOrWhiteSpace($nodeSpecMatch.Groups[1].Value)) {
        throw "Invalid post-probe action '$ActionLabel'. Use tapText:value or tapText:value,waitMs."
    }
    $expectedValue = $nodeSpecMatch.Groups[1].Value.Trim()
    $waitMs = if ($nodeSpecMatch.Groups[2].Success) { [int] $nodeSpecMatch.Groups[2].Value } else { 1000 }
    $center = Get-AdbUiNodeCenter -MatcherKind $MatcherKind -ExpectedValue $expectedValue
    Invoke-Adb @("shell", "input", "tap", ([string] $center.X), ([string] $center.Y))
    Start-Sleep -Milliseconds $waitMs
}

function Invoke-PostProbeUiNodeActionIfPresent {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ActionLabel,
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $NodeSpec
    )

    $nodeSpecMatch = [regex]::Match($NodeSpec, '^(.*?)(?:\s*,\s*(\d+))?$')
    if (-not $nodeSpecMatch.Success -or [string]::IsNullOrWhiteSpace($nodeSpecMatch.Groups[1].Value)) {
        throw "Invalid post-probe action '$ActionLabel'. Use tapDescIfPresent:value or tapDescIfPresent:value,waitMs."
    }
    $expectedValue = $nodeSpecMatch.Groups[1].Value.Trim()
    $waitMs = if ($nodeSpecMatch.Groups[2].Success) { [int] $nodeSpecMatch.Groups[2].Value } else { 1000 }
    $bounds = Find-AdbUiNodeBounds -MatcherKind $MatcherKind -ExpectedValue $expectedValue
    if ($null -eq $bounds) {
        Write-Host "post-probe action '$ActionLabel' skipped; UI node by $MatcherKind '$expectedValue' was not present."
        return
    }
    $x = [int] (($bounds.Left + $bounds.Right) / 2)
    $y = [int] (($bounds.Top + $bounds.Bottom) / 2)
    Invoke-Adb @("shell", "input", "tap", ([string] $x), ([string] $y))
    Start-Sleep -Milliseconds $waitMs
}

function Invoke-PostProbeUiNodeActionWhenPresent {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ActionLabel,
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $NodeSpec
    )

    $nodeSpecMatch = [regex]::Match($NodeSpec, '^(.*?),\s*(\d+)\s*,\s*(\d+)$')
    if (-not $nodeSpecMatch.Success -or [string]::IsNullOrWhiteSpace($nodeSpecMatch.Groups[1].Value)) {
        throw "Invalid post-probe action '$ActionLabel'. Use tapDescWhenPresent:value,maxAttempts,waitMs."
    }
    $expectedValue = $nodeSpecMatch.Groups[1].Value.Trim()
    $maxAttempts = [int] $nodeSpecMatch.Groups[2].Value
    $waitMs = [int] $nodeSpecMatch.Groups[3].Value
    if ($maxAttempts -lt 1) {
        throw "Invalid post-probe action '$ActionLabel'. maxAttempts must be at least 1."
    }

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        $bounds = Find-AdbUiNodeBounds -MatcherKind $MatcherKind -ExpectedValue $expectedValue
        if ($null -ne $bounds) {
            $x = [int] (($bounds.Left + $bounds.Right) / 2)
            $y = [int] (($bounds.Top + $bounds.Bottom) / 2)
            Invoke-Adb @("shell", "input", "tap", ([string] $x), ([string] $y))
            Start-Sleep -Milliseconds $waitMs
            return
        }

        Start-Sleep -Milliseconds $waitMs
    }

    throw "Post-probe action '$ActionLabel' did not find UI node by $MatcherKind '$expectedValue' after $maxAttempts attempt(s)."
}

function Invoke-PostProbeTapFractionUntilDescPresent {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ActionLabel,
        [Parameter(Mandatory = $true)]
        [string] $NodeAndTapSpec
    )

    $nodeAndTapMatch = [regex]::Match(
        $NodeAndTapSpec,
        '^(.*?),\s*([0-9]*\.?[0-9]+)\s*,\s*([0-9]*\.?[0-9]+)(?:\s*,\s*(\d+))?(?:\s*,\s*(\d+))?$'
    )
    if (-not $nodeAndTapMatch.Success -or [string]::IsNullOrWhiteSpace($nodeAndTapMatch.Groups[1].Value)) {
        throw "Invalid post-probe action '$ActionLabel'. Use tapFractionUntilDescPresent:desc,xFraction,yFraction or tapFractionUntilDescPresent:desc,xFraction,yFraction,maxAttempts,waitMs."
    }

    $expectedValue = $nodeAndTapMatch.Groups[1].Value.Trim()
    $xFraction = $nodeAndTapMatch.Groups[2].Value
    $yFraction = $nodeAndTapMatch.Groups[3].Value
    $maxAttempts = if ($nodeAndTapMatch.Groups[4].Success) { [int] $nodeAndTapMatch.Groups[4].Value } else { 2 }
    $waitMs = if ($nodeAndTapMatch.Groups[5].Success) { [int] $nodeAndTapMatch.Groups[5].Value } else { 1000 }
    if ($maxAttempts -lt 1) {
        throw "Invalid post-probe action '$ActionLabel'. maxAttempts must be at least 1."
    }

    $screenSize = Get-AdbScreenSize
    $convertedTapSpec = Convert-TapFraction `
        -TapSpec "$xFraction,$yFraction,$waitMs" `
        -Width $screenSize.Width `
        -Height $screenSize.Height

    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        $existingBounds = Find-AdbUiNodeBounds -MatcherKind "desc" -ExpectedValue $expectedValue
        if ($null -ne $existingBounds) {
            return
        }

        Invoke-PostProbeTapSpec -ActionLabel $ActionLabel -TapSpec ([string] $convertedTapSpec)
    }

    $finalBounds = Find-AdbUiNodeBounds -MatcherKind "desc" -ExpectedValue $expectedValue
    if ($null -eq $finalBounds) {
        throw "Post-probe action '$ActionLabel' did not reveal UI node by desc '$expectedValue' after $maxAttempts tap attempt(s)."
    }
}

function Invoke-PostProbeUiNodeFractionAction {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ActionLabel,
        [Parameter(Mandatory = $true)]
        [ValidateSet("text", "desc")]
        [string] $MatcherKind,
        [Parameter(Mandatory = $true)]
        [string] $NodeSpec
    )

    $nodeSpecMatch = [regex]::Match($NodeSpec, '^(.*?),\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)(?:\s*,\s*(\d+))?$')
    if (-not $nodeSpecMatch.Success -or [string]::IsNullOrWhiteSpace($nodeSpecMatch.Groups[1].Value)) {
        throw "Invalid post-probe action '$ActionLabel'. Use tapDescFraction:value,xFraction,yFraction or tapDescFraction:value,xFraction,yFraction,waitMs."
    }
    $expectedValue = $nodeSpecMatch.Groups[1].Value.Trim()
    $xFraction = [double]::Parse($nodeSpecMatch.Groups[2].Value, [System.Globalization.CultureInfo]::InvariantCulture)
    $yFraction = [double]::Parse($nodeSpecMatch.Groups[3].Value, [System.Globalization.CultureInfo]::InvariantCulture)
    $waitMs = if ($nodeSpecMatch.Groups[4].Success) { [int] $nodeSpecMatch.Groups[4].Value } else { 1000 }
    $point = Get-AdbUiNodeFractionPoint `
        -MatcherKind $MatcherKind `
        -ExpectedValue $expectedValue `
        -XFraction $xFraction `
        -YFraction $yFraction
    Invoke-Adb @("shell", "input", "tap", ([string] $point.X), ([string] $point.Y))
    Start-Sleep -Milliseconds $waitMs
}

foreach ($postProbeActionEntry in $PostProbeAction) {
    $postProbeAction = [string] $postProbeActionEntry
    $postProbeAction = $postProbeAction.Trim()
    if ($postProbeAction.StartsWith("tap:")) {
        $tapSpec = $postProbeAction.Substring("tap:".Length)
        Invoke-PostProbeTapSpec -ActionLabel $postProbeAction -TapSpec $tapSpec
        continue
    }

    if ($postProbeAction.StartsWith("tapFraction:")) {
        $tapSpec = $postProbeAction.Substring("tapFraction:".Length)
        $screenSize = Get-AdbScreenSize
        $convertedTapSpec = Convert-TapFraction -TapSpec $tapSpec -Width $screenSize.Width -Height $screenSize.Height
        Invoke-PostProbeTapSpec -ActionLabel $postProbeAction -TapSpec ([string] $convertedTapSpec)
        continue
    }

    if ($postProbeAction.StartsWith("tapFractionUntilDescPresent:")) {
        $nodeAndTapSpec = $postProbeAction.Substring("tapFractionUntilDescPresent:".Length)
        Invoke-PostProbeTapFractionUntilDescPresent -ActionLabel ([string] $postProbeAction) -NodeAndTapSpec ([string] $nodeAndTapSpec)
        continue
    }

    if ($postProbeAction.StartsWith("tapText:")) {
        $nodeSpec = $postProbeAction.Substring("tapText:".Length)
        Invoke-PostProbeUiNodeAction -ActionLabel ([string] $postProbeAction) -MatcherKind "text" -NodeSpec ([string] $nodeSpec)
        continue
    }

    if ($postProbeAction.StartsWith("tapDesc:")) {
        $nodeSpec = $postProbeAction.Substring("tapDesc:".Length)
        Invoke-PostProbeUiNodeAction -ActionLabel ([string] $postProbeAction) -MatcherKind "desc" -NodeSpec ([string] $nodeSpec)
        continue
    }

    if ($postProbeAction.StartsWith("tapDescIfPresent:")) {
        $nodeSpec = $postProbeAction.Substring("tapDescIfPresent:".Length)
        Invoke-PostProbeUiNodeActionIfPresent -ActionLabel ([string] $postProbeAction) -MatcherKind "desc" -NodeSpec ([string] $nodeSpec)
        continue
    }

    if ($postProbeAction.StartsWith("tapDescWhenPresent:")) {
        $nodeSpec = $postProbeAction.Substring("tapDescWhenPresent:".Length)
        Invoke-PostProbeUiNodeActionWhenPresent -ActionLabel ([string] $postProbeAction) -MatcherKind "desc" -NodeSpec ([string] $nodeSpec)
        continue
    }

    if ($postProbeAction.StartsWith("tapDescFraction:")) {
        $nodeSpec = $postProbeAction.Substring("tapDescFraction:".Length)
        Invoke-PostProbeUiNodeFractionAction -ActionLabel ([string] $postProbeAction) -MatcherKind "desc" -NodeSpec ([string] $nodeSpec)
        continue
    }

    if ($postProbeAction.StartsWith("text:")) {
        $textPayload = $postProbeAction.Substring("text:".Length)
        $textWaitMatch = [regex]::Match($textPayload, '^(.*?)(?:\s*,\s*(\d+))?$')
        $text = $textWaitMatch.Groups[1].Value.Replace(" ", "%s")
        $waitMs = if ($textWaitMatch.Groups[2].Success) { [int] $textWaitMatch.Groups[2].Value } else { 500 }
        Invoke-Adb @("shell", "input", "text", $text)
        Start-Sleep -Milliseconds $waitMs
        continue
    }

    if ($postProbeAction.StartsWith("keyevent:")) {
        $keyEventPayload = $postProbeAction.Substring("keyevent:".Length)
        $keyEventWaitMatch = [regex]::Match($keyEventPayload, '^(.*?)(?:\s*,\s*(\d+))?$')
        $keyEvent = $keyEventWaitMatch.Groups[1].Value
        $waitMs = if ($keyEventWaitMatch.Groups[2].Success) { [int] $keyEventWaitMatch.Groups[2].Value } else { 500 }
        Invoke-Adb @("shell", "input", "keyevent", $keyEvent)
        Start-Sleep -Milliseconds $waitMs
        continue
    }

    throw "Invalid post-probe action '$postProbeAction'. Use tap:, tapFraction:, tapFractionUntilDescPresent:, tapText:, tapDesc:, tapDescIfPresent:, tapDescWhenPresent:, tapDescFraction:, text:, or keyevent:."
}

Invoke-ReaderDevtoolsProbe -ProbeName $PostActionReaderDevtoolsProbe -OutputFileName "reader-devtools-post-action-probe.json"

Invoke-AdbExecOutToFile -Arguments @("exec-out", "screencap", "-p") -OutputPath (Join-Path $ArtifactDir "screen.png")

Invoke-Adb @("exec-out", "uiautomator", "dump", "/dev/tty") -PassThru |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "window.xml")

$windowXmlText = Get-TextFileRaw -Path (Join-Path $ArtifactDir "window.xml")
$nativeShellCoverVisible = Get-ReaderNativeShellCoverVisible -WindowXmlText $windowXmlText
@(
    "nativeShellCoverVisible=$nativeShellCoverVisible",
    "marker=full-window-clickable-naf-view"
) | Out-File -Encoding utf8 (Join-Path $ArtifactDir "reader-native-cover-validation.txt")

Invoke-Adb @("logcat", "-d", "--pid=$processId", "-v", "time") -PassThru |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "logcat-full.log")

Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
    Select-String -Pattern "Reader|Foliate|Paginator|content-layout|iframe-srcdoc|firstText|publicationReady|locationChanged|AndroidRuntime|FATAL|ERROR|WARNING|Exception|503|404|unsupported" -CaseSensitive:$false |
    ForEach-Object { $_.Line } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "logcat-reader.log")

$readerLogText = Get-TextFileRaw -Path (Join-Path $ArtifactDir "logcat-reader.log")

if ($RequireNativeShellCover -and -not $nativeShellCoverVisible) {
    throw "Reader diagnostics validation failed: native shell cover was not visible. See $ArtifactDir"
}

if ($RequireNeutralReaderVisualState) {
    Assert-NeutralReaderVisualState -WindowXmlText $windowXmlText
}

function Assert-ReaderDevtoolsProbeLogLabels {
    param(
        [string] $ProbeName,
        [Parameter(Mandatory = $true)]
        [string] $OutputFileName
    )

    if ([string]::IsNullOrWhiteSpace($ProbeName)) {
        return
    }

    $probeJsonPath = Join-Path $ArtifactDir $OutputFileName
    $probeJsonText = Get-TextFileRaw -Path $probeJsonPath
    try {
        $probeJson = $probeJsonText | ConvertFrom-Json
    } catch {
        throw "Reader DevTools probe '$ProbeName' did not return parseable JSON. See $probeJsonPath"
    }
    $expectedLogLabels = @($probeJson.result.expectedLogLabels)
    foreach ($expectedLogLabel in $expectedLogLabels) {
        if ([string]::IsNullOrWhiteSpace($expectedLogLabel)) {
            continue
        }
        if (-not (Test-TextMatches -Text $readerLogText -Pattern ([regex]::Escape($expectedLogLabel)))) {
            throw "Reader DevTools probe '$ProbeName' expected log label '$expectedLogLabel' was not captured. See $ArtifactDir\logcat-reader.log"
        }
    }
}

Assert-ReaderDevtoolsProbeLogLabels -ProbeName $ReaderDevtoolsProbe -OutputFileName "reader-devtools-probe.json"
Assert-ReaderDevtoolsProbeLogLabels -ProbeName $PostActionReaderDevtoolsProbe -OutputFileName "reader-devtools-post-action-probe.json"

function Assert-ReaderDevtoolsLocationEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string] $OutputFileName,
        [ValidateSet("", "start", "end")]
        [string] $ExpectedEndpoint = ""
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedEndpoint)) {
        return
    }

    function Convert-ReaderRequiredInt {
        param(
            [object] $Value,
            [Parameter(Mandatory = $true)]
            [string] $Name,
            [Parameter(Mandatory = $true)]
            [string] $SourceFileName
        )

        $valueText = [string] $Value
        $parsedValue = 0
        if (-not [int]::TryParse($valueText, [ref] $parsedValue)) {
            throw "Reader DevTools location endpoint validation expected numeric $Name, got '$valueText'. See $ArtifactDir\$SourceFileName"
        }
        return $parsedValue
    }

    $probeResult = Get-ReaderDevtoolsProbeResult -OutputFileName $OutputFileName
    if ($null -eq $probeResult) {
        throw "Reader DevTools location endpoint validation expected '$OutputFileName'. See $ArtifactDir"
    }
    if ($probeResult.probe -ne "location-snapshot") {
        throw "Reader DevTools location endpoint validation expected a location-snapshot probe, got '$($probeResult.probe)'. See $ArtifactDir\$OutputFileName"
    }

    $location = $probeResult.location
    if ($null -eq $location) {
        throw "Reader DevTools location endpoint validation expected a location payload. See $ArtifactDir\$OutputFileName"
    }
    $chapterPageIndex = Convert-ReaderRequiredInt -Value $location.chapterPageIndex -Name "chapterPageIndex" -SourceFileName $OutputFileName
    $chapterPageCount = Convert-ReaderRequiredInt -Value $location.chapterPageCount -Name "chapterPageCount" -SourceFileName $OutputFileName
    if ($chapterPageCount -lt 1) {
        throw "Reader DevTools location endpoint validation expected a usable chapterPageCount, got '$($location.chapterPageCount)'. See $ArtifactDir\$OutputFileName"
    }

    if ($ExpectedEndpoint -eq "start" -and $chapterPageIndex -ne 0) {
        throw "Expected first chapter page after native rail endpoint tap, got chapterPageIndex=$chapterPageIndex chapterPageCount=$chapterPageCount. See $ArtifactDir\$OutputFileName"
    }
    if ($ExpectedEndpoint -eq "end" -and $chapterPageIndex -ne ($chapterPageCount - 1)) {
        throw "Expected last chapter page after native rail endpoint tap, got chapterPageIndex=$chapterPageIndex chapterPageCount=$chapterPageCount. See $ArtifactDir\$OutputFileName"
    }
}

Assert-ReaderDevtoolsLocationEndpoint -OutputFileName "reader-devtools-post-action-probe.json" -ExpectedEndpoint $RequirePostActionChapterPageEndpoint

function Get-ReaderDevtoolsPdfVisibleResult {
    param(
        [Parameter(Mandatory = $true)]
        [string] $OutputFileName
    )

    $probeJsonPath = Join-Path $ArtifactDir $OutputFileName
    if (-not (Test-Path -LiteralPath $probeJsonPath -PathType Leaf)) {
        return $null
    }
    $probeJsonText = Get-TextFileRaw -Path $probeJsonPath
    if ([string]::IsNullOrWhiteSpace($probeJsonText)) {
        return $null
    }
    try {
        $probeJson = $probeJsonText | ConvertFrom-Json
    } catch {
        return $null
    }
    $probeResult = $probeJson.result
    if (
        $probeResult.probe -eq "pdf-visible-page" -and
        $probeResult.fixedLayout -eq $true -and
        $probeResult.visiblePdfVisualCount -gt 0
    ) {
        return $probeResult
    }
    return $null
}

function Test-ReaderDevtoolsPdfVisible {
    param(
        [Parameter(Mandatory = $true)]
        [string] $OutputFileName
    )

    return $null -ne (Get-ReaderDevtoolsPdfVisibleResult -OutputFileName $OutputFileName)
}

if ($RequireNoReaderConsoleErrors -and (Test-TextMatches -Text $readerLogText -Pattern "Reader console ERROR:")) {
    throw "Reader smoke validation failed: required no reader console errors, but at least one Reader console ERROR was captured. See $ArtifactDir\logcat-reader.log"
}

foreach ($requiredEngineCommand in $RequireReaderEngineCommand) {
    if (-not (Test-TextMatches -Text $readerLogText -Pattern ([regex]::Escape("Dispatching reader engine command: $requiredEngineCommand")))) {
        throw "Reader smoke validation failed: required engine command '$requiredEngineCommand' was not captured. See $ArtifactDir\logcat-reader.log"
    }
}

foreach ($requiredReaderLog in $RequireReaderLog) {
    if (-not (Test-TextMatches -Text $readerLogText -Pattern ([regex]::Escape($requiredReaderLog)))) {
        throw "Reader smoke validation failed: required reader log '$requiredReaderLog' was not captured. See $ArtifactDir\logcat-reader.log"
    }
}

if ($CaptureReaderDiagnostics) {
    $textureDiagnosticPattern = "surface-texture-scroll|surface-texture-update|texture:scroll|texture:update"
    $touchDiagnosticPattern = "Reader surface touch down|Reader surface tap action=|Reader native tap action=|Reader native drag preview|Reader native drag candidate|Reader native long tap|Reader surface dispatch center tap|Reader surface tap ignored|Reader shell cover drag candidate|Reader shell cover swipe action=|Reader shell cover command action=|Reader bridge raw|Reader bridge event:|readerContentTapHandled|content-touch:media|content-touch:link|image:sepia-overlay|link:navigate|link:media-tap|link:text-hit-miss"
    $bridgeDiagnosticPattern = "Reader bridge raw|Reader bridge event:"

    $textureDiagnosticsPath = Join-Path $ArtifactDir "reader-texture-diagnostics.log"
    $touchDiagnosticsPath = Join-Path $ArtifactDir "reader-touch-diagnostics.log"
    $bridgeDiagnosticsPath = Join-Path $ArtifactDir "reader-bridge-events.log"
    $summaryPath = Join-Path $ArtifactDir "reader-diagnostics-summary.txt"

    Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
        Select-String -Pattern $textureDiagnosticPattern -CaseSensitive:$false |
        ForEach-Object { $_.Line } |
        Out-File -Encoding utf8 $textureDiagnosticsPath

    Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
        Select-String -Pattern $touchDiagnosticPattern -CaseSensitive:$false |
        ForEach-Object { $_.Line } |
        Out-File -Encoding utf8 $touchDiagnosticsPath

    Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
        Select-String -Pattern $bridgeDiagnosticPattern -CaseSensitive:$false |
        ForEach-Object { $_.Line } |
        Out-File -Encoding utf8 $bridgeDiagnosticsPath

    $textureDiagnosticsText = Get-TextFileRaw -Path $textureDiagnosticsPath
    $touchDiagnosticsText = Get-TextFileRaw -Path $touchDiagnosticsPath
    $bridgeDiagnosticsText = Get-TextFileRaw -Path $bridgeDiagnosticsPath
    $logcatFullText = Get-TextFileRaw -Path (Join-Path $ArtifactDir "logcat-full.log")
    $pdfProbeResult = Get-ReaderDevtoolsPdfVisibleResult -OutputFileName "reader-devtools-post-action-probe.json"
    if ($null -eq $pdfProbeResult) {
        $pdfProbeResult = Get-ReaderDevtoolsPdfVisibleResult -OutputFileName "reader-devtools-probe.json"
    }
    $pdfProbeVisible = $null -ne $pdfProbeResult
    $pdfRendererIndex = if ($null -ne $pdfProbeResult) { [int] $pdfProbeResult.rendererIndex } else { -1 }
    $pdfRuntimeDiagnostics = (Test-TextMatches -Text $logcatFullText -Pattern '\[FoliatePDF\]|makePDF|pdfjs|PDF\.js|publication\.pdf|format=Pdf') -or
        $pdfProbeVisible
    $textureLines = @(Get-Content -LiteralPath $textureDiagnosticsPath)
    $textureDirectionSamples = @()
    $wrongTextureDirection = $false
    if (-not [string]::IsNullOrWhiteSpace($RequireTextureDirection)) {
        $textureDirectionSamples = Get-ReaderTextureDirectionSamples `
            -Lines $textureLines `
            -Direction $RequireTextureDirection
        $wrongTextureDirection = [bool]($textureDirectionSamples | Where-Object { $_.WrongTextureDirection })
        $directionValidationPath = Join-Path $ArtifactDir "reader-texture-direction-validation.txt"
        @(
            "requiredTextureDirection=$RequireTextureDirection",
            "textureDirectionSamples=$($textureDirectionSamples.Count)",
            "wrongTextureDirection=$wrongTextureDirection"
        ) + ($textureDirectionSamples | ForEach-Object {
            "sample axis=$($_.Axis) offset=$($_.Offset) expectedSign=$($_.ExpectedSign) wrong=$($_.WrongTextureDirection) line=$($_.Line)"
        }) | Out-File -Encoding utf8 $directionValidationPath
    }
    $summaryLines = @(
        "textureScrollLines=$((@(Select-String -Path $textureDiagnosticsPath -Pattern 'surface-texture-scroll' -CaseSensitive:$false)).Count)",
        "textureUpdateLines=$((@(Select-String -Path $textureDiagnosticsPath -Pattern 'surface-texture-update' -CaseSensitive:$false)).Count)",
        "readerSurfaceTouchDown=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader surface touch down')",
        "readerSurfaceTapAction=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader surface tap action=')",
        "readerNativeTapAction=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader native tap action=')",
        "readerNativeDragPreview=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader native drag preview')",
        "readerNativeDragCandidate=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader native drag candidate')",
        "readerNativeLongTap=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader native long tap')",
        "readerCenterDispatch=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader surface dispatch center tap')",
        "readerContentTapHandled=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'readerContentTapHandled|Reader bridge event: contentTapHandled')",
        "requiredBridgeEvents=$($RequireReaderBridgeEvent -join ',')",
        "requiredReaderLogs=$($RequireReaderLog -join ',')",
        "imageSepiaOverlay=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'image:sepia-overlay')",
        "linkNavigate=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'link:navigate')",
        "pdfProbeVisible=$pdfProbeVisible",
        "pdfRendererIndex=$pdfRendererIndex",
        "pdfRuntimeDiagnostics=$pdfRuntimeDiagnostics",
        "shellCoverDragCandidate=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader shell cover drag candidate')",
        "shellCoverSwipe=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader shell cover swipe')",
        "shellCoverCommand=$(Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader shell cover command')",
        "textureHasPosition=$(Test-TextMatches -Text $textureDiagnosticsText -Pattern 'pos=')",
        "textureHasBase=$(Test-TextMatches -Text $textureDiagnosticsText -Pattern 'base=')",
        "textureHasDelta=$(Test-TextMatches -Text $textureDiagnosticsText -Pattern 'delta=')",
        "textureHasDirection=$(Test-TextMatches -Text $textureDiagnosticsText -Pattern 'dir=')",
        "textureHasPage=$(Test-TextMatches -Text $textureDiagnosticsText -Pattern 'page=')",
        "textureHasHref=$(Test-TextMatches -Text $textureDiagnosticsText -Pattern 'href=')",
        "textureDirectionSamples=$($textureDirectionSamples.Count)",
        "wrongTextureDirection=$wrongTextureDirection"
    )
    foreach ($requiredBridgeEvent in $RequireReaderBridgeEvent) {
        $summaryLines += "bridgeEvent:$requiredBridgeEvent=$(Test-TextMatches -Text $bridgeDiagnosticsText -Pattern ([regex]::Escape("Reader bridge event: $requiredBridgeEvent")))"
    }
    $summaryLines | Out-File -Encoding utf8 $summaryPath

    if ($RequireShellCoverSwipe -and -not (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader shell cover swipe')) {
        throw "Reader diagnostics validation failed: no shell-cover swipe was captured. See $ArtifactDir"
    }
    if ($RequireShellCoverDragDiagnostic -and -not (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader shell cover drag candidate')) {
        throw "Reader diagnostics validation failed: no shell-cover drag candidate was captured. See $ArtifactDir"
    }
    if ($RequireShellCoverCommand -and -not (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader shell cover command')) {
        throw "Reader diagnostics validation failed: no shell-cover command was captured. See $ArtifactDir"
    }
    if ($RequireNativeSwipeAction -and -not (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader native drag preview')) {
        throw "Reader diagnostics validation failed: no native reader drag preview was captured. See $ArtifactDir"
    }
    if ($RequireNativeLongTap -and -not (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader native long tap')) {
        throw "Reader diagnostics validation failed: no native reader long tap was captured. See $ArtifactDir"
    }
    if ($RequireContentTapHandled -and -not (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'readerContentTapHandled|Reader bridge event: contentTapHandled')) {
        throw "Reader diagnostics validation failed: no readerContentTapHandled bridge event was captured. See $ArtifactDir"
    }
    foreach ($requiredBridgeEvent in $RequireReaderBridgeEvent) {
        if (-not (Test-TextMatches -Text $bridgeDiagnosticsText -Pattern ([regex]::Escape("Reader bridge event: $requiredBridgeEvent")))) {
            throw "Reader diagnostics validation failed: required bridge event '$requiredBridgeEvent' was not captured. See $ArtifactDir"
        }
    }
    if ($RequireNoReaderCenterDispatch -and (Test-TextMatches -Text $touchDiagnosticsText -Pattern 'Reader surface dispatch center tap')) {
        throw "Reader diagnostics validation failed: reader center dispatch was captured. See $ArtifactDir"
    }
    if ($RequirePdfDiagnostics -and -not $pdfRuntimeDiagnostics) {
        throw "Reader diagnostics validation failed: no PDF runtime diagnostics were captured. See $ArtifactDir"
    }
    if ($RequirePdfRendererIndex -ge 0) {
        if ($null -eq $pdfProbeResult) {
            throw "Reader diagnostics validation failed: PDF renderer index required but no pdf-visible-page probe result was captured. See $ArtifactDir"
        }
        if ($pdfRendererIndex -ne $RequirePdfRendererIndex) {
            throw "Reader diagnostics validation failed: PDF renderer index $pdfRendererIndex did not match expected $RequirePdfRendererIndex. See $ArtifactDir"
        }
    }
    if ($RequireTextureDiagnostics) {
        foreach ($requiredTextureField in @('pos=', 'base=', 'delta=', 'dir=', 'page=', 'href=')) {
            if (-not (Test-TextMatches -Text $textureDiagnosticsText -Pattern ([regex]::Escape($requiredTextureField)))) {
                throw "Reader diagnostics validation failed: texture diagnostics did not include '$requiredTextureField'. See $ArtifactDir"
            }
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($RequireTextureDirection)) {
        if ($textureDirectionSamples.Count -eq 0) {
            throw "Reader diagnostics validation failed: no moved texture samples were captured for direction '$RequireTextureDirection'. See $ArtifactDir"
        }
        if ($wrongTextureDirection) {
            throw "Reader diagnostics validation failed: texture movement inverted for direction '$RequireTextureDirection'. See $ArtifactDir"
        }
    }
}

if ($ValidateReaderTaps) {
    $validationLines = New-Object System.Collections.Generic.List[string]
    $hasPlainImageRegression = Test-TextMatches -Text $readerLogText -Pattern "Reader surface tap ignored for content hitType=5"
    $hasNativeTapAction = Test-TextMatches -Text $readerLogText -Pattern "Reader surface tap action=|Reader native tap action="
    $hasExplicitContentHandler = Test-TextMatches -Text $readerLogText -Pattern "Reader surface tap ignored for explicit content handler"
    $hasContentTapHandledEvent = Test-TextMatches -Text $readerLogText -Pattern "Reader bridge event: contentTapHandled"

    $validationLines.Add("plainImageHitType5Regression=$hasPlainImageRegression")
    $validationLines.Add("nativeTapAction=$hasNativeTapAction")
    $validationLines.Add("explicitContentHandler=$hasExplicitContentHandler")
    $validationLines.Add("contentTapHandledEvent=$hasContentTapHandledEvent")
    $validationLines |
        Out-File -Encoding utf8 (Join-Path $ArtifactDir "reader-tap-validation.txt")

    if ($hasPlainImageRegression) {
        throw "Reader tap validation failed: logcat still contains 'Reader surface tap ignored for content hitType=5'. See $ArtifactDir"
    }
    if ($RequireReaderTapAction -and -not $hasNativeTapAction) {
        throw "Reader tap validation failed: no native 'Reader surface tap action=' log was captured. See $ArtifactDir"
    }
}

Write-Host "Reader smoke artifacts: $ArtifactDir"
Write-Host "PID: $processId"
Get-Content -LiteralPath (Join-Path $ArtifactDir "package-version.txt")
$devtoolsSockets = Get-Content -LiteralPath (Join-Path $ArtifactDir "webview-devtools-sockets.txt")
if ($devtoolsSockets) {
    Write-Host "WebView devtools sockets:"
    $devtoolsSockets | ForEach-Object { Write-Host $_ }
}
