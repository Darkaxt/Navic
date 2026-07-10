# Reader Aged Paper Safe Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a distinct Aged Paper reader theme, correct the landscape spread gap through Foliate's native layout input, and deliver separate safe landscape and portrait paper compositions without restoring synthetic shell geometry.

**Architecture:** Foliate remains the only text-layout authority. Navic supplies one qualifying landscape `gap` attribute and paints bounded deterministic decoration layers over the real reader viewport. Aged Paper is a theme preset with shared warm-theme semantics; Sepia remains unchanged and cover mode stays isolated.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, JavaScript WebView reader bridge, Foliate paginator, Android host tests, readerdev ADB harness, emulator screenshots.

---

## Stage 0: Preserve The Safe Rollback Baseline

**Existing commits:**

- `a20875a6 Restore theta77 reader layout geometry`
- `d6696aab Rebuild reader overlays without shell geometry`
- `0788415a Guard native reader cover containment`

**Files already guarded:**

- `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-typography.js`
- `composeApp/src/androidMain/kotlin/paige/navic/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeImageLinkTest.kt`

- [x] **Step 1: Remove synthetic text-page shell geometry**

Normal text pages no longer reference `readerPageShellGeometry`, renderer shell rectangles, or static fake paper shells.

- [x] **Step 2: Restore safe overlay-only text pages**

Paper, edge, stain, and gutter layers are attached without changing the renderer rectangle.

- [x] **Step 3: Preserve and guard cover containment**

The native foreground cover uses contained geometry and is drawn above the diffuse backdrop.

- [x] **Step 4: Validate on emulator and tablet**

Existing captures:

- `captures/reader-dev/reader-safe-rebuild-settled.png`
- `captures/reader-dev/reader-cover-settled.png`
- `captures/reader-dev/reader-tablet-text.png`
- `captures/reader-dev/reader-tablet-cover.png`

## Stage 1: Add The Aged Paper Theme Contract

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbookReaderSettingOptions.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchRows.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderChromeStateTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPreferenceSettingsTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/settings/EbookReaderSettingsPolicyTest.kt`

- [ ] **Step 1: Write failing Kotlin theme tests**

Add assertions equivalent to:

```kotlin
@Test
fun agedPaperIsAStableReaderTheme() {
    assertEquals(ReaderAgedPaperTheme, normalizedReaderTheme("aged-paper"))
    assertTrue(ReaderAgedPaperTheme in ReaderSupportedThemes)
    assertEquals("Aged", readerThemeShortLabel(ReaderAgedPaperTheme))
}

@Test
fun readerPreferencesRoundTripAgedPaperWithoutMigratingSepia() {
    val preferences = PreferenceManager(MapSettings())
    preferences.setReaderDefaultSettings(ReaderSettings(theme = ReaderAgedPaperTheme))
    assertEquals(ReaderAgedPaperTheme, preferences.readerDefaultSettings().theme)
    assertEquals(ReaderSepiaTheme, normalizedReaderTheme(ReaderSepiaTheme))
}
```

- [ ] **Step 2: Run the focused tests in red state**

Run once:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderChromeStateTest --tests paige.navic.reader.ReaderPreferenceSettingsTest --tests paige.navic.ui.screens.settings.EbookReaderSettingsPolicyTest
```

Expected: failure because `ReaderAgedPaperTheme` and its UI option do not exist.

- [ ] **Step 3: Implement the Kotlin theme model**

Add the theme constant and include it in normalization/cycling:

```kotlin
const val ReaderAgedPaperTheme = "aged-paper"

val ReaderSupportedThemes = listOf(
    ReaderLightTheme,
    ReaderSepiaTheme,
    ReaderAgedPaperTheme,
    ReaderDuskTheme,
    ReaderDarkTheme,
    ReaderBlackTheme
)
```

Add `AgedPaper` to `ReaderThemeOption`, add `option_ebook_reader_theme_aged_paper`, and make settings search render the same label. Do not migrate existing Sepia values.

- [ ] **Step 4: Run the focused theme tests in green state**

Run the Stage 1 Gradle command again.

Expected: `BUILD SUCCESSFUL`, zero failed focused tests.

- [ ] **Step 5: Commit Stage 1**

```powershell
git add composeApp/src/commonMain
git commit -m "feat: add aged paper reader theme"
```

## Stage 2: Unify Warm Theme Semantics In The Reader Runtime

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-settings-core.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-typography.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeSettingsBridgeTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Add source tests for shared warm-theme behavior**

