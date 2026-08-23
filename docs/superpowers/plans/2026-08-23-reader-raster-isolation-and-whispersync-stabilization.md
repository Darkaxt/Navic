# Reader Raster Isolation And Whispersync Stabilization Staged Plan

**Goal:** Replace shared-foreground passive capture, correct the Whispersync
lifecycle and seek loop, make preparation Retry generation-safe, and make curl
presentation visually atomic without changing Foliate or PlayLikeCurl authority.

**Specification:**
`docs/superpowers/specs/2026-08-23-reader-raster-isolation-and-whispersync-stabilization-design.md`

**Delivery style:** Each stage is a focused TDD checkpoint. Group coherent RED
tests, run one focused RED gate, implement only the stage contract, run one
focused GREEN gate, validate against the specification, then commit and push to
`fork`. Do not launch Gradle after every file edit.

## Stage Exit Rule

After every stage:

1. Re-read the specification sections named by that stage.
2. Map each requirement to code, a test, runtime evidence, or an explicit
   deferral.
3. Classify every gap as:
   - **Blocker:** continuing would build on an invalid authority/state contract,
     hide the defect, violate privacy, or make the next stage's result unreliable.
   - **Deferred:** the requirement is not exercised by the next stage and is safer
     or cheaper to complete when its consumer exists.
4. For every deferral, record the latest stage where it must be completed.
5. Stop the stage if any blocker remains. Do not compensate with fixed delays,
   retries over stale state, native EPUB inference, or a shared-WebView fallback.
6. Commit and push the verified checkpoint. Keep `.codex-validation` local.

Prefer deferral when implementing a requirement now would create unused
abstractions or speculative integration. Prefer blocking when the next stage
would depend on the missing invariant. No requirement may remain unclassified,
and all deferrals must close before the final release unless the specification
explicitly marks them non-release scope.

## Stage 1 — Whispersync Intent And Event Provenance

### Steps

1. Add RED reducer/policy tests for durable manual Stop, same-spread Start,
   boundary pause, post-turn resume, explicit cue selection, and maintenance
   events that must not seek.
2. Separate playback intent and prepared visible target from engine phase and
   active visual highlight.
3. Add one-shot typed provenance from user navigation/cue selection through the
   causally matching committed destination.
4. Remove generic `visibleTextRange`/`locationChanged` as standalone seek
   authority; unknown provenance fails closed as maintenance.
5. Run focused GREEN tests and preserve exact/progressive cue and page-boundary
   coverage.

### Expected outcome

- Stop resets/prepares the current spread and leaves Start available immediately
  after preparation.
- Stopped intent survives page turns; enabled intent resumes only after manual
  curl settlement and destination commit.
- Internal reader settlements produce zero audio seeks, eliminating the verified
  full-spread restart mechanism.

### Specification validation

Re-read Sections 7, 8, 11.3-11.5, 12, and 14.1. Any path where active highlight
controls eligibility, or a maintenance event can seek, is a blocker. Isolated
backend/exact-cue mismatch investigation may defer to Stage 6 unless it prevents
these lifecycle tests from being evaluated.

### Stage 1 execution ledger

- **Validated:** Playback intent, transport phase, prepared target, active overlay,
  and one-shot causal provenance are independent; focused reducer, bridge,
  WordSync, legacy Readaloud, JavaScript, and bounded Android host gates pass.
- **Deferred to Stage 5:** A bridge overlay-activation event is not final native
  mask/presentation-bundle proof. Native receipt, mask, and bundle admission must
  close this visual-proof requirement before Stage 5 exits.
- **Blockers:** None after the Stage 1 correction and verification gates.

## Stage 2 — Passive Capture Feasibility Prototype

### Steps

1. Add RED contract tests for a raster-only passive port, live-issued manifest,
   passive receipt, strict admission, and absence of semantic callbacks.
2. Implement a minimal passive WebView host and passive Foliate capture runtime
   without routing production preparation through it.
3. Build a synthetic parity harness for portrait, landscape spread, profile
   change, orientation replacement, and stale-generation rejection.
4. Measure capture reliability plus bounded memory, CPU, and lifecycle behavior
   on the supported emulator/tablet class without using protected content.
5. Run the focused GREEN/parity gates and retain only privacy-safe results.

### Expected outcome

A separately hosted passive Foliate session can reproduce the live-issued raster
profile and geometry reliably, return strictly admissible rasters, and remain
incapable of publishing live reader events.

### Specification validation

Re-read Sections 4-6, 13, 14.2, and 15.1. Pagination/profile mismatch, unreliable
off-screen capture, a semantic event path, or unacceptable steady-state resource
cost is a blocker for Stage 3. Fine-grained low-memory tuning may defer to Stage
4 if the prototype remains safe and bounded under normal conditions.

## Stage 3 — Isolate Prewarm And Background Capture

### Steps

1. Issue passive manifests from current canonical live commits.
2. Route prewarm and background capture through the passive port.
3. Admit results only in `ReaderPageTurnBundleSource`; keep current-page live
   snapshots separate.
4. Fence session, settings, orientation, profile, and deck changes with capture
   epochs and strict receipt equality.
