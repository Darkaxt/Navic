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
$GesturePostRollMilliseconds = 4000

function Get-ReaderVisualCaptureBackend([string] $Serial) {
    if ($Serial -match '^emulator-\d+$') {
        return 'emulator-framebuffer'
    }
    return 'android-screenrecord'
}

function Test-DisplaySizeMatchesOrientation {
    param(
        [int] $Width,
        [int] $Height,
        [string] $ExpectedOrientation
    )

    if ($ExpectedOrientation -eq 'landscape') {
        return $Width -gt $Height
    }
    return $Width -lt $Height
}

function Get-EmulatorFrameBufferNormalization {
    param(
        [int] $FrameBufferWidth,
        [int] $FrameBufferHeight,
        [int] $PhysicalDisplayWidth,
        [int] $PhysicalDisplayHeight,
        [int] $DisplayWidth,
        [int] $DisplayHeight
    )

    foreach ($dimension in @(
        $FrameBufferWidth,
        $FrameBufferHeight,
        $PhysicalDisplayWidth,
        $PhysicalDisplayHeight,
        $DisplayWidth,
        $DisplayHeight
    )) {
        if ($dimension -le 0) {
            throw 'Framebuffer normalization dimensions must be positive.'
        }
    }
    $upright = $FrameBufferWidth -eq $PhysicalDisplayWidth -and
        $FrameBufferHeight -eq $PhysicalDisplayHeight
    $rotatedClockwise = -not $upright -and
        $FrameBufferWidth -eq $PhysicalDisplayHeight -and
        $FrameBufferHeight -eq $PhysicalDisplayWidth
    if (-not $upright -and -not $rotatedClockwise) {
        throw 'Emulator framebuffer dimensions do not match the physical display in an upright or clockwise-rotated orientation.'
    }
    $contentAspect = if ($rotatedClockwise) {
        $DisplayHeight / [double]$DisplayWidth
    } else {
        $DisplayWidth / [double]$DisplayHeight
    }
    $widthFromHeight = [int]([Math]::Round(
        ($FrameBufferHeight * $contentAspect) / 2.0
    ) * 2)
    if ($widthFromHeight -le $FrameBufferWidth) {
        $cropWidth = [Math]::Max(2, $widthFromHeight)
        $cropHeight = $FrameBufferHeight - ($FrameBufferHeight % 2)
        $cropX = [int]([Math]::Floor(
            (($FrameBufferWidth - $cropWidth) / 2.0) / 2.0
        ) * 2)
        $cropY = 0
    } else {
        $cropWidth = $FrameBufferWidth - ($FrameBufferWidth % 2)
        $cropHeight = [int]([Math]::Round(
            ($FrameBufferWidth / $contentAspect) / 2.0
        ) * 2)
        $cropHeight = [Math]::Min($cropHeight, $FrameBufferHeight)
        $cropY = [int]([Math]::Floor(
            (($FrameBufferHeight - $cropHeight) / 2.0) / 2.0
        ) * 2)
        $cropX = 0
    }
    $filter = "crop=$($cropWidth):$($cropHeight):$($cropX):$($cropY)"
    if ($rotatedClockwise) {
        $filter += ',transpose=clock'
    }
    return [pscustomobject]@{
        CropX = $cropX
        CropY = $cropY
        CropWidth = $cropWidth
        CropHeight = $cropHeight
        OutputWidth = if ($rotatedClockwise) { $cropHeight } else { $cropWidth }
        OutputHeight = if ($rotatedClockwise) { $cropWidth } else { $cropHeight }
        Filter = $filter
    }
}

function Invoke-AdbText {
    param([string[]] $Arguments, [string] $Description)

    $output = @(& adb -s $DeviceSerial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed (exit=$LASTEXITCODE)."
    }
    return ($output | Out-String).Trim()
}

