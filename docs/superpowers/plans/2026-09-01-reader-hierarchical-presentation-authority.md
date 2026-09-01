# Reader Hierarchical Presentation Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every delegated agent must be told not to spawn subagents.

**Goal:** Make one common reducer select the reader's visual owner, compatible input policy, preparation presentation, diagnostics, and proof-bound transition so Stage 6 no longer permits locally correct components to publish contradictory UI.

**Architecture:** Live Foliate remains semantic destination authority, PlayLikeCurl remains deformation authority, and Android raster/WebView components remain proof producers. A pure common `ReaderPresentationState` reducer consumes their typed facts and emits one immutable `ReaderPresentationDecision`; the Android host applies that decision idempotently and returns token-bound commit/failure receipts. Shell-cover entry, cover dismissal, curl settlement, and live-engine exposure retain one proven predecessor until a matching successor proof arrives.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android View/WebView lifecycle APIs, PlayLikeCurl, `kotlin.test`, Android host tests, Gradle, JavaScript source-contract tests, GitHub Actions production signing.

---

## Governing Documents

- Corrective specification: `docs/superpowers/specs/2026-09-01-reader-hierarchical-presentation-authority-design.md`
- Parent Stage 6 plan: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md`
- Parent design: `docs/superpowers/specs/2026-08-23-reader-raster-isolation-and-whispersync-stabilization-design.md`

This is one tightly coupled Stage 6 correction rather than several independent projects: shell transactions, preparation visibility, input admission, curl settlement, and lifecycle liveness must use the same decision to remove the split authority. Stage 7 remains blocked throughout this plan.

## Worktree, Privacy, And TDD Rules

- Work only in `C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-playlist-pattern-fix` on `fix/foreground-webview-handoff-ownership`.
- Never run `git reset`, `git restore`, `git clean`, amend, force-push, or discard `.codex-validation`.
- Preserve the existing uncommitted `pageTurnPreparationPresentationVisible` prototype until Task 6 replaces it through reviewed edits. Do not stage its two files during Tasks 1-5.
- For each task, add one coherent RED group, run one focused Gradle RED boundary, implement the task, then run one focused GREEN boundary. Do not launch Gradle after each small edit.
- Stage files by exact path. Commit and push each verified checkpoint to `fork/fix/foreground-webview-handoff-ownership`.
- Do not log or retain publication text, raster payloads, URLs, hrefs, CFIs, book IDs, user IDs, credentials, annotations, selected text, or Whispersync transcript content. New diagnostics use enums, opaque counters, and Booleans only.
- Do not access a physical device without explicit thread-scoped ownership. Re-capture the current UI before every ADB gesture. Do not clear Logcat or app data.
- No debug or ReaderDev APK may be installed on tablet `R52W60CFTRL`. The final tablet gate uses a production-signed GitHub Actions artifact and remains limited to the configured pair's first two Chapter 1 pages in landscape.

## Final File Structure

### New common authority files

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt` — identities, authorities, projections, events, reducer, and stale-event handling.
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt` — authority matrix, receipt matching, and liveness table.
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthoritySequenceTest.kt` — common deterministic multi-transition sequence.

### New Android adapter files

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt` — idempotent application of `requiredTransition`, cover/native/WebView proof publication, and exact token cancellation.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt` — Android callback-to-common-lifecycle mapping.
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeout.android.kt` — one cancellable timeout per current transition token.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt` — proof-before-hide and stale callback disposal.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycleTest.kt` — typed trim/window mapping.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeoutTest.kt` — deterministic timeout replacement/cancellation.
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationIntegratedSequenceTest.kt` — bounded host sequence.

### Existing integration files

- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderControllerState.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressReducer.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncReducer.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageOperationPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoff.android.kt`
- `composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.ios.kt`

## Shared Type Contract

Task 1 must introduce these shapes before any host migration. Repository naming may be shortened only if every later task and the specification link are updated in the same checkpoint.

