package paige.navic.androidApp

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import paige.navic.ui.screens.reader.ReaderPassiveRasterParityHarness
import paige.navic.ui.screens.reader.createReaderPassiveRasterParityHarness

class ReaderPassiveRasterParityActivity : ComponentActivity() {
	private lateinit var parityHarness: ReaderPassiveRasterParityHarness

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val statusView = TextView(this).apply {
			setTextColor(Color.WHITE)
			setBackgroundColor(Color.rgb(34, 34, 34))
			setPadding(24, 16, 24, 16)
			gravity = Gravity.START
			text = "Passive raster parity\nstatus=starting\ncaptureAttempts=0\n" +
				"captureSuccesses=0\ncaptureFailures=0"
		}
		val webViewContainer = FrameLayout(this).apply {
			setBackgroundColor(Color.BLACK)
		}
		setContentView(
			LinearLayout(this).apply {
				orientation = LinearLayout.VERTICAL
				addView(
					statusView,
					LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					)
				)
				addView(
					webViewContainer,
					LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						0,
						1f
					)
				)
			}
		)
		parityHarness = createReaderPassiveRasterParityHarness(
			activity = this,
			webViewContainer = webViewContainer,
			statusView = statusView
		)
	}

	override fun onResume() {
		super.onResume()
		if (::parityHarness.isInitialized) parityHarness.resume()
	}

	override fun onPause() {
		if (::parityHarness.isInitialized) parityHarness.pause()
		super.onPause()
	}

	override fun onDestroy() {
		if (::parityHarness.isInitialized) parityHarness.close()
		super.onDestroy()
	}
}
