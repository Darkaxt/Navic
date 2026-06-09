param(
    [string] $Package = "darkaxt.navic",
    [string] $ApkPath,
    [string] $ArtifactDir,
    [string[]] $Tap = @(),
    [int] $LaunchWaitSeconds = 5,
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

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found on PATH"
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

$processId = (& adb shell pidof $Package).Trim()
if ([string]::IsNullOrWhiteSpace($processId)) {
    throw "Package is not running: $Package"
}

adb shell dumpsys package $Package |
    Select-String -Pattern "versionCode|versionName|lastUpdateTime" |
    ForEach-Object { $_.Line.Trim() } |
    Out-File -Encoding utf8 (Join-Path $ArtifactDir "package-version.txt")

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

Write-Host "Reader smoke artifacts: $ArtifactDir"
Write-Host "PID: $processId"
Get-Content -LiteralPath (Join-Path $ArtifactDir "package-version.txt")
$devtoolsSockets = Get-Content -LiteralPath (Join-Path $ArtifactDir "webview-devtools-sockets.txt")
if ($devtoolsSockets) {
    Write-Host "WebView devtools sockets:"
    $devtoolsSockets | ForEach-Object { Write-Host $_ }
}
