package paige.navic.domain.models

enum class NowPlayingPlaybackControl {
	Shuffle,
	Previous,
	PlayPause,
	Next,
	Repeat
}

fun nowPlayingPlaybackControls(
	showShuffleControl: Boolean,
	showRepeatControl: Boolean
): List<NowPlayingPlaybackControl> = buildList {
	if (showShuffleControl) {
		add(NowPlayingPlaybackControl.Shuffle)
	}
	add(NowPlayingPlaybackControl.Previous)
	add(NowPlayingPlaybackControl.PlayPause)
	add(NowPlayingPlaybackControl.Next)
	if (showRepeatControl) {
		add(NowPlayingPlaybackControl.Repeat)
	}
}
