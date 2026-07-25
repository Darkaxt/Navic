$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$recorder = Join-Path $PSScriptRoot 'record-reader-visual-probe.ps1'
$analyzer = Join-Path $PSScriptRoot 'reader-visual-qa.py'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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
            $plan.DisplayWidth -ne 2400 -or
            $plan.DisplayHeight -ne 1080 -or
            $plan.RecordingWidth -ne 1280 -or
            $plan.RecordingHeight -ne 576 -or
            @($plan.Actions).Count -ne $expectedActions[$scenario]) {
            throw "Plan-only recorder emitted the wrong contract for $scenario"
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
                -ExpectedTerminalCount 1 | Out-Null
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
            -ExpectedTerminalCount 1
        if (-not $evidence.Matched -or $evidence.ObservedTerminalCount -ne 1) {
            throw 'Gesture-terminal semantics rejected the unique winning result'
        }
        Write-Output 'terminal-parser-pass'
    })
    if ($parserContract.Count -ne 1 -or
        $parserContract[0] -ne 'terminal-parser-pass') {
        throw 'Gesture-terminal parser contract did not execute completely'
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
