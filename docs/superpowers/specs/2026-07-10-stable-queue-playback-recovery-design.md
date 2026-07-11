# Stable Queue and Playback Recovery Design

**Status:** Approved direction, staged delivery

**Platform scope:** Android only. No iOS work is included.

**First release scope:** Stage 1 containment for mobile-data live testing.

## Problem Statement

Navic currently treats playback preparation, durable offline downloading, and queue recovery as one pipeline. This creates a failure loop on constrained or changing networks:

1. The visible Up Next window is passed to `DownloadManager.prefetchPlaybackSongs`.
2. `prefetchPlaybackSongs` uses the same persistent queue as an explicit offline download.
3. The current stream competes with several full-song downloads.
4. A player error is converted into another persistent download.
5. Recovery advances playback and later moves completed downloads beside the current item.
6. Queue and Up Next projections change as background work completes.

The `skipMediaOnError` preference remains exposed but the production recovery path no longer consults it. Availability is also represented by one broad predicate, `online || downloaded`, which cannot express whether a local file is verified, a remote source is usable, or the active network policy permits streaming.

Retained Android MediaSession history showed multiple transitions lasting only milliseconds. This supports the cascade diagnosis, but the mobile-data behavior still requires a fresh field acceptance run on the released build.

## Goals

- Keep logical queue order stable during source resolution, playback failure, downloads, and download completion.
- Stream uncached songs directly when the active network policy permits it.
- Keep persistent offline downloads explicit and user-owned.
- Resolve current and upcoming source state concurrently and publish each result as soon as it is known.
- Preserve user playback intent independently from Media3 buffering and readiness.
- Refresh or replace a failed source before considering advancement.
- Honor `skipMediaOnError`; never advance because a background download completed.
- Give Queue and Up Next one canonical state source.
- Produce diagnostics that explain each source decision without logging credentials or signed URLs.

## Non-Goals

- iOS support.
- Replacing all existing download storage in the first release.
- Automatically deleting a user's offline download after a decoder or filesystem error.
- Treating a WiFi smoke test as proof of mobile-data behavior.
- Adding cancellation timeouts. Connectivity recovery is event-driven.

## Required Invariants

1. Queue order changes only through an explicit user command or a declared auto-fill append.
2. Source availability updates never call `moveMediaItem`.
3. Up Next display count never controls audio download count.
4. A playback error never starts a persistent offline download.
5. Persistent offline downloads retain an explicit user reason.
6. User pause always wins over source refresh, connectivity restoration, and download completion.
7. A queue entry is identified by a stable `queueEntryId`, not only by song ID or array index.
8. Media3 timeline state is derived from canonical queue state and is not edited independently by background jobs.

## Target Architecture

### Canonical Queue Store

`QueueStore` owns an immutable ordered list of queue entries:

```kotlin
data class QueueEntry(
	val queueEntryId: String,
	val song: DomainSong,
	val sourceState: SourceState
)
```

`queueEntryId` distinguishes duplicate occurrences of the same song. Queue commands are serialized through a reducer. Media3 receives timeline updates from reducer effects, while UI state is projected from the same reducer state.

### Playback Intent and Engine State

Playback intent is independent from engine state:

```kotlin
enum class PlaybackIntent { PlayRequested, PausedByUser, Stopped }

sealed interface EngineState {
	data object Idle : EngineState
	data object Resolving : EngineState
	data object Buffering : EngineState
	data object Ready : EngineState
	data object Ended : EngineState
	data class Failed(val category: PlaybackFailureCategory) : EngineState
}
```

`Player.isPlaying == false` must not be persisted or displayed as a user pause when the engine is buffering, resolving, suppressed, or recovering.

### Source Resolution

`SourceResolver` resolves the current entry and a bounded upcoming window concurrently:

```kotlin
sealed interface SourceState {
	data class LocalVerified(val path: String) : SourceState
	data class RemoteReady(val generation: Long) : SourceState
	data object WaitingForNetwork : SourceState
	data object BlockedByNetworkPolicy : SourceState
	data class FailedPermanent(val reason: String) : SourceState
}
```

Results are first-resolved, first-served for state publication: each entry updates immediately when its own resolution completes. Playback order remains the canonical order. A later queue entry resolving first does not move ahead of an earlier entry.

### Error Decisions

