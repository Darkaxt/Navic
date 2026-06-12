# Reader Stabilization Design

Date: 2026-06-11

Status: active reader stabilization anchor. All EPUB/PDF/WebView/native-touch reader stabilization work is tracked here.

## Objective

Stabilize Navic's ebook reader with a laptop-testable WebView renderer, a native Android touch surface that owns reader-wide gestures, and release-sized microdeliverables that can be validated on the phone.

The original first priority was fixing EPUB/WebView pagination defects: unstable page numbers, area-transition jumps, cover rendering/suppression, texture transitions, hyperlink handling, and renderer CSS behavior. That work now has local harness coverage and remains part of this plan.

The current priority is the native APK integration boundary: native touch controls above the WebView, shell-cover behavior, explicit content-action suppression, and phone validation of cover/EPUB/PDF taps. Continued reader upgrades from Anx Reader, Komikku, Readest, Colibrio, and LibreraReader references remain sequenced behind this stabilization work.

The page-curl mockup work, drag-to-turn animation, dual-page/spread animation, and rotation-triggered spread mode are explicitly lowest priority. They must not displace reader correctness, native tap ownership, shell-cover behavior, PDF navigation, cache/progress, Storyteller/readaloud support, or core settings work.

## Non-Negotiable Direction

- Do not debug renderer behavior primarily by repeatedly deploying phone builds.
- Do not keep inventing WebView tap handling when Komikku already demonstrates native viewer-owned tap zones.
- Do not render the EPUB cover in the WebView when Navic has a shell cover surface.
- Do not treat Foliate's raw relocation events as final page state during spine/area transitions.
- Do not batch unrelated reader improvements into opaque releases.

## Active Register: 2026-06-12

This document is the single source of truth for the current reader objective, including the previous fix and the pending phone-evaluable objective.

Previous fix registered here:

- Plain image taps are no longer blanket-suppressed as content-owned WebView taps.
- Real content actions now use an explicit `readerContentTapHandled` bridge signal.
- The Android native reader surface suppresses the paired native tap only after that explicit content-handled signal.
- Harness and host-test evidence is recorded in `Microdeliverable Checkpoint: 2026-06-12 Plain Image Tap Ownership Fix`.

Pending objective registered here:

- Build/release the next APK containing the plain-image tap ownership fix.
- Validate on the connected phone with ADB that cover image taps, cover margin taps, EPUB image taps, EPUB text taps, and PDF taps dispatch expected previous / next / center-menu behavior.
- Confirm image sepia-tint toggles still post `readerContentTapHandled` and do not accidentally turn pages or open chrome.
- Keep the implementation aligned to the Komikku three-layer model: native gesture owner, visual-only tap-zone overlay, separate Compose chrome/settings.

## Current Active Objective: Native Reader Surface Ownership

As of the eta39 ADB validation, this document is the single active reader plan. The immediate priority is no longer generic WebView pagination: Phase 1 renderer stabilization has useful harness coverage, and the current blocker is the native interaction boundary between the shell cover, WebView content, and Komikku-style tap-zone ownership.

The active objective is to unify the reader around Komikku's three-layer model:

1. The Android reader surface owns confirmed reader-wide gestures for shell cover, EPUB, PDF, and image-heavy pages.
2. The tap-zone overlay is visual/debug only and never decides navigation.
3. Compose reader chrome/settings are separate UI layers triggered by the native center/menu action.

The ADB-proven failure to fix first:

- Installed app: `v1.0.11-eta39`, `versionCode=372`.
- Navic receives injected taps and `ReaderSurfaceHost` logs `ACTION_DOWN` / `ACTION_UP`.
- The tap manager discards cover taps before zone mapping with `Reader surface tap ignored for content hitType=5`.
- `hitType=5` is `WebView.HitTestResult.IMAGE_TYPE`.
- The visible cover logs as `shellCover=false`, so the cover is effectively still WebView/image content for input purposes.

This must be fixed before more reader chrome, settings, PDF polish, page-curl animation, spread mode, or other reader enhancements. The fix must not be a narrow one-off for the current cover screenshot. The target behavior is:

- EPUB cover content is suppressed from the WebView reader path.
- The visible cover is rendered as the native shell-cover surface, or any fallback image-heavy first page is still governed by the native tap-zone manager.
- Plain image hits do not blanket-block native previous / next / center-menu tap zones.
- Anchors, image anchors, editable fields, media controls, text selection, and explicit image-tint toggles remain protected content actions.
- Drag/swipe ownership is explicit: Foliate/PDF.js keeps smooth child drag streams where needed, while native confirmed taps remain reliable.

The next microdeliverable starts with the failing focused test `ReaderRuntimeShellProgressTest.nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch`, then fixes only this input ownership bug and validates it with ADB over cover image, cover margins, EPUB image pages, EPUB text pages, and PDF pages.

## Phase 1 Scope: WebView Renderer Stabilization

Phase 1 is limited to the reader assets and renderer runtime:

- `composeApp/src/androidMain/assets/reader/index.html`
- `composeApp/src/androidMain/assets/reader/navic-reader.js`
- `composeApp/src/androidMain/assets/reader/vendor/foliate-js/**`
- reader CSS, fonts, texture assets, and test harness files

Phase 1 should avoid Kotlin/APK changes unless a tiny bridge contract adjustment is required and coordinated with the APK refactor thread.

### Laptop Simulation Harness

Create a local simulation environment that runs the same reader files packaged into the APK:

- Load the local `index.html`.
- Use the same `navic-reader.js`.
- Use the vendored Foliate/PDF.js assets.
- Use the same paper textures, border overlays, themes, font assets, and bridge protocol.
- Run in Playwright/Chromium with phone-like viewport sizes.
- Drive the reader through the same bridge commands the APK uses for next page, previous page, go-to location, theme changes, and settings updates.

The harness should emit structured traces for:

- raw Foliate relocation events
- committed display location
- page label and total page count
- href/spine index/CFI/progression
- texture variant key and selected assets
- texture movement offset
- area/spine transition start and end
- cover suppression decisions
- hyperlink/image click handling
- renderer warnings and errors