The tests must require one semantic helper and prohibit repeated ad hoc theme pairs:

```kotlin
assertContains(settingsCore, "export const ReaderThemeAgedPaper = 'aged-paper'")
assertContains(settingsCore, "readerThemeUsesWarmPaperTreatment")
assertContains(settingsCore, "readerThemeUsesSepiaImageTreatment")
assertFalse(contentInteractions.contains("theme === ReaderThemeSepia ||"))
```

Also require `ReaderThemePalettes` to contain `aged-paper` with a warm readable foreground.

- [ ] **Step 2: Run the two focused host tests in red state**

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest
```

Expected: failure on missing Aged Paper runtime symbols.

- [ ] **Step 3: Implement shared runtime semantics**

Add:

```javascript
export const ReaderThemeAgedPaper = 'aged-paper'

export const readerThemeUsesSepiaImageTreatment = theme => {
  const key = readerThemeKey(theme)
  return key === ReaderThemeSepia || key === ReaderThemeAgedPaper
}

export const readerThemeUsesWarmPaperTreatment = theme =>
  readerThemeKey(theme) === ReaderThemeAgedPaper
```

Use the first helper for existing Sepia image adaptation and publisher-style treatment. Use the second only for Aged Paper visual intensity. Keep exact Aged Paper branches centralized in paper composition helpers.

- [ ] **Step 4: Validate JS and focused host tests**

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-settings-core.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-content-interactions.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest
```

Expected: syntax checks exit `0`; Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Stage 2**

```powershell
git add composeApp/src/androidMain/assets/reader composeApp/src/androidHostTest
git commit -m "refactor: unify warm reader theme semantics"
```

## Stage 3: Widen Only The Native Landscape Spread Gap

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-typography.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- Modify: `tools/reader-harness/src/adb-webview-eval.mjs`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Add failing layout-contract tests**

Require a pure resolver:

```javascript
export const readerResolvedFoliateGap = ({ flowMode, width, height, columnCount }) =>
  flowMode === 'paged' && width >= height * 1.12 && columnCount >= 2
    ? '8.5%'
    : null
```

Source tests must require `renderer.setAttribute('gap', resolvedGap)` when non-null and `renderer.removeAttribute('gap')` otherwise. They must continue to prohibit shell geometry.

