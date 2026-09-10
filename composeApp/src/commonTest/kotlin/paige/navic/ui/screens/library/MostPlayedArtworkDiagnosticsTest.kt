package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MostPlayedArtworkDiagnosticsTest {
	@Test
	fun diagnosticUrlSummaryDoesNotRetainHostPathOrIdentifiers() {
		val forbiddenMarkers = listOf(
			"synthetic-artwork.invalid",
			"SYNTHETIC_PATH_ID",
			"SYNTHETIC_RESOURCE_ID",
			"SYNTHETIC_ACTION_ID"
		)
		val summary = mostPlayedDiagnosticUrlSummary(
			"https://synthetic-artwork.invalid/SYNTHETIC_PATH_ID/SYNTHETIC_RESOURCE_ID" +
				"?action=SYNTHETIC_ACTION_ID"
		)

		assertEquals("absolute-url", summary)
		forbiddenMarkers.forEach { marker -> assertFalse(summary.contains(marker)) }
	}

	@Test
	fun diagnosticHeaderSummaryReportsOnlyCount() {
		val summary = mostPlayedDiagnosticHeaderSummary(
			mapOf(
				"SYNTHETIC_HEADER_ID" to "SYNTHETIC_HEADER_VALUE",
				"SYNTHETIC_ACTION_ID" to "SYNTHETIC_ACTION_VALUE"
			)
		)

		assertEquals("count=2", summary)
		assertFalse(summary.contains("SYNTHETIC_HEADER_ID"))
		assertFalse(summary.contains("SYNTHETIC_ACTION_ID"))
	}

	@Test
	fun diagnosticUrlSummaryClassifiesRelativeUrls() {
		val summary = mostPlayedDiagnosticUrlSummary(
			"/SYNTHETIC_PATH_ID/SYNTHETIC_RESOURCE_ID?id=SYNTHETIC_ACTION_ID"
		)

		assertEquals("relative-path", summary)
		assertFalse(summary.contains("SYNTHETIC_PATH_ID"))
		assertFalse(summary.contains("SYNTHETIC_RESOURCE_ID"))
	}

	@Test
	fun diagnosticTextReportsPresenceWithoutRetainingIdsOrNames() {
		val summary = mostPlayedDiagnosticText("SYNTHETIC_ARTIST_ID SYNTHETIC_ARTIST_NAME")

		assertEquals("present", summary)
		assertFalse(summary.contains("SYNTHETIC_ARTIST_ID"))
		assertFalse(summary.contains("SYNTHETIC_ARTIST_NAME"))
	}
}