The durable local Phase 1 gate is:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "<path-to-epub>" --pdf-fixture "<path-to-pdf>"
```

This runs the current EPUB front-matter, page-boundary, shell-cover, CSS, texture, full-traversal, and PDF checks through the same reader assets packaged into the APK.

The first regression fixture should cover the observed failure path:

```text
shell cover -> EPUB start -> map/front matter -> author's note -> chapter content
```

If the exact user book cannot be committed as a fixture, use a local ignored fixture path plus a synthetic fixture that has the same structure: cover item, map/front matter spine item, author's note, and chapter content.

### Page Number Model

Replace direct raw-relocation page posting with a committed page state:

- Raw Foliate relocations become candidates.
- Candidates during spine/area transitions are not immediately shown as final state.
- The committed page changes only after the candidate is stable enough to represent the visible page.
- The display page is derived from a single normalized sequence, not separately from href/progression/CFI heuristics in different places.
- Shell cover is page index `-1` and is not counted as page `1 / total`.
- Suppressed EPUB cover spine items do not produce visible page numbers.

The goal is to prevent sequences such as:

```text
2 / 1050
3 / 1165
4 / 1165
2 / 357
```

and transition regressions such as repeated `4 / 411` pages followed by a jump to `6 / 411`.

### Texture Transition Model

Keep paper texture behavior tied to the visible committed page, not unstable relocation noise.

Required behavior:

- Each committed visible page gets a deterministic texture selection.
- The base paper texture uses the existing 3 x 2 x 2 variation model.
- The border degradation layer uses its own deterministic selection and is combined as a second layer.
- The same page always receives the same texture combination.
- Different pages can receive different texture combinations.
- Texture movement must follow the visible page movement during a page turn.
- Texture movement must not invert at area/spine transitions.
- Texture overlays apply to the whole reader surface, not individual DOM elements.

The harness should capture enough state to prove that texture selection and offset are stable across normal page turns and across spine/area boundaries.

### Cover Suppression

Add a basic safeguard so EPUB cover-like content is not rendered in the WebView when Navic is responsible for shell-cover presentation.

Detection should use conservative signals:

- OPF/item properties such as `cover-image` where exposed.
- guide/nav cover entries where exposed.
- first-spine image-only XHTML cover pages.
- common cover href/name patterns only as fallback.

Behavior:

- On reader open, Navic can show the shell cover first.
- When continuing into the WebView, the WebView starts at the first readable non-cover location.
- Seeking to page index `-1` returns to the shell cover.
- Going backward from the first readable page returns to shell cover instead of the suppressed EPUB cover page.

### Renderer CSS And Interaction Bugs

Phase 1 should also validate and fix renderer-only issues already reported:

- paragraph spacing not applying reliably
- paper texture not applying or using only one repeated variant
- hyperlink styling and activation intermittently failing
- hyperlink areas affecting nearby images
- sepia/theme backgrounds not affecting page/content surfaces consistently
- sepia image overlay behavior and click-to-toggle tint behavior
- PDF page centering and tap-turn coalescing where reproducible in the harness

## Phase 2 Scope: Komikku-Style Native Touch Ownership

After Phase 1 is stable, move tap controls out of the WebView.

Komikku reference findings:

- Komikku maps touches in native viewer code, not inside the rendered content.
- `ViewerNavigation.getAction(PointF)` maps normalized tap coordinates to actions.
- `PagerViewer` and `WebtoonViewer` dispatch actions from native touch events.
- `ReaderNavigationOverlayView` paints visible navigation zones separately from content.

Navic direction:

- Add a native Compose `ReaderTouchOverlay` above the WebView.
- Put next/previous/menu touch zones in this native overlay.
- Keep WebView interactions for content-specific actions such as text selection, links, and image tint toggling only where explicitly allowed.
- Add a visible tap-zone debug setting implemented natively.
- Support Komikku-style presets first: default, L-shaped, Kindle-ish, edge, right-and-left, disabled.

This should fix the reported cover issue where touch controls only work outside the image. Touch zones must sit above all rendered content, including shell cover and WebView content.

## Phase 3 Scope: APK Cover Surface

After the renderer and native touch layer are stable:

- Always surface the shell cover when opening a book, before resuming the saved reading location.
- Animate from shell cover to first readable/resumed page if it improves the experience.
- Treat shell cover as reader page index `-1`.
- Avoid rendering the EPUB cover page inside the WebView.
- Keep page numbering organic and book-like, with `current / total` formatting and the ebook font styling.

## Phase 4 Scope: Continued Reader Upgrades

Continue upgrading the reader in small deliverables:

- Anx Reader/Readest-style EPUB reader completeness.
- Komikku-style settings layout and tap-zone behavior.
- PDF/image scaling, centering, and navigation polish.
- Storyteller-generated readaloud EPUB support, including media overlays and audio metadata labels.
- Colibrio-inspired synced audiobook/ebook media support where it maps cleanly to Navic's architecture.

Lowest priority backlog floor:

- The page-curl HTML mockup, drag-to-turn animation, dual-page/spread animation, and rotation-triggered spread mode are deferred behind reader correctness, native touch ownership, shell-cover behavior, PDF navigation, cache/progress, Storyteller/readaloud media support, and core reader settings.
- Dual-page/spread animation is also deferred. Rotation-triggered spread mode, including switching to the spread animation model after phone rotation, should not be implemented during stabilization unless all higher-priority reader issues are already closed.
- The 2026-06-12 request to enter dual-page mode on phone rotation and use the spread-mode animation is explicitly part of this deferred backlog item, not an active stabilization task.
- If implemented later, page-curl should be a reader-owned snapshot overlay: portrait/single-page layout uses the clipped single-page model, and rotation into dual-page layout uses the spread model with real content on both sides.
- Do not spend active stabilization time on page-curl, dual-page, spread, or rotation-mode animation work until the core reader can reliably paginate, resume, render themes/textures, and handle native tap zones.

## Delivery Protocol

Every implementation slice should be a microdeliverable:

1. State the specific bug or behavior being changed.
2. State the files expected to change.
3. Add or update harness coverage before relying on phone validation.
4. Run syntax/tests for the changed layer.
5. Commit the slice.
6. Only create a release when there is a phone-evaluable behavior change.

For Phase 1, a release is not the deliverable. A passing harness trace and committed WebView fix is the deliverable. Phone release validation comes after WebView behavior is demonstrably stable locally.

## Definition Of Done For Phase 1

Phase 1 is complete when:

- The local harness can open an EPUB fixture through the same reader assets as the APK.
- The harness can advance through the known front-matter/area-transition path.
- Page labels remain monotonic and stable across that path.
- EPUB cover content is suppressed from WebView rendering.
- Shell cover handoff points are represented in bridge state.
- Texture variant and offset traces show no inversion or hard reset during page turns or area transitions.
- Renderer CSS checks pass for paragraph spacing, theme backgrounds, hyperlink presentation, and paper texture layering.
- The fixes are committed before APK integration begins.

## Validation Checkpoint: 2026-06-12

Current branch status:

- `master` is clean against `fork/master`, excluding untracked local `releases/` and `tmp/` folders.
- `v1.0.11-eta29` has been pushed and its `Navic.apk` release asset was produced by GitHub Actions.
- Page-curl mockup work, drag-to-turn animation, dual-page/spread animation, and rotation-triggered spread mode remain the lowest priority backlog item.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.BinderyReaderPublicationResolverTest" --tests "paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.ui.components.layouts.MiniPlayerVisibilityPolicyTest" --tests "paige.navic.domain.models.AudioPlaybackOwnershipPolicyTest"
```

