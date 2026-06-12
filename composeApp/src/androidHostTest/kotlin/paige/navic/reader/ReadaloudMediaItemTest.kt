package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadaloudMediaItemTest {
	@Test
	fun mediaItemDescriptorIncludesReadaloudMetadataLabelsWithoutUsingMusicQueueModels() {
		val track = ReadaloudAudioTrack(
			id = "audio-001",
			resourceKey = "audio-001",
			href = "https://bindery.local/opds/books/3693/resources/audio-1",
			title = "Part 01",
			displayTitle = "Chapter 1",
			sectionLabel = "Opening",
			trackNumber = 1,
			discNumber = 1,
			narrator = "Michael Kramer",
			author = "Brandon Sanderson",
			durationMs = 3763592,
			codec = "mp3",
			bitrateKbps = 128,
			sampleRateHz = 44100,
			channels = 2,
			qualityLabel = "High",
			sourceProviderLabel = "Audible",
			sourceReleaseLabel = "Unabridged / MP3",
			sourceUrl = "https://example.com/audible/alcatraz"
		)

		val descriptor = track.toReadaloudMediaItemDescriptor(
			sessionTitle = "Alcatraz versus the Evil Librarians",
			sessionAuthor = "Brandon Sanderson",
			sessionNarrator = "Michael Kramer",
			requestHeaders = mapOf("X-Api-Key" to "secret")
		)

		assertEquals("readaloud:audio-001", descriptor.mediaId)
		assertEquals("https://bindery.local/opds/books/3693/resources/audio-1", descriptor.uri)
		assertEquals(mapOf("X-Api-Key" to "secret"), descriptor.requestHeaders)
		assertEquals("Chapter 1", descriptor.title)
		assertEquals("Michael Kramer", descriptor.artist)
		assertEquals("Alcatraz versus the Evil Librarians", descriptor.albumTitle)
		assertEquals("Brandon Sanderson", descriptor.albumArtist)
		assertEquals("Opening", descriptor.subtitle)
		assertEquals(1, descriptor.trackNumber)
		assertEquals(1, descriptor.discNumber)
		assertEquals("Unabridged / MP3", descriptor.sourceReleaseLabel)
		assertEquals("https://example.com/audible/alcatraz", descriptor.sourceUrl)
	}

	@Test
	fun playbackPlanMediaItemsKeepNarratorChapterAndQualityLabelsForMedia3Queue() {
		val session = ReadaloudAudioSession(
			id = "urn:bindery:book:3693",
			title = "Alcatraz versus the Evil Librarians",
			author = "Brandon Sanderson",
			narrator = "Michael Kramer",
			kind = ReaderPublicationKind.Readaloud,
			tracks = listOf(
				ReadaloudAudioTrack(
					id = "audio-001",
					resourceKey = "audio-001",
					href = "https://bindery.local/opds/books/3693/resources/audio-1",
					title = "Part 01",
					displayTitle = "Chapter 1",
					sectionLabel = "Opening",
					trackNumber = 1,
					discNumber = 1,
					narrator = "Michael Kramer",
					author = "Brandon Sanderson",
					durationMs = 3763592,
					codec = "mp3",
					bitrateKbps = 128,
					sampleRateHz = 44100,
					channels = 2,
					qualityLabel = "High",
					sourceProviderLabel = "Audible",
					sourceReleaseLabel = "Unabridged / MP3",
					sourceUrl = "https://example.com/audible/alcatraz"
				)
			)
		)

		val descriptor = session.toReadaloudPlaybackPlan()
			.mediaItems
			.single()

		assertEquals("Chapter 1", descriptor.title)
		assertEquals("Opening", descriptor.subtitle)
		assertEquals("Michael Kramer", descriptor.artist)
		assertEquals("Alcatraz versus the Evil Librarians", descriptor.albumTitle)
		assertEquals("Brandon Sanderson", descriptor.albumArtist)
		assertEquals("audio-001", descriptor.resourceKey)
		assertEquals("High", descriptor.qualityLabel)
		assertEquals("Audible", descriptor.sourceProviderLabel)
		assertEquals("Unabridged / MP3", descriptor.sourceReleaseLabel)
		assertEquals("https://example.com/audible/alcatraz", descriptor.sourceUrl)
		assertEquals("mp3", descriptor.codec)
		assertEquals(128, descriptor.bitrateKbps)
		assertEquals(44100L, descriptor.sampleRateHz)
		assertEquals(2, descriptor.channels)
		assertEquals(3763592L, descriptor.durationMs)
	}

	@Test
	fun mediaItemExtrasPreserveReadaloudSpecificMetadataLabels() {
		val descriptor = ReadaloudAudioTrack(
			id = "audio-001",
			resourceKey = "audio-001",
			href = "file:///cache/storyteller/audio/chapter1.mp3",
			title = "Part 01",
			displayTitle = "Storyteller Chapter 1",
			sectionLabel = "An Unexpected Party",
			trackNumber = 7,
			discNumber = 1,
			narrator = "Andy Serkis",
			author = "J.R.R. Tolkien",
			durationMs = 3763592,
			codec = "mp3",
			bitrateKbps = 128,
			sampleRateHz = 44100,
			channels = 2,
			qualityLabel = "Studio 128 kbps",
			sourceProviderLabel = "Storyteller",
			sourceReleaseLabel = "Unabridged / MP3",
			sourceUrl = "https://storyteller.local/releases/hobbit"
		).toReadaloudMediaItemDescriptor(
			sessionTitle = "The Hobbit",
			sessionAuthor = "J.R.R. Tolkien",
			sessionNarrator = "Andy Serkis"
		)

		val extras = descriptor.toReadaloudMediaExtras()
		assertEquals("audio-001", extras.resourceKey)
		assertEquals("file:///cache/storyteller/audio/chapter1.mp3", extras.href)
		assertEquals("Storyteller Chapter 1", extras.title)
		assertEquals("Storyteller Chapter 1", extras.chapterLabel)
		assertEquals("An Unexpected Party", extras.sectionLabel)
		assertEquals("Andy Serkis", extras.narrator)
		assertEquals("J.R.R. Tolkien", extras.author)
		assertEquals(7, extras.trackNumber)
		assertEquals(1, extras.discNumber)
		assertEquals(3763592L, extras.durationMs)
		assertEquals("mp3", extras.codec)
		assertEquals(128, extras.bitrateKbps)
		assertEquals(44100L, extras.sampleRateHz)
		assertEquals(2, extras.channels)
		assertEquals("Studio 128 kbps", extras.qualityLabel)
		assertEquals("Storyteller", extras.sourceProvider)
		assertEquals("Unabridged / MP3", extras.sourceRelease)
		assertEquals("https://storyteller.local/releases/hobbit", extras.sourceUrl)
	}
}
