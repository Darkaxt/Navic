# Reader Live Authority Restoration Design

**Status:** Approved for implementation

## 1. Problem

A PlayLikeCurl deck can establish a valid Foliate live-presentation receipt and
then lose it when background raster work mutates the foreground WebView. The
passive mutation advances the shared foreground mutation generation, so the
previous live receipt correctly fails closed. Normal passive completion does
not currently emit an edge that restores live authority.

The verified landscape failure was:

1. Cold live receipt was valid.
2. Whispersync playback caused an external Foliate relocation and deck rebuild.
3. The replacement active deck became prepared.
4. Background prefetch acquired and released a passive WebView lease.
5. The live receipt became null and was never re-established.
6. Cue progression continued, but every anchor receipt was null, so the native
   PlayLikeCurl highlight remained invisible.

The existing location retry is too early because external relocation has just
invalidated the active deck. The deck-prepared retry is also insufficient
because later passive work can invalidate the receipt it creates.

Bounded follow-up diagnostics exposed a second lifecycle edge. A receipt can
remain valid while Foliate is in its retained presentation layout, then the
first active playback overlay makes the foreground renderer occupy its live
bounds. The paginator commit receipt remains valid, but its remembered visible
content no longer matches. The first anchor therefore fails closed and clears
live presentation authority before native geometry can be published.

## 2. Architecture

Foliate remains the sole authority for EPUB layout, DOM ranges, visible text,
pagination, and committed destinations. PlayLikeCurl remains the sole page
deformation renderer. Native code continues to consume only validated
page-local geometry and must not infer semantic positions.

Foreground WebView ownership becomes the lifecycle authority for restoring a
live receipt:

- A normal passive lease release is a completed-mutation edge.
- The ownership component publishes that edge synchronously after clearing the
  lease and before another passive lease can be admitted.
- The reader host routes the edge to the PlayLikeCurl/Foliate controller.
- If and only if an active prepared deck still exists, the controller requests
  the existing exact `goToVisualPage` settlement under an exclusive live claim.
- The exclusive claim fences further passive work until settlement and strict
  live-receipt confirmation complete.
- A later passive mutation repeats this contract; generation-level “already
  authorized” state is forbidden.

The native host also observes the distinction between an active playback
overlay and a validated anchor receipt. A transition into active-without-anchor,
or loss of an established anchor while playback remains active, requests the
same exact active-deck authority path. Repeated missing-anchor updates do not
re-request authority until the overlay or anchor state changes. This edge uses
no EPUB coordinates and does not promote preview authority.

This does not make prewarm part of Whispersync semantics. Prewarm remains
optional, but any component that mutates the shared foreground WebView must
leave live presentation authority restorable before page-attached overlays can
publish.

## 3. Required Behavior

1. `releasePassive(currentLease)` publishes exactly one completed-mutation
   callback after the lease is no longer current.
2. A stale or duplicate passive release publishes no callback.
3. Passive preemption by an already-waiting live claim does not publish a second
   restoration request; that claim already owns restoration.
4. The callback may synchronously acquire an exclusive live claim, and that
   reentrant acquisition must block passive admission.
5. The host callback first rearms live authority for the active prepared deck,
   then resumes genuine deferred passive work only when ownership permits.
   Ownership availability resumes only explicitly deferred raster or destination
   work; it must not synthesize an unconditional prewarm/deck refresh after any
   live claim releases. Such a refresh feeds deck preparation back into another
   live settlement.
6. Live settlement continues to require matching Foliate session, page, raster,
   texture, foreground-mutation generation, and settlement token.
7. Native Whispersync geometry remains fail-closed until an anchor receipt
   matches the confirmed live presentation receipt.
8. An active overlay with no validated anchor, or loss of an established anchor,
   requests exact authority once per state transition. Repeated missing-anchor
   progress must not create a settlement loop.
9. Playback fallback remains progressive `cue-v1-dom-utf16`; it must not regress
   to whole-sentence presentation.
10. Page-bounded playback allows the overlapping active sentence to finish and
    pauses only when the next cue is wholly outside the visible page.

## 4. Lifecycle And Failure Rules

- Detach, session replacement, invalidation, generation release, and destruction
  release pending live claims and clear native overlay proof.
- If the WebView is unavailable or exact settlement fails, no stale receipt is
  promoted. A later real lifecycle edge may retry.
- Live/preview receipt scopes remain separate; preview authority cannot be
  promoted to live authority.
- No fixed delay, frame count, polling loop, or navigation gesture may be used
  to materialize the initial highlight.
- Diagnostics and tests must not log or persist EPUB text, hrefs, CFIs, IDs,
  tokens, raster payloads, or screenshots.

## 5. Acceptance Criteria

### Automated

- Ownership tests prove one-shot current passive release notification,
  stale-release fencing, and synchronous live-claim reentrancy.
- Host/controller tests prove the completed passive mutation edge and an active
  missing-anchor transition request exact live authority for the active prepared
  deck, with repeated missing-anchor updates coalesced.
- Existing foreground ownership, raster preparation, exact settlement,
  Whispersync anchor, progressive fallback, and page-boundary tests pass.
- Reader JavaScript harness tests pass.

### Landscape emulator

Using only the first two pages of Chapter 1 of the configured paired test book:

- cold live receipt is valid without navigation;
- playback may invalidate the receipt through passive mutation or live renderer
  layout, but a fresh same-deck receipt follows the corresponding restoration
  edge;
- active cue events carry non-empty finite page-local spread geometry matching
  the live receipt;
- fallback cue progress produces more than one partial progress value;
- no inactive event is emitted for an overlapping page-spanning sentence;
- the next wholly outside cue pauses page-bounded playback;
- no screenshot, OCR, protected text, href, ID, or payload is captured.

## 6. Release Gate

Publish a new signed production release only after focused tests, consolidated
reader gates, JavaScript harness tests, and the bounded landscape emulator
acceptance pass. Verify the APK certificate against the established production
SHA-256 identity before publishing.
