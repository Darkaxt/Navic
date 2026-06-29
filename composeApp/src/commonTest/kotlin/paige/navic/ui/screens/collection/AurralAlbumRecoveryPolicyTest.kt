package paige.navic.ui.screens.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
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
	fun recoveryCandidateUsesTitleAndYearBeforeSelectedTrackArtist() {
		val album = album(
			name = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki"
		)
		val exactAlbum = aurralAlbum(
			id = "release-group",
			title = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "浜渦正志",
			releaseDate = "2011-12-14",
			primaryType = "Album",
			secondaryTypes = listOf("Soundtrack")
		)

		assertEquals(
			exactAlbum,
			aurralAlbumRecoveryCandidate(
				album,
				listOf(
					aurralAlbum(
						id = "special",
						title = "FINAL FANTASY XIII-2 Original Soundtrack -SPECIAL Package-",
						artistName = "浜渦正志",
						releaseDate = "2012-01-31"
					),
					aurralAlbum(
						id = "composer-selected",
						title = "Final Fantasy XIII-2 Composer Selected Soundtrack",
						artistName = "Various Artists",
						releaseDate = "2011-12-18"
					),
					exactAlbum
				)
			)
		)
	}

	@Test
	fun recoveryCandidateKeepsSoundtrackWhenArtistNameIsLocalized() {
		val album = album(
			name = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki",
			year = 2011
		)
		val localizedArtistCandidate = aurralAlbum(
			id = "release-group",
			title = "FINAL FANTASY XIII-2 Original Soundtrack",
			artistName = "浜渦正志",
			releaseDate = "2011-12-14",
			primaryType = "Album",
			secondaryTypes = listOf("Soundtrack")
		)

		assertEquals(
			localizedArtistCandidate,
			aurralAlbumRecoveryCandidate(album, listOf(localizedArtistCandidate))
		)
	}

	@Test
	fun recoveryCandidateAcceptsNearbyYearWhenTitleAndTypeAreStrong() {
		val album = album(
			name = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki",
			year = 2011
		)
		val candidate = aurralAlbum(
			id = "near-year-soundtrack",
			title = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "浜渦正志",
			releaseDate = "2012-01-01",
			primaryType = "Album",
			secondaryTypes = listOf("Soundtrack")
		)

		assertEquals(candidate, aurralAlbumRecoveryCandidate(album, listOf(candidate)))
	}

	@Test
	fun recoveryCandidateRejectsGenericExactTitleWithoutSupportingMetadata() {
		val album = album(
			name = "Crystallize",
			artistName = "Lindsey Stirling",
		)
		val unrelatedAlbum = aurralAlbum(
			id = "wrong-album",
			title = "Crystallize...",
			artistName = "Nora Below",
			releaseDate = "2002",
			primaryType = "Album"
		)
		val unrelatedSingle = aurralAlbum(
			id = "wrong-single",
			title = "Crystallize",
			artistName = "Eliminate",
			releaseDate = "2020-05-15",
			primaryType = "Single"
		)

		assertNull(
			aurralAlbumRecoveryCandidate(album, listOf(unrelatedAlbum, unrelatedSingle))
		)
	}

	@Test
	fun recoveryCandidateChoicesKeepLowConfidenceExactTitleMatchesForManualSelection() {
		val album = album(
			name = "Crystallize",
			artistName = "Lindsey Stirling",
		)
		val unrelatedSingle = aurralAlbum(
			id = "wrong-single",
			title = "Crystallize",
			artistName = "Eliminate",
			releaseDate = "2020-05-15",
			primaryType = "Single"
		)

		assertEquals(
			listOf(unrelatedSingle),
			aurralAlbumRecoveryCandidateChoices(album, listOf(unrelatedSingle)).map { it.album }
		)
	}

	@Test
	fun recoveryCandidateAcceptsSingleWhenArtistAndYearMatch() {
		val album = album(
			name = "strawberry moon",
			artistName = "IU",
			year = 2021
		)
		val exactSingle = aurralAlbum(
			id = "iu-single",
			title = "strawberry moon",
			artistName = "IU",
			releaseDate = "2021-10-19",
			primaryType = "Single"
		)
		val unrelatedAlbum = aurralAlbum(
			id = "raury-album",
			title = "Strawberry Moon",
			artistName = "Raury",
			releaseDate = "2022-06-14",
			primaryType = "Album"
		)

		assertEquals(
			exactSingle,
			aurralAlbumRecoveryCandidate(album, listOf(unrelatedAlbum, exactSingle))
		)
	}

	@Test
	fun recoveryQueriesUseAlbumTitleArtistCandidatesAndYearHints() {
		val album = album(
			name = "Final Fantasy XIII-2 Original Soundtrack",
			artistName = "Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki",
			year = 2011
		)

		assertEquals(
			listOf(
				"Final Fantasy XIII-2 Original Soundtrack Masashi Hamauzu 2011",
				"Final Fantasy XIII-2 Original Soundtrack Naoshi Mizuta 2011",
				"Final Fantasy XIII-2 Original Soundtrack Mitsuto Suzuki 2011",
				"Final Fantasy XIII-2 Original Soundtrack Masashi Hamauzu",
				"Final Fantasy XIII-2 Original Soundtrack Naoshi Mizuta",
				"Final Fantasy XIII-2 Original Soundtrack Mitsuto Suzuki",
				"Final Fantasy XIII-2 Original Soundtrack 2011",
				"Final Fantasy XIII-2 Original Soundtrack"
			),
			aurralAlbumRecoveryQueries(album)
		)
	}

	@Test
	fun artistCreditPartsSplitClickableAlbumCredits() {
		assertEquals(
			listOf("Masashi Hamauzu", "Naoshi Mizuta", "Mitsuto Suzuki"),
			aurralAlbumArtistCreditParts("Masashi Hamauzu, Naoshi Mizuta & Mitsuto Suzuki")
		)
	}

	@Test
	fun artistCreditPartsSplitBulletCreditsAndRemoveDuplicates() {
		assertEquals(
			listOf("Eric Buchholz", "Braxton Burks"),
			aurralAlbumArtistCreditParts(
				"Eric Buchholz & Braxton Burks, Eric Buchholz • Eric Buchholz & Braxton Burks"
			)
		)
	}

	@Test
	fun recoveryRowsMatchOwnedTrackByRecordingMbidEvenWhenTitlesDiffer() {
		val localSong = song(
			title = "Paradigm Shift",
			musicBrainzId = "55ceca99-a43f-4dfb-8711-b27bd6dbecf2",
			trackNumber = 1
		)
		val rows = aurralAlbumRecoveryRows(
			album = album(
				name = "Final Fantasy XIII-2 Original Soundtrack",
				songs = listOf(localSong)
			),
			tracks = listOf(
				AurralAlbumRecoveryTrack(
					id = "aurral-15",
					title = "パラダイムシフト",
					recordingMbid = "55ceca99-a43f-4dfb-8711-b27bd6dbecf2",
					trackNumber = 15
				),
				AurralAlbumRecoveryTrack(
					id = "aurral-16",
					title = "名誉のファンファーレ",
					recordingMbid = "other-mbid",
					trackNumber = 16
				)
			)
		)

		assertEquals(localSong, rows[0].localSong)
		assertEquals(AurralOwnershipStatus.Owned, rows[0].ownershipStatus)
		assertNull(rows[1].localSong)
		assertEquals(AurralOwnershipStatus.Missing, rows[1].ownershipStatus)
	}

	@Test
	fun recoveryRowsMatchNormalSameLanguageTracksByNumberTitleAndDuration() {
		val localSong = song(
			title = "A Normal Track",
			trackNumber = 3,
			discNumber = 1,
			durationSeconds = 182
		)

		val rows = aurralAlbumRecoveryRows(
			album = album(name = "Normal Album", songs = listOf(localSong)),
			tracks = listOf(
				AurralAlbumRecoveryTrack(
					id = "aurral-3",
					title = "A Normal Track",
					discNumber = 1,
					trackNumber = 3,
					durationMs = 181_000
				)
			)
		)

		assertEquals(localSong, rows.single().localSong)
		assertTrue(rows.single().track.previewUrl == null)
	}

	@Test
	fun displayRowsMergeMissingAurralTracksIntoAlbumTrackOrder() {
		val localSong = song(
			title = "Paradigm Shift",
			musicBrainzId = "55ceca99-a43f-4dfb-8711-b27bd6dbecf2",
			trackNumber = 1
		)
		val ownedTrack = AurralAlbumRecoveryTrack(
			id = "aurral-1",
			title = "Paradigm Shift",
			recordingMbid = "55ceca99-a43f-4dfb-8711-b27bd6dbecf2",
			trackNumber = 1
		)
		val missingTrack = AurralAlbumRecoveryTrack(
			id = "aurral-2",
			title = "Missing Theme",
			trackNumber = 2
		)

		val rows = aurralAlbumDisplayRows(
			album = album(
				name = "Final Fantasy XIII-2 Original Soundtrack",
				songs = listOf(localSong)
			),
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = ownedTrack,
					localSong = localSong,
					ownershipStatus = AurralOwnershipStatus.Owned
				),
				AurralAlbumRecoveryTrackRow(
					track = missingTrack,
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				)
			)
		)

		assertEquals(listOf("Paradigm Shift", "Missing Theme"), rows.map { it.title })
		assertEquals(localSong, rows[0].localSong)
		assertEquals(AurralOwnershipStatus.Owned, rows[0].ownershipStatus)
		assertNull(rows[1].localSong)
		assertEquals(AurralOwnershipStatus.Missing, rows[1].ownershipStatus)
	}

	@Test
	fun displayRowsDoNotAppendLocalOnlySongsWhenAurralHasPartialAlbumData() {
		val ownedSong = song(title = "Owned", trackNumber = 1)
		val localOnlySong = song(title = "Local Only", trackNumber = 2)
		val rows = aurralAlbumDisplayRows(
			album = album(name = "Album", songs = listOf(ownedSong, localOnlySong)),
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-owned",
						title = "Owned",
						trackNumber = 1
					),
					localSong = ownedSong,
					ownershipStatus = AurralOwnershipStatus.Owned
				)
			)
		)

		assertEquals(listOf("Owned"), rows.map { it.title })
		assertEquals(ownedSong, rows.single().localSong)
		assertEquals(AurralOwnershipStatus.Owned, rows.single().ownershipStatus)
	}

	@Test
	fun displayRowsDoNotAppendLocalOnlyDuplicatesWhenAurralTracksExist() {
		val localSong = song(
			id = "song-test-drive-primary",
			title = "Test Drive",
			musicBrainzId = "recording-test-drive",
			trackNumber = 1,
			durationSeconds = 164
		)
		val duplicateLocalSong = song(
			id = "song-test-drive-duplicate",
			title = "Test Drive",
			musicBrainzId = null,
			trackNumber = 2,
			durationSeconds = 164
		)
		val rows = aurralAlbumDisplayRows(
			album = album(
				name = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
				songs = listOf(localSong, duplicateLocalSong)
			),
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-test-drive",
						title = "Test Drive",
						recordingMbid = "recording-test-drive",
						trackNumber = 1,
						durationMs = 164_000
					),
					localSong = localSong,
					ownershipStatus = AurralOwnershipStatus.Owned
				)
			)
		)

		assertEquals(listOf("Test Drive"), rows.map { it.title })
		assertEquals(localSong, rows.single().localSong)
	}

	@Test
	fun displayRowsDoNotPlaceLocalOnlySongsIntoAurralTrackNumberGaps() {
		val localOnlySong = song(
			title = "Ori, Lost In the Storm (feat. Aeralie Brighton)",
			trackNumber = 1
		)

		val rows = aurralAlbumDisplayRows(
			album = album(
				name = "Ori and the Blind Forest (Original Soundtrack)",
				songs = listOf(localOnlySong)
			),
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-1",
						title = "Main Theme - Definitive Edition",
						trackNumber = 1
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				),
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-3",
						title = "Naru, Embracing the Light",
						trackNumber = 3
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				),
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-4",
						title = "The Blinded Forest",
						trackNumber = 4
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				)
			)
		)

		assertEquals(
			listOf(
				"Main Theme - Definitive Edition",
				"Naru, Embracing the Light",
				"The Blinded Forest"
			),
			rows.map { it.title }
		)
		assertEquals(listOf(1, 3, 4), rows.map { it.trackNumber })
		assertNull(rows[0].localSong)
		assertNull(rows[1].localSong)
		assertNull(rows[2].localSong)
		assertEquals(AurralOwnershipStatus.Missing, rows[0].ownershipStatus)
		assertEquals(AurralOwnershipStatus.Missing, rows[1].ownershipStatus)
		assertEquals(AurralOwnershipStatus.Missing, rows[2].ownershipStatus)
	}

	@Test
	fun displayRowsShareDiscKeyWhenAurralRowsOmitDiscNumber() {
		val localSong = song(
			title = "Ori, Lost In the Storm (feat. Aeralie Brighton)",
			trackNumber = 1,
			discNumber = 1
		)

		val rows = aurralAlbumDisplayRows(
			album = album(
				name = "Ori and the Blind Forest (Original Soundtrack)",
				songs = listOf(localSong)
			),
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-1",
						title = "Main Theme - Definitive Edition",
						trackNumber = 1
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				),
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-3",
						title = "Naru, Embracing the Light",
						trackNumber = 3
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				)
			)
		)

		assertEquals(listOf(1, 1), rows.map { aurralAlbumDisplayDiscKey(it) })
		assertEquals(1, rows.groupBy(::aurralAlbumDisplayDiscKey).size)
	}

	@Test
	fun displayRowKeyPrefersCanonicalAurralIdentityBeforeLocalSong() {
		val row = AurralAlbumDisplayRow(
			track = AurralAlbumRecoveryTrack(
				id = "aurral-track-1",
				title = "Test Drive",
				recordingMbid = "recording-1",
				discNumber = 1,
				trackNumber = 1
			),
			localSong = song(
				id = "local-duplicate",
				title = "Test Drive",
				trackNumber = 1
			),
			ownershipStatus = AurralOwnershipStatus.Owned,
			title = "Test Drive",
			artistName = "John Powell",
			discNumber = 1,
			trackNumber = 1,
			durationMs = null,
			previewUrl = null
		)

		assertEquals("aurral:aurral-track-1", aurralAlbumDisplayRowKey(row))
	}

	@Test
	fun recoveryRowsInferDiscKeysWhenAurralTrackNumbersResetWithoutDiscNumbers() {
		val localSong = song(
			title = "Dearly Beloved",
			trackNumber = 1,
			discNumber = 1
		)
		val recoveryRows = aurralAlbumRecoveryRows(
			album = album(
				name = "Kingdom Hearts II Original Soundtrack",
				songs = listOf(localSong)
			),
			tracks = listOf(
				AurralAlbumRecoveryTrack(
					id = "aurral-1",
					title = "Dearly Beloved",
					trackNumber = 1
				),
				AurralAlbumRecoveryTrack(
					id = "aurral-51",
					title = "Bounce-O-Rama (Speed Up Ver.)",
					trackNumber = 51
				),
				AurralAlbumRecoveryTrack(
					id = "aurral-52",
					title = "Isn't It Lovely?",
					trackNumber = 1
				),
				AurralAlbumRecoveryTrack(
					id = "aurral-53",
					title = "Let's Sing and Dance!",
					trackNumber = 2
				)
			)
		)

		val displayRows = aurralAlbumDisplayRows(
			album = album(
				name = "Kingdom Hearts II Original Soundtrack",
				songs = listOf(localSong)
			),
			recoveryRows = recoveryRows
		)

		assertEquals(
			listOf(
				"Dearly Beloved",
				"Bounce-O-Rama (Speed Up Ver.)",
				"Isn't It Lovely?",
				"Let's Sing and Dance!"
			),
			displayRows.map { it.title }
		)
		assertEquals(listOf(1, 1, 2, 2), displayRows.map { aurralAlbumDisplayDiscKey(it) })
		assertEquals(listOf(1, 51, 1, 2), displayRows.map { it.trackNumber })
		assertEquals(localSong, displayRows.first().localSong)
	}

	@Test
	fun displayTrackNumberLabelUsesPerDiscNumberWhenGroupedByDisc() {
		val row = AurralAlbumDisplayRow(
			track = null,
			localSong = null,
			ownershipStatus = AurralOwnershipStatus.Missing,
			title = "Isn't It Lovely?",
			artistName = null,
			discNumber = 2,
			trackNumber = 1,
			durationMs = null,
			previewUrl = null
		)

		assertEquals(
			"1",
			aurralAlbumDisplayTrackNumberLabel(
				row = row,
				index = 0,
				isGroupedByDisc = true
			)
		)
	}

	@Test
	fun displayTrackNumberLabelKeepsDiscPrefixWhenRowsAreFlattened() {
		val row = AurralAlbumDisplayRow(
			track = null,
			localSong = null,
			ownershipStatus = AurralOwnershipStatus.Missing,
			title = "Isn't It Lovely?",
			artistName = null,
			discNumber = 2,
			trackNumber = 1,
			durationMs = null,
			previewUrl = null
		)

		assertEquals(
			"2.1",
			aurralAlbumDisplayTrackNumberLabel(
				row = row,
				index = 0,
				isGroupedByDisc = false
			)
		)
	}

	@Test
	fun displayRowsKeepAurralTrackArtistForMissingRows() {
		val rows = aurralAlbumDisplayRows(
			album = album(
				name = "Ori and the Blind Forest (Original Soundtrack)",
				songs = emptyList()
			),
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-1",
						title = "Main Theme - Definitive Edition",
						artistName = "Gareth Coker",
						trackNumber = 1
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				)
			)
		)

		assertEquals("Gareth Coker", rows.single().artistName)
		assertEquals(AurralOwnershipStatus.Missing, rows.single().ownershipStatus)
	}

	@Test
	fun headerActionAllowsAcquisitionWhenPartialAlbumStillHasMissingTracks() {
		val status = aurralAlbumHeaderActionStatus(
			matchStatus = AurralOwnershipStatus.Partial,
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-1",
						title = "Missing",
						trackNumber = 1
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Missing
				),
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-2",
						title = "Owned",
						trackNumber = 2
					),
					localSong = song(title = "Owned", trackNumber = 1),
					ownershipStatus = AurralOwnershipStatus.Owned
				)
			)
		)

		assertEquals(AurralOwnershipStatus.Missing, status)
	}

	@Test
	fun headerActionKeepsPendingStateWhenRowsAreRequestedButNoneAreMissing() {
		val status = aurralAlbumHeaderActionStatus(
			matchStatus = AurralOwnershipStatus.Partial,
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-1",
						title = "Requested",
						trackNumber = 1
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Requested
				)
			)
		)

		assertEquals(AurralOwnershipStatus.Requested, status)
	}

	@Test
	fun headerActionKeepsFailedStateWhenRowsFailedButNoneAreMissing() {
		val status = aurralAlbumHeaderActionStatus(
			matchStatus = AurralOwnershipStatus.Partial,
			recoveryRows = listOf(
				AurralAlbumRecoveryTrackRow(
					track = AurralAlbumRecoveryTrack(
						id = "aurral-1",
						title = "Failed",
						trackNumber = 1
					),
					localSong = null,
					ownershipStatus = AurralOwnershipStatus.Failed
				)
			)
		)

		assertEquals(AurralOwnershipStatus.Failed, status)
	}

	@Test
	fun displayRowsFallbackToPlainLocalRowsWithoutAurralRecovery() {
		val localSong = song(title = "Only Local", trackNumber = 1)
		val rows = aurralAlbumDisplayRows(
			album = album(name = "Album", songs = listOf(localSong)),
			recoveryRows = emptyList()
		)

		assertEquals(listOf("Only Local"), rows.map { it.title })
		assertEquals(localSong, rows.single().localSong)
		assertNull(rows.single().ownershipStatus)
	}

	@Test
	fun albumPageStateUsesLocalFallbackWhenAurralIsDisabled() {
		val state = aurralAlbumPageState(
			aurralEnabled = false,
			loading = false,
			match = null,
			rows = emptyList(),
			candidates = emptyList(),
			lookupFailed = false
		)

		assertEquals(AurralAlbumPageSource.LocalFallback, state.source)
		assertTrue(state.candidates.isEmpty())
	}

	@Test
	fun albumPageStateMarksResolvedWhenMatchExists() {
		val match = aurralAlbum(
			id = "release-group",
			title = "How to Train Your Dragon",
			artistName = "John Powell"
		)
		val state = aurralAlbumPageState(
			aurralEnabled = true,
			loading = false,
			match = match,
			rows = emptyList(),
			candidates = listOf(match),
			lookupFailed = false
		)

		assertEquals(AurralAlbumPageSource.AurralResolved, state.source)
		assertEquals(match, state.match)
		assertTrue(state.candidates.isEmpty())
	}

	@Test
	fun albumPageStateMarksAmbiguousWhenCandidatesExistWithoutConfidentMatch() {
		val candidate = aurralAlbum(
			id = "candidate",
			title = "How to Train Your Dragon",
			artistName = "John Powell"
		)
		val state = aurralAlbumPageState(
			aurralEnabled = true,
			loading = false,
			match = null,
			rows = emptyList(),
			candidates = listOf(candidate),
			lookupFailed = false
		)

		assertEquals(AurralAlbumPageSource.AurralAmbiguous, state.source)
		assertEquals(listOf(candidate), state.candidates)
	}

	@Test
	fun albumPageStateMarksUnavailableWhenLookupFailsWithoutCache() {
		val state = aurralAlbumPageState(
			aurralEnabled = true,
			loading = false,
			match = null,
			rows = emptyList(),
			candidates = emptyList(),
			lookupFailed = true
		)

		assertEquals(AurralAlbumPageSource.AurralUnavailable, state.source)
	}

	@Test
	fun albumHeaderProjectionUsesAurralMatchWhenResolved() {
		val album = album(
			name = "Local Title",
			artistName = "Local Artist",
			year = 2009
		)
		val match = aurralAlbum(
			id = "release-group",
			title = "How to Train Your Dragon",
			artistName = "John Powell",
			releaseDate = "2010-03-23",
			primaryType = "Album",
			secondaryTypes = listOf("Soundtrack")
		).copy(coverUrl = "https://aurral.example/covers/dragon.jpg")
		val projection = aurralAlbumHeaderProjection(
			album = album,
			pageState = aurralAlbumPageState(
				aurralEnabled = true,
				loading = false,
				match = match,
				rows = emptyList(),
				candidates = emptyList(),
				lookupFailed = false
			)
		)

		assertEquals("How to Train Your Dragon", projection.title)
		assertEquals("John Powell", projection.artistName)
		assertEquals("https://aurral.example/covers/dragon.jpg", projection.coverUrl)
		assertEquals("Album • Soundtrack • 2010", projection.detail)
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
		artistName: String = "Artist",
		year: Int? = 2011,
		songs: List<DomainSong> = emptyList()
	) = DomainAlbum(
		id = "album",
		name = name,
		artistName = artistName,
		artistId = "artist",
		year = year,
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
		songs = songs
	)

	private fun aurralAlbum(
		id: String = "id",
		title: String,
		artistName: String = "Artist",
		releaseDate: String? = null,
		primaryType: String? = null,
		secondaryTypes: List<String> = emptyList()
	) = AurralAlbumSearchItem(
		id = id,
		title = title,
		artistName = artistName,
		artistMbid = "artist-mbid",
		releaseDate = releaseDate,
		primaryType = primaryType,
		secondaryTypes = secondaryTypes
	)

	private fun song(
		id: String? = null,
		title: String,
		musicBrainzId: String? = null,
		trackNumber: Int? = null,
		discNumber: Int? = null,
		durationSeconds: Int = 180
	) = DomainSong(
		id = id ?: "song-$title",
		title = title,
		artistName = "Song Artist",
		artistId = "song-artist",
		albumTitle = "Album",
		albumId = "album",
		parentId = "album",
		comment = null,
		trackNumber = trackNumber,
		discNumber = discNumber,
		isrc = emptyList(),
		year = 2011,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = durationSeconds.seconds,
		bpm = null,
		contributors = emptyList(),
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
		musicBrainzId = musicBrainzId,
		explicitStatus = DomainExplicitStatus.Unknown
	)
}