- [ ] **Step 2: Run the focused host tests in red state**

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellGeometryTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest
```

Expected: failure on missing gap resolver/application.

- [ ] **Step 3: Implement the Foliate-native gap**

Add the pure resolver to `navic-reader-typography.js`. Include its result in the existing page-box projection. In `navic-reader-viewport.js`:

```javascript
if (pageBox.foliateGap) renderer.setAttribute('gap', pageBox.foliateGap)
else renderer.removeAttribute('gap')
```

Do not edit `vendor/foliate-js/paginator.js`. Its existing observed `gap` attribute is the supported insertion point.

- [ ] **Step 4: Extend the page-box probe**

Report the renderer `gap` attribute and computed document `columnGap`. Landscape must report `8.5%`; portrait and scrolled modes must report no explicit Navic gap.

- [ ] **Step 5: Run code-level validation**

Run Node checks for typography, viewport, and harness, then the Stage 3 focused host tests.

Expected: all checks pass and shell-geometry negative guards remain green.

- [ ] **Step 6: Build once and validate landscape on the emulator**

```powershell
.\scripts\set-reader-dev-viewport.ps1 -DeviceSerial emulator-5554 -Profile tab-s9-ultra-landscape
.\scripts\install-reader-dev.ps1 -DeviceSerial emulator-5554 -ReaderBookId 3959 -ReaderTitle "Alcatraz versus the Evil Librarians" -ReaderStartHref "OEBPS/Text/authorsforeword.xhtml" -StartProgress "0.05" -SkipNativeShellCover -RequireReaderLaunch
node tools\reader-harness\src\adb-webview-eval.mjs --device emulator-5554 --probe page-box --local-port 9241
```

Capture `captures/reader-aged-paper/stage-3-landscape-gap.png` after publication ready. Reject if the prose gap is not visibly wider and balanced.

- [ ] **Step 7: Commit Stage 3**

```powershell
git add composeApp/src/androidMain/assets/reader tools/reader-harness composeApp/src/androidHostTest
git commit -m "fix: widen native reader landscape spread gap"
```

## Stage 4: Implement Aged Paper Landscape Composition

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-settings-core.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Add failing composition guards**

Require dedicated pure helpers:

```javascript
readerSurfacePaperBaseBackground(settings, spreadMode)
readerSurfacePaperTextureOpacity(settings)
readerSurfacePageBorderOverlayOpacity(settings)
readerSurfacePageStainOverlayOpacity(settings)
readerSurfaceSpreadGutterOverlayOpacity(settings)
```

The tests must require an Aged Paper branch and confirm Sepia values remain unchanged.

- [ ] **Step 2: Implement the Aged Paper base**

For Aged Paper only, return a warm page-local background composition comparable to the accepted PoC:

```javascript
const agedPaperBase = [
  'linear-gradient(90deg, rgba(118,76,31,.10) 0%, rgba(255,244,212,.09) 7%, rgba(255,244,212,0) 19%, rgba(95,57,20,.07) 49.4%, rgba(49,29,10,.16) 50%, rgba(255,238,190,.09) 50.8%, rgba(255,244,212,0) 82%, rgba(103,65,26,.10) 100%)',
  '#ead9ae',
]
```

Apply it inside the existing backing/overlay pipeline. Do not create a shell node, reserve margins, or move Foliate content.

- [ ] **Step 3: Tune only the Aged Paper layer strengths**

Use stable preset values, keeping Sepia unchanged:

```text
paper texture: 0.64
edge wear: 0.74
stains: 0.62
gutter: 0.92
```

The final values may move by at most `0.08` during emulator tuning. Edge assets must remain narrow; do not solve visibility by scaling them into the prose area.

- [ ] **Step 4: Validate independent toggles in code**

Focused tests must prove:

- paper off removes fibers but leaves warm theme color
- edges off removes outer wear and gutter
- stains off removes patina only
- no layer alters renderer dimensions

- [ ] **Step 5: Validate the Aged Paper spread on emulator**

Reuse the installed readerdev build after one rebuild. Capture:

- `stage-4-landscape-sepia.png`
- `stage-4-landscape-aged.png`
- `stage-4-landscape-aged-paper-off.png`
- `stage-4-landscape-aged-edges-off.png`
- `stage-4-landscape-aged-stains-off.png`

Acceptance: Aged Paper is visibly warmer and more worn than Sepia, but text remains clear and no framed-window appearance returns.

- [ ] **Step 6: Commit Stage 4**

```powershell
git add composeApp/src/androidMain/assets/reader composeApp/src/androidHostTest
git commit -m "feat: add aged paper reader composition"
```

## Stage 5: Add A Separate Portrait Composition

**Files:**

- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`

- [ ] **Step 1: Add failing portrait guards**

Tests must require:

```text
spread mode single -> no spread gutter slots
spread mode single -> no explicit 8.5% gap
page edges enabled -> optional left binding hint
page edges disabled -> no binding hint
```

They must prohibit a second-page slab and centered two-page seam in portrait.

- [ ] **Step 2: Implement the single-page resolver**

Add a pure profile such as:

```javascript
export const readerPaperLayoutProfile = ({ flowMode, width, height }) => {
  const spread = readerSurfaceSpreadMode({ flowMode, width, height }) === 'spread'
  return spread
    ? { mode: 'spread', bindingEdge: 'center' }
    : { mode: 'single', bindingEdge: 'left' }
}
```

Use `mode: single` to render one page texture/edge/stain field and a subtle left binding hint. The hint is an overlay and cannot affect width, padding, or column layout.

- [ ] **Step 3: Validate portrait on the emulator**

Switch to the portrait tablet profile, launch the same Alcatraz location, and capture:

- `stage-5-portrait-aged.png`
- `stage-5-portrait-aged-edges-off.png`

Acceptance: one complete page, no center seam, no right blank page, no inside-cover slab, and no landscape gap behavior.

