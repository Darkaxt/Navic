# Reader Paginator Commit Receipts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Foliate the sole authority for exact reflowable text-page commitment, then require its generation-scoped receipt through pagination profiling, passive raster capture, and live exact page turns.

**Architecture:** Add an immutable receipt transaction beside Foliate's current Boolean API, migrate one delivery path at a time, and preserve the existing native presentation/session/raster/texture fences. Runtime receipts remain opaque JS-local state; persistent profile and raster identity continues to use stable render fingerprints, with a one-time profile schema bump.

**Tech Stack:** Browser-native JavaScript modules, Foliate Web Components, Node `node:test`, Playwright Chromium, Kotlin Android host tests, Android WebView, PowerShell vendor/release governance, Gradle, ADB.

**Approved specification:** `docs/superpowers/specs/2026-08-04-reader-paginator-commit-receipts-design.md`

**Implementation baseline:** `c21e86f1` (`936e68d5` production code plus the approved specification commit)

---

## Delivery Rules

- Execute in the existing `navic-playlist-pattern-fix` worktree. Do not reset,
  clean, restore, amend, or overwrite paused work.
- Preserve `.codex-validation/` and keep all visual/runtime evidence there.
- Complete each stage as a working checkpoint, commit it, and push it to
  `fork/master` before beginning the next stage.
- Do not include unrelated paginator cleanup, performance tuning, cache tuning,
  UI changes, or renderer changes.
- Every delegated agent must be told not to spawn subagents.
- Use TDD: create the focused failing case, verify the intended failure, make the
  smallest production change, and rerun the focused gate.
- Touched test suites must pass completely. Report unrelated pre-existing suite
  failures without weakening the focused gates.
- Do not serialize, log, or persist paginator receipts.
- Do not log publication text, URLs, hrefs, CFIs, book IDs, reusable publication
  identifiers, raster payloads, annotations, selected text, credentials, or
  Whispersync content.
- Use only `emulator-5554` for interactive acceptance. Do not touch a phone or
  tablet, clear Logcat, or stop the emulator.
- Publish a production release only after the specification audit and emulator
  gates pass.

## File Map

### Create

- `composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js`
  — reader-neutral commit-result handling and JS-local receipt ownership.
- `tools/reader-harness/src/paginator-commit-receipt.test.mjs`
  — real-browser paginator generation and transaction tests.
- `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
  — profile, passive, and live receipt-consumer tests.

### Modify

- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js`
- `composeApp/src/androidMain/assets/reader/vendor/manifest.json`
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- `composeApp/src/androidMain/assets/reader/navic-reader.js`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchController.android.kt`
  only if receipt invalidation during native capture needs same-item repolling.
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetTestFixtures.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- focused raster/presentation tests under
  `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/`
- `tools/reader-harness/src/presentation-receipt.test.mjs`
- `scripts/test-reader-relocation-bridge.mjs`
- `tools/reader-harness/package.json`

### Delete after every consumer has migrated

- `composeApp/src/androidMain/assets/reader/navic-reader-pagination-stability.js`
- `tools/reader-harness/src/pagination-stability.test.mjs`

### Release-only modification

- `androidApp/build.gradle.kts`

---

## Stage 1: Deliver Paginator Commitment Authority

### Task 1: Define the failing paginator transaction contract

**Files:**

- Create: `tools/reader-harness/src/paginator-commit-receipt.test.mjs`
- Modify: `tools/reader-harness/package.json`

- [ ] **Step 1: Add a Playwright-backed paginator fixture**

  Launch Chromium from `node:test`, load a local page, import the packaged
  `paginator.js`, and construct a synthetic reflowable book whose sections load
  same-origin HTML blobs. The fixture must expose the real custom element rather
  than a mock of its pagination methods.

  Use this shape for each synthetic section:

  ```javascript
  const section = html => ({
    linear: 'yes',
    async load() {
      return URL.createObjectURL(new Blob([html], { type: 'text/html' }))
    },
    unload() {},
  })
  ```

  Revoke fixture blob URLs during teardown and close Chromium after the file's
  tests.

- [ ] **Step 2: Add exact commit and immutability tests**

  Assert the future API resolves this contract:

  ```javascript
  const result = await paginator.commitTextPage(0, 1, 'test-exact')

  assert.equal(result.status, 'committed')
  assert.deepEqual(result.position, {
    index: 0,
    pageIndex: 1,
    pageCount: result.position.pageCount,
  })
  assert.equal(result.receipt.index, 0)
  assert.equal(result.receipt.pageIndex, 1)
  assert.equal(result.receipt.pageCount, result.position.pageCount)
  assert.equal(result.receipt.flow, 'paginated')
  assert.equal(paginator.validateTextPageCommit(result.receipt), true)
  assert.equal(Object.isFrozen(result), true)
  assert.equal(Object.isFrozen(result.position), true)
  assert.equal(Object.isFrozen(result.receipt), true)
  ```

  Also assert a structurally identical copied receipt is invalid; receipt object
  identity is part of authority.

- [ ] **Step 3: Add mismatch and unsupported-flow tests**

  Request a page beyond the measured section and assert the result is
  `mismatch`, never `committed`, with a receipt only when the actual position is
  valid. Switch to `flow="scrolled"` and assert `unsupported`, no position, and
  no receipt.

- [ ] **Step 4: Add argument and lifecycle tests**

  Assert negative, fractional, infinite, and non-integer section/page arguments
  reject with `TypeError`. Assert section replacement, navigation supersession,
  and paginator destruction return `cancelled` or `invalidated` with no receipt.

- [ ] **Step 5: Add invalidation tests**

  Prove the active receipt is invalidated before each mutation class required by
  Specification Section 5:

  - changed observed layout attribute;
  - `setStyles()` content change;
  - explicit `render()`;
  - container or visual-viewport resize;
  - changed `View.expand()` signature;
  - late font/body expansion;
  - exact and ordinary page movement;
  - view replacement and destroy.

  Assert a duplicate observer delivery with the same measured signature is a
  no-op. Position-only movement must invalidate the receipt without advancing
  `layoutGeneration`.

- [ ] **Step 6: Add transaction-order and event-privacy tests**

  Delay `document.fonts.ready` and assert commitment does not resolve until the
  current view owns the font/layout result. Capture
  `text-page-commit-invalidated` and assert its detail keys are exactly:

  ```javascript
  [
    'commitSequence',
    'layoutGeneration',
    'previousLayoutGeneration',
    'reason',
    'viewGeneration',
  ]
  ```

  Assert the reason is a bounded enum and the serialized event contains no URL,
  href, CFI, locator, text, or publication identity.

- [ ] **Step 7: Register and run the red suite**

  Add:

  ```json
  "test:paginator-commit-receipt": "node --test src/paginator-commit-receipt.test.mjs"
  ```

  Run:

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-receipt
  ```

  Expected result: failure because `commitTextPage` and
  `validateTextPageCommit` do not exist.

