package paige.navic.domain.models.settings

import androidx.compose.ui.text.style.TextAlign
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsAlignmentTest {
	@Test
	fun autoPreservesCurrentTextDirectionBehavior() {
		assertEquals(TextAlign.Start, LyricsAlignment.Auto.textAlign(isRtl = false))
		assertEquals(TextAlign.End, LyricsAlignment.Auto.textAlign(isRtl = true))
	}

	@Test
	fun explicitAlignmentsIgnoreTextDirection() {
		assertEquals(TextAlign.Start, LyricsAlignment.Start.textAlign(isRtl = true))
		assertEquals(TextAlign.Center, LyricsAlignment.Center.textAlign(isRtl = false))
		assertEquals(TextAlign.End, LyricsAlignment.End.textAlign(isRtl = false))
	}
}
