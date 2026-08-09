# Foreground WebView Handoff Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every delegated agent must be told not to spawn subagents.

**Goal:** Prevent PlayLikeCurl-to-WebView handoff from ever exposing the source spread or an unrelated passive-raster page after the native renderer has reached the committed destination.

**Architecture:** Add one host-owned foreground-WebView ownership state machine shared by live relocation and passive raster work. A live claim begins after relocation reservation and before renderer admission, preempts passive work, waits for typed visual restoration, and remains current until the inline destination shield has faded and the exposed WebView frame has committed. Foliate keeps exact-position authority; a paginator-private, receipt-bound visible-text proof makes live presentation receipts unavailable unless the current visible text still matches the committed destination, while a native mutation generation fences every asynchronous callback.

**Tech Stack:** Kotlin Multiplatform, Android WebView, Compose Multiplatform, JavaScript ES modules, Foliate paginator, PlayLikeCurl `GLSurfaceView`, Kotlin/JVM Android host tests, Node test runner, Gradle, ADB-based ReaderDev acceptance.

---

## Fixed architectural decisions

1. Foliate remains the only exact text-page commitment authority. The new ownership generation coordinates native composition; it never replaces `layoutGeneration`, `viewGeneration`, or `commitSequence`.
2. PlayLikeCurl remains the canonical native renderer and the source of the destination raster retained by the inline shield.
3. The foreground WebView is exclusive mutable presentation state. Passive preview exposure/restoration and live exact relocation cannot overlap.
4. Live priority starts only after relocation capacity has been reserved, but before PlayLikeCurl renderer admission. This avoids cancelling passive work for gestures that cannot enter the relocation protocol.
5. Renderer animation may proceed while passive visual restoration completes, but exact WebView dispatch must wait for `Restored`. `Detached` and `TimedOut` fail closed.
6. Consecutive live claims share one live ownership epoch. Releasing one claim cannot admit passive work while another reservation/relocation still owns the pipeline.
7. Receipt-bound visible text never crosses the JavaScript boundary. Foliate stores normalized visible text in a private `WeakMap`; native sees only whether the ordinary live presentation receipt remains available.
8. Empty visible text is valid when receipt/index/generation/raster proof also match. This preserves image-only and intentionally blank reflowable pages. A failed range read is unavailable proof and fails closed.
9. The current queue head is not completed before the final presentation path succeeds. Inline-shield presentation, curl hiding, fade, and the exposed-frame latch are part of handoff finalization.
10. Passive work resumes only after the last live claim releases. There is no fixed sleep or sampled-frame count in the authority chain; timeouts remain failure bounds only.
11. Shield presentation failure retains PlayLikeCurl or the opaque destination shield and enters typed recovery. It must never hide PlayLikeCurl and expose an unproved WebView.
12. No publication text, normalized text, text-derived value, raster payload, URL, href, CFI, book ID, or user/session identifier may be logged or persisted by this fix.

## File responsibility map

### New files

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnership.android.kt` — exclusive passive/live leases, asynchronous passive restoration, mutation generation, multi-claim live epoch, teardown drain, privacy-safe counts.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnershipTest.kt` — behavioral state-machine and adversarial interleaving tests.

### Principal modified files

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt` — constructs and shares one ownership coordinator; reports bounded counts.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationGestureCoordinator.android.kt` — acquires a live claim after reservation, transfers it with the relocation, and releases every non-committed path exactly once.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt` — stores request-to-claim ownership, waits for passive restoration before exact JS dispatch, sends mutation generation, fences validation, and releases only after handoff finalization.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt` — admits all normal/repair/background passive work through the same passive lease and supplies the cancellation/restoration callback used by live preemption.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchController.android.kt` — carries passive mutation generation and preserves typed restoration completion.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoff.android.kt` — includes mutation generation in currentness and makes shield/fade completion part of handoff completion.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInlineRasterShield.android.kt` — reports committed presentation and committed exposed-frame completion; failure restores opacity.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnPresentationReceipt.android.kt` — strictly parses and matches `foregroundMutationGeneration`.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt` and `ReaderPageTurnBundleSource.android.kt` — carry the matching mutation generation through initial/final/third receipt validation.
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js` — records and validates private receipt-bound normalized visible text.
- `composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js` — associates the private visible-content proof with JS-local receipt owners.
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js` — requires visible-content proof before live settlement/presentation publication and carries native mutation generation.
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js` — carries and fences passive mutation generation.
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-presentation.js` — adds strict mutation-generation receipt keys and matching.
- Relevant Android host, common, browser, relocation-bridge, privacy, package, and ownership tests listed in the tasks below.

---

### Task 1: Add the exclusive foreground-WebView ownership state machine

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnership.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnershipTest.kt`

- [ ] **Step 1: Write failing behavioral tests for passive admission and live preemption**

Create tests with these exact scenarios:

