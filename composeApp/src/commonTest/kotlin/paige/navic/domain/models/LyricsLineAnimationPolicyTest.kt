package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsLineAnimationPolicyTest {
	@Test
	fun lyricLineScalePreservesCurrentSyncedActiveEmphasis() {
		assertEquals(
			1.05f,
			lyricsLineScale(
				animateSize = true,
				isSynced = true,
				isActive = true,
				isSelectionMode = false
			)
		)
		assertEquals(
			0.98f,
			lyricsLineScale(
				animateSize = true,
				isSynced = true,
				isActive = false,
				isSelectionMode = false
			)
		)
	}

	@Test
	fun lyricLineScaleCanDisableSizeAnimation() {
		assertEquals(
			1.0f,
			lyricsLineScale(
				animateSize = false,
				isSynced = true,
				isActive = true,
				isSelectionMode = false
			)
		)
		assertEquals(
			1.0f,
			lyricsLineScale(
				animateSize = false,
				isSynced = true,
				isActive = false,
				isSelectionMode = false
			)
		)
	}

	@Test
	fun lyricLineScaleDoesNotAnimateUnsyncedOrSelectionLines() {
		assertEquals(
			1.0f,
			lyricsLineScale(
				animateSize = true,
				isSynced = false,
				isActive = true,
				isSelectionMode = false
			)
		)
		assertEquals(
			1.0f,
			lyricsLineScale(
				animateSize = true,
				isSynced = true,
				isActive = true,
				isSelectionMode = true
			)
		)
	}
}
