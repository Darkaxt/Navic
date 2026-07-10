# Stable Queue and Playback Recovery Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Contain Navic's Android mobile playback cascade without claiming the later canonical-queue migration is complete.

**Architecture:** Keep the existing Media3 integration for this release, but split artwork prefetch from audio downloads and replace deferred-download queue mutation with an in-place remote-source refresh followed by the configured final error policy. Queue order remains unchanged by all Stage 1 recovery effects.

**Tech Stack:** Kotlin Multiplatform, Android Media3, Kotlin coroutines/Flow, kotlin.test, Gradle, PowerShell release scripts, GitHub CLI.

---

### Task 1: Lock the containment behavior with failing tests

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/PlaybackDiagnosticsSourceTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueuePolicyTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicyTest.kt`

- [ ] **Step 1: Replace the old full-audio prefetch expectation**

Require the asset prefetcher to retain artwork work while containing neither `DownloadManager` nor `prefetchPlaybackSongs`.

- [ ] **Step 2: Replace deferred-download recovery expectations**

Require the ViewModel to call `shouldSkipMediaAfterPlaybackError`, attempt an in-place remote refresh, and contain no automatic `prefetchPlaybackSongs` or recovery `moveMediaItem` path.

- [ ] **Step 3: Add pure policy coverage**

Rename the visible-window helper to `playbackArtworkPrefetchIndexes` and assert that final error advancement is allowed only when the preference is enabled and a playable target exists.

- [ ] **Step 4: Verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:allTests --tests "paige.navic.domain.models.PlaybackQueuePolicyTest" --tests "paige.navic.domain.models.PlaybackQueueRecoveryPolicyTest"
.\gradlew.bat :composeApp:androidHostTest --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest" --tests "paige.navic.shared.PlaybackDiagnosticsSourceTest"
```

Expected: failures reference the old `playbackPrefetchIndexes`, automatic `prefetchPlaybackSongs`, and deferred-download recovery source.

### Task 2: Separate artwork prefetch from durable audio downloads

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueuePolicy.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackAssetPrefetcher.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`

- [ ] **Step 1: Rename the visible-window policy**

Change `playbackPrefetchIndexes` to `playbackArtworkPrefetchIndexes` so its ownership is explicit.

- [ ] **Step 2: Remove the download dependency**

Remove `DownloadManager` from `AndroidPlaybackAssetPrefetcher` and delete the `prefetchPlaybackSongs(songs)` call. Keep concurrent artwork prefetch and signature deduplication.

- [ ] **Step 3: Update construction and call sites**

Construct the prefetcher without `downloadManager`; retain current and upcoming artwork calls.

- [ ] **Step 4: Verify GREEN for prefetch tests**

Run the two focused test classes from Task 1 and confirm the prefetch assertions pass.

### Task 3: Replace deferred-download recovery with stable in-place recovery

**Files:**
- Delete: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackDownloadRecoveryCoordinator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackDiagnosticsLogger.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicy.kt`

- [ ] **Step 1: Add a one-generation remote refresh key**

Track `(mediaId, currentIndex)` for the remote source refreshed in the current queue position. Reset it when Media3 transitions to a different entry.

- [ ] **Step 2: Refresh remote media in place**

For a non-file source error, rebuild the current `MediaItem`, replace it at the same index, restore position, prepare, and play only when `playWhenReady` indicates play intent. Never enqueue a download.

- [ ] **Step 3: Apply final error policy**

After the single refresh is exhausted, notify the error. Find the next playable logical index and advance only when `shouldSkipMediaAfterPlaybackError(preference, target != null)` is true. Otherwise remain on the current entry with loading cleared.

- [ ] **Step 4: Contain unavailable automatic transitions**

Use the same final policy for an unavailable automatically transitioned item. Do not download, replay, or reorder it.

- [ ] **Step 5: Remove deferred-download promotion**

Delete calls and state that promote completed downloads or move recovered queue entries. Preserve ordinary URI replacement when an explicit download completes and the current item can be replaced without changing order.

- [ ] **Step 6: Verify GREEN for recovery tests**

Run the focused common and Android host tests and confirm all assertions pass.

### Task 4: Preserve playback intent during buffering

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`

- [ ] **Step 1: Add the failing source assertion**

Require pause projection to use `!playWhenReady`, not `!isPlaying` or `!controller.isPlaying`.

- [ ] **Step 2: Update pause projection**

In Media3 callbacks and state synchronization, derive the existing `isPaused` UI field from `playWhenReady`. Buffering remains loading while user intent remains play.

- [ ] **Step 3: Verify RED then GREEN**

Run `:composeApp:androidHostTest` before and after implementation and retain both outputs in the work log.

### Task 5: Validate the Stage 1 release candidate

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/specs/2026-07-10-stable-queue-playback-recovery-design.md` only if validation exposes a documented limitation

- [ ] **Step 1: Run focused tests**

```powershell
.\gradlew.bat :composeApp:allTests :composeApp:androidHostTest
```

- [ ] **Step 2: Bump the release version**

Increment `versionCode` from `513` to `514` and set `versionName` to `v1.0.11-theta86`.

- [ ] **Step 3: Verify the release version and lint**

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName "v1.0.11-theta86"
.\gradlew.bat :androidApp:lintRelease
```

- [ ] **Step 4: Build the signed APK**

Use the repository's established signed release Gradle task and verify APK package metadata, signing certificate, and SHA-256 digest.

- [ ] **Step 5: Device smoke test**

Install with `adb install -r`, confirm `versionCode=514` and `versionName=v1.0.11-theta86`, launch Navic, and inspect startup playback/media errors. This gate does not claim mobile-data acceptance.

### Task 6: Sync and publish

**Files:**
- Create: `releases/v1.0.11-theta86/Navic.apk`
- Create: `releases/v1.0.11-theta86/SHA256SUMS.txt`

- [ ] **Step 1: Review repository state**

Confirm only intended tracked files changed and generated release artifacts remain in ignored/untracked release directories as established by the repository.

- [ ] **Step 2: Commit implementation and release version**

Create scoped commits for documentation, containment behavior, and release metadata.

- [ ] **Step 3: Synchronize master**

Fetch `fork`, confirm no unexpected divergence, rebase or fast-forward only when required, then push `master`.

- [ ] **Step 4: Publish public GitHub release**

Create and push tag `v1.0.11-theta86`, publish the release with `Navic.apk` and checksum, and verify the public release and assets through GitHub.

- [ ] **Step 5: Record deferred field gate**

Report that mobile-data walk validation remains the Stage 1 acceptance gate for uncached streaming, stable Queue/Up Next, and cached multi-song playback.
