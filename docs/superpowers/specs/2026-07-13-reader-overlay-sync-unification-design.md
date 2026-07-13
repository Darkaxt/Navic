# Reader Overlay Sync Unification Design

**Date:** 2026-07-13

**Finding:** B12

**Target release:** `v1.0.11-iota21` (`versionCode=548`, Android only)

## Problem

Navic has two active reader-to-audio synchronization paths. Whispersync resolves sidecar `WhispersyncSegment` values, while EPUB readaloud resolves SMIL-derived `MediaOverlayClip` values. Their timeline lookup rules differ, but both independently own the same state transitions: sync enablement, active-cue identity, duplicate suppression, overlay clear/apply dispatch, reader-initiated seek loop suppression, and monotonically increasing engine-command keys.

The original audit also suggested that the readaloud path might be unused. Current call-site evidence disproves that: `ReaderReadaloudRuntimeHost.android.kt` owns `ReaderReadaloudSyncState` and drives it from Media3 playback positions and Foliate bridge events. Deleting that path would break EPUB media-overlay playback.

Duplicated transition logic can drift even when both current suites pass. A change to clear behavior, command sequencing, or loop suppression must currently be repeated in both implementations, and the codebase has no contract test proving that the common behavior remains identical.

## Goals

- Define one immutable overlay-sync state and one reducer for behavior shared by both timeline formats.
- Keep timeline lookup and seek-target construction behind typed Whispersync and media-overlay adapters.
- Run the same behavioral contract against both adapters.
- Preserve Whispersync progressive text highlighting, status values, diagnostics, page-boundary pause policy, and repair flow.
- Preserve EPUB readaloud Media3 seek targets and Foliate bridge event handling.
- Preserve command-key monotonicity and suppress playback-reader feedback loops.
- Remove the duplicate readaloud coordinator/state implementation after all production call sites migrate.
- Make no persistence, progress-conflict, cache, ebook-animation, iOS, or UI-string change.

## Current Call-Site Evidence

- `ReaderController` owns the Whispersync session and invokes playback, pause, visible-range, and text-point synchronization.
- `ReaderReadaloudRuntimeHost.android.kt` owns the EPUB readaloud synchronization state and invokes playback-position, sync-toggle, and reader-event synchronization.
- `ReaderWhispersyncSyncCoordinator.kt` also owns Whispersync-specific status and diagnostic behavior that is not duplicated by readaloud.
- `ReaderReadaloudSyncCoordinator.kt` and `ReaderMediaOverlaySync.kt` split the readaloud transition state across two layers and duplicate command sequencing already present in Whispersync.

Both formats are active. The safe change is consolidation, not deletion of either feature.

## Design

### Shared reducer

`ReaderOverlaySync.kt` will own:

- `ReaderOverlaySyncState`: `syncEnabled`, `activeCueKey`, optional `activeProgressTextEnd`, the last `ReaderEngineCommand`, and `engineCommandKey`;
- `ReaderOverlayCue`: stable cue key, overlay fragment, and optional progress marker;
- `ReaderOverlayReaderTarget<T>`: a resolved cue plus the format-specific audio seek target;
- transitions for sync enablement, playback following, reader following, missing-cue clearing, and command publication.

The reducer is format-neutral. It never reads a timeline, builds a Media3 target, emits a Whispersync status, or logs. It publishes `ApplyMediaOverlay` when a new cue becomes active, `UpdateMediaOverlayProgress` only when the active cue's progressive marker changes and the caller enables progressive updates, and `ClearMediaOverlay` only when an active cue is actually cleared. Every published command increments the key exactly once; no-op transitions preserve both the command and key.

Disabling sync clears any active cue and publishes one clear command. Repeated disable, repeated playback within a static cue, and reader events resolving to the already active cue are no-ops. Missing playback cues clear an active overlay, while timeline absence remains a caller-level no-op so a transient unavailable timeline does not erase state.

### Typed timeline adapters

`ReaderOverlayTimelineAdapter<PlaybackInput, ReaderInput, SeekTarget>` defines two translations:

1. playback input to a common overlay cue;
2. reader input to a common cue plus a format-specific seek target.

