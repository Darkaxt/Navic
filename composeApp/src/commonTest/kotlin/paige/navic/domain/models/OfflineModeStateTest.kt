package paige.navic.domain.models

import paige.navic.domain.models.settings.OfflineMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineModeStateTest {
	@Test
	fun autoRequiresRawNetworkAvailability() {
		assertTrue(isOnlineForOfflineMode(true, false, OfflineMode.Auto))
		assertFalse(isOnlineForOfflineMode(false, false, OfflineMode.Auto))
	}

	@Test
	fun forcedIsOfflineOnEveryNetwork() {
		assertFalse(isOnlineForOfflineMode(true, false, OfflineMode.Forced))
		assertFalse(isOnlineForOfflineMode(true, true, OfflineMode.Forced))
	}

	@Test
	fun noWifiAllowsValidatedWifiOnly() {
		assertTrue(isOnlineForOfflineMode(true, false, OfflineMode.NoWiFi))
		assertFalse(isOnlineForOfflineMode(true, true, OfflineMode.NoWiFi))
		assertFalse(isOnlineForOfflineMode(false, false, OfflineMode.NoWiFi))
	}

	@Test
	fun automaticReasonUsesForcedPolicy() {
		val state = offlineModeState(
			selectedMode = OfflineMode.Auto,
			automaticReason = AutomaticOfflineReason.NavidromeUnavailable
		)

		assertFalse(isOnlineForOfflineMode(true, false, state.effectiveMode))
	}
}
