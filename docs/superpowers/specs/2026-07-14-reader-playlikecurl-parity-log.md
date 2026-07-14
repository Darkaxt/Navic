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
