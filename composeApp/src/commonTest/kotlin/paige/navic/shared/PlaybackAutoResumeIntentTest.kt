package paige.navic.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackAutoResumeIntentTest {
	@Test
	fun normalGapAndVolumeRestoreConsumeTheirPauseExactlyOnce() {
		for (cause in listOf("gap", "volume-zero")) {
			val owner = PlaybackAutoResumeIntent()
			val session = Any()
			val token = owner.capture(session, "item", 0)
			assertTrue(owner.consume(token, session, "item", 0), cause)
			assertFalse(owner.consume(token, session, "item", 0), cause)
		}
	}

	@Test
	fun manualResumeThenPauseAndSameIndexReplacementInvalidateBothAutoResumePaths() {
		for (cause in listOf("gap", "volume-zero")) {
			for (command in listOf("resume-pause", "same-index-replacement", "seek", "clear")) {
				val owner = PlaybackAutoResumeIntent()
				val session = Any()
				val token = owner.capture(session, "item", 0)
				owner.invalidate()
				if (command == "resume-pause") owner.invalidate()
				assertFalse(owner.consume(token, session, "item", 0), "$cause/$command")
			}
		}
	}

	@Test
	fun changedSessionItemOrIndexCannotResumeAndOldTokenCannotConsumeNewPause() {
		val owner = PlaybackAutoResumeIntent()
		val session = Any()
		val old = owner.capture(session, "item", 0)
		assertFalse(owner.consume(old, Any(), "item", 0))
		assertFalse(owner.consume(old, session, "other", 0))
		assertFalse(owner.consume(old, session, "item", 1))
		val current = owner.capture(session, "item", 0)
		assertFalse(owner.consume(old, session, "item", 0))
		assertTrue(owner.consume(current, session, "item", 0))
	}
}
