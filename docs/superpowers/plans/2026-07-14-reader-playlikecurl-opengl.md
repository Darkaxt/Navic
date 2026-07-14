# Reader PlayLikeCurl OpenGL Implementation Plan (Superseded Execution Order)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Superseded by `2026-07-14-reader-playlikecurl-faithful-port.md`. Tasks 2-4 and their recorded Gate A are historical evidence of the failed prototype, not accepted fidelity progress.

**Goal:** Replace Navic's current Canvas page turn with a faithful modern OpenGL port of PlayLikeCurl, backed by an asynchronous configurable-resolution page raster cache that prevents visible capture and page-settlement blinking.

**Architecture correction:** Preserve Foliate as the sole final layout and navigation authority, but first port PlayLikeCurl's complete three-page model, projection, draw order, gesture mapping, settlement, and texture-identity lifecycle into GLES2 without Navic-specific geometry changes. Only after reference-demo parity may a narrow adapter feed dynamic Navic page textures from the managed raster cache. Keep exact target relocation and settlement shielding outside the renderer; remove failed Canvas/custom-curl production paths only after cutover.

**Tech Stack:** Kotlin Multiplatform, Android OpenGL ES 2.0, GLSurfaceView, Android Bitmap/PNG, Foliate JavaScript, Compose Multiplatform, Kotlin test, Android host tests, Node reader harness, Gradle, ReaderDev emulator, ADB.

**Release rule:** Debug and ReaderDev APKs are allowed during implementation. Do not publish a public release until Tasks 10-12 pass. Expensive emulator runs occur only at Tasks 4, 10, and 12, after meaningful feature-complete milestones.

---

## File Structure

### New production files

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGeometry.android.kt`
  - faithful PlayLikeCurl constants, forward/backward vertex generation, indices, and texture coordinates
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlView.android.kt`
  - persistent `GLSurfaceView`, lifecycle events, transparency, and input-independent attachment
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRenderer.android.kt`
  - GLES2 programs, preallocated buffers, texture upload/reuse, active-leaf clipping, and frame drawing
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageRasterPolicy.kt`
  - quality enum, scale conversion, cache profile identity, preparation priority, and calibration decision
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterManifest.android.kt`
  - versioned manifest models and atomic persistence
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCache.android.kt`
  - raster key, memory LRU, disk LRU, atomic files, decode, invalidation, and managed cleanup
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterScheduler.android.kt`
  - single-flight generation, priority queue, profile tokens, calibration, and chapter precomputation
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt`
  - cover-backed determinate preparation state
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt`
  - pure readiness, progress, and cache-outrun policy

### New tests

- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGeometryTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRendererSourceTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageRasterPolicyTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCacheTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterSchedulerTest.kt`
- `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPagePreparationPolicyTest.kt`

### Existing production files modified

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderManagedStorage.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/domain/manager/StorageManager.android.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/StorageManager.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPreferenceSettings.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageSlideCoordinator.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsModePages.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbookReaderSettingOptions.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbooksScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchEbookRows.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchRows.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/DataStorageScreen.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`

### Files removed after final cutover

- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnWaveGeometry.android.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideViewSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnSlidePerformanceSourceTest.kt`
- `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnWaveGeometryTest.kt`

### Attribution files modified

- `THIRD_PARTY.md`
- `composeApp/src/commonMain/composeResources/files/acknowledgements.json`
- `third_party/licenses/playlikecurl.txt`

---

### Task 1: Lock Reference Provenance And Migration Guards

**Files:**
- Create: `third_party/licenses/playlikecurl.txt`
- Modify: `THIRD_PARTY.md`
- Modify: `composeApp/src/commonMain/composeResources/files/acknowledgements.json`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ThirdPartyAttributionSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDragAnimationMigrationSourceTest.kt`

- [x] **Step 1: Write failing attribution and migration guard tests**

Add assertions that the attribution catalog contains `PlayLikeCurl`, its repository URL, MIT license, and the local license file. Add a source guard that permits the historical `canvas` preference while requiring its production target to be the new OpenGL renderer after cutover.

```kotlin
@Test
fun playLikeCurlAttributionIsPackaged() {
    assertTrue(thirdPartyMarkdown.contains("PlayLikeCurl"))
    assertTrue(acknowledgements.contains("https://github.com/karankalsi/PlayLikeCurl"))
    assertTrue(licenseFiles.contains("playlikecurl.txt"))
}
```

- [x] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ThirdPartyAttributionSourceTest" --tests "paige.navic.reader.ReaderDragAnimationMigrationSourceTest"
```