Result: `BUILD SUCCESSFUL`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 11 checks`.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.StorytellerMediaOverlayParserTest" --tests "paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest"
```

Result: `BUILD SUCCESSFUL`.

Verified high-priority contracts:

- Bindery EPUB/PDF reader resources are materialized into the local `reader/reader-publications` cache and repeat opens reuse the cached file without refetching.
- Reader progress ignores repeated startup cover placeholders before saving the first real readable location.
- The WebView harness opens the real local EPUB, suppresses cover rendering, starts visible pages at page index `0`, traverses all visible pages with a stable total, and verifies texture/page-label behavior across page turns.
- PDF harness coverage checks horizontal centering, normal next-page movement, coalesced double next-page behavior, and fast sequential next-page behavior.
- Native Compose tap zones sit above WebView and shell-cover surfaces; WebView JavaScript no longer owns reader-wide center/menu/page tap classification.
- Storyteller/readaloud parsing preserves audio metadata labels including chapter, section, narrator, quality, source provider, source release, source URL, codec, bitrate, sample rate, and channel count.
- Music and audiobook mini-player visibility is scoped by app area, and Android playback managers use `AudioPlaybackArbitrator` so starting one owner pauses the other.

No new high-priority stabilization fix is currently confirmed by local evidence. The next implementation slice should start with a failing focused test only after a concrete gap is observed or selected from the Phase 4 reader-upgrade backlog.

## Microdeliverable Checkpoint: 2026-06-12 eta30

Scope:

- Keep the page-curl HTML mockup, drag-to-turn animation, dual-page/spread animation, and rotation-triggered spread mode at the lowest priority backlog floor.
- Fix stale default reader settings propagation in `ReaderScreen` by keying the default-settings `remember` block on every reader preference consumed by `readerDefaultSettings()`.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks executed.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta30
```

Result: `Android versionName matches v1.0.11-eta30`.

```powershell
gh run watch 27394239177 --repo Darkaxt/Navic --exit-status
```

Result: `v1.0.11-eta30 Build Navic` completed successfully. `Build Android APK` completed in 7m21s, `Verify release APK signing` passed, `Build iOS IPA` was skipped, and `Create GitHub Release` completed.

Published release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta30`
- Asset: `Navic.apk`
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta30/Navic.apk`
- Asset SHA-256 digest: `28255470f6c209bd8247808d03c5d8cbd6ba79c62fe326e1da09601fe7b9bf45`

## Microdeliverable Checkpoint: 2026-06-12 eta31

Scope:

- Add a Komikku-style reader-settings scope foundation with `Global`, `For this book`, and `Reset book` controls in the reader options sheet.
- Persist book-scoped reader settings in `PreferenceManager.readerBookSettingsJson`.
- Resolve reader settings from the selected book override when present, while leaving global defaults unchanged for other books.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerBookSettingsOverrideMergesOverGlobalDefaultsWithoutMutatingThem" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerBookSettingsOverrideIsScopedToTheRequestedBook" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerBookSettingsOverrideCanBeCleared" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsSupportKomikkuStylePerBookSettingsScope"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 7 executed and 17 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
git diff --check
```

Result: no whitespace errors.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta31
```

Result: `Android versionName matches v1.0.11-eta31`.

```powershell
gh run watch 27396083569 --repo Darkaxt/Navic --exit-status
```

Result: `v1.0.11-eta31 Build Navic` completed successfully. `Build Android APK` completed in 6m33s, `Verify release APK signing` passed, `Build iOS IPA` was skipped, and `Create GitHub Release` completed.

Published release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta31`
- Asset: `Navic.apk`
- Asset size: `13,152,601` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta31/Navic.apk`
- Asset SHA-256 digest: `2c2709378a6698e59a3ce1d5a287da619947d73742729488d6f9e85436251bbf`

## Validation Checkpoint: 2026-06-12 WebView Phase 1 Gate

Scope:

- Re-run the laptop WebView harness against the real local Hobbit EPUB and PDF fixtures before continuing APK-native work.
- Verify EPUB cover suppression, page labels, texture movement, shell-cover handoff, renderer CSS, full traversal, and PDF navigation from the same reader assets shipped in the APK.

Fresh validation evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode smoke
node tools\reader-harness\src\run-reader-harness.mjs --mode serve-smoke
node tools\reader-harness\src\run-reader-harness.mjs --mode trace-smoke
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Result: all commands exited `0`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 11 checks`.

