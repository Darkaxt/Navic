package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverArtFallbackPolicyTest {
	@Test
	fun usesTrimmedContentDescriptionAsFallbackLabel() {
		assertEquals(
			CoverArtFallbackContent(
				label = "You'll Be Alright, Kid",
				initials = "YBA",
				seedSource = "You'll Be Alright, Kid"
			),
			coverArtFallbackContent(
				contentDescription = "  You'll Be Alright, Kid  ",
				coverArtId = "album-1",
				imageUrl = null
			)
		)
	}

	@Test
	fun usesCoverIdentifierOnlyAsFallbackSeedWhenDescriptionIsMissing() {
		assertEquals(
			CoverArtFallbackContent(label = null, initials = null, seedSource = "album-1"),
			coverArtFallbackContent(
				contentDescription = null,
				coverArtId = "album-1",
				imageUrl = null
			)
		)
	}

	@Test
	fun omitsTextWhenNoUsableFallbackLabelExists() {
		val content = coverArtFallbackContent(
			contentDescription = " ",
			coverArtId = null,
			imageUrl = null
		)

		assertNull(content.label)
		assertNull(content.initials)
		assertNull(content.seedSource)
	}
}
