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

- [x] Add a suspend playback-recovery download request with explicit result.
- [x] Reject missing local catalog entries and inactive sessions directly.
- [x] Track accepted and active request lifecycle without polling deadlines.
- [x] Resume only from a verified usable local file.
- [x] Treat failed or vanished accepted requests as terminal.

Gate:

- [x] Focused download/recovery policy tests pass.
- [x] Existing download scheduler tests pass.
- [x] Android debug Kotlin compilation passes.

## Stage 4: Design Comparison and Regression Gates

- [x] Add bounded diagnostics for probe, replacement, stale result, rejected
  request, and terminal decision.
- [x] Run the duplicate Logs regression test.
- [x] Run stale identity and playback recovery tests.
- [x] Run Android host tests and compare failures with the known baseline.
- [x] Run the Android debug compilation gate.
- [x] Run the Android release build gate.
- [x] Review every design invariant and acceptance row against code/tests.
- [x] Record deviations or residual risk in this plan; do not silently weaken
  the design.

## Stage 5: Release and Cleanup

- [x] Verify commit `9c619f10` is in release ancestry.
- [x] Fetch `fork/master` again and reconcile any public drift.
- [x] Increment version exactly once to `v1.0.11-iota28` and the next Android
  version code.
- [x] Build and inspect the signed Android APK.
- [x] Commit release metadata intentionally.
- [x] Push public master and tag.
- [x] Create the public GitHub release with Android artifact and digest.
- [x] Verify tag, release, asset, version, signature, and ancestry remotely.
- [x] Remove only this isolated worktree and temporary branches after public
  ancestry is proven.

## Design Comparison

| Design requirement | Implementation evidence | Result |
| --- | --- | --- |
| Parser work stays off the player callback | `AndroidStablePlaybackRecoveryCoordinator.beginStaleSongProbe` launches the resolver in its injected coroutine scope | Pass |
| Authenticated stale-ID confirmation | `AndroidStalePlaybackSongResolverFactory` uses `SessionManager.withApi { getSong(id) }`; `StalePlaybackSongResolver` only remaps after typed `DATA_NOT_FOUND` | Pass |
| Conservative unique matching | `StalePlaybackSongPolicy` implements ordered unique MusicBrainz, ISRC, and exact metadata tiers; fuzzy/incomplete and ambiguous tests reject replacement | Pass |
| In-place logical/media replacement | `withQueueSongReplacement` repairs one logical index; the coordinator calls `replaceMediaItem` at the same index and seeks to the retained position | Pass |
| Queue order and playback intent preservation | `PlayerUiStateQueueRepairTest`, the Media3 source contract, retained `shouldResume`, and the pause-during-probe guard preserve order and current user intent | Pass |
| Async stale-result rejection | The coordinator re-reads `pending` and validates captured song ID plus queue index before applying either probe or request results | Pass |
| Actual upcoming-order terminal target | `firstPlayableUpcomingIndex` consumes `PlayerUiState.upcomingIndexes`; shuffled-order and unavailable-item tests pass | Pass |
| Missing/ambiguous terminal behavior | `finishConfirmedMissingSong` emits one notice and holds or advances once according to `skipMediaOnError` and retained Play intent | Pass |
| Explicit download request result | `PlaybackDownloadRequestResult` and `DownloadManager.requestPlaybackRecoveryDownload` return all five specified outcomes | Pass |
| No indefinite `NOT_DOWNLOADED` wait | Enqueued/active results record their committed generation as Active; matching cancellation, missing row, failure, and unusable downloaded file are terminal in `playbackRecoveryResolution` | Pass |
| Service errors preserve offline fallback | Resolver service classification feeds `handleServiceUnavailable`; unknown probe/catalog failures remain unresolved instead of claiming a stale ID | Pass |
| Bounded, credential-safe diagnostics | Probe start/result, replacement, request outcome, and terminal decision log IDs and decisions only | Pass |
| No timeout-based cancellation | The scoped recovery implementation contains no timeout, polling deadline, or cancellation delay | Pass |
| Android-only release | Tag CI skipped the iOS build/attachment jobs and published only the verified Android APK | Pass |

## Acceptance Comparison