### Task 2: Implement Foliate layout generations and receipts

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js`

- [ ] **Step 1: Add private authority state**

  Add these fields to `Paginator`:

  ```javascript
  #layoutGeneration = 0
  #viewGeneration = 0
  #commitSequence = 0
  #activeTextPageCommitReceipt = null
  ```

  Add one `View` layout-signature field covering flow, writing direction, page
  axis size, expanded content size, and text page count.

- [ ] **Step 2: Add centralized invalidation methods**

  Implement private methods equivalent to:

  ```javascript
  #invalidateTextPageCommit(reason) { /* clear active receipt; emit if present */ }
  #advanceTextLayoutGeneration(reason) { /* invalidate first, then increment */ }
  ```

  Event reasons must come from a fixed internal set. Do not use caller-provided
  navigation text as an invalidation reason.

- [ ] **Step 3: Put layout invalidation before mutation**

  Route view create/discard/destroy, changed attributes, changed styles,
  container/visual resize render, explicit render, section commit, and changed
  expansion metrics through `#advanceTextLayoutGeneration()` before changing DOM
  geometry. Compare observed old/new attribute values and style text so no-op
  assignments do not create generations.

- [ ] **Step 4: Make `View.expand()` signature-aware**

  Compute the next signature before writes. If it equals the applied signature,
  return without DOM writes or reanchoring. When it differs, notify the paginator
  before mutation, apply the dimensions, record the signature, and then reanchor
  only the still-current committed view.

- [ ] **Step 5: Put position invalidation before movement**

  Clear active position authority before exact anchors, range/selection/progress
  anchors, previous/next/snap, direct paginated scroll, and touch page movement.
  Do not advance `layoutGeneration` for same-layout movement.

- [ ] **Step 6: Implement `commitTextPage()`**

  Add:

  ```javascript
  async commitTextPage(index, pageIndex, reason = 'navigation')
  ```

  Follow Specification Section 6.3 exactly: validate integer arguments; return
  `unsupported` for scrolled flow; acquire exact-navigation ownership; load the
  section; await current-document fonts; recheck view/navigation ownership; run
  one paginator-owned render/expand; place the numeric anchor; recheck generation;
  read the actual position; issue a frozen receipt; return `committed` only when
  actual coordinates equal the request, otherwise `mismatch`.

  Result reasons are bounded values such as `exact-position`,
  `coordinate-mismatch`, `layout-invalidated`, `navigation-superseded`,
  `section-replaced`, `paginator-destroyed`, and `unsupported-flow`.

- [ ] **Step 7: Implement receipt validation**

  Add:

  ```javascript
  validateTextPageCommit(receipt)
  ```

  Return true only for the active receipt object while paginator, flow, layout,
  view, section, current local page, and page count still match.

