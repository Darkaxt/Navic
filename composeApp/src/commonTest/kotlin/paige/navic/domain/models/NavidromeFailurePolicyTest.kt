package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class NavidromeFailurePolicyTest {
	@Test
	fun serverAndGatewayFailuresMarkTheServiceUnavailable() {
		listOf(500, 502, 503, 504, 521, 522, 523, 524).forEach { status ->
			assertEquals(
				NavidromeFailureDisposition.ServiceUnavailable,
				classifyNavidromeFailure(IllegalStateException("HTTP $status upstream failure"))
			)
		}
		assertEquals(
			NavidromeFailureDisposition.ServiceUnavailable,
			classifyNavidromeFailure(IllegalStateException("Response code: 503"))
		)
	}

	@Test
	fun dnsAndTransportFailuresMarkTheServiceUnavailableAcrossCauseChain() {
		val failures = listOf(
			FakeUnknownHostException("Unable to resolve host"),
			FakeConnectException("Connection refused"),
			FakeSocketException("Connection reset"),
			FakeNoRouteToHostException("No route to host"),
			FakeSocketTimeoutException("Read timed out")
		)

		failures.forEach { failure ->
			assertEquals(
				NavidromeFailureDisposition.ServiceUnavailable,
				classifyNavidromeFailure(IllegalStateException("stream failed", failure))
			)
		}
	}

	@Test
	fun authenticationContentAndDecoderFailuresRemainTerminal() {
		val failures = listOf(
			IllegalStateException("HTTP 401 Unauthorized"),
			IllegalStateException("HTTP 403 Forbidden"),
			IllegalStateException("HTTP 404 Not Found"),
			IllegalStateException("Stream returned non-audio content: text/html"),
			IllegalStateException("Decoder initialization failed"),
			IllegalStateException("Malformed media container")
		)

		failures.forEach { failure ->
			assertEquals(
				NavidromeFailureDisposition.Terminal,
				classifyNavidromeFailure(failure)
			)
		}
	}

	@Test
	fun unclassifiedFailuresRemainTerminal() {
		assertEquals(
			NavidromeFailureDisposition.Terminal,
			classifyNavidromeFailure(IllegalStateException("unexpected state"))
		)
	}
}

private class FakeUnknownHostException(message: String) : Exception(message)
private class FakeConnectException(message: String) : Exception(message)
private class FakeSocketException(message: String) : Exception(message)
private class FakeNoRouteToHostException(message: String) : Exception(message)
private class FakeSocketTimeoutException(message: String) : Exception(message)
