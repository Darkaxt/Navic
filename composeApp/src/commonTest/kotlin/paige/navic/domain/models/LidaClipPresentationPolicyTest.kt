package paige.navic.domain.models

import paige.navic.domain.models.settings.LidaClipsBackgroundVideoMode
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidaClipPresentationPolicyTest {
	@Test
	fun backgroundVideoIsEnabledOnlyForNonOffModes() {
		assertFalse(shouldShowLidaClipBackgroundVideo(LidaClipsBackgroundVideoMode.Off))
		assertTrue(shouldShowLidaClipBackgroundVideo(LidaClipsBackgroundVideoMode.Blurred))
		assertTrue(shouldShowLidaClipBackgroundVideo(LidaClipsBackgroundVideoMode.Normal))
	}

	@Test
	fun backgroundVideoAlwaysUsesCropAndForegroundKeepsConfiguredFit() {
		assertEquals(
			LidaClipsVideoFitMode.Crop,
			lidaClipBackgroundVideoFitMode(LidaClipsBackgroundVideoMode.Blurred)
		)
		assertEquals(
			LidaClipsVideoFitMode.Crop,
			lidaClipBackgroundVideoFitMode(LidaClipsBackgroundVideoMode.Normal)
		)
		assertEquals(
			LidaClipsVideoFitMode.Fit,
			lidaClipForegroundVideoFitMode(LidaClipsVideoFitMode.Fit)
		)
		assertEquals(
			LidaClipsVideoFitMode.Crop,
			lidaClipForegroundVideoFitMode(LidaClipsVideoFitMode.Crop)
		)
	}

	@Test
	fun proportionalClipStartUsesSongProgressAndClipDuration() {
		assertEquals(90_000L, lidaClipProgressStartPositionMs(0.5f, 180_000L))
		assertEquals(0L, lidaClipProgressStartPositionMs(-1f, 180_000L))
		assertEquals(180_000L, lidaClipProgressStartPositionMs(2f, 180_000L))
		assertEquals(0L, lidaClipProgressStartPositionMs(0.5f, null))
		assertEquals(0L, lidaClipProgressStartPositionMs(0.5f, 0L))
	}

	@Test
	fun clipDurationSecondsConvertsToMillisecondsOnlyWhenPositive() {
		assertEquals(181_000L, lidaClipDurationMs(181))
		assertEquals(null, lidaClipDurationMs(null))
		assertEquals(null, lidaClipDurationMs(0))
	}

	@Test
	fun nowPlayingClipVideoUsesNavicControlsAndPromotedVideoAudio() {
		assertTrue(shouldMuteNowPlayingBackgroundLidaClipVideo())
		assertFalse(shouldMuteNowPlayingPromotedLidaClipVideo())
		assertFalse(shouldShowNowPlayingLidaClipControls())
		assertTrue(shouldPlayNowPlayingLidaClipVideo(musicIsPaused = false))
		assertFalse(shouldPlayNowPlayingLidaClipVideo(musicIsPaused = true))
		assertTrue(shouldMuteMusicForNowPlayingPromotedLidaClip())
		assertFalse(shouldPauseMusicForNowPlayingPromotedLidaClip())
	}
}
