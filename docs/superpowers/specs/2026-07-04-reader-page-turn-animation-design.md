# Reader Page-Turn Animation — Design Specification

**Status:** Draft (rev 3 — native architecture pivot after capture spike)
**Date:** 2026-07-04 (revised 2026-07-05, 2026-07-05 native pivot)
**Owner:** Darkaxt
**Validation target:** Codex architecture review
**Scope:** Replace the current page-turn drag animation infrastructure with a three-mode (`none` / `canvas` / `webgl`) progressive edge-curl system, with orientation-aware fidelity (landscape spread = full effect, portrait = light effect).

> **⚠ REV 3 ARCHITECTURAL PIVOT (2026-07-05):** The Stage 1 capture spike ran against the live Android tablet WebView and proved that **no in-WebView JS capture path works** — Foliate renders EPUB chapters in an `about:srcdoc` iframe (origin `null`), which is cross-origin to the reader's top page (`appassets.androidplatform.net`), so any bitmap the content touches is tainted and unreadable from JS. See §11 for the full evidence. As a result, the bitmap source and the `canvas`/`webgl` renderers are **native** (Android-side), not JS. The setting surface, state machine, commit/release rules, and orientation model from rev 2 are unchanged; only the *rendering location* changes (JS → native). Rev 2's JS-renderer sections (§5.2–5.3, §7, §8, §9) are superseded by the native sections below.

> **Source-reference policy:** this document refers to code by **stable symbol names** (`readerPageDragCurlMetrics`, `ensurePageDragPreviewLayer`, `KomikkuReaderNativeViewerContainer`, etc.), not line numbers. The implementation branch should be re-synced to a clean master tip before any edit, and symbol presence re-verified; line numbers drift and are unsafe to cite.

---

## 1. Motivation

The current page-turn drag animation has two failure modes that this work resolves:

1. **The standard drag animation is glitchy.** It drives Foliate-js's renderer live via `renderer.scrollBy(...)` during the drag and commits via `renderer.snap(vx, vy)`, layered with a paper-texture surface and a boundary underlay iframe. Physical testing reports tearing/shimmer on the paper texture and page edges/stains during drag, and preview/commit mismatches at section boundaries. The root cause is per-gesture DOM churn: cloned snapshot `<iframe>`s crossfaded against the live paper-texture layer.

2. **The optional curl animation looks rigid.** It is implemented as `perspective(1800px) rotateY(${angle}deg)` on a cloned snapshot — a flat plane rotating wholesale from the first pixel of drag. A flat `rotateY` **cannot bend**; it produces a door-swing, not a page curl. No tuning of angle, shadow, or easing fixes this, because the rendering technique is wrong for the goal.

This spec defines the replacement: a **progressive edge-curl** interaction rendered on a **2D canvas mesh** or a **WebGL shader**, with a deterministic `none` fallback, fed by **rasterized page bitmaps** instead of live-DOM clones.

---

## 2. Goals & non-goals

### Goals
- Replace the `{standard, curl}` `dragAnimationMode` with `{none, canvas, webgl}` `pageTurnAnimation`.
- Implement the **edge-grab progressive curl** interaction: dragging from the page edge deforms the paper locally; the deformation grows with drag distance/velocity and commits to a full turn past a threshold; below threshold on release it relaxes back to flat.
- Render a **real curved deformation** (bending geometry + arc-length texture remap + crease shadow + mirrored back-face), not a flat-plane rotation.
- Produce a **full-fidelity effect in landscape spread mode** (2 pages) and a **lighter effect in portrait mode** (1 page).
- Eliminate the live-DOM-snapshot-per-gesture hack that causes the texture/edge/stain glitches.
- Provide a robust **`none`** mode as the universal fallback and a **WebGL capability probe** so `webgl` degrades to `canvas` on unsupported devices.

### Non-goals (explicitly out of scope)
- Replacing Foliate-js for layout/pagination. Foliate remains the pagination engine; the animation is a pure overlay layer over page bitmaps. On commit, Foliate's `renderer.snap()` performs the real navigation.
- Changing the native touch-detection layer (`KomikkuReaderNativeViewerContainer`). It already produces correct `deltaX/deltaY`, `phase` (`Update`/`Release`/`Cancel`), and `viewWidth/viewHeight`, and already arbitrates tap vs. drag. This spec consumes that stream unchanged.
- Changing the bridge protocol's `previewPageDrag` command shape. The existing `{deltaX, deltaY, viewWidth, viewHeight, phase}` payload is sufficient; the curl state machine derives everything else.
- Audiobook/Whispersync integration, highlighting, or any non-animation reader behavior.

---

## 3. The interaction model (normative)

The interaction is a **staged state machine** driven by the drag stream. It is identical for `canvas` and `webgl`; only the renderer differs.

### 3.1 States

| State | Meaning |
|---|---|
| `idle` | No drag in progress. No overlay rendered. |
| `deforming` | Drag active, below commit threshold. A local curl grows at the grab edge. |
| `committing` | Commit threshold crossed (distance or release velocity). The curl sweeps the full page width and the turn completes. |
| `relaxing` | Drag released below threshold. The curl eases back to flat over a short duration; no page change. |

### 3.2 Inputs

From the existing `previewPageDrag` command:
- `deltaX`, `deltaY` — drag delta since gesture start (px).
- `phase` — `update` | `release` | `cancel`.
- `viewWidth`, `viewHeight` — surface size (px).

