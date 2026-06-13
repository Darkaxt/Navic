param(
    [string] $Package = "darkaxt.navic",
    [string] $ApkPath,
    [string] $ArtifactDir,
    [string[]] $Tap = @(),
    [string[]] $TapFraction = @(),
    [string[]] $Swipe = @(),
    [string[]] $SwipeFraction = @(),
    [ValidateSet("None", "ReaderHorizontalZones")]
    [string] $TapPreset = "None",
    [string] $ExpectedVersionName,
    [int] $LaunchWaitSeconds = 5,
    [int] $CaptureWaitSeconds = 0,
    [switch] $ValidateReaderTaps,
    [switch] $RequireReaderTapAction,
    [switch] $RequireShellCoverSwipe,
    [switch] $RequireContentTapHandled,
    [switch] $RequireTextureDiagnostics,
    [switch] $CaptureReaderDiagnostics,
    [switch] $NoLaunch
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    & adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-AdbScreenSize {
    $wmSize = (& adb shell wm size) -join "`n"
    if ($wmSize -notmatch '(\d+)x(\d+)') {
        throw "Could not parse adb shell wm size output: $wmSize"
    }
    return [pscustomobject]@{
        Width = [int] $Matches[1]
        Height = [int] $Matches[2]
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

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found on PATH"
}

$devices = @(
    & adb devices |
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
    & adb shell monkey -p $Package 1 | Out-File -Encoding utf8 (Join-Path $ArtifactDir "launch.txt")
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to launch $Package"
    }
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

if ($SwipeFraction.Count -gt 0) {
    $screenSize = Get-AdbScreenSize
    foreach ($swipeFractionSpec in $SwipeFraction) {
        $Swipe += Convert-SwipeFraction -SwipeSpec $swipeFractionSpec -Width $screenSize.Width -Height $screenSize.Height
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

$processId = (& adb shell pidof $Package).Trim()
if ([string]::IsNullOrWhiteSpace($processId)) {
    throw "Package is not running: $Package"
}

adb shell dumpsys package $Package |
    Select-String -Pattern "versionCode|versionName|lastUpdateTime" |
    ForEach-Object { $_.Line.Trim() } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "package-version.txt")

$packageVersionText = Get-Content -LiteralPath (Join-Path $ArtifactDir "package-version.txt") -Raw
if (-not [string]::IsNullOrWhiteSpace($ExpectedVersionName)) {
    if ($packageVersionText -notmatch [regex]::Escape($ExpectedVersionName)) {
        throw "Installed $Package version did not contain expected versionName '$ExpectedVersionName'. Captured version: $packageVersionText"
    }
}

adb shell cat /proc/net/unix |
    Select-String -Pattern "webview_devtools|chrome_devtools" -CaseSensitive:$false |
    ForEach-Object { $_.Line.Trim() } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "webview-devtools-sockets.txt")

Invoke-Adb @("shell", "screencap", "-p", "/sdcard/navic-reader-smoke.png")
Invoke-Adb @("pull", "/sdcard/navic-reader-smoke.png", (Join-Path $ArtifactDir "screen.png"))
Invoke-Adb @("shell", "rm", "/sdcard/navic-reader-smoke.png")

adb exec-out uiautomator dump /dev/tty 2>$null |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "window.xml")

adb logcat -d "--pid=$processId" -v time |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "logcat-full.log")

Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
    Select-String -Pattern "Reader|Foliate|Paginator|content-layout|iframe-srcdoc|firstText|publicationReady|locationChanged|AndroidRuntime|FATAL|ERROR|WARNING|Exception|503|404|unsupported" -CaseSensitive:$false |
    ForEach-Object { $_.Line } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "logcat-reader.log")

$readerLogText = Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-reader.log") -Raw

