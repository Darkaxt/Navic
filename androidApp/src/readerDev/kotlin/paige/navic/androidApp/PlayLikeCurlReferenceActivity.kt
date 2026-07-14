package paige.navic.androidApp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import paige.navic.ui.screens.reader.ReaderPlayLikeCurlReferenceView

class PlayLikeCurlReferenceActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.setFlags(
			WindowManager.LayoutParams.FLAG_FULLSCREEN,
			WindowManager.LayoutParams.FLAG_FULLSCREEN
		)
		setContentView(ReaderPlayLikeCurlReferenceView(this))
	}
}
