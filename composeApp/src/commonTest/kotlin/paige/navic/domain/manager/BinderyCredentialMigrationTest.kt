package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinderyCredentialMigrationTest {
	@Test
	fun legacyKeyMigratesOnceAndPlaintextIsRemoved() {
		val settings = MapSettings("binderyApiKey" to " legacy-secret ")
		val credentials = RecordingCredentialStore()

		val manager = PreferenceManager(settings, credentials)

		assertEquals(" legacy-secret ", manager.binderyApiKey)
		assertEquals(" legacy-secret ", credentials.get(BinderyApiKeyCredential))
		assertFalse(settings.hasKey("binderyApiKey"))
		assertEquals(1, credentials.putCalls)
		assertEquals(" legacy-secret ", manager.binderyApiKey)
		assertEquals(1, credentials.putCalls)
	}

	@Test
	fun secureKeyWinsAndStalePlaintextIsRemoved() {
		val settings = MapSettings("binderyApiKey" to "stale-secret")
		val credentials = RecordingCredentialStore(
			values = mutableMapOf(BinderyApiKeyCredential to "secure-secret")
		)

		val manager = PreferenceManager(settings, credentials)

		assertEquals("secure-secret", manager.binderyApiKey)
		assertFalse(settings.hasKey("binderyApiKey"))
		assertEquals(0, credentials.putCalls)
	}

	@Test
	fun failedSecureMigrationPreservesPlaintextForRetry() {
		val settings = MapSettings("binderyApiKey" to "legacy-secret")
		val credentials = RecordingCredentialStore(allowWrites = false)

		val manager = PreferenceManager(settings, credentials)

		assertEquals("legacy-secret", manager.binderyApiKey)
		assertTrue(settings.hasKey("binderyApiKey"))
		assertNull(credentials.get(BinderyApiKeyCredential))
	}

	@Test
	fun setterWritesOnlySecureStorageAndBlankClearsBothLocations() {
		val settings = MapSettings()
		val credentials = RecordingCredentialStore()
		val manager = PreferenceManager(settings, credentials)

		manager.binderyApiKey = "new-secret"

		assertEquals("new-secret", manager.binderyApiKey)
		assertEquals("new-secret", credentials.get(BinderyApiKeyCredential))
		assertFalse(settings.hasKey("binderyApiKey"))

		settings.putString("binderyApiKey", "stale-secret")
		manager.binderyApiKey = ""

		assertEquals("", manager.binderyApiKey)
		assertNull(credentials.get(BinderyApiKeyCredential))
		assertFalse(settings.hasKey("binderyApiKey"))
	}
}

private class RecordingCredentialStore(
	private val values: MutableMap<String, String> = mutableMapOf(),
	private val allowWrites: Boolean = true
) : CredentialStore {
	var putCalls: Int = 0
		private set

	override fun get(key: String): String? = values[key]

	override fun put(key: String, value: String): Boolean {
		putCalls += 1
		if (!allowWrites) return false
		values[key] = value
		return true
	}

	override fun remove(key: String): Boolean {
		values.remove(key)
		return true
	}
}