5. Prove repeated passive prewarm leaves live mutation generation, receipt,
   anchor, overlay, and committed location unchanged.
6. Run focused and consolidated GREEN gates for the migrated path.

### Expected outcome

Ordinary passive preparation no longer touches foreground WebView composition or
requires live-authority restoration. Initial and progressing highlights remain
valid while background raster work runs.

### Specification validation

Re-read Sections 4-6, 11.1-11.2, 12, and 14.2. Any prewarm/background route that
can expose the foreground preview, advance foreground mutation, or promote
passive proof is a blocker. Repair routing, failed-generation Retry, and final
removal of the transitional shared path may defer only to Stage 4.

## Stage 4 — Fresh-Generation Recovery And Full Passive Isolation

### Steps

1. Add RED tests proving failed-generation callbacks/decks cannot publish and
   Retry creates a new generation with fresh manifests.
2. Route repair capture through the passive host and recreate an unhealthy
   passive session when required.
3. Make duplicate Retry input coalesce into the active fresh attempt while
   preserving valid cache entries, live location, and playback intent.
4. Remove the shared-foreground preview path from all production passive work and
   narrow foreground ownership to genuine live mutations.
5. Verify Retry reaches Ready without closing the publication and that failure
   keeps the current page rather than exposing black content.
6. Run focused and consolidated GREEN gates.

### Expected outcome

A preparation failure is recoverable in-session through one fresh attempt.
Prewarm, background capture, and repair are fully isolated from the live WebView;
production no longer depends on expose/capture/restore preview transactions.

### Specification validation

Re-read Sections 5, 6, 9, 11, 12, and 14.3. Reusing failed state, accepting a late
old-generation callback, requiring book reopen, or retaining a reachable
production shared-preview fallback is a blocker. Optional cache tuning may defer
to Stage 6 if it does not affect correctness or bounded resource use.

## Stage 5 — Atomic Curl Material And Highlight Presentation

### Steps

1. Add RED bundle/renderer/host tests requiring matching raster, geometry, page
   material, border, and highlight-mask ownership.
2. Make PlayLikeCurl own paper backing, back-cover material, clipping, and borders
   for every forward, backward, cancelled, and settled curl frame.
3. Route exact/progressive word updates through bounded page-local masks or
   renderer-native rectangles without full-page recapture.
4. Coalesce updates during curl and enforce source/destination ownership at
   settlement.
5. Verify Compose Start/Stop and Retry remain above the native surface and
   hit-testable.
6. Run focused renderer, host, and integration GREEN gates.

### Expected outcome

Curl animation never depends on transparent exposure of the WebView, never shows
black/missing backing or borders, and presents only a current page-owned
highlight without recapturing page pixels per word.

### Specification validation

Re-read Sections 4.3-4.4, 10, 12, and 14.4. Missing backing/borders, mixed bundle
generations, full-page highlight recapture, or source/destination mask leakage is
a blocker. Live in-curl word updates may use the specification's supported
suspension-and-coalescing path instead of forcing a new renderer API.

## Stage 6 — Integrated Reader Acceptance

### Steps

1. Cross-check all specification acceptance items and close every deferral whose
   consumer now exists.
2. Run consolidated JavaScript, common, Android host, renderer, build, and lint
   gates with no test failures.
3. Freeze one ReaderDev APK from the pushed commit and run the focused synthetic
   emulator acceptance for initial highlight, Stop/Start, provenance, page-end
   pause, Retry, passive isolation, and curl material.
4. With explicit device ownership, run only the first two landscape pages of
   Chapter 1 on the approved tablet using privacy-safe evidence.
5. Classify any remaining cue mismatch as backend data, frontend mapping, or
   unresolved. Fix it now only if it violates the specification or blocks the
   bounded acceptance.
6. Re-run only the gates affected by a necessary correction, then run the final
   consolidated gate once.

### Expected outcome

The integrated reader satisfies the authoritative interaction contract without
highlight loss, same-spread Start failure, maintenance-origin audio loops,
poisoned Retry, black curl material, or cross-page highlight leakage.

### Specification validation

Re-read the complete specification and account for every Acceptance Summary
item. Any unimplemented authority, privacy, lifecycle, Retry, mask, or material
requirement is a blocker. A remaining feature may defer beyond this project only
if the specification names it as a non-goal and the bounded acceptance proves it
cannot mask a core failure.

## Stage 7 — Signed Production Delivery

### Steps

1. Run the final release gates from the exact pushed commit.
2. Prepare the next immutable version and trigger the GitHub-managed signed
   production build.
3. Verify workflow success, APK hash, package/version, and certificate SHA-256
   `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
4. Publish the immutable release and verify the downloaded asset independently.
5. Record acceptance evidence and close the deferral ledger.
6. Save and stop any owned emulator after validation; do not alter unrelated
   emulators or devices.

### Expected outcome

A signed production release contains the verified architecture and behavior, all
release-blocking specification requirements are satisfied, and no local work or
untracked deferral remains.

### Specification validation

Re-read Sections 13-17 and the complete Acceptance Summary before publication.
Any unchecked release-path item blocks publication; there is no post-release
correctness deferral.
