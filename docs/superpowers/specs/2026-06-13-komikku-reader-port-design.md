# Komikku Reader Port Design

Date: 2026-06-13
Last compacted: 2026-06-17
Status: active source of truth for Navic reader-shell work.
Branch at compaction: `codex/komikku-reader-backbone-eta64`

The full historical register that used to live in this file is preserved at:

- `docs/superpowers/specs/archive/2026-06-13-komikku-reader-port-design-full-log.md`

Future test evidence belongs in:

- `docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

Anx/Foliate behavior parity execution belongs in:

- `docs/superpowers/specs/2026-06-17-anx-parity-7-phase-plan.md`

Remaining reader and Whispersync gap closure now executes through staged plans in:

- `docs/superpowers/plans/2026-06-28-reader-whispersync-gap-closure.md`

Keep this design file short. It is the operating contract, not a diary.

## Objective

Replace the old Navic eReader shell with a Komikku-derived frontend/controller backbone, while restoring EPUB/PDF/readaloud capabilities through controlled adapters.

Long-term target:

- Komikku frontend/layout/input/progress/settings behavior.
- Anx Reader/Foliate EPUB/PDF engine capabilities.
- Navic/Bindery integration for auth, OPDS, caching, progress sync, audio metadata, settings, and release packaging.

The reader must feel like a proper eBook/manga reader, not a Navic music screen with an embedded WebView.

## Reference Authority

Every reader feature or bugfix must be matched against the reference product before implementing it.

- Komikku is authoritative for the reader UI layer: native shell hierarchy, gesture ownership, menu/chrome behavior, tap-zone layout, progress rail presentation, settings overlay layout, and responsive behavior.
- Anx Reader/Foliate is authoritative for the reader behavior layer: EPUB/PDF rendering, pagination model, navigation semantics, search, content-action taxonomy, annotations/highlights, selection/footnote/history callbacks, style dimensions, font-source behavior, media/readaloud hooks, and the WebView/Foliate bridge event/payload contract.
- Navic/Bindery is authoritative for OPDS, credentials, publication cache, progress storage, audio/ebook metadata, app navigation outside the reader, and release packaging.

Boundary rule: when a feature spans both references, Komikku decides how the UI/control surface is presented and how gestures are owned; Anx decides what behavior command, event, payload, or engine state exists behind that controller surface.

Every Anx bridge callback/event exposed by the reference EPUB/Foliate layer must have a Navic bridge/engine counterpart unless a deliberate divergence is documented with a parity guard, rationale, and replacement behavior. Missing Anx events are failing behavior parity, not optional future polish.

Current enforcement note: Komikku parity is already guarded by source-reading tests. Anx behavior parity is now executed through `2026-06-17-anx-parity-7-phase-plan.md`. Phase 1 is closed with source citations, a green known-gaps registry, and route/order guards in `FoliateAnxParityTest.kt`. Phases 2-8 are host-verified for internal link suppression, the missing bridge event catalog, relocation payload parity, selection payload parity, all 10 Anx style dimensions, PDF parity guards, and font-source parity guards. The older `2026-06-17-komikku-reader-port-status-audit.md` and `2026-06-17-anx-middleware-complaint-brief.md` are diagnostic inputs, not the active implementation plan.

`FoliateAnxParityTest` must not treat an Anx entry as `Exists` based only on bridge/event/type/debug-label symbols. Behavior entries marked `Exists` must carry a verified controller or native UI route, usually including a behavior test plus the production controller/UI component that consumes the event. This specifically guards against the "types-only parity" regression where JS emits, bridge decode, adapter mapping, and debug labels all exist but the reader silently discards the event.

If a Navic feature works but is not faithful to the reference, treat it as unfinished. Do not polish or build dependent behavior on a non-faithful workaround.

## Non-Negotiable Guardrails

- Do not revive the old docked settings panel or legacy reader-wide WebView tap ownership.
- Do not add another workaround until the current reference behavior and root cause are documented.
- Release cadence is debug-first and public-last. Intermediate reader slices, minor fixes, diagnostics, and unproven animation changes must use debug/readerdev APKs on the emulator plus local harness/ADB validation only. A public GitHub APK release is allowed only after a coherent feature or major user-visible fix is fully implemented, locally validated, committed, and worth physical-device acceptance testing.
- A "debug release" means a local debug/readerdev APK built and installed for emulator validation. It does not mean a GitHub tag, GitHub prerelease, public APK upload, or physical-phone candidate.
- Do not bump public release versions or start GitHub release workflows merely to let the user test a microfix. If the fix is not complete enough to be called the next deployed feature/fix candidate, keep it in debug/readerdev validation.
- Final/public releases are reserved for completed feature slices or major fixes after their plan gates pass. Do not publish a public APK simply because one isolated test turned green.
- `scripts/publish-github-release.ps1` must fail closed unless the caller explicitly passes a public-release override and a readiness note naming the completed feature/fix plus validation evidence. Treat missing override as a process error, not as a prompt to publish.
- Do not build iOS artifacts for this Android reader work.
- Do not treat host tests, desktop browser harnesses, or manual screenshots as proof of Android input/progress behavior.
- Do not ask for routine human testing while host tests, emulator probes, or readerdev automation can still answer the question. Human/physical-device validation is the final acceptance gate for coherent candidates, not the normal implementation loop.
- Do not let Foliate/WebView own normal reader tap zones. Short taps belong to the native Komikku-style surface; long press can reach content actions.
- Do not regress links, image interaction, search, EPUB text rendering, PDF rendering, or readaloud hooks while replacing the shell.
- Do not invent Navic-specific reader behavior where Anx already defines a bridge callback, payload field, style dimension, or engine action.
- Do not launch long Gradle, readerDev, emulator, or DevTools validation work through a foreground shell or `Start-Process` directly on `.bat` files. Use a no-console launch path with `ProcessStartInfo.UseShellExecute=false`, `CreateNoWindow=true`, file logs, and a PID file; smoke-test the wrapper before using it for a long command.

## Current Architecture

Active reader path:

- `ReaderScreen` routes through `KomikkuReaderRoot`.
- `KomikkuReaderRoot` mounts `KomikkuReaderNativeFrameHost`.
- Android native host owns the reader/viewer hierarchy: reader container, viewer container, passive navigation overlay, Compose overlay.
- `KomikkuReaderNavigator` owns tap-zone classification, using ported Komikku region semantics.
- `ReaderController` owns menu state, shell-cover state, chapter progress state, TOC navigation, bookmarks, settings, and progress-save decisions.
- Foliate runtime remains the EPUB/PDF engine, but reader-wide input and chrome state are controller-owned.

Important files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderChapterNavigator.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/`