- [ ] **Step 8: Convert the Boolean API into a compatibility wrapper**

  Replace independent exact-page logic with:

  ```javascript
  async goToTextPage(index, pageIndex, reason = 'navigation') {
    const result = await this.commitTextPage(index, pageIndex, reason)
    return result.status === 'committed'
  }
  ```

- [ ] **Step 9: Run the paginator suite until green**

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-receipt
  ```

  Expected result: all paginator transaction tests pass.

### Task 3: Lock source contracts and vendor governance

**Files:**

- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- Modify: `composeApp/src/androidMain/assets/reader/vendor/manifest.json`

- [ ] **Step 1: Add source-order assertions**

  Assert the paginator source contains the new API and that:

  - `goToTextPage()` delegates without independent layout logic;
  - invalidation precedes each mutation;
  - font readiness precedes transaction render and exact placement;
  - receipt issuance follows actual-position reading;
  - duplicate expansion signatures are no-ops;
  - scrolled flow is unsupported.

- [ ] **Step 2: Run the focused Android tests**

  ```bash
  ./gradlew.bat :composeApp:testAndroidHostTest \
    --tests "paige.navic.reader.ReaderRuntimeAssetsTest" \
    --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
  ```

- [ ] **Step 3: Regenerate and verify Foliate provenance**

  Update the paginator component's `localChanges` description to name
  generation-scoped exact text-page receipts, then run:

  ```bash
  pwsh -NoProfile -File scripts/update-reader-vendor-manifest.ps1
  pwsh -NoProfile -File scripts/test-reader-vendor-assets-verifier.ps1
  pwsh -NoProfile -File scripts/verify-reader-vendor-assets.ps1
  ```

- [ ] **Step 4: Check and commit Stage 1**

  ```bash
  git diff --check
  git status --short
  git add composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js \
    composeApp/src/androidMain/assets/reader/vendor/manifest.json \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt \
    tools/reader-harness/src/paginator-commit-receipt.test.mjs \
    tools/reader-harness/package.json
  git commit -m "feat(reader): add paginator commit receipts" \
    -m "Co-Authored-By: Claude <noreply@anthropic.com>"
  git push fork master
  ```

**Stage 1 delivery:** The additive paginator API and its governance are shipped;
existing reader consumers still behave as before.

---

## Stage 2: Deliver Profile And Passive Raster Receipts

### Task 4: Add the reader-neutral commit adapter

**Files:**

- Create: `composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js`
- Create: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
- Modify: `tools/reader-harness/package.json`

- [ ] **Step 1: Write unsupported and validation tests**

  Test these exports:

  ```javascript
  readerCommitTextPage(renderer, index, pageIndex, reason)
  readerTextPageCommitIsValid(renderer, result)
  readerTextPageCommitMatches(result, expected)
  readerRememberTextPageCommit(owner, renderer, receipt)
  readerTextPageCommitOwnerIsValid(owner)
  readerForgetTextPageCommit(owner)
  ```

  A renderer without the receipt API must return a frozen `unsupported` result
  with reason `receipt-api-unavailable`; it must never fall back to `page`,
  `pages`, `exactTextPagePosition()`, ranges, or frame sampling.

- [ ] **Step 2: Write JS-local ownership tests**

  Store `{ renderer, receipt }` in a module-local `WeakMap` keyed by a frozen
  runtime owner object. Assert `JSON.stringify(owner)` contains no receipt,
  layout generation, view generation, or commit sequence.

- [ ] **Step 3: Verify the red test**

  Register:

  ```json
  "test:paginator-commit-consumers": "node --test src/paginator-commit-consumers.test.mjs"
  ```

  Run:

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  ```

  Expected result: module-not-found failure.

- [ ] **Step 4: Implement the minimal adapter and rerun**

  The adapter validates frozen result shape and delegates authority exclusively
  to `renderer.validateTextPageCommit(receipt)`. Rerun until its focused tests
  pass.

### Task 5: Migrate and fence pagination profiling

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader.js`
- Modify: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`

- [ ] **Step 1: Add failing profiler ownership cases**

  Cover:

  - one valid page-zero receipt records one section count;
  - mismatch, missing receipt, invalid receipt, and stale task record nothing;
  - publication or render-fingerprint replacement after an await publishes
    nothing;
  - stale work cannot assign `paginationProfile`, write local storage, post
    `ready`, or post a location snapshot;
  - each readable section commits exactly once;
  - profile-2 cache data misses after the schema bump;
  - runtime receipt generations never enter persistent profile JSON or keys.

- [ ] **Step 2: Add explicit task ownership methods**

  Implement runtime methods equivalent to:

  ```javascript
  paginationProfileTaskIsCurrent({ token, url, fingerprint })
  invalidatePaginationProfileTask(reason)
  ```

  Call invalidation before publication replacement, close, settings/layout
  fingerprint change, and starting a replacement profile task.