```kotlin
@Test
fun liveClaimWaitsForPassiveRestorationBeforeMutation() {
	var finishRestoration: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
	val ownership = ReaderForegroundWebViewOwnership()
	val passive = checkNotNull(
		ownership.tryAcquirePassive(sessionId = 7L) { onRestored ->
			finishRestoration = onRestored
		}
	)
	val live = ownership.acquireLive(gestureId = 14L)
	val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
	ownership.whenLiveReady(live, readiness::add)

	assertFalse(ownership.isCurrent(passive))
	assertNull(ownership.beginLiveMutation(live))
	assertTrue(readiness.isEmpty())

	checkNotNull(finishRestoration)(ReaderPageRasterCancellationRestoration.Restored)

	assertEquals(listOf(ReaderForegroundWebViewLiveReadiness.Ready), readiness)
	assertNotNull(ownership.beginLiveMutation(live))
}

@Test
fun secondLiveClaimPreventsPassiveGapWhenFirstCompletes() {
	val ownership = ReaderForegroundWebViewOwnership()
	val first = ownership.acquireLive(gestureId = 14L)
	val second = ownership.acquireLive(gestureId = 15L)
	assertTrue(ownership.releaseLive(first))
	assertNull(ownership.tryAcquirePassive(8L) { error("must not preempt") })
	assertNotNull(ownership.beginLiveMutation(second))
	assertTrue(ownership.releaseLive(second))
	assertNotNull(ownership.tryAcquirePassive(8L) { error("not preempted") })
}

@Test
fun restorationTimeoutFailsEveryWaitingLiveClaimClosed() {
	var finishRestoration: ((ReaderPageRasterCancellationRestoration) -> Unit)? = null
	val ownership = ReaderForegroundWebViewOwnership()
	checkNotNull(ownership.tryAcquirePassive(7L) { finishRestoration = it })
	val live = ownership.acquireLive(14L)
	val readiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()
	ownership.whenLiveReady(live, readiness::add)

	checkNotNull(finishRestoration)(ReaderPageRasterCancellationRestoration.TimedOut)

	assertEquals(
		listOf(
			ReaderForegroundWebViewLiveReadiness.Failed(
				ReaderPageRasterCancellationRestoration.TimedOut
			)
		),
		readiness
	)
	assertNull(ownership.beginLiveMutation(live))
}
```

Also cover stale passive release, duplicate restoration callback, detached restoration, mutation-generation monotonicity and JavaScript-safe bounds, claim release while restoration is pending, one `onPassiveAvailable` notification after the last live claim, and `close()` draining all counts without scheduling passive work.

- [ ] **Step 2: Run the new test class and confirm it fails before implementation**

Run:

```bash
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderForegroundWebViewOwnershipTest"
```

Expected: compilation failure because the ownership types do not exist.

- [ ] **Step 3: Implement the focused ownership API**

Create these public-in-package contracts and implement their state transitions in the same file:

```kotlin
@JvmInline
internal value class ReaderForegroundWebViewMutationGeneration(val value: Long) {
	init {
		require(value in 1L..ReaderPageTurnPresentationMaximumSafeInteger)
	}
}

internal data class ReaderForegroundWebViewPassiveLease internal constructor(
	val leaseId: Long,
	val sessionId: Long,
	val mutationGeneration: ReaderForegroundWebViewMutationGeneration
)

internal data class ReaderForegroundWebViewLiveClaim internal constructor(
	val claimId: Long,
	val gestureId: Long
)

internal sealed interface ReaderForegroundWebViewLiveReadiness {
	data object Ready : ReaderForegroundWebViewLiveReadiness
	data class Failed(
		val restoration: ReaderPageRasterCancellationRestoration
	) : ReaderForegroundWebViewLiveReadiness
	data object Invalidated : ReaderForegroundWebViewLiveReadiness
}

internal data class ReaderForegroundWebViewOwnershipSnapshot(
	val passiveOwners: Int,
	val liveClaims: Int,
	val restorationCallbacks: Int,
	val closed: Boolean
)

internal class ReaderForegroundWebViewOwnership(
	private val onPassiveAvailable: () -> Unit = {}
) {
	fun canAcquirePassive(): Boolean

	fun tryAcquirePassive(
		sessionId: Long,
		cancelAndRestore: (
			(ReaderPageRasterCancellationRestoration) -> Unit
		) -> Unit
	): ReaderForegroundWebViewPassiveLease?

	fun acquireLive(gestureId: Long): ReaderForegroundWebViewLiveClaim

	fun whenLiveReady(
		claim: ReaderForegroundWebViewLiveClaim,
		callback: (ReaderForegroundWebViewLiveReadiness) -> Unit
	)

	fun beginLiveMutation(
		claim: ReaderForegroundWebViewLiveClaim
	): ReaderForegroundWebViewMutationGeneration?

	fun isCurrent(lease: ReaderForegroundWebViewPassiveLease): Boolean

	fun isCurrent(
		claim: ReaderForegroundWebViewLiveClaim,
		generation: ReaderForegroundWebViewMutationGeneration
	): Boolean

	fun releasePassive(lease: ReaderForegroundWebViewPassiveLease): Boolean
	fun releaseLive(claim: ReaderForegroundWebViewLiveClaim): Boolean
	fun snapshot(): ReaderForegroundWebViewOwnershipSnapshot
	fun close()
}
```

Implementation rules:

- `canAcquirePassive` is a read-only idle-state check used by host admission; the subsequent `tryAcquirePassive` remains the authoritative acquisition.
- `tryAcquirePassive` succeeds only from idle and increments the mutation generation.
- `acquireLive` from passive immediately invalidates `isCurrent(passive)`, enters restoring-live state, and invokes `cancelAndRestore` once after installing that state.
- `acquireLive` from idle/live creates or joins the live epoch.
- `whenLiveReady` publishes exactly one terminal per claim.
- Only `Restored` admits `beginLiveMutation`; `Detached` and `TimedOut` publish `Failed` and invalidate the waiting claims.
- `beginLiveMutation` increments the JavaScript-safe generation and returns null for stale claims.
- The last live claim release returns to idle and invokes `onPassiveAvailable` once; earlier releases do neither.
- `close()` invalidates every callback and reports zero owners/callbacks without invoking `onPassiveAvailable`.
- Use `Math.incrementExact`; reject values beyond `ReaderPageTurnPresentationMaximumSafeInteger` before publication.

- [ ] **Step 4: Run the behavioral test class**

Run the command from Step 2.

