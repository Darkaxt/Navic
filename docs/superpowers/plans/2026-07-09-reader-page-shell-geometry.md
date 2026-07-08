# Reader Page Shell Geometry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the reader page-shell geometry and cover-backdrop design from `docs/superpowers/specs/2026-07-08-reader-page-shell-geometry-design.md` without repeating the earlier overlay-only failures.

**Architecture:** Keep one geometry source of truth for page rectangles, content rectangles, gutter, foreground cover, diffuse cover backdrop, and back-cover plane. The static HTML prototype is the visual contract; production code must not move past each stage until its prototype, source tests, readerdev/emulator probes, and physical-device screenshots pass.

**Tech Stack:** Kotlin Multiplatform/Compose settings UI, Android WebView reader assets, Foliate runtime JS, Android host source tests, readerdev APK, ADB smoke scripts, static HTML prototype screenshots.

---

## Current State

- The design spec is tracked at `docs/superpowers/specs/2026-07-08-reader-page-shell-geometry-design.md`.
- The exploratory prototype currently lives outside the repo at `C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype`.
- `v1.0.11-theta79` contains an initial shell-bounds/runtime geometry slice, but it is not the full spec:
  - it does not provide a repo-tracked accepted prototype,
  - it does not prove cover/back-cover mode with a tinted back-cover plane,
  - it does not prove final text centering against the visual page rects on physical tablet screenshots.

## File Map

- Create: `docs/superpowers/prototypes/reader-page-shell/index.html`
  - Self-contained static prototype for spread, portrait, and cover modes.
- Create: `docs/superpowers/prototypes/reader-page-shell/assets/*`
  - Minimal accepted source textures copied from the current prototype, not Chrome profiles or generated cache folders.
- Create: `docs/superpowers/prototypes/reader-page-shell/README.md`
  - How to render screenshots and what must be accepted before production work continues.
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`
  - Source-level guards for geometry, content-rect consumption, and cover slots.
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`
  - Layer/toggle guards for paper texture, edges, gutter, stains, and prototype references.
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
  - Geometry helper, visual-layer builders, cover/back-cover slots.
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
  - Renderer/content viewport constraints and readerdev diagnostics.
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
  - Application of geometry to layers and content documents after viewport changes.
- Modify: `scripts/adb-reader-smoke.ps1`
  - Add shell-geometry capture/probe requirements if current probes are missing a mode.
- Modify: `scripts/adb-reader-komikku-matrix.ps1`
  - Add named shell-geometry checks for spread, portrait, and cover mode.
- Test/capture output: `captures/reader-page-shell-geometry/*`
  - Local evidence only. Do not commit generated screenshots unless the final reviewer explicitly asks for tracked evidence images.

## Stage 1: Repo-Tracked Static Prototype

**Files:**
- Create: `docs/superpowers/prototypes/reader-page-shell/index.html`
- Create: `docs/superpowers/prototypes/reader-page-shell/README.md`
- Create: `docs/superpowers/prototypes/reader-page-shell/assets/`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Add the prototype source guard test**

Add a test in `ReaderRuntimePaperSurfaceTest.kt`:

```kotlin
@Test
fun readerPageShellPrototypeIsTrackedAndCaptureFriendly() {
    val root = repoRoot()
    val prototype = root.resolve("docs/superpowers/prototypes/reader-page-shell/index.html")
    val readme = root.resolve("docs/superpowers/prototypes/reader-page-shell/README.md")
    assertTrue(prototype.isFile, "Reader page-shell prototype must be tracked before production shell changes continue.")
    assertTrue(readme.isFile, "Reader page-shell prototype must document capture and acceptance steps.")

    val html = prototype.readText()
    assertContains(html, "data-mode=\"spread\"")
    assertContains(html, "data-mode=\"portrait\"")
    assertContains(html, "data-mode=\"cover\"")
    assertContains(html, "data-capture")
    assertContains(html, "paper-texture-toggle")
    assertContains(html, "edge-width")
    assertContains(html, "back-cover-plane")
    assertContains(html, "foreground-cover")
    assertContains(html, "diffuse-cover-backdrop")

    assertFalse(
        html.contains("chrome-profile"),
        "The tracked prototype must not depend on local Chrome profile folders."
    )
}
```

