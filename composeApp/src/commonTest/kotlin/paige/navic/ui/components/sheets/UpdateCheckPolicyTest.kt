package paige.navic.ui.components.sheets

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateCheckPolicyTest {
	@Test
	fun androidUsesAutomaticUpdateCheckPreference() {
		assertEquals(
			true,
			shouldRunUpdateCheck(
				platformName = "Android",
				automaticChecksEnabled = true,
				forceCheckRequests = 0
			)
		)

		assertEquals(
			false,
			shouldRunUpdateCheck(
				platformName = "Android",
				automaticChecksEnabled = false,
				forceCheckRequests = 0
			)
		)
	}

	@Test
	fun androidVersionTapForcesUpdateCheck() {
		assertEquals(
			true,
			shouldRunUpdateCheck(
				platformName = "Android",
				automaticChecksEnabled = false,
				forceCheckRequests = 1
			)
		)
	}

	@Test
	fun applePlatformsSkipUpdateChecks() {
		assertEquals(
			false,
			shouldRunUpdateCheck(
				platformName = "iOS",
				automaticChecksEnabled = true,
				forceCheckRequests = 1
			)
		)

		assertEquals(
			false,
			shouldRunUpdateCheck(
				platformName = "iPadOS",
				automaticChecksEnabled = true,
				forceCheckRequests = 1
			)
		)
	}
}
