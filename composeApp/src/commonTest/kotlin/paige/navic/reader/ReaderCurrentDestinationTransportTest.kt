package paige.navic.reader

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Parser-only prerequisite tests. All input is synthetic; no publication content or locator
 * strings are used. Fixture IDs describe wire syntax, not a dispatched semantic request.
 *
 * Positives assert the typed query wire envelope and typed result fields. The original 17
 * recognition/rejection/control cases remain; decoding alone never proves that an executor
 * owns or may consume a response.
 */
class ReaderCurrentDestinationTransportTest {
	@Test
	fun typedQuerySerializesActualDispatchIdAndExplicitSessionWithoutInventingDestination() {
		val command = ReaderBridgeCommand.ResolveCurrentDestination(SessionId)
		val dispatch = ReaderBridgeDispatchCommand(CommandId, command)
		assertEquals("resolveCurrentDestination", command.type)
		assertTrue(SessionId == command.foliateSessionId, "Query must retain its exact explicit session")
		assertTrue(
			buildJsonObject {
				put("commandId", CommandId)
				put("type", "resolveCurrentDestination")
				put("foliateSessionId", SessionId)
			} == dispatch.toJsonObject(),
			"Query must serialize exactly the dispatch ID and session envelope"
		)
		assertTrue(
			"window.NavicReaderBridge.dispatch(${dispatch.toJsonObject()});" == dispatch.toJavaScript(),
			"Query must use the actual dispatch JavaScript envelope"
		)
	}

	@Test
	fun typedQueryRejectsBlankPaddedAndUnboundSession() {
		listOf("", " ", "\t\n", " $SessionId", "$SessionId ").forEach { session ->
			assertFailsWith<IllegalArgumentException> {
				ReaderBridgeCommand.ResolveCurrentDestination(session)
			}
		}
		assertFailsWith<IllegalStateException> {
			ReaderBridgeCommand.ResolveCurrentDestination(ReaderUnboundFoliateSessionId).toJsonObject()
		}
	}

	@Test
	fun validFlatCurrentDestinationResponseDecodesToDistinctSemanticEvent() {
		assertSemanticResult(response())
	}

	@Test
	fun numericFlatLocationFieldsAreAllowedWithoutPublicationLocatorStrings() {
		val minimal = JsonObject(response().filterKeys { key ->
			key in correlationFields || key in setOf("type", "progress", "pageIndex", "pageCount")
		})
		assertSemanticResult(minimal, ReaderLocator(progress = 0.25, pageIndex = 2, pageCount = 8))
	}

	@Test
	fun eachMissingCorrelationFieldIsInvalidPayloadNotUnknownType() {
		correlationFields.forEach { field ->
			assertInvalidResponse(without(field))
		}
	}

	@Test
	fun eachNullCorrelationFieldIsInvalidPayload() {
		correlationFields.forEach { field ->
			assertInvalidResponse(replacing(field, JsonNull))
		}
	}

	@Test
	fun blankCommandAndSessionStringsAreInvalidPayload() {
		stringCorrelationFields.forEach { field ->
			listOf("", " ", "\t\n").forEach { blank ->
				assertInvalidResponse(replacing(field, JsonPrimitive(blank)))
			}
		}
	}

	@Test
	fun nonStringCommandAndSessionValuesAreInvalidPayload() {
		val malformed = listOf<JsonElement>(
			JsonPrimitive(7),
			JsonPrimitive(false),
			buildJsonArray { },
			buildJsonObject { }
		)
		stringCorrelationFields.forEach { field ->
			malformed.forEach { value ->
				assertInvalidResponse(replacing(field, value))
			}
		}
	}

	@Test
	fun paddedCorrelationStringsCannotBeNormalizedIntoMatchingIdentity() {
		stringCorrelationFields.forEach { field ->
			val value = if (field == "commandId") CommandId else SessionId
			assertInvalidResponse(replacing(field, JsonPrimitive(" $value")))
			assertInvalidResponse(replacing(field, JsonPrimitive("$value ")))
		}
	}

	@Test
	fun differentEnvelopeAndDestinationSessionsAreInvalidPayload() {
		assertInvalidResponse(
			replacing("destinationFoliateSessionId", JsonPrimitive(OtherSessionId))
		)
		assertInvalidResponse(replacing("foliateSessionId", JsonPrimitive(OtherSessionId)))
	}

	@Test
	fun missingDestinationSessionCannotFallBackToEnvelopeSession() {
		// Unlike ordinary LocationChanged, this result must explicitly carry both sessions.
		assertInvalidResponse(without("destinationFoliateSessionId"))
	}

