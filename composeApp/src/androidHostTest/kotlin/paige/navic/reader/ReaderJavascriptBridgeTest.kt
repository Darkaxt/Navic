package paige.navic.reader

import paige.navic.ui.screens.reader.engineDebugLabel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderJavascriptBridgeTest {
	@Test
	fun javascriptBridgeReportsTypedFailureAndSurfacesPersistentProtocolError() {
		val events = mutableListOf<ReaderBridgeEvent>()
		val rejections = mutableListOf<ReaderBridgeDecodeResult.Rejected>()
		val bridge = ReaderJavascriptBridge(
			onEvent = events::add,
			onRejected = rejections::add
		)

		repeat(3) { bridge.postMessage("{") }

		assertEquals(1, rejections.size)
		assertEquals(ReaderBridgeDecodeFailure.MalformedJson, rejections.single().failure)
		assertEquals("[redacted-reader-bridge-payload]", rejections.single().rawMessage)
		assertEquals(
			"reader_bridge_protocol",
			assertIs<ReaderBridgeEvent.Error>(events.single()).code
		)
	}

	@Test
	fun duplicatePageDiagnosticLogLabelContainsOnlyOrdinalsAndBooleans() {
		val event = ReaderBridgeEvent.DuplicatePageSuspected(
			currentPageOrdinal = 11,
			previousPageOrdinal = 7,
			plainTextSame = true,
			locatorSame = false
		)

		assertEquals(
			"duplicate-page suspected current=11 previous=7 " +
				"plainTextSame=true locatorSame=false",
			event.engineDebugLabel()
		)
	}

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
}
