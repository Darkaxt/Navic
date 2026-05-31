package paige.navic.ui.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.dropUnlessResumed
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.LidaClipAvailability
import paige.navic.domain.models.lidaClipAvailability
import paige.navic.domain.models.shouldShowLidaClipsMusicVideoAction
import paige.navic.domain.models.shouldShowVerifiedLidaClipsMusicVideoAction
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.ui.navigation.Screen

@Composable
fun lidaClipsMusicVideoAction(song: DomainSong): (() -> Unit)? {
	val preferenceManager = koinInject<PreferenceManager>()
	val repository = koinInject<LidaClipsRepository>()
	val backStack = LocalNavStack.current
	val lidaClipsEnabled = preferenceManager.lidaClipsEnabled
	val lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl
	val userActionEnabled = preferenceManager.showNowPlayingMusicVideoAction
	val songId = song.id
	val shouldResolveClip = shouldShowLidaClipsMusicVideoAction(
		lidaClipsEnabled = lidaClipsEnabled,
		lidaClipsBaseUrl = lidaClipsBaseUrl,
		userActionEnabled = userActionEnabled,
		songId = songId
	)
	val clipAvailability by produceState(
		initialValue = LidaClipAvailability.Unknown,
		shouldResolveClip,
		lidaClipsBaseUrl,
		preferenceManager.lidaClipsApiKey,
		songId
	) {
		value = LidaClipAvailability.Unknown
		if (!shouldResolveClip) {
			value = LidaClipAvailability.Unavailable
			return@produceState
		}
		value = repository.findClipByNavidromeSongId(song.id).fold(
			onSuccess = ::lidaClipAvailability,
			onFailure = { LidaClipAvailability.Unknown }
		)
	}
	return if (shouldShowVerifiedLidaClipsMusicVideoAction(
			lidaClipsEnabled = lidaClipsEnabled,
			lidaClipsBaseUrl = lidaClipsBaseUrl,
			userActionEnabled = userActionEnabled,
			songId = songId,
			clipAvailability = clipAvailability
		)
	) {
		dropUnlessResumed {
			backStack.add(Screen.LidaClipPlayer(songId))
		}
	} else null
}
