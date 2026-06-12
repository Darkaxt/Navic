# Komikku Reader Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Navic's current reader touch/chrome direction with a Komikku-style native reader surface, visual-only tap-zone overlay, compact chrome, and adaptive tabbed settings for EPUB and PDF.

**Architecture:** Android owns reader-wide confirmed tap classification in a native surface container that dispatches touch events to child content first, then observes the same stream. Compose owns visible chrome and settings only. JavaScript keeps rendering, pagination, PDF/image handling, links, selections, media overlays, and visual tap-zone diagnostics, but does not dispatch reader-wide previous/next/menu taps on Android.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform common UI, Android `AndroidView`, Android `FrameLayout`/`WebView`, Foliate/PDF.js reader assets, Gradle Android host tests, ADB validation.

---

### Task 1: Replace Wrong Touch-Ownership Test Contract

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeSettingsBridgeTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellProgressTest.kt`
- Modify: `docs/superpowers/specs/2026-06-12-komikku-reader-parity-design.md` only if test findings expose an ambiguity.

- [x] **Step 1: Write failing tests**

Replace assertions that require a Compose `ReaderNativeTapOverlay` to own readable WebView taps with assertions that require:

```kotlin
assertContains(webViewHostText, "ReaderSurfaceHost")
assertContains(webViewHostText, "dispatchTouchEvent(event: MotionEvent)")
assertContains(webViewHostText, "val childHandled = super.dispatchTouchEvent(event)")
assertContains(webViewHostText, "readerGestureDetector.onTouchEvent(event)")
assertContains(webViewHostText, "return childHandled")
assertContains(webViewHostText, "settings.copy(nativeTapZones = true)")
assertFalse(readerScreenText.contains("ReaderNativeTapOverlay("))
```

- [x] **Step 2: Run focused tests and verify RED**

Run:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest"
```

Expected: FAIL because `ReaderSurfaceHost` does not exist and `ReaderNativeTapOverlay` still exists.

### Task 2: Add Android Reader Surface Host

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`

- [x] **Step 1: Implement child-first Android surface**

Create a private Android `ReaderSurfaceHost : FrameLayout` in `ReaderWebViewHost.android.kt` that:

```kotlin
override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val childHandled = super.dispatchTouchEvent(event)
    if (readerWideTapsEnabled) {
        readerGestureDetector.onTouchEvent(event)
    }
    return childHandled
}
```

The gesture listener maps confirmed taps with `readerTapZoneActionAt(x, y, settings.tapZone, settings.smallerTapZone == true, settings.flowMode)` and dispatches `ReaderBridgeCommand.PreviousPage`, `ReaderBridgeCommand.NextPage`, or `ReaderBridgeEvent.CenterTap`.

- [x] **Step 2: Remove Compose input ownership**

Delete or disable `ReaderNativeTapOverlay` as an input owner in `ReaderScreen.kt`. Do not leave `pointerInput` tap regions above WebView readable content.

- [x] **Step 3: Run focused tests and verify GREEN**

Run the same focused test command from Task 1.

Expected: PASS for touch-ownership source assertions.

### Task 3: Move Shell Cover Into Surface Contract

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt` expect signature for `ReaderWebViewHost`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/paige/navic/reader/ReaderWebViewHost.ios.kt` if the expect signature changes

- [x] **Step 1: Extend host inputs**

Pass `shellCoverUrl`, `shellCoverVisible`, and `onShellCoverVisibleChange` or equivalent into the Android host so the cover is a child of `ReaderSurfaceHost`.

- [x] **Step 2: Preserve cover behavior**

Next from shell cover hides the cover and enters readable content. Previous from first readable page still returns to the shell cover through the existing page-index `-1` logic.

- [ ] **Step 3: Verify with tests and ADB**

Host tests pass for the native shell-cover path. ADB validation is still pending because `adb devices` reported no connected devices during this slice.

Run source tests plus device checks over cover image and cover margins.

### Task 4: Visual-Only Tap-Zone Overlay

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader.js` only if JavaScript visual diagnostics need cleanup

- [ ] **Step 1: Keep tap-zone rendering, remove tap authority**

The overlay should draw regions only. It must not call `onPageTurn` or `onMenuTap`.

- [ ] **Step 2: Verify**

Tests must assert that visible zones and input dispatch are separate.

### Task 5: Komikku Chrome And Settings Layout

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt`

- [ ] **Step 1: Compact chrome**

Reshape bottom chrome into a compact navigator plus icon row. Add top chrome for title/subtitle/back/bookmark where appropriate.

- [ ] **Step 2: Adaptive tabbed settings**

Move the current options list into an adaptive tabbed dialog/sheet with Reading, General, Appearance/Filter, Media, and PDF/Image tabs as applicable.

- [ ] **Step 3: Verify**

Run host chrome tests and inspect with ADB screenshots on EPUB and PDF.

### Task 6: Release Gate

**Files:**
- Modify docs/release evidence only after validation.

- [ ] **Step 1: Full focused verification**

Run:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderRuntimeSettingsBridgeTest" --tests "paige.navic.reader.ReaderRuntimeShellProgressTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest"
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

- [ ] **Step 2: ADB smoke**

Validate EPUB and PDF:

- cover image left/right/center taps
- cover margin left/right/center taps
- EPUB text page edge and center taps
- EPUB image page image tap versus page tap behavior
- PDF page edge and center taps
- EPUB/PDF drag/swipe still follows the finger

- [ ] **Step 3: Commit and release**

Commit the slice, then trigger release only after ADB behavior is phone-evaluable.
