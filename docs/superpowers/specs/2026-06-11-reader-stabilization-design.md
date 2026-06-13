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

## ADB/User Validation Feedback: 2026-06-13 eta41 Reader Interaction Results

User-tested behavior from the installed eta41 release must be treated as the current bug register before any more reader work:

Confirmed working:

- Cover taps work.
- Normal readable EPUB page taps work.
- Normal readable EPUB drag gestures work.
- Image sepia-tint toggling works after interaction, although the initial image can load without the expected sepia filter applied.

Still broken:

- Dragging does not work on the cover.
- The paper texture transition still inverts when moving from the maps into the Author's Note and stays inverted after that transition.
- Center-tapping interactive images still also brings back the reader menu bar.
- Tapping links in the chapter-selection/frontmatter area still also brings back the reader menu bar.

Current priority order:

1. Fix interactive content center-tap ownership so image interactions and links do not surface reader chrome.
2. Fix the texture movement sign across the maps -> Author's Note area transition.
3. Fix cover dragging without regressing cover taps or normal-page drag gestures.
4. Re-run host harnesses first; only then publish a new Android release candidate for phone validation.

## Release Candidate: 2026-06-13 eta42 Reader Interaction And Texture Stabilization

Scope:

- Extend the Android native center/menu tap delay from `120ms` to `320ms`, while keeping left/right edge page-turn taps immediate.
- Mark `readerContentTapHandled` ownership before canceling pending center chrome so the delayed runnable suppresses itself even if callback cancellation races.
- Extract surface-paper texture offset math into `readerSurfacePaperTextureScrollOffset`.
- Clamp impossible directionless renderer coordinate wrap jumps to zero texture offset so they cannot become persistent inverted paper movement.
- Add the `texture-offset-logic` harness mode and include it in `phase1-stabilization`.
- Add the real EPUB `epub-texture-frontmatter-transition` harness mode over the cover -> maps -> Author's Note path.
- Tighten texture page-turn assertions so Foliate internal coordinate wraps are ignored only when the renderer jump is larger than two visible viewports; real forward texture inversion still fails.

TDD evidence:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
```

Initial result: failed with `readerSurfacePaperTextureScrollOffset helper is not exported`.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderGivesWebViewContentEnoughTimeToCancelCenterChrome"
```

Initial result: failed while `ReaderCenterTapDelayMs` was still `120L`.

Fresh validation evidence:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
```

Result: all commands exited `0`; texture-offset harness printed `reader harness texture-offset-logic passed`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-page-turns --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Result: both commands exited `0` and wrote fresh trace JSON files under `tools\reader-harness\output`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 15 checks`.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta42
git diff --check
```

Result: both commands exited `0`; release verifier printed `Android versionName matches v1.0.11-eta42`.

Phone validation required after release:

- Verify center-tapping interactive images toggles sepia image behavior without surfacing reader chrome.
- Verify chapter-selection/frontmatter links navigate without surfacing reader chrome.
- Verify left/right edge taps still page on shell cover, image pages, text pages, and PDF pages.
- Verify maps -> Author's Note texture movement no longer flips direction or remains inverted.
- Cover drag remains a known follow-up unless explicitly fixed in a later slice.

Release publication status:

- Commit: `e220cb834066bac7f15adb4b18a2095447ed2020`
- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta42`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27446667488`
- Android job: `Build Android APK` completed successfully; `Verify release APK signing` passed.
- iOS job: skipped.
- Asset: `Navic.apk`
- Asset size: `13,171,889` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta42/Navic.apk`
- Asset SHA-256 digest: `ec4dae1bcca833402a5a94c73c27b2595b99da65b3e417f40f04041e9303c748`

## Release Candidate: 2026-06-13 eta43 Shell Cover Drag Support

Scope:

- Add a native shell-cover horizontal swipe fallback in `ReaderSurfaceHost`.
- Keep readable WebView drags child-first: `dispatchTouchEvent` still calls `super.dispatchTouchEvent(event)` and returns the child result.
- Restrict the new swipe fallback to `shellCoverVisible`; normal EPUB/PDF drag streams remain renderer-owned.
- Use the same direction semantics as existing reader page turns: swipe left maps through `ReaderTapZoneAction.Right`, swipe right maps through `ReaderTapZoneAction.Left`, and `readerTapZonePageTurnCommand` applies RTL/LTR direction.
- Ignore vertical or short movements using `ViewConfiguration.scaledPagingTouchSlop`.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Initial result: failed while no native shell-cover swipe path existed.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Result: `BUILD SUCCESSFUL` after Kotlin daemon fallback compilation.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`.

Phone validation required after release:

- Verify dragging left on the shell cover advances into the first readable page.
- Verify dragging right on the shell cover does not accidentally advance in LTR.
- Verify cover taps still work.
- Verify normal readable EPUB drags still work.
- Verify PDF/fixed-layout swipes still work.

Release publication status:

- Commit: `32642c7fef2b1f95318d7f4a3fde58635a9dcd1f`
- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta43`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27447882521`
- Android job: `Build Android APK` completed successfully; `Verify release APK signing` passed.
- iOS job: skipped.
- Asset: `Navic.apk`
- Asset size: `13,171,889` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta43/Navic.apk`
- Asset SHA-256 digest: `d27ff1aad67a7cbc930fe73909c74ae829718d3a048fe75f4acf117ab7242c27`

## ADB/User Validation Feedback: 2026-06-13 eta43 Reader Interaction Results

User-tested behavior from the latest phone session is the current bug register before further stabilization work:

Confirmed working:

- Cover tapping works.
- Normal readable EPUB taps work.
- Normal readable EPUB drag gestures work.
- Image sepia-tint interaction can bring the tint back and toggle it as expected, even if the image initially loads without the expected sepia filter.

Still broken:

- Dragging does not work on the cover in the latest phone test, so eta43's shell-cover swipe fallback is not yet proven on-device.
- Center-tapping interactive images still also surfaces reader chrome.
- Tapping links in the chapter-selection/frontmatter area still also surfaces reader chrome.
- The paper texture transition still inverts when moving from the maps into the Author's Note and remains inverted after that area transition.

Current priority order:

1. Fix interactive content center-tap ownership so image interactions and chapter/frontmatter links do not surface reader chrome.
2. Re-check shell-cover drag with ADB once a device is attached; if eta43's fallback is not firing, diagnose the native shell-cover event path rather than adding another blind gesture path.
3. Fix the paper-texture sign inversion at the maps -> Author's Note area transition.
4. Publish the next APK only after host/harness evidence proves the selected slice and the release is phone-evaluable.

## Release Candidate: 2026-06-13 eta44 Interactive Touch Ownership

Scope:

- Claim content ownership during the touch phase for actual EPUB image/media targets and real text-link hits.
- Keep ordinary reader-area taps untouched so left/right page-turn zones remain immediate.
- Keep final click handlers for image sepia toggles and link navigation, but stop relying on synthetic click timing to suppress native center chrome.
- Do not change texture movement or cover drag in this slice.

Root-cause evidence:

- eta41/eta42 delayed native center chrome after `ACTION_UP`, but phone validation still showed image taps and chapter/frontmatter links surfacing the reader menu.
- The existing runtime only posted `readerContentTapHandled` from final `click`/toggle/navigation handling.
- Android's center-menu runnable can therefore win when WebView/Foliate iframe click delivery is late.
- The existing `epub-texture-frontmatter-transition` harness does not yet reproduce the phone texture failure: the trace stayed in `OEBPS/Text/1.html` from pages `4 -> 6`, so the texture bug remains open instead of being declared fixed.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderClaimsInteractiveTouchBeforeNativeCenterChromeCanDispatch"
```

Initial result: failed while no `claimReaderInteractiveContentTouch` path existed.

Implementation:

- `navic-reader.js` now detects real media/image targets and real text-link hits during `touchstart` and `touchend`.
- Those paths post the existing `readerContentTapHandled` bridge event with `media-touch` or `link-touch`.
- The Android native surface already treats that bridge event as content ownership and cancels/suppresses pending center chrome.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderClaimsInteractiveTouchBeforeNativeCenterChromeCanDispatch"
```

Result: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta44
git diff --check
```

Result: all commands exited `0`; release verifier printed `Android versionName matches v1.0.11-eta44`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: first attempt timed out at the 5-minute command limit; rerun with a larger command timeout passed all 15 checks, including EPUB full traversal and PDF smoke/fast-turn/image-settings checks.

Phone validation required after release:

- Center-tapping an image should toggle sepia image behavior without surfacing reader chrome.
- Tapping a chapter-selection/frontmatter link should navigate without surfacing reader chrome.
- Left/right edge taps should still page on shell cover, image pages, text pages, and PDF pages.
- Confirm normal readable EPUB drag gestures still work.
- Texture inversion at maps -> Author's Note and cover drag remain separate open issues unless this release is later proven to affect them.

Release publication status:

- Commit: `1239b779992874b21a52990a4ac54003921ee9e8`
- Release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta44`
- Workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27449235129`
- Android job: `Build Android APK` completed successfully; `Verify release APK signing` passed.
- iOS job: skipped.
- Asset: `Navic.apk`
- Asset size: `13,172,033` bytes
- Asset URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta44/Navic.apk`
- Asset SHA-256 digest: `734a5669f8766160d6c2795fb1788b742e650584ef8e8ef950d9e4f70e2ff23a`
- Local download verification: `Get-FileHash releases\v1.0.11-eta44\Navic.apk -Algorithm SHA256` produced `734A5669F8766160D6C2795FB1788B742E650584EF8E8EF950D9E4F70E2FF23A`.
- ADB validation status: `adb devices -l` returned no connected devices from this session, so phone validation is pending.

