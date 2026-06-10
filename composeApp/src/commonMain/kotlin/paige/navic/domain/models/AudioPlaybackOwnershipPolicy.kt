package paige.navic.domain.models

enum class AudioPlaybackOwner {
	Music,
	Audiobook
}

fun shouldPauseForAudioPlaybackClaim(
	currentOwner: AudioPlaybackOwner,
	claimedOwner: AudioPlaybackOwner,
	isPlaying: Boolean
): Boolean =
	isPlaying && currentOwner != claimedOwner
