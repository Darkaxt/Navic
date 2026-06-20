package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostedDownloadFailurePolicyTest {
	@Test
	fun transientConnectionFailuresRemainRetryable() {
		assertFalse(shouldFailHostedDownload(ConnectTimeoutException("failed to connect")))
		assertFalse(shouldFailHostedDownload(SocketTimeoutException("timed out")))
		assertFalse(shouldFailHostedDownload(UnknownHostException("unable to resolve host")))
	}

	@Test
	fun hostedHttpFailuresAreTerminalForCurrentQueuePass() {
		assertTrue(shouldFailHostedDownload(IllegalStateException("Stream request failed: HTTP 503 Service Unavailable")))
		assertTrue(shouldFailHostedDownload(IllegalStateException("status 524")))
	}
}

private class ConnectTimeoutException(message: String) : Exception(message)
private class SocketTimeoutException(message: String) : Exception(message)
private class UnknownHostException(message: String) : Exception(message)
