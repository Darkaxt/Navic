# Readaloud Reader Core Implementation Plan

> **For agentic workers:** This is the required project direction for Navic's ebook/readaloud reader work. Do not replace it with a Storyteller-client clone or a generic audiobook sidecar. Implement task-by-task and update checkbox status as work lands.

**Date:** 2026-06-08

**Status:** required implementation plan and context anchor.

**Goal:** Build a proper ebook reader experience in Navic, with Storyteller-generated readaloud EPUB support injected into the reader and playback runtime.

**Replacement objective:** Build Navic into a proper premium ebook and synced audiobook reader, using the strongest reusable pieces from Readest, Anx Reader, Colibrio, LibreraReader, and Komikku instead of centering the architecture on any single upstream repo. The reader must support Storyteller-generated ebooks as first-class input, including merged ebook/audiobook packages with audio metadata labels, chapter/position mapping, media overlays, and synced read-aloud playback. Bindery remains the private backend integration layer, while the public Navic fork contains the open reader/client implementation.

**Non-negotiable direction:** The reader UX must be closer to Readest/Anx Reader than to the Storyteller client. Storyteller is the artifact compatibility target for generated readaloud EPUBs and Media Overlay metadata, not the UX baseline.

**Architecture:** Use a foliate-js based reader runtime for the polished ebook surface, a Navic Compose host for native UI, and Android Media3 for audio playback. EPUB Media Overlay and Storyteller clip metadata bridge the reader and audio controller.

**Tech stack:** Kotlin Multiplatform, Compose Multiplatform, Android WebView, foliate-js, Media3, Bindery OPDS 2, Storyteller EPUB 3 Media Overlay artifacts.

---

## Delivery Protocol - Microdeliverables

Reader work must land as small, reviewable checkpoints rather than a long private implementation pass. Each checkpoint should be something the user can evaluate, redirect, or reject before the next layer is built on top of it.

Required cadence:

1. Define the microdeliverable before editing: user-visible behavior, files likely touched, and the exact verification command or device smoke test.
2. Prefer one focused change per checkpoint: parser/model, bridge command, runtime rendering, native UI control, persisted setting, or smoke-test/logging hook.
3. Use upstream projects as implementation references before inventing renderer behavior. For reader rendering/layout issues, inspect Anx Reader/Readest/Foliate usage first; only write Navic-specific fixes after identifying why the upstream pattern does not directly apply.
4. Stop after each microdeliverable with a concise checkpoint report: changed files, test/build result, what the user can try, and the next proposed slice.
5. Do not batch unrelated reader settings into one opaque update. Group controls by evaluateable surfaces: navigation, typography, appearance, readaloud, PDF/image, logs/debugging.
6. If a microdeliverable changes behavior on device, produce a debug APK or adb validation target before moving to the next feature.
7. Keep commits aligned with microdeliverables when asked to commit/push. Avoid giant mixed commits that combine rendering fixes, settings UI, parser changes, and release mechanics.

Default reader microdeliverable size:

- 1-3 production files plus matching tests, or
- one UI panel/control group with persisted settings and tests, or
- one runtime bridge behavior with JavaScript syntax validation and Android test coverage, or
- one signed/debug phone smoke fix with adb evidence and logs.

The user must be able to redirect focus at every checkpoint.

---

## Required Core Model

Navic must support these book variants as first-class concepts:

- `ebook`: a readable EPUB/PDF-like resource without embedded synced audio.
- `audiobook`: a long-form audio publication with chapters/tracks and metadata.
- `readaloud`: a Storyteller-style EPUB 3 Media Overlay publication containing text, embedded audio, SMIL clips, and sync metadata.
- `ebook+audiobook`: separate resources that may later be linked by imported alignment metadata.

Do not model readaloud as "just an ebook" or "just an audiobook". It is both a reader session and an audio session, tied by clip mappings.

## Required Metadata Labels