## Release Candidate: 2026-06-13 eta45 Shell Cover Move-Phase Swipe Dispatch

Latest phone behavior register before eta45 release:

- Cover state: tapping works, but dragging on the cover does not.
- Cover touch ownership: taps only work reliably outside the cover image area, which means the current overlay is still not behaving like a true top-level touch layer over the whole visual reader surface.
- Normal EPUB pages: taps and drags work on readable pages.
- Image interaction: tapping an image center toggles the image tint as expected, but it also brings the menu bar back. Interactable content must not toggle reader chrome.
- Link interaction: tapping links in the chapter/table-of-contents flow also brings the menu bar back. Real link activation must not toggle reader chrome.
- Texture transition: texture movement is correct from the cover through the maps, then inverts at the maps -> Author's Note area transition and stays inverted afterwards.
- Priority order: first restore reliable touch ownership, then fix texture sign/area transition behavior, then continue the larger reader stabilization plan.

Scope:

- Fix the phone-reported shell-cover drag failure without changing readable EPUB/PDF drag ownership.
- Keep `ReaderSurfaceHost.dispatchTouchEvent()` child-first and return the WebView child result.
- Continue to observe the child touch stream natively.
- Dispatch shell-cover horizontal swipes during `ACTION_MOVE` once the movement crosses the paging threshold, instead of depending only on receiving `ACTION_UP`.
- Preserve the `ACTION_UP` fallback for streams that do complete normally.

Root-cause evidence:

- eta43 added shell-cover swipe dispatch only after `ACTION_UP`.
- The latest phone test still reported: cover taps work, but dragging the cover does not.
- Code inspection showed `ACTION_MOVE` stopped tracking after regular tap slop, then deferred the shell-cover swipe until `ACTION_UP`.
- If the child WebView/gesture pipeline cancels or disrupts the stream during cover drag, the native fallback never fires.

Texture investigation evidence:

- A local Chromium touch probe against the real Hobbit EPUB exercised Foliate drag gestures through early frontmatter pages and the first spine boundary.
- During those local forward drags, `renderer.containerPosition` increased and surface texture CSS moved left (`calc(50% - Npx)`), including across `OEBPS/Text/1.html -> OEBPS/Text/2.html`.
- That did not reproduce the phone-reported maps -> Author's Note texture inversion, so no texture production code was changed in eta45.
- The texture issue remains open until a harness/ADB trace reproduces the failing transition.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Initial result: failed at `ReaderRuntimeShellProgressTest.kt:229` because `ACTION_MOVE` did not call `dispatchReaderShellCoverSwipe`.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks executed.

Phone validation required after release:

- Drag left on the shell cover should advance into the first readable page.
- Drag right on the shell cover should not accidentally advance in LTR.
- Cover taps should still work.
- Normal readable EPUB drags should still work.
- PDF/fixed-layout swipes should still work.

Release publication status:

- Published release tag: `v1.0.11-eta45`
- Release URL: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta45`
- APK URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta45/Navic.apk`
- GitHub asset digest: `sha256:343654220aa0a3f76d6b48fda0137a45e22198174709921df472e39464528428`
- ADB validation status: `adb devices -l` returned no connected devices from this session, so phone validation is pending.

## Release Candidate: 2026-06-13 eta46 Interactive Image Center-Tap Suppression

Scope:

- Fix the phone-reported image interaction leak where tapping an image center toggles sepia tint but also brings the reader menu bar back.
- Keep edge taps over image content available for page turns.
- Add center/menu-only suppression for native `WebView.HitTestResult.IMAGE_TYPE` so the native overlay does not depend entirely on a racing JavaScript bridge message for image taps.
- Leave anchor/link handling unchanged in this slice; link menu leakage remains open unless eta46 phone validation proves it was the same native center-hit race.

Root-cause evidence:

- eta44 posts `readerContentTapHandled` from JS touch/click handlers, but phone validation still showed image center taps surfacing chrome.
- Android native tap-zone dispatch deliberately does not treat `IMAGE_TYPE` as a blanket content hit because plain image hits must not block edge previous/next zones.
- The missing distinction was center/menu-only content handling: images can be page-turn surfaces at the edges but must be treated as interactive content in the center.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceSuppressesCenterChromeForInteractiveImageHitsOnly"
```

Initial result: failed at `ReaderRuntimeShellProgressTest.kt:274` because no `readerContentHandledCenterTap` path existed.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeReaderSurfaceSuppressesCenterChromeForInteractiveImageHitsOnly"
```

Result: `BUILD SUCCESSFUL`; 24 actionable tasks executed.

Phone validation required after release:

- Center tapping a sepia-tinted image should toggle the image tint only.
- The same image tap should not open the reader menu bar.
- Edge taps over image content should still page-turn when the configured tap zone maps that edge to previous/next.
- Text links and table-of-contents links still need explicit validation; if they still open chrome, the next slice must target the link-specific path.

Release publication status:

- Published release tag: `v1.0.11-eta46`
- Release URL: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta46`
- APK URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta46/Navic.apk`
- GitHub asset digest: `sha256:b38a31f3ba8136f04d025b902ea2b60bf2a10db1f9430e7ddf951ad32b7d570a`
- GitHub workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27451039284`
- Android build job: success.
- iOS jobs: skipped by workflow.
- ADB validation status: `adb devices -l` returned no connected devices from this session, so phone validation is pending.

## Investigation Checkpoint: 2026-06-13 Texture Inversion Reproduction

Latest local reproduction attempt:

- Ran the existing `epub-texture-frontmatter-transition` harness against `D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub`; it passed, but still only sampled shallow frontmatter bridge turns.
- Ran a temporary real-touch Chromium probe over the same EPUB, swiping through frontmatter pages `0 -> 13`.
- Every forward drag produced negative texture X offsets (`-35px` through about `-280px`) and monotonically increasing `renderer.containerPosition`.
- The desktop Chromium path therefore did not reproduce the phone-reported inversion where maps -> Author's Note flips texture movement and stays inverted.

Current conclusion:

- Do not flip texture sign globally from the desktop harness result; that would likely regress the path that is already correct.
- The next texture slice needs Android WebView evidence: either ADB/WebView inspection while reproducing the maps -> Author's Note transition, or richer `console.debug` texture logs that include page index, href, `renderer.containerPosition`, `surfacePaperTextureBaseOffset`, `pageTurnDirection`, and computed offset.

## Release Candidate: 2026-06-13 eta47 Texture Transition Diagnostics

Scope:

- Add Android-visible texture diagnostics without changing texture movement behavior.
- Enrich `surface-texture-scroll` console logs with computed offset, renderer position, base offset, delta, page-turn direction, current page label, and href.
- Add `surface-texture-update` console logs when a committed page selects a new texture variant.
- Enrich `texture:scroll` and `texture:update` harness trace payloads with the same diagnostic fields.

Reason:

- Local Chromium real-touch probing did not reproduce the phone-reported maps -> Author's Note inversion.
- The phone failure still needs evidence showing whether the inverted movement comes from Android WebView reporting a reversed `renderer.containerPosition`, a late `surfacePaperTextureBaseOffset` reset, or an unexpected `pageTurnDirection` state.

Phone validation target:

- Install eta47.
- Open the Hobbit EPUB in sepia mode.
- Move from the maps into Author's Note and then one or two pages after the inversion.
- Capture logcat lines containing `surface-texture-scroll` and `surface-texture-update`.
- A useful failing trace must include at least one scroll line around the inversion with `x=`, `pos=`, `base=`, `delta=`, `dir=`, `page=`, and `href=`.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSyncsSurfaceTextureWithPaginatorScrollDrags"
```

Initial result: failed at `ReaderRuntimePaperSurfaceTest.kt:277` because no structured texture diagnostic state existed.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
powershell -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta47
git diff --check
```

Result: all commands passed. The generated `epub-texture-frontmatter-transition.trace.json` includes `texture:scroll` and `texture:update` payloads with `offset`, `position`, `baseOffset`, `delta`, `pageTurnDirection`, `flowMode`, `pageIndex`, `pageCount`, `href`, and `textureKey`.

Release publication status:

- Published release tag: `v1.0.11-eta47`
- Release URL: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta47`
- APK URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta47/Navic.apk`
- GitHub asset digest: `sha256:fbb6050692aae31628ab4f98da637a29c53ced27b4dbe83f376e763634256b2d`
- GitHub workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27451669574`
- Android build job: success.
- iOS jobs: skipped by workflow.
- Local download verification: `Get-FileHash releases\v1.0.11-eta47\Navic.apk -Algorithm SHA256` produced `FBB6050692AAE31628AB4F98DA637A29C53CED27B4DBE83F376E763634256B2D`.
- ADB validation status: `adb devices -l` returned no connected devices from this session, so phone validation is pending.

## ADB/User Validation Feedback: 2026-06-13 eta47 Reader Interaction Results

This feedback supersedes the pending eta45/eta46/eta47 phone-validation assumptions. It is the current behavior register before any eta48 implementation.

Confirmed working:

- Cover tapping works.
- Normal readable EPUB taps work.
- Normal readable EPUB drag gestures work.
- Image sepia-tint toggling works visually: interacting with the image can bring the tint back and toggle it out again.

