package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class QueueSummaryPolicyTest {
	@Test
	fun queueTotalDurationLabelFormatsShortQueues() {
		assertEquals("0s", queueTotalDurationLabel(0))
		assertEquals("45s", queueTotalDurationLabel(45))
		assertEquals("1m 5s", queueTotalDurationLabel(65))
	}

	@Test
	fun queueTotalDurationLabelFormatsHourQueues() {
		assertEquals("1h 0m 0s", queueTotalDurationLabel(3600))
		assertEquals("1h 1m 1s", queueTotalDurationLabel(3661))
	}

	@Test
	fun queueTotalDurationLabelClampsNegativeDurations() {
		assertEquals("0s", queueTotalDurationLabel(-1))
	}
}
