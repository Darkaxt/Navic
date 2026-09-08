# Navic Music Maintenance QA

Implementation follow-up: all nine findings below have been addressed in
`fix/music-maintenance-qa`. The authoritative specification is
`../specs/2026-09-08-music-maintenance.md`; the paired
`../plans/2026-09-08-music-maintenance.md` records requirement-by-requirement
reconciliation and the passing 1,048-test music gate. The assessment below is
preserved as baseline evidence; its locations refer to the assessed revision.

Date: 2026-09-08

Reviewed revision: `e1668fad37055ac73fe13b41bfe1014defe7c0cf`, remote
`Darkaxt/Navic:master` and released `v1.0.11-iota67` at inspection time.

Scope: Android music only. Three read-only agents reviewed playback/queue,
downloads/offline recovery, and music UI/enrichment. The coordinating reviewer
checked the findings against the source and ran the focused host tests. Active
ebook work was not included or modified. No device operations, installs, live
server tests, fixes, commits, or releases were performed for this analysis.

The file references below are relative to the reviewed revision, not necessarily
the checkout containing this report. Source:
[iota67 snapshot](https://github.com/Darkaxt/Navic/tree/e1668fad37055ac73fe13b41bfe1014defe7c0cf).

## Findings

These are source-established defects with explicit triggering conditions, not
proof that every condition has occurred on the user's device. P1 is high priority;
P2 is normal priority. The database race also has the limited executable evidence
recorded below. The other runtime interleavings have not been device-reproduced.

### M1 - P1: Reconnection can admit two writers for one download

- Location: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt:136-138`, `377-379`, `509-519`, `538-548`, `721-726`.
- Supporting DAO: `composeApp/src/commonMain/kotlin/paige/navic/data/database/dao/DownloadDao.kt:90-97`, `124-138`.
- Trigger: queued work resumes with effective connectivity and more than one download worker. A worker claims a row before the connectivity observer runs queue recovery. This is possible because the worker and observer independently consume the online transition.
- Cause: recovery resets all `DOWNLOADING` rows to `QUEUED` without changing generation or excluding live workers. Slot admission checks capacity, not whether that song already owns a slot. A second worker can claim the same generation, and both use the same final audio path.
- Impact: after one completion changes the row to `DOWNLOADED`, the other completion fails its conditional update and deletes the shared file. The database can report a downloaded song whose audio is missing. Concurrent writes can also damage the shared output.
- Recommendation: make startup recovery and live worker admission mutually consistent. Do not globally reset live claims on reconnect. Enforce one owner per song and publish attempt-owned temporary output only after an ownership-checked completion; stale workers must never delete another worker's committed output.
- Regression needed: deterministic reconnect/admission interleaving with multiple workers, checking one writer, a valid final file, and matching database state.

### M2 - P2: Recovery consumes download status and file availability from different snapshots

- Location: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidStablePlaybackRecoveryCoordinator.android.kt:433-439`.
- Supporting paths: `AndroidMediaPlayerViewModel.android.kt:518-546` in the same directory; `domain/manager/DownloadManager.kt:118-129,157-159` and `domain/models/PlaybackQueueRecoveryPolicy.kt:75-94` under commonMain.
- Trigger: the playback collector receives a completed database row before DownloadManager's separate collector updates its downloaded-file map.
- Cause: recovery reads status from the supplied database snapshot but resolves the path through that independently updated map. `DOWNLOADED` plus a missing path is classified as terminal failure. The map's later update is not an input to this recovery collector.
- Impact: a successfully downloaded recovery song can be skipped or left paused. Recovery is cleared before the matching file map arrives.
- Recommendation: derive status and validated file availability from one authoritative snapshot. Preserve the real missing-file failure case without conflating it with delayed observation of a separate map.
- Regression needed: deliberately deliver the completed row before the derived map and prove that playback resumes exactly once.

### M3 - P2: Non-bulk startup jobs can override later user commands

- Location: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt:927-956` and `985-1026`; cancellation callers at `877-893`, `1064-1079`.
- Trigger: select a row in a large collection, or start song radio, then pause, clear the queue, or make a newer selection before preparation finishes. Song radio has a network suspension; collection preparation maps the entire list off the main thread.
- Cause: these methods create untracked ViewModel jobs. Later commands cancel the bulk coordinator, not these jobs. The old jobs can still replace the queue and call `play()`.
- Impact: obsolete work replaces a newer queue or resumes playback after an explicit pause/clear.
- Recommendation: apply one request-ownership and supersession contract to all queue-replacing startup paths, including row selection and song radio, not only bulk Play/Shuffle.
- Regression needed: suspend preparation, issue a newer command, then complete the old work and verify no stale queue or playback mutation.

### M4 - P2: Service auto-resume retains obsolete intent

- Location: `composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt:483-497` and `539-562`.
- Inter-song-gap trigger: while a configured gap is pending, manually resume and then pause again before the original gap expires. The delayed job survives and calls `play()` if the index still matches. Queue identity and subsequent user intent are not checked.
- Volume-zero trigger: enable volume-zero pause, lower volume to zero, manually resume and pause while still muted, then raise volume. The `pausedByZeroVolume` latch is not invalidated by those user commands, so raising volume resumes playback.
- Impact: optional automatic-resume behavior overrides a later explicit pause.
- Recommendation: represent each automatic resume as an owned intent invalidated by subsequent explicit playback commands, queue replacement, or session change. Keep the configured inter-song delay; a timeout is not needed to resolve ownership.
- Regression needed: event-sequence tests for both paths, including a later pause and replacement at the same queue index.

### M5 - P2: Lyrics transport errors become successful absence

- Location: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/LyricsRepository.kt:142-148`.
- Consumer: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/viewmodels/NowPlayingViewModel.kt:91-104,275-303`.
- Trigger: all configured providers fail for an uncached song, then connectivity recovers while the same song remains selected.
- Cause: provider exceptions are swallowed and the repository returns the absent cached result. The UI records `Success(false)` instead of a retryable error. Resume invalidates failed lookup identity only for `UiState.Error`, so this false absence does not receive the intended resume retry. Backward seeking or changing song can retrigger lookup.
- Impact: lyrics stay unavailable after connectivity returns; a lyrics panel receiving successful absence can dismiss itself.
- Recommendation: distinguish a successful empty provider result from transport/service failure. Retain valid cached lyrics when available and retry unresolved failures on appropriate lifecycle/connectivity events.
- Regression needed: provider transport failure followed by same-song screen resume and successful provider response. This differs from a request that was never started while the screen was inactive.

### M6 - P2: Failed Aurral core lookup leaves dependent sections loading

- Location: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt:421-426,554-570,668-721`.
- Trigger: open an artist without cached sections and fail the available core-enrichment identity lookups.
- Cause: all section-loading flags are enabled, but the core failure branch clears only overall/profile loading and returns before requests, previews, and similar-artist jobs are launched.
- Impact: those sections can display persistent loading placeholders even though no work remains running.
- Recommendation: finish each section's state explicitly when its prerequisite fails, retaining cached data and exposing an appropriate unavailable/retry state. Do not simply propagate one error indiscriminately to independent sections.
- Regression needed: fail the core lookup and assert that every section without an active job has left loading state.

### M7 - P2: Aurral monitoring confirmation does not update the discovered-artist action

- Location: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralArtistScreen.kt:349-369,603-609`.
- Trigger: finish loading an initially unmonitored discovered artist, monitor it successfully, and remain on that page through confirmation.
- Cause: the screen observes queue `Pending` but retains its original `monitorConfirmed` value. Mutation success only clears an error; the loading effect is not keyed to repository artist-state revisions or confirmed status.
- Impact: the action returns to its crossed, enabled appearance after confirmation, suggesting monitoring is off and allowing another submission. This is a client-state defect, not evidence of a failed server monitoring operation.
- Recommendation: derive confirmed monitoring from authoritative observed state as the local artist-detail path does. Keep acceptance/pending separate from confirmed success.
- Regression needed: render the transition from unmonitored to pending to confirmed without leaving the screen.

### M8 - P2: Repeat-one discards the shuffle traversal needed for restoration

- Location: `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt:647-655,668-672`.
- Consumers: `AndroidPlaybackStateSynchronizer.android.kt:71-79` in the same directory; `composeApp/src/commonMain/kotlin/paige/navic/domain/models/PlaybackShuffleRestorePolicy.kt:8-10`.
- Trigger: use shuffle on a multi-song queue, enable repeat-one, persist/restart the player, then disable repeat-one.
- Cause: repeat-one publishes only the current index as `upcomingIndexes`, overwriting the previous traversal in persisted state. The restore policy rejects a traversal containing the current index and falls back to the replacement player's shuffle order.
- Impact: the established order is lost across restart; subsequent songs can differ from the previously queued traversal.
- Recommendation: persist the underlying shuffle order independently of the repeat-one Up Next projection.
- Regression needed: an Android-produced repeat-one state round-trip, then leaving repeat-one and checking the original traversal.

### M9 - P2: Download ownership is not scoped to account/server

- Location: `composeApp/src/commonMain/kotlin/paige/navic/data/database/entities/DownloadEntity.kt:13-19`.
- Supporting paths: `composeApp/src/androidMain/kotlin/paige/navic/di/DatabaseModule.android.kt:14`; `AndroidMediaItemFactory.android.kt:75-80` under androidMain/shared; commonMain `ui/screens/login/viewmodels/LoginViewModel.kt:124-128` and `domain/repositories/DbRepository.kt:122-130`.
- Trigger: retain a download from account/server A, log out, and log into B with a colliding song ID.
- Cause: download rows and paths survive logout in a global database and are looked up solely by song ID. Session job shutdown does not namespace retained files.
- Impact: B's song can select A's audio file. A cross-catalog ID collision is required; no collision in the user's live catalogs was verified.
- Recommendation: associate downloads and lookup keys with a stable server/account owner. Migrate legacy files conservatively without silently assigning them to a different account or deleting valid user downloads.
- Regression needed: two accounts with the same song ID and different bytes, including logout, restart, and account return.

## Verification

Fresh command on the immutable snapshot:

```powershell
$env:ANDROID_HOME = 'C:\Users\darka\AppData\Local\Android\Sdk'
.\gradlew.bat :composeApp:testAndroidHostTest `
  --tests '*Playback*Test' --tests '*Queue*Test' --tests '*Player*Test' `
  --tests '*Offline*Test' --tests '*Download*Test' --tests '*Lyrics*Test' `
  --tests '*LidaClip*Test' --tests '*Aurral*Test' --tests '*NowPlaying*Test' `
  --tests '*Playlist*Test' --tests '*Genre*Test' --tests '*VisualEnrichment*Test' `
  --console=plain --no-daemon --max-workers=2 `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

- 869 tests in 152 suites: 868 passed, 1 failed, 0 errors, 0 skipped.
- Of those, 793 tests in 144 suites were outside reader/audiobook-named suites: 792 passed, 1 failed. The name filters also matched 76 tests in 8 reader/audiobook-named suites; all 76 passed. This was not the full repository suite.
- Failure: `AndroidMediaPlayerDecompositionSourceTest.viewModelCoordinatesExtractedPlaybackResponsibilities`, line 13. It asserts that the Android player ViewModel remains below 1,200 lines; the file has 1,229 physical lines. This is an architecture/size guard, not a reproduced playback failure. It should not be counted as a tenth runtime defect.
- Android main and host compilation were satisfied from the Gradle build cache; test execution was fresh.
- An initial invocation failed before tests because PowerShell split an unquoted dotted `-P` argument. The corrected command above produced the reported result.
- No physical-device, UI-rendering, mobile latency, audio-focus, or live-network reproduction was performed.

For M1, the exact `DownloadDao` SQL strings were extracted from source and executed
against an in-memory SQLite database with a single queued song. Observed affected
row counts:

| Ordered operation | Rows affected |
| --- | ---: |
| First worker claims generation 1 | 1 |
| Online recovery resets live claim | 1 |
| Second worker claims generation 1 | 1 |
| First worker completes | 1 |
| Second worker completes | 0 |

The final row remained `DOWNLOADED` with generation 1 and the shared file path.
The production completion branch deletes that path when the affected count is
not 1. The SQL exercise proves the duplicate-claim/completion interleaving; it
does not simulate Android scheduling or perform real file deletion.

## Test Coverage and Priority

The checked-in release workflow packages the APK but does not invoke
`:composeApp:testAndroidHostTest` (`.github/workflows/build.yml:83,118,233`).
Several relevant Android checks assert source text, rather than executing the
service/collector transition. Examples include `OfflineAwareDownloadQueueSourceTest`
and `VisualEnrichmentSourceTest`. Pure policy tests are useful but do not prove
that production callers deliver coherent snapshots or invalidate old jobs.

Recommended order for a separately authorized implementation:

1. M1 and M2: download ownership and coherent recovery state; deterministic concurrency tests.
2. M3 and M4: latest user intent wins across startup and automatic resume.
3. M5: preserve retryable lyrics failure semantics through screen resume.
4. M6 and M7: terminate failed section loading and observe monitoring confirmation.
5. M8 and M9: shuffle persistence and retained-download account isolation. Move M9 earlier if multi-account/server switching is used.

Add a maintained music regression gate to CI alongside those fixes, and resolve
the ViewModel size guard deliberately. Do not relax assertions merely to make the
suite green. Historical broad-suite failures were not treated as evidence for
any finding in this report.