- [ ] **Step 2: Run the guard and confirm it fails**

Run:

```powershell
.\gradlew :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerPageShellPrototypeIsTrackedAndCaptureFriendly
```

Expected: FAIL because the tracked prototype does not exist yet.

- [ ] **Step 3: Add the minimal tracked prototype**

Copy only the intentional assets from the temporary prototype:

```powershell
New-Item -ItemType Directory -Force docs\superpowers\prototypes\reader-page-shell\assets
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\paper-base-05.jpg" docs\superpowers\prototypes\reader-page-shell\assets\paper-base-05.jpg
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\sample-cover.jpg" docs\superpowers\prototypes\reader-page-shell\assets\sample-cover.jpg
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\edge-frame-wear.png" docs\superpowers\prototypes\reader-page-shell\assets\edge-frame-wear.png
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\edge-frame-rim.png" docs\superpowers\prototypes\reader-page-shell\assets\edge-frame-rim.png
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\gutter-shadow.png" docs\superpowers\prototypes\reader-page-shell\assets\gutter-shadow.png
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\gutter-highlight.png" docs\superpowers\prototypes\reader-page-shell\assets\gutter-highlight.png
Copy-Item "C:\Users\darka\Documents\Projects\Android\.codex-temp\navic-reader-paper-prototype\assets\paper-stains.png" docs\superpowers\prototypes\reader-page-shell\assets\paper-stains.png
```

Create `index.html` with these structural requirements:

```html
<main id="prototype" data-mode="spread" data-paper-texture="on" data-page-edges="on" data-paper-stains="on" data-cover-backdrop="on">
  <section class="mode mode-spread" data-mode="spread" data-capture="spread">
    <div class="book-shell spread-shell">
      <article class="page left-page"><div class="page-content"></div></article>
      <div class="gutter" aria-hidden="true"></div>
      <article class="page right-page"><div class="page-content"></div></article>
    </div>
  </section>
  <section class="mode mode-portrait" data-mode="portrait" data-capture="portrait">
    <div class="book-shell portrait-shell">
      <div class="portrait-gutter-hint" aria-hidden="true"></div>
      <article class="page single-page"><div class="page-content"></div></article>
    </div>
  </section>
  <section class="mode mode-cover" data-mode="cover" data-capture="cover">
    <div class="cover-shell">
      <div class="diffuse-cover-backdrop" aria-hidden="true"></div>
      <div class="back-cover-plane" aria-hidden="true"></div>
      <img class="foreground-cover" src="assets/sample-cover.jpg" alt="Sample cover" />
    </div>
  </section>
  <aside class="prototype-controls">
    <label><input id="paper-texture-toggle" type="checkbox" checked /> Paper texture</label>
    <label><input id="page-edges-toggle" type="checkbox" checked /> Page edges</label>
    <label><input id="paper-stains-toggle" type="checkbox" checked /> Paper stains</label>
    <label><input id="cover-backdrop-toggle" type="checkbox" checked /> Cover backdrop</label>
    <label>Edge width <input id="edge-width" type="range" min="0" max="1" step="0.01" value="0.75" /></label>
  </aside>
</main>
```

The CSS must:

- reserve a real `--gutter-width`,
- define separate `--edge-width` and `--edge-opacity`,
- use `background-image: url("assets/paper-base-05.jpg")` for paper texture,
- hide `.prototype-controls` when `document.documentElement.dataset.capture === "true"`,
- make `.foreground-cover { object-fit: contain; }`,
- place `.foreground-cover` above `.diffuse-cover-backdrop` and `.back-cover-plane`.

