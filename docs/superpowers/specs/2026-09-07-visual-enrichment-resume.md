# Visibility-driven music enrichment

Status: implemented and host-verified for iota67. Public release authorized;
device installation is not authorized.

## Contract

- V1: Automatic Now Playing lyrics/clip lookups require the relevant resumed UI.
  Hide, screen-off and background transitions defer new work, not reject content.
- V2: Returning to that UI emits current demand immediately, even when the song
  and playback position have not changed. Process the current song only, not a
  backlog of songs played with the display off. Paused playback can fetch visuals.
- V3: Do not start automatic enrichment during explicit bulk playback startup.
  Releasing the startup guard resumes eligible visible demand automatically.
- V4: Retain completed same-song results and on-disk caches. A deferred request
  must not be recorded as success-with-no-content or a permanent failure. Clear
  stale displayed results when the current song changes; revalidate demand before
  starting a clip download after discovery completes.
- V5: Existing transfers may finish and be reused. Do not cancel deliberate
  offline downloads, clear caches, or alter background music/queue/recovery.
  This change prevents new automatic work; it does not promise instantaneous
  suspension of an already active transfer. Explicit video/PiP playback is unchanged.
- V6: Apply the same resume behavior to the lyrics panel and extra-screen clip
  discovery, so those paths cannot keep creating lookups after screen-off.

## Design and stages

Use the existing Compose lifecycle owner, with a small shared resume/dispose
effect. No polling, delays, screen-on preference changes or global offline switch.
Combine current playback, UI demand and the bulk-startup guard. Retain song
identity changes while hidden so stale results are cleared, but suppress hidden
progress ticks. Gate methods again at the request boundary to cover suspension
between input collection and network work. Skipped requests do not consume lookup
dedupe keys; interrupted clip discovery can be reconsidered on resume.

| Stage | Requirements | Evidence | Status |
| --- | --- | --- | --- |
| 1. Demand and lifecycle | V1-V4 | Coroutine tests: off/on, unchanged song, changed songs, paused state, startup gate | Passed |
| 2. Consumer integration | V1-V6 | Source checks, Android compilation, lyrics/clip/playback regressions | Passed; review blockers resolved |
| 3. Release | V1-V6 | Reconcile spec; version, signer, package and artifact verification | Host gates passed; publication and APK evidence belong in the iota67 release entry |

After each stage reconcile against these requirements. Required gaps block release;
optional refinements remain explicit. No measured claim of mobile latency reduction
will be made without a device/network benchmark. Real-device screen-off/on playback
remains a user acceptance check; no Dev APK may be installed.

Review reconciliation: lifecycle effects are keyed by their recipient view model
as well as visibility, so continuous song changes activate the new lyrics model.
Clip-download admission revalidates demand inside the manager mutex, after cache
and database suspension points. Same-song deferred discovery leaves its lookup
key reusable. Cancellation does not mark the clips service unavailable.

Final host verification, 2026-09-07: Android main and host sources compiled;
350 targeted tests across 73 suites passed with zero failures, errors or skips.
This includes four demand-flow tests, three lifecycle/admission wiring checks,
six playback-startup lifecycle tests, and a cancellation/retry repository test.
Reader vendor and PlayLikeCurl verifier self-tests passed; version-name validation
and whitespace checks passed. Latest published iota66 history is preserved.
No physical-device, visual UI, or mobile latency result is claimed. No required
code-stage feature is deferred; actual release APK proof is a publication gate.
