# Automatic Offline Playback Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Offline Mode reactive and automatically continue Android music
playback from verified downloads when Navidrome becomes unavailable, without
persisting the automatic state or mutating the queue.

**Architecture:** A common `OfflineModeCoordinator` separates the persisted
selection from a process-local automatic reason and feeds one effective mode to
`ConnectivityManager`. A coalesced Navidrome availability manager owns outage
classification and authenticated health probes; Android playback chooses local
sources in Media3 traversal order, while download workers suspend and preserve
their durable intents until effective online state returns.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines/Flow, Compose settings,
Android Media3 1.10.1, Room 3, Koin, Gradle Android host tests, ADB, GitHub
Actions.

---

## Scope and Branch

- Worktree:
  `C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-offline-playback-fallback`
- Branch: `fix/offline-playback-fallback`
- Baseline: public `fork/master` commit `0fde4f1f`
- Specification:
  `docs/superpowers/specs/2026-07-22-automatic-offline-playback-fallback-design.md`
- Platform release: Android only. Do not invoke or wait for iOS jobs.
- Preserve the active ebook worktree at
  `C:\Users\darka\Documents\Projects\Android\Navic` and all locked Claude
  worktrees.

The stages below are commits and verification gates on one branch. Publish one
public candidate only after all behavior stages pass.

## File Map

### Reactive mode ownership

- Create
  `composeApp/src/commonMain/kotlin/paige/navic/domain/models/OfflineModeState.kt`
  - Selected mode, effective mode, and process-local automatic reason.
- Create
  `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/OfflineModeCoordinator.kt`
  - Combines the selected mode with the automatic reason.
- Modify
  `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
  - Publish `offlineModeState` while retaining the existing property API.
- Modify
  `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/ConnectivityManager.kt`
  - Add raw `isNetworkAvailable` alongside effective `isOnline`.
- Modify Android and iOS `ConnectivityManager` actuals
  - Recompute from both network and effective mode. The iOS edit is compile
    compatibility only; no iOS automatic playback behavior is added.
- Modify
  `composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt`
  - Register the coordinator.
- Modify
  `composeApp/src/commonMain/composeResources/values/strings.xml`
  - Remove the restart requirement.
- Test `PreferenceManagerTest.kt` and create `OfflineModeCoordinatorTest.kt`.

### Service availability and download suspension

- Create
  `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NavidromeAvailabilityPolicy.kt`
  - Pure transport/service failure classification.
- Create
  `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/NavidromeAvailabilityManager.kt`
  - One outage state, one probe worker, and one monitoring heartbeat.
- Modify `SessionManager.kt`
  - Expose authenticated `ping()` through the current client slot.
- Modify `HostedDownloadFailurePolicy.kt` and its test
  - Replace terminal/retry Boolean with `WaitForService` or `Fail`.
- Modify `DownloadDao.kt`
  - Atomically return a current generation from `DOWNLOADING` to `QUEUED`.
- Modify `DownloadManager.kt`
  - Gate claims, pause active work, report service outages, and remove the
    fixed-delay retry loop.
- Test `NavidromeAvailabilityPolicyTest.kt`,
  `NavidromeAvailabilityManagerTest.kt`, and Android download source contracts.

### Android cached playback and notification

- Create
  `composeApp/src/commonMain/kotlin/paige/navic/domain/models/OfflinePlaybackFallbackPolicy.kt`
  - Pure current-local/upcoming-local/wait decision.
- Create `OfflinePlaybackFallbackPolicyTest.kt`.
- Modify `AndroidStablePlaybackRecoveryCoordinator.android.kt`
  - Apply the local decision, retain no-cache intent, and restore in place.
- Modify `AndroidMediaPlayerViewModel.android.kt`
  - Observe effective mode and service restoration.
- Modify `AndroidPlaybackErrorNotifier.android.kt`
  - Emit the exact one-shot snackbar.
- Create `OfflineAwareMediaNotificationProvider.android.kt`
  - Decorate the existing Media3 notification subtext without replacing title
    or artist.
- Modify `MediaPlayer.android.kt`
  - Install the provider and invalidate it on service-state transitions.
- Modify `AndroidPlaybackDiagnosticsLogger.android.kt`
  - Record automatic offline, fallback, wait, and restoration decisions.
- Create
  `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AutomaticOfflinePlaybackSourceTest.kt`
  - Structural Android wiring and no-queue-mutation contract.

### Candidate and release

- Modify `androidApp/build.gradle.kts` only after syncing current public master.
- Update this plan with observed Gradle, APK, ADB, workflow, and public artifact
  evidence.

## Task 1: Lock the Reactive Offline Mode Contract

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/OfflineModeState.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/OfflineModeCoordinator.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/OfflineModeCoordinatorTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt`

- [ ] **Step 1: Add failing preference-flow tests**

Add tests proving the stored ordinal remains compatible and changes emit
without reconstructing the manager:

```kotlin
@Test
fun offlineModeIsObservableAndPersisted() = runTest {
	val settings = MapSettings()
	val manager = PreferenceManager(settings)

	assertEquals(OfflineMode.Auto, manager.offlineModeState.value)
	manager.offlineMode = OfflineMode.Forced

	assertEquals(OfflineMode.Forced, manager.offlineModeState.value)
	assertEquals(OfflineMode.Forced, PreferenceManager(settings).offlineMode)
}

@Test
fun offlineModeKeepsRawOrdinalCompatibility() {
	val settings = MapSettings()
	settings.putInt("offlineMode", OfflineMode.NoWiFi.ordinal)

	assertEquals(OfflineMode.NoWiFi, PreferenceManager(settings).offlineMode)
}
```