- [ ] **Step 3: Replace profile sampling with page-zero transactions**

  Apply viewport/settings before each section, then call:

  ```javascript
  const result = await readerCommitTextPage(
    profileView.renderer,
    index,
    0,
    'pagination-profile'
  )
  ```

  Record `result.position.pageCount` only for `committed` plus immediate receipt
  validation and current task ownership. Do not call `profileView.goTo()` first,
  force a second host render afterward, or sample frames.

- [ ] **Step 4: Fence every side effect**

  Recheck token, publication URL, and original fingerprint before progress status,
  profile assignment, cache write, ready status, and location snapshot. Scrolled,
  fixed-layout, and PDF profiling must bypass without a failure status.

- [ ] **Step 5: Bump the stable cache schema**

  Change only:

  ```javascript
  runtimeVersion: 'navic-reader-pagination-profile-3'
  ```

  Do not add runtime layout/view/commit generations to fingerprints or cache
  keys.

- [ ] **Step 6: Run profile consumer tests**

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  ```

### Task 6: Migrate passive preview and raster preparation

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Modify: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
- Modify: `tools/reader-harness/src/presentation-receipt.test.mjs`
- Modify only if required by the red integration test:
  `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchController.android.kt`

- [ ] **Step 1: Replace old passive mocks with receipt results**

  Rewrite the existing larger-count, exact-anchor, and shorter-count cases to
  mock `commitTextPage()` plus `validateTextPageCommit()`. Keep each current
  regression as a required green case.

- [ ] **Step 2: Add failing trust-boundary cases**

  Assert:

  - a valid same-section `mismatch` may repair a changed chapter count;
  - a different-section, missing, stale, or invalid receipt never repairs;
  - larger and shorter counts rebuild the global locator and retry;
  - invalidation retries the same active batch item at most three transaction
    attempts without advancing its cursor;
  - stale preview/batch tokens publish nothing;
  - unsupported flow bypasses passive text raster preparation.

- [ ] **Step 3: Return locator plus opaque commitment**

  Replace `resolvePageTurnPreviewLocator()` internals with a receipt transaction.
  Apply the hidden viewport layout before the transaction and never after it.
  Return an internal object containing locator, actual position, receipt,
  transaction-attempt count, and profile-repair count. Keep repair capped at two.

- [ ] **Step 4: Bind receipts to ready states without serialization**

  Bind the passive ready state and active batch state to the receipt through the
  shared WeakMap helper. Do not put the receipt or generation fields into the
  enumerable state returned to Kotlin or diagnostics.

- [ ] **Step 5: Validate at every passive boundary**

  Require current ownership at ready-state access, preview expose, presentation
  confirmation, preview presentation-receipt lookup, raster descriptor lookup,
  and batch advancement. If invalidated, clear presentation authority and
  restart the same active item while attempts remain.

- [ ] **Step 6: Preserve native initial/final capture validation**

  Extend `presentation-receipt.test.mjs` to prove
  `pageTurnPreviewPresentationReceipt()` returns null when its paginator receipt
  is stale. The existing native capture already queries the presentation receipt
  before and after bitmap capture; do not remove or weaken those checks.

  Only if the red integration case shows native code turns this expected stale
  rejection into a permanent batch failure, update
  `ReaderPageRasterBatchController.android.kt` to repoll the same token/cursor.
  Other capture failures retain their existing cleanup and failure policy.

- [ ] **Step 7: Run passive-focused suites**

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  npm --prefix tools/reader-harness run test:presentation-receipt
  ./gradlew.bat :composeApp:testAndroidHostTest \
    --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" \
    --tests "paige.navic.reader.ReaderRuntimeAssetsTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterPreparationSourceTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterDescriptorTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterBatchOutcomeTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest"
  ```

### Task 7: Register runtime assets and commit Stage 2

**Files:**

- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetTestFixtures.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [ ] **Step 1: Register the new helper module**

  Add `navic-reader-paginator-commit.js` to packaged runtime/import checks and all
  test fixture concatenation lists. Keep the old stability module registered
  until Stage 4 because live code has not yet migrated.

- [ ] **Step 2: Lock profile/passive source contracts**

  Assert viewport layout precedes the transaction, no layout application follows
  exact commitment, profile counts come only from validated receipts, and passive
  ready/presentation state requires receipt validation.