Expected: FAIL because the PlayLikeCurl attribution and OpenGL migration guard do not exist.

- [x] **Step 3: Add attribution and migration contract**

Copy the exact MIT notice from the audited reference into `third_party/licenses/playlikecurl.txt`. Record that Navic ports geometry formulas but not the obsolete GLES1 activity or lifecycle code.

Lock these mappings in the migration test:

```text
none -> none
canvas -> animated OpenGL mode
curl -> animated OpenGL mode
standard -> none
```

- [x] **Step 4: Run attribution verification and focused tests**

Run:

```powershell
.\scripts\verify-third-party-attributions.ps1
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ThirdPartyAttributionSourceTest" --tests "paige.navic.reader.ReaderDragAnimationMigrationSourceTest"
```

Expected: both commands succeed.

- [x] **Step 5: Commit**

```powershell
git add THIRD_PARTY.md third_party/licenses/playlikecurl.txt composeApp/src/commonMain/composeResources/files/acknowledgements.json composeApp/src/androidHostTest/kotlin/paige/navic/reader/ThirdPartyAttributionSourceTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDragAnimationMigrationSourceTest.kt
git commit -m "docs(reader): attribute PlayLikeCurl geometry port"
```

### Task 2: Port And Test The Reference Geometry

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGeometry.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGeometryTest.kt`

- [x] **Step 1: Write failing geometry tests**

Define tests for the locked constants, 26 x 26 mesh, UV bounds, finite coordinates, exact endpoints, and distinct forward/backward samples.

```kotlin
@Test
fun referenceMeshUsesLockedPlayLikeCurlDimensions() {
    val mesh = ReaderPageCurlGeometry.createReferenceMesh()
    assertEquals(25, mesh.grid)
    assertEquals(26 * 26, mesh.vertexCount)
    assertEquals(0.18f, mesh.radius)
}

@Test
fun backwardGeometryIsNotForwardGeometryPlayedInReverse() {
    val forward = ReaderPageCurlGeometry.forward(progress = 0.5f)
    val backward = ReaderPageCurlGeometry.backward(progress = 0.5f)
    assertNotEquals(forward.positions.toList(), backward.positions.toList())
}
```

- [x] **Step 2: Run the geometry test and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageCurlGeometryTest"
```

Expected: FAIL because `ReaderPageCurlGeometry` is missing.

- [x] **Step 3: Implement the immutable mesh contract**

Create these public boundaries:

```kotlin
internal object ReaderPageCurlGeometry {
    const val Grid = 25
    const val Radius = 0.18f
    const val RightEndpoint = -1.25f
    const val LeftEndpoint = 25f

    fun createReferenceMesh(): ReaderPageCurlMesh
    fun forward(progress: Float, target: ReaderPageCurlMesh = createReferenceMesh()): ReaderPageCurlMesh
    fun backward(progress: Float, target: ReaderPageCurlMesh = createReferenceMesh()): ReaderPageCurlMesh
}
```

Port the `PageFront`, `PageLeft`, and stationary `PageRight` equations directly. Clamp only normalized input progress; do not alter the equations for visual taste.

- [x] **Step 4: Add sampled parity fixtures**

Record reference vertex samples at progress `0.0`, `0.25`, `0.5`, `0.75`, and `1.0` from the audited Java implementation. Assert position and UV values within `0.0001f`.

- [x] **Step 5: Run geometry tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageCurlGeometryTest"
```

Expected: PASS.

- [x] **Step 6: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGeometry.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGeometryTest.kt
git commit -m "feat(reader): port PlayLikeCurl geometry"
```

