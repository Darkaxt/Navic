package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistAurralMonitorDisplayPolicyTest {
	@Test
	fun aurralMonitorActionKeepsPendingSeparateFromVerifiedStates() {
		assertEquals(
			AurralMonitorActionState.PendingVerification,
			aurralMonitorActionState(aurralMonitored = null)
		)
		assertEquals(
			AurralMonitorActionState.Monitored,
			aurralMonitorActionState(aurralMonitored = true)
		)
		assertEquals(
			AurralMonitorActionState.NotMonitored,
			aurralMonitorActionState(aurralMonitored = false)
		)
		assertTrue(isAurralMonitorActionVerified(AurralMonitorActionState.Monitored))
		assertTrue(isAurralMonitorActionVerified(AurralMonitorActionState.NotMonitored))
		assertFalse(isAurralMonitorActionVerified(AurralMonitorActionState.PendingVerification))
		assertFalse(isAurralMonitorActionVerified(AurralMonitorActionState.PendingConfirmation))
	}
}