- [ ] **Step 4: Document prototype capture**

Create `README.md` with:

```markdown
# Reader Page Shell Prototype

This prototype is the visual acceptance gate for `docs/superpowers/specs/2026-07-08-reader-page-shell-geometry-design.md`.

## Capture

Use Chromium/Chrome with controls hidden:

```powershell
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=2960,1848 --screenshot=captures\reader-page-shell-geometry\prototype-spread.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=spread&capture=1"
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=1848,2960 --screenshot=captures\reader-page-shell-geometry\prototype-portrait.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=portrait&capture=1"
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=2960,1848 --screenshot=captures\reader-page-shell-geometry\prototype-cover.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=cover&capture=1"
```

## Acceptance

- Spread page content is centered inside each visual page, not the raw viewport half.
- Portrait mode reads as the right-side page with a left gutter hint, not a notepad.
- Cover mode keeps the foreground cover fully visible, shows a diffuse backdrop, and shows a simple tinted back-cover plane.
- Paper texture, page edges, stains, gutter, and cover backdrop can be independently disabled.
```

- [ ] **Step 5: Run the guard again**

Run:

```powershell
.\gradlew :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest.readerPageShellPrototypeIsTrackedAndCaptureFriendly
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add docs/superpowers/prototypes/reader-page-shell composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt
git commit -m "test: track reader page shell prototype gate"
```

## Stage 2: Prototype Visual Acceptance

**Files:**
- Modify: `docs/superpowers/prototypes/reader-page-shell/index.html`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Add source guards for the visual rules**

Add a test:

```kotlin
@Test
fun readerPageShellPrototypeUsesSharedGeometryVisualRules() {
    val html = repoRoot()
        .resolve("docs/superpowers/prototypes/reader-page-shell/index.html")
        .readText()

    assertContains(html, "--gutter-width")
    assertContains(html, "--edge-width")
    assertContains(html, "--edge-opacity")
    assertContains(html, "object-fit: contain")
    assertContains(html, ".back-cover-plane")
    assertContains(html, ".diffuse-cover-backdrop")
    assertTrue(
        html.indexOf("diffuse-cover-backdrop") < html.indexOf("foreground-cover"),
        "Cover backdrop must be declared below the foreground cover in the rendered stack."
    )
    assertFalse(
        html.contains("border:") && html.contains("coffee"),
        "Edge wear must be represented as a narrow tunable wear layer, not a broad border stain."
    )
}
```

- [ ] **Step 2: Tune the prototype**

Use `edge-width=0.75` as the starting default from the prior visual iteration. Keep the paper rectangle large; do not shrink the page area to hide edge artifacts.

The final CSS must include:

```css
:root {
  --edge-width: 0.75;
  --edge-opacity: 0.58;
  --gutter-width: clamp(18px, 1.7vw, 42px);
  --page-radius: clamp(10px, 0.9vw, 22px);
}
```

- [ ] **Step 3: Capture clean prototype screenshots**

Run:

```powershell
New-Item -ItemType Directory -Force captures\reader-page-shell-geometry
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=2960,1848 --screenshot=captures\reader-page-shell-geometry\prototype-spread.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=spread&capture=1"
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=1848,2960 --screenshot=captures\reader-page-shell-geometry\prototype-portrait.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=portrait&capture=1"
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=2960,1848 --screenshot=captures\reader-page-shell-geometry\prototype-cover.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=cover&capture=1"
```

Expected:

- no controls visible,
- paper texture visible but not noisy,
- edge burn reads as worn edge, not wide coffee stain,
- cover foreground is fully visible,
- cover mode includes the tinted back-cover plane.

- [ ] **Step 4: Commit after visual acceptance**

Only commit after screenshots are reviewed:

```powershell
git add docs/superpowers/prototypes/reader-page-shell composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt
git commit -m "docs: accept reader page shell prototype"
```

