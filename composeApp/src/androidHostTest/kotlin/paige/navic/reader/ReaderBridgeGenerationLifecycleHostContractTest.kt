package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderBridgeGenerationLifecycleHostContractTest {
	@Test
	fun androidHostRetiresBridgeBeforeHolderDetachAndDestroysWebViewOnRelease() {
		val hostText = readerEngineWebViewHostFile().readText()
		val beforeGeneration = hostText.substringBefore("key(webViewGeneration) {")
		val generationBlock = hostText
			.substringAfter("key(webViewGeneration) {")
			.substringBefore("\n\t\tAndroidView(")
		val retireBlock = generationBlock
			.substringAfter("val retireGeneration: () -> Boolean")
			.substringBefore("val releaseGeneration: (WebView?) -> Boolean")
		val releaseBlock = generationBlock
			.substringAfter("val releaseGeneration: (WebView?) -> Boolean")
			.substringBefore("DisposableEffect(bridge, generation)")
		val compositionDisposeBlock = generationBlock
			.substringAfter("DisposableEffect(bridge, generation)")
			.substringBefore("\n\t\t}")
		val releaseQueue = hostText
			.substringAfter("private object ReaderWebViewReleaseQueue")
			.substringBefore("@Composable")
		val androidViewBlock = hostText
			.substringAfter("\n\t\tAndroidView(")
			.substringBefore("\n\t}")
		val rendererGoneBlock = hostText
			.substringAfter("override fun onRenderProcessGone")
			.substringBefore("return true")

		assertFalse(
			beforeGeneration.contains("ReaderJavascriptBridge("),
			"The bridge must not outlive the keyed WebView generation."
		)
		assertContains(generationBlock, "val generation = webViewGeneration")
		assertContains(generationBlock, "val generationDisposed = remember(generation) { AtomicBoolean(false) }")
		assertContains(generationBlock, "val generationReleased = remember(generation) { AtomicBoolean(false) }")
		assertContains(generationBlock, "val generationWebView = remember(generation) { AtomicReference<WebView?>(null) }")
		assertContains(generationBlock, "val bridge = remember(generation)")
		assertContains(generationBlock, "targetView.post")
		assertContains(generationBlock, "!generationDisposed.get()")
		assertContains(generationBlock, "generation == webViewGeneration")
		assertContains(generationBlock, "webView === targetView")
		assertContains(generationBlock, "val retireGeneration: () -> Boolean")
		assertContains(generationBlock, "val releaseGeneration: (WebView?) -> Boolean")
		val retirementCasIndex = retireBlock.indexOf(
			"generationDisposed.compareAndSet(false, true)"
		)
		val deactivateIndex = retireBlock.indexOf("bridge.deactivate()")
		assertTrue(
			retirementCasIndex >= 0 && deactivateIndex > retirementCasIndex,
			"Bridge retirement must be owned by the generation-disposal compare-and-set."
		)
		val retireIndex = releaseBlock.indexOf("retireGeneration()")
		val releaseCasIndex = releaseBlock.indexOf(
			"generationReleased.compareAndSet(false, true)"
		)
		val removeBridgeIndex = releaseBlock.indexOf(
			"removeJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)"
		)
		val destroyIndex = releaseBlock.indexOf("destroy()")
		assertTrue(
			retireIndex >= 0 &&
				releaseCasIndex > retireIndex &&
				removeBridgeIndex > releaseCasIndex &&
				destroyIndex > removeBridgeIndex,
			"Released WebView cleanup must retire first, remain single-owner, remove the bridge, then destroy."
		)
		assertContains(generationBlock, "DisposableEffect(bridge, generation)")
		assertContains(compositionDisposeBlock, "retireGeneration()")
		assertFalse(
			compositionDisposeBlock.contains("releaseGeneration(") ||
				compositionDisposeBlock.contains("removeJavascriptInterface(") ||
				compositionDisposeBlock.contains("destroy()"),
			"Composition disposal must not mutate an attached WebView before its AndroidView holder detaches."
		)
		assertContains(releaseQueue, "Handler(Looper.getMainLooper())")
		assertContains(releaseQueue, "handler.post { release() }")
		assertContains(androidViewBlock, "onRelease = { view ->")
		assertContains(
			androidViewBlock,
			"ReaderWebViewReleaseQueue.enqueue {\n\t\t\t\t\treleaseGeneration(view)\n\t\t\t\t}"
		)
		assertTrue(
			Regex("releaseGeneration\\(view\\)")
				.findAll(androidViewBlock.substringAfter("onRelease = { view ->"))
				.count() == 1,
			"AndroidView release must perform destructive cleanup exactly once and only inside the posted callback."
		)
		assertContains(rendererGoneBlock, "retireGeneration()")
		assertFalse(
			rendererGoneBlock.contains("releaseGeneration(") || rendererGoneBlock.contains("view.destroy()"),
			"Renderer loss must retire the generation and let AndroidView release destroy it after holder detach."
		)
	}
}
