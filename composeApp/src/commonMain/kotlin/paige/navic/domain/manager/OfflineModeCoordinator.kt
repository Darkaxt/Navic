package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import paige.navic.domain.models.AutomaticOfflineReason
import paige.navic.domain.models.OfflineModeState
import paige.navic.domain.models.offlineModeState

class OfflineModeCoordinator internal constructor(
	preferenceManager: PreferenceManager,
	scope: CoroutineScope
) {
	constructor(preferenceManager: PreferenceManager) : this(
		preferenceManager = preferenceManager,
		scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	)

	private val automaticReason = MutableStateFlow<AutomaticOfflineReason?>(null)

	val state: StateFlow<OfflineModeState> = combine(
		preferenceManager.offlineModeState,
		automaticReason,
		::offlineModeState
	).stateIn(
		scope = scope,
		started = SharingStarted.Eagerly,
		initialValue = offlineModeState(preferenceManager.offlineMode, null)
	)

	fun enterAutomatic(reason: AutomaticOfflineReason): Boolean =
		automaticReason.compareAndSet(expect = null, update = reason)

	fun clearAutomatic(reason: AutomaticOfflineReason): Boolean =
		automaticReason.compareAndSet(expect = reason, update = null)
}