The implementation must preserve structured audio labels and avoid flattening nested OPDS properties into string-only maps.

Required audio/resource fields:

- `resourceKey`
- `href`
- `title`
- `chapterLabel`
- `sectionLabel`
- `trackNumber`
- `discNumber`
- `narrator`
- `author`
- `durationMs`
- `codec`
- `bitrateKbps`
- `sampleRateHz`
- `channels`
- `qualityLabel`
- `sourceProvider`
- `sourceRelease`

Required Storyteller/Media Overlay clip fields:

- `audioResource`
- `textResource`
- `fragmentId`
- `startSeconds`
- `endSeconds`
- `label`

These labels must be available to UI, playback metadata, progress sync, and tests.

---

## Current Support Audit - 2026-06-10

Reference snapshots inspected:

- Anx Reader: `Anxcye/anx-reader` at `107f4fa`
- Komikku: `komikku-app/komikku` at `582ea3e`

The checked tasks below mean first-pass Navic implementation exists. They do not mean Navic has Anx/Readest-class reader parity yet.

### Anx Reader Parity

| Capability | Navic status | Required direction |
| --- | --- | --- |
| Foliate-style EPUB runtime | Supported | Keep this as the EPUB/readaloud reader surface. Do not replace it with the Storyteller client. |
| Bindery EPUB/readaloud routing | Supported | Keep ebook/readaloud routing separate from audiobook-only playback. |
| PDF.js-backed PDF opening | Supported in `v1.0.11-zeta6`, phone smoke pending | PDF.js assets are packaged through foliate-js. `v1.0.11-zeta6` adds tap-turn coalescing, adjacent-page prefetch, natural PDF layout dimensions, and centered fit-width gutters; run signed-release adb smoke when a device is connected. |
| TOC navigation | Supported | Hide reader chrome after TOC navigation so content stays immersive. |
| Search and result navigation | Supported | Keep as core reader chrome, not the global app search bar. |
| CFI/location progress and resume | Supported | Continue syncing Bindery progress by locator/CFI where available. |
| Bookmarks | Supported | Keep CFI-backed and hide chrome after navigation. |
| Annotations/highlights | Supported | Keep CFI-backed, and add export/share later only after renderer stability is settled. |
| Basic typography settings | Supported | Current set includes font family/source, font size, line height, paragraph spacing, margins, publisher-style override, theme palette, dim overlay, paged/scroll, direction, and orientation controls. |
| Immersive reader chrome | Supported in `v1.0.10-epsilon7` work | Reader removes the global top bar, hides controls by default, and toggles reader controls through center tap. |
| Left/right tap page turns | Supported in code/tests | Reader-wide tap zones are owned by the native Compose overlay above the WebView and shell-cover surfaces. WebView content keeps only content-specific gestures such as links, media/image taps, and fixed-layout swipe handling. |
| Storyteller EPUB Media Overlay parsing | Supported in tests, partial on device | Parser now preserves OPF media-overlay, SMIL clips, embedded audio resources, duration, and Storyteller audio metadata labels; still requires signed-release smoke tests with real Storyteller-generated EPUB bundles. |
| Media3 readaloud playback | Supported in tests, partial on device | Use Media3 for audio. Do not play embedded audio through WebView. |
| Synced audio/text highlighting | Partial | Existing bridge and sync coordinator exist; validate with real Storyteller audio clips and visible label metadata. |
| Audio metadata labels | Supported in models/playback, partial on device | Bindery and Storyteller OPF labels now flow into readaloud tracks, Media3 item descriptors, playback logs, and reader chrome metadata; still requires signed-release smoke tests with real synced packages. |
| Custom/downloaded/book fonts | Partial | Reader now separates font family from font source: Navic bundled fonts, Android system fallbacks, publication-provided book fonts, and a sanitized `custom` source. Android can import TTF/OTF/WOFF/WOFF2/TTC files into the reader cache, show imported-font cache storage, clear cached imported fonts, and feed selected fonts to WebView through the appassets reader-cache URL. Downloaded font library management and per-book imported font presets remain pending. |
| Rich themes/background images | Partial | Named reader palettes, dim overlay, sepia image tint toggle, paper texture, and border overlays exist. User-imported/background-image themes remain pending. |
| Paragraph spacing and publisher style override | Supported in code/tests | `ReaderSettings`, Settings > Ebooks, search, and the JS runtime apply paragraph spacing and publisher-style override. Continue validating on real EPUBs because publisher CSS can still be hostile. |
| Custom CSS editor | Missing | Adapt Anx's custom CSS concept only after sanitizing/injection boundaries in the Foliate runtime. |
| Screen awake/fullscreen controls | Supported in code/tests | Settings > Ebooks exposes fullscreen and keep-screen-on controls; Android WebView host applies keep-screen-on and reader system bar effects. |
| Volume-key page turn | Supported in code/tests, phone smoke pending | Reader screen handles volume-key page turns behind a persisted Settings > Ebooks switch. Validate with signed APK on device because OEM key routing can vary. |
| Custom tap-zone editor | Partial | Komikku-style presets, smaller zones, and visible tap-zone overlay exist. A true custom 3x3 editor remains pending. |
| Header/footer reading info customization | Partial | Organic page number overlay exists, including `current / total`. Configurable chapter/book/section/battery/time slots remain pending. |
| Per-book reader preferences | Supported foundation in `v1.0.11-eta31` | Reader options expose `Global`, `For this book`, and `Reset book`; book overrides persist in `readerBookSettingsJson` and merge over global defaults. Per-series grouping remains pending until Navic has a stable series identity for Bindery books. |
| TTS service/rate/pitch/volume | Not prioritized | Storyteller synced audio is the primary path. Generic TTS is fallback work, not the core experience. |