```kotlin
@JvmInline
value class ReaderPresentationToken(val value: Long) {
    init {
        require(value > 0L)
    }
}

enum class ReaderLiveEngineHandoffDirection {
    NativeToLiveEngine,
    LiveEngineToNative
}

data class ReaderPresentationBinding(
    val foliateSessionId: String,
    val publicationGeneration: Long,
    val viewportGeneration: Long,
    val profileGeneration: Long,
    val destinationCommitIdentity: ReaderDestinationCommitIdentity?,
    val rasterGeneration: Long?,
    val textureGeneration: Long?,
    val preparationGeneration: Long?
) {
    init {
        require(foliateSessionId.isNotBlank())
        require(publicationGeneration >= 0L)
        require(viewportGeneration >= 0L)
        require(profileGeneration >= 0L)
        require(rasterGeneration == null || rasterGeneration >= 0L)
        require(textureGeneration == null || textureGeneration >= 0L)
        require(preparationGeneration == null || preparationGeneration >= 0L)
    }
}

data class ReaderShellCoverCommitProof(
    val token: ReaderPresentationToken,
    val binding: ReaderPresentationBinding,
    val coverGeneration: Long,
    val widthPx: Int,
    val heightPx: Int,
    val presentedFrameSequence: Long
)

data class ReaderNativePagePresentationProof(
    val token: ReaderPresentationToken?,
    val binding: ReaderPresentationBinding,
    val presentedFrameSequence: Long
)

data class ReaderLiveEnginePresentationProof(
    val token: ReaderPresentationToken,
    val binding: ReaderPresentationBinding,
    val presentedFrameSequence: Long
)

sealed interface ReaderPresentationFrameOwner {
    data object Neutral : ReaderPresentationFrameOwner
    data class ShellCover(val proof: ReaderShellCoverCommitProof) : ReaderPresentationFrameOwner
    data class NativePage(val proof: ReaderNativePagePresentationProof) : ReaderPresentationFrameOwner
    data class Curl(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val presentedFrameSequence: Long
    ) : ReaderPresentationFrameOwner
    data class LiveEngine(val proof: ReaderLiveEnginePresentationProof) : ReaderPresentationFrameOwner
}

enum class ReaderCurlSettlementStage {
    AwaitingFoliate,
    AwaitingNativePresentation
}

sealed interface ReaderPresentationAuthority {
    data object Unavailable : ReaderPresentationAuthority
    data class ShellCover(val frame: ReaderPresentationFrameOwner.ShellCover) : ReaderPresentationAuthority
    data class ShellCoverCommitPending(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val coverGeneration: Long,
        val retainedFrame: ReaderPresentationFrameOwner
    ) : ReaderPresentationAuthority
    data class CurlGesture(val frame: ReaderPresentationFrameOwner.Curl) : ReaderPresentationAuthority
    data class CurlSettlementPending(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val retainedFrame: ReaderPresentationFrameOwner.Curl,
        val stage: ReaderCurlSettlementStage
    ) : ReaderPresentationAuthority
    data class SettledNativePage(val frame: ReaderPresentationFrameOwner.NativePage) : ReaderPresentationAuthority
    data class LiveEngineHandoffPending(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val direction: ReaderLiveEngineHandoffDirection,
        val retainedFrame: ReaderPresentationFrameOwner
    ) : ReaderPresentationAuthority
    data class LiveEngineExposed(val frame: ReaderPresentationFrameOwner.LiveEngine) : ReaderPresentationAuthority
    data class BlockingPreparation(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val retainedFrame: ReaderPresentationFrameOwner
    ) : ReaderPresentationAuthority
}

enum class ReaderPresentationLayer {
    Neutral,
    ShellCover,
    NativePage,
    Curl,
    LiveEngine
}

sealed interface ReaderPresentationInputPolicy {
    data object RecoveryOnly : ReaderPresentationInputPolicy
    data object ChromeOnly : ReaderPresentationInputPolicy
    data object ShellCover : ReaderPresentationInputPolicy
    data class ClaimedCurl(val token: ReaderPresentationToken) : ReaderPresentationInputPolicy
    data class NativePage(val policy: ReaderPageOperationPolicy) : ReaderPresentationInputPolicy
    data object LiveEngine : ReaderPresentationInputPolicy
}

sealed interface ReaderPreparationPresentation {
    data object Hidden : ReaderPreparationPresentation
    data class Blocking(
        val completedCount: Int,
        val requiredCount: Int,
        val determinate: Boolean
    ) : ReaderPreparationPresentation
}

enum class ReaderPresentationFailureReason {
    CoverCommitFailed,
    NativePagePresentationFailed,
    LiveEngineExposureFailed,
    RendererLost,
    PreparationFailed,
    TransitionTimedOut
}

enum class ReaderPresentationLifecycleState {
    Visible,
    Hidden,
    Closed
}

data class ReaderPagePreparationFacts(
    val phase: ReaderPagePreparationPhase = ReaderPagePreparationPhase.Idle,
    val generation: Long = 0L,
    val completedCount: Int = 0,
    val requiredCount: Int = 0,
    val readiness: ReaderPageReadinessState = ReaderPageReadinessState(
        interaction = ReaderPageInteractionState.Ready
    ),
    val failureReason: ReaderPresentationFailureReason? = null,
    val retryable: Boolean = false
)

sealed interface ReaderDiagnosticPresentation {
    data object Hidden : ReaderDiagnosticPresentation
    data class Failure(
        val reason: ReaderPresentationFailureReason,
        val retryable: Boolean,
        val cancellable: Boolean
    ) : ReaderDiagnosticPresentation
}

sealed interface ReaderRequiredTransition {
    data object None : ReaderRequiredTransition
    data class CommitShellCover(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val coverGeneration: Long
    ) : ReaderRequiredTransition
    data class PresentNativePage(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding
    ) : ReaderRequiredTransition
    data class ExposeLiveEngine(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding,
        val direction: ReaderLiveEngineHandoffDirection
    ) : ReaderRequiredTransition
}

data class ReaderPresentationDecision(
    val authority: ReaderPresentationAuthority,
    val frameOwner: ReaderPresentationFrameOwner,
    val layer: ReaderPresentationLayer,
    val inputPolicy: ReaderPresentationInputPolicy,
    val preparationPresentation: ReaderPreparationPresentation,
    val diagnosticPresentation: ReaderDiagnosticPresentation,
    val requiredTransition: ReaderRequiredTransition
)

data class ReaderPresentationState(
    val authority: ReaderPresentationAuthority = ReaderPresentationAuthority.Unavailable,
    val binding: ReaderPresentationBinding? = null,
    val lifecycle: ReaderPresentationLifecycleState = ReaderPresentationLifecycleState.Visible,
    val preparationFacts: ReaderPagePreparationFacts = ReaderPagePreparationFacts(),
    val failureReason: ReaderPresentationFailureReason? = null,
    val failureRetryable: Boolean = false,
    val failureCancellable: Boolean = false,
    val nextTokenValue: Long = 1L
)

sealed interface ReaderPresentationLifecycleEvent {
    data object VisibilityLost : ReaderPresentationLifecycleEvent
    data object VisibilityRestored : ReaderPresentationLifecycleEvent
    data class RunningMemoryPressure(val level: Int) : ReaderPresentationLifecycleEvent
    data class BackgroundMemoryPressure(val level: Int) : ReaderPresentationLifecycleEvent
    data object RendererLost : ReaderPresentationLifecycleEvent
    data object PublicationClosed : ReaderPresentationLifecycleEvent
}

sealed interface ReaderPresentationEvent {
    data class PublicationOpened(
        val binding: ReaderPresentationBinding,
        val initialFrame: ReaderPresentationFrameOwner
    ) : ReaderPresentationEvent
    data class FoliateRelocated(
        val binding: ReaderPresentationBinding,
        val settlementReceipt: ReaderPageTurnSettlementAck?
    ) : ReaderPresentationEvent
    data class ShellCoverRequested(
        val binding: ReaderPresentationBinding,
        val coverGeneration: Long
    ) : ReaderPresentationEvent
    data class ShellCoverCommitted(
        val proof: ReaderShellCoverCommitProof
    ) : ReaderPresentationEvent
    data class ShellCoverCommitFailed(
        val token: ReaderPresentationToken
    ) : ReaderPresentationEvent
    data class ShellCoverEntryRequested(
        val binding: ReaderPresentationBinding
    ) : ReaderPresentationEvent
    data class NativePageProved(
        val proof: ReaderNativePagePresentationProof
    ) : ReaderPresentationEvent
    data class CurlGestureClaimed(
        val frame: ReaderPresentationFrameOwner.Curl
    ) : ReaderPresentationEvent
    data class CurlGestureTerminal(
        val token: ReaderPresentationToken,
        val binding: ReaderPresentationBinding
    ) : ReaderPresentationEvent
    data class LiveEngineHandoffRequested(
        val binding: ReaderPresentationBinding,
        val direction: ReaderLiveEngineHandoffDirection
    ) : ReaderPresentationEvent
    data class LiveEngineExposureCommitted(
        val proof: ReaderLiveEnginePresentationProof
    ) : ReaderPresentationEvent
    data class LiveEngineExposureFailed(
        val token: ReaderPresentationToken
    ) : ReaderPresentationEvent
    data class PreparationReported(
        val binding: ReaderPresentationBinding,
        val facts: ReaderPagePreparationFacts
    ) : ReaderPresentationEvent
    data class PreparationFailed(
        val binding: ReaderPresentationBinding,
        val reason: ReaderPresentationFailureReason
    ) : ReaderPresentationEvent
    data class TransitionTimedOut(
        val token: ReaderPresentationToken
    ) : ReaderPresentationEvent
    data object RetryRequested : ReaderPresentationEvent
    data object CancelRequested : ReaderPresentationEvent
    data class Lifecycle(
        val event: ReaderPresentationLifecycleEvent
    ) : ReaderPresentationEvent
}

sealed interface ReaderPresentationEffect {
    data class ReleaseStalePresentation(
        val token: ReaderPresentationToken?,
        val binding: ReaderPresentationBinding
    ) : ReaderPresentationEffect
}
```

`ReaderPresentationState` owns the current authority, typed lifecycle state, raw preparation facts, current sanitized diagnostic, and `nextTokenValue`. It must not contain layer visibility Booleans or a separately mutable input policy. `readerPresentationDecision(state)` derives all six projections. `readerPresentationReduce(state, event)` returns the next state plus once-only release/engine effects; the host executes `requiredTransition` idempotently by token.

