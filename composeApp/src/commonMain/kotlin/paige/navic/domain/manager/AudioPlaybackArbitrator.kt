package paige.navic.domain.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import paige.navic.domain.models.AudioPlaybackOwner

class AudioPlaybackArbitrator {
	private val _claims = MutableSharedFlow<AudioPlaybackOwner>(extraBufferCapacity = 8)
	val claims: SharedFlow<AudioPlaybackOwner> = _claims.asSharedFlow()

	fun claim(owner: AudioPlaybackOwner) {
		_claims.tryEmit(owner)
	}
}
