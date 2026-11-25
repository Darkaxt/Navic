package paige.navic.domain.models

import paige.navic.data.database.Converters
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.artistDetailSongCreditAlbumIds
import paige.navic.ui.screens.artist.structuredArtistCreditRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ArtistCreditsTest {
	@Test
	fun structuredArraysBecomeArtistAndAlbumArtistContributors() {
		val composer = DomainContributor("composer", null, "c1", "Someone")
		val contributors = contributorsWithStructuredArtists(
			contributors = listOf(composer),
			artists = listOf(
				StructuredArtistCredit("acraze", "ACRAZE"),
				StructuredArtistCredit("106uJQW1ymogri5aggVVnS", "Cherish"),
				StructuredArtistCredit("acraze", "ACRAZE"),
				StructuredArtistCredit("", "No id"),
				StructuredArtistCredit("odd", "Semi;colon")
			),
			albumArtists = listOf(StructuredArtistCredit("acraze", "ACRAZE"))
		)

		assertEquals(
			listOf(
				composer,
				DomainContributor(ContributorRoleArtist, null, "acraze", "ACRAZE"),
				DomainContributor(ContributorRoleArtist, null, "106uJQW1ymogri5aggVVnS", "Cherish"),
				DomainContributor(ContributorRoleAlbumArtist, null, "acraze", "ACRAZE")
			),
			contributors
		)
		val converters = Converters()
		assertEquals(contributors, converters.toContributorList(converters.fromContributorList(contributors)))
	}

	@Test
	fun aSongCreditedToSeveralArtistsListsEachOne() {
		val song = song(
			id = "30EOBllYhtjXZdvitFdgkc",
			albumId = "do-it-to-it",
			artistName = "ACRAZE, Cherish",
			artistId = "acraze",
			artists = listOf("acraze" to "ACRAZE", "106uJQW1ymogri5aggVVnS" to "Cherish"),
			albumArtists = listOf("acraze" to "ACRAZE")
		)

		assertEquals(listOf("ACRAZE", "Cherish"), song.structuredArtists().map { it.artistName })
		// Cherish's page finds the album through her id, not through a name split.
		val cherish = DomainArtist(id = "106uJQW1ymogri5aggVVnS", name = "Cherish")
		assertEquals(listOf("do-it-to-it"), artistDetailSongCreditAlbumIds(cherish, listOf(song)))
		// She is not in getArtists (participant only): the credit opens her library page by id,
		// and the page names her from the songs, not from the raw id.
		val credit = song.structuredArtists().last()
		assertEquals(Screen.ArtistDetail("106uJQW1ymogri5aggVVnS"), structuredArtistCreditRoute(credit))
		assertEquals("Cherish", listOf(song).structuredArtistName("106uJQW1ymogri5aggVVnS"))
		assertEquals(null, listOf(song).structuredArtistName("unknown"))
		val fromRouteId = DomainArtist(id = "106uJQW1ymogri5aggVVnS", name = "106uJQW1ymogri5aggVVnS")
		assertEquals(listOf("do-it-to-it"), artistDetailSongCreditAlbumIds(fromRouteId, listOf(song)))
	}

	@Test
	fun aDuoNameStaysOneAlbumArtist() {
		val album = album(
			artistName = "Chase & Status",
			songs = listOf(
				song(
					id = "s1",
					albumId = "album",
					artistName = "Chase & Status",
					artistId = "2uQbYXjkaGXdAn3mOAzEhr",
					artists = listOf("2uQbYXjkaGXdAn3mOAzEhr" to "Chase & Status"),
					albumArtists = listOf("2uQbYXjkaGXdAn3mOAzEhr" to "Chase & Status")
				)
			)
		)

		assertEquals(
			listOf("2uQbYXjkaGXdAn3mOAzEhr" to "Chase & Status"),
			album.structuredAlbumArtists().map { it.artistId to it.artistName }
		)
	}

	@Test
	fun songsWithoutArraysLeaveTheFallbackToTheDisplayString() {
		val album = album(
			artistName = "Jonas Blue & EYLR",
			songs = listOf(song("s1", "album", "Jonas Blue & EYLR", "combined", emptyList(), emptyList()))
		)

		assertEquals(emptyList(), album.structuredAlbumArtists())
		assertEquals(emptyList(), album.songs.single().structuredArtists())
	}

	private fun album(artistName: String, songs: List<DomainSong>) = DomainAlbum(
		id = "album",
		name = "Album",
		artistName = artistName,
		artistId = null,
		year = null,
		coverArtId = "cover",
		genre = null,
		genres = emptyList(),
		songCount = songs.size,
		duration = songs.fold(0.seconds) { total, song -> total + song.duration },
		createdAt = Instant.fromEpochMilliseconds(0),
		starredAt = null,
		lastPlayedAt = null,
		playCount = 0,
		userRating = null,
		version = null,
		musicBrainzId = null,
		songs = songs
	)

	private fun song(
		id: String,
		albumId: String,
		artistName: String,
		artistId: String,
		artists: List<Pair<String, String>>,
		albumArtists: List<Pair<String, String>>
	) = DomainSong(
		id = id,
		title = id,
		artistName = artistName,
		artistId = artistId,
		albumTitle = "Album",
		albumId = albumId,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 200.seconds,
		bpm = null,
		contributors = contributorsWithStructuredArtists(
			contributors = emptyList(),
			artists = artists.map { (artistId, name) -> StructuredArtistCredit(artistId, name) },
			albumArtists = albumArtists.map { (artistId, name) -> StructuredArtistCredit(artistId, name) }
		),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = "flac",
		mimeType = "audio/flac",
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