Derived:
- `axisDelta` — `Math.abs(vertical ? deltaY : deltaX)`.
- `axisSize` — `vertical ? height : width`.
- `progress` — `clamp(axisDelta / axisSize, 0, 1)`.
- `velocity` — computed from the time delta between consecutive `update` commands (the controller already coalesces; a rolling window of the last 3–4 samples is sufficient).
- `direction` — `next` or `previous`, from the sign of the delta and the reader's RTL/LTR direction (reuse existing `readerPaperTextureDragDirection` logic).
- `spread` — boolean, true when the renderer is in 2-page spread mode (see §6).

### 3.3 Commit rule (normative: commit-on-release only)

**Commit is decided exclusively at `phase: release`.** The renderer must never commit a page turn while the finger is still down, even if the drag distance crosses the threshold mid-gesture. Rationale: continuous commit during drag reintroduces the Foliate/snap/desync problems that motivated this redesign — committing while the renderer is still being live-driven by `scrollBy`/snap races the very state this spec removes from the animation path.

At `phase: release`, commit fires when **either** condition holds:
- **Distance:** peak `progress` reached during the gesture `>= DistanceCommitThreshold` (default `0.38`).
- **Velocity:** release velocity magnitude `>= VelocityCommitThreshold` (default `900 px/s`, subject to physical tuning).

If neither holds at release → `relaxing` (curl eases back to flat, no page change). If either holds → `committing` (curl sweeps the full page, then hands navigation to Foliate exactly once — see §5.3 "no double snap").

`phase: cancel` always → snap-flat with no commit, regardless of distance/velocity accumulated.

### 3.4 Visual parameters (renderer-agnostic)

The state machine outputs a `curlState` object both renderers consume:

```ts
curlState = {
  phase: 'deforming' | 'committing' | 'relaxing',
  direction: 'next' | 'previous',
  spread: boolean,
  vertical: boolean,
  creasePosition: number,   // 0..axisSize, where the curl crease sits along the axis
  curlRadius: number,       // px, radius of the bending cylinder; shrinks as deformation grows
  lift: number,             // 0..1, how much the edge is lifted off the page
  commitProgress: number,   // 0..1, only meaningful in 'committing'; sweeps crease across
  relaxProgress: number,    // 0..1, only meaningful in 'relaxing'; eases back to flat
}
```

**Deforming-phase mapping (the core of the "natural" feel):**
- For `progress ∈ [0, LocalDeformCeil]` (default `LocalDeformCeil = 0.30`), the curl is **local**: `creasePosition` stays at the grab edge and grows inward, `curlRadius` tightens, the page body beyond the crease stays flat. Only the strip within `curlRadius` of the crease is deformed.
- For `progress > LocalDeformCeil` while still dragging, the curl widens and more of the page lifts, but the state stays `deforming` — no commit occurs mid-gesture (§3.3). The renderer may visually preview the impending turn (e.g., lift the page further), but navigation is not triggered.
- Transition to `committing` happens **only at `phase: release`** when the commit rule (§3.3) is satisfied.

**Committing-phase mapping:** `commitProgress` sweeps 0→1; `creasePosition` travels the full axis; the whole page follows the curl; at `commitProgress = 1` the page is fully turned and the layer tears down, handing navigation to Foliate.

**Relaxing-phase mapping:** `relaxProgress` eases 1→0 over `RelaxDurationMs` (default `160ms`); all curl parameters interpolate back to flat; the layer tears down at `relaxProgress = 0`.

---

## 4. The setting

### 4.1 New setting: `pageTurnAnimation`

Replace `dragAnimationMode` (`standard`/`curl`) with `pageTurnAnimation` (`none`/`canvas`/`webgl`).

**Allowed values:**
- `none` — no live drag animation; on commit, a short deterministic slide or instant cut.
- `canvas` — progressive curl rendered on a 2D canvas (triangle-mesh bend). Universal device support.
- `webgl` — progressive curl rendered via a GLSL shader (true cylinder deformation). Capability-gated; falls back to `canvas` if WebGL is unavailable or loses context.

### 4.2 Files changed for the setting (exact current references)

The setting mirrors the existing `dragAnimationMode` plumbing 1:1, replacing values:

| Concern | Current symbol (re-verify on implementation branch) | Change |
|---|---|---|
| Constants | `ReaderChromeState.kt` — `ReaderDragAnimationStandard`, `ReaderDragAnimationCurl`, `ReaderSupportedDragAnimationModes`, `normalizedReaderDragAnimationMode` | Replace with `ReaderPageTurnNone/Canvas/Webgl` constants; update the supported-modes list and normalizer. |
| Bridge field | `ReaderBridgeProtocol.kt` — `dragAnimationMode: String?` field + its serializer entry | Rename to `pageTurnAnimation`. |
| Settings enum | `EbookReaderSettingOptions.kt` — `ReaderDragAnimationOption` enum (`Standard`/`Curl`) and `forDragAnimationMode` | Replace with `ReaderPageTurnOption` enum: `None`, `Canvas`, `Webgl`, each with its own string resource. Add `option_ebook_reader_page_turn_animation_none/canvas/webgl` strings; retire `..._standard`/`..._curl`. |
| Persistence | `PreferenceManager.kt` — `readerDragAnimationMode by preference("standard")` | Rename key to `readerPageTurnAnimation`, default `"canvas"`. **Migration:** read the old `dragAnimationMode` key once on first launch after upgrade and map `curl→canvas`, `standard→none` (rationale below), then write the new key. |
| JS mirror | `navic-reader-settings-core.js` — `ReaderDragAnimationStandard`/`Curl` constants + the `dragAnimationMode` resolver | Replace with three new constants and a `pageTurnAnimation(settings)` resolver that also returns the **effective** mode after capability fallback (§5.2). |
| JS switch | `navic-reader-page-turns.js` — `ensurePageDragPreviewLayer({ curlEnabled })` branch and the `readerDragAnimationModeValue !== 'curl'` gate | Replace the curl/standard branch with a three-way dispatch on the resolved mode (see §5). |