---

### Task 1: Define The Pure Authority And Projection Matrix

**Specification coverage:** Sections 5, 6, 7.1-7.2, 7.5-7.8, 8, and 9.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt`

- [ ] **Step 1: Add the grouped RED authority tests**

Create reusable opaque fixtures in the test file, then add these tests:

```kotlin
private val binding = ReaderPresentationBinding(
    foliateSessionId = "session-a",
    publicationGeneration = 1L,
    viewportGeneration = 2L,
    profileGeneration = 3L,
    destinationCommitIdentity = ReaderDestinationCommitIdentity("session-a", 4L),
    rasterGeneration = 5L,
    textureGeneration = 6L,
    preparationGeneration = 7L
)

private val pageProof = ReaderNativePagePresentationProof(
    token = null,
    binding = binding,
    presentedFrameSequence = 8L
)

@Test
fun shellCoverRequestRetainsNativePageAndRejectsNewPointersUntilCommit() {
    val initial = ReaderPresentationState(
        authority = ReaderPresentationAuthority.SettledNativePage(
            ReaderPresentationFrameOwner.NativePage(pageProof)
        ),
        binding = binding
    )
    val requested = readerPresentationReduce(
        initial,
        ReaderPresentationEvent.ShellCoverRequested(binding, coverGeneration = 9L)
    )

    assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(requested.state.authority)
    assertEquals(ReaderPresentationFrameOwner.NativePage(pageProof), requested.decision.frameOwner)
    assertEquals(ReaderPresentationLayer.NativePage, requested.decision.layer)
    assertEquals(ReaderPresentationInputPolicy.ChromeOnly, requested.decision.inputPolicy)
    assertIs<ReaderRequiredTransition.CommitShellCover>(requested.decision.requiredTransition)
}

@Test
fun preparationFailureOverStablePageKeepsPageAndShowsRetryableDiagnostic() {
    val failed = readerPresentationReduce(
        ReaderPresentationState(
            authority = ReaderPresentationAuthority.SettledNativePage(
                ReaderPresentationFrameOwner.NativePage(pageProof)
            ),
            binding = binding
        ),
        ReaderPresentationEvent.PreparationFailed(
            binding = binding,
            reason = ReaderPresentationFailureReason.PreparationFailed
        )
    )

    assertEquals(ReaderPresentationFrameOwner.NativePage(pageProof), failed.decision.frameOwner)
    assertEquals(ReaderPresentationLayer.NativePage, failed.decision.layer)
    assertEquals(
        ReaderDiagnosticPresentation.Failure(
            reason = ReaderPresentationFailureReason.PreparationFailed,
            retryable = true,
            cancellable = false
        ),
        failed.decision.diagnosticPresentation
    )
}

@Test
fun authorityAlwaysProjectsOneLayerAndOneCompatibleInputPolicy() {
    authorityMatrixFixtures().forEach { state ->
        val decision = readerPresentationDecision(state)
        assertEquals(decision.frameOwner.toLayer(), decision.layer)
        assertTrue(decision.inputPolicy.isCompatibleWith(decision.authority))
    }
}
```

- [ ] **Step 2: Run one focused RED boundary**

Run:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest"
```

Expected: compilation fails because the authority types and reducer do not exist. Record the exact missing-symbol boundary in the task notes.

- [ ] **Step 3: Implement the minimal pure model**

Add the shared types above, then implement a pure projection and reducer entry point:

```kotlin
data class ReaderPresentationReduction(
    val state: ReaderPresentationState,
    val decision: ReaderPresentationDecision,
    val effects: List<ReaderPresentationEffect> = emptyList()
)

fun readerPresentationReduce(
    state: ReaderPresentationState,
    event: ReaderPresentationEvent
): ReaderPresentationReduction {
    val nextState = state.reduce(event)
    return ReaderPresentationReduction(
        state = nextState,
        decision = readerPresentationDecision(nextState),
        effects = nextState.releaseEffectsFor(event)
    )
}
```

Implement explicit `when` branches for every sealed authority. Do not select winners with independent layer Booleans, ordinal priority arithmetic, or nullable flag combinations.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: `ReaderPresentationAuthorityReducerTest` passes with zero failures.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Verify that every authority fixture has exactly one frame owner, that diagnostics overlay rather than replace a stable owner, and that background preparation cannot claim a layer. Then run:

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt
git diff --cached --check
git commit -m "feat(reader): define hierarchical presentation authority" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 2: Make Foliate Settlement Receipts Event-Scoped

**Specification coverage:** Sections 5.2, 7.5, 10, 13.2, and 15.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt:ReaderEngineEvent.Relocated`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderControllerState.kt:ReaderPageTurnSettlementAck, ReaderControllerState`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressReducer.kt:ReaderProgressReducer.onRelocated`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncReducer.kt:reduceWhispersyncRelocated`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt:ReaderEngineEvent.Relocated handling`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncLifecycleReducerTest.kt`

- [ ] **Step 1: Add grouped RED receipt tests**

Add tests proving the receipt is parsed from the current relocation and consumed by the current curl transaction only:

```kotlin
@Test
fun matchingRelocationAdvancesOnlyTheCurrentCurlSettlement() {
    val pending = presentationStateAwaitingFoliate(binding, token = ReaderPresentationToken(11L))
    val event = relocatedEvent(
        binding = binding,
        settleToken = "turn-11",
        rasterGeneration = 5L,
        textureGeneration = 6L
    )

    val reduced = readerPresentationReduce(
        pending,
        ReaderPresentationEvent.FoliateRelocated(
            binding = binding,
            settlementReceipt = event.pageTurnSettlementReceiptOrNull()
        )
    )

    val authority = assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
        reduced.state.authority
    )
    assertEquals(ReaderCurlSettlementStage.AwaitingNativePresentation, authority.stage)
}

@Test
fun untaggedSameSessionTocRelocationCannotInheritCompletedTurnReceipt() {
    val controller = controllerAfterCompletedTurnReceipt()
    val relocated = controller.onEngineEvent(
        relocatedEvent(binding = nextBinding(), settleToken = null)
    )

    assertNull(relocated.controller.state.pageTurnSettlementAck)
    val authority = relocated.controller.state.presentation.authority
    assertFalse(
        authority is ReaderPresentationAuthority.CurlSettlementPending &&
            authority.stage == ReaderCurlSettlementStage.AwaitingNativePresentation
    )
}

@Test
fun whispersyncChecksTheCurrentRelocationReceiptRatherThanControllerHistory() {
    val step = pendingWhispersyncController().onEngineEvent(
        relocatedEvent(binding = binding, settleToken = null)
    )

    assertFalse(step.controller.state.whispersync.pendingCausalIntent?.destinationCommitted == true)
}
```

- [ ] **Step 2: Run one focused RED boundary**