	@Test
	fun commitMustBePositiveIntegralWireNumber() {
		listOf<JsonElement>(
			JsonPrimitive(0),
			JsonPrimitive(-1),
			JsonPrimitive(1.5),
			JsonPrimitive("not-an-integer"),
			JsonPrimitive("41"),
			JsonPrimitive(true),
			buildJsonArray { },
			buildJsonObject { }
		).forEach { value ->
			assertInvalidResponse(replacing("destinationCommitSequence", value))
		}
	}

	@Test
	fun malformedKnownResultIsRejectedWithoutRetainingInput() {
		assertRejected(
			"""{"type":"currentDestinationResolved","commandId":"synthetic-command""",
			ReaderBridgeDecodeFailure.MalformedJson
		)
	}

	@Test
	fun nonObjectPayloadIsRejectedWithoutRetainingInput() {
		assertRejected("[]", ReaderBridgeDecodeFailure.NonObjectPayload)
	}

	@Test
	fun missingAndUnknownTypesRemainRedactedRejections() {
		assertRejected("{}", ReaderBridgeDecodeFailure.MissingType)
		assertRejected(
			"""{"type":"syntheticUnknownResult","commandId":"synthetic-command"}""",
			ReaderBridgeDecodeFailure.UnknownType
		)
	}

	@Test
	fun semanticQueryResultCannotBecomeOrdinaryEngineRelocation() {
		val resolved = decodedEvent(response().toString())
		assertTrue(resolved is ReaderBridgeEvent.CurrentDestinationResolved, "Expected distinct query event")
		val engineEvent = FoliateEpubEngineAdapter().onHostEvent(ReaderEngineHostEvent.FoliateBridge(resolved))
		assertTrue(engineEvent == null, "Query result requires its dispatched semantic owner, not ordinary relocation")
	}

	@Test
	fun ordinaryCommandAckIsNotCurrentDestinationProof() {
		val event = decodedEvent(
			buildJsonObject {
				put("type", "commandAck")
				put("commandId", CommandId)
			}.toString()
		)
		assertTrue(event is ReaderBridgeEvent.CommandAcknowledged, "ACK must stay transport-only")
		assertTrue(event::class.simpleName != SemanticEventName, "ACK is not semantic proof")
	}

	@Test
	fun ackWithExtraDestinationFieldsIsStillOnlyTransportAcknowledgement() {
		val event = decodedEvent(replacing("type", JsonPrimitive("commandAck")).toString())
		assertTrue(event is ReaderBridgeEvent.CommandAcknowledged, "ACK must not be promoted")
		assertTrue(event::class.simpleName != SemanticEventName, "ACK is not semantic proof")
	}

	@Test
	fun readyAndPublicationReadyCannotStandInForCurrentDestinationResult() {
		listOf("ready", "publicationReady").forEach { type ->
			val event = decodedEvent(replacing("type", JsonPrimitive(type)).toString())
			assertTrue(event::class.simpleName != SemanticEventName, "Readiness is not semantic proof")
			assertTrue(
				event === ReaderBridgeEvent.Ready || event === ReaderBridgeEvent.PublicationReady,
				"Readiness event must preserve its existing type"
			)
		}
	}

	@Test
	fun uncorrelatedLocationChangedWithCommitRemainsOrdinaryLocationEvent() {
		val payload = JsonObject(
			replacing("type", JsonPrimitive("locationChanged")).filterKeys { it != "commandId" }
		)
		val event = decodedEvent(payload.toString())
		assertTrue(event is ReaderBridgeEvent.LocationChanged, "Location event must stay ordinary")
		assertTrue(event::class.simpleName != SemanticEventName, "Location event is not semantic proof")
		val location = event as ReaderBridgeEvent.LocationChanged
		assertEquals(0.25, location.locator.progress)
		assertEquals(2, location.locator.pageIndex)
		assertEquals(8, location.locator.pageCount)
		assertEquals(0.5, location.locator.chapterProgress)
		assertEquals(1, location.locator.chapterPageIndex)
		assertEquals(4, location.locator.chapterPageCount)
		assertTrue(location.locator.href == null, "Numeric ordinary fixture must have no href")
		assertTrue(location.locator.cfi == null, "Numeric ordinary fixture must have no CFI")
		val identity = location.destinationCommitIdentity
		assertTrue(identity != null, "Ordinary commit metadata should still decode")
		assertTrue(identity?.foliateSessionId == SessionId, "Ordinary commit session must be preserved")
		assertTrue(identity?.commitSequence == 41L, "Ordinary commit sequence must be preserved")
	}