- [ ] **Step 2: Run the preference tests and confirm the missing flow fails**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.manager.PreferenceManagerTest"
```

Expected: compilation fails because `offlineModeState` does not exist.

- [ ] **Step 3: Make `PreferenceManager.offlineMode` reactive**

Replace the delegated property at the end of `PreferenceManager` with:

```kotlin
private var persistedOfflineMode by preference(OfflineMode.Auto)
private val _offlineModeState = MutableStateFlow(persistedOfflineMode)
val offlineModeState: StateFlow<OfflineMode> = _offlineModeState.asStateFlow()

var offlineMode: OfflineMode
	get() = _offlineModeState.value
	set(value) {
		if (_offlineModeState.value == value) return
		persistedOfflineMode = value
		_offlineModeState.value = value
	}
```

Add `MutableStateFlow`, `StateFlow`, and `asStateFlow` imports. Rerun the focused
preference tests and confirm they pass.

- [ ] **Step 4: Add the failing coordinator tests**

Cover automatic forcing, duplicate entry, latest-selection restoration, and
process-local construction:

```kotlin
@Test
fun automaticReasonForcesEffectiveModeWithoutChangingSelection() = runTest {
	val preferences = PreferenceManager(MapSettings())
	val coordinator = OfflineModeCoordinator(preferences, backgroundScope)

	assertTrue(coordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable))
	runCurrent()

	assertEquals(OfflineMode.Auto, preferences.offlineMode)
	assertEquals(OfflineMode.Forced, coordinator.state.value.effectiveMode)
	assertEquals(
		AutomaticOfflineReason.NavidromeUnavailable,
		coordinator.state.value.automaticReason
	)
	assertFalse(coordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable))
}

@Test
fun clearingAutomaticReasonRevealsLatestUserSelection() = runTest {
	val settings = MapSettings()
	val preferences = PreferenceManager(settings)
	val coordinator = OfflineModeCoordinator(preferences, backgroundScope)
	coordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable)
	preferences.offlineMode = OfflineMode.NoWiFi
	runCurrent()

	assertTrue(coordinator.clearAutomatic(AutomaticOfflineReason.NavidromeUnavailable))
	runCurrent()

	assertEquals(OfflineMode.NoWiFi, coordinator.state.value.selectedMode)
	assertEquals(OfflineMode.NoWiFi, coordinator.state.value.effectiveMode)
	assertEquals(OfflineMode.NoWiFi, PreferenceManager(settings).offlineMode)
}
```

- [ ] **Step 5: Implement the mode model and coordinator**

Use these public contracts:

```kotlin
enum class AutomaticOfflineReason {
	NavidromeUnavailable
}

data class OfflineModeState(
	val selectedMode: OfflineMode,
	val effectiveMode: OfflineMode,
	val automaticReason: AutomaticOfflineReason?
) {
	val isAutomaticallyForced: Boolean
		get() = automaticReason != null
}

fun offlineModeState(
	selectedMode: OfflineMode,
	automaticReason: AutomaticOfflineReason?
): OfflineModeState = OfflineModeState(
	selectedMode = selectedMode,
	effectiveMode = if (automaticReason == null) selectedMode else OfflineMode.Forced,
	automaticReason = automaticReason
)
```

Implement `OfflineModeCoordinator` as one long-lived state owner:

```kotlin
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
```

Run both focused test classes and confirm they pass.

- [ ] **Step 6: Register and commit Stage 1 ownership**

Add this definition to `ManagerModule.kt`:

```kotlin
single { OfflineModeCoordinator(get()) }
```

Run `git diff --check`, stage the model, manager, DI, and tests, then commit:

```powershell
git commit -m "refactor(connectivity): make offline mode observable"
```

## Task 2: Feed Effective Mode Through Connectivity

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/ConnectivityManager.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/domain/manager/ConnectivityManager.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/paige/navic/domain/manager/ConnectivityManager.ios.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/manager/ReactiveOfflineConnectivitySourceTest.kt`

- [ ] **Step 1: Add failing source-contract assertions**

Require both actuals to use `combine(networkStatus, offlineModeCoordinator.state)`
and require the settings string to omit restart language. Also assert the
common contract exposes `isNetworkAvailable`.

```kotlin
@Test
fun connectivityCombinesNetworkAndEffectiveOfflineState() {
	assertTrue(commonSource.contains("val isNetworkAvailable: StateFlow<Boolean>"))
	assertTrue(androidSource.contains("combine(networkStatus, offlineModeCoordinator.state)"))
	assertTrue(iosSource.contains("combine(networkStatus, offlineModeCoordinator.state)"))
	assertFalse(stringsSource.contains("Requires application restart"))
}
```

- [ ] **Step 2: Run the source test and confirm all new assertions fail**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.manager.ReactiveOfflineConnectivitySourceTest"
```

- [ ] **Step 3: Add raw-network and effective-online flows**

Add to the expect class:

```kotlin
val isNetworkAvailable: StateFlow<Boolean>
```

Add `OfflineModeCoordinator` to both actual constructors. In each actual, keep
the platform network callback unchanged and derive:

```kotlin
actual val isNetworkAvailable = networkStatus
	.map { it.isOnline }
	.distinctUntilChanged()
	.flowOn(dispatcher)
	.stateIn(scope, started, false)

actual val isOnline = combine(networkStatus, offlineModeCoordinator.state) { status, offline ->
	when (offline.effectiveMode) {
		OfflineMode.Forced -> false
		OfflineMode.NoWiFi -> status.isOnline && !status.isCellular
		OfflineMode.Auto -> status.isOnline
	}
}
	.distinctUntilChanged()
	.flowOn(dispatcher)
	.stateIn(scope, started, false)