Run:

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderWhispersyncLifecycleReducerTest"
```

Expected: the new reducer event/parser tests fail because settlement still lives on `ReaderControllerState` and Whispersync reads historical state.

- [ ] **Step 3: Parse and consume the current event once**

Expose a single strict parser and pass its result to both presentation and Whispersync reductions during the current `Relocated` call:

```kotlin
internal fun ReaderEngineEvent.Relocated.pageTurnSettlementReceiptOrNull(): ReaderPageTurnSettlementAck? {
    val token = pageTurnSettleToken?.takeIf(String::isNotBlank) ?: return null
    val pageIndex = locator.pageIndex?.takeIf { it >= 0 } ?: return null
    val sessionId = pageTurnSettleSessionId?.takeIf { it == foliateSessionId } ?: return null
    val raster = pageTurnSettleRasterGeneration?.takeIf { it >= 0L } ?: return null
    val texture = pageTurnSettleTextureGeneration?.takeIf { it >= 0L } ?: return null
    return ReaderPageTurnSettlementAck(token, pageIndex, sessionId, raster, texture)
}
```

Change `reduceWhispersyncRelocated` to accept `settlementReceipt: ReaderPageTurnSettlementAck?` and compare that argument. Feed the same receipt to `ReaderPresentationEvent.FoliateRelocated`. During this checkpoint, keep `ReaderControllerState.pageTurnSettlementAck` only as a legacy Android-host shadow that is replaced on every relocation, including replacement with `null`; no common reducer may read it. Task 4 removes the shadow and host parameter once Android consumes the settlement stage/binding from `ReaderPresentationDecision`.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: all selected tests pass and an untagged relocation leaves no reusable receipt.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Confirm that a valid receipt requires token, matching Foliate session, page index, raster generation, texture generation, and the current destination binding. Confirm duplicate/stale receipts leave the authority decision unchanged and that the legacy host shadow is always replaced by the current relocation rather than inherited. Then commit and push exact changed files:

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderControllerState.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressReducer.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncReducer.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncLifecycleReducerTest.kt
git diff --cached --check
git commit -m "fix(reader): scope settlement receipts to relocation events" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 3: Add Typed Lifecycle And Raw Preparation Facts

**Specification coverage:** Sections 5.1, 7.1, 8, 10, 13.4, and 13.5.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycleTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt`

- [ ] **Step 1: Add grouped RED lifecycle and preparation tests**

```kotlin
@Test
fun uiHiddenMapsOnlyToVisibilityLoss() {
    assertEquals(
        ReaderPresentationLifecycleEvent.VisibilityLost,
        readerPresentationLifecycleEventForTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
    )
}

@Test
fun actualPressureMapsToTypedPressureEvents() {
    assertIs<ReaderPresentationLifecycleEvent.RunningMemoryPressure>(
        readerPresentationLifecycleEventForTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
    )
    assertIs<ReaderPresentationLifecycleEvent.BackgroundMemoryPressure>(
        readerPresentationLifecycleEventForTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    )
}

@Test
fun returnedCoverPreparationFactsDoNotClaimForegroundPresentation() {
    val state = committedCoverState().copy(
        preparationFacts = preparingFacts(completed = 1, required = 4)
    )

    assertEquals(
        ReaderPreparationPresentation.Hidden,
        readerPresentationDecision(state).preparationPresentation
    )
}
```

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest" --tests "paige.navic.ui.screens.reader.ReaderPresentationLifecycleTest" --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest"
```

Expected: lifecycle mapper and reducer preparation facts are missing.

- [ ] **Step 3: Implement semantic lifecycle mapping and fact-only ingress**

Complete the reducer branches for the `ReaderPresentationLifecycleEvent` variants introduced in Task 1, then map Android constants by meaning rather than numeric ordering:

```kotlin
internal fun readerPresentationLifecycleEventForTrim(
    level: Int
): ReaderPresentationLifecycleEvent? = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
        ReaderPresentationLifecycleEvent.VisibilityLost
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
        ReaderPresentationLifecycleEvent.RunningMemoryPressure(level)
    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
    ComponentCallbacks2.TRIM_MEMORY_MODERATE,
    ComponentCallbacks2.TRIM_MEMORY_COMPLETE ->
        ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(level)
    else -> null
}
```

Populate the Task 1 `ReaderPagePreparationFacts` with only phase, generation, counts, readiness, and sanitized failure reason. During this task, adapt `ReaderPagePreparationState.toPresentationFacts()` without yet deleting legacy presentation/input fields; Task 6 removes them at the atomic cutover.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: all selected lifecycle and fact-projection tests pass.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Confirm `VisibilityLost` retains publication, authority, binding, and stable frame; it may reject input and pause work but may not invoke a memory-pressure failure. Commit and push:

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycleTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt
git diff --cached --check
git commit -m "refactor(reader): type presentation lifecycle and work facts" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 4: Wire The Common Decision Through Controller And Platform Hosts

**Specification coverage:** Sections 6, 11.1-11.3, 11.6, and Migration Rules 1-3.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderControllerState.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressReducer.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.ios.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthoritySequenceTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt`

- [ ] **Step 1: Add grouped RED wiring tests**

```kotlin
@Test
fun shellIntentChangesPresentationAuthorityWithoutDirectlyHidingThePage() {
    val controller = readerControllerWithSettledNativePage()
    val step = controller.onViewerAction(ReaderViewerAction.PreviousPage)

    assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
        step.controller.state.presentation.authority
    )
    assertEquals(ReaderPresentationLayer.NativePage, step.controller.state.presentationDecision.layer)
}

@Test
fun platformHostContractCarriesDecisionAndTypedEventsInShadowMode() {
    val source = platformHostSource()
    assertContains(source, "presentationDecision: ReaderPresentationDecision")
    assertContains(source, "onPresentationEvent: (ReaderPresentationEvent) -> Unit")
    assertContains(source, "pagePreparationCoverVisible: Boolean")
}
```

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthoritySequenceTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Expected: `ReaderControllerState.presentation` and platform decision/event parameters are absent.

- [ ] **Step 3: Add the authority data path in shadow mode**

Add `presentation: ReaderPresentationState` to `ReaderControllerState` and a derived decision:

```kotlin
val ReaderControllerState.presentationDecision: ReaderPresentationDecision
    get() = readerPresentationDecision(presentation)
```

Route `ReaderPresentationEvent` through `ReaderController`/`ReaderCoordinator`. Pass `presentationDecision` and `onPresentationEvent` through `ReaderScreen`, `KomikkuReaderRoot`, and the expect/actual host API. Android compares only privacy-safe enum outputs against legacy local decisions during this task; it must not change final layers or input yet. iOS accepts the decision/event API, retains its existing live-engine frame, and reports no Android-only commit proof.

Remove `pageTurnSettlementAck` from `ReaderControllerState`, `ReaderProgressReducer`, `ReaderRoot`, and the platform host signature: curl settlement now arrives as authority stage/binding data. Keep the existing shell/preparation parameters reachable until Tasks 5-6 perform proof-bound entry and atomic cutover.

- [ ] **Step 4: Run the focused GREEN and iOS compile boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthoritySequenceTest" --tests "paige.navic.reader.ReaderControllerTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" :composeApp:compileKotlinIosSimulatorArm64
```

Expected: selected tests pass and the iOS actual compiles with the common signature.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Verify that shadow reporting logs authority/layer/input enum names and mismatch Booleans only. Confirm legacy local behavior is still the active host path at this checkpoint. Commit and push the exact files listed above.

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderControllerState.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressReducer.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.ios.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthoritySequenceTest.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt
git diff --cached --check
git commit -m "refactor(reader): wire presentation authority in shadow mode" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 5: Make Shell-Cover Entry Proof-Before-Hide

**Specification coverage:** Sections 7.2, 7.3, 10, 11.2-11.4, and 13.2.

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlayReducer.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt`

