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
				eventBlock.indexOf("webView?.dispatchReadyReaderCommands()", eventBlock.indexOf("is ReaderBridgeEvent.CommandAcknowledged")),
			"Acknowledging one command must immediately dispatch the next pending command."
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
}
