# Reader Overlay Sync Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve B12 by replacing the duplicate Whispersync and EPUB readaloud overlay transition logic with one reducer and two typed timeline adapters, while preserving each format's distinct lookup, status, progress, and playback behavior.

**Architecture:** `ReaderOverlaySync.kt` owns the only synchronization state and command reducer. `WhispersyncOverlaySyncAdapter` and `MediaOverlaySyncAdapter` translate their real timelines into opaque common cues and typed reader seek targets; the existing format entry points keep logging, status, page-boundary, and Media3 responsibilities outside the reducer.

**Tech Stack:** Kotlin Multiplatform common code, immutable reducer functions, Kotlin test, Android host source-contract tests, Compose Android runtime host, Gradle Android test and assembly tasks.

---

## Contract And File Map

- Create `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlaySync.kt`: the single state, cue, adapter interface, playback reducer, reader reducer, and command-key publication site.
- Create `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderMediaOverlaySyncAdapter.kt`: SMIL/media-overlay adapter, readaloud playback and bridge inputs, Media3 seek-target construction, and the existing readaloud entry-point names.
- Create `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncOverlaySyncAdapter.kt`: Whispersync playback/visible-range/text-point inputs, segment lookup, stable key, progressive fragment construction, and typed seek targets.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt`: retain Whispersync status/logging policy while delegating state transitions and timeline resolution.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`: use common active-cue/progress names; preserve page-boundary and status behavior.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderReadaloudRuntimeHost.android.kt`: own `ReaderReadaloudSyncState` directly without a nested duplicate overlay state.
- Delete `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderMediaOverlaySync.kt` after its active call sites and tests migrate.
- Delete `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderReadaloudSyncCoordinator.kt` after its active call sites and tests migrate.
- Create `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderOverlaySyncContractTest.kt`: run the same seven scenarios against both real adapters.
- Create `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderOverlaySyncReducerTest.kt`: isolate command publication, progressive updates, and repeat-seek policy.
- Rename `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderMediaOverlaySyncTest.kt` to `ReaderMediaOverlaySyncAdapterTest.kt` and preserve bridge/track edge cases.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderReadaloudSyncCoordinatorTest.kt`: assert the source-compatible readaloud entry points are backed by the common state.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinatorTest.kt`: preserve every existing progressive/status behavior through the adapter-backed path.
- Create `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderOverlaySyncSourceTest.kt`: reject duplicate state/command reducers and prove both production owners use adapters.

## Task 1: Lock The Shared Reducer Contract In RED

**Files:**
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderOverlaySyncReducerTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderOverlaySyncSourceTest.kt`

- [x] **Step 1: Add reducer tests against the wished-for API**

Use one static cue and one progressive cue. The complete reducer contract is:

```kotlin
class ReaderOverlaySyncReducerTest {
    private val first = ReaderOverlayCue(
        key = "cue-1",
        fragment = ReaderOverlayFragment(resourceHref = "audio.mp3", fragmentId = "one")
    )

    @Test
    fun staticPlaybackAppliesOnceThenClearsOnce() {
        val applied = ReaderOverlaySyncState().followPlaybackCue(first)
        assertIs<ReaderEngineCommand.ApplyMediaOverlay>(applied.engineCommand)
        assertEquals(1L, applied.engineCommandKey)

        val repeated = applied.followPlaybackCue(first)
        assertEquals(1L, repeated.engineCommandKey)

        val cleared = repeated.followPlaybackCue(null)
        assertEquals(ReaderEngineCommand.ClearMediaOverlay, cleared.engineCommand)
        assertEquals(2L, cleared.engineCommandKey)
        assertEquals(2L, cleared.followPlaybackCue(null).engineCommandKey)
    }

    @Test
    fun progressiveCueUpdatesOnlyWhenProgressMarkerChanges() {
        val start = ReaderOverlaySyncState().followPlaybackCue(
            first.copy(progressTextEnd = 10)
        )
        val progress = start.followPlaybackCue(first.copy(progressTextEnd = 15))
        assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(progress.engineCommand)
        assertEquals(2L, progress.engineCommandKey)
        assertEquals(2L, progress.followPlaybackCue(first.copy(progressTextEnd = 15)).engineCommandKey)
    }

    @Test
    fun readerTargetSuppressesRepeatUnlessAdapterAllowsRepeatSeek() {
        val target = ReaderOverlayReaderTarget(first, seekTarget = "seek-1")
        val firstStep = ReaderOverlaySyncState().followReaderTarget(target)
        assertEquals("seek-1", firstStep.seekTarget)
        assertEquals(1L, firstStep.state.engineCommandKey)

        val repeated = firstStep.state.followReaderTarget(target)
        assertNull(repeated.seekTarget)
        assertEquals(1L, repeated.state.engineCommandKey)

        val repeatable = firstStep.state.followReaderTarget(target.copy(repeatSeek = true))
        assertEquals("seek-1", repeatable.seekTarget)
        assertEquals(1L, repeatable.state.engineCommandKey)
    }

    @Test
    fun disablingActiveSyncClearsExactlyOnceAndSuppressesPlayback() {
        val active = ReaderOverlaySyncState().followPlaybackCue(first)
        val disabled = active.setSyncEnabled(false)
        assertEquals(ReaderEngineCommand.ClearMediaOverlay, disabled.engineCommand)
        assertEquals(2L, disabled.engineCommandKey)
        assertNull(disabled.activeCueKey)
        assertEquals(2L, disabled.setSyncEnabled(false).engineCommandKey)
        assertEquals(2L, disabled.followPlaybackCue(first).engineCommandKey)
    }
}
```

- [x] **Step 2: Add a source contract that rejects the current duplicate design**

Resolve the repository root using the existing upward `androidApp/build.gradle.kts` search. Read all production reader Kotlin files and assert:

```kotlin
assertEquals(1, Regex("data class ReaderOverlaySyncState\\(").findAll(allReaderSources).count())
assertFalse(allReaderSources.contains("data class ReaderWhispersyncSyncState("))
assertFalse(allReaderSources.contains("data class ReaderMediaOverlaySyncState("))
assertFalse(allReaderSources.contains("data class ReaderReadaloudSyncState("))
assertEquals(1, Regex("engineCommandKey = engineCommandKey \\+ 1L").findAll(allReaderSources).count())
assertTrue(allReaderSources.contains("WhispersyncOverlaySyncAdapter("))
assertTrue(allReaderSources.contains("MediaOverlaySyncAdapter("))
assertFalse(root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderMediaOverlaySync.kt").exists())
assertFalse(root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderReadaloudSyncCoordinator.kt").exists())
```

- [x] **Step 3: Run RED and record the exact failure reason**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderOverlaySyncReducerTest" --tests "paige.navic.reader.ReaderOverlaySyncSourceTest" --console=plain
```

Expected: compilation fails because `ReaderOverlaySyncState`, `ReaderOverlayCue`, and `ReaderOverlayReaderTarget` do not exist; the source contract also describes the duplicate declarations still present. Do not modify production code before capturing this RED result.

RED evidence: `:composeApp:compileAndroidHostTest` failed on 2026-07-13 with unresolved references to `ReaderOverlayCue`, `ReaderOverlaySyncState`, and `ReaderOverlayReaderTarget` in `ReaderOverlaySyncReducerTest`. No production B12 type existed during that run.

## Task 2: Implement The Single Overlay Reducer

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlaySync.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderOverlaySyncReducerTest.kt`

- [x] **Step 1: Add the domain-neutral types**

```kotlin
data class ReaderOverlaySyncState(
    val syncEnabled: Boolean = true,
    val activeCueKey: String? = null,
    val activeProgressTextEnd: Int? = null,
    val engineCommand: ReaderEngineCommand? = null,
    val engineCommandKey: Long = 0L
)

data class ReaderOverlayCue(
    val key: String,
    val fragment: ReaderOverlayFragment,
    val progressTextEnd: Int? = null
)

data class ReaderOverlayReaderTarget<T>(
    val cue: ReaderOverlayCue,
    val seekTarget: T,
    val repeatSeek: Boolean = false
)

data class ReaderOverlayReaderStep<T>(
    val state: ReaderOverlaySyncState,
    val seekTarget: T? = null
)

interface ReaderOverlayTimelineAdapter<PlaybackInput, ReaderInput, SeekTarget> {
    fun playbackCue(input: PlaybackInput): ReaderOverlayCue?
    fun readerTarget(input: ReaderInput): ReaderOverlayReaderTarget<SeekTarget>?
}
```

