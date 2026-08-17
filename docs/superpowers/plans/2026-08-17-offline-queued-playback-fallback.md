# Offline Queued Playback Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Android offline playback from waiting indefinitely on a queued recovery download and immediately continue with the first usable cached song in Media3 order.

**Architecture:** Add an effective-connectivity gate to `AndroidStablePlaybackRecoveryCoordinator` and apply it before network recovery starts and while pending download snapshots are processed. Reuse the existing offline fallback resolver for queue-preserving cached selection, while the view model hides download progress whenever effective online state is false.

**Tech Stack:** Kotlin Multiplatform, Android Media3, coroutines and StateFlow, Kotlin Test, Gradle Android host tests.

---

## File Map

- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt`: arbitrate remote recovery against effective connectivity at every asynchronous entry point.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`: inject effective connectivity and suppress impossible download progress.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`: enforce coordinator wiring, pre-download fallback, pending-download fallback, and offline progress behavior.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/domain/models/OfflinePlaybackFallbackPolicyTest.kt`: prove complete ordered traversal and queue-preserving outcomes.
- Create `docs/superpowers/specs/2026-08-17-offline-queued-playback-fallback-design.md`: approved behavior and release contract.

### Task 1: Lock the offline recovery contract

**Files:**
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/OfflinePlaybackFallbackPolicyTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`

- [ ] **Step 1: Add a complete-traversal policy test**

Add a test whose cached candidate is beyond the five visible Up Next entries:

```kotlin
@Test
fun offlineFallbackSearchesTheCompleteMediaTraversal() {
	val queue = (0..9).map { "song-$it" }

	assertEquals(
		OfflinePlaybackFallbackResolution.PlayUpcoming(8),
		resolveOfflinePlaybackFallback(
			currentIndex = 0,
			queueSongIds = queue,
			upcomingIndexes = listOf(3, 5, 2, 6, 4, 7, 8, 9, 1),
			availableSongIds = setOf("song-8"),
			currentUsesLocalFile = false
		)
	)
}
```

- [ ] **Step 2: Add Android source-contract tests**

Assert that the coordinator accepts `isEffectivelyOnline`, checks remote source
ownership before stale probing/download recovery, checks connectivity again in
`beginDownloadRecovery`, and hands a pending snapshot to offline fallback. Also
assert that progress is suppressed when `connectivityManager.isOnline.value` is
false.

- [ ] **Step 3: Run the focused tests and record RED**

Run:

```powershell
.\gradlew.bat :composeApp:commonTest --tests "paige.navic.domain.models.OfflinePlaybackFallbackPolicyTest" :composeApp:testAndroidHostTest --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest"
```

Expected: the traversal test passes against existing pure policy, while the new
Android source contracts fail because connectivity-aware recovery wiring does
not exist.

### Task 2: Add connectivity-aware recovery arbitration

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`

- [ ] **Step 1: Inject effective connectivity**

Add this coordinator dependency and wire it from the view model:

```kotlin
private val isEffectivelyOnline: () -> Boolean
```

```kotlin
isEffectivelyOnline = { connectivityManager.isOnline.value }
```

- [ ] **Step 2: Centralize the remote offline handoff**

Add a coordinator helper that returns false for local `file` items and routes a
remote item through `handleServiceUnavailable()` when effective online state is
false:

```kotlin
private fun handOffRemoteRecoveryWhenOffline(
	player: MediaController,
	state: PlayerUiState,
	reason: String,
	error: PlaybackException? = null
): Boolean
```

The helper must return whether it handled recovery so each caller exits without
starting network work.

- [ ] **Step 3: Apply the gate before recovery work**

Call the helper in `handlePlayerError()` after explicit service-unavailable
classification but before stale-song probing, remote refresh, or download
recovery. Keep local-file failures on their existing path.

- [ ] **Step 4: Apply the defensive gate before requesting a download**

Call the same helper at the start of `beginDownloadRecovery()`. This closes the
race where connectivity changes after the initial player-error decision but
before its coroutine issues a persistent download request.

- [ ] **Step 5: Re-arbitrate pending download snapshots**

At the beginning of `handleDownloadSnapshot()`, after retrieving the pending
recovery and before interpreting `QUEUED` or `DOWNLOADING`, call the helper. An
offline pending recovery must enter cached fallback instead of returning
`PlaybackRecoveryResolution.Wait`.

- [ ] **Step 6: Suppress impossible progress presentation**

Change `updatePlaybackDownloadProgress()` so either service-wait state or false
effective connectivity clears `playbackDownloadProgress`:

```kotlin
if (playbackRecovery.isWaitingForService || !connectivityManager.isOnline.value) {
	_uiState.update { state -> state.copy(playbackDownloadProgress = null) }
	return
}
```

- [ ] **Step 7: Run focused tests and record GREEN**

Run the Task 1 command. Expected: all selected tests pass.

### Task 3: Verify recovery semantics

**Files:**
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicyTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/OfflinePlaybackFallbackPolicyTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`

- [ ] **Step 1: Run all focused recovery policy tests**

```powershell
.\gradlew.bat :composeApp:commonTest --tests "paige.navic.domain.models.*Playback*Recovery*Test" --tests "paige.navic.domain.models.OfflinePlaybackFallbackPolicyTest" :composeApp:testAndroidHostTest --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest" --tests "paige.navic.shared.PlaybackDiagnosticsSourceTest"
```

Expected: all selected tests pass with no new failures.

- [ ] **Step 2: Inspect the diff against design invariants**

Confirm that the production diff does not mutate queue contents, does not add a
timeout, does not alter `skipMediaOnError`, and does not route local-file errors
into service outage handling.

- [ ] **Step 3: Commit the behavior change**

```powershell
git add composeApp/src docs/superpowers
git commit -m "fix(playback): bypass queued recovery while offline"
```

### Task 4: Broad verification and release

**Files:**
- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Run broad host verification**

```powershell
.\gradlew.bat :composeApp:commonTest :composeApp:testAndroidHostTest
```

Expected: no regression from the known baseline; all music recovery tests pass.

- [ ] **Step 2: Advance the release version**

Change the Android release suffix from `iota59` to `iota60` without changing the
established `{letter}##` sequence.

- [ ] **Step 3: Assemble the public release artifact**

```powershell
.\gradlew.bat :androidApp:assembleRelease
```

Expected: Gradle exits zero and produces the signed release APK expected by the
repository release workflow.

- [ ] **Step 4: Verify artifact identity and checksum**

Use `aapt dump badging`, `apksigner verify --print-certs`, and
`Get-FileHash -Algorithm SHA256` against the release APK. Confirm package,
version name, version code, signer, and checksum before publication.

- [ ] **Step 5: Compare implementation to the specification**

Read every Required Behavior, Invariant, and Acceptance Criteria item in the
design document and map it to either a focused test, source inspection, or build
evidence. Record any device-only field acceptance as pending instead of claiming
it was reproduced locally.

- [ ] **Step 6: Commit release metadata and publish**

```powershell
git add androidApp/build.gradle.kts
git commit -m "chore(release): prepare v1.0.11-iota60"
git tag -a v1.0.11-iota60 -m "v1.0.11-iota60"
```

Push the integrated public branch and tag, create the GitHub release with the
stable APK only, then verify the remote tag, release metadata, asset checksum,
and public download response.

- [ ] **Step 7: Clean the temporary worktree**

After commits, push, release, and verification are complete, remove only
`D:\Temp\navic-offline-stall-analysis`. Preserve the branch and public Git
history; do not touch active ebook worktrees.
