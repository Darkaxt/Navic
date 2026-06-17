package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.ui.core.UiState

class BinderyAudiobookPlayerViewModel(
	private val bookId: String,
	private val audiobookId: String,
	private val repository: BinderyRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _manifestState = MutableStateFlow<UiState<BinderyManifest>>(UiState.Loading())
	val manifestState = _manifestState.asStateFlow()
	private val _detailState = MutableStateFlow<UiState<BinderyAudiobookVersion>>(UiState.Loading())
	val detailState = _detailState.asStateFlow()

	private var manifestJob: Job? = null
	private var lastSavedProgress: BinderyAudiobookPlaybackProgress? = null

	fun refreshManifest(fullRefresh: Boolean = false) {
		manifestJob?.cancel()
		manifestJob = viewModelScope.launch {
			val currentData = _manifestState.value.data
			val cachedManifest = if (!fullRefresh && currentData == null) {
				repository.getCachedAudiobookManifest(audiobookId).getOrNull()
			} else {
				null
			}
			if (cachedManifest != null) {
				_manifestState.value = UiState.Success(cachedManifest)
			}
			val visibleManifest = cachedManifest ?: currentData
			if (fullRefresh || visibleManifest == null) {
				_manifestState.value = UiState.Loading(visibleManifest)
			}
			val currentDetail = _detailState.value.data
			val cachedDetail = if (!fullRefresh && currentDetail == null) {
				repository.getCachedAudiobookDetail(audiobookId).getOrNull()
			} else {
				null
			}
			if (cachedDetail != null) {
				_detailState.value = UiState.Success(cachedDetail)
			}
			val visibleDetail = cachedDetail ?: currentDetail
			if (fullRefresh || visibleDetail == null) {
				_detailState.value = UiState.Loading(visibleDetail)
			}
			repository.getAudiobookDetail(audiobookId).fold(
				onSuccess = { detail ->
					val state = _detailState.value
					if (state !is UiState.Success || state.data != detail) {
						_detailState.value = UiState.Success(detail)
					}
				},
				onFailure = { error ->
					_detailState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = visibleDetail
					)
				}
			)
			repository.getAudiobookManifest(audiobookId).fold(
				onSuccess = { manifest ->
					val state = _manifestState.value
					if (state !is UiState.Success || state.data != manifest) {
						_manifestState.value = UiState.Success(manifest)
					}
				},
				onFailure = { error ->
					_manifestState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = visibleManifest
					)
				}
			)
		}
	}

	fun clearError() {
		_manifestState.value = _manifestState.value.data?.let { UiState.Success(it) } ?: UiState.Loading()
	}

	fun rememberedProgress(versionRowId: String): BinderyAudiobookPlaybackProgress? =
		binderyAudiobookSavedProgress(
			json = preferenceManager.binderyAudiobookProgressJson,
			bookId = bookId,
			versionRowId = versionRowId
		).also { progress ->
			lastSavedProgress = progress
		}

	fun savePlaybackProgress(
		versionRowId: String,
		position: ReadaloudPlaybackPosition,
		updatedAtMs: Long = Clock.System.now().toEpochMilliseconds()
	) {
		val next = binderyAudiobookProgressForPosition(
			bookId = bookId,
			versionRowId = versionRowId,
			position = position,
			updatedAtMs = updatedAtMs
		) ?: return
		if (!shouldAutosaveBinderyAudiobookProgress(lastSavedProgress, next)) return
		preferenceManager.binderyAudiobookProgressJson = binderyAudiobookProgressJsonWithUpdate(
			json = preferenceManager.binderyAudiobookProgressJson,
			progress = next
		)
		lastSavedProgress = next
	}
}