- [x] **Step 2: Implement reducer transitions**

`setSyncEnabled(false)` clears an active cue once. `followPlaybackCue(null)` clears only when enabled and active. A new cue publishes `ApplyMediaOverlay`; the same cue with a changed non-null progress marker publishes `UpdateMediaOverlayProgress`; all other repeats preserve the state and key. `followReaderTarget` uses the same activation transition and emits its seek target only for a changed cue or `repeatSeek=true`.

Keep command publication in exactly one private function:

```kotlin
private fun ReaderOverlaySyncState.withEngineCommand(
    command: ReaderEngineCommand?
): ReaderOverlaySyncState = if (command == null) this else copy(
    engineCommand = command,
    engineCommandKey = engineCommandKey + 1L
)
```

- [x] **Step 3: Run GREEN for the reducer only**

Run the reducer test class without the source test. Expected: all reducer tests pass with zero failures.

GREEN evidence: `ReaderOverlaySyncReducerTest` initially passed 4/4, then passed 5/5 after the Whispersync migration added the repeated-cue progress-preservation policy. The suite has zero failures, errors, or skips and covers static apply/clear deduplication, progressive update deduplication, repeated reader-target seek/update policies, and disabled-sync suppression.

- [x] **Step 4: Commit the reducer**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderOverlaySync.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderOverlaySyncReducerTest.kt
git commit -m "refactor(reader): add shared overlay sync reducer"
```

## Task 3: Add And Migrate The EPUB Media-Overlay Adapter

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderMediaOverlaySyncAdapter.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderReadaloudRuntimeHost.android.kt`
- Rename: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderMediaOverlaySyncTest.kt` to `ReaderMediaOverlaySyncAdapterTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderReadaloudSyncCoordinatorTest.kt`
- Delete after migration: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderMediaOverlaySync.kt`
- Delete after migration: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderReadaloudSyncCoordinator.kt`

- [x] **Step 1: Change the existing tests to require the adapter-backed state**

Use `ReaderReadaloudSyncState()` as the only state constructor. Preserve assertions for first apply, duplicate suppression, out-of-clip clear, bridge-event seek target, repeated bridge-event suppression, track-index lookup, disabled sync, and stable command keys. Add:

```kotlin
assertIs<ReaderOverlaySyncState>(ReaderReadaloudSyncState())
assertNotNull(seek.state.activeCueKey)
assertEquals("frag-2", assertIs<ReaderEngineCommand.ApplyMediaOverlay>(seek.state.engineCommand).fragment.fragmentId)
```

Prefer direct command/target assertions over depending on a key's internal delimiter.

- [x] **Step 2: Run the changed media/readaloud tests and record RED**

Run both adapter/readaloud test classes. Expected: failure because the adapter and `ReaderReadaloudSyncState` alias are absent.

RED evidence: host-test compilation failed with unresolved `MediaOverlaySyncAdapter` and `MediaOverlayPlaybackInput`, while the migrated coordinator assertions could not resolve `syncEnabled` or `activeCueKey` on the old nested readaloud state.

- [x] **Step 3: Implement `MediaOverlaySyncAdapter`**

```kotlin
typealias ReaderReadaloudSyncState = ReaderOverlaySyncState

data class MediaOverlayPlaybackInput(val position: ReadaloudPlaybackPosition)