### 4.3 Orientation default mapping

A **single** user choice applies to both orientations, but the *effect* auto-scales:

| Mode | Portrait (1 page) | Landscape (2-page spread) |
|---|---|---|
| `none` | instant / short slide | instant / short slide |
| `canvas` | single-page mesh curl, lighter shadow, no cast-shadow pass | full spread curl (inner-edge grab, spine shadow, cast shadow on facing page) |
| `webgl` | single-page shader curl, lighter | full spread shader curl |

The renderer knows its orientation/spread via `readerLandscapeSpreadColumnCount` (see export prerequisite in §6) and the existing `width < height * 1.12` heuristic used by `applyPageDragCurlSheet`. No separate per-orientation setting is exposed.

**Default value:** `canvas`. It is the safe universal choice; users who want shader-grade fidelity opt into `webgl`; users who want no animation opt into `none`.

---

## 5. Architecture (native, per rev 3 spike result)

**The curl is rendered on the Android side, not in JS.** The drag detector (`KomikkuReaderNativeViewerContainer`) already lives native and already produces the drag stream; the curl now renders right there, over `PixelCopy` bitmaps of the WebView. The JS layer is responsible only for layout/pagination and for performing the real navigation on commit (`renderer.snap`) — exactly the handoff Foliate already provides.

This pivot is forced by §11: JS cannot capture a Foliate page bitmap (cross-origin taint), so the bitmaps must come from native, and the renderer that consumes them is most robust on native too (decoupled from WebView rAF, direct touch, predictable frame pacing).

### 5.1 Layer responsibilities (rev 3)

```
Native touch (KomikkuReaderNativeViewerContainer)            [android, exists]
  → onReadableDragPreview(dx, dy, w, h, phase)                [android, exists]
  → PageTurnController (NEW, commonMain/androidMain)          // state machine + renderer dispatch
      ├── captures page bitmaps via PixelCopy                 [androidMain, NEW]
      ├── drives the native curl renderer                     [androidMain, NEW]
      └── on commit: emits ReaderViewerAction.TurnPage        // same path as a tap turn
  → ReaderController.turnPage → ReaderEngineCommand.NavigateTo [exists]
  → JS renderer.snap commits the real Foliate navigation      [exists]
```

