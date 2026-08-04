# Reader Paginator Commit Receipts Design

**Status:** Approved for implementation

**Date:** 2026-08-04

**Navic baseline:** `936e68d560d486058bc7758de1f5d3640a2b33dc`

**Related specifications:**

- `docs/superpowers/specs/2026-07-15-reader-playlikecurl-library-integration-design.md`
- `docs/superpowers/specs/2026-07-17-reader-playlikecurl-raster-readiness-design.md`
- `docs/superpowers/specs/2026-07-18-reader-playlikecurl-qa-remediation-design.md`

This document defines an authoritative generation-scoped pagination transaction
for reflowable, paginated publications. Foliate remains the authority for
section loading, layout, exact text-page placement, visible ranges, and
relocation. Navic and PlayLikeCurl consume Foliate's committed result instead of
inferring commitment from font promises, animation frames, or host-side page
sampling.

## 1. Problem

The current reader has several individually event-driven components but no event
that means the complete invariant Navic needs:

> The requested reflowable text page is committed under the current layout, and
> that commitment has not subsequently been invalidated.

`goToTextPage()` currently returns a Boolean after section navigation and
scrolling. Layout can still change because of paginator attributes, styles,
fonts, viewport resizing, or `ResizeObserver` expansion. Navic compensates by
waiting for fonts, forcing another render, and sampling the same position over
multiple frames. That sequence has already produced two distinct production
failures:

1. A cached chapter count differed from the renderer's eventual count.
2. A post-navigation layout application converted the exact numeric anchor to a
   range and moved the passive renderer to the adjacent page.

The second failure stabilized on the wrong page. This proves that repeated equal
samples establish only temporary stability, not correctness or authority.

## 2. Objective

Add a single paginator-owned contract that:

- assigns every paginated text layout a monotonic generation
- commits an exact section, chapter-local page, and page count atomically
- returns an immutable receipt for the actual committed position
- invalidates the receipt before any later pagination-affecting mutation
- lets consumers validate the receipt at their own commit boundary
- distinguishes exact success, trustworthy count mismatch, invalidation,
  cancellation, and unsupported flows
- causes stale work to restart or stop rather than capture or publish the wrong
  page

The delivery is complete when pagination profiling, passive raster preparation,
and live exact turns use this contract and no longer use host-side frame sampling
to decide whether a text page is committed.

## 3. Scope

### 3.1 In scope

- Foliate's reflowable paginated `Paginator`
- hidden pagination-profile measurement
- hidden passive preview and passive raster preparation
- live exact page-turn settlement
- generation-aware cache/profile publication
- validation of paginator receipts through the existing native presentation
  receipt before and after raster capture
- focused browser, Android host, and emulator acceptance
- vendor provenance updates for the local Foliate patch

### 3.2 Non-goals

- replacing Foliate or its pagination algorithm
- changing PlayLikeCurl geometry, texture ownership, or deformation
- changing fixed-layout EPUB, PDF, or scrolled reading behavior
- redesigning the Kotlin reader bridge when JS-local receipt validation suffices
- changing user-facing pagination or page numbering semantics
- optimizing layout speed, cache size, raster quality, memory residency, or UI
- unrelated paginator cleanup
- accepting a fixed delay, timeout, or repeated-frame equality as proof of
  layout commitment

The existing exact-navigation lock timeout remains a deadlock safeguard. It is
not a layout-readiness mechanism and must not be used to accept a page.

## 4. Authority Model

### 4.1 Layout generation

Each `Paginator` owns a monotonic `layoutGeneration`. The generation identifies
one version of the inputs and measured geometry that determine text-page
boundaries.

The paginator increments the generation **before** a pagination-affecting
mutation. Incrementing it immediately invalidates the active text-page receipt.
No consumer may retain authority from an older generation.

A generation is local to one paginator instance. It is runtime state, not a
persistent cache identity and not a cross-WebView identifier.

### 4.2 View generation

