package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsLineTapPolicyTest {
	@Test
	fun seeksOnlyWhenTapSeekingIsEnabledOutsideSelectionMode() {
		assertTrue(
			shouldSeekLyricsLineOnTap(
				isSelectionMode = false,
				lyricsJumpOnTap = true
			)
		)

		assertFalse(
			shouldSeekLyricsLineOnTap(
				isSelectionMode = false,
				lyricsJumpOnTap = false
			)
		)

		assertFalse(
			shouldSeekLyricsLineOnTap(
				isSelectionMode = true,
				lyricsJumpOnTap = true
			)
		)
	}
}