The native drag detector is the source of truth for the gesture. The new `PageTurnController` consumes its stream, owns the bitmap capture + curl rendering + commit decision, and on commit routes through the *existing* `TurnPage` action — so Foliate still performs the actual page change. **The JS `previewPageDrag` path is retired for curl modes** (it remains the fallback for `none`'s optional post-release slide, if any).

### 5.2 New native components

```
composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/
  ├── PageTurnController.kt              // staged state machine (§3); mode dispatch; commit rule
  ├── PageTurnBitmapSource.android.kt    // PixelCopy of the WebView surface (current + next page)
  ├── PageTurnCurlView.android.kt        // the curl rendering surface (Canvas2D or OpenGL)
  └── (existing KomikkuReaderNativeViewerContainer hosts the above)
composeApp/src/commonMain/kotlin/paige/navic/reader/
  └── PageTurnState.kt                   // pure state machine (CurlState, phases) — unit-testable
```

`PageTurnController` resolves the **effective mode** once per gesture start:
1. Take `pageTurnAnimation` from settings.
2. If `webgl`: probe OpenGL ES support; if unavailable or a prior context was lost, downgrade to `canvas`.
3. If device `Build.VERSION.SDK_INT < 26` (PixelCopy unavailable): downgrade to `none`.
4. Drive the corresponding native renderer; reuse a cached renderer across gestures.

The WebGL capability probe from Stage 0 (JS `probePageTurnWebglAvailability` / `webglcontextlost`) is **retired** — it lived in JS, and JS no longer renders. Its native equivalent is an OpenGL ES context-creation probe + `GLSurfaceView`'s surface-loss callbacks (which are already event-driven).

### 5.3 Renderer contract (native)

Every renderer implements (Kotlin):

```kotlin
interface PageTurnRenderer {
    fun begin(context: PageTurnContext)          // spread, vertical, direction, bitmaps, w, h
    fun update(curlState: CurlState)             // per drag-frame
    suspend fun commit(curlState: CurlState)     // resolves when the commit sweep finishes
    suspend fun relax(curlState: CurlState)      // resolves when relax-to-flat finishes
    fun cancel()                                 // synchronous teardown, no animation
    fun destroy()                                // release GL context / canvases
}

data class PageTurnContext(
    val spread: Boolean, val vertical: Boolean,
    val direction: PageTurnDirection,            // NEXT | PREVIOUS
    val pageBitmaps: PageBitmaps, val width: Int, val height: Int
)

data class PageBitmaps(
    val front: Bitmap,   // current page (or current-R + current-L for spread)
    val back: Bitmap     // next page (mirrored back-face texture)
)
```

The renderer never touches Foliate's DOM or the WebView's JS. **Navigation handoff and state clearing are normative (unchanged from rev 2 — renderer-agnostic):**

- **No double snap.** On `commit()` resolve, the controller emits exactly one `ReaderViewerAction.TurnPage(direction)` — the same action a tap produces — and Foliate's existing turn path performs the navigation. The renderer must never call `renderer.snap` or navigate directly. (The legacy `suppressNativeDragCommittedPageTurn` JS flag is no longer needed in the curl path because the native side owns the whole gesture; it remains only for the `none` path if that path still drives the live strip.)
- **Release clears pending drag state.** On `phase: release`, after the renderer resolves (commit or relax), the controller must clear all per-gesture state. The native side already calls `clearSwipeTouchState` / `clearNativeTapState` on `ACTION_UP`; the controller mirrors this so no stale curl state leaks into the next gesture.
- **Cancel snaps flat with no navigation.** On `phase: cancel` (native `ACTION_CANCEL` / `ACTION_POINTER_DOWN`), the renderer's `cancel()` runs synchronously: tear down the curl surface, discard bitmaps, emit no turn.
- **Boundary suppression preserved.** The existing section-boundary suppression logic (which prevents a drag at a chapter boundary from triggering a premature adjacent-section navigation) must be preserved. The controller's commit decision consults the same boundary context the current native drag path uses.

---

## 6. Orientation / spread awareness

The reader is in **spread mode** when landscape and the viewport supports two columns. Detection reuses two existing signals:
- `readerLandscapeSpreadColumnCount(maxColumnCount, inlineViewport, blockViewport, columnThreshold)` returns 2 in spread. **Export prerequisite:** this function is currently module-private in `navic-reader-typography.js` (no `export`); before reuse it must either be `export`ed, or — preferred — be replaced by a single public layout-profile helper (e.g. `readerResolvedLayoutProfile(settings, viewport)`) that returns `{ spread: boolean, columns: 1|2, vertical: boolean }` so the curl, texture, and page-box code all consume one source of truth.
- The `width < height * 1.12` heuristic already used by `applyPageDragCurlSheet` (a secondary fallback signal).

**Spread semantics for the curl:**
- The grab edge is the **inner edge** (the spine). Dragging leftward lifts the **right page** and curls it left across the spine, revealing the next spread underneath.
- Bitmap source must supply **four** bitmaps in spread: current-left, current-right, next-left, next-right.
- The cast shadow falls on the facing (left) page; the spine shadow runs down the center.

**Portrait (single-page) semantics:**
- Grab edge is the outer edge in the drag direction.
- Bitmap source supplies **two** bitmaps: current, next.
- Lighter shadow model: crease shadow + mirrored back-face only; no cast-shadow pass.

The state machine (§3) is identical; only the bitmap count and the renderer's geometry input differ.

---

## 7. The page-bitmap source (native PixelCopy, per rev 3)

Both `canvas` and `webgl` renderers consume **rasterized page bitmaps**, captured natively via `PixelCopy`. This is the primary fix for the texture/edge/stain glitches: the bitmap is a snapshot of exactly what the user sees, and the per-gesture DOM churn that caused tearing/shimmer is eliminated entirely.

### 7.1 Why native, not JS (spike result — see §11)

The Stage 1 capture spike proved that **no in-WebView JS capture path works**: Foliate renders EPUB chapters in an `about:srcdoc` iframe (origin `null`), cross-origin to the reader's top page, so any bitmap the content touches is tainted and unreadable from JS. `PixelCopy` reads composited surface pixels from the Android compositor — it sees no origins, only pixels — and is therefore the only viable capture.

### 7.2 Capture mechanism: `PixelCopy`

- `android.view.PixelCopy.request(surface, rect, bitmap, listener, executor)` captures a region of the WebView's rendered surface into a `Bitmap`. This is the same API used for screenshots/video-frame extraction; it captures the *composited* result (text + fonts + images + any JS-drawn overlays), at full resolution.
- **API guard:** `PixelCopy` requires `Build.VERSION.SDK_INT >= 26` (Android 8.0). The app's `minSdk = 24`. On API 24–25, the curl modes downgrade to `none` (capability gate in §5.2). `View.draw(Canvas)` is **not** a viable fallback — it truncates off-screen content and fails on hardware-accelerated WebView paths.
- **Capture timing:** once per gesture start (first `update` after `idle`), for the current page region. The next-page bitmap requires either (a) a second capture after scrolling the WebView to the next page (visible flash — avoid), or (b) capturing the adjacent column's region if it's already laid out off-screen. Option (b) is preferred; if the next page isn't available off-screen, synthesize the back-face by mirroring + tinting the front bitmap (acceptable for a back-face that's mostly shadowed).
- **Cost budget:** `PixelCopy` is asynchronous and typically completes in a few ms on the GPU thread; verify <30 ms on the lowest supported device during Stage 2 validation. The capture must not block the touch thread.