- [ ] **Step 3: Run the Stage 2 checkpoint**

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-receipt
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  npm --prefix tools/reader-harness run test:presentation-receipt
  node scripts/test-reader-relocation-bridge.mjs
  ./gradlew.bat :composeApp:testAndroidHostTest \
    --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" \
    --tests "paige.navic.reader.ReaderRuntimeAssetsTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterPreparationSourceTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterDescriptorTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterBatchOutcomeTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest"
  git diff --check
  ```

- [ ] **Step 4: Commit and push Stage 2**

  ```bash
  git add composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js \
    composeApp/src/androidMain/assets/reader/navic-reader-pagination.js \
    composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js \
    composeApp/src/androidMain/assets/reader/navic-reader-appearance.js \
    composeApp/src/androidMain/assets/reader/navic-reader-viewport.js \
    composeApp/src/androidMain/assets/reader/navic-reader.js \
    composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchController.android.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetTestFixtures.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterPreparationSourceTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterDescriptorTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterBatchOutcomeTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSourceTest.kt \
    tools/reader-harness/src/paginator-commit-consumers.test.mjs \
    tools/reader-harness/src/presentation-receipt.test.mjs \
    tools/reader-harness/package.json
  git commit -m "feat(reader): gate profiles and passive rasters on commit receipts" \
    -m "Co-Authored-By: Claude <noreply@anthropic.com>"
  git push fork master
  ```

**Stage 2 delivery:** New profiles and passive rasters are derived only from
Foliate receipts; live exact turns retain the old path until Stage 3.

---

## Stage 3: Deliver Live Exact-Turn Receipts

### Task 8: Write failing live settlement and presentation cases

**Files:**

- Modify: `scripts/test-reader-relocation-bridge.mjs`
- Modify: `tools/reader-harness/src/paginator-commit-consumers.test.mjs`
- Modify: `tools/reader-harness/src/presentation-receipt.test.mjs`

- [ ] **Step 1: Replace Boolean exact-navigation mocks**

  Return frozen `committed`, `mismatch`, `invalidated`, and `cancelled` results
  with an associated validating renderer.

- [ ] **Step 2: Add exact settlement cases**

  Prove:

  1. valid exact receipt settles once;
  2. receipt/locator coordinate mismatch does not settle;
  3. current global location mismatch does not settle;
  4. invalidation retries while the same token owns the turn;
  5. attempt exhaustion preserves the visible page and emits no acknowledgement;
  6. trusted larger/shorter count mismatch repairs/remaps and retries;
  7. untrusted mismatch never repairs;
  8. superseded token publishes no stale location or settlement;
  9. invalidation after settlement makes live presentation lookup return null;
  10. consecutive turns and a chapter transition preserve existing Foliate
      session, raster generation, texture generation, profile, relocation, and
      one-shot token fences;
  11. serialized bridge/settlement state contains no paginator receipt fields.

- [ ] **Step 3: Run the red suites**

  ```bash
  node scripts/test-reader-relocation-bridge.mjs
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  npm --prefix tools/reader-harness run test:presentation-receipt
  ```

  Expected result: live exact navigation still expects a Boolean and does not
  retain receipt authority.

### Task 9: Migrate live exact navigation and capture authority

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader.js`

- [ ] **Step 1: Commit the exact page through the shared adapter**

  Keep the existing synchronous live layout application before navigation, then
  call the receipt API. Do not reapply layout afterward. Keep the exact-turn
  settlement token active across bounded invalidation and trusted profile-repair
  retries.

- [ ] **Step 2: Bind receipt authority to live owners**

  Bind the pending settlement, completed settlement, and live presentation target
  objects to `{ renderer, receipt }` through the shared WeakMap. Keep all receipt
  fields out of native bridge payloads.

- [ ] **Step 3: Require receipt validity for settlement**

  Update `exactPageTurnSettlementMatches()`,
  `maybeCompleteNativePageTurnSettlement()`, and related completion paths so a
  turn settles only when the receipt validates and its section/local page/count
  equal the final locator, while all existing native ownership fields also match.

- [ ] **Step 4: Require receipt validity for live presentation**

  Update `pageTurnLivePresentationTargetMatchesCurrent()`,
  `restorePageTurnLivePresentationReceipt()`, and
  `pageTurnLivePresentationReceipt()` to reject invalid paginator authority. This
  automatically preserves the native bitmap source's initial/final receipt fence.

- [ ] **Step 5: Cancel authority on live invalidation**

  Attach one listener to the active live paginator after `view.open()`. When an
  associated receipt invalidates, clear live presentation authority and cancel or
  restart only the exact operation that still owns its token. Remove the listener
  on publication replacement and close.

- [ ] **Step 6: Run the live green checkpoint**

  ```bash
  node scripts/test-reader-relocation-bridge.mjs
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  npm --prefix tools/reader-harness run test:presentation-receipt
  ./gradlew.bat :composeApp:testAndroidHostTest \
    --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" \
    --tests "paige.navic.reader.ReaderJavascriptBridgeTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest"
  git diff --check
  ```

- [ ] **Step 7: Commit and push Stage 3**

  ```bash
  git add composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js \
    composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js \
    composeApp/src/androidMain/assets/reader/navic-reader.js \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderJavascriptBridgeTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSourceTest.kt \
    tools/reader-harness/src/paginator-commit-consumers.test.mjs \
    tools/reader-harness/src/presentation-receipt.test.mjs \
    scripts/test-reader-relocation-bridge.mjs
  git commit -m "feat(reader): gate live exact turns on commit receipts" \
    -m "Co-Authored-By: Claude <noreply@anthropic.com>"
  git push fork master
  ```