Expected: all `ReaderForegroundWebViewOwnershipTest` cases pass.

- [ ] **Step 5: Commit and push the state machine checkpoint**

```bash
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnership.android.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnershipTest.kt
git commit -m "feat(reader): add foreground WebView ownership"
git push -u fork fix/foreground-webview-handoff-ownership
```

---

### Task 2: Put every passive raster path behind one restorable lease

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt:182-217,315-369,967-986,1194-1299,1608-1622,2022-2031,2248-2318`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchController.android.kt:74-80,534-629,676-963`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`

- [ ] **Step 1: Write failing passive-ownership tests**

Add tests proving:

```kotlin
@Test
fun passivePrewarmIsRejectedWhileLiveOwnershipExists() {
	val ownership = ReaderForegroundWebViewOwnership()
	val live = ownership.acquireLive(gestureId = 14L)
	assertNull(ownership.tryAcquirePassive(sessionId = 29L) { error("not acquired") })
	assertTrue(ownership.releaseLive(live))
}

@Test
fun livePreemptionWaitsForBatchRestorationTerminal() {
	val terminals = mutableListOf<ReaderPageRasterCancellationRestoration>()
	val join = ReaderPageRasterCancellationJoin(expectedCallbacks = 3, terminals::add)
	join.complete(ReaderPageRasterCancellationRestoration.Restored)
	join.complete(ReaderPageRasterCancellationRestoration.Detached)
	assertTrue(terminals.isEmpty())
	join.complete(ReaderPageRasterCancellationRestoration.Restored)
	assertEquals(listOf(ReaderPageRasterCancellationRestoration.Detached), terminals)
}

@Test
fun restorationJoinFailsClosedWhenAnyBatchTimesOut() {
	val terminals = mutableListOf<ReaderPageRasterCancellationRestoration>()
	val join = ReaderPageRasterCancellationJoin(expectedCallbacks = 3, terminals::add)
	join.complete(ReaderPageRasterCancellationRestoration.Restored)
	join.complete(ReaderPageRasterCancellationRestoration.TimedOut)
	join.complete(ReaderPageRasterCancellationRestoration.Detached)
	assertEquals(listOf(ReaderPageRasterCancellationRestoration.TimedOut), terminals)
}
```

Extend the existing source/integration tests to assert that normal preparation, targeted repair, and adjacent/background prefetch all call the same `tryAcquirePassive` helper and release only from batch terminal/restoration paths.

- [ ] **Step 2: Run focused passive tests and confirm failure**

```bash
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderForegroundWebViewOwnershipTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRasterPreparationSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest"
```

Expected: new tests fail because preparation does not acquire ownership and the restoration join does not exist.

- [ ] **Step 3: Add the ownership dependency and a typed restoration join**

Add to `ReaderPageRasterPreparationController`:

```kotlin
private val foregroundWebViewOwnership: ReaderForegroundWebViewOwnership
```

Add a small internal join beside `ReaderPageRasterCancellationRestoration`:

```kotlin
internal class ReaderPageRasterCancellationJoin(
	expectedCallbacks: Int,
	private val onCompleted: (ReaderPageRasterCancellationRestoration) -> Unit
) {
	private var remaining = expectedCallbacks
	private var terminal = ReaderPageRasterCancellationRestoration.Restored
	private var completed = false

	init {
		require(expectedCallbacks > 0)
	}

	fun complete(result: ReaderPageRasterCancellationRestoration) {
		if (completed) return
		terminal = when {
			terminal == ReaderPageRasterCancellationRestoration.TimedOut ||
				result == ReaderPageRasterCancellationRestoration.TimedOut ->
				ReaderPageRasterCancellationRestoration.TimedOut
			terminal == ReaderPageRasterCancellationRestoration.Detached ||
				result == ReaderPageRasterCancellationRestoration.Detached ->
				ReaderPageRasterCancellationRestoration.Detached
			else -> ReaderPageRasterCancellationRestoration.Restored
		}
		remaining -= 1
		if (remaining != 0) return
		completed = true
		onCompleted(terminal)
	}
}
```

- [ ] **Step 4: Acquire and release one passive lease for each batch transaction**

Use one helper for all three batch ports:

```kotlin
private fun acquirePassiveOwnership(
	sessionId: Long
): ReaderForegroundWebViewPassiveLease? =
	foregroundWebViewOwnership.tryAcquirePassive(sessionId) { onRestored ->
		cancelPassiveWorkForLiveRelocation(onRestored)
	}
```

Store the active lease with the active batch/session. Pass `lease.mutationGeneration` into the batch start. Release the lease only when the batch completes without exposed preview state, or after its cancellation restoration terminal is delivered. If acquisition returns null, retain the existing typed deferred-prewarm behavior and request retry after live ownership releases; do not busy-loop.

Use `foregroundWebViewOwnership.canAcquirePassive()` for the read-only `visualCommitPending` admission input, then call `tryAcquirePassive` as the authoritative acquisition. Remove the constant `visualCommitPending = false`.

Implement `cancelPassiveWorkForLiveRelocation` by cancelling the normal, repair, and background batch ports and aggregating their typed restoration callbacks through `ReaderPageRasterCancellationJoin`.

- [ ] **Step 5: Fence passive batch callbacks by their mutation generation**

Add `foregroundMutationGeneration: ReaderForegroundWebViewMutationGeneration` to batch start/capture state. Every expose, confirm, capture, cache publication, restoration, and completion callback must require both the existing session fence and:

```kotlin
foregroundWebViewOwnership.isCurrent(passiveLease)
```

Late callbacks may release local bitmap/callback ownership but may not expose preview content or publish rasters.

- [ ] **Step 6: Run focused passive tests**

Run the command from Step 2.

Expected: all selected tests pass, including timeout/detach restoration cases.

- [ ] **Step 7: Commit and push passive ownership integration**

```bash
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchController.android.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationSourceTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt
git commit -m "fix(reader): serialize passive WebView raster work"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 3: Carry live ownership from gesture reservation through exact dispatch

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationGestureCoordinator.android.kt:12-261`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt:799-998,1376-1509,4477-4559`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageDiagnostic.android.kt:82-89`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationGestureCoordinatorTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageDiagnosticTest.kt`
- Modify: `scripts/test-reader-relocation-bridge.mjs`