- [ ] **Step 1: Add grouped RED shell-entry tests**

```kotlin
@Test
fun coverCommitRequiresMatchingTokenGeometryAndPresentedFrame() {
    val bridge = bridgeWithNativePageVisible()
    val transition = commitCoverTransition(token = 21L, coverGeneration = 22L)

    bridge.update(transition)
    bridge.onCoverDrawn(token = 20L, widthPx = 1920, heightPx = 1200)
    assertTrue(bridge.nativePageVisible)
    assertFalse(bridge.events.any { it is ReaderPresentationEvent.ShellCoverCommitted })

    bridge.onCoverDrawn(token = 21L, widthPx = 1920, heightPx = 1200)
    bridge.runNextAnimationFrame()
    assertTrue(bridge.nativePageVisible)
    assertIs<ReaderPresentationEvent.ShellCoverCommitted>(bridge.events.single())
}

@Test
fun showingCoverDoesNotInvalidateMatchingRasterOrPreparationProof() {
    val bridge = bridgeWithNativePageVisible()
    val before = bridge.currentNativeProof

    bridge.update(commitCoverTransition(token = 23L, coverGeneration = 24L))
    bridge.onCoverDrawn(token = 23L, widthPx = 1920, heightPx = 1200)
    bridge.runNextAnimationFrame()

    assertEquals(before, bridge.currentNativeProof)
    assertEquals(0, bridge.rasterInvalidationCount)
    assertEquals(0, bridge.preparationInvalidationCount)
}
```

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.ui.screens.reader.ReaderPresentationHostBridgeTest" --tests "paige.navic.ui.screens.reader.KomikkuReaderNativeFrameHostTest" --tests "paige.navic.reader.ReaderControllerTest"
```

Expected: the bridge/commit receipt is missing and the host still applies cover visibility immediately.

- [ ] **Step 3: Implement token-bound cover commit**

The bridge must be idempotent by `ReaderPresentationToken`:

```kotlin
internal fun update(decision: ReaderPresentationDecision) {
    val transition = decision.requiredTransition
    if (transition !is ReaderRequiredTransition.CommitShellCover) {
        cancelCoverCommitUnlessToken(null)
        return
    }
    if (activeCoverToken == transition.token) return
    cancelCoverCommitUnlessToken(transition.token)
    beginCoverCommit(transition)
}
```

`beginCoverCommit` prepares an opaque cover without hiding the predecessor, validates current cover generation and measured geometry, waits for one `ViewTreeObserver.OnDrawListener` callback and one `postOnAnimation` boundary, then emits `ShellCoverCommitted` with the exact token/binding. A stale listener unregisters once and emits no commit. `ReaderOverlayReducer` and `ReaderController` request the transaction; neither directly changes final layer visibility.

Do not invalidate current native raster/deck/preparation proof merely because the cover becomes the selected owner. Invalidation remains tied to publication, Foliate session, profile, viewport, destination, generation replacement, or explicit resource eviction.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: matching post-draw proof commits, stale/mismatched callbacks do not, and predecessor presentation remains selected until reducer acceptance.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Confirm there is no arbitrary delay, transparent WebView fallback, or publication-sensitive diagnostic. Commit and push:

```bash
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlayReducer.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt
git diff --cached --check
git commit -m "feat(reader): commit shell cover before hiding pages" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 6: Make Cover Dismissal, Preparation, Diagnostics, And Input Atomic

**Specification coverage:** Sections 7.4, 7.6, 7.8, 8, 9, 11.5-11.6, and Migration Rules 4-6.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageOperationPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostControllerTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`

- [ ] **Step 1: Migrate the existing prototype requirements into grouped RED authority tests**

Keep the intent of the two existing prototype tests while changing their subject from `readerMergedPagePreparationState` to the immutable decision:

```kotlin
@Test
fun returnedCoverHidesBackgroundPreparationWithoutChangingCoverInput() {
    val decision = readerPresentationDecision(
        committedCoverState(preparationFacts = preparingFacts(completed = 1, required = 4))
    )

    assertEquals(ReaderPreparationPresentation.Hidden, decision.preparationPresentation)
    assertEquals(ReaderPresentationInputPolicy.ShellCover, decision.inputPolicy)
}

@Test
fun returnedCoverFailureRemainsVisibleAndRetryable() {
    val decision = readerPresentationDecision(
        committedCoverState(preparationFacts = failedPreparationFacts())
    )

    assertEquals(ReaderPresentationLayer.ShellCover, decision.layer)
    assertEquals(
        ReaderDiagnosticPresentation.Failure(
            ReaderPresentationFailureReason.PreparationFailed,
            retryable = true,
            cancellable = false
        ),
        decision.diagnosticPresentation
    )
}

@Test
fun earlyCoverForwardCoalescesAndKeepsCoverUntilNativeProof() {
    val first = requestPageEntry(committedCoverState())
    val second = requestPageEntry(first.state)

    assertEquals(first.state.authority, second.state.authority)
    assertEquals(ReaderPresentationLayer.ShellCover, second.decision.layer)
    assertIs<ReaderPresentationAuthority.BlockingPreparation>(second.state.authority)
}
```

Add host input tests proving physical `DOWN` is admitted only from `ReaderPresentationDecision.inputPolicy`; local visibility or raw renderer readiness may reject for safety but may never grant input.

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest" --tests "paige.navic.ui.screens.reader.KomikkuReaderNativeFrameHostTest" --tests "paige.navic.ui.screens.reader.ReaderPageInputSettlementHostControllerTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
```

Expected: cover dismissal still depends on semantic relocation/raw readiness and presentation/input remain independently derived.

- [ ] **Step 3: Perform the atomic production cutover**

Apply all final layer visibility, overlay presentation, and pointer admission from the same decision in one checkpoint:

```kotlin
val decision = controllerState.presentationDecision
KomikkuReaderNativeFrameHost(
    presentationDecision = decision,
    onPresentationEvent = onPresentationEvent,
    // semantic and renderer fact inputs remain separate
)

ReaderPagePreparationOverlay(
    preparation = decision.preparationPresentation,
    diagnostic = decision.diagnosticPresentation,
    onRetry = { onPresentationEvent(ReaderPresentationEvent.RetryRequested) },
    onCancel = { onPresentationEvent(ReaderPresentationEvent.CancelRequested) }
)
```

A cover-forward action creates one page-entry token. If matching deck/native proof already exists, `requiredTransition` requests native presentation below the cover; otherwise `BlockingPreparation` retains the cover and shows blocking progress. Only a matching `NativePageProved` changes the owner and allows the host to hide the cover.