```

This is the only iOS source change in the feature and exists to satisfy the
common expect contract. Do not add iOS service probing or playback fallback.

- [ ] **Step 4: Remove restart copy and rerun Stage 1-2 tests**

Change `subtitle_offline_mode` to:

```xml
<string name="subtitle_offline_mode">Controls when Navic uses downloaded content only</string>
```

Run the preference, coordinator, and source tests. Then run:

```powershell
.\gradlew.bat :composeApp:compileAndroidMain
```

Expected: all focused tests and Android compilation pass.

- [ ] **Step 5: Commit the effective connectivity boundary**

```powershell
git commit -m "fix(connectivity): apply offline mode without restart"
```

## Task 3: Classify and Monitor Navidrome Availability

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NavidromeAvailabilityPolicy.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/NavidromeAvailabilityManager.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/NavidromeAvailabilityPolicyTest.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/NavidromeAvailabilityManagerTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/SessionManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt`

- [ ] **Step 1: Write the failure-classification tests**

Test connection errors and 5xx as service outages, while authentication,
missing media, malformed media, and decoder failures remain terminal:

```kotlin
@Test
fun transportAndServiceFailuresEnterOffline() {
	listOf(
		"ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
		"ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"
	).forEach { code ->
		assertEquals(
			NavidromeFailureDisposition.ServiceUnavailable,
			navidromeFailureDisposition(code, emptyList())
		)
	}
	assertEquals(
		NavidromeFailureDisposition.ServiceUnavailable,
		navidromeFailureDisposition(null, listOf("Unable to resolve host music.example"))
	)
	assertEquals(
		NavidromeFailureDisposition.ServiceUnavailable,
		navidromeFailureDisposition(null, listOf("HTTP 503 Service Unavailable"))
	)
}

@Test
fun itemAndAuthenticationFailuresRemainTerminal() {
	listOf("HTTP 401", "HTTP 403", "HTTP 404", "non-audio content", "decoder failed")
		.forEach { message ->
			assertEquals(
				NavidromeFailureDisposition.Terminal,
				navidromeFailureDisposition(null, listOf(message))
			)
		}
}
```

- [ ] **Step 2: Implement the pure classifier and run it green**

Define:

```kotlin
enum class NavidromeFailureDisposition {
	ServiceUnavailable,
	Terminal
}

fun navidromeFailureDisposition(
	errorCodeName: String?,
	details: List<String>
): NavidromeFailureDisposition {
	val text = (listOfNotNull(errorCodeName) + details).joinToString(" ").lowercase()
	val unavailable = listOf(
		"error_code_io_network_connection_failed",
		"error_code_io_network_connection_timeout",
		"unknownhost",
		"unable to resolve host",
		"failed to connect",
		"connection refused",
		"connection reset",
		"no route to host",
		"connect timeout",
		"connecttimeoutexception",
		"socket timeout",
		"sockettimeoutexception",
		"timed out"
	).any(text::contains) || listOf(500, 502, 503, 504, 521, 522, 523, 524)
		.any { status -> "http $status" in text || "status $status" in text }
	return if (unavailable) {
		NavidromeFailureDisposition.ServiceUnavailable
	} else {
		NavidromeFailureDisposition.Terminal
	}
}

fun navidromeFailureDisposition(error: Throwable): NavidromeFailureDisposition =
	navidromeFailureDisposition(
		errorCodeName = null,
		details = throwableMessages(error)
	)

private fun throwableMessages(error: Throwable): List<String> {
	val messages = mutableListOf<String>()
	val seen = mutableSetOf<Throwable>()
	var current: Throwable? = error
	while (current != null && seen.add(current)) {
		messages += current::class.simpleName.orEmpty()
		current.message?.let(messages::add)
		current = current.cause
	}
	return messages
}
```

Run the focused test.

- [ ] **Step 3: Write deterministic availability-manager tests**

Use `MutableStateFlow` fakes and the test scope to prove:

- the first report enters automatic mode and a duplicate returns false;
- no probe runs without raw network;
- a network restoration requests one probe;
- failed probes retain offline state;
- a successful authenticated probe clears only the automatic reason; and
- one heartbeat requests another probe without cancelling any task.

The core restoration assertion is:

```kotlin
manager.reportUnavailable("playback-timeout")
runCurrent()
assertIs<NavidromeAvailability.Unavailable>(manager.state.value)
assertEquals(OfflineMode.Forced, offlineCoordinator.state.value.effectiveMode)

pingShouldSucceed = true
networkAvailable.value = true
runCurrent()

assertEquals(NavidromeAvailability.Available, manager.state.value)
assertEquals(OfflineMode.Auto, offlineCoordinator.state.value.effectiveMode)
assertEquals(OfflineMode.Auto, preferences.offlineMode)
```

- [ ] **Step 4: Implement the coalesced availability manager**

Use these contracts:

```kotlin
sealed interface NavidromeAvailability {
	data object Available : NavidromeAvailability
	data class Unavailable(val trigger: String) : NavidromeAvailability
}

sealed interface NavidromeAvailabilityEvent {
	data class EnteredOffline(val trigger: String) : NavidromeAvailabilityEvent
	data class DuplicateOutage(val trigger: String) : NavidromeAvailabilityEvent
	data class ProbeFailed(val message: String?) : NavidromeAvailabilityEvent
	data object Restored : NavidromeAvailabilityEvent
}

class NavidromeAvailabilityManager(
	private val isNetworkAvailable: StateFlow<Boolean>,
	private val isLoggedIn: StateFlow<Boolean>,
	private val ping: suspend () -> Unit,
	private val offlineModeCoordinator: OfflineModeCoordinator,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
	private val heartbeatInterval: Duration = 30.seconds
) {
	private val _state = MutableStateFlow<NavidromeAvailability>(NavidromeAvailability.Available)
	val state: StateFlow<NavidromeAvailability> = _state.asStateFlow()
	private val _events = MutableSharedFlow<NavidromeAvailabilityEvent>(extraBufferCapacity = 16)
	val events: SharedFlow<NavidromeAvailabilityEvent> = _events.asSharedFlow()
	private val probeRequests = Channel<Unit>(Channel.CONFLATED)
	private var heartbeatJob: Job? = null

	init {
		scope.launch {
			isNetworkAvailable.distinctUntilChanged().collect { available ->
				if (available && _state.value is NavidromeAvailability.Unavailable) requestProbe()
			}
		}
		scope.launch {
			for (ignored in probeRequests) probeOnce()
		}
	}

	fun reportUnavailable(trigger: String): Boolean {
		val entered = _state.compareAndSet(
			expect = NavidromeAvailability.Available,
			update = NavidromeAvailability.Unavailable(trigger)
		)
		if (!entered) {
			_events.tryEmit(NavidromeAvailabilityEvent.DuplicateOutage(trigger))
			return false
		}
		offlineModeCoordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable)
		_events.tryEmit(NavidromeAvailabilityEvent.EnteredOffline(trigger))
		startHeartbeat()
		requestProbe()
		return true
	}

	fun requestProbe() {
		probeRequests.trySend(Unit)
	}

	private fun startHeartbeat() {
		heartbeatJob?.cancel()
		heartbeatJob = scope.launch {
			while (_state.value is NavidromeAvailability.Unavailable) {
				delay(heartbeatInterval)
				requestProbe()
			}
		}
	}

	private suspend fun probeOnce() {
		val unavailable = _state.value as? NavidromeAvailability.Unavailable ?: return
		if (!isNetworkAvailable.value || !isLoggedIn.value) return
		runCatching { ping() }
			.onSuccess {
				if (_state.compareAndSet(unavailable, NavidromeAvailability.Available)) {
					offlineModeCoordinator.clearAutomatic(
						AutomaticOfflineReason.NavidromeUnavailable
					)
					heartbeatJob?.cancel()
					heartbeatJob = null
					_events.tryEmit(NavidromeAvailabilityEvent.Restored)
				}
			}
			.onFailure { error ->
				_events.tryEmit(NavidromeAvailabilityEvent.ProbeFailed(error.message))
			}
	}
}
```

The implementation owns exactly one loop over `probeRequests`. The event flow is
diagnostic only; consumers derive notification and recovery from `state`.
`delay(heartbeatInterval)` exists only inside the monitoring heartbeat.

- [ ] **Step 5: Expose authenticated ping and register the manager**

Add to `SessionManager`:

```kotlin
suspend fun ping() {
	withApi { client -> client.ping() }
}
```

Register explicitly in `ManagerModule.kt`:

```kotlin
single {
	NavidromeAvailabilityManager(
		isNetworkAvailable = get<ConnectivityManager>().isNetworkAvailable,
		isLoggedIn = get<SessionManager>().isLoggedIn,
		ping = get<SessionManager>()::ping,
		offlineModeCoordinator = get()
	)
}
```

The public constructor supplies an application `SupervisorJob`, IO dispatcher,
and a 30-second monitoring heartbeat. Do not use `withTimeout` or a deadline to
cancel probes.

- [ ] **Step 6: Run and commit service state**

Run both new test classes, `SessionResourceSlotTest`, and
`SessionBoundManagersSourceTest`. Commit:

```powershell
git commit -m "feat(connectivity): track Navidrome availability"
```

## Task 4: Suspend Downloads While Offline

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/HostedDownloadFailurePolicy.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/HostedDownloadFailurePolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/DownloadDao.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/manager/DownloadQueueOwnershipSourceTest.kt`

- [ ] **Step 1: Replace retry expectations with wait/fail decisions**

Update policy tests to require:

```kotlin
assertEquals(
	HostedDownloadFailureAction.WaitForService,
	hostedDownloadFailureAction(UnknownHostException("unable to resolve host"))
)
assertEquals(
	HostedDownloadFailureAction.WaitForService,
	hostedDownloadFailureAction(IllegalStateException("HTTP 503 Service Unavailable"))
)
assertEquals(
	HostedDownloadFailureAction.Fail,
	hostedDownloadFailureAction(IllegalStateException("non-audio content: text/html"))
)
```

Run `HostedDownloadFailurePolicyTest`; expect compilation failure for the new
action type.

- [ ] **Step 2: Implement the download failure action**

Define:

```kotlin
enum class HostedDownloadFailureAction {
	WaitForService,
	Fail
}

fun hostedDownloadFailureAction(error: Throwable): HostedDownloadFailureAction =
	if (navidromeFailureDisposition(error) == NavidromeFailureDisposition.ServiceUnavailable) {
		HostedDownloadFailureAction.WaitForService
	} else {
		HostedDownloadFailureAction.Fail
	}
