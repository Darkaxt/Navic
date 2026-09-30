package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderCommandAcknowledgementHostContractTest {
	@Test
	fun androidHostConsumesAcksAndRetainsLedgerAcrossRendererGenerations() {
		val hostText = readerEngineWebViewHostFile().readText()
		val eventBlock = hostText
			.substringAfter("fun handleReaderBridgeEvent")
			.substringBefore("val bridge = remember")
		val rendererGoneBlock = hostText
			.substringAfter("override fun onRenderProcessGone")
			.substringBefore("post { startReaderRuntimeIfVisible() }")

		assertContains(hostText, "runtimeGeneration = webViewGeneration")
		assertContains(hostText, "ReaderWebRuntime.commandScript(dispatch)")
		assertContains(eventBlock, "is ReaderBridgeEvent.CommandAcknowledged")
		assertContains(eventBlock, "commandDispatchState.acknowledge(event.commandId)")
		assertTrue(
			eventBlock.indexOf("commandDispatchState.acknowledge(event.commandId)") <
				eventBlock.indexOf("targetView?.dispatchReadyReaderCommands()"),
			"A non-settings acknowledgement must update the ledger before dispatching the next pending command."
		)
		assertContains(eventBlock, "ReaderBridgeEvent.Ready")
		assertContains(eventBlock, "webView?.dispatchReadyReaderCommands()")
		assertContains(eventBlock, "is ReaderBridgeEvent.LocationChanged")
		assertContains(eventBlock, "commandDispatchState.observeLocator(event.locator)")
		assertTrue(
			eventBlock.indexOf("commandDispatchState.acknowledge(event.commandId)") <
				eventBlock.indexOf("currentOnEvent(ReaderEngineHostEvent.FoliateBridge(event))"),
			"Transport acknowledgements must update the host ledger before normal reader events are forwarded."
		)
		assertContains(rendererGoneBlock, "readerRuntimeReady = false")
		assertContains(rendererGoneBlock, "webViewGeneration += 1")
		assertFalse(
			rendererGoneBlock.contains("commandDispatchState = ReaderWebCommandDispatchState()"),
			"Renderer loss must retain the ledger so the next generation can replay publication and locator state."
		)
	}

	@Test
	fun settingsCommandAcquiresForegroundOwnershipBeforeJavascriptMutation() {
		val hostText = readerEngineWebViewHostFile().readText()
		val settingsDispatch = sourceBlock(
			hostText, "fun WebView.dispatchSettingsCommand", "fun WebView.dispatchReadyReaderCommands"
		)
		val boundary = sourceBlock(
			hostText, "internal class ReaderSettingsWebViewPhysicalBoundary(", "private object ReaderWebViewReleaseQueue"
		)
		val physicalDispatch = sourceBlock(boundary, "fun dispatch(script: String)", "fun acknowledge(")

		assertContains(settingsDispatch, "findReaderSettingsWebViewMutationHost()")
		assertContains(settingsDispatch, "mutation = readiness.mutation")
		assertContains(settingsDispatch, "boundary = boundary")
		assertInOrder(
			settingsDispatch,
			"acquireSettingsMutation(",
			"ReaderSettingsWebViewMutationReadiness.Ready",
			"ReaderSettingsWebViewPhysicalBoundary(targetView, readiness.mutation)",
			"activeSettingsMutation.compareAndSet(null, active)",
			"boundary.dispatch(script)"
		)
		assertInOrder(
			physicalDispatch,
			"val current = isHostCurrent()",
			"if (!current) { mutation.cancel(); return false }",
			"mutation.invokeMutation {",
			"webView.evaluateJavascript(script, null)"
		)
		assertContains(physicalDispatch, "if (!started) mutation.deferPhase { dispatch(script) }")
	}

	@Test
	fun failedSettingsCommandCancelsOwnershipAndRestartsTheRuntime() {
		val hostText = readerEngineWebViewHostFile().readText()
		val eventBlock = hostText
			.substringAfter("fun handleReaderBridgeEvent")
			.substringBefore("val bridge = remember")
		val runtimeText = readerRuntimeImplementationText()
		val dispatchBridge = runtimeText
			.substringAfter("window.NavicReaderBridge = {")
			.substringBefore("armNativePageTurnSettle")

		assertContains(dispatchBridge, "type: 'commandFailed'")
		assertContains(eventBlock, "is ReaderBridgeEvent.CommandFailed")
		assertContains(eventBlock, "active.mutation.cancel()")
		assertContains(eventBlock, "readerRuntimeReady = false")
		assertContains(eventBlock, "webViewGeneration += 1")
	}

	@Test
	fun settingsAckWaitsForCurrentWebViewVisualStateBeforePublishingRasterKey() {
		val hostText = readerEngineWebViewHostFile().readText()
		val eventBlock = hostText
			.substringAfter("fun handleReaderBridgeEvent")
			.substringBefore("val bridge = remember")
		val visualCommit = sourceBlock(
			hostText, "fun WebView.commitSettingsPresentation", "fun handleReaderBridgeEvent"
		)
		val boundary = sourceBlock(
			hostText, "internal class ReaderSettingsWebViewPhysicalBoundary(", "private object ReaderWebViewReleaseQueue"
		)
		val visualBoundary = boundary.substringAfter("fun acknowledge(", missingDelimiterValue = "")
		val mutationText = readerAndroidFile("ReaderSettingsWebViewMutationCoordinator.android.kt").readText()
		val completePresentation = sourceBlock(mutationText, "fun completePresentation(", "private fun publishNextPhase(")
		val publication = sourceBlock(mutationText, "private fun publishNextPhase(", "fun cancel()")

		assertContains(eventBlock, "acknowledgedCommand(event.commandId)")
		assertContains(eventBlock, "is ReaderBridgeCommand.ApplySettings")
		assertContains(eventBlock, "targetView.commitSettingsPresentation(")
		assertContains(visualCommit, "settingsVisualStateSequence.incrementAndGet()")
		assertContains(visualCommit, "active.boundary.acknowledge(")
		assertContains(visualCommit, "snapshotKey = snapshotKey")
		assertContains(visualCommit, "onCommitted = {")
		assertContains(visualCommit, "dispatchNext = {")
		assertContains(visualCommit, "isVisualCurrent = { settingsVisualStateSequence.get() == sequence }")
		assertInOrder(
			visualBoundary,
			"val current = isHostCurrent()",
			"if (!current) { mutation.cancel(); return false }",
			"mutation.invokeMutationWithResult<Long>",
			"webView.postVisualStateCallback(sequence,",
			"override fun onComplete(requestId: Long) { result(requestId) }",
			"val currentResult = isHostCurrent() && isVisualCurrent()",
			"if (requestId != sequence || !currentResult) mutation.cancel()",
			"else mutation.completePresentation(snapshotKey, onCommitted, dispatchNext)"
		)
		assertInOrder(
			completePresentation,
			"this.snapshotKey = snapshotKey",
			"this.onCommitted = onCommitted",
			"this.dispatchNext = dispatchNext",
			"return publishNextPhase()"
		)
		assertInOrder(
			publication,
			"ownership.invokeLivePublication(claim, generation)",
			"when (publicationPhase++)",
			"0 -> onSnapshotCommitted(checkNotNull(snapshotKey))",
			"1 -> onCommitted()",
			"2 -> {",
			"ownership.releaseLive(claim, dispatchNext)"
		)
		assertInOrder(
			visualCommit,
			"onCommitted = {",
			"commandDispatchState.acknowledge(active.commandId)",
			"ReaderEngineHostEvent.SettingsPresentationCommitted(snapshotKey)",
			"dispatchNext = {"
		)
	}

	private fun sourceBlock(source: String, start: String, end: String): String {
		assertContains(source, start)
		val remainder = source.substringAfter(start)
		assertContains(remainder, end)
		return remainder.substringBefore(end)
	}

	private fun assertInOrder(source: String, vararg statements: String) {
		statements.forEach { assertContains(source, it) }
		statements.toList().zipWithNext().forEach { (before, after) ->
			assertTrue(source.indexOf(before) < source.indexOf(after), "'$before' must precede '$after'.")
		}
	}
}
