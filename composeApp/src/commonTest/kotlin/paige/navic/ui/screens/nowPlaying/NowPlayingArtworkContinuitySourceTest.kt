package paige.navic.ui.screens.nowPlaying

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingArtworkContinuitySourceTest {
	@Test
	fun vinylWaitsForTheExactCurrentArtworkRequestToResolve() {
		val artwork = sourceFile("ui/screens/nowPlaying/components/Artwork.kt").readText()

		assertTrue(
			"val artworkRequestIdentity = remember(" in artwork &&
				"NowPlayingArtworkRequestIdentity(" in artwork,
			"Now Playing must identify artwork readiness by song and complete cover request."
		)
		assertTrue(
			"var resolvedVinylArtworkRequest by remember {" in artwork &&
				"resolvedVinylArtworkRequest = artworkRequestIdentity" in artwork,
			"Only a successful image resolution may unlock the active artwork request."
		)
		assertTrue(
			"isNowPlayingVinylArtworkReady(" in artwork &&
				"requestedArtwork = artworkRequestIdentity" in artwork &&
				"resolvedArtwork = resolvedVinylArtworkRequest" in artwork,
			"Vinyl readiness must reject unresolved and previous-song artwork identities."
		)
		assertTrue(
			"hasCoverArt = vinylHasCoverArt" in artwork &&
				"hasGeneratedArtwork = vinylHasGeneratedArtwork" in artwork,
			"Rotation, shape, and groove overlay must receive readiness-gated artwork flags."
		)
	}

	@Test
	fun coverArtScopesCachedLoadingPlaceholderToAnExplicitOptIn() {
		val source = sourceFile("ui/components/common/CoverArt.kt").readText()

		assertTrue(
			"crossfadeMs: Int = 500,\n\tuseCachedLoadingPlaceholder: Boolean = false," in source,
			"CoverArt must default cached loading placeholders off beside its crossfade configuration."
		)
		assertTrue(
			"internal fun cachedCoverArtLoadingPlaceholderKey(\n\tenabled: Boolean,\n\tresolvedImageCacheKey: String?\n): String?" in source &&
				"if (!enabled || resolvedImageCacheKey.isNullOrBlank()) return null" in source &&
				"return resolvedImageCacheKey" in source,
			"The pure helper must be the sole policy that permits a resolved cache key as a placeholder."
		)
	}

	@Test
	fun coverArtAppliesAndRendersTheCachedPlaceholderOnlyWhenItsKeyIsAvailable() {
		val source = sourceFile("ui/components/common/CoverArt.kt").readText()
		val request = blockBetween(source, "val model = remember(", ".build()")
		val requestRememberArguments = blockBetween(source, "val model = remember(", "\n\t) {")
		val painterKey = blockBetween(source, "key(\n\t\tcoilPlatformContext,", "\n\t}")
		val loading = blockBetween(source, "loading = loading@{", "\n\t\t\terror = {")
		val fallbackIndex = requiredMarkerIndex(loading, "CoverArtFallback(")
		val fallbackFillSizeIndex = requiredMarkerIndex(
			loading,
			"modifier = Modifier.fillMaxSize(),",
			fallbackIndex
		)
		val cachedPlaceholderGuardIndex = requiredMarkerIndex(
			loading,
			"if (cachedLoadingPlaceholderKey != null) {",
			fallbackFillSizeIndex
		)
		val cachedPlaceholderContentIndex = requiredMarkerIndex(
			loading,
			"SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())",
			cachedPlaceholderGuardIndex
		)

		listOf(
			"coilPlatformContext",
			"resolvedImageData",
			"resolvedImageCacheKey",
			"cachedLoadingPlaceholderKey",
			"resolvedRequestHeaders",
			"crossfadeMs",
			"normalization"
		).forEach { requestIdentityKey ->
			assertTrue(
				requestIdentityKey in requestRememberArguments,
				"The ImageRequest remember key list must contain $requestIdentityKey."
			)
		}
		assertTrue(
			"val resolvedImageData = resolvedImageUrl ?: visibleCoverArtId?.let { sessionManager.getCoverArtUrl(it) }" in source &&
			".data(resolvedImageData)" in request,
			"ImageRequest data must be resolved before remember so preference-backed cover quality reshapes the request."
		)
		assertTrue(
			"cachedLoadingPlaceholderKey" in request,
			"The model remember keys must include the cached loading placeholder identity."
		)
		assertTrue(
			"if (cachedLoadingPlaceholderKey != null) {\n\t\t\t\t\tplaceholderMemoryCacheKey(cachedLoadingPlaceholderKey)" in request,
			"Coil must receive a placeholder memory key only when the destination cache identity is available."
		)
		assertTrue(
			fallbackIndex < cachedPlaceholderGuardIndex &&
				cachedPlaceholderGuardIndex < cachedPlaceholderContentIndex,
			"Loading must keep the generated fallback underneath the guarded Coil memory-cache placeholder."
		)
		listOf(
			"coilPlatformContext",
			"resolvedImageData",
			"resolvedImageCacheKey",
			"cachedLoadingPlaceholderKey",
			"resolvedRequestHeaders",
			"crossfadeMs",
			"normalization",
			"SubcomposeAsyncImage("
		).forEach { painterIdentityInput ->
			assertTrue(
				painterIdentityInput in painterKey,
				"The Compose key around SubcomposeAsyncImage must contain $painterIdentityInput."
			)
		}
		assertTrue("success = { state ->" in source, "The existing success branch must remain.")
		assertTrue("error = {" in source, "The existing error branch must remain.")
	}

	@Test
	fun onlyExpandedNowPlayingArtworkOptsIntoCachedLoadingPlaceholders() {
		val artwork = sourceFile("ui/screens/nowPlaying/components/Artwork.kt").readText()
		val upNext = sourceFile("ui/screens/nowPlaying/components/rows/UpNextRow.kt").readText()
		val playbackSongCoverArt = sourceFile("ui/components/common/PlaybackArtworkState.kt").readText()
		val optInPattern = Regex("useCachedLoadingPlaceholder\\s*=\\s*true")
		val optInPaths = productionSourceSetRoots().flatMap { sourceSetRoot ->
			kotlinSourceFiles(sourceSetRoot).flatMap { file ->
				optInPattern.findAll(file.readText())
					.map {
						"${sourceSetName(sourceSetRoot)}/" +
							file.relativeTo(sourceSetRoot).path.replace('\\', '/')
					}
					.toList()
			}
		}

		assertEquals(
			1,
			optInPaths.size,
			"Exactly one production surface may opt into cached loading placeholders."
		)
		assertEquals(
			"commonMain/paige/navic/ui/screens/nowPlaying/components/Artwork.kt",
			optInPaths.single(),
			"The sole cached loading placeholder opt-in must remain owned by expanded Now Playing artwork."
		)
		assertTrue(
			"normalization = CoverArtNormalization.TrimWhitespace" in artwork,
			"Expanded Now Playing must retain trimmed cache-key normalization."
		)
		assertTrue(
			"normalization = CoverArtNormalization.TrimWhitespace" in upNext,
			"Up Next must retain the matching trimmed cache-key normalization."
		)
		assertFalse(
			"useCachedLoadingPlaceholder" in upNext,
			"Up Next must prime its ordinary destination cache entry without enabling a loading placeholder."
		)
		assertFalse(
			"useCachedLoadingPlaceholder" in playbackSongCoverArt,
			"Shared PlaybackSongCoverArt must stay generic so grids and mini-player surfaces cannot opt in."
		)
	}

	private fun composeAppRoot(): File =
		listOf(
			File("composeApp"),
			File(".")
		).firstOrNull { File(it, "src").isDirectory }
			?: error("Unable to locate composeApp root")

	private fun productionSourceSetRoots(): List<File> =
		File(composeAppRoot(), "src")
			.listFiles()
			?.filter { it.isDirectory && it.name.endsWith("Main") }
			?.mapNotNull { File(it, "kotlin").takeIf(File::isDirectory) }
			.orEmpty()

	private fun commonMainKotlinRoot(): File =
		productionSourceSetRoots().firstOrNull { sourceSetName(it) == "commonMain" }
			?: error("Unable to locate commonMain Kotlin source root")

	private fun sourceSetName(sourceSetRoot: File): String =
		sourceSetRoot.parentFile?.name ?: error("Unable to locate source-set directory for $sourceSetRoot")

	private fun kotlinSourceFiles(root: File): List<File> =
		root.walkTopDown()
			.filter { it.isFile && it.extension == "kt" }
			.toList()

	private fun sourceFile(path: String): File =
		File(commonMainKotlinRoot(), "paige/navic/$path")
			.takeIf(File::isFile)
			?: error("Unable to locate source file for $path")

	private fun blockBetween(source: String, startMarker: String, endMarker: String): String {
		val startIndex = requiredMarkerIndex(source, startMarker)
		val endIndex = requiredMarkerIndex(source, endMarker, startIndex + startMarker.length)
		assertTrue(endIndex > startIndex, "Expected $endMarker to follow $startMarker")
		return source.substring(startIndex, endIndex)
	}

	private fun requiredMarkerIndex(source: String, marker: String, startIndex: Int = 0): Int {
		val markerIndex = source.indexOf(marker, startIndex)
		assertTrue(markerIndex >= 0, "Expected source marker: $marker")
		return markerIndex
	}
}