Observed harness details:

- EPUB full traversal reported `505` visible pages and completed through page `501/505` progress logging before the final assertion passed.
- Shell-cover handoff starts with native shell cover visible, suppresses the WebView cover, advances to page `1/505` on the second next action, and returns to shell cover on previous from the first visible page.
- Texture scroll produced a positive renderer movement delta of `98` with `calc(50% - 98px)` background counter-movement.
- PDF smoke and fast sequential turn checks passed against a `3` page fixture, ending at page index `2`.

## Validation Checkpoint: 2026-06-12 Native Interaction Coverage

Scope:

- Verify current `master` includes the APK/native reader interaction refactor contracts before adding more native changes.
- Confirm reader-wide tap-zone ownership is native, shell-cover surfaces are covered by the native overlay, PDF/fixed-layout navigation guards remain green, and media/image/link tap guards still compile.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeNavigationFlowTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

## Microdeliverable Checkpoint: 2026-06-12 Readaloud Media3 Metadata Extras

Scope:

- Strengthen the Storyteller/readaloud metadata boundary between parser/runtime models and Android Media3 playback items.
- Add a common `ReadaloudMediaExtras` projection so readaloud-specific labels stay available as structured fields before Android Bundle serialization.
- Populate Media3 extras with `href`, `title`, `chapterLabel`, `sectionLabel`, `narrator`, `author`, `trackNumber`, `discNumber`, `durationMs`, audio format labels, source provider, source release, and source URL.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReadaloudMediaItemTest.mediaItemExtrasPreserveReadaloudSpecificMetadataLabels"
```

Initial result: failed at compile time with unresolved `toReadaloudMediaExtras`, proving the metadata-extras contract was not implemented.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReadaloudMediaItemTest" --tests "paige.navic.reader.StorytellerMediaOverlayParserTest" --tests "paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReadaloudModelsTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

## Microdeliverable Checkpoint: 2026-06-12 Imported Font Source Contract

Scope:

- Add a `custom` reader font source that can carry an imported font family and app-local font URL through settings, preferences, and the reader bridge.
- Keep imported font URLs constrained to the app-local reader-cache font path before WebView CSS emits an `@font-face` rule.
- Surface the imported source label in Settings > Ebooks and reader settings search.

Current limitation:

- This checkpoint does not implement the Android file picker or font-cache importer. It creates the safe runtime/settings contract that a later importer can populate.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderSettingsDefaultsTest.readerSettingsDefaultsKeepExpandedFontSources" --tests "paige.navic.reader.ReaderBridgeProtocolTest.openPublicationCommandDispatchesEscapedJsonToNavicReaderBridge" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripFontSourcePreference" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.androidReaderPackagesBundledFontSourcesForWebViewRendering" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderDefaultSettingsRememberKeyTracksReaderPreferenceInputs"
```

Initial result: failed at compile time with unresolved `ReaderFontSourceCustom`, missing custom font settings fields, and missing preference keys.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
```

Initial result: failed with `Expected custom font source; observed navic`, proving the WebView helper did not yet honor the custom font-source contract.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 3 executed and 21 up-to-date.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
```

Result: `reader harness font-css-smoke passed`.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
```

Result: all commands exited `0`.

## Microdeliverable Checkpoint: 2026-06-12 Android Imported Font Picker

Scope:

- Add a Settings > Ebooks import row for Android that opens a document picker for ebook font files.
- Copy selected TTF, OTF, WOFF, WOFF2, or TTC files into `context.cacheDir/reader/fonts`.
- Store the imported font family and `https://appassets.androidplatform.net/reader-cache/fonts/...` URL in the reader preferences, then switch the default font source to `custom`.
- Keep iOS as an explicit unsupported no-op for this Android-only slice.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontTest" --tests "paige.navic.reader.ReaderImportedFontCacheTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonSettingsEbooksScreenCanImportCustomFontIntoPreferences"
```

Initial result: failed at compile time with unresolved `ReaderImportedFontCache`, `readerImportedFontFamilyFromDisplayName`, and `readerImportedFontExtension`, proving the importer and cache path were not implemented.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontTest" --tests "paige.navic.reader.ReaderImportedFontCacheTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonSettingsEbooksScreenCanImportCustomFontIntoPreferences"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 13 executed and 11 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontTest" --tests "paige.navic.reader.ReaderImportedFontCacheTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
```

Result: all commands exited `0`.

## Microdeliverable Checkpoint: 2026-06-12 Imported Font Management

Scope:

- Add an imported-font cache readout to Settings > Ebooks so the user can see storage used by imported ebook fonts.
- Add a clear imported font action that deletes cached imported font files and resets the default reader font source back to Navic bundled fonts.
- Keep Android as the active cache-management implementation and iOS as an explicit unsupported/no-op importer.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontCacheTest.importedFontCacheReportsStorageAndCanBeCleared" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonSettingsEbooksScreenCanClearImportedFontAndShowsFontCacheStorage"
```

Initial result: failed at compile time with unresolved `cachedFontsByteSize` and `clearImportedFonts`, proving imported-font cache management was not implemented.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontCacheTest.importedFontCacheReportsStorageAndCanBeCleared" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonSettingsEbooksScreenCanClearImportedFontAndShowsFontCacheStorage"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 3 executed and 21 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderImportedFontTest" --tests "paige.navic.reader.ReaderImportedFontCacheTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode font-css-smoke
git diff --check
```

Result: both commands exited `0`; `reader harness font-css-smoke passed`.

## Microdeliverable Checkpoint: 2026-06-12 PDF/Image Options Foundation And Native Drag Fallback

Scope:

