# Playback Download Recovery Design

## Goal

Navic should not stop music just because the next queued song is temporarily unavailable. An unavailable song should become deferred download work, playback should continue with the next playable song, and if no playable song remains Navic should replay the last confirmed playable song until a deferred item becomes available.

## Current Failure

The Android player currently has two stop-prone paths:

- `onMediaItemTransition(... AUTO)` sees an unavailable item and immediately calls `seekToNextMediaItem()`.
- `onPlayerError(...)` can skip after a source error, then prepare and play the next item.

Both paths can bounce through several queue items in seconds. They request no durable deferred recovery for skipped items, do not reinsert downloaded items near the front of the queue, and can leave the player paused at a non-playable item.

## Required Behavior

### Unavailable item on automatic transition

When Media3 auto-transitions into a queued item that Navic cannot play:

1. Record the item as a deferred playback download.
2. Request or prefetch its download through the existing download pipeline.
3. Look for the next playable queued item after the current index.
4. If a playable item exists, move to it and keep playback active.
5. If no playable item exists, replay the last confirmed playable item from the queue.
6. If there is no last confirmed playable item, keep the current recovery state visible and wait for the download flow to provide a playable file.

### Source error while playing

When the current media item fails with a source/download error:

1. Try the existing local-file recovery for that same item.
2. If a local file is not ready, record the item as deferred playback download work.
3. Request or prefetch its download.
4. Continue with the next playable queued item.
5. If no playable item exists, replay the last confirmed playable item.
6. Only surface a hard failure when Navic has no playable fallback and no deferred download work that can be waited on.

### Download completion

When a deferred item becomes downloaded:

1. Replace its Media3 item with the local file-backed item if it is already in the queue.
2. Move it to the first safe upcoming slot, directly after the current item.
3. If playback is stopped or waiting because no fallback existed, start the downloaded item automatically.
4. If playback is currently using the fallback song, do not interrupt mid-song; the recovered item should be next.

### Last playable fallback

Navic must keep a last confirmed playable snapshot:

- song id and title
- most recent queue index if still present
- last known media item
- last known playback position is optional, but fallback replay starts at the beginning to avoid resuming at the old end position

The snapshot is refreshed only after Media3 reaches a ready/playable state for a real song. A song that fails before becoming ready must not replace the snapshot.

### Logging

Every recovery decision must be visible in `PlaybackDiagnostics`:

- `deferred-download-requested`
- `unavailable-auto-transition`
- `source-error-deferred`
- `continue-next-playable`
- `replay-last-playable`
- `deferred-download-ready`
- `deferred-download-reinserted`
- `waiting-for-deferred-download`
- `hard-playback-failure`

Each log should include at least song id, title, current index, target index when applicable, reason, pending deferred count, and whether a fallback was available.

## Non-Goals

- Do not introduce timeout-based recovery.
- Do not change the download manager architecture.
- Do not change user queue ordering except for moving a completed deferred item into the first safe upcoming position.
- Do not change explicit manual previous/next button semantics in this task.

## Acceptance Criteria

- An unavailable auto-transition requests download instead of only skipping.
- A failed source item is deferred and downloaded instead of becoming a stop condition.
- Downloaded deferred items are promoted to the next playable slot.
- If every upcoming item is unavailable, the last playable song is replayed rather than stopping.
- Playback diagnostics can explain why Navic skipped, deferred, replayed, waited, or resumed.
- Focused policy and source tests cover the new branches.
