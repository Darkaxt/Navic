# Komikku Reader Port — Status Audit

Date: 2026-06-17
Companion to: `2026-06-13-komikku-reader-port-design.md`
Purpose: Record the **verified** current state of the port, flag where the design spec's claims are aspirational rather than enforced, and give Codex concrete next work. This is not a diary; it is an audit + work plan.

## Audit Method

Side-by-side comparison of:
- The refactored design spec (`2026-06-13-komikku-reader-port-design.md`, 159 lines).
- The active source tree under `composeApp/src/**` (production + tests).
- The reference clones at `tmp/references/komikku` and `tmp/references/anx-reader`.
- The validation log (`2026-06-13-komikku-reader-port-validation-log.md`).

Claims were checked by reading the real reference source, the real Navic implementation, and the guard tests — not by trusting the spec's self-reporting.

## Section 1 — Spec Claims That Are VERIFIED TRUE

These are backed by code and by guard tests that read reference source from disk and assert parity.

| Spec claim | Evidence |
| --- | --- |
| Komikku is authoritative for shell/input/chrome/progress/settings | 43 Komikku reference anchors in `composeApp/src/**/test/*.kt` and `KomikkuViewerNavigation.kt:4-5` cite `tmp/references/komikku/...` |
| `KomikkuReaderNavigator` uses ported Komikku region semantics | `KomikkuViewerNavigation.kt:69-92` matches `ViewerNavigation.kt:34-54` line-for-line: same `constantMenuRegion (0,0,1,0.05)`, same `0.33/0.25` sizes, same ARGB region colors, same inversion modes, same `getAction` fallback |
| Native frame owns short taps Komikku-style (child-first + `onSingleTapConfirmed`) | `KomikkuReaderNativeFrameHost.android.kt:419-430` calls `super.dispatchTouchEvent` first then `gestureDetector.onTouchEvent`; `:328` fires only from `onSingleTapConfirmed`; `:619` defines `KomikkuGestureDetectorWithLongTap` with `onLongTapConfirmed` + `HapticFeedbackConstants.LONG_PRESS` — matches `Pager.kt:46-79` |
| No ACTION_UP short-tap workaround | `KomikkuReaderNativeFrameHost.android.kt:404-406` ACTION_UP only consumes `if (nativeSwipeIntercepted)`. `ReaderKomikkuBackboneResetTest.kt:759-763` negatively asserts `nativeShortTapIntercepted` is absent |
| `ReaderController` owns menu/shell-cover/chapter-progress/TOC/bookmarks/settings/progress-save | Verified in `ReaderController.kt`, `ReaderCoordinator.kt`, and locked by `ReaderControllerTest.kt`, `ReaderCoordinatorTest.kt` |
| Foliate runtime is wrapped, not owning chrome | `FoliateEpubEngineAdapter.kt:50-120` is a pure typed translator; `ReaderKomikkuBackboneResetTest.kt:1434-1465` locks raw `ReaderBridgeCommand`/`ReaderBridgeEvent` out of controller/chrome |
| Guards read Komikku source and reject divergence | `ReaderRuntimeCommonChromeTest.kt:485-528` loads `ChapterNavigator.kt` from `tmp/references/komikku/`, asserts Navic contains the same tokens (`rotationZ = 90f`, `Icons.Outlined.SkipPrevious`, etc.), and negatively asserts the duct-tape alternative (`Canvas(`, `alpha = 0.01f`) is absent |
| Component extraction is real | `ReaderRoot.kt`, `ReaderAppBars.kt`, `ReaderSettingsDialog.kt`, `ReaderContentsDialog.kt`, `ReaderContentOverlay.kt`, `ReaderChapterNavigator.kt`, `ReaderNavigation.kt`, `ReaderViewerHost.kt`, `ReaderOpenRequest.kt`, `ReaderPaginationProfileBadge.kt` all exist; `ReaderScreen.kt` is app-boundary coordinator |

**Conclusion for Section 1:** The Komikku-frontend port is faithful and guardrail-enforced. This is the opposite of duct tape. Codex can trust the spec's Komikku claims.

## Section 2 — Spec Claims That Are ASPIRATIONAL, Not Enforced

This is the gap the user has repeatedly flagged. The spec lists Anx/Foliate as authoritative for EPUB/PDF engine capabilities (design spec line 35), but that authority is **not enforced** anywhere in code.