- [ ] **Step 1: Write failing lease-lifecycle tests around the gesture coordinator**

Extend `ReaderPageRelocationGestureCoordinatorTest` to prove:

- Capacity rejection happens before live acquisition.
- Successful reservation acquires before `rendererAdmission` executes.
- Renderer false/throw, synchronous terminal, generation drift, terminal-publication failure, and `cancelAll()` each release exactly once.
- A committed relocation transfers its claim to the dispatch callback instead of releasing it.

Use the new callback shape:

```kotlin
dispatch = { request, foregroundClaim ->
	dispatched += request to foregroundClaim
}
```

- [ ] **Step 2: Run coordinator tests and confirm failure**

```bash
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderPageRelocationGestureCoordinatorTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSourceTest"
```

Expected: compile/assertion failure because the coordinator does not own or transfer live claims.

- [ ] **Step 3: Acquire after queue reservation and transfer on commit**

Change the private owner to:

```kotlin
private data class Owner(
	val reservation: ReaderPageRelocationReservation,
	val metadata: ReaderPageRelocationReservationMetadata,
	val foregroundClaim: ReaderForegroundWebViewLiveClaim,
	var rendererAdmissionOpen: Boolean = false,
	var synchronousTerminal: ReaderPageRelocationStartResult.TerminalPublished? = null
)
```

Inject `ReaderForegroundWebViewOwnership`. In the `Reserved` branch, call `acquireLive(metadata.gestureId)` before opening renderer admission. All `releaseOwner` paths must call `releaseLive`; successful `enqueueReserved` transfers the claim through:

```kotlin
dispatch: (
	ReaderPageRelocationRequest,
	ReaderForegroundWebViewLiveClaim
) -> Unit
```

Do not release the transferred claim from the gesture coordinator.

- [ ] **Step 4: Store request-to-claim ownership before dispatch**

In `ReaderPlayLikeCurlFoliateController`, add:

```kotlin
private val relocationForegroundClaims =
	mutableMapOf<String, ReaderForegroundWebViewLiveClaim>()
private val relocationMutationGenerations =
	mutableMapOf<String, ReaderForegroundWebViewMutationGeneration>()
```

The commit dispatch callback must insert the claim by relocation token before calling `dispatchNextRelocation()`. Every reject, replacement, recovery, cancellation, destroy, and drain path must remove and release the exact claim once.

- [ ] **Step 5: Wait for passive restoration before exact JavaScript dispatch**

Replace immediate dispatch with:

```kotlin
private fun dispatchRelocation(request: ReaderPageRelocationRequest) {
	val claim = relocationForegroundClaims[request.token.value]
		?: return rejectDispatchedRelocation(
			request,
			ReaderPageRelocationDiagnosticRejectionReason.OwnershipUnavailable
		)
	foregroundWebViewOwnership.whenLiveReady(claim) { readiness ->
		when (readiness) {
			ReaderForegroundWebViewLiveReadiness.Ready -> {
				val generation = foregroundWebViewOwnership.beginLiveMutation(claim)
					?: return@whenLiveReady rejectDispatchedRelocation(
						request,
						ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
					)
				relocationMutationGenerations[request.token.value] = generation
			val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
				?: return@whenLiveReady rejectDispatchedRelocation(
					request,
					ReaderPageRelocationDiagnosticRejectionReason.WebViewUnavailable
				)
			clearRetainedInlineHandoffSnapshot()
			dispatchExactVisualPage(webView, request, generation)
		}
			is ReaderForegroundWebViewLiveReadiness.Failed,
			ReaderForegroundWebViewLiveReadiness.Invalidated ->
				rejectDispatchedRelocation(
					request,
					ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
				)
		}
	}
}
```

Add `settleForegroundMutationGeneration` to the JSON command. Do not arm the JS dispatch timeout until `evaluateJavascript` has actually been called. Add `OwnershipUnavailable`, `OwnershipInvalidated`, and `WebViewUnavailable` to `ReaderPageRelocationDiagnosticRejectionReason`; extend `ReaderPageDiagnosticTest` so each maps to a bounded rejection without serializing claim IDs, generations, or session data.

- [ ] **Step 6: Extend relocation bridge fixtures and run focused tests**

Update all `goToVisualPage` fixtures to include a positive JavaScript-safe mutation generation and add malformed/missing/reused-generation rejection cases.

Run:

```bash
node scripts/test-reader-relocation-bridge.mjs
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderPageRelocationGestureCoordinatorTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRelocationDispatchTimeoutTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageDiagnosticTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageControllerDestroyFenceTest"
```

Expected: all commands pass; timeout starts only after live readiness/restoration.

- [ ] **Step 7: Commit and push live ownership transfer**

```bash
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationGestureCoordinator.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageDiagnostic.android.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRelocationGestureCoordinatorTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageDiagnosticTest.kt \
  scripts/test-reader-relocation-bridge.mjs
git commit -m "fix(reader): own WebView across exact relocation"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 4: Add Foliate-private destination visible-text proof

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js:1391-1417,1564-1693`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js:1-143`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js:337-490,531-908`
- Modify: `tools/reader-harness/src/paginator-commit-receipt.test.mjs`
- Modify: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [ ] **Step 1: Write failing browser tests for receipt-bound visible content**