Still broken:

- Dragging does not work on the native cover surface.
- Tapping interactive images in the center still also surfaces reader chrome.
- Tapping links in the chapter/frontmatter selection flow still also surfaces reader chrome.
- The paper texture transition works from the cover through the map pages, then inverts specifically while moving from the maps into the Author's Note and remains inverted after that area transition.

Diagnosis constraints:

- Do not explain the texture inversion as a generic mid-motion relocation reset unless ADB/WebView evidence proves that path. The observed failure is area-transition-specific.
- Do not fix cover drag by reintroducing WebView-owned page-zone handling. Cover taps and drags must be owned above the rendered cover surface.
- Do not treat image/link content actions as reader-center taps. If an EPUB image tint toggle or link navigation is the intended content action, it must claim content ownership before native chrome can toggle.

Next eta48 target:

1. Strengthen diagnostics so ADB/logcat can capture touch ownership and texture state in one repeatable command.
2. Harden interactive content ownership so image and link actions cancel native center chrome even when Android WebView hit testing reports an iframe/unknown target.
3. Rework cover drag ownership only after a focused test proves the native cover surface receives or loses the drag stream.
4. Keep texture movement production code unchanged until the maps -> Author's Note transition trace identifies whether the sign flip comes from renderer position, base offset, page-turn direction, or area-transition coordinate wrapping.

## Release Candidate: 2026-06-13 eta48 Shell-Cover Touch Ownership And Reader Diagnostics

Scope:

- Add a focused `adb-reader-smoke.ps1 -CaptureReaderDiagnostics` mode that writes touch, content-ownership, and texture diagnostics into separate artifact files.
- Keep the normal reader smoke capture path intact while adding `reader-touch-diagnostics.log`, `reader-texture-diagnostics.log`, and `reader-diagnostics-summary.txt`.
- Let `ReaderSurfaceHost` own touch streams before child dispatch only while the native shell cover is visible, so cover taps and cover drags are above the cover WebView surface.
- Keep readable EPUB/PDF touch streams child-first so Foliate/PDF drag behavior remains renderer-owned.
- Claim real EPUB anchor touches during `touchstart` / `touchend` before text-rectangle navigation hit testing, so intended link interactions can cancel native center chrome even when Android hit testing reports an iframe/unknown target.
- Do not change paper texture movement production code in this slice; eta48 is intended to collect decisive ADB texture evidence for the maps -> Author's Note inversion.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics"
```

Initial result: failed because `adb-reader-smoke.ps1` did not expose `-CaptureReaderDiagnostics` or focused touch/texture artifact files.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderClaimsAnchorTouchBeforeTextRectNavigationHitTesting"
```

Initial result: failed because touch-phase link ownership still depended on `readerPointInsideAnchorText(anchor, event)`.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverTouchStreamIsOwnedBeforeCoverWebViewChildDispatch"
```

Initial result: failed because shell-cover touch streams were still dispatched to the cover WebView child before native reader handling.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderClaimsAnchorTouchBeforeTextRectNavigationHitTesting"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverTouchStreamIsOwnedBeforeCoverWebViewChildDispatch"
```

Result: each focused test passed after its corresponding implementation. The shell-cover focused test needed a rerun with a larger command timeout; the rerun completed successfully.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeAssetsTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderBridgeProtocolTest"
```

Result: `BUILD SUCCESSFUL`.

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
```

Result: all commands exited `0`; the harness checks passed.

Phone validation required after release:

- Drag left on the native cover should advance into the first readable page.
- Cover taps should still work.
- Normal readable EPUB taps and drags should still work.
- Center tapping an image should toggle sepia tint without surfacing reader chrome.
- Tapping a chapter/frontmatter link should navigate without surfacing reader chrome.
- Run `scripts\adb-reader-smoke.ps1 -ExpectedVersionName v1.0.11-eta48 -NoLaunch -CaptureReaderDiagnostics` after reproducing the maps -> Author's Note transition; inspect `reader-texture-diagnostics.log` and `reader-diagnostics-summary.txt` for `surface-texture-scroll` lines with `pos`, `base`, `delta`, `dir`, `page`, and `href`.

Release publication status:

- Published release tag: `v1.0.11-eta48`.
- Release URL: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta48`.
- APK URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta48/Navic.apk`.
- GitHub asset digest: `sha256:2686b1e9a14238945805c7057b4b6891bf0b890f96a27404edac6a1c23981b30`.
- GitHub workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27452637945`.
- Android build job: success; release APK signing verification passed.
- iOS jobs: skipped by workflow.
- Local download verification: `Get-FileHash releases\v1.0.11-eta48\Navic.apk -Algorithm SHA256` produced `2686B1E9A14238945805C7057B4B6891BF0B890F96A27404EDAC6A1C23981B30`.
- ADB validation status: pending; eta48 is published for phone validation and diagnostic capture.

## ADB/User Validation Feedback: 2026-06-13 eta48 Reader Interaction Results

This feedback supersedes the pending eta48 phone-validation assumptions. It is the current behavior register before any eta49 implementation.

Confirmed working:

- Cover tapping works.
- Normal readable EPUB taps work.
- Normal readable EPUB drag gestures work.
- Image sepia-tint toggling works visually: image interaction can restore and remove the sepia tint as expected.

Still broken:

- Dragging does not work on the native cover surface.
- Tapping interactive images in the center still also surfaces reader chrome.
- Tapping links in the chapter/frontmatter selection flow still also surfaces reader chrome.
- The paper texture transition works from the cover through the map pages, then inverts specifically while moving from the maps into the Author's Note and remains inverted after that area transition.

Priority order:

1. Fix explicit content-action ownership so image tint toggles and link navigation do not also toggle reader chrome.
2. Fix shell-cover drag ownership without regressing normal EPUB/PDF drags.
3. Diagnose the maps -> Author's Note texture inversion with Android/WebView evidence before changing texture movement production code.

Diagnosis constraints:

- Do not explain the texture inversion as a generic mid-motion relocation reset unless Android/WebView evidence proves that path. The observed failure is area-transition-specific.
- Do not fix cover drag by reintroducing WebView-owned reader-wide gesture zones.
- Do not treat image/link content actions as reader-center taps. Intended content actions must claim content ownership before native chrome can toggle.
- Do not ship another phone-facing APK without either a focused local regression test or ADB trace proving what changed.

## Microdeliverable Checkpoint: 2026-06-13 eta49 Content-Action Chrome Suppression Hardening

Scope:

- Register eta48 phone feedback as the current behavior baseline.
- Strengthen the local `css-smoke` harness so image tint toggles and text-link navigation must emit `readerContentTapHandled` through the Android bridge.
- Add touch-phase `css-smoke` coverage for `media-touch` and `link-touch`, because Android native chrome races the WebView touch sequence, not just synthetic click handlers.
- Harden native center-menu scheduling so center chrome waits longer for slow Android WebView bridge delivery before opening.
- Keep edge page-turn taps immediate; the delay only applies to center-menu dispatch.

Root-cause evidence:

- The real Hobbit EPUB fixture passes the new renderer ownership smoke: image click sources are `image`, image touch sources include `media-touch`, text-link click source is `link`, and text-link touch sources include `link-touch`.
- Because renderer bridge ownership is present locally while eta48 still opened chrome on image/link actions, the remaining likely gap is native timing/dispatch, not missing renderer messages.
- No ADB device was available in this session, so this slice does not claim phone validation.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
```

Initial result: failed because `css-smoke` did not record or assert image/link content-ownership bridge messages.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsSlowWebViewBridgeDeliveryBeforeOpeningCenterChrome"
```

Initial result: failed because native center chrome still used `ReaderCenterTapDelayMs = 320L`.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderAllowsSlowWebViewBridgeDeliveryBeforeOpeningCenterChrome" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderGivesWebViewContentEnoughTimeToCancelCenterChrome" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderCancelsPendingCenterChromeWhenContentHandlesTap"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
git diff --check
```

Result: all commands exited `0`. One grouped Gradle run needed a longer shell timeout and passed on rerun.

Phone validation target after release:

- Center tapping an image should toggle sepia tint without surfacing reader chrome.
- Tapping a chapter/frontmatter link should navigate without surfacing reader chrome.
- Normal readable-page center taps should still open chrome, with a slightly longer delay.
- Edge taps should still turn pages without the center-menu delay.
- Cover drag and the maps -> Author's Note texture inversion remain separate unresolved issues.

Release publication status:

- Published release tag: `v1.0.11-eta49`.
- Release URL: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta49`.
- APK URL: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta49/Navic.apk`.
- GitHub asset digest: `sha256:d74b1a71ec8d597af7d82a694be3b2d6c45b178034b478d7726bf7b0d25c9396`.
- GitHub workflow run: `https://github.com/Darkaxt/Navic/actions/runs/27455175041`.
- Android build job: success; release APK signing verification passed.
- iOS jobs: skipped by workflow.
- Local download verification: `Get-FileHash releases\v1.0.11-eta49\Navic.apk -Algorithm SHA256` produced `D74B1A71EC8D597AF7D82A694BE3B2D6C45B178034B478D7726BF7B0D25C9396`.
- ADB validation status: initially pending because no device was available during release publication; user phone validation results are now registered below.

## Phone Checkpoint: 2026-06-13 eta49 Reader Interaction Validation

Observed eta49 behavior:

- Tapping works on the shell cover.
- Dragging does not work on the shell cover.
- Taps and drags work on normal EPUB pages.
- Interacting with images by tapping center still surfaces the reader chrome, even when the intended content action is only the sepia image-tint toggle.
- Interacting with links in the chapter selection still surfaces the reader chrome.
- The texture transition behaves correctly from the shell cover through early pages, then inverts when moving from the maps/frontmatter area into Author's Note.
- After that maps -> Author's Note transition, texture movement remains inverted.

Immediate conclusions:

- The cover failure is not a global native tap failure; it is specific to cover drag recognition or cover drag routing.
- The content-action chrome leak is still a native/WebView timing or ownership boundary issue for explicit image/link actions, not a complete loss of content action handling.
- The texture problem is not a random mid-motion relocation reset throughout the whole book; the user-visible break happens at an area/section transition and persists afterward.

Next phone-testable microdeliverable:

- Harden shell-cover drag recognition without changing normal-page tap/drag behavior.
- Keep normal EPUB edge taps and drags working.
- Add diagnostics or a focused production fix for the maps -> Author's Note texture sign/area transition.
- Suppress reader chrome when image tint toggles or chapter links handle the interaction.
- Publish the next APK release only after focused host tests pass, because this slice changes packaged Android reader behavior.

## Harness Checkpoint: 2026-06-13 Author's Note Texture Boundary Coverage

Scope:

- Fix the local `epub-texture-frontmatter-transition` harness so it actually targets the reported frontmatter boundary instead of sampling shallow pages `4 -> 6`.
- Use the reader's real `search` bridge command to locate the second `Author's Note` search hit, which is the rendered heading, not the table-of-contents entry.
- Seek to that heading, step back one page, and animate forward through `author-note-boundary`.
- Record `authorBoundarySearch.first`, `authorBoundarySearch.searchResult`, `authorBoundarySearch.before`, and `authorBoundarySearch.author` in the emitted trace.
- Keep production texture code unchanged because the stronger Chromium harness still does not reproduce the Android-only inversion.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessTextureFrontmatterTransitionTargetsVisibleAuthorNoteBoundary"
```

Initial result: failed because the harness still hard-coded `while (Number(currentLocation?.pageIndex) < 4)` and did not contain a visible/text/search target for the real Author's Note boundary.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessTextureFrontmatterTransitionTargetsVisibleAuthorNoteBoundary"
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Result:

- The focused host test passed.
- The harness syntax check exited `0`.
- The real EPUB boundary harness passed and wrote `tools\reader-harness\output\epub-texture-frontmatter-transition.trace.json`.
- The trace now targets the actual heading boundary: search result `epubcfi(/6/2!/4/180/2,/1:0,/1:13)`, before page `3 / 505`, author heading page `4 / 505`, href `OEBPS/Text/1.html`.
- Texture deltas during the animated forward boundary remained negative on desktop Chromium, for example `posDelta=112` with `texDelta=-77`, `posDelta=253` with `texDelta=-229`, and `posDelta=366` with `texDelta=-366`.

Current conclusion:

- The old local harness coverage was falsely named and too shallow.
- The strengthened local harness now exercises the real Author's Note heading boundary and does not reproduce the phone-reported inversion.
- The next texture production fix still requires eta48 ADB diagnostics from Android WebView around the failing maps -> Author's Note transition.

## Implementation Checkpoint: 2026-06-13 eta50 Candidate Interaction Hardening

Scope:

- Keep eta49's working normal-page taps and drags intact.
- Make shell-cover horizontal drags use the same tap-slop-sized recognition threshold as the rest of the native reader surface, instead of Android's larger paging slop.
- Make paper texture movement use the known page-turn direction when available, so inverted renderer coordinate signs at area/section transitions cannot invert the perceived paper movement.
- Make delayed native center-menu dispatch re-read the latest WebView hit type before opening reader chrome, so image and chapter-link hits that become visible after `ACTION_UP` can still suppress the menu.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderRechecksLatestContentHitBeforeDelayedCenterChromeDispatch"
```

Initial result: failed because delayed center chrome used the `ACTION_UP` hit type captured before Android WebView had a chance to update image/link hit testing.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
git diff --check
```

Result:

- The image/link, shell progress, and paper surface host test classes passed.
- The syntax checks and diff whitespace check exited `0`.
- The pure texture-offset harness passed.
- The real EPUB Author's Note boundary harness passed and wrote `tools\reader-harness\output\epub-texture-frontmatter-transition.trace.json`.
- The real EPUB CSS/content-action smoke harness passed and wrote `tools\reader-harness\output\css-smoke.trace.json`.

Phone validation target for eta50:

- Shell cover: dragging horizontally should move from cover to first readable page.
- Normal pages: existing taps and drags should still work.
- Image tint toggles and chapter links should not surface reader chrome.
- Paper texture movement should not invert when crossing from maps/frontmatter into Author's Note.

## Phone Checkpoint: 2026-06-13 eta50 Reader Interaction Validation

Observed eta50 behavior:

- Tapping works on the shell cover.
- Dragging does not work on the shell cover.
- Taps and drags work on normal EPUB pages.
- Interacting with images by tapping center still surfaces the reader chrome, even when the intended content action is only the sepia image-tint toggle.
- Interacting with links in the chapter selection still surfaces the reader chrome.
- The texture transition behaves correctly from the shell cover through the early map/frontmatter pages, then inverts when moving from the maps/frontmatter area into Author's Note.
- After that maps -> Author's Note transition, texture movement remains inverted.

Immediate conclusions:

- The native reader surface is not globally dead: normal-page taps/drags and shell-cover taps work.
- The shell-cover failure is isolated to drag/swipe behavior on the native cover surface.
- The content-action chrome leak still happens despite renderer-side `readerContentTapHandled` posts, so Android bridge delivery/threading/timing must be treated as suspect until ADB logs prove otherwise.
- The texture inversion is not random mid-page noise; it begins at the frontmatter area transition and persists after that transition.

Next phone-testable microdeliverable:

- Keep eta50's working normal-page taps and drags intact.
- Harden Android content-action ownership so image tint toggles and chapter links cannot surface center chrome.
- Keep texture page-turn direction stable through delayed area-transition relocation/scroll events.
- Preserve shell-cover tap behavior while improving shell-cover drag diagnostics and handoff.
- Publish the next APK release after focused host tests pass, because this slice changes packaged Android/WebView reader behavior.

Release publication status:

- Commit: `1fe8b32abe650ec3f8dd09059baf1340c9b565e5`.
- Tag: `v1.0.11-eta50`.
- GitHub Actions run: `27456542836` (`https://github.com/Darkaxt/Navic/actions/runs/27456542836`).
- GitHub release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta50`.
- APK asset: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta50/Navic.apk`.
- Android job: success.
- iOS jobs: skipped by workflow.
- Local download verification: `Get-FileHash releases\v1.0.11-eta50\Navic.apk -Algorithm SHA256` produced `E6C942753E75D8C31250A282B33CFD9092746E0B7B5D75EFE1B7CFBF0ADEB7FA`.

## Harness Checkpoint: 2026-06-13 Phase 1 Gate Reliability

Scope:

- Investigate why the full `phase1-stabilization` gate timed out from the shell after the Author's Note boundary coverage change.
- Identify the slow sub-check instead of treating the outer timeout as a renderer failure.
- Keep `epub-full-traversal` in the Phase 1 gate, but make it observable and bounded.
- Split the full traversal snapshots so the expensive cover-like DOM geometry scan runs for the first visible page only; subsequent pages use lightweight location snapshots.
- Disable renderer animation before each full-traversal `nextPage` dispatch.
- Add explicit per-step `timeoutMs` and elapsed-time logging to the `phase1-stabilization` runner, including a longer timeout for the known 505-page full traversal.

Root-cause evidence:

- Per-step timing showed `epub-full-traversal` was the timeout source.
- The check reached about `201 / 505` pages in a 90-second diagnostic run.
- The timeout was not a blank renderer, crash, or missing fixture; the traversal loop was too slow for a 505-page real EPUB because it performed heavy DOM/image geometry work every page.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessFullTraversalUsesLightweightPerPageSnapshots"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessPhase1RunnerReportsPerStepTimeoutsAndElapsedTime"
```

Initial results:

- `readerHarnessFullTraversalUsesLightweightPerPageSnapshots` failed because the full traversal loop still used the expensive `collectSnapshot()` path for every page.
- `readerHarnessPhase1RunnerReportsPerStepTimeoutsAndElapsedTime` failed because the Phase 1 runner used `spawnSync` without per-step timeout or elapsed reporting.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessPhase1RunnerReportsPerStepTimeoutsAndElapsedTime" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessFullTraversalUsesLightweightPerPageSnapshots"
node --check tools\reader-harness\src\run-reader-harness.mjs
git diff --check
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result:

- Focused host tests passed.
- Harness syntax check exited `0`.
- `git diff --check` exited `0`.
- Full Phase 1 gate passed: `reader harness phase1-stabilization passed: 15 checks`.
- The runner now reports every sub-check with elapsed time. The slowest check remains `epub-full-traversal`, which completed in `179.6s` for the `505` page Hobbit EPUB.

Current conclusion:

- The Phase 1 gate is now laptop-verifiable again with explicit per-step evidence.
- It is not a quick smoke test; it is a full real-EPUB traversal gate and currently takes about five minutes end to end on this machine.
- No APK release is required for this checkpoint because it changes the local validation harness and plan documentation, not packaged reader runtime behavior.

## Verification Refresh: 2026-06-13 Current Branch Stabilization Gates

Scope:

- Re-verify the current `master` branch after eta50 publication and the release-status documentation commit.
- Treat current command output as authoritative rather than relying on older checkpoint notes.
- Keep phone validation explicitly separate from laptop validation.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.BinderyReaderPublicationResolverTest" --tests "paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.ui.components.layouts.MiniPlayerVisibilityPolicyTest" --tests "paige.navic.domain.models.AudioPlaybackOwnershipPolicyTest"
```