Reader JS asset layout:

- `navic-reader.js` is the stable WebView entrypoint, public bridge, lifecycle/open/load shell, and Foliate event coordinator. It must stay below the host-test line-count guard.
- `navic-reader-page-turns.js` owns page-turn commands, native drag preview bridge support, fixed-layout/PDF adjacent page targeting, scrolled-edge turn helpers, and direction-aware turn calculations.
- `navic-reader-content-interactions.js` owns renderer-surface touch suppression, link/image/content-action classification, long-press content actions, and sepia image toggling.
- `navic-reader-pagination.js` owns fixed/reflowable page-position math, deterministic pagination profile/cache helpers, chapter-local position reporting, and organic page-number layer updates.
- `navic-reader-appearance.js` owns settings application, theme/document CSS, tap-zone diagnostic overlay rendering, reader direction application, and surface paper/border texture layers.

This split is intentional. Do not add new listener, pagination, texture, or content-action work back into the entrypoint.

## Current Verified State

Latest dirty emulator validation is summarized in `2026-06-13-komikku-reader-port-validation-log.md`.

As of the 2026-07-01 Stage 9H.2 current-source validation:

- Physical feedback after `v1.0.11-theta29` reported remaining reader visual regressions: weak sepia intensity, page numbers not visually following `Dys`, missing edge-gradient texture strength, texture swapping after text movement, and landscape rotation collapsing Western EPUB text into a narrow vertical column.
- `v1.0.11-theta30` closed part of that batch, but physical tablet feedback still showed page texture swapping and a landscape collapse case, so Stage 9H.2 moved texture handling away from a single mutable root background.
- The current source now splits paper ownership into an opaque full-window color backing for margins/fallback and separate moving page paper/border layers with previous/current/next slots. Browser proof showed a 98px renderer movement producing a matching `translate3d(-98px, 0px, 0px)` current texture-slot transform while the static color backing stayed fixed and did not duplicate the paper bitmap.
- The current source keeps page-number font resolution tied to the configured reader font and registers bundled Navic font faces in the root document whenever the configured family references a Navic font. This targets the root organic page number layer, not only EPUB iframe text.
- The tablet-width `css-smoke` harness now measures actual rendered layout. Against a 1974x1232 viewport it reported `htmlWidth=1855.5625`, `bodyWidth=1737.125`, and `paragraphWidth=809.34375`, preventing the one-word min-content column from passing as a valid landscape spread.
- This source slice is now published as `v1.0.11-theta31` for physical validation. The pre-release evidence includes browser/host checks plus current-source readerdev/emulator proof: `darkaxt.navic.readerdev` installed on `emulator-5554`, reached `publicationReady`, passed live texture/font/page-box probes, and passed the 12-row Komikku matrix at `captures\reader-komikku-matrix\stage9h2-current-source-20260701`.
- Physical release-device validation is still required before claiming final visual acceptance for texture feel, edge-gradient strength, tablet typography, and the `Dys` page-number visual match.
- Post-theta31 physical feedback found a real drag commit regression: while settled on Chapter 1 page 1/global `5/140`, dragging toward the next page previewed page 6 content but finger-up committed page 7 as the new page 6. The root cause was native drag preview mutating Foliate with `renderer.scrollBy(...)` before Android dispatched the release page action. Stage 9H.3 removes preview-time Foliate mutation and adds `epub-native-drag-single-commit` to guard that preview update is non-committing and release plus page action advances exactly one page.
- Post-theta35 physical feedback found a second visual split: the curl/drag preview could show a page from the start of the chapter while the committed page model and texture layer moved elsewhere. Stage 9L keeps curl snapshots on the current Foliate renderer page, prevents explicit page-turn targets from being projected across sections, rejects unchanged/boundary relocation aftershocks before renderer-derived page math can fabricate movement, and keeps the texture update reason aligned with page-numbering. Current-source proof is browser/host only; release-device drag feel remains open until a new signed candidate is published and tested.
- Stage 9L is published as `v1.0.11-theta36` for physical validation. The release candidate specifically targets curl/current-page mismatch, section-boundary texture direction, and one-page target projection aftershocks; it does not add a standard/curl mode toggle.
- Post-theta36 physical feedback shows the curl effect is visible and more stable, but still suspect: the animation can appear to use a chapter-start page instead of the current visible page. Stage 9M adds an explicit reader page-turn animation mode with `standard` as the default and `curl` as an opt-in mode. Standard mode keeps the exposed-page drag preview and clears curl sheets/snapshots so the experimental curl path cannot conflict with normal dragging. Release-device validation remains open.