Delete the superseded Boolean prototype and its old helper tests through explicit edits. Remove `ReaderPagePreparationPresentation`, `ReaderPagePreparationGestureDisposition`, `presentation`, `gestureDisposition`, `operationPolicy`, `interactiveReady`, and `showsProgress` from raw `ReaderPagePreparationState` after every caller uses `ReaderPresentationDecision`. Remove Compose-local `pagePreparationState`/retry-key authority and the host's independent `pagePreparationCoverVisible`/`pageOperationPolicy` grant paths.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: returned-cover work is hidden, failure remains visible/retryable, duplicate entry coalesces, cover persists through native proof, and input matches the selected authority.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Search the touched reader path for the removed prototype symbol and independent presentation/input fields; all production call sites must be absent. Confirm the final diff preserves the prototype's two user-visible requirements while replacing its architecture. Commit and push the exact files listed for this task.

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageOperationPolicy.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHostTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostControllerTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt
git diff --cached --check
git commit -m "refactor(reader): centralize cover preparation and input" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 7: Make Live WebView Exposure And Handback Proof-Bound

**Specification coverage:** Sections 7.7, 10, 11.2, 13.1-13.2, and 15.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoff.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoffTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt`

- [ ] **Step 1: Add grouped RED WebView transaction tests**

```kotlin
@Test
fun liveEngineExposureKeepsNativeFrameUntilMatchingVisualStateAndFrameProof() {
    val pending = requestLiveEngineExposure(settledPageState())
    val wrong = liveEngineProof(token = ReaderPresentationToken(30L), binding = binding)

    val ignored = readerPresentationReduce(
        pending.state,
        ReaderPresentationEvent.LiveEngineExposureCommitted(wrong)
    )
    assertEquals(ReaderPresentationLayer.NativePage, ignored.decision.layer)

    val matching = liveEngineProof(
        token = pending.requiredToken(),
        binding = binding
    )
    val committed = readerPresentationReduce(
        pending.state,
        ReaderPresentationEvent.LiveEngineExposureCommitted(matching)
    )
    assertEquals(ReaderPresentationLayer.LiveEngine, committed.decision.layer)
}

@Test
fun nativeHandbackKeepsWebViewUntilMatchingNativeDrawProof() {
    val pending = requestNativeHandback(liveEngineState())
    assertEquals(ReaderPresentationLayer.LiveEngine, pending.decision.layer)
    assertIs<ReaderRequiredTransition.PresentNativePage>(pending.decision.requiredTransition)
}
```

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest" --tests "paige.navic.ui.screens.reader.ReaderWebViewVisualHandoffTest" --tests "paige.navic.ui.screens.reader.ReaderPresentationHostBridgeTest"
```

Expected: existing WebView proof is not bound to the common transition token/binding and handback is not represented by authority.

- [ ] **Step 3: Adapt the existing visual proof primitive**

Add `ReaderPresentationToken` and `ReaderPresentationBinding` to the existing handoff request/result. Preserve its visual-state callback plus subsequent animation-frame proof and callback ownership accounting. Map success/failure to:

```kotlin
onPresentationEvent(
    ReaderPresentationEvent.LiveEngineExposureCommitted(
        ReaderLiveEnginePresentationProof(
            token = result.token,
            binding = result.binding,
            presentedFrameSequence = result.presentedFrameSequence
        )
    )
)
```

For native handback, retain WebView as frame owner until `NativePageProved` matches the token and binding. Remove direct WebView visibility changes outside `ReaderPresentationHostBridge.update(decision)`. A stale callback unregisters/releases once and cannot hide the selected owner.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: exposure and handback both retain their predecessor until matching proof, while stale proof, timeout, and cancellation leave the predecessor visible.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Confirm no transparent WebView fallback remains reachable. Commit and push:

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoff.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderWebViewVisualHandoffTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridgeTest.kt
git diff --cached --check
git commit -m "feat(reader): bind webview exposure to presentation proof" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 8: Route Curl Gesture And Settlement Through Authority

**Specification coverage:** Sections 7.5, 9, 10, 11.4, 13.2-13.3, and 15.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageVisualLocationOrigin.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageVisualLocationOriginTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostControllerTest.kt`

- [ ] **Step 1: Add grouped RED curl ownership tests**

```kotlin
@Test
fun claimedCurlOwnsMovingAndTerminalPixelsUntilBothProofsArrive() {
    val gesture = claimCurlGesture(settledPageState())
    assertEquals(ReaderPresentationLayer.Curl, gesture.decision.layer)
    assertIs<ReaderPresentationInputPolicy.ClaimedCurl>(gesture.decision.inputPolicy)

    val terminal = finishCurlGesture(gesture.state)
    assertEquals(ReaderPresentationLayer.Curl, terminal.decision.layer)
    assertEquals(ReaderPresentationInputPolicy.ChromeOnly, terminal.decision.inputPolicy)

    val relocated = acceptMatchingFoliateReceipt(terminal.state)
    assertEquals(ReaderPresentationLayer.Curl, relocated.decision.layer)

    val presented = proveMatchingNativePage(relocated.state)
    assertEquals(ReaderPresentationLayer.NativePage, presented.decision.layer)
}

@Test
fun staleRendererDeckIsReleasedExactlyOnceWithoutChangingAuthority() {
    val harness = settlementHarness()
    harness.deliverStaleAcceptedDeck()
    harness.deliverStaleAcceptedDeck()

    assertEquals(1, harness.releaseCountForStaleDeck)
    assertEquals(ReaderPresentationLayer.Curl, harness.currentDecision.layer)
}
```

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest" --tests "paige.navic.ui.screens.reader.ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest" --tests "paige.navic.ui.screens.reader.ReaderPageVisualLocationOriginTest" --tests "paige.navic.ui.screens.reader.ReaderPageInputSettlementHostControllerTest"
```

Expected: curl controller still owns local hide/admission decisions and settlement is not a two-proof authority transaction.

- [ ] **Step 3: Emit curl facts and obey reducer transitions**

Emit `CurlGestureClaimed` only after physical pointer claim. During the accepted gesture, continue to route only that token's pointer stream. On terminal animation, emit `CurlGestureTerminal`; retain the complete curl terminal frame while the reducer waits for the current exact Foliate receipt and matching native page proof.

Replace direct `hideSurface()`/visibility calls with reducer-authorized bridge transitions. Fence every renderer callback by token and current preparation/raster/texture generations. Existing ownership-safe deck release remains the only disposal path, and each stale/pending accepted renderer deck is released exactly once.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: curl owns all gesture/terminal material, unrelated TOC relocation cannot complete an old curl, and stale deck release is exactly once.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Confirm PlayLikeCurl remains deformation authority and the common reducer never manipulates bitmaps or infers EPUB positions. Commit and push:

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostController.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageVisualLocationOrigin.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageVisualLocationOriginTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageInputSettlementHostControllerTest.kt
git diff --cached --check
git commit -m "feat(reader): retain curl authority through settlement" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 9: Guarantee Timeout, Retry, Cancel, And Restore Liveness

