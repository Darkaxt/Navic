# Offline Queued Playback Fallback Validation

**Date:** 2026-08-17

**Design:** `docs/superpowers/specs/2026-08-17-offline-queued-playback-fallback-design.md`

**Baseline:** `v1.0.11-iota59` (`b2d2b9194`)

**Implementation:** `43aa4bc60`

## Red-Green Evidence

The first focused baseline run executed 23 tests. The two new Android contracts
failed because effective-connectivity recovery wiring and offline progress
suppression were absent. The complete-traversal policy test passed against the
existing resolver.

After the first implementation, those 23 tests passed. A second pure-policy RED
run then failed to compile because `OfflinePlaybackRecoveryRoute` and
`resolvePlaybackRecoveryConnectivity()` did not yet exist. After adding the
policy and integrating it, the selected policy and Android source tests passed.

A final UI RED run failed because offline hold still set `isLoading`. After the
offline-hold presentation change, that test passed and the play control no
longer presents either determinate or indeterminate download/loading progress
while waiting for service.

## Focused Verification

The final focused recovery run covered 70 tests across 11 suites:

- offline fallback and connectivity routing;
- pending download recovery;
- stale-song policy and resolution;
- offline-mode coordination and Navidrome availability;
- offline-aware download queue ownership;
- Android player source contracts;
- playback diagnostics and offline notification contracts.

Result: 70 tests, zero failures, zero errors, zero skipped.

## Broad Verification

The first unprepared broad run executed 3,552 tests and reported one failure:
`ReaderDevEnvironmentContractTest.readerQaFaultReceiverExistsOnlyInReaderDevMergedManifest`.
The assertion explicitly required generated readerDev, debug, and release merged
manifests. It was unrelated to music playback or changed source.

The repository's neutral manifest prerequisite was then run:

```powershell
.\gradlew.bat -I scripts/reader-qa-manifest-check.init.gradle readerQaProcessVariantManifests
```

The isolated failing test passed afterward. A fresh complete
`:composeApp:testAndroidHostTest` run then passed all 3,552 tests.

## Design Comparison

| Requirement | Implementation evidence | Result |
| --- | --- | --- |
| Offline remote recovery cannot start network work | Effective connectivity gate runs before stale probe and download recovery | Pass |
| Connectivity loss during pending download cannot remain `Wait` | Download snapshots hand pending remote recovery to offline fallback before policy resolution | Pass |
| Race immediately before persistent request is closed | Connectivity is checked both before pending setup and inside the request coroutine | Pass |
| Local-file failures remain item failures | Pure route policy returns `ContinueRecovery` for local files regardless of effective connectivity | Pass |
| Online download-and-resume remains available | Pure route policy returns `ContinueRecovery` for online remote files; existing recovery policy is unchanged | Pass |
| Cached selection uses complete Media3 order | Existing `upcomingIndexes` resolver is unchanged; regression candidate is beyond five visible entries | Pass |
| Queue is retained and ordered | No queue or Media3 move/remove operation is introduced | Pass |
| User pause remains authoritative | `PendingPlaybackRecovery.shouldResume` and pause handling are unchanged | Pass |
| No timeout or retry cancellation | No delay, timeout, cancellation policy, or fixed retry is added | Pass |
| Offline hold is not presented as a download | Effective offline state clears progress and service-wait state clears `isLoading` | Pass |
| Durable download intent is preserved | Playback recovery state is cleared or replaced; `DownloadManager` rows are not cancelled or removed | Pass |

## Release Boundary

Local production signing credentials are intentionally unavailable. The public
tag workflow is therefore the only accepted release artifact path: it runs the
guarded signed package task, verifies the expected certificate, uploads the APK,
and creates the GitHub release.

Physical-device flight-mode acceptance remains pending because no device is
currently connected. The user will test the published release later; host tests
prove arbitration and invariants but do not claim a real roaming reproduction.