function Assert-DeterministicEmulatorViewport([object] $Display) {
    if ((Get-ReaderVisualCaptureBackend $DeviceSerial) -ne
        'emulator-framebuffer') {
        return
    }
    $accelerometerRotation = Invoke-AdbText @(
        'shell', 'settings', 'get', 'system', 'accelerometer_rotation'
    ) 'Emulator auto-rotation query'
    $userRotation = Invoke-AdbText @(
        'shell', 'settings', 'get', 'system', 'user_rotation'
    ) 'Emulator user-rotation query'
    if ($accelerometerRotation -ne '0' -or $userRotation -ne '0') {
        throw 'Emulator visual QA requires accelerometer_rotation=0 and user_rotation=0. Apply set-reader-dev-viewport.ps1 before recording.'
    }
    $orientationMatches = Test-DisplaySizeMatchesOrientation `
        -Width $Display.Width `
        -Height $Display.Height `
        -ExpectedOrientation $Orientation
    if (-not $orientationMatches) {
        throw "Emulator wm override does not match requested $Orientation orientation."
    }
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

function Assert-EmulatorConsoleResponse {
    param(
        [string] $Response,
        [string] $Description
    )

    if ($Response -match '(?m)^\s*KO(?::|\s|$)' -or
        $Response -notmatch '(?m)^\s*OK\s*$') {
        throw "$Description was rejected by the emulator console."
    }
}

function Invoke-EmulatorConsoleText {
    param(
        [string[]] $Arguments,
        [string] $Description
    )

    $response = Invoke-AdbText (@('emu') + $Arguments) $Description
    Assert-EmulatorConsoleResponse `
        -Response $response `
        -Description $Description
    return $response
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

function Remove-LocalReaderArtifact {
    param(
        [string] $Path,
        [string] $Description
    )

    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Force
    }
    if (Test-Path -LiteralPath $Path) {
        throw "$Description deletion verification failed."
    }
}

function Start-EmulatorFrameBufferRecording {
    param(
        [string] $Path,
        [int] $LimitSeconds
    )

    $emulatorPath = [IO.Path]::GetFullPath($Path).Replace('\', '/')
    Invoke-EmulatorConsoleText @(
        'screenrecord', 'start',
        '--time-limit', "$LimitSeconds",
        $emulatorPath
    ) 'Emulator framebuffer recording start' | Out-Null
}

function Stop-EmulatorFrameBufferRecording {
    Invoke-EmulatorConsoleText @(
        'screenrecord', 'stop'
    ) 'Emulator framebuffer recording stop' | Out-Null
}

function Convert-EmulatorFrameBufferRecording {
    param(
        [string] $SourcePath,
        [string] $TargetPath,
        [int] $DisplayWidth,
        [int] $DisplayHeight
    )

    if (-not (Test-Path -LiteralPath $SourcePath -PathType Leaf) -or
        (Get-Item -LiteralPath $SourcePath).Length -eq 0) {
        throw 'Emulator framebuffer recording is missing or empty.'
    }
    $sourceMetadata = Get-VideoMetadata $SourcePath
    $physicalSizeText = Invoke-AdbText @(
        'shell', 'wm', 'size'
    ) 'Physical display-size query'
    $physicalDisplay = ConvertFrom-AndroidRawPhysicalDisplaySize $physicalSizeText
    $normalization = Get-EmulatorFrameBufferNormalization `
        -FrameBufferWidth $sourceMetadata.Width `
        -FrameBufferHeight $sourceMetadata.Height `
        -PhysicalDisplayWidth $physicalDisplay.Width `
        -PhysicalDisplayHeight $physicalDisplay.Height `
        -DisplayWidth $DisplayWidth `
        -DisplayHeight $DisplayHeight
    $temporaryPath = "$TargetPath.$([guid]::NewGuid().ToString('N')).tmp.mp4"
    try {
        & ffmpeg -y -v error -i $SourcePath -map '0:v:0' -an `
            -vf $normalization.Filter `
            -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p `
            -movflags '+faststart' $temporaryPath
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporaryPath -PathType Leaf) -or
            (Get-Item -LiteralPath $temporaryPath).Length -eq 0) {
            throw 'Emulator framebuffer recording conversion failed.'
        }
        [IO.File]::Move(
            [IO.Path]::GetFullPath($temporaryPath),
            [IO.Path]::GetFullPath($TargetPath)
        )
    } finally {
        Remove-LocalReaderArtifact $temporaryPath 'Temporary MP4 conversion'
    }
}