| Spec claim | Reality | Evidence |
| --- | --- | --- |
| Anx Reader/Foliate is authoritative for EPUB/PDF rendering, pagination, search, content actions, annotations, media/readaloud hooks, bridge behavior | **0 Anx reference anchors** in `composeApp/src/**/*.{kt,js}`. `FoliateEpubEngineAdapter.kt` (186 lines) has no reference citation. `FoliateEpubEngineAdapterTest.kt` locks Navic's own typed mapping, not Anx parity. The real EPUB/PDF logic lives in Navic's pre-existing fork under `vendor/foliate-js/` and `navic-reader.js`, with no tie to Anx's `epub_player.dart` or `foliate-js/src/view.js` |
| Foliate runtime remains the EPUB/PDF engine | True, but it is **Navic's existing Foliate fork**, not an Anx-derived adapter. The event names resemble Anx's callback catalog (Relocated, Toc, Selection, SearchResults, ContentActionClaimed, MediaOverlayActive/Inactive) but nothing proves they match Anx's contract |

**The duct-tape pattern is confined to the engine/middleware side.** Codex wrapped the existing Foliate integration in a clean typed boundary (good architecture) and gave events Anx-shaped names (cosmetic), but did not anchor the implementation to Anx's source the way it anchored the shell to Komikku's source.

Layer rule now recorded in the active spec: Komikku owns the UI/control surface; Anx owns reader behavior. Therefore the Anx gaps below are not optional feature ideas. They are failing behavior parity until implemented or explicitly documented as deliberate, guarded divergences.

