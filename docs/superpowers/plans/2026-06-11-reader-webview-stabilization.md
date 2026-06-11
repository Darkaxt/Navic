# Reader WebView Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a laptop-testable WebView reader harness and use it to stabilize EPUB pagination, texture transitions, cover suppression, and renderer CSS before touching APK-native reader code.

**Architecture:** Add a focused Node/Playwright harness around the exact Android reader assets under `composeApp/src/androidMain/assets/reader`. The harness serves those assets locally, drives reader bridge commands in Chromium, records structured trace events, and gives renderer fixes a repeatable validation loop independent from phone releases.

**Tech Stack:** Node.js, Playwright/Chromium when available, JavaScript ES modules, Android reader assets, foliate-js, Gradle host tests for packaged-asset guards.

## Execution Status

Phase 1 has produced committed WebView/harness slices:

- `d89ea815 docs(reader): register stabilization plan`
- `0738edc5 docs(reader): plan webview stabilization`
- `eec83c0b test(reader): add webview trace harness`
- `f3f28aaf test(reader): serve reader assets locally`
- `ecdacb57 test(reader): capture browser reader traces`
- `16db0b0e test(reader): drive epub frontmatter fixture`
- `f2923801 fix(reader): stabilize webview page labels`
- `741865de fix(reader): sync paper texture movement`
- `41ab92b1 test(reader): guard shell cover navigation`
- CSS smoke harness coverage for paragraph spacing, theme backgrounds, hyperlink styling, image sepia toggling, and paper texture layers.

Current phase boundary:

- WebView pagination labels, texture drag direction, shell-cover handoff, and renderer CSS behavior now have laptop harness coverage.
- APK/native touch ownership belongs to Phase 2 and should remain separated from renderer fixes.
- Android release work should start only after the Phase 2 native-overlay slice is committed and verified.

---

## File Structure

- Create `tools/reader-harness/package.json`: local harness scripts and dependency declaration.
- Create `tools/reader-harness/src/serve-reader-assets.mjs`: small static server for APK reader assets and ignored local fixtures.
- Create `tools/reader-harness/src/run-reader-harness.mjs`: CLI runner that starts the server, opens Chromium, loads `index.html`, injects bridge tracing, and writes JSON traces.
- Create `tools/reader-harness/src/reader-trace-assertions.mjs`: reusable assertions for page labels, texture keys, cover suppression, and console errors.
- Create `tools/reader-harness/fixtures/README.md`: explains local ignored fixture placement and the synthetic fixture requirement.
- Modify `composeApp/src/androidMain/assets/reader/navic-reader.js`: add trace hooks and later renderer state fixes.
- Modify `composeApp/src/androidMain/assets/reader/index.html`: only if the harness needs a non-production-safe testing hook; production behavior must remain unchanged.
- Modify `.gitignore`: ignore local EPUB/PDF fixtures and harness output.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`: add lightweight guard assertions after runtime hooks exist.

## Task 1: Register Phase 1 Plan

- [x] **Step 1: Write the plan document**

Create `docs/superpowers/plans/2026-06-11-reader-webview-stabilization.md` with this Phase 1-only execution plan.

- [ ] **Step 2: Validate the plan document**

Run:

```powershell
rg -n "T[B]D|T[O]DO|F[I]XME|\?\?" docs\superpowers\plans\2026-06-11-reader-webview-stabilization.md
git diff --check -- docs\superpowers\plans\2026-06-11-reader-webview-stabilization.md
```

Expected:

```text
rg exits 1 with no matches
git diff --check exits 0
```

- [ ] **Step 3: Commit the plan**

Run:

```powershell
git add docs\superpowers\plans\2026-06-11-reader-webview-stabilization.md
git commit -m "docs(reader): plan webview stabilization"
```

Expected: one commit containing only the plan file.

## Task 2: Add A Failing Harness Smoke Test

**Files:**

- Create `tools/reader-harness/package.json`
- Create `tools/reader-harness/src/run-reader-harness.mjs`

- [ ] **Step 1: Add the initial runner that deliberately requires trace support**

Create `tools/reader-harness/package.json`:

```json
{
  "name": "navic-reader-harness",
  "private": true,
  "type": "module",
  "scripts": {
    "smoke": "node src/run-reader-harness.mjs --mode smoke"
  },
  "dependencies": {
    "playwright": "^1.44.0"
  }
}
```

Create `tools/reader-harness/src/run-reader-harness.mjs`:

```javascript
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const repoRoot = path.resolve(import.meta.dirname, '../../..')
const readerBridge = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader.js')
const bridgeText = fs.readFileSync(readerBridge, 'utf8')

