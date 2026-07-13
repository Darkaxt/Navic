# Reader Bridge Generation Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The user explicitly requested inline execution without subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bind each Android `ReaderJavascriptBridge` to exactly one WebView generation and remove/deactivate it before that generation's view is destroyed.

**Architecture:** The keyed WebView generation owns an atomic reference to its exact view, an atomic disposed flag, and one bridge instance. Bridge callbacks always post to that generation's view and re-check generation/view identity on the UI thread. One idempotent disposal function deactivates the bridge, removes `NavicAndroidBridge`, clears references, and destroys the view in that order; both Compose disposal and renderer loss use it.

**Tech Stack:** Kotlin Multiplatform, Compose Android `AndroidView`, Android `WebView`, JavaScript interface, `AtomicBoolean`, `AtomicReference`, kotlin.test, Android host source-contract tests, Gradle, ADB.

---

## File Map

- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt`: make `ReaderJavascriptBridge` explicitly deactivatable and ignore messages after deactivation.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt`: create one bridge inside each `key(webViewGeneration)` lifecycle, gate callbacks by generation/view identity, and centralize ordered idempotent disposal.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderJavascriptBridgeTest.kt`: prove a deactivated bridge emits neither decoded events nor rejection diagnostics.
- Create `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderBridgeGenerationLifecycleHostContractTest.kt`: prove source ownership, callback gating, disposal order, and renderer-loss reuse of the disposal path.
- Modify `docs/superpowers/plans/2026-07-12-qa-analysis.md`: record B6 resolution and evidence.
- Modify `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`: record B6 implementation/release evidence and status.
- Modify `androidApp/build.gradle.kts`: prepare only `v1.0.11-iota14`, Android `versionCode=541`.

### Task 1: Define bridge deactivation behavior

- [x] **Step 1: Write the failing bridge lifecycle test**

Add this test to `ReaderJavascriptBridgeTest`:

```kotlin
@Test
fun deactivatedBridgeDropsEventsAndRejections() {
	val events = mutableListOf<ReaderBridgeEvent>()
	val rejections = mutableListOf<ReaderBridgeDecodeResult.Rejected>()
	val bridge = ReaderJavascriptBridge(
		onEvent = events::add,
		onRejected = rejections::add
	)

	bridge.deactivate()
	bridge.postMessage("""{"type":"ready"}""")
	bridge.postMessage("{")

	assertTrue(events.isEmpty())
	assertTrue(rejections.isEmpty())
}
```

- [x] **Step 2: Run the focused test and confirm RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderJavascriptBridgeTest
```

Expected: compilation fails because `ReaderJavascriptBridge.deactivate()` does not exist.

- [x] **Step 3: Implement minimal deactivation**

In `ReaderJavascriptBridge`, add an active flag and guard message processing:

```kotlin
@Volatile
private var active = true

fun deactivate() {
	active = false
}

@JavascriptInterface
fun postMessage(message: String) {
	if (!active) return
	messageProcessor.process(message)
}
```

This is a lifetime gate, not retry/cancellation logic. It must not add a timeout.

- [x] **Step 4: Run the focused test and confirm GREEN**

Run the command from Step 2. Expected: both `ReaderJavascriptBridgeTest` tests pass.

