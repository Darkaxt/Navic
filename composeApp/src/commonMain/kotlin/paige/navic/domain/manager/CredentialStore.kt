package paige.navic.domain.manager

import com.russhwolf.settings.Settings

const val BinderyApiKeyCredential = "bindery_api_key"

interface CredentialStore {
	fun get(key: String): String?
	fun put(key: String, value: String): Boolean
	fun remove(key: String): Boolean
}

class SettingsCredentialStore(
	private val settings: Settings
) : CredentialStore {
	override fun get(key: String): String? = settings.getStringOrNull(key)

	override fun put(key: String, value: String): Boolean {
		settings.putString(key, value)
		return settings.getStringOrNull(key) == value
	}

	override fun remove(key: String): Boolean {
		settings.remove(key)
		return !settings.hasKey(key)
	}
}
