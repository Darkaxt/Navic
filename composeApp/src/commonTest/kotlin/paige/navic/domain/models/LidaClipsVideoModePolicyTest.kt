package paige.navic.domain.models

import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidaClipsVideoModePolicyTest {
	@Test
	fun landscapeVideoModeRequiresEnabledActiveClip() {
		assertFalse(shouldUseLidaClipsLandscapeVideoMode(enabled = false, videoActive = true))
		assertFalse(shouldUseLidaClipsLandscapeVideoMode(enabled = true, videoActive = false))
		assertTrue(shouldUseLidaClipsLandscapeVideoMode(enabled = true, videoActive = true))
	}

	@Test
	fun videoFitModeCropFillsFrameByCroppingEdges() {
		assertFalse(shouldCropLidaClipsVideoFrame(LidaClipsVideoFitMode.Fit))
		assertTrue(shouldCropLidaClipsVideoFrame(LidaClipsVideoFitMode.Crop))
	}
}