Each committed content `View` receives a monotonic `viewGeneration`. Replacing,
discarding, or destroying the iframe invalidates every receipt from the previous
view even if the section index happens to be the same.

### 4.3 Position commitment

Moving within an unchanged layout does not create a new layout generation, but
it invalidates the previous position receipt before scrolling. A successful
placement issues a new receipt with a monotonic `commitSequence`.

A receipt proves one actual position only. It does not assert that the cached
pagination profile was correct, that pixels have reached Android composition, or
that the receipt will remain valid after future layout work.

### 4.4 Presentation commitment

Paginator commitment and Android presentation commitment remain separate:

1. the paginator receipt proves DOM pagination and exact placement
2. the existing Navic presentation receipt proves the intended live or preview
   surface remained selected
3. passive `WebView.VisualStateCallback` and capture prove preview pixels reached
   the native capture boundary
4. live capture requires a PlayLikeCurl presented-frame callback queued after the
   destination deck activation and copies the matching region from that
   `PageSurfaceView`'s `Surface`, not from the Window backing surface

The renderer callback is presentation evidence, not page-position authority. Its
closure must remain bound to the acknowledged relocation token, Foliate session,
destination ordinal, raster generation, and texture generation. Capture retains
an initial presentation receipt before arming the callback, an equal final receipt
after `PixelCopy`, and the existing equal third receipt before handoff. Every
capture retry must arm a fresh renderer callback; fixed sleeps, Window frame
callbacks, and prior renderer events cannot substitute for this evidence.

For passive captures, the existing initial and final presentation-receipt checks
must also validate the associated paginator receipt. A receipt invalidated during
capture therefore rejects and recycles the candidate instead of caching it.

## 5. Invalidation Rules

The active receipt must be cleared before all operations that can change
paginated text geometry or the current text page.

### 5.1 Layout-generation invalidation

The paginator increments `layoutGeneration` before:

- creating, replacing, discarding, or destroying a content view
- committing a different section document
- applying a changed observed layout attribute, including flow, gap, content
  gap, margins, maximum sizes, column count, or column threshold
- applying publication or Navic typography/style text through `setStyles()`
- rendering because the paginator container or visual viewport changed size
- an explicit render that reapplies pagination geometry
- applying a `View.expand()` result whose measured layout signature differs from
  its previously applied signature
- applying a late font or body `ResizeObserver` expansion that changes the
  measured signature
- changing between paginated and scrolled flow
- destroying the paginator

The `View` layout signature must cover the measured values that determine page
identity, including flow mode, writing direction, page axis size, expanded
content size, and text page count. A duplicate observer delivery with an
identical signature is a no-op and must not invalidate a fresh receipt.

### 5.2 Position-only invalidation

The paginator clears the active receipt, without incrementing
`layoutGeneration`, before:

- exact text-page scrolling
- ordinary previous/next/snap navigation within the same layout
- anchor, selection, progress, or range navigation that can change the visible
  page

If the movement changes sections, the view/layout invalidation rules also apply.

### 5.3 Non-invalidating work

The following do not invalidate pagination by themselves:

- background-only color replacement that cannot affect document geometry
- overlayer redraws that do not participate in document layout
- Navic page texture, stain, edge, or page-number overlays outside the Foliate
  content document
- native bitmap decode, persistence, texture upload, or PlayLikeCurl settlement
- reading or validating a receipt

## 6. Paginator API

The migration adds the new API beside the existing Boolean API so unrelated
Foliate callers keep their behavior during staged delivery.

```javascript
await paginator.commitTextPage(index, pageIndex, reason)
paginator.validateTextPageCommit(receipt)
```

`goToTextPage()` remains available and delegates to `commitTextPage()`, returning
`true` only for `status === 'committed'`. It does not regain independent layout
logic.

### 6.1 Commit result

`commitTextPage()` always resolves to an immutable result for expected lifecycle
outcomes:

