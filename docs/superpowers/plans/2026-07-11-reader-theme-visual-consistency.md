# Reader Theme Visual Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve cover-derived hue in portrait and landscape back-cover reveals and make the Whispersync headset use the active reader theme foreground.

**Architecture:** Keep Foliate geometry untouched. Resolve one JavaScript back-cover palette before orientation-specific gradient placement, and resolve one Kotlin reader foreground color before rendering the native Compose headset overlay. The page-turn revision remains documentation-only in this release.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, JavaScript reader runtime, Android host tests, Node reader harness, Gradle, adb readerdev emulator.

---

### Task 1: Lock the shared back-cover palette contract

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
- Modify: `tools/reader-harness/src/run-reader-harness.mjs`

- [ ] **Step 1: Write the failing source guard**

Update `portraitPaperCompositionDoesNotInheritLandscapeGeometry()` to require a shared exported palette resolver and require both orientation paths to consume it:

```kotlin
assertContains(helperText, "export const readerSurfaceBackCoverPalette = (settings, coverTint) =>")
assertContains(helperText, "const coverPalette = readerSurfaceBackCoverPalette(settings, geometry.coverTint)")
assertFalse(
    helperText.substringAfter("export const readerSurfaceBackCoverBackground")
        .substringBefore("export const readerSurfacePageDecorationBackground")
        .contains("readerDesaturatedColorChannels(geometry.coverTint"),
    "Orientation-specific back-cover gradients must not independently desaturate the sampled cover hue."
)
```

- [ ] **Step 2: Add a failing executable harness mode**

Add `back-cover-tint-logic` to `run-reader-harness.mjs`. Import `navic-reader-helpers.js` with the same minimal globals used by `pagination-profile-logic`, then assert:

```javascript
const teal = helpers.readerSurfaceBackCoverPalette(
  { theme: 'sepia' },
  'rgb(17, 103, 112)'
)
if (!(teal.base.green > teal.base.red && teal.base.blue > teal.base.red)) {
  throw new Error(`Expected cover hue to survive normalization: ${JSON.stringify(teal)}`)
}
const portrait = helpers.readerSurfaceBackCoverBackground(
  { theme: 'sepia' },
  { backCoverVisible: true, backCoverEdge: 'right', coverTint: 'rgb(17, 103, 112)', backCoverRevealPercent: 1 }
)
const landscape = helpers.readerSurfaceBackCoverBackground(
  { theme: 'sepia' },
  { backCoverVisible: true, backCoverEdge: 'both', coverTint: 'rgb(17, 103, 112)', backCoverRevealPercent: 1, outerInsetPercent: 1, backCoverStartPercent: 0 }
)
if (!portrait.includes(`rgba(${teal.base.red}, ${teal.base.green}, ${teal.base.blue}`)) throw new Error('Portrait lost shared tint')
if (!landscape.includes(`rgba(${teal.base.red}, ${teal.base.green}, ${teal.base.blue}`)) throw new Error('Landscape lost shared tint')
```

- [ ] **Step 3: Run RED verification**

Run:

```powershell
node tools/reader-harness/src/run-reader-harness.mjs --mode back-cover-tint-logic
./gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest"
```

Expected: both fail because `readerSurfaceBackCoverPalette` does not exist.

- [ ] **Step 4: Commit the failing tests**

```powershell
git add composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt tools/reader-harness/src/run-reader-harness.mjs
git commit -m "test: lock shared reader cover tint"
```

### Task 2: Implement the shared back-cover palette

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`

- [ ] **Step 1: Add the minimal pure palette resolver**

Add:

```javascript
export const readerSurfaceBackCoverPalette = (settings, coverTint) => {
  const theme = readerThemePalette(settings?.theme)
  const base = readerReadableCoverTintChannels(coverTint) || theme.background
  return {
    base,
    highlight: readerMixedColorChannels(base, theme.background, 0.26),
    middle: readerMixedColorChannels(base, theme.foreground, 0.04),
    edge: readerMixedColorChannels(base, theme.foreground, 0.14),
    outer: readerMixedColorChannels(base, theme.foreground, 0.30),
  }
}
```

Make `readerSurfaceBackCoverBackground()` call it once. Keep portrait and landscape gradient stop placement separate, but use only the returned colors.

- [ ] **Step 2: Run GREEN verification**

Run the two Task 1 commands and `node --check composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`.

Expected: all pass.

- [ ] **Step 3: Commit the implementation**

```powershell
git add composeApp/src/androidMain/assets/reader/navic-reader-helpers.js
git commit -m "fix: preserve reader cover tint in landscape"
```

### Task 3: Lock the reader foreground contract for native controls

**Files:**
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/reader/ReaderThemeForegroundColorTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderKomikkuBackboneResetTest.kt`

- [ ] **Step 1: Write the failing color mapping test**

Assert the exact six reader palette foregrounds and Light fallback:

```kotlin
assertEquals(Color(0xFF1D1B18), readerThemeForegroundColor(ReaderLightTheme))
assertEquals(Color(0xFF2B2118), readerThemeForegroundColor(ReaderSepiaTheme))
assertEquals(Color(0xFF261B10), readerThemeForegroundColor(ReaderAgedPaperTheme))
assertEquals(Color(0xFFECE7F6), readerThemeForegroundColor(ReaderDuskTheme))
assertEquals(Color(0xFFF2F0EA), readerThemeForegroundColor(ReaderDarkTheme))
assertEquals(Color(0xFFF3F3F3), readerThemeForegroundColor(ReaderBlackTheme))
assertEquals(Color(0xFF1D1B18), readerThemeForegroundColor("unknown"))
```

- [ ] **Step 2: Replace stale source expectations**

Require `KomikkuWhispersyncPlaybackControl` to accept `readerTheme`, call `readerThemeForegroundColor(readerTheme).copy(alpha = 0.86f)`, and forbid the old `onSurface`/`0.42f` path. Preserve source guards for the 48 dp target, 22 dp glyph, shared slash color, and absence of a chrome container.

- [ ] **Step 3: Run RED verification**

```powershell
./gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
./gradlew.bat --no-daemon :composeApp:testAndroid --tests "paige.navic.ui.screens.reader.ReaderThemeForegroundColorTest"
```

Expected: missing resolver/signature and stale `0.42f` production path failures.

- [ ] **Step 4: Commit the failing tests**

```powershell
git add composeApp/src/commonTest/kotlin/paige/navic/ui/screens/reader/ReaderThemeForegroundColorTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeCommonChromeTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderKomikkuBackboneResetTest.kt
git commit -m "test: lock reader themed Whispersync control"
```

### Task 4: Implement the native reader foreground resolver

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderThemeForegroundColor.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`

- [ ] **Step 1: Implement the pure mapping**

Create `readerThemeForegroundColor(theme: String?): Color`, normalize with `normalizedReaderTheme`, and map the six constants to the exact colors in the specification.

- [ ] **Step 2: Thread the active theme to the control**

Pass `controllerState.chrome.settings.theme` from `KomikkuComposeOverlay` to `KomikkuWhispersyncPlaybackControl`. Resolve one `glyphColor` at `0.86f`; use it for the icon and slash.

- [ ] **Step 3: Run GREEN verification**

Run the Task 3 commands. Expected: all pass.

- [ ] **Step 4: Commit the implementation**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderThemeForegroundColor.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt
git commit -m "fix: theme the reader Whispersync control"
```

### Task 5: Code-level verification

**Files:**
- Verify only

- [ ] **Step 1: Run focused checks**

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-helpers.js
node tools/reader-harness/src/run-reader-harness.mjs --mode back-cover-tint-logic
./gradlew.bat --no-daemon :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderRuntimePaperSurfaceTest" --tests "paige.navic.reader.ReaderRuntimeCommonChromeTest" --tests "paige.navic.reader.ReaderKomikkuBackboneResetTest"
./gradlew.bat --no-daemon :composeApp:testAndroid --tests "paige.navic.ui.screens.reader.ReaderThemeForegroundColorTest"
git diff --check
```

- [ ] **Step 2: Run the full reader host gate**

```powershell
./gradlew.bat --no-daemon :composeApp:testAndroidHostTest
```

- [ ] **Step 3: Commit any verification-only corrections**

Do not amend behavior commits after their RED/GREEN evidence. Commit only required test or formatting corrections separately.

### Task 6: Readerdev emulator validation

**Files:**
- Runtime verification only

- [ ] **Step 1: Prove emulator ownership and bounds**

Use `adb devices -l`, `adb -s emulator-5554 shell wm size`, and `dumpsys window` to prove the readerdev instance and true portrait/landscape app bounds. Do not start a second emulator for the same AVD.

- [ ] **Step 2: Build and install readerdev**

Use the repository's established readerdev Gradle/install task. Confirm the installed package/version before opening Alcatraz.

- [ ] **Step 3: Capture portrait evidence**

Open Alcatraz in Sepia, show the thin right cover reveal and headset in enabled/disabled states, and capture a screenshot.

- [ ] **Step 4: Capture landscape evidence**

Rotate to true wide landscape spread, confirm both outer reveals retain the same green/blue family, and capture a screenshot.

- [ ] **Step 5: Compare and record evidence**

Confirm no page geometry, reveal width, paper texture, gutter, cover page, or Whispersync behavior regression.

### Task 7: Integrate and publish

**Files:**
- Version/release files determined from current repository release convention

- [ ] **Step 1: Fetch and reconcile current master**

Fetch `fork/master`. If master advanced, merge it into this branch and rerun Tasks 5 and 6. Do not overwrite unrelated master worktree edits.

- [ ] **Step 2: Merge the verified branch into master**

Use a non-destructive merge from the authoritative master worktree after confirming its active dirty files are either committed by their owner or safely isolated.

- [ ] **Step 3: Prepare the next theta release**

Bump to the next sequential theta version, commit release metadata, push master and tag, and build the signed release APK.

- [ ] **Step 4: Verify publication**

Confirm the GitHub release tag, uploaded `Navic.apk`, release commit, and APK SHA-256. Mark the goal complete only after these checks succeed.
