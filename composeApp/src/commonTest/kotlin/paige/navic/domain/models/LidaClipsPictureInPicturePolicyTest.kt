package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidaClipsPictureInPicturePolicyTest {
	@Test
	fun pictureInPictureRequiresAndroidOAndDeviceFeature() {
		assertFalse(supportsLidaClipsPictureInPicture(sdkInt = 25, hasPictureInPictureFeature = true))
		assertFalse(supportsLidaClipsPictureInPicture(sdkInt = 26, hasPictureInPictureFeature = false))
		assertTrue(supportsLidaClipsPictureInPicture(sdkInt = 26, hasPictureInPictureFeature = true))
	}

	@Test
	fun autoEnterParamsAreAndroidTwelveAndNewerOnly() {
		assertFalse(shouldUseLidaClipsAutoPictureInPictureParams(
			enabled = true,
			sdkInt = 30,
			hasPictureInPictureFeature = true
		))
		assertTrue(shouldUseLidaClipsAutoPictureInPictureParams(
			enabled = true,
			sdkInt = 31,
			hasPictureInPictureFeature = true
		))
	}

	@Test
	fun userLeaveFallbackIsOnlyForActiveVideoOnAndroidEightThroughEleven() {
		assertTrue(shouldEnterLidaClipsPictureInPictureOnUserLeave(
			enabled = true,
			videoActive = true,
			alreadyInPictureInPicture = false,
			sdkInt = 30,
			hasPictureInPictureFeature = true
		))
		assertFalse(shouldEnterLidaClipsPictureInPictureOnUserLeave(
			enabled = true,
			videoActive = true,
			alreadyInPictureInPicture = false,
			sdkInt = 31,
			hasPictureInPictureFeature = true
		))
		assertFalse(shouldEnterLidaClipsPictureInPictureOnUserLeave(
			enabled = true,
			videoActive = false,
			alreadyInPictureInPicture = false,
			sdkInt = 30,
			hasPictureInPictureFeature = true
		))
		assertFalse(shouldEnterLidaClipsPictureInPictureOnUserLeave(
			enabled = true,
			videoActive = true,
			alreadyInPictureInPicture = true,
			sdkInt = 30,
			hasPictureInPictureFeature = true
		))
	}
}
