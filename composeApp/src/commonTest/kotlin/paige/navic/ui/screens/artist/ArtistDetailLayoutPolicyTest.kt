package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.models.DomainArtist

class ArtistDetailLayoutPolicyTest {
	@Test
	fun frequentSongsGridHeightOnlyReservesVisibleRows() {
		assertEquals(0, artistTopSongsGridHeightDp(songCount = 0))
		assertEquals(84, artistTopSongsGridHeightDp(songCount = 1))
		assertEquals(168, artistTopSongsGridHeightDp(songCount = 2))
		assertEquals(252, artistTopSongsGridHeightDp(songCount = 3))
		assertEquals(252, artistTopSongsGridHeightDp(songCount = 12))
	}

	@Test
	fun headingUsesVerifiedExternalImageBeforePotentiallyStaleServerCover() {
		assertEquals(
			"https://assets.example.com/bond.jpg",
			artistDetailHeadingImageUrl(
				DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = null,
					artistImageUrl = " https://assets.example.com/bond.jpg "
				)
			)
		)
		assertEquals(
			"https://aurral.example.com/bond.webp",
			artistDetailHeadingImageUrl(
				artist = DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = null,
					artistImageUrl = null
				),
				verifiedExternalImageUrl = " https://aurral.example.com/bond.webp "
			)
		)
		assertEquals(
			"https://aurral.example.com/bond.webp",
			artistDetailHeadingImageUrl(
				artist = DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = "server-cover",
					artistImageUrl = "https://assets.example.com/bond.jpg"
				),
				verifiedExternalImageUrl = "https://aurral.example.com/bond.webp"
			)
		)
	}

	@Test
	fun playbackOriginUsesVerifiedArtistImageShownInHeading() {
		val origin = artistDetailPlaybackOrigin(
			artistStateForTransition("iu").copy(
				artist = DomainArtist(
					id = "iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				aurralArtistImageUrl = " https://aurral.example.com/iu.webp "
			)
		)

		assertEquals("https://aurral.example.com/iu.webp", origin.coverArtId)
	}

	@Test
	fun artistPageTransitionKeyIgnoresAurralOnlyStateChanges() {
		val baseState = artistStateForTransition("artist-1").copy(
			aurralLoading = true,
			aurralMonitored = null
		)
		val enrichedState = baseState.copy(
			aurralLoading = false,
			aurralMonitored = true,
			aurralArtistImageUrl = "https://aurral.example.com/artist.webp"
		)

		assertEquals(artistDetailTransitionKey(baseState), artistDetailTransitionKey(enrichedState))
		assertFalse(shouldAnimateArtistDetailStateChange(baseState, enrichedState))
		assertTrue(
			shouldAnimateArtistDetailStateChange(
				baseState,
				artistStateForTransition("artist-2")
			)
		)
	}

	private fun artistStateForTransition(artistId: String) =
		paige.navic.ui.screens.artist.viewmodels.ArtistState(
			artist = DomainArtist(id = artistId, name = artistId),
			albums = emptyList(),
			topSongs = emptyList()
		)

	@Test
	fun artistBiographyPreviewExpandsInlineInsteadOfRequiringExternalLink() {
		val biography = "A".repeat(205)

		assertTrue(shouldShowArtistBiographyToggle(biography, limit = 200))
		assertEquals("${"A".repeat(200)}...", artistBiographyDisplayText(biography, expanded = false, limit = 200))
		assertEquals(biography, artistBiographyDisplayText(biography, expanded = true, limit = 200))
		assertFalse(shouldShowArtistBiographyToggle("Short biography", limit = 200))
		assertNull(artistBiographyDisplayText(null, expanded = false, limit = 200))
	}
}