As of the 2026-06-18 dirty emulator Phase 6 refactor check:

- The current dirty `readerdev` APK installed on `emulator-5554`, launched, opened a real EPUB, emitted `Reader publication ready`, and computed `Pages ready: 270`.
- The Komikku reader matrix passed baseline, cover center tap, cover drag, center tap, native long press, edge tap next/previous, drag next/previous, and texture next walk checks.
- Diagnostics show short taps and drags route through the native Komikku controller path rather than WebView content handlers for the completed checks.
- The run is not release-green: the `texture-previous-walk` screenshot pull failed in the smoke harness, and a focused rerun found that repeated previous taps at the beginning can leave the WebView on a suppressed/blank cover document instead of returning cleanly to the native cover surface.
- The 2026-06-17 rail endpoint and rail chapter-button checks remain valid historical evidence, but the latest emulator run supersedes them as the current dirty-build state.

This is local/dirty validation, not a GitHub release validation.

As of the later 2026-06-18 eta71 continuation matrix:

- The dirty `readerdev` APK on `emulator-5554` reported `versionName=v1.0.11-eta71`, `versionCode=404`, and `lastUpdateTime=2026-06-18 18:22:03`.
- `adb-reader-komikku-matrix.ps1` passed all scripted checks: baseline current reader, baseline native cover, cover center tap, cover drag next, center tap toggle, native long press, edge tap next, drag next, texture next walk, edge tap previous, drag previous, and texture previous walk.
- `reader-matrix-failures.txt` reported `No matrix failures`.
- The dirty-emulator diagnostics showed cover drag using the shell-cover path (`shellCoverDragCandidate=True`, `shellCoverSwipe=True`, `shellCoverCommand=True`), normal page drags using the native drag-preview path in both directions, and texture direction sampling without inversion for the scripted next/previous walks.
- Visual inspection of the `baseline-native-cover` screenshot showed the native cover on a black cover surface without the bottom menu overlay.
- This does not validate progress rail endpoints, resume after app/window interruption, physical-phone release behavior, or manual drag feel. Keep those as active Priority 0/1 work.

As of the 2026-06-18 Anx parity guard check:

- GLM's "types-only bridge parity" audit was reviewed against the current branch. The general risk is accepted and is now a guardrail: bridge/event/type/debug-label symbols are not enough. The specific quoted `ReaderController.kt` no-op block is stale on this branch. `ReaderController` now routes the Phase 3 bridge events into controller state or UI-facing prompt/popup state, and the targeted `ReaderControllerTest` + `FoliateAnxParityTest` host gate passed on 2026-06-18 for that route.
- The remaining GLM audit work is not more type plumbing. It is behavior proof: clean release validation for high-priority reader bugs, resume persistence after disrupted gestures/app recreation, user-driven selection-clear/pull-up validation, and release-device confirmation of behavior already proven in the dirty emulator.
- `FoliateAnxParityTest` exists as the Phase 1 green known-gaps registry and reads the Anx reference files instead of only round-tripping Navic's own mappings.
- Anx source citations exist in `FoliateEpubEngineAdapter.kt`, `ReaderBridgeProtocol.kt`, `navic-reader.js`, and `navic-reader-content-interactions.js`.
- Phase 2 adds a cancelable Foliate `link` listener in `navic-reader.js`, an `internalLink` bridge message, `ReaderBridgeEvent.InternalLinkRequested`, `ReaderEngineEvent.InternalLinkRequested`, and an ADB-visible `internalLink(...)` debug label.
- Phase 3 adds bridge/engine events and ADB-visible labels for `ExternalLink`, `SelectionCleared`, `AnnotationClick`, `AnnotationDrawn`, `OverlayCreated`, `LoadDoc`, `PushState`, `FootnoteClose`, and `PullUp`.
- Phase 3 runtime hooks are in `navic-reader.js` for Foliate `external-link`, `draw-annotation`, `show-annotation`, `create-overlay`, `load`, and history `index-change`; selection clear posts `selectionCleared`; scrolled-edge overscroll posts `pullUp`; overlay clearing posts `footnoteClose`.
- The nine Phase 3 bridge events must not be treated as type-only parity. `ReaderController` currently routes them into controller state (`lastLinkInteraction`, `externalLinkPrompt`, `lastAnnotationInteraction`, `annotationPopup`, `lastOverlayInteraction`, `loadedDocument`, and `engineNavigation`), and `FoliateAnxParityTest.phase3AnxBridgeEventsHaveControllerBehaviorRoutes` guards against restoring the old no-op branches.
- Phase 5 dirty emulator evidence proves the selection payload can reach Android with `footnote=true`, CFI, context text, and bounds, and that the native Komikku-style `Highlight`, `Copy`, and `Note` action overlay appears after shell-cover dismissal. Highlight has a repeatable smoke gate through `applyHighlights` + `annotationDrawn`; Copy has a repeatable node-tap smoke gate that reaches the native clipboard boundary (`Reader selection copied length=31`). Note is dirty-emulator verified through a DevTools-created selection plus native UI taps: `Reader selection note save length=10`, `applyHighlights(... notes=1)`, and `annotationDrawn` were captured on eta76. The 2026-06-20 dirty emulator run now also proves real ADB long-press normal-text selection plus native Highlight, Copy, and Note Save actions without DevTools-created selection. Clean release/physical-device validation remains open.
- Phase 3 controller behavior routes are now required: bridge/engine events must feed `ReaderControllerState` or an explicit UI route, not stop at type/decode/debug-label parity. The guard rejects no-op controller branches for `InternalLinkRequested`, `ExternalLinkOpened`, `AnnotationClicked`, `AnnotationDrawn`, `OverlayCreated`, `DocLoaded`, `NavigationStateChanged`, `FootnoteClose`, and `PullUp`.
- `PushState` is not passive history metadata: Anx routes it to a visible history capsule when `canGoBack || canGoForward`. Navic now stores `ReaderEngineNavigationState.visible`, renders `KomikkuReaderHistoryCapsule`, and routes capsule back/forward through `ReaderEngineCommand.NavigateHistory` to `ReaderBridgeCommand.HistoryBack` / `HistoryForward` and Foliate `view.history.back()` / `forward()`.
- `PullUp` is not a passive diagnostic event: Navic must route it to controller-owned overlay state while keeping the renderer command list empty, but it must not implicitly force `menuVisible=true`. The Komikku shell owns explicit chrome visibility through center/menu actions; pull-up bridge events only preserve menu state so vertical page movement cannot surface chrome by accident.
- Phase 3 now has dirty-emulator WebView evidence for the high-risk bridge path. `captures\reader-bridge-probes\20260618-phase3-pullup-diagnostic-command\reader-devtools-probe.json` shows `externalLink`, `annotationDrawn`, `annotationClick`, `overlayCreated`, `loadDoc`, `pushState`, `footnoteClose`, and diagnostic `pullUp` crossing into Android logcat. This is bridge-path evidence, not a replacement for user-driven scrolled-edge gesture validation.
- Phase 4 extends `ReaderLocator` and `locationChanged` with Anx relocation payload fields: `rangeCfi`, `reason`, `fraction`, `size`, `tocItemLabel`, and `pageItemLabel`.
- Phase 4 keeps Navic-specific page/progress extensions alongside the Anx fields and adds an ADB-visible `locationChanged(... reason=..., rangeCfi=...)` debug label.
- Phase 4 now has dirty-emulator WebView evidence for the relocation payload path. `captures\reader-bridge-probes\20260618-161735-relocation\reader-devtools-probe.json` shows a real `locationChanged` message from `darkaxt.navic.readerdev` on `emulator-5554` carrying `rangeCfi`, `reason`, `fraction`, pagination profile metadata, and page counts.
- The DevTools relocation probe must not wait on monkey-patching Android's injected JS bridge method. The runtime diagnostic command returns the posted `locationChanged` payload, and the harness accepts that returned payload as evidence when `observedMessageCount` is zero.
- Phase 5 extends `SelectionChanged` through bridge, engine, controller, runtime, and debug logging with Anx `onSelectionEnd` payload fields: `footnote`, `contextText`, and `pos.left/top/right/bottom`.
- Phase 5 now has a controller/UI route for selected text: `ReaderControllerState.selectionActions` drives a native Komikku overlay with Highlight, Copy, and Note actions. Highlight and Note apply through the annotation engine command path; Copy is handled at the app boundary with the native clipboard.
- Note is not a hidden no-op route: `startSelectionNote` opens a native draft dialog, and `saveSelectionNote` stores a note-bearing `ReaderAnnotation` and emits `ApplyAnnotations`.
- Anx `show-annotation`/`AnnotationClick` is no longer a hidden no-op route: `ReaderControllerState.annotationPopup` drives a dedicated Komikku-native annotation overlay, and dismissal is routed through `ReaderCoordinator` without WebView ownership.
- Anx/Foliate external links are no longer hidden state-only events: `ReaderControllerState.externalLinkPrompt` drives a dedicated Komikku-native external-link prompt, and `ReaderScreen` opens confirmed URLs through the native `LocalUriHandler` before clearing the prompt.
- Phase 6 adds Anx `BookStyle` dimensions to the settings contract: `fontWeight`, `letterSpacing`, `wordSpacing`, `sideMargin`, `topMargin`, `bottomMargin`, `indent`, and `headingFontSize`.
- Phase 6 carries those fields through defaults, preference persistence, book overrides, bridge serialization, pagination cache metadata, Foliate renderer attributes, runtime CSS, and the Komikku settings dialog.
- Phase 7 PDF and font-source parity guards are host-verified. `FoliatePdfAnxParityTest` guards the Anx/Foliate `makePDF(file)` contract, and `ReaderFontSourceAnxParityTest` guards local import, remote manifest, WebView-safe font URLs, deletion, and remote download progress/pause/resume/cancel routes.
- Phase 8 adds the remaining Anx `BookStyle` adaptive composition dimensions: `maxColumnCount` and `columnThreshold`.
- Phase 8 carries those fields through defaults, preference persistence, book overrides, bridge serialization, pagination profile metadata, Foliate paginator attributes, runtime layout, global Ebook settings, and the Komikku settings dialog.
- Phase 8I corrected the adaptive composition contract: Navic preserves Anx `maxColumnCount=0` as automatic mode and lets Foliate resolve columns from page size plus `columnThreshold`, instead of pre-collapsing portrait phone/fold/tablet viewports to a forced single column.
- The focused Phase 4 host test passed on 2026-06-18 for bridge decode, engine mapping, and Anx source parity.
- The reader host suite passed on 2026-06-18 after Phase 4.
- The focused Phase 5 host test passed on 2026-06-18 for bridge decode, engine mapping, controller state, and Anx source parity.
- The focused selection-action host tests passed on 2026-06-18 for controller-owned action availability, native Komikku overlay routing, note draft creation, and note annotation saving.
- The reader host suite passed on 2026-06-18 after Phase 5.
- The focused Phase 6 host test passed on 2026-06-18 for bridge serialization, default normalization, preference round-trip, and Anx source parity.
- The reader host suite passed on 2026-06-18 after Phase 6.
- The focused Phase 7 host tests passed on 2026-06-18 for PDF and font source parity guards.
- The focused Phase 8 host test passed on 2026-06-18 for Anx adaptive composition fields, bridge serialization, default normalization, preference round-trip, and paginator contract.
- The reader host suite passed on 2026-06-18 after Phase 8.
- A dirty emulator install and Komikku matrix rerun passed after Phase 8; see `2026-06-13-komikku-reader-port-validation-log.md`.
- `node --check` passed for `navic-reader.js`, `navic-reader-content-interactions.js`, and `navic-reader-page-turns.js`.
- `node --check` passed for the Phase 6-touched runtime modules: `navic-reader.js`, `navic-reader-helpers.js`, `navic-reader-pagination.js`, `navic-reader-appearance.js`, `navic-reader-content-interactions.js`, `navic-reader-page-turns.js`, and `navic-reader-pdf.js`.
- `node --check` passed for the Phase 8-touched runtime modules: `navic-reader.js`, `navic-reader-helpers.js`, `navic-reader-pagination.js`, and `vendor/foliate-js/paginator.js`.
- `git diff --check` passed after the Phase 6 code updates and again after the Phase 8 documentation updates.

