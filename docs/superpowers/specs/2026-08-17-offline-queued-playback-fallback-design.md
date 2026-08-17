# Offline Queued Playback Fallback Design

**Date:** 2026-08-17

**Status:** Approved

**Baseline:** `v1.0.11-iota59`

**Platform scope:** Android music playback. No iOS behavior or release work is included.

## Goal

Offline music playback must continue through the first usable cached song in
Media3 traversal order. A remote song must never leave playback waiting on a
download that cannot run while Navic is effectively offline.

## Field Failure

During an offline flight, playback stopped after one or two songs and remained
in a download-like state. Pressing Next allowed Navic to find another playable
song. Retained Android media-session history also showed sessions remaining on
tracks beyond their nominal duration instead of completing a normal transition.

The installed `v1.0.11-iota59` reader-only delta does not change the music
recovery implementation, so the failure is present in the latest public music
baseline.

## Root Cause

Playback recovery and offline fallback are independent asynchronous paths:

1. An unclassified remote playback error can enter
   `beginDownloadRecovery()` before the availability observer publishes the
   effective offline state.
2. `DownloadManager` preserves that request as `QUEUED` and suspends its workers
   until effective connectivity returns.
3. `playbackRecoveryResolution()` treats `QUEUED` and `DOWNLOADING` as `Wait`.
4. The Now Playing UI renders a queued pending recovery as zero-percent download
   progress.
5. No later download snapshot can become terminal while offline, so recovery can
   wait indefinitely.
6. Manual Next clears the pending recovery before advancing, which explains why
   that action unblocks playback.

The existing automatic offline fallback already searches the complete
`PlayerUiState.upcomingIndexes` traversal produced by Media3. The five-item Up
Next display is not the fallback search boundary.

## Required Behavior

### Recovery arbitration

Every remote playback-recovery entry point must inspect effective connectivity
before allowing a download to own playback:

1. If the current item is already local, retain normal local-file error policy.
2. If effective online state is true, retain same-song download recovery.
3. If effective online state is false, hand control to offline fallback before
   creating or waiting on a recovery download.
4. If connectivity becomes false while a recovery download is pending, hand the
   pending recovery to offline fallback on the next connectivity or download
   observation.

### Offline fallback

Offline fallback applies this ordered decision:

1. If the current song has a usable downloaded file, replace its source in place
   and preserve position and playback intent.
2. Otherwise, traverse the complete Media3 `upcomingIndexes` sequence and play
   the first song with a currently usable downloaded file.
3. If no cached candidate exists, retain the queue, current index, position, and
   playback intent in an offline hold state.

Offline fallback must not consult `skipMediaOnError`, because connectivity
fallback is source selection rather than a terminal item error.

### Download ownership

- A queued download intent remains durable and may resume after connectivity
  returns.
- A queued or interrupted download must not retain ownership of music playback
  while offline.
- Entering offline fallback must not create a new persistent download request.
- No timeout or fixed-delay cancellation is introduced.

### User interface

- Download progress is shown only while a recovery download can make progress.
- Offline fallback or offline hold clears the download-progress presentation.
- The existing connection-loss notification remains authoritative.
- User pause intent wins over fallback, completion, and service restoration.

## Architecture

`AndroidStablePlaybackRecoveryCoordinator` owns one connectivity-aware gate for
remote recovery. It receives effective online state from
`ConnectivityManager.isOnline` and routes offline remote failures through the
existing `handleServiceUnavailable()` decision.

The gate is used defensively at all relevant asynchronous boundaries:

- before stale-song probing or download recovery begins;
- immediately before a download request is issued; and
- while processing snapshots for an already pending recovery download.

This makes behavior independent of whether Media3, Android connectivity,
Navidrome availability, or Room download state publishes first.

`AndroidMediaPlayerViewModel` suppresses recovery download progress whenever
effective online state is false. This is presentation hardening, not the source
of truth for playback decisions.

## Invariants

1. Connectivity changes never remove, reorder, or rewrite queue entries.
2. Cached selection follows Media3 shuffle and repeat traversal.
3. A usable cache file is proven only by
   `DownloadManager.getDownloadedFilePath()`.
4. Local-file decoder and storage errors remain item failures.
5. Online same-song download-and-resume behavior remains available.
6. Manual Next and Previous continue clearing stale pending recovery.
7. Service restoration never jumps back from a cached song already playing.
8. Offline recovery does not add polling, timeout, or retry cancellation.

## Staged Deployment

### Stage 1: Pure policy and source contracts

Add regression coverage for the connectivity gate, complete traversal, local
source exclusion, and offline progress suppression. Verify the tests fail on
the iota59 baseline before production changes.

### Stage 2: Coordinator integration

Inject effective online state, route offline remote errors before network-bound
recovery, and re-arbitrate pending queued recovery when connectivity is lost.
Run focused common and Android host tests.

### Stage 3: Regression verification

Run the complete common and Android host suites and the Android assemble task.
Compare the implementation against every invariant in this document.

### Stage 4: Public release

Integrate the isolated branch without modifying the active ebook worktree,
advance the `{letter}##` release from `iota59` to `iota60`, build and checksum
the release APK, push the integrated branch and tag, and publish the stable APK.

## Acceptance Criteria

- Offline remote error plus cached current song resumes the cached current song.
- Offline remote error plus uncached current song advances to the first cached
  upcoming song in Media3 order.
- Pending `QUEUED` or interrupted `DOWNLOADING` recovery cannot wait indefinitely
  after effective connectivity becomes false.
- Offline with no cached candidate holds the queue without download progress.
- Online queued recovery still waits for same-song download completion.
- Queue IDs and ordering are unchanged by every fallback outcome.
- User pause intent is not converted into autoplay.
- Focused tests, broad host tests, and release assembly pass before publication.

## Non-Goals

- Removing unavailable songs from the queue.
- Rewriting shuffle or repeat behavior.
- Treating authentication, missing-media, local decoder, or storage failures as
  connectivity outages.
- Adding a download timeout.
- Adding or changing iOS playback behavior.