class MediaOverlaySyncAdapter(
    private val plan: ReadaloudPlaybackPlan,
    private val timeline: MediaOverlayTimeline
) : ReaderOverlayTimelineAdapter<MediaOverlayPlaybackInput, ReaderBridgeEvent, ReadaloudAudioSeekTarget> {
    override fun playbackCue(input: MediaOverlayPlaybackInput): ReaderOverlayCue? {
        val audio = plan.audioResourceForOverlay(input.position) ?: return null
        return timeline.activeClip(audio, input.position.positionMs)?.toOverlayCue()
    }

    override fun readerTarget(input: ReaderBridgeEvent): ReaderOverlayReaderTarget<ReadaloudAudioSeekTarget>? {
        val href = input.syncedOverlayHref() ?: return null
        val target = timeline.seekTargetForText(href) ?: return null
        val track = plan.trackIndexForOverlayAudio(target.audioResource) ?: return null
        return ReaderOverlayReaderTarget(
            cue = target.clip.toOverlayCue(),
            seekTarget = ReadaloudAudioSeekTarget(track, target.audioResource, target.positionMs, target.clip)
        )
    }
}
```

Keep normalization and stable-key construction private to this adapter file. Add `ReaderReadaloudReaderEventStep`, `onPlaybackPosition`, and `onReaderEvent` entry points in this file; each creates the adapter and delegates to the reducer. A null timeline remains a no-op. A present timeline with no active clip delegates `null` to `followPlaybackCue` so an old overlay clears.

- [x] **Step 4: Migrate the Android host and remove duplicate files**

Initialize `ReaderReadaloudSyncState(syncEnabled = readaloudSyncEnabled)`, read `syncState.syncEnabled`, and dispatch `syncState.engineCommand`/`engineCommandKey` directly. Remove all `ReaderMediaOverlaySyncState` imports and nested `overlayState` references. Delete the two old production files only after `rg` proves no remaining production call site needs their declarations.

- [x] **Step 5: Run media-overlay/readaloud GREEN**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderMediaOverlaySyncAdapterTest" --tests "paige.navic.reader.ReaderReadaloudSyncCoordinatorTest" --console=plain
```

Expected: every migrated readaloud test passes with zero failures.

GREEN evidence: `ReaderMediaOverlaySyncAdapterTest` passed 3/3 and `ReaderReadaloudSyncCoordinatorTest` passed 3/3 with zero failures, errors, or skips. The Android host compiled with direct `ReaderReadaloudSyncState`; production has no `ReaderMediaOverlaySyncState` reference.

- [x] **Step 6: Commit the media-overlay migration**

Commit message: `refactor(reader): adapt readaloud overlay sync`.

## Task 4: Add And Migrate The Whispersync Adapter

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncOverlaySyncAdapter.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinatorTest.kt`

- [x] **Step 1: Extend Whispersync tests before migration**

Keep all current tests and add assertions that a repeated visible range emits neither seek nor command, while a repeated text point remains seekable when the adapter marks it `repeatSeek=true`. Add a test proving progressive playback on the same segment publishes `UpdateMediaOverlayProgress`, not a second apply.

- [x] **Step 2: Run the new tests and capture RED for the missing adapter contract**

Require `WhispersyncOverlaySyncAdapter` in the tests. Expected: compilation fails before production implementation.

RED evidence: host-test compilation failed with unresolved `WhispersyncOverlaySyncAdapter`, `WhispersyncPlaybackSyncInput`, and `WhispersyncReaderSyncInput`. A second reducer RED run failed solely because the requested `updateRepeatedCue` policy did not yet exist.

- [x] **Step 3: Implement typed Whispersync inputs and adapter**

```kotlin
data class WhispersyncPlaybackSyncInput(
    val audioResource: String,
    val positionMs: Long,
    val audioTrackIndex: Int? = null,
    val playbackSpeed: Float = 1f,
    val highlightLeadMs: Int = 0
)

sealed interface WhispersyncReaderSyncInput {
    data class VisibleRange(val textHref: String, val visibleStart: Int, val visibleEnd: Int) : WhispersyncReaderSyncInput
    data class TextPoint(val textHref: String, val textOffset: Int) : WhispersyncReaderSyncInput
}

