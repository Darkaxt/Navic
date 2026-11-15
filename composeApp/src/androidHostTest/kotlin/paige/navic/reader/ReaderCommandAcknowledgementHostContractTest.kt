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
	fun failedOpenIsReportedAsCommandFailureAndRetiredWithoutRuntimeRestart() {
		val runtimeText = readerRuntimeImplementationText()
		val openPublication = runtimeText
			.substringAfter("async openPublication({")
			.substringBefore("\n  onInternalLink(")
		for (code in listOf("missing_url", "missing_session")) {
			val missing = openPublication.substringAfter("code: '$code'")
			assertTrue(
				missing.indexOf("throw new Error('$code')") in 0 until missing.indexOf("\n    }"),
				"A missing $code open must reject so the bridge posts commandFailed, not commandAck (#8)."
			)
		}
		val openCatch = openPublication.substringAfterLast("} catch (error) {")
		assertTrue(
			openCatch.indexOf("reportError(error, 'open_failed')") in 0 until openCatch.indexOf("throw error"),
			"A rejected open must report open_failed and then reject (#8)."
		)

		val eventBlock = readerEngineWebViewHostFile().readText()
			.substringAfter("fun handleReaderBridgeEvent")
			.substringBefore("val bridge = remember")
		val failedBlock = eventBlock.substringAfter("is ReaderBridgeEvent.CommandFailed ->")
		val openBranch = failedBlock
			.substringAfter("if (failed is ReaderBridgeCommand.OpenPublication) {")
			.substringBefore("\n\t\t\t\t}")
		assertTrue(
			failedBlock.indexOf("if (failed is ReaderBridgeCommand.OpenPublication)") in 0 until
				failedBlock.indexOf("webViewGeneration += 1"),
			"A failed open must be handled before the runtime-restart path."
		)
		assertContains(openBranch, "commandDispatchState.acknowledge(event.commandId)")
		assertContains(openBranch, "return")
		assertFalse(openBranch.contains("webViewGeneration"), "A deterministic open failure must not rebuild the WebView.")
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