Result: `BUILD SUCCESSFUL`.

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result: `reader harness phase1-stabilization passed: 15 checks`.

Observed sub-checks:

- `trace-smoke`
- `epub-frontmatter`
- `epub-page-boundary`
- `epub-shell-cover`
- `epub-external-shell-cover`
- `epub-native-tap-zone-open`
- `css-smoke`
- `texture-offset-logic`
- `epub-texture-scroll`
- `epub-texture-page-turns`
- `epub-texture-frontmatter-transition`
- `epub-full-traversal`
- `pdf-smoke`
- `pdf-fast-sequential-turns`
- `pdf-image-settings`

The full traversal reached `501/505` progress logging and completed in `176.4s`; the full gate completed in `292.9s`.

Phone validation status:

- `adb devices -l` returned no connected devices in this session.
- eta50 remains the current phone-testable release for shell-cover drag, normal-page taps/drags, image/link chrome suppression, and maps/frontmatter -> Author's Note texture movement.

## Tooling Checkpoint: 2026-06-13 eta50 ADB Swipe Validation Support

Scope:

- Close a validation-tooling gap for eta50: the pending phone checks require shell-cover drag and normal-page drag evidence, but `scripts\adb-reader-smoke.ps1` only injected taps.
- Add repeatable swipe injection without changing packaged Android reader behavior.
- Add optional assertions for the eta50 diagnostic signals that must be present when a connected device is used for validation.

Implementation:

- `scripts\adb-reader-smoke.ps1` now accepts `-Swipe` and `-SwipeFraction`.
- Absolute swipe format: `x1,y1,x2,y2` or `x1,y1,x2,y2,durationMs,waitMs`.
- Fractional swipe format: `x1Fraction,y1Fraction,x2Fraction,y2Fraction` or `x1Fraction,y1Fraction,x2Fraction,y2Fraction,durationMs,waitMs`.
- The script now supports `-RequireShellCoverSwipe`, `-RequireContentTapHandled`, and `-RequireTextureDiagnostics`.
- The required diagnostic checks use the same captured log files as `-CaptureReaderDiagnostics`: `reader-touch-diagnostics.log`, `reader-texture-diagnostics.log`, and `reader-diagnostics-summary.txt`.

Example eta50 device command once a phone is connected and Navic is already open in the reader:

```powershell
.\scripts\adb-reader-smoke.ps1 -ExpectedVersionName v1.0.11-eta50 -NoLaunch -CaptureReaderDiagnostics -SwipeFraction "0.80,0.50,0.20,0.50,350,1000" -TapFraction "0.50,0.50,1200" -RequireShellCoverSwipe -RequireContentTapHandled -RequireTextureDiagnostics
```

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics"
```

Initial result: failed because the script had no swipe input support or eta50-required diagnostic assertions.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCapturesFocusedReaderDiagnostics" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics"
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path scripts\adb-reader-smoke.ps1), [ref]$tokens, [ref]$errors)
git diff --check
```

Result:

- Focused ADB smoke host tests passed.
- PowerShell parser found no syntax errors.
- `git diff --check` exited `0`.

Release status:

- No APK release is required for this checkpoint because it changes only the local ADB validation script and host-test coverage.

## Implementation Checkpoint: 2026-06-13 eta51 Candidate Interaction And Texture Hardening

Scope:

- Register the eta50 phone validation results as the current behavior baseline.
- Keep eta50's working normal-page taps and drags intact.
- Make Android `readerContentTapHandled` ownership mark the `ReaderSurfaceHost` from the surface/UI thread instead of directly from the JavaScript bridge callback thread.
- Keep paper texture page-turn direction alive through delayed Foliate relocation/scroll events at area transitions, then clear it only after the committed page texture update.
- Preserve the eta50 ADB swipe-validation script additions so the next phone pass can inject cover/normal-page swipes and assert captured diagnostics.

Root-cause evidence:

- The current Android bridge branch handled `ReaderBridgeEvent.ContentTapHandled` by calling `surfaceHostRef.get()?.markContentTapHandled()` directly from the JavaScript interface callback. Android does not guarantee that callback is on the UI thread, while `markContentTapHandled()` mutates `View`-owned state and cancels pending callbacks.
- `pageTurnDirection` was cleared in the page-turn `finally` block before the delayed `requestAnimationFrame` relocation path could commit texture state. At frontmatter/area boundaries, late scroll/relocation samples could therefore fall back to raw renderer coordinate signs.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderMarksContentHandledOnReaderSurfaceThread"
```

Initial result: failed because the content-handled branch did not post back to the reader surface thread.

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsTextureTurnDirectionUntilCommittedTextureUpdate"
```

Initial result: failed because there was no sticky surface texture turn direction state.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderMarksContentHandledOnReaderSurfaceThread" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderKeepsTextureTurnDirectionUntilCommittedTextureUpdate"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeAssetsTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
git diff --check
```

Result:

- Focused and affected host test groups passed.
- The JavaScript syntax checks exited `0`.
- Texture offset logic passed.
- The real Hobbit Author's Note boundary harness passed and wrote `tools\reader-harness\output\epub-texture-frontmatter-transition.trace.json`.
- The real Hobbit CSS/content-action smoke harness passed and wrote `tools\reader-harness\output\css-smoke.trace.json`.
- The full Phase 1 gate passed all `15` checks. The full EPUB traversal reached `501/505` progress logging and completed in `176.9s`; the whole gate completed in about `290s`.
- `git diff --check` exited `0`.

Phone validation target after release:

- Image tint toggles and chapter links should not surface reader chrome.
- Paper texture movement should not invert when crossing from maps/frontmatter into Author's Note.
- Normal EPUB page taps and drags should remain working.
- Shell-cover tapping should remain working; shell-cover dragging still needs explicit phone validation with the updated ADB swipe tooling.

## Implementation Checkpoint: 2026-06-13 eta52 Candidate Shell-Cover Drag Diagnostics

Scope:

- Continue the native interaction boundary work without pretending laptop tests can prove Android phone drag behavior.
- Preserve eta51 behavior and add stronger evidence for the unresolved shell-cover drag failure.
- Add native shell-cover drag-candidate logging before swipe dispatch so failed cover drags still leave ADB evidence.
- Extend `scripts\adb-reader-smoke.ps1` so the phone run distinguishes `shellCoverDragCandidate` from `shellCoverSwipe`.

Root-cause status:

- Current code already owns shell-cover touch streams before child WebView dispatch and routes successful cover swipes through `readerShellCoverSwipeAction`.
- The remaining reported failure is therefore not proven from the laptop. It may be a device-only state/timing issue, a stream not reaching `ReaderSurfaceHost`, a movement-threshold issue, or a command/visibility issue after dispatch.
- eta52 intentionally improves observability first: a phone log can now show whether native received cover movement at all, whether that movement crossed the shell-cover swipe decision, and whether a shell-cover page command was dispatched.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverLogsDragCandidatesBeforeSwipeDispatch" --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics"
```

Initial result:

- Failed because `ReaderWebViewHost.android.kt` had no shell-cover drag-candidate diagnostic state/log.
- Failed because `scripts\adb-reader-smoke.ps1` had no `-RequireShellCoverDragDiagnostic` flag or `shellCoverDragCandidate=` summary field.

Implementation:

- `ReaderSurfaceHost` now resets `shellCoverDragDiagnosticLogged` on `ACTION_DOWN`.
- On shell-cover `ACTION_MOVE`, it logs one `Reader shell cover drag candidate` line once movement exceeds touch slop, before attempting swipe dispatch.
- The log includes the candidate action, delta, and threshold.
- ADB smoke diagnostics now capture that line, write `shellCoverDragCandidate=...`, and can require it with `-RequireShellCoverDragDiagnostic`.

Phone validation target after release:

```powershell
.\scripts\adb-reader-smoke.ps1 -ExpectedVersionName v1.0.11-eta52 -NoLaunch -CaptureReaderDiagnostics -SwipeFraction "0.80,0.50,0.20,0.50,350,1200" -RequireShellCoverDragDiagnostic -RequireShellCoverSwipe
```

Interpretation:

- If `shellCoverDragCandidate=False`, the native surface is not receiving the drag stream on the shell cover.
- If `shellCoverDragCandidate=True` and `shellCoverSwipe=False`, the stream reaches native code but fails the swipe-decision threshold/direction filter.
- If both are `True` but the cover does not dismiss, the failure is in command dispatch or cover visibility state after dispatch.

## Phone Validation: 2026-06-13 eta52 Reader Behavior Baseline

Observed on device:

- Cover tapping works.
- Cover dragging does not work.
- Normal EPUB page tapping works.
- Normal EPUB page dragging works.
- Center-tapping interactive images toggles the image state but also surfaces reader chrome.
- Tapping chapter-selection/frontmatter links also surfaces reader chrome.
- The paper texture transition still inverts when moving from the maps into the Author's Note and stays inverted after that area transition.

Current diagnosis constraints:

- Do not treat shell-cover tapping success as proof that cover dragging is fixed.
- Do not treat normal EPUB page drag success as proof that the shell-cover drag path is fixed; the cover path is a separate native shell-cover surface state.
- Do not declare the content-interaction fix complete until image taps and chapter/frontmatter links claim content ownership early enough to prevent the native center-menu action.
- Do not declare the texture fix complete until the maps -> Author's Note boundary is validated on the real device and the renderer trace shows stable forward direction across that area transition.

Current implementation priority:

1. Fix interactive content center-tap ownership so image interactions and chapter/frontmatter links do not surface reader chrome.
2. Fix the paper-texture sign inversion across the maps -> Author's Note area transition.
3. Re-check shell-cover drag with ADB diagnostics; use `shellCoverDragCandidate` and `shellCoverSwipe` to localize whether the failure is stream capture, threshold/direction filtering, or command/cover-state dispatch.
4. Keep normal page taps/drags and cover taps green while making the fixes above.

## Implementation Checkpoint: 2026-06-13 eta53 Candidate Content Ownership And Drag Texture Direction

Scope:

- Address the eta52 phone baseline where image taps and chapter/frontmatter links still surfaced reader chrome.
- Address the texture inversion hypothesis that real finger drags can let Foliate move pages without Navic seeding `pageTurnDirection`, so texture motion falls back to raw renderer coordinate sign at the maps -> Author's Note boundary.
- Keep shell-cover drag as an explicitly pending phone-validation item; eta53 does not claim that cover dragging is fixed.

Implementation:

- Link/navigation documents now claim `readerContentTapHandled` on `pointerdown` and `mousedown`, in addition to the existing touch and click phases.
- Sepia image gestures now claim interactive content ownership during `touchstart`, before waiting for `touchend` or synthetic click.
- Added `readerPaperTextureDragDirection` to map physical finger movement to `next` / `previous` texture direction.
- EPUB content documents now install a passive texture drag-direction tracker. Horizontal left drags seed `next`, horizontal right drags seed `previous`, and vertical paged mode uses up/down movement.
- The drag tracker writes the same sticky `surfacePaperTextureTurnDirection` used by explicit Navic page-turn commands, so frontmatter/page-boundary renderer coordinate sign changes cannot invert texture motion during a real drag.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderClaimsInteractiveContentOnPointerAndMouseDownBeforeNativeCenterChrome" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderClaimsSepiaImageTouchAtGestureStart"
node tools\reader-harness\src\run-reader-harness.mjs --mode texture-offset-logic
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.androidReaderSeedsTextureTurnDirectionFromReaderDocumentDrags"
```

Initial result:

- The image/link tests failed because link documents did not claim pointer/mouse-down ownership and sepia image touchstart did not claim ownership before gesture bookkeeping.
- The texture logic check failed because `readerPaperTextureDragDirection` was not exported.
- The paper-surface wiring test failed because no reader document drag tracker was attached.

Fresh validation evidence:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check tools\reader-harness\src\run-reader-harness.mjs
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode phase1-stabilization --epub-fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub" --pdf-fixture "D:\Downloads\Trash\movements-2032026.pdf"
```

Result:

- JavaScript syntax checks exited `0`.
- `ReaderRuntimeImageLinkTest` and `ReaderRuntimePaperSurfaceTest` passed.
- CSS smoke passed and still verifies image/link bridge ownership.
- Real Hobbit frontmatter texture transition passed.
- Full Phase 1 reader harness passed all `15` checks, including the `505` page full EPUB traversal and PDF smoke/fast-turn checks.

Phone validation target after eta53 release:

- Center-tapping an image should toggle the image sepia state without surfacing reader chrome.
- Tapping chapter-selection/frontmatter links should navigate without surfacing reader chrome.
- Dragging from maps/frontmatter into Author's Note should not invert the texture movement or leave later pages inverted.
- Normal EPUB taps/drags and cover taps should remain working.
- Cover dragging remains pending and should be diagnosed with eta52/eta53 ADB `shellCoverDragCandidate` / `shellCoverSwipe` output before changing that path further.

## Phone Validation: 2026-06-13 eta53 Reader Behavior Baseline

Observed on device after installing eta53:

- Cover tapping works.
- Cover dragging still does not work.
- Normal EPUB page tapping works.
- Normal EPUB page dragging works.
- Center-tapping interactive images toggles image state, but still surfaces reader chrome.
- Tapping chapter-selection/frontmatter links also still surfaces reader chrome.
- The paper texture transition still inverts when moving from the maps into the Author's Note and stays inverted after that area transition.

Current diagnosis constraints:

- eta53 did not close the content-action chrome leak. The next fix must prove that native center-menu dispatch is suppressed after actual image/link interactions, not only after synthetic document-level handlers.
- eta53 did not close the maps -> Author's Note texture inversion. The next fix must prove the visible page turn direction remains stable through the real frontmatter area transition and does not depend on transient Foliate relocation signs.
- Cover drag remains a separate native shell-cover issue. Cover tapping success is not evidence that shell-cover drag works.
- Normal page taps/drags are working and must remain green.

Current implementation priority:

1. Add stronger laptop-testable coverage that simulates Android native center-tap timing around real image and link interactions.
2. Add stronger texture-transition coverage for the real maps -> Author's Note path that detects persistent direction inversion after the boundary.
3. Fix only the root causes proven by those tests.
4. Release only after the next build contains a phone-evaluable behavior change, then validate against this eta53 baseline.

## Implementation Checkpoint: 2026-06-13 eta54 Candidate Runtime Content Hit-Test And Drag Boundary Probe

Scope:

- Address the eta53 phone result where image taps and chapter/frontmatter links still surfaced reader chrome.
- Strengthen local texture validation so the maps -> Author's Note boundary is exercised with a real touch-drag page turn, not only a bridge `nextPage` command.
- Keep shell-cover drag as a separate pending phone issue. eta54 does not claim cover dragging is fixed.

Root-cause update:

- eta53 relied on `readerContentTapHandled` bridge posts from content document handlers. That proves content JavaScript ran, but it does not prove Android's delayed center-menu path will suppress itself when `WebView.hitTestResult` is `UNKNOWN` or stale.
- The native center-menu path now needs a direct coordinate hit-test into the current Foliate documents before opening chrome.
- eta53's frontmatter texture harness used bridge-driven page turns, while the phone regression happens during finger dragging. The harness now includes a named `drag-author-note-boundary` probe driven through browser touch events.

Implementation:

- `NavicReaderBridge.readerContentActionAtPoint(x, y)` now asks the reader runtime to map root/WebView coordinates into loaded Foliate content documents and classify links, media/images, and form controls.
- Android `ReaderSurfaceHost.scheduleReaderCenterTap()` now queries that runtime hit-test with `evaluateJavascript()` before dispatching reader chrome.
- Asynchronous center-tap callbacks are guarded by `centerTapSequence`, so canceled/stale center taps cannot open chrome later.
- CSS smoke now checks `imageNativeCenterContentHit`, `textLinkNativeCenterContentHit`, and `paragraphNativeCenterContentHit` so it verifies Android-style center suppression, not just bridge message counts.
- The frontmatter transition harness now performs a real touch drag across the Author's Note boundary and requires a `texture:drag-direction` trace.

Focused validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderUsesCoordinateContentHitTestBeforeDelayedCenterChrome" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerHarnessTextureFrontmatterTransitionIncludesRealDragProbe"
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Result:

- Focused host tests passed after the initial RED failures.
- CSS smoke passed and verifies native-center image/link suppression with ordinary paragraph text left unsuppressed.
- Real Hobbit frontmatter transition passed with `drag-author-note-boundary`.
- Trace summary: drag probe settled from page `3` to `4`, emitted repeated `texture:drag-direction` events with `direction=next`, and texture scroll samples stayed counter-moving through the Author's Note boundary.

Phone validation target after eta54 release:

- Center-tapping an image should toggle the image sepia state without surfacing reader chrome.
- Tapping chapter-selection/frontmatter links should navigate without surfacing reader chrome.
- Dragging from maps/frontmatter into Author's Note should not invert texture movement or leave later pages inverted.
- Normal EPUB taps/drags and cover taps should remain working.
- Cover dragging remains pending and should still be diagnosed separately with `shellCoverDragCandidate` / `shellCoverSwipe`.

## Implementation Checkpoint: 2026-06-13 eta55 Candidate Shell-Cover Swipe Tolerance

Scope:

- Continue the native shell-cover interaction work after eta54 without touching normal EPUB/PDF drag ownership.
- Make shell-cover swipe recognition less brittle for real finger movement over the cover image.
- Keep phone validation explicit: eta55 should be tested on the cover with a leftward drag, but this checkpoint does not claim cover drag is proven until the APK is installed and observed.

Root-cause update:

- No eta52/eta53 ADB artifact with `Reader shell cover drag candidate` deltas was available in the repo, so stream receipt versus threshold rejection is still not proven from device logs.
- The code-level gap is concrete: `readerShellCoverSwipeAction()` required horizontal movement to be greater than vertical movement. That is too strict for the shell-cover-only path because there is no readable WebView scroll stream to protect while the shell cover is visible.
- Normal readable-page drags remain separated: `ReaderSurfaceHost.dispatchTouchEvent()` still lets child WebView/Foliate receive the stream when `shellCoverVisible` is false.

Implementation:

- `readerShellCoverSwipeAction()` now treats horizontal movement past tap slop as the shell-cover swipe intent, even when the finger path has natural vertical drift.
- The existing direction mapping remains unchanged: leftward drag maps to the right/next action in LTR/default direction, and rightward drag maps to left/previous.

