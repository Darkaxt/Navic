package paige.navic.domain.models

enum class VolumeKeySkipKey {
	VolumeUp,
	VolumeDown,
	Other
}

enum class VolumeKeySkipEventAction {
	Down,
	Up,
	Other
}

enum class VolumeKeySkipAction {
	Previous,
	Next
}

data class VolumeKeySkipDecision(
	val consume: Boolean,
	val skipAction: VolumeKeySkipAction?
)

fun volumeKeySkipDecision(
	enabled: Boolean,
	key: VolumeKeySkipKey,
	eventAction: VolumeKeySkipEventAction,
	repeatCount: Int
): VolumeKeySkipDecision {
	if (!enabled || key == VolumeKeySkipKey.Other || eventAction == VolumeKeySkipEventAction.Other) {
		return VolumeKeySkipDecision(consume = false, skipAction = null)
	}

	if (eventAction == VolumeKeySkipEventAction.Up || repeatCount > 0) {
		return VolumeKeySkipDecision(consume = true, skipAction = null)
	}

	return VolumeKeySkipDecision(
		consume = true,
		skipAction = when (key) {
			VolumeKeySkipKey.VolumeUp -> VolumeKeySkipAction.Next
			VolumeKeySkipKey.VolumeDown -> VolumeKeySkipAction.Previous
			VolumeKeySkipKey.Other -> null
		}
	)
}
