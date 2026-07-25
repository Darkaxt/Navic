# Reader visual QA bench

Use this local-only bench to inspect PlayLikeCurl behavior that state and log assertions cannot prove. It records the mandatory `darkaxt.navic.readerdev` package, keeps the MP4 and reports under `.codex-validation/`, and analyzes frames in memory without OCR or frame extraction.

## Prerequisites

- ReaderDev is installed, running, and displaying the test publication.
- `adb`, `python`, `ffmpeg`, and `ffprobe` are on `PATH`.
- Python provides Pillow and NumPy.
- The emulator or device is already in the orientation under test.

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

Supported scenarios:

- `slow-next` — a 1.5-second committed Next drag for deformation, travel, and threshold inspection.
- `snap-back` — a short drag that must settle back without relocation.
- `previous` — a committed Previous drag.
- `rapid-turns` — four bounded fast Next gestures.
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
- `decorationContinuity` — change in narrow, non-content outer decoration bands.
- `idleStability` — large consecutive-frame changes during an idle probe.

A skipped gutter check means the recording had insufficient non-content contrast for a privacy-safe gutter estimate. Use `-RequireAllVisualChecks` on the recorder, or `--require-all` on the analyzer, when a release gate requires every applicable metric to be measurable.

Automated metrics are triage gates, not a replacement for direct MP4 review. Before release, inspect slow deformation, the commit/snap-back threshold, fold travel, stable page scale, preserved paper decorations, gutter continuity, and the absence of edge clipping.

## Self-tests

```powershell
python .\scripts\test-reader-visual-qa.py
pwsh -NoProfile -ExecutionPolicy Bypass -File `
  .\scripts\test-record-reader-visual-probe.ps1
```