```text
TextPageCommitResult
  status: committed | mismatch | invalidated | cancelled | unsupported
  requestedIndex
  requestedPageIndex
  position: TextPagePosition | null
  receipt: TextPageCommitReceipt | null
  reason: privacy-safe enum
```

Meanings:

- `committed`: actual section and page equal the request; `receipt` is present.
- `mismatch`: an actual valid text page was committed, but it differs from the
  request; `position` and its receipt are present. This is the only non-success
  result whose page count may repair a pagination profile.
- `invalidated`: a layout/view generation changed before the operation could
  commit. No receipt is returned.
- `cancelled`: navigation was superseded, the paginator was destroyed, the
  caller's section was replaced, or exact navigation did not own the current
  operation. No receipt is returned.
- `unsupported`: the flow is scrolled, fixed-layout, PDF, or the renderer lacks
  the receipt API. No receipt is returned.

Programmer errors such as non-integer or negative arguments remain rejected
synchronously or by a thrown `TypeError`; they are not lifecycle statuses.

### 6.2 Receipt schema

```text
TextPageCommitReceipt
  layoutGeneration
  viewGeneration
  commitSequence
  flow: paginated
  index
  pageIndex
  pageCount
```

All numeric fields are finite non-negative integers; `pageCount` is positive and
`pageIndex < pageCount`. The receipt and nested result data are frozen.

Receipts are JS-local opaque capabilities. They must not be serialized into
persistent storage, used as raster-cache keys, or treated as valid in another
paginator instance.

### 6.3 Transaction sequence

For reflowable paginated text, `commitTextPage()` performs one serialized
transaction:

1. validate the request and acquire exact-navigation ownership
2. load and commit the requested section when needed
3. await the committed document's `fonts.ready`
4. verify navigation, view, and caller ownership after the await
5. apply one paginator-owned render/expand pass and record its generation
6. invalidate the prior position receipt and place the exact numeric page anchor
7. read the actual position from the same current view and layout generation
8. issue the actual position receipt
9. compare actual and requested coordinates and return `committed` or `mismatch`

If a generation/view/navigation check fails at any boundary, the method returns
`invalidated` or `cancelled` and never issues a receipt.

A late observer or font callback may still change layout after return. It must
invalidate the receipt before mutation. Consumers detect this through
`validateTextPageCommit()` and retry or reject their in-flight result.

### 6.4 Validation

`validateTextPageCommit(receipt)` returns `true` only when:

- the paginator is alive and paginated
- the argument is the currently active receipt
- layout, view, and commit generations still match
- the current committed view and section still match
- the paginator's current exact text position still equals receipt section,
  local page, and page count

Validation has no fallback to raw `page`, `pages`, a range anchor, cached profile
coordinates, or repeated samples.

### 6.5 Invalidation event

The paginator emits a privacy-safe `text-page-commit-invalidated` event whenever
an active receipt is invalidated. Event detail contains only:

- prior and current layout generation
- view generation
- commit sequence
- a bounded reason enum

It contains no publication URL, href, CFI, text, locator, or persistent reader
identifier. Consumers use the event to abandon ready state promptly; validation
remains the final authority.

## 7. Consumer Migration

### 7.1 Pagination profiler

For every readable section, the hidden profiler:

1. applies the target viewport/settings before exact navigation
2. calls `commitTextPage(sectionIndex, 0, 'pagination-profile')`
3. accepts the page count only from a `committed` result whose receipt validates
4. records the count immediately, then proceeds to the next section
5. fences status updates, profile publication, and cache writes by the active
   profile task token, publication URL ownership, and original render fingerprint

A setting, viewport, publication, close, or task replacement increments the task
token and destroys or supersedes stale profiler work. Stale work cannot publish
`ready`, alter `paginationProfile`, or write local storage.

The profiler no longer waits for frames, calls raw `exactTextPagePosition()`, or
forces a second host render after exact navigation.

### 7.2 Passive preview and raster preparation

The passive resolver applies layout before the transaction and calls
`commitTextPage()` for the locator. It stores both the locator and paginator
receipt in ready state.

