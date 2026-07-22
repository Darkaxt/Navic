package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import paige.navic.domain.models.settings.OfflineMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavidromeAvailabilityManagerTest {
	@Test
	fun firstOutageForcesOfflineWithoutOverwritingTheSelectedMode() = runBlocking {
		val fixture = availabilityFixture()
		try {
			assertTrue(fixture.manager.reportUnavailable(NavidromeOutageTrigger.Playback))
			assertFalse(fixture.manager.reportUnavailable(NavidromeOutageTrigger.Download))

			val unavailable = fixture.manager.state.value as NavidromeAvailability.Unavailable
			val offline = fixture.offlineMode.state.first { it.isAutomaticallyForced }

			assertEquals(NavidromeOutageTrigger.Playback, unavailable.trigger)
			assertEquals(OfflineMode.Auto, offline.selectedMode)
			assertEquals(OfflineMode.Forced, offline.effectiveMode)
			assertEquals(OfflineMode.Auto, fixture.preferences.offlineMode)
			assertTrue(fixture.manager.claimConnectionLostNotice())
			assertFalse(fixture.manager.claimConnectionLostNotice())
		} finally {
			fixture.scope.cancel()
		}
	}

	@Test
	fun duplicateReportsDoNotCreateDuplicateConnectionLostNotices() {
		val fixture = availabilityFixture()
		try {
			fixture.manager.reportUnavailable(NavidromeOutageTrigger.Playback)
			fixture.manager.reportUnavailable(NavidromeOutageTrigger.Download)

			assertTrue(fixture.manager.claimConnectionLostNotice())
			assertFalse(fixture.manager.claimConnectionLostNotice())
		} finally {
			fixture.scope.cancel()
		}
	}

	@Test
	fun successfulAuthenticatedProbeRestoresTheLatestSelectedMode() = runBlocking {
		val probeStarted = CompletableDeferred<Unit>()
		val allowProbe = CompletableDeferred<Unit>()
		val fixture = availabilityFixture {
			probeStarted.complete(Unit)
			allowProbe.await()
		}
		try {
			fixture.manager.reportUnavailable(NavidromeOutageTrigger.Playback)
			fixture.offlineMode.state.first { it.isAutomaticallyForced }
			fixture.preferences.offlineMode = OfflineMode.NoWiFi
			fixture.manager.requestProbe()
			probeStarted.await()
			allowProbe.complete(Unit)

			fixture.manager.state.first { it is NavidromeAvailability.Available }
			val restored = fixture.offlineMode.state.first { !it.isAutomaticallyForced }
			assertEquals(OfflineMode.NoWiFi, restored.selectedMode)
			assertEquals(OfflineMode.NoWiFi, restored.effectiveMode)
		} finally {
			fixture.scope.cancel()
		}
	}

	@Test
	fun probeWaitsForRawNetworkAndAuthenticatedSession() = runBlocking {
		val probeStarted = CompletableDeferred<Unit>()
		val fixture = availabilityFixture(
			networkAvailable = false,
			loggedIn = false
		) {
			probeStarted.complete(Unit)
		}
		try {
			fixture.manager.reportUnavailable(NavidromeOutageTrigger.RawNetworkLost)
			fixture.manager.requestProbe()
			assertFalse(probeStarted.isCompleted)

			fixture.networkAvailable.value = true
			fixture.loggedIn.value = true
			fixture.manager.requestProbe()
			probeStarted.await()
			fixture.manager.state.first { it is NavidromeAvailability.Available }
			Unit
		} finally {
			fixture.scope.cancel()
		}
	}

	private fun availabilityFixture(
		networkAvailable: Boolean = true,
		loggedIn: Boolean = true,
		ping: suspend () -> Unit = { throw IllegalStateException("still unavailable") }
	): AvailabilityFixture {
		val scope = CoroutineScope(Job() + Dispatchers.Default)
		val preferences = PreferenceManager(MapSettings())
		val offlineMode = OfflineModeCoordinator(preferences, scope)
		val network = MutableStateFlow(networkAvailable)
		val login = MutableStateFlow(loggedIn)
		return AvailabilityFixture(
			scope = scope,
			preferences = preferences,
			offlineMode = offlineMode,
			networkAvailable = network,
			loggedIn = login,
			manager = NavidromeAvailabilityManager(
				networkAvailable = network,
				isLoggedIn = login,
				ping = ping,
				offlineModeCoordinator = offlineMode,
				scope = scope,
				heartbeat = { awaitCancellation() }
			)
		)
	}

	private data class AvailabilityFixture(
		val scope: CoroutineScope,
		val preferences: PreferenceManager,
		val offlineMode: OfflineModeCoordinator,
		val networkAvailable: MutableStateFlow<Boolean>,
		val loggedIn: MutableStateFlow<Boolean>,
		val manager: NavidromeAvailabilityManager
	)
}
