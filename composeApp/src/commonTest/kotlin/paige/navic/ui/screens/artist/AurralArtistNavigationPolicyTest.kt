package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.DomainArtist
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.SearchScope

class AurralArtistNavigationPolicyTest {
	@Test
	fun contributorArtistRouteUsesLocalArtistWhenIdExists() {
		assertEquals(
			Screen.ArtistDetail("local-artist-id"),
			artistCreditRoute(
				artistId = "local-artist-id",
				artistName = "Track Artist",
				localArtists = listOf(
					DomainArtist(id = "local-artist-id", name = "Different Display")
				),
				aurralEnabled = true
			)
		)
	}

	@Test
	fun contributorArtistRouteFallsBackToLocalArtistNameBeforeAurral() {
		assertEquals(
			Screen.ArtistDetail("local-artist-id"),
			artistCreditRoute(
				artistId = "stale-song-artist-id",
				artistName = " Track Artist ",
				localArtists = listOf(
					DomainArtist(id = "local-artist-id", name = "track artist")
				),
				aurralEnabled = true
			)
		)
	}

	@Test
	fun contributorArtistRouteUsesAurralNameLookupForMissingLocalArtist() {
		assertEquals(
			Screen.AurralArtist(
				artistMbid = "name:naoshi-mizuta",
				artistName = "Naoshi Mizuta"
			),
			artistCreditRoute(
				artistId = "stale-song-artist-id",
				artistName = " Naoshi Mizuta ",
				localArtists = emptyList(),
				aurralEnabled = true
			)
		)
	}

	@Test
	fun contributorArtistRouteSkipsMissingLocalArtistWhenAurralDisabled() {
		assertNull(
			artistCreditRoute(
				artistId = "stale-song-artist-id",
				artistName = "Naoshi Mizuta",
				localArtists = emptyList(),
				aurralEnabled = false
			)
		)
	}

	@Test
	fun albumArtistCreditRouteOpensMusicSearchForCompoundCredits() {
		assertEquals(
			Screen.Search(
				nested = true,
				scope = SearchScope.Music,
				initialQuery = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki"
			),
			albumArtistCreditRoute(
				artistId = "album-artist-id",
				artistName = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki",
				localArtists = emptyList(),
				aurralEnabled = true
			)
		)
	}

	@Test
	fun externalSimilarArtistRouteKeepsAurralArtistDetails() {
		assertEquals(
			Screen.AurralArtist(
				artistMbid = "external-mbid",
				artistName = "External Artist",
				imageUrl = "https://aurral.example.com/external.jpg"
			),
			aurralExternalArtistRoute(
				AurralSimilarArtistRow(
					artist = AurralSimilarArtist(
						id = " external-mbid ",
						name = " External Artist ",
						imageUrl = "https://aurral.example.com/external.jpg"
					),
					localArtistId = null,
					inLibrary = false,
					matchPercent = 91
				)
			)
		)
	}

	@Test
	fun externalSimilarArtistRouteSkipsLocalAndIncompleteRows() {
		assertNull(
			aurralExternalArtistRoute(
				AurralSimilarArtistRow(
					artist = AurralSimilarArtist(id = "external-mbid", name = "External Artist"),
					localArtistId = "local-id",
					inLibrary = true,
					matchPercent = null
				)
			)
		)
		assertNull(
			aurralExternalArtistRoute(
				AurralSimilarArtistRow(
					artist = AurralSimilarArtist(id = " ", name = "External Artist"),
					localArtistId = null,
					inLibrary = false,
					matchPercent = null
				)
			)
		)
		assertNull(
			aurralExternalArtistRoute(
				AurralSimilarArtistRow(
					artist = AurralSimilarArtist(id = "external-mbid", name = " "),
					localArtistId = null,
					inLibrary = false,
					matchPercent = null
				)
			)
		)
	}
}