if ($CaptureReaderDiagnostics) {
    $textureDiagnosticPattern = "surface-texture-scroll|surface-texture-update|texture:scroll|texture:update"
    $touchDiagnosticPattern = "Reader surface touch down|Reader surface tap action=|Reader surface dispatch center tap|Reader surface tap ignored|Reader shell cover swipe|Reader shell cover command|Reader bridge raw|Reader bridge event: contentTapHandled|readerContentTapHandled|content-touch:media|content-touch:link|image:sepia-overlay|link:navigate|link:media-tap|link:text-hit-miss"

    $textureDiagnosticsPath = Join-Path $ArtifactDir "reader-texture-diagnostics.log"
    $touchDiagnosticsPath = Join-Path $ArtifactDir "reader-touch-diagnostics.log"
    $summaryPath = Join-Path $ArtifactDir "reader-diagnostics-summary.txt"

    Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
        Select-String -Pattern $textureDiagnosticPattern -CaseSensitive:$false |
        ForEach-Object { $_.Line } |
        Out-File -Encoding utf8 $textureDiagnosticsPath

    Get-Content -LiteralPath (Join-Path $ArtifactDir "logcat-full.log") |
        Select-String -Pattern $touchDiagnosticPattern -CaseSensitive:$false |
        ForEach-Object { $_.Line } |
        Out-File -Encoding utf8 $touchDiagnosticsPath

    $textureDiagnosticsText = Get-Content -LiteralPath $textureDiagnosticsPath -Raw
    $touchDiagnosticsText = Get-Content -LiteralPath $touchDiagnosticsPath -Raw
    $summaryLines = @(
        "textureScrollLines=$((Select-String -Path $textureDiagnosticsPath -Pattern 'surface-texture-scroll' -CaseSensitive:$false).Count)",
        "textureUpdateLines=$((Select-String -Path $textureDiagnosticsPath -Pattern 'surface-texture-update' -CaseSensitive:$false).Count)",
        "readerSurfaceTouchDown=$($touchDiagnosticsText -match 'Reader surface touch down')",
        "readerSurfaceTapAction=$($touchDiagnosticsText -match 'Reader surface tap action=')",
        "readerCenterDispatch=$($touchDiagnosticsText -match 'Reader surface dispatch center tap')",
        "readerContentTapHandled=$($touchDiagnosticsText -match 'readerContentTapHandled|Reader bridge event: contentTapHandled')",
        "imageSepiaOverlay=$($touchDiagnosticsText -match 'image:sepia-overlay')",
        "linkNavigate=$($touchDiagnosticsText -match 'link:navigate')",
        "shellCoverSwipe=$($touchDiagnosticsText -match 'Reader shell cover swipe')",
        "textureHasPosition=$($textureDiagnosticsText -match 'pos=')",
        "textureHasBase=$($textureDiagnosticsText -match 'base=')",
        "textureHasDelta=$($textureDiagnosticsText -match 'delta=')",
        "textureHasDirection=$($textureDiagnosticsText -match 'dir=')",
        "textureHasPage=$($textureDiagnosticsText -match 'page=')",
        "textureHasHref=$($textureDiagnosticsText -match 'href=')"
    )
    $summaryLines | Out-File -Encoding utf8 $summaryPath

    if ($RequireShellCoverSwipe -and -not ($touchDiagnosticsText -match 'Reader shell cover swipe')) {
        throw "Reader diagnostics validation failed: no shell-cover swipe was captured. See $ArtifactDir"
    }
    if ($RequireContentTapHandled -and -not ($touchDiagnosticsText -match 'readerContentTapHandled|Reader bridge event: contentTapHandled')) {
        throw "Reader diagnostics validation failed: no readerContentTapHandled bridge event was captured. See $ArtifactDir"
    }
    if ($RequireTextureDiagnostics) {
        foreach ($requiredTextureField in @('pos=', 'base=', 'delta=', 'dir=', 'page=', 'href=')) {
            if ($textureDiagnosticsText -notmatch [regex]::Escape($requiredTextureField)) {
                throw "Reader diagnostics validation failed: texture diagnostics did not include '$requiredTextureField'. See $ArtifactDir"
            }
        }
    }
}

if ($ValidateReaderTaps) {
    $validationLines = New-Object System.Collections.Generic.List[string]
    $hasPlainImageRegression = $readerLogText -match "Reader surface tap ignored for content hitType=5"
    $hasNativeTapAction = $readerLogText -match "Reader surface tap action="
    $hasExplicitContentHandler = $readerLogText -match "Reader surface tap ignored for explicit content handler"
    $hasContentTapHandledEvent = $readerLogText -match "Reader bridge event: contentTapHandled"

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
