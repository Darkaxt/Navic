package paige.navic.domain.models

const val DefaultLyricsAccentBackgroundAlpha = 0.14f

fun lyricsAccentBackgroundAlpha(enabled: Boolean): Float =
	if (enabled) DefaultLyricsAccentBackgroundAlpha else 0f