```

Remove `shouldFailHostedDownload` after all call sites migrate.

- [ ] **Step 3: Add an atomic requeue DAO operation**

Add:

```kotlin
@Query(
	"""
	UPDATE DownloadEntity
	SET status = 'QUEUED', progress = 0, queuedAtEpochMs = :queuedAtEpochMs
	WHERE songId = :songId AND intentGeneration = :generation
		AND status = 'DOWNLOADING' AND cancelled = 0
	"""
)
suspend fun requeueIfCurrent(songId: String, generation: Long, queuedAtEpochMs: Long): Int
```

The generation and cancellation predicates are mandatory; an old worker must
not resurrect a cancelled or replaced intent.

- [ ] **Step 4: Add failing source contracts for offline gating**

Require `DownloadManager` to:

- inject `ConnectivityManager` and `NavidromeAvailabilityManager`;
- wait with `connectivityManager.isOnline.first { it }` before claiming work;
- cancel and join active jobs when effective online becomes false;
- call `requeueIfCurrent` for a service outage;
- wake workers when effective online becomes true; and
- contain neither `HOSTED_DOWNLOAD_RETRY_DELAY_MS` nor a retry `delay` in
  `executeDownloadProcess`.

Run `DownloadQueueOwnershipSourceTest` and confirm the assertions fail against
the current retry loop.

- [ ] **Step 5: Gate workers and pause active jobs**

Add the two dependencies to `DownloadManager`. In `init`, collect effective
online state in `applicationScope`:

```kotlin
applicationScope.launch {
	connectivityManager.isOnline.distinctUntilChanged().collect { online ->
		if (online) {
			downloadWakeups.trySend(Unit)
		} else {
			pauseActiveDownloadsForOffline()
		}
	}
}
```

`pauseActiveDownloadsForOffline` snapshots active jobs under the existing mutex,
cancels and joins them, calls `downloadDao.recoverInterruptedDownloads()`, and
leaves intent generations and queue timestamps intact.

Before `claimNextDownloadSlot()` in each worker pass, use:

```kotlin
connectivityManager.isOnline.first { it }
```

Continue the inner claim loop only while `isOnline.value` remains true.

- [ ] **Step 6: Replace fixed-delay retry with state transition**

Use this catch decision in `executeDownloadProcess`:

```kotlin
when (hostedDownloadFailureAction(error)) {
	HostedDownloadFailureAction.WaitForService -> {
		navidromeAvailabilityManager.reportUnavailable("download:${error::class.simpleName}")
		downloadDao.requeueIfCurrent(
			songId = song.id,
			generation = generation,
			queuedAtEpochMs = Clock.System.now().toEpochMilliseconds()
		)
		return
	}
	HostedDownloadFailureAction.Fail -> {
		downloadDao.completeIfCurrent(
			songId = song.id,
			generation = generation,
			status = DownloadStatus.FAILED,
			progress = 0f,
			filePath = null
		)
		return
	}
}
```

Remove `HOSTED_DOWNLOAD_RETRY_DELAY_MS`. Retain the 500 ms library progress
poll because it observes local database completion and does not cancel work.

- [ ] **Step 7: Run download gates and commit**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.models.HostedDownloadFailurePolicyTest" `
  --tests "paige.navic.domain.manager.DownloadQueueOwnershipSourceTest" `
  --tests "paige.navic.domain.models.QueuedDownloadRecoveryPolicyTest"
```

Run `:composeApp:compileAndroidMain`, then commit:

```powershell
git commit -m "fix(downloads): suspend queue while offline"
```

## Task 5: Choose Cached Playback in Actual Upcoming Order

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/OfflinePlaybackFallbackPolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/OfflinePlaybackFallbackPolicyTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AutomaticOfflinePlaybackSourceTest.kt`

- [ ] **Step 1: Write the pure fallback tests**

Cover current-local preference, Media3 traversal order, invalid indexes,
repeat-one/no-cache wait, and duplicate song IDs at different queue positions:

```kotlin
@Test
fun currentLocalSourceWinsBeforeUpcomingTraversal() {
	assertEquals(
		OfflinePlaybackFallback.UseCurrentLocal(index = 2, path = "current.flac"),
		offlinePlaybackFallback(
			currentIndex = 2,
			upcomingIndexes = listOf(4, 1, 3),
			queueSongIds = listOf("a", "b", "current", "d", "e"),
			downloadedPaths = mapOf("current" to "current.flac", "e" to "e.flac")
		)
	)
}

@Test
fun shuffledMedia3OrderSelectsFirstUsableUpcomingEntry() {
	assertEquals(
		OfflinePlaybackFallback.UseUpcomingLocal(index = 4, path = "e.flac"),
		offlinePlaybackFallback(
			currentIndex = 2,
			upcomingIndexes = listOf(4, 1, 3),
			queueSongIds = listOf("a", "b", "current", "d", "e"),
			downloadedPaths = mapOf("b" to "b.flac", "e" to "e.flac")
		)
	)
}

@Test
fun noUsableLocalSourceWaitsWithoutInventingTarget() {
	assertEquals(
		OfflinePlaybackFallback.WaitForService,
		offlinePlaybackFallback(0, listOf(0), listOf("remote"), emptyMap())
	)
}
```

- [ ] **Step 2: Implement and run the pure fallback policy**

Define:

```kotlin
sealed interface OfflinePlaybackFallback {
	data class UseCurrentLocal(val index: Int, val path: String) : OfflinePlaybackFallback
	data class UseUpcomingLocal(val index: Int, val path: String) : OfflinePlaybackFallback
	data object WaitForService : OfflinePlaybackFallback
}