### Task 3: Build The Persistent GLES2 Renderer

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlView.android.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRenderer.android.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlDiagnosticTextureFactory.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRendererSourceTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnSlidePerformanceSourceTest.kt`

- [x] **Step 1: Write failing renderer source guards**

Require GLES2, persistent dirty rendering, preallocated buffers, dynamic textures, no asset texture loader, no per-frame bitmap allocation, and no navigation calls.

```kotlin
@Test
fun rendererIsPersistentGles2AndPassive() {
    assertContains(viewSource, "setEGLContextClientVersion(2)")
    assertContains(viewSource, "RENDERMODE_WHEN_DIRTY")
    assertFalse(rendererSource.contains("Bitmap.createBitmap"))
    assertFalse(rendererSource.contains("goTo("))
    assertFalse(rendererSource.contains("renderer.next"))
}
```

- [x] **Step 2: Run the renderer guard and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageCurlGlRendererSourceTest"
```

Expected: FAIL because the renderer files are missing.

Execution evidence: the five source guards failed before the renderer files existed. The diagnostic checker-texture guard subsequently failed before the scoped ReaderDev harness was added.

- [x] **Step 3: Implement the persistent view**

Create a single surface with:

```kotlin
internal class ReaderPageCurlGlView(context: Context) : GLSurfaceView(context) {
    val pageRenderer = ReaderPageCurlGlRenderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(pageRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
        setZOrderOnTop(true)
    }
}
```

Keep it transparent until a complete texture set is committed.

- [x] **Step 4: Implement shaders and draw order**

Use one texture shader with position, UV, projection, and alpha. Draw:

```text
1. final or stationary full base
2. stationary companion leaf in landscape
3. underneath page clipped to active leaf
4. active PlayLikeCurl mesh
```

Use VBO/IBO or preallocated direct buffers. Texture uploads occur when bundle identity changes.

- [x] **Step 5: Add a deterministic renderer harness state**

Add an internal `ReaderPageTurnOpenGlPrototypeEnabled` branch in the controller's renderer factory. While this plan is in progress, that branch routes the existing prepared page bundle into `ReaderPageCurlGlView` but leaves the Canvas source files available for source comparison. The branch is removed in Task 9, where OpenGL becomes the only animated production renderer.

Expose a deterministic texture-set factory that can supply four generated checker/text bitmaps when reader diagnostics are enabled. With diagnostics disabled, the prototype renders the real current publication bundle. This makes Gate A inspectable in ReaderDev without adding a public setting or a second navigation screen.

- [x] **Step 6: Run renderer and geometry tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageCurlGlRendererSourceTest" --tests "paige.navic.ui.screens.reader.ReaderPageCurlGeometryTest"
```

Expected: PASS.

Execution evidence: the renderer and geometry tests passed, followed by a broader 99-test gate covering renderer, geometry, bundle, native bridge, performance, and destination settlement. The cancellation path now consumes the structured JS settlement state instead of treating a cancelled exact relocation as successful navigation.

- [x] **Step 7: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlView.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRenderer.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundle.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRendererSourceTest.kt
git commit -m "feat(reader): add persistent PlayLikeCurl GLES2 renderer"
```

### Task 4: Meaningful Visual Gate A - Geometry Parity

**Files:**
- Modify: `docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md`

- [x] **Step 1: Build the ReaderDev APK once**

Run:

```powershell
.\gradlew.bat --no-daemon :androidApp:assembleReaderDev
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Install the exact build and record its hash**

Run:

```powershell
Get-FileHash androidApp\build\outputs\apk\readerDev\Navic.apk -Algorithm SHA256
.\scripts\install-reader-dev.ps1 -Package darkaxt.navic.readerdev -NoBuild
```

Record the SHA256 in this task's evidence section before checking the emulator.

- [x] **Step 3: Capture the geometry harness matrix**

Open the exact Alcatraz ReaderDev publication through the existing reader intent seed and capture portrait and tablet-like landscape frames for:

- forward at 25%, 50%, and 75% drag progress
- backward at 25%, 50%, and 75% drag progress
- landscape active left leaf
- landscape active right leaf

Expected: the shape matches the PlayLikeCurl reference, one landscape leaf deforms, and backward is not a reversed forward animation.

- [x] **Step 4: Reject geometry changes that lack parity evidence**

If a frame differs from the reference formulas, correct Task 2 or Task 3 and rerun this gate. Do not add shadow, perspective, or smoothing to hide a mismatch.

- [x] **Step 5: Commit the gate evidence**

Add a concise evidence entry to the plan with APK SHA256, emulator profile, screenshot paths, and result.

```powershell
git add docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md
git commit -m "test(reader): record PlayLikeCurl geometry gate"
```

**Execution evidence (2026-07-14):**

- Exact APK: `androidApp/build/outputs/apk/readerDev/Navic.apk`
- SHA256: `5CC9250D4F006E9F2706C6D766ACCDBF5170C6886D2398C8D45C7A817E80C5E1`
- Installed package: `darkaxt.navic.readerdev`, version `v1.0.11-iota21` (`548`)
- Emulator: `NavicReaderLab`, `sdk_gphone64_x86_64`, 320 dpi override, portrait `1848x2960`, landscape `2960x1848`
- Publication: Alcatraz book `3959`, resource `/opds/books/3959/resources/ebook-3dfaf0ebad2e666212ba`
- Captures: `captures/reader-dev/playlikecurl-gate-a/portrait-forward-{25,50,75}.png`, `portrait-backward-{25,50,75}.png`, `landscape-forward-{25,50,75}.png`, and `landscape-backward-{25,50}.png`
- Result: portrait forward/backward use distinct PlayLikeCurl geometry; landscape forward deforms only the right leaf and backward deforms only the left leaf; the stationary source/destination leaves remain fixed.
- Integration blocker retained for Task 9: diagnostic labels expose mirrored texture orientation on the deforming face. Geometry parity passes, but production page textures must correct front/back UV orientation before release.

### Task 5: Add Configurable Page Bitmap Quality

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageRasterPolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageRasterPolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPreferenceSettings.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbookReaderSettingOptions.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbooksScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchEbookRows.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchRows.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsModePages.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`

- [ ] **Step 1: Write failing policy and preference tests**

```kotlin
@Test
fun bitmapQualityDefaultsToHalfResolution() {
    assertEquals(ReaderPageBitmapQuality.Balanced, normalizeReaderPageBitmapQuality(null))
    assertEquals(0.5f, ReaderPageBitmapQuality.Balanced.scale)
}

@Test
fun bitmapQualityExposesFourStableValues() {
    assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), ReaderPageBitmapQuality.entries.map { it.scale })
}
```

Extend reader preference tests to prove default/per-book JSON round-trip and invalid-value normalization.

- [ ] **Step 2: Run focused common tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:allTests --tests "paige.navic.reader.ReaderPageRasterPolicyTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest"
```

Expected: FAIL because the quality model and preference field are missing.

- [ ] **Step 3: Implement the stable quality enum**

```kotlin
enum class ReaderPageBitmapQuality(val persistedValue: String, val scale: Float) {
    Low("25", 0.25f),
    Balanced("50", 0.50f),
    High("75", 0.75f),
    Native("100", 1.00f),
}
```

Replace `ReaderPageTurnAnimationBitmapScale = 0.5f` with the resolved setting.

- [ ] **Step 4: Add settings surfaces**

Add `Page animation bitmap quality` with `25%`, `50%`, `75%`, and `100%` to global Ebooks settings, live reader settings, and settings search. Default to `50%`.

- [ ] **Step 5: Run focused settings and preference tests**

Run:

```powershell
.\gradlew.bat :composeApp:allTests --tests "paige.navic.reader.ReaderPageRasterPolicyTest" --tests "paige.navic.reader.ReaderPreferenceSettingsTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderDragAnimationMigrationSourceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src/commonMain composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt
git commit -m "feat(reader): make page raster quality configurable"
```

### Task 6: Add Managed Raster Cache And Atomic Manifest

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterManifest.android.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCache.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCacheTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderManagedStorage.android.kt`

- [ ] **Step 1: Write failing cache tests**

Test complete key separation, atomic publication, corrupt-file recovery, LRU eviction, quality-profile invalidation, and no symlinks.

```kotlin
@Test
fun qualityIsPartOfRasterIdentity() {
    assertNotEquals(key(quality = "50"), key(quality = "100"))
}

@Test
fun corruptRasterIsDeletedAndReportedAsMiss() {
    cache.writeInvalidBytes(testKey)
    assertNull(cache.read(testKey))
    assertFalse(cache.pathFor(testKey).exists())
}
```