- `committed` with a matching profile identity becomes ready.
- `mismatch` may repair a chapter count only when its receipt validates, the
  actual section equals the requested section, and the count differs. The global
  locator is rebuilt and retried, with the existing maximum of two repairs.
- `invalidated` retries the same active batch item under the fresh generation,
  with a bounded maximum of three transaction attempts.
- `cancelled` returns silently when the preview/batch token is stale; otherwise
  it fails the active item.
- `unsupported` bypasses passive paginated-text raster preparation.

Ready preview state becomes invalid immediately when its paginator receipt is
invalidated. Expose, presentation confirmation, raster descriptor access, and
both presentation-receipt checks must validate it. Invalid candidates are never
persisted, decoded, uploaded, or advanced as completed batch items.

The existing native initial/final presentation-receipt checks provide the
cross-WebView capture fence; no new Kotlin bridge payload is required.

### 7.3 Live exact turns

A live exact turn calls `commitTextPage()` after applying layout once, before the
transaction. Settlement stores the returned paginator receipt with the existing
Foliate session, raster generation, texture generation, pagination profile, and
one-shot settlement token.

A live turn can settle only when:

- the result is `committed`
- the receipt validates
- receipt coordinates equal the destination locator
- the current Navic location maps to the same global page
- existing session/generation/token ownership checks pass

The live presentation target retains the paginator receipt. Issuing or reading a
live presentation receipt validates it, so native capture rejects a page that
was invalidated after settlement.

After Foliate acknowledges the exact destination, live validation queries the
initial receipt, arms `PageSurfaceView.requestNextPresentedFrame()`, and captures
only after that generation-current callback. GL queue ordering places the frame
request after destination deck activation. `PixelCopy` reads the PlayLikeCurl
surface region corresponding to the exact WebView page box; `PixelCopy(Window)`
is forbidden because the topmost `GLSurfaceView` is composed independently of the
Window/ViewRoot backing surface. A stale callback invalidates without capture,
and an internal or coordinator retry must obtain a new renderer event.

A trusted same-section `mismatch` may invoke the same bounded profile repair and
remap used by the passive path, then retry. Invalidated work retries only while
the exact-turn token remains current. Superseded turns remain one-shot and do not
publish stale settlement acknowledgements.

### 7.4 Legacy stabilization removal

After all three consumers migrate:

- delete `navic-reader-pagination-stability.js`
- remove its runtime asset and test-fixture entries
- replace sampling-focused tests with paginator receipt tests
- retain `exactTextPagePosition()` only as a diagnostic API if other code still
  reads it; it is not a commitment authority

## 8. Cache And Profile Policy

Set pagination render metadata to:

```text
runtimeVersion = navic-reader-pagination-profile-3
```

This invalidates profile-2 local-storage entries. Because pagination and layout
fingerprints participate in raster identity, rasters derived from pre-receipt
profiles are not reused under the new fingerprint.

Runtime `layoutGeneration`, `viewGeneration`, and `commitSequence` never enter
persistent keys. They would destroy valid reuse and are meaningful only inside
one live paginator.

A repaired profile may be persisted only from a currently valid mismatch receipt
and only while publication, task, and fingerprint ownership remain current.

## 9. Flow And Format Behavior

- Reflowable paginated EPUB uses the complete receipt contract.
- Scrolled and scrolled-gaps flows return `unsupported` and retain their existing
  navigation/location behavior.
- Fixed-layout EPUB and PDF bypass text-page receipts and retain their renderer-
  specific page authority.
- Cover/synthetic page handling remains outside Foliate text-page ordinals.
- Changing into or out of a supported paginated flow invalidates all receipts.

No unsupported format may be reported as a failed pagination transaction merely
because it does not implement text-page receipts.

## 10. Failure And Recovery

Expected invalidation is recoverable control flow, not an exception shown to the
user.

- Active profiler invalidation restarts the affected section or the current
  profile task; stale tasks stop.
- Active passive invalidation retries the current batch item without advancing
  the cursor.
