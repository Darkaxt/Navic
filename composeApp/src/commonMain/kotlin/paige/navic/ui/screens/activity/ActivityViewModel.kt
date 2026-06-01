package paige.navic.ui.screens.activity

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
import paige.navic.data.database.entities.LidaClipDownloadEntity
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.LidaClipDownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.downloadQueueDownloads
import paige.navic.domain.models.lidaClipDownloadQueueDownloads
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LidaClipsServiceStatus
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.domain.repositories.configuredLidaClipsBaseUrl
import paige.navic.ui.core.UiState

class ActivityViewModel(
	private val downloadManager: DownloadManager,
	private val lidaClipDownloadManager: LidaClipDownloadManager,
	private val songDao: SongDao,
	private val aurralRepository: AurralRepository,
	private val lidaClipsRepository: LidaClipsRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val downloadItems = downloadManager.allDownloads
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
				val song = songsById[download.songId]
				ActivityDownloadItem(
					songId = download.songId,
					title = song?.title ?: download.songId,
					artistName = song?.artistName,
					albumTitle = song?.albumTitle,
					status = download.status,
					progress = download.progress.coerceIn(0f, 1f)
				)
			}.toImmutableList()
		}
		.flowOn(Dispatchers.IO)
		.stateIn(
			viewModelScope,
			SharingStarted.WhileSubscribed(5000),
			emptyList<ActivityDownloadItem>().toImmutableList()
		)

	val lidaClipDownloadItems = lidaClipDownloadManager.allDownloads
		.map { downloads ->
			lidaClipDownloadQueueDownloads(downloads).toImmutableList()
		}
		.flowOn(Dispatchers.IO)
		.stateIn(
			viewModelScope,
			SharingStarted.WhileSubscribed(5000),
			emptyList<LidaClipDownloadEntity>().toImmutableList()
		)

	private val _aurralStatus = MutableStateFlow<UiState<AurralServiceStatus?>>(UiState.Success(null))
	val aurralStatus = _aurralStatus.asStateFlow()

	private val _lidaClipsStatus = MutableStateFlow<UiState<LidaClipsServiceStatus?>>(UiState.Success(null))
	val lidaClipsStatus = _lidaClipsStatus.asStateFlow()

	fun refresh() {
		refreshAurralStatus()
		refreshLidaClipsStatus()
	}

	fun retryFailedDownloads() {
		downloadManager.retryFailedDownloads()
	}

	fun discardFailedDownloads() {
		downloadManager.discardFailedDownloads()
	}

	fun clearDownloadQueue() {
		downloadManager.clearDownloadQueue()
	}

	fun retryFailedLidaClipDownloads() {
		lidaClipDownloadManager.retryFailedDownloads()
	}

	fun discardFailedLidaClipDownloads() {
		lidaClipDownloadManager.discardFailedDownloads()
	}

	fun clearLidaClipDownloadQueue() {
		lidaClipDownloadManager.clearDownloadQueue()
	}

	fun cancelLidaClipDownload(songId: String) {
		lidaClipDownloadManager.cancelDownload(songId)
	}

	fun cancelAurralAcquisition(item: AurralAcquisitionQueueItem) {
		viewModelScope.launch(Dispatchers.IO) {
			aurralRepository.cancelAcquisitionRequest(item)
				.onFailure { error -> _aurralStatus.value = UiState.Error(error.asException(), _aurralStatus.value.data) }
			refreshAurralStatus()
		}
	}

	fun retryAurralAcquisition(item: AurralAcquisitionQueueItem) {
		viewModelScope.launch(Dispatchers.IO) {
			aurralRepository.retryAcquisitionRequest(item)
				.onFailure { error -> _aurralStatus.value = UiState.Error(error.asException(), _aurralStatus.value.data) }
			refreshAurralStatus()
		}
	}

	private fun refreshAurralStatus() {
		if (
			!preferenceManager.aurralEnabled ||
			configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) == null
		) {
			_aurralStatus.value = UiState.Success(null)
			return
		}
		if (_aurralStatus.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			_aurralStatus.value = UiState.Loading(_aurralStatus.value.data)
			val result = aurralRepository.getServiceStatus()
			_aurralStatus.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { error -> UiState.Error(error.asException(), _aurralStatus.value.data) }
			)
		}
	}

	private fun refreshLidaClipsStatus() {
		if (
			!preferenceManager.lidaClipsEnabled ||
			configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl) == null
		) {
			_lidaClipsStatus.value = UiState.Success(null)
			return
		}
		if (_lidaClipsStatus.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			_lidaClipsStatus.value = UiState.Loading(_lidaClipsStatus.value.data)
			val result = lidaClipsRepository.getServiceStatus()
			_lidaClipsStatus.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { error -> UiState.Error(error.asException(), _lidaClipsStatus.value.data) }
			)
		}
	}

	private fun Throwable.asException(): Exception =
		this as? Exception ?: Exception(this)
}
