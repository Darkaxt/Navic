package paige.navic.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.ui.core.PlayerUiState
import paige.navic.ui.core.durablePlayerStateKey
import paige.navic.ui.core.restoredPlayerStateForPreferences
import paige.navic.util.core.Logger
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerViewModel(
	private val stateRepository: PlayerStateRepository,
	protected val connectivityManager: ConnectivityManager,
	protected val downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {

	@Suppress("PropertyName")
	protected val _uiState = MutableStateFlow(PlayerUiState())
	val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

	// Narrow views of uiState so always-on chrome (mini player, bottom bar) can subscribe to
	// only the slice they render and avoid recomposing on every playback-position tick.
	val currentSongFlow: Flow<DomainSong?> = uiState.map { it.currentSong }.distinctUntilChanged()
	val isPausedFlow: Flow<Boolean> = uiState.map { it.isPaused }.distinctUntilChanged()
	val progressFlow: Flow<Float> = uiState.map { it.progress }.distinctUntilChanged()
	val isLoadingFlow: Flow<Boolean> = uiState.map { it.isLoading }.distinctUntilChanged()
	val playbackDownloadProgressFlow: Flow<Float?> =
		uiState.map { it.playbackDownloadProgress }.distinctUntilChanged()

	private val _seekEvents = MutableSharedFlow<Float>(extraBufferCapacity = 1)
	val seekEvents: SharedFlow<Float> = _seekEvents.asSharedFlow()

	protected fun publishSeekEvent(normalized: Float) {
		_seekEvents.tryEmit(normalized.coerceIn(0f, 1f))
	}

	protected fun isAvailable(songId: String): Boolean {
		val isOnline = connectivityManager.isOnline.value
		val isDownloaded = downloadManager.downloadedSongs.value.containsKey(songId)
		return isOnline || isDownloaded
	}

	init {
		viewModelScope.launch {
			restoreState()
			observeAndSaveState()
		}
	}

	abstract fun addToQueueSingle(song: DomainSong, notify: Boolean = true)
	abstract fun addToQueue(collection: DomainSongCollection, notify: Boolean = true)
	abstract fun addToQueue(songs: List<DomainSong>, notify: Boolean = true)
	abstract fun removeFromQueue(index: Int)
	abstract fun moveQueueItem(fromIndex: Int, toIndex: Int)
	abstract fun applyDiscoverQueueFilter(onComplete: (removedCount: Int) -> Unit = {})
	abstract fun clearQueue()
	abstract fun setPlaybackOrigin(origin: PlaybackOrigin?)
	abstract fun playAt(index: Int)
	abstract fun playCollection(collection: DomainSongCollection, startSong: DomainSong)
	abstract fun playNextSingle(song: DomainSong)
	abstract fun playNext(collection: DomainSongCollection)
	abstract fun startSongRadio(song: DomainSong)
	abstract fun playRadio(radio: DomainRadio)
	abstract fun pause()
	abstract fun resume()
	abstract fun seek(normalized: Float)
	abstract fun next()
	abstract fun previous()
	abstract fun toggleShuffle()
	abstract fun toggleRepeat()
	abstract fun shufflePlay(collection: DomainSongCollection)
	abstract fun setPlaybackSpeed(value: Float)
	abstract fun setPlaybackPitch(value: Float)
	abstract fun openSystemEqualizer(): Boolean
	abstract fun refreshAudioEffects()
	abstract fun refreshPlaybackVolume()
	open fun setNowPlayingVideoClipAudioActive(active: Boolean) {}

	fun playNow(song: DomainSong) {
		clearQueue()
		addToQueueSingle(song, notify = false)
		playAt(0)
	}

	fun playNow(collection: DomainSongCollection, startIndex: Int = 0) {
		clearQueue()
		addToQueue(collection, notify = false)
		playAt(startIndex)
	}

	fun playNow(songs: List<DomainSong>, startIndex: Int = 0) {
		clearQueue()
		addToQueue(songs, notify = false)
		playAt(startIndex)
	}

	fun togglePlay() {
		if (!_uiState.value.isPaused) {
			pause()
		} else {
			resume()
		}
	}

	abstract fun syncPlayerWithState(state: PlayerUiState)

	private suspend fun restoreState() {
		val savedJson = stateRepository.loadState()
		if (!savedJson.isNullOrBlank()) {
			try {
				val restoredState = Json.decodeFromJsonElement<PlayerUiState>(
					Json.parseToJsonElement(savedJson)
				)
				val stateToApply = restoredPlayerStateForPreferences(
					restoredState = restoredState,
					persistentQueue = preferenceManager.persistentQueue,
					resumePlaybackOnStartup = preferenceManager.resumePlaybackOnStartup
				) ?: return

				_uiState.value = stateToApply

				syncPlayerWithState(stateToApply)

			} catch (e: Exception) {
				Logger.e("MediaPlayerViewModel", "Failed to restore state!", e)
				_uiState.value = PlayerUiState()
			}
		}
	}

	@OptIn(FlowPreview::class)
	private fun observeAndSaveState() {
		viewModelScope.launch {
			val structuralSnapshots = _uiState.distinctUntilChangedBy {
				it.durablePlayerStateKey()
			}
			val progressSnapshots = _uiState.sample(5.seconds)

			merge(structuralSnapshots, progressSnapshots).collect(::persistState)
		}
	}

	private suspend fun persistState(state: PlayerUiState) {
		try {
			if (!preferenceManager.persistentQueue) {
				stateRepository.clearState()
				return
			}

			val jsonString = Json.encodeToString(state)
			stateRepository.saveState(jsonString)
		} catch (e: Exception) {
			Logger.e("MediaPlayerViewModel", "Failed to save state!", e)
		}
	}
}
