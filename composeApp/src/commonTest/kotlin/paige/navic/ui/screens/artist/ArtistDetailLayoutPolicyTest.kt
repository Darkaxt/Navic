package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
	fun headingUsesArtistImageUrlWhenServerCoverIsMissing() {
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
		assertNull(
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
}
