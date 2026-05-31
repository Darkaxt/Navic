package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.ui.navigation.Screen

class AurralArtistNavigationPolicyTest {
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
