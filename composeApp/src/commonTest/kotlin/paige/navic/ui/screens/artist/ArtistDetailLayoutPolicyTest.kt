package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.AurralArtistExternalLink
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainContributor
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.repositories.AurralDiscoverArtist

class ArtistDetailLayoutPolicyTest {
	@Test
	fun artistHeaderGenreLabelsAreTrimmedDedupedAndLimited() {
		assertEquals(
			listOf("Soundtrack", "Film Score"),
			artistHeaderGenreLabels(
				listOf(" Soundtrack ", "", "Soundtrack", "Film Score", "Ambient"),
				limit = 2
			)
		)
	}

	@Test
	fun artistHeaderExternalLinksKeepOnlyDistinctHttpLinks() {
		assertEquals(
			listOf(
				ArtistHeaderExternalLink("musicbrainz", "https://musicbrainz.org/artist/id"),
				ArtistHeaderExternalLink("Aurral", "https://aurral.example/artist/id")
			),
			artistHeaderExternalLinks(
				listOf(
					AurralArtistExternalLink("musicbrainz", "https://musicbrainz.org/artist/id"),
					AurralArtistExternalLink("musicbrainz", "https://musicbrainz.org/artist/id"),
					AurralArtistExternalLink("", "ftp://invalid.example"),
					AurralArtistExternalLink("Aurral", "https://aurral.example/artist/id"),
					AurralArtistExternalLink("lastfm", "https://last.fm/music/artist")
				),
				limit = 2
			)
		)
	}

	@Test
	fun localCatalogAddsSongCreditAlbumsAsPartialEvidence() {
		val artist = DomainArtist(
			id = "john-powell",
			name = "John Powell",
			musicBrainzId = "52bb713d-b0c9-4bf6-9f58-392388d5cc11"
		)
		val testDrive = song(
			id = "test-drive",
			title = "Test Drive",
			artistId = "john-powell",
			artistName = "John Powell",
			albumId = "httyd-fyc",
			albumTitle = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
			playCount = 9
		)
		val unrelated = song(
			id = "unrelated",
			title = "Unrelated Cue",
			artistId = "other",
			artistName = "Other",
			albumId = "httyd-fyc",
			albumTitle = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]"
		)
		val candidateAlbum = album(
			id = "httyd-fyc",
			name = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
			songs = listOf(testDrive, unrelated)
		)

		val creditAlbumIds = artistDetailSongCreditAlbumIds(
			artist = artist,
			allSongs = listOf(testDrive, unrelated)
		)
		val catalog = artistDetailLocalCatalog(
			artist = artist,
			directAlbums = emptyList(),
			allSongs = listOf(testDrive, unrelated),
			creditCandidateAlbums = listOf(candidateAlbum)
		)