## Anx/Foliate Handoff

Do not start any Anx/Foliate phase from memory. Use `2026-06-17-anx-parity-7-phase-plan.md` as the execution contract.

Phase 1 is complete:

- Citations were added to the adapter, bridge protocol, and JS bridge files.
- `FoliateAnxParityTest.kt` documents all known Anx callbacks, Foliate emits, style dimensions, product divergences, and out-of-scope entries.
- Product divergence entries are guarded by route verification and same-file ordering checks where applicable.
- The reader host baseline is green.

Phase 2 is host-verified:

- Internal Foliate `link` emits are now suppressed when native tap zones own short taps, preserving Komikku short-tap ownership.
- Long-press/direct link activation remains explicit through `navic-reader-content-interactions.js` and posts `internalLink(prevented=false, source=...)` before `goTo`.
- `ReaderBridgeProtocol.kt`, `FoliateEpubEngineAdapter.kt`, `ReaderEngineWebViewHost.android.kt`, JS link handling, and `FoliateAnxParityTest.kt` were updated.
- Browser/WebView runtime validation now dispatches Foliate's cancelable `link` event in native and non-native modes: native short-tap mode posts `internalLink(prevented=true, source=native-short-tap)` and cancels the event; non-native mode posts `internalLink(prevented=false, source=foliate-link)` and leaves the event uncanceled.
- Android/emulator validation now covers the injected WebView flow: `captures\reader-smoke\20260618-105750` shows `internalLink(#navic-adb-internal-link-probe, prevented=true, source=native-short-tap)` in logcat after dispatching Foliate's cancelable `link` event through the reader WebView CDP target.

