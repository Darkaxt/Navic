package paige.navic.domain.models

import kotlin.math.sqrt

const val ShakeToSkipThreshold = 12f
const val ShakeToSkipCooldownMs = 1_500L

data class ShakeToSkipReading(
	val magnitude: Float,
	val acceleration: Float
)

fun shakeToSkipReading(
	previousAcceleration: Float,
	previousMagnitude: Float,
	x: Float,
	y: Float,
	z: Float
): ShakeToSkipReading {
	val magnitude = sqrt((x * x) + (y * y) + (z * z))
	val acceleration = (previousAcceleration * 0.9f) + (magnitude - previousMagnitude)
	return ShakeToSkipReading(
		magnitude = magnitude,
		acceleration = acceleration
	)
}

fun shouldSkipOnShake(
	shakeToSkip: Boolean,
	acceleration: Float,
	eventTimeMs: Long,
	lastSkipTimeMs: Long,
	cooldownMs: Long = ShakeToSkipCooldownMs
): Boolean =
	shakeToSkip &&
		acceleration > ShakeToSkipThreshold &&
		(lastSkipTimeMs <= 0L || eventTimeMs - lastSkipTimeMs >= cooldownMs.coerceAtLeast(0L))
