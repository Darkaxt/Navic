# Reader Live Authority Restoration Implementation Plan

**Goal:** Restore exact Foliate live presentation authority after passive
foreground-WebView mutations so Whispersync geometry reaches PlayLikeCurl
without navigation.

**Specification:**
`docs/superpowers/specs/2026-08-22-reader-live-authority-restoration-design.md`

**Verification policy:** Group each coherent RED/GREEN stage. Run one focused
Gradle invocation at RED and one at GREEN; do not launch Gradle after individual
file edits.

## Stage 1 — Completed-passive-mutation contract

**Files**

- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnershipTest.kt`
- Modify `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderForegroundWebViewOwnership.android.kt`

**Steps**

1. Add RED coverage proving that releasing the current passive lease publishes
   exactly one completed-mutation callback after clearing ownership.
2. In the callback, synchronously acquire an exclusive live claim and assert
   that another passive lease cannot enter.
3. Extend stale-release coverage to assert that stale/duplicate release does not
   publish the callback.
4. Run the focused ownership test class once and confirm the expected RED.
5. Add the minimum callback parameter and current-release publication.
6. Run the focused ownership test class once and confirm GREEN.

## Stage 2 — Reader host reauthorization seam

**Files**

- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateControllerSourceTest.kt`
- Modify `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt`

**Steps**

1. Add RED source-contract coverage requiring the ownership callback to route
   first to the PlayLikeCurl controller and then to existing passive-work resume
   logic.
2. Require the controller seam to call the active-prepared-deck exact live
   authority request, without preview receipt promotion or persistent
   generation authorization.
3. Run the focused source test class once and confirm the expected RED.
4. Wire the ownership callback through the host.
5. Add the minimal controller entry point that rearms the existing strict exact
   settlement path.
6. Remove unconditional prewarm scheduling from generic ownership availability.
   Resume only the raster and destination work that recorded an explicit
   deferral; otherwise every live-authority release can feed deck preparation
   back into another settlement.
7. Route active-overlay-without-anchor and established-anchor-loss transitions
   to the same exact active-deck authority request. Coalesce repeated missing
   anchor updates until active or anchor state changes.
8. Keep the exclusive live claim through one atomic Foliate bridge evaluation
   that validates the live target once and captures its presentation receipt and
   canonical text-page commit identity as one authority snapshot. Pass that same
   snapshot into retained active-overlay anchor construction in the same
   JavaScript turn, normalize numeric raster hashes to wire strings, and forbid
   nested receipt or commit revalidation. Native code validates the returned
   receipt afterward and must not reconstruct the cue or reuse failed geometry.
9. Run the focused source test class plus ownership and transition tests once
   and confirm GREEN.

## Stage 3 — Specification cross-check and consolidated gates

1. Cross-check every requirement in Sections 2–5 of the specification against
   code and tests; fix only missing release-blocking behavior.
2. Run consolidated reader Android host tests in one Gradle invocation.
3. Run the focused JavaScript reader harness tests covering live receipts,
   anchor provenance, progressive fallback, and page-spanning cues.
4. Commit and push the verified implementation to `fork`.

## Stage 4 — Bounded landscape emulator acceptance

1. Build and install ReaderDev from the pushed commit on `emulator-5554` only.
2. Launch directly at the first synced Chapter 1 resource in landscape.
3. Use privacy-safe CDP breakpoints/counters to verify cold authority, passive
   mutation invalidation/recovery, at least one post-recovery anchor attempt,
   live-matching page-local geometry, progressive fallback, and correct
   first-page boundary behavior.
4. Do not use screenshots, OCR, protected text, hrefs, IDs, or payload logging.
5. If acceptance fails, stop release and use the captured lifecycle boundary as
   the next root-cause input; do not stack another speculative retry.

## Stage 5 — Signed production release

1. Run the final consolidated release gates once.
2. Build using the persistent GitHub-managed production signing identity.
3. Verify certificate SHA-256
   `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
4. Publish a new immutable release and verify its uploaded APK and certificate.
5. Mark the highlight and sentence-boundary tasks complete only after release
   verification succeeds.