## Stage 3: Geometry Contract And Runtime Guards

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`

- [ ] **Step 1: Add failing geometry tests**

Add tests covering:

```kotlin
@Test
fun shellGeometryReservesGutterBeforeContentRects() {
    val helper = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
    assertContains(helper, "export const readerPageShellGeometry")
    assertContains(helper, "gutterRect")
    assertContains(helper, "contentRects")
    assertTrue(
        helper.indexOf("gutterRect") < helper.indexOf("contentRects"),
        "Content rects must be derived after the gutter reservation exists."
    )
}

@Test
fun viewportConstrainsRendererToShellAndExposesDiagnostics() {
    val viewport = readerAssetRoot().resolve("navic-reader-viewport.js").readText()
    assertContains(viewport, "readerPageShellGeometryForViewport")
    assertContains(viewport, "renderer.dataset.navicReaderShellContentRects")
    assertContains(viewport, "reader-shell-geometry")
}
```

- [ ] **Step 2: Implement deterministic geometry**

In `navic-reader-helpers.js`, keep `readerPageShellGeometry(...)` pure:

```js
export const readerPageShellGeometry = ({
  width = 0,
  height = 0,
  flowMode = null,
  coverMode = false,
  settings = {},
} = {}) => {
  // no DOM reads, no image decoding, no network, no async
}
```

The implementation must return:

- `viewportRect`
- `shellRect`
- `pageRects`
- `contentRects`
- `gutterRect` for spread only
- `edgeInsets.inner`
- `cover.backdropRect`
- `cover.foregroundRect`
- `cover.backCoverRect`

- [ ] **Step 3: Wire viewport diagnostics**

In `navic-reader-viewport.js`, after computing geometry:

```js
this.readerPageShellGeometry = shellGeometry
readerRoot.dataset.navicReaderShellGeometry = JSON.stringify(
  readerShellGeometryDiagnosticState(shellGeometry, 'reader-shell-geometry')
)
renderer.dataset.navicReaderShellContentRects = JSON.stringify(shellGeometry.contentRects)
```

- [ ] **Step 4: Verify**

Run:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-viewport.js
.\gradlew :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellGeometryTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/androidMain/assets/reader/navic-reader-helpers.js composeApp/src/androidMain/assets/reader/navic-reader-viewport.js composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt
git commit -m "fix: unify reader page shell geometry"
```

## Stage 4: Visual Layers Consume Geometry

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt`

- [ ] **Step 1: Add layer source guards**

Add tests asserting:

```kotlin
@Test
fun paperLayersConsumePageShellGeometry() {
    val helper = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
    assertContains(helper, "readerPageShellRectStyle")
    assertContains(helper, "geometry.pageRects")
    assertContains(helper, "geometry.gutterRect")
    assertContains(helper, "settings?.paperTextureEnabled === false")
    assertContains(helper, "settings?.pageEdgesEnabled === false")
    assertContains(helper, "settings?.paperStainsEnabled === false")
}
```

- [ ] **Step 2: Wire each visual layer**

Use `ReaderPageShellGeometry` for:

- paper base texture page rects,
- outer edge wear,
- center gutter shadow/highlight,
- stains,
- moving-page texture surfaces.

Do not infer visual page bounds from `window.innerWidth`, CSS columns, or raw renderer bounds inside individual layer builders.

- [ ] **Step 3: Verify**

Run:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
.\gradlew :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add composeApp/src/androidMain/assets/reader/navic-reader-helpers.js composeApp/src/androidMain/assets/reader/navic-reader-appearance.js composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimePaperSurfaceTest.kt
git commit -m "fix: render reader paper layers from shell geometry"
```

## Stage 5: Content Layout Consumes Geometry

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-appearance.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-viewport.js`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`

- [ ] **Step 1: Add the content-centering guard**

Add a test:

```kotlin
@Test
fun readerContentDocumentsReceiveShellContentRectVariables() {
    val helper = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
    val appearance = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
    assertContains(helper, "--navic-reader-shell-content-left")
    assertContains(helper, "--navic-reader-shell-content-width")
    assertContains(appearance, "applyReaderShellContentGeometry")
    assertContains(appearance, "contentEntries")
}
```

- [ ] **Step 2: Apply content rect variables**

Every loaded content document should receive CSS variables for the active content rect:

```js
root.style.setProperty('--navic-reader-shell-content-left', `${Math.round(contentRect.left)}px`)
root.style.setProperty('--navic-reader-shell-content-top', `${Math.round(contentRect.top)}px`)
root.style.setProperty('--navic-reader-shell-content-width', `${Math.round(contentRect.width)}px`)
root.style.setProperty('--navic-reader-shell-content-height', `${Math.round(contentRect.height)}px`)
```

Then use these variables through the stable Foliate hook identified by readerdev. Do not fake success by only painting the gutter overlay.

- [ ] **Step 3: Verify with readerdev probe**

Run readerdev and capture diagnostics:

```powershell
.\gradlew :androidApp:assembleReaderDev
.\scripts\install-reader-dev.ps1 -Package darkaxt.navic.readerdev -SkipBuild
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -ReaderDevtoolsProbe page-box -CaptureDir captures\reader-page-shell-geometry\readerdev-spread
```

Expected:

- `reader-devtools-probe.json` includes `contentRects`.
- Screenshot shows content centered inside each visual page.
- No `Reader console ERROR`.

- [ ] **Step 4: Commit**

```powershell
git add composeApp/src/androidMain/assets/reader composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt
git commit -m "fix: center reader content inside shell pages"
```

## Stage 6: Cover And Back-Cover Mode

**Files:**
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-shell-cover.js`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/KomikkuReaderNativeFrameHost.android.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeImageLinkTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeShellGeometryTest.kt`

- [ ] **Step 1: Add cover-mode guards**

Add tests asserting the code has distinct cover slots:

```kotlin
@Test
fun coverModeHasDistinctBackdropBackCoverAndForegroundSlots() {
    val helper = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
    assertContains(helper, "diffuse")
    assertContains(helper, "backCoverRect")
    assertContains(helper, "foregroundRect")
    assertContains(helper, "object-fit")
    assertContains(helper, "contain")
}
```

- [ ] **Step 2: Implement cover mode**

Rules:

- foreground cover is `contain`,
- diffuse backdrop is `cover` + blur + dim,
- back-cover plane is tinted from dominant color when available,
- fallback tint is sepia/brown,
- no shell clipping may crop the foreground.

- [ ] **Step 3: Verify in readerdev cover mode**

Run:

```powershell
.\scripts\adb-reader-smoke.ps1 -Package darkaxt.navic.readerdev -ReaderDevtoolsProbe page-box -CaptureDir captures\reader-page-shell-geometry\readerdev-cover -RequireNativeCoverVisible
```

Expected:

- screenshot shows foreground cover fully visible,
- diffuse backdrop fills black areas,
- tinted back-cover plane visible where expected,
- no foreground cover crop.

- [ ] **Step 4: Commit**

```powershell
git add composeApp/src/androidMain/assets/reader composeApp/src/androidMain/kotlin/paige/navic/reader composeApp/src/androidHostTest/kotlin/paige/navic/reader
git commit -m "fix: complete reader cover backdrop shell"
```

## Stage 7: Emulator Matrix

