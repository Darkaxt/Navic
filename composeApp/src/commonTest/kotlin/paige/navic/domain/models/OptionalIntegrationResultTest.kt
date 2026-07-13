package paige.navic.domain.models

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OptionalIntegrationResultTest {
	@Test
	fun successfulPayloadsDistinguishAvailableEmptyAndStale() {
		val available = optionalIntegrationResult(
			result = Result.success(listOf("live")),
			isEmpty = List<String>::isEmpty
		)
		val empty = optionalIntegrationResult(
			result = Result.success(emptyList<String>()),
			isEmpty = List<String>::isEmpty
		)
		val staleFailure = OptionalIntegrationFailure(
			kind = OptionalIntegrationFailureKind.Unavailable,
			message = "Service unavailable"
		)
		val stale = optionalIntegrationResult(
			result = Result.success(listOf("cached")),
			staleFailure = staleFailure,
			isEmpty = List<String>::isEmpty
		)
		val staleEmpty = optionalIntegrationResult(
			result = Result.success(emptyList<String>()),
			staleFailure = staleFailure,
			isEmpty = List<String>::isEmpty
		)

		assertEquals(listOf("live"), assertIs<OptionalIntegrationResult.Available<List<String>>>(available).data)
		assertIs<OptionalIntegrationResult.Empty>(empty)
		assertEquals(listOf("cached"), assertIs<OptionalIntegrationResult.Stale<List<String>>>(stale).data)
		assertEquals(emptyList(), assertIs<OptionalIntegrationResult.Stale<List<String>>>(staleEmpty).data)
	}

	@Test
	fun failuresDistinguishHttpSerializationAndGenericAvailability() {
		val unauthorized = optionalIntegrationResult<String>(
			result = Result.failure(OptionalIntegrationHttpException(401, "Unauthorized")),
			isEmpty = String::isEmpty
		)
		val forbidden = optionalIntegrationResult<String>(
			result = Result.failure(
				IllegalStateException("request failed", OptionalIntegrationHttpException(403, "Forbidden"))
			),
			isEmpty = String::isEmpty
		)
		val malformed = optionalIntegrationResult<String>(
			result = Result.failure(IllegalStateException("decode failed", SerializationException("bad payload"))),
			isEmpty = String::isEmpty
		)
		val unavailable = optionalIntegrationResult<String>(
			result = Result.failure(IllegalStateException("offline")),
			isEmpty = String::isEmpty
		)

		assertEquals(OptionalIntegrationFailureKind.Unauthorized, unauthorized.failureOrNull()?.kind)
		assertEquals(OptionalIntegrationFailureKind.Unauthorized, forbidden.failureOrNull()?.kind)
		assertEquals(OptionalIntegrationFailureKind.Malformed, malformed.failureOrNull()?.kind)
		assertEquals(OptionalIntegrationFailureKind.Unavailable, unavailable.failureOrNull()?.kind)
	}

	@Test
	fun explicitConfigurationFailuresRemainTyped() {
		val disabled = optionalIntegrationUnavailable(
			kind = OptionalIntegrationFailureKind.Disabled,
			message = "Aurral is disabled"
		)
		val misconfigured = optionalIntegrationUnavailable(
			kind = OptionalIntegrationFailureKind.Misconfigured,
			message = "Bindery URL is required"
		)

		assertEquals(OptionalIntegrationFailureKind.Disabled, disabled.failure.kind)
		assertEquals(OptionalIntegrationFailureKind.Misconfigured, misconfigured.failure.kind)
	}
}
