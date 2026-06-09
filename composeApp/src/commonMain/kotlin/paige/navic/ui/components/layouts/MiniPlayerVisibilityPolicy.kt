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