Phase 3 is host-verified:

- Missing bridge events now exist: `LoadDoc`, `ExternalLink`, `SelectionCleared`, annotation events, overlay creation, push state, footnote close, and pull-up.
- These events are no longer allowed to remain adapter-only. `ReaderController` must retain link, annotation, overlay, loaded-document, and navigation state for downstream UI behavior.
- `AnnotationClick` now has a visible controller-owned UI route: tapping an existing Foliate/Anx annotation can surface a native Komikku annotation popup instead of only updating debug/controller state.
- `ExternalLink` now has a visible controller-owned UI route: Foliate external-link events surface a native confirmation prompt and open only through the app boundary, not through WebView chrome.
- `PushState` is not passive history metadata: Anx routes history capability changes into visible reader chrome, so Navic now surfaces `KomikkuReaderHistoryCapsule` whenever `canGoBack || canGoForward` and only hides it through the controller-owned dismiss route.
- Do not convert unrelated product divergences or out-of-scope entries to `Exists`.
- Android/emulator bridge-path validation now covers `externalLink`, `loadDoc`, `annotationClick`, `annotationDrawn`, `overlayCreated`, `footnoteClose`, and diagnostic `pullUp` in a dirty readerdev WebView.
- Dirty-emulator validation now proves a user-like ADB tap can clear an existing WebView selection and emit Android-side `selectionCleared` without center-menu dispatch. This still used a DevTools probe to create the selection, so real manual text selection and scrolled-edge pull-up gestures remain release-readiness validation items.

Phase 4 is host-verified:

- `ReaderLocator` carries Anx relocation payload fields without giving the WebView chrome/progress ownership.
- `locationChanged` posts `rangeCfi` from `detail.cfi`; do not stringify DOM `Range` objects.
- Dirty-emulator WebView validation passed on 2026-06-18 for the diagnostic relocation path: the runtime returned a posted `locationChanged` payload with `reason=adb-relocation-payload-probe`, a CFI `rangeCfi`, `fraction`, and pagination profile metadata.
- Remaining runtime validation before treating this behavior as release-ready: real user-driven relocations under tap, drag, progress rail, TOC, and resume must keep posting `reason` and CFI/null `rangeCfi`; `rangeCfi` must never become `[object Range]`.

Phase 5 is host-verified:

- `SelectionChanged` now carries Anx `onSelectionEnd` payload fields through the bridge, engine adapter, and controller state.
- Runtime selection posts include a DOM selection bounding rectangle, bounded context text, and footnote detection for footnote/noteref-like elements.
- Native selection actions are host-verified: selected text surfaces Highlight, Copy, and Note from a dedicated Komikku overlay component; Note opens a native draft dialog and saves a note-bearing annotation through `ApplyAnnotations`.
- Dirty Android/emulator validation now proves the footnote-positive selection payload, the native selection toolbar, Copy, Highlight, Note save, and selection-clear-after-selection paths. The eta76 DevTools-created Note save path produced `Reader selection note save length=10`, `applyHighlights(count=2, notes=1)` in a state that already contained one prior highlight, and `annotationDrawn` bridge events. The 2026-06-20 real-input run selected visible EPUB words with ADB long-press, copied one selection (`Reader selection copied length=5`), highlighted one selection with visible inline annotation, and saved a note from a real selection (`Reader selection note save length=11`, `applyHighlights(count=4, notes=2)`, and `annotationDrawn`). Before treating this behavior as release-ready, still validate a clean release APK on the phone.

Reader search is host-verified:

- Anx/Foliate `search` is now surfaced through Komikku-owned native reader chrome rather than remaining a backend-only bridge symbol.
- `ReaderController` owns the search dialog lifecycle, clear-search command, active result state, and search-result navigation by CFI/HREF.
- `ReaderCoordinator`, `FoliateWebViewEngineAdapter`, `ReaderBridgeCommand.ClearSearch`, and `navic-reader.js` now clear WebView search highlights when the native search dialog is dismissed.
- `KomikkuReaderSearchDialog` is a dedicated overlay component routed from `ReaderRoot`, with result taps navigating through controller/coordinator commands.
- Android/emulator validation now proves the readerdev direct-text route can bypass the native shell cover for validation only, open the native search UI, dispatch a real Foliate search for `alcatraz`, and receive complete search results without `Reader console ERROR` or the Foliate `IndexSizeError` from stale search-annotation CFI resolution.
- Android/emulator validation also proves a native search-result row exposes stable accessibility semantics, can be tapped deterministically, and dispatches `goToCfi`; the final captured hierarchy shows the first result passage. This is result-tap evidence, not a broader history/location bridge claim.
- Android/emulator validation also proves the explicit dismiss path: a stale-query cleanup followed by native `alcatraz` search produced non-empty streamed results, then closing the search dialog dispatched `clearSearch` and emitted empty `searchResults`. This closes readerdev/emulator search result tapping and active-result dismissal; signed release-device proof remains separate from this implementation evidence.