- [x] **Step 5: Commit the bridge lifetime primitive**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderJavascriptBridgeTest.kt
git commit -m "fix(reader): deactivate retired javascript bridges"
```

### Task 2: Bind the bridge to the keyed WebView generation

- [x] **Step 1: Write the failing Android host contract**

Create `ReaderBridgeGenerationLifecycleHostContractTest.kt`. Read `ReaderEngineWebViewHost.android.kt` with `readerEngineWebViewHostFile()` and assert all of these conditions:

```kotlin
assertFalse(
	hostText.substringBefore("key(webViewGeneration)").contains("ReaderJavascriptBridge("),
	"The bridge must not outlive the keyed WebView generation."
)
assertContains(generationBlock, "val generation = webViewGeneration")
assertContains(generationBlock, "val bridge = remember(generation)")
assertContains(generationBlock, "targetView.post")
assertContains(generationBlock, "generation == webViewGeneration")
assertContains(generationBlock, "webView === targetView")
assertTrue(disposalBlock.indexOf("bridge.deactivate()") < disposalBlock.indexOf("removeJavascriptInterface"))
assertTrue(disposalBlock.indexOf("removeJavascriptInterface") < disposalBlock.indexOf("destroy()"))
assertContains(effectBlock, "disposeGeneration(generationWebView.get())")
assertContains(rendererGoneBlock, "disposeGeneration(view)")
assertFalse(rendererGoneBlock.contains("view.destroy()"))
```

The test must isolate `generationBlock`, `disposalBlock`, `effectBlock`, and `rendererGoneBlock` with `substringAfter`/`substringBefore` so ordering assertions apply to the intended code only.

- [x] **Step 2: Run host contracts and confirm RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderBridgeGenerationLifecycleHostContractTest --tests paige.navic.reader.ReaderCommandAcknowledgementHostContractTest
```

Expected: the new contract fails because bridge creation and `DisposableEffect(Unit)` currently sit outside `key(webViewGeneration)` and no interface removal exists.

- [x] **Step 3: Implement generation-local bridge ownership**

Inside `key(webViewGeneration)`, create:

```kotlin
val generation = webViewGeneration
val generationDisposed = remember(generation) { AtomicBoolean(false) }
val generationWebView = remember(generation) { AtomicReference<WebView?>(null) }
val bridge = remember(generation) {
	ReaderJavascriptBridge(
		onEvent = bridgeEvent@{ event ->
			val targetView = generationWebView.get() ?: return@bridgeEvent
			targetView.post {
				if (
					!generationDisposed.get() &&
					generation == webViewGeneration &&
					webView === targetView
				) {
					handleReaderBridgeEvent(event)
				}
			}
		}
	)
}
```

Add `java.util.concurrent.atomic.AtomicBoolean` and `AtomicReference` imports. Remove the old process-wide `remember`ed bridge and the fallback that called `handleReaderBridgeEvent` without a live view.

- [x] **Step 4: Implement one ordered, idempotent disposal path**

Still inside the keyed generation, define a local `disposeGeneration` function/lambda that returns whether it performed first disposal:

```kotlin
val disposeGeneration: (WebView?) -> Boolean = { requestedView ->
	if (!generationDisposed.compareAndSet(false, true)) {
		false
	} else {
		bridge.deactivate()
		val targetView = generationWebView.getAndSet(null) ?: requestedView
		targetView?.removeJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)
		if (webView === targetView) webView = null
		targetView?.destroy()
		true
	}
}
```

Add `DisposableEffect(bridge, generation)` inside the key and call `disposeGeneration(generationWebView.get())` from `onDispose`. In the `AndroidView` factory, set both `webView` and `generationWebView`. In `onRenderProcessGone`, replace direct null/destroy logic with:

```kotlin
if (disposeGeneration(view) && generation == webViewGeneration) {
	webViewGeneration += 1
}
```

Keep `readerRuntimeReady = false`, the existing typed renderer-loss event, and acknowledgement ledger retention unchanged.

- [x] **Step 5: Run host contracts and bridge tests and confirm GREEN**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderJavascriptBridgeTest --tests paige.navic.reader.ReaderBridgeGenerationLifecycleHostContractTest --tests paige.navic.reader.ReaderCommandAcknowledgementHostContractTest
```

Expected: all tests pass and no existing acknowledgement assertion regresses.

- [x] **Step 6: Commit the generation lifecycle**

```powershell
git add composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderBridgeGenerationLifecycleHostContractTest.kt
git commit -m "fix(android): scope reader bridge to webview generation"
```

### Task 3: Validate the owning reader surface

- [x] **Step 1: Run focused owning suites**

Run bridge protocol/processor, command dispatch, bridge lifecycle, host contract, runtime asset, Storyteller, Foliate adapter, controller, and coordinator tests. Record exact test counts and failures from JUnit XML.

- [x] **Step 2: Run JavaScript and governance gates**

Run `node --check` for `navic-reader.js`, the Chromium command-ack runtime, reader smoke/trace smoke, page-turn model tests, source vendor verification, vendor-verifier self-test, and source attribution verification.

- [x] **Step 3: Assemble Android candidates**

Run:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:assembleReaderDev
```

