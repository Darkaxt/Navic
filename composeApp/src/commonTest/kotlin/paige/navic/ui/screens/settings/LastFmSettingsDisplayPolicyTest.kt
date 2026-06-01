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
				apiKey = "",
				connectionResult = null,
				isTestingConnection = true
			)
		)
		assertEquals(
			LastFmConnectionState.MissingApiKey,
			lastFmConnectionState(
				apiKey = " ",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.NotTested,
			lastFmConnectionState(
				apiKey = "configured",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.InvalidApiKey,
			lastFmConnectionState(
				apiKey = "configured",
				connectionResult = LastFmConnectionResult.InvalidApiKey,
				isTestingConnection = false
			)
		)
		assertEquals(
			LastFmConnectionState.Connected(sampleArtistCount = 1),
			lastFmConnectionState(
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
				LastFmStatusRow(LastFmStatusType.ArtistTopTracks, LastFmStatusValue.Disabled)
			),
			lastFmStatusRows(
				LastFmServiceStatus(
					apiKeyConfigured = false,
					artistTopTracksEnabled = false
				)
			)
		)
		assertEquals(
			listOf(
				LastFmStatusRow(LastFmStatusType.ApiKey, LastFmStatusValue.Configured),
				LastFmStatusRow(LastFmStatusType.ArtistTopTracks, LastFmStatusValue.Enabled),
				LastFmStatusRow(LastFmStatusType.ValidationSample, LastFmStatusValue.Count(3))
			),
			lastFmStatusRows(
				LastFmServiceStatus(
					apiKeyConfigured = true,
					artistTopTracksEnabled = true,
					sampleArtistCount = 3
				)
			)
		)
	}
}