Phase 6 is host-verified:

- The eight non-architectural Anx `BookStyle` fields now exist in `ReaderSettings`, defaults, persistence, per-book overrides, bridge JSON, runtime CSS, renderer attributes, pagination fingerprints, and the Komikku settings dialog.
- Anx defaults are preserved: `fontWeight=400`, `letterSpacing=0`, `wordSpacing=0`, `sideMargin=6`, `topMargin=90`, `bottomMargin=50`, `indent=0`, and `headingFontSize=1`.
- Android/emulator validation is still required before treating this behavior as release-ready: change each style control in the settings dialog and confirm visible EPUB layout changes plus stable pagination/cache recalculation.

Phase 8 is host- and emulator-verified:

- The remaining Anx `BookStyle` adaptive composition fields now exist: `maxColumnCount` and `columnThreshold`.
- Anx defaults are preserved: `maxColumnCount=0`, `columnThreshold=720`.
- `maxColumnCount=0` is stored and forwarded as Anx automatic mode. Foliate remains responsible for resolving actual column count from the available page box and `columnThreshold`; Navic must not reintroduce a shell-side `readerEffectiveMaxColumnCount(...)` collapse.
- The current dirty emulator matrix passed after rebuilding and installing the Phase 8 source, but visual/manual validation of the settings controls remains required before treating it as release-ready.

## Active Bugs And Open Risks

Priority 0:

- Validate the selection action UI on a clean release APK/physical device: dirty-emulator automation proves DevTools-created selections and real ADB long-press normal-text selections surface Highlight/Copy/Note without opening reader chrome, Copy reaches the clipboard, Highlight/Note render as annotations, and footnote selections keep the Anx payload fields.
- The stale drag-preview stuck-state blocker is superseded by the 2026-06-18 `08:33:30` dirty emulator matrix: `drag-previous` passed, diagnostics showed `readerNativeDragPreview=True`, `wrongTextureDirection=False`, and the captured page was not stuck in split preview. Keep the manual black-void/drag-feel polish below as active work, but do not keep treating the old stuck-preview note as a release blocker without fresh reproduction.
- Validate the progress rail fixes on a clean release candidate or the exact device/package where the user saw `10 / 12`, `2 / 4`, and page-1 rail-button failures.
  The 2026-06-18 host guard only made the rail targetable by native UI semantics (`Chapter page slider`) and ADB `tapDescFraction`; it did not close endpoint behavior.
  The 2026-06-28 host guard closes the lossy bridge contract where native `chapterPageIndex`/`chapterPageCount` targets were discarded before Foliate. It still does not close release/device endpoint behavior by itself.
- Dirty-emulator validated on 2026-06-20: persistence/resume after a real swipe and after force-stopping during a long drag restored the same readable locator (`OEBPS/Text/Chapter-37.xhtml`, `chapterPageIndex=2`, `chapterPageCount=44`, `fraction=0.7584986058101005`) with no start override. Clean release/physical-device confirmation is still required before release-candidate closure.
- Validate cover chrome layering on the installed APK/phone release. Dirty-emulator eta71 visual evidence shows the native cover on a black cover surface without the bottom menu overlay, but this still needs physical/release confirmation before closure.

Priority 1:

- Host/emulator-closed on 2026-06-21: drag preview now keeps the current page moving while a section-boundary adjacent preview is still loading, avoiding the black-void fallback. Keep manual release/physical validation open for perceived drag feel.
- Correct remaining texture transition weirdness during page movement and page/section transitions.
- Confirm cover drag behavior is faithful: cover should not vanish on touch; drag should produce reader-owned feedback and commit on release.
- Keep progress rail chapter-local and Komikku-like; avoid whole-book rail behavior unless explicitly designed as a separate UI.
- Host-closed on 2026-06-19: duplicate bottom-toolbar settings entry points were removed at the controller route level. The bottom toolbar keeps distinct contents/search/settings actions, and `ReaderControllerDialog` has a single settings route.

Priority 2:

- Redesign the settings overlay density and scroll treatment around Komikku behavior.
- Improve paper texture/border texture visibility and asset strategy.
- Host-guarded on 2026-06-30: Navic can consume optional Bindery-provided fullscreen cover-surface URLs from manifest properties, rels such as `fullscreen-cover`, or generated variant arrays, choose the closest variant for the reader surface, fetch remote Bindery cover assets through the authenticated resource path, cache them as reader-local asset-loader files, and fall back to the EPUB-extracted cover when absent or unavailable. Bindery still owns generating/caching the derived cover asset, ideally one cached outpainted cover canvas per `bookId + coverHash + styleVersion`, with the original cover preserved in the center and only the surrounding area generated. Navic must not call an AI service at runtime.
- Host-closed on 2026-06-22: developer-only reader options, including WebView debugging and tap-zone visibility, live under Developer Options and Settings search routes them there instead of bloating the reader sheet.
- Keep page-curl animation sample as low-priority follow-up: `D:\Downloads\Trash\navic_page_curl_toggle_mockup_single_clipped.html`.
- Keep Turn.js-style page flip code out of the reader core. Foliate/Anx must continue to own EPUB/PDF layout, locators, annotations, selection, search, visible ranges, and Whispersync media-overlay bridges. If a page-flip library or sample is used later, it is only a visual-reference layer for drag preview animation after the Standard path is stable.