fun offlinePlaybackFallback(
	currentIndex: Int,
	upcomingIndexes: List<Int>,
	queueSongIds: List<String>,
	downloadedPaths: Map<String, String>
): OfflinePlaybackFallback {
	val currentId = queueSongIds.getOrNull(currentIndex)
	val currentPath = currentId?.let(downloadedPaths::get)
	if (currentPath != null) return OfflinePlaybackFallback.UseCurrentLocal(currentIndex, currentPath)

	for (index in upcomingIndexes) {
		if (index == currentIndex) continue
		val songId = queueSongIds.getOrNull(index) ?: continue
		val path = downloadedPaths[songId] ?: continue
		return OfflinePlaybackFallback.UseUpcomingLocal(index, path)
	}
	return OfflinePlaybackFallback.WaitForService
}
```

Run `OfflinePlaybackFallbackPolicyTest` green.

- [ ] **Step 3: Add failing Android source contracts**

Require production wiring to use:

- `PlayerUiState.upcomingIndexes`, not `firstPlayableUpcomingIndex`;
- `DownloadManager.getDownloadedFilePath` for every candidate;
- `replaceMediaItem` with a local `file:` URI before seeking;
- no `moveMediaItem`, `removeMediaItem`, or queue list mutation;
- a pending service recovery containing index, position, and retained intent;
- restoration only when the waiting item is still current; and
- no persistent download request on the service-unavailable branch.

Run `AutomaticOfflinePlaybackSourceTest`; confirm it fails.

- [ ] **Step 4: Add effective-offline handling to the recovery coordinator**

Add `NavidromeAvailabilityManager` and an effective-online lambda to the
coordinator. Implement:

```kotlin
fun handleEffectiveOffline(player: MediaController, state: PlayerUiState, reason: String) {
	val currentIndex = player.currentMediaItemIndex
	val paths = state.queue.mapNotNull { song ->
		downloadManager.getDownloadedFilePath(song.id)?.let { song.id to it }
	}.toMap()
	when (val fallback = offlinePlaybackFallback(
		currentIndex = currentIndex,
		upcomingIndexes = state.upcomingIndexes,
		queueSongIds = state.queue.map { it.id },
		downloadedPaths = paths
	)) {
		is OfflinePlaybackFallback.UseCurrentLocal ->
			playLocal(player, fallback.index, fallback.path, player.currentPosition, player.playWhenReady)
		is OfflinePlaybackFallback.UseUpcomingLocal ->
			playLocal(player, fallback.index, fallback.path, 0L, player.playWhenReady || !state.isPaused)
		OfflinePlaybackFallback.WaitForService ->
			waitForService(player, state, reason)
	}
}
```

`playLocal` replaces only the Media3 source at the same index, seeks, prepares,
and plays only when retained intent is Play. `waitForService` records a
`PendingPlaybackRecovery` but does not call `prefetchPlaybackSongs`.

On a classified service error, bypass remote URL refresh and call
`reportUnavailable` before `handleEffectiveOffline`. Keep current refresh and
download recovery for non-service item errors.

- [ ] **Step 5: Restore only a waiting item**

Add:

```kotlin
fun onServiceRestored(player: MediaController, state: PlayerUiState) {
	val recovery = pendingServiceRecovery ?: return
	if (player.currentMediaItemIndex != recovery.queueIndex ||
		player.currentMediaItem?.mediaId != recovery.songId) {
		clear("service-restored-stale")
		return
	}
	val song = state.queue.getOrNull(recovery.queueIndex) ?: return
	player.replaceMediaItem(recovery.queueIndex, mediaItemForSong(song))
	player.seekTo(recovery.queueIndex, recovery.positionMs)
	player.prepare()
	if (recovery.shouldResume) {
		claimMusicPlayback()
		player.play()
	}
	clear("service-restored")
}
```

Update `onUserPause`, `onUserResume`, transitions, and explicit queue commands
to update or clear both download and service recovery state.

- [ ] **Step 6: Observe mode and service state in the Android ViewModel**

Inject `OfflineModeCoordinator` and `NavidromeAvailabilityManager`. Start two
collectors after the media controller is connected:

```kotlin
viewModelScope.launch {
	connectivityManager.isOnline.distinctUntilChanged().collect { online ->
		val player = controller ?: return@collect
		if (!online) {
			val offlineState = offlineModeCoordinator.state.value
			if (offlineState.selectedMode == OfflineMode.Auto &&
				!connectivityManager.isNetworkAvailable.value) {
				navidromeAvailabilityManager.reportUnavailable("network-lost")
			}
			playbackRecovery.handleEffectiveOffline(player, _uiState.value, "effective-offline")
		}
	}
}

viewModelScope.launch {
	navidromeAvailabilityManager.state.distinctUntilChanged().collect { availability ->
		if (availability == NavidromeAvailability.Available && connectivityManager.isOnline.value) {
			controller?.let { playbackRecovery.onServiceRestored(it, _uiState.value) }
		}
	}
}
```

An explicit Resume while waiting calls `requestProbe()` and retains Play intent.
An automatic transition into an unavailable remote item applies the same
fallback policy.

- [ ] **Step 7: Run playback gates and commit**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.models.OfflinePlaybackFallbackPolicyTest" `
  --tests "paige.navic.shared.AutomaticOfflinePlaybackSourceTest" `
  --tests "paige.navic.domain.models.PlaybackQueueRecoveryPolicyTest" `
  --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest"
```

Compile Android and commit:

```powershell
git commit -m "fix(playback): continue from cached songs offline"
```

## Task 6: Notify Once and Decorate the Playback Notification

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackErrorNotifier.android.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/shared/OfflineAwareMediaNotificationProvider.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackDiagnosticsLogger.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AutomaticOfflinePlaybackSourceTest.kt`

- [ ] **Step 1: Lock exact copy and transition ownership in tests**

Add assertions for the exact resource text:

```text
Connection lost - Switching to Offline mode
```

Require the snackbar to be called only on
`NavidromeAvailability.Available -> Unavailable`, and require the Media3 wrapper
to use notification subtext rather than replacing content title/text.

- [ ] **Step 2: Add the exact snackbar resource and notifier method**

Add:

```xml
<string name="notice_connection_lost_offline">Connection lost - Switching to Offline mode</string>
```

Add to `AndroidPlaybackErrorNotifier`:

```kotlin
fun notifyConnectionLost() {
	snackBarManager.notify(Res.string.notice_connection_lost_offline)
}
```

The availability collector calls it only when the new state is
`Unavailable`; duplicate reports never emit a new state.

- [ ] **Step 3: Wrap the default Media3 notification provider**

Implement `MediaNotification.Provider` by delegation. Store the most recent
creation inputs and callback, decorate every synchronous/asynchronous default
notification, and rebuild when the connection flag changes:

```kotlin
private fun decorate(source: MediaNotification): MediaNotification {
	val builder = Notification.Builder.recoverBuilder(context, source.notification)
	builder.setSubText(if (connectionLost) CONNECTION_LOST_OFFLINE_MESSAGE else null)
	return MediaNotification(source.notificationId, builder.build())
}

fun setConnectionLost(value: Boolean) {
	if (connectionLost == value) return
	connectionLost = value
	latestSnapshot?.let { snapshot ->
		snapshot.callback.onNotificationChanged(createDecorated(snapshot))
	}
}
```

