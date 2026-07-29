package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderPageQaFaultCommandTest {
	@Test
	fun decoderAcceptsOnlyTheReaderDevAction() {
		assertTrue(
			ReaderPageQaFaultCommandDecoder.acceptsAction(
				ReaderPageQaFaultCommandDecoder.Action
			)
		)
		assertFalse(ReaderPageQaFaultCommandDecoder.acceptsAction(null))
		assertFalse(ReaderPageQaFaultCommandDecoder.acceptsAction("wrong.action"))
	}

	@Test
	fun everyFaultDecodesAsAnExactEnqueueCommand() {
		ReaderPageQaFault.entries.forEach { fault ->
			assertEquals(
				ReaderPageQaFaultCommand.Enqueue("request-1", fault),
				ReaderPageQaFaultCommandDecoder.decode(
					requestId = "request-1",
					command = "enqueue",
					faultName = fault.name
				)
			)
		}
	}

	@Test
	fun everyControlCommandDecodesToItsClosedType() {
		val commands = mapOf(
			"release-publication" to
				ReaderPageQaFaultCommand.ReleasePublication("request_2"),
			"release-relocation" to
				ReaderPageQaFaultCommand.ReleaseRelocation("request_2"),
			"release-visual-state" to
				ReaderPageQaFaultCommand.ReleaseVisualState("request_2"),
			"arm-input" to ReaderPageQaFaultCommand.ArmInput("request_2"),
			"clear-input" to ReaderPageQaFaultCommand.ClearInput("request_2"),
			"clear" to ReaderPageQaFaultCommand.Clear("request_2")
		)

		commands.forEach { (wireCommand, expected) ->
			assertEquals(
				expected,
				ReaderPageQaFaultCommandDecoder.decode(
					requestId = "request_2",
					command = wireCommand,
					faultName = "PRIVATE_EPUB_TEXT"
				)
			)
		}
	}

	@Test
	fun missingAndUnsafeRequestIdsReturnOnlyInvalidIdentity() {
		listOf(
			null,
			"",
			"contains space",
			"none",
			"NONE",
			"NoNe",
			"PRIVATE_EPUB_TEXT/SECRET_TOKEN",
			"a".repeat(65)
		).forEach { requestId ->
			val rejected = assertIs<ReaderPageQaFaultCommand.Rejected>(
				ReaderPageQaFaultCommandDecoder.decode(
					requestId = requestId,
					command = "enqueue",
					faultName = ReaderPageQaFault.FailNextPersistence.name
				)
			)
			assertEquals("invalid", rejected.requestId)
			assertEquals(
				ReaderPageQaFaultCommand.Rejection.InvalidRequestId,
				rejected.reason
			)
		}
	}

	@Test
	fun missingAndUnknownCommandsReturnTypedRejectionWithoutRawValue() {
		listOf(null, "PRIVATE_EPUB_TEXT-SECRET_TOKEN").forEach { command ->
			val rejected = assertIs<ReaderPageQaFaultCommand.Rejected>(
				ReaderPageQaFaultCommandDecoder.decode(
					requestId = "request-3",
					command = command,
					faultName = "PRIVATE_EPUB_TEXT"
				)
			)
			assertEquals("request-3", rejected.requestId)
			assertEquals(
				ReaderPageQaFaultCommand.Rejection.InvalidCommand,
				rejected.reason
			)
			assertFalse(rejected.toString().contains("PRIVATE_EPUB_TEXT"))
			assertFalse(rejected.toString().contains("SECRET_TOKEN"))
		}
	}

	@Test
	fun missingAndUnknownFaultsReturnTypedRejectionWithoutRawValue() {
		listOf(null, "PRIVATE_EPUB_TEXT_SECRET_TOKEN").forEach { fault ->
			val rejected = assertIs<ReaderPageQaFaultCommand.Rejected>(
				ReaderPageQaFaultCommandDecoder.decode(
					requestId = "request-4",
					command = "enqueue",
					faultName = fault
				)
			)
			assertEquals("request-4", rejected.requestId)
			assertEquals(
				ReaderPageQaFaultCommand.Rejection.InvalidFault,
				rejected.reason
			)
			assertFalse(rejected.toString().contains("PRIVATE_EPUB_TEXT"))
			assertFalse(rejected.toString().contains("SECRET_TOKEN"))
		}
	}
}
