[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $DeviceSerial,

    [Parameter(Mandatory = $true)]
    [ValidateSet('slow-next', 'snap-back', 'previous', 'rapid-turns', 'idle')]
    [string] $Scenario,

    [Parameter(Mandatory = $true)]
    [ValidateSet('portrait', 'landscape')]
    [string] $Orientation,

    [ValidateSet('ltr', 'rtl')]
    [string] $ReaderDirection = 'ltr',

    [string] $OutputRoot = '.codex-validation/reader-visual',

    [ValidateRange(10, 180)]
    [int] $TimeLimitSeconds = 30,

    [ValidateRange(1, 10000)]
    [int] $DisplayWidth,

    [ValidateRange(1, 10000)]
    [int] $DisplayHeight,

    [switch] $PlanOnly,
    [switch] $SkipAnalysis,
    [switch] $RequireAllVisualChecks
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$ReaderDevPackage = 'darkaxt.navic.readerdev'
$RecordingStartupTimeoutSeconds = 10
$RecordingDurationToleranceSeconds = 0.5

function Invoke-AdbText {
    param([string[]] $Arguments, [string] $Description)

    $output = @(& adb -s $DeviceSerial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed (exit=$LASTEXITCODE)."
    }
    return ($output | Out-String).Trim()
}

function Get-DeviceKey([string] $Serial) {
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Serial)
        return ([BitConverter]::ToString($hasher.ComputeHash($bytes))).Replace('-', '').Substring(0, 12)
    } finally {
        $hasher.Dispose()
    }
}

function Get-LogicalDisplaySize {
    if ($DisplayWidth -gt 0 -and $DisplayHeight -gt 0) {
        return [pscustomobject]@{ Width = $DisplayWidth; Height = $DisplayHeight }
    }
    if (($DisplayWidth -gt 0) -xor ($DisplayHeight -gt 0)) {
        throw 'DisplayWidth and DisplayHeight must be supplied together.'
    }
    $sizeText = Invoke-AdbText @('shell', 'wm', 'size') 'Display-size query'
    $matches = [regex]::Matches($sizeText, '(?:Override|Physical) size:\s*(\d+)x(\d+)')
    if ($matches.Count -eq 0) {
        throw 'Unable to parse the Android display size.'
    }
    $selected = @($matches) | Where-Object { $_.Value.StartsWith('Override') } |
        Select-Object -Last 1
    if ($null -eq $selected) { $selected = $matches[$matches.Count - 1] }
    $width = [int]$selected.Groups[1].Value
    $height = [int]$selected.Groups[2].Value
    if ($Orientation -eq 'landscape' -and $width -lt $height) {
        $width, $height = $height, $width
    } elseif ($Orientation -eq 'portrait' -and $width -gt $height) {
        $width, $height = $height, $width
    }
    return [pscustomobject]@{ Width = $width; Height = $height }
}

function Get-RecordingSize([object] $Display) {
    $maximumEdge = 1280.0
    $scale = [Math]::Min(
        1.0,
        $maximumEdge / [Math]::Max($Display.Width, $Display.Height)
    )
    $width = [Math]::Max(
        2,
        [int]([Math]::Round(($Display.Width * $scale) / 2.0) * 2)
    )
    $height = [Math]::Max(
        2,
        [int]([Math]::Round(($Display.Height * $scale) / 2.0) * 2)
    )
    return [pscustomobject]@{ Width = $width; Height = $height }
}

function New-SwipeAction {
    param(
        [string] $Name,
        [int] $AtMs,
        [double] $StartX,
        [double] $EndX,
        [int] $DurationMs,
        [object] $Display
    )

    return [pscustomobject]@{
        Name = $Name
        AtMs = $AtMs
        Kind = 'swipe'
        StartX = [int][Math]::Round($Display.Width * $StartX)
        StartY = [int][Math]::Round($Display.Height * 0.60)
        EndX = [int][Math]::Round($Display.Width * $EndX)
        EndY = [int][Math]::Round($Display.Height * 0.60)
        DurationMs = $DurationMs
    }
}

