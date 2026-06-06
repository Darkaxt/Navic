package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.ui.components.common.AurralActionIconOverlay

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

	@Test
	fun aurralMonitorActionUsesAurralSpecificIconOverlays() {
		assertEquals(
			AurralActionIconOverlay.QuestionMark,
			aurralMonitorActionIconOverlay(AurralMonitorActionState.PendingVerification)
		)
		assertEquals(
			AurralActionIconOverlay.Progress,
			aurralMonitorActionIconOverlay(AurralMonitorActionState.PendingConfirmation)
		)
		assertEquals(
			AurralActionIconOverlay.None,
			aurralMonitorActionIconOverlay(AurralMonitorActionState.Monitored)
		)
		assertEquals(
			AurralActionIconOverlay.Crossed,
			aurralMonitorActionIconOverlay(AurralMonitorActionState.NotMonitored)
		)
	}
}