### 7.3 Overlay composition (free with PixelCopy)

Because `PixelCopy` captures the composited surface, the paper-texture, edge, and stain layers — which are JS-drawn overlays the user already sees — are **included automatically**, in the correct stacking order, respecting every toggle (`paperTextureEnabled`, `paperStainsEnabled`, border-overlay slots). There is no separate "bake" step and no risk of the curling page differing from the static page. This is a significant simplification over the rev-2 JS design, which had to manually bake overlays.

The **curl shading** (crease shadow, back-face tint, cast shadow in spread) is **not** in the captured bitmap — it is applied by the native renderer at draw time on top of the bitmap, because it animates with `curlState` every frame.

### 7.4 Open questions
- Whether the next-page bitmap can be captured off-screen (preferred) or must be synthesized by mirroring. Needs a Stage 2 probe of whether Foliate lays out the adjacent column within the capturable surface.
- `PixelCopy` of a `WebView` specifically: confirm it captures the hardware-accelerated layer reliably across the device matrix (some Adreno drivers have had surface-readback issues). The Stage 2 device validation covers this.

---

## 8. Canvas renderer (`canvas` mode) — native Android Canvas

A **2D triangle-mesh curl** drawn via `android.graphics.Canvas` (hardware-accelerated `View` or a `TextureView`/`SurfaceView`):
- Subdivide the page into vertical strips (resolution ~16–24 strips per page width).
- For each strip, compute its position on a bending cylinder parameterized by `curlState.creasePosition` and `curlState.curlRadius`. Strips outside the curl radius stay flat; strips inside wrap the cylinder.
- Remap the captured `Bitmap` onto the mesh via `Canvas.drawBitmapMesh` (Android's built-in mesh-warp primitive, purpose-built for this) or per-quad `drawBitmap` with skew/scale, so text/ink follows the curve.
- Render the **back face** as the next-page bitmap, mirrored and tinted slightly darker/desaturated, on the lifted strip.
- Composite a **crease shadow** (`RadialGradient` along the crease) and, in spread mode, a **cast shadow** on the facing page.

**Why this works where JS didn't:** `drawBitmapMesh` operates on a `Bitmap` (the `PixelCopy` result), with no origin/taint concerns — it's a pure GPU pixel operation. The mesh math is identical to the StPageFlip reference; only the drawing API changes from JS canvas to Android Canvas.

**Reference for the curl geometry:** [StPageFlip](https://github.com/Nodlik/StPageFlip)'s triangle-mesh deformation + shadow gradients (the *math*, ported to Kotlin; the library itself is JS and not vendored).

### 8.1 Open questions
- Strip resolution vs. performance on mid-range Android: 16 strips is visually smooth; 24 is crisper. Needs device testing.
- Whether to render to an offscreen `Bitmap` and blit, or draw directly to the visible `Canvas` each frame (Choreographer-driven).

---

## 9. WebGL renderer (`webgl` mode) — native OpenGL ES

A **GLSL curl shader** rendered via `GLSurfaceView` (or a `SurfaceView` + manual EGL context):
- The page is a textured quad deformed in the **vertex shader** by bending it around a cylinder (axis = crease line, radius = `curlRadius`), with arc-length UV remap in the fragment shader so the texture follows the bend without stretching.
- Shadows are computed in-shader: crease shadow (dot of normal and light dir), curl-edge highlight, and cast shadow.
- Back face rendered with the next-page texture, mirrored, in a second draw call.
- The `PixelCopy` `Bitmap` is uploaded to a GL texture (`GLUtils.texImage2D`) once per gesture; the shader deforms it per frame.
- Uses a single quad (no mesh subdivision needed — the shader does the deformation), so it is cheaper than the canvas mesh at equivalent quality.

**Why native OpenGL, not WebView WebGL:** the §11 spike ruled out in-WebView capture, but more importantly, native OpenGL ES gives full control over the GL context lifecycle, surface compositing, and the `Bitmap`→texture upload — none of which are constrained by the WebView's CSP or origin model. The shader math is identical to the JS-WebGL version; only the host changes.

**Reference shader math:** [Page Curl Shader Breakdown](https://andrewhungblog.wordpress.com/2018/04/29/page-curl-shader-breakdown/) (cylinder deformation + arc-length UV). Runnable reference GLSL: [Shadertoy: Interactive 3D Book Flip](https://www.shadertoy.com/view/tfyfzG).

### 9.1 Capability probe and fallback (native)
- On first use of `webgl`, attempt to create an EGL/OpenGL ES context via `GLSurfaceView` (or a probe `EGLContext`). **Probe failure** = context creation throws or returns a null config.
- If probe fails, **persist** the downgrade (`pageTurnAnimation` effective = `canvas`) for the session and surface a one-time diagnostic. Do **not** re-probe within the session after a failure.

### 9.2 Context-loss handling (event-driven, no timeouts)
- `GLSurfaceView.Renderer.onSurfaceCreated` is the recreation hook. If the GL surface is lost (the `GLSurfaceView` reports it), downgrade to `canvas` for the remainder of the session — same as a probe failure. No auto-promote back mid-session.
- This mirrors the rev-2 event-driven rule (no timeouts/polling), translated to the native GL lifecycle.

### 9.3 Open questions
- OpenGL ES on Android is widely supported (GLES 2.0 is baseline since API 8), so the driver-risk surface is far smaller than WebView-WebGL was. The main residual risk is `PixelCopy`→texture timing on specific Adreno drivers; covered by Stage 2 device validation.

---

## 10. `none` mode

- **No live drag driving.** During the drag (`phase: update`) the renderer must **not** call `renderer.scrollBy(...)` or otherwise live-drive Foliate's container — that is precisely the per-gesture Foliate-coupling this spec removes. The drag stream is consumed only to track distance/velocity for the release decision; the page stays visually static under the finger.
- **Decision at release only.** At `phase: release`, the controller applies the §3.3 commit rule (distance OR velocity) and either turns the page or does not. No intermediate state.
- **On commit:** a short, deterministic **post-release** slide of the page content (e.g., 180 ms ease-out translate of the renderer container) OR an instant cut — user-tunable later. Default: short slide. This slide runs *after* release, decoupled from the drag, so it cannot race the renderer.
- **On sub-threshold release:** no-op (page already static).
- **On cancel:** no-op.
- This is the only mode that avoids the bitmap-capture pipeline entirely, so it is both the safest default-fallback and the lowest-overhead option.

---

## 11. Capture spike — COMPLETED (2026-07-05, result: RED for JS → pivoted to native)

The Stage 1 spike ran against the **live Android tablet WebView** (`darkaxt.navic`, book open on chapter 1) via CDP (`adb forward` + `tools/reader-harness/src/adb-webview-eval.mjs` plumbing). It tested whether a Foliate page could be captured to a bitmap inside the WebView.

### 11.1 What was tested (real device)
- **foreignObject → `<img>` → canvas:** the image loaded and drew (37 ms — fast), but `getImageData` threw *"The canvas has been tainted by cross-origin data."* Even with **all external resources stripped** (img/object/embed/svg removed from the clone), still tainted.
- **`drawImage(iframeElement)`:** *"The provided value is not of type CSSImageValue or HTMLCanvasElement..."* — an iframe element is not a valid image source.
- **Canvas built *inside* the srcdoc doc** (to stay same-origin with the content): the canvas itself read fine, but drawing the foreignObject image into it **still tainted** — the SVG-image is the cross-origin data.

### 11.2 Root cause (structural, unfixable in JS)
Foliate renders each EPUB chapter in an **`about:srcdoc` iframe**. The content document's origin is **`null`** (opaque). The reader's top page is `https://appassets.androidplatform.net` (WebViewAssetLoader origin). Confirmed via the spike:
```
docOrigin: "null"   docHref: "about:srcdoc"   topOrigin: "https://appassets.androidplatform.net"   sameOrigin: false
```
They are cross-origin, and `srcdoc` content has no origin it can opt into sharing. Any bitmap that content touches is tainted and unreadable from JS. This is a hard browser-security wall — there is **no in-WebView JS path** to capture a Foliate page bitmap.

### 11.3 Outcome
- **RED for in-WebView JS capture** (rev 2's §7.1.1 foreignObject, §7.1.2 direct iframe, §7.1.3 html2canvas — all blocked by the same wall).
- **The only viable capture is native `PixelCopy`** of the WebView surface (Android reads composited pixels, sees no origins). This forced the rev 3 architectural pivot: the bitmap source and the `canvas`/`webgl` renderers are native (§5, §7, §8, §9), not JS.
- The Stage 0 setting plumbing (commit `142712bc`) is unaffected and stands. The JS WebGL probe (`probePageTurnWebglAvailability` / `webglcontextlost` handler in `navic-reader.js`) is now dead code (JS doesn't render) and should be removed when the native probe lands.

### 11.4 What remains to validate (Stage 2)
`PixelCopy` itself was not tested on-device yet (the spike focused on ruling out JS). Stage 2 must confirm:
- `PixelCopy.request` captures the WebView surface reliably and cheaply (<30 ms) on the device matrix.
- The next-page bitmap can be captured off-screen, or must be synthesized by mirroring.
- API 24–25 fallback to `none` (PixelCopy is API 26+).

This is no longer a blocking gate on a yes/no question — it's implementation validation of a known-viable API.

---

## 12. Migration & compatibility

The `dragAnimationMode` symbol and its string values appear across **about 20 files** (19 code/test/harness + `strings.xml` at the current audit; re-count on the implementation branch after syncing). The rename must touch every one; a partial migration leaves the setting non-functional in some surfaces. Enumerated surface (re-verify on the implementation branch — symbol names, not line numbers):

**Kotlin — setting definition & model:**
- `ReaderChromeState.kt` — constants `ReaderDragAnimationStandard`/`ReaderDragAnimationCurl`, the supported-modes list, the normalizer, the default in two defaults blocks, the short-label helper, the settings-builder fields.
- `ReaderBridgeProtocol.kt` — the `dragAnimationMode: String?` field and its serializer entry.
- `ReaderPreferenceSettings.kt` — **all five touchpoints**, including the book-override merge (`withReaderSettingsOverride`), the JSON decode (`stringValue("dragAnimationMode")`), and the JSON encode. The new `pageTurnAnimation` must be carried through the book-specific override path identically, or per-book page-turn overrides will silently drop.
- `PreferenceManager.kt` — the `readerDragAnimationMode by preference("standard")` property.

**Kotlin — UI surfaces:**
- `EbookReaderSettingOptions.kt` — the `ReaderDragAnimationOption` enum (`Standard`/`Curl`) and its `forDragAnimationMode` resolver.
- `ReaderSettingsDialog.kt` — the in-reader settings panel option row.
- `EbooksScreen.kt` — the global ebook-settings page-turn option.
- `SettingsSearchEbookRows.kt` and `SettingsSearchRows.kt` — the settings-search rows that surface this option by name/string.

**Resources:**
- `composeResources/values/strings.xml` — `option_ebook_reader_page_turn_animation_standard` and `..._curl`. Add `..._none`/`..._canvas`/`..._webgl`; retire the old two.

**JS:**
- `navic-reader-settings-core.js` — `ReaderDragAnimationStandard`/`Curl` constants and the `dragAnimationMode` resolver.
- `navic-reader-settings.js`, `navic-reader-appearance.js`, `navic-reader.js`, `navic-reader-page-turns.js` — every read of the mode and the `readerDragAnimationModeValue` field threaded through them.

**Tests & harness:**
- `ReaderRuntimePaperSurfaceTest.kt` (androidHostTest) — curl-source-guard tests that slice `ensurePageDragPreviewLayer({ curlEnabled })` and assert on `data-navic-page-curl-*` / `readerPageDragCurlMetrics`. Replace with new-renderer guards (§13).
- `ReaderBridgeProtocolTest.kt`, `ReaderPreferenceSettingsTest.kt`, `ReaderSettingsDefaultsTest.kt` (commonTest) — serialization/normalization/default assertions for `dragAnimationMode`.
- `tools/reader-harness/src/run-reader-harness.mjs` — the `--mode epub-native-drag-*` flags reference the old modes; add `epub-page-turn-none/canvas/webgl`.

**Migration rules:**
1. **Atomic rename.** The `dragAnimationMode → pageTurnAnimation` rename must land across Kotlin + JS + resources + tests + harness in a single commit. Version skew (e.g., Kotlin emits the new key, JS still reads the old) leaves the setting dead.
2. **Preference migration on first launch.** Read the old `dragAnimationMode` preference once, map `curl → canvas`, `standard → none`, write the new `pageTurnAnimation` key, delete the old key. Same migration applies to any persisted **per-book override** JSON that contains `dragAnimationMode`.
   - **`standard → none` rationale (definitive):** the spec's central goal is to stop live-driving Foliate's renderer during drag. The old `standard` mode *is* that live drag. Users who wanted animation had to opt into the (rigid) `curl`; mapping them to `none` lands them on the deterministic, no-Foliate-coupling default, and they can opt into the new `canvas` curl explicitly. Mapping `standard → canvas` would silently change every standard user's page-turn feel to a curl, which is not what they configured.
3. **Bridge field rename is breaking.** There is no on-wire backward compatibility obligation (the engine and host ship together), so a clean rename is acceptable and preferred over a dual-key transition.

---

## 13. Testing strategy

- **Unit (commonTest):** the `PageTurnState` state machine (pure Kotlin) — test all transitions: idle→deforming, deforming→committing (distance), deforming→committing (velocity), deforming→relaxing (release below threshold), committing→idle (handoff), relaxing→idle, any-state→cancel.
- **Source guards (androidHostTest):** update the existing curl-source-guard tests to assert:
  - The native `PageTurnController` / `PageTurnCurlView` exist and are wired into `KomikkuReaderNativeViewerContainer`.
  - No `perspective(1800px) rotateY` whole-page-rotation remains anywhere (JS or native).
  - No `surroundContents` / `extractContents` / live-DOM clone in the animation path.
  - The JS `probePageTurnWebglAvailability` / `webglcontextlost` handler (Stage 0 dead code) is removed once the native probe lands.
- **On-device smoke (`scripts/adb-reader-smoke.ps1`):** add a probe asserting that a drag in `canvas`/`webgl` mode produces a curl overlay that is created on gesture-start and destroyed on commit, and that exactly one `TurnPage` action fires on release-past-threshold. The existing `RequireNativeSwipeAction` / devtools-probe machinery covers this.
- **Physical validation (manual, not automated):** the curl's visual fidelity and gesture feel are acceptance criteria, not CI gates. Validate on (a) a landscape tablet for spread mode, (b) a portrait phone for single-page mode, (c) a low-end device for canvas-mode frame rate, (d) an API 24–25 device confirming downgrade to `none`.

---

## 14. Risks & mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ~~Page-bitmap capture in WebView~~ | ~~Resolved (RED)~~ | — | §11 spike ruled out JS; native `PixelCopy` is the path (§7) |
| `PixelCopy` of WebView surface slow/unreliable on some GPUs | Low–Medium | High | Stage 2 device validation; `none` fallback on API <26 and on probe failure |
| Native renderer frame pacing / input latency | Low | Low | Native owns the touch + Choreographer; no WebView rAF in the loop — this is the *improvement* over the JS design |
| OpenGL ES context issues on specific Adreno/GPU combos | Low | Medium (only `webgl` users) | Native capability probe + automatic `canvas` fallback (§9.1) |
| Foliate column offset drift between capture and commit → page mismatch | Low | Medium | Capture is for the *current* page only; commit routes through the existing `TurnPage` action (Foliate's navigation is the source of truth) |
| Spread-mode bitmap alignment (4 bitmaps) is hard to seam | Medium | Medium | Defer spread polish to a follow-up slice; ship portrait-first if needed |
| Snapshot text is non-selectable during the curl | Certain | Low | Acceptable — same as every reader that animates page turns; selection resumes after commit |

---

## 15. Decisions & implementation-validation gates

### 15.1 Product decisions (made — recorded, no longer open)

- **Default mode: `canvas`.** Universality over fidelity; `webgl` is opt-in.
- **Migration:** `standard → none`, `curl → canvas` (rationale in §12.2). Definitive. (Stage 0 shipped: commit `142712bc`.)
- **Back-face overlays:** with native `PixelCopy`, overlays are captured automatically (§7.3) — no per-page bake decision needed. The back-face bitmap is the next page's own captured pixels.

### 15.2 Technical questions (measured evidence)

1. **Capture method — RESOLVED (§11).** JS capture is impossible (cross-origin taint); native `PixelCopy` is the only path. The architecture pivoted to native (rev 3).
2. **StPageFlip mesh math (§8) — answered during Stage 2 implementation.** The triangle-mesh deformation math is ported to Kotlin `Canvas.drawBitmapMesh`; it's pure geometry, no library dependency. Validated by the Stage 2 visual acceptance.
3. **`PixelCopy` reliability + cost — answered by Stage 2 device validation.** Confirm <30 ms capture and reliable surface readback across the device matrix; confirm next-page off-screen capture or fall back to mirror-synthesis.

> **Architecturally resolved (no longer open):** commit-on-release is normative (§3.3); capture is native `PixelCopy` (§7, §11); `none` mode does not live-drive the renderer during drag (§10); migration scope is the ~20-file surface (§12); overlay composition is automatic via `PixelCopy` (§7.3); the renderer is native, not JS (§5, rev 3).

---

## 16. Out of scope (explicit)

- ~~Native (Compose/Canvas) rendering of the curl over `PixelCopy` bitmaps.~~ **In scope as of rev 3** — the §11 spike proved JS capture impossible, so the native renderer is the primary architecture (§5, §7–§9).
- Per-orientation separate settings. One setting; the effect auto-scales by orientation (§4.3).
- Any change to touch detection, tap/drag arbitration. (The native drag detector is reused as-is; the controller consumes its existing stream.)
- Replace Foliate-js.

---

## Appendix A — Current-state references (what this replaces)

All references are by **symbol name**; re-resolve on the implementation branch after syncing.

**Stage 0 (commit `142712bc`) — DONE:**
- The Kotlin `dragAnimationMode` → `pageTurnAnimation` rename across all surfaces (constants, bridge, preference settings, UI, migration) is complete.
- `navic-reader.js` has the Stage 0 WebGL probe (`probePageTurnWebglAvailability`, `resolveEffectivePageTurnAnimation`, `webglcontextlost` handler) — **this is now dead code** (rev 3 moved rendering native) and should be removed when the native probe lands in Stage 2.

**Still to remove/replace (Stage 2+):**
- `navic-reader-page-turns.js` — `readerPageDragCurlMetrics` (the `perspective(1800px) rotateY` whole-page rotation; to be removed — native owns the curl).
- `navic-reader-page-turns.js` — `ensurePageDragPreviewLayer({ curlEnabled })` and the `data-navic-page-curl-sheet` / `data-navic-page-curl-snapshot` cloned-iframe machinery (the live-DOM-snapshot hack; to be removed).
- `navic-reader-page-turns.js` — `applyPageDragCurlSheet` `width < height * 1.12` spread heuristic (the *logic* is reused, ported to the native layout profile helper §6).
- `navic-reader-page-turns.js` — `ensurePageDragPreviewLayerChild` and the `data-navic-page-drag-preview-paper-layer` / `-border-layer` / `-stain-layer` overlay layer machinery: **no longer replaced by baking** — these JS layers stay as-is (they render the static page's overlays), and `PixelCopy` captures them automatically when the curl needs a snapshot (§7.3).
- `docs/superpowers/plans/2026-07-02-reader-whispersync-focused-file-plans.md` "Focused Plan I" — the prior turn.js decision this spec supersedes for the curl path (turn.js remains rejected; this spec defines the native replacement for the custom JS curl instead).

## Appendix B — External references

- StPageFlip (canvas mesh curl engine to extract): https://github.com/Nodlik/StPageFlip
- StPageFlip active forks to evaluate: https://www.npmjs.com/package/@cdk0507/page-flip , https://npmx.dev/package/react-pageflip-enhanced ; enumerate via https://useful-forks.github.io/
- Page Curl Shader Breakdown (cylinder + arc-length UV math): https://andrewhungblog.wordpress.com/2018/04/29/page-curl-shader-breakdown/
- Shadertoy Interactive 3D Book Flip (runnable GLSL): https://www.shadertoy.com/view/tfyfzG
- Codrops Nov 2025 fold + curvature shadow effect: https://tympanus.net/codrops/2025/11/27/letting-the-creative-process-shape-a-webgl-portfolio/
- rotateY() MDN (why CSS cannot curl): https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Values/transform-function/rotateY
- Open Source Page Flip Solutions 2026 (landscape): https://portalzine.de/open-source-page-flip-and-pdf-viewer-solutions-in-javascript-2026/