function Initialize-ReaderDevComposition {
    param(
        [object] $Plan,
        [string] $OutputDirectory
    )

    if ($Plan.CaptureBackend -eq 'emulator-framebuffer') {
        $preflightPath = Join-Path $OutputDirectory (
            ".composition-preflight-$([guid]::NewGuid().ToString('N')).webm"
        )
        $started = $false
        try {
            Start-EmulatorFrameBufferRecording `
                -Path $preflightPath `
                -LimitSeconds 5
            $started = $true
            Start-Sleep -Milliseconds 1250
            Stop-EmulatorFrameBufferRecording
            $started = $false
            if (-not (Test-Path -LiteralPath $preflightPath -PathType Leaf) -or
                (Get-Item -LiteralPath $preflightPath).Length -eq 0) {
                throw 'Emulator framebuffer preflight produced no video.'
            }
        } finally {
            if ($started) {
                Stop-EmulatorFrameBufferRecording
                $started = $false
            }
            Remove-LocalReaderArtifact $preflightPath 'Composition preflight'
        }
        Start-Sleep -Milliseconds 250
        return
    }

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
        [string] $Token,
        [string] $MarkerName = 'probe-start'
    )

    $escapedToken = [regex]::Escape($Token)
    $escapedMarkerName = [regex]::Escape($MarkerName)
    $pattern = '^\s*(?<timestamp>\d+(?:\.\d+)?)\s+\d+\s+\d+\s+[A-Z]\s+' +
        "NavicReaderVisualQa:\s+$escapedMarkerName`:$escapedToken$"
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

