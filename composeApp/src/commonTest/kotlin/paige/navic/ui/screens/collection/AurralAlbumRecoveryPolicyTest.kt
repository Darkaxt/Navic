package paige.navic.ui.screens.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.repositories.AurralAlbumSearchItem

class AurralAlbumRecoveryPolicyTest {
	@Test
	fun recoveryCandidateMatchesCompoundAlbumArtistByTitleAndContainedArtist() {
		val album = album(
			name = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki"
		)
		val candidate = aurralAlbum(
			id = "release-group",
			title = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "Naoshi Mizuta"
		)

		assertEquals(
			candidate,
			aurralAlbumRecoveryCandidate(album, listOf(aurralAlbum(title = "Final Fantasy XIII"), candidate))
		)
	}

	@Test
	fun recoveryCandidateSkipsDifferentAlbumTitles() {
		val album = album(name = "Final Fantasy XIII-2 Original Soundtrack")

		assertNull(
			aurralAlbumRecoveryCandidate(
				album,
				listOf(aurralAlbum(title = "Final Fantasy XIII Original Soundtrack"))
			)
		)
	}

	private fun album(
		name: String,
		artistName: String = "Artist"
	) = DomainAlbum(
		id = "album",
		name = name,
		artistName = artistName,
		artistId = "artist",
		year = 2011,
		coverArtId = "cover",
		genre = null,
		genres = emptyList(),
		songCount = 0,
		duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST,
		starredAt = null,
		lastPlayedAt = null,
		userRating = null,
		version = null,
		musicBrainzId = null,
		songs = emptyList()
	)

	private fun aurralAlbum(
		id: String = "id",
		title: String,
		artistName: String = "Artist"
	) = AurralAlbumSearchItem(
		id = id,
		title = title,
		artistName = artistName,
		artistMbid = "artist-mbid"
	)
}
