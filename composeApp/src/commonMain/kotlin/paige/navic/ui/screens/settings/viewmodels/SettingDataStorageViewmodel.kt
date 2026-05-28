package paige.navic.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.dao.SyncActionDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.downloadQueueDownloads
import paige.navic.domain.repositories.DbRepository
import paige.navic.domain.repositories.SongRepository

class SettingsDataStorageViewModel(
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository,
	private val syncDao: SyncActionDao,
	private val downloadManager: DownloadManager,
	private val songRepository: SongRepository,
	private val songDao: SongDao,
	connectivityManager: ConnectivityManager
) : ViewModel() {

	val syncState = syncManager.syncState
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = syncManager.syncState.value
		)

	private val _pendingActionCount = MutableStateFlow(0)
	val pendingActionCount = _pendingActionCount.asStateFlow()

	val downloadCount = downloadManager.downloadCount.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), 0
	)
	val downloadSize = downloadManager.downloadSize.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
	)
	val pendingDownloadCount = downloadManager.pendingDownloadCount.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), 0
	)
	val downloadQueueItems = downloadManager.allDownloads
		.map { downloads ->
			val queueDownloads = downloadQueueDownloads(downloads)
			val songsById = if (queueDownloads.isEmpty()) {
				emptyMap()
			} else {
				songDao
					.getSongsByIds(queueDownloads.map { it.songId })
					.associateBy { it.songId }
			}
			queueDownloads.map { download ->
				val song = songsById[download.songId]?.toDomainModel()
				DownloadQueueItem(
					songId = download.songId,
					title = song?.title ?: download.songId,
					artistName = song?.artistName,
					albumTitle = song?.albumTitle,
					status = download.status,
					progress = download.progress.coerceIn(0f, 1f),
					canRetry = song != null && download.status == DownloadStatus.FAILED
				)
			}.toImmutableList()
		}
		.flowOn(Dispatchers.IO)
		.stateIn(
			viewModelScope,
			SharingStarted.WhileSubscribed(5000),
			emptyList<DownloadQueueItem>().toImmutableList()
		)

	val isDownloadingLibrary = downloadManager.isDownloadingLibrary
	val libraryDownloadProgress = downloadManager.libraryDownloadProgress
	val isOnline = connectivityManager.isOnline

	init {
		loadPendingActions()
	}

	private fun loadPendingActions() {
		viewModelScope.launch(Dispatchers.IO) {
			_pendingActionCount.value = syncDao.getPendingActions().size
		}
	}

	fun triggerManualSync() {
		syncManager.triggerManualSync()
	}

	fun rebuildDatabase() {
		viewModelScope.launch(Dispatchers.IO) {
			dbRepository.removeEverything()
			syncManager.stopPeriodicSync()
			_pendingActionCount.value = 0
		}
		triggerManualSync()
	}

	fun removeAllActions() {
		viewModelScope.launch(Dispatchers.IO) {
			syncDao.clearAllActions()
			_pendingActionCount.value = 0
		}
	}

	fun clearAllDownloads() {
		downloadManager.clearAllDownloads()
	}

	fun downloadEntireLibrary() {
		viewModelScope.launch(Dispatchers.IO) {
			val allSongs = songRepository.getAllSongs()
			downloadManager.downloadEntireLibrary(allSongs)
		}
	}

	fun cancelLibraryDownload() {
		downloadManager.cancelAllActiveDownloads()
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun cancelPendingDownloads() {
		downloadManager.cancelAllActiveDownloads()
	}

	fun retryFailedDownloads() {
		downloadManager.retryFailedDownloads()
	}
}

data class DownloadQueueItem(
	val songId: String,
	val title: String,
	val artistName: String?,
	val albumTitle: String?,
	val status: DownloadStatus,
	val progress: Float,
	val canRetry: Boolean
) {
	val canCancel: Boolean
		get() = status == DownloadStatus.DOWNLOADING ||
			status == DownloadStatus.QUEUED ||
			status == DownloadStatus.FAILED
}
