package paige.navic.domain.manager

import dev.zt64.subsonic.api.model.SubsonicException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_status_idle
import org.jetbrains.compose.resources.StringResource
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.SyncActionDao
import paige.navic.data.database.entities.SyncActionEntity
import paige.navic.data.database.entities.SyncActionType
import paige.navic.domain.repositories.DbRepository
import paige.navic.domain.models.afterSyncFailure
import paige.navic.domain.models.classifySyncFailure
import paige.navic.domain.models.processOrderedSyncActions
import paige.navic.util.core.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class SyncState(
	val isSyncing: Boolean = false,
	val progress: Float = 0f,
	val message: StringResource = Res.string.info_status_idle,
	val deadLetterCount: Int = 0
)

class SyncManager(
	private val repository: DbRepository,
	private val syncDao: SyncActionDao,
	private val albumDao: AlbumDao,
	private val connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var syncJob: Job? = null
	private var retryWakeJob: Job? = null
	private val syncWakeups = Channel<Unit>(capacity = 1)
	private val requestMutex = Mutex()
	private var fullSyncRequested = false

	private val fullSyncThreshold = 1.hours

	val syncState: StateFlow<SyncState>
		field = MutableStateFlow(SyncState())

	init {
		scope.launch {
			for (ignored in syncWakeups) {
				val forceFull = requestMutex.withLock {
					fullSyncRequested.also { fullSyncRequested = false }
				}
				try {
					runSyncCycle(forceFull)
				} catch (error: CancellationException) {
					throw error
				} catch (error: Throwable) {
					Logger.e("SyncManager", "Sync cycle failed; actor remains available.", error)
				}
			}
		}
		scope.launch {
			syncDao.observeDeadLetterCount().collect { count ->
				syncState.update { it.copy(deadLetterCount = count) }
			}
		}
		scope.launch {
			connectivityManager.isOnline.collect { isOnline ->
				if (isOnline) requestSync()
			}
		}
	}

	fun startPeriodicSync() {
		Logger.i("SyncManager", "Starting periodic sync cicle.")
		if (syncJob?.isActive == true) return

		scope.launch {
			if (albumDao.getAlbumCount() == 0
				|| preferenceManager.lastFullSyncTime <= 0L
			) {
				Logger.i("SyncManager", "Syncing now because we haven't synced before")
				requestSync(forceFull = true)
			}
		}

		syncJob = scope.launch {
			while (isActive) {
				requestSync()
				delay(15.minutes)
			}
		}
	}

	fun triggerManualSync() {
		requestSync(forceFull = true)
	}

	fun stopPeriodicSync() {
		syncJob?.cancel()
		syncState.value = SyncState(isSyncing = false)
	}

	fun enqueueAction(actionType: SyncActionType, itemId: String) {
		scope.launch {
			syncDao.enqueue(
				SyncActionEntity(
					actionType = actionType,
					itemId = itemId,
					createdAtEpochMs = Clock.System.now().toEpochMilliseconds()
				)
			)
			requestSync()
		}
	}

	private fun requestSync(forceFull: Boolean = false) {
		scope.launch {
			requestMutex.withLock {
				fullSyncRequested = fullSyncRequested || forceFull
			}
			syncWakeups.trySend(Unit)
		}
	}

	private suspend fun runSyncCycle(forceFull: Boolean) {
		processDueQueueActions()
		scheduleNextRetryWakeup()

		val currentTime = Clock.System.now()
		if (forceFull || currentTime - Instant.fromEpochMilliseconds(preferenceManager.lastFullSyncTime) > fullSyncThreshold) {
				Logger.i("SyncManager", "Starting full library pull...")

				syncState.update {
					it.copy(isSyncing = true)
				}

				try {
					val result = repository.syncEverything { progress, message ->
						syncState.update {
							it.copy(isSyncing = true, progress = progress, message = message)
						}
					}

					if (result.isSuccess) {
						preferenceManager.lastFullSyncTime = currentTime.toEpochMilliseconds()
						Logger.i("SyncManager", "Full library sync complete.")
					}

				} finally {
					syncState.update {
						it.copy(isSyncing = false, message = Res.string.info_status_idle)
					}
				}
		}
	}

	private suspend fun scheduleNextRetryWakeup() {
		retryWakeJob?.cancel()
		val nowEpochMs = Clock.System.now().toEpochMilliseconds()
		val nextRetryEpochMs = syncDao.getNextRetryEpochMs(nowEpochMs) ?: return
		retryWakeJob = scope.launch {
			delay((nextRetryEpochMs - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L))
			requestSync()
		}
	}

	private suspend fun processDueQueueActions() {
		val actions = syncDao.getDueActions(Clock.System.now().toEpochMilliseconds())
		if (actions.isEmpty()) return

		processOrderedSyncActions(
			actions = actions,
			execute = { action ->
				when (action.actionType) {
					SyncActionType.STAR -> sessionManager.api.star(action.itemId)
					SyncActionType.UNSTAR -> sessionManager.api.unstar(action.itemId)
					SyncActionType.DELETE_PLAYLIST -> sessionManager.api.deletePlaylist(action.itemId)
					SyncActionType.SCROBBLE -> sessionManager.api.scrobble(
						action.itemId,
						submission = true
					)

					SyncActionType.STAR_0 -> sessionManager.api.setRating(action.itemId, 0)
					SyncActionType.STAR_1 -> sessionManager.api.setRating(action.itemId, 1)
					SyncActionType.STAR_2 -> sessionManager.api.setRating(action.itemId, 2)
					SyncActionType.STAR_3 -> sessionManager.api.setRating(action.itemId, 3)
					SyncActionType.STAR_4 -> sessionManager.api.setRating(action.itemId, 4)
					SyncActionType.STAR_5 -> sessionManager.api.setRating(action.itemId, 5)
				}
			},
			onSuccess = { action ->
				syncDao.removeAction(action.id)
				Logger.i(
					"SyncManager",
					"Successfully synced ${action.actionType} for ${action.itemId}"
				)
			},
			onFailure = { action, error ->
				if (error is CancellationException) throw error
				val disposition = classifySyncFailure(
					httpStatus = (error as? ResponseException)?.response?.status?.value,
					subsonicErrorCode = (error as? SubsonicException)?.code
				)
				val failedAction = action.afterSyncFailure(
					disposition = disposition,
					nowEpochMs = Clock.System.now().toEpochMilliseconds(),
					errorSummary = (error.message ?: error::class.simpleName ?: "Unknown error").take(500)
				)
				syncDao.updateAction(failedAction)
				Logger.e(
					"SyncManager",
					"Failed ${action.actionType} for ${action.itemId}; disposition=$disposition",
					error
				)
			}
		)
	}
}