Use a private constant with the exact approved text and a source test that
compares it to the Compose resource. `handleCustomCommand` and
`getNotificationChannelInfo` delegate unchanged to
`DefaultMediaNotificationProvider`.

- [ ] **Step 4: Install and invalidate the wrapper in `PlaybackService`**

Build the existing default provider, wrap it, and pass the wrapper to
`setMediaNotificationProvider`. Collect `NavidromeAvailabilityManager.state`
in `serviceScope`:

```kotlin
serviceScope.launch {
	navidromeAvailabilityManager.state
		.map { it is NavidromeAvailability.Unavailable }
		.distinctUntilChanged()
		.collect(notificationProvider::setConnectionLost)
}
```

On restoration this clears subtext silently. Do not change media title, artist,
notification channel importance, sound, or vibration.

- [ ] **Step 5: Add bounded diagnostics**

Add methods/events for automatic entry, duplicate report, current-local target,
upcoming-local target, no-cache wait, download suspension/requeue, failed probe,
service restoration, and waiting-item resume. Reuse `PlaybackDiagnostics`; do
not log exception URLs, request headers, or credentials.

- [ ] **Step 6: Run notification/diagnostic gates and commit**

Run `AutomaticOfflinePlaybackSourceTest`, `PlaybackDiagnosticsSourceTest`, and
`AppLogManagerTest`, then compile Android. Commit:

```powershell
git commit -m "feat(playback): report automatic offline fallback"
```

## Task 7: Full Verification and Controlled ADB Deployment

**Files:**
- Update: this plan with observed evidence only

- [ ] **Step 1: Run all focused tests from Tasks 1-6**

Run the exact focused classes named above in one
`:composeApp:testAndroidHostTest` invocation. Expected: zero failures.

- [ ] **Step 2: Run repository verification gates**

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :androidApp:lintDebug
.\scripts\verify-reader-vendor-assets.ps1
.\scripts\verify-third-party-attributions.ps1
git diff --check
git status --short
```

If the full host suite or lint has failures, run the same command at a fresh
detached `fork/master` worktree and record the exact baseline delta. Do not call
an unrelated pre-existing failure a feature regression.

- [ ] **Step 3: Install the debug candidate on an Android target**

Use an emulator when available; otherwise use the USB tablet selected explicitly
by serial. Record:

```powershell
$serials = @(
  adb devices |
    Select-String '^\S+\s+device$' |
    ForEach-Object { ($_.Line -split '\s+')[0] }
)
if ($serials.Count -ne 1) {
  throw "Expected exactly one Android target, found $($serials.Count): $($serials -join ', ')"
}
$serial = $serials[0]
$apk = Resolve-Path 'androidApp\build\outputs\apk\debug\Navic.apk'
$package = (& apkanalyzer manifest application-id $apk.Path).Trim()
adb -s $serial install -r $apk.Path
adb -s $serial shell dumpsys package $package
adb -s $serial shell pidof $package
```

The package is read from the APK rather than assumed. Do not install or modify
the production package during this gate.

- [ ] **Step 4: Exercise runtime Offline Mode without restart**

With a queue containing cached and uncached entries:

1. switch `Auto -> Forced` in settings;
2. verify Queue/Now Playing availability updates while the process PID remains
   unchanged;
3. verify current-local or first cached upcoming playback starts without queue
   order changes;
4. switch `Forced -> Auto`; and
5. verify remote eligibility returns without relaunching.

Capture PID, media session queue size/current index, and PlaybackDiagnostics
before and after each transition.

- [ ] **Step 5: Exercise a controlled connection loss and restoration**

On an approved Android test target with USB ADB and at least two verified cached
songs, capture the original Wi-Fi state. Disable only that target's Wi-Fi,
observe validated network loss, then verify:

- the exact message appears once;
- the playback notification retains song title/artist and adds the status;
- cached playback continues in actual upcoming order;
- queued downloads remain queued without repeated network attempts; and
- queue size/order are unchanged.

Re-enable Wi-Fi in the same session, wait for Android validated connectivity and
an authenticated health success, then verify remote eligibility and download
wake-up. Restore the original Wi-Fi state in a `finally` block even if an
assertion fails. Do not stop the live server or alter DNS for this test.

- [ ] **Step 6: Record evidence and commit verification notes**

Add exact test counts, baseline comparison, APK path/hash, target serial/package,
queue evidence, notification copy, outage diagnostics, and restoration evidence
to an `Execution Status` section in this plan. Commit:

```powershell
git commit -m "docs(playback): record offline fallback validation"
```

## Task 8: Sync Current Master and Prepare the Next `iota##` Candidate

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Resolve only genuine overlap introduced by current `fork/master`

- [ ] **Step 1: Fetch and integrate current public master**

```powershell
git fetch fork master --tags --prune
git rev-list --left-right --count HEAD...fork/master
```

Integrate `fork/master` non-interactively. Preserve the ebook implementation
that has landed on public master; do not copy from or reset the active ebook
worktree. Rerun all focused tests and Android assembly after integration.

- [ ] **Step 2: Derive, do not pre-reserve, the next release identifiers**

Read `androidApp/build.gradle.kts` after the sync. Require its version name to
match the regular expression `^v1\.0\.11-iota\d+$`, increment that numeric suffix by exactly one, and
increment `versionCode` by exactly one. For example, current master
`v1.0.11-iota26`/`553` becomes `v1.0.11-iota27`/`554`; if another release has
landed, use the next number after that release. Do not change Greek letter.

Derive and verify the values:

```powershell
$gradleText = Get-Content 'androidApp\build.gradle.kts' -Raw
$nameMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"(?<name>v1\.0\.11-iota(?<suffix>\d+))"')
$codeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*(?<code>\d+)')
if (-not $nameMatch.Success -or -not $codeMatch.Success) {
  throw 'Current Android release metadata is not an iota prerelease'
}
$nextName = "v1.0.11-iota$([int]$nameMatch.Groups['suffix'].Value + 1)"
$nextCode = [int]$codeMatch.Groups['code'].Value + 1
Write-Host "Next release: versionName=$nextName versionCode=$nextCode"
```

Update the two Gradle values to `$nextName` and `$nextCode`, then verify:

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName $nextName
if (git tag --list $nextName) { throw "Tag already exists: $nextName" }
```

- [ ] **Step 3: Rebuild and verify embedded metadata**

Run focused tests, full Android host tests, lint comparison, and debug assembly
again. Inspect the APK with `apkanalyzer manifest version-name` and
`version-code`; both must match the derived values.

- [ ] **Step 4: Commit the release candidate**

```powershell
git commit -m "release: prepare automatic offline playback fallback"
```

## Task 9: Publish and Verify the Android-Only Release

**Files:**
- Update: this plan with final public evidence

- [ ] **Step 1: Prove release ancestry and clean scope**

Fetch `fork/master` again immediately before publication. Prove the candidate
contains current public master and required historical playback commit
`9c619f10`:

```powershell
git merge-base --is-ancestor fork/master HEAD
git merge-base --is-ancestor 9c619f10 HEAD
git status --short
```

Expected: both ancestry commands return 0 and status is clean.

- [ ] **Step 2: Push the candidate and annotated tag**

Fast-forward public master, create the derived annotated `iota##` tag, and push
it. No iOS release work is performed.

```powershell
$gradleText = Get-Content 'androidApp\build.gradle.kts' -Raw
$releaseTag = [regex]::Match($gradleText, 'versionName\s*=\s*"(?<name>v1\.0\.11-iota\d+)"').Groups['name'].Value
if ([string]::IsNullOrWhiteSpace($releaseTag)) { throw 'Unable to derive release tag' }
git tag -a $releaseTag -m "Automatic offline playback fallback"
git push --atomic fork HEAD:master "refs/tags/$releaseTag"
```

- [ ] **Step 3: Start the guarded public release workflow**

Use:

```powershell
.\scripts\publish-github-release.ps1 `
  -Tag $releaseTag `
  -AllowPublicRelease `
  -ReleaseReadinessNote "Automatic Offline Mode is reactive; Android playback falls back through verified cached songs, downloads suspend without polling, and focused/full/build/ADB gates are recorded in the implementation plan."
```

Follow the workflow through GitHub CLI heartbeats until it completes. Do not use
a cancellation timeout. Confirm all iOS jobs are skipped.

- [ ] **Step 4: Verify the public artifact independently**

Download public `Navic.apk` and verify:

- GitHub asset digest equals local SHA-256;
- package is `darkaxt.navic`;
- embedded `versionName` and `versionCode` equal the derived values;
- `apksigner verify --print-certs` reports the expected production signer; and
- the public tag points to the released commit.

- [ ] **Step 5: Install the public APK in place and capture readiness**

On an attached production Android target, record the installed version and
queue state, install with `adb install -r`, launch Navic, and verify version,
PID, MediaSession queue size/current index, and no startup crash. Do not repeat
the destructive network exercise unless the target owner has agreed to it for
the production package.

- [ ] **Step 6: Record release evidence**

Add workflow run ID, release URL, tag/commit, artifact digest, signer digest,
embedded metadata, device serial, installed package version, PID, and queue
preservation evidence to this plan. Commit and push the evidence commit only if
the repository convention keeps post-release evidence on master.

## Task 10: Field Acceptance and Workspace Cleanup

**Files:**
- Update: this plan with field observations when available

- [ ] **Step 1: Review next-walk diagnostics**

Capture the first real roaming/server-loss transition, cached target choice,
download state, service restoration, and any unexpected pause. Compare queue
IDs/order and actual `upcomingIndexes`; Wi-Fi smoke evidence is not substituted
for this field result.

- [ ] **Step 2: Classify field findings without destabilizing the release**

If behavior matches the acceptance matrix, mark field acceptance complete. If
it does not, retain the branch/worktree until the deterministic cause is fixed
and a follow-up release is verified.

- [ ] **Step 3: Remove only this completed worktree and branch**

After the feature tip is contained in public master and the worktree is clean:

```powershell
$repo = 'C:\Users\darka\Documents\Projects\Android\Navic'
$target = 'C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-offline-playback-fallback'
$expected = [IO.Path]::GetFullPath($target)
$resolved = (Resolve-Path -LiteralPath $target).Path
if ($resolved -ne $expected) { throw "Refusing unexpected worktree path: $resolved" }
git -C $repo worktree remove $resolved
git -C $repo branch -d fix/offline-playback-fallback
git -C $repo worktree prune
```

Do not remove, unlock, prune as missing, or modify the ebook, PlayLikeCurl,
playlist, or Claude worktrees.

---

## Self-Review Checklist

- [ ] Every requirement in the design specification maps to a task and test.
- [ ] `offlineMode` changes emit without restart.
- [ ] Automatic state is never persisted.
- [ ] Raw network remains available to health probing.
- [ ] Only classified service failures enter automatic Offline Mode.
- [ ] Download intents wait/requeue without fixed-delay retries.
- [ ] Cached fallback uses usable files and Media3 upcoming order.
- [ ] No connectivity path mutates queue contents or order.
- [ ] User pause wins over restoration.
- [ ] Exact notification copy appears once and clears silently.
- [ ] Android-only build, ADB, release, and artifact gates are explicit.
- [ ] Release version stays in the current `iota##` family and increments once.
- [ ] Cleanup is limited to this worktree after public ancestry is proven.
