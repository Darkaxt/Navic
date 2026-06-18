# Anx/Foliate Middleware — Complaint Brief for Codex

Date: 2026-06-17
Companion to: `2026-06-17-komikku-reader-port-status-audit.md`
Purpose: Expand on the audit's "Do not trust the Anx-middleware claims at face value" finding with the exact evidence Codex needs to act on. This is the prioritized grievance list.

## The Core Grievance

The design spec (`2026-06-13-komikku-reader-port-design.md:35`) declares:

> Anx Reader/Foliate is authoritative for EPUB/PDF rendering, pagination, search, content actions, annotations/highlights, media/readaloud hooks, and WebView/Foliate bridge behavior.

User ruling recorded after review: Komikku is responsible for the UI layer; Anx is responsible for reader behavior. That means every Anx bridge callback/event/payload is required for behavior parity unless Navic documents a deliberate, guarded divergence.

But that authority is **not enforced anywhere in code**. My grep across `composeApp/src/**/*.{kt,js}` for `tmp/references/anx`, `anx-reader/lib`, `foliate-js/src/view.js`, `epub_player.dart`, `book_style.dart` returned **0 matches in production code** and **0 matches in test code**. Every Anx citation lives only inside the design doc.

Meanwhile the Komikku side has **43 reference anchors** in guard tests that read Komikku source from disk and assert line-for-line parity (e.g., `ReaderRuntimeCommonChromeTest.kt:485-528` loads `ChapterNavigator.kt` and requires Navic to contain `rotationZ = 90f`, `Icons.Outlined.SkipPrevious`, etc.).

So the port is **asymmetric**: Komikku is a real guardrail; Anx is a label. Codex wrapped Navic's pre-existing Foliate fork in a clean typed boundary (good architecture) and gave the events Anx-shaped names (cosmetic), but never anchored the implementation to Anx's contract the way it anchored the shell to Komikku.

## Grievance 1 — `FoliateEpubEngineAdapter` has no reference citation

