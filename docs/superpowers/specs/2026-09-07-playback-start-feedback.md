# Bulk playback startup feedback

Status: implemented and host-verified. Android only; no installation or release.

## Evidence and scope

Artist, genre and collection Play All already receive song lists. The Android
bulk coordinator prepares media items off the main thread, then replaces the
queue and prepares Media3. It publishes loading only after media-item preparation
and admits overlapping requests. The source establishes missing feedback, not
the measured cause of slow mobile streaming.

## Requirements

- R1: Publish an indeterminate progress dialog synchronously when a nonempty
  bulk Play/Shuffle request is admitted, before expensive preparation.
- R2: Admit one bulk startup at a time. A repeated request brings its feedback
  back into view, without rebuilding/replacing the queue.
- R3: Show Preparing playback followed by Starting playback through initial
  buffering. Finish on actual playing, error, interruption, or service hold.
  Do not show this dialog for ordinary track transitions or background buffering.
- R4: Closing the dialog hides feedback without stopping music. Explicit pause,
  next, previous, queue selection/clear, or another playback mode cancels pending
  preparation so it cannot later overwrite that command. Cancellation and failure
  release the admission guard. No control-flow timers.
- R5: Controller connection is event-driven. A request awaiting connection must
  remain cancellable and must not leave a delayed queue restore behind. Report
  preparation/connection failure using the existing playback error notification.
- R6: Preserve shuffle ordering, index-zero selection, cache/recovery behavior,
  and existing artwork. Keep transient feedback out of saved player state.

## Design

A small shared request owner exposes transient phase/visibility state, owns the
startup coroutine, and rejects duplicate admissions. Each request has an identity;
late cleanup cannot clear a newer request. Android waits for controller readiness,
prepares items, submits the queue, and then awaits Media3 startup events. A single
app-level Material dialog observes this state across collection/artist/genre pages.
The dialog can be closed; repeat Play reveals it again. Existing error/recovery UI
remains authoritative after an initial playback error or offline hold.

## Stages and reconciliation

| Stage | Requirements | Verification | Status |
| --- | --- | --- | --- |
| 1. Request lifecycle and UI | R1-R4, R6 | Coroutine tests for admission, phases, dismissal, cancellation and stale cleanup | Passed focused tests |
| 2. Android integration | R2-R6 | Source wiring checks and Android host compilation/tests | Passed |
| 3. Final reconciliation | R1-R6 | Diff review, focused regressions, record evidence and limitations | Passed host verification; device observations remain unmeasured |

After each stage compare behavior with every mapped requirement. Blocking gaps
must be resolved; optional work must remain explicitly tracked, not silently dropped.

Verification on 2026-09-07: Android main/host sources compiled; 239 tests across
49 playback, queue and collection-order suites passed, with zero failures, errors
or skips. Six request lifecycle tests cover the new behavior; source checks cover
the app-level dialog, command cancellation and stale restore invalidation.
`git diff --check` passed. No device installation, mobile latency benchmark, or
visual device validation was performed. No claim is made of faster audio startup.

## Separate follow-up: visibility-aware visual enrichment

The NowPlayingViewModel independently collects player state in viewModelScope and
calls lyrics and clip lookups without a visibility/lifecycle gate. Clip discovery
can call getOrQueueClipForPlayback, including automatic video downloads. These
downloads run in the manager's own scope; cancelling the view-model lookup alone
does not stop them. Demand ownership must distinguish automatic visible playback
from deliberate offline acquisition before cancelling background transfers. Gate
automatic visual enrichment on visible, active consumers, retain cached results,
and resume only for the current song. Preserve intentional offline downloads and
visible picture-in-picture/secondary-display consumers. Audio and queue preparation
must remain independent. This is an evidence-backed optimization opportunity, not
proof that these requests cause the reported startup delay; implementation is not
part of the feedback change.
