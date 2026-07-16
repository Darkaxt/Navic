# Android Playback Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate Now Playing pager command races and make uncached Android
playback recover the current queue item asynchronously without queue churn.

**Architecture:** A common pager-intent tracker emits one atomic queue-selection
request for genuine user drags. Android retains one current-item recovery and
uses the existing download flow as its completion signal; playback diagnostics
are retained in the bounded log ring independently from general issue logging.

**Tech Stack:** Kotlin Multiplatform, Compose pager, Android Media3,
Kotlin coroutines/Flow, Room-backed `DownloadManager`, Gradle Android host tests,
ADB, GitHub Actions.

## Execution Status

- Stage 1 committed as `c5a7e55e`: artwork pager settlements are gesture-owned
  and queue selection carries the final playback intent atomically.
- Stage 2 committed as `1797426b`: uncached current-item failures wait on the
  existing download pipeline and replace the same queue item in place.
- Stage 3 committed as `a484707b`: bounded playback diagnostics persist
  independently from the general issue-logging toggle.
- Candidate metadata committed as `a70f98a8`: `versionCode=550` and
  `versionName=v1.0.11-iota23`.
- Focused playback policy, source-wiring, recovery, and diagnostics tests pass.
- `:androidApp:assembleDebug` passes, and the embedded APK metadata matches the
  release target.
- The debug candidate installed and launched on `emulator-5554` as
  `darkaxt.navic.debug`, with process and resumed-activity state verified.
- Reader vendor verifier, packaged vendor hashes, and third-party attribution
  checks pass.
- The full Android host suite currently has 75 failures in reader, Aurral, and
  database source/parity tests outside this branch's changed files.
- Android lint currently has three pre-existing `RestrictedApi` errors in
  `MainActivity.dispatchKeyEvent`; this branch does not modify `MainActivity`.

## Final Release Evidence

- Public master and tag point to release commit `651d1dc6`; the tag descends
  from required commit `9c619f10`.
- GitHub Actions run `29514934015` completed successfully. The Android APK and
  GitHub release jobs passed; all iOS jobs were skipped.
- Public release:
  `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-iota23`
- The downloaded `Navic.apk` SHA-256 is
  `58322a9fc0241c6ca38c026de33279cc82ef03c7bd9ba04d5d4ef30647865882`,
  matching the GitHub asset digest.
- Independent APK inspection confirms package `darkaxt.navic`,
  `versionCode=550`, and `versionName=v1.0.11-iota23`.
- `apksigner` verifies one v2 signer with certificate SHA-256
  `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
- The public APK upgraded the emulator's existing production package in place,
  preserved its `firstInstallTime`, launched successfully, and became the
  resumed activity with a live process.
- The emulator's production queue was empty before installation, so non-empty
  queue preservation could not be observed there.
- Phone `RFCY80551LT` was not attached during deployment. Physical-phone
  installation and walking/roaming acceptance remain field validation, not a
  claimed local result.

---

## File Map

- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingPagerIntent.kt`
  - Pure user-drag settlement state and queue-selection request.
- Create `composeApp/src/commonTest/kotlin/paige/navic/domain/models/NowPlayingPagerIntentTest.kt`
  - Pager command ownership and paused-intent tests.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt`
  - Add atomic queue-selection contract and retain `playAt` compatibility.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/controls/ArtworkPager.kt`
  - Gate selection on a consumed user drag and read current state at settlement.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`
  - Execute/persist atomic selections and connect recovery to download state.
- Modify `composeApp/src/iosMain/kotlin/paige/navic/shared/MediaPlayer.ios.kt`
  - Compile-compatible queue-selection implementation only.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt`
  - Retain current-item recovery intent and handle download completion/failure.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackDiagnosticsLogger.android.kt`
  - Record queue-selection origins and terminal recovery decisions.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicy.kt`
  - Pure terminal recovery target policy.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicyTest.kt`
  - Terminal hold/single-advance tests.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/AppLogManager.kt`
  - Persist playback diagnostics independently from the general logging toggle.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/AppLogManagerTest.kt`
  - Always-retained playback diagnostics and disable-filter behavior.
- Modify Android source tests under `composeApp/src/androidHostTest/kotlin/paige/navic/shared/`
  - Guard pager, selection, recovery, and diagnostics wiring.
- Modify `androidApp/build.gradle.kts`
  - Set `versionCode=550`, `versionName=v1.0.11-iota23`.

## Task 1: Pager Intent and Atomic Selection

- [ ] Add failing `NowPlayingPagerIntentTest` cases proving that programmatic
  settlements emit no request, one drag emits one request, invalid/current pages
  emit none, and paused state becomes `playWhenReady=false`.
- [ ] Run:
  `.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.NowPlayingPagerIntentTest"`
  and confirm failure because the policy does not exist.
