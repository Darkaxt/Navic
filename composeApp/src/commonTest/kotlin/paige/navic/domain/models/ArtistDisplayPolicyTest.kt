package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistDisplayPolicyTest {
	@Test
	fun artistListsHideSyntheticUnknownArtistOnly() {
		assertFalse(shouldShowArtistInArtistLists(artist("[Unknown Artist]")))
		assertFalse(shouldShowArtistInArtistLists(artist(" [unknown artist] ")))
		assertTrue(shouldShowArtistInArtistLists(artist("Unknown Artist")))
		assertTrue(shouldShowArtistInArtistLists(artist("[Unknown Album]")))
		assertEquals(
			listOf("Real Artist"),
			listOf(artist("[Unknown Artist]"), artist("Real Artist"))
				.visibleArtistListEntries()
				.map { it.name }
		)
	}

	private fun artist(name: String) = DomainArtist(
		id = name,
		name = name
	)
}
