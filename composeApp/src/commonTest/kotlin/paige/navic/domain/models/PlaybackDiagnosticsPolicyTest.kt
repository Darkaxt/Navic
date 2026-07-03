package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackDiagnosticsPolicyTest {
	@Test
	fun diagnosticMessagesUseStableKeyValueFields() {
		assertEquals(
			"paused reason=audio-focus-loss songId=42 index=3 title=Everybody Has Secrets",
			playbackDiagnosticMessage(
				"paused",
				"reason" to "audio-focus-loss",
				"songId" to "42",
				"index" to 3,
				"title" to "Everybody\nHas\tSecrets"
			)
		)
	}

	@Test
	fun diagnosticMessagesSkipNullAndBlankFields() {
		val message = playbackDiagnosticMessage(
			"recovery-download-status",
			"songId" to "42",
			"status" to "DOWNLOADING",
			"filePath" to null,
			"detail" to ""
		)

		assertEquals("recovery-download-status songId=42 status=DOWNLOADING", message)
		assertFalse(message.contains("filePath"))
		assertFalse(message.contains("detail"))
	}
}
