package paige.navic.ui.screens.nowPlaying.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.LidaClipCacheManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS
import paige.navic.domain.models.nextLidaClipsPrefetchKey
import paige.navic.domain.models.shouldShowLidaClipsMusicVideoAction
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.domain.repositories.lidaClipsStreamRequestHeaders
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import kotlin.time.Clock

class NowPlayingViewModel(
	private val player: MediaPlayerViewModel,
	private val songRepository: SongRepository,
	private val lidaClipsRepository: LidaClipsRepository,
	private val lyricsRepository: LyricsRepository,
	private val lidaClipCacheManager: LidaClipCacheManager,
	private val downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel(), KoinComponent {

	private val _songIsStarred = MutableStateFlow(false)
	val songIsStarred = _songIsStarred.asStateFlow()

	private val _songRating = MutableStateFlow(0)
	val songRating = _songRating.asStateFlow()

	private val _lidaClipState = MutableStateFlow<UiState<DomainLidaClip?>>(UiState.Success(null))
	val lidaClipState = _lidaClipState.asStateFlow()

	private val _lyricsAvailableState = MutableStateFlow<UiState<Boolean>>(UiState.Success(false))
	val lyricsAvailableState = _lyricsAvailableState.asStateFlow()

	private var lastLidaClipsPrefetchKey: String? = null
	private var lastLidaClipsPrefetchTimeMillis: Long? = null
	private var currentLidaClipSongId: String? = null
	private var lidaClipLookupJob: Job? = null
	private var currentLyricsSongId: String? = null
	private var lyricsLookupJob: Job? = null

	init {
		viewModelScope.launch {
			player.uiState.collect { state ->
				val song = state.currentSong
				if (song == null) {
					_songIsStarred.value = false
					_songRating.value = 0
					clearLidaClip()
					clearLyrics()
				} else {
					_songIsStarred.value = songRepository.isSongStarred(song)
					_songRating.value = songRepository.getSongRating(song)
					loadLidaClip(song)
					loadLyrics(song)
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

	fun refreshLidaClip() {
		player.uiState.value.currentSong?.let { song ->
			loadLidaClip(song, forceRefresh = true)
		}
	}

	private fun loadLidaClip(song: DomainSong, forceRefresh: Boolean = false) {
		if (
			!shouldShowLidaClipsMusicVideoAction(
				lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
				lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
				userActionEnabled = true,
				songId = song.id
			)
		) {
			clearLidaClip()
			return
		}

		if (currentLidaClipSongId != song.id) {
			currentLidaClipSongId = song.id
			_lidaClipState.value = UiState.Success(null)
		}

		val nowMillis = Clock.System.now().toEpochMilliseconds()
		val nextPrefetchKey = nextLidaClipsPrefetchKey(
			enabled = preferenceManager.lidaClipsEnabled,
			baseUrl = preferenceManager.lidaClipsBaseUrl,
			apiKey = preferenceManager.lidaClipsApiKey,
			songId = song.id,
			lastPrefetchKey = if (forceRefresh) null else lastLidaClipsPrefetchKey,
			lastPrefetchTimeMillis = if (forceRefresh) null else lastLidaClipsPrefetchTimeMillis,
			currentTimeMillis = nowMillis,
			refreshAfterMillis = LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS
		) ?: return

		lastLidaClipsPrefetchKey = nextPrefetchKey
		lastLidaClipsPrefetchTimeMillis = nowMillis
		lidaClipLookupJob?.cancel()
		_lidaClipState.value = UiState.Loading(_lidaClipState.value.data)
		lidaClipLookupJob = viewModelScope.launch(Dispatchers.IO) {
			lidaClipsRepository.findClipForSong(song, forceRefresh = forceRefresh)
				.onSuccess { clip ->
					val cachedClip = clip?.let {
						val persistOffline = downloadManager.isDownloaded(song.id)
						lidaClipCacheManager.getOrCacheClip(
							clip = it,
							requestHeaders = lidaClipsStreamRequestHeaders(
								baseUrl = preferenceManager.lidaClipsBaseUrl,
								streamUrl = it.streamUrl,
								requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
							),
							songId = song.id,
							persistOffline = persistOffline
						).getOrElse { null }
					}
					if (currentLidaClipSongId == song.id) {
						_lidaClipState.value = UiState.Success(cachedClip)
					}
				}
				.onFailure { error ->
					if (currentLidaClipSongId == song.id) {
						_lidaClipState.value = UiState.Error(
							error as? Exception ?: Exception(error.message, error),
							_lidaClipState.value.data
						)
					}
				}
		}
	}

	private fun loadLyrics(song: DomainSong) {
		if (currentLyricsSongId == song.id) return

		currentLyricsSongId = song.id
		lyricsLookupJob?.cancel()
		_lyricsAvailableState.value = UiState.Loading(false)
		lyricsLookupJob = viewModelScope.launch(Dispatchers.IO) {
			runCatching { lyricsRepository.fetchLyrics(song) }
				.onSuccess { result ->
					if (currentLyricsSongId == song.id) {
						_lyricsAvailableState.value = UiState.Success(!result?.lines.isNullOrEmpty())
					}
				}
				.onFailure { error ->
					if (currentLyricsSongId == song.id) {
						_lyricsAvailableState.value = UiState.Error(
							error as? Exception ?: Exception(error.message, error),
							false
						)
					}
				}
		}
	}

	private fun clearLidaClip() {
		currentLidaClipSongId = null
		lastLidaClipsPrefetchKey = null
		lastLidaClipsPrefetchTimeMillis = null
		lidaClipLookupJob?.cancel()
		_lidaClipState.value = UiState.Success(null)
	}

	private fun clearLyrics() {
		currentLyricsSongId = null
		lyricsLookupJob?.cancel()
		_lyricsAvailableState.value = UiState.Success(false)
	}
}
