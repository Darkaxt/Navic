package paige.navic.domain.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import paige.navic.domain.models.AutomaticOfflineReason
import paige.navic.util.core.Logger
import kotlin.time.Duration.Companion.seconds

sealed interface NavidromeAvailability {
	data object Available : NavidromeAvailability

	data class Unavailable(
		val trigger: NavidromeOutageTrigger
	) : NavidromeAvailability
}

enum class NavidromeOutageTrigger {
	RawNetworkLost,
	Playback,
	Download
}

enum class NavidromeAvailabilityEventType {
	EnteredOffline,
	DuplicateOutage,
	ProbeFailed,
	Restored
}

data class NavidromeAvailabilityEvent(
	val type: NavidromeAvailabilityEventType,
	val trigger: NavidromeOutageTrigger,
	val error: Throwable? = null
)

class NavidromeAvailabilityManager internal constructor(
	private val networkAvailable: StateFlow<Boolean>,
	private val isLoggedIn: StateFlow<Boolean>,
	private val ping: suspend () -> Unit,
	private val offlineModeCoordinator: OfflineModeCoordinator,
	private val scope: CoroutineScope,
	private val heartbeat: suspend () -> Unit
) {
	constructor(
		connectivityManager: ConnectivityManager,
		sessionManager: SessionManager,
		offlineModeCoordinator: OfflineModeCoordinator
	) : this(
		networkAvailable = connectivityManager.isNetworkAvailable,
		isLoggedIn = sessionManager.isLoggedIn,
		ping = sessionManager::ping,
		offlineModeCoordinator = offlineModeCoordinator,
		scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
		heartbeat = { delay(30.seconds) }
	)

	private val mutableState = MutableStateFlow<NavidromeAvailability>(NavidromeAvailability.Available)
	val state: StateFlow<NavidromeAvailability> = mutableState.asStateFlow()

	private val mutableEvents = MutableSharedFlow<NavidromeAvailabilityEvent>(extraBufferCapacity = 16)
	val events: SharedFlow<NavidromeAvailabilityEvent> = mutableEvents.asSharedFlow()

	private val probeWakeups = Channel<Unit>(capacity = Channel.CONFLATED)

	init {
		scope.launch {
			for (ignored in probeWakeups) probeOnce()
		}
		scope.launch {
			networkAvailable.collect { available ->
				if (available) requestProbe()
			}
		}
		scope.launch {
			isLoggedIn.collect { loggedIn ->
				if (loggedIn) requestProbe()
			}
		}
		scope.launch {
			state.collectLatest { availability ->
				if (availability !is NavidromeAvailability.Unavailable) return@collectLatest
				while (
					currentCoroutineContext().isActive &&
					state.value is NavidromeAvailability.Unavailable
				) {
					heartbeat()
					requestProbe()
				}
			}
		}
	}

	fun reportUnavailable(trigger: NavidromeOutageTrigger, error: Throwable? = null): Boolean {
		val unavailable = NavidromeAvailability.Unavailable(trigger)
		if (!mutableState.compareAndSet(NavidromeAvailability.Available, unavailable)) {
			mutableEvents.tryEmit(
				NavidromeAvailabilityEvent(
					type = NavidromeAvailabilityEventType.DuplicateOutage,
					trigger = trigger,
					error = error
				)
			)
			return false
		}

		offlineModeCoordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable)
		mutableEvents.tryEmit(
			NavidromeAvailabilityEvent(
				type = NavidromeAvailabilityEventType.EnteredOffline,
				trigger = trigger,
				error = error
			)
		)
		Logger.w("NavidromeAvailability", "Navidrome unavailable; switching to automatic offline mode", error)
		requestProbe()
		return true
	}

	fun requestProbe() {
		if (state.value is NavidromeAvailability.Unavailable) probeWakeups.trySend(Unit)
	}

	private suspend fun probeOnce() {
		val unavailable = state.value as? NavidromeAvailability.Unavailable ?: return
		if (!networkAvailable.value || !isLoggedIn.value) return

		try {
			ping()
		} catch (error: CancellationException) {
			throw error
		} catch (error: Throwable) {
			mutableEvents.emit(
				NavidromeAvailabilityEvent(
					type = NavidromeAvailabilityEventType.ProbeFailed,
					trigger = unavailable.trigger,
					error = error
				)
			)
			Logger.i("NavidromeAvailability", "Authenticated availability probe failed")
			return
		}

		if (!mutableState.compareAndSet(unavailable, NavidromeAvailability.Available)) return
		offlineModeCoordinator.clearAutomatic(AutomaticOfflineReason.NavidromeUnavailable)
		mutableEvents.emit(
			NavidromeAvailabilityEvent(
				type = NavidromeAvailabilityEventType.Restored,
				trigger = unavailable.trigger
			)
		)
		Logger.i("NavidromeAvailability", "Authenticated availability probe succeeded; restored selected mode")
	}
}
