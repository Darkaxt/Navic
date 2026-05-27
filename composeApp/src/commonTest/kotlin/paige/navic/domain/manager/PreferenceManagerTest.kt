package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferenceManagerTest {
	@Test
	fun serverRequestHeadersMapKeepsCustomHeadersWhenBasicAuthIsDisabled() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = """
			X-Proxy-User: training
			Authorization: Basic manual-token
		""".trimIndent()

		assertEquals(
			mapOf(
				"X-Proxy-User" to "training",
				"Authorization" to "Basic manual-token"
			),
			manager.serverRequestHeadersMap()
		)
	}

	@Test
	fun serverRequestHeadersMapAddsGeneratedBasicAuthWhenEnabled() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "X-Forwarded-Host: music.example.test"
		manager.reverseProxyBasicAuthEnabled = true
		manager.reverseProxyBasicAuthUsername = "traefik"
		manager.reverseProxyBasicAuthPassword = "secret"

		assertEquals(
			mapOf(
				"X-Forwarded-Host" to "music.example.test",
				"Authorization" to "Basic dHJhZWZpazpzZWNyZXQ="
			),
			manager.serverRequestHeadersMap()
		)
	}

	@Test
	fun generatedBasicAuthOverridesManualAuthorizationOnlyWhenEnabled() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "Authorization: Basic manual-token"
		manager.reverseProxyBasicAuthUsername = "traefik"
		manager.reverseProxyBasicAuthPassword = "secret"

		assertEquals("Basic manual-token", manager.serverRequestHeadersMap()["Authorization"])

		manager.reverseProxyBasicAuthEnabled = true

		assertEquals("Basic dHJhZWZpazpzZWNyZXQ=", manager.serverRequestHeadersMap()["Authorization"])
	}

	@Test
	fun generatedBasicAuthRemovesCaseInsensitiveManualAuthorization() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "authorization: Basic manual-token"
		manager.reverseProxyBasicAuthEnabled = true
		manager.reverseProxyBasicAuthUsername = "traefik"
		manager.reverseProxyBasicAuthPassword = "secret"

		assertEquals(
			mapOf("Authorization" to "Basic dHJhZWZpazpzZWNyZXQ="),
			manager.serverRequestHeadersMap()
		)
	}

	@Test
	fun generatedBasicAuthRequiresUsernameAndPassword() {
		val manager = PreferenceManager(MapSettings())
		manager.customHeaders = "Authorization: Basic manual-token"
		manager.reverseProxyBasicAuthEnabled = true
		manager.reverseProxyBasicAuthUsername = "traefik"

		assertEquals("Basic manual-token", manager.serverRequestHeadersMap()["Authorization"])
	}

	@Test
	fun respectAudioFocusDefaultsToCurrentBehavior() {
		val manager = PreferenceManager(MapSettings())

		assertTrue(manager.respectAudioFocus)
		manager.respectAudioFocus = false
		assertFalse(manager.respectAudioFocus)
	}
}
