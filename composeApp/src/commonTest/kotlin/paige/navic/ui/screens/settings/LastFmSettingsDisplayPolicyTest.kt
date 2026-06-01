package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.LastFmConnectionResult
import paige.navic.domain.repositories.LastFmServiceStatus

class LastFmSettingsDisplayPolicyTest {
	@Test
	fun connectionStatePrefersTestingThenKeyValidationThenResult() {
		assertEquals(
			LastFmConnectionState.Testing,
			lastFmConnectionState(
				enabled = true,
				apiKey = "",
				connectionResult = null,
				isTestingConnection = true
			)
		)
		assertEquals(
			LastFmConnectionState.MissingApiKey,
			lastFmConnectionState(
				enabled = true,
				apiKey = " ",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.NotTested,
			lastFmConnectionState(
				enabled = true,
				apiKey = "configured",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.InvalidApiKey,
			lastFmConnectionState(
				enabled = true,
				apiKey = "configured",
				connectionResult = LastFmConnectionResult.InvalidApiKey,
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.Connected(sampleArtistCount = 1),
			lastFmConnectionState(
				enabled = true,
				apiKey = "configured",
				connectionResult = LastFmConnectionResult.Connected(sampleArtistCount = 1),
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.Disabled,
			lastFmConnectionState(
				enabled = false,
				apiKey = "configured",
				connectionResult = LastFmConnectionResult.Connected(sampleArtistCount = 1),
				isTestingConnection = false
			)
		)
	}

	@Test
	fun statusRowsExposeConfiguredKeyTopTracksAndProbeMetric() {
		assertEquals(
			listOf(
				LastFmStatusRow(LastFmStatusType.ApiKey, LastFmStatusValue.NotConfigured),
				LastFmStatusRow(LastFmStatusType.Integration, LastFmStatusValue.Enabled),
				LastFmStatusRow(LastFmStatusType.ArtistTopTracks, LastFmStatusValue.Disabled)
			),
			lastFmStatusRows(
				LastFmServiceStatus(
					enabled = true,
					apiKeyConfigured = false,
					artistTopTracksEnabled = false
				)
			)
		)
		assertEquals(
			listOf(
				LastFmStatusRow(LastFmStatusType.ApiKey, LastFmStatusValue.Configured),
				LastFmStatusRow(LastFmStatusType.Integration, LastFmStatusValue.Enabled),
				LastFmStatusRow(LastFmStatusType.ArtistTopTracks, LastFmStatusValue.Enabled),
				LastFmStatusRow(LastFmStatusType.AccountFeatures, LastFmStatusValue.Unsupported),
				LastFmStatusRow(LastFmStatusType.ValidationSample, LastFmStatusValue.Count(3))
			),
			lastFmStatusRows(
				LastFmServiceStatus(
					enabled = true,
					apiKeyConfigured = true,
					artistTopTracksEnabled = true,
					sampleArtistCount = 3
				)
			)
		)
		assertEquals(
			listOf(
				LastFmStatusRow(LastFmStatusType.ApiKey, LastFmStatusValue.Configured),
				LastFmStatusRow(LastFmStatusType.Integration, LastFmStatusValue.Disabled),
				LastFmStatusRow(LastFmStatusType.ArtistTopTracks, LastFmStatusValue.Disabled)
			),
			lastFmStatusRows(
				LastFmServiceStatus(
					enabled = false,
					apiKeyConfigured = true,
					artistTopTracksEnabled = false
				)
			)
		)
	}
}
