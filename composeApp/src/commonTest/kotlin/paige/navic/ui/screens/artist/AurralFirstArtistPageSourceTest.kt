package paige.navic.ui.screens.artist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralFirstArtistPageSourceTest {
	@Test
	fun artistDetailsDtoCarriesProfileImageAliases() {
		val source = sourceFile("data/remote/aurral/AurralDtos.kt").readText()
		val dtoBody = source.substringAfter("internal data class AurralArtistDetailsDto(")
			.substringBefore("\n)")

		assertTrue("val image:" in dtoBody, "Aurral artist details must decode the canonical image field.")
		assertTrue("val images:" in dtoBody, "Aurral artist details must decode image lists from profile payloads.")
		assertTrue("val imageUrl:" in dtoBody, "Aurral artist details must decode imageUrl aliases.")
		assertTrue("val coverUrl:" in dtoBody, "Aurral artist details must decode coverUrl aliases.")
	}

	@Test
	fun artistEnrichmentMapsImageFromDetailsBeforeSearchFallback() {
		val mapping = sourceFile("data/remote/aurral/AurralServiceDtoMapping.kt").readText()
		val model = sourceFile("domain/models/AurralArtistEnrichmentPolicy.kt").readText()

		assertTrue("val imageUrl: String? = null" in model, "AurralArtistEnrichment must carry profile artwork.")
		assertTrue("imageUrl = aurralArtistDetailsImageUrl(baseUrl, details)" in mapping)
	}

	@Test
	fun previewAndSimilarNonSuccessResponsesThrowInsteadOfReturningEmpty() {
		val source = sourceFile("data/remote/aurral/AurralApiClient.kt").readText()
		val previewBody = functionBody(source, "private suspend fun fetchArtistPreview")
		val similarBody = functionBody(source, "private suspend fun fetchSimilarArtists")

		assertFalse(
			"else -> AurralArtistPreviewDto()" in previewBody,
			"Preview failures must surface as errors; only successful empty payloads are Empty."
		)
		assertFalse(
			"HttpStatusCode.Unauthorized -> AurralArtistPreviewDto()" in previewBody ||
				"HttpStatusCode.Forbidden -> AurralArtistPreviewDto()" in previewBody,
			"Preview auth/config failures must surface visibly, not as empty results."
		)
		assertTrue("error(aurralHttpErrorMessage(\"Aurral artist preview\"" in previewBody)

		assertFalse(
			"else -> AurralSimilarArtistsDto()" in similarBody,
			"Similar artist failures must surface as errors; only successful empty payloads are Empty."
		)
		assertFalse(
			"HttpStatusCode.Unauthorized -> AurralSimilarArtistsDto()" in similarBody ||
				"HttpStatusCode.Forbidden -> AurralSimilarArtistsDto()" in similarBody,
			"Similar auth/config failures must surface visibly, not as empty results."
		)
		assertTrue("error(aurralHttpErrorMessage(\"Aurral similar artists\"" in similarBody)
	}

	@Test
	fun coreProfileFailureDoesNotCollapseEveryAurralSection() {
		val source = sourceFile("ui/screens/artist/viewmodels/ArtistDetailViewModel.kt").readText()
		val failureBlock = source.substringAfter("if (coreEnrichment == null) {")
			.substringBefore("return@launch")

		assertTrue("aurralProfileError = error.message ?: error::class.simpleName" in failureBlock)
		assertFalse("aurralOwnershipError = error.message ?: error::class.simpleName" in failureBlock)
		assertFalse("aurralPreviewTracksError = error.message ?: error::class.simpleName" in failureBlock)
		assertFalse("aurralSimilarArtistsError = error.message ?: error::class.simpleName" in failureBlock)
		assertFalse("aurralRequestsError = error.message ?: error::class.simpleName" in failureBlock)
	}

	@Test
	fun artistListPassesAurralMonitorStateToCards() {
		val viewModel = sourceFile("ui/screens/artist/viewmodels/ArtistListViewModel.kt").readText()
		val content = sourceFile("ui/screens/artist/components/ListContent.kt").readText()

		assertTrue("val aurralMonitorStates" in viewModel)
		assertTrue("aurralRepository.libraryArtistMonitorStates" in viewModel)
		assertTrue("aurralMonitorStates: Map<String, AurralMonitorActionState>" in content)
		assertTrue("aurralMonitorState = aurralMonitorStates[artist.id]" in content)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/$path"),
			File("composeApp/src/commonMain/kotlin/paige/navic/$path"),
			File("../composeApp/src/commonMain/kotlin/paige/navic/$path")
		).first(File::exists)

	private fun functionBody(source: String, signature: String): String {
		val start = source.indexOf(signature)
		require(start >= 0) { "Missing signature $signature" }
		val firstBrace = source.indexOf('{', start)
		require(firstBrace >= 0) { "Missing body for $signature" }
		var depth = 0
		for (index in firstBrace until source.length) {
			when (source[index]) {
				'{' -> depth++
				'}' -> {
					depth--
					if (depth == 0) return source.substring(firstBrace, index + 1)
				}
			}
		}
		error("Unclosed body for $signature")
	}
}