### Komikku Options To Adapt

Komikku is image/manga-first, so it is not the EPUB core. Its useful parts are reader settings architecture and native viewer ergonomics:

- Reading mode and direction presets: default, left-to-right, right-to-left, vertical, continuous vertical.
- Orientation lock: default, portrait, landscape, sensor variants where Android supports them cleanly.
- Fullscreen and display-cutout handling.
- Tap navigation modes with a preview overlay.
- Brightness overlay and color filter controls.
- Image/PDF scaling options: fit width, fit height, original size, smart fit, background color.
- Crop-border behavior for image/PDF-like pages.
- Per-title reader preferences that override global defaults.
- Bottom-reader-button customization for commands users use repeatedly.

### Komikku-Inspired Reader UX Direction

Use Komikku's reader chrome behavior as the reader-shell target:

- Center tap reveals a reader-owned overlay, not Navic's global app chrome.
- The top overlay should contain only reader context: back, book title, current chapter/section, bookmark/readaloud state, and optionally a compact play/pause chip for readaloud books.
- The bottom overlay should use icon-first commands: table of contents, readaloud/audio sync, reader settings, search, bookmark/highlight actions, and page/navigation controls.
- The settings surface should be a modal tabbed panel over dimmed content, with dense segmented controls instead of a long generic settings list.
- Reader settings support global defaults plus a per-book override layer, mirroring Komikku's "For this series" model as "For this book" in Navic. Per-series grouping remains a later data-model extension.
- A right-side progress scrubber is desirable for PDF/image/continuous layouts. For EPUB/readaloud it should be treated as a progress navigator only after Foliate locator/percentage seeking is reliable.
- The reader must stay usable with chrome hidden: left/right tap zones, center menu zone, and scroll/page gestures should work inside the WebView content document.

Navic should adapt Komikku's shell, not its manga-only content model. EPUB/readaloud remains Foliate/WebView, Storyteller remains the generated media-overlay compatibility target, and Media3 remains the synced audio engine.

Proposed reader-settings tabs:

- **Reading mode:** paged/scroll, LTR/RTL direction, vertical/continuous where supported, tap-zone preset, page-turn animation/behavior, volume-key turns.
- **General:** font family/custom font, font size, line height, paragraph spacing, margins, publisher style override, keep screen on, fullscreen/system bars.
- **Appearance:** theme palette, foreground/background colors, background image, brightness overlay, color filter, code/highlight theme if needed.
- **Readaloud:** narrator/source labels, clip label display, sync on/off, auto-scroll/highlight behavior, audio quality/source release metadata, compact controls visibility.
- **PDF/Image:** fit width/height/page, crop borders, page gap, background, orientation, continuous strip behavior.

### Lowest Priority / Deferred Animation Work

The page-curl mockup, drag-to-turn animation, dual-page/spread animation, and rotation-triggered spread mode are explicitly the lowest-priority reader backlog item.

Rotation can eventually switch the reader into a dual-page/spread-capable layout, and that layout should use the spread animation model when page-curl work is revisited. That does not move it into the active stabilization or core reader queue.

Do not spend implementation time on page-curl, drag animation, dual-page animation, or rotation-triggered spread behavior until the higher-priority reader work is stable: EPUB pagination/resume, shell cover behavior, native tap ownership, PDF navigation, cache/progress, Storyteller/readaloud metadata, audio sync, and core reader settings.

### Required Next Steps

1. Stabilize the signed-release phone test loop: build in CI, install the signed APK over the existing release package, and run an adb smoke script for EPUB, PDF, and readaloud EPUB. `scripts/adb-reader-smoke.ps1` exists, but live validation still depends on an attached device.
2. Add Storyteller fixture coverage from real generated EPUBs and assert the required audio labels reach parser output, Media3 metadata, reader UI state, and sync logs.
3. Separate EPUB/readaloud settings from PDF/image settings. EPUB stays Foliate/WebView; PDF/image options should borrow Komikku's scaling, crop, brightness, orientation, and navigation patterns.
4. Add a compact readaloud metadata surface in the reader that can show chapter, section, narrator, source release, quality label, and current clip label without making the ebook layout feel like an audiobook player.
5. Continue broader imported/downloaded font management: imported-font removal and cache-size display are supported; downloaded font library management and per-book imported font presets remain pending.

### Recent Release Checkpoints

- `v1.0.11-zeta6` / commit `9abfa4b9`: stabilizes PDF page navigation by serializing duplicate tap/click page-turn events, rendering PDFs with natural layout dimensions, centering PDF pages with fit-width gutters, prefetching adjacent PDF pages, and disabling normal-path bitmap diagnostics. CI built the release APK, verified signing, skipped iOS, and created the GitHub release. ADB phone smoke is still pending because no device was attached during the follow-up audit.
- `v1.0.11-zeta5` / commit `3755a1a8`: fixes release workflow token handling so GitHub release creation succeeds.
- `v1.0.11-zeta4` / commit `cc7d5309`: isolates readaloud playback into the dedicated readaloud media session/service path.

---

### Task 1: Add Readaloud As A First-Class Variant

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogDisplayPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogDisplayPolicyTest.kt`

- [x] Add explicit `Readaloud` media/version kind alongside `Ebook` and `Audiobook`.
- [x] Detect `application/epub+zip` resources that advertise EPUB Media Overlay support.
- [x] Prefer readaloud rows when a Storyteller-generated EPUB is available, without hiding standalone ebook/audiobook options.
- [x] Keep readaloud routing separate from generic ebook and audiobook actions.
- [x] Replace Bindery book/finding row download affordances with a play/open-reader affordance; keep OPDS download-request links available as backend metadata, not as the primary reader UI button.
- [x] Make Bindery book finding candidate rows use an enabled play action backed by the OPDS download-request link instead of a disabled placeholder/download affordance.

### Task 2: Preserve Structured Bindery Metadata

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTest.kt`