- Add a dedicated in-reader `PDF/Image` options tab for PDF publications, separate from EPUB/readaloud settings.
- Add normalized PDF/Image settings for page fit, crop borders, and page gap to `ReaderSettings`, default preferences, per-book override JSON, and bridge serialization.
- Preserve touch-zone tap behavior while adding a native horizontal-drag page-turn fallback for touch streams already intercepted by the native overlay.
- Keep page-curl, drag animation, dual-page/spread animation, and rotation-triggered spread mode deferred as visual polish.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.pdfReaderOptionsUseDedicatedPdfImageTabAndSettings" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripPdfImagePreferences" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsSeparatePdfImageSettingsByPublicationFormat"
```

Initial result: failed at compile time with unresolved `ReaderOptionsTab.PdfImage`, `publicationFormat`, `ReaderPdfFit*`, `pdfFitMode`, `pdfCropBorders`, `pdfPageGapPercent`, and PDF setting methods, proving the PDF/Image settings contract was not implemented.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.nativeTapOverlayDragCommandsRespectReadingDirection" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeTapOverlayTurnsHorizontalDragsWithoutDependingOnWebViewSwipeHandlers" --tests "paige.navic.reader.ReaderChromeStateTest.pdfReaderOptionsUseDedicatedPdfImageTabAndSettings" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripPdfImagePreferences" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsSeparatePdfImageSettingsByPublicationFormat"
```

Intermediate result: failed only in `ReaderRuntimeShellProgressTest.nativeTapOverlayTurnsHorizontalDragsWithoutDependingOnWebViewSwipeHandlers`, proving the native overlay still had tap-only handling before the drag fallback.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest.nativeTapOverlayDragCommandsRespectReadingDirection" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeTapOverlayTurnsHorizontalDragsWithoutDependingOnWebViewSwipeHandlers" --tests "paige.navic.reader.ReaderChromeStateTest.pdfReaderOptionsUseDedicatedPdfImageTabAndSettings" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripPdfImagePreferences" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest.commonReaderOptionsSeparatePdfImageSettingsByPublicationFormat"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 6 executed and 18 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
git diff --check
```

Result: command exited `0`.

## Microdeliverable Checkpoint: 2026-06-12 Restore Foliate-Owned Page Drags

Scope:

- Supersede the native horizontal-drag fallback from the prior checkpoint because it made page turns fire only after release and blocked Foliate's page-following drag animation.
- Keep the native tap overlay for the separate native cover surface only.
- Restore readable EPUB/PDF WebView tap-zone handling so taps still trigger previous/next/menu actions while `touchmove` remains owned by Foliate/PDF.js.
- Keep image/media taps and native cover taps from being hijacked by readable tap zones.
- Move visible tap-zone diagnostics back into the WebView layer for readable content, while native cover diagnostics remain native.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readerChromeIsImmersiveAndDrivenByNativeTapOverlay" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readableContentDragsRemainOwnedByFoliateInsteadOfNativeTapOverlay" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderNormalizesReadableTapZonesInWebViewLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderExposesVisibleTapZoneOverlayControl"
```

Initial result: failed in all four tests, proving readable tap zones were still modeled as native overlay ownership and JS tap-zone handling was not active.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readerChromeIsImmersiveAndDrivenByNativeTapOverlay" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.readableContentDragsRemainOwnedByFoliateInsteadOfNativeTapOverlay" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderNormalizesReadableTapZonesInWebViewLikeKomikku" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderExposesVisibleTapZoneOverlayControl"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 3 executed and 21 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderChromeStateTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 3 executed and 21 up-to-date.

Release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta32`
- Asset: `Navic.apk`
- Asset size: `13,154,253` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta32/Navic.apk`
- Asset SHA-256 digest: `de65fe5b16ae310e6b9e61ed80a0bd744d16dda9d413f4e5a4e026b4c2afe010`

## Microdeliverable Checkpoint: 2026-06-12 Android WebView Native Tap Observer

Scope:

- Move readable-content page/menu tap classification back into Android-native code without restoring the Compose overlay that blocked Foliate drag gestures.
- Add a `WebView.setOnTouchListener` observer that always returns `false`, allowing WebView/Foliate/PDF.js to keep the full drag stream.
- Stamp `nativeTapZones=true` onto Android reader `openPublication` and `applySettings` bridge commands so JavaScript visible tap-zone diagnostics can remain active without dispatching duplicate page/menu taps.
- Keep native tap dispatch conservative around WebView hit-test results for anchors and images so link navigation and image tint toggles are not obviously hijacked.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidWebViewObservesReadableTapsNativelyWithoutConsumingDrags" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeObserverOwnsTaps"
```

Initial result: failed in both tests, proving Android did not yet install a native non-consuming WebView tap observer and the reader bridge had no `nativeTapZones` guard.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidWebViewObservesReadableTapsNativelyWithoutConsumingDrags" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeObserverOwnsTaps"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 7 executed and 17 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 6 executed and 18 up-to-date.

Release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta33`
- Asset: `Navic.apk`
- Asset size: `13,170,669` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta33/Navic.apk`
- Asset SHA-256 digest: `1b9a59de4b8e17c59c4a8d23df23fc2d71cb51aee75dcf8577d14c7da343f78a`

## Microdeliverable Checkpoint: 2026-06-12 Preserve WebView Page Drag Gestures

Scope:

- Keep Android-native readable tap-zone dispatch from eta33.
- Protect the active Android WebView gesture stream with `requestDisallowInterceptTouchEvent(true)` so parent Compose/AndroidView interception cannot break Foliate's page-following drag animation.
- Release the interception guard on `ACTION_UP`, `ACTION_CANCEL`, multi-touch, or lost tracked pointer.
- Keep `setOnTouchListener` returning `false` so WebView/Foliate still receive every touch event.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidWebViewKeepsFoliateDragStreamOwnedByWebViewParentsCannotIntercept"
```

Initial result: failed in `ReaderRuntimeShellProgressTest.kt`, proving the native observer did not yet protect the WebView drag stream from parent interception.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.androidWebViewKeepsFoliateDragStreamOwnedByWebViewParentsCannotIntercept"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 24 executed.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName "v1.0.11-eta34"
git diff --check
```

Result: both commands exited `0`.

Release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta34`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27411506764`
- Asset: `Navic.apk`
- Asset size: `13,170,669` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta34/Navic.apk`
- Asset SHA-256 digest: `66eddc97e5144179060af8dbe6501f8291d60f5f535f38ef3fa4a044557c06c0`

