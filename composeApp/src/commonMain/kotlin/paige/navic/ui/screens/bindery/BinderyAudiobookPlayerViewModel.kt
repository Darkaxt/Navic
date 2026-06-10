package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.ui.core.UiState

class BinderyAudiobookPlayerViewModel(
	private val bookId: String,
	private val repository: BinderyRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _manifestState = MutableStateFlow<UiState<BinderyManifest>>(UiState.Loading())
	val manifestState = _manifestState.asStateFlow()

	private var manifestJob: Job? = null
	private var lastSavedProgress: BinderyAudiobookPlaybackProgress? = null

	fun refreshManifest(fullRefresh: Boolean = false) {
		manifestJob?.cancel()
		manifestJob = viewModelScope.launch {
			val currentData = _manifestState.value.data
			if (fullRefresh || currentData == null) {
				_manifestState.value = UiState.Loading(currentData)
			}
			repository.getManifest(bookId).fold(
				onSuccess = { manifest ->
					_manifestState.value = UiState.Success(manifest)
				},
				onFailure = { error ->
					_manifestState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
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
