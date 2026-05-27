package paige.navic.domain.models

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShakeToSkipPolicyTest {
	@Test
	fun shakeAccelerationUsesSmoothedMagnitudeDelta() {
		val reading = shakeToSkipReading(
			previousAcceleration = 2f,
			previousMagnitude = 10f,
			x = 0f,
			y = 0f,
			z = 20f
		)

		assertEquals(20f, reading.magnitude)
		assertEquals(11.8f, reading.acceleration)
	}

	@Test
	fun shakeAccelerationComputesVectorMagnitude() {
		val reading = shakeToSkipReading(
			previousAcceleration = 0f,
			previousMagnitude = 0f,
			x = 3f,
			y = 4f,
			z = 12f
		)

		assertEquals(sqrt(169f), reading.magnitude)
	}

	@Test
	fun shakeSkipRequiresEnabledThresholdAndCooldown() {
		assertTrue(
			shouldSkipOnShake(
				shakeToSkip = true,
				acceleration = 13f,
				eventTimeMs = 100L,
				lastSkipTimeMs = 0L
			)
		)
		assertFalse(
			shouldSkipOnShake(
				shakeToSkip = false,
				acceleration = 13f,
				eventTimeMs = 100L,
				lastSkipTimeMs = 0L
			)
		)
		assertFalse(
			shouldSkipOnShake(
				shakeToSkip = true,
				acceleration = 12f,
				eventTimeMs = 100L,
				lastSkipTimeMs = 0L
			)
		)
		assertFalse(
			shouldSkipOnShake(
				shakeToSkip = true,
				acceleration = 13f,
				eventTimeMs = 1_000L,
				lastSkipTimeMs = 500L
			)
		)
		assertTrue(
			shouldSkipOnShake(
				shakeToSkip = true,
				acceleration = 13f,
				eventTimeMs = 2_000L,
				lastSkipTimeMs = 500L
			)
		)
	}
}
