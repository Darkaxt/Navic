# Komikku Reader Parity Design

Date: 2026-06-12

Status: approved replacement direction for reader touch behavior, chrome, and settings layout.

## Objective

Replicate Komikku's reader interaction and layout model in Navic for EPUB and PDF reading, while preserving Navic-specific ebook features:

- Bindery EPUB/PDF open and cache flow.
- Shell-cover presentation and EPUB cover suppression.
- Readaloud and future Storyteller media-overlay support.
- Future Whispersync-style audiobook/ebook synchronization.
- Existing reader progress, bookmarks, annotations, search, paper textures, themes, imported fonts, and PDF/Image settings.

This document replaces the earlier eta33/eta37 touch-direction assumptions. The prior stabilization design remains useful historical evidence, but its touch checkpoints contradict each other. The current target is the Komikku model described here.

## Source Reference

Komikku reference checkout:

```text
C:\Users\darka\AppData\Local\Temp\codex-komikku-ref
commit 3b06366
```

Relevant Komikku files:

- `app/src/main/res/layout/reader_activity.xml`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderNavigationOverlayView.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/GestureDetectorWithLongTap.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/Pager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerViewer.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonRecyclerView.kt`
- `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`
- `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt`
- `app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt`
- `app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt`
- `app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt`
- `app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt`
- `app/src/main/java/eu/kanade/presentation/reader/settings/GeneralSettingsPage.kt`
- `app/src/main/java/eu/kanade/presentation/reader/settings/ColorFilterPage.kt`
- `app/src/main/java/eu/kanade/presentation/components/AdaptiveSheet.kt`

## Required Behavioral Contract

Komikku has three separate layers:

1. The real reader surface owns the touch stream and maps confirmed taps.
2. The navigation overlay only visualizes tap zones.
3. Compose chrome renders menus, page controls, and settings dialogs.

Navic must preserve this separation.

### Gesture Ownership

The real gesture owner in Navic must be an Android-native reader surface container, not a Compose `pointerInput` overlay and not JavaScript reader-wide tap handling.

The Android-native reader surface must:

- Contain the WebView and the native shell-cover surface as child views.
- Receive the Android `dispatchTouchEvent` stream.
- Call `super.dispatchTouchEvent(event)` first so the child WebView/PDF renderer gets the complete stream.
- Feed the same stream to a Komikku-style gesture detector after child dispatch.
- Convert only confirmed single taps into reader-wide actions.
- Never consume or cancel the low-level drag stream required by Foliate/PDF.js page movement.
- Dispatch the same action path for EPUB, PDF, and shell-cover surfaces.
- Keep JavaScript `nativeTapZones=true` on Android so JS never dispatches duplicate reader-wide previous, next, or menu taps.

This mirrors Komikku's `Pager.dispatchTouchEvent`: child/page handling first, tap-zone observation second.

### Page Actions

Reader-wide tap actions remain:

- `PreviousPage`
- `NextPage`
- `CenterTap` or equivalent menu toggle

The surface must map tap zones through the existing Komikku-compatible Navic model in `ReaderChromeState.kt`:

- Default
- L-shaped
- Kindle-ish
- Edge
- Right and Left
- Disabled

Default mode must resolve like Komikku:

- paged horizontal: right-and-left
- paged vertical and scrolled modes: L-shaped

Smaller tap zones must affect region size consistently. If Navic keeps a different numeric size from Komikku for ergonomics, that difference must be explicit and tested. Otherwise it should match Komikku's 0.33 normal and 0.25 smaller region split.

### Content-Specific Gestures

Reader-wide tap classification must not break content actions:

- WebView drag/swipe page turning.
- PDF drag/swipe page turning.
- Text selection.
- Hyperlink activation.
- Image tap behavior such as sepia-tint toggle.
- Media/readaloud interaction points.

The acceptable rule is: the child surface gets the full stream first; the native container only reacts to confirmed single taps that are not handled as content interactions. If a practical WebView hit-test guard is needed for anchors, images, or editable/media nodes, it belongs in the native surface contract and must still return the stream to WebView.

### Shell Cover

The shell cover must not be a separate Compose input island.

Required behavior:

- The shell cover is a child of the same Android reader surface used by WebView content.
- Taps on the cover image and cover margins behave the same.
- Next from shell cover enters the first readable WebView/PDF page.
- Previous from shell cover is a no-op unless a future book-level transition is introduced.
- Center/menu tap toggles reader chrome.
- Previous from the first readable page returns to shell cover when shell-cover mode is active.
- EPUB cover content remains suppressed from the WebView.

### Visual Tap-Zone Overlay

The visible tap-zone overlay is only a visual/debug layer.

Required behavior:

- It is non-interactive.
- It renders over the WebView and shell cover.
- It follows the selected tap-zone mode, smaller-zone setting, flow mode, and direction mapping.
- It can fade in on demand or when "Show tap zones" is enabled.
- It must not decide page actions.

This matches Komikku's `ReaderNavigationOverlayView`.

## Required Layout Contract

Navic's reader chrome must move toward Komikku's overlay structure.

### Base Layering

The reader screen should be modeled as:

1. Full-screen reader surface host.
2. Non-interactive visual overlays: dim layer, page texture/page number if applicable, tap-zone visualizer.
3. Animated reader chrome: top app bar, page navigator, compact bottom controls.
4. Adaptive dialogs/sheets for settings, TOC, search, bookmarks, annotations, and media controls.

Global Navic navigation chrome must not appear inside the reader.

### Top Chrome

The top reader bar should be hidden by default and shown only when reader chrome is visible.

It should contain:

- Back action.
- Book title.
- Current section/subtitle when available.
- Bookmark action when applicable.
- Overflow or compact actions only where needed.

It should slide and fade similarly to Komikku's top app bar.

### Bottom Chrome

The bottom chrome must stay compact. It is not a settings page.

It should contain:

- Page/section navigator with previous/next buttons and a slider.
- Compact icon row for TOC, highlights/annotations, bookmark, search, settings, and readaloud/media when relevant.
- No endless option list directly docked into the reader.

The existing `ReaderBottomChrome` has some useful state plumbing, but its layout should be reshaped to Komikku's compact app-bar model.

### Page Navigator

The navigator should support:

- Horizontal bottom slider.
- Vertical left/right slider for landscape or large layout where appropriate.
- Current page label and total.
- Haptic or equivalent feedback if supported locally.
- Consistent behavior for EPUB and PDF.

The page number display in the reading surface remains the organic book-style `current / total` overlay already introduced, but cover page index `-1` must not be counted.

### Settings Dialog

The reader settings surface should be a Komikku-style adaptive tabbed dialog/sheet:

- Phone: bottom sheet, height capped around 75% of the window.
- Large/tablet/landscape: centered or constrained dialog, max-width style similar to Komikku's `AdaptiveSheet`.
- Tabs use chip groups, toggles, and sliders rather than cyclic value rows.

Required tabs:

- Reading: scope, reading mode/flow, direction, font source/family, font size, line height, paragraph spacing, margins, tap zones when it is more ergonomic here.
- General: theme, fullscreen, rotation, keep screen on, publisher styles, paper texture, page numbers, visible tap zones.
- Appearance/Filter: brightness/dim, sepia/image tint options, custom filter controls as Navic grows toward Komikku parity.
- Media: readaloud and media-overlay controls when the book supports them.
- PDF/Image: page fit, crop borders, page gap, PDF-specific scaling and navigation controls when the publication is PDF/fixed-layout.

The global Settings > Ebooks page remains for defaults. The in-reader settings dialog is for current reading behavior and per-book override scope.

## Navic Components To Change

Primary files expected to change during implementation:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`
- new Android reader surface/gesture classes under `composeApp/src/androidMain/kotlin/paige/navic/reader/`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt` only if the native surface needs a small command/event contract adjustment
- reader host tests under `composeApp/src/androidHostTest/kotlin/paige/navic/reader/`

Implementation must remove or rewrite tests that currently require the wrong architecture:

- Tests that require `ReaderNativeTapOverlay` to own readable WebView taps.
- Tests that forbid a native WebView/surface touch observer categorically.
- Tests that describe the Compose sibling overlay as the single top tap manager.

Replacement tests must assert the actual Komikku contract:

- Android reader surface dispatches to children first.
- The native gesture detector observes without consuming drags.
- JS reader-wide taps are disabled on Android through `nativeTapZones=true`.
- Visual tap-zone overlay is non-interactive.
- Shell cover and WebView content share the same action dispatch path.

## Implementation Plan

### Microdeliverable 1: Spec And Test Contract

Scope:

- Add this design.
- Rewrite focused host tests so current code fails for the right reason.
- Tests must describe Android-native reader surface ownership, not Compose top overlay ownership.

Verification:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
```

