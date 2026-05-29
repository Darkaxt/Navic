package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.AurralConnectionResult
import paige.navic.domain.repositories.AurralServiceStatus

class AurralSettingsDisplayPolicyTest {
	@Test
	fun connectionStatusDisplayClassifiesUrlAndConnectionResult() {
		assertEquals(
			AurralConnectionStatusDisplay.MissingUrl,
			aurralConnectionStatusDisplay(
				baseUrl = " ",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.InvalidUrl,
			aurralConnectionStatusDisplay(
				baseUrl = "aurral.example.com",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.Testing,
			aurralConnectionStatusDisplay(
				baseUrl = "aurral.example.com",
				connectionResult = null,
				isTestingConnection = true
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.NotTested,
			aurralConnectionStatusDisplay(
				baseUrl = "https://aurral.example.com",
				connectionResult = null,
				isTestingConnection = false
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.Connected,
			aurralConnectionStatusDisplay(
				baseUrl = "https://aurral.example.com",
				connectionResult = AurralConnectionResult.Connected,
				isTestingConnection = false
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.Unauthorized,
			aurralConnectionStatusDisplay(
				baseUrl = "https://aurral.example.com",
				connectionResult = AurralConnectionResult.Unauthorized,
				isTestingConnection = false
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.Forbidden,
			aurralConnectionStatusDisplay(
				baseUrl = "https://aurral.example.com",
				connectionResult = AurralConnectionResult.Forbidden,
				isTestingConnection = false
			)
		)
		assertEquals(
			AurralConnectionStatusDisplay.Failed("boom"),
			aurralConnectionStatusDisplay(
				baseUrl = "https://aurral.example.com",
				connectionResult = AurralConnectionResult.Failed("boom"),
				isTestingConnection = false
			)
		)
	}

	@Test
	fun permissionSummaryListsEnabledNativeActions() {
		assertEquals(
			"Not authenticated",
			aurralPermissionSummary(AurralServiceStatus())
		)
		assertEquals(
			"Flows, artist requests, album requests",
			aurralPermissionSummary(
				AurralServiceStatus(
					username = "darka",
					accessFlow = true,
					addArtist = true,
					addAlbum = true
				)
			)
		)
		assertEquals(
			"Flows",
			aurralPermissionSummary(
				AurralServiceStatus(
					username = "flow",
					accessFlow = true,
					addArtist = false,
					addAlbum = false
				)
			)
		)
	}

	@Test
	fun flowTrackSummaryUsesOnlyNonZeroStateCounts() {
		assertEquals(
			"6 total, 1 pending, 2 downloading, 3 ready",
			aurralFlowTrackSummary(
				AurralServiceStatus(
					flowTracksTotal = 6,
					flowTracksPending = 1,
					flowTracksDownloading = 2,
					flowTracksDone = 3,
					flowTracksFailed = 0
				)
			)
		)
		assertEquals(
			"0 total",
			aurralFlowTrackSummary(AurralServiceStatus())
		)
	}
}
