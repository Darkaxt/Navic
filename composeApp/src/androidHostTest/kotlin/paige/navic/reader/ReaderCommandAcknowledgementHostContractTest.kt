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
		val settingsDispatch = hostText
			.substringAfter("fun WebView.dispatchSettingsCommand")
			.substringBefore("fun WebView.dispatchReadyReaderCommands")

		assertContains(settingsDispatch, "findReaderSettingsWebViewMutationHost()")
		assertContains(settingsDispatch, "acquireSettingsMutation(")
		assertContains(settingsDispatch, "ReaderSettingsWebViewMutationReadiness.Ready")
		assertTrue(
			settingsDispatch.indexOf("ReaderSettingsWebViewMutationReadiness.Ready") <
				settingsDispatch.indexOf("evaluateJavascript("),
			"ApplySettings must wait for foreground ownership and passive restoration before mutating the WebView."
		)
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
		val visualCommit = hostText
			.substringAfter("fun WebView.commitSettingsPresentation")
			.substringBefore("fun handleReaderBridgeEvent")

		assertContains(eventBlock, "acknowledgedCommand(event.commandId)")
		assertContains(eventBlock, "is ReaderBridgeCommand.ApplySettings")
		assertContains(visualCommit, "postVisualStateCallback")
		assertContains(visualCommit, "settingsVisualStateSequence")
		assertContains(visualCommit, "mutation.isCurrent()")
		assertContains(visualCommit, "mutation.commit(snapshotKey)")
		assertContains(
			visualCommit,
			"commandDispatchState.acknowledge(active.commandId)"
		)
		assertContains(
			visualCommit,
			"ReaderEngineHostEvent.SettingsPresentationCommitted("
		)
		assertTrue(
			visualCommit.indexOf("postVisualStateCallback") <
				visualCommit.indexOf("ReaderEngineHostEvent.SettingsPresentationCommitted("),
			"Raster invalidation must follow the WebView visual-state callback."
		)
	}
}
