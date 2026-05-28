package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingInfoIconPolicyTest {
	@Test
	fun hiddenWhenSettingIsOff() {
		assertFalse(
			shouldShowNowPlayingInfoIcon(
				enabled = false,
				hasNavigationTarget = true
			)
		)
	}

	@Test
	fun hiddenWhenThereIsNoNavigationTarget() {
		assertFalse(
			shouldShowNowPlayingInfoIcon(
				enabled = true,
				hasNavigationTarget = false
			)
		)
	}

	@Test
	fun shownOnlyWhenSettingAndNavigationTargetAreAvailable() {
		assertTrue(
			shouldShowNowPlayingInfoIcon(
				enabled = true,
				hasNavigationTarget = true
			)
		)
	}
}
