package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LidaClipResumePositionPolicyTest {
	@Test
	fun startsFromRememberedPositionOnlyForTheSameClipWhenEnabled() {
		assertEquals(
			42_000L,
			lidaClipStartPositionMs(
				rememberPosition = true,
				clipId = 7,
				lastClipId = "7",
				lastPositionMs = 42_000L,
				durationMs = 180_000L
			)
		)
		assertEquals(
			0L,
			lidaClipStartPositionMs(
				rememberPosition = false,
				clipId = 7,
				lastClipId = "7",
				lastPositionMs = 42_000L,
				durationMs = 180_000L
			)
		)
		assertEquals(
			0L,
			lidaClipStartPositionMs(
				rememberPosition = true,
				clipId = 8,
				lastClipId = "7",
				lastPositionMs = 42_000L,
				durationMs = 180_000L
			)
		)
	}

	@Test
	fun ignoresPositionsTooCloseToTheStartOrEnd() {
		assertEquals(
			0L,
			lidaClipStartPositionMs(
				rememberPosition = true,
				clipId = 7,
				lastClipId = "7",
				lastPositionMs = 2_999L,
				durationMs = 180_000L
			)
		)
		assertEquals(
			0L,
			lidaClipStartPositionMs(
				rememberPosition = true,
				clipId = 7,
				lastClipId = "7",
				lastPositionMs = 176_000L,
				durationMs = 180_000L
			)
		)
	}

	@Test
	fun storesSanitizedPositionOnlyWhenRememberingIsEnabled() {
		assertEquals(
			LidaClipRememberedPosition(clipId = "7", positionMs = 42_000L),
			nextRememberedLidaClipPosition(
				rememberPosition = true,
				clipId = 7,
				positionMs = 42_000L,
				durationMs = 180_000L
			)
		)
		assertEquals(
			LidaClipRememberedPosition(clipId = "7", positionMs = 0L),
			nextRememberedLidaClipPosition(
				rememberPosition = true,
				clipId = 7,
				positionMs = 176_000L,
				durationMs = 180_000L
			)
		)
		assertNull(
			nextRememberedLidaClipPosition(
				rememberPosition = false,
				clipId = 7,
				positionMs = 42_000L,
				durationMs = 180_000L
			)
		)
	}
}