- [x] Replace string-only property flattening for book/resource/reading-order metadata with typed nested models.
- [x] Preserve nested `audio` properties such as codec, bitrate, sample rate, channel count, `qualityLabel`, and quality score.
- [x] Preserve nested `sourceRelease` metadata, including provider, source URL, narrator/read-by, edition, format, categories, and keywords when Bindery exposes them.
- [x] Preserve `resourceKey`, `relativePath`, `durationMs`, `language`, `editionSuffix`, and provider labels on every relevant resource.
- [x] Add regression tests proving nested properties survive decode.

### Task 3: Build The Foliate Reader Runtime

**Files:**

- Create: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`
- Create: `composeApp/src/androidMain/assets/reader/`
- Create or modify tests as appropriate for runtime asset validation.

- [x] Package a vetted foliate-js runtime as Navic reader assets.
- [x] Add a Navic JavaScript bridge for location, CFI, TOC, selection, annotations, search, and Media Overlay events.
- [x] Expose reader events from JavaScript to Kotlin: location changed, CFI changed, TOC item changed, selection changed, overlay fragment active, overlay fragment inactive.
- [x] Expose commands from Kotlin to JavaScript: open publication, go to CFI, go to href/fragment, apply highlight, clear overlay, apply reader settings.
- [x] Keep the reader runtime reusable for plain ebooks and readaloud EPUBs.

### Task 4: Add Compose Reader WebView Host

**Files:**

- Create: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`

- [x] Add a native Navic reader route and screen.
- [x] Host Android WebView inside Compose with lifecycle-safe initialization and teardown.
- [x] Add a WebView command channel for non-open bridge commands without duplicate recomposition dispatches.
- [x] Support reader chrome suitable for ebooks: top controls, bottom progress, typography/settings entry, search, TOC, bookmarks, and annotations.
- [x] Do not make the first reader screen a Storyteller-client clone.
- [x] Support opening from Bindery book rows for ebook and readaloud variants.
- [x] Route Bindery ebook/readaloud resource version rows into the native reader route.

### Task 5: Build Media3 Readaloud Audio Controller

**Files:**

- Create: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReadaloudAudioController.android.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReadaloudModels.kt`
- Modify or extend Android playback service wiring without pushing readaloud items through music `DomainSong`.

- [x] Use Media3 for readaloud audio playback, not WebView audio.
- [x] Preserve background playback, lockscreen metadata, playback speed, seek, pause/resume, and audio focus.
- [x] Render chapter/resource labels from structured metadata.
- [x] Include narrator and author metadata in Android media metadata when available.
- [x] Keep readaloud/audiobook playback separate from music queue semantics.

### Task 6: Inject Storyteller Media Overlay Sync

**Files:**

- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/MediaOverlayModels.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerMediaOverlayParser.android.kt`
- Test: add Storyteller readaloud fixture tests.

- [x] Parse Storyteller-generated EPUB 3 Media Overlay data: OPF `media-overlay`, SMIL resources, `clipBegin`, `clipEnd`, text fragments, embedded audio resources, and `media:duration`.
- [x] Extract Storyteller EPUB package-level audio resources and convert them into the same readaloud audio session model used by Media3.
- [x] Preserve Storyteller OPF audio metadata labels such as chapter, section, narrator, quality, source, codec, bitrate, sample rate, channel count, and track/disc numbers.
- [x] Build clip mappings from audio position to text fragment.
- [x] Build reverse mappings from text locator/fragment to audio position.
- [x] Add a tested common sync policy that converts Media3 playback positions to foliate overlay commands and reader navigation events to Media3 seek targets.
- [x] Add a tested reader-readaloud sync coordinator that carries overlay commands with stable WebView dispatch keys and returns Media3 seek targets for synced reader navigation.
- [x] Add tested periodic Media3 position pulses so active playback can continuously feed live text sync.
- [x] On Media3 position changes, highlight the active text fragment in foliate.
- [x] On text tap/navigation to a synced fragment, seek the Media3 controller to the matching clip.
- [x] Support disabling live syncing while keeping read and listen modes independently usable.