Verify the debug APK's 30 packaged vendor hashes and packaged acknowledgements.

- [x] **Step 4: Validate renderer recovery through ADB**

Install `readerDev` on `emulator-5554`, open an EPUB at a non-zero locator, capture app and isolated renderer PIDs, clear logcat, and kill only the renderer. Prove:

- the app PID survives;
- a different renderer PID appears;
- generation 1 replays the same stable open ID;
- the exact pre-kill href/range CFI returns;
- `publicationReady` and `commandAck` arrive;
- AndroidRuntime has no fatal error.

### Task 4: Document, release, and clean

- [x] **Step 1: Record B6 implementation evidence**

Update the QA analysis and roadmap with source, test, and device evidence. Mark B6 as validated candidate while preserving all remaining Tranche 3 work.

- [x] **Step 2: Prepare only `iota14`**

Set `androidApp/build.gradle.kts` to `versionCode=541` and `versionName=v1.0.11-iota14`. Run `scripts/verify-android-release-version.ps1` and prove no `kappa`/`lambda` refs or release names exist.

- [x] **Step 3: Re-fetch and integrate current public master**

Fetch `fork`, run `git rev-list --left-right --count HEAD...fork/master`, and rebase this isolated branch only if public master advanced. Preserve concurrent ebook changes and rerun the owning gates after any rebase.

Validation evidence: focused owner suites passed 168/168; JavaScript, Chromium, governance, package, assembly, and ADB renderer-recovery gates passed. The debug APK SHA-256 is `ef28d2e6c4ab0b578bfa90f053dd3db4d2d156a2e69cb5d56c390b2625cfb02c`. A fresh fetch reported `3 0` against unchanged `fork/master`, so no rebase was required. Release metadata is exactly `v1.0.11-iota14` / `541`, with no local/public `kappa` or `lambda` refs.

- [ ] **Step 4: Publish and verify the Android release**

Commit/push the candidate, tag `v1.0.11-iota14`, and run `scripts/publish-github-release.ps1` with public-release readiness evidence. iOS must remain skipped. Download public `Navic.apk` and independently verify GitHub digest, SHA-256, v2 certificate, `versionCode=541`, `versionName=v1.0.11-iota14`, all 30 vendor hashes, packaged acknowledgements, and an in-place emulator upgrade/startup.

- [ ] **Step 5: Record immutable release evidence and clean**

Commit/push run ID, hashes, signing/version, and ADB evidence. Verify public `master` and release refs, then remove only `C:/Users/darka/Documents/Projects/Android/.codex-temp/navic-qa-tranche-3-bridge-lifecycle` and delete `fix/qa-tranche-3-bridge-lifecycle`. Do not modify or remove the ebook worktrees.

## Self-Review

- B6 coverage: bridge construction is generation-local; callbacks require the generation's exact live view; deactivation and interface removal precede destroy; renderer loss and Compose disposal share one idempotent path.
- Regression coverage: B5 acknowledgement ledger and renderer replay remain intact and are revalidated by host tests plus ADB renderer kill.
- Scope: no B3 capability, B8 cache policy, B15/B24 process-state, B22 storage, B23 debugging, iOS, or timeout work is mixed into this unit.
- Delivery: `iota14`/541, public artifact verification, signed in-place upgrade, and isolated-worktree cleanup are explicit.
