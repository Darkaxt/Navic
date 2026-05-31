package paige.navic.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.ui.core.UiState

class MostPlayedShortcutsViewModel(
	private val repository: PlaybackOriginRepository
) : ViewModel() {
	private val _shortcutsState =
		MutableStateFlow<UiState<ImmutableList<DomainMostPlayedShortcut>>>(UiState.Loading())
	val shortcutsState = _shortcutsState.asStateFlow()

	init {
		viewModelScope.launch {
			repository.observeMostPlayed(MOST_PLAYED_LIMIT)
				.catch { error ->
					_shortcutsState.value = UiState.Error(
						error.asException(),
						_shortcutsState.value.data
					)
				}
				.collect { shortcuts ->
					_shortcutsState.value = UiState.Success(shortcuts)
				}
		}
	}

	fun clearError() {
		_shortcutsState.value = UiState.Success(_shortcutsState.value.data ?: persistentListOf())
	}

	private fun Throwable.asException(): Exception =
		this as? Exception ?: Exception(this)

	private companion object {
		const val MOST_PLAYED_LIMIT = 20
	}
}
