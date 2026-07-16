# Android Playback Stability Design

**Date:** 2026-07-16
**Release target:** `v1.0.11-iota23`, `versionCode=550`
**Platform:** Android only

## Goal

Navic must keep one authoritative playback intent while the Now Playing pager,
Media3, connectivity, and the download pipeline update asynchronously. A visual
pager synchronization must never become a playback command, and a recoverable
remote-source failure must not silently pause, destroy, reorder, or rapidly
scan through the queue.

## Observed Evidence

ADB evidence from the production phone showed:

- `darkaxt.navic` `v1.0.11-iota22` remained alive with a 24-item queue.
- The active queue item remained at index 17; the queue was not cancelled.
- No crash, process death, audio-focus loss, or noisy-route callback caused the
  final stop.
- At `17:40:38`, Navic emitted a user-requested play transition followed about
  2 ms later by a user-requested pause for the same song and queue index.
- `NowPlayingArtworkPager` reacted to every settled pager page, including
  programmatic synchronization, called `playAt(page)`, and then called
  `pause()` from stale captured state.
- Earlier gaps involved uncached songs and were not retained because issue
  logging was disabled. The current recovery coordinator refreshes a remote URI
  once and then pauses or skips; it does not wait for the same song's download.

## Invariants

1. Only a completed user drag may select a song from the artwork pager.
2. Programmatic pager synchronization emits no playback command.
3. A queue selection is one command carrying the desired final
   `playWhenReady` value. It must never be implemented as play followed by
   pause.
4. The queue order and current index remain stable during source recovery.
5. Recovery retains the user's playback intent independently from transient
   `isPlaying` and buffering state.
6. A remote source gets one fresh in-place stream URI attempt.
7. If that retry fails, Navic requests the same song through the existing
   download pipeline and observes download state asynchronously.
8. A completed download replaces the current source in place and resumes only
   when the retained intent still says play.
9. Explicit pause, queue selection, next, previous, or queue clearing prevents
   a stale recovery from auto-resuming an old item.
10. Optional skip-on-error applies only after terminal recovery failure. It
    selects at most one next playable item per failed recovery.
11. Playback diagnostics are retained in the bounded issue-log ring even when
    general issue logging is disabled. Explicit log clearing still clears them.
12. Recovery uses state changes, not cancellation timeouts.

## Considered Approaches

### A. Restore the historical pager drag flag

This prevents many programmatic scrolls from selecting tracks, but it leaves
the `playAt()` followed by `pause()` race and stale closure reads intact.
Rejected because it treats only one symptom.

### B. Atomic selection plus gesture ownership and in-place recovery

Introduce an explicit queue-selection command with final playback intent,
isolate user drag settlement from programmatic pager motion, and model source
recovery as one pending current-item operation. The existing download flow
becomes the asynchronous completion source.

This is the selected approach because it fixes command ownership without
changing the queue model or download architecture.

### C. Move pager, queue, and recovery ownership into a new playback engine

This could produce a stronger long-term boundary, but it would replace several
stable Media3 and multiplatform contracts at once. Rejected for this release
because the blast radius is not justified by the confirmed failures.

## Architecture

### Pager Intent Tracker

A small common Kotlin state machine owns whether a user drag is armed. Drag
start arms it; the next settled page consumes it. A settlement produces a
selection only when the page is valid and differs from the latest current
index. Programmatic animations never arm the tracker.

Compose reads the latest queue index and pause state when settlement occurs. It
does not rely on values captured when a long-lived `LaunchedEffect` started.

### Atomic Queue Selection

`MediaPlayerViewModel` exposes a queue-selection operation containing:

- target index
- final `playWhenReady`
- command origin

Existing `playAt(index)` remains the normal play-oriented wrapper. Android
seeks once, applies the final playback intent once, and records the origin.
Pending controller work stores the complete selection rather than only an
index.

The iOS implementation receives only the compile-compatible contract update;
no iOS behavior, build, validation, or release is in scope.

### Current-Item Recovery

`AndroidStablePlaybackRecoveryCoordinator` owns one pending recovery:

- song id and queue index
- playback position
- retained `shouldResume`
- failure reason and original error
- whether the fresh remote source was already attempted

