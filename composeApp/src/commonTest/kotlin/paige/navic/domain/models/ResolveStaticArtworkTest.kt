package paige.navic.domain.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveStaticArtworkTest {
	@Test
	fun nativeCoverIsKeptWhenAurralIsDisabled() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = " native-cover-id ",
			externalArtworkUrl = null,
			externalArtworkCacheKey = null,
			aurralEnabled = false
		)
		assertEquals("native-cover-id", resolution.coverArtId)
		assertNull(resolution.imageUrl)
		assertEquals(PlaybackArtworkSource.NativeCover, resolution.source)
	}

	@Test
	fun nativeCoverStaysAsFallbackWhenAurralIsEnabledButNoExternalImage() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = "native-cover-id",
			externalArtworkUrl = null,
			externalArtworkCacheKey = null,
			aurralEnabled = true
		)
		assertEquals("native-cover-id", resolution.coverArtId)
		assertNull(resolution.imageUrl)
	}

	@Test
	fun nativeCoverIsSuppressedWhenAnExternalAurralImageIsPresent() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = "native-cover-id",
			externalArtworkUrl = "https://aurral.example.com/cover.webp",
			externalArtworkCacheKey = null,
			aurralEnabled = true
		)
		assertNull(resolution.coverArtId)
		assertEquals("https://aurral.example.com/cover.webp", resolution.imageUrl)
		assertEquals(PlaybackArtworkSource.External, resolution.source)
	}

	@Test
	fun navidromeImageUrlIsSuppressedWhenANativeCoverFallbackExists() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = "native-cover-id",
			externalArtworkUrl = "https://music.example.com/rest/getCoverArt?id=album-1",
			externalArtworkCacheKey = null,
			aurralEnabled = true
		)
		assertEquals("native-cover-id", resolution.coverArtId)
		assertNull(resolution.imageUrl)
	}

	@Test
	fun navidromeImageUrlStaysWhenItIsTheOnlyArtworkFallback() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = null,
			externalArtworkUrl = " https://navidrome.example.com/rest/getArtistImage?id=jason-ross ",
			externalArtworkCacheKey = null,
			aurralEnabled = true
		)
		assertNull(resolution.coverArtId)
		assertEquals(
			"https://navidrome.example.com/rest/getArtistImage?id=jason-ross",
			resolution.imageUrl
		)
	}

	@Test
	fun navidromeImageUrlStaysWhenAurralIsDisabled() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = "native-cover-id",
			externalArtworkUrl = "https://music.example.com/rest/getCoverArt?id=album-1",
			externalArtworkCacheKey = null,
			aurralEnabled = false
		)
		// Aurral off: the external URL is kept and wins (rendered on top); native id is retained too.
		assertEquals("native-cover-id", resolution.coverArtId)
		assertEquals("https://music.example.com/rest/getCoverArt?id=album-1", resolution.imageUrl)
	}

	@Test
	fun blankInputsResolveToNoArtwork() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = " ",
			externalArtworkUrl = "  ",
			externalArtworkCacheKey = null,
			aurralEnabled = true
		)
		assertNull(resolution.coverArtId)
		assertNull(resolution.imageUrl)
		assertEquals(PlaybackArtworkSource.None, resolution.source)
	}

	@Test
	fun externalArtworkCacheKeyIsPassedThrough() {
		val resolution = resolveStaticArtwork(
			serverCoverArtId = null,
			externalArtworkUrl = "https://aurral.example.com/cover.webp",
			externalArtworkCacheKey = "aurral-key",
			aurralEnabled = true
		)
		assertEquals("aurral-key", resolution.imageCacheKey)
	}

	@Test
	fun removedAurralVisibilityHelpersAreNotReferencedAnywhereInCommonMain() {
		val commonMain = File("src/commonMain/kotlin/paige/navic")
		val forbidden = listOf("visibleCoverArtIdForAurralPolicy", "visibleImageUrlForAurralPolicy")
		val offenders = commonMain.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.flatMap { file ->
				val text = file.readText()
				forbidden.filter { symbol -> "$symbol(" in text }
					.map { symbol -> "${file.path} references $symbol" }
			}
			.toList()
		assertTrue(
			offenders.isEmpty(),
			"Removed Aurral visibility helpers must not be called from commonMain (use resolveStaticArtwork): $offenders"
		)
	}
}
