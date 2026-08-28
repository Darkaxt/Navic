package paige.navic.reader

import paige.navic.util.core.AppLogLevel
import paige.navic.util.core.LoggerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadaloudPlaybackDiagnosticsTest {
	@Test
	fun playbackPlanDiagnosticContextDoesNotExposeRequestHeaders() {
		val plan = ReadaloudPlaybackPlan(
			sessionId = "urn:bindery:book:3816",
			title = "The Hobbit",
			kind = ReaderPublicationKind.Readaloud,
			mediaItems = listOf(
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:audio-1",
					uri = "https://bindery.local/opds/books/3816/resources/audio-1",
					title = "Chapter 1",
					subtitle = "An Unexpected Party",
					artist = "Andy Serkis",
					albumTitle = "The Hobbit",
					albumArtist = "J.R.R. Tolkien",
					trackNumber = 1,
					discNumber = 1,
					requestHeaders = mapOf("X-Api-Key" to "secret"),
					resourceKey = "audio-1",
					bookFileId = "633",
					qualityLabel = "128 kbps",
					sourceProviderLabel = "AudioBook Bay",
					sourceReleaseLabel = "private-release-label",
					sourceUrl = "https://example.invalid/private-source",
					codec = "mp3",
					bitrateKbps = 128,
					sampleRateHz = 44100,
					channels = 2,
					durationMs = 12345L
				)
			),
			startTrackIndex = 0,
			startPositionMs = 0L,
			playbackSpeed = 1f
		)

		val event = plan.toReadaloudPlaybackLoadedEvent()

		assertEquals(AppLogLevel.Info, event.level)
		assertEquals("ReadaloudPlayback", event.tag)
		assertTrue(event.message.contains("items=1"))
		assertTrue(event.message.contains("kind=Readaloud"))
		assertFalseContains(event, "urn:bindery:book:3816")
		assertFalseContains(event, "The Hobbit")
		assertFalseContains(event, "readaloud:audio-1")
		assertFalseContains(event, "audio-1")
		assertFalseContains(event, "633")
		assertFalseContains(event, "AudioBook Bay")
		assertFalseContains(event, "private-release-label")
		assertFalseContains(event, "https://")
		assertFalseContains(event, "secret")
		assertFalseContains(event, "X-Api-Key")
	}

	private fun assertFalseContains(event: LoggerEvent, value: String) {
		assertEquals(false, event.message.contains(value), event.message)
	}
}
