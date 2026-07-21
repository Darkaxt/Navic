package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class LrcLibLookupPolicyTest {
	@Test
	fun knownLegacyEndpointMigratesInBothDirections() {
		assertEquals(
			"https://lrclib.net/api/search",
			normalizedLrcLibSearchUrl("https://lrclib.net/api/get")
		)
		assertEquals(
			"https://lyrics.example/api/search",
			normalizedLrcLibSearchUrl("https://lyrics.example/api/search")
		)
		assertEquals(
			"https://lyrics.example/api/get",
			lrcLibExactUrl("https://lyrics.example/api/search")
		)
	}

	@Test
	fun relaxedTitleDropsTrailingParentheticalWithoutReturningBlank() {
		assertEquals("The Song", relaxedLrcLibTrackName(" The Song (Live at Home) "))
		assertEquals("(Live)", relaxedLrcLibTrackName(" (Live) "))
	}

	@Test
	fun durationIsSentAsWholeSeconds() {
		assertEquals(223L, lrcLibDurationSeconds(223_900.milliseconds))
	}

	@Test
	fun rankingRejectsUnrelatedAndLyriclessCandidates() {
		val unrelated = candidate(
			id = 1,
			trackName = "Another Song",
			artistName = "Another Artist",
			syncedLyrics = "[00:01.00]wrong"
		)
		val lyricless = candidate(id = 2)

		assertNull(
			selectLrcLibCandidate(
				candidates = listOf(unrelated, lyricless),
				trackName = "The Song",
				artistName = "The Artist",
				albumName = "The Album",
				durationSeconds = 224
			)
		)
	}

	@Test
	fun rankingUsesNormalizedMetadataBeforeResultOrder() {
		val wrongAlbum = candidate(
			id = 1,
			trackName = "The Song",
			artistName = "The Artist",
			albumName = "Compilation",
			duration = 224.0,
			syncedLyrics = "[00:01.00]compilation"
		)
		val correctAlbum = candidate(
			id = 2,
			trackName = "The Song!",
			artistName = "the artist",
			albumName = "The Album",
			duration = 225.0,
			plainLyrics = "album"
		)

		assertEquals(
			2,
			selectLrcLibCandidate(
				candidates = listOf(wrongAlbum, correctAlbum),
				trackName = "The Song",
				artistName = "The Artist",
				albumName = "The Album",
				durationSeconds = 224
			)?.id
		)
	}

	@Test
	fun rankingUsesDurationThenSyncedLyricsForEquivalentMetadata() {
		val farther = candidate(id = 1, duration = 230.0, syncedLyrics = "[00:01.00]far")
		val closerPlain = candidate(id = 2, duration = 224.0, plainLyrics = "close")
		val closerSynced = candidate(id = 3, duration = 224.0, syncedLyrics = "[00:01.00]close")

		assertEquals(
			3,
			selectLrcLibCandidate(
				candidates = listOf(farther, closerPlain, closerSynced),
				trackName = "The Song",
				artistName = "AC/DC",
				albumName = "The Album",
				durationSeconds = 224
			)?.id
		)
	}

	private fun candidate(
		id: Int,
		trackName: String = "The Song",
		artistName: String = "ac dc",
		albumName: String = "The Album",
		duration: Double? = 224.0,
		syncedLyrics: String? = null,
		plainLyrics: String? = null
	) = LrcLibCandidate(
		id = id,
		trackName = trackName,
		artistName = artistName,
		albumName = albumName,
		duration = duration,
		syncedLyrics = syncedLyrics,
		plainLyrics = plainLyrics
	)
}
