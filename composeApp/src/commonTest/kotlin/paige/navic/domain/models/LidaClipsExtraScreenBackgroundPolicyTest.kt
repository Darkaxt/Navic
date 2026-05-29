package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidaClipsExtraScreenBackgroundPolicyTest {
	@Test
	fun extraScreenVideoBackgroundRequiresSettingLidaClipsClipAndPlayback() {
		assertFalse(
			shouldShowLidaClipExtraScreenBackground(
				settingEnabled = false,
				lidaClipsEnabled = true,
				hasCachedClip = true,
				musicIsPlaying = true
			)
		)
		assertFalse(
			shouldShowLidaClipExtraScreenBackground(
				settingEnabled = true,
				lidaClipsEnabled = false,
				hasCachedClip = true,
				musicIsPlaying = true
			)
		)
		assertFalse(
			shouldShowLidaClipExtraScreenBackground(
				settingEnabled = true,
				lidaClipsEnabled = true,
				hasCachedClip = false,
				musicIsPlaying = true
			)
		)
		assertFalse(
			shouldShowLidaClipExtraScreenBackground(
				settingEnabled = true,
				lidaClipsEnabled = true,
				hasCachedClip = true,
				musicIsPlaying = false
			)
		)
		assertTrue(
			shouldShowLidaClipExtraScreenBackground(
				settingEnabled = true,
				lidaClipsEnabled = true,
				hasCachedClip = true,
				musicIsPlaying = true
			)
		)
	}
}
