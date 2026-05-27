package paige.navic.androidApp

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.App
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.VolumeKeySkipAction
import paige.navic.domain.models.VolumeKeySkipEventAction
import paige.navic.domain.models.VolumeKeySkipKey
import paige.navic.domain.models.volumeKeySkipDecision
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.screens.lidaClips.LidaClipPictureInPictureCoordinator

class MainActivity : ComponentActivity(), KoinComponent {
	private val preferenceManager: PreferenceManager by inject()
	private val player: MediaPlayerViewModel by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent { App() }
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		val decision = volumeKeySkipDecision(
			enabled = preferenceManager.volumeKeysSkipTracks,
			key = event.toVolumeKeySkipKey(),
			eventAction = event.toVolumeKeySkipEventAction(),
			repeatCount = event.repeatCount
		)

		when (decision.skipAction) {
			VolumeKeySkipAction.Next -> player.next()
			VolumeKeySkipAction.Previous -> player.previous()
			null -> Unit
		}

		return if (decision.consume) true else super.dispatchKeyEvent(event)
	}

	override fun onUserLeaveHint() {
		LidaClipPictureInPictureCoordinator.onUserLeaveHint(this)
		super.onUserLeaveHint()
	}
}

private fun KeyEvent.toVolumeKeySkipKey(): VolumeKeySkipKey =
	when (keyCode) {
		KeyEvent.KEYCODE_VOLUME_UP -> VolumeKeySkipKey.VolumeUp
		KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKeySkipKey.VolumeDown
		else -> VolumeKeySkipKey.Other
	}

private fun KeyEvent.toVolumeKeySkipEventAction(): VolumeKeySkipEventAction =
	when (action) {
		KeyEvent.ACTION_DOWN -> VolumeKeySkipEventAction.Down
		KeyEvent.ACTION_UP -> VolumeKeySkipEventAction.Up
		else -> VolumeKeySkipEventAction.Other
	}
