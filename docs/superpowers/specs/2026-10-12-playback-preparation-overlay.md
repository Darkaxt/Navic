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

Status: COMPLETE

Requirements: R1-R5.
Changes: route UI Back through the player to the existing preparation owner;
preserve pre-commit playback and use Pause then Stop for a pending committed start.
Replace dialog content with a responsive vinyl presentation and modal semantics.
Verification: focused cancellation/regression tests, Android rendering/input
checks, source wiring guards, Android build. Reconcile R1-R5 before closure.

Acceptance satisfied: R1-R5. See the final Stage 1 reconciliation below.
Acceptance remaining: none.
Blockers: none. Tracked deferrals: none.

### Stage 2: Verified Public Release

Status: COMPLETE

Requirements: R6.
Changes: bump the next iota version, commit, sync, tag and publish only after the
integrated feature passes Stage 1. Inspect required CI music and packaging gates,
independently validate the public APK, record durable evidence, clean scratch.

Acceptance satisfied: R6, including source synchronization, public release,
independent artifact/source proof, recorded evidence and reviewed scratch cleanup.
Acceptance remaining: none.
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

## Final Stage 1 Reconciliation

- R1: Native dialog rendering passed for 393x851 phone, 1280x800 tablet and
  640x320 landscape. Actual Android resource font scale 2 was verified through
  TextLayoutResult (a parent composition-local override does not test a separate
  dialog window). Captures were visually inspected: centered, circular, readable,
  no clipping. Disabled animations and cancellation during the reveal passed.
- R2: Real modal window flags verified dim-behind with a fractional opacity,
  focusable/touch-modal isolation, ignored outside events, no Cancel/Close action,
  and Back consumed without underlying navigation. Direct decor captures contain
  dialog content; the separate window compositor supplies the background scrim.
- R3: Owning-job and generation tests reject late non-cooperative completion,
  preserve previous engine item/position/play intent and independent downloads,
  and retain duplicate-admission/stale-owner protection.
- R4: Real ExoPlayer tests passed Pause plus Stop, STATE_IDLE, retained media
  items/current item, and stale Back no-op. Production ViewModel wiring checks
  passed and explicit resume already prepares an idle retained queue.
- R5: Gate ticket 687 completed successfully on candidate 308775bb0: all 30
  focused native UI/engine/wiring checks passed, and assembleDebug packaged
  Navic.apk (76,240,963 bytes). Gate acquisition, two workers, no parallelism and
  actual 3 GiB Gradle heap were verified; Kotlin was configured in-process and
  the test task configured one 512 MiB fork. No memory-related failure occurred.
  CI run 38185662155 tested the identical production source: all existing music
  regressions and cancellation checks passed; its only failure was the new
  manual-clock capture assertion. That harness sequencing was corrected and
  passed in the focused run above, alongside a stronger native-font assertion.
  Full current-source music CI is also required by the Stage 2 release workflow.

Local build evidence: gate log 031a5163f31c470f8423358b0c7b8d34.log; inspected
host-test XML reports and native PNG captures. These expendable local outputs
were removed after the public-release proof was recorded. No device was changed.

## Public Release Verification

- Public release: https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-iota71
  (published 2026-10-12T00:15:13Z; neither draft nor prerelease).
- Release source: 16f7253277e0d6d6f588672a6d84164d1f4c1a34, synchronized
  to fork/master. The remote annotated tag peels to this exact commit.
- CI: https://github.com/Darkaxt/Navic/actions/runs/38186620219 completed
  successfully at that source SHA. Inspected XML: 1,168 tests, 215 suites,
  zero failures, errors or skips. Native rendering/input/animation checks,
  production signing and packaged vendor/attribution gates passed; iOS skipped.
- Independently downloaded the pinned public Navic.apk URL using unauthenticated
  curl, not the Actions artifact endpoint. Compared it with the separately
  downloaded CI APK and the GitHub release asset metadata.
- All copies: 47,541,078 bytes; SHA-256
  `87ccce7ba41c4301ae26b50d391a69c545bf5031f85042b195db67cf6aa2639a`.
- aapt: package `darkaxt.navic`, versionName `v1.0.11-iota71`, versionCode
  `598`, no application-debuggable flag. apksigner verify succeeded (v2) with
  the pinned production certificate SHA-256
  `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
- Native dialog capture corner alpha is 0, while the disc is opaque: the
  content does not replace the native dim-behind scrim with a solid full-screen
  background. Phone/tablet/landscape captures were visually inspected.
- Removed the task-owned verification branch after proving its commit was an
  ancestor of fork/master, using an exact-SHA deletion lease. Confirmed absent.
  Redundant task-owned master/candidate CI runs were cancelled, not other builds.
- No device installation, reader changes, production preferences, signing-key
  changes, server changes or hibernation were performed.

Cleanup completed: transaction `848eb8e164dd8fbc5f5ebc1f600358c8`, exact
root `D:/Temp/navic-playback-overlay-20261012`, state `applied`. All 35,479
reviewed entries were deleted (1,722,700,940 attributed bytes), with no warnings,
errors or residuals; the exact root is absent. Only generated verification
outputs, extracted dependency inspection copies, captures and APK copies were
included. Maintained worktrees, shared caches and previous diagnostic evidence
were outside its scope and preserved. Final evidence synchronization is a
documentation-only commit; production files remain identical to the release tag.

Final reconciliation: R1-R6 satisfied and verified. Both stages COMPLETE, no
required blockers or tracked deferrals. Progress filling remains explicitly out
of scope, not an unfulfilled requirement.