		assertEquals(listOf("httyd-fyc"), creditAlbumIds)
		assertEquals(listOf("httyd-fyc"), catalog.albums.map { it.id })
		assertEquals(listOf("Test Drive"), catalog.albums.single().songs.map { it.title })
		assertEquals(1, catalog.albums.single().songCount)
		assertEquals(listOf("Test Drive"), catalog.songs.map { it.title })
	}

	@Test
	fun frequentSongsGridHeightOnlyReservesVisibleRows() {
		assertEquals(0, artistTopSongsGridHeightDp(songCount = 0))
		assertEquals(84, artistTopSongsGridHeightDp(songCount = 1))
		assertEquals(168, artistTopSongsGridHeightDp(songCount = 2))
		assertEquals(252, artistTopSongsGridHeightDp(songCount = 3))
		assertEquals(252, artistTopSongsGridHeightDp(songCount = 12))
	}

	@Test
	fun headingOnlyUsesVerifiedExternalImageUrl() {
		assertNull(
			artistDetailHeadingImageUrl(
				DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = null,
					artistImageUrl = " https://navidrome.example.com/protected/bond.jpg?token=expired "
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
				verifiedExternalImageUrl = "https://aurral.example.com/bond.webp",
				artistArtworkPriority = ArtworkSourcePriority.AurralFirst
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
				verifiedExternalImageUrl = "https://aurral.example.com/bond.webp",
				artistArtworkPriority = ArtworkSourcePriority.NativeFirst
			)
		)
	}

	@Test
	fun headingSuppressesNativeCoverWhileAurralArtworkIsEnabled() {
		val artist = DomainArtist(
			id = "jason-ross",
			name = "Jason Ross",
			coverArtId = "navidrome-artist-cover"
		)

		assertNull(
			artistDetailHeadingCoverArtId(
				artist = artist,
				externalArtworkEnabled = true
			)
		)
		assertNull(
			artistCoverArtIdForExternalArtworkPolicy(
				artist = artist,
				externalArtworkEnabled = true
			)
		)
		assertEquals(
			"navidrome-artist-cover",
			artistDetailHeadingCoverArtId(
				artist = artist,
				artistArtworkPriority = ArtworkSourcePriority.NativeFirst,
				externalArtworkEnabled = true
			)
		)
		assertEquals(
			"navidrome-artist-cover",
			artistCoverArtIdForExternalArtworkPolicy(
				artist = artist,
				artistArtworkPriority = ArtworkSourcePriority.NativeFirst,
				externalArtworkEnabled = true
			)
		)
		assertNull(
			artistDetailPlaybackOrigin(
				artistStateForTransition("jason-ross").copy(
					artist = artist,
					aurralArtistImageUrl = null
				),
				externalArtworkEnabled = true
			).coverArtId
		)
		assertEquals(
			"navidrome-artist-cover",
			artistDetailHeadingCoverArtId(
				artist = artist,
				externalArtworkEnabled = false
			)
		)
		assertEquals(
			"navidrome-artist-cover",
			artistCoverArtIdForExternalArtworkPolicy(
				artist = artist,
				externalArtworkEnabled = false
			)
		)
	}

	@Test
	fun artistImagePolicyTreatsNavidromeArtistImageAsUnresolvedWhenAurralIsEnabled() {
		assertNull(
			artistImageUrlForExternalArtworkPolicy(
				artist = DomainArtist(
					id = "jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-cover",
					artistImageUrl = "https://navidrome.example.com/rest/getArtistImage?id=jason-ross"
				),
				externalArtworkEnabled = true
			)
		)
		assertNull(
			artistImageUrlForExternalArtworkPolicy(
				artist = DomainArtist(
					id = "jason-ross",
					name = "Jason Ross",
					artistImageUrl = "https://navidrome.example.com/rest/getCoverArt?id=jason-cover"
				),
				externalArtworkEnabled = true
			)
		)
		assertEquals(
			"https://aurral.example.com/artists/jason-ross.webp",
			artistImageUrlForExternalArtworkPolicy(
				artist = DomainArtist(
					id = "jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-cover",
					artistImageUrl = " https://aurral.example.com/artists/jason-ross.webp "
				),
				externalArtworkEnabled = true
			)
		)
		assertNull(
			artistImageUrlForExternalArtworkPolicy(
				artist = DomainArtist(
					id = "jason-ross",
					name = "Jason Ross",
					artistImageUrl = "https://aurral.example.com/artists/jason-ross.webp"
				),
				externalArtworkEnabled = false
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheByLocalArtistId() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "아이유",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				)
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheFromPrecomputedIndex() {
		val index = artistHeaderImageCacheIndex(
			listOf(
				ArtistHeaderImageCacheEntry(
					artistId = "other-local-id",
					sourceArtistId = "other-source-id",
					name = "Other Artist",
					normalizedName = "other artist",
					imageUrl = "https://aurral.example.com/other.webp"
				),
				ArtistHeaderImageCacheEntry(
					artistId = "local-john-powell",
					sourceArtistId = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
					name = "John Powell",
					normalizedName = "john powell",
					imageUrl = "https://aurral.example.com/john-powell.webp"
				)
			)
		)

		assertEquals(
			"https://aurral.example.com/john-powell.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-john-powell",
					name = "John   Powell",
					musicBrainzId = "52bb713d-b0c9-4bf6-9f58-392388d5cc11"
				),
				index = index
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheByNormalizedNameAfterIdChange() {
		assertEquals(
			"https://aurral.example.com/lindsey.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "new-local-id",
					name = "Lindsey   Stirling",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "old-local-id",
						sourceArtistId = "source-lindsey",
						name = "Lindsey Stirling",
						normalizedName = "lindsey stirling",
						imageUrl = "https://aurral.example.com/lindsey.webp"
					)
				)
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheBeforeNativeCoverWhenAurralIsFirst() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = "ar-local-iu",
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				),
				artistArtworkPriority = ArtworkSourcePriority.AurralFirst
			)
		)
	}

	@Test
	fun headingPrefersExactLocalArtistCacheOverNewerNameOnlyMatch() {
		assertEquals(
			"https://aurral.example.com/exact-iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = null,
						sourceArtistId = null,
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://other.example.com/newer-name-only.webp",
						source = "Other",
						updatedAtMillis = 2_000L
					),
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/exact-iu.webp",
						source = "Aurral",
						updatedAtMillis = 1_000L
					)
				)
			)
		)
	}

	@Test
	fun headingPrefersAurralSourceWithinSameCacheMatchStrength() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://other.example.com/newer-iu.webp",
						source = "Other",
						updatedAtMillis = 2_000L
					),
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp",
						source = "Aurral",
						updatedAtMillis = 1_000L
					)
				)
			)
		)
	}

	@Test
	fun headingKeepsNativeCoverBeforePersistentArtistPhotoCacheWhenNativeIsFirst() {
		assertNull(
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = "ar-local-iu",
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				),
				artistArtworkPriority = ArtworkSourcePriority.NativeFirst
			)
		)
	}

	@Test
	fun headingSkipsPersistentArtistPhotoCacheWhenExternalArtworkIsDisabled() {
		assertNull(
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				),
				artistArtworkPriority = ArtworkSourcePriority.AurralFirst,
				externalArtworkEnabled = false
			)
		)
	}

	@Test
	fun headingIgnoresNonHttpArtistPhotoCacheUrls() {
		assertNull(
			artistDetailCachedImageUrl(
				artist = DomainArtist(id = "local-iu", name = "IU"),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = null,
						name = "IU",
						normalizedName = "iu",
						imageUrl = "/artist/iu.webp"
					)
				)
			)
		)
	}

	@Test
	fun headingIgnoresNavidromeArtistPhotoCacheUrlsWhenAurralIsEnabled() {
		assertNull(
			artistDetailCachedImageUrl(
				artist = DomainArtist(id = "local-jason", name = "Jason Ross"),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-jason",
						sourceArtistId = "source-jason",
						name = "Jason Ross",
						normalizedName = "jason ross",
						imageUrl = "https://navidrome.example.com/rest/getArtistImage?id=jason-ross",
						source = "Navidrome",
						updatedAtMillis = 2_000L
					)
				),
				externalArtworkEnabled = true
			)
		)
	}

	@Test
	fun artistListHydrationTargetsArtistsWithoutCachedAurralPhotos() {
		val targets = artistListAurralPhotoHydrationTargets(
			artists = listOf(
				DomainArtist(
					id = "jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-jason-cover",
					artistImageUrl = null
				),
				DomainArtist(
					id = "iu",
					name = "IU",
					coverArtId = "navidrome-iu-cover",
					artistImageUrl = "https://aurral.example.com/iu.webp"
				)
			),
			attemptedLookupKeys = emptySet(),
			externalArtworkEnabled = true
		)

		assertEquals(listOf("jason-ross|jason ross"), targets.map { it.lookupKey })
		assertEquals("Jason Ross", targets.single().artist.name)
	}

	@Test
	fun artistListHydrationTreatsNavidromeArtistImageAsUnresolvedWhenAurralIsEnabled() {
		val targets = artistListAurralPhotoHydrationTargets(
			artists = listOf(
				DomainArtist(
					id = "jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-jason-cover",
					artistImageUrl = "https://navidrome.example.com/rest/getArtistImage?id=jason-ross"
				),
				DomainArtist(
					id = "iu",
					name = "IU",
					coverArtId = "navidrome-iu-cover",
					artistImageUrl = "https://aurral.example.com/artist/iu.webp"
				)
			),
			attemptedLookupKeys = emptySet(),
			externalArtworkEnabled = true
		)

		assertEquals(listOf("jason-ross|jason ross"), targets.map { it.lookupKey })
	}

	@Test
	fun artistListHydrationSkipsWhenAurralIsDisabledOrAlreadyAttempted() {
		val artist = DomainArtist(
			id = "jason-ross",
			name = "Jason Ross",
			coverArtId = "navidrome-jason-cover"
		)

		assertEquals(
			emptyList(),
			artistListAurralPhotoHydrationTargets(
				artists = listOf(artist),
				attemptedLookupKeys = emptySet(),
				externalArtworkEnabled = false
			)
		)
		assertEquals(
			emptyList(),
			artistListAurralPhotoHydrationTargets(
				artists = listOf(artist),
				attemptedLookupKeys = setOf("jason-ross|jason ross"),
				externalArtworkEnabled = true
			)
		)
	}

	@Test
	fun artistListHydrationUsesAurralCandidateAndCreatesPersistentCache() {
		val localArtist = DomainArtist(
			id = "local-jason",
			name = "Jason Ross",
			coverArtId = "navidrome-jason-cover",
			musicBrainzId = "jason-mbid"
		)
		val candidate = artistListAurralPhotoCandidate(
			localArtist = localArtist,
			candidates = listOf(
				AurralDiscoverArtist(
					id = "other-mbid",
					name = "Other Jason",
					imageUrl = "https://aurral.example.com/other.webp"
				),
				AurralDiscoverArtist(
					id = "jason-mbid",
					name = "Jason Ross",
					imageUrl = "https://aurral.example.com/jason.webp",
					detailsIdVerified = true
				)
			)
		)
		val entity = artistListAurralPhotoCacheEntity(
			localArtist = localArtist,
			sourceArtist = candidate,
			nowMillis = 2_000L
		)

		assertEquals("https://aurral.example.com/jason.webp", candidate?.imageUrl)
		assertEquals("artist:local-jason", entity?.cacheKey)
		assertEquals("local-jason", entity?.artistId)
		assertEquals("jason-mbid", entity?.sourceArtistId)
		assertEquals("https://aurral.example.com/jason.webp", entity?.imageUrl)
	}

	@Test
	fun headingCreatesPersistentArtistPhotoCacheFromVerifiedExternalImage() {
		val entity = artistDetailPhotoCacheEntity(
			localArtist = DomainArtist(
				id = "local-iu",
				name = "IU",
				musicBrainzId = "mbid-iu"
			),
			sourceArtist = DomainArtist(
				id = "local-iu",
				name = "아이유",
				musicBrainzId = "mbid-iu"
			),
			imageUrl = " https://aurral.example.com/iu.webp ",
			nowMillis = 1_000L
		)

		assertEquals("artist:local-iu", entity?.cacheKey)
		assertEquals("local-iu", entity?.artistId)
		assertEquals("mbid-iu", entity?.sourceArtistId)
		assertEquals("iu", entity?.normalizedName)
		assertEquals("https://aurral.example.com/iu.webp", entity?.imageUrl)
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

	@Test
	fun artistBiographyScrollFadesOnlyShowWhenMoreTextExistsInThatDirection() {
		assertEquals(
			ArtistBiographyScrollFades(showTop = false, showBottom = false),
			artistBiographyScrollFades(scrollValue = 0, maxScrollValue = 0)
		)
		assertEquals(
			ArtistBiographyScrollFades(showTop = false, showBottom = true),
			artistBiographyScrollFades(scrollValue = 0, maxScrollValue = 100)
		)
		assertEquals(
			ArtistBiographyScrollFades(showTop = true, showBottom = true),
			artistBiographyScrollFades(scrollValue = 50, maxScrollValue = 100)
		)
		assertEquals(
			ArtistBiographyScrollFades(showTop = true, showBottom = false),
			artistBiographyScrollFades(scrollValue = 100, maxScrollValue = 100)
		)
	}

	private fun album(
		id: String,
		name: String,
		songs: List<DomainSong>
	) = DomainAlbum(
		id = id,
		name = name,
		artistName = "Soundtrack",
		artistId = "soundtrack",
		year = 2010,
		coverArtId = "cover",
		genre = "Soundtrack",
		genres = listOf("Soundtrack"),
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
		title: String,
		artistId: String,
		artistName: String,
		albumId: String,
		albumTitle: String,
		playCount: Int = 0,
		contributors: List<DomainContributor> = emptyList()
	) = DomainSong(
		id = id,
		title = title,
		artistName = artistName,
		artistId = artistId,
		albumTitle = albumTitle,
		albumId = albumId,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = 2010,
		genre = "Soundtrack",
		genres = listOf("Soundtrack"),
		moods = emptyList(),
		duration = 164.seconds,
		bpm = null,
		contributors = contributors,
		playCount = playCount,
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
		coverArtId = "cover",
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
