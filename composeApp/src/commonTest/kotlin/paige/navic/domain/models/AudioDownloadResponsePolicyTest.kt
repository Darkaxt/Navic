package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioDownloadResponsePolicyTest {
	@Test
	fun htmlResponsesAreRejectedAsAudioDownloads() {
		assertTrue(shouldRejectAudioDownloadContentType("text/html"))
		assertTrue(shouldRejectAudioDownloadContentType("text/html; charset=utf-8"))
	}

	@Test
	fun jsonAndXmlResponsesAreRejectedAsAudioDownloads() {
		assertTrue(shouldRejectAudioDownloadContentType("application/json"))
		assertTrue(shouldRejectAudioDownloadContentType("application/xml"))
	}

	@Test
	fun audioAndBinaryResponsesAreAcceptedAsAudioDownloads() {
		assertFalse(shouldRejectAudioDownloadContentType("audio/flac"))
		assertFalse(shouldRejectAudioDownloadContentType("audio/mpeg"))
		assertFalse(shouldRejectAudioDownloadContentType("application/octet-stream"))
		assertFalse(shouldRejectAudioDownloadContentType(null))
	}

	@Test
	fun implausiblySmallDownloadedFilesAreNotUsedForPlayback() {
		assertFalse(shouldUseDownloadedAudioFile(0))
		assertFalse(shouldUseDownloadedAudioFile(8_192))
		assertTrue(shouldUseDownloadedAudioFile(65_536))
	}
}