**Stage 3 delivery:** Profile, passive raster, and live exact navigation now share
one Foliate commitment authority.

---

## Stage 4: Remove Sampling And Validate The Specification

### Task 10: Delete obsolete host-side stabilization

**Files:**

- Delete: `composeApp/src/androidMain/assets/reader/navic-reader-pagination-stability.js`
- Delete: `tools/reader-harness/src/pagination-stability.test.mjs`
- Modify: imports in pagination/preview modules
- Modify: `tools/reader-harness/package.json`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetTestFixtures.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`

- [ ] **Step 1: Remove runtime and fixture references**

  Delete the module and its package script, imports, fixture entries, and
  sampling-specific source assertions.

- [ ] **Step 2: Add negative authority assertions**

  Assert profile/passive/live consumers do not use
  `readerWaitForStableTextPagePosition`, `readerExactTextPagePosition`, raw
  `page/pages`, or repeated animation frames as exact commitment authority.
  `exactTextPagePosition()` may remain only inside `paginator.js` and its
  diagnostic-focused transaction tests.

- [ ] **Step 3: Run source audits**

  ```bash
  git grep -n "readerWaitForStableTextPagePosition\|readerExactTextPagePosition\|navic-reader-pagination-stability" -- composeApp tools scripts
  git grep -n "navic-reader-pagination-profile-2" -- composeApp tools scripts
  git grep -n "exactTextPagePosition" -- composeApp/src/androidMain/assets/reader
  ```

  Expected: first two commands return no production/runtime hits; profile-2 is
  absent; `exactTextPagePosition` remains only in paginator diagnostics.

### Task 11: Audit every specification requirement

**Files:**

- Modify only files needed to close an identified requirement gap.
- Record local evidence under `.codex-validation/paginator-receipts/spec-audit/`.

- [ ] **Step 1: Build the requirement matrix**

  Create a local, uncommitted matrix mapping each acceptance gate in
  Specification Section 15 to concrete source and a passing test. The matrix must
  explicitly cover invalidation-before-mutation, trusted mismatch repair, stale
  task suppression, format bypass, privacy, and non-serialization.

- [ ] **Step 2: Run all focused JS gates**

  ```bash
  npm --prefix tools/reader-harness run test:paginator-commit-receipt
  npm --prefix tools/reader-harness run test:paginator-commit-consumers
  npm --prefix tools/reader-harness run test:presentation-receipt
  npm --prefix tools/reader-harness run test:page-turn-model
  node scripts/test-reader-relocation-bridge.mjs
  ```

- [ ] **Step 3: Run all focused Android gates**

  ```bash
  ./gradlew.bat :composeApp:testAndroidHostTest \
    --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" \
    --tests "paige.navic.reader.ReaderRuntimeAssetsTest" \
    --tests "paige.navic.reader.ReaderJavascriptBridgeTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterPreparationSourceTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterDescriptorTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageRasterBatchOutcomeTest" \
    --tests "paige.navic.ui.screens.reader.ReaderPageTurnBitmapSourceTest"
  ```

- [ ] **Step 4: Verify governance and a packaged ReaderDev build**

  ```bash
  pwsh -NoProfile -File scripts/test-reader-vendor-assets-verifier.ps1
  pwsh -NoProfile -File scripts/verify-reader-vendor-assets.ps1
  pwsh -NoProfile -File scripts/verify-third-party-attributions.ps1
  ./gradlew.bat :androidApp:assembleReaderDev
  pwsh -NoProfile -File scripts/verify-reader-vendor-assets.ps1 \
    -ApkPath androidApp/build/outputs/apk/readerDev/Navic.apk
  pwsh -NoProfile -File scripts/verify-third-party-attributions.ps1 \
    -ApkPath androidApp/build/outputs/apk/readerDev/Navic.apk
  ```

- [ ] **Step 5: Review scope and formatting**

  ```bash
  git diff --check c21e86f1..HEAD
  git diff --stat c21e86f1..HEAD
  git status --short
  ```

  Confirm no unrelated cleanup, micro-optimization, UI work, protected evidence,
  or receipt serialization entered the changes.

- [ ] **Step 6: Fix requirement gaps before continuing**

  A missing matrix entry or failing focused gate blocks emulator acceptance. Add
  the smallest missing test and implementation, rerun its stage, and update the
  local matrix. Do not waive a requirement.

- [ ] **Step 7: Commit and push Stage 4**

  ```bash
  git add composeApp/src/androidMain/assets/reader/navic-reader-pagination-stability.js \
    composeApp/src/androidMain/assets/reader/navic-reader-pagination.js \
    composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetTestFixtures.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt \
    composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt \
    tools/reader-harness/src/pagination-stability.test.mjs \
    tools/reader-harness/package.json
  git commit -m "test(reader): remove pagination stability sampling" \
    -m "Co-Authored-By: Claude <noreply@anthropic.com>"
  git push fork master
  ```

**Stage 4 delivery:** The implementation has no legacy sampling authority and is
validated requirement-by-requirement against the approved specification.

---

## Stage 5: Emulator Acceptance And Production Release

### Task 12: Claim and verify the authorized emulator

**Files:** None committed.

- [ ] **Step 1: Establish thread-scoped device ownership**

  This implementation thread owns only `emulator-5554` for the acceptance run.
  Do not issue state-changing ADB commands to any other serial.

- [ ] **Step 2: Verify the emulator without stopping it**

  ```bash
  adb devices -l
  adb -s emulator-5554 get-state
  ```

  Require `device`. If unavailable, pause acceptance without touching another
  device.

- [ ] **Step 3: Install the exact audited ReaderDev APK**

  ```bash
  pwsh -NoProfile -File scripts/install-reader-dev.ps1 \
    -DeviceSerial emulator-5554 \
    -NoBuild \
    -NoDiscoverPublication \
    -PreserveLogcat
  ```

  Do not clear Logcat, kill the emulator, or install to a phone/tablet.

### Task 13: Preprocess several books one at a time

**Files:** Local evidence only under `.codex-validation/paginator-receipts/`.

- [ ] **Step 1: Select three reflowable test books through ReaderDev**

  Use locally available test-account publications at runtime, one at a time:

  - one ordinary multi-section book;
  - one book with publication fonts;
  - one with a chapter boundary reachable within 20 forward turns.

  Keep their titles, URLs, IDs, hrefs, and other reusable identities out of logs,
  commands saved in evidence, filenames, and the final report. Refer to them only
  as A, B, and C.

- [ ] **Step 2: Open Book A and wait for normal preprocessing**

  Use the normal ReaderDev launch/UI path. Do not inject internal profile state.
  Capture only privacy-safe diagnostics:

  ```bash
  pwsh -NoProfile -File scripts/adb-reader-smoke.ps1 \
    -Package darkaxt.navic.readerdev \
    -DeviceSerial emulator-5554 \
    -NoLaunch \
    -PreserveLogcat \
    -CaptureReaderDiagnostics \
    -PrivacySafeEvidence \
    -RequireNoReaderConsoleErrors \
    -ArtifactDir .codex-validation/paginator-receipts/book-a-preprocess
  ```

- [ ] **Step 3: Check Book A preprocessing logs**

  Confirm one complete profile, each readable section measured once, passive
  raster progress without uncommitted-position failure, no profile-repair or
  invalidation retry loop, no stale batch publication, and no protected content
  or reusable publication identity in retained output.

- [ ] **Step 4: Repeat independently for Books B and C**

  Close the current reader through the normal UI, open only the next book, and
  use separate `book-b-preprocess` and `book-c-preprocess` artifact directories.
  Do not batch multiple books into one reader session.

### Task 14: Automate 20 forward turns and cross a chapter boundary

**Files:**

- `scripts/adb-reader-smoke.ps1`
- `tools/reader-harness/src/adb-webview-eval.mjs`
- `tools/reader-harness/src/paginator-commit-receipt-acceptance.mjs`
- Local evidence under `.codex-validation/paginator-receipts/`

- [ ] **Step 1: Use the condition-based exact-settlement probe**

  Do not use `-RequireReaderLog page-turn:exact-settled`: `readerTrace()` is
  JS-local and is not a Logcat event. Use the exclusive privacy-safe probe. It
  installs a bounded in-memory trace sink, projects only numeric exact-settlement
  state, injects one forward tap, and waits for that settlement to be consumed
  before injecting the next tap. Fixed delays are not commitment authority.

- [ ] **Step 2: Run 20 accepted forward turns for each book**

  With one book open and preprocessing complete:

  ```powershell
  pwsh -NoProfile -File scripts/adb-reader-smoke.ps1 `
    -Package darkaxt.navic.readerdev `
    -DeviceSerial emulator-5554 `
    -NoLaunch `
    -PreserveLogcat `
    -PrivacySafeEvidence `
    -VerifyPaginatorCommitReceipts `
    -ArtifactDir .codex-validation/paginator-receipts/book-a-turns
  ```

  Repeat with `book-b-turns` after opening Book B independently. For Book C,
  add `-RequirePaginatorChapterTransition` and use `book-c-turns`.

