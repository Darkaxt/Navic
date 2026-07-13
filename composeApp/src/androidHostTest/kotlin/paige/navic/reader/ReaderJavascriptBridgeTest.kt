package paige.navic.reader

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
		assertEquals("{", rejections.single().rawMessage)
		assertEquals(
			"reader_bridge_protocol",
			assertIs<ReaderBridgeEvent.Error>(events.single()).code
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
