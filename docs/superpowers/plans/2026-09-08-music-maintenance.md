# Music Maintenance Implementation Plan

Authoritative specification: `../specs/2026-09-08-music-maintenance.md`.
Authorization: `already_authorized`. Baseline: iota67 / `e1668fad`.
Status: complete, host-verified. Branch: `fix/music-maintenance-qa`.

## Stages

| Stage | Requirements | Work and dependencies | Required evidence | Status |
| --- | --- | --- | --- | --- |
| S1 Download correctness | R1, R2, R9 | Serialize admission/recovery, attempt-owned files, coherent recovery row validation, owner-scoped registry and non-destructive migration | Deterministic ownership/migration/recovery tests, Android compilation | Passed focused and final integrated verification; reconciled with specification |
| S2 Playback intent | R3, R4, R8 | Unify supersession, invalidate service automatic resume, separate saved shuffle traversal; independent of S1 implementation except final integration | Coroutine/event sequence and persistence round-trip tests | Passed component and final integrated verification; reconciled with specification |
| S3 Enrichment state | R5, R6, R7 | Preserve lyrics failure semantics, finish blocked section state, observe confirmed monitoring; independent of S1/S2 | Repository/state-transition tests and existing regressions | Passed component and final integrated verification; reconciled with specification |
| S4 Integrated gate | R1-R10 | Integrate branches/components, add CI music selection, resolve size guard, compare full implementation to specification | Fresh selected suite, diff/source review, migration review, zero blockers/deferrals | Passed: 1,048 tests, 196 suites, zero failures/errors/skips; full specification reconciliation complete |

Independent implementation within a stage may proceed in parallel, but no stage
is passed until its tests and specification comparison are recorded. The main
reviewer owns cross-component decisions and final integration. No deployment or
release is part of this plan.

## Reconciliation Ledger

| Requirement | Evidence | Remaining gap | Classification |
| --- | --- | --- | --- |
| R1 | DownloadWorkCoordinatorTest (3), DownloadWorkerLifecycleTest (2), AudioDownloadPublicationTest (7), actual DAO generation/deletion tests; admission and cancellation call sites reviewed | None | satisfied |
| R2 | DownloadedAudioSnapshotTest (4), existing recovery regressions, reviewed same-row path resolution in Android recovery coordinator | None | satisfied |
| R3 | PlaybackPreparationCoordinatorTest (3), Android wiring guards, account lease and initial-restore invalidation review | None | satisfied |
| R4 | PlaybackAutoResumeIntentTest (3), AndroidPlaybackAutoResumeCoordinatorTest (5); secondary controller disconnect does not cancel valid resume | None | satisfied |
| R5 | LyricsFailureRecoveryTest (6), LyricsHttpFailureTest (3), VisualEnrichmentRequestOwnershipTest (5); both lyric surfaces defer and resume owned work | None | satisfied |
| R6 | AurralCoreFailureStateTest (2), shared visual-enrichment ownership tests, core failure and atomic sibling publication wiring guards | None | satisfied |
| R7 | AurralArtistMonitoringStateTest (5), reviewed authoritative confirmation-queue collection and duplicate-submit prevention | None | satisfied |
| R8 | PlaybackShuffleRestorePolicyTest (4), PlayerPersistencePolicyTest (7), AndroidPlaybackQueueTraversalTest (1); full traversal survives repeat-one projection | None | satisfied |
| R9 | DownloadOwnershipMigrationTest (9), AccountDownloadRegistryTest (15), PlaybackAccountBoundaryTest (2), AndroidPlaybackAccountBindingTest (1); schema 6, scoped views, real ExoPlayer revocation and retained bytes | None | satisfied |
| R10 | Android production and host compilation successful; shared CI/local gate passes all 1,048 selected tests; ViewModel 1,180 lines; unchanged size guard and no reader/version changes | None | satisfied |

Final reconciliation: `blockers = 0`, `tracked_deferrals = 0`. Every requirement
was reread against the authoritative specification after integration. No required
behavior was removed or deferred. Test counts in individual rows can overlap;
the integrated total below counts each test once.

## Final Verification

Executed on Windows in `D:\Temp\navic-music-maintenance-20260908`:

```powershell
$env:ANDROID_HOME='C:\Users\darka\AppData\Local\Android\Sdk'
$env:TEMP='D:\Temp'
$env:TMP='D:\Temp'
.\gradlew.bat :composeApp:testAndroidHostTest '-Pnavic.musicTests=true' --console=plain --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process'
```

- Final run: `BUILD SUCCESSFUL`, 39 seconds, 196 XML suites, 1,048 tests,
  zero failures, errors, or skipped tests. This was an executed test task, not an
  up-to-date test result. Production and host compilation had succeeded on the
  same final source and were reused by this run.
- Immediately preceding focused run: all 24 account/SQLite migration tests passed.
- Earlier integrated runs exposed stale source-contract assertions, a JUnit
  non-void test method, and Windows SQLite fixture deficiencies. They were fixed
  and rerun; no failing test was disabled to obtain the final result.
- CI runs the same Gradle music selection before packaging and uploads its reports.
  CI was not triggered during this task. No push, release, APK installation, or
  device test was performed. This evidence is host verification, not live-device
  proof or a claim that the entire reader/audiobook suite passes.
- The active `feat/page-turn-animation` checkout is separate and untouched by the
  maintenance implementation. Build outputs are disposable; this verification
  record and the schema-6 migration artifact are version-controlled.

## Integration Review Corrections

- Keep successful database write acknowledgments across logout; hide stale reads,
  not committed writes. Delete registered rows before their attempt-owned bytes.
- Failed cleanup checks `FAILED`; snapshot deletion additionally checks status and
  path, protecting a new transfer even after a row is deleted and recreated.
- Register download jobs at claim time, including the pending catalog lookup.
- Ignore unrelated controller disconnects when deciding automatic resume.
- Cancel hidden lyrics-provider fallback without consuming future retry demand.
- Give Aurral refreshes ownership and atomically rebase sibling section patches.
- Account changes must also revoke already-resolved player URIs and old restores.
  The current queue is cleared on account change; retained downloads are not.
- Room host tests use Robolectric's Android SQLite backend, not the Android-only
  bundled JNI loader. The existing migration-only JDBC fixtures construct their
  driver directly so execution does not depend on test-order registration.
