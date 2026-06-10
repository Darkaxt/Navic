package paige.navic.ui.components.layouts

import paige.navic.domain.models.settings.BottomBarProfile
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.bottomBarProfileForScreen

enum class MiniPlayerPlaybackKind {
	None,
	Music,
	Audiobook
}

fun shouldShowMiniPlayerForRoute(
	screen: Screen?,
	playbackKind: MiniPlayerPlaybackKind,
	binderyEnabled: Boolean
): Boolean {
	val isAudiobookSurface = bottomBarProfileForScreen(
		screen = screen,
		rememberedProfile = BottomBarProfile.Music,
		binderyEnabled = binderyEnabled
	) == BottomBarProfile.Audiobooks
	return !isAudiobookSurface || playbackKind == MiniPlayerPlaybackKind.Audiobook
}

fun rootMiniPlayerPlaybackKind(
	screen: Screen?,
	hasMusicPlayback: Boolean,
	audiobookAvailable: Boolean,
	audiobookPlaying: Boolean,
	binderyEnabled: Boolean
): MiniPlayerPlaybackKind {
	val profile = bottomBarProfileForScreen(
		screen = screen,
		rememberedProfile = BottomBarProfile.Music,
		binderyEnabled = binderyEnabled
	)
	return when {
		profile == BottomBarProfile.Audiobooks && audiobookAvailable -> MiniPlayerPlaybackKind.Audiobook
		profile == BottomBarProfile.Music && hasMusicPlayback -> MiniPlayerPlaybackKind.Music
		else -> MiniPlayerPlaybackKind.None
	}
}
