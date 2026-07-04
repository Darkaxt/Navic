package paige.navic.shared

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import paige.navic.domain.manager.AudioPlaybackArbitrator
import paige.navic.domain.models.AudioPlaybackOwner
import paige.navic.domain.models.shouldPauseForAudioPlaybackClaim
import paige.navic.reader.ReadaloudAudioController
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.reader.metadataLabelsForPlaybackPosition
import paige.navic.reader.normalizedReadaloudPlaybackSpeed
import paige.navic.reader.toReadaloudPlaybackMetadataLabels
import paige.navic.ui.core.AudiobookMiniPlayerUiState

class AndroidAudiobookPlaybackManager(
	application: Application,
	private val audioPlaybackArbitrator: AudioPlaybackArbitrator
) : AudiobookPlaybackManager {
	private val scope = MainScope()
	private val controller = ReadaloudAudioController(application, ::publishPosition)
	private val _uiState = MutableStateFlow(AudiobookMiniPlayerUiState())
	override val uiState: StateFlow<AudiobookMiniPlayerUiState> = _uiState.asStateFlow()

	private var activePlan: ReadaloudPlaybackPlan? = null
	private var activeBookId: String? = null
	private var activeBookTitle: String? = null
	private var activeVersionRowId: String? = null
	private var activeCoverUrl: String? = null
	private var activeCoverCacheKey: String? = null
	private var activeImageRequestHeaders: Map<String, String> = emptyMap()

	init {
		scope.launch {
			audioPlaybackArbitrator.claims.collectLatest { claimedOwner ->
				if (
					shouldPauseForAudioPlaybackClaim(
						currentOwner = AudioPlaybackOwner.Audiobook,
						claimedOwner = claimedOwner,
						isPlaying = _uiState.value.isPlaying
					)
				) {
					controller.pause()
				}
			}
		}
	}

	override fun load(
		playbackPlan: ReadaloudPlaybackPlan?,
		bookId: String,
		bookTitle: String,
		versionRowId: String,
		coverUrl: String?,
		coverCacheKey: String?,
		imageRequestHeaders: Map<String, String>,
		playWhenReady: Boolean
	) {
		if (playbackPlan == null) {
			_uiState.value = AudiobookMiniPlayerUiState()
			activePlan = null
			activeBookId = null
			activeBookTitle = null
			activeVersionRowId = null
			activeCoverUrl = null
			activeCoverCacheKey = null
			activeImageRequestHeaders = emptyMap()
			return
		}

		activeCoverUrl = coverUrl
		activeCoverCacheKey = coverCacheKey
		activeImageRequestHeaders = imageRequestHeaders
		if (
			activeBookId == bookId &&
			activeVersionRowId == versionRowId &&
			activePlan?.sessionId == playbackPlan.sessionId &&
			_uiState.value.isAvailable
		) {
			activeBookTitle = bookTitle
			_uiState.value = _uiState.value.copy(
				bookTitle = bookTitle,
				coverUrl = activeCoverUrl,
				coverCacheKey = activeCoverCacheKey,
				imageRequestHeaders = activeImageRequestHeaders
			)
			return
		}

		activePlan = playbackPlan
		activeBookId = bookId
		activeBookTitle = bookTitle
		activeVersionRowId = versionRowId
		if (playWhenReady) {
			audioPlaybackArbitrator.claim(AudioPlaybackOwner.Audiobook)
		}
		controller.load(playbackPlan, playWhenReady = playWhenReady)
		_uiState.value = playbackPlan.initialUiState(
			bookId = bookId,
			bookTitle = bookTitle,
			versionRowId = versionRowId,
			coverUrl = activeCoverUrl,
			coverCacheKey = activeCoverCacheKey,
			imageRequestHeaders = activeImageRequestHeaders,
			isPlaying = playWhenReady
		)
	}

	override fun dispatch(command: ReaderReadaloudPlaybackCommand) {
		when (command) {
			ReaderReadaloudPlaybackCommand.Play -> {
				audioPlaybackArbitrator.claim(AudioPlaybackOwner.Audiobook)
				controller.play()
			}
			ReaderReadaloudPlaybackCommand.Pause -> controller.pause()
			ReaderReadaloudPlaybackCommand.StopAndReset -> controller.stopAndReset()
			is ReaderReadaloudPlaybackCommand.SeekTo -> controller.seekTo(command.positionMs)
			is ReaderReadaloudPlaybackCommand.SeekToTrack -> controller.seekTo(command.trackIndex, command.positionMs)
			is ReaderReadaloudPlaybackCommand.SetSpeed -> {
				controller.setPlaybackSpeed(command.speed)
				_uiState.value = _uiState.value.copy(playbackSpeed = normalizedReadaloudPlaybackSpeed(command.speed))
			}
			is ReaderReadaloudPlaybackCommand.SetSyncEnabled -> {
				if (!command.enabled) controller.stopAndReset()
			}
		}
	}

	private fun publishPosition(position: ReadaloudPlaybackPosition) {
		val plan = activePlan
		val metadata = plan?.metadataLabelsForPlaybackPosition(position)
		val item = plan?.mediaItems?.getOrNull(position.trackIndex)
			?: position.mediaId?.let { mediaId -> plan?.mediaItems?.firstOrNull { it.mediaId == mediaId } }
		_uiState.value = AudiobookMiniPlayerUiState(
			isAvailable = plan?.mediaItems?.isNotEmpty() == true,
			isPlaying = position.isPlaying,
			bookId = activeBookId,
			bookTitle = activeBookTitle ?: plan?.title,
			versionRowId = activeVersionRowId,
			coverUrl = activeCoverUrl,
			coverCacheKey = activeCoverCacheKey,
			imageRequestHeaders = activeImageRequestHeaders,
			chapterLabel = metadata?.chapterLabel ?: item?.title,
			sectionLabel = metadata?.sectionLabel ?: item?.subtitle,
			narratorLabel = metadata?.narratorLabel ?: item?.artist,
			trackIndex = position.trackIndex,
			mediaId = position.mediaId,
			positionMs = position.positionMs,
			durationMs = position.durationMs ?: item?.durationMs,
			playbackSpeed = position.playbackSpeed,
			activeAudioMetadata = metadata
		)
	}
}

private fun ReadaloudPlaybackPlan.initialUiState(
	bookId: String,
	bookTitle: String,
	versionRowId: String,
	coverUrl: String?,
	coverCacheKey: String?,
	imageRequestHeaders: Map<String, String>,
	isPlaying: Boolean
): AudiobookMiniPlayerUiState {
	val item = mediaItems.getOrNull(startTrackIndex)
	val metadata = item?.toReadaloudPlaybackMetadataLabels()
	return AudiobookMiniPlayerUiState(
		isAvailable = mediaItems.isNotEmpty(),
		isPlaying = isPlaying,
		bookId = bookId,
		bookTitle = bookTitle,
		versionRowId = versionRowId,
		coverUrl = coverUrl,
		coverCacheKey = coverCacheKey,
		imageRequestHeaders = imageRequestHeaders,
		chapterLabel = metadata?.chapterLabel ?: item?.title,
		sectionLabel = metadata?.sectionLabel ?: item?.subtitle,
		narratorLabel = metadata?.narratorLabel ?: item?.artist,
		trackIndex = startTrackIndex,
		mediaId = item?.mediaId,
		positionMs = startPositionMs,
		durationMs = item?.durationMs,
		playbackSpeed = playbackSpeed,
		activeAudioMetadata = metadata
	)
}