- [ ] **Step 2: Run cache tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageRasterCacheTest"
```

Expected: FAIL because the cache is missing.

- [ ] **Step 3: Implement the complete raster key**

```kotlin
internal data class ReaderPageRasterKey(
    val publicationHash: String,
    val paginationHash: String,
    val spineIndex: Int,
    val hrefHash: String,
    val chapterPageIndex: Int,
    val visualPageOrdinal: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val layoutHash: String,
    val decorationHash: String,
    val quality: ReaderPageBitmapQuality,
    val schemaVersion: Int = 1,
)
```

Use a cryptographic digest of the serialized key as the filename.

- [ ] **Step 4: Implement atomic page and manifest writes**

Write PNG and manifest temporary files in the final directory, close them, then replace the final path atomically. Register the manifest entry only after the PNG is durable.

- [ ] **Step 5: Add bounded cache tiers**

Add a decoded bitmap LRU and disk LRU. Keep the disk byte limit internal and constant in this migration. Expose metrics but not a user setting for capacity.

- [ ] **Step 6: Integrate with managed reader storage**

Store rasters below `reader-page-rasters/v1`. Add cleanup methods that remove old schema/profile entries and temporary files without touching publication resources.

- [ ] **Step 7: Run cache tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageRasterCacheTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterManifest.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCache.android.kt composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderManagedStorage.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCacheTest.kt
git commit -m "feat(reader): add managed page raster cache"
```

### Task 7: Add Asynchronous Single-Flight Raster Scheduling

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterScheduler.android.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterSchedulerTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageRasterPolicy.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageRasterPolicyTest.kt`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt`

- [ ] **Step 1: Write failing scheduler tests**

Test single-flight ownership, profile-token rejection, priority ordering, complete-chapter versus rolling-window calibration, and no cancellation timeout.

```kotlin
@Test
fun duplicateRequestsShareOneGeneration() = runTest {
    val first = scheduler.request(key)
    val second = scheduler.request(key)
    assertSame(first, second)
    assertEquals(1, fakeGenerator.calls)
}

@Test
fun obsoleteProfileCannotPublish() = runTest {
    val pending = scheduler.request(oldProfileKey)
    scheduler.activateProfile(newProfile)
    pending.complete()
    assertNull(cache.read(oldProfileKey))
}
```

- [ ] **Step 2: Run scheduler tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageRasterSchedulerTest"
```

Expected: FAIL because the scheduler is missing.

- [ ] **Step 3: Implement priority and single-flight ownership**

Use one coroutine-owned queue keyed by `ReaderPageRasterKey`. Order work:

```text
current -> next transition -> previous transition -> current chapter -> next chapter -> previous chapter
```

Do not create one coroutine per visible card or frame. Do not use a timeout to cancel slow capture.

- [ ] **Step 4: Add calibration policy**

Measure the first three representative capture/encode/write/read/decode/upload operations. Use projected chapter byte and duration cost to choose eager chapter preparation or a rolling window.

Keep the decision pure in `ReaderPageRasterPolicy` so tests can supply deterministic measurements.

- [ ] **Step 5: Extend passive preview commands**

Add a batch command that stages exact page ordinals serially in the passive renderer and returns page identity before capture. The passive renderer remains muted and cannot publish progress or reader events.

- [ ] **Step 6: Persist every successful raster**

Scale using the selected quality before PNG encoding. Publish only when publication, pagination, and quality profile tokens still match.

- [ ] **Step 7: Run scheduler, cache, and harness tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageRasterSchedulerTest" --tests "paige.navic.ui.screens.reader.ReaderPageRasterCacheTest"
npm --prefix tools/reader-harness run test:page-turn-model
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterScheduler.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterSchedulerTest.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageRasterPolicy.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPageRasterPolicyTest.kt composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt
git commit -m "feat(reader): precompute page rasters asynchronously"
```

### Task 8: Add Cover-Backed Determinate Preparation UI

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPagePreparationPolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Write failing preparation-state tests**

```kotlin
@Test
fun progressUsesCompletedRequiredWork() {
    val state = preparationState(required = 12, completed = 3)
    assertEquals(0.25f, state.progress)
    assertFalse(state.interactiveReady)
}

@Test
fun readerOpensWhenCurrentAndAdjacentBundlesAreReady() {
    val state = preparationState(current = true, next = true, previous = true)
    assertTrue(state.interactiveReady)
}
```

