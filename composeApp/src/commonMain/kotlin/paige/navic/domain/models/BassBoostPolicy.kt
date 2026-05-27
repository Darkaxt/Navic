package paige.navic.domain.models

fun bassBoostStrengthPermille(strength: Int): Short =
	strength.coerceIn(0, 1000).toShort()

fun shouldEnableBassBoost(
	bassBoostEnabled: Boolean,
	audioSessionId: Int?
): Boolean = bassBoostEnabled && audioSessionId != null && audioSessionId > 0
