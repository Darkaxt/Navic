package paige.navic.reader

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/** Prepared rendering RED; never use the rendered value as an assertion operand/message. */
class ReaderCurrentDestinationPrivacyTest {
	@Test
	fun currentDestinationQueryRenderingUsesOnlyFixedRedaction() {
		val query = ReaderBridgeCommand.ResolveCurrentDestination("foliate-synthetic-private")
		assertTrue(query.toString() == "ReaderBridgeCommand.ResolveCurrentDestination(<redacted>)",
			"Query rendering must be a fixed redacted label without its source session")
	}

	@Test
	fun currentDestinationResultRenderingUsesOnlyFixedRedaction() {
		val result = result()
		assertTrue(result.toString() == "ReaderBridgeEvent.CurrentDestinationResolved(<redacted>)",
			"Result rendering must be a fixed redacted label without identity or locator payload")
	}

	@Test
	fun queryRedactionMustNotChangeExactEqualityOrWireIdentity() {
		val query = ReaderBridgeCommand.ResolveCurrentDestination("foliate-synthetic-private")
		assertTrue(query == query.copy(), "Exact query equality must survive redaction")
		assertTrue(query != query.copy(foliateSessionId = "foliate-synthetic-other"),
			"Redaction must not collapse distinct session identities")
		assertTrue(query.toJsonObject()["foliateSessionId"] == JsonPrimitive(query.foliateSessionId),
			"In-memory wire transport must preserve the exact private identity")
	}

	@Test
	fun resultRedactionMustNotChangeTypedFieldsOrExactEquality() {
		val result = result()
		assertTrue(result == result.copy(), "Exact result equality must survive redaction")
		assertTrue(result != result.copy(commandId = "synthetic-other-query"),
			"Redaction must not collapse distinct dispatch identities")
		assertTrue(result != result.copy(locator = result.locator.copy(cfi = "synthetic-other-cfi")),
			"Redaction must not weaken exact locator equality")
		assertTrue(result.destinationCommitIdentity.commitSequence == 1L,
			"Redaction must not alter the typed commit identity")
	}

	private fun result(): ReaderBridgeEvent.CurrentDestinationResolved {
		val session = "foliate-synthetic-private"
		return ReaderBridgeEvent.CurrentDestinationResolved(
			commandId = "synthetic-private-query",
			foliateSessionId = session,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 1L),
			locator = ReaderLocator(href = "synthetic-private.xhtml", cfi = "synthetic-private-cfi")
		)
	}
}