- [ ] **Step 2: Run the policy test and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:allTests --tests "paige.navic.reader.ReaderPagePreparationPolicyTest"
```

Expected: FAIL because preparation policy is missing.

- [ ] **Step 3: Implement preparation state**

Create immutable state with required count, completed count, active page label, error, retryability, and `interactiveReady`.

- [ ] **Step 4: Implement the overlay**

Render the uncropped cover above the existing diffused cover backdrop with a determinate progress bar. Keep it visible until current, next, and previous transition requirements are ready.

- [ ] **Step 5: Add the outrun-cache state**

When the reader is already open and a requested adjacent page is missing, keep the stable page visible and show a compact preparation indicator. Consume the turn gesture without converting it into a tap.

- [ ] **Step 6: Run preparation and reader setting tests**

Run:

```powershell
.\gradlew.bat :composeApp:allTests --tests "paige.navic.reader.ReaderPagePreparationPolicyTest" --tests "paige.navic.reader.ReaderSettingsDefaultsTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPagePreparationOverlay.kt composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPagePreparationPolicy.kt composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderPagePreparationPolicyTest.kt composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(reader): show page raster preparation progress"
```

### Task 9: Integrate OpenGL With Exact Foliate Settlement And Cut Over

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnController.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageTurnStateMachine.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderPageSlideCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbookReaderSettingOptions.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js`
- Modify: `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnNativeSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderPageTurnDestinationSourceTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleTest.kt`
- Remove: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt`
- Remove: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnWaveGeometry.android.kt`
- Remove: corresponding Canvas/wave tests listed in File Structure

- [ ] **Step 1: Write failing integration guards**

Require one persistent GL view, complete-bundle attachment, release-only exact relocation, target-token plus composited-frame detach, active-leaf landscape bounds, and no Canvas/wave production reference.

```kotlin
@Test
fun productionReaderNoLongerReferencesCanvasWaveRenderer() {
    assertFalse(controllerSource.contains("ReaderPageTurnSlideView"))
    assertFalse(controllerSource.contains("ReaderPageTurnWaveGeometry"))
    assertContains(controllerSource, "ReaderPageCurlGlView")
}
```

- [ ] **Step 2: Run integration guards and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest" --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleTest"
```

Expected: FAIL while Canvas remains wired.

- [ ] **Step 3: Wire prepared bundles into the GL view**

The controller attaches the opaque GL surface only when current, active, underneath, and final textures required by the transition plan are valid. Gesture progress calls `setProgress` and `requestRender`; it never captures or uploads.

- [ ] **Step 4: Preserve release-only commit and exact relocation**

On release, animate from current progress to the commit or cancel endpoint. Dispatch exactly one visual-page target only for commit. Cancel removes the overlay without navigation.

- [ ] **Step 5: Preserve settlement shield**

Keep `finalBase` visible until expected target token and a later composited frame are observed. Do not copy PlayLikeCurl's post-animation resource swap.

- [ ] **Step 6: Fix gesture ownership and readiness**

A page-turn-owned gesture cannot become a tap while animation or settlement is active. A missing bundle consumes the gesture into preparation state. The next prepared gesture may begin according to coordinator capacity without waiting for cache prewarm of the entire chapter.

- [ ] **Step 7: Remove Canvas/wave production files and tests**

Delete the files listed under final cutover. Keep historical commits as the rollback path; do not preserve dead runtime fallback branches.

Atomically update the animated-mode label and option wiring to target OpenGL while preserving the persisted `canvas` value for migration compatibility. Remove `ReaderPageTurnOpenGlPrototypeEnabled`; the OpenGL renderer is now the direct production implementation of the animated mode.

- [ ] **Step 8: Run state, integration, bundle, and harness tests**

Run:

```powershell
.\gradlew.bat :composeApp:allTests --tests "paige.navic.reader.ReaderPageTurnStateMachineTest" --tests "paige.navic.reader.ReaderPageSlideCoordinatorTest"
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest" --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest" --tests "paige.navic.ui.screens.reader.ReaderPageTurnBundleTest" --tests "paige.navic.ui.screens.reader.ReaderPageCurlGlRendererSourceTest"
npm --prefix tools/reader-harness run test:page-turn-model
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add composeApp tools/reader-harness
git commit -m "feat(reader): replace Canvas turns with PlayLikeCurl OpenGL"
```

### Task 10: Meaningful Visual Gate B - Live Cache Integration

**Files:**
- Modify: `docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md`

- [ ] **Step 1: Build and hash one ReaderDev APK**

Run:

```powershell
.\gradlew.bat --no-daemon :androidApp:assembleReaderDev
Get-FileHash androidApp\build\outputs\apk\readerDev\androidApp-readerDev.apk -Algorithm SHA256
.\scripts\install-reader-dev.ps1 -Package darkaxt.navic.readerdev -SkipBuild
```

Expected: build succeeds and the installed APK hash is recorded.

- [ ] **Step 2: Validate the exact Alcatraz publication in portrait**

Check forward, backward, cancel, drag to both endpoints, chapter boundary, three rapid consecutive turns, cache miss, cache hit, and quality change.

Expected:

- correct current and target text
- no mirrored front text on the reverse
- no visible capture
- no blank page
- no post-animation blink
- no gesture becomes a tap during settlement
- no half-page freeze

- [ ] **Step 3: Validate tablet-like landscape**

Check left and right active leaves, gutter-relative geometry, forward/backward turns, companion-page stability, and cover-to-content transition.

Expected: only one leaf deforms and the final spread matches Foliate.

- [ ] **Step 4: Measure preparation and cache behavior**

Record:

- first-open current/adjacent preparation duration
- current-chapter total preparation duration
- cache-hit reopen duration
- PNG disk bytes at 25%, 50%, 75%, and 100%
- decoded memory and GPU texture estimates

Use the measurements to retain or revise the calibration threshold without adding cancellation timeouts.

- [ ] **Step 5: Commit gate evidence**

Record APK hash, emulator profile, screenshot/video paths, diagnostics excerpt paths, measurements, and pass/fail in this plan.

```powershell
git add docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md
git commit -m "test(reader): record live OpenGL page-turn gate"
```

### Task 11: Add Resilience, Cache Clearing, And Diagnostics

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlView.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRenderer.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCache.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterScheduler.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/domain/manager/StorageManager.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderManagedStorage.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/StorageManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/DataStorageScreen.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCacheTest.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRendererSourceTest.kt`

