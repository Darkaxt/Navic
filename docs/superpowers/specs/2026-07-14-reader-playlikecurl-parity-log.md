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

## Tranche 3: Narrow Raster Adapter

- Implementation base: `d08eba2c190a33994db9b2bf9efefb098441b68b` plus the staged Tranche 3 diff.
- Reference commit: `915a5a33773b1b2534134a56cdab00303b29a442`.
- Emulator: `sdk_gphone64_x86_64`, API 35, `1848x2960` override, locked portrait and landscape rotations.
- The reference mode uses the original eight native-resolution assets. The diagnostic mode uses eight generated page-identity rasters at the existing `Balanced` 50% quality.
- Both modes enter the same `ReaderPlayLikeCurlReferenceModel`, renderer, page-role lifecycle, draw order, gesture mapping, and 300 ms settlement path.

### Recorded Adapter Parity

- Portrait reference forward recording SHA256: `73F1854E050210496FA51B6AC6E6E64B2C5E13D2CBC1019BC109C12B5389AF1F`.
- Portrait diagnostic forward recording SHA256: `832B4BBB027702FBF76C6672B2437DB215F41988C7B96EB0A3F3FD4A300CB1F9`.
- Landscape reference forward recording SHA256: `7360C3DB8D1D0FEA61F900F5CAC81278FA54DB19C09D691876409B6CE9DA0F63`.
- Landscape diagnostic forward recording SHA256: `64E8EFBC7F6A88AC0AC2403F3DFC051AF8748AE1B828B6FE835498CE327497F4`.
- Contact sheets at progressive frames show the same deformation envelope, stationary-page exposure, role widths, and post-settlement identity rotation. The only expected differences are source pixels, native versus 50% raster resolution, and resulting video compression size.
- No geometry, progress mapping, texture-slot rotation, draw order, gesture threshold, duration, or interpolator was changed by the adapter.

### Raster Lifecycle

- Duplicate page identities share one materialization.
- Concurrent preparations share one in-flight raster.
- Profile changes reject stale decks and release stale rasters without cancelling the loader that owns cleanup.
- Decode and diagnostic generation run off the main thread; texture upload runs once on the GL thread before interaction is enabled.
- `onDrawFrame` contains no bitmap decode or texture upload.
- ReaderDev displays a cover-backed determinate preparation surface and consumes touch without invoking the page model until all required textures are uploaded.
- No cancellation timeout is used.

### Verification

- `ReaderPlayLikeCurlReferenceModelTest`, `ReaderPlayLikeCurlReferenceDemoSourceTest`, `ReaderPlayLikeCurlReferencePathSourceTest`, and `ReaderPlayLikeCurlRasterAdapterTest`: GREEN.
- `:androidApp:assembleReaderDev`: GREEN.
- Fresh combined test/build gate with `--rerun-tasks`: `BUILD SUCCESSFUL in 4m 58s`; 71 tasks executed.
- ReaderDev APK SHA256: `D686972505BDA98805EE39A9CEB7F654FD9849C3B7972A78EB780281EE617197`.
- Progression decision: Tranche 3 passes after its final clean verification and commit. Tranche 4 may map Foliate page identities into this adapter but may not reinterpret renderer geometry or interaction.