TDD evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSwipeUsesTapSlopSizedHorizontalDragContract"
```

Initial result:

- Failed after the test was updated to require `readerShellCoverSwipeAction(deltaX = -11f, deltaY = 13f, thresholdPx = 10f)` to return `ReaderTapZoneAction.Right`.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest.nativeShellCoverSwipeUsesTapSlopSizedHorizontalDragContract"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderChromeStateTest"
git diff --check
```

Result:

- The focused shell-cover swipe contract passed after the minimal change.
- The affected reader shell/tap-zone host tests passed.
- `git diff --check` exited `0`.

Phone validation target after eta55 release:

- Shell cover leftward drags with natural vertical drift should dismiss the cover / advance to the first readable page.
- Shell cover taps should remain working.
- Normal EPUB page taps and drags should remain working.
- Image taps and chapter/frontmatter links should still be checked against eta54 behavior: they should not surface reader chrome.
- Texture movement across maps/frontmatter into Author's Note should still be checked against eta54 behavior.

## Phone Validation: 2026-06-13 eta55 Reader Behavior Baseline

Observed on device after installing eta55:

- Cover tapping works.
- Cover dragging still does not work.
- Normal EPUB page tapping works.
- Normal EPUB page dragging works.
- Center-tapping interactive images toggles image state, but still surfaces reader chrome.
- Tapping chapter-selection/frontmatter links also still surfaces reader chrome.
- The paper texture transition still inverts when moving from the maps/frontmatter area into the Author's Note and stays inverted after that transition.

Current diagnosis constraints:

- Do not conflate normal readable-page drags with shell-cover drags; the readable-page path works and must remain untouched unless evidence shows it is involved.
- Do not claim the native top-level touch layer is proven until cover dragging produces `Reader shell cover drag candidate`, `Reader shell cover swipe`, and `Reader shell cover command` diagnostics on device.
- Do not delay image/link chrome suppression until after link navigation or image state mutation has already changed the document under the native coordinate query.
- Do not treat the current texture harness as sufficient unless it proves the post-boundary direction remains correct after the maps/frontmatter -> Author's Note transition.

Current implementation priority:

1. Strengthen ADB smoke validation so shell-cover drag diagnosis requires command dispatch, not only drag/swipe recognition.
2. Query runtime content hit ownership at native center-tap schedule time, before delayed chrome dispatch and before link/image interactions can mutate the visible document.
3. Tighten the texture transition harness around the real Author's Note boundary so it can detect persistent post-boundary inversion, then fix the proven sign/direction issue without touching working normal page taps/drags.
4. Release only after a phone-evaluable behavior change is committed and host/harness validation passes.

## Implementation Checkpoint: 2026-06-13 eta56 Candidate Immediate Content Hit-Test And ADB Gates

Scope:

- Preserve the eta55 shell-cover swipe tolerance while improving diagnosis for the unresolved cover-drag failure.
- Address the eta55 phone result where image taps and chapter/frontmatter links still toggled reader chrome.
- Do not claim the texture inversion is fixed in this slice; the current local frontmatter harness still passes and therefore does not reproduce the persistent phone-side inversion yet.

Root-cause update:

- The native center-menu fallback queried `readerContentActionAtPoint()` only inside the delayed center-tap runnable.
- For links and image interactions, that is too late: content JavaScript can navigate or mutate the visible document before the delayed native coordinate query runs.
- The query now starts immediately when the native center tap is scheduled, while the delayed fallback query remains in place.
- The ADB smoke script can now require all shell-cover drag stages: drag candidate, swipe recognition, and command dispatch.
- The ADB smoke script can also require that a content interaction does not dispatch native center chrome.

TDD and validation evidence:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderQueriesRuntimeContentHitBeforeDelayedCenterChromeCanMutateDocument"
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeAssetsTest.adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
```

Result:

- The focused diagnostic test initially failed until `-RequireShellCoverCommand`, `shellCoverCommand=`, and `-RequireNoReaderCenterDispatch` were added.
- The focused content-hit test initially failed until `ReaderSurfaceHost.scheduleReaderCenterTap()` started the runtime coordinate hit-test before delayed chrome dispatch.
- The affected host tests passed after clearing the known Kotlin host-test incremental cache.
- The real Hobbit frontmatter texture harness still passed, which means it is not yet sufficient evidence for the phone-side persistent texture inversion.

Phone validation target after eta56 release:

- Center-tapping an image should toggle the image sepia state without surfacing reader chrome.
- Tapping chapter-selection/frontmatter links should navigate without surfacing reader chrome.
- Normal EPUB page taps and drags should remain working.
- Cover taps should remain working.
- Cover drag should be diagnosed with `-RequireShellCoverDragDiagnostic -RequireShellCoverSwipe -RequireShellCoverCommand`.
- Image/link chrome suppression should be diagnosed with `-RequireContentTapHandled -RequireNoReaderCenterDispatch`.
- Texture movement across maps/frontmatter into Author's Note remains an open phone validation item; if still inverted, capture texture diagnostics because the current local harness is not reproducing that persistent state.

## Phone Validation: 2026-06-13 eta56 Reader Behavior Baseline

Observed on device after installing eta56:

- Cover tapping works.
- Cover dragging still does not work.
- Normal EPUB page tapping works.
- Normal EPUB page dragging works.
- Center-tapping interactive images toggles the image state, but still surfaces reader chrome.
- Tapping links in the chapter-selection/frontmatter area also surfaces reader chrome.
- The paper texture transition still inverts when moving from the maps/frontmatter area into the Author's Note and remains inverted afterward.

Current diagnosis constraints:

- The working normal-page tap and drag path must remain untouched unless a failing test proves it is involved.
- The shell-cover drag failure is isolated from normal readable-page dragging; cover tap success is not evidence that cover drag works.
- Content interactions must not surface reader chrome. The acceptance behavior is content action only: image center taps toggle sepia state, and chapter/frontmatter links navigate, without paired menu/chrome dispatch.
- The texture inversion begins at the maps/frontmatter -> Author's Note area transition and persists after the transition. Local harnesses that only pass shallow page turns are not sufficient evidence.

Current implementation priority:

1. Strengthen laptop-testable coverage for the real frontmatter texture boundary until it can detect persistent post-boundary inversion, then fix only the proven texture direction/state bug.
2. Strengthen content-interaction chrome suppression so image/link ownership wins before native center-menu dispatch for the actual Android timing path.
3. Re-check shell-cover drag using ADB diagnostics that prove drag candidate, swipe recognition, and command dispatch; do not mix this with normal readable-page drag behavior.
4. Release only after the next APK contains a phone-evaluable behavior change and host/harness validation is green.

## Implementation Checkpoint: 2026-06-13 eta57 Candidate Native Coordinate Content Ownership

Root cause hypothesis for the eta56 image/link chrome leak:

- Android delivers touch coordinates in native WebView view pixels.
- The reader runtime content hit-test uses `elementFromPoint()`, which expects CSS viewport coordinates.
- The previous local CSS smoke harness only checked CSS-coordinate taps, so it could pass while a high-density Android WebView still missed image/link ownership and allowed delayed center-menu dispatch.

Scope of this slice:

- Pass native WebView width and height into `readerContentActionAtPoint()`.
- Normalize native touch coordinates into CSS viewport coordinates before hit-testing EPUB iframe content.
- Add harness checks for scaled native image/link taps and paragraph non-content taps.
- Strengthen the real frontmatter texture harness with a second touch-drag probe after the Author's Note boundary, but keep texture production code unchanged because local Chromium still does not reproduce the phone-side persistent inversion.

Verification evidence:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
node --check tools\reader-harness\src\run-reader-harness.mjs
node --check tools\reader-harness\src\reader-trace-assertions.mjs
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-texture-frontmatter-transition --fixture "D:\Downloads\Trash\01 - The Hobbit The Hobbit (illustrated Edition by Alan Lee).epub"
.\gradlew.bat --no-daemon --rerun-tasks :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.readerHarnessCssSmokeRequiresContentActionBridgeOwnership" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderUsesCoordinateContentHitTestBeforeDelayedCenterChrome" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidReaderQueriesRuntimeContentHitBeforeDelayedCenterChromeCanMutateDocument"
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
git diff --check
```

Phone validation target after eta57 release:

- Center-tapping interactive images should toggle sepia/image state without surfacing reader chrome.
- Tapping chapter/frontmatter links should navigate without surfacing reader chrome.
- Normal EPUB page taps and drags should remain working.
- Cover taps should remain working.
- Cover drag remains open unless separately proven by ADB diagnostics.
- Texture inversion across maps/frontmatter -> Author's Note remains open unless separately proven on device.

## Phone Validation: 2026-06-13 eta57 Reader Behavior Baseline

Observed on device after installing eta57:

- Image sepia state toggling works once the image is interacted with, although the initial image load can appear without the expected sepia filter.
- Interacting with images by tapping center still surfaces reader chrome; this is incorrect because content-owned actions should not open the menu bar.
- Tapping links in the chapter-selection/frontmatter area still surfaces reader chrome; this is incorrect because link navigation should not open the menu bar.
- Cover tapping works.
- Cover dragging still does not work.
- Normal EPUB page taps work.
- Normal EPUB page drags work.
- The paper texture transition still inverts when moving from the maps/frontmatter area into the Author's Note and stays inverted afterward.