Add real Chromium paginator cases that prove:

```javascript
assert.equal(paginator.validateTextPageVisibleContent(receipt), true)
visibleTextNode.data = 'different in-memory fixture text'
assert.equal(paginator.validateTextPageVisibleContent(receipt), false)
```

Use synthetic harness text only. Also test:

- Same normalized text with whitespace-only differences remains valid.
- Empty visible text is a valid private proof.
- An unavailable/disconnected range returns false.
- Wrong, stale, replaced-view, invalidated-layout, scrolled, and destroyed receipts return false.
- Neither the commit result nor `JSON.stringify(receipt)` contains text, normalized text, a digest, or a fingerprint field.

In consumer tests, assert that a live presentation receipt is withheld when the commit owner lacks current visible-content proof, while passive/profile consumers remain unchanged.

- [ ] **Step 2: Run browser tests and confirm failure**

```bash
npm --prefix tools/reader-harness run test:paginator-commit-receipt
npm --prefix tools/reader-harness run test:paginator-commit-consumers
```

Expected: new tests fail because `validateTextPageVisibleContent` and JS-local proof ownership do not exist.

- [ ] **Step 3: Record normalized visible text privately in Foliate**

Add a private `WeakMap` and helpers to `Paginator`:

```javascript
#textPageVisibleContent = new WeakMap()

#normalizedVisibleText() {
    try {
        const range = this.#getVisibleRange()
        if (!range?.startContainer?.isConnected) return null
        return String(range.toString())
            .normalize('NFKC')
            .replace(/\s+/gu, ' ')
            .trim()
    } catch {
        return null
    }
}

#rememberTextPageVisibleContent(receipt) {
    const normalized = this.#normalizedVisibleText()
    if (normalized == null) return false
    this.#textPageVisibleContent.set(receipt, normalized)
    return true
}

validateTextPageVisibleContent(receipt) {
    if (!this.validateTextPageCommit(receipt) ||
        !this.#textPageVisibleContent.has(receipt)) return false
    const current = this.#normalizedVisibleText()
    return current != null && current === this.#textPageVisibleContent.get(receipt)
}
```

After issuing the exact receipt, remember its visible text. Do not add text-derived fields to the receipt and do not change `validateTextPageCommit` semantics.

- [ ] **Step 4: Add JS-local ownership without affecting passive consumers**

In `navic-reader-paginator-commit.js`, add a second `WeakMap` for owners that explicitly require visible-content proof:

```javascript
const textPageVisibleContentOwners = new WeakMap()

export function readerRememberTextPageVisibleContent(owner) {
  const commitment = textPageCommitOwners.get(owner)
  if (!commitment ||
      commitment.renderer?.validateTextPageVisibleContent?.(
        commitment.receipt
      ) !== true) return false
  textPageVisibleContentOwners.set(owner, commitment)
  return true
}

export function readerTextPageCommitOwnerHasExpectedVisibleContent(owner) {
  const commitment = owner && typeof owner === 'object'
    ? textPageVisibleContentOwners.get(owner)
    : null
  return Boolean(commitment &&
    commitment.renderer?.validateTextPageCommit?.(commitment.receipt) === true &&
    commitment.renderer?.validateTextPageVisibleContent?.(commitment.receipt) === true)
}
```

`readerCopyTextPageCommit` copies this second capability only when the source owns it. `readerForgetTextPageCommit` deletes both maps.

- [ ] **Step 5: Require the proof only for live settlement/presentation**

After `readerRememberTextPageCommit(pending, ...)`, require `readerRememberTextPageVisibleContent(pending)` before publishing the live settlement. Add `readerTextPageCommitOwnerHasExpectedVisibleContent(target)` to `pageTurnLivePresentationTargetMatchesCurrent` and `issuePageTurnLivePresentationReceipt`.

This creates the required causal order: exact commit and text match occur before native acknowledgement; native then requests its visual-state callback; every later live receipt read revalidates the same private expected content.

- [ ] **Step 6: Run browser and destination-source tests**

```bash
npm --prefix tools/reader-harness run test:paginator-commit-receipt
npm --prefix tools/reader-harness run test:paginator-commit-consumers
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

Expected: all tests pass and no serialized contract contains protected text or a text-derived value.

- [ ] **Step 7: Commit and push private content proof**

```bash
git add composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js \
  composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js \
  composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js \
  tools/reader-harness/src/paginator-commit-receipt.test.mjs \
  tools/reader-harness/src/paginator-commit-consumers.test.mjs \
  composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt
git commit -m "fix(reader): prove live destination text privately"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 5: Put mutation generation into every presentation receipt and callback fence

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-presentation.js:1-108`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js:337-908`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js:606-1049`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnPresentationReceipt.android.kt:21-181`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt:458-747`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt:1941-2387`
- Modify: `tools/reader-harness/src/presentation-receipt.test.mjs`
- Modify: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnPresentationReceiptTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleTest.kt`

- [ ] **Step 1: Write failing strict-wire tests**

Add native and browser cases for missing, negative, fractional, string, too-large, stale, initial/final mismatch, and third-receipt mutation-generation changes. Extend both preview and live fixtures with:

```json
"foregroundMutationGeneration": 41
```

Assert strict key sets reject legacy receipts without the field.

- [ ] **Step 2: Run strict receipt tests and confirm failure**

```bash
npm --prefix tools/reader-harness run test:presentation-receipt
npm --prefix tools/reader-harness run test:paginator-commit-consumers
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnPresentationReceiptTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleTest"
```

Expected: new strict-contract cases fail.

- [ ] **Step 3: Extend the Kotlin receipt and target contracts**

Add the required field to both receipt and target:

```kotlin
val foregroundMutationGeneration: Long
```

Validate it with `requirePageTurnPositiveWireInteger`. Add `foregroundMutationGeneration` to `PreviewReceiptKeys` and `LiveReceiptKeys`, parse it strictly, and require equality in `matches`.

- [ ] **Step 4: Extend JavaScript receipt issue and matching**

Add `foregroundMutationGeneration` to both exact live and preview target key sets in `navic-reader-page-turn-presentation.js`. Validate with `Number.isSafeInteger(value) && value > 0`; issue no receipt for malformed or missing authority.

Carry `settleForegroundMutationGeneration` through live pending state, settlement, target, and receipt. Carry the passive lease generation through preview batch state, expose, confirmation, and restoration. Any late callback with a superseded generation must return without publishing or changing presentation authority.

- [ ] **Step 5: Fence native capture and validation with the same generation**

Construct every `ReaderPageTurnPresentationTarget.Live` and `.Preview` with the generation supplied by the exact live claim or passive lease. Require the ownership coordinator to report it current before initial receipt read, renderer callback, `PixelCopy`, final receipt, semantic comparison, third receipt, cache publication, and completion callback.

- [ ] **Step 6: Run strict receipt/capture tests**

Run the command from Step 2.

Expected: all selected Node and Android tests pass; a generation change at any boundary rejects publication.

- [ ] **Step 7: Commit and push mutation-generation fencing**

```bash
git add composeApp/src/androidMain/assets/reader/navic-reader-page-turn-presentation.js \
  composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js \
  composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnPresentationReceipt.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt \
  tools/reader-harness/src/presentation-receipt.test.mjs \
  tools/reader-harness/src/paginator-commit-consumers.test.mjs \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnPresentationReceiptTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSourceTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleTest.kt
git commit -m "fix(reader): fence presentation by WebView mutation"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 6: Make shield fade and exposed-frame commit part of handoff completion

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoff.android.kt:752-863,1285-1541`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInlineRasterShield.android.kt:25-211`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt:4134-4281,4676-4744`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoffTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlSurfaceBoundsTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`

- [ ] **Step 1: Write the frame-121 regression as deterministic handoff tests**

Add behavioral tests proving:

1. Content acceptance does not call `queue.completeHandoff` yet.
2. Shield presentation failure does not hide PlayLikeCurl and enters recovery.
3. Shield commit hides PlayLikeCurl, but queue/live ownership remain active through fade.
4. Fade animation end alone does not complete handoff.
5. Only the subsequent exposed-WebView frame commit completes the queue head and releases the live claim.
6. Mutation change, stale receipt, detach, destroy, or a newer request during fade cannot complete/release the old handoff.
7. Passive acquisition remains rejected at every boundary above.

Use an asynchronous finalizer callback:

```kotlin
finalizePresentation = { request, onFinalized ->
	presentationFinalizers += request.token.value to onFinalized
}
```

- [ ] **Step 2: Run handoff/shield tests and confirm failure**

```bash
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderWebViewVisualHandoffTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlSurfaceBoundsTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSourceTest"
```

Expected: new tests fail because queue completion precedes shield/fade completion and `fadeOut` has no terminal callback.

- [ ] **Step 3: Add asynchronous presentation finalization to the coordinator**

Replace `hideSurface` with:

```kotlin
private val finalizePresentation: (
	ReaderPageRelocationRequest,
	(Boolean) -> Unit
) -> Unit
```

Add phase `FinalizingPresentation`. After accepted content validation, start finalization without calling `queue.completeHandoff`. On `true`, revalidate queue head/current state/mutation generation, call `queue.completeHandoff`, publish completion, release the request claim, then dispatch the next queued request. On `false` or stale currentness, call existing typed recovery while the queue head and safe visual owner still exist.

- [ ] **Step 4: Make inline fade report a committed exposed frame**

Change the API to:

```kotlin
fun fadeOut(
	durationMillis: Long,
	onExposedFrameCommitted: (Boolean) -> Unit
)
```

At animation end, keep the destination bitmap owned with `alpha = 0f`, register a frame-commit callback on the host (`ViewTreeObserver.registerFrameCommitCallback` on Android Q+, existing two-`postOnAnimation` latch below Q), and invalidate the host. Only after that latch succeeds may `clearPresentation()` and `onExposedFrameCommitted(true)` run. On detach, timeout, cancellation, or stale request, restore `alpha = 1f`, retain the destination bitmap, and report false. The timeout is a failure bound, never positive evidence.

- [ ] **Step 5: Enforce final currentness in the controller**

Before presenting the shield, before hiding curl, before starting fade, and in the exposed-frame callback require:

```kotlin
foregroundWebViewOwnership.isCurrent(claim, mutationGeneration)
```

plus existing session, destination, raster, texture, gesture, generation-owner, and queue-head checks. On shield presentation failure, do not call `hideCurlSurface()`. Keep PlayLikeCurl visible and report finalization failure.

- [ ] **Step 6: Run handoff/shield tests**

Run the command from Step 2.

Expected: all tests pass and no path completes queue/live ownership before the exposed-frame latch.

- [ ] **Step 7: Commit and push fail-closed finalization**

```bash
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoff.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInlineRasterShield.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoffTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlSurfaceBoundsTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt
git commit -m "fix(reader): complete handoff after WebView exposure"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 7: Wire one host owner, lifecycle drain, and bounded diagnostics

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt:915-919,996-1107,1182-1217,1574-1639`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageOwnershipProbe.android.kt:26-46`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageOwnershipProbeTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPagePendingCallbackOwnersTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageControllerDestroyFenceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`

