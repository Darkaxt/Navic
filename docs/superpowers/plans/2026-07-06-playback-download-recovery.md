# Playback Download Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep music playing when queued songs are unavailable by deferring unavailable items for download, promoting recovered items, and replaying the last confirmed playable song when the queue has no playable candidate.

**Architecture:** Add a small pure playback recovery policy in common code and wire Android Media3 behavior through it. Android owns Media3 mutations, download requests, and diagnostics; common code owns index/decision rules that can be tested without a device.

**Tech Stack:** Kotlin Multiplatform, Media3 `MediaController`, Room/download snapshots, Android logcat diagnostics, `:composeApp:testAndroidHostTest`.

---

## File Map

- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicy.kt`
  - Pure queue/deferred recovery decisions.
- Create `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicyTest.kt`
  - Red/green tests for next-playable, fallback replay, and recovered download promotion.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackDiagnosticsLogger.android.kt`
  - Add structured recovery decision logs.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`
  - Track last playable snapshot and deferred playback downloads.
  - Replace blind skip paths with defer/download/continue/replay behavior.
  - Promote deferred downloaded items into the next slot.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/shared/PlaybackDiagnosticsSourceTest.kt`
  - Guard that diagnostics are emitted for the new recovery decisions.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/shared/AndroidMediaPlayerViewModelSourceTest.kt`
  - Guard that auto-transition and source-error paths use deferred recovery instead of blind skip-only behavior.

## Task 1: Pure Recovery Policy

- [x] Write failing tests in `PlaybackQueueRecoveryPolicyTest`:
  - `firstPlayableUpcomingIndexSkipsUnavailableItems`
  - `queueRecoveryReplaysLastPlayableWhenNoPlayableCandidateExists`
  - `downloadedDeferredItemMovesDirectlyAfterCurrentItem`
- [x] Run the focused test and confirm it fails because the policy does not exist.
- [x] Add `PlaybackQueueRecoveryPolicy.kt` with:
  - `firstPlayableUpcomingIndex(currentIndex, queueSongIds, availableSongIds)`
  - `shouldReplayLastPlayable(hasLastPlayable, hasPlayableUpcoming, hasDeferredDownloads)`
  - `recoveredDownloadTargetIndex(currentIndex, queueSize)`
- [x] Run the focused test and confirm it passes.

## Task 2: Diagnostics Contract

- [x] Extend `PlaybackDiagnosticsSourceTest` with assertions for:
  - `onDeferredDownloadRequested`
  - `onPlaybackRecoveryDecision`
  - `onDeferredDownloadReady`
  - `onReplayLastPlayable`
- [x] Run the focused source test and confirm it fails.
- [x] Add the diagnostics methods to `AndroidPlaybackDiagnosticsLogger`.
- [x] Run the focused source test and confirm it passes.

## Task 3: Android Player Wiring

- [x] Extend `AndroidMediaPlayerViewModelSourceTest` with assertions that:
  - auto-transition unavailable handling calls a helper that defers current song for download
  - source-error skip handling calls the same recovery helper instead of only `seekToNextMediaItem()`
  - download-flow recovery promotes deferred items after the current item
  - the view model tracks a last playable snapshot
- [x] Run the focused source test and confirm it fails.
- [x] Add `LastPlayableSnapshot` and `DeferredPlaybackDownload` state to `AndroidPlaybackDownloadRecoveryCoordinator`.
- [x] Refresh the last playable snapshot from ready/playable state changes.
- [x] Add `deferCurrentAndContinueOrReplay(...)`:
  - records deferred download work
  - calls `downloadManager.prefetchPlaybackSongs(listOf(song))`
  - chooses next playable using `firstPlayableUpcomingIndex(...)`
  - replays last playable when no candidate exists
  - logs every decision
- [x] Replace the auto-transition skip path with `deferCurrentAndContinueOrReplay(...)`.
- [x] Replace source-error skip-only handling with `deferCurrentAndContinueOrReplay(...)`.
- [x] In the download flow, detect deferred downloaded items and move them to the first safe upcoming slot.
- [x] Run the focused source test and confirm it passes.

## Task 4: Verification

- [x] Run focused policy and source tests:
  - `./gradlew :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.PlaybackQueueRecoveryPolicyTest" --tests "paige.navic.shared.PlaybackDiagnosticsSourceTest" --tests "paige.navic.shared.AndroidMediaPlayerViewModelSourceTest"`
- [x] Run `git diff --check`.
- [x] Run a broader host test only after focused tests are green.

## Task 5: Commit, Sync, Release

- [x] Commit the spec, plan, tests, and implementation.
- [x] Fetch `fork` and rebase/merge if master moved.
- [x] Prepare the theta75 release version.
- [x] Push master and tag/release with the APK artifact.
- [x] Verify the GitHub release exists and the uploaded APK is present.

## Theta76 Diagnostic Tightening

- [x] Add RED source assertions for explicit skip, retry, and hard-failure recovery events.
- [x] Emit `skip-to-next-playable` when the coordinator skips an unavailable item to a playable candidate.
- [x] Emit `retry-playback-source` when a local file replaces the failed stream/download source.
- [x] Emit `hard-playback-failure` only when no defer/continue/replay recovery path can be started.
- [x] Re-run focused tests and `git diff --check` for the theta76 patch.