**Concrete gap — no guard tests verify Anx parity for:**
- Relocation payload shape (`anx-reader/assets/foliate-js/src/view.js:115-194` vs Navic's `ReaderBridgeEvent.LocationChanged`).
- Link/image/view-click taxonomy (`view.js:216-327` vs `ReaderBridgeEvent.ContentTapHandled` claim metadata).
- Annotation hooks (`view.js:335-397` vs `ReaderEngineCommand.ApplyAnnotations`).
- Bridge event catalog (`anx-reader/lib/page/book_player/epub_player.dart:667-804` vs `ReaderBridgeEvent` enum).
- Style dimensions (`anx-reader/lib/models/book_style.dart:4-17` vs `ReaderSettings`).
- PDF integration (`anx-reader/assets/foliate-js/src/pdf.js:568-614` vs Navic's bundled `vendor/foliate-js/pdf.js`).
- Font sources (`anx-reader/lib/providers/fonts.dart` vs `ReaderImportedFont`).

## Section 3 — Debt Marked In Prose But Not Anchored In Code

The full log honestly marked these as stopgaps, but the markers live only in the archived design doc (`archive/2026-06-13-komikku-reader-port-design-full-log.md`), not at the call sites. A future agent editing these files will not see the debt marker.

| Stopgap | Prose location | Code location missing the marker |
| --- | --- | --- |
| Readable drag delegation is "the correct hybrid-reader stopgap, not the final Komikku endpoint" | Full log line ~4580 | `KomikkuReaderNativeFrameHost.android.kt` drag-handling region |
| Drag preview underlay is "a pragmatic bridge... not the final page-curl or dual-page spread implementation" | Full log line ~7105 | `navic-reader.js` `previewPageDrag` / `updatePageDragPreviewLayer` |
| Readaloud runtime host "still translates typed overlay commands back to bridge commands locally because that old host has not been removed yet" | Full log line ~1460 | `ReaderReadaloudRuntimeHost.android.kt` |
| "The remaining Compose-hosted WebView bridge is an implementation gap, not the final model" | Full log line ~647 | `ReaderEngineWebViewHost.android.kt` |

**Risk:** One compaction away from losing these markers. The refactored spec dropped them because they were diary entries, but the code-level debt is still real.

## Section 4 — Verified Current Runtime State

From the validation log + my audit of the 2026-06-17 dirty emulator check:

- **Rail endpoint math:** FIXED at host level. `reflowablePaginatedTextPageCount(pages)` uses Foliate's `pages - 2` sentinel-column contract. Dirty emulator reported `11 / 11` for Chapter 1 endpoint. Host guard: `ReaderRuntimeShellProgressTest.androidReaderReportsDynamicReflowablePagePositionToChrome`.
- **Page-1 rail chapter buttons:** FIXED at host level. Chapter 1 page-1 lower button dispatches `goToHref(...capitancebolleta02.xhtml)` → Chapter 2; Chapter 2 page-1 upper button dispatches `goToHref(...capitancebolleta01.xhtml)` → Chapter 1. Host guard: `ReaderRuntimeShellProgressTest.androidReaderChapterRailSeekCommitsWithControlledReasonInsteadOfPassiveClamp`.
- **Cover bottom-menu:** Host guard added (`ReaderRuntimeCommonChromeTest.commonReaderBottomMenuDoesNotRenderOverShellCover`, asserts `showBottomBar = visible && !controllerState.shellCoverVisible`).
- **Controlled relocation reason:** Fixed. `beginControlledRelocation(reason)` / `consumeControlledRelocationReason(fallback)` prevent passive clamping from rewriting explicit chapter rail seeks.
- **TOC href navigation:** Fixed. `androidReaderResolvesTocHrefNavigationBeforeCommittingLocation`.
- **Host suite:** `paige.navic.reader.*` = 402 tests, 0 failures (per full log line ~7272). `ReaderKomikkuBackboneResetTest` = 38/0.

**NOT yet device/release validated:**
- Clean release candidate behavior (dirty emulator only).
- Physical device behavior (Samsung `SM_F966B` / `RFCY80551LT`).
- Resume persistence after app/window disruption.
- Drag preview black-void behavior on device.
- Cover chrome layering on installed APK.

## Section 5 — Concrete Next Work For Codex

Ordered by the spec's Implementation Order (design spec lines 108-115), with concrete acceptance criteria.

### Work Item 1 — Validate rail + cover fixes on clean RC (Priority 0, spec line 110)

**Do:** Build `v1.0.11-eta70` (or next), install on `emulator-5554` via `scripts\install-reader-dev.ps1`, run `scripts\adb-reader-komikku-matrix.ps1 -NoLaunch -IncludeCoverChecks -ContinueOnFailure`. Then install on physical device `RFCY80551LT` if available and repeat.

**Acceptance:**
- Chapter 1 endpoint reports `11 / 11` on the clean APK (not just dirty build).
- Page-1 lower rail button navigates to Chapter 2; page-1 upper rail button navigates back.
- Cover center tap toggles chrome; bottom menu does not appear over cover.
- Results appended to `2026-06-13-komikku-reader-port-validation-log.md`.

### Work Item 2 — Fix resume/persistence after disrupted drag (Priority 0, spec line 91, 111)

**Root cause (from full log):** Progress is not persisted on committed reading-location changes; only on clean exit. A disrupted drag (system gesture nav) loses the last position.

**Do:** Persist `ReaderCoordinatorStep.progressToSave` through `BinderyRepository.putReadingProgress(...)` on every committed relocation, not just on screen exit. The boundary already exists (`ReaderCoordinatorStepConsumer`, `ReaderScreen` app-boundary sink). The bug is the *trigger frequency*, not the plumbing.

**Acceptance:**
- After a disrupted drag + reopen, the reader returns to the last committed page, not page 1.
- Host guard proves progress-save fires on committed relocation events, not only on dispose.
- No `BinderyRepository` reference leaks into `paige.navic.reader.*` (existing boundary rule).

### Work Item 3 — Fix drag preview black void + texture movement (Priority 1, spec lines 96-97, 112)

**Do:** The clipped adjacent-section underlay exists in `navic-reader.js` (`previewPageDrag` / `updatePageDragPreviewLayer` / `nativeDragPreviewAtSectionBoundary`) and passed the browser harness, but is not device-validated. Validate on device; if the black void persists, the underlay is not mounting on the Android WebView path.

**Acceptance:**
- During a readable-page drag at a section boundary, the next section is visible (not black) before release.
- Texture movement follows the page movement axis across Contents → Maps → Author's Note → Chapter I.
- `reader-texture-direction-validation.txt` shows `wrongTextureDirection=0` for both `next` and `previous` walks.

### Work Item 4 — Anchor mandatory Anx/Foliate behavior parity (Priority 0 acceptance gate, spec Reference Authority and Implementation Order)

**This is the work item that closes the user's repeated concern.** The spec says Komikku is the UI layer and Anx is the behavior layer; the code does not yet enforce the Anx half. Add the same guard pattern that Komikku already has, but for Anx. Missing Anx callbacks/events are failing behavior parity, not optional polish.

**Do:** Add `FoliateAnxParityTest.kt` (or extend `FoliateEpubEngineAdapterTest.kt`) that reads Anx source from `tmp/references/anx-reader/` and asserts parity. Specifically:

1. **Relocation payload:** Read `assets/foliate-js/src/view.js:115-194`. Assert Navic's `ReaderBridgeEvent.LocationChanged` carries the same fields Anx/Foliate emits (section progress, TOC/page progress, CFI, renderer page counts). Add the missing fields if any.
2. **Content-action taxonomy:** Read `view.js:216-327`. Assert `ReaderBridgeEvent.ContentTapHandled` distinguishes the same cases Anx distinguishes (internal link, external link, image click, view click) with the same metadata.
3. **Bridge event catalog:** Read `lib/page/book_player/epub_player.dart:667-804`. Assert every callback Anx registers has a corresponding `ReaderBridgeEvent` / `ReaderEngineEvent`. No missing events unless the divergence is source-cited, tested, and justified.
4. **Annotation hooks:** Read `view.js:335-397`. Assert `ReaderEngineCommand.ApplyAnnotations` maps to Anx's `addAnnotation`/`removeAnnotation` contract (id, type, CFI, color, note).
5. **Style dimensions:** Read `lib/models/book_style.dart:4-17`. Assert `ReaderSettings` covers the same dimensions (font size/family/weight, line height, letter/word spacing, paragraph spacing, margins, indent, max column count, heading scale, column threshold). Add missing dimensions.
6. **PDF integration:** Read `assets/foliate-js/src/pdf.js:568-614`. Assert `FoliatePdfEngineAdapter` exposes the same capabilities (sections, outline/TOC resolution, page lookup, cover) as Anx's `makePDF`.
7. **Font sources:** Read `lib/providers/fonts.dart:16-103`, `lib/service/font.dart:22-24`, `lib/models/font_model.dart:28-33`. Assert `ReaderImportedFont` covers remote manifest, local import, and WebView-accessible URL.

**Also do:** Add source citations at the top of `FoliateEpubEngineAdapter.kt`, `FoliateWebViewEngineAdapter` (if separate), `ReaderBridgeProtocol.kt`, and `navic-reader.js` — the same way `KomikkuViewerNavigation.kt:4-5` cites its reference.

**Acceptance:**
- `FoliateAnxParityTest.kt` is green and reads `tmp/references/anx-reader/` from disk (not hardcoded strings).
- Each Anx-derived file has a `// tmp/references/anx-reader/...` citation comment.
- A deliberate divergence in any of the 7 areas above makes the test red.
- The test follows the Komikku pattern: `firstOrNull { it.isFile }?.readText() ?: error("Could not locate Anx reference")` so it fails loudly if the clone is missing.

### Work Item 5 — Anchor stopgap markers in code (Priority 2, hygiene)

**Do:** Add `// TEMPORARY ADAPTER:` comments at the four call sites listed in Section 3, each with: the reference gap, the removal path, and a pointer to this audit doc. This protects the debt markers from compaction.

**Acceptance:**
- `KomikkuReaderNativeFrameHost.android.kt` drag region has a `// TEMPORARY ADAPTER: hybrid stopgap, final = native pager` comment.
- `navic-reader.js` `previewPageDrag` has a `// TEMPORARY ADAPTER: pragmatic bridge, final = page-curl/dual-page` comment.
- `ReaderReadaloudRuntimeHost.android.kt` has a `// TEMPORARY ADAPTER: legacy bridge translation, removal = controller-only readaloud` comment.
- `ReaderEngineWebViewHost.android.kt` has a `// TEMPORARY ADAPTER: Compose-hosted WebView bridge, final = concrete native viewer` comment.

### Work Item 6 — Continue Komikku UI parity (Priority 2, spec lines 103-106, 113)

**Do:** Settings overlay density/scroll treatment, paper texture visibility, move dev-only reader options to Developer Options. These are lower priority and should follow Items 1-4.

**Acceptance:** Per spec acceptance criteria (design spec lines 130-142).

## Section 6 — What The Refactored Spec Got Right

Codex can trust the refactored spec for:
- Objective, reference authority split, guardrails — accurate.
- Current architecture — accurately describes the extracted component boundaries.
- Current verified state — accurately reflects the 2026-06-17 dirty emulator check.
- Active bugs — accurately reflects the bottom-of-full-log state.
- Implementation order — reasonable and matches original priority.
- Emulator validation gate — correct procedure.
- Acceptance criteria — corrected on 2026-06-17 to require Anx bridge/event/payload counterparts.

The previous soft spot was listing Anx as "authoritative" without saying that authority was not yet enforced. The active spec now records that Komikku owns UI and Anx owns behavior, and Work Item 4 is the mandatory guardrail work that makes that statement true in code.

## Section 7 — File Pointers

- Design spec: `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- Validation log: `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`
- Full historical log: `docs/superpowers/specs/archive/2026-06-13-komikku-reader-port-design-full-log.md`
- This audit: `docs/superpowers/specs/2026-06-17-komikku-reader-port-status-audit.md`
- Komikku reference: `tmp/references/komikku/`
- Anx reference: `tmp/references/anx-reader/`
- Key Komikku guard: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt:485-528`
- Key parity guard: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderKomikkuBackboneResetTest.kt:759-784`
- Adapter needing Anx anchors: `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt`
- Native frame: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
