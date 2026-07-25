#!/usr/bin/env python3
"""Privacy-safe frame analysis for local ReaderDev curl recordings.

The analyzer decodes frames in memory and emits aggregate geometry/change metrics only.
It never writes frames, performs OCR, or includes rendered reader content in its report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

import numpy as np
from PIL import Image


@dataclass(frozen=True)
class Region:
    left: float
    top: float
    width: float
    height: float

    def slices(self, width: int, height: int) -> tuple[slice, slice]:
        left = max(0, min(width - 1, round(self.left * width)))
        top = max(0, min(height - 1, round(self.top * height)))
        right = max(left + 1, min(width, round((self.left + self.width) * width)))
        bottom = max(top + 1, min(height, round((self.top + self.height) * height)))
        return slice(top, bottom), slice(left, right)


@dataclass(frozen=True)
class Thresholds:
    black_luma: float = 16.0
    max_black_ratio: float = 0.45
    max_black_delta: float = 0.12
    min_leaf_coverage_ratio: float = 0.78
    min_leaf_bounds_area_ratio: float = 0.75
    max_gutter_drift_ratio: float = 0.04
    min_gutter_contrast: float = 0.015
    max_decoration_changed_fraction: float = 0.35
    max_decoration_mae: float = 12.0
    max_idle_changed_fraction: float = 0.12
    max_idle_mae: float = 4.0


def parse_region(value: str) -> Region:
    try:
        parts = [float(part.strip()) for part in value.split(",")]
    except ValueError as error:
        raise argparse.ArgumentTypeError("region values must be numeric") from error
    if len(parts) != 4:
        raise argparse.ArgumentTypeError("region must be left,top,width,height")
    region = Region(*parts)
    if (
        region.left < 0
        or region.top < 0
        or region.width <= 0
        or region.height <= 0
        or region.left + region.width > 1
        or region.top + region.height > 1
    ):
        raise argparse.ArgumentTypeError("region must fit normalized frame bounds")
    return region


def default_decoration_regions(orientation: str, roi: Region) -> list[Region]:
    if orientation == "landscape":
        relative = (
            (0.00, 0.00, 1.00, 0.04),
            (0.00, 0.96, 1.00, 0.04),
            (0.00, 0.05, 0.025, 0.90),
            (0.975, 0.05, 0.025, 0.90),
        )
    else:
        relative = (
            (0.00, 0.05, 0.04, 0.90),
            (0.96, 0.05, 0.04, 0.90),
        )
    return [
        Region(
            left=roi.left + left * roi.width,
            top=roi.top + top * roi.height,
            width=width * roi.width,
            height=height * roi.height,
        )
        for left, top, width, height in relative
    ]


def luma(frame: np.ndarray) -> np.ndarray:
    rgb = frame.astype(np.float32)
    return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722


def resized_luma(frame: np.ndarray, width: int = 96, height: int = 96) -> np.ndarray:
    image = Image.fromarray(frame, mode="RGB").resize((width, height), Image.Resampling.BILINEAR)
    return luma(np.asarray(image))


def median_reference(frames: Sequence[np.ndarray]) -> np.ndarray:
    if not frames:
        raise ValueError("at least one baseline frame is required")
    return np.median(np.stack(frames, axis=0), axis=0).astype(np.uint8)


def extend_idle_frames(
    frames: Sequence[np.ndarray],
    sample_fps: float,
    scenario: str,
    expected_duration_seconds: float | None,
) -> tuple[list[np.ndarray], bool]:
    copied = list(frames)
    if scenario != "idle" or expected_duration_seconds is None:
        return copied, False
    if expected_duration_seconds <= 0:
        raise ValueError("expected idle duration must be positive")
    if not copied:
        return copied, False
    target_count = max(6, math.ceil(sample_fps * expected_duration_seconds))
    if len(copied) >= target_count:
        return copied, False
    copied.extend([copied[-1]] * (target_count - len(copied)))
    return copied, True


def leaf_metrics(frame: np.ndarray, roi: Region, black_luma: float) -> dict[str, float]:
    frame_height, frame_width = frame.shape[:2]
    rows, columns = roi.slices(frame_width, frame_height)
    surface_luma = luma(frame[rows, columns])
    visible = surface_luma > black_luma
    coverage = float(np.mean(visible))
    if not np.any(visible):
        return {
            "blackRatio": 1.0,
            "coverage": 0.0,
            "boundsArea": 0.0,
            "boundsWidth": 0.0,
            "boundsHeight": 0.0,
        }
    ys, xs = np.nonzero(visible)
    width = (int(xs.max()) - int(xs.min()) + 1) / visible.shape[1]
    height = (int(ys.max()) - int(ys.min()) + 1) / visible.shape[0]
    return {
        "blackRatio": 1.0 - coverage,
        "coverage": coverage,
        "boundsArea": width * height,
        "boundsWidth": width,
        "boundsHeight": height,
    }


def gutter_metrics(frame: np.ndarray, roi: Region) -> tuple[float, float]:
    frame_height, frame_width = frame.shape[:2]
    rows, columns = roi.slices(frame_width, frame_height)
    profile = np.mean(luma(frame[rows, columns]), axis=0)
    smoothing_width = max(3, int(round(profile.size * 0.012)))
    if smoothing_width % 2 == 0:
        smoothing_width += 1
    kernel = np.ones(smoothing_width, dtype=np.float32) / smoothing_width
    smoothed = np.convolve(profile, kernel, mode="same")
    center_start = max(0, int(profile.size * 0.35))
    center_end = min(profile.size, int(math.ceil(profile.size * 0.65)))
    center = smoothed[center_start:center_end]
    gutter_local = int(np.argmin(center))
    gutter_x = (center_start + gutter_local) / max(1, profile.size - 1)
    contrast = float((np.median(center) - center[gutter_local]) / 255.0)
    return gutter_x, contrast


def decoration_metrics(
    frame: np.ndarray,
    reference: np.ndarray,
    regions: Sequence[Region],
) -> list[tuple[float, float]]:
    if not regions:
        return [(0.0, 0.0)]
    frame_height, frame_width = frame.shape[:2]
    samples: list[tuple[float, float]] = []
    for region in regions:
        rows, columns = region.slices(frame_width, frame_height)
        current = luma(frame[rows, columns])
        expected = luma(reference[rows, columns])
        difference = np.abs(current - expected)
        samples.append(
            (
                float(np.mean(difference > 18.0)),
                float(np.mean(difference)),
            )
        )
    return samples


def check_result(status: str, metrics: dict, reason: str | None = None) -> dict:
    result = {"status": status, "metrics": metrics}
    if reason:
        result["reason"] = reason
    return result


def analyze_frames(
    frames: Sequence[np.ndarray],
    sample_fps: float,
    orientation: str,
    scenario: str,
    roi: Region,
    decoration_regions: Sequence[Region],
    thresholds: Thresholds = Thresholds(),
) -> dict:
    if sample_fps <= 0:
        raise ValueError("sample_fps must be positive")
    if len(frames) < max(6, math.ceil(sample_fps)):
        raise ValueError("recording does not contain enough sampled frames")
    shape = frames[0].shape
    if any(frame.shape != shape or frame.ndim != 3 or frame.shape[2] != 3 for frame in frames):
        raise ValueError("all frames must have one consistent RGB shape")

    baseline_start = min(len(frames) - 1, max(0, math.floor(sample_fps * 0.25)))
    baseline_end = min(len(frames), max(baseline_start + 3, math.ceil(sample_fps * 1.0)))
    baseline_frames = list(frames[baseline_start:baseline_end])
    reference = median_reference(baseline_frames)
    baseline_leaf = [leaf_metrics(frame, roi, thresholds.black_luma) for frame in baseline_frames]
    baseline_black = statistics.median(metric["blackRatio"] for metric in baseline_leaf)
    baseline_coverage = max(1e-6, statistics.median(metric["coverage"] for metric in baseline_leaf))
    baseline_bounds = max(1e-6, statistics.median(metric["boundsArea"] for metric in baseline_leaf))

    evaluated = list(frames[baseline_start:])
    evaluated_leaf = [leaf_metrics(frame, roi, thresholds.black_luma) for frame in evaluated]
    black_ratios = [metric["blackRatio"] for metric in evaluated_leaf]
    coverage_ratios = [metric["coverage"] / baseline_coverage for metric in evaluated_leaf]
    bounds_ratios = [metric["boundsArea"] / baseline_bounds for metric in evaluated_leaf]
    peak_black = max(black_ratios)
    black_limit = baseline_black + thresholds.max_black_delta
    if baseline_black < 0.30:
        black_limit = min(thresholds.max_black_ratio, black_limit)
    checks: dict[str, dict] = {
        "blackRegions": check_result(
            "pass" if peak_black <= black_limit else "fail",
            {
                "baselineRatio": baseline_black,
                "peakRatio": peak_black,
                "allowedRatio": black_limit,
                "peakFrame": baseline_start + int(np.argmax(black_ratios)),
            },
        ),
        "leafBounds": check_result(
            "pass"
            if min(coverage_ratios) >= thresholds.min_leaf_coverage_ratio
            and min(bounds_ratios) >= thresholds.min_leaf_bounds_area_ratio
            else "fail",
            {
                "minimumCoverageRatio": min(coverage_ratios),
                "minimumBoundsAreaRatio": min(bounds_ratios),
                "coverageLimit": thresholds.min_leaf_coverage_ratio,
                "boundsAreaLimit": thresholds.min_leaf_bounds_area_ratio,
            },
        ),
    }

    decoration_samples = [
        sample
        for frame in evaluated
        for sample in decoration_metrics(frame, reference, decoration_regions)
    ]
    peak_changed = max(sample[0] for sample in decoration_samples)
    peak_decoration_mae = max(sample[1] for sample in decoration_samples)
    decorations_disappeared = any(
        changed_fraction > thresholds.max_decoration_changed_fraction
        and mean_error > thresholds.max_decoration_mae
        for changed_fraction, mean_error in decoration_samples
    )
    checks["decorationContinuity"] = check_result(
        "fail" if decorations_disappeared else "pass",
        {
            "peakChangedFraction": peak_changed,
            "peakMeanAbsoluteError": peak_decoration_mae,
            "changedFractionLimit": thresholds.max_decoration_changed_fraction,
            "meanAbsoluteErrorLimit": thresholds.max_decoration_mae,
            "regions": len(decoration_regions),
        },
    )

    if orientation == "landscape":
        gutter_samples = [gutter_metrics(frame, roi) for frame in baseline_frames]
        baseline_gutter = statistics.median(sample[0] for sample in gutter_samples)
        baseline_contrast = statistics.median(sample[1] for sample in gutter_samples)
        all_gutters = [gutter_metrics(frame, roi) for frame in evaluated]
        peak_drift = max(abs(sample[0] - baseline_gutter) for sample in all_gutters)
        if baseline_contrast < thresholds.min_gutter_contrast:
            checks["gutterDrift"] = check_result(
                "skipped",
                {
                    "baselineContrast": baseline_contrast,
                    "requiredContrast": thresholds.min_gutter_contrast,
                },
                "stable gutter could not be detected without inspecting content",
            )
        else:
            checks["gutterDrift"] = check_result(
                "pass" if peak_drift <= thresholds.max_gutter_drift_ratio else "fail",
                {
                    "baselineX": baseline_gutter,
                    "peakDriftRatio": peak_drift,
                    "driftLimit": thresholds.max_gutter_drift_ratio,
                    "baselineContrast": baseline_contrast,
                },
            )
    else:
        checks["gutterDrift"] = check_result(
            "skipped", {}, "portrait recording has no spread gutter"
        )

    if scenario == "idle":
        idle_start = min(len(frames) - 2, max(baseline_end, math.ceil(sample_fps * 1.25)))
        idle_frames = [resized_luma(frame) for frame in frames[idle_start:]]
        idle_differences = [
            np.abs(current - previous)
            for previous, current in zip(idle_frames, idle_frames[1:])
        ]
        changed = [float(np.mean(difference > 15.0)) for difference in idle_differences]
        mean_errors = [float(np.mean(difference)) for difference in idle_differences]
        excursions = sum(
            changed_fraction > thresholds.max_idle_changed_fraction
            and mean_error > thresholds.max_idle_mae
            for changed_fraction, mean_error in zip(changed, mean_errors)
        )
        checks["idleStability"] = check_result(
            "pass" if excursions == 0 else "fail",
            {
                "excursions": excursions,
                "peakChangedFraction": max(changed, default=0.0),
                "peakMeanAbsoluteError": max(mean_errors, default=0.0),
                "changedFractionLimit": thresholds.max_idle_changed_fraction,
                "meanAbsoluteErrorLimit": thresholds.max_idle_mae,
                "evaluatedFramePairs": len(idle_differences),
            },
        )
    else:
        checks["idleStability"] = check_result(
            "skipped", {}, "scenario contains an intentional gesture"
        )

    statuses = [check["status"] for check in checks.values()]
    overall = "fail" if "fail" in statuses else "pass"
    return {
        "schemaVersion": 1,
        "privacy": {
            "framesPersisted": False,
            "ocrPerformed": False,
            "contentFieldsEmitted": False,
        },
        "scenario": scenario,
        "orientation": orientation,
        "sampleFps": sample_fps,
        "sampledFrames": len(frames),
        "frameWidth": int(shape[1]),
        "frameHeight": int(shape[0]),
        "overall": overall,
        "checks": checks,
    }


def probe_video(path: Path) -> tuple[int, int, float]:
    command = [
        "ffprobe",
        "-v",
        "error",
        "-select_streams",
        "v:0",
        "-show_entries",
        "stream=width,height:format=duration",
        "-of",
        "json",
        str(path),
    ]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    payload = json.loads(completed.stdout)
    streams = payload.get("streams") or []
    if len(streams) != 1:
        raise ValueError("recording must contain exactly one video stream")
    width = int(streams[0]["width"])
    height = int(streams[0]["height"])
    duration = float((payload.get("format") or {}).get("duration") or 0.0)
    if width <= 0 or height <= 0 or duration < 0:
        raise ValueError("recording has invalid video geometry or duration")
    return width, height, duration


def decode_video(path: Path, sample_fps: float, max_width: int = 720) -> list[np.ndarray]:
    source_width, source_height, _ = probe_video(path)
    target_width = min(source_width, max_width)
    target_height = max(2, round(source_height * target_width / source_width))
    if target_height % 2:
        target_height += 1
    command = [
        "ffmpeg",
        "-v",
        "error",
        "-i",
        str(path),
        "-vf",
        f"fps={sample_fps},scale={target_width}:{target_height}",
        "-f",
        "rawvideo",
        "-pix_fmt",
        "rgb24",
        "-",
    ]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    frame_bytes = target_width * target_height * 3
    frames: list[np.ndarray] = []
    assert process.stdout is not None
    while True:
        payload = process.stdout.read(frame_bytes)
        if not payload:
            break
        if len(payload) != frame_bytes:
            process.kill()
            raise ValueError("ffmpeg emitted a partial frame")
        frames.append(
            np.frombuffer(payload, dtype=np.uint8)
            .reshape((target_height, target_width, 3))
            .copy()
        )
    stderr = process.stderr.read().decode("utf-8", errors="replace") if process.stderr else ""
    exit_code = process.wait()
    if exit_code != 0:
        raise RuntimeError(f"ffmpeg decode failed with exit {exit_code}: {stderr.strip()}")
    if not frames:
        fallback = subprocess.run(
            [
                "ffmpeg",
                "-v",
                "error",
                "-i",
                str(path),
                "-frames:v",
                "1",
                "-vf",
                f"scale={target_width}:{target_height}",
                "-f",
                "rawvideo",
                "-pix_fmt",
                "rgb24",
                "-",
            ],
            check=True,
            capture_output=True,
        )
        if len(fallback.stdout) != frame_bytes:
            raise ValueError("recording does not contain a complete video frame")
        frames.append(
            np.frombuffer(fallback.stdout, dtype=np.uint8)
            .reshape((target_height, target_width, 3))
            .copy()
        )
    return frames


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video", required=True, type=Path)
    parser.add_argument("--scenario", required=True, choices=(
        "slow-next", "snap-back", "previous", "rapid-turns", "idle"
    ))
    parser.add_argument("--orientation", required=True, choices=("portrait", "landscape"))
    parser.add_argument("--sample-fps", type=float, default=12.0)
    parser.add_argument("--expected-duration-seconds", type=float)
    parser.add_argument("--roi", type=parse_region, default=Region(0.02, 0.05, 0.96, 0.90))
    parser.add_argument("--decoration-region", action="append", type=parse_region, default=[])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--require-all", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    video = args.video.resolve()
    if not video.is_file() or video.stat().st_size == 0:
        raise FileNotFoundError(f"recording is missing or empty: {video}")
    regions = args.decoration_region or default_decoration_regions(args.orientation, args.roi)
    if args.expected_duration_seconds is not None and (
        args.scenario != "idle" or args.expected_duration_seconds <= 0
    ):
        raise ValueError("expected duration is supported only for positive idle probes")
    decoded_frames = decode_video(video, args.sample_fps)
    frames, idle_extended = extend_idle_frames(
        frames=decoded_frames,
        sample_fps=args.sample_fps,
        scenario=args.scenario,
        expected_duration_seconds=args.expected_duration_seconds,
    )
    report = analyze_frames(
        frames=frames,
        sample_fps=args.sample_fps,
        orientation=args.orientation,
        scenario=args.scenario,
        roi=args.roi,
        decoration_regions=regions,
    )
    report["sourceDecodedFrames"] = len(decoded_frames)
    report["idleTimelineExtended"] = idle_extended
    if args.expected_duration_seconds is not None:
        report["expectedDurationSeconds"] = args.expected_duration_seconds
    report["videoSha256"] = sha256(video)
    report["videoBytes"] = video.stat().st_size
    if args.require_all and any(
        check["status"] == "skipped" for check in report["checks"].values()
    ):
        report["overall"] = "incomplete"
    output = args.output or video.with_suffix(".analysis.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Reader visual QA {report['overall']}: {output}")
    return {"pass": 0, "fail": 2, "incomplete": 3}[report["overall"]]


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Reader visual QA error: {error}", file=sys.stderr)
        raise SystemExit(4)