| Source/error | First action | Fallback | Queue effect |
| --- | --- | --- | --- |
| Verified local source missing before prepare | Mark local invalid for playback session | Resolve remote if policy permits | None |
| Local decoder/read error | Report local failure | Pause, or advance only when skip-on-error is enabled | None |
| Remote 401/403 or stale URL | Refresh source generation once | Classify final error | None |
| Remote server/source failure | Report typed failure | Pause, or advance only when skip-on-error is enabled | None |
| Connectivity lost | Enter `WaitingForNetwork` | Resume resolution on connectivity event if intent is play | None |
| Blocked by network policy | Enter `BlockedByNetworkPolicy` | Await policy/network change or explicit user action | None |
| Permanent unsupported source | Surface error | Advance only when skip-on-error is enabled | None |

Retries are tied to source generation and state transitions, not wall-clock cancellation timeouts.

### Streaming Cache and Downloads

The three concerns are separate:

- **Artwork prefetch:** metadata/image-only and may follow the visible Up Next window.
- **Read-ahead cache:** bounded ephemeral Media3 cache for smooth playback. It observes metered/roaming policy and is evictable.
- **Offline download:** durable full-song storage started by an explicit user command.

The existing `DownloadManager` remains behind the explicit offline-download boundary until a later Media3 `DownloadService` migration is justified.

### Queue and Up Next

Queue and Up Next are projections of canonical order plus the current traversal mode. Download progress and source state may update row badges, but may not change order. Shuffle stores or derives a stable traversal order using queue-entry IDs.

### MediaSession Metadata

Timeline items use artwork URIs rather than embedding full cached artwork byte arrays. Notification and widget artwork loading may resize or cache independently. This keeps MediaSession binder payloads bounded for large queues.

### Diagnostics

Every decision records:

- queue entry ID and song ID
- current logical index
- source kind and source generation
- network class and policy result
- playback intent and engine state
- error category and selected recovery action
- whether advancement was permitted by `skipMediaOnError`

Credentials, authorization headers, and complete stream URLs are excluded.

## Staged Deployment

### Stage 1: Containment Release

Ship immediately for field validation:

- Remove full-audio downloads from Up Next asset prefetch.
- Keep artwork prefetch asynchronous.
- Stop source-error recovery from creating persistent downloads.
- Stop download completion from moving queue items.
- Refresh a failed remote source once in place.
- Honor `skipMediaOnError` for final advancement.
- Use `playWhenReady` as playback intent for UI pause state.
- Keep enhanced diagnostics enabled.

Automated gate: common tests, Android host tests, release version guard, release lint, and signed APK assembly all pass. Device gate: install and launch on an attached Android device, verify package/version, and inspect startup logs.

### Stage 2: Canonical State Reducer

After Stage 1 field evidence is reviewed:

- Introduce queue-entry IDs and a canonical queue reducer.
- Project Media3 and UI state from reducer effects.
- Introduce typed playback intent, engine state, source state, and error decisions.
- Convert auto-fill and manual queue edits into reducer commands.

Gate: deterministic reducer tests cover duplicate songs, shuffle, manual reorder during resolution, network changes, and concurrent source results. No recovery path may mutate queue order.

### Stage 3: Media3 Cache Boundary

- Add bounded `CacheDataSource` read-ahead.
- Add explicit metered and roaming streaming policy.
- Add a distinct explicit-offline-download policy, defaulting to no metered/roaming download.
- Evaluate migration of durable downloads to Media3 `DownloadService`.

Gate: network-shaping integration tests demonstrate that read-ahead cannot starve the active stream and cannot become a durable library download.

### Stage 4: Metadata and Legacy Cleanup

- Replace raw `setArtworkData` timeline metadata with artwork URIs.
- Remove obsolete deferred-download recovery models and diagnostics.
- Remove dual queue/state mutations left behind after reducer migration.

Gate: large-queue MediaSession parcel tests and Android device verification show no oversized binder transaction.

## Stage 1 Acceptance Matrix

- Online, uncached queue: current song streams and Up Next does not enqueue audio downloads.
- Mobile data, uncached queue: queue order stays unchanged while songs prepare or fail.
- Offline, uncached current song: no persistent download starts; playback pauses unless skip-on-error is enabled.
- Cached queue: no background Up Next audio jobs start; local files remain preferred.
- Remote source error: one in-place source refresh occurs before final error policy.
- Skip-on-error disabled: final failure remains on the current entry and reports the error.
- Skip-on-error enabled: final failure advances without moving entries.
- Manual pause during buffering/recovery: no automatic resume occurs.
- Download completion during playback: source URI may become local when safe, but queue order remains unchanged.

## Field Validation

The public Stage 1 release is a field candidate. During the next walk, capture:

- mobile-data network class and streaming quality
- Queue and Up Next before and after transitions
- whether uncached songs stream without mass download creation
- any pause or sub-second transition sequence
- `PlaybackDiagnostics` around the first failure

Stage 2 begins only after this evidence is reviewed or after Stage 1 automated/device gates reveal a remaining deterministic defect.
