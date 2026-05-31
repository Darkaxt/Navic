package paige.navic.ui.screens.lidaClips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.LidaClipCacheManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.lidaClipsStreamRequestHeaders
import paige.navic.ui.core.UiState

class LidaClipPlayerViewModel(
	private val songId: String,
	private val collectionRepository: CollectionRepository,
	private val repository: LidaClipsRepository,
	private val cacheManager: LidaClipCacheManager,
	private val downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _clipState = MutableStateFlow<UiState<DomainLidaClip?>>(UiState.Loading())
	val clipState = _clipState.asStateFlow()

	init {
		load()
	}

	fun load(forceRefresh: Boolean = false) {
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
			_clipState.value = result.fold(
				onSuccess = { clip ->
					UiState.Success(
						clip?.let {
							clipForPlayback(
								clip = it,
								songId = song?.id ?: songId,
								persistOffline = persistOffline
							)
						}
					)
				},
				onFailure = { UiState.Error(Exception(it), _clipState.value.data) }
			)
		}
	}

	private suspend fun clipForPlayback(
		clip: DomainLidaClip,
		songId: String,
		persistOffline: Boolean
	): DomainLidaClip {
		if (!persistOffline) {
			return cacheManager.cachedClipFor(songId, clip) ?: clip
		}

		return cacheManager.getOrCacheClip(
			clip = clip,
			requestHeaders = lidaClipsStreamRequestHeaders(
				baseUrl = preferenceManager.lidaClipsBaseUrl,
				streamUrl = clip.streamUrl,
				requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
			),
			songId = songId,
			persistOffline = true
		).getOrNull() ?: clip
	}
}
