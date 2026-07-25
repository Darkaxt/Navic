package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import paige.navic.domain.models.AutomaticOfflineReason
import paige.navic.domain.models.settings.OfflineMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineModeCoordinatorTest {
	@Test
	fun automaticReasonForcesEffectiveModeWithoutChangingSelection() = runBlocking {
		val preferences = PreferenceManager(MapSettings())
		val coordinator = OfflineModeCoordinator(preferences)

		assertTrue(coordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable))
		val forced = coordinator.state.first { it.isAutomaticallyForced }

		assertEquals(OfflineMode.Auto, preferences.offlineMode)
		assertEquals(OfflineMode.Auto, forced.selectedMode)
		assertEquals(OfflineMode.Forced, forced.effectiveMode)
		assertEquals(AutomaticOfflineReason.NavidromeUnavailable, forced.automaticReason)
		assertFalse(coordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable))
	}

	@Test
	fun clearingAutomaticReasonRevealsLatestUserSelection() = runBlocking {
		val settings = MapSettings()
		val preferences = PreferenceManager(settings)
		val coordinator = OfflineModeCoordinator(preferences)
		coordinator.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable)
		coordinator.state.first { it.isAutomaticallyForced }

		preferences.offlineMode = OfflineMode.NoWiFi
		coordinator.state.first { it.selectedMode == OfflineMode.NoWiFi }
		assertTrue(coordinator.clearAutomatic(AutomaticOfflineReason.NavidromeUnavailable))
		val restored = coordinator.state.first { !it.isAutomaticallyForced }

		assertEquals(OfflineMode.NoWiFi, restored.selectedMode)
		assertEquals(OfflineMode.NoWiFi, restored.effectiveMode)
		assertEquals(OfflineMode.NoWiFi, PreferenceManager(settings).offlineMode)
	}

	@Test
	fun automaticReasonIsProcessLocal() = runBlocking {
		val settings = MapSettings()
		val preferences = PreferenceManager(settings)
		val first = OfflineModeCoordinator(preferences)
		first.enterAutomatic(AutomaticOfflineReason.NavidromeUnavailable)
		first.state.first { it.isAutomaticallyForced }

		val recreated = OfflineModeCoordinator(PreferenceManager(settings))
		val recreatedState = recreated.state.value

		assertEquals(OfflineMode.Auto, recreatedState.selectedMode)
		assertEquals(OfflineMode.Auto, recreatedState.effectiveMode)
		assertNull(recreatedState.automaticReason)
	}
}
