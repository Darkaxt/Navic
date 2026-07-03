package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWhispersyncPlaybackPolicyTest {
	@Test
	fun playCommandSeeksToCurrentVisibleTextRangeBeforeStartingAudio() {
		val commands = readerWhispersyncPlaybackCommandsForUserRequest(
			playbackPlan = testPlaybackPlan(),
			session = ReaderWhispersyncSessionState(
				sidecar = testSidecar(),
				visibleTextRange = ReaderWhispersyncVisibleTextRange(
					textHref = "EPUB/Text/chapter-01.xhtml",
					visibleStart = 110,
					visibleEnd = 150
				)
			),
			command = ReaderReadaloudPlaybackCommand.Play
		)

		assertEquals(
			listOf(
				ReaderReadaloudPlaybackCommand.SeekToTrack(trackIndex = 0, positionMs = 12_000L),
				ReaderReadaloudPlaybackCommand.Play
			),
			commands
		)
	}

	@Test
	fun playCommandDoesNotSeekWhenSyncIsDisabledOrNoVisibleRangeExists() {
		assertEquals(
			listOf(ReaderReadaloudPlaybackCommand.Play),
			readerWhispersyncPlaybackCommandsForUserRequest(
				playbackPlan = testPlaybackPlan(),
				session = ReaderWhispersyncSessionState(
					sidecar = testSidecar(),
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = "EPUB/Text/chapter-01.xhtml",
						visibleStart = 110,
						visibleEnd = 150
					),
					sync = ReaderWhispersyncSyncState(syncEnabled = false)
				),
				command = ReaderReadaloudPlaybackCommand.Play
			)
		)
		assertEquals(
			listOf(ReaderReadaloudPlaybackCommand.Play),
			readerWhispersyncPlaybackCommandsForUserRequest(
				playbackPlan = testPlaybackPlan(),
				session = ReaderWhispersyncSessionState(sidecar = testSidecar()),
				command = ReaderReadaloudPlaybackCommand.Play
			)
		)
	}

	@Test
	fun nonPlayCommandsPassThroughWithoutVisibleTextSeek() {
		assertEquals(
			listOf(ReaderReadaloudPlaybackCommand.Pause),
			readerWhispersyncPlaybackCommandsForUserRequest(
				playbackPlan = testPlaybackPlan(),
				session = ReaderWhispersyncSessionState(
					sidecar = testSidecar(),
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = "EPUB/Text/chapter-01.xhtml",
						visibleStart = 110,
						visibleEnd = 150
					)
				),
				command = ReaderReadaloudPlaybackCommand.Pause
			)
		)
	}

	@Test
	fun readerExitPausesOnlyActiveWhispersyncPlayback() {
		val playing = ReaderReadaloudPlaybackUiState(
			isAvailable = true,
			isPlaying = true,
			audioResource = "Audio/chapter01.m4b",
			positionMs = 12_000L
		)

		assertTrue(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = true,
				playbackState = playing
			)
		)
		assertFalse(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = false,
				playbackState = playing
			)
		)
		assertFalse(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = true,
				playbackState = playing.copy(isPlaying = false)
			)
		)
		assertFalse(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = true,
				playbackState = null
			)
		)
	}

	@Test
	fun readerExitPausesAfterReaderIssuedPlayEvenBeforePlaybackStateCatchesUp() {
		assertTrue(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = true,
				playbackState = null,
				playbackStartedFromReader = true
			)
		)
		assertTrue(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = true,
				playbackState = ReaderReadaloudPlaybackUiState(
					isAvailable = true,
					isPlaying = false,
					audioResource = "Audio/chapter01.m4b",
					positionMs = 0L
				),
				playbackStartedFromReader = true
			)
		)
		assertFalse(
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = true,
				playbackState = null,
				playbackStartedFromReader = false
			)
		)
	}

	private fun testPlaybackPlan(): ReadaloudPlaybackPlan =
		ReadaloudPlaybackPlan(
			sessionId = "session-1",
			title = "Test audiobook",
			kind = ReaderPublicationKind.Readaloud,
			mediaItems = listOf(
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:chapter-01",
					uri = "https://bindery.test/audio/chapter-01.m4b",
					title = "Chapter 1",
					subtitle = null,
					artist = "Narrator",
					albumTitle = "Test audiobook",
					albumArtist = "Author",
					trackNumber = 1,
					discNumber = null,
					requestHeaders = emptyMap(),
					resourceKey = "Audio/chapter01.m4b"
				)
			),
			startTrackIndex = 0,
			startPositionMs = 0L,
			playbackSpeed = 1f
		)

	private fun testSidecar(): WhispersyncSidecar =
		WhispersyncSidecar(
			artifactId = "artifact-1",
			timeline = WhispersyncTimeline(
				segments = listOf(
					WhispersyncSegment(
						id = "seg-1",
						audioResource = "Audio/chapter01.m4b",
						audioTrackIndex = 0,
						startMs = 12_000L,
						endMs = 16_000L,
						textHref = "EPUB/Text/chapter-01.xhtml",
						textStart = 100,
						textEnd = 180,
						label = "Chapter 1 / sentence 2"
					)
				)
			)
		)
}