- [ ] **Step 1: Write failing resilience tests**

Require context-recreation reupload, corrupt-entry deletion, profile-token rejection, Clear Bindery Cache integration, opt-in diagnostics, and absence of timeout cancellation.

```kotlin
@Test
fun clearReaderCacheRemovesAnimationRasters() {
    seedRaster()
    storage.clearReaderPublicationCache()
    assertFalse(rasterRoot.exists())
}

@Test
fun rendererDoesNotLogPerFrame() {
    assertFalse(rendererSource.contains("Logger.d(\"ReaderPageCurlFrame\""))
}
```

- [ ] **Step 2: Run resilience tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageRasterCacheTest" --tests "paige.navic.ui.screens.reader.ReaderPageCurlGlRendererSourceTest"
```

Expected: FAIL for missing cleanup/context guards.

- [ ] **Step 3: Implement event-driven context recreation**

Keep Kotlin texture descriptors after GL handle loss. Recreate programs/buffers and reupload valid cached bitmaps in `onSurfaceCreated`. Do not wait on a timer.

- [ ] **Step 4: Expand Clear Bindery Cache**

Ensure the existing action removes publication resources, metadata, Whispersync artifacts, and page-animation rasters. Update its description to name page-animation images.

- [ ] **Step 5: Add structured opt-in diagnostics**

Log cache tier, raster key hash, quality, capture/encode/decode/upload duration, gesture target, animation completion, Foliate settlement token, and detach frame only when reader diagnostics are enabled.

- [ ] **Step 6: Run resilience and storage tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageRasterCacheTest" --tests "paige.navic.ui.screens.reader.ReaderPageCurlGlRendererSourceTest" --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add composeApp
git commit -m "fix(reader): harden OpenGL page cache lifecycle"
```

### Task 12: Final Verification, Tablet Acceptance, Sync, And Release

**Files:**
- Modify: release/version files selected by the repository's current release procedure
- Modify: `docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md`

- [ ] **Step 1: Run syntax, harness, attribution, and focused tests**

Run:

```powershell
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js
node --check composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js
npm --prefix tools/reader-harness run test:page-turn-model
.\scripts\verify-third-party-attributions.ps1
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.ui.screens.reader.ReaderPageCurlGeometryTest" --tests "paige.navic.ui.screens.reader.ReaderPageCurlGlRendererSourceTest" --tests "paige.navic.ui.screens.reader.ReaderPageRasterCacheTest" --tests "paige.navic.ui.screens.reader.ReaderPageRasterSchedulerTest" --tests "paige.navic.reader.ReaderPageTurnNativeSourceTest" --tests "paige.navic.reader.ReaderPageTurnDestinationSourceTest"
```

Expected: all commands pass.

- [ ] **Step 2: Run the full Android host test suite once**

Run:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest
```

Expected: `BUILD SUCCESSFUL` with no new failures.

- [ ] **Step 3: Run final ReaderDev emulator matrix**

Build, hash, and install one APK. Validate portrait and landscape forward/backward, cancel, rapid turns, chapter boundary, cover boundary, rotation, process recreation, context recreation, all four bitmap qualities, cache clear, and cache-hit reopen.

Expected: every acceptance criterion in the design specification passes.

- [ ] **Step 4: Run the physical tablet acceptance gate**

Install the exact same hashed ReaderDev APK on the tablet. Validate Alcatraz in tablet landscape and portrait. Capture screenshots/video and diagnostics for forward/backward, chapter boundary, rapid turns, 50% default quality, and one 75% quality comparison.

Expected: no emulator-only geometry or rendering difference and no visible capture/blink.

- [ ] **Step 5: Review the complete diff**

Run:

```powershell
git diff --check
git status --short
git log --oneline --decorate -12
```

Expected: clean diff checks and only intentional release files remain uncommitted.

- [ ] **Step 6: Commit release metadata**

Use the next valid release identifier from current `master`, not a stale identifier from this plan.

```powershell
git add androidApp/build.gradle.kts docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md
git commit -m "release: publish OpenGL reader page turns"
```

- [ ] **Step 7: Sync and push master**

Run:

```powershell
git fetch fork
git rebase fork/master
git push fork master
```

Expected: local `master` and `fork/master` point to the same commit.

- [ ] **Step 8: Publish and verify the public release**

Read the committed version directly from `androidApp/build.gradle.kts` and run the guarded release workflow:

```powershell
$buildFile = Get-Content androidApp/build.gradle.kts -Raw
$tag = [regex]::Match($buildFile, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
.\scripts\publish-github-release.ps1 -Tag $tag -AllowPublicRelease -ReleaseReadinessNote "PlayLikeCurl OpenGL page turns passed the full host suite, ReaderDev emulator matrix, and physical tablet acceptance gate recorded in the implementation plan."
```

Verify:

- tag points to `master`
- GitHub Actions release build succeeds
- public `Navic.apk` exists
- update manifest reports the new version
- downloaded APK hash matches the workflow artifact

- [ ] **Step 9: Record final evidence and commit documentation**

Record release URL, tag, commit, APK SHA256, emulator evidence, tablet evidence, test commands, and cache measurements in this plan.

```powershell
git add docs/superpowers/plans/2026-07-14-reader-playlikecurl-opengl.md
git commit -m "docs(reader): record OpenGL page-turn release"
git push fork master
```

---

## Completion Checklist

- [ ] PlayLikeCurl geometry parity is locked by sampled tests.
- [ ] Forward and backward use distinct geometry.
- [ ] GLES2 renderer is persistent and passive.
- [ ] Canvas/wave production paths are removed.
- [ ] Foliate remains the only navigation and progress authority.
- [ ] Portrait deforms one page and landscape deforms one resolved leaf.
- [ ] Raster quality offers 25%, 50%, 75%, and 100%, default 50%.
- [ ] Quality participates in cache identity and invalidates only raster artifacts.
- [ ] Raster generation is asynchronous, single-flight, persistent, and bounded.
- [ ] Preparation UI uses the cover and real determinate progress.
- [ ] No visible capture, blank frame, mirrored front text, or settlement blink remains.
- [ ] Clear Bindery Cache removes page-animation rasters.
- [ ] Attribution verification passes.
- [ ] ReaderDev emulator gates A and B pass.
- [ ] Final ReaderDev and physical tablet gate C pass.
- [ ] Master is synced and the verified public release is published.