class WhispersyncOverlaySyncAdapter(
    private val timeline: WhispersyncTimeline
) : ReaderOverlayTimelineAdapter<WhispersyncPlaybackSyncInput, WhispersyncReaderSyncInput, WhispersyncAudioSeekTarget>
```

Playback resolution uses the current audio resource/track rules, visual lead, playback speed, `nextSegmentAfter`, and progressive text marker. Visible ranges return `repeatSeek=false`; text points return `repeatSeek=true` to preserve current user-initiated seeking within an already active segment.

- [x] **Step 4: Delegate coordinator transitions to the shared reducer**

Replace the Whispersync state declaration with:

```kotlin
typealias ReaderWhispersyncSyncState = ReaderOverlaySyncState
```

Keep every existing status and log branch in `ReaderWhispersyncSyncCoordinator.kt`. Resolve through the adapter, then call `followPlaybackCue` or `followReaderTarget`. Preserve the current difference between unmatched visible ranges (clear an active overlay) and unmatched text points (retain it). Remove the private duplicate key builder, clear helper, and command-key increment helper.

Update `ReaderController` field references from `activeSegmentKey`/`activeSegmentProgressTextEnd` to `activeCueKey`/`activeProgressTextEnd`. Do not alter the page-boundary pause, status, progress-save, repair, or audio-seek behavior.

- [x] **Step 5: Run Whispersync GREEN**

Run the coordinator, controller, playback-policy, launch-policy, progress-highlight source, and diagnostics source tests. Expected: zero failures and all existing status/progressive assertions preserved.

GREEN evidence: 120 tests passed with zero failures, errors, or skips: coordinator 9, controller 81, playback policy 12, launch policy 3, progress-highlight source 14, and diagnostics source 1. The shared reducer's 5 tests and the media/readaloud 6 tests also remained green after migration.

- [x] **Step 6: Commit the Whispersync migration**

Commit message: `refactor(reader): adapt whispersync overlay sync`.

## Task 5: Run One Contract Against Both Real Adapters

**Files:**
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderOverlaySyncContractTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderOverlaySyncSourceTest.kt`

- [ ] **Step 1: Build two real adapter harnesses**

Define a private test harness with operations for first playback, repeated playback, second cue, outside cue, first reader target, and repeated reader target. Implement one harness with a two-segment `WhispersyncTimeline` and one with a two-clip `MediaOverlayTimeline` plus a two-item `ReadaloudPlaybackPlan`. Do not mock either production adapter.

- [ ] **Step 2: Execute the same scenarios in a loop**

```kotlin
@Test
fun bothTimelineAdaptersHonorTheOverlaySyncContract() {
    listOf(whispersyncHarness(), mediaOverlayHarness()).forEach { harness ->
        val first = harness.followFirstPlayback(ReaderOverlaySyncState())
        assertIs<ReaderEngineCommand.ApplyMediaOverlay>(first.engineCommand, harness.name)
        val repeated = harness.followRepeatedPlayback(first)
        assertEquals(first.engineCommandKey, repeated.engineCommandKey, harness.name)
        val second = harness.followSecondPlayback(repeated)
        assertEquals(first.engineCommandKey + 1L, second.engineCommandKey, harness.name)
        val cleared = harness.followOutsidePlayback(second)
        assertEquals(ReaderEngineCommand.ClearMediaOverlay, cleared.engineCommand, harness.name)

        val reader = harness.followReaderTarget(ReaderOverlaySyncState())
        assertNotNull(reader.seekTarget, harness.name)
        val repeatedReader = harness.followRepeatedReaderTarget(reader.state)
        assertNull(repeatedReader.seekTarget, harness.name)
        assertEquals(reader.state.engineCommandKey, repeatedReader.state.engineCommandKey, harness.name)
    }
}
```

Add a second loop that activates a cue, disables sync, proves one clear, and proves subsequent adapter playback is ignored.

- [ ] **Step 3: Make the source guard pass**

Verify one state declaration, one command-key increment, both adapter constructions, no duplicate old declarations, and absence of both deleted files. Also scan production call sites and record their exact owners in the implementation evidence section.

- [ ] **Step 4: Run the focused B12 suite**

Run reducer, contract, both adapter/coordinator suites, controller, diagnostics, and source contract. Record exact JUnit test/failure/error/skip counts.

- [ ] **Step 5: Commit the contract proof**