**Files:**
- Modify: `scripts/adb-reader-smoke.ps1`
- Modify: `scripts/adb-reader-komikku-matrix.ps1`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderKomikkuBackboneResetTest.kt`

- [ ] **Step 1: Add named checks**

The matrix needs these named checks:

- `shell-geometry-spread`
- `shell-geometry-portrait`
- `shell-geometry-cover`
- `shell-geometry-toggle-paper-off`
- `shell-geometry-toggle-edges-off`
- `shell-geometry-toggle-stains-off`
- `shell-geometry-toggle-cover-backdrop-off`

- [ ] **Step 2: Verify matrix source guards**

Run:

```powershell
.\gradlew :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderKomikkuBackboneResetTest
```

- [ ] **Step 3: Run the emulator matrix**

Run:

```powershell
.\scripts\adb-reader-komikku-matrix.ps1 -Package darkaxt.navic.readerdev -CaptureRoot captures\reader-page-shell-geometry\matrix-current
```

Expected:

- all named shell checks pass,
- screenshots are present,
- readerdev logs include `reader-shell-geometry`,
- no console errors.

- [ ] **Step 4: Commit**

```powershell
git add scripts composeApp/src/androidHostTest/kotlin/paige/navic/reader
git commit -m "test: add reader shell geometry adb matrix"
```

## Stage 8: Physical Tablet Gate

**Files:**
- No required source files unless this gate exposes a bug.
- Evidence: `captures/reader-page-shell-geometry/tablet-*`

- [ ] **Step 1: Confirm device**

Run:

```powershell
adb devices -l
```

Expected: Tab S9 Ultra or user-selected physical device attached.

- [ ] **Step 2: Install candidate**

Use readerdev or release-candidate package depending on stage:

```powershell
.\scripts\install-reader-dev.ps1 -Package darkaxt.navic.readerdev -SkipBuild
```

- [ ] **Step 3: Capture the required physical screenshots**

Capture:

- landscape spread,
- portrait page,
- cover/back-cover,
- paper off,
- edges off,
- stains off,
- cover backdrop off.

Use:

```powershell
adb exec-out screencap -p > captures\reader-page-shell-geometry\tablet-landscape-spread.png
```

Repeat with mode-specific filenames.

- [ ] **Step 4: Validate**

Reject the build if:

- text is centered against the raw viewport rather than page rect,
- center gutter overlaps or steals text space,
- cover foreground is cropped,
- black cutoffs remain behind cover,
- paper/edge/stain toggles do not visually isolate layers,
- low-resolution banding is obvious on Tab S9 Ultra.

## Stage 9: Release

**Files:**
- Modify version/release metadata using the repo’s existing release script/process.

- [ ] **Step 1: Final tests**

Run focused tests, not repeated full Gradle loops after every tiny edit:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader-helpers.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-appearance.js
node --check composeApp\src\androidMain\assets\reader\navic-reader-viewport.js
.\gradlew :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeShellGeometryTest --tests paige.navic.reader.ReaderRuntimePaperSurfaceTest --tests paige.navic.reader.ReaderRuntimeImageLinkTest
```

- [ ] **Step 2: Sync**

```powershell
git fetch fork --prune
git fetch origin --prune
git status --short --branch
```

Resolve conflicts before versioning.

- [ ] **Step 3: Version**

Use the next theta subversion from the current tag. Do not skip Greek-letter subversions.

- [ ] **Step 4: Publish**

Run the existing release script only after Stages 1-8 are green:

```powershell
.\scripts\publish-github-release.ps1 -AllowPublicRelease -ReleaseReadinessNote "Reader page shell geometry accepted in prototype, readerdev/emulator, and physical tablet validation."
```

- [ ] **Step 5: Verify release**

Confirm:

- GitHub Actions release build passed,
- release contains `Navic.apk`,
- APK version matches the tag,
- changelog mentions prototype gate, shell geometry, content centering, cover/back-cover mode, and tablet validation.

## Self-Review Checklist

- [ ] The plan starts with the static prototype and does not permit production reader changes before visual acceptance.
- [ ] Cover mode includes foreground cover, diffuse backdrop, and simple tinted back-cover plane.
- [ ] Text layout is required to consume content rects, not only visual overlays.
- [ ] Readerdev, emulator, and physical tablet gates are explicit.
- [ ] No runtime timeout/cancellation behavior is introduced.
- [ ] Public release is blocked until the final acceptance gates pass.