### Task 7: Add Proper Ebook Controls

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Create supporting reader settings/state files as needed.

- [x] Add typography controls inspired by Readest/Anx: font family, font size, line height, margins, theme, paged/scrolled mode where supported.
- [x] Add tested reader chrome state for current location, section label, progress percent, typography settings commands, and readaloud playback intent.
- [x] Add bottom reader chrome with progress display and inline font size, theme, and paged/scrolled controls.
- [x] Add TOC navigation with current section tracking.
- [x] Add search with result navigation.
- [x] Add bookmarks.
- [x] Add annotations/highlights with CFI-backed persistence.
- [x] Add progress display and resume behavior.
- [x] Add a compact readaloud control surface that does not compromise the ebook reading layout.
- [x] Wire the compact readaloud play/pause control to the Android Media3 readaloud controller.

### Task 8: Wire Bindery Progress And Resources

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- Add tests for progress contract.

- [x] Add Bindery progress `GET` and `PUT` support.
- [x] Store ebook progress as locator/CFI where available.
- [x] Store audiobook/readaloud progress as resource plus position.
- [x] Preserve alias-scoped progress behavior.
- [x] Add an authenticated Bindery resource byte fetch path for Storyteller EPUB loading.
- [x] Cache authenticated Storyteller readaloud EPUBs and embedded audio locally for WebView and Media3 runtime use.
- [x] Allow the asset-hosted reader runtime to open cached local Storyteller EPUB files while keeping universal file URL access disabled.
- [x] Resolve authenticated resource URLs safely for WebView and Media3.
- [x] Support local caching later without changing the public reader contracts.

### Task 9: Add Compatibility And Regression Tests

**Files:**

- Add Storyteller-generated readaloud EPUB fixtures under an appropriate test fixture path.
- Add focused common and Android tests as needed.

- [x] Test opening a Storyteller-generated readaloud EPUB.
- [x] Test extracting SMIL clips and labels from the fixture.
- [x] Test extracting Storyteller package audio resources into a readaloud playback session.
- [x] Test extracting embedded Storyteller audio and publication bytes to local playable/cacheable URIs.
- [x] Test `audio position -> text fragment` mapping.
- [x] Test `text fragment -> audio seek` mapping.
- [x] Test structured Bindery audio labels survive decoding.
- [x] Test reader resume with CFI/locator.
- [x] Test Media3 metadata labels for narrator, title, chapter, and quality label.

---

## Recycle Map

Use the candidate projects this way:

- **Readest:** reader product model, CFI progress, annotations, search, sidebar/notebook patterns, OPDS UX ideas.
- **Anx Reader:** practical foliate-js WebView bridge, bundled reader runtime patterns, Media Overlay support already present in its foliate assets.
- **Komikku:** reader settings architecture, tap-zone overlay, orientation/fullscreen handling, brightness/color filters, and image/PDF ergonomics.
- **Storyteller:** generated readaloud EPUB contract, SMIL/clip semantics, readaloud compatibility, audio/text sync fixtures.
- **Colibrio:** multimedia UX reference and possible iOS/commercial reference, not Android core.
- **LibreraReader:** broad format/TTS fallback reference, not the main reader runtime.

## Completion Criteria

This project is not complete until:

- Navic can open a plain EPUB with proper ebook controls.
- Navic can open a Storyteller-generated readaloud EPUB.
- Audio playback runs through Media3 with structured metadata labels.
- Active audio clips highlight matching text.
- Text navigation can seek matching audio where Storyteller clip data exists.
- Bindery metadata remains structured enough to preserve audio labels and source evidence.
- Tests cover Storyteller fixtures, OPDS decoding, and sync mappings.
