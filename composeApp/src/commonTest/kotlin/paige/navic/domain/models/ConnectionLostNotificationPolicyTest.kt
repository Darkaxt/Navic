package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionLostNotificationPolicyTest {
	@Test
	fun connectionLostMessageMatchesTheProductCopyExactly() {
		assertEquals(
			"Connection lost - Switching to Offline mode",
			CONNECTION_LOST_OFFLINE_MESSAGE
		)
	}
}
