package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingArtworkRotationPolicyTest {
	@Test
	fun rotationDefaultsToInactive() {
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = false,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = true
			)
		)
	}

	@Test
	fun rotationRequiresPlayingActiveArtworkWithCoverArt() {
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = true,
				isActiveArtwork = true,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = false,
				hasCoverArt = true
			)
		)
		assertFalse(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = false
			)
		)
	}

	@Test
	fun rotationCanRunForPlayingActiveArtworkWithCoverArt() {
		assertTrue(
			shouldRotateNowPlayingArtwork(
				enabled = true,
				isPaused = false,
				isActiveArtwork = true,
				hasCoverArt = true
			)
		)
	}
}
