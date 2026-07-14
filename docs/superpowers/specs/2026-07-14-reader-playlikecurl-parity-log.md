# Reader PlayLikeCurl Parity Log

## Reference

- Repository: `https://github.com/karankalsi/PlayLikeCurl`
- Local checkout: `C:\Users\darka\Documents\Projects\Android\.codex-temp\reference-playlikecurl`
- Reference commit: `915a5a33773b1b2534134a56cdab00303b29a442`

## Tranche 0: Failed-Prototype Quarantine

- Orthogonal raster-preparation UI committed as `33d77cf9`.
- Fidelity specification reset committed as `5a909400`.
- `ReaderPageCurlGlRenderer` remains available only as the failed historical implementation while the replacement is built.
- Every production file named `ReaderPlayLikeCurl*` is guarded against dependencies on `ReaderPageCurlGlRenderer`, `ReaderPageCurlLeafProjection`, and the old normalized forward/backward helpers.
- Visual parity: not applicable. No accepted reference renderer exists in Navic yet.

## Tranche 1: Complete Reference Model

- Model commit: `276ba539`.
- RED evidence: `ReaderPlayLikeCurlReferenceModelTest` failed at Android-host-test compilation because the faithful model and geometry types did not exist.
- GREEN evidence: 7 model tests plus 1 quarantine source guard, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESSFUL in 22s` for the final focused gate.

### Source Mapping

| Navic contract | PlayLikeCurl source |
| --- | --- |
| 25 by 25 grid, radius, UVs, indices, bitmap aspect correction | `Page.java` |
| front-page depth and deformation | `PageFront.java` |
| left-page depth and distinct deformation | `PageLeft.java` |
| right-page stationary depth | `PageRight.java` |
| persistent left/front/right objects, draw order, endpoint reset, active role | `PageRenderer.java` |
| previous/current/next boundary duplication and identity rotation | `PageSurfaceView.processPage()` |
| down/move/release/fling equations and direction semantics | `PageSurfaceView.onPageTouchEvent()` and `onFling()` |
| 300 ms settlement and interpolator selection | `PageSurfaceView.animatePagetoDefault()` |

### Parity Result

- Lifecycle parity: pass at pure-model level.
- Geometry-equation parity: pass at pure-model level.
- Visual parity: deliberately unclaimed. GLES2 rendering has not started.
- Differences: the model is allocation-free Android-independent Kotlin; this changes plumbing only, not state, equations, role identity, or timing constants.
- Progression decision: Tranche 2 may start only after the source guard is GREEN. Tranche 2 must use original reference textures and may not connect Foliate.

## Tranche 2: Faithful GLES2 Reference Demo

- Implementation commit: `fac9788ec143725fbc824a59d58e3844403b91d4`.
- Reference commit: `915a5a33773b1b2534134a56cdab00303b29a442`.
- ReaderDev APK SHA256: `9224231D77D42C87DB0E153457FC40BC5B8C9F0657F8D7E36F6E378F47962A50`.
- Emulator: `sdk_gphone64_x86_64`, API 35, `1848x2960` override; portrait and landscape rotations were locked independently before each recording.
- Original portrait and landscape page textures are isolated to `androidApp/src/readerDev/assets/playlikecurl-reference/` with the upstream MIT license.
- No Foliate document, Navic page bitmap, leaf projection, settlement shield, or failed-prototype renderer participates in this harness.

### Rendering Mapping

| GLES2 implementation | PlayLikeCurl source |
| --- | --- |
| three persistent GPU page objects and left/front/right draw order | `PageRenderer.java` |
| 26 by 26 vertices, UVs, indices, role-specific deformation | `Page.java`, `PageFront.java`, `PageLeft.java`, `PageRight.java` |
| 45 degree projection, orientation aspect rule, `z=-2`, `x/y=-0.5` transform | `PageRenderer.onSurfaceChanged()` and `onDrawFrame()` |
| depth test with `LEQUAL` and original role depths | `PageRenderer.onSurfaceCreated()` and page constructors |
| texture identity rotation, nearest minification, linear magnification, repeat wrap | `Page.loadTexture()` and `PageSurfaceView.processPage()` |
| down/move/release/fling mapping and 300 ms settlement | `PageSurfaceView.onPageTouchEvent()`, `onFling()`, and `animatePagetoDefault()` |

### Recorded Parity

- Portrait forward commit: start, progressive deformation, adjacent-page exposure, completion, and post-settlement identity rotation match the upstream `demo.gif` sequence.
- Portrait backward commit: previous-page expansion, layer order, completion, and identity rotation match the reverse segment of `demo.gif`.
- Portrait non-commit release: a slow partial drag deforms continuously, returns through the original accelerate/decelerate settlement, and preserves the current page identity.
- Landscape forward commit: original landscape textures, projection rule, full-page deformation, layer order, and identity rotation match the upstream source and shipped landscape assets.
- The upstream repository ships no landscape animation recording. Landscape visual-video parity is therefore not claimed; its gate is source, asset, and runtime-state parity. Navic leaf scissoring remains forbidden until Tranche 5.
- Diagnostic recordings and contact sheets were retained only as local `.codex-*` evidence and were deliberately excluded from Git.

### Expected Modernization Differences

- Fixed-function matrices, client arrays, and `GL_TEXTURE_2D` state are represented by GLES2 uniforms, attributes, VBOs, and shaders.
- `GestureDetector.onDown()` returns `true` so current Android dispatches the subsequent move/fling stream. The original returned `false`; this is input-plumbing compatibility and does not change its equations, thresholds, or rendered result.
- No geometry, page role, direction, endpoint, timing, interpolator, texture assignment, or draw-order difference remains unexplained.

### Verification

- `ReaderPlayLikeCurlReferenceModelTest`, `ReaderPlayLikeCurlReferenceDemoSourceTest`, and `ReaderPlayLikeCurlReferencePathSourceTest`: GREEN.
- `:androidApp:assembleReaderDev`: GREEN.
- Combined focused test/build gate: `BUILD SUCCESSFUL in 47s`.
- Progression decision: Tranche 2 passes. Tranche 3 may add only a narrow asynchronous raster adapter; it may not reinterpret the proven renderer.
