package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LidaClipsServiceStatusRefreshPolicyTest {
	@Test
	fun serviceStatusRefreshRequiresEnabledAndConfiguredBaseUrl() {
		assertNull(nextLidaClipsServiceStatusRefreshKey(
			enabled = false,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret"
		))
		assertNull(nextLidaClipsServiceStatusRefreshKey(
			enabled = true,
			baseUrl = " ",
			apiKey = "secret"
		))
		assertNull(nextLidaClipsServiceStatusRefreshKey(
			enabled = true,
			baseUrl = "clips.remaxku.eu",
			apiKey = "secret"
		))
	}

	@Test
	fun serviceStatusRefreshKeyFollowsBaseUrlAndApiKeyChanges() {
		assertEquals(
			"https://clips.remaxku.eu|secret",
			nextLidaClipsServiceStatusRefreshKey(
				enabled = true,
				baseUrl = " https://clips.remaxku.eu/ ",
				apiKey = " secret "
			)
		)
		assertEquals(
			"https://clips.remaxku.eu|new-secret",
			nextLidaClipsServiceStatusRefreshKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "new-secret"
			)
		)
	}
}