function Get-ReaderActionDeviceTimings([object[]] $PendingActions) {
    $pending = @($PendingActions)
    if ($pending.Count -eq 0) { return @() }
    $log = Invoke-AdbText @(
        'logcat', '-d', '-v', 'monotonic',
        'NavicReaderVisualQa:I',
        '*:S'
    ) 'Reader visual action-marker query'
    return @(
        foreach ($action in $pending) {
            $start = Get-ReaderVisualMarkerTimestamp `
                -Log $log `
                -Token $action.MarkerToken `
                -MarkerName 'action-start'
            $finish = Get-ReaderVisualMarkerTimestamp `
                -Log $log `
                -Token $action.MarkerToken `
                -MarkerName 'action-finish'
            if ($null -eq $start -or $null -eq $finish) {
                throw "Gesture $($action.Event.Name) device timing markers are incomplete."
            }
            if ($finish -lt $start) {
                throw "Gesture $($action.Event.Name) device timing markers are reversed."
            }
            [pscustomobject]@{
                StartSeconds = [double]$start
                FinishSeconds = [double]$finish
            }
        }
    )
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
        [int] $InjectedActionCount,
        [int] $MinimumExpectedTerminalCount,
        [int] $MaximumExpectedTerminalCount
    )

    if ($MinimumExpectedTerminalCount -lt 0 -or
        $MaximumExpectedTerminalCount -lt $MinimumExpectedTerminalCount -or
        $MaximumExpectedTerminalCount -gt $InjectedActionCount) {
        throw "ReaderDev terminal expectations are invalid for $ScenarioName."
    }
    $replays = @($Terminals | Where-Object { $_.Replay -or -not $_.Won })
    if ($replays.Count -gt 0) {
        throw "ReaderDev emitted a duplicate terminal attempt during $ScenarioName."
    }
    $winners = @($Terminals | Where-Object { $_.Won -and -not $_.Replay })
    $uniqueGestureIds = @($winners | ForEach-Object { $_.GestureId } |
        Sort-Object -Unique)
    if ($winners.Count -gt $InjectedActionCount -or
        $uniqueGestureIds.Count -ne $winners.Count) {
        throw "ReaderDev emitted an invalid unique winning terminal set for $ScenarioName."
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
    $matchingWinners = @($winners | Where-Object {
        $_.Outcome -eq $expectedOutcome -and
            $_.PageChange -eq $expectedPageChange
    })
    $busyRejections = if ($ScenarioName -eq 'rapid-turns') {
        @($winners | Where-Object {
            $_.Outcome -in @('RejectedPreparing', 'RejectedSettling') -and
                $null -eq $_.PageChange
        })
    } else {
        @()
    }
    $recognizedWinnerCount = $matchingWinners.Count + $busyRejections.Count
    if ($matchingWinners.Count -lt $MinimumExpectedTerminalCount -or
        $matchingWinners.Count -gt $MaximumExpectedTerminalCount -or
        $recognizedWinnerCount -ne $winners.Count) {
        throw "ReaderDev emitted $($matchingWinners.Count) matching terminal outcomes; expected $MinimumExpectedTerminalCount..$MaximumExpectedTerminalCount for $ScenarioName."
    }
    return [pscustomobject]@{
        InjectedActionCount = $InjectedActionCount
        MinimumExpectedTerminalCount = $MinimumExpectedTerminalCount
        MaximumExpectedTerminalCount = $MaximumExpectedTerminalCount
        ObservedTerminalCount = $matchingWinners.Count
        BusyRejectionCount = $busyRejections.Count
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
    $minimumExpectedTerminalCount = if ($ScenarioName -eq 'rapid-turns') {
        [Math]::Min(2, $Plan.Actions.Count)
    } else {
        $Plan.Actions.Count
    }
    return Assert-ReaderGestureTerminalSet `
        -Terminals $terminals `
        -ScenarioName $ScenarioName `
        -InjectedActionCount $Plan.Actions.Count `
        -MinimumExpectedTerminalCount $minimumExpectedTerminalCount `
        -MaximumExpectedTerminalCount $Plan.Actions.Count
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

function ConvertFrom-AndroidCurrentDisplaySize([string] $WindowDisplayText) {
    $match = [regex]::Match(
        $WindowDisplayText,
        '(?m)^\s*init=\d+x\d+.*?\bcur=(\d+)x(\d+)\b'
    )
    if (-not $match.Success) {
        throw 'Unable to parse the current Android window display size.'
    }
    return [pscustomobject]@{
        Width = [int]$match.Groups[1].Value
        Height = [int]$match.Groups[2].Value
    }
}

function Get-LogicalDisplaySize {
    if ($DisplayWidth -gt 0 -and $DisplayHeight -gt 0) {
        if (-not $PlanOnly) {
            throw 'DisplayWidth and DisplayHeight may only be supplied with PlanOnly.'
        }
        return [pscustomobject]@{ Width = $DisplayWidth; Height = $DisplayHeight }
    }
    if (($DisplayWidth -gt 0) -xor ($DisplayHeight -gt 0)) {
        throw 'DisplayWidth and DisplayHeight must be supplied together.'
    }
    $captureBackend = Get-ReaderVisualCaptureBackend $DeviceSerial
    if ($captureBackend -eq 'android-screenrecord') {
        $windowDisplayText = Invoke-AdbText @(
            'shell', 'dumpsys', 'window', 'displays'
        ) 'Current Android window display query'
        $currentDisplay = ConvertFrom-AndroidCurrentDisplaySize $windowDisplayText
        $orientationMatches = Test-DisplaySizeMatchesOrientation `
            -Width $currentDisplay.Width `
            -Height $currentDisplay.Height `
            -ExpectedOrientation $Orientation
        if (-not $orientationMatches) {
            throw "Physical device current display does not match requested $Orientation orientation."
        }
        return $currentDisplay
    }
    $sizeText = Invoke-AdbText @('shell', 'wm', 'size') 'Display-size query'
    $matches = [regex]::Matches($sizeText, '(?:Override|Physical) size:\s*(\d+)x(\d+)')
    if ($matches.Count -eq 0) {
        throw 'Unable to parse the Android display size.'
    }
    $selected = @($matches) | Where-Object { $_.Value.StartsWith('Override') } |
        Select-Object -Last 1
    if ($null -eq $selected) { $selected = $matches[$matches.Count - 1] }
    $display = [pscustomobject]@{
        Width = [int]$selected.Groups[1].Value
        Height = [int]$selected.Groups[2].Value
    }
    $orientationMatches = Test-DisplaySizeMatchesOrientation `
        -Width $display.Width `
        -Height $display.Height `
        -ExpectedOrientation $Orientation
    if (-not $orientationMatches) {
        throw "Emulator wm override does not match requested $Orientation orientation."
    }
    return $display
}

function ConvertFrom-AndroidRawPhysicalDisplaySize([string] $SizeText) {
    $matches = [regex]::Matches($SizeText, 'Physical size:\s*(\d+)x(\d+)')
    if ($matches.Count -eq 0) {
        throw 'Unable to parse the Android physical display size.'
    }
    $selected = $matches[$matches.Count - 1]
    return [pscustomobject]@{
        Width = [int]$selected.Groups[1].Value
        Height = [int]$selected.Groups[2].Value
    }
}

function ConvertFrom-AndroidPhysicalDisplaySize {
    param(
        [string] $SizeText,
        [ValidateSet('portrait', 'landscape')]
        [string] $OrientationName
    )

    $physicalDisplay = ConvertFrom-AndroidRawPhysicalDisplaySize $SizeText
    $width = $physicalDisplay.Width
    $height = $physicalDisplay.Height
    if ($OrientationName -eq 'landscape' -and $width -lt $height) {
        $width, $height = $height, $width
    } elseif ($OrientationName -eq 'portrait' -and $width -gt $height) {
        $width, $height = $height, $width
    }
    return [pscustomobject]@{ Width = $width; Height = $height }
}

function Get-EmulatorFrameBufferSize {
    $sizeText = Invoke-AdbText @('shell', 'wm', 'size') 'Physical display-size query'
    return ConvertFrom-AndroidPhysicalDisplaySize `
        -SizeText $sizeText `
        -OrientationName $Orientation
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

    $captureBackend = Get-ReaderVisualCaptureBackend $DeviceSerial
    $recordingSize = if ($captureBackend -eq 'emulator-framebuffer') {
        Get-EmulatorFrameBufferSize
    } else {
        Get-RecordingSize $Display
    }
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
                New-SwipeAction "rapid-next-$($_ + 1)" (1700 + $_ * 1500) `
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
        [int]$lastActionEnd + $GesturePostRollMilliseconds
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
        CaptureBackend = $captureBackend
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

function Assert-ReaderActionCadence {
    param(
        [object] $Plan,
        [object[]] $Events,
        [object[]] $DeviceTimings
    )

    $allowedStartLagMs = 350
    $actions = @($Plan.Actions)
    $observedEvents = @($Events)
    $observedDeviceTimings = @($DeviceTimings)
    if ($observedEvents.Count -ne $actions.Count -or
        $observedDeviceTimings.Count -ne $actions.Count) {
        throw 'Visual probe action cadence does not cover every planned action.'
    }
    $maximumHostStartLagMs = 0L
    $maximumDeviceCommandStartDriftMs = 0L
    $overlappingCommandCount = 0
    $firstScheduledAtMs = if ($actions.Count -gt 0) {
        [long]$actions[0].AtMs
    } else {
        0L
    }
    $firstDeviceStartSeconds = if ($actions.Count -gt 0) {
        [double]$observedDeviceTimings[0].StartSeconds
    } else {
        0.0
    }
    for ($index = 0; $index -lt $actions.Count; $index += 1) {
        $action = $actions[$index]
        $event = $observedEvents[$index]
        $timing = $observedDeviceTimings[$index]
        if ($event.Name -ne $action.Name -or
            [long]$event.ScheduledAtMs -ne [long]$action.AtMs) {
            throw 'Visual probe action cadence does not match the planned sequence.'
        }
        $hostStartLagMs = [long]$event.StartedAtMs - [long]$action.AtMs
        if ($hostStartLagMs -lt 0) {
            throw 'Visual probe action started before its scheduled cadence.'
        }
        $maximumHostStartLagMs = [Math]::Max(
            $maximumHostStartLagMs,
            $hostStartLagMs
        )
        $scheduledOffsetMs = [long]$action.AtMs - $firstScheduledAtMs
        $deviceOffsetMs = [long][Math]::Round(
            ([double]$timing.StartSeconds - $firstDeviceStartSeconds) * 1000.0
        )
        $deviceStartDriftMs = [Math]::Abs(
            $deviceOffsetMs - $scheduledOffsetMs
        )
        $maximumDeviceCommandStartDriftMs = [Math]::Max(
            $maximumDeviceCommandStartDriftMs,
            $deviceStartDriftMs
        )
        if ([double]$timing.FinishSeconds -lt [double]$timing.StartSeconds) {
            throw 'Visual probe action device timing is reversed.'
        }
        if ($index + 1 -lt $actions.Count -and
            [double]$timing.FinishSeconds -gt
                [double]$observedDeviceTimings[$index + 1].StartSeconds) {
            $overlappingCommandCount += 1
        }
    }
    if ($maximumHostStartLagMs -gt $allowedStartLagMs) {
        throw "Visual probe host action cadence drifted by $maximumHostStartLagMs ms; allowed maximum is $allowedStartLagMs ms."
    }
    if ($maximumDeviceCommandStartDriftMs -gt $allowedStartLagMs) {
        throw "Visual probe device command cadence drifted by $maximumDeviceCommandStartDriftMs ms; allowed maximum is $allowedStartLagMs ms."
    }
    return [pscustomobject]@{
        Matched = $true
        AllowedStartLagMs = $allowedStartLagMs
        MaximumHostStartLagMs = $maximumHostStartLagMs
        MaximumDeviceCommandStartDriftMs = $maximumDeviceCommandStartDriftMs
        OverlappingCommandCount = $overlappingCommandCount
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
            -show_entries 'stream=duration,nb_read_frames,width,height:format=duration' `
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
    $width = 0
    $height = 0
    if (-not [int]::TryParse("$($stream.width)", [ref]$width) -or
        -not [int]::TryParse("$($stream.height)", [ref]$height) -or
        $width -le 0 -or $height -le 0) {
        throw 'Recorded video has invalid frame dimensions.'
    }
    return [pscustomobject]@{
        DurationSeconds = $duration
        FrameCount = $frameCount
        Width = $width
        Height = $height
    }
}

function Start-ReaderProbeAction {
    param(
        [object] $Action,
        [Diagnostics.Stopwatch] $Stopwatch
    )

    if ($Action.Kind -ne 'swipe') {
        throw "Unsupported visual probe action: $($Action.Kind)"
    }
    $token = [guid]::NewGuid().ToString('N')
    $remoteCommand =
        "log -p i -t NavicReaderVisualQa action-start:$token; " +
        "input touchscreen swipe $($Action.StartX) $($Action.StartY) " +
        "$($Action.EndX) $($Action.EndY) $($Action.DurationMs); " +
        "result=`$?; log -p i -t NavicReaderVisualQa action-finish:$token; " +
        'exit $result'
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'adb'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    foreach ($argument in @(
        '-s', $DeviceSerial,
        'shell', $remoteCommand
    )) {
        [void]$startInfo.ArgumentList.Add("$argument")
    }
    $process = [Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Gesture $($Action.Name) process did not start."
    }
    $event = [pscustomobject]@{
        Name = $Action.Name
        Kind = $Action.Kind
        ScheduledAtMs = $Action.AtMs
        StartedAtMs = $Stopwatch.ElapsedMilliseconds
        FinishedAtMs = $null
    }
    return [pscustomobject]@{
        Process = $process
        Event = $event
        MarkerToken = $token
    }
}

function Complete-ReaderProbeActions {
    param([object[]] $PendingActions)

    $deadline = [DateTime]::UtcNow.AddMilliseconds(500)
    foreach ($pending in @($PendingActions)) {
        $process = $pending.Process
        if (-not $process.HasExited) {
            $remainingMs = [Math]::Max(
                0,
                [int][Math]::Ceiling(
                    ($deadline - [DateTime]::UtcNow).TotalMilliseconds
                )
            )
            if ($remainingMs -gt 0) {
                [void]$process.WaitForExit($remainingMs)
            }
        }
        if (-not $process.HasExited) {
            $process.Kill($true)
            throw "Gesture $($pending.Event.Name) did not complete within the bounded capture cadence."
        }
        if ($process.ExitCode -ne 0) {
            throw "Gesture $($pending.Event.Name) failed (exit=$($process.ExitCode))."
        }
        $durationMs = [Math]::Max(
            0,
            [long][Math]::Round(
                ($process.ExitTime - $process.StartTime).TotalMilliseconds
            )
        )
        $pending.Event.FinishedAtMs =
            [long]$pending.Event.StartedAtMs + $durationMs
    }
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
Assert-DeterministicEmulatorViewport $display
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
$emulatorSourcePath = Join-Path $outputDirectory "$stem.framebuffer.webm"
$ownedPaths = @($videoPath, $manifestPath, $analysisPath)
if ($plan.CaptureBackend -eq 'emulator-framebuffer') {
    $ownedPaths += $emulatorSourcePath
}
foreach ($ownedPath in $ownedPaths) {
    if (Test-Path -LiteralPath $ownedPath) {
        throw "Visual QA refuses to overwrite an existing artifact: $ownedPath"
    }
}
Initialize-ReaderDevComposition `
    -Plan $plan `
    -OutputDirectory $outputDirectory
$probeStartedAtMonotonicSeconds = Write-ReaderVisualLogMarker
$remotePath = "/sdcard/navic-reader-visual-$Orientation-$Scenario.mp4"
if ($plan.CaptureBackend -eq 'android-screenrecord') {
    Remove-RemoteReaderArtifact $remotePath 'Remote recording'
}
$recording = $null
$recordingStopped = $false
$emulatorRecordingStarted = $false
$events = @()
$videoMetadata = $null
$captureElapsedMs = $null
$gestureSemantics = $null
$actionCadence = $null
$pendingActions = @()
try {
    if ($plan.CaptureBackend -eq 'emulator-framebuffer') {
        Start-EmulatorFrameBufferRecording `
            -Path $emulatorSourcePath `
            -LimitSeconds $TimeLimitSeconds
        $emulatorRecordingStarted = $true
        Start-Sleep -Milliseconds 500
    } else {
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
    }
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    foreach ($action in $plan.Actions) {
        Wait-UntilElapsed $stopwatch $action.AtMs
        $pending = Start-ReaderProbeAction `
            -Action $action `
            -Stopwatch $stopwatch
        $pendingActions += $pending
        $events += $pending.Event
    }
    Wait-UntilElapsed $stopwatch $plan.DurationMs
    Complete-ReaderProbeActions -PendingActions $pendingActions
    $captureElapsedMs = $stopwatch.ElapsedMilliseconds
    if ($plan.CaptureBackend -eq 'emulator-framebuffer') {
        Stop-EmulatorFrameBufferRecording
        $emulatorRecordingStarted = $false
        Convert-EmulatorFrameBufferRecording `
            -SourcePath $emulatorSourcePath `
            -TargetPath $videoPath `
            -DisplayWidth $plan.DisplayWidth `
            -DisplayHeight $plan.DisplayHeight
    } else {
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
    }
    $videoMetadata = Get-VideoMetadata $videoPath
    if ($Scenario -ne 'idle' -and $videoMetadata.FrameCount -lt 2) {
        throw 'Visual QA gesture recording contains no visible frame transition.'
    }
} finally {
    try {
        foreach ($pending in @($pendingActions)) {
            $process = $pending.Process
            try {
                if (-not $process.HasExited) {
                    try {
                        $process.Kill($true)
                    } catch [InvalidOperationException] {
                        if (-not $process.HasExited) { throw }
                    }
                    if (-not $process.HasExited -and
                        -not $process.WaitForExit(1000)) {
                        throw "Gesture $($pending.Event.Name) process cleanup did not terminate."
                    }
                }
            } finally {
                $process.Dispose()
            }
        }
    } finally {
        if ($plan.CaptureBackend -eq 'emulator-framebuffer') {
            if ($emulatorRecordingStarted) {
                Stop-EmulatorFrameBufferRecording
                $emulatorRecordingStarted = $false
            }
            Remove-LocalReaderArtifact $emulatorSourcePath 'Emulator source recording'
        } else {
            if ($null -ne $recording -and -not $recording.HasExited) {
                if (-not $recordingStopped) {
                    & adb -s $DeviceSerial shell pkill -2 screenrecord 2>$null | Out-Null
                }
                if (-not $recording.WaitForExit(5000)) { $recording.Kill() }
            }
            Remove-RemoteReaderArtifact $remotePath 'Remote recording'
        }
    }
}
$deviceActionTimings = @(Get-ReaderActionDeviceTimings $pendingActions)
$actionCadence = Assert-ReaderActionCadence `
    -Plan $plan `
    -Events $events `
    -DeviceTimings $deviceActionTimings
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
    CaptureBackend = $plan.CaptureBackend
    DisplayWidth = $display.Width
    DisplayHeight = $display.Height
    RecordingWidth = $videoMetadata.Width
    RecordingHeight = $videoMetadata.Height
    CompositionPreflight = [pscustomobject]@{
        Method = if ($plan.CaptureBackend -eq 'emulator-framebuffer') {
            'discarded-emulator-framebuffer-recording'
        } else {
            'discarded-screenrecord'
        }
        DurationSeconds = 1
        HostArtifactPersisted = $false
        RemoteArtifactRetained = $false
        ReaderInputInjected = $false
    }
    ActionCadence = $actionCadence
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
