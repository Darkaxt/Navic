package paige.navic.reader

import paige.navic.domain.repositories.BinderyAudioMetadata
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceMetadata
import paige.navic.domain.repositories.BinderySourceReleaseMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadaloudModelsTest {
	@Test
	fun binderyReadingOrderBuildsReadaloudAudioSessionWithStructuredLabels() {
		val manifest = BinderyManifest(
			id = "urn:bindery:book:3693",
			title = "Alcatraz versus the Evil Librarians",
			author = "Brandon Sanderson"
		)
		val session = readaloudAudioSessionFromBindery(
			manifest = manifest,
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3693/resources/audio-1",
					title = "Part 01",
					type = "audio/mpeg",
					durationSeconds = 3763.592,
					metadata = BinderyResourceMetadata(
						kind = "audio",
						format = "audiobook",
						artifactType = "source",
						resourceKey = "audio-001",
						bookFileId = "file-694",
						relativePath = "Audio/Part 01.mp3",
						durationMs = 3763592,
						deliveryPolicy = "proxy",
						origin = "local",
						language = "eng",
						version = "Macmillan Audio - Unabridged",
						chapterLabel = "Chapter 1",
						sectionLabel = "Opening",
						trackNumber = 1,
						discNumber = 1,
						narrator = "Michael Kramer",
						author = "Brandon Sanderson",
						sourceProvider = "audible",
						findingId = "87",
						findingHref = "/opds/findings/87",
						audio = BinderyAudioMetadata(
							codec = "mp3",
							bitrateKbps = 128,
							sampleRateHz = 44100,
							channels = 2,
							qualityLabel = "High"
						),
						sourceRelease = BinderySourceReleaseMetadata(
							provider = "Audible",
							sourceUrl = "https://example.com/audible/alcatraz",
							narrator = "Michael Kramer",
							readBy = "Michael Kramer",
							edition = "Unabridged",
							format = "MP3"
						)
					)
				)
			),
			kind = ReaderPublicationKind.Readaloud
		)

		assertEquals("urn:bindery:book:3693", session.id)
		assertEquals("Alcatraz versus the Evil Librarians", session.title)
		assertEquals("Brandon Sanderson", session.author)
		assertEquals("Michael Kramer", session.narrator)
		assertEquals(ReaderPublicationKind.Readaloud, session.kind)

		val track = session.tracks.single()
		assertEquals("audio-001", track.resourceKey)
		assertEquals("file-694", track.bookFileId)
		assertEquals("/opds/books/3693/resources/audio-1", track.href)
		assertEquals("Chapter 1", track.displayTitle)
		assertEquals("audiobook", track.format)
		assertEquals("source", track.artifactType)
		assertEquals("proxy", track.deliveryPolicy)
		assertEquals("local", track.origin)
		assertEquals("Macmillan Audio - Unabridged", track.version)
		assertEquals("Opening", track.sectionLabel)
		assertEquals(1, track.trackNumber)
		assertEquals(1, track.discNumber)
		assertEquals(3763592, track.durationMs)
		assertEquals("mp3", track.codec)
		assertEquals(128, track.bitrateKbps)
		assertEquals(44100, track.sampleRateHz)
		assertEquals(2, track.channels)
		assertEquals("High", track.qualityLabel)
		assertEquals("Audible", track.sourceProviderLabel)
		assertEquals("Unabridged / MP3", track.sourceReleaseLabel)
		assertEquals("https://example.com/audible/alcatraz", track.sourceUrl)
		assertEquals("87", track.findingId)
		assertEquals("/opds/findings/87", track.findingHref)
		assertEquals("Michael Kramer / High / Audible", track.subtitleLabel)

		val descriptor = track.toReadaloudMediaItemDescriptor(
			sessionTitle = session.title,
			sessionAuthor = session.author,
			sessionNarrator = session.narrator
		)
		assertEquals("file-694", descriptor.bookFileId)
		assertEquals("proxy", descriptor.deliveryPolicy)
		assertEquals("local", descriptor.origin)
		assertEquals("Macmillan Audio - Unabridged", descriptor.version)
		assertEquals("87", descriptor.findingId)
		val extras = descriptor.toReadaloudMediaExtras()
		assertEquals("file-694", extras.bookFileId)
		assertEquals("/opds/findings/87", extras.findingHref)
	}

	@Test
	fun binderyReadingOrderUsesCurrentSourceReleaseEditionTypeForLabels() {
		val session = readaloudAudioSessionFromBindery(
			manifest = BinderyManifest(
				id = "urn:bindery:book:3816",
				title = "The Hobbit",
				author = "J.R.R. Tolkien"
			),
			readingOrder = listOf(
				BinderyReadingOrderItem(
					href = "/opds/books/3816/resources/audio-1",
					title = "01 - An Unexpected Party.mp3",
					type = "audio/mpeg",
					metadata = BinderyResourceMetadata(
						resourceKey = "audio-c310a962a70802eb2e65",
						narrator = "Andy Serkis",
						sourceRelease = BinderySourceReleaseMetadata(
							provider = "audiobookbay",
							title = "The Hobbit - J. R. R. Tolkien Audiobook MP3 [Unabridged]",
							format = "mp3",
							bitrate = "128 kbps",
							editionType = "unabridged",
							narrator = "Andy Serkis"
						)
					)
				)
			),
			kind = ReaderPublicationKind.Readaloud
		)

		val track = session.tracks.single()
		assertEquals("audiobookbay", track.sourceProviderLabel)
		assertEquals("unabridged / mp3", track.sourceReleaseLabel)
		assertEquals("Andy Serkis / audiobookbay", track.subtitleLabel)
	}

	@Test
	fun playbackPlanNormalizesSpeedStartPositionAndBuildsReadaloudDescriptors() {
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
					href = "https://bindery.local/audio-1.mp3",
					title = "Part 01",
					displayTitle = "Chapter 1",
					durationMs = 3_763_592
				),
				ReadaloudAudioTrack(
					id = "audio-002",
					resourceKey = "audio-002",
					href = "https://bindery.local/audio-2.mp3",
					title = "Part 02",
					displayTitle = "Chapter 2",
					durationMs = 3_000
				)
			)
		)

		val plan = session.toReadaloudPlaybackPlan(
			requestHeaders = mapOf("X-Api-Key" to "secret"),
			startTrackIndex = 4,
			startPositionMs = -500,
			playbackSpeed = 5f
		)

		assertEquals("urn:bindery:book:3693", plan.sessionId)
		assertEquals(1, plan.startTrackIndex)
		assertEquals(0, plan.startPositionMs)
		assertEquals(3f, plan.playbackSpeed)
		assertEquals(ReaderPublicationKind.Readaloud, plan.kind)
		assertEquals("readaloud:audio-001", plan.mediaItems.first().mediaId)
		assertEquals("readaloud:audio-002", plan.mediaItems.last().mediaId)
		assertEquals(mapOf("X-Api-Key" to "secret"), plan.mediaItems.last().requestHeaders)
	}

	@Test
	fun playbackPlanExposesReaderMetadataLabelsForActiveTrack() {
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
					qualityLabel = "128 kbps",
					sourceProviderLabel = "AudioBook Bay",
					sourceReleaseLabel = "Unabridged / MP3",
					sourceUrl = "https://example.com/audiobook-bay/hobbit",
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

		val labels = plan.metadataLabelsForPlaybackPosition(
			ReadaloudPlaybackPosition(
				sessionId = "urn:bindery:book:3816",
				trackIndex = 0,
				mediaId = "readaloud:audio-1",
				positionMs = 1200L,
				durationMs = 12345L,
				isPlaying = true,
				playbackSpeed = 1f
			)
		)

		assertEquals("Chapter 1", labels?.chapterLabel)
		assertEquals("An Unexpected Party", labels?.sectionLabel)
		assertEquals("Andy Serkis", labels?.narratorLabel)
		assertEquals("128 kbps", labels?.qualityLabel)
		assertEquals("AudioBook Bay", labels?.sourceProviderLabel)
		assertEquals("Unabridged / MP3", labels?.sourceReleaseLabel)
		assertEquals("https://example.com/audiobook-bay/hobbit", labels?.sourceUrlLabel)
		assertEquals("mp3 / 128 kbps / 44.1 kHz / stereo", labels?.formatLabel)
	}

	@Test
	fun playbackMetadataFormatLabelsUseSampleRateAndChannelLayout() {
		val plan = ReadaloudPlaybackPlan(
			sessionId = "urn:bindery:book:3816",
			title = "The Hobbit",
			kind = ReaderPublicationKind.Readaloud,
			mediaItems = listOf(
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:audio-1",
					uri = "https://bindery.local/opds/books/3816/resources/audio-1",
					title = "Chapter 1",
					subtitle = null,
					artist = null,
					albumTitle = "The Hobbit",
					albumArtist = null,
					trackNumber = 1,
					discNumber = 1,
					requestHeaders = emptyMap(),
					codec = "opus",
					bitrateKbps = 96,
					sampleRateHz = 48_000,
					channels = 1
				),
				ReadaloudMediaItemDescriptor(
					mediaId = "readaloud:audio-2",
					uri = "https://bindery.local/opds/books/3816/resources/audio-2",
					title = "Chapter 2",
					subtitle = null,
					artist = null,
					albumTitle = "The Hobbit",
					albumArtist = null,
					trackNumber = 2,
					discNumber = 1,
					requestHeaders = emptyMap(),
					codec = "flac",
					sampleRateHz = 96_000,
					channels = 6
				)
			),
			startTrackIndex = 0,
			startPositionMs = 0L,
			playbackSpeed = 1f
		)

		val monoLabels = plan.metadataLabelsForPlaybackPosition(
			ReadaloudPlaybackPosition(
				sessionId = "urn:bindery:book:3816",
				trackIndex = 0,
				mediaId = "readaloud:audio-1",
				positionMs = 0L,
				durationMs = null,
				isPlaying = true,
				playbackSpeed = 1f
			)
		)
		val surroundLabels = plan.metadataLabelsForPlaybackPosition(
			ReadaloudPlaybackPosition(
				sessionId = "urn:bindery:book:3816",
				trackIndex = 1,
				mediaId = "readaloud:audio-2",
				positionMs = 0L,
				durationMs = null,
				isPlaying = true,
				playbackSpeed = 1f
			)
		)

		assertEquals("opus / 96 kbps / 48 kHz / mono", monoLabels?.formatLabel)
		assertEquals("flac / 96 kHz / 6 ch", surroundLabels?.formatLabel)
	}
}
