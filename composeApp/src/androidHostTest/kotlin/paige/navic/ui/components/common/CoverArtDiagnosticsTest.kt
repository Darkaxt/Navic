package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoverArtDiagnosticsTest {
	@Test
	fun diagnosticValueDoesNotRetainSyntheticUrlPathOrIdentifiers() {
		val forbiddenMarkers = listOf(
			"synthetic-cover.invalid",
			"SYNTHETIC_PATH_ID",
			"SYNTHETIC_RESOURCE_ID",
			"SYNTHETIC_ACTION_ID"
		)
		val summary = invokeCoverArtDiagnostic(
			methodName = "coverArtDiagnosticValue",
			parameterType = String::class.java,
			value = "https://synthetic-cover.invalid/SYNTHETIC_PATH_ID/SYNTHETIC_RESOURCE_ID" +
				"?action=SYNTHETIC_ACTION_ID"
		) as String

		assertEquals("absolute-url", summary)
		forbiddenMarkers.forEach { marker -> assertFalse(summary.contains(marker)) }
	}

	@Test
	fun diagnosticHeadersReportOnlyCount() {
		val summary = invokeCoverArtDiagnostic(
			methodName = "coverArtDiagnosticHeaderKeys",
			parameterType = Map::class.java,
			value = mapOf(
				"SYNTHETIC_HEADER_ID" to "SYNTHETIC_HEADER_VALUE",
				"SYNTHETIC_ACTION_ID" to "SYNTHETIC_ACTION_VALUE"
			)
		) as String

		assertEquals("count=2", summary)
		assertFalse(summary.contains("SYNTHETIC_HEADER_ID"))
		assertFalse(summary.contains("SYNTHETIC_ACTION_ID"))
	}

	@Test
	fun diagnosticLabelUsesOnlyApprovedFixedNames() {
		val summary = invokeCoverArtDiagnostic(
			methodName = "coverArtDiagnosticLabel",
			parameterType = String::class.java,
			value = "artist-detail-SYNTHETIC_ARTIST_ID"
		) as String

		assertEquals("artist-detail", summary)
		assertFalse(summary.contains("SYNTHETIC_ARTIST_ID"))
	}

	private fun invokeCoverArtDiagnostic(
		methodName: String,
		parameterType: Class<*>,
		value: Any?
	): Any? = Class.forName("paige.navic.ui.components.common.CoverArtKt")
		.getDeclaredMethod(methodName, parameterType)
		.apply { isAccessible = true }
		.invoke(null, value)
}
