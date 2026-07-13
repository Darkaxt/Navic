package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.zt64.subsonic.client.SubsonicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import paige.navic.data.database.dao.SyncActionDao
import paige.navic.data.remote.SubsonicClientFactory

class SessionManager(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager,
	private val syncActionDao: SyncActionDao,
	private val sessionLifetime: AuthenticatedSessionLifetime,
	private val clientFactory: SubsonicClientFactory
) {
	private val transitionMutex = Mutex()
	private val _isLoggedIn = MutableStateFlow(false)
	val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

	private val clientSlot = SessionResourceSlot(createClient(
		instanceUrl = storedInstanceUrl(),
		username = settings.getString("username", ""),
		password = settings.getString("password", ""),
	))

	init {
		val storedInstanceUrl = settings.getString("instanceUrl", "")
		val normalizedInstanceUrl = normalizeSubsonicInstanceUrl(storedInstanceUrl)
		if (storedInstanceUrl.isNotBlank() && storedInstanceUrl != normalizedInstanceUrl) {
			settings["instanceUrl"] = normalizedInstanceUrl
		}
		_isLoggedIn.value = settings.getStringOrNull("username") != null
		if (_isLoggedIn.value) sessionLifetime.activateInitialSession()
	}

	private fun storedInstanceUrl(): String =
		normalizeSubsonicInstanceUrl(settings.getString("instanceUrl", ""))

	private fun createClient(
		instanceUrl: String,
		username: String,
		password: String,
	) = clientFactory.create(
		instanceUrl = instanceUrl,
		username = username,
		password = password,
		requestHeaders = preferenceManager.serverRequestHeadersMap()
	)

	suspend fun login(
		instanceUrl: String,
		username: String,
		password: String
	) {
		val normalizedInstanceUrl = normalizeSubsonicInstanceUrl(instanceUrl)
		val client = createClient(normalizedInstanceUrl, username, password)

		try {
			client.ping()
		} catch (e: Exception) {
			throw Exception(
				"Failed to connect to the instance. Please check your credentials and try again.",
				e
			)
		}

		transitionMutex.withLock {
			val accountChanged = _isLoggedIn.value && (
				storedInstanceUrl() != normalizedInstanceUrl ||
					settings.getString("username", "") != username
				)
			sessionLifetime.endSession()
			if (accountChanged) clearOutgoingSyncState()

			settings["instanceUrl"] = normalizedInstanceUrl
			settings["username"] = username
			settings["password"] = password

			clientSlot.swap(client)
			sessionLifetime.startSession()
			_isLoggedIn.value = true
		}
	}

	suspend fun logout() {
		withContext(NonCancellable) {
			transitionMutex.withLock {
				sessionLifetime.endSession()
				clearOutgoingSyncState()
				settings["username"] = null
				settings["password"] = null
				clientSlot.swap(createClient(storedInstanceUrl(), "", ""))
				_isLoggedIn.value = false
			}
		}
	}

	private suspend fun clearOutgoingSyncState() {
		syncActionDao.clearAllActions()
		preferenceManager.lastFullSyncTime = 0L
	}

	fun refreshClient() {
		clientSlot.swap(createClient(
			instanceUrl = storedInstanceUrl(),
			username = settings.getString("username", ""),
			password = settings.getString("password", ""),
		))
	}

	internal suspend fun <T> withApi(block: suspend (SubsonicClient) -> T): T =
		clientSlot.withResource(block)

	fun getStreamUrl(id: String): String =
		clientSlot.snapshot().getStreamUrl(id)

	fun getStreamUrl(id: String, bitrate: Int, container: String?): String =
		clientSlot.snapshot().getStreamUrl(id, bitrate, container)

	fun getCoverArtUrl(coverArtId: String) = clientSlot.snapshot().getCoverArtUrl(
		coverArtId,
		auth = true,
		size = "${preferenceManager.coverArtQuality.value}"
	)
}
