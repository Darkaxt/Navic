# Stale Playback Identity Recovery Design

**Date:** 2026-07-26

**Status:** Approved for implementation

**Platform scope:** Android playback and Android release only. Shared policy and
download code may change where Android consumes it. No iOS behavior or release
is included.

## Goal

Navic must recover deterministically when a persisted queue references a song
ID that Navidrome no longer recognizes after a scan, reimport, or reindex.
Recovery must be asynchronous, preserve queue order and playback intent, and
must never remain indefinitely in a loading state after a download request was
rejected or disappeared.

## Observed Failure

The physical tablet stopped on `Between Twilight` by Lindsey Stirling at queue
index 183 of 429. Media3 reported
`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` at 51 ms. The queued ID was
`GXmVQuhGQQZpBJoE28gtkg`, while the current Navidrome song ID for the same
recording was `ZHIFVmVzHJy7vbXlnEnhKU`.

Navidrome returned HTTP 200 with an `application/json` Subsonic error for the
old stream URL. Media3 therefore attempted to parse JSON as audio and reported
an unsupported container instead of an HTTP error. Navic then refreshed the
same stale URL once, requested a download for the stale ID, observed
`NOT_DOWNLOADED`, and waited forever.

## Root Cause

Four contracts fail together:

1. Persistent playback state stores complete `DomainSong` queue entries, so a
   server-side ID change can outlive a successful library refresh.
2. The stream endpoint can encode `Song not found` as HTTP-200 JSON, outside
   Media3's HTTP failure classification.
3. Source refresh rebuilds the URL from the same stale ID and cannot change the
   outcome.
4. `playbackRecoveryResolution` treats every download state except `FAILED` as
   non-terminal, including a request that was never accepted or that returned
   to `NOT_DOWNLOADED`.

This is not a network outage, audio-focus loss, headset event, queue
cancellation, or an unsupported audio codec.

## Required Invariants

1. A possible non-audio response is resolved off the player callback and never
   blocks Media3's listener thread.
2. A stale ID is confirmed through the authenticated `getSong` API. A parser
   error alone never proves that an item is stale.
3. Automatic remapping requires one unique, conservative identity match.
4. Fuzzy title similarity is never sufficient to replace a queued song.
5. A replacement updates the logical queue and Media3 item at the same index
   before playback resumes.
6. Queue order, shuffle order, repeat mode, retained position, and user play or
   pause intent are preserved.
7. A result is applied only if the same song and queue index are still current.
8. Confirmed missing or ambiguous songs terminate according to the existing
   `skipMediaOnError` preference; they do not create repeated download intents.
9. Terminal advancement follows Media3's recorded `upcomingIndexes`, not
   assumed list order.
10. A rejected download request is terminal immediately. An accepted request
    may wait only while it is queued, downloading, or producing a usable file.
11. Returning to `NOT_DOWNLOADED` after an active request is terminal.
12. No timeout is used for identity resolution, download recovery, or player
    cancellation.
13. Authentication and service-unavailable errors keep their existing paths.
14. Logs contain IDs and decisions, but never signed stream URLs or credentials.

## Target Model

### Potential Stale Response

Only a remote item that fails with a Media3 container/parser error enters stale
identity resolution. Local-file failures and ordinary network failures retain
their current recovery paths.

The resolver calls authenticated `getSong(oldId)` asynchronously:

- success means the ID is still current, so normal source/download recovery
  continues;
- `SubsonicErrorCode.DATA_NOT_FOUND` confirms that the ID is stale;
- a classified service failure enters the existing automatic Offline Mode
  path; and
- any other failure leaves the item unresolved and uses normal terminal error
  handling.

This probe turns the misleading HTTP-200 stream response into a typed Subsonic
decision without adding a second media data source.

### Conservative Identity Matching

After `DATA_NOT_FOUND`, the resolver compares the queued song with current local
catalog entries in this order:

1. one unique non-blank MusicBrainz recording ID match;
2. one unique normalized ISRC intersection;
3. one unique exact metadata signature consisting of normalized title, artist,
   album, disc number, track number, and duration within two seconds.

The old ID itself is excluded. Multiple matches at any tier are ambiguous and
stop automatic remapping. A blank album, artist, or title cannot qualify for
the metadata tier.

### First Resolved, First Served

Identity probing, catalog matching, download scheduling, and download status
observation are independent asynchronous decisions. The first conclusive
result is applied immediately:

- unique replacement: replace and retry the current item;
- confirmed missing without replacement: notify and hold/advance;
- service unavailable: use cached/offline fallback;
- accepted download with usable file: resume locally; or
- rejected/vanished/failed download: notify and hold/advance.

No branch waits for unrelated enrichment, a complete queue rewrite, or a full
library refresh.

