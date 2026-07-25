#!/usr/bin/env python3

import importlib.util
import sys
import unittest
from pathlib import Path

import numpy as np


MODULE_PATH = Path(__file__).with_name("reader-visual-qa.py")
SPEC = importlib.util.spec_from_file_location("reader_visual_qa", MODULE_PATH)
assert SPEC and SPEC.loader
QA = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = QA
SPEC.loader.exec_module(QA)


def bright_frame(width=200, height=120):
    frame = np.full((height, width, 3), 220, dtype=np.uint8)
    frame[:, width // 2 - 2 : width // 2 + 3] = 70
    frame[:, :8] = (185, 155, 110)
    frame[:, -8:] = (185, 155, 110)
    return frame


class ReaderVisualQaTest(unittest.TestCase):
    def analyze(self, frames, orientation="landscape", scenario="idle"):
        roi = QA.Region(0.0, 0.0, 1.0, 1.0)
        return QA.analyze_frames(
            frames=frames,
            sample_fps=10.0,
            orientation=orientation,
            scenario=scenario,
            roi=roi,
            decoration_regions=QA.default_decoration_regions(orientation, roi),
        )

    def test_stable_idle_surface_passes_without_persisting_content(self):
        frames = [bright_frame() for _ in range(40)]

        report = self.analyze(frames)

        self.assertEqual("pass", report["overall"])
        self.assertEqual("pass", report["checks"]["blackRegions"]["status"])
        self.assertEqual("pass", report["checks"]["leafBounds"]["status"])
        self.assertEqual("pass", report["checks"]["gutterDrift"]["status"])
        self.assertEqual("pass", report["checks"]["idleStability"]["status"])
        self.assertFalse(report["privacy"]["framesPersisted"])
        self.assertFalse(report["privacy"]["ocrPerformed"])

    def test_black_capture_baseline_is_not_accepted_as_page_geometry(self):
        frames = [np.zeros((120, 200, 3), dtype=np.uint8) for _ in range(40)]

        report = self.analyze(frames)

        self.assertEqual("fail", report["overall"])
        self.assertEqual("fail", report["checks"]["blackRegions"]["status"])

    def test_single_frame_idle_capture_extends_to_expected_timeline(self):
        frame = bright_frame()

        frames, extended = QA.extend_idle_frames(
            [frame],
            sample_fps=10.0,
            scenario="idle",
            expected_duration_seconds=9.0,
        )

        self.assertTrue(extended)
        self.assertEqual(90, len(frames))
        self.assertIs(frame, frames[-1])
        report = self.analyze(frames)
        self.assertEqual("pass", report["checks"]["idleStability"]["status"])

    def test_sparse_idle_changes_remain_visible_after_timeline_extension(self):
        stable = bright_frame()
        changed = stable.copy()
        changed[18:102, 25:175] = (105, 130, 175)
        decoded = [stable.copy() for _ in range(15)] + [changed, stable.copy()]

        frames, extended = QA.extend_idle_frames(
            decoded,
            sample_fps=10.0,
            scenario="idle",
            expected_duration_seconds=9.0,
        )

        self.assertTrue(extended)
        report = self.analyze(frames)
        self.assertEqual("fail", report["checks"]["idleStability"]["status"])

    def test_zoomed_texture_with_black_borders_fails_geometry_checks(self):
        baseline = [bright_frame() for _ in range(12)]
        defect = bright_frame()
        defect[:, :55] = 0
        defect[:, -55:] = 0
        frames = baseline + [defect.copy() for _ in range(12)]

        report = self.analyze(frames, scenario="slow-next")

        self.assertEqual("fail", report["overall"])
        self.assertEqual("fail", report["checks"]["blackRegions"]["status"])
        self.assertEqual("fail", report["checks"]["leafBounds"]["status"])

    def test_idle_page_cycling_is_reported_as_unsolicited_change(self):
        stable = bright_frame()
        changed = stable.copy()
        changed[18:102, 25:175] = (105, 130, 175)
        frames = [stable.copy() for _ in range(15)]
        frames += [changed.copy() if index % 2 else stable.copy() for index in range(20)]

        report = self.analyze(frames)

        self.assertEqual("fail", report["checks"]["idleStability"]["status"])
        self.assertGreater(report["checks"]["idleStability"]["metrics"]["excursions"], 0)

    def test_landscape_gutter_drift_is_detected(self):
        baseline = [bright_frame() for _ in range(12)]
        drifted = np.full((120, 200, 3), 220, dtype=np.uint8)
        drifted[:, 138:143] = 70
        drifted[:, :8] = (185, 155, 110)
        drifted[:, -8:] = (185, 155, 110)
        frames = baseline + [drifted.copy() for _ in range(12)]

        report = self.analyze(frames, scenario="slow-next")

        self.assertEqual("fail", report["checks"]["gutterDrift"]["status"])

    def test_transient_curl_fold_does_not_count_as_settled_gutter_drift(self):
        baseline = [bright_frame() for _ in range(12)]
        folding = bright_frame()
        folding[:, 98:103] = 220
        folding[:, 120:125] = 40
        frames = baseline + [folding.copy() for _ in range(10)] + [bright_frame() for _ in range(8)]

        report = self.analyze(frames, scenario="slow-next")

        self.assertEqual("pass", report["checks"]["gutterDrift"]["status"])

    def test_localized_edge_motion_does_not_fail_decoration_continuity(self):
        baseline = [bright_frame(width=120, height=200) for _ in range(12)]
        moving = baseline[0].copy()
        moving[50:100, :8] = 0
        moving[50:100, -8:] = 0
        roi = QA.Region(0.0, 0.0, 1.0, 1.0)

        report = QA.analyze_frames(
            frames=baseline + [moving.copy() for _ in range(12)],
            sample_fps=10.0,
            orientation="portrait",
            scenario="slow-next",
            roi=roi,
            decoration_regions=QA.default_decoration_regions("portrait", roi),
        )

        self.assertEqual("pass", report["checks"]["decorationContinuity"]["status"])

    def test_decoration_thresholds_must_fail_in_one_region_sample(self):
        baseline = [bright_frame(width=120, height=200) for _ in range(12)]
        changed = baseline[0].copy()
        changed[:80, :8] = np.minimum(changed[:80, :8].astype(np.int16) + 20, 255)
        changed[:60, -8:] = 0
        roi = QA.Region(0.0, 0.0, 1.0, 1.0)

        report = QA.analyze_frames(
            frames=baseline + [changed.copy() for _ in range(12)],
            sample_fps=10.0,
            orientation="portrait",
            scenario="slow-next",
            roi=roi,
            decoration_regions=QA.default_decoration_regions("portrait", roi),
        )

        self.assertEqual("pass", report["checks"]["decorationContinuity"]["status"])

    def test_disappearing_outer_decorations_fail_continuity(self):
        baseline = [bright_frame(width=120, height=200) for _ in range(12)]
        defect = baseline[0].copy()
        defect[:, :8] = 0
        defect[:, -8:] = 0
        roi = QA.Region(0.0, 0.0, 1.0, 1.0)
        report = QA.analyze_frames(
            frames=baseline + [defect.copy() for _ in range(12)],
            sample_fps=10.0,
            orientation="portrait",
            scenario="slow-next",
            roi=roi,
            decoration_regions=QA.default_decoration_regions("portrait", roi),
        )

        self.assertEqual("fail", report["checks"]["decorationContinuity"]["status"])


if __name__ == "__main__":
    unittest.main()