- Active live invalidation retries while the same settlement token owns the turn.
- Repeated invalidation beyond the bounded attempt limit fails with a specific
  privacy-safe stage/reason and preserves the current visible page.
- A trusted count mismatch repairs/remaps at most twice.
- A section mismatch, missing receipt, invalid receipt, or untrusted page count
  never repairs the profile.
- Errors must preserve existing live/passive composition restoration and bitmap
  ownership cleanup.

No recovery path may accept the nearest page, clamp silently, use a stable but
wrong page, or continue from a stale receipt.

## 11. Diagnostics And Privacy

Structured diagnostics may record:

- operation type: profile, passive-preview, passive-raster, or live-turn
- bounded invalidation/retry reason
- layout/view/commit generation numbers
- requested and actual numeric section/local-page/page-count coordinates
- batch/profile/settlement generation numbers
- receipt validation outcome
- retry and profile-repair counts

Diagnostics must not record:

- EPUB text or raster pixels/payloads
- URLs, hrefs, CFIs, publication/book IDs, or reusable locators
- selected text, annotations, credentials, transcript content, or user identity
- serialized receipts in persistent logs or cache manifests

Existing privacy-safe preprocessing progress remains sufficient for emulator
acceptance. Raw screenshots, recordings, and derivatives remain local under
`.codex-validation` and are not committed or released.

## 12. Delivery Stages

### Stage 1 - Paginator receipt API

Ship the additive Foliate API, focused transaction tests, and vendor provenance.
Legacy consumers still run. The checkpoint proves generation increment,
invalidation, exact receipt creation, mismatch reporting, and unsupported-flow
behavior without changing visible reader behavior.

### Stage 2 - Profile and passive delivery

Migrate profile measurement and passive preview/raster preparation. Bump the
profile schema and remove passive dependence on frame sampling. The checkpoint
must preprocess books and prepare raster batches without accepting invalid or
adjacent pages.

### Stage 3 - Live exact-turn delivery

Migrate live exact settlement and presentation receipts. Preserve all existing
session, raster, texture, relocation, and one-shot token fences. The checkpoint
must perform consecutive exact turns and chapter transitions without stale
settlements.

### Stage 4 - Legacy removal and specification audit

Delete the unused stabilization module, update runtime asset/source contracts,
run focused tests, and audit every requirement in this document. Fix gaps before
interactive acceptance.

### Stage 5 - Emulator acceptance and production release

Use only `emulator-5554`. Load several test books one at a time, inspect
privacy-safe preprocessing logs, automate 20 forward page turns per acceptance
run, and include chapter transitions. Publish a signed production release only
if all acceptance gates pass.

Each stage ends in a focused green checkpoint and a pushed commit. No stage
includes unrelated cleanup or micro-optimization.

## 13. Test Requirements

### 13.1 Browser/unit tests

Tests must prove:

- exact section/page/count returns a valid immutable receipt
- a different actual page returns `mismatch`, never `committed`
- out-of-range navigation cannot silently clamp to success
- changed layout attributes invalidate before render
- changed `View.expand()` metrics invalidate while duplicate observer delivery
  does not
- container and visual viewport resize invalidate
- style/font completion invalidates stale work and the transaction commits only
  after current font/layout work
- section replacement, navigation supersession, and destroy return no receipt
- ordinary page movement invalidates the prior receipt without pretending the
  persistent layout fingerprint changed
- receipt validation compares current identity and coordinates
- scrolled mode returns `unsupported`
- a larger trusted count repairs/remaps and retries
- a shorter trusted count remaps the global destination before capture
- profile repair rejects invalid, stale, or different-section receipts
- passive ready/presentation state rejects an invalidated receipt
- live settlement/presentation rejects an invalidated receipt
- stale profile, preview, batch, and exact-turn tokens publish nothing

### 13.2 Android host/source tests

Tests must prove:

- packaged assets contain the new paginator API and no legacy stabilization
  import after final migration
