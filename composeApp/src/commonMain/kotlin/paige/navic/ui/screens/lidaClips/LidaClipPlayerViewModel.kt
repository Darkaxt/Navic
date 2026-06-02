package paige.navic.ui.screens.lidaClips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.LidaClipDownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.isCachedLidaClipStreamUrl
import paige.navic.domain.models.shouldShowLidaClipsMusicVideoAction
import paige.navic.domain.models.shouldTreatLidaClipAsMusicVideo
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.ui.core.UiState

class LidaClipPlayerViewModel(
	private val songId: String,
	private val collectionRepository: CollectionRepository,
	private val repository: LidaClipsRepository,
	private val lidaClipDownloadManager: LidaClipDownloadManager,
	private val downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _clipState = MutableStateFlow<UiState<DomainLidaClip?>>(UiState.Loading())
	val clipState = _clipState.asStateFlow()

	init {
		load()
	}

	fun load(forceRefresh: Boolean = false) {
		if (
			!shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
				lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
				userActionEnabled = true,
				songId = songId
			)
		) {
			_clipState.value = UiState.Success(null)
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			_clipState.value = UiState.Loading(_clipState.value.data)
			val song = runCatching { collectionRepository.getSongById(songId) }.getOrNull()
			val result = if (song != null) {
				repository.findClipForSong(
					song = song,
					forceRefresh = forceRefresh
				)
			} else {
				repository.findClipByNavidromeSongId(
					songId = songId,
					forceRefresh = forceRefresh
				)
			}
			val persistOffline = downloadManager.isDownloaded(song?.id ?: songId)
			val playbackResult = result.fold(
				onSuccess = { clip ->
					if (clip == null || !shouldTreatLidaClipAsMusicVideo(clip)) {
						Result.success(null)
					} else {
						runCatching {
							clipForPlayback(
								clip = clip,
								songId = song?.id ?: songId,
								persistOffline = persistOffline
							)
						}
					}
				},
				onFailure = { Result.failure(it) }
			)
			_clipState.value = playbackResult.fold(
				onSuccess = { clip -> UiState.Success(clip) },
				onFailure = { error ->
					UiState.Error(
						error as? Exception ?: Exception(error.message, error),
						_clipState.value.data
					)
				}
			)
		}
	}

	private suspend fun clipForPlayback(
		clip: DomainLidaClip,
		songId: String,
		persistOffline: Boolean
	): DomainLidaClip {
		val cachedClip = lidaClipDownloadManager.getOrQueueClipForPlayback(
			songId = songId,
			clip = clip,
			persistOffline = persistOffline
		).getOrThrow()
			?: error("LidaClips video cache is disabled")

		if (!isCachedLidaClipStreamUrl(cachedClip.streamUrl)) {
			error("LidaClips video was not cached for playback")
		}
		return cachedClip
	}
}
