package paige.navic.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import paige.navic.domain.models.AudioPlaybackOwner
import paige.navic.util.core.Logger

class AudioPlaybackOwnershipClaim internal constructor(
	val owner: AudioPlaybackOwner
)

class AudioPlaybackOwnershipCoordinator {
	private val _activeClaim = MutableStateFlow<AudioPlaybackOwnershipClaim?>(null)
	val activeClaim: StateFlow<AudioPlaybackOwnershipClaim?> = _activeClaim.asStateFlow()

	val currentOwner: AudioPlaybackOwner?
		get() = _activeClaim.value?.owner

	fun claim(owner: AudioPlaybackOwner): AudioPlaybackOwnershipClaim =
		AudioPlaybackOwnershipClaim(owner).also { claim ->
			_activeClaim.value = claim
			Logger.i("AudioPlaybackOwnership", "claimed owner=$owner")
		}

	fun release(claim: AudioPlaybackOwnershipClaim): Boolean =
		_activeClaim.compareAndSet(claim, null).also { released ->
			if (released) Logger.i("AudioPlaybackOwnership", "released owner=${claim.owner}")
		}
}