Expected initial state before implementation: focused failures proving the current architecture is still wrong.

### Microdeliverable 2: Android Reader Surface Host

Scope:

- Add a native Android surface/container around WebView content.
- Install a Komikku-style gesture detector on that surface.
- Dispatch child touches first, then observe confirmed taps.
- Dispatch previous/next/menu actions through the existing `ReaderBridgeCommand` path.
- Keep JS `nativeTapZones=true`.

Verification:

- Host tests prove child-first dispatch and non-consuming gesture observation from source structure.
- ADB verifies EPUB center/edge taps over actual rendered text and images.
- ADB verifies PDF center/edge taps over rendered pages.
- ADB verifies drag/swipe still moves pages smoothly.

### Microdeliverable 3: Shell Cover In Same Surface

Scope:

- Move native shell cover under the Android reader surface.
- Remove cover-only Compose tap ownership.
- Make cover image and cover margins use the same tap action path.
- Preserve shell-cover page index `-1` behavior.

Verification:

- ADB verifies cover image taps: left/right/center all work over the image itself.
- ADB verifies next from cover enters readable content.
- ADB verifies previous from first readable page returns to shell cover.
- Harness still verifies WebView cover suppression.

### Microdeliverable 4: Visual Tap-Zone Overlay

Scope:

- Convert "Show tap zones" to a non-interactive visual overlay.
- It must draw the actual selected regions for current flow/tap mode.
- It must not perform navigation.