if (!bridgeText.includes('__navicReaderTrace')) {
  console.error('Reader harness requires window.__navicReaderTrace instrumentation in navic-reader.js')
  process.exit(1)
}

console.log('reader harness smoke passed')
```

- [ ] **Step 2: Run the smoke test and verify RED**

Run:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode smoke
```

Expected failure:

```text
Reader harness requires window.__navicReaderTrace instrumentation in navic-reader.js
```

## Task 3: Add Minimal Trace Instrumentation

**Files:**

- Modify `composeApp/src/androidMain/assets/reader/navic-reader.js`

- [ ] **Step 1: Add trace collector helpers**

Add a small production-safe helper near the top-level constants:

```javascript
const readerTrace = (type, payload = {}) => {
  const trace = window.__navicReaderTrace
  if (!trace || typeof trace.push !== 'function') return
  trace.push({
    type,
    timestamp: Date.now(),
    payload
  })
}
```

- [ ] **Step 2: Trace relocation, location posts, and texture updates**

Call `readerTrace('relocate:raw', detail)` at the start of `onRelocate(detail)`.

Call `readerTrace('location:post', message)` immediately before posting `locationChanged`.

Call `readerTrace('texture:update', { key, baseAsset, borderAsset, offset })` from the paper texture update path using the actual variable names present in the implementation.

- [ ] **Step 3: Run the smoke test and verify GREEN**

Run:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode smoke
```

Expected:

```text
reader harness smoke passed
```

- [ ] **Step 4: Validate JavaScript syntax**

Run:

```powershell
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Expected: exit 0.

- [ ] **Step 5: Commit Task 2 and Task 3 together**

Run:

```powershell
git add tools\reader-harness composeApp\src\androidMain\assets\reader\navic-reader.js
git commit -m "test(reader): add webview trace harness"
```

Expected: one microdeliverable commit with harness smoke and trace hooks.

## Task 4: Serve Real Reader Assets Locally

**Files:**

- Create `tools/reader-harness/src/serve-reader-assets.mjs`
- Modify `tools/reader-harness/src/run-reader-harness.mjs`
- Modify `.gitignore`
- Create `tools/reader-harness/fixtures/README.md`

- [ ] **Step 1: Write a failing runner path that expects asset serving**

Extend the runner so `--mode serve-smoke` starts a server and fetches `/index.html`. Before the server exists this mode must fail with a module-not-found or missing-export error.

- [ ] **Step 2: Implement the static asset server**

Serve files from `composeApp/src/androidMain/assets/reader` with content types for `.html`, `.js`, `.json`, `.css`, `.png`, `.ttf`, `.otf`, `.wasm`, `.epub`, and `.pdf`. Reject path traversal by resolving every request and verifying the resolved path starts with the asset root or fixture root.

- [ ] **Step 3: Ignore local fixtures and traces**

Add:

```gitignore
tools/reader-harness/fixtures/local/
tools/reader-harness/output/
```

- [ ] **Step 4: Verify server smoke**

Run:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode serve-smoke
```

Expected: the runner reports a 200 response for `index.html` and confirms it contains `navic-reader.js`.

- [ ] **Step 5: Commit**

Run:

```powershell
git add .gitignore tools\reader-harness
git commit -m "test(reader): serve reader assets locally"
```

## Task 5: Add Browser Trace Capture

**Files:**

- Modify `tools/reader-harness/src/run-reader-harness.mjs`
- Create `tools/reader-harness/src/reader-trace-assertions.mjs`

- [ ] **Step 1: Write failing trace assertion mode**

Add `--mode trace-smoke`, which launches Chromium, initializes `window.__navicReaderTrace = []` before scripts run, loads the local `index.html`, and expects at least one `runtime:ready` trace event. It should fail before `navic-reader.js` emits that event.

- [ ] **Step 2: Emit runtime ready trace**

In `navic-reader.js`, emit `readerTrace('runtime:ready', { engine: 'foliate-js' })` after the runtime object is installed.

- [ ] **Step 3: Verify trace smoke**

Run:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode trace-smoke
```

