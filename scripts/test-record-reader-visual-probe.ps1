$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$recorder = Join-Path $PSScriptRoot 'record-reader-visual-probe.ps1'
$analyzer = Join-Path $PSScriptRoot 'reader-visual-qa.py'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Assert-FrameBufferNormalization {
    param(
        [object] $Actual,
        [hashtable] $Expected,
        [string] $FailureMessage
    )

    foreach ($propertyName in $Expected.Keys) {
        if ($Actual.$propertyName -ne $Expected[$propertyName]) {
            throw $FailureMessage
        }
    }
}

$testRoot = Join-Path $repositoryRoot '.codex-validation/reader-visual-plan-tests'
if (Test-Path -LiteralPath $testRoot) {
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    $expectedActions = @{
        'slow-next' = 1
        'snap-back' = 1
        'previous' = 1
        'rapid-turns' = 4
        'idle' = 0
    }
    foreach ($scenario in $expectedActions.Keys) {
        $output = @(& $recorder `
            -DeviceSerial 'plan-only-device' `
            -Scenario $scenario `
            -Orientation landscape `
            -ReaderDirection ltr `
            -OutputRoot $testRoot `
            -DisplayWidth 2400 `
            -DisplayHeight 1080 `
            -PlanOnly)
        if ($output.Count -ne 1 -or
            -not (Test-Path -LiteralPath $output[0] -PathType Leaf)) {
            throw "Plan-only recorder did not produce one plan for $scenario"
        }
        $plan = Get-Content -LiteralPath $output[0] -Raw | ConvertFrom-Json
        if ($plan.Package -ne 'darkaxt.navic.readerdev' -or
            $plan.Scenario -ne $scenario -or
            $plan.Orientation -ne 'landscape' -or
            $plan.CaptureBackend -ne 'android-screenrecord' -or
            $plan.DisplayWidth -ne 2400 -or
            $plan.DisplayHeight -ne 1080 -or
            $plan.RecordingWidth -ne 1280 -or
            $plan.RecordingHeight -ne 576 -or
            @($plan.Actions).Count -ne $expectedActions[$scenario]) {
            throw "Plan-only recorder emitted the wrong contract for $scenario"
        }
        if ($scenario -eq 'rapid-turns') {
            $rapidActions = @($plan.Actions)
            $rapidIntervals = @(1..($rapidActions.Count - 1) | ForEach-Object {
                $rapidActions[$_].AtMs - $rapidActions[$_ - 1].AtMs
            })
            if (@($rapidIntervals | Where-Object { $_ -ne 1000 }).Count -gt 0 -or
                $plan.DurationMs -ne 8860) {
                throw 'Rapid-turn probe does not preserve the bounded injected-attempt cadence'
            }
        }
        if ($scenario -ne 'idle') {
            $lastActionEnd = @($plan.Actions | ForEach-Object {
                $_.AtMs + $_.DurationMs
            } | Measure-Object -Maximum).Maximum
            if ($plan.DurationMs - $lastActionEnd -lt 4000) {
                throw 'Gesture probe does not reserve a full settlement and handoff tail'
            }
        }
        if ($scenario -eq 'snap-back') {
            $snapAction = @($plan.Actions)[0]
            $averageVelocity = [Math]::Abs(
                $snapAction.EndX - $snapAction.StartX
            ) / ($snapAction.DurationMs / 1000.0)
            if ($averageVelocity -ge 150.0 -or
                [Math]::Abs($snapAction.EndX - $snapAction.StartX) -gt
                    $plan.DisplayWidth * 0.1) {
                throw 'Snap-back probe can cross the renderer fling or release threshold'
            }
        }
        if ($plan.DurationMs + 10000 -ge 30000) {
            throw "Plan-only recorder does not reserve startup time for $scenario"
        }
    }

    $shortLimitRejected = $false
    try {
        & $recorder `
            -DeviceSerial 'plan-only-device' `
            -Scenario idle `
            -Orientation portrait `
            -OutputRoot (Join-Path $testRoot 'short-limit') `
            -DisplayWidth 1080 `
            -DisplayHeight 2400 `
            -TimeLimitSeconds 10 `
            -PlanOnly | Out-Null
    } catch {
        $shortLimitRejected = $true
    }
    if (-not $shortLimitRejected) {
        throw 'Recorder accepted a time limit that cannot cover startup and the probe'
    }

    $rtlRoot = Join-Path $testRoot 'rtl'
    $rtlPath = @(& $recorder `
        -DeviceSerial 'plan-only-device' `
        -Scenario slow-next `
        -Orientation portrait `
        -ReaderDirection rtl `
        -OutputRoot $rtlRoot `
        -DisplayWidth 1080 `
        -DisplayHeight 2400 `
        -PlanOnly)[0]
    $rtl = Get-Content -LiteralPath $rtlPath -Raw | ConvertFrom-Json
    if ($rtl.RecordingWidth -ne 576 -or $rtl.RecordingHeight -ne 1280) {
        throw 'Portrait recording size is not codec-safe and aspect-preserving'
    }
    if ($rtl.Actions[0].EndX -le $rtl.Actions[0].StartX) {
        throw 'RTL next probe did not reverse the physical swipe direction'
    }

    $nonExactPath = @(& $recorder `
        -DeviceSerial 'plan-only-device' `
        -Scenario idle `
        -Orientation portrait `
        -OutputRoot (Join-Path $testRoot 'non-exact-aspect') `
        -DisplayWidth 1080 `
        -DisplayHeight 2520 `
        -PlanOnly)[0]
    $nonExact = Get-Content -LiteralPath $nonExactPath -Raw | ConvertFrom-Json
    $displayRatio = $nonExact.DisplayWidth / $nonExact.DisplayHeight
    $recordingRatio = $nonExact.RecordingWidth / $nonExact.RecordingHeight
    if ([Math]::Abs($displayRatio - $recordingRatio) -gt 0.001) {
        throw 'Codec-safe recording size changes the active display aspect ratio'
    }

    $source = Get-Content -LiteralPath $recorder -Raw
    foreach ($required in @(
        'darkaxt.navic.readerdev',
        'screenrecord',
        "'--size'",
        'Wait-RecordingReady',
        'Get-VideoMetadata',
        'VideoDurationSeconds',
        'VideoFrameCount',
        'StaticIdleCapture',
        'CaptureElapsedMs',
        'CompositionPreflight',
        'Initialize-ReaderDevComposition',
        'emulator-framebuffer',
        'Get-ReaderVisualCaptureBackend',
        'Assert-EmulatorConsoleResponse',
        'Invoke-EmulatorConsoleText',
        'ConvertFrom-AndroidPhysicalDisplaySize',
        'Get-EmulatorFrameBufferNormalization',
        'Assert-DeterministicEmulatorViewport',
        'accelerometer_rotation=0 and user_rotation=0',
        'Start-EmulatorFrameBufferRecording',
        'Stop-EmulatorFrameBufferRecording',
        'Convert-EmulatorFrameBufferRecording',
        'Assert-ReaderActionCadence',
        'Start-ReaderProbeAction',
        'Complete-ReaderProbeActions',
        'Get-ReaderActionDeviceTimings',
        'action-start:',
        'action-finish:',
        'DisplayWidth and DisplayHeight may only be supplied with PlanOnly',
        'ActionCadence',
        'Remove-LocalReaderArtifact',
        'discarded-screenrecord',
        'HostArtifactPersisted',
        'ReaderInputInjected',
        'Get-ReaderGestureTerminals',
        'ConvertFrom-ReaderGestureTerminalLog',
        'Get-ReaderVisualMarkerTimestamp',
        'Write-ReaderVisualLogMarker',
        'NavicReaderVisualQa',
        'Assert-ReaderGestureTerminalSet',
        'Assert-ReaderGestureSemantics',
        'GestureSemantics',
        'Remove-RemoteReaderArtifact',
        "'test', '!', '-e'",
        "'-v', 'monotonic'",
        'CancelledByUser',
        'CommittedForward',
        'CommittedBackward',
        'Wait-ReaderDevVisualReady',
        'Page preparation state phase=',
        'reader-repair ',
        'KomikkuReaderNativeFrameHost:I',
        '--expected-duration-seconds',
        'pkill -2 screenrecord',
        'reader-visual-qa.py',
        '.events.json',
        'refuses to overwrite'
    )) {
        if (-not $source.Contains($required)) {
            throw "Visual recorder omits required contract: $required"
        }
    }
    $parserContract = @(& {
        . $recorder `
            -DeviceSerial 'plan-only-device' `
            -Scenario snap-back `
            -Orientation landscape `
            -OutputRoot (Join-Path $testRoot 'terminal-parser') `
            -DisplayWidth 2400 `
            -DisplayHeight 1080 `
            -PlanOnly | Out-Null
        $markerLog = @'
--------- beginning of main
   100.000 2 2 I NavicReaderVisualQa: probe-start:synthetic-marker
'@
        $markerTimestamp = Get-ReaderVisualMarkerTimestamp `
            -Log $markerLog `
            -Token 'synthetic-marker'
        if ($markerTimestamp -ne 100.0) {
            throw 'Visual probe marker did not use the logcat monotonic timestamp'
        }
        $syntheticLog = @'
--------- beginning of main
    99.000 1 1 I KomikkuReaderNativeFrameHost: Reader gesture terminal gestureId=900 outcome=CommittedForward won=true detail=SettlementCompleted(pageChange=NEXT, ordinal=1)
   101.000 1 1 W KomikkuReaderNativeFrameHost: Reader gesture terminal replay Reader gesture terminal gestureId=1 outcome=CancelledByUser won=false detail=SettlementCompleted(pageChange=NONE, ordinal=1)
   102.000 1 1 I KomikkuReaderNativeFrameHost: Reader gesture terminal gestureId=1 outcome=CancelledByUser won=true detail=SettlementCompleted(pageChange=NONE, ordinal=1)
'@
        $parsed = @(ConvertFrom-ReaderGestureTerminalLog `
            -Log $syntheticLog `
            -AfterMonotonicSeconds $markerTimestamp)
        if ($parsed.Count -ne 2 -or
            @($parsed | Where-Object { $_.Replay }).Count -ne 1 -or
            @($parsed | Where-Object { $_.Won }).Count -ne 1) {
            throw 'Gesture-terminal parser does not isolate the current monotonic window'
        }
        $replayRejected = $false
        try {
            Assert-ReaderGestureTerminalSet `
                -Terminals $parsed `
                -ScenarioName snap-back `
                -InjectedActionCount 1 `
                -MinimumExpectedTerminalCount 1 `
                -MaximumExpectedTerminalCount 1 | Out-Null
        } catch {
            $replayRejected = $true
        }
        if (-not $replayRejected) {
            throw 'Gesture-terminal semantics accepted a replay record'
        }
        $winner = @($parsed | Where-Object { $_.Won -and -not $_.Replay })
        $evidence = Assert-ReaderGestureTerminalSet `
            -Terminals $winner `
            -ScenarioName snap-back `
            -InjectedActionCount 1 `
            -MinimumExpectedTerminalCount 1 `
            -MaximumExpectedTerminalCount 1
        if (-not $evidence.Matched -or $evidence.ObservedTerminalCount -ne 1 -or
            $evidence.MinimumExpectedTerminalCount -ne 1 -or
            $evidence.MaximumExpectedTerminalCount -ne 1) {
            throw 'Gesture-terminal semantics rejected the unique winning result'
        }
        $rapidWinners = @(1..2 | ForEach-Object {
            [pscustomobject]@{
                Timestamp = 103.0 + $_
                GestureId = [long]$_
                Outcome = 'CommittedForward'
                Won = $true
                Replay = $false
                PageChange = 'NEXT'
            }
        })
        $rapidWithBusyRejection = @($rapidWinners) + @(
            [pscustomobject]@{
                Timestamp = 106.0
                GestureId = 3L
                Outcome = 'RejectedPreparing'
                Won = $true
                Replay = $false
                PageChange = $null
            }
        )
        $rapidEvidence = Assert-ReaderGestureTerminalSet `
            -Terminals $rapidWithBusyRejection `
            -ScenarioName rapid-turns `
            -InjectedActionCount 4 `
            -MinimumExpectedTerminalCount 2 `
            -MaximumExpectedTerminalCount 4
        if (-not $rapidEvidence.Matched -or
            $rapidEvidence.ObservedTerminalCount -ne 2 -or
            $rapidEvidence.BusyRejectionCount -ne 1) {
            throw 'Rapid-turn semantics rejected two valid commits plus a permitted busy admission rejection'
        }
        $underfilledRapidRejected = $false
        try {
            Assert-ReaderGestureTerminalSet `
                -Terminals @($rapidWinners[0]) `
                -ScenarioName rapid-turns `
                -InjectedActionCount 4 `
                -MinimumExpectedTerminalCount 2 `
                -MaximumExpectedTerminalCount 4 | Out-Null
        } catch {
            $underfilledRapidRejected = $true
        }
        if (-not $underfilledRapidRejected) {
            throw 'Rapid-turn semantics accepted fewer than two consecutive commits'
        }
        $unexpectedRapidTerminalRejected = $false
        try {
            $unexpectedRapidTerminals = @($rapidWinners) + @(
                [pscustomobject]@{
                    Timestamp = 106.0
                    GestureId = 3L
                    Outcome = 'CancelledByUser'
                    Won = $true
                    Replay = $false
                    PageChange = 'NONE'
                }
            )
            Assert-ReaderGestureTerminalSet `
                -Terminals $unexpectedRapidTerminals `
                -ScenarioName rapid-turns `
                -InjectedActionCount 4 `
                -MinimumExpectedTerminalCount 2 `
                -MaximumExpectedTerminalCount 4 | Out-Null
        } catch {
            $unexpectedRapidTerminalRejected = $true
        }
        if (-not $unexpectedRapidTerminalRejected) {
            throw 'Rapid-turn semantics accepted a non-busy unexpected terminal'
        }
        if ((Get-ReaderVisualCaptureBackend 'emulator-5554') -ne
            'emulator-framebuffer' -or
            (Get-ReaderVisualCaptureBackend 'physical-device') -ne
            'android-screenrecord') {
            throw 'Visual recorder selected the wrong capture backend'
        }
        Assert-EmulatorConsoleResponse `
            -Response "recording started`nOK" `
            -Description 'synthetic console success'
        $consoleRejectionObserved = $false
        try {
            Assert-EmulatorConsoleResponse `
                -Response 'KO: recording already in progress' `
                -Description 'synthetic console rejection'
        } catch {
            $consoleRejectionObserved = $true
        }
        if (-not $consoleRejectionObserved) {
            throw 'Visual recorder accepted an emulator console KO response'
        }
        $frameBufferSize = ConvertFrom-AndroidPhysicalDisplaySize `
            -SizeText "Physical size: 1080x2400`nOverride size: 1848x2960" `
            -OrientationName landscape
        if ($frameBufferSize.Width -ne 2400 -or
            $frameBufferSize.Height -ne 1080) {
            throw 'Emulator framebuffer dimensions did not use the physical display'
        }
        $landscapeNormalization = Get-EmulatorFrameBufferNormalization `
            -FrameBufferWidth 2400 `
            -FrameBufferHeight 1080 `
            -PhysicalDisplayWidth 1080 `
            -PhysicalDisplayHeight 2400 `
            -DisplayWidth 2960 `
            -DisplayHeight 1848
        Assert-FrameBufferNormalization `
            -Actual $landscapeNormalization `
            -FailureMessage 'Landscape framebuffer normalization does not recover the upright guest viewport' `
            -Expected @{
                CropX = 862
                CropY = 0
                CropWidth = 674
                CropHeight = 1080
                OutputWidth = 1080
                OutputHeight = 674
                Filter = 'crop=674:1080:862:0,transpose=clock'
            }
        $portraitNormalization = Get-EmulatorFrameBufferNormalization `
            -FrameBufferWidth 2400 `
            -FrameBufferHeight 1080 `
            -PhysicalDisplayWidth 1080 `
            -PhysicalDisplayHeight 2400 `
            -DisplayWidth 1848 `
            -DisplayHeight 2960
        Assert-FrameBufferNormalization `
            -Actual $portraitNormalization `
            -FailureMessage 'Portrait framebuffer normalization does not recover the upright guest viewport' `
            -Expected @{
                CropX = 334
                CropY = 0
                CropWidth = 1730
                CropHeight = 1080
                OutputWidth = 1080
                OutputHeight = 1730
            }
        $uprightLandscapeNormalization = Get-EmulatorFrameBufferNormalization `
            -FrameBufferWidth 2400 `
            -FrameBufferHeight 1080 `
            -PhysicalDisplayWidth 2400 `
            -PhysicalDisplayHeight 1080 `
            -DisplayWidth 2960 `
            -DisplayHeight 1848
        Assert-FrameBufferNormalization `
            -Actual $uprightLandscapeNormalization `
            -FailureMessage 'Upright landscape framebuffer normalization rotates valid content' `
            -Expected @{
                CropX = 334
                CropY = 0
                CropWidth = 1730
                CropHeight = 1080
                OutputWidth = 1730
                OutputHeight = 1080
                Filter = 'crop=1730:1080:334:0'
            }
        $uprightPortraitNormalization = Get-EmulatorFrameBufferNormalization `
            -FrameBufferWidth 1080 `
            -FrameBufferHeight 2400 `
            -PhysicalDisplayWidth 1080 `
            -PhysicalDisplayHeight 2400 `
            -DisplayWidth 1848 `
            -DisplayHeight 2960
        Assert-FrameBufferNormalization `
            -Actual $uprightPortraitNormalization `
            -FailureMessage 'Upright portrait framebuffer normalization rotates valid content' `
            -Expected @{
                CropX = 0
                CropY = 334
                CropWidth = 1080
                CropHeight = 1730
                OutputWidth = 1080
                OutputHeight = 1730
                Filter = 'crop=1080:1730:0:334'
            }
        $cadencePlan = [pscustomobject]@{
            Actions = @(
                [pscustomobject]@{ Name = 'one'; AtMs = 1700 },
                [pscustomobject]@{ Name = 'two'; AtMs = 2400 },
                [pscustomobject]@{ Name = 'three'; AtMs = 3100 },
                [pscustomobject]@{ Name = 'four'; AtMs = 3800 }
            )
        }
        $boundedEvents = @(
            [pscustomobject]@{ Name = 'one'; ScheduledAtMs = 1700; StartedAtMs = 1710 },
            [pscustomobject]@{ Name = 'two'; ScheduledAtMs = 2400; StartedAtMs = 2450 },
            [pscustomobject]@{ Name = 'three'; ScheduledAtMs = 3100; StartedAtMs = 3399 },
            [pscustomobject]@{ Name = 'four'; ScheduledAtMs = 3800; StartedAtMs = 3800 }
        )
        $boundedDeviceTimings = @(
            [pscustomobject]@{ StartSeconds = 100.0; FinishSeconds = 100.2 },
            [pscustomobject]@{ StartSeconds = 100.7; FinishSeconds = 100.9 },
            [pscustomobject]@{ StartSeconds = 101.4; FinishSeconds = 101.6 },
            [pscustomobject]@{ StartSeconds = 102.1; FinishSeconds = 102.3 }
        )
        $cadence = Assert-ReaderActionCadence `
            -Plan $cadencePlan `
            -Events $boundedEvents `
            -DeviceTimings $boundedDeviceTimings
        if (-not $cadence.Matched -or
            $cadence.MaximumHostStartLagMs -ne 299 -or
            $cadence.MaximumDeviceCommandStartDriftMs -ne 0 -or
            $cadence.OverlappingCommandCount -ne 0 -or
            $cadence.AllowedStartLagMs -ne 350) {
            throw 'Visual recorder rejected a bounded rapid-turn cadence'
        }
        $driftRejected = $false
        try {
            $driftedDeviceTimings = @($boundedDeviceTimings)
            $driftedDeviceTimings[2] = [pscustomobject]@{
                StartSeconds = 101.751
                FinishSeconds = 101.9
            }
            Assert-ReaderActionCadence `
                -Plan $cadencePlan `
                -Events $boundedEvents `
                -DeviceTimings $driftedDeviceTimings | Out-Null
        } catch {
            $driftRejected = $true
        }
        if (-not $driftRejected) {
            throw 'Visual recorder accepted excessive device-observed action schedule drift'
        }
        $overlappingDeviceTimings = @($boundedDeviceTimings)
        $overlappingDeviceTimings[0] = [pscustomobject]@{
            StartSeconds = 100.0
            FinishSeconds = 100.8
        }
        $overlapEvidence = Assert-ReaderActionCadence `
            -Plan $cadencePlan `
            -Events $boundedEvents `
            -DeviceTimings $overlappingDeviceTimings
        if (-not $overlapEvidence.Matched -or
            $overlapEvidence.OverlappingCommandCount -ne 1) {
            throw 'Visual recorder did not disclose overlapping device input commands'
        }
        Write-Output 'terminal-parser-pass'
    })
    if ($parserContract.Count -ne 1 -or
        $parserContract[0] -ne 'terminal-parser-pass') {
        throw 'Gesture-terminal parser contract did not execute completely'
    }
    if ($source.Contains('Invoke-ProbeAction')) {
        throw 'Visual recorder serializes adb input commands and can drift off the planned cadence'
    }
    if ($source.Contains('minimumDurationSeconds')) {
        throw 'Visual recorder treats variable-frame-rate duration as wall-clock coverage'
    }
    if ($source.Contains('SurfacePrime') -or $source.Contains("'surface-prime'")) {
        throw 'Visual recorder mutates reader position while priming capture'
    }
    if ($source.Contains('exec-out screencap -p')) {
        throw 'Visual recorder uses a still capture that cannot warm video composition'
    }
    if ($source.Contains('/proc/uptime')) {
        throw 'Visual recorder compares different Android monotonic clock domains'
    }
    if (-not (Test-Path -LiteralPath $analyzer -PathType Leaf)) {
        throw 'Visual recorder analyzer is missing'
    }
    $ignore = Get-Content -LiteralPath (Join-Path $repositoryRoot '.gitignore') -Raw
    if (-not $ignore.Contains('.codex-validation/')) {
        throw 'Visual evidence root is not excluded from Git'
    }
    if (@(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter '*.mp4').Count -ne 0) {
        throw 'Plan-only visual recorder created a video artifact'
    }
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}

Write-Output 'Reader visual probe planner PASS'
