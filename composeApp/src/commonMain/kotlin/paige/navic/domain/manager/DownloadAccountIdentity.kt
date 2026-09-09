package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString.Companion.encodeUtf8

/** The empty database owner is reserved for inaccessible, preserved legacy rows. */
class DownloadAccountIdentity(settings: Settings) {
	val startupOwnerId: String? = musicDownloadOwnerId(
		settings.getString("instanceUrl", ""), settings.getString("username", "")
	)
	// Persist the first upgrade decision before Room opens, including an ownerless decision.
	// A restart during legacy import must not reattribute old files to a newer login.
	val legacyOwnerId: String? = settings.getStringOrNull(LEGACY_OWNER_KEY)?.takeIf { it.isNotEmpty() }
		?: if (settings.hasKey(LEGACY_OWNER_KEY)) null else startupOwnerId.also {
			settings[LEGACY_OWNER_KEY] = it.orEmpty()
		}
	private val currentOwner = MutableStateFlow(startupOwnerId)
	val ownerId: StateFlow<String?> = currentOwner.asStateFlow()

	internal fun deactivate() { currentOwner.value = null }

	internal fun activate(instanceUrl: String, username: String) {
		currentOwner.value = musicDownloadOwnerId(instanceUrl, username)
	}

	private companion object {
		const val LEGACY_OWNER_KEY = "musicDownloadsLegacyOwnerV6"
	}
}

internal fun musicDownloadOwnerId(instanceUrl: String, username: String): String? {
	if (instanceUrl.isBlank() || username.isBlank()) return null
	val url = runCatching { Url(normalizeSubsonicInstanceUrl(instanceUrl)) }.getOrNull() ?: return null
	if (url.host.isBlank()) return null
	val server = "${url.protocol.name.lowercase()}://${url.host.lowercase()}:${url.port}${url.encodedPath.trimEnd('/')}"
	// Length framing avoids ambiguous pairs; passwords and URL credentials are excluded.
	return "${server.length}:$server${username.length}:$username".encodeUtf8().sha256().hex()
}