**Specification coverage:** Sections 8, 9, 10, 13.4-13.5, and 15.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeout.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeoutTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt`

- [ ] **Step 1: Add the complete deferred-state RED table**

```kotlin
@Test
fun everyPendingAuthorityHasSuccessFailureTimeoutRetryAndCancelOutcomes() {
    pendingAuthorityFixtures().forEach { fixture ->
        assertTrue(fixture.reduceSuccess().isTerminalOrProgressed())
        assertTrue(fixture.reduceFailure().showsActionableFailure())
        assertTrue(fixture.reduceTimeout().showsActionableFailure())
        assertTrue(fixture.reduceRetry().usesTokenAfter(fixture.originalToken))
        assertEquals(fixture.retainedFrame, fixture.reduceCancel().decision.frameOwner)
    }
}

@Test
fun visibilityRestoreRetainsPublicationAndCurrentProof() {
    val hidden = readerPresentationReduce(
        settledPageState(),
        ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost)
    )
    val restored = readerPresentationReduce(
        hidden.state,
        ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored)
    )

    assertEquals(hidden.state.binding, restored.state.binding)
    assertEquals(hidden.decision.frameOwner, restored.decision.frameOwner)
    assertFalse(restored.decision.diagnosticPresentation is ReaderDiagnosticPresentation.Failure)
}
```

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthorityReducerTest" --tests "paige.navic.ui.screens.reader.ReaderPresentationTransitionTimeoutTest" --tests "paige.navic.ui.screens.reader.ReaderPageAdjacentChapterPrefetchIntegrationTest"
```

Expected: one or more pending authorities lack explicit timeout/retry/cancel behavior and no generic token scheduler exists.

- [ ] **Step 3: Implement one timeout per current token**

Model the scheduler after `ReaderPageRelocationDispatchTimeout`, with injected scheduling for deterministic tests:

```kotlin
internal interface ReaderPresentationTimeoutScheduler {
    fun postDelayed(action: Runnable, delayMillis: Long): Boolean
    fun remove(action: Runnable)
}

internal class ReaderPresentationTransitionTimeout(
    private val scheduler: ReaderPresentationTimeoutScheduler,
    private val timeoutMillis: Long,
    private val onTimeout: (ReaderPresentationToken) -> Unit
) {
    private var pending: Pair<ReaderPresentationToken, Runnable>? = null

    fun replace(token: ReaderPresentationToken?) {
        pending?.second?.let(scheduler::remove)
        pending = token?.let { current ->
            current to Runnable { onTimeout(current) }
        }
        pending?.second?.let { scheduler.postDelayed(it, timeoutMillis) }
    }
}
```

Bind scheduling to the exact current `requiredTransition` token. A changed/completed token cancels the old callback. Timeout emits `TransitionTimedOut(token)` and never swaps owners silently. Retry allocates a fresh token and, for preparation, a fresh generation. Duplicate Retry for the same active fresh generation coalesces. Cancel restores the retained frame when its binding is still current; otherwise it enters `Unavailable` with recovery controls. `VisibilityLost` pauses unsafe work/input but retains identities and proof; `VisibilityRestored` resumes the same pending generation or stable owner.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: the complete deferred-state table and lifecycle integration pass with zero pending timeout callbacks after terminal events.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Verify every pending state in specification Section 10 has a tested success, failure, timeout, Retry, and cancel/terminal recovery. Commit and push:

```bash
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPresentationAuthority.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeout.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationController.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationTransitionTimeoutTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageAdjacentChapterPrefetchIntegrationTest.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthorityReducerTest.kt
git diff --cached --check
git commit -m "fix(reader): make presentation transitions terminal and retryable" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 10: Add The Deterministic Integrated Stage 6 Sequence

**Specification coverage:** Sections 13.6, 14, 15, and 16.

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationIntegratedSequenceTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthoritySequenceTest.kt`
- Modify only when the RED sequence exposes a missing production route: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- Modify only when the RED sequence exposes a missing host route: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt`
- Modify only when the RED sequence exposes a missing lifecycle route: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt`

- [ ] **Step 1: Add the RED sequence fixture**

Build one deterministic fake Foliate/native host without raster bytes or publication payloads:

```kotlin
@Test
fun boundedPostWhispersyncSequenceHasOneOwnerAndNoDeadPendingState() {
    val fixture = ReaderPresentationSequenceFixture.openAtNativePage()

    fixture.turnPage()
    fixture.completeExactAcknowledgement()
    fixture.relocateFromTocWithoutTurnReceipt(destination = fixture.chapterOneDestination)
    fixture.turnPage()
    fixture.requestShellCover()
    fixture.commitShellCoverDraw()
    fixture.requestPageEntryBeforePreparationReady()
    fixture.reportPreparationReady()
    fixture.commitNativePageDraw()
    fixture.sendTrimMemoryUiHidden()
    fixture.restoreWindowVisibility()

    assertEquals(fixture.chapterOneSecondPageDestination, fixture.semanticDestination)
    assertEquals(fixture.semanticDestination, fixture.displayedBinding.destinationCommitIdentity)
    assertEquals(1, fixture.nextTurnCountAfterTocRelocation)
    assertTrue(fixture.decisions.all { it.hasExactlyOneFrameOwner() })
    assertFalse(fixture.returnedCoverShowedBackgroundProgress)
    assertEquals(1, fixture.coalescedPageEntryCount)
    assertTrue(fixture.pendingTokens.isEmpty())
    assertTrue(fixture.pendingHostCallbacks.isEmpty())
    assertTrue(fixture.publicationStillOpen)
}
```

After every fixture event, assert layer/input compatibility, binding agreement, and no hidden failure.

- [ ] **Step 2: Run one focused RED boundary**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPresentationAuthoritySequenceTest" --tests "paige.navic.ui.screens.reader.ReaderPresentationIntegratedSequenceTest"
```

Expected: the integrated fixture or at least one required transition assertion fails before the full sequence contract is wired.

- [ ] **Step 3: Wire the fixture through production adapters**

Drive the deterministic fixture through the real reducer, `ReaderController` event route, host bridge, lifecycle mapper, and receipt parser. Do not add test-only methods to production classes. If RED identifies a missing semantic route, add it only in `ReaderController.kt`; if it identifies a missing host route, add it only in `ReaderPresentationHostBridge.android.kt`; if it identifies a missing lifecycle route, add it only in `ReaderPresentationLifecycle.android.kt`. Add a direct assertion for the corrected route and do not introduce delays or bypass proof.

- [ ] **Step 4: Run the focused GREEN boundary**

Run the same focused command. Expected: both sequence classes pass and end with no pending token, callback, host transition, or stale receipt.

- [ ] **Step 5: Cross-check and publish the checkpoint**

Map each specification acceptance-summary checkbox to a named test. Record any gap as a Stage 6 blocker rather than a deferral to Stage 7. Commit and push:

```bash
git add composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPresentationIntegratedSequenceTest.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPresentationAuthoritySequenceTest.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPresentationLifecycle.android.kt
git diff --cached --check
git commit -m "test(reader): cover hierarchical presentation sequence" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

Before committing, inspect `git diff --cached --name-only`; Git ignores unchanged named files, and no directory-wide staging is permitted.

---

### Task 11: Run Consolidated Automated Gates And Specification Audit

**Specification coverage:** Sections 12, 13, 15, and 16.

