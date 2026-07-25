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
        'exec-out screencap -p',
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
    if ($source.Contains('minimumDurationSeconds')) {
        throw 'Visual recorder treats variable-frame-rate duration as wall-clock coverage'
    }
    if ($source.Contains('SurfacePrime') -or $source.Contains("'surface-prime'")) {
        throw 'Visual recorder mutates reader position while priming capture'
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