- [ ] **Step 3: Validate exact settlements**

  For each run, require `paginator-commit-receipts.json` to be the only retained
  artifact. It must report exactly 20 accepted forward settlements, intended
  monotonic global destinations, no malformed/dropped/duplicate receipt, and no
  terminal state. The driver waits for both pending and settled native bridge
  state to drain after each accepted page before continuing.

- [ ] **Step 4: Validate the chapter transition**

  Book C must report at least one numeric chapter-index change. Global page must
  remain monotonic, and passive preparation/settlement must continue without a
  duplicate or skipped chapter transition. No href, CFI, URL, title, text, book
  ID, raster data, screenshot, Logcat dump, or reusable reader identity may enter
  the retained summary.

- [ ] **Step 5: Keep evidence private and local**

  Store screenshots/recordings only if needed under `.codex-validation`. Do not
  OCR them, commit them, upload them, add them to release assets, or quote
  protected content.

### Task 15: Prepare and publish the signed production release

**Files:**

- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Determine the next unused release identity**

  At the acceptance checkpoint, confirm `fork/master`, tags, and release list.
  From the current baseline the expected next values are:

  ```kotlin
  versionCode = 576
  versionName = "v1.0.11-iota49"
  ```

  If that tag or version was independently consumed while this plan ran, use the
  next monotonically increasing unused code/name; never overwrite an existing
  tag or asset.