**Files:**
- Modify: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md` only after every gate passes, recording the authority checkpoint and remaining signed acceptance blocker.

- [ ] **Step 1: Run static/privacy checks before Gradle**

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-location.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
node scripts/test-reader-relocation-bridge.mjs
pwsh -NoProfile -File scripts/test-reader-vendor-assets-verifier.ps1
pwsh -NoProfile -File scripts/test-playlikecurl-snapshot-verifier.ps1
pwsh -NoProfile -File scripts/test-reader-privacy-safe-evidence.ps1
```

Expected: every command exits zero. Any failure blocks the consolidated Gradle gate.

- [ ] **Step 2: Run one consolidated Gradle gate**

```powershell
.\gradlew.bat --no-daemon --console=plain :composeApp:testAndroidHostTest :composeApp:compileKotlinIosSimulatorArm64 :androidApp:assembleReaderDev :androidApp:lintReaderDev
```

Expected: full Android host tests, iOS compilation, ReaderDev assembly, and ReaderDev lint all pass with zero failures.

- [ ] **Step 3: Audit the final production path**

Verify all of the following with source searches and the final diff:

- exactly one `ReaderPresentationDecision` drives layer, input, preparation, diagnostic, and transition;
- no reachable `pageTurnPreparationPresentationVisible`, `pagePreparationCoverVisible`, raw-work input grant, direct cover hide, or direct WebView fallback remains;
- one-shot settlement data is read from the current relocation event and cannot survive an unrelated relocation;
- `TRIM_MEMORY_UI_HIDDEN` maps only to visibility loss;
- every pending authority has tested success, failure, timeout, Retry, and cancel/recovery;
- no source logging exposes prohibited reader data;
- no Stage 7 file or task was started.

- [ ] **Step 4: Update the Stage 6 blocker ledger**

Record the exact passing commands and counts in the active Stage 6 plan. Mark hierarchical authority automated implementation complete while keeping signed bounded tablet acceptance as the only Stage 6 exit blocker.

- [ ] **Step 5: Commit and push the audited checkpoint**

```bash
git add docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md
git diff --cached --check
git commit -m "docs(reader): record presentation authority gates" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
```

---

### Task 12: Run Production-Signed Bounded Tablet Acceptance And Publish Stage 6

**Specification coverage:** Sections 14, 15, and 16.

**Files:**
- Modify after acceptance passes: `androidApp/build.gradle.kts` — planned next candidate `versionCode = 594`, `versionName = "v1.0.11-iota67"`.
- Modify after acceptance passes: `docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md` — record signed acceptance evidence and Stage 6 exit.
- Keep all screenshots, recordings, frames, and visual derivatives local under `.codex-validation`.

- [ ] **Step 1: Obtain and verify the signed branch artifact**

Push the exact automated-gate commit and wait for its `Build Navic` workflow. Download the `navic-release-android` artifact. Verify:

- package: `darkaxt.navic`;
- GitHub run commit equals local `git rev-parse HEAD`;
- certificate SHA-256 equals `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`;
- certificate is not Android Debug;
- APK SHA-256 is recorded in the local acceptance manifest.

A failed or mismatched workflow/artifact is a blocker; do not substitute a local release or ReaderDev APK.

- [ ] **Step 2: Claim the tablet session and preserve app state**

Obtain explicit confirmation that this thread owns `R52W60CFTRL`. Confirm the current UI before every gesture, keep landscape orientation, install with replacement semantics that preserve app data, and do not touch phone `RFCY80551LT` or clear Logcat.

- [ ] **Step 3: Run only the bounded acceptance sequence**

Using the configured pair and only the first two Chapter 1 pages in landscape, verify:

1. complete one page turn and wait for settlement;
2. relocate through TOC to Chapter 1;
3. confirm semantic chrome and native raster agree;
4. perform the next page action and confirm it advances exactly once;
5. return to the native shell cover and confirm ordinary off-screen prewarm shows no foreground progress;
6. dismiss the cover before readiness once and confirm the request coalesces, completes automatically, or shows truthful blocking progress;
7. return Home, restore Navic, and confirm the publication and latest valid spread remain open without memory-pressure failure;
8. confirm any actual preparation/renderer failure is visible and retryable over the stable frame.

Stop after this sequence. Do not expand acceptance chapter-by-chapter.

- [ ] **Step 4: Record privacy-safe evidence and close blockers**

Store visual artifacts only under `.codex-validation`. Retain only binary outcomes, counts, orientation, package/version, commit, artifact hash, certificate digest, timestamps, and device serial in structured evidence. Do not OCR or commit visual material. If any assertion fails, keep Stage 6 open and add a named blocker with authority, failure event, and reachable retry event.

- [ ] **Step 5: Prepare and publish iota67 only after acceptance passes**

Set `versionCode = 594` and `versionName = "v1.0.11-iota67"`, run:

```powershell
.\gradlew.bat --no-daemon --console=plain :androidApp:assembleReaderDev
```

Commit and push the release metadata, tag the exact commit, and publish through the existing signed workflow/release script:

```bash
git add androidApp/build.gradle.kts docs/superpowers/plans/2026-08-23-reader-raster-isolation-and-whispersync-stabilization.md
git diff --cached --check
git commit -m "chore(release): prepare v1.0.11-iota67" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork HEAD:fix/foreground-webview-handoff-ownership
git tag v1.0.11-iota67
git push fork v1.0.11-iota67
```

After the tag workflow succeeds, independently verify the published APK hash, version, and certificate digest, then publish the immutable release with:

```powershell
pwsh -NoProfile -File scripts/publish-github-release.ps1 `
  -Tag v1.0.11-iota67 `
  -Repo Darkaxt/Navic `
  -Remote fork `
  -SkipPush `
  -AllowPublicRelease `
  -ReleaseReadinessNote "Stage 6 hierarchical presentation authority passed consolidated automation and bounded production-signed tablet acceptance"
```

Existing release assets are immutable. Mark Stage 6 complete only after the published artifact matches the accepted commit and signing identity. Do not begin Stage 7 in this plan.

## Final Acceptance Checklist

- [ ] Exactly one underlying visual owner is selected at every reducer state and integrated-sequence step.
- [ ] Layer, input, preparation, diagnostic, and required transition derive from one immutable decision.
- [ ] Foliate remains the exclusive semantic destination authority.
- [ ] Settlement receipts are event-scoped, strict, and consumed once.
- [ ] Shell-cover entry retains page/curl presentation until matching post-draw cover proof.
- [ ] Cover dismissal retains cover until matching destination native or explicit live-engine proof.
- [ ] Active curl owns all moving/terminal page material until both settlement proofs arrive.
- [ ] Stable cover/page/curl/WebView frames suppress ordinary background progress.
- [ ] Failures overlay the stable frame and remain visible and retryable.
- [ ] Every deferred state has success, failure, timeout, Retry, and cancel/terminal recovery.
- [ ] `TRIM_MEMORY_UI_HIDDEN` is visibility loss only.
- [ ] Home/restore retains publication, binding, and latest valid frame.
- [ ] Android and iOS actuals compile against the common host contract.
- [ ] The full host, iOS compile, build, lint, JavaScript, governance, and privacy gates pass.
- [ ] Production-signed bounded tablet acceptance passes on the configured pair's first two Chapter 1 pages in landscape.
- [ ] Stage 6 evidence is recorded without protected reader payloads.
- [ ] Stage 7 remains unstarted.