- [ ] **Step 1: Write failing host-wiring and drain tests**

Assert that the host constructs exactly one `ReaderForegroundWebViewOwnership`, injects the same instance into preparation/gesture/PlayLikeCurl paths, closes it after controller callbacks are fenced, and reports:

```kotlin
foregroundPassiveOwners <= 1
foregroundLiveClaims <= relocationQueue.capacity
foregroundRestorationCallbacks <= 1
```

Teardown must finish with all three values zero.

- [ ] **Step 2: Run ownership/lifecycle tests and confirm failure**

```bash
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.ui.screens.reader.ReaderPageOwnershipProbeTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPagePendingCallbackOwnersTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageControllerDestroyFenceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSourceTest"
```

Expected: new wiring/count assertions fail.

- [ ] **Step 3: Construct and share one owner**

In `KomikkuReaderNativeFrameHost`, create one owner before either controller:

```kotlin
private val foregroundWebViewOwnership = ReaderForegroundWebViewOwnership {
	requestPageTurnPrewarmWhenReady()
}
```

Use the host’s existing main-thread `requestPageTurnPrewarmWhenReady()` scheduler; it must continue to no-op after teardown. Pass the owner to raster preparation, gesture coordination, PlayLikeCurl dispatch/validation, and ownership diagnostics. The host’s prewarm request must treat any live claim/restoration as unavailable passive ownership instead of relying on stable pre-draw alone.

- [ ] **Step 4: Extend privacy-safe bounded ownership snapshots**

Add numeric counts and limits only. Do not include gesture IDs, tokens, Foliate session IDs, publication identifiers, mutation generations, or receipt data in logs/evidence. Include the new counts in `withinBounds` and teardown assertions.

- [ ] **Step 5: Run host ownership/lifecycle tests**

Run the command from Step 2.

Expected: all selected tests pass and teardown drains to zero.

- [ ] **Step 6: Commit and push host integration**

```bash
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt \
  composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageOwnershipProbe.android.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageOwnershipProbeTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPagePendingCallbackOwnersTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageControllerDestroyFenceTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt
git commit -m "fix(reader): share foreground WebView ownership"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 8: Add the end-to-end adversarial interleaving regression gate

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoffTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInlineRasterShieldTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- Modify: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
- Modify: `scripts/test-reader-relocation-bridge.mjs`

- [ ] **Step 1: Add a deterministic source-to-destination race model**

Model source ordinal `0`, destination ordinal `2`, a passive preview attempting other numeric ordinals, and callbacks delivered in every dangerous order:

- passive expose before live reservation;
- live reservation during passive capture;
- restoration callback before/after renderer settlement;
- stale passive capture after live mutation begins;
- visual-state callback before/after stale passive callback;
- content match followed by attempted passive reacquisition;
- shield commit, animation end, and exposed-frame latch;
- cancellation/detach at each boundary.

Assert throughout:

```kotlin
assertFalse(passiveMutationPublishedWhileLiveOwned)
assertFalse(shieldReleasedBeforeDestinationProof)
assertFalse(sourceOrPreviewPresentationExposed)
assertEquals(2, completedWebViewOrdinal)
assertEquals(0, ownership.snapshot().passiveOwners)
assertEquals(0, ownership.snapshot().liveClaims)
```

Tests must use only synthetic numeric page identities and synthetic text.

- [ ] **Step 2: Run the adversarial gate repeatedly without sleeps**

```bash
for i in 1 2 3 4 5; do
  ./gradlew.bat :composeApp:testAndroidHostTest \
    --tests "paige.navic.ui.screens.reader.ReaderForegroundWebViewOwnershipTest" \
    --tests "paige.navic.ui.screens.reader.ReaderWebViewVisualHandoffTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageInlineRasterShieldTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest" \
    --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" || exit 1
done
npm --prefix tools/reader-harness run test:paginator-commit-consumers
node scripts/test-reader-relocation-bridge.mjs
```

Expected: all five deterministic runs and both JS gates pass; no test uses `sleep`, polling, or frame sampling for positive authority.

- [ ] **Step 3: Commit and push the regression gate**

```bash
git add composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoffTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInlineRasterShieldTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt \
  composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt \
  tools/reader-harness/src/paginator-commit-consumers.test.mjs \
  scripts/test-reader-relocation-bridge.mjs
git commit -m "test(reader): reproduce foreground handoff race"
git push fork fix/foreground-webview-handoff-ownership
```

---

### Task 9: Run focused, broad-baseline, privacy, governance, and package gates

**Files:**
- Modify only if evidence finds a defect: files already listed in Tasks 1-8
- Keep local: `.codex-validation/reader-foreground-webview-handoff-*`

- [ ] **Step 1: Run the complete browser gate**

```bash
npm --prefix tools/reader-harness run test:command-ack-runtime
npm --prefix tools/reader-harness run test:page-turn-model
npm --prefix tools/reader-harness run test:paginator-commit-receipt
npm --prefix tools/reader-harness run test:paginator-commit-consumers
npm --prefix tools/reader-harness run test:presentation-receipt
npm --prefix tools/reader-harness run test:baseline-hmac
node --test tools/reader-harness/src/paginator-commit-receipt-acceptance.test.mjs
node scripts/test-reader-relocation-bridge.mjs
```

Expected: all commands exit zero.

- [ ] **Step 2: Run the focused Android gate**

```bash
./gradlew.bat :composeApp:testAndroidHostTest \
  --tests "paige.navic.reader.ReaderPageRelocationQueueTest" \
  --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderForegroundWebViewOwnershipTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRelocationGestureCoordinatorTest" \
  --tests "paige.navic.ui.screens.reader.ReaderWebViewVisualHandoffTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnPresentationReceiptTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRasterPreparationSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSourceTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlSurfaceBoundsTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageOwnershipProbeTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPagePendingCallbackOwnersTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageRelocationDispatchTimeoutTest" \
  --tests "paige.navic.ui.screens.reader.ReaderPageControllerDestroyFenceTest"
