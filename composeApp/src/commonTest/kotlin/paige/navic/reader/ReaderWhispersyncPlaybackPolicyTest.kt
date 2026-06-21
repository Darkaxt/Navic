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
	fun whispersyncControlIsHiddenWhenReaderHasNoSyncedAudio() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(),
			playbackState = null
		)

		assertFalse(control.visible)
		assertNull(control.command)
	}

	@Test
	fun whispersyncControlShowsLoadingWhenSidecarIsReadyButAudioIsNotLoaded() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				label = "Whispersync ready"
			),
			playbackState = null
		)

		assertTrue(control.visible)
		assertTrue(control.loading)
		assertTrue(control.crossed)
		assertFalse(control.enabled)
		assertNull(control.command)
	}

	@Test
	fun whispersyncControlShowsPlayingStateAndPausesOnTap() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Playing,
				label = "Whispersync playing"
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				syncEnabled = true
			)
		)

		assertTrue(control.visible)
		assertFalse(control.loading)
		assertFalse(control.crossed)
		assertTrue(control.enabled)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, control.command)
	}

	@Test
	fun whispersyncControlShowsCrossedPausedStateAndPlaysOnTap() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			),
			playbackState = ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				syncEnabled = false
			)
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