Verification:

- Host tests ensure visual overlay is separated from action dispatch.
- ADB verifies toggling visible zones does not change tap behavior.

### Microdeliverable 5: Komikku Chrome Reshape

Scope:

- Replace current docked reader chrome with animated top and bottom reader chrome.
- Keep existing TOC/search/bookmark/annotation/readaloud capabilities.
- Move option controls out of the bottom chrome.

Verification:

- Screenshot/ADB inspection in EPUB and PDF.
- No global app top bar appears in reader.
- Center tap toggles both top and bottom chrome consistently.

### Microdeliverable 6: Adaptive Tabbed Settings

Scope:

- Replace the current long bottom settings list with a Komikku-style adaptive tabbed dialog/sheet.
- Preserve Navic settings: reading mode, direction, font sources, typography, themes, paper textures, PDF/Image, readaloud/media, scope/global/book overrides.
- Keep Settings > Ebooks defaults intact.

Verification:

- Host tests confirm settings are not embedded directly in bottom chrome.
- ADB verifies phone layout.
- If possible, desktop/landscape render verifies constrained dialog behavior.

### Microdeliverable 7: Release Gate

Release only after phone-evaluable behavior changes pass:

- EPUB: cover, text page, image page, hyperlink page, search result page.
- PDF: first page, next/previous taps, drag/swipe, fit modes.
- Center tap toggles chrome on EPUB, PDF, cover image, and cover margins.
- Edge tap turns page on EPUB, PDF, cover image, and cover margins.
- Drag/swipe page movement is still smooth and not delayed until release.
- Visible tap zones are accurate and non-interactive.

## Verification Rules

Do not claim Komikku parity from host string tests alone.

Required evidence before claiming a slice fixed:

- Source tests for the architectural invariant.
- Runtime harness check where the behavior is renderer-visible.
- ADB check when Android touch layering is involved.
- Release only when the behavior is visible to the user on the phone.

## Deferred Work

These stay below touch/layout parity:

- Page-curl animation.
- Dual-page/spread animation.
- Rotation-triggered spread animation.
- More advanced Whispersync feature work beyond preserving the extension points.

The reader surface design must not block these future features. It should leave a clean command/event path for synchronized media and future page transition effects.
