package paige.navic.domain.models.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LyricsConfig(
	val priority: List<LyricsProvider> = DefaultLyricsProviderPriority,
	val lyricsPlusMirrors: List<String> = listOf(
		"https://lyricsplus.atomix.one",
		"https://lyricsplus-seven.vercel.app",
		"https://lyricsplus.prjktla.workers.dev"
	),
	val lrcLibBaseUrl: String = "https://lrclib.net/api/search"
) {
	companion object {
		const val KEY = "lyrics_config_prefs"
	}
}

val DefaultLyricsProviderPriority = listOf(
	LyricsProvider.SUBSONIC,
	LyricsProvider.LYRICS_PLUS,
	LyricsProvider.LRCLIB
)

private val LegacyDefaultLyricsProviderPriority = listOf(
	LyricsProvider.LYRICS_PLUS,
	LyricsProvider.SUBSONIC,
	LyricsProvider.LRCLIB
)

fun normalizedLyricsConfig(config: LyricsConfig): LyricsConfig =
	if (config.priority == LegacyDefaultLyricsProviderPriority) {
		config.copy(priority = DefaultLyricsProviderPriority)
	} else {
		config
	}