- source ordering applies viewport layout before the paginator transaction and
  does not reapply it after exact commitment
- profiler page counts come only from a validated receipt
- passive raster readiness requires a validated receipt
- live exact settlement and presentation require a validated receipt
- native presented-surface capture retains its initial/final receipt checks
- live validation arms a fresh generation-current PlayLikeCurl presented-frame
  callback before each capture attempt
- live `PixelCopy` reads the mapped region from the PlayLikeCurl `Surface`; tests
  reject Window capture, capture before the callback, and stale callback ownership
- fixed-layout, PDF, and scrolled paths remain bypassed
- runtime asset imports and vendor manifest hashes are complete

### 13.3 Focused regression suites

At minimum run:

- reader harness pagination transaction/profile tests
- reader harness relocation/settlement tests
- `ReaderPageTurnDestinationSourceTest`
- `ReaderRuntimeAssetsTest`
- passive raster preparation/descriptor/content-ready tests
- presentation receipt and bridge protocol tests affected by the migration
- reader vendor source and packaged-asset verification

Unrelated pre-existing suite failures must be reported accurately. Touched suites
must pass completely.

## 14. Emulator Acceptance

Acceptance uses only `emulator-5554`. Do not use, alter, clear logs on, or install
to a phone or tablet for this delivery gate. Do not stop the emulator.

For each of several test books, one at a time:

1. Open the book from a clean reader session.
2. Wait for preprocessing to complete through the normal UI.
3. Inspect privacy-safe logs for every readable section measured once, a complete
   profile, and passive raster progress without uncommitted-position failures.
4. Confirm there is no profile repair loop, invalidation retry loop, adjacent-page
   acceptance, stale batch publication, or protected-content logging.
5. Trigger 20 forward page turns through the supported reader automation path.
6. Confirm every accepted turn settles on its intended global page exactly once.
7. Ensure at least one run crosses a chapter boundary; verify section/local-page
   progression remains coherent and the next chapter starts once.
8. Confirm no passive raster failure, crash surface, black/cover flash, stuck
   preparation state, or broken interaction occurs.

Retain only privacy-safe logs and local visual evidence under
`.codex-validation`. Do not OCR, print, or persist publication text or reusable
publication identifiers.

## 15. Acceptance Gates

The implementation is ready to ship only when all are true:

1. Foliate is the only authority that issues text-page commitment.
2. Every accepted exact result has a currently valid receipt matching section,
   local page, and page count.
3. Any layout/view/page mutation invalidates the prior receipt before mutation.
4. Profile counts are recorded only from validated receipts.
5. Passive rasters are captured and persisted only while the same receipt remains
   valid through native presentation capture.
6. Live settlement and capture require the same valid receipt, existing native
   ownership tokens, a fresh generation-current PlayLikeCurl presented-frame
   event, and `PixelCopy` from the PlayLikeCurl surface owner.
7. No host-side frame sampling or raw fallback is used as commitment authority.
8. Trusted count mismatch recovery is bounded; untrusted mismatch never repairs.
9. Stale tasks cannot publish profile, preview, raster, location, settlement, or
   cache state.
10. Scrolled, fixed-layout, and PDF behavior is unchanged or explicitly bypassed.
11. Focused browser, Android, and vendor tests pass.
12. Several books preprocess successfully on `emulator-5554`.
13. Twenty automated forward turns complete without failure, including a chapter
    transition.
14. No protected publication content or identifiers appear in retained logs or
    committed artifacts.
15. The release APK is built by GitHub Actions with the persistent release
    certificate and independently verified before publication is declared done.

## 16. Release Policy

- Intermediate checkpoints are committed and pushed but are not production
  releases.
- The first production release follows only the completed specification audit
  and emulator acceptance.
- Version metadata is changed in a separate release commit after acceptance.
- Existing release tags and assets remain immutable.
- The release must use certificate SHA-256
  `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
- Downloaded release evidence must verify version metadata, APK digest, signing
  certificate, vendor governance, and acknowledgements.