## Microdeliverable Checkpoint: 2026-06-12 PDF/Image Settings Runtime Wiring

Scope:

- Continue the reader stabilization thread after the Whispersync discussion was moved out of scope for this thread.
- Wire the existing PDF/Image settings foundation into the WebView/Foliate fixed-layout renderer.
- Map PDF fit modes to renderer zoom values: width -> `fit-width`, page -> `fit-page`, height -> `fit-height`, original -> `1`.
- Convert `pdfPageGapPercent` into a viewport-relative fixed-layout page gap.
- Apply `pdfCropBorders` to image-backed fixed-layout pages by slightly scaling the page image inside its clipped page frame.
- Extend the Phase 1 harness with `pdf-image-settings` so PDF/Image settings are verified locally instead of only through APK inspection.

TDD evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode pdf-image-settings --fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Initial result: failed with `Expected PDF fit height to set fixed-layout zoom=fit-height; observed unset`, proving the renderer ignored the PDF/Image settings bridge fields.

Fresh validation evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode pdf-image-settings --fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness pdf-image-settings passed`.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\vendor\foliate-js\fixed-layout.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
```

Result: all commands exited `0`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode pdf-smoke --fixture "D:\Downloads\Trash\movements-2032026.pdf"
node tools\reader-harness\src\run-reader-harness.mjs --mode pdf-fast-sequential-turns --fixture "D:\Downloads\Trash\movements-2032026.pdf"
node tools\reader-harness\src\run-reader-harness.mjs --mode pdf-image-settings --fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: all three PDF harness checks passed.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 12 checks`.

```powershell
git diff --check
```

Result: command exited `0`.

## Microdeliverable Checkpoint: 2026-06-12 Native Tap-Zone Crash Fix And PDF/Image Defaults

Scope:

- Fix the phone-visible WebView error `this.updateTapZoneOverlayLayer is not a function` when Android opens the reader with `nativeTapZones=true`.
- Add `epub-native-tap-zone-open` to the reader harness and Phase 1 gate so the Android/native tap-zone bridge path is exercised locally.
- Expose PDF/Image defaults in Settings > Ebooks: fit mode, crop borders, and page gap.
- Add searchable Settings rows for those PDF/Image defaults.

TDD evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-tap-zone-open --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Initial result: failed with `this.updateTapZoneOverlayLayer is not a function`, matching the phone screenshot and proving the harness covered the Android/native tap-zone open path.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.ui.screens.settings.EbookReaderSettingsPolicyTest"
```

Initial result: failed at compile time with unresolved `ebookReaderSettingDescriptors` and `toSearchEntry`, proving the app-level PDF/Image settings/search descriptor contract did not exist yet.

Fresh validation evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-tap-zone-open --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Result: `reader harness epub-native-tap-zone-open passed`.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
```

Result: both commands exited `0`.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.ui.screens.settings.EbookReaderSettingsPolicyTest" --tests "paige.navic.ui.screens.settings.SettingsSearchPolicyTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest.readerDefaultSettingsRoundTripPdfImagePreferences"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 13 checks`.

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName "v1.0.11-eta36"
git diff --check
```

Result: both commands exited `0`.

Release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta36`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27415696224`
- Asset: `Navic.apk`
- Asset size: `13,171,633` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta36/Navic.apk`
- Asset SHA-256 digest: `1be8030ffb9fe3e40c014343604819fc217b939964d53d39713bb923377ab8d9`

## Microdeliverable Checkpoint: 2026-06-12 Single Top Tap Manager

Scope:

- Restore one reader-wide native tap manager above both the native shell cover and the WebView.
- Remove the Android WebView tap-zone observer that split touch ownership between cover and readable EPUB content.
- Keep Android `nativeTapZones=true` so the JavaScript runtime does not also dispatch reader-wide page/menu taps.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderUsesOneTopTapManagerAboveCoverAndWebView"
```

Initial result: failed because the reader overlay was still gated to `nativeShellCoverVisible && !optionsVisible`, and the Android WebView still installed `ReaderAndroidTapZoneObserver`.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderUsesOneTopTapManagerAboveCoverAndWebView" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest.androidReaderDisablesJavaScriptReadableTapDispatchWhenTopOverlayOwnsTaps"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
```

Result: all commands exited `0`.

Known follow-up:

- `phase1-stabilization` currently reaches `epub-texture-page-turns` and fails on an existing texture counter-motion assertion. That check is outside this Kotlin touch-manager change and needs a separate texture-focused pass before it is used as a release gate again.

