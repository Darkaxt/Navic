# Playback Preparation Vinyl Overlay

Authorization: already_authorized. The user requested implementation and public
release. Vertical progress filling is explicitly excluded for this release.

## Evidence And Scope

PlaybackStartDialog routes its button and onDismissRequest to feedback.hide().
That only changes visibility; PlaybackPreparationCoordinator owns cancellation
and the generation fence protecting queue commits. The UI must call that owner.
Existing playback remains untouched during preparation, until a new queue commits.

No reader work, device installation, unrelated download cancellation, loading
deadlines, synthetic progress, or iOS-specific work is authorized by this change.

## Requirements And Acceptance

- R1: Replace the AlertDialog/card, spinner and button with a centered vinyl and
  a stationary, centered, readable Preparing playback / Starting playback label.
  Reuse Navic's vinyl visual language, not a remotely loaded or blank cover.
  Constrain the record to the available phone/tablet/landscape space, with no
  text overlap or clipping at increased font sizes. Fade the presentation and
  use subtle indeterminate rotation; honor disabled system animations.
- R2: Keep underlying controls visible under the modal dark scrim, but block
  touch and accessibility interaction with them. Outside taps do nothing.
  Android Back cancels the pending request and dismisses the overlay without
  navigating the underlying screen. There is no Cancel or Close button.
- R3: Cancellation during preparation cancels the owning job and invalidates its
  generation. Late completion must not replace the queue or start playback.
  Previously playing music, queue and independent downloads are not cancelled.
- R4: If the queue already committed and the request is still Starting, Back
  uses the existing Pause path to revoke the pending play intent and recovery,
  then Stop to cancel Media3's native buffering/preparation. Pause alone may
  continue loading. Retain that queue, do not clear or reconstruct it. Back with no pending request
  is a no-op for playback. New requests remain admissible; stale completion must
  not clear newer feedback. Repeated bulk Play must not duplicate preparation.
- R5: Verify the cancellation contracts with focused behavioral tests and the
  real Android modal/rendering boundary (phone and tablet/landscape, theme and
  font sizing). Verify current Android compilation/packaging and existing music
  regressions. Preserve unrelated worktrees and devices.
- R6: Commit and synchronize the verified integrated changes to fork/master;
  publish the next iota## release with the production signing identity. Verify
  CI source SHA, public APK version/package, digest, size and signature using an
  independently downloaded public asset. Record evidence and remove expendable
  task-generated verification output, not maintained source or diagnostics.

## Compact Staged Plan

### Stage 1: Integrated Overlay And Cancellation

Status: ACTIVE

Requirements: R1-R5.
Changes: route UI Back through the player to the existing preparation owner;
preserve pre-commit playback and use Pause then Stop for a pending committed start.
Replace dialog content with a responsive vinyl presentation and modal semantics.
Verification: focused cancellation/regression tests, Android rendering/input
checks, source wiring guards, Android build. Reconcile R1-R5 before closure.

Acceptance satisfied: R3, verified by preparation/feedback behavioral tests,
real ExoPlayer cancellation tests and production ViewModel wiring guards.
Acceptance remaining: R1, R2, R4 and R5. R4's native Stop boundary is strengthened
to cancel buffering as well as revoke playback, with fresh verification pending.
Native large-font rendering failed the
no-clipping assertion; bounded text autosizing and relative line height have
been applied, with fresh native rendering/input and packaging checks pending.
Blockers: none. Tracked deferrals: none.

### Stage 2: Verified Public Release

Status: NOT STARTED

Requirements: R6.
Changes: bump the next iota version, commit, sync, tag and publish only after the
integrated feature passes Stage 1. Inspect required CI music and packaging gates,
independently validate the public APK, record durable evidence, clean scratch.

Acceptance satisfied: none yet.
Acceptance remaining: R6.
Blockers: none. Tracked deferrals: none.

## Verification Ledger

The first verification attempt compiled production Android code but failed host
test compilation on an invalid assertion import. That test import was corrected;
no passing behavioral-test claim is based on that attempt.

The second attempt verified R3-R4: Back before commitment preserves the existing
engine track/position/play intent and independent downloads; late dependencies
cannot commit; Back during Starting revokes play intent without clearing the
queue; subsequent requests remain admissible and stale cleanup cannot hide them.
The existing artwork interaction/rotation/size/swipe policies and ViewModel
wiring/decomposition checks passed (74 inspected passing test cases in total).

The native tablet large-font test failed its text-overflow assertion before
modal input checks ran. It is not classified as a baseline or ignored failure.
R1-R2 and Android packaging remain unverified. Added bounded TextAutoSize and
relative line height, plus captures/layout diagnostics before assertions. Host
tests now include real Android resources and the Compose resource provider;
they do not substitute mocked strings or an HTML replica. Focused recheck pending.

The third attempt reached the native dialog but Compose captureToImage failed
waiting for WindowCapture.forceRedraw's device callback in Robolectric. The test
now draws the real native dialog decor into a native Bitmap synchronously after
Compose idleness. It retains the no-overflow, nonblank-pixel, native modal-window,
outside-input and Back/cancellation assertions. No application timeout was added.
The redundant remote run was cancelled after this capture incompatibility was
identified; it is not passing verification evidence.

All local Gradle attempts acquired Local\\Darka.AndroidGradleBuildGate and
reported max_workers=2, parallel=false and actual Gradle heap 3,221,225,472 bytes.
Kotlin was configured in-process; tests configured one 512 MiB fail-fast fork.
No diagnosed memory exhaustion or budget increase. Existing SQLite loader
warnings about locked native libraries were not treated as playback failures or
permission to delete another process's files.