The recovery flow is:

1. Use an already downloaded local file immediately when available.
2. Refresh the current remote media item once and retry in place.
3. On a second failure, pause the broken source, retain intent, queue the same
   song for download, and keep recovery UI active.
4. Observe `DownloadManager.allDownloads`.
5. On `DOWNLOADED`, replace the same current media item with the local file,
   seek to the saved position, prepare, and conditionally resume.
6. On `FAILED`, notify the user. If skip-on-error is enabled, select one next
   playable item. Otherwise hold the failed current item without queue changes.

An unavailable automatic transition enters the same pending recovery directly.
It does not recursively skip unavailable entries.

### Intent Cancellation

The coordinator exposes explicit user-intent hooks:

- pause changes pending `shouldResume` to false
- resume changes it to true but waits for the recoverable source
- selecting another item clears the old pending recovery
- next, previous, clear queue, and collection replacement clear it
- a Media3 transition to a different song clears stale recovery

This prevents late download completion from reviving an item the user left.

### Durable Diagnostics

`PlaybackDiagnostics` remains a structured tag. `AppLogManager` persists this
tag even when general issue logging is disabled, while all other tags preserve
the existing opt-in behavior. Disabling general logging removes non-playback
entries but retains the bounded playback history. Explicit clear removes all
entries.

New command/recovery events include:

- `queue-selection`
- `pager-settlement-ignored`
- `recovery-pending`
- `recovery-download-status`
- `recovery-local-file-ready`
- `recovery-terminal-failure`
- `recovery-cleared`

No stream URI, credentials, or headers are logged.

## Error Handling

- A stale download completion is ignored if song id or queue index no longer
  matches.
- A missing queue item cancels recovery rather than seeking an invented index.
- A failed download is terminal for that recovery generation.
- A paused user intent never auto-resumes.
- A pending recovery keeps loading/progress state visible for queued or active
  downloads and clears it on success, terminal failure, or cancellation.
- Queue shuffle order is not recalculated by recovery.

## Testing

Common tests cover:

- user drag settlement emits exactly one selection
- programmatic settlement emits none
- latest paused state is preserved in the selection request
- playback diagnostics persistence policy
- terminal recovery target selection

Android host/source tests cover:

- pager wiring uses drag ownership and atomic selection
- Android selection never performs play then pause
- pending selection stores final playback intent
- recovery requests the current song download
- recovery never moves queue items
- download completion replaces/resumes the current item in place
- explicit user commands cancel or update pending recovery
- diagnostics include command origin and pending recovery state

The release gate includes focused tests, the full Android host suite, debug APK
assembly, Android lint/compile checks, APK metadata/signature verification,
ADB installation of the signed public APK, and final GitHub release validation.

## Staged Deployment

1. **Stage 1, command ownership:** pager tracker and atomic queue selection.
2. **Stage 2, recovery:** current-item download wait and terminal policy.
3. **Stage 3, observability:** always-retained bounded playback diagnostics.
4. **Stage 4, candidate:** bump only Android metadata to `iota23`, build, and
   install a non-public candidate where signature compatibility permits.
5. **Stage 5, public:** sync latest `fork/master`, prove ancestry from
   `9c619f10`, fast-forward master, publish the Android-only release, download
   the public APK, verify it independently, and install it on the phone.

Rollback is commit-granular. A public rollback may point users back to iota22;
no database schema or queue migration is introduced.

## Acceptance Criteria

- Opening Now Playing or programmatically changing tracks cannot generate a
  play-then-pause pair.
- Swiping artwork while playing selects once and keeps playing.
- Swiping artwork while paused selects once and remains paused.
- An uncached source failure retains the current queue entry and starts
  asynchronous download recovery.
- Download completion resumes the same item only when the user still intends
  playback.
- Recovery does not reorder or repeatedly scan the queue.
- Terminal failure either holds visibly or advances once according to the
  existing skip-on-error setting.
- Playback diagnostics survive with general issue logging disabled and remain
  bounded.
- The public APK is `v1.0.11-iota23`, `versionCode=550`, Android-only, and its
  release commit descends from `9c619f10`.