Release evidence:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta37`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27419438894`
- Android job: `Build Android APK` completed successfully; `Verify release APK signing` passed.
- iOS job: skipped.
- Asset: `Navic.apk`
- Asset size: `13,171,633` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta37/Navic.apk`
- Asset SHA-256 digest: `bcae9e4da51a95f1815b90657bf1d2030c3f9e97f027c04ee68a19e3c2d29000`
- Local APK install attempt: downloaded release APK and verified SHA-256; `adb install -r` could not run because adb reported no connected devices.

## Microdeliverable Checkpoint: 2026-06-12 eta38 and eta39 Native Surface Iterations

Scope already shipped:

- eta38 moved reader-wide tap handling into `ReaderSurfaceHost` so shell-cover and WebView children live under a single native Android surface.
- eta39 removed delayed `GestureDetector` dependence and switched to direct `ACTION_DOWN` / `ACTION_MOVE` / `ACTION_UP` tap recognition with `ViewConfiguration.scaledTouchSlop`.
- Android bridge commands continue forcing `nativeTapZones=true` so JavaScript does not dispatch duplicate reader-wide page/menu taps.

Release and install evidence:

- eta39 local commit: `037dc8e0 fix(reader): handle native taps directly`.
- eta39 remote release tag: `v1.0.11-eta39`.
- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta39`
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta39/Navic.apk`
- Installed package check showed `versionCode=372` and `versionName=v1.0.11-eta39`.

ADB validation result:

```text
Reader surface touch down x=1600 y=1100 shellCover=false
Reader surface tap ignored for content hitType=5 x=1600 y=1100
Reader surface touch down x=984 y=1100 shellCover=false
Reader surface tap ignored for content hitType=5 x=984 y=1100
Reader surface touch down x=80 y=1100 shellCover=false
Reader surface tap ignored for content hitType=5 x=80 y=1100
```

Interpretation:

- The native reader surface receives the tap stream.
- The remaining failure is inside Navic's own content hit-test gate.
- `WebView.HitTestResult.IMAGE_TYPE` currently suppresses reader-wide tap-zone dispatch, so image-heavy content and cover-like pages block page turns and center-menu taps.
- Because the visible cover reports `shellCover=false`, the shell-cover handoff is not yet fully authoritative for input.

Pending objective registered from this checkpoint:

1. Make the native reader surface the authoritative tap-zone owner across shell cover, WebView EPUB, WebView PDF/fixed-layout, and image-heavy pages.
2. Remove plain `IMAGE_TYPE` from the blanket content-handled tap suppression path.
3. Preserve explicit content actions for anchors, image anchors, editable/media controls, text selection, and image tint toggling.
4. Verify with ADB that cover image taps, cover margin taps, EPUB image taps, EPUB text taps, and PDF taps all dispatch the expected previous / next / center-menu behavior.
5. Keep the Komikku three-layer model as the implementation standard: native gesture owner, visual-only tap-zone overlay, separate Compose chrome/settings.

TDD starting point:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch"
```

Expected initial result: fail while `readerContentHandledTap` still includes `WebView.HitTestResult.IMAGE_TYPE`.

## Microdeliverable Checkpoint: 2026-06-12 Plain Image Tap Ownership Fix

Scope:

- Remove `WebView.HitTestResult.IMAGE_TYPE` from the blanket native content-hit suppression path.
- Keep `SRC_ANCHOR_TYPE`, `SRC_IMAGE_ANCHOR_TYPE`, editable fields, phone, email, and geo hits protected as content-owned taps.
- Add explicit `readerContentTapHandled` bridge signaling for real media/image interactions such as sepia image-tint toggles and media-anchor taps.
- Let the Android native reader surface suppress only the paired native tap dispatch after that explicit content-handled signal.
- Preserve the current Komikku three-layer objective: native surface owns reader-wide tap zones, visible tap-zone overlay is diagnostic only, and Compose chrome/settings remain separate.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch"
```

Initial result: failed at `ReaderRuntimeShellProgressTest.kt:225`, proving plain `IMAGE_TYPE` was still part of the blanket suppression path.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderLetsMediaTogglesWinOverReadableTapZones"
```

Initial result after tightening the media contract: failed at `ReaderRuntimeImageLinkTest.kt:338`, proving the explicit content-handled bridge path did not exist yet.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderLetsMediaTogglesWinOverReadableTapZones"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 7 executed and 17 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 2 executed and 22 up-to-date.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

Result: both commands exited `0`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-native-tap-zone-open --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Result: `reader harness epub-native-tap-zone-open passed`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 13 checks`.

Phone validation requirement before release claim:

- Build and install a new APK containing this slice.
- With ADB logcat, verify cover image taps no longer log `Reader surface tap ignored for content hitType=5`.
- Verify cover image right/left/center taps, cover margin taps, EPUB image taps, EPUB text taps, and PDF taps dispatch expected previous / next / center-menu behavior.
- Verify image sepia-tint toggles post `readerContentTapHandled` and suppress the paired native page/menu tap.

## Microdeliverable Checkpoint: 2026-06-12 eta40 Release Preparation

Scope:

- Package the plain-image tap ownership fix as the next phone-evaluable Android release.
- Align local `master` with remote eta39 before committing because remote `v1.0.11-eta39` and local `v1.0.11-eta39` had different commit IDs but identical trees.
- Bump Android release metadata to `versionCode=373` and `versionName=v1.0.11-eta40`.

Fresh pre-release validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderLetsMediaTogglesWinOverReadableTapZones"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 1 executed, 1 from cache, 22 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 1 executed, 1 from cache, 22 up-to-date.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta40
git diff --check
```

Result: all commands exited `0`; release verifier printed `Android versionName matches v1.0.11-eta40`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 13 checks`.

Release and ADB validation status:

- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta40`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27438260692`
- Android job: `Build Android APK` completed successfully; `Verify release APK signing` passed.
- iOS job: skipped.
- Asset: `Navic.apk`
- Asset size: `13,171,669` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta40/Navic.apk`
- Asset SHA-256 digest: `9b1bb0c1c655a22f8327b38f62da06cbecfa9fd2c8ac5bb13ac733603a3de226`
- Local download verification: `Get-FileHash releases\v1.0.11-eta40\Navic.apk -Algorithm SHA256` produced `9B1BB0C1C655A22F8327B38F62DA06CBECFA9FD2C8AC5BB13AC733603A3DE226`.
- Pending phone validation: ADB currently reports no connected devices. When a device is available, install the release APK, verify native cover/EPUB/PDF tap zones with ADB logs, and verify explicit image/media content handling still suppresses paired native taps.

ADB validation helper:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -ApkPath releases\v1.0.11-eta40\Navic.apk -ExpectedVersionName v1.0.11-eta40 -ValidateReaderTaps -RequireReaderTapAction -NoLaunch -CaptureWaitSeconds 20
```

Use this after opening the target reader screen on the phone. During the 20-second capture window, tap the cover/image/text/PDF zones being validated. The helper now fails immediately if no ADB device is connected, checks the installed `versionName`, captures package/screenshot/UI/logcat artifacts, fails on the known `Reader surface tap ignored for content hitType=5` regression, and can require at least one native `Reader surface tap action=` log.

Repeatable tap preset:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -ApkPath releases\v1.0.11-eta40\Navic.apk -ExpectedVersionName v1.0.11-eta40 -ValidateReaderTaps -RequireReaderTapAction -NoLaunch -TapPreset ReaderHorizontalZones
```

