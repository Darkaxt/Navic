# Reader visual QA bench

Use this local-only bench to inspect PlayLikeCurl behavior that state and log assertions cannot prove. It records the mandatory `darkaxt.navic.readerdev` package, keeps the MP4 and reports under `.codex-validation/`, and analyzes frames in memory without OCR or frame extraction.

## Prerequisites

- ReaderDev is installed, running, and displaying the test publication.
- `adb`, `python`, `ffmpeg`, and `ffprobe` are on `PATH`.
- Python provides Pillow and NumPy.
- The emulator or device is already in the orientation under test. For an emulator, apply `scripts/set-reader-dev-viewport.ps1`; simulated profiles disable sensor rotation and retain `user_rotation=0`, and the recorder rejects a non-deterministic or mismatched viewport.

Do not commit recordings, extracted frames, or analysis output. `.codex-validation/` is Git-ignored. Do not attach these local artifacts to a release.

## Record one probe

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\record-reader-visual-probe.ps1 `
  -DeviceSerial emulator-5554 `
  -Scenario slow-next `
  -Orientation portrait `
  -OutputRoot .codex-validation\reader-visual\portrait
```

Before each capture, the recorder waits for ReaderDev's current page-preparation state to allow gestures. On physical devices it runs a one-second discarded Android `screenrecord` preflight at the target capture size. On Android emulators it uses the emulator console's framebuffer recorder for both the discarded preflight and the retained MP4; this captures the display users actually see across rapid `SurfaceView`/WebView handoffs instead of the guest virtual display's transient missing-layer frames. The emulator conversion compares the recorded framebuffer to the physical display, crops the centered aspect-preserving guest viewport, and rotates only a physically rotated recording before analysis, so host-panel orientation and letterboxing cannot become false black-region evidence. Neither preflight touches or advances the publication. Its temporary artifact is deleted, and the manifest records that no reader input was injected or preflight artifact retained.

The physical-device backend chooses an aspect-preserving, codec-safe capture size with a maximum edge of 1280 pixels, waits for Android's recording container to initialize, and keeps the `screenrecord` process alive through the complete probe. The emulator backend records a temporary host-local WebM from the emulator framebuffer, converts its video stream to the retained MP4, then verifies deletion of the temporary WebM. Both backends record measured wall-clock time, decoded frame count, actual frame dimensions, and their backend identity. Android guest recording uses variable-frame-rate capture and can emit a single-frame MP4 when the display remains completely unchanged during `idle`; the analyzer extends only the final decoded frame in memory to represent the measured idle interval. Any emitted visual changes remain in sequence and are still evaluated.

For gesture scenarios, the recorder also verifies the new ReaderDev terminal outcomes emitted during that probe. It fails when either the host launch or the marker emitted immediately before the device `input` command drifts more than 350 ms from the planned cadence. The manifest discloses overlapping device-command lifetimes without equating guest command return latency with overlapping pointer delivery. A committed Next or Previous probe must relocate in the requested logical direction, while `snap-back` must emit `CancelledByUser` with no page change. `rapid-turns` injects four fast attempts and requires at least two distinct committed-forward terminals; additional attempts may be consumed while settlement or working-set refill intentionally closes new-pointer admission. Only aggregate outcome and cadence fields are retained in the local manifest.

Supported scenarios:

- `slow-next` — a 1.5-second committed Next drag for deformation, travel, and threshold inspection.
- `snap-back` — a velocity-capped drag that must settle back without relocation.
- `previous` — a committed Previous drag.
- `rapid-turns` — four bounded fast Next attempts; at least two must commit consecutively while busy-state consumption remains permitted.
- `idle` — nine seconds without injected input; detects unsolicited page cycling or blinking.

Run each scenario in portrait and landscape. Add `-ReaderDirection rtl` for an RTL publication. The recorder refuses to overwrite existing artifacts.

For a deterministic plan without ADB or a recording:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\record-reader-visual-probe.ps1 `
  -DeviceSerial plan-only `
  -Scenario rapid-turns `
  -Orientation landscape `
  -DisplayWidth 2400 `
  -DisplayHeight 1080 `
  -OutputRoot .codex-validation\reader-visual\plan `
  -PlanOnly
```

## Analyze an existing MP4

```powershell
python .\scripts\reader-visual-qa.py `
  --video .codex-validation\reader-visual\portrait\portrait-idle-DEVICE.mp4 `
  --scenario idle `
  --orientation portrait
```

The JSON report contains aggregate pixel metrics only:

- `blackRegions` — black-area growth relative to the stable opening surface.
- `leafBounds` — minimum visible leaf coverage and bounding-area retention.
- `gutterDrift` — landscape gutter movement when a stable gutter is detectable.
- `decorationContinuity` — change in narrow, non-content outer decoration bands. Idle probes evaluate the complete timeline; gesture probes evaluate the final settled window so the moving curl edge itself cannot masquerade as lost decoration.
- `idleStability` — large consecutive-frame changes during an idle probe.

A skipped gutter check means the recording had insufficient non-content contrast for a privacy-safe gutter estimate. Use `-RequireAllVisualChecks` on the recorder, or `--require-all` on the analyzer, when a release gate requires every applicable metric to be measurable.

Automated metrics are triage gates, not a replacement for direct MP4 review. Before release, inspect slow deformation, the commit/snap-back threshold, fold travel, stable page scale, preserved paper decorations, gutter continuity, and the absence of edge clipping.

## Self-tests

```powershell
python .\scripts\test-reader-visual-qa.py
pwsh -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\test-record-reader-visual-probe.ps1
```