`WhispersyncOverlaySyncAdapter` uses `WhispersyncTimeline.activeSegment`, `seekTargetForVisibleTextRange`, and `seekTargetForTextPoint`. It creates progressive fragments with playback lead, speed, and next-segment metadata. Existing Whispersync status selection, logging, unmatched-range behavior, and visible-page boundary handling remain in `ReaderWhispersyncSyncCoordinator.kt` and `ReaderController`.

`MediaOverlaySyncAdapter` uses `MediaOverlayTimeline.activeClip` and `seekTargetForText`. It resolves the current `ReadaloudPlaybackPlan` track index and returns `ReadaloudAudioSeekTarget`. Bridge events without a synchronized href return no reader target.

The adapters own stable-key construction for their timeline objects. The reducer treats keys as opaque values.

### Compatibility boundary

`ReaderWhispersyncSyncState` and `ReaderReadaloudSyncState` become source-level aliases of `ReaderOverlaySyncState` so existing public coordinator entry points remain focused and call sites do not acquire generic adapter types. Format-specific coordinator functions construct their adapter, resolve a cue or seek target, and pass it to the reducer.

`ReaderMediaOverlaySyncState` and `ReaderMediaOverlaySyncStep` are removed. Their production owner migrates to `ReaderReadaloudSyncState`, and their behavior moves to the shared reducer plus `MediaOverlaySyncAdapter`. `ReaderReadaloudSyncCoordinator.kt` is removed after the active Android host and tests use the adapter-backed entry points in the media-overlay adapter file.

### Behavioral boundaries

The shared reducer owns only behavior that must be identical:

- enable and disable;
- active cue replacement;
- repeated-cue suppression;
- optional same-cue progress update;
- clear-on-missing playback cue;
- reader-seek feedback suppression;
- command and command-key publication.

Format owners retain behavior that is intentionally different:

- Whispersync status labels and mismatch/repair state;
- Whispersync logging and page-boundary pause;
- visible-range versus text-point matching rules;
- SMIL href matching and Media3 track lookup;
- Whispersync progressive character highlighting.

## Alternatives Considered

### Delete the readaloud synchronization path

Rejected. Current Android production code actively uses it for EPUB media-overlay playback and reader-initiated audio seeking.

### Make the entire coordinator generic

Rejected. Status, diagnostics, page-boundary policy, reader event shapes, and seek-target types differ materially. A generic coordinator would spread type parameters and callbacks through `ReaderController` and Compose while hiding rather than removing complexity.

### Share only helper functions

Rejected. Sharing key builders or a clear helper would leave two independent state models and command-key reducers, which is the drift risk B12 requires removing.

## Validation Contract

The implementation is test-first. A new contract suite must fail before production changes because the shared state and adapters do not yet exist.

The same contract scenarios run against both real timeline adapters:

1. first playback cue publishes one apply command;
2. repeated playback inside the same cue preserves the command key;
3. transition to the next cue publishes a new apply command;
4. playback outside any cue publishes one clear command;
5. disabling with an active cue clears once and suppresses later playback;
6. reader navigation to a different cue returns a seek target and apply command;
7. repeating the same reader navigation returns no seek target and preserves the command key.

Separate Whispersync tests retain progressive character updates, lead handling, statuses, diagnostics, page gaps, visible-range matching, and text-point behavior. Separate readaloud tests retain bridge-event href extraction and Media3 track-index lookup.

Source guards prove that production has one overlay state declaration and one command-key increment site, both production paths instantiate their adapters, and removed duplicate state/coordinator symbols have no call sites. Existing reader controller, runtime host, Whispersync, media-overlay, readaloud, and parser suites remain green. Android debug assembly, governance checks, signed public APK, and in-place ADB upgrade complete the staged release proof. All iOS tasks remain skipped.

## Rollout And Rollback

The change is stateless and does not alter persisted reader or playback data. Rollout uses the normal Android `iota##` release sequence as `iota21` after focused tests, adjacent owner tests, assembly/governance gates, and device smoke validation.

Rollback is a forward Android release restoring the prior coordinators. No data migration or cache cleanup is required.
