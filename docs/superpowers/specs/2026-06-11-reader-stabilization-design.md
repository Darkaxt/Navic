# Reader Stabilization Design

Date: 2026-06-11

Status: approved design anchor. Implementation starts with WebView renderer stabilization only.

## Objective

Stabilize Navic's ebook reader by making the WebView-rendered reader testable from the laptop before further APK behavior is changed.

The first priority is fixing EPUB/WebView pagination defects: unstable page numbers, area-transition jumps, cover rendering/suppression, texture transitions, hyperlink handling, and renderer CSS behavior. Native APK refactoring is happening in another thread and must not be blocked by this work.

After WebView pagination is stable, this thread can move into APK integration work: native touch controls above the WebView, shell cover behavior, and continued reader upgrades from Anx Reader, Komikku, Readest, Colibrio, and LibreraReader references.

The page-curl mockup work, drag-to-turn animation, dual-page/spread animation, and rotation-triggered spread mode are explicitly lowest priority. They must not displace reader correctness, native tap ownership, shell-cover behavior, PDF navigation, cache/progress, Storyteller/readaloud support, or core settings work.

## Non-Negotiable Direction

- Do not debug renderer behavior primarily by repeatedly deploying phone builds.
- Do not keep inventing WebView tap handling when Komikku already demonstrates native viewer-owned tap zones.
- Do not render the EPUB cover in the WebView when Navic has a shell cover surface.
- Do not treat Foliate's raw relocation events as final page state during spine/area transitions.
- Do not batch unrelated reader improvements into opaque releases.

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
- Dual-page/spread animation is also deferred. Rotation-triggered spread mode should not be implemented during stabilization unless all higher-priority reader issues are already closed.
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
