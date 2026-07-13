package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderBridgeGenerationLifecycleHostContractTest {
	@Test
	fun androidHostScopesBridgeAndDisposesItBeforeItsWebView() {
		val hostText = readerEngineWebViewHostFile().readText()
		val beforeGeneration = hostText.substringBefore("key(webViewGeneration) {")
		val generationBlock = hostText
			.substringAfter("key(webViewGeneration) {")
			.substringBefore("\n\t\tAndroidView(")
		val rendererGoneBlock = hostText
			.substringAfter("override fun onRenderProcessGone")
			.substringBefore("return true")

		assertFalse(
			beforeGeneration.contains("ReaderJavascriptBridge("),
			"The bridge must not outlive the keyed WebView generation."
		)
		assertContains(generationBlock, "val generation = webViewGeneration")
		assertContains(generationBlock, "val generationDisposed = remember(generation) { AtomicBoolean(false) }")
		assertContains(generationBlock, "val generationWebView = remember(generation) { AtomicReference<WebView?>(null) }")
		assertContains(generationBlock, "val bridge = remember(generation)")
		assertContains(generationBlock, "targetView.post")
		assertContains(generationBlock, "!generationDisposed.get()")
		assertContains(generationBlock, "generation == webViewGeneration")
		assertContains(generationBlock, "webView === targetView")
		assertContains(generationBlock, "val disposeGeneration: (WebView?) -> Boolean")
		assertTrue(
			generationBlock.indexOf("bridge.deactivate()") <
				generationBlock.indexOf("removeJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)"),
			"The retired bridge must be deactivated before its JavaScript interface is removed."
		)
		assertTrue(
			generationBlock.indexOf("removeJavascriptInterface(ReaderWebRuntime.AndroidBridgeName)") <
				generationBlock.indexOf("destroy()"),
			"The JavaScript interface must be removed before the generation WebView is destroyed."
		)
		assertContains(generationBlock, "DisposableEffect(bridge, generation)")
		assertContains(generationBlock, "disposeGeneration(generationWebView.get())")
		assertContains(rendererGoneBlock, "disposeGeneration(view)")
		assertFalse(
			rendererGoneBlock.contains("view.destroy()"),
			"Renderer loss must use the same ordered, idempotent disposal path as Compose disposal."
		)
	}
}
