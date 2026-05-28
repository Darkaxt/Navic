package paige.navic.ui.components.sheets

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.shouldShowLidaClipsMusicVideoAction
import paige.navic.ui.navigation.Screen

@Composable
fun lidaClipsMusicVideoAction(songId: String): (() -> Unit)? {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	return if (shouldShowLidaClipsMusicVideoAction(
			lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
			lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
			userActionEnabled = preferenceManager.showNowPlayingMusicVideoAction,
			songId = songId
		)
	) {
		dropUnlessResumed {
			backStack.add(Screen.LidaClipPlayer(songId))
		}
	} else null
}
