package paige.navic.ui.screens.nowPlaying.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.LidaClipDownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.LIDA_CLIPS_PREFETCH_REFRESH_AFTER_MILLIS
import paige.navic.domain.models.isCachedLidaClipStreamUrl
import paige.navic.domain.models.nextLidaClipsPrefetchKey
import paige.navic.domain.models.shouldShowLidaClipsMusicVideoAction
import paige.navic.domain.models.shouldTreatLidaClipAsMusicVideo
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import paige.navic.ui.core.EnrichmentRequestOwner
import kotlin.time.Clock

class NowPlayingViewModel(
	private val player: MediaPlayerViewModel,
	private val songRepository: SongRepository,
	private val lidaClipsRepository: LidaClipsRepository,
	private val lyricsRepository: LyricsRepository,
	private val lidaClipDownloadManager: LidaClipDownloadManager,
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
	private var lastLyricsProgress: Float? = null
	private val lyricsLookup = EnrichmentRequestOwner()
	private val visualContentActive = MutableStateFlow(false)
	private var displayedSongId: String? = null
	private var previousDemandActive = false
	private val integrationEnabledListenerRemovers = mutableListOf<() -> Unit>()

	init {
		integrationEnabledListenerRemovers += preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.LidaClips) { enabled ->
			if (!enabled) clearLidaClip()
		}
		viewModelScope.launch {
			visualEnrichmentDemand(player.uiState, visualContentActive, player.playbackStartFeedback.state).collect { state ->
				val song = state.song
				val resuming = state.canRequest && !previousDemandActive
				previousDemandActive = state.canRequest
				if (displayedSongId != song?.id) {
					displayedSongId = song?.id
					clearLidaClip()
					clearLyrics()
					if (song != null) {
						_lidaClipState.value = UiState.Loading(null)
						_lyricsAvailableState.value = UiState.Loading(false)
					}
				}
				if (song == null) {
					_songIsStarred.value = false
					_songRating.value = 0
					clearLidaClip()
					clearLyrics()
				} else {
					if (!state.canRequest) {
						deferActiveLyricsLookup()
						return@collect
					}
					if (resuming) {
						if (_lidaClipState.value is UiState.Error) lastLidaClipsPrefetchKey = null
					}
					val previousLyricsProgress = lastLyricsProgress
					_songIsStarred.value = songRepository.isSongStarred(song)
					_songRating.value = songRepository.getSongRating(song)
					loadLidaClip(song)
					loadLyrics(
						song = song,
						previousProgress = previousLyricsProgress,
						currentProgress = state.progress,
						resuming = resuming
					)
					lastLyricsProgress = state.progress
				}
			}
		}
	}

	fun setVisualContentActive(active: Boolean) {
		visualContentActive.value = active
		if (!active) {
			previousDemandActive = false
			deferActiveLyricsLookup()
		}
	}

	private fun deferActiveLyricsLookup() {
		if (lyricsLookup.cancel() && _lyricsAvailableState.value is UiState.Loading) {
			currentLyricsSongId = null
			lastLyricsProgress = null
		}
	}

	private fun canRequestVisualContent(songId: String): Boolean =
		visualContentActive.value && player.playbackStartFeedback.state.value == null &&
			player.uiState.value.currentSong?.id == songId

	private fun deferClipLookup(songId: String) {
		if (currentLidaClipSongId == songId) {
			lastLidaClipsPrefetchKey = null
			lastLidaClipsPrefetchTimeMillis = null
		}
	}

	override fun onCleared() {
		integrationEnabledListenerRemovers.forEach { removeListener -> removeListener() }
		integrationEnabledListenerRemovers.clear()
		super.onCleared()
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
		if (!canRequestVisualContent(song.id)) return
		if (!canLoadLidaClip(song.id)) {
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
		_lidaClipState.value = UiState.Loading(
			if (forceRefresh) null else _lidaClipState.value.data
		)
		lidaClipLookupJob = viewModelScope.launch {
			withContext(Dispatchers.IO) {
				lidaClipsRepository.findClipForSong(song, forceRefresh = forceRefresh)
			}
				.also { currentCoroutineContext().ensureActive() }
				.onSuccess { clip ->
					if (!canRequestVisualContent(song.id)) {
						// Discovery may finish after locking. Do not consume demand for resume.
						deferClipLookup(song.id)
						return@onSuccess
					}
					val cachedClipResult = clip
						?.takeIf { shouldTreatLidaClipAsMusicVideo(it) }
						?.let {
							val persistOffline = downloadManager.isDownloaded(song.id)
							if (!canRequestVisualContent(song.id)) {
								deferClipLookup(song.id)
								return@onSuccess
							}
							lidaClipDownloadManager.getOrQueueClipForPlayback(
								songId = song.id,
								clip = it,
								persistOffline = persistOffline,
								canStartRequest = { canRequestVisualContent(song.id) }
							)
						}
					currentCoroutineContext().ensureActive()
					if (!canRequestVisualContent(song.id)) {
						deferClipLookup(song.id)
						return@onSuccess
					}
					if (currentLidaClipSongId == song.id) {
						if (!canLoadLidaClip(song.id)) {
							clearLidaClip()
							return@onSuccess
						}
						_lidaClipState.value = cachedClipResult?.fold(
							onSuccess = { cachedClip ->
								UiState.Success(
									cachedClip?.takeIf {
										isCachedLidaClipStreamUrl(it.streamUrl)
									}
								)
							},
							onFailure = { error ->
								UiState.Error(
									error as? Exception ?: Exception(error.message, error),
									if (forceRefresh) null else _lidaClipState.value.data
								)
							}
						) ?: UiState.Success(null)
					}
				}
				.onFailure { error ->
					if (currentLidaClipSongId == song.id) {
						if (!canLoadLidaClip(song.id)) {
							clearLidaClip()
							return@onFailure
						}
						_lidaClipState.value = UiState.Error(
							error as? Exception ?: Exception(error.message, error),
							if (forceRefresh) null else _lidaClipState.value.data
						)
					}
				}
		}
	}

	private fun canLoadLidaClip(songId: String): Boolean =
		shouldShowLidaClipsMusicVideoAction(
			lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
			lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
			userActionEnabled = true,
			songId = songId
		)

	private fun loadLyrics(
		song: DomainSong,
		previousProgress: Float?,
		currentProgress: Float,
		resuming: Boolean
	) {
		if (!canRequestVisualContent(song.id)) return
		if (!shouldStartLyricsLookup(
				currentSongId = currentLyricsSongId,
				requestedSongId = song.id,
				lyricsState = _lyricsAvailableState.value,
				previousProgress = previousProgress,
				currentProgress = currentProgress,
				resuming = resuming
			)
		) return

		currentLyricsSongId = song.id
		_lyricsAvailableState.value = UiState.Loading(false)
		lyricsLookup.launch(viewModelScope) {
			runCatching { withContext(Dispatchers.IO) { lyricsRepository.fetchLyrics(song) } }
				.also { currentCoroutineContext().ensureActive() }
				.onSuccess { result ->
					lyricsLookup.commit {
						if (currentLyricsSongId == song.id) {
							_lyricsAvailableState.value = UiState.Success(!result?.lines.isNullOrEmpty())
						}
					}
				}
				.onFailure { error ->
					if (error is CancellationException) throw error
					lyricsLookup.commit {
						if (currentLyricsSongId == song.id) {
							_lyricsAvailableState.value = UiState.Error(
								error as? Exception ?: Exception(error.message, error),
								false
							)
						}
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
		lastLyricsProgress = null
		lyricsLookup.cancel()
		_lyricsAvailableState.value = UiState.Success(false)
	}
}
