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
		val key = nextLidaClipsServiceStatusRefreshKey(
			enabled = true,
			baseUrl = " https://clips.remaxku.eu/ ",
			apiKey = " secret "
		)

		assertEquals(
			key,
			nextLidaClipsServiceStatusRefreshKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "secret"
			)
		)
		assertEquals(
			false,
			key == nextLidaClipsServiceStatusRefreshKey(
				enabled = true,
				baseUrl = "https://clips.remaxku.eu",
				apiKey = "new-secret"
			)
		)
	}

	@Test
	fun serviceStatusRefreshKeyDoesNotExposeRawApiKey() {
		val key = nextLidaClipsServiceStatusRefreshKey(
			enabled = true,
			baseUrl = "https://clips.remaxku.eu",
			apiKey = "secret"
		)

		assertEquals(false, key?.contains("secret") == true)
		assertEquals(false, key?.contains("X-Api-Key") == true)
	}
}