## Implementation Order

1. Validate Phase 5 selection actions on a clean release APK/physical device before release-candidate claims; dirty-emulator real normal-text selection is now proven.
2. Validate remaining user-driven Phase 3 bridge flows: scrolled-edge pull-up gestures must be observed without diagnostic commands.
3. Validate/fix release-candidate parity for the progress rail and cover chrome through host/emulator automation first; batch physical-device confirmation into the final candidate pass.
4. Resume persistence after disrupted drag/app interruption is dirty-emulator validated; keep clean release/physical confirmation open.
5. Continue texture movement polish as the remaining drag-feel slice; the black-void boundary fallback itself is host/emulator-closed but still needs release-device feel validation.
6. Continue the remaining Anx/Foliate behavior work behind the controller boundary: PDF runtime interaction, annotations/highlights, media/readaloud sync, hyperlink behavior, and image interaction.
7. Continue Komikku UI parity: rail proportions, bottom menu placement, non-duplicated bottom actions, settings overlay, tap-zone visibility.
8. Only after the shell is stable, revisit lower-priority page curl animation and optional visual polish.

## Required Emulator Gate

After every major reader code/asset/script change:

1. Run `adb devices` and choose a serial explicitly.
2. Check installed reader-dev package evidence: `versionName`, `versionCode`, `lastUpdateTime`.
3. If stale or ambiguous, rebuild/install/open with `scripts\install-reader-dev.ps1` using `-DeviceSerial` and the Bindery env file.
4. Run `scripts\adb-reader-komikku-matrix.ps1` with `-DeviceSerial`, `-ExpectedVersionName`, `-NoLaunch`, `-IncludeCoverChecks`, and a fresh artifact root.
5. Inspect baseline screenshot, hierarchy, summary CSV, failures file, logs, and relevant screenshots.
6. For DevTools bridge probes, require deterministic evidence in the artifact JSON. Do not rely on replacing Android-injected bridge methods from DevTools; commands must return the payload or a concrete failure.
7. Append only a concise result to `2026-06-13-komikku-reader-port-validation-log.md`.

If emulator launch, install, Bindery seed, or the matrix script fails, that validation path failure becomes the current task.

## Human And Release-Device Gate

Real devices and human review are reserved for the final stage of a coherent candidate, or for issues that cannot be judged by automation:

- Physical touch feel: drag, cover drag, tap zones, menu show/hide.
- Visual judgment: texture strength, typography, margins, settings density, and whether the result feels faithful to Komikku instead of merely inspired by it.
- Release APK validation with real app data/login state.
- Foldable/tablet layout checks, especially Tab S9 Ultra and Fold dimensions.
- End-to-end Whispersync enjoyment pass: open paired ebook, tap the headset, playback seeks correctly, highlight follows audio, resume works, and no release-only regressions appear.

Until that stage, the implementation loop should use host tests, browser/WebView harnesses, readerdev, emulator, ADB, and DevTools probes. Asking for human testing before those paths are exhausted is a validation-pipeline failure.

## Acceptance Criteria

The Komikku reader backbone is acceptable only when:

- Native shell owns reader-wide short taps and drag preview over cover, EPUB text, EPUB images, links, and PDF pages.
- Interactive content is not hijacked by normal center taps; content actions are long-press or explicitly delegated.
- Cover can be shown, dismissed, returned to, and navigated from without bottom-menu overlap.
- If Bindery provides an extended cover asset, the native cover surface uses it as the fullscreen background while preserving the original cover content; otherwise the current native cover fallback remains valid.
- Progress rail is chapter-local, reaches first and last pages, and its buttons navigate adjacent chapters from page 1 and endpoints.
- Page numbering is deterministic for a given viewport/pagination profile and does not count suppressed cover or Foliate sentinel columns.
- Reading progress persists after committed relocation and survives app interruption/reopen.
- EPUB and PDF rendering work through the controller boundary, not through legacy shell shortcuts.
- Every Anx EPUB/Foliate bridge callback/event/payload has a Navic bridge/engine counterpart, or a deliberate divergence with a source-reading parity guard and rationale.
- The Anx parity phase guard remains green after every behavior-layer phase; no phase is complete with a red reader host suite.
- Settings overlay follows Komikku behavior and remains usable on phone, foldable, and tablet dimensions.
- Release candidates are only published when a major bug or milestone has been verified locally and is worth user validation.

## Validation Log Policy

Do not append large dated sections here. For each test run, add a compact entry to:

`docs/superpowers/specs/2026-06-13-komikku-reader-port-validation-log.md`

Each entry should contain:

- Date/time and target serial.
- Package/version/update timestamp.
- Commands or scripts used.
- Artifact root/screenshots.
- Pass/fail summary.
- Next required fix.

If full logs are needed, store them as artifacts and link the path.
