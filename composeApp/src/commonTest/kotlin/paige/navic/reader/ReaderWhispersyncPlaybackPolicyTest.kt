package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWhispersyncPlaybackPolicyTest {
	@Test
	fun productionSingleTrackBinderyPlanUsesSidecarTrackIndexWhenAudioHrefDiffers() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = ReadaloudPlaybackPlan(
				sessionId = "audiobook-34",
				title = "Bastille vs. the Evil Librarians",
				kind = ReaderPublicationKind.Readaloud,
				mediaItems = listOf(
					ReadaloudMediaItemDescriptor(
						mediaId = "readaloud:audio-71ad8af54af0d403a1b5",
						uri = "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=633",
						title = "Bastille vs. the Evil Librarians",
						subtitle = null,
						artist = "Ramon De Ocampo / Suzy Jackson",
						albumTitle = "Bastille vs. the Evil Librarians",
						albumArtist = "Brandon Sanderson",
						trackNumber = 1,
						discNumber = null,
						requestHeaders = emptyMap(),
						resourceKey = "audio-71ad8af54af0d403a1b5"
					)
				),
				startTrackIndex = 0,
				startPositionMs = 0L,
				playbackSpeed = 1f
			),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b",
				positionMs = 263_360L,
				segment = WhispersyncSegment(
					audioResourceId = "track-001",
					audioTrackIndex = 0,
					audioResource = "6 Bastille vs. the Evil Librarians/Bastille vs. the Evil Librarians.m4b",
					startMs = 263_360L,
					endMs = 282_920L,
					textHref = "OEBPS/xhtml/Authorforeword.xhtml",
					textStart = 3,
					textEnd = 4851
				)
			)
		)

		val seekCommand = assertIs<ReaderReadaloudPlaybackCommand.SeekToTrack>(command)
		assertEquals(0, seekCommand.trackIndex)
		assertEquals(263_360L, seekCommand.positionMs)
	}

	@Test
	fun visibleRangeSeekTargetBuildsTrackSeekCommandForMatchingAudiobookPlan() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = whispersyncPlaybackPlan(),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "Audio/chapter02.m4b",
				positionMs = 42_000L,
				segment = WhispersyncSegment(
					audioResource = "Audio/chapter02.m4b",
					startMs = 42_000L,
					endMs = 45_000L,
					textHref = "Text/chapter2.xhtml",
					textStart = 10,
					textEnd = 80,
					label = "Chapter two"
				)
			)
		)

		val seekCommand = assertIs<ReaderReadaloudPlaybackCommand.SeekToTrack>(command)
		assertEquals(1, seekCommand.trackIndex)
		assertEquals(42_000L, seekCommand.positionMs)
	}

	@Test
	fun absolutePlaybackUrlsCanMatchRelativeWhispersyncAudioResources() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = whispersyncPlaybackPlan(),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "/opds/books/3816/files/Audio/chapter01.m4b?download=1",
				positionMs = 7_500L,
				segment = WhispersyncSegment(
					audioResource = "/opds/books/3816/files/Audio/chapter01.m4b?download=1",
					startMs = 7_500L,
					endMs = 9_000L,
					textHref = "Text/chapter1.xhtml",
					textStart = 1,
					textEnd = 30,
					label = "Chapter one"
				)
			)
		)

		val seekCommand = assertIs<ReaderReadaloudPlaybackCommand.SeekToTrack>(command)
		assertEquals(0, seekCommand.trackIndex)
		assertEquals(7_500L, seekCommand.positionMs)
	}

	@Test
	fun unmatchedAudioResourceDoesNotSeekTheAudiobookPlayer() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = whispersyncPlaybackPlan(),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "Audio/missing.m4b",
				positionMs = 12_000L,
				segment = WhispersyncSegment(
					audioResource = "Audio/missing.m4b",
					startMs = 12_000L,
					endMs = 14_000L,
					textHref = "Text/chapter3.xhtml",
					textStart = 1,
					textEnd = 40
				)
			)
		)

		assertNull(command)
	}

	@Test
	fun sidecarTrackIndexWinsOverAmbiguousSuffixMatches() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = ReadaloudPlaybackPlan(
				sessionId = "ambiguous-book",
				title = "Ambiguous audiobook",
				kind = ReaderPublicationKind.Readaloud,
				mediaItems = listOf(
					ReadaloudMediaItemDescriptor(
						mediaId = "wrong-track",
						uri = "https://bindery.local/files/wrong-book/chapter.m4b",
						title = "Wrong track",
						subtitle = null,
						artist = "Narrator",
						albumTitle = "Ambiguous audiobook",
						albumArtist = "Author",
						trackNumber = 1,
						discNumber = null,
						requestHeaders = emptyMap(),
						resourceKey = "wrong-book/chapter.m4b"
					),
					ReadaloudMediaItemDescriptor(
						mediaId = "correct-track",
						uri = "https://bindery.local/files/correct-book/chapter.m4b",
						title = "Correct track",
						subtitle = null,
						artist = "Narrator",
						albumTitle = "Ambiguous audiobook",
						albumArtist = "Author",
						trackNumber = 2,
						discNumber = null,
						requestHeaders = emptyMap(),
						resourceKey = "correct-book/chapter.m4b"
					)
				),
				startTrackIndex = 0,
				startPositionMs = 0L,
				playbackSpeed = 1f
			),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "chapter.m4b",
				positionMs = 63_000L,
				segment = WhispersyncSegment(
					audioTrackIndex = 1,
					audioResource = "chapter.m4b",
					startMs = 63_000L,
					endMs = 65_000L,
					textHref = "Text/chapter2.xhtml",
					textStart = 10,
					textEnd = 60
				)
			)
		)

		val seekCommand = assertIs<ReaderReadaloudPlaybackCommand.SeekToTrack>(command)
		assertEquals(1, seekCommand.trackIndex)
		assertEquals(63_000L, seekCommand.positionMs)
	}

	@Test
	fun sidecarTrackIndexWinsOverStaleResourceMatch() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = ReadaloudPlaybackPlan(
				sessionId = "repaired-book",
				title = "Repaired audiobook",
				kind = ReaderPublicationKind.Readaloud,
				mediaItems = listOf(
					ReadaloudMediaItemDescriptor(
						mediaId = "stale-track",
						uri = "https://bindery.local/files/old-release/chapter01.m4b",
						title = "Old release chapter",
						subtitle = null,
						artist = "Narrator",
						albumTitle = "Repaired audiobook",
						albumArtist = "Author",
						trackNumber = 1,
						discNumber = null,
						requestHeaders = emptyMap(),
						resourceKey = "old-release/chapter01.m4b"
					),
					ReadaloudMediaItemDescriptor(
						mediaId = "sidecar-track",
						uri = "https://bindery.local/files/new-release/chapter01.m4b",
						title = "New release chapter",
						subtitle = null,
						artist = "Narrator",
						albumTitle = "Repaired audiobook",
						albumArtist = "Author",
						trackNumber = 2,
						discNumber = null,
						requestHeaders = emptyMap(),
						resourceKey = "new-release/chapter01.m4b"
					)
				),
				startTrackIndex = 0,
				startPositionMs = 0L,
				playbackSpeed = 1f
			),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "old-release/chapter01.m4b",
				positionMs = 91_000L,
				segment = WhispersyncSegment(
					audioTrackIndex = 1,
					audioResource = "old-release/chapter01.m4b",
					startMs = 91_000L,
					endMs = 94_000L,
					textHref = "Text/chapter1.xhtml",
					textStart = 40,
					textEnd = 100
				)
			)
		)

		val seekCommand = assertIs<ReaderReadaloudPlaybackCommand.SeekToTrack>(command)
		assertEquals(1, seekCommand.trackIndex)
		assertEquals(91_000L, seekCommand.positionMs)
	}

	@Test
	fun exactWordSyncTrackIdentityWinsOverLegacyCueTrack() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = whispersyncPlaybackPlan(),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "Audio/chapter02.m4b",
				positionMs = 51_000L,
				segment = WhispersyncSegment(
					audioTrackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					startMs = 50_000L,
					endMs = 53_000L,
					textHref = "Text/chapter2.xhtml"
				),
				audioTrackIndex = 1
			)
		)

		val seekCommand = assertIs<ReaderReadaloudPlaybackCommand.SeekToTrack>(command)
		assertEquals(1, seekCommand.trackIndex)
		assertEquals(51_000L, seekCommand.positionMs)
	}

	@Test
	fun exactWordSyncTrackIdentityFailsClosedOnResourceMismatch() {
		val command = readerWhispersyncPlaybackCommandForSeekTarget(
			playbackPlan = whispersyncPlaybackPlan(),
			seekTarget = WhispersyncAudioSeekTarget(
				audioResource = "Audio/chapter01.m4b",
				positionMs = 51_000L,
				segment = WhispersyncSegment(
					audioTrackIndex = 0,
					audioResource = "Audio/chapter01.m4b",
					startMs = 50_000L,
					endMs = 53_000L,
					textHref = "Text/chapter2.xhtml"
				),
				audioTrackIndex = 1
			)
		)

		assertNull(command)
	}

	@Test
	fun whispersyncControlIsHiddenWhenReaderHasNoSyncedAudio() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(),
			playbackState = null,
			hasConfirmedVisibleCue = false
		)

		assertFalse(control.visible)
		assertNull(control.command)
	}

	@Test
	fun whispersyncControlCanRestartListeningWhenSidecarIsReadyAndPlaybackIsStopped() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				message = ReaderWhispersyncStatusMessage.Ready
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				syncEnabled = false
			),
			hasConfirmedVisibleCue = true
		)

		assertTrue(control.visible)
		assertFalse(control.loading)
		assertTrue(control.crossed)
		assertTrue(control.enabled)
		assertFalse(control.noAudioCueOnPage)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, control.command)
	}

	@Test
	fun whispersyncControlCannotStartStaleAudioWithoutConfirmedVisibleCue() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.NoActiveCue,
				message = ReaderWhispersyncStatusMessage.NoActiveCue
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				syncEnabled = false
			),
			hasConfirmedVisibleCue = false
		)

		assertTrue(control.visible)
		assertFalse(control.loading)
		assertTrue(control.crossed)
		assertFalse(control.enabled)
		assertTrue(control.noAudioCueOnPage)
		assertEquals(
			ReaderWhispersyncPlaybackControlDescription.NoAudioCueOnPage,
			control.contentDescription
		)
		assertNull(control.command)
	}

	@Test
	fun whispersyncControlStaysDisabledUntilOverlayActivationIsConfirmed() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SeekingAudio,
				message = ReaderWhispersyncStatusMessage.SeekingAudio,
				audioResource = "Audio/chapter01.m4b",
				positionMs = 42_000L
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				syncEnabled = true
			),
			hasConfirmedVisibleCue = false
		)

		assertTrue(control.visible)
		assertTrue(control.loading)
		assertTrue(control.crossed)
		assertFalse(control.enabled)
		assertFalse(control.noAudioCueOnPage)
		assertNull(control.command)
	}

	@Test
	fun whispersyncControlKeepsStopAvailableWhileNextCueActivationIsPending() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SeekingAudio,
				message = ReaderWhispersyncStatusMessage.SeekingAudio
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				syncEnabled = true
			),
			hasConfirmedVisibleCue = false
		)

		assertTrue(control.visible)
		assertFalse(control.loading)
		assertFalse(control.crossed)
		assertTrue(control.enabled)
		assertFalse(control.noAudioCueOnPage)
		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, control.command)
	}

	@Test
	fun whispersyncControlShowsPlayingStateAndResetsOnTap() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Playing,
				message = ReaderWhispersyncStatusMessage.Playing
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				syncEnabled = true
			),
			hasConfirmedVisibleCue = false
		)

		assertTrue(control.visible)
		assertFalse(control.loading)
		assertFalse(control.crossed)
		assertTrue(control.enabled)
		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, control.command)
	}

	@Test
	fun whispersyncControlShowsCrossedPausedStateAndPlaysOnTap() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				message = ReaderWhispersyncStatusMessage.Paused
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				syncEnabled = false
			),
			hasConfirmedVisibleCue = true
		)

		assertTrue(control.visible)
		assertFalse(control.loading)
		assertTrue(control.crossed)
		assertTrue(control.enabled)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, control.command)
	}

	private fun whispersyncPlaybackPlan(): ReadaloudPlaybackPlan =
		ReadaloudPlaybackPlan(
			sessionId = "book-3816",
			title = "Whispersync Audiobook",
			kind = ReaderPublicationKind.Readaloud,
			mediaItems = listOf(
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:chapter01",
					uri = "https://bindery.local/opds/books/3816/files/Audio/chapter01.m4b",
					title = "Chapter 1",
					subtitle = null,
					artist = "Narrator",
					albumTitle = "Whispersync Audiobook",
					albumArtist = "Author",
					trackNumber = 1,
					discNumber = null,
					requestHeaders = emptyMap(),
					resourceKey = "Audio/chapter01.m4b"
				),
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:chapter02",
					uri = "https://bindery.local/opds/books/3816/files/Audio/chapter02.m4b",
					title = "Chapter 2",
					subtitle = null,
					artist = "Narrator",
					albumTitle = "Whispersync Audiobook",
					albumArtist = "Author",
					trackNumber = 2,
					discNumber = null,
					requestHeaders = emptyMap(),
					resourceKey = "Audio/chapter02.m4b"
				)
			),
			startTrackIndex = 0,
			startPositionMs = 0L,
			playbackSpeed = 1f
		)
}