### Queue Replacement

For a unique replacement Navic:

1. validates the current media ID and index against the captured request;
2. replaces the `DomainSong` at that queue index;
3. updates `currentSong` when the replaced index is current;
4. replaces the Media3 item using the new ID and stream URL;
5. seeks to the retained position;
6. prepares and resumes only when retained intent is Play; and
7. lets the existing player-state persistence save the repaired queue.

The old item is not appended, and the queue is not rebuilt. This preserves
Media3's shuffle timeline and prevents a visible Up Next reshuffle.

### Terminal Missing Item

If the old ID is confirmed missing but no unique replacement exists, Navic
shows `Song not found.` exactly once for that recovery. With
`skipMediaOnError` enabled and retained intent set to Play, it advances once to
the first playable index in `upcomingIndexes`. Otherwise it holds the current
queue and clears loading state. It does not enqueue a download for the missing
ID.

### Explicit Download Request Result

Playback recovery uses a dedicated suspend request rather than fire-and-forget
`prefetchPlaybackSongs`. The request returns one of:

- `Enqueued`
- `AlreadyActive`
- `AlreadyDownloaded`
- `MissingCatalogEntry`
- `InactiveSession`

`MissingCatalogEntry` and `InactiveSession` are terminal. `Enqueued` is
returned only after the DAO commits `QUEUED`, and `AlreadyActive` is returned
only after reading `QUEUED` or `DOWNLOADING`; either result therefore records
the returned generation as active immediately. `AlreadyDownloaded` resumes
only after `getDownloadedFilePath` verifies a usable file.

The recovery policy tracks the active generation. A later
`NOT_DOWNLOADED`/missing row is terminal, covering cancellation and
queue-worker rejection without using a deadline. Older generation snapshots
are ignored.

## State Transitions

| State | Event | Result |
| --- | --- | --- |
| Remote playback | Parser/container failure | Start one async identity probe |
| Identity probe | Old ID exists | Continue normal recovery |
| Identity probe | Old ID missing, unique match | Replace queue/media item and retry |
| Identity probe | Old ID missing, no unique match | Notify; hold or advance once |
| Identity probe | Service unavailable | Existing automatic offline fallback |
| Download request | Missing catalog/session | Terminal hold or advance once |
| Download request | Enqueued/already active | Observe accepted lifecycle |
| Accepted lifecycle | Usable downloaded file | Resume same item locally |
| Accepted lifecycle | Failed or returns inactive | Terminal hold or advance once |
| Any async result | Current item/index changed | Discard stale result |

## Acceptance Matrix

| Scenario | Required result |
| --- | --- |
| Exact tablet stale-ID case | Queue entry changes to current ID and playback retries |
| Old ID still resolves | No automatic remap |
| Unique MusicBrainz match | Replacement accepted |
| Unique ISRC match | Replacement accepted |
| Unique exact metadata match | Replacement accepted |
| Two matching candidates | No replacement; terminal item policy |
| Different title/artist/album | No replacement |
| User changes song during probe | Late result ignored |
| User pauses during probe | Replacement prepares but does not auto-play |
| Shuffle enabled | Terminal advance uses `upcomingIndexes` |
| Missing song and skip disabled | One notice, loading clears, queue holds |
| Missing song and skip enabled | One notice, one advance, queue remains intact |
| Download request lacks local song | Immediate terminal decision |
| Accepted download completes | Same item resumes from usable local file |
| Accepted download is cancelled | `NOT_DOWNLOADED` becomes terminal |
| Navidrome becomes unavailable during probe | Existing cached/offline fallback |

## Staged Delivery

1. Add pure identity and download-lifecycle policies with red/green tests.
2. Add authenticated stale-ID resolver and logical queue replacement wiring.
3. Add explicit playback-download request outcomes and terminal recovery.
4. Add diagnostics/source-contract coverage and compare code to this document.
5. Run focused/full Android gates, build the signed Android artifact, and
   publish `v1.0.11-iota28`.

No partially implemented public release is published between stages.

## Non-Goals

- No iOS implementation or release.
- No fuzzy or remote search-based song matching.
- No server, DNS, reverse-proxy, or Navidrome deployment change.
- No full queue reload after every library sync.
- No queue reorder or wholesale replacement.
- No retry timeout, cancellation timeout, or polling deadline.
- No claim that every malformed media file is a stale ID.

## Success Criteria

The feature is complete when the observed stale-ID failure either repairs the
current queue entry and resumes from the current song, or reaches a visible
terminal hold/advance decision. It must never remain indefinitely in recovery
with `NOT_DOWNLOADED`, and the released Android artifact must preserve the
existing offline fallback, queue order, and user playback intent.
