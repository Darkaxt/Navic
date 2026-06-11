package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.readerPublicationResourceLogLabel
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.ui.core.UiState
import paige.navic.util.core.Logger

class BinderyAudiobookPlayerViewModel(
	private val bookId: String,
	private val repository: BinderyRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _manifestState = MutableStateFlow<UiState<BinderyManifest>>(UiState.Loading())
	val manifestState = _manifestState.asStateFlow()
	private val _findingsState = MutableStateFlow<UiState<BinderyCatalog>>(UiState.Loading())
	val findingsState = _findingsState.asStateFlow()
	private val _providerCoverUrlState = MutableStateFlow<UiState<String?>>(UiState.Success(null))
	val providerCoverUrlState = _providerCoverUrlState.asStateFlow()

	private var manifestJob: Job? = null
	private var providerCoverJob: Job? = null
	private var lastSavedProgress: BinderyAudiobookPlaybackProgress? = null

	fun refreshManifest(fullRefresh: Boolean = false) {
		manifestJob?.cancel()
		manifestJob = viewModelScope.launch {
			val currentData = _manifestState.value.data
			if (fullRefresh || currentData == null) {
				_manifestState.value = UiState.Loading(currentData)
			}
			val currentFindings = _findingsState.value.data
			if (fullRefresh || currentFindings == null) {
				_findingsState.value = UiState.Loading(currentFindings)
			}
			repository.getManifest(bookId).fold(
				onSuccess = { manifest ->
					_manifestState.value = UiState.Success(manifest)
					repository.getBookFindings(bookId).fold(
						onSuccess = { findings ->
							_findingsState.value = UiState.Success(findings)
						},
						onFailure = {
							_findingsState.value = UiState.Success(currentFindings ?: BinderyCatalog(title = "Findings"))
						}
					)
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

	fun refreshProviderCover(
		finding: BinderyFindingMetadata?,
		fallbackCoverHref: String?
	) {
		providerCoverJob?.cancel()
		if (finding == null) {
			Logger.i(
				BinderyAudiobookCoverLogTag,
				"provider cover skipped book=$bookId reason=no-associated-finding fallback=${fallbackCoverHref?.let(::readerPublicationResourceLogLabel) ?: "<none>"}"
			)
			_providerCoverUrlState.value = UiState.Success(null)
			return
		}
		val findingId = finding.findingId?.trim()?.takeIf { it.isNotEmpty() } ?: "<unknown>"
		Logger.i(
			BinderyAudiobookCoverLogTag,
			"provider cover resolve book=$bookId finding=$findingId provider=${finding.providerKind ?: finding.provider.orEmpty()} source=${finding.sourceUrl?.let(::readerPublicationResourceLogLabel) ?: "<none>"} fallback=${fallbackCoverHref?.let(::readerPublicationResourceLogLabel) ?: "<none>"}"
		)
		_providerCoverUrlState.value = UiState.Loading(null)
		providerCoverJob = viewModelScope.launch {
			repository.getFindingProviderCoverUrl(finding).fold(
				onSuccess = { coverUrl ->
					Logger.i(
						BinderyAudiobookCoverLogTag,
						"provider cover resolved book=$bookId finding=$findingId cover=${coverUrl?.let(::readerPublicationResourceLogLabel) ?: "<fallback>"}"
					)
					_providerCoverUrlState.value = UiState.Success(coverUrl)
				},
				onFailure = { error ->
					Logger.w(
						BinderyAudiobookCoverLogTag,
						"provider cover resolution failed book=$bookId finding=$findingId; using fallback",
						error
					)
					_providerCoverUrlState.value = UiState.Success(null)
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

private const val BinderyAudiobookCoverLogTag = "BinderyAudiobookCover"
