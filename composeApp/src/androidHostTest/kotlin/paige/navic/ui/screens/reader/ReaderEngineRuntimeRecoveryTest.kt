package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderEngineRuntimeRecoveryTest {
	@Test
	fun hiddenIncompleteRuntimeRetriesExactlyOnceWhenVisible() {
		val recovery = ReaderEngineRuntimeRecovery()

		recovery.onRuntimeLoadStarted()
		assertFalse(recovery.onWindowVisibilityChanged(visible = false))
		assertTrue(recovery.onWindowVisibilityChanged(visible = true))
		assertFalse(recovery.onWindowVisibilityChanged(visible = true))
	}

	@Test
	fun hiddenGenerationStartsExactlyOnceWhenVisible() {
		val gate = ReaderEngineRuntimeStartGate()

		assertFalse(gate.startIfVisible(visible = false))
		assertTrue(gate.startIfVisible(visible = true))
		assertFalse(gate.startIfVisible(visible = true))
	}

	@Test
	fun readyRuntimeCancelsPendingVisibilityRetry() {
		val recovery = ReaderEngineRuntimeRecovery()

		recovery.onRuntimeLoadStarted()
		recovery.onWindowVisibilityChanged(visible = false)
		recovery.onRuntimeReady()

		assertFalse(recovery.onWindowVisibilityChanged(visible = true))
	}

	@Test
	fun resetCancelsPendingVisibilityRetry() {
		val recovery = ReaderEngineRuntimeRecovery()

		recovery.onRuntimeLoadStarted()
		recovery.onWindowVisibilityChanged(visible = false)
		recovery.reset()

		assertFalse(recovery.onWindowVisibilityChanged(visible = true))
	}

	@Test
	fun androidHostReplacesTheInterruptedRuntimeGenerationWhenItsWindowReturns() {
		val source = readerEngineWebViewHostSource()
		val factory = source.substringAfter("factory = {")
			.substringBefore("onRelease = { view ->")
		val restart = source.substringAfter("fun restartInterruptedReaderRuntime(")
			.substringBefore("fun WebView.dispatchReadyReaderCommands()")
		val runtimeStart = source.substringAfter("fun WebView.startReaderRuntimeIfVisible()")
			.substringBefore("AndroidView(")
		val loadStartedIndex = runtimeStart.indexOf("runtimeRecovery.onRuntimeLoadStarted()")
		val configureIndex = runtimeStart.indexOf("ReaderWebRuntime.configure(")

		assertContains(source, "private class ReaderEngineWebView")
		assertContains(source, "override fun onWindowVisibilityChanged(visibility: Int)")
		assertContains(source, "runtimeRecovery.onWindowVisibilityChanged(")
		assertContains(source, "restartInterruptedReaderRuntime(")
		assertContains(restart, "retireGeneration()")
		assertContains(restart, "webViewGeneration += 1")
		assertFalse(restart.contains("loadUrl("))
		assertContains(source, "runtimeRecovery.onRuntimeReady()")
		assertContains(runtimeStart, "windowVisibility == View.VISIBLE")
		assertContains(factory, "post { startReaderRuntimeIfVisible() }")
		assertTrue(loadStartedIndex >= 0)
		assertTrue(configureIndex > loadStartedIndex)
	}

	@Test
	fun androidHostIgnoresRendererLossFromARetiredGeneration() {
		val source = readerEngineWebViewHostSource()
		val rendererLoss = source.substringAfter("override fun onRenderProcessGone(")
			.substringBefore("return true\n\t\t\t\t\t\t}")
		val staleGuardIndex = rendererLoss.indexOf("generationDisposed.get()")
		val resetIndex = rendererLoss.indexOf("runtimeRecovery.reset()")

		assertTrue(staleGuardIndex >= 0)
		assertContains(rendererLoss, "generation != webViewGeneration")
		assertContains(rendererLoss, "webView !== view")
		assertTrue(resetIndex > staleGuardIndex)
	}

	@Test
	fun visibilityChangesBeforeRuntimeLoadDoNotScheduleRetry() {
		val recovery = ReaderEngineRuntimeRecovery()

		assertFalse(recovery.onWindowVisibilityChanged(visible = false))
		assertFalse(recovery.onWindowVisibilityChanged(visible = true))
	}
}

private fun readerEngineWebViewHostSource(): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate ReaderEngineWebViewHost.android.kt")
}