Use the preset after manually opening each target state: shell cover, EPUB image page, EPUB text page, and PDF page. The preset reads `adb shell wm size` and injects left/center/right taps at `0.10,0.50`, `0.50,0.50`, and `0.90,0.50`, so validation does not depend on hard-coded phone coordinates.

Helper validation evidence:

```powershell
powershell -NoProfile -Command '$script = Get-Content -Raw scripts\adb-reader-smoke.ps1; [void][scriptblock]::Create($script); "parse-ok"'
```

Result: `parse-ok`.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -ExpectedVersionName v1.0.11-eta40 -ValidateReaderTaps -RequireReaderTapAction -NoLaunch
```

Result with no connected phone: failed early with `No adb devices are connected`, before install, launch, or input injection.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\adb-reader-smoke.ps1 -Package darkaxt.navic -ExpectedVersionName v1.0.11-eta40 -ValidateReaderTaps -RequireReaderTapAction -NoLaunch -TapPreset ReaderHorizontalZones
```

Result with no connected phone: failed early with `No adb devices are connected`; the preset does not run without a connected device.

## ADB Diagnosis: 2026-06-12 eta40 Reader Interaction Results

Installed app:

- Device: `SM_F966B`, package `darkaxt.navic`.
- Installed version: `versionCode=373`, `versionName=v1.0.11-eta40`.
- Current reader state: Hobbit EPUB open in the reader.

Confirmed fixed:

- Tapping the cover works.
- A cover image right-side tap no longer logs `Reader surface tap ignored for content hitType=5`.
- The same tap logs `Reader surface tap action=Right command=nextPage ... hitType=5 shellCover=false` and dispatches `nextPage`.
- Taps and drag gestures work on normal readable pages.

Still broken:

- Dragging does not work on the cover.
- The paper texture transition still inverts when moving from the maps into the Author's Note and stays inverted after that transition.
- Center-tapping interactive images toggles the image behavior, but it also brings back the reader menu bar.
- Chapter-selection links have the same ownership bug: interacting with the link can also surface reader chrome.

Root-cause diagnosis for the interactive-tap bug:

- The old eta39 blocker was blanket suppression of `WebView.HitTestResult.IMAGE_TYPE`; eta40 correctly removed that blocker.
- The remaining bug is event ordering. ADB logs show native `readerCenterTap` dispatch before the WebView bridge posts `readerContentTapHandled` from the image.
- Restoring `IMAGE_TYPE` suppression would regress cover/image page turns, so the fix must be narrower.

Next release-candidate slice:

- Keep edge page-turn taps immediate.
- Delay only center/menu dispatch briefly.
- Cancel that pending center/menu dispatch if WebView posts `readerContentTapHandled` during the delay window.
- Make normal link navigation post `readerContentTapHandled`, not only media/image link handling.
- Validate that image taps and chapter-selection links do not open reader chrome, while cover/image/text edge taps still turn pages.

Deferred follow-up slices:

- Cover drag support should be handled separately after the interactive-tap race is fixed.
- Texture inversion at the maps -> Author's Note transition should be handled separately as a renderer transition/sign bug.

## Release Candidate: 2026-06-12 eta41 Interactive Content Tap Ownership

Scope:

- Keep native left/right edge page-turn taps immediate so cover/image/text pages still turn without waiting for the WebView.
- Delay only the center/menu tap dispatch long enough for the WebView bridge to claim interactive content taps.
- Cancel the pending center/menu dispatch when the reader runtime posts `readerContentTapHandled`.
- Make regular EPUB links post `readerContentTapHandled` before navigation so chapter-selection links do not also open reader chrome.

Expected eta41 validation:

- Center-tapping an image should toggle the image sepia handling without surfacing the bottom reader menu.
- Tapping a chapter-selection link should navigate without surfacing the bottom reader menu.
- Right/left edge taps should still page on the shell cover, image pages, text pages, and PDF pages.
- The old `hitType=5` image suppression regression must remain absent.

Out of scope for eta41:

- Cover drag gestures still need a native gesture-forwarding slice.
- The paper texture sign inversion at maps -> Author's Note still needs a renderer transition/sign fix.

Fresh eta41 pre-release validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderCancelsPendingCenterChromeWhenContentHandlesTap" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerNormalLinksReportContentTapHandledBeforeNavigation"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 1 executed, 1 from cache, 22 up-to-date.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks, 1 executed, 1 from cache, 22 up-to-date.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 13 checks`; included EPUB shell-cover checks, native tap-zone open checks, texture page-turn checks, full EPUB traversal over 505 harness pages, PDF smoke, PDF fast sequential turns, and PDF image settings.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta41
git diff --check
```

Result: all commands exited `0`; release verifier printed `Android versionName matches v1.0.11-eta41`.

Release publication status:

- Commit: `f332dd6b8aa49c31e9a7b5c32df541a2be244fcb`
- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta41`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27443030348`
- Android job: `Build Android APK` completed successfully; `Verify release APK signing` passed.
- iOS job: skipped.
- Asset: `Navic.apk`
- Asset size: `13,171,673` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta41/Navic.apk`
- Asset SHA-256 digest: `8cc5144b4427b6d4c66cd31608c1bbe3cabd0a3722b53998004fd239c22b06b6`
- Local download verification: `Get-FileHash releases\v1.0.11-eta41\Navic.apk -Algorithm SHA256` produced `8CC5144B4427B6D4C66CD31608C1BBE3CABD0A3722B53998004FD239C22B06B6`.

ADB validation status:

- `adb devices -l` returned no connected devices after `adb start-server`.
- eta41 phone validation is still pending: install `releases\v1.0.11-eta41\Navic.apk`, verify image center taps do not surface chrome, verify chapter-selection links do not surface chrome, and verify edge page-turn taps still work.