Expected: trace JSON includes `runtime:ready` and no console errors.

- [ ] **Step 4: Commit**

Run:

```powershell
git add tools\reader-harness composeApp\src\androidMain\assets\reader\navic-reader.js
git commit -m "test(reader): capture browser reader traces"
```

## Task 6: Add Cover And Page-State Regression Fixture

**Files:**

- Modify `tools/reader-harness/src/run-reader-harness.mjs`
- Modify `tools/reader-harness/src/reader-trace-assertions.mjs`
- Modify `composeApp/src/androidMain/assets/reader/navic-reader.js`

- [ ] **Step 1: Add a fixture-driven mode**

Add `--mode epub-frontmatter --fixture <path>` that loads a local EPUB fixture, advances through at least six next-page commands, and writes `tools/reader-harness/output/epub-frontmatter.trace.json`.

- [ ] **Step 2: Add assertions that fail on current behavior**

Assert:

- no visible location event is posted for a suppressed cover
- page labels are stable and monotonic across front-matter transitions
- texture keys change deterministically per committed page
- texture offset sign does not invert during area transitions

- [ ] **Step 3: Implement cover suppression and committed page-state fixes**

Modify `navic-reader.js` so raw relocation events become candidates and visible state is posted only after cover suppression and area-transition stability checks. Keep this change isolated to renderer state; do not add native APK touch code in this task.

- [ ] **Step 4: Verify fixture mode**

Run:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-frontmatter --fixture tools\reader-harness\fixtures\local\frontmatter.epub
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Expected: the trace assertions pass and JavaScript syntax is valid.

- [ ] **Step 5: Commit**

Run:

```powershell
git add tools\reader-harness composeApp\src\androidMain\assets\reader\navic-reader.js
git commit -m "fix(reader): stabilize epub frontmatter pagination"
```

## Task 7: Add Renderer CSS Regression Checks

**Files:**

- Modify `tools/reader-harness/src/run-reader-harness.mjs`
- Modify `tools/reader-harness/src/reader-trace-assertions.mjs`
- Modify `composeApp/src/androidMain/assets/reader/navic-reader.js`

- [ ] **Step 1: Add CSS assertion mode**

Add `--mode css-smoke --fixture <path>` and assertions for paragraph spacing, theme background propagation, hyperlink affordance, image tint toggling, and paper layer presence.

- [ ] **Step 2: Fix only failing renderer CSS behavior**

Apply renderer CSS/settings fixes in `navic-reader.js` and reader document injection paths only. Keep the APK untouched.

- [ ] **Step 3: Verify CSS mode and syntax**

Run:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tools\reader-harness\fixtures\local\frontmatter.epub
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
```

Expected: CSS assertions pass and JavaScript syntax is valid.

- [ ] **Step 4: Commit**

Run:

```powershell
git add tools\reader-harness composeApp\src\androidMain\assets\reader\navic-reader.js
git commit -m "fix(reader): validate renderer css behavior"
```

## Phase 1 Completion Gate

Run before moving to APK/native touch work:

```powershell
node tools\reader-harness\src\run-reader-harness.mjs --mode smoke
node tools\reader-harness\src\run-reader-harness.mjs --mode serve-smoke
node tools\reader-harness\src\run-reader-harness.mjs --mode trace-smoke
node tools\reader-harness\src\run-reader-harness.mjs --mode epub-frontmatter --fixture tools\reader-harness\fixtures\local\frontmatter.epub
node tools\reader-harness\src\run-reader-harness.mjs --mode css-smoke --fixture tools\reader-harness\fixtures\local\frontmatter.epub
node --check composeApp\src\androidMain\assets\reader\navic-reader.js
git diff --check
```

Expected: all commands exit 0.
