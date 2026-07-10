package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingScreenOnMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingScreenOnPolicyTest {
	@Test
	fun offNeverKeepsScreenOnWithOrWithoutPower() {
		assertFalse(
			shouldKeepNowPlayingScreenOn(
				mode = NowPlayingScreenOnMode.Off,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = false
			)
		)
		assertFalse(
			shouldKeepNowPlayingScreenOn(
				mode = NowPlayingScreenOnMode.Off,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = true
			)
		)
	}

	@Test
	fun everyModeIsFalseWithoutAnActiveSong() {
		NowPlayingScreenOnMode.entries.forEach { mode ->
			assertFalse(
				shouldKeepNowPlayingScreenOn(
					mode = mode,
					hasActiveSong = false,
					isPaused = false,
					isExternalPowerConnected = true
				)
			)
		}
	}

	@Test
	fun everyModeIsFalseWhilePaused() {
		NowPlayingScreenOnMode.entries.forEach { mode ->
			assertFalse(
				shouldKeepNowPlayingScreenOn(
					mode = mode,
					hasActiveSong = true,
					isPaused = true,
					isExternalPowerConnected = true
				)
			)
		}
	}

	@Test
	fun chargingModeRequiresExternalPower() {
		assertTrue(
			shouldKeepNowPlayingScreenOn(
				mode = NowPlayingScreenOnMode.WhilePlayingAndCharging,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = true
			)
		)
		assertFalse(
			shouldKeepNowPlayingScreenOn(
				mode = NowPlayingScreenOnMode.WhilePlayingAndCharging,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = false
			)
		)
	}

	@Test
	fun whilePlayingKeepsScreenOnWithoutExternalPower() {
		assertTrue(
			shouldKeepNowPlayingScreenOn(
				mode = NowPlayingScreenOnMode.WhilePlaying,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = false
			)
		)
	}
}
