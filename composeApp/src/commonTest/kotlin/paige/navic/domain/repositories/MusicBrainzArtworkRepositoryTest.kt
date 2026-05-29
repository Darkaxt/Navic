package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class MusicBrainzArtworkRepositoryTest {
	@Test
	fun playbackLookupRequiresSettingNetworkMissingCoverAndMusicBrainzId() {
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = false,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = "recording-mbid",
				albumMusicBrainzId = null
			)
		)
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = false,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = "recording-mbid",
				albumMusicBrainzId = null
			)
		)
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = "song-cover",
				albumCoverArtId = null,
				songMusicBrainzId = "recording-mbid",
				albumMusicBrainzId = null
			)
		)
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = "album-cover",
				songMusicBrainzId = "recording-mbid",
				albumMusicBrainzId = null
			)
		)
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = true,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = "recording-mbid",
				albumMusicBrainzId = null
			)
		)
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = " ",
				albumMusicBrainzId = null,
				songTitle = " ",
				artistName = "Artist"
			)
		)
		assertFalse(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = null,
				albumMusicBrainzId = null,
				songTitle = "Song",
				artistName = " "
			)
		)

		assertTrue(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = null,
				albumMusicBrainzId = "album-mbid"
			)
		)
		assertTrue(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = "recording-mbid",
				albumMusicBrainzId = null
			)
		)
		assertTrue(
			shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = true,
				isOnline = true,
				isRadio = false,
				songCoverArtId = null,
				albumCoverArtId = null,
				songMusicBrainzId = null,
				albumMusicBrainzId = null,
				songTitle = "Dancing Queen",
				artistName = "ABBA"
			)
		)
	}

	@Test
	fun coverArtArchiveEndpointsUsePublicReadOnlyUrls() {
		assertEquals(
			"https://coverartarchive.org/release/76df3287-6cda-33eb-8e9a-044b5e15ffdd",
			coverArtArchiveReleaseEndpoint(" 76df3287-6cda-33eb-8e9a-044b5e15ffdd ")
		)
		assertEquals(
			"https://coverartarchive.org/release-group/c31a5e2b-0bf8-32e0-8aeb-ef4ba9973932",
			coverArtArchiveReleaseGroupEndpoint("c31a5e2b-0bf8-32e0-8aeb-ef4ba9973932")
		)
		assertEquals(
			"https://musicbrainz.org/ws/2/recording/0f6d28a0-2fb9-4c67-8f7b-53b6c7a7f2a1?inc=artist-credits+isrcs+releases+genres+tags&fmt=json",
			musicBrainzRecordingLookupEndpoint("0f6d28a0-2fb9-4c67-8f7b-53b6c7a7f2a1")
		)
		assertEquals(
			"https://musicbrainz.org/ws/2/recording?query=recording%3A%22Dancing%20Queen%22%20AND%20artistname%3A%22ABBA%22&limit=5&fmt=json",
			musicBrainzRecordingSearchEndpoint(title = "Dancing Queen", artistName = "ABBA")
		)
	}

	@Test
	fun recordingSearchEndpointEscapesLuceneSpecialCharactersBeforeUrlEncoding() {
		assertEquals(
			"https://musicbrainz.org/ws/2/recording?query=recording%3A%22Love%20%5C%2F%20Hate%5C%3A%20Part%20%5C%281%5C%29%22%20AND%20artistname%3A%22AC%5C%2FDC%22&limit=5&fmt=json",
			musicBrainzRecordingSearchEndpoint(title = "Love / Hate: Part (1)", artistName = "AC/DC")
		)
	}

	@Test
	fun bestRecordingSearchMatchRequiresHighScoreAndUsableMbid() {
		assertEquals(
			"recording-100",
			bestMusicBrainzRecordingSearchMatch(
				MusicBrainzRecordingSearchResponseDto(
					recordings = listOf(
						MusicBrainzRecordingSearchResultDto(id = " ", score = "100"),
						MusicBrainzRecordingSearchResultDto(id = "recording-89", score = "89"),
						MusicBrainzRecordingSearchResultDto(id = "recording-100", score = "100")
					)
				)
			)
		)
		assertNull(
			bestMusicBrainzRecordingSearchMatch(
				MusicBrainzRecordingSearchResponseDto(
					recordings = listOf(
						MusicBrainzRecordingSearchResultDto(id = "recording-weak", score = "80")
					)
				)
			)
		)
	}

	@Test
	fun recordingMetadataUsesPreferredReleaseAndNormalizesCreditTagsAndUrls() {
		val metadata = musicBrainzTrackMetadata(
			recording = MusicBrainzRecordingDto(
				id = "recording-mbid",
				title = "Recording Title",
				firstReleaseDate = "1999-01-02",
				artistCredits = listOf(
					MusicBrainzArtistCreditDto(name = "Artist A", joinphrase = " feat. "),
					MusicBrainzArtistCreditDto(name = "Artist B", joinphrase = "")
				),
				isrcs = listOf("USAAA9900001", "USAAA9900001", "USAAA9900002"),
				genres = listOf(
					MusicBrainzTagDto(name = "rock", count = 8),
					MusicBrainzTagDto(name = "alternative rock", count = 12)
				),
				tags = listOf(
					MusicBrainzTagDto(name = "favorite", count = 2),
					MusicBrainzTagDto(name = "live", count = 5),
					MusicBrainzTagDto(name = " ", count = 99)
				),
				releases = listOf(
					MusicBrainzReleaseDto(
						id = "release-1",
						title = "First Release",
						date = "1999",
						country = "US",
						status = "Official",
						releaseGroup = MusicBrainzReleaseGroupDto(
							id = "release-group-1",
							title = "First Group"
						)
					),
					MusicBrainzReleaseDto(
						id = "release-2",
						title = "Preferred Release",
						date = "2001-03-04",
						country = "GB",
						status = "Bootleg",
						releaseGroup = MusicBrainzReleaseGroupDto(
							id = "release-group-2",
							title = "Preferred Group"
						)
					)
				)
			),
			preferredReleaseMbid = "release-2"
		)

		assertEquals("recording-mbid", metadata.recordingMbid)
		assertEquals("Recording Title", metadata.recordingTitle)
		assertEquals("Artist A feat. Artist B", metadata.artistCredit)
		assertEquals("1999-01-02", metadata.firstReleaseDate)
		assertEquals("release-2", metadata.releaseMbid)
		assertEquals("Preferred Release", metadata.releaseTitle)
		assertEquals("release-group-2", metadata.releaseGroupMbid)
		assertEquals("Preferred Group", metadata.releaseGroupTitle)
		assertEquals("2001-03-04", metadata.releaseDate)
		assertEquals("GB", metadata.country)
		assertEquals("Bootleg", metadata.status)
		assertEquals(listOf("alternative rock", "rock"), metadata.genres)
		assertEquals(listOf("live", "favorite"), metadata.tags)
		assertEquals(listOf("USAAA9900001", "USAAA9900002"), metadata.isrcs)
		assertEquals("https://musicbrainz.org/recording/recording-mbid", metadata.recordingUrl)
		assertEquals("https://musicbrainz.org/release/release-2", metadata.releaseUrl)
		assertEquals("https://musicbrainz.org/release-group/release-group-2", metadata.releaseGroupUrl)
	}

	@Test
	fun recordingMetadataPrefersReleaseMatchingLocalAlbumTitleWhenArtworkDidNotPickRelease() {
		val metadata = musicBrainzTrackMetadata(
			recording = MusicBrainzRecordingDto(
				id = "recording-mbid",
				title = "Recording Title",
				releases = listOf(
					MusicBrainzReleaseDto(
						id = "release-1",
						title = "Greatest Hits",
						releaseGroup = MusicBrainzReleaseGroupDto(
							id = "release-group-1",
							title = "Greatest Hits"
						)
					),
					MusicBrainzReleaseDto(
						id = "release-2",
						title = "Target Album",
						date = "2001-03-04",
						country = "GB",
						status = "Official",
						releaseGroup = MusicBrainzReleaseGroupDto(
							id = "release-group-2",
							title = "Target Album"
						)
					)
				)
			),
			preferredReleaseMbid = null,
			preferredAlbumTitle = "target album"
		)

		assertEquals("release-2", metadata.releaseMbid)
		assertEquals("Target Album", metadata.releaseTitle)
		assertEquals("release-group-2", metadata.releaseGroupMbid)
		assertEquals("Target Album", metadata.releaseGroupTitle)
	}

	@Test
	fun musicBrainzReleasesForArtworkLookupPreferLocalAlbumTitleMatches() {
		val releases = listOf(
			MusicBrainzReleaseDto(
				id = "release-1",
				title = "Greatest Hits",
				releaseGroup = MusicBrainzReleaseGroupDto(
					id = "release-group-1",
					title = "Greatest Hits"
				)
			),
			MusicBrainzReleaseDto(
				id = "release-2",
				title = "Target Album",
				releaseGroup = MusicBrainzReleaseGroupDto(
					id = "release-group-2",
					title = "Target Album"
				)
			),
			MusicBrainzReleaseDto(
				id = "release-3",
				title = "Other Album",
				releaseGroup = MusicBrainzReleaseGroupDto(
					id = "release-group-3",
					title = "Other Album"
				)
			)
		)

		assertEquals(
			listOf("release-2", "release-1", "release-3"),
			preferredMusicBrainzRecordingReleases(
				releases = releases,
				preferredAlbumTitle = " target   album "
			).map { it.id }
		)
	}

	@Test
	fun musicBrainzReleasesForArtworkLookupCanMatchReleaseGroupTitle() {
		val releases = listOf(
			MusicBrainzReleaseDto(
				id = "release-1",
				title = "Different Edition",
				releaseGroup = MusicBrainzReleaseGroupDto(
					id = "release-group-1",
					title = "Target Album"
				)
			),
			MusicBrainzReleaseDto(
				id = "release-2",
				title = "Other Album",
				releaseGroup = MusicBrainzReleaseGroupDto(
					id = "release-group-2",
					title = "Other Album"
				)
			)
		)

		assertEquals(
			listOf("release-1", "release-2"),
			preferredMusicBrainzRecordingReleases(
				releases = releases,
				preferredAlbumTitle = "Target Album"
			).map { it.id }
		)
	}

	@Test
	fun metadataMapIncludesEntriesEvenWhenArtworkWasNotFound() {
		val metadata = MusicBrainzTrackMetadata(recordingMbid = "recording-mbid")
		val entries = listOf(
			MusicBrainzArtworkCacheEntry(
				songId = "song-1",
				fingerprint = "fingerprint",
				status = MusicBrainzArtworkCacheStatus.NotFound,
				imageUrl = null,
				sourceMbid = null,
				sourceType = null,
				metadata = metadata,
				updatedAtMillis = 1_000L
			)
		)

		assertEquals(mapOf("song-1" to metadata), entries.musicBrainzMetadataBySongId())
	}

	@Test
	fun metadataDisplayFieldsSkipBlankValuesAndJoinLists() {
		val fields = musicBrainzMetadataDisplayFields(
			MusicBrainzTrackMetadata(
				recordingTitle = "Recording Title",
				artistCredit = "Artist A feat. Artist B",
				firstReleaseDate = "1999-01-02",
				releaseTitle = "Preferred Release",
				releaseGroupTitle = "Preferred Group",
				releaseDate = "2001-03-04",
				country = "GB",
				status = "Official",
				genres = listOf("alternative rock", "rock"),
				tags = listOf("live", "favorite"),
				isrcs = listOf("USAAA9900001", "USAAA9900002"),
				recordingUrl = "https://musicbrainz.org/recording/recording-mbid",
				releaseUrl = " ",
				releaseGroupUrl = null
			)
		)

		assertEquals(
			listOf(
				MusicBrainzMetadataField.RecordingTitle to "Recording Title",
				MusicBrainzMetadataField.ArtistCredit to "Artist A feat. Artist B",
				MusicBrainzMetadataField.FirstReleaseDate to "1999-01-02",
				MusicBrainzMetadataField.ReleaseTitle to "Preferred Release",
				MusicBrainzMetadataField.ReleaseGroupTitle to "Preferred Group",
				MusicBrainzMetadataField.ReleaseDate to "2001-03-04",
				MusicBrainzMetadataField.Country to "GB",
				MusicBrainzMetadataField.Status to "Official",
				MusicBrainzMetadataField.Genres to "alternative rock, rock",
				MusicBrainzMetadataField.Tags to "live, favorite",
				MusicBrainzMetadataField.Isrcs to "USAAA9900001, USAAA9900002",
				MusicBrainzMetadataField.RecordingUrl to "https://musicbrainz.org/recording/recording-mbid"
			),
			fields.map { it.field to it.value }
		)
	}

	@Test
	fun frontThumbnailPrefersLargestConfiguredThumbnailBeforeOriginalImage() {
		val image = musicBrainzFrontArtworkImageUrl(
			CoverArtArchiveResponseDto(
				images = listOf(
					CoverArtArchiveImageDto(
						front = false,
						image = "https://example.test/back.jpg",
						thumbnails = mapOf("500" to "https://example.test/back-500.jpg")
					),
					CoverArtArchiveImageDto(
						front = true,
						image = "https://example.test/front.jpg",
						thumbnails = mapOf(
							"250" to "https://example.test/front-250.jpg",
							"500" to "https://example.test/front-500.jpg"
						)
					)
				)
			)
		)

		assertEquals("https://example.test/front-500.jpg", image)
	}

	@Test
	fun coverArtArchiveImageUrlsAreNormalizedToHttps() {
		val image = musicBrainzFrontArtworkImageUrl(
			CoverArtArchiveResponseDto(
				images = listOf(
					CoverArtArchiveImageDto(
						front = true,
						image = "http://coverartarchive.org/release/release-id/front.jpg",
						thumbnails = mapOf(
							"500" to "http://coverartarchive.org/release/release-id/front-500.jpg"
						)
					)
				)
			)
		)

		assertEquals("https://coverartarchive.org/release/release-id/front-500.jpg", image)
	}

	@Test
	fun cachedFoundArtworkLastsLongerThanCachedMisses() {
		val found = MusicBrainzArtworkCacheEntry(
			songId = "song-1",
			fingerprint = "fingerprint",
			status = MusicBrainzArtworkCacheStatus.Found,
			imageUrl = "https://coverartarchive.org/front.jpg",
			sourceMbid = "release-mbid",
			sourceType = MusicBrainzArtworkSourceType.Release,
			updatedAtMillis = 1_000L
		)
		val missing = found.copy(
			status = MusicBrainzArtworkCacheStatus.NotFound,
			imageUrl = null
		)

		assertEquals(found, usableMusicBrainzArtworkCacheEntry(found, "fingerprint", 1_000L + 30.days.inWholeMilliseconds))
		assertNull(usableMusicBrainzArtworkCacheEntry(missing, "fingerprint", 1_000L + 30.days.inWholeMilliseconds))
		assertNull(usableMusicBrainzArtworkCacheEntry(found, "different", 1_000L))
	}

	@Test
	fun cacheStoreKeepsNewestEntriesUpToLimit() {
		val entries = (1..5).map { index ->
			MusicBrainzArtworkCacheEntry(
				songId = "song-$index",
				fingerprint = "fingerprint-$index",
				status = MusicBrainzArtworkCacheStatus.NotFound,
				imageUrl = null,
				sourceMbid = null,
				sourceType = null,
				updatedAtMillis = index.toLong()
			)
		}

		assertEquals(
			listOf("song-5", "song-4", "song-3"),
			cappedMusicBrainzArtworkCacheEntries(entries, maxEntries = 3).map { it.songId }
		)
	}
}