**Evidence:**
- `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt` (186 lines) — grep for `tmp/references/anx|anx-reader|foliate-js/src|epub_player.dart` → **0 matches**.
- Contrast: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/KomikkuViewerNavigation.kt:4-5` cites `tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt` and `viewer/navigation/*.kt`.

**Why it matters:** Without a citation, a future agent editing the adapter has no pointer to the reference contract. The Komikku files self-document their source; the Foliate adapter does not.

**Fix:** Add `// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:667-804` (callback catalog) and `// tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194` (relocation payload) at the top of `FoliateEpubEngineAdapter.kt`, `FoliateWebViewEngineAdapter`, `ReaderBridgeProtocol.kt`, and `navic-reader.js`.

## Grievance 2 — The adapter test locks Navic's own mapping, not Anx parity

**Evidence:** `FoliateEpubEngineAdapterTest.kt:220-320` (`mapsBridgeEventsToEngineEventsWithoutLettingBridgeOwnChrome`) asserts `ReaderBridgeEvent.X` → `ReaderEngineEvent.X` round-trips. It never reads `tmp/references/anx-reader/...`. It proves the boundary is clean; it does **not** prove the event taxonomy matches Anx.

**Why it matters:** If the adapter diverges from Anx's callback contract (missing events, wrong payload shape), no test catches it. The Komikku side has `ReaderRuntimeCommonChromeTest.kt:485-528` reading Komikku source and asserting parity; the Anx side has no equivalent.

**Fix:** Add a `FoliateAnxParityTest.kt` that reads `tmp/references/anx-reader/lib/page/book_player/epub_player.dart` and `assets/foliate-js/src/view.js` from disk and asserts parity. Use the same pattern as the Komikku guards:
```kotlin
val anxViewText = listOf(
    File("tmp/references/anx-reader/assets/foliate-js/src/view.js"),
    File("../tmp/references/anx-reader/assets/foliate-js/src/view.js")
).firstOrNull { it.isFile }?.readText() ?: error("Could not locate Anx view.js reference")
```

## Grievance 3 — Required Anx bridge events are missing entirely

**Evidence:** Anx `epub_player.dart:628-821` registers 13 callbacks. Navic `ReaderBridgeEvent` (`ReaderBridgeProtocol.kt:363-399`) covers ~7 of them. The gaps:

| Anx callback | Anx source | Navic event | Gap |
| --- | --- | --- | --- |
| `onExternalLink` | `epub_player.dart:673` | Collapsed into `ContentTapHandled(action=Link)` | **Missing:** external vs internal link distinction. Anx emits `external-link` (`view.js:223`) and `link` (`view.js:226`) as separate events with `{ a, href }`. Navic collapses both into one `ContentTapHandled`. |
| `onSelectionCleared` | `epub_player.dart:716` | None | **Missing:** Navic has `SelectionChanged` but no "cleared" event. Selection-clear UI state has no engine signal. |
| `onAnnotationClick` | `epub_player.dart:726` | None | **Missing:** `view.js:380` emits `show-annotation { value, index, range }`. Navic has `ApplyAnnotations` as a command but no annotation-click **event** back. Users cannot tap an existing highlight to open its note. |
| `onPushState` | `epub_player.dart:790` | None | **Missing:** `view.js:443` does `history.pushState({ fraction })`. Navic has no history/push-state event. Back-stack navigation inside EPUB is not engine-fed. |
| `onFootnoteClose` | `epub_player.dart:815` | None | **Missing:** footnote close has no event. |
| `onPullUp` | `epub_player.dart:821` | None | **Missing:** pull-up (continuous scroll end) has no event. |
| `draw-annotation` | `view.js:360` | None | **Missing:** `view.js:360` emits `draw-annotation { draw, annotation, doc, range }`. Navic has no annotation-draw event, so the controller cannot know when Foliate rendered a highlight. |
| `load` | `view.js:211` | `PublicationReady` (no payload) | **Partial:** Anx carries `{ doc, index }`; Navic's `PublicationReady` is a bare object. The controller cannot know which doc/index loaded. |

**Why it matters:** These are not cosmetic. External-link handling, annotation click-to-edit, in-book back-stack, and footnote UX are all acceptance criteria in the spec (`2026-06-13-komikku-reader-port-design.md:114`: "Restore/extend Anx/Foliate reader capabilities behind the controller boundary: PDF browsing, EPUB search, annotations/highlights..."). They cannot be restored correctly without the events Anx already emits.

**Fix:** For each missing event, add a `ReaderBridgeEvent` variant, decode it in `decodeReaderBridgeEvent`, map it through `FoliateWebViewEngineAdapter.onBridgeEvent`, and add a `ReaderEngineEvent` variant. Add a parity guard that reads `epub_player.dart:628-821` and asserts every `handlerName:` has a corresponding `ReaderBridgeEvent` unless a deliberate divergence is source-cited, tested, and justified.

## Grievance 4 — Relocation payload is thinner than Anx's

**Evidence:**
- Anx `view.js:175-194` `#onRelocate({ reason, range, index, fraction, size })` builds `lastLocation = { ...progress, tocItem, pageItem, cfi, range, chapterLocation }` and emits `relocate` with that full payload.
- Navic `ReaderBridgeProtocol.kt:374-377` `LocationChanged(locator: ReaderLocator, tocTitle: String?)`. `ReaderLocator` (`:22-31`) has `href, cfi, progress, pageIndex, pageCount, chapterProgress, chapterPageIndex, chapterPageCount`.

**Missing from Navic's relocation payload:**
- `range` — the DOM range Anx carries; needed for precise annotation/selection positioning.
- `chapterLocation` — Anx's richer chapter location object (distinct from `chapterProgress`).
- `pageItem` — Anx's page item (distinct from `pageIndex`; carries label/text).
- `reason` — Anx carries the relocation reason (`view.js:175`). Navic carries it only in JS (`navic-reader.js` `scheduleCommittedRelocation`) but does not propagate it through the bridge event, so the controller cannot distinguish `link` vs `progress-seek` vs `relocate-committed` from the event alone.
- `fraction` / `size` — renderer section fraction and size; used by Anx for section-progress math.

**Why it matters:** The spec's acceptance criteria require deterministic page numbering and chapter-local progress. Without `range` and `chapterLocation`, annotation positioning and chapter-boundary detection are weaker than Anx's. Without `reason` in the event, the controller must infer relocation reason from JS-side state — which is exactly the kind of bridge leak the spec forbids.

**Fix:** Extend `ReaderLocator` with `range: String?`, `chapterLocation: JsonElement?`, `pageItemLabel: String?`, `reason: String?`. Decode them in `decodeReaderBridgeEvent`. Add a parity guard that reads `view.js:175-194` and asserts every field Anx puts on `lastLocation` has a Navic counterpart.

## Grievance 5 — Eight style dimensions from Anx `book_style.dart` are missing

**Evidence:**
- Anx `lib/models/book_style.dart:3-17` defines 14 style dimensions: `fontSize, fontFamily, fontWeight, lineHeight, letterSpacing, wordSpacing, paragraphSpacing, sideMargin, topMargin, bottomMargin, indent, maxColumnCount, headingFontSize, columnThreshold`.
- Navic `ReaderSettings` (`ReaderBridgeProtocol.kt:89-124`) has style-relevant fields: `fontFamily, fontSource, customFontFamily, customFontUrl, fontSizePercent, lineHeight, paragraphSpacingPercent, marginPercent, publisherStyles`.

**Missing from Navic:**
| Anx field | Anx default | Navic | Impact |
| --- | --- | --- | --- |
| `fontWeight` | 400 | — | Cannot set bold/medium weights. |
| `letterSpacing` | 0.0 | — | Cannot adjust letter spacing. |
| `wordSpacing` | 0.0 | — | Cannot adjust word spacing. |
| `sideMargin` | 6.0 | `marginPercent` (one value) | Cannot set independent side/top/bottom margins. |
| `topMargin` | 90.0 | (collapsed into marginPercent) | Same. |
| `bottomMargin` | 50.0 | (collapsed into marginPercent) | Same. |
| `indent` | 0 | — | Cannot set paragraph indent. |
| `maxColumnCount` | 0 | — | **Cannot set multi-column layout.** Directly blocks the spec's "adaptive EPUB page composition" acceptance criterion. |
| `headingFontSize` | 1.0 | — | Cannot scale headings independently. |
| `columnThreshold` | 720.0 | — | **Cannot set the viewport width above which multi-column activates.** Directly blocks adaptive composition. |

**Why it matters:** The design spec (`2026-06-13-komikku-reader-port-design.md:114`) lists "font sources, hyperlink styling, image interaction" as Anx capabilities to restore. The full log (`archive/...-full-log.md:606`) explicitly calls out "Column count and page-box width are especially relevant to the adaptive EPUB page composition requirement: Navic must not preserve a narrow column just because the old WebView-era defaults happened to render." Without `maxColumnCount` and `columnThreshold` in `ReaderSettings`, the adaptive-composition work is blocked at the engine boundary.

**Fix:** Add the 10 missing fields to `ReaderSettings` with bridge serialization (`toJsonObject`), settings normalization, Komikku settings dialog controls, and preference persistence. Add a parity guard that reads `book_style.dart:3-17` and asserts every field has a `ReaderSettings` counterpart.

## Grievance 6 — PDF engine parity is unverified

**Evidence:** The spec lists PDF as Anx-authoritative. `FoliatePdfEngineAdapter.kt` exists (lines 21-37 of `FoliateEpubEngineAdapter.kt`) but is a 17-line subclass that only changes `format = ReaderPublicationFormat.Pdf`. The real PDF logic is in Navic's bundled `composeApp/src/androidMain/assets/reader/vendor/foliate-js/pdf.js`. No test reads `tmp/references/anx-reader/assets/foliate-js/src/pdf.js:568-614` and compares.

**Why it matters:** The spec's acceptance criterion (`2026-06-13-komikku-reader-port-design.md:140`) says "EPUB and PDF rendering work through the controller boundary." PDF-as-book integration (sections, outline/TOC resolution, page lookup, cover behavior) is an Anx capability that may have diverged in Navic's fork.

**Fix:** Add a `FoliatePdfAnxParityTest.kt` that reads `anx-reader/assets/foliate-js/src/pdf.js:568-614` and asserts Navic's bundled `vendor/foliate-js/pdf.js` exposes the same `makePDF(file)` contract: sections, outline resolution, page lookup, cover. If Navic's fork diverged, document the divergence as a `Temporary adapter` with a removal path.

## Grievance 7 — Font source model is not anchored to Anx

**Evidence:** The spec's full log (`archive/...-full-log.md:607`) cites `lib/providers/fonts.dart:16-122`, `lib/service/font.dart:22-24`, `lib/models/font_model.dart:28-33` for "remote font manifests, local font import, and WebView font URLs." Navic has `ReaderImportedFont.kt`, `ReaderImportedFontCache.android.kt`, and a `Dyx` typewriter font registration. No test reads the Anx font files and compares the model.

**Why it matters:** Remote font manifests (download, cache, WebView URL serving) are a real Anx capability. If Navic's `ReaderImportedFont` only covers local import and not remote manifests, the gap is invisible.

**Fix:** Add a parity guard that reads `lib/providers/fonts.dart`, `lib/service/font.dart`, and `lib/models/font_model.dart` and asserts `ReaderImportedFont` + `ReaderImportedFontCache` cover the same surfaces (remote manifest, local import, WebView-accessible URL, deletion).

## Summary Of Missing Work

Ordered by impact on the spec's acceptance criteria:

1. **Bridge event parity (Grievance 3):** Add every missing required `ReaderBridgeEvent` variant + parity guard reading `epub_player.dart:628-821`. Blocks annotation-click, external-link, footnote, back-stack, selection-clear, pull-up.
2. **Style dimension parity (Grievance 5):** Add 10 missing `ReaderSettings` fields + parity guard reading `book_style.dart:3-17`. **Blocks adaptive EPUB page composition** (`maxColumnCount`, `columnThreshold`).
3. **Relocation payload parity (Grievance 4):** Extend `ReaderLocator` + parity guard reading `view.js:175-194`. Blocks precise annotation positioning and controller-visible relocation reason.
4. **Anx parity guard infrastructure (Grievances 1, 2):** Add `FoliateAnxParityTest.kt` using the Komikku disk-reading pattern. Add source citations to adapter files. This is the structural fix that prevents the duct-tape pattern from recurring.
5. **PDF parity (Grievance 6):** Add `FoliatePdfAnxParityTest.kt` reading `anx-reader/assets/foliate-js/src/pdf.js:568-614`.
6. **Font source parity (Grievance 7):** Add parity guard reading the Anx font files.

## What Codex Should Stop Doing

- Stop calling the adapter "Anx-derived" in the spec until the parity guards exist. The spec's `Reference Authority` line 35 should note: *"Anx/Foliate authority is target state. Komikku authority is enforced by guard tests today; Anx parity guards are tracked in `2026-06-17-komikku-reader-port-status-audit.md` and `2026-06-17-anx-middleware-complaint-brief.md`."*
- Stop wrapping Navic's existing Foliate fork in typed names that resemble Anx's callbacks without verifying the names match Anx's actual contract. A typed boundary with the wrong taxonomy is still duct tape.
- Stop satisfying adapter tests with round-trip assertions only. Round-trip proves the boundary is clean; it does not prove the boundary is faithful.

## What Codex Should Start Doing

- Apply the **same guard pattern already used for Komikku** to Anx: read the reference source from disk in a test, assert Navic's code contains the corresponding tokens/fields/events. Negatively assert the duct-tape alternative is absent.
- Cite the exact Anx file/function being matched at the top of every adapter file, the same way `KomikkuViewerNavigation.kt:4-5` cites Komikku.
- Treat a missing Anx event as a `Failing` feature per the spec's own status labels, not as "not yet implemented." Komikku owns UI; Anx owns behavior. If Anx exposes the behavior callback, Navic must carry it through the bridge/engine boundary or document a tested divergence.

## File Pointers

- This brief: `docs/superpowers/specs/2026-06-17-anx-middleware-complaint-brief.md`
- Status audit: `docs/superpowers/specs/2026-06-17-komikku-reader-port-status-audit.md`
- Design spec: `docs/superpowers/specs/2026-06-13-komikku-reader-port-design.md`
- Anx reference root: `tmp/references/anx-reader/`
- Key Anx files to anchor against:
  - `lib/page/book_player/epub_player.dart:628-821` (callback catalog)
  - `assets/foliate-js/src/view.js:115-194` (relocation payload), `:216-327` (link/image/view click taxonomy), `:335-397` (annotation hooks)
  - `lib/models/book_style.dart:3-17` (style dimensions)
  - `assets/foliate-js/src/pdf.js:568-614` (PDF integration)
  - `lib/providers/fonts.dart`, `lib/service/font.dart`, `lib/models/font_model.dart` (font sources)
- Navic files needing anchors:
  - `composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt`
  - `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt`
  - `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt`
  - `composeApp/src/androidMain/assets/reader/navic-reader.js`
- Komikku guard pattern to copy:
  - `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt:485-528`
  - `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderKomikkuBackboneResetTest.kt:759-784`
