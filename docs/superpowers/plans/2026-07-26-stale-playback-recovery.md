# Stale Playback Identity Recovery Implementation Plan

**Date:** 2026-07-26

**Design:** `docs/superpowers/specs/2026-07-26-stale-playback-recovery-design.md`

**Release:** Android-only `v1.0.11-iota28`

## Stage 0: Synchronize and Isolate

- [x] Work in `fix/stale-playback-recovery` under the isolated Codex worktree.
- [x] Fetch `fork/master` and `origin/master`.
- [x] Record upstream `origin/master` in ancestry while preserving the fork's
  incompatible navigation/playback architecture.
- [x] Prove `origin/master` is an ancestor of the feature branch.
- [x] Compile the preserved synchronized Android base.
- [x] Integrate the separately tested duplicate Logs cleanup.

## Stage 1: Pure Identity and Lifecycle Policies

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/StalePlaybackSongPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicy.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/StalePlaybackSongPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/domain/models/PlaybackQueueRecoveryPolicyTest.kt`

Tasks:

- [x] Add a parser-error eligibility policy.
- [x] Add tiered unique identity matching for MusicBrainz ID, ISRC, and exact
  metadata signature.
- [x] Add accepted/active/rejected download lifecycle state.
- [x] Make failed, rejected, and vanished accepted requests terminal.
- [x] Select terminal targets from Media3 `upcomingIndexes`.

Gate:

- [x] New tests fail before production code.
- [x] Focused common tests pass after production code.

## Stage 2: Authenticated Stale-ID Resolution

Files:

- `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStalePlaybackSongResolver.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt`

Tasks:

- [x] Probe the old ID with authenticated `getSong` only for eligible remote
  parser/container failures.
- [x] Classify `DATA_NOT_FOUND`, service unavailable, current, and unresolved.
- [x] Resolve a confirmed stale song against current catalog data.
- [x] Validate current song/index before applying async results.
- [x] Replace the logical queue and Media3 item in place.
- [x] Preserve position and user playback intent.
- [x] Notify and hold/advance once when no unique replacement exists.

Gate:

- [x] Android source/contract tests fail before wiring.
- [x] Android source/contract tests pass after wiring.
- [x] Android debug Kotlin compilation passes.

## Stage 3: Terminal Download Recovery

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackDownloadRequestResult.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt`

Tasks:

- [ ] Add a suspend playback-recovery download request with explicit result.
- [ ] Reject missing local catalog entries and inactive sessions directly.
- [ ] Track accepted and active request lifecycle without polling deadlines.
- [ ] Resume only from a verified usable local file.
- [ ] Treat failed or vanished accepted requests as terminal.

Gate:

- [ ] Focused download/recovery policy tests pass.
- [ ] Existing download scheduler tests pass.
- [ ] Android debug Kotlin compilation passes.

## Stage 4: Design Comparison and Regression Gates

- [ ] Add bounded diagnostics for probe, replacement, stale result, rejected
  request, and terminal decision.
- [ ] Run the duplicate Logs regression test.
- [ ] Run stale identity and playback recovery tests.
- [ ] Run Android host tests and compare failures with the known baseline.
- [ ] Run Android debug and release compilation/build gates.
- [ ] Review every design invariant and acceptance row against code/tests.
- [ ] Record deviations or residual risk in this plan; do not silently weaken
  the design.

## Stage 5: Release and Cleanup

- [ ] Verify commit `9c619f10` is in release ancestry.
- [ ] Fetch `fork/master` again and reconcile any public drift.
- [ ] Increment version exactly once to `v1.0.11-iota28` and the next Android
  version code.
- [ ] Build and inspect the signed Android APK.
- [ ] Commit release metadata intentionally.
- [ ] Push public master and tag.
- [ ] Create the public GitHub release with Android artifact and digest.
- [ ] Verify tag, release, asset, version, signature, and ancestry remotely.
- [ ] Remove only this isolated worktree and temporary branches after public
  ancestry is proven.

## Design Comparison

Complete this section after implementation.

| Design requirement | Implementation evidence | Result |
| --- | --- | --- |
| Authenticated stale-ID confirmation | Pending | Pending |
| Conservative unique matching | Pending | Pending |
| In-place logical/media replacement | Pending | Pending |
| Async stale-result rejection | Pending | Pending |
| Actual upcoming-order terminal target | Pending | Pending |
| Explicit download request result | Pending | Pending |
| No indefinite `NOT_DOWNLOADED` wait | Pending | Pending |
| No timeout-based cancellation | Pending | Pending |
| Android-only release | Pending | Pending |
