package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals

class MostPlayedArtworkDiagnosticsTest {
	@Test
	fun diagnosticUrlSummaryDoesNotLogQueryValues() {
		val summary = mostPlayedDiagnosticUrlSummary(
			"https://aurral.example.com/api/artist/iu/image?token=secret-token&size=large"
		)

		assertEquals(
			"https://aurral.example.com/api/artist/iu/image?query",
			summary
		)
	}

	@Test
	fun diagnosticHeaderSummaryLogsNamesOnly() {
		val summary = mostPlayedDiagnosticHeaderSummary(
			mapOf(
				"Authorization" to "Bearer secret-token",
				"X-Api-Key" to "secret-api-key"
			)
		)

		assertEquals("Authorization,X-Api-Key", summary)
	}

	@Test
	fun diagnosticUrlSummaryMarksRelativeUrls() {
		val summary = mostPlayedDiagnosticUrlSummary("/rest/getArtistImage?id=iu")

		assertEquals("relative:/rest/getArtistImage?query", summary)
	}
}