Current diagnosis constraints:

- The working normal readable-page tap and drag paths must remain green.
- Cover dragging is still isolated to the shell-cover surface path; do not change normal readable-page dragging to fix it.
- Content interactions must be treated as content-owned at the native reader surface before center chrome can dispatch. A visible content action, such as image sepia toggle or link navigation, must suppress the paired menu/chrome action.
- The texture inversion is tied to the maps/frontmatter -> Author's Note area transition and persists after the boundary. A local harness pass is not sufficient unless the harness proves structured `texture:scroll` payload direction through and after that exact boundary.
- ADB was not attached during the next local implementation pass, so any no-device slice must be limited to harness/host-test hardening or a production change backed by local evidence. Phone behavior remains unproven until a connected-device run.

Current implementation priority:

1. Make the laptop texture harness fail on structured `texture:scroll` payload direction if the Author's Note transition produces persistent inverted movement.
2. Reproduce or instrument the native content-interaction chrome leak with timing/coordinate evidence; do not ship another content suppression guess without a failing test.
3. Re-check shell-cover drag with ADB diagnostics once a device is attached.
4. Release only after the APK contains a phone-evaluable behavior change, not for harness-only hardening.

## Implementation Checkpoint: 2026-06-13 eta58 Candidate Recent Content Touch Ownership

Scope:

- Keep eta57's working normal EPUB page taps/drags and cover taps untouched.
- Add a local runtime memory of the most recent content-owned touch for image/media/link targets.
- Query that remembered point before Android's delayed native center-menu hit test falls back to the current DOM tree.
- Model the Android race where an image or frontmatter link touch mutates/removes its DOM node before the native center-chrome check runs.
- Tighten the Author's Note texture harness so structured `texture:scroll` payload direction is checked, even though the phone-side texture inversion remains unproven locally.

Verification performed before release candidate:

- The focused Android host test initially failed because the runtime had no `rememberReaderContentActionTouch` / `recentReaderContentActionAtRootPoint` path.
- After the implementation, the focused Android host test passed.
- The real Hobbit EPUB CSS/content-action smoke harness passed with recent image/link ownership after DOM removal.
- The real Hobbit EPUB frontmatter texture harness passed with structured texture payload direction checks.
- The affected Android host test classes passed.
- `git diff --check` passed.
- The full `phase1-stabilization` gate timed out after 244 seconds during this pass, so it is not counted as passing or failing for eta58.
- ADB was not attached during the local pass; phone behavior is still pending.

Phone validation target after eta58 release:

- Image center taps should toggle sepia/image state without surfacing the reader chrome.
- Chapter/frontmatter link taps should navigate without surfacing the reader chrome.
- Normal EPUB page taps and drags should still work.
- Cover taps should still work.
- Cover drag remains an open shell-cover issue unless separately proven by ADB diagnostics.
- Texture inversion across maps/frontmatter -> Author's Note remains open unless the phone test proves the structured payload guard changed the behavior.

Release result:

- Commit: `d2600df0 Improve reader content touch ownership`.
- Tag: `v1.0.11-eta58`.
- GitHub release: `https://github.com/Darkaxt/Navic/releases/tag/v1.0.11-eta58`.
- APK asset: `https://github.com/Darkaxt/Navic/releases/download/v1.0.11-eta58/Navic.apk`.
- GitHub Actions run: `https://github.com/Darkaxt/Navic/actions/runs/27465074286`.
- Android release build, release APK signing verification, artifact upload, and GitHub release creation completed successfully.
- iOS IPA build and attach jobs were skipped for this eta tag.
- GitHub asset digest and local downloaded APK hash both matched SHA256 `E411A361394217E0C01270A7E7CEE28CC98D044F4447C76180DAF16BDC4ECC71`.

## Phone Validation: 2026-06-13 eta58 ADB Diagnosis

Observed on device with `v1.0.11-eta58`, `versionCode=391`:

- Package state: `lastUpdateTime=2026-06-13 14:21:07`.
- Focused activity: `darkaxt.navic/paige.navic.androidApp.MainActivity`.
- A shell-cover drag smoke run against the visible cover failed with no native cover drag stream:

```text
readerSurfaceTouchDown=True
readerSurfaceTapAction=False
shellCoverDragCandidate=False
shellCoverSwipe=False
shellCoverCommand=False
```

- Center tap on the visible cover logged `Reader surface tap action=Menu ... hitType=5 shellCover=false`.
- Right-edge tap on the visible cover logged `Reader surface tap action=Right command=nextPage ... hitType=5 shellCover=false`, followed by WebView runtime logs:

```text
page-turn:shell-cover-hide next
shell-cover:hide animated
```

Conclusion: the visible cover in eta58 was still the WebView/JS shell-cover fallback, not the native shell-cover surface. Cover dragging cannot work in this state because `ReaderSurfaceHost` only owns shell-cover drag streams while `shellCoverVisible=true`, and ADB showed `shellCover=false`.

Additional device log from reopening the book:

```text
Reader publication prepared ... cache=hit cacheKey=reader-99c6f0ddfe443b040249e42a fileBytes=24480455
openPublication(url=https:publication.epub, overlay=false)
shell-cover:loaded image/jpeg 81694
shell-cover:show animated
```

That proves the EPUB contains a cover that Foliate/WebView can load, but eta58 did not expose whether native cover extraction returned a shell-cover URL. The next slice must log `shellCover=present/missing/unavailable` at the resolver boundary and preserve an already resolved native shell-cover URL when a later runtime-preparation callback has no cover.

Last-page / transition texture issue also reproduced in ADB trace:

```text
surface-texture-scroll scroll x=697 y=0 pos=0 base=697 delta=-697 dir=previous page=5/357 href=OEBPS/Text/Hobbit_chap-1.html
surface-texture-scroll scroll x=698 y=0 pos=1395 base=697 delta=698 dir=previous page=5/357 href=OEBPS/Text/Hobbit_author-1.html
```

The same previous-page action crossed `Hobbit_chap-1.html` and `Hobbit_author-1.html` while keeping the same display page. This remains an open texture-state bug; it must not be described as fixed until a harness or ADB validation catches and proves the transition behavior.

## Implementation Checkpoint: 2026-06-13 eta59 Candidate Native Shell-Cover State Preservation

Scope:

- Preserve `nativeShellCoverUrl` in `ReaderScreen` when a later publication-prepared callback has no cover URL.
- Route readaloud-only publication preparation through a separate handler so it does not clear native EPUB cover state.
- Add resolver-boundary logging for `shellCover=present`, `shellCover=missing`, and `shellCover=unavailable` so the next phone run can separate native extractor failure from common-state overwrite.
- Do not change tap-zone math, texture production behavior, or normal EPUB/PDF drag ownership in this slice.

RED tests added:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.commonReaderDoesNotLetSecondaryPublicationPreparationClearNativeShellCover" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidPublicationRuntimeLogsNativeShellCoverResolution"
```

Initial result:

- `commonReaderDoesNotLetSecondaryPublicationPreparationClearNativeShellCover` failed because `handlePublicationPrepared()` always assigned `nativeShellCoverUrl = shellCoverUrl`.
- `androidPublicationRuntimeLogsNativeShellCoverResolution` failed because eta58 did not log native shell-cover resolution state.
- `extractsEpubCoverImageWhenOpfParserRejectsDoctype` failed because native cover extraction returned `null` when hardened XML parsing rejected the OPF before reading `meta name="cover"`.

Implementation:

- `ReaderScreen` now preserves an existing native shell-cover URL when a later preparation callback has no cover URL.
- Readaloud-only preparation updates the prepared publication without passing a literal null shell-cover URL through the common EPUB preparation path.
- `ReaderPublicationRuntimeHost` logs `shellCover=present`, `shellCover=missing`, or `shellCover=unavailable`.
- `ReaderPublicationResource` keeps the structured XML cover extraction path, then falls back to a constrained OPF text scan for manifest image items and `meta name="cover"` when hardened XML parsing fails.

Fresh validation evidence:

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.commonReaderDoesNotLetSecondaryPublicationPreparationClearNativeShellCover" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest.androidPublicationRuntimeLogsNativeShellCoverResolution"
.\gradlew.bat --no-daemon "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.BinderyReaderPublicationResolverTest.extractsEpubCoverImageWhenOpfParserRejectsDoctype"
.\gradlew.bat --no-daemon "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.BinderyReaderPublicationResolverTest" --tests "paige.navic.reader.ReaderRuntimeImageLinkTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-eta59
git diff --check
```

Result:

- Focused shell-cover state/logging tests passed after implementation.
- Focused OPF fallback cover extraction test passed after implementation.
- Affected resolver, image/link, and shell-progress host test classes passed.
- Android versionName matches `v1.0.11-eta59`.
- `git diff --check` exited `0`.

Phone validation target after eta59 release:

- Reopen the same EPUB and check `ReaderPublicationRuntime` for `shellCover=present` or `shellCover=missing`.
- If `shellCover=present`, the first visible cover should be the native shell-cover surface and ADB touch logs should show `shellCover=true`.
- Run shell-cover drag smoke with `-RequireShellCoverDragDiagnostic -RequireShellCoverSwipe -RequireShellCoverCommand`.
- If `shellCover=missing` still appears while WebView logs `shell-cover:loaded`, the remaining root cause is native extraction coverage for that EPUB structure, not tap-zone thresholds.
- Texture inversion around `Hobbit_chap-1.html` / `Hobbit_author-1.html` remains open and must be validated separately.
