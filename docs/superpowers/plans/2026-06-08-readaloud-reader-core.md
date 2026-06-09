# Readaloud Reader Core Implementation Plan

> **For agentic workers:** This is the required project direction for Navic's ebook/readaloud reader work. Do not replace it with a Storyteller-client clone or a generic audiobook sidecar. Implement task-by-task and update checkbox status as work lands.

**Date:** 2026-06-08

**Status:** required implementation plan and context anchor.

**Goal:** Build a proper ebook reader experience in Navic, with Storyteller-generated readaloud EPUB support injected into the reader and playback runtime.

**Non-negotiable direction:** The reader UX must be closer to Readest/Anx Reader than to the Storyteller client. Storyteller is the artifact compatibility target for generated readaloud EPUBs and Media Overlay metadata, not the UX baseline.

**Architecture:** Use a foliate-js based reader runtime for the polished ebook surface, a Navic Compose host for native UI, and Android Media3 for audio playback. EPUB Media Overlay and Storyteller clip metadata bridge the reader and audio controller.

**Tech stack:** Kotlin Multiplatform, Compose Multiplatform, Android WebView, foliate-js, Media3, Bindery OPDS 2, Storyteller EPUB 3 Media Overlay artifacts.

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

## Current Support Audit - 2026-06-09

Reference snapshots inspected:

- Anx Reader: `Anxcye/anx-reader` at `107f4fa`
- Komikku: `komikku-app/komikku` at `582ea3e`

The checked tasks below mean first-pass Navic implementation exists. They do not mean Navic has Anx/Readest-class reader parity yet.

### Anx Reader Parity

| Capability | Navic status | Required direction |
| --- | --- | --- |
| Foliate-style EPUB runtime | Supported | Keep this as the EPUB/readaloud reader surface. Do not replace it with the Storyteller client. |
| Bindery EPUB/readaloud routing | Supported | Keep ebook/readaloud routing separate from audiobook-only playback. |
| PDF.js-backed PDF opening | Partial | PDF.js assets are present through foliate-js, but signed-release phone smoke tests must prove real Bindery PDFs render. |
| TOC navigation | Supported | Hide reader chrome after TOC navigation so content stays immersive. |
| Search and result navigation | Supported | Keep as core reader chrome, not the global app search bar. |
| CFI/location progress and resume | Supported | Continue syncing Bindery progress by locator/CFI where available. |
| Bookmarks | Supported | Keep CFI-backed and hide chrome after navigation. |
| Annotations/highlights | Supported | Keep CFI-backed, and add export/share later only after renderer stability is settled. |
| Basic typography settings | Supported | Current set is font family, font size, line height, margin, light/dark theme, paged/scroll. |
| Immersive reader chrome | Supported in `v1.0.10-epsilon7` work | Reader removes the global top bar, hides controls by default, and toggles reader controls through center tap. |
| Left/right tap page turns | Supported in `v1.0.10-epsilon7` work | Keep page-zone behavior in the WebView bridge so it works inside EPUB iframe documents. |
| Storyteller EPUB Media Overlay parsing | Supported in tests, partial on device | Requires signed-release smoke tests with real Storyteller-generated EPUB bundles. |
| Media3 readaloud playback | Supported in tests, partial on device | Use Media3 for audio. Do not play embedded audio through WebView. |
| Synced audio/text highlighting | Partial | Existing bridge and sync coordinator exist; validate with real Storyteller audio clips and visible label metadata. |
| Audio metadata labels | Partial | Preserve labels in models and Media3 metadata; still need reader UI display for chapter, section, narrator, quality, and source labels. |
| Custom/downloaded/book fonts | Missing | Adapt Anx-style custom font model, but expose it through Navic settings and reader chrome. |
| Rich themes/background images | Missing | Add named reader themes with foreground/background/accent and optional background image. |
| Paragraph spacing and publisher style override | Missing | Extend `ReaderSettings` and JS `applySettings`; include "follow book styles" vs "override" toggle. |
| Custom CSS editor | Missing | Adapt Anx's custom CSS concept only after sanitizing/injection boundaries in the Foliate runtime. |
| Screen awake/fullscreen controls | Missing | Add keep-screen-on and system bar/fullscreen preferences. |
| Volume-key page turn | Missing | Add Android key handling in the reader host. |
| Custom tap-zone editor | Missing | Adapt Anx's simple/custom page-turn mode and 3x3 zone editor. |
| Header/footer reading info customization | Missing | Add configurable chapter title, book progress, section progress, battery/time slots. |
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

### Required Next Steps

1. Stabilize the signed-release phone test loop: build in CI, install the signed APK over the existing release package, and run an adb smoke script for EPUB, PDF, and readaloud EPUB.
2. Promote reader navigation settings from hard-coded behavior to persisted preferences: tap zones, center-menu zone, swap zones, disable tap turns, RTL/LTR direction, and volume-key turns.
3. Expand `ReaderSettings` and the Settings > Ebooks page with Anx-class controls: custom fonts, paragraph spacing, theme palettes, publisher-style override, fullscreen/awake settings, and optional custom CSS.
4. Add Storyteller fixture coverage from real generated EPUBs and assert the required audio labels reach parser output, Media3 metadata, reader UI state, and sync logs.
5. Separate EPUB/readaloud settings from PDF/image settings. EPUB stays Foliate/WebView; PDF/image options should borrow Komikku's scaling, crop, brightness, orientation, and navigation patterns.
6. Add a compact readaloud metadata surface in the reader that can show chapter, section, narrator, source release, quality label, and current clip label without making the ebook layout feel like an audiobook player.

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
