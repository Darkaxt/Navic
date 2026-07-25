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

    [ValidateRange(1, 180)]
    [int] $VisualReadyTimeoutSeconds = 60,

    [switch] $PlanOnly,
    [switch] $SkipAnalysis,
    [switch] $RequireAllVisualChecks
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$ReaderDevPackage = 'darkaxt.navic.readerdev'
$RecordingStartupTimeoutSeconds = 10

function Invoke-AdbText {
    param([string[]] $Arguments, [string] $Description)

    $output = @(& adb -s $DeviceSerial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed (exit=$LASTEXITCODE)."
    }
    return ($output | Out-String).Trim()
}

function Wait-ReaderDevVisualReady([string] $ReaderPid) {
    $deadline = [DateTime]::UtcNow.AddSeconds($VisualReadyTimeoutSeconds)
    do {
        $log = Invoke-AdbText @(
            'logcat', '-d', '-v', 'brief', "--pid=$ReaderPid",
            'ReaderPageRasterPreparation:I',
            'KomikkuReaderNativeFrameHost:I',
            '*:S'
        ) 'ReaderDev visual-readiness query'
        $latest = @($log -split '\r?\n' | Where-Object {
            $_.Contains('Page preparation state phase=')
        } | Select-Object -Last 1)
        $latestRepair = @($log -split '\r?\n' | Where-Object {
            $_.Contains('reader-repair ') && $_.Contains(' state=')
        } | Select-Object -Last 1)
        $repairBusy = $latestRepair.Count -gt 0 -and @(
            'state=Started',
            'state=Ready',
            'state=Submitted'
        ).Where({ $latestRepair[0].Contains($_) }).Count -gt 0
        if ($latest.Count -gt 0) {
            if ($latest[0].Contains('phase=Ready') -and
                $latest[0].Contains('gestures=Allow') -and
                -not $repairBusy) {
                return
            }
            if ($latest[0].Contains('phase=Failed')) {
                throw 'ReaderDev page preparation failed before visual capture.'
            }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "ReaderDev did not become visually ready within $VisualReadyTimeoutSeconds seconds."
}

function Remove-RemoteReaderArtifact {
    param(
        [string] $Path,
        [string] $Description
    )

    Invoke-AdbText @('shell', 'rm', '-f', $Path) "$Description cleanup" |
        Out-Null
    Invoke-AdbText @('shell', 'test', '!', '-e', $Path) `
        "$Description deletion verification" | Out-Null
}

function Initialize-ReaderDevComposition([object] $Plan) {
    $remotePath = "/sdcard/navic-reader-visual-composition-preflight.mp4"
    Remove-RemoteReaderArtifact $remotePath 'Composition preflight'
    try {
        & adb -s $DeviceSerial shell screenrecord `
            --size "$($Plan.RecordingWidth)x$($Plan.RecordingHeight)" `
            --time-limit 1 $remotePath 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'ReaderDev video-composition preflight failed.'
        }
    } finally {
        Remove-RemoteReaderArtifact $remotePath 'Composition preflight'
    }
    Start-Sleep -Milliseconds 250
}

function Get-ReaderVisualMarkerTimestamp {
    param(
        [string] $Log,
        [string] $Token
    )

    $escapedToken = [regex]::Escape($Token)
    $pattern = '^\s*(?<timestamp>\d+(?:\.\d+)?)\s+\d+\s+\d+\s+[A-Z]\s+' +
        "NavicReaderVisualQa:\s+probe-start:$escapedToken$"
    $timestamps = @(
        foreach ($line in @($Log -split '\r?\n')) {
            $match = [regex]::Match($line, $pattern)
            if ($match.Success) {
                [double]::Parse(
                    $match.Groups['timestamp'].Value,
                    [Globalization.CultureInfo]::InvariantCulture
                )
            }
        }
    )
    if ($timestamps.Count -eq 0) { return $null }
    return [double]$timestamps[-1]
}

function Write-ReaderVisualLogMarker {
    $token = [guid]::NewGuid().ToString('N')
    Invoke-AdbText @(
        'shell', 'log', '-p', 'i', '-t', 'NavicReaderVisualQa',
        "probe-start:$token"
    ) 'Reader visual log marker' | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(2)
    do {
        $log = Invoke-AdbText @(
            'logcat', '-d', '-v', 'monotonic',
            'NavicReaderVisualQa:I',
            '*:S'
        ) 'Reader visual log-marker query'
        $timestamp = Get-ReaderVisualMarkerTimestamp -Log $log -Token $token
        if ($null -ne $timestamp) { return [double]$timestamp }
        Start-Sleep -Milliseconds 50
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Reader visual log marker did not become observable.'
}

function ConvertFrom-ReaderGestureTerminalLog {
    param(
        [string] $Log,
        [double] $AfterMonotonicSeconds
    )

    $terminals = @()
    $pattern = '^\s*(?<timestamp>\d+(?:\.\d+)?)\s+\d+\s+\d+\s+[A-Z]\s+' +
        'KomikkuReaderNativeFrameHost:\s+' +
        '(?:(?<replay>Reader gesture terminal replay)\s+)?' +
        'Reader gesture terminal gestureId=(?<gestureId>\d+)\s+' +
        'outcome=(?<outcome>[A-Za-z]+)\s+won=(?<won>true|false)\s+' +
        'detail=(?<detail>.+)$'
    foreach ($line in @($Log -split '\r?\n')) {
        $match = [regex]::Match($line, $pattern)
        if (-not $match.Success) { continue }
        $timestamp = [double]::Parse(
            $match.Groups['timestamp'].Value,
            [Globalization.CultureInfo]::InvariantCulture
        )
        if ($timestamp -lt $AfterMonotonicSeconds) { continue }
        $pageChange = $null
        $pageChangeMatch = [regex]::Match(
            $match.Groups['detail'].Value,
            '^SettlementCompleted\(pageChange=(NONE|NEXT|PREVIOUS),'
        )
        if ($pageChangeMatch.Success) {
            $pageChange = $pageChangeMatch.Groups[1].Value
        }
        $terminals += [pscustomobject]@{
            Timestamp = $timestamp
            GestureId = [long]$match.Groups['gestureId'].Value
            Outcome = $match.Groups['outcome'].Value
            Won = $match.Groups['won'].Value -eq 'true'
            Replay = $match.Groups['replay'].Success
            PageChange = $pageChange
        }
    }
    return @($terminals)
}

function Get-ReaderGestureTerminals {
    param(
        [string] $ReaderPid,
        [double] $AfterMonotonicSeconds
    )

    $log = Invoke-AdbText @(
        'logcat', '-d', '-v', 'monotonic', "--pid=$ReaderPid",
        'KomikkuReaderNativeFrameHost:I',
        '*:S'
    ) 'ReaderDev gesture-terminal query'
    return @(ConvertFrom-ReaderGestureTerminalLog `
        -Log $log `
        -AfterMonotonicSeconds $AfterMonotonicSeconds)
}

function Assert-ReaderGestureTerminalSet {
    param(
        [object[]] $Terminals,
        [string] $ScenarioName,
        [int] $ExpectedTerminalCount
    )

    $replays = @($Terminals | Where-Object { $_.Replay -or -not $_.Won })
    if ($replays.Count -gt 0) {
        throw "ReaderDev emitted a duplicate terminal attempt during $ScenarioName."
    }
    $winners = @($Terminals | Where-Object { $_.Won -and -not $_.Replay })
    $uniqueGestureIds = @($winners | ForEach-Object { $_.GestureId } |
        Sort-Object -Unique)
    if ($winners.Count -ne $ExpectedTerminalCount -or
        $uniqueGestureIds.Count -ne $ExpectedTerminalCount) {
        throw "ReaderDev emitted $($winners.Count) unique winning terminal outcomes; expected $ExpectedTerminalCount for $ScenarioName."
    }
    $expectedOutcome = switch ($ScenarioName) {
        'slow-next' { 'CommittedForward' }
        'snap-back' { 'CancelledByUser' }
        'previous' { 'CommittedBackward' }
        'rapid-turns' { 'CommittedForward' }
        'idle' { $null }
    }
    $expectedPageChange = switch ($ScenarioName) {
        'slow-next' { 'NEXT' }
        'snap-back' { 'NONE' }
        'previous' { 'PREVIOUS' }
        'rapid-turns' { 'NEXT' }
        'idle' { $null }
    }
    foreach ($terminal in $winners) {
        if ($terminal.Outcome -ne $expectedOutcome -or
            $terminal.PageChange -ne $expectedPageChange) {
            throw "ReaderDev gesture semantics did not match the $ScenarioName probe."
        }
    }
    return [pscustomobject]@{
        ExpectedTerminalCount = $ExpectedTerminalCount
        ObservedTerminalCount = $winners.Count
        ExpectedOutcome = $expectedOutcome
        ExpectedPageChange = $expectedPageChange
        Matched = $true
    }
}

function Assert-ReaderGestureSemantics {
    param(
        [string] $ReaderPid,
        [double] $ProbeStartedAtMonotonicSeconds,
        [object] $Plan,
        [string] $ScenarioName
    )

    $terminals = @(Get-ReaderGestureTerminals `
        -ReaderPid $ReaderPid `
        -AfterMonotonicSeconds $ProbeStartedAtMonotonicSeconds)
    return Assert-ReaderGestureTerminalSet `
        -Terminals $terminals `
        -ScenarioName $ScenarioName `
        -ExpectedTerminalCount $Plan.Actions.Count
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
    $snapEnd = if ($ReaderDirection -eq 'ltr') { 0.80 } else { 0.20 }
    $snapDistancePixels = [Math]::Abs(
        $Display.Width * ($nextStart - $snapEnd)
    )
    $snapDurationMs = [Math]::Max(
        1200,
        [int][Math]::Ceiling($snapDistancePixels / 125.0 * 1000.0)
    )
    $previousStart = 1.0 - $nextStart
    $previousEnd = 1.0 - $nextEnd
    $actions = switch ($Scenario) {
        'slow-next' {
            @(New-SwipeAction 'slow-next' 1800 $nextStart $nextEnd 1500 $Display)
        }
        'snap-back' {
            @(New-SwipeAction 'snap-back' 1800 $nextStart $snapEnd `
                $snapDurationMs $Display)
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
$readerDevMainPid = @($readerDevPid -split '\s+')[0]
Start-Sleep -Milliseconds 2000
Wait-ReaderDevVisualReady $readerDevMainPid

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
Initialize-ReaderDevComposition $plan
$probeStartedAtMonotonicSeconds = Write-ReaderVisualLogMarker
$remotePath = "/sdcard/navic-reader-visual-$Orientation-$Scenario.mp4"
Remove-RemoteReaderArtifact $remotePath 'Remote recording'
$recording = $null
$recordingStopped = $false
$events = @()
$videoMetadata = $null
$captureElapsedMs = $null
$gestureSemantics = $null
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
    if ($Scenario -ne 'idle' -and $videoMetadata.FrameCount -lt 2) {
        throw 'Visual QA gesture recording contains no visible frame transition.'
    }
} finally {
    if ($null -ne $recording -and -not $recording.HasExited) {
        if (-not $recordingStopped) {
            & adb -s $DeviceSerial shell pkill -2 screenrecord 2>$null | Out-Null
        }
        if (-not $recording.WaitForExit(5000)) { $recording.Kill() }
    }
    Remove-RemoteReaderArtifact $remotePath 'Remote recording'
}
$gestureSemantics = Assert-ReaderGestureSemantics `
    -ReaderPid $readerDevMainPid `
    -ProbeStartedAtMonotonicSeconds $probeStartedAtMonotonicSeconds `
    -Plan $plan `
    -ScenarioName $Scenario

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
    CompositionPreflight = [pscustomobject]@{
        Method = 'discarded-screenrecord'
        DurationSeconds = 1
        HostArtifactPersisted = $false
        RemoteArtifactRetained = $false
        ReaderInputInjected = $false
    }
    GestureSemantics = $gestureSemantics
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