- [ ] **Step 2: Change only version metadata**

  Update `androidApp/build.gradle.kts`, then run the repository release-version
  verifier with the selected version name.

- [ ] **Step 3: Commit and push release metadata**

  ```bash
  git add androidApp/build.gradle.kts
  git commit -m "chore(release): prepare v1.0.11-iota49" \
    -m "Co-Authored-By: Claude <noreply@anthropic.com>"
  git push fork master
  ```

  Substitute the selected next name in the commit if iota49 was already used.

- [ ] **Step 4: Publish through the guarded release script**

  Run in the foreground so completion is event-driven and visible:

  ```bash
  pwsh -NoProfile -File scripts/publish-github-release.ps1 \
    -Tag v1.0.11-iota49 \
    -Repo Darkaxt/Navic \
    -Remote fork \
    -Branch master \
    -AllowPublicRelease \
    -ReleaseReadinessNote "Paginator commit receipts passed focused specification audit and privacy-safe multi-book emulator acceptance with 20 forward turns and a chapter transition."
  ```

  Use the selected next tag if iota49 was unavailable. Do not clobber or modify
  an existing release.

### Task 16: Independently verify the production artifact

**Files:** Local release evidence only.

- [ ] **Step 1: Download the immutable APK once**

  ```bash
  TAG=v1.0.11-iota49
  RELEASE_DIR=.codex-validation/paginator-receipts/release/$TAG
  mkdir -p "$RELEASE_DIR"
  gh release download "$TAG" --repo Darkaxt/Navic --pattern '*.apk' --dir "$RELEASE_DIR"
  ```

- [ ] **Step 2: Verify APK identity and signing**

  Use Android SDK build-tools `apksigner` and `aapt` to verify:

  - package `darkaxt.navic`;
  - selected version name/code;
  - release certificate SHA-256
    `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`;
  - local APK SHA-256 matches GitHub's release digest.

- [ ] **Step 3: Verify packaged governance**

  ```bash
  pwsh -NoProfile -File scripts/verify-reader-vendor-assets.ps1 -ApkPath "$RELEASE_DIR/Navic.apk"
  pwsh -NoProfile -File scripts/verify-third-party-attributions.ps1 -ApkPath "$RELEASE_DIR/Navic.apk"
  ```

- [ ] **Step 4: Verify Git ancestry and immutable release state**

  Confirm the release tag peels to the release metadata commit, all four staged
  implementation commits and the specification commit are ancestors, the
  workflow succeeded, and the release contains the production APK only.

- [ ] **Step 5: Report the shipped delivery**

  Report the tag/release URL, workflow URL, APK SHA-256, version code/name,
  certificate SHA-256, focused test results, number of emulator books, 20-turn
  results, and chapter-transition result. Report skipped or failed gates exactly;
  do not claim shipment if any acceptance gate remains unresolved.

---

## Final Completion Checklist

- [ ] Paginator transaction tests pass in a real browser.
- [ ] Profile, passive raster, and live exact turns accept only validated receipts.
- [ ] All receipt invalidation rules occur before mutation.
- [ ] Trusted count repair is bounded; untrusted mismatch cannot repair.
- [ ] Runtime receipt fields never serialize, log, or enter persistent keys.
- [ ] Profile runtime schema is `navic-reader-pagination-profile-3`.
- [ ] Legacy stability sampling is deleted from production and fixtures.
- [ ] Scrolled, fixed-layout, PDF, cover, and synthetic-page paths bypass safely.
- [ ] Focused JS, Android host, vendor, and packaged ReaderDev gates pass.
- [ ] Specification matrix covers all 15 acceptance gates.
- [ ] Several books preprocess one at a time on `emulator-5554`.
- [ ] Every acceptance run completes 20 forward turns.
- [ ] At least one run crosses a chapter transition without duplication or breakage.
- [ ] No phone/tablet was touched, Logcat was not cleared, and the emulator was not stopped.
- [ ] Local evidence remains under `.codex-validation` and uncommitted.
- [ ] Every implementation stage is committed and pushed to `fork/master`.
- [ ] The production release is signed by the persistent certificate and independently verified.
