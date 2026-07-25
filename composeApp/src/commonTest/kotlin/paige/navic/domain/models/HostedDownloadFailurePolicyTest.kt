package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class HostedDownloadFailurePolicyTest {
	@Test
	fun transientConnectionFailuresWaitForServiceRecovery() {
		assertEquals(
			HostedDownloadFailureAction.WaitForService,
			hostedDownloadFailureAction(ConnectTimeoutException("failed to connect"))
		)
		assertEquals(
			HostedDownloadFailureAction.WaitForService,
			hostedDownloadFailureAction(SocketTimeoutException("timed out"))
		)
		assertEquals(
			HostedDownloadFailureAction.WaitForService,
			hostedDownloadFailureAction(UnknownHostException("unable to resolve host"))
		)
	}

	@Test
	fun hostedHttpFailuresWaitForServiceRecovery() {
		assertEquals(
			HostedDownloadFailureAction.WaitForService,
			hostedDownloadFailureAction(IllegalStateException("Stream request failed: HTTP 503 Service Unavailable"))
		)
		assertEquals(
			HostedDownloadFailureAction.WaitForService,
			hostedDownloadFailureAction(IllegalStateException("status 524"))
		)
	}

	@Test
	fun nonAudioDownloadResponsesAreTerminalForCurrentQueuePass() {
		assertEquals(
			HostedDownloadFailureAction.Fail,
			hostedDownloadFailureAction(IllegalStateException("Stream request returned non-audio content: text/html"))
		)
	}
}

private class ConnectTimeoutException(message: String) : Exception(message)
private class SocketTimeoutException(message: String) : Exception(message)
private class UnknownHostException(message: String) : Exception(message)
