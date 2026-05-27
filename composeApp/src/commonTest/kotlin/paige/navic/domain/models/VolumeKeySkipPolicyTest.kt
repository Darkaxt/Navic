package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VolumeKeySkipPolicyTest {
	@Test
	fun disabledSettingDoesNotConsumeVolumeKeys() {
		val decision = volumeKeySkipDecision(
			enabled = false,
			key = VolumeKeySkipKey.VolumeUp,
			eventAction = VolumeKeySkipEventAction.Down,
			repeatCount = 0
		)

		assertFalse(decision.consume)
		assertNull(decision.skipAction)
	}

	@Test
	fun volumeKeyDownMapsToTrackSkipActions() {
		assertEquals(
			VolumeKeySkipDecision(
				consume = true,
				skipAction = VolumeKeySkipAction.Next
			),
			volumeKeySkipDecision(
				enabled = true,
				key = VolumeKeySkipKey.VolumeUp,
				eventAction = VolumeKeySkipEventAction.Down,
				repeatCount = 0
			)
		)
		assertEquals(
			VolumeKeySkipDecision(
				consume = true,
				skipAction = VolumeKeySkipAction.Previous
			),
			volumeKeySkipDecision(
				enabled = true,
				key = VolumeKeySkipKey.VolumeDown,
				eventAction = VolumeKeySkipEventAction.Down,
				repeatCount = 0
			)
		)
	}

	@Test
	fun volumeKeyUpAndRepeatsAreConsumedWithoutRepeatedSkipping() {
		val keyUp = volumeKeySkipDecision(
			enabled = true,
			key = VolumeKeySkipKey.VolumeUp,
			eventAction = VolumeKeySkipEventAction.Up,
			repeatCount = 0
		)
		val repeatDown = volumeKeySkipDecision(
			enabled = true,
			key = VolumeKeySkipKey.VolumeUp,
			eventAction = VolumeKeySkipEventAction.Down,
			repeatCount = 2
		)

		assertTrue(keyUp.consume)
		assertNull(keyUp.skipAction)
		assertTrue(repeatDown.consume)
		assertNull(repeatDown.skipAction)
	}

	@Test
	fun otherKeysAndUnknownActionsPassThrough() {
		assertFalse(
			volumeKeySkipDecision(
				enabled = true,
				key = VolumeKeySkipKey.Other,
				eventAction = VolumeKeySkipEventAction.Down,
				repeatCount = 0
			).consume
		)
		assertFalse(
			volumeKeySkipDecision(
				enabled = true,
				key = VolumeKeySkipKey.VolumeUp,
				eventAction = VolumeKeySkipEventAction.Other,
				repeatCount = 0
			).consume
		)
	}
}