- [ ] **Step 4: Run Stage 5 focused tests and commit**

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest --tests paige.navic.reader.ReaderRuntimeShellGeometryTest
git add composeApp/src/androidMain/assets/reader composeApp/src/androidHostTest
git commit -m "fix: separate portrait reader paper composition"
```

## Stage 6: Lock Cover Isolation And Diagnostics

**Files:**

- Modify: `tools/reader-harness/src/adb-webview-eval.mjs`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeImageLinkTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Extend diagnostics**

The texture probe reports theme, spread mode, explicit Foliate gap, toggle state, and layer presence. It must distinguish the portrait binding hint from the landscape gutter.

- [ ] **Step 2: Keep cover regression guards green**

Require:

- foreground draw uses `foregroundImageRect`
- foreground scale uses `min(maxWidth / bitmapWidth, maxHeight / bitmapHeight)`
- backdrop and foreground draw paths remain distinct
- no text-page paper layer is inserted into cover mode
- no back-cover plane is drawn on the cover page

- [ ] **Step 3: Capture the emulator cover matrix**

Capture backdrop on and off at `OEBPS/Text/cubierta.xhtml`.

Acceptance: both foreground covers are fully visible; backdrop-on fills unused space; backdrop-off does not introduce the removed green plane.

- [ ] **Step 4: Run the focused diagnostics/cover suite and commit**

```powershell
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeImageLinkTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest
git add tools/reader-harness composeApp/src/androidHostTest
git commit -m "test: lock aged reader visual diagnostics"
```

## Stage 7: Emulator Acceptance Matrix

**Evidence directory:** `captures/reader-aged-paper/final-emulator`

- [ ] **Step 1: Build readerdev once from the final candidate**

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :androidApp:assembleReaderDev
```

- [ ] **Step 2: Run the landscape matrix**

Validate Sepia, Aged Paper, three layer toggles, and the `8.5%` page-box probe.

- [ ] **Step 3: Run the portrait matrix**

Validate Aged Paper and edges-off. Confirm no explicit landscape gap and no spread gutter.

- [ ] **Step 4: Run the cover matrix**

Validate foreground containment with backdrop on and off.

- [ ] **Step 5: Compare captures**

Compare against:

- theta77 baseline
- accepted HTML PoC
- physical tablet rollback captures from Stage 0

Reject if any shell/window/triple-margin regression returns or if portrait looks like half of the landscape spread.

## Stage 8: Conditional Physical Tablet Gate

- [ ] **Step 1: Decide whether escalation is required**

The tablet is required only if emulator output conflicts with prior tablet output, DPI/banding is uncertain, insets alter the gap, or the acceptance result is ambiguous.

- [ ] **Step 2: If required, install readerdev and capture only the disputed modes**

Use Alcatraz and the same hrefs as the emulator. Do not rerun the entire matrix when the uncertainty is limited to one mode.

- [ ] **Step 3: Patch and repeat the smallest relevant gate if the tablet exposes a discrepancy**

Do not create a release while a disputed visual gate remains unresolved.

## Stage 9: Final Verification, Integration, And Release

**Files:**

- Modify release metadata using the repository's current release process.

- [ ] **Step 1: Run fresh syntax and focused reader verification**

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-settings-core.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-viewport.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-typography.js
node --check tools\reader-harness\src\adb-webview-eval.mjs
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderChromeStateTest --tests paige.navic.reader.ReaderPreferenceSettingsTest --tests paige.navic.ui.screens.settings.EbookReaderSettingsPolicyTest --tests paige.navic.reader.ReaderRuntimeShellGeometryTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest --tests paige.navic.reader.ReaderRuntimeImageLinkTest --tests paige.navic.reader.ReaderRuntimeSettingsBridgeTest
git diff --check
```

Expected: all syntax checks exit `0`, the focused Gradle invocation reports `BUILD SUCCESSFUL`, and `git diff --check` reports no errors.

- [ ] **Step 2: Sync the integration target**

```powershell
git fetch fork --prune
git fetch origin --prune
git status --short --branch
```

Resolve master conflicts while preserving newer unrelated work. Re-run the fresh verification commands after conflict resolution.

- [ ] **Step 3: Merge the staged branch into master**

Use non-interactive Git operations. Confirm the merged master contains every staged commit and is clean before versioning.

- [ ] **Step 4: Build the release candidate once**

Run the repository's release build. Install the resulting candidate on the emulator and repeat the three representative captures: landscape Aged Paper, portrait Aged Paper, and cover backdrop.

- [ ] **Step 5: Publish the next sequential theta release**

Do not skip theta subversions. Push master and tag, publish `Navic.apk`, and verify the remote release metadata and asset.

- [ ] **Step 6: Report evidence**

Report commit SHAs, test/build results, emulator capture paths, whether the tablet escalation gate was used, tag, release URL, and APK asset status.

## Plan Self-Review

- Every specification requirement has a stage.
- Landscape and portrait have separate contracts and separate screenshots.
- Aged Paper does not modify Sepia or migrate existing users.
- The Foliate gap uses the existing observed `gap` attribute and does not edit vendor code.
- Cover mode remains isolated and guarded.
- Emulator validation is the default; tablet validation is conditional and evidence-driven.
- No placeholder implementation steps or runtime timeouts are present.
- Gradle runs are batched at meaningful stage gates rather than repeated after every small edit.