	@Test
	fun locationChangedWithCommandIdStillCannotBePromotedToSemanticResult() {
		val event = decodedEvent(replacing("type", JsonPrimitive("locationChanged")).toString())
		assertTrue(event is ReaderBridgeEvent.LocationChanged, "Wire type must not be inferred from fields")
		assertTrue(event::class.simpleName != SemanticEventName, "Location event is not semantic proof")
	}

	private fun response(): JsonObject = buildJsonObject {
		put("type", "currentDestinationResolved")
		put("commandId", CommandId)
		put("foliateSessionId", SessionId)
		put("destinationFoliateSessionId", SessionId)
		put("destinationCommitSequence", 41L)
		// Flat numeric locator schema used by LocationChanged; deliberately no href/CFI/text.
		put("progress", 0.25)
		put("pageIndex", 2)
		put("pageCount", 8)
		put("chapterProgress", 0.5)
		put("chapterPageIndex", 1)
		put("chapterPageCount", 4)
		put("fraction", 0.25)
		put("size", 0.125)
	}

	private fun replacing(field: String, value: JsonElement): JsonObject =
		JsonObject(response().toMutableMap().apply { this[field] = value })

	private fun without(field: String): JsonObject =
		JsonObject(response().filterKeys { it != field })

	private fun assertSemanticResult(
		payload: JsonObject,
		expectedLocator: ReaderLocator = ReaderLocator(
			progress = 0.25,
			pageIndex = 2,
			pageCount = 8,
			chapterProgress = 0.5,
			chapterPageIndex = 1,
			chapterPageCount = 4,
			fraction = 0.25,
			size = 0.125
		)
	) {
		val event = decodedEvent(payload.toString())
		assertTrue(event !is ReaderBridgeEvent.LocationChanged, "Result needs a distinct semantic type")
		assertTrue(event !is ReaderBridgeEvent.CommandAcknowledged, "Result must not decode as ACK")
		assertEquals(SemanticEventName, event::class.simpleName)
		assertTrue(event is ReaderBridgeEvent.CurrentDestinationResolved, "Expected the distinct typed result")
		val resolved = event as ReaderBridgeEvent.CurrentDestinationResolved
		assertTrue(CommandId == resolved.commandId, "Result must preserve the exact command identity")
		assertTrue(SessionId == resolved.foliateSessionId, "Result must preserve the envelope session")
		assertTrue(SessionId == resolved.destinationCommitIdentity.foliateSessionId,
			"Result must preserve the destination session")
		assertEquals(41L, resolved.destinationCommitIdentity.commitSequence)
		assertTrue(expectedLocator == resolved.locator, "Result must preserve the exact flat locator")
		assertTrue(resolved.locator.href == null, "Numeric fixture must have no href")
		assertTrue(resolved.locator.cfi == null, "Numeric fixture must have no CFI")
	}

	private fun decodedEvent(message: String): ReaderBridgeEvent {
		val result = decodeReaderBridgeMessage(message)
		// Never interpolate a payload, event, or decode result into an assertion message.
		assertTrue(result is ReaderBridgeDecodeResult.Decoded, "Expected a decoded typed bridge event")
		return (result as ReaderBridgeDecodeResult.Decoded).event
	}

	private fun assertInvalidResponse(payload: JsonObject) {
		assertRejected(payload.toString(), ReaderBridgeDecodeFailure.InvalidPayload)
	}

	private fun assertRejected(message: String, expectedFailure: ReaderBridgeDecodeFailure) {
		val result = decodeReaderBridgeMessage(message)
		assertTrue(result is ReaderBridgeDecodeResult.Rejected, "Invalid input must be rejected")
		val rejection = result as ReaderBridgeDecodeResult.Rejected
		assertEquals(expectedFailure, rejection.failure)
		// Boolean assertions prevent accidental raw-message output even on a redaction regression.
		assertTrue(
			rejection.rawMessage == "[redacted-reader-bridge-payload]",
			"Rejected input must use the fixed redaction marker"
		)
		assertTrue(rejection.rawMessage != message, "Rejected input must not retain its payload")
	}

	private companion object {
		const val CommandId = "reader-command-1-7"
		const val SessionId = "foliate-1-7"
		const val OtherSessionId = "foliate-1-8"
		const val SemanticEventName = "CurrentDestinationResolved"
		val stringCorrelationFields = listOf(
			"commandId", "foliateSessionId", "destinationFoliateSessionId"
		)
		val correlationFields = stringCorrelationFields + "destinationCommitSequence"
	}
}