| Acceptance scenario | Evidence | Result |
| --- | --- | --- |
| Exact tablet stale-ID case | Exact `Between Twilight` metadata fixture resolves old to new; source contract repairs logical and Media3 entries in place | Code/test pass; device replay pending |
| Old ID still resolves | `currentServerIdDoesNotLoadTheCatalog` | Pass |
| Unique MusicBrainz match | `uniqueMusicBrainzIdentityWinsBeforeMetadata` | Pass |
| Unique ISRC match | `uniqueIsrcIdentityIsAccepted` | Pass |
| Unique exact metadata match | `uniqueExactMetadataAllowsSmallDurationDrift` | Pass |
| Two matching candidates | `ambiguousStrongIdentityNeverPicksTheFirstCandidate` | Pass |
| Different or incomplete metadata | `fuzzyOrIncompleteMetadataDoesNotReplace` | Pass |
| User changes song during probe | Captured song/index application guard plus stale recovery cancellation policy/source checks | Pass |
| User pauses during probe | Coordinator re-reads pending intent before replacement; retained-intent tests pass | Pass |
| Shuffle enabled | `firstPlayableUpcomingIndexUsesMedia3TraversalOrder` | Pass |
| Missing song, skip disabled | Terminal policy holds and clears recovery | Pass |
| Missing song, skip enabled | Terminal policy advances once to the first playable Media3 index | Pass |
| Download lacks catalog/session | Explicit `MissingCatalogEntry`/`InactiveSession` branches reject immediately | Pass |
| Accepted download completes | Usable path is revalidated before local replacement and retained-position resume | Pass |
| Accepted download is cancelled | `conclusiveQueuedRequestRecordsAnActiveGenerationImmediately` and `acceptedDownloadThatBecameActiveCannotReturnToNotDownloadedForever` | Pass |
| Navidrome unavailable during probe | `serviceFailureRemainsAnOfflineFallbackDecision` and coordinator fallback wiring | Pass |

## Regression Record

- Focused stale identity, queue repair, download lifecycle, diagnostics, and
  duplicate Logs tests pass.
- `:androidApp:compileDebugKotlin` passes.
- The first full host run exposed one new ViewModel decomposition failure. The
  stale resolver factory and queue-state mutation were extracted, reducing the
  ViewModel from 1,211 to 1,197 lines, and the decomposition test then passed.
- The final full host run completed 2,584 tests with the known 74 unrelated
  failures. The count matches the pre-change baseline; no playback, download,
  diagnostics, decomposition, or Settings test failed.

## Deviations and Residual Risk

- The design was tightened during audit: `Enqueued` and `AlreadyActive` now
  establish Active immediately because both outcomes already prove a committed
  `QUEUED`/`DOWNLOADING` generation. Waiting for a duplicate Room emission had
  a race that could otherwise miss activity and recreate the indefinite wait.
- MediaController behavior is covered by pure policies and Android source
  contracts rather than a deterministic real-player integration harness. The
  observed tablet case therefore still needs the planned field test after the
  released APK is installed.
- The host suite remains red only for its existing 74 reader, Aurral, database,
  and Android bitmap-host baseline failures. Those failures are outside this
  release scope.
- Upstream `origin/master` is recorded as an ancestor with an `ours` strategy
  merge because applying the 35-commit navigation/UI rewrite to this fork
  produced incompatible partial architecture and compile failures. The fork's
  working architecture and all release changes are preserved explicitly.

## Release Record

- Tag: `v1.0.11-iota28`, peeled commit `b823561316383cda04b417c6f617eab276b424f9`.
- Public release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-iota28`.
- Tag workflow: GitHub Actions run `30216808994`; Android build, expected
  certificate, reader-vendor governance, and packaged attribution gates passed.
- Android package: `darkaxt.navic`, versionCode `555`, versionName
  `v1.0.11-iota28`, 46,436,880 bytes.
- APK SHA-256: `9f0e82c50a9fe701d1a21f0c2272a7afa3f86ba3ab0b979ea64fe63355f4bd3e`.
- Signing certificate SHA-256:
  `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
- Required commit `9c619f10` and `origin/master` are both ancestors of the
  released commit.
- The iOS build and IPA attachment jobs were skipped; the release contains one
  Android APK and no iOS asset.
