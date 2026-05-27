package paige.navic.androidApp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import paige.navic.App
import paige.navic.ui.screens.lidaClips.LidaClipPictureInPictureCoordinator

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent { App() }
	}

	override fun onUserLeaveHint() {
		LidaClipPictureInPictureCoordinator.onUserLeaveHint(this)
		super.onUserLeaveHint()
	}
}