Commit message: `test(reader): enforce overlay adapter parity`.

## Task 6: Adjacent Validation And Android Device Smoke

**Files:**
- Modify: `docs/superpowers/plans/2026-07-13-reader-overlay-sync-unification-implementation.md`

- [ ] **Step 1: Run adjacent owner tests**

Include reader controller/coordinator, readaloud runtime navigation, readaloud controller, media-overlay parser, Whispersync models/timeline/parser/playback policy/launch policy, bridge protocol, and engine capability tests. Compare any broad-suite failure against clean `fork/master` before attributing it to B12.

- [ ] **Step 2: Run source governance and Android assemblies**

Run `git diff --check`, source reader-vendor 30/30, verifier tamper self-test, source attribution, `:androidApp:assembleDebug`, and `:androidApp:assembleReaderDev`. Verify packaged vendor 30/30 and attribution for both APKs. Do not invoke an iOS task.

- [ ] **Step 3: Validate Whispersync on Android**

Use an existing sidecar-backed reader fixture. Prove playback applies an overlay, repeated position updates do not create redundant apply commands, progressive updates continue, reader navigation emits one audio seek, disabling sync clears once, and no AndroidRuntime/Media3 fatal appears.

- [ ] **Step 4: Validate EPUB media-overlay readaloud on Android**

Reuse the local valid EPUB3/SMIL fixture pattern from B11. Prove playback applies the correct clip overlay, reader navigation seeks Media3 once, repeated navigation does not loop, disabling sync clears once, and normal exit removes managed session files. Capture targeted logs without exposing credentials.

- [ ] **Step 5: Record exact evidence**

Add test counts, source-contract results, device cue/seek/clear observations, assembly hashes, and any baseline-reproduced exclusions to this plan. Do not claim B12 complete before these artifacts exist.

## Task 7: Document, Version, Publish, And Clean Up

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-reader-overlay-sync-unification-implementation.md`
- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Mark implementation complete but release pending**

Update B12 with the single-state declaration, adapter call-site evidence, shared contract count, focused/adjacent test counts, and Android device results. Update roadmap accounting only after the public release succeeds.

- [ ] **Step 2: Prepare `iota21`**

Set `versionCode=548` and `versionName=v1.0.11-iota21`. Run `scripts/verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-iota21`, prove no unpadded iota/kappa/lambda public refs exist, rerun the focused suite, and rebuild/reverify Android APKs. Do not invoke iOS.

- [ ] **Step 3: Commit and publish**

Fast-forward public `master`, create annotated `v1.0.11-iota21`, and publish the Android release with all iOS jobs skipped. Do not retitle or advance the Greek letter.

- [ ] **Step 4: Independently validate the public APK**

Download `Navic.apk`; verify GitHub digest, local SHA-256, APK Signature Scheme v2 certificate, `548/iota21` metadata, all 30 reader-vendor files, and packaged attribution. Upgrade `darkaxt.navic` in place from `iota20`, cold-start `MainActivity`, confirm a live PID/resumed activity, and scan targeted AndroidRuntime, Koin, Media3, and media-session errors.

- [ ] **Step 5: Publish immutable evidence and clean only this worktree**

Mark B12 Released, increment released/pending roadmap accounting, commit the immutable evidence after the tag, and push public `master`. Confirm the tag still peels to the release commit. Remove the temporary public APK and only `navic-qa-tranche-4-overlay-sync` plus its local branch after proving the evidence commit is reachable from `fork/master`; leave every ebook-animation worktree untouched.

## Self-Review

- One state and command reducer: Tasks 1, 2, and 5.
- Two real typed timeline adapters: Tasks 3 and 4.
- Same seven scenarios against both adapters: Task 5.
- Whispersync progressive/status/page-boundary preservation: Tasks 4 and 6.
- EPUB bridge/Media3 seek and loop suppression preservation: Tasks 3 and 6.
- Duplicate coordinator/state removal only after call-site migration: Tasks 3 and 5.
- Android-only `iota21` release and cleanup: Task 7.
- No placeholder, iOS, persistence, cache, B13, B19, B20, or ebook-animation work is included.