function New-ReaderVisualProbePlan {
    param([object] $Display)

    $recordingSize = Get-RecordingSize $Display
    $nextStart = if ($ReaderDirection -eq 'ltr') { 0.88 } else { 0.12 }
    $nextEnd = if ($ReaderDirection -eq 'ltr') { 0.18 } else { 0.82 }
    $snapEnd = if ($ReaderDirection -eq 'ltr') { 0.67 } else { 0.33 }
    $previousStart = 1.0 - $nextStart
    $previousEnd = 1.0 - $nextEnd
    $actions = switch ($Scenario) {
        'slow-next' {
            @(New-SwipeAction 'slow-next' 1800 $nextStart $nextEnd 1500 $Display)
        }
        'snap-back' {
            @(New-SwipeAction 'snap-back' 1800 $nextStart $snapEnd 1200 $Display)
        }
        'previous' {
            @(New-SwipeAction 'previous' 1800 $previousStart $previousEnd 1100 $Display)
        }
        'rapid-turns' {
            @(0..3 | ForEach-Object {
                New-SwipeAction "rapid-next-$($_ + 1)" (1700 + $_ * 700) `
                    $nextStart $nextEnd 160 $Display
            })
        }
        'idle' { @() }
    }
    $lastActionEnd = @($actions | ForEach-Object { $_.AtMs + $_.DurationMs } |
        Measure-Object -Maximum).Maximum
    $durationMs = if ($Scenario -eq 'idle') {
        9000
    } elseif ($null -eq $lastActionEnd) {
        5000
    } else {
        [int]$lastActionEnd + 2500
    }
    if ($durationMs + $RecordingStartupTimeoutSeconds * 1000 -ge
        $TimeLimitSeconds * 1000) {
        throw 'TimeLimitSeconds does not leave enough room for recorder startup and the selected probe.'
    }
    return [pscustomobject]@{
        SchemaVersion = 1
        Package = $ReaderDevPackage
        Scenario = $Scenario
        Orientation = $Orientation
        ReaderDirection = $ReaderDirection
        DisplayWidth = $Display.Width
        DisplayHeight = $Display.Height
        RecordingWidth = $recordingSize.Width
        RecordingHeight = $recordingSize.Height
        DurationMs = $durationMs
        Actions = @($actions)
    }
}

function Wait-UntilElapsed {
    param([Diagnostics.Stopwatch] $Stopwatch, [int] $TargetMs)

    while ($Stopwatch.ElapsedMilliseconds -lt $TargetMs) {
        $remaining = $TargetMs - $Stopwatch.ElapsedMilliseconds
        Start-Sleep -Milliseconds ([Math]::Min(50, [Math]::Max(1, $remaining)))
    }
}

function Wait-RecordingReady {
    param(
        [Diagnostics.Process] $Recording,
        [string] $RemotePath
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($RecordingStartupTimeoutSeconds)
    do {
        if ($Recording.HasExited) {
            throw "Android screen recording exited before capture started (exit=$($Recording.ExitCode))."
        }
        $sizeText = @(
            & adb -s $DeviceSerial shell stat -c '%s' $RemotePath 2>$null
        ) | Out-String
        if ($LASTEXITCODE -eq 0 -and
            $sizeText.Trim() -match '^\d+$' -and
            [long]$sizeText.Trim() -gt 0) {
            return
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Android screen recording did not become ready within $RecordingStartupTimeoutSeconds seconds."
}

function Get-VideoMetadata([string] $Path) {
    $metadataText = @(
        & ffprobe -v error -select_streams 'v:0' -count_frames `
            -show_entries 'stream=duration,nb_read_frames:format=duration' `
            -of json $Path 2>&1
    ) | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw 'Recorded video metadata query failed.'
    }
    try {
        $metadata = $metadataText | ConvertFrom-Json
    } catch {
        throw 'Recorded video metadata is invalid.'
    }
    $stream = @($metadata.streams)[0]
    $frameCount = 0L
    if ($null -eq $stream -or
        -not [long]::TryParse("$($stream.nb_read_frames)", [ref]$frameCount) -or
        $frameCount -le 0) {
        throw 'Recorded video does not contain a decodable video frame.'
    }
    $duration = 0.0
    $durationText = @(
        "$($metadata.format.duration)",
        "$($stream.duration)"
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -First 1
    if (-not [string]::IsNullOrWhiteSpace($durationText)) {
        [void][double]::TryParse(
            $durationText,
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$duration
        )
    }
    return [pscustomobject]@{
        DurationSeconds = $duration
        FrameCount = $frameCount
    }
}

function Invoke-ProbeAction {
    param([object] $Action)

    if ($Action.Kind -ne 'swipe') {
        throw "Unsupported visual probe action: $($Action.Kind)"
    }
    Invoke-AdbText @(
        'shell', 'input', 'swipe',
        $Action.StartX, $Action.StartY,
        $Action.EndX, $Action.EndY,
        $Action.DurationMs
    ) "Gesture $($Action.Name)" | Out-Null
}

$display = Get-LogicalDisplaySize
$plan = New-ReaderVisualProbePlan $display
$outputDirectory = [IO.Path]::GetFullPath($OutputRoot)
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$planPath = Join-Path $outputDirectory "$Orientation-$Scenario.plan.json"
$plan | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $planPath -Encoding utf8
if ($PlanOnly) {
    Write-Output $planPath
    return
}

foreach ($command in @('adb', 'python', 'ffmpeg', 'ffprobe')) {
    if ($null -eq (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required visual QA command is unavailable: $command"
    }
}
$readerDevPid = Invoke-AdbText @('shell', 'pidof', $ReaderDevPackage) 'ReaderDev PID query'
if ($readerDevPid -notmatch '^\d+(?:\s+\d+)*$') {
    throw 'ReaderDev is not running on the selected device.'
}
$resumed = Invoke-AdbText @('shell', 'dumpsys', 'activity', 'activities') 'Foreground activity query'
$resumedReaderDev = @($resumed -split '\r?\n' | Where-Object {
    $_ -match '(?:mResumedActivity|topResumedActivity|ResumedActivity)' -and
        $_ -match [regex]::Escape($ReaderDevPackage)
})
if ($resumedReaderDev.Count -eq 0) {
    throw 'ReaderDev is not the resumed Android activity.'
}

$deviceKey = Get-DeviceKey $DeviceSerial
$stem = "$Orientation-$Scenario-$deviceKey"
$videoPath = Join-Path $outputDirectory "$stem.mp4"
$manifestPath = Join-Path $outputDirectory "$stem.events.json"
$analysisPath = Join-Path $outputDirectory "$stem.analysis.json"
foreach ($ownedPath in @($videoPath, $manifestPath, $analysisPath)) {
    if (Test-Path -LiteralPath $ownedPath) {
        throw "Visual QA refuses to overwrite an existing artifact: $ownedPath"
    }
}
$remotePath = "/sdcard/navic-reader-visual-$Orientation-$Scenario.mp4"
Invoke-AdbText @('shell', 'rm', '-f', $remotePath) 'Remote recording cleanup' | Out-Null
$recording = $null
$recordingStopped = $false
$events = @()
$videoMetadata = $null
$captureElapsedMs = $null
try {
    $recording = Start-Process -FilePath 'adb' -ArgumentList @(
        '-s', $DeviceSerial, 'shell', 'screenrecord',
        '--size', "$($plan.RecordingWidth)x$($plan.RecordingHeight)",
        '--time-limit', $TimeLimitSeconds, $remotePath
    ) -PassThru -NoNewWindow
    Wait-RecordingReady -Recording $recording -RemotePath $remotePath
    Start-Sleep -Milliseconds 500
    if ($recording.HasExited) {
        throw "Android screen recording exited during capture stabilization (exit=$($recording.ExitCode))."
    }
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    foreach ($action in $plan.Actions) {
        Wait-UntilElapsed $stopwatch $action.AtMs
        $startedMs = $stopwatch.ElapsedMilliseconds
        Invoke-ProbeAction $action
        $events += [pscustomobject]@{
            Name = $action.Name
            Kind = $action.Kind
            ScheduledAtMs = $action.AtMs
            StartedAtMs = $startedMs
            FinishedAtMs = $stopwatch.ElapsedMilliseconds
        }
    }
    Wait-UntilElapsed $stopwatch $plan.DurationMs
    $captureElapsedMs = $stopwatch.ElapsedMilliseconds
    & adb -s $DeviceSerial shell pkill -2 screenrecord 2>$null | Out-Null
    $recordingStopped = $true
    if (-not $recording.WaitForExit(10000)) {
        $recording.Kill()
        throw 'Android screen recording did not stop within ten seconds.'
    }
    if ($recording.ExitCode -ne 0) {
        throw "Android screen recording failed (exit=$($recording.ExitCode))."
    }
    Invoke-AdbText @('pull', $remotePath, $videoPath) 'Recording pull' | Out-Null
    if (-not (Test-Path -LiteralPath $videoPath -PathType Leaf) -or
        (Get-Item -LiteralPath $videoPath).Length -eq 0) {
        throw 'Pulled visual QA recording is missing or empty.'
    }
    $videoMetadata = Get-VideoMetadata $videoPath
    $minimumDurationSeconds =
        $plan.DurationMs / 1000.0 - $RecordingDurationToleranceSeconds
    if ($Scenario -ne 'idle' -and
        $videoMetadata.DurationSeconds -lt $minimumDurationSeconds) {
        throw "Visual QA recording ended before the probe completed (duration=$($videoMetadata.DurationSeconds))."
    }
} finally {
    if ($null -ne $recording -and -not $recording.HasExited) {
        if (-not $recordingStopped) {
            & adb -s $DeviceSerial shell pkill -2 screenrecord 2>$null | Out-Null
        }
        if (-not $recording.WaitForExit(5000)) { $recording.Kill() }
    }
    & adb -s $DeviceSerial shell rm -f $remotePath 2>$null | Out-Null
}

$commit = (& git -C (Join-Path $PSScriptRoot '..') rev-parse HEAD 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') {
    throw 'Unable to bind the visual probe to a Git commit.'
}
$manifest = [pscustomobject]@{
    SchemaVersion = 1
    Privacy = [pscustomobject]@{
        FramesPersisted = $false
        OcrPerformed = $false
        ReaderContentRecordedOnlyInLocalMp4 = $true
    }
    GitCommit = $commit
    DeviceKey = $deviceKey
    Package = $ReaderDevPackage
    Scenario = $Scenario
    Orientation = $Orientation
    ReaderDirection = $ReaderDirection
    DisplayWidth = $display.Width
    DisplayHeight = $display.Height
    RecordingWidth = $plan.RecordingWidth
    RecordingHeight = $plan.RecordingHeight
    DurationMs = $plan.DurationMs
    CaptureElapsedMs = $captureElapsedMs
    Events = $events
    VideoArtifact = [IO.Path]::GetFileName($videoPath)
    VideoDurationSeconds = $videoMetadata.DurationSeconds
    VideoFrameCount = $videoMetadata.FrameCount
    StaticIdleCapture = $Scenario -eq 'idle' -and $videoMetadata.FrameCount -eq 1
    VideoBytes = (Get-Item -LiteralPath $videoPath).Length
    VideoSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $videoPath).Hash.ToLowerInvariant()
}
$manifest | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $manifestPath -Encoding utf8

if (-not $SkipAnalysis) {
    $analyzer = Join-Path $PSScriptRoot 'reader-visual-qa.py'
    $analysisArguments = @(
        $analyzer,
        '--video', $videoPath,
        '--scenario', $Scenario,
        '--orientation', $Orientation,
        '--output', $analysisPath
    )
    if ($Scenario -eq 'idle') {
        $expectedDuration = ($plan.DurationMs / 1000.0).ToString(
            '0.###',
            [Globalization.CultureInfo]::InvariantCulture
        )
        $analysisArguments += @('--expected-duration-seconds', $expectedDuration)
    }
    if ($RequireAllVisualChecks) { $analysisArguments += '--require-all' }
    & python @analysisArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Reader visual frame analysis failed (exit=$LASTEXITCODE)."
    }
}

Write-Output $manifestPath
