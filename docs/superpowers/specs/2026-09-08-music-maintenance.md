# Music Maintenance Correctness Specification

Status: implemented and host-verified; all R1-R10 acceptance gates satisfied.
Final evidence and scope limitations are recorded in the paired implementation
plan. Authorization: `already_authorized` by the
2026-09-08 request to fix the nine music QA findings using specification-gated
implementation. Baseline: `e1668fad37055ac73fe13b41bfe1014defe7c0cf` (iota67).
The QA report `../reports/2026-09-08-music-maintenance-qa.md` provides root-cause
evidence. This specification, not the staged plan, is authoritative.

## Requirements and Acceptance Criteria

### R1: Exclusive download ownership (M1)

Reconnect must not reset a live download claim. At most one worker may own a
song for an account, including the interval between database claim and job
registration. Startup/interrupted-queue recovery must run before worker admission
or after admitted work has stopped. Cancellation, reconnect, and replacement
must not create duplicate writers. Each transfer writes attempt-owned output;
stale cleanup must never delete another attempt's successfully published audio.
Acceptance: deterministic multi-worker/offline/reconnect tests verify exclusive
admission, stale completion rejection, intact successful bytes, and matching DB
state. User cancellation and session termination must still join owned work.

### R2: Coherent recovery observation (M2)

Recovery must validate the path belonging to the same row/snapshot used for its
download status. A lagging derived cache must not turn a successful download into
a terminal error. A genuinely absent/empty file must remain a failure; stale
song/generation results must not resume playback. Acceptance: completed-row-before-
derived-map ordering resumes the intended song once; real missing files and stale
ownership do not.

### R3: Latest queue-start intent wins (M3)

All queue-replacing preparation paths, including collection-row selection and
song radio, must share the applicable cancellation/supersession contract. Pause,
clear, newer selection, ownership loss, and session/controller replacement
invalidate old work before it can mutate the queue or start audio. Preserve bulk
duplicate-tap suppression and immediate progress feedback. Acceptance: suspend
old preparation, issue each superseding command, finish old preparation, and
observe no stale mutation. Do not introduce arbitrary timers/retries.
Pending initial datastore restoration is also a queue writer and follows this
contract, before it publishes UI state as well as before it changes Media3.

### R4: Automatic resume cannot override newer user intent (M4)

Inter-song delay and volume-zero resume may resume only their own still-current
pause intent, for the same playback session/queue item. Explicit playback commands
and queue replacement invalidate it. Preserve intentional automatic resume when
no superseding command occurs and retain the user-configured inter-song delay.
Acceptance: event-sequence tests include manual resume then pause during the gap
or while muted, same-index queue replacement, and normal unmodified auto-resume.

### R5: Lyrics errors remain retryable (M5)

Provider transport/service failure is not successful absence. Retain valid cached
lyrics when appropriate, propagate cancellation, and allow same-song retry on
screen resume following unresolved failure. Genuine successful absence must not
cause repeated polling. Requests deferred while hidden must run when visible.
Acceptance: failing providers then screen resume/success; genuine absence;
cached fallback; cancellation and no hidden automatic work.

### R6: Aurral sections terminate their loading state (M6)

If core identity/profile resolution fails, every dependent section whose work
will not run must leave loading, preserve useful cached data, and expose a
truthful failure/unavailable state. Independent requests must remain independent.
Acceptance: failed core lookup with and without cached data has no indefinitely
loading section whose job never started.

### R7: Observe confirmed artist monitoring (M7)

The discovered-artist page must observe the authoritative monitoring result while
remaining open. Acceptance, pending, confirmed, and failed must be distinct.
Confirmation must show monitored state and prevent duplicate submission without
navigation/reload; failure must permit retry. Acceptance: unmonitored -> pending
-> confirmed and unmonitored -> pending -> failure UI-state transitions.

### R8: Persist shuffle independently of repeat-one projection (M8)

Repeat-one may show the current song in Up Next but must not destroy the underlying
shuffle traversal. Restoring then disabling repeat-one must preserve the previous
order. Legacy saved states must be handled safely. Acceptance: round-trip a
multi-song shuffled queue through repeat-one/restart and verify traversal after
leaving repeat-one, plus ordinary repeat modes and invalid legacy inputs.

### R9: Account-scoped retained music downloads (M9)

Server/account identity must scope audio download rows, paths, pending work, and
all downloaded/offline views. Password changes must not change ownership. Logging
out or switching accounts must not expose or delete another account's audio;
returning to the original account must retain its usable downloads. Legacy rows
must not be silently assigned to an unrelated later login: migration may bind to
the account already persisted when upgrading; if no such owner is available,
preserve legacy rows/files unassigned and unavailable to other accounts. Clearly
record this compatibility rule and its limitation: old rows contain no per-file
account evidence. No destructive migration/fallback is allowed.
Acceptance: identical song IDs with different audio across accounts, logout and
restart, return to the original account, logged-in upgrade, unowned legacy upgrade,
owner-filtered collection/offline queries, and cancellation during transition.
Already-resolved local MediaItems and saved queue state also retain ownership:
account replacement must revoke the active player URI before the next account
is usable, and reject old restores. Switching accounts clears the current queue;
same-account credential refresh does not. This is not a new per-account queue
history or storage feature.

### R10: Verification and maintainability

Add a repeatable music-focused host-test gate to CI before publication and use the
same selection locally. Include new behavioral regressions, not only source-text
checks. Keep the Android player ViewModel below its existing 1,200-line guard by
extracting coherent responsibilities where needed, not by removing the guard or
minifying source. Required tests must pass; existing unrelated failures must be
classified rather than counted as music defects or silently ignored. No new
reader/audiobook behavior, release/version change, or device install is authorized.

## Design Constraints

- Use existing coroutines, flows, Room, Media3, and repository boundaries.
- Own cancellation through jobs/generations and serialized admission, not sleeps.
- Do not hold blocking locks across slow network work.
- Reuse current UI wording, layout, and optional-feature preferences.
- Shared/common source changes may require compilation-compatible platform code;
  this task does not add iOS functionality or publish any platform build.
- Preserve the separate active ebook checkout. Implement and commit on an isolated
  maintenance branch; integrate only into the intended music/master history.
- Record host proof separately from unperformed device proof. Release authorization
  from the previous completed task does not authorize a new release here.