- [ ] Add the minimal tracker/request types and rerun the focused test green.
- [ ] Add failing Android source assertions requiring
  `selectQueueItem(index, playWhenReady, origin)`, a complete pending selection,
  and no pager `playAt(page)` plus `pause()` sequence.
- [ ] Run the source test and confirm the new assertions fail.
- [ ] Implement the common contract, Android atomic command, iOS compile
  compatibility, and pager wiring.
- [ ] Run the focused policy/source tests and commit:
  `fix(playback): make artwork selection atomic`.

## Task 2: Queue-Preserving Download Recovery

- [ ] Extend `PlaybackQueueRecoveryPolicyTest` with failing cases proving terminal
  failure holds by default, advances once only when enabled, and never invents a
  target.
- [ ] Run the focused policy test and confirm the new API is absent.
- [ ] Add the minimal terminal decision policy and rerun green.
- [ ] Add failing Android source assertions requiring pending current-item
  recovery, `prefetchPlaybackSongs(listOf(song))`, download status observation,
  same-index local replacement, user-intent cancellation hooks, and absence of
  `moveMediaItem`/queue mutation in recovery.
- [ ] Run the source test and confirm failure.
- [ ] Implement pending recovery in
  `AndroidStablePlaybackRecoveryCoordinator`, wire download snapshots and user
  commands in `AndroidMediaPlayerViewModel`, and keep loading progress active.
- [ ] Run focused tests and commit:
  `fix(playback): recover uncached songs in place`.

## Task 3: Durable Playback Diagnostics

- [ ] Add failing `AppLogManagerTest` cases proving `PlaybackDiagnostics` records
  while general logging is disabled, normal tags remain dropped, disabling
  logging retains only playback entries, and explicit clear removes everything.
- [ ] Run the focused test and confirm current behavior fails.
- [ ] Add a pure persistence policy and update `AppLogManager` minimally.
- [ ] Add failing source assertions for queue-selection origin, pending recovery,
  terminal failure, and cancellation reason.
- [ ] Implement diagnostics methods/wiring and rerun focused tests green.
- [ ] Commit:
  `fix(playback): retain bounded recovery diagnostics`.

## Task 4: Android Verification and Candidate

- [ ] Run all focused tests from Tasks 1-3.
- [ ] Run `.\gradlew.bat :composeApp:testAndroidHostTest`.
- [ ] Run `.\gradlew.bat :androidApp:assembleDebug`.
- [ ] Run `.\gradlew.bat :androidApp:lintDebug`.
- [ ] Run `git diff --check` and inspect `git status --short`.
- [ ] Confirm no iOS build/release job is invoked.
- [ ] Set Android metadata to `versionCode=550` and
  `versionName=v1.0.11-iota23`.
- [ ] Run `scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-iota23`.
- [ ] Rebuild debug APK and verify embedded metadata.
- [ ] Install the candidate on an available emulator or compatible Android test
  package, launch it, and verify package/version/process state.
- [ ] Commit:
  `release: prepare iota23 playback stability`.

## Task 5: Sync and Public Android Release

- [ ] Fetch `fork` and tags; if `fork/master` moved, integrate it before release
  and rerun Task 4 verification.
- [ ] Prove:
  `git merge-base --is-ancestor 9c619f10 HEAD`.
- [ ] Prove the branch is a fast-forward of current `fork/master`.
- [ ] Push `HEAD:master`.
- [ ] Create and push annotated tag `v1.0.11-iota23`.
- [ ] Publish the Android-only GitHub release with the repository release script
  and a readiness note containing test/build/ancestry evidence.
- [ ] Wait on the release workflow through GitHub CLI, not cancellation
  timeouts.
- [ ] Download public `Navic.apk`; verify GitHub digest, local SHA-256, APK
  signature certificate, `versionCode=550`, and
  `versionName=v1.0.11-iota23`.
- [ ] Install public `Navic.apk` in place on phone `RFCY80551LT`, launch Navic,
  and verify package version, PID, media session, and queue preservation.
- [ ] Verify the GitHub release is public and contains the Android APK.

## Task 6: Cleanup and Completion

- [ ] Record final release/device evidence in this plan.
- [ ] Confirm the feature branch is merged by ancestry and the worktree is clean.
- [ ] Remove only
  `C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-playback-stability-iota23`
  with `git worktree remove`.
- [ ] Delete the local feature branch only after remote master and tag contain
  its tip.
- [ ] Run `git worktree prune` and confirm unrelated ebook worktrees/changes are
  untouched.
