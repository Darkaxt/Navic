package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderBridgeMessageProcessorTest {
	@Test
	fun isolatedFailureLogsOnceAndDecodedEventResetsTheEpisode() {
		val events = mutableListOf<ReaderBridgeEvent>()
		val rejections = mutableListOf<ReaderBridgeDecodeResult.Rejected>()
		val processor = ReaderBridgeMessageProcessor(
			onEvent = events::add,
			onRejected = rejections::add
		)

		processor.process("{")
		processor.process("[]")

		assertEquals(1, rejections.size)
		assertTrue(events.isEmpty())

		processor.process("""{"type":"ready"}""")
		processor.process("""{"type":"unknown"}""")

		assertEquals(1, events.size)
		assertEquals(ReaderBridgeEvent.Ready, events.single())
		assertEquals(2, rejections.size)
		assertEquals(ReaderBridgeDecodeFailure.UnknownType, rejections.last().failure)
	}

	@Test
	fun persistentFailureEmitsOneErrorPerConsecutiveFailureEpisode() {
		val events = mutableListOf<ReaderBridgeEvent>()
		val rejections = mutableListOf<ReaderBridgeDecodeResult.Rejected>()
		val processor = ReaderBridgeMessageProcessor(
			onEvent = events::add,
			onRejected = rejections::add
		)

		repeat(4) { processor.process("{") }

		assertEquals(1, rejections.size)
		val firstError = assertIs<ReaderBridgeEvent.Error>(events.single())
		assertEquals("reader_bridge_protocol", firstError.code)
		assertEquals("Reader communication failed repeatedly. Close and reopen this book.", firstError.message)

		processor.process("""{"type":"ready"}""")
		repeat(3) { processor.process("[]") }

		assertEquals(2, rejections.size)
		assertEquals(2, events.filterIsInstance<ReaderBridgeEvent.Error>().size)
		assertEquals(ReaderBridgeEvent.Ready, events[1])
	}
}
