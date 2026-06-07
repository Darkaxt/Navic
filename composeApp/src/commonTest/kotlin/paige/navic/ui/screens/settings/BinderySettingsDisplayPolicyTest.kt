package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyConnectionResult
import paige.navic.domain.repositories.BinderyServiceStatus

class BinderySettingsDisplayPolicyTest {
	@Test
	fun connectionStateMapsConfigurationAndValidationResults() {
		assertEquals(
			BinderyConnectionState.Disabled,
			binderyConnectionState(
				enabled = false,
				opdsUrl = "https://bindery.example.com/opds",
				apiKey = "secret",
				connectionResult = BinderyConnectionResult.Connected(4, true),
				isTestingConnection = false
			)
		)
		assertEquals(
			BinderyConnectionState.MissingOpdsUrl,
			binderyConnectionState(
				enabled = true,
				opdsUrl = "",
				apiKey = "secret",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			BinderyConnectionState.MissingApiKey,
			binderyConnectionState(
				enabled = true,
				opdsUrl = "https://bindery.example.com/opds",
				apiKey = "",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			BinderyConnectionState.InvalidOpdsUrl("Bindery OPDS URL must start with http:// or https://."),
			binderyConnectionState(
				enabled = true,
				opdsUrl = "bindery.example.com/opds",
				apiKey = "secret",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			BinderyConnectionState.Testing,
			binderyConnectionState(
				enabled = true,
				opdsUrl = "https://bindery.example.com/opds",
				apiKey = "secret",
				connectionResult = null,
				isTestingConnection = true
			)
		)
		assertEquals(
			BinderyConnectionState.Connected(
				navigationCount = 4,
				audiobooksAvailable = true
			),
			binderyConnectionState(
				enabled = true,
				opdsUrl = "https://bindery.example.com/opds",
				apiKey = "secret",
				connectionResult = BinderyConnectionResult.Connected(4, true),
				isTestingConnection = false
			)
		)
	}

	@Test
	fun statusRowsExposeCatalogAndKnownServerGaps() {
		assertEquals(
			listOf(
				BinderyStatusRow(BinderyStatusType.OpdsUrl, BinderyStatusValue.Configured),
				BinderyStatusRow(BinderyStatusType.ApiKey, BinderyStatusValue.Configured),
				BinderyStatusRow(BinderyStatusType.Audiobooks, BinderyStatusValue.Enabled),
				BinderyStatusRow(BinderyStatusType.Authors, BinderyStatusValue.Enabled),
				BinderyStatusRow(BinderyStatusType.Collections, BinderyStatusValue.Enabled),
				BinderyStatusRow(BinderyStatusType.Findings, BinderyStatusValue.Enabled),
				BinderyStatusRow(BinderyStatusType.Series, BinderyStatusValue.Enabled),
				BinderyStatusRow(BinderyStatusType.Search, BinderyStatusValue.Enabled),
				BinderyStatusRow(BinderyStatusType.Navigation, BinderyStatusValue.Count(4)),
				BinderyStatusRow(BinderyStatusType.ProgressSync, BinderyStatusValue.Unsupported),
				BinderyStatusRow(BinderyStatusType.Pagination, BinderyStatusValue.Unsupported)
			),
			binderyStatusRows(
				BinderyServiceStatus(
					enabled = true,
					opdsUrlConfigured = true,
					apiKeyConfigured = true,
					navigationCount = 4,
					hasSearch = true,
					hasAudiobooks = true,
					hasAuthors = true,
					hasCollections = true,
					hasFindings = true,
					hasSeries = true,
					progressSyncSupported = false,
					paginationSupported = false
				)
			)
		)
	}
}
