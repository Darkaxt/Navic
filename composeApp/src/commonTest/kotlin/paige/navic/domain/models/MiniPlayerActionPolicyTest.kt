package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniPlayerActionPolicyTest {
	@Test
	fun miniPlayerQueueActionRequiresSettingAndCurrentSong() {
		assertFalse(
			shouldShowMiniPlayerQueueAction(
				enabled = false,
				hasCurrentSong = true
			)
		)
		assertFalse(
			shouldShowMiniPlayerQueueAction(
				enabled = true,
				hasCurrentSong = false
			)
		)
		assertTrue(
			shouldShowMiniPlayerQueueAction(
				enabled = true,
				hasCurrentSong = true
			)
		)
	}
}
