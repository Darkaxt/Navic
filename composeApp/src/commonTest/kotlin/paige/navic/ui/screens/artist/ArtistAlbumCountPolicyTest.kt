package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.visibleArtistListEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistAlbumCountPolicyTest {
	@Test
	fun defaultPolicyKeepsExistingCallerPresentation() {
		for (count in listOf(-1, 0, 1, 7)) {
			assertTrue(shouldShowArtistAlbumCount(count))
			assertTrue(shouldShowArtistAlbumCount(count, showUnknownAlbumCount = true))
		}
	}

	@Test
	fun searchPolicyShowsOnlyPositiveCounts() {
		assertFalse(shouldShowArtistAlbumCount(-1, showUnknownAlbumCount = false))
		assertFalse(shouldShowArtistAlbumCount(0, showUnknownAlbumCount = false))
		assertTrue(shouldShowArtistAlbumCount(1, showUnknownAlbumCount = false))
		assertTrue(shouldShowArtistAlbumCount(7, showUnknownAlbumCount = false))
	}

	@Test
	fun hidingUnknownCountDoesNotHideArtistResult() {
		val artist = DomainArtist(id = "koji", name = "Koji Kondo")
		val results = listOf(artist).visibleArtistListEntries()

		assertEquals(listOf(artist), results)
		assertFalse(shouldShowArtistAlbumCount(results.single().albumCount, showUnknownAlbumCount = false))
		assertEquals(listOf(artist), results)
	}
}
