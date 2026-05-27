package paige.navic.ui.screens.nowPlaying.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS
import paige.navic.domain.models.LidaClipAvailability
import paige.navic.domain.models.lidaClipAvailability
import paige.navic.domain.models.nextLidaClipsPrefetchKey
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.shared.MediaPlayerViewModel
import kotlin.time.Clock

class NowPlayingViewModel(
	private val player: MediaPlayerViewModel,
	private val songRepository: SongRepository,
	private val lidaClipsRepository: LidaClipsRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel(), KoinComponent {

	private val _songIsStarred = MutableStateFlow(false)
	val songIsStarred = _songIsStarred.asStateFlow()

	private val _songRating = MutableStateFlow(0)
	val songRating = _songRating.asStateFlow()

	private val _lidaClipAvailability = MutableStateFlow(LidaClipAvailability.Unknown)
	val lidaClipAvailability = _lidaClipAvailability.asStateFlow()

	private var lastLidaClipsPrefetchKey: String? = null
	private var lastLidaClipsPrefetchTimeMillis: Long? = null

	init {
		viewModelScope.launch {
			player.uiState.collect { state ->
				state.currentSong?.let { song ->
					_songIsStarred.value = songRepository.isSongStarred(song)
					_songRating.value = songRepository.getSongRating(song)
					prefetchLidaClip(song.id)
				}
			}
		}
	}

	fun starSong(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				player.uiState.value.currentSong?.let { song ->
					_songIsStarred.value = starred
					if (starred) {
						songRepository.starSong(song)
					} else {
						songRepository.unstarSong(song)
					}
				}
			}
		}
	}

	fun rateSong(rating: Int) {
		viewModelScope.launch {
			runCatching {
				player.uiState.value.currentSong?.let { song ->
					_songRating.value = rating
					songRepository.rateSong(song, rating)
				}
			}
		}
	}

	private fun prefetchLidaClip(songId: String) {
		val nowMillis = Clock.System.now().toEpochMilliseconds()
		val nextPrefetchKey = nextLidaClipsPrefetchKey(
			enabled = preferenceManager.lidaClipsEnabled,
			baseUrl = preferenceManager.lidaClipsBaseUrl,
			apiKey = preferenceManager.lidaClipsApiKey,
			songId = songId,
			lastPrefetchKey = lastLidaClipsPrefetchKey,
			lastPrefetchTimeMillis = lastLidaClipsPrefetchTimeMillis,
			currentTimeMillis = nowMillis,
			refreshAfterMillis = LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS
		) ?: return

		lastLidaClipsPrefetchKey = nextPrefetchKey
		lastLidaClipsPrefetchTimeMillis = nowMillis
		_lidaClipAvailability.value = LidaClipAvailability.Unknown
		viewModelScope.launch(Dispatchers.IO) {
			val availability = lidaClipsRepository.findClipByNavidromeSongId(songId).fold(
				onSuccess = ::lidaClipAvailability,
				onFailure = { LidaClipAvailability.Unknown }
			)
			if (lastLidaClipsPrefetchKey == nextPrefetchKey) {
				_lidaClipAvailability.value = availability
			}
		}
	}
}