```

Expected: all selected tests pass.

- [ ] **Step 3: Run the broad host suite and classify only against the frozen pre-fix baseline**

```bash
./gradlew.bat :composeApp:testAndroidHostTest
```

Expected: no new failure names or counts attributable to this branch. If the repository’s known broad failures remain, compare exact test names to the preserved iota50 baseline; do not call a changed failure set acceptable.

- [ ] **Step 4: Run vendor, attribution, privacy, and packaged-runtime verification**

Use the repository’s existing PlayLikeCurl snapshot verifier, attribution verifier, privacy-safe evidence tests, and packaged-reader asset verifier. Search changed production/test files and retained JSON/logs for forbidden content classes. The sweep must confirm that normalized visible text and any derived value are absent from Kotlin, JSON receipts, logs, exceptions, cache keys, evidence, and Git diffs.

- [ ] **Step 5: Review the final diff for authority regressions**

Confirm by code and tests:

- no exact-position fallback bypasses Foliate receipts;
- no frame callback, timeout, fade duration, or text match becomes exact commitment authority;
- PlayLikeCurl remains the raster source for native animation/shield;
- passive work cannot acquire during any live claim;
- every owner/callback drains on destroy;
- no current-source snapshot can replace the destination shield.

- [ ] **Step 6: Commit and push any gate-driven correction separately**

For each verified correction, rerun its failing gate first, commit only the correction and its test, and push the branch. Do not bundle unrelated cleanup.

---

### Task 10: Emulator acceptance, then explicit tablet validation and production shipment

**Files:**
- Keep local: `.codex-validation/reader-foreground-webview-handoff-<checkpoint>/`
- Modify for release identity only after acceptance: `androidApp/build.gradle.kts`
- Modify local audit evidence if required: `.codex-validation/paginator-receipts/spec-audit/requirement-matrix.md`

- [ ] **Step 1: Freeze the exact tested checkpoint and ReaderDev APK**

Record the commit SHA and APK SHA-256 in privacy-safe local evidence. Build only after Tasks 1-9 pass. Keep all raw screenshots, video, and frames local under a fresh `.codex-validation` root; never reuse a failed evidence root.

- [ ] **Step 2: Run bounded emulator acceptance on `emulator-5554`**

Use the existing ReaderDev launch/visual-QA scripts. Load several distinct private books one at a time, then execute a bounded forward-turn probe that includes at least one numeric chapter transition. Acceptance requires:

- no source/destination rollback between native settlement and WebView exposure;
- no unrelated passive page visible;
- all logical settlements accepted once;
- passive work resumes after handoff;
- ownership/callback counts return within bounds;
- no protected content in retained evidence.

Do not run a broad chapter-by-chapter barrage.

- [ ] **Step 3: Request explicit thread-scoped ownership of tablet `R52W60CFTRL`**

Do not install, interact with, record, or change tablet state until the user confirms this thread owns the tablet for the validation window. Never touch phone `RFCY80551LT`.

- [ ] **Step 4: Install the frozen ReaderDev candidate and validate the reported transition**

After authorization, preserve existing Logcat, install only the frozen candidate, and reproduce a bounded page 1-2 to 3-4 style forward turn using private content without recording text. Capture local frame evidence and privacy-safe numeric transition diagnostics. Acceptance requires continuous destination presentation after native settlement, with no source or third-page interval and no abrupt destination pop.

- [ ] **Step 5: Commit and push the accepted implementation to `fork/master`**

After all applicable gates pass, commit any final evidence-safe metadata, push the exact accepted checkpoint to the feature branch, then fast-forward `fork/master` using `HEAD:master`. Never reset, amend, force-push, move old tags, or delete existing release assets.

- [ ] **Step 6: Publish and independently verify the signed production release**

Advance version code/name, use the persistent GitHub-managed release signing identity, publish exactly one `Navic.apk`, and independently verify package, non-debuggable manifest, APK digest, persistent certificate digest, tag/commit/master identity, required ancestry, vendor assets, acknowledgements, and single immutable asset state. Do not publish raw visual evidence.

- [ ] **Step 7: Report exact outcomes**

Report the tested commit, focused/broad/emulator/tablet results, production tag/workflow/release URL, APK digest, certificate match, and any known baseline failures. If tablet validation fails, stop before release and preserve the failed evidence root unchanged.

---

## Plan self-review checklist

- Every dangerous interleaving has a deterministic behavioral test before implementation.
- The ownership start boundary is after relocation reservation and before renderer admission.
- Passive cancellation is not considered complete until typed visual restoration returns.
- Live ownership persists through committed WebView exposure, not merely logical queue completion.
- Destination text proof is receipt-bound, private, ephemeral, and never serialized.
- Empty text is handled without weakening receipt/raster/mutation proof.
- Mutation generation is strict, JavaScript-safe, and checked at every native/JS callback boundary.
- Shield failure retains a safe visual owner.
- Foliate and PlayLikeCurl authority boundaries remain unchanged.
- Validation is local/emulator first; tablet access is deferred until explicitly authorized.
- All changes are committed and pushed; `.codex-validation` and paused artifacts are preserved.
