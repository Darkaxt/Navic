package paige.navic.androidApp

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import paige.navic.ui.screens.reader.ReaderPlayLikeCurlReferenceMode
import paige.navic.ui.screens.reader.ReaderPlayLikeCurlReferenceView

class PlayLikeCurlReferenceActivity : ComponentActivity() {
	private lateinit var reader: ReaderPlayLikeCurlReferenceView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.setFlags(
			WindowManager.LayoutParams.FLAG_FULLSCREEN,
			WindowManager.LayoutParams.FLAG_FULLSCREEN
		)
		val mode = if (intent.getBooleanExtra(ExtraDiagnosticPages, false)) {
			ReaderPlayLikeCurlReferenceMode.Diagnostic
		} else {
			ReaderPlayLikeCurlReferenceMode.Reference
		}
		reader = ReaderPlayLikeCurlReferenceView(this, mode)
		val loadingCover = ImageView(this).apply {
			scaleType = ImageView.ScaleType.CENTER_CROP
			setBackgroundColor(Color.BLACK)
		}
		val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
			isIndeterminate = false
			max = 1
		}
		val status = TextView(this).apply {
			setTextColor(Color.WHITE)
			textSize = 16f
			gravity = Gravity.CENTER
			text = "Preparing pages 0 / 0"
		}
		val progressPanel = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			gravity = Gravity.CENTER
			setPadding(48, 32, 48, 32)
			addView(
				status,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				)
			)
			addView(
				progress,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply { topMargin = 20 }
			)
		}
		val loadingOverlay = FrameLayout(this).apply {
			addView(
				loadingCover,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT
				)
			)
			addView(
				View(this@PlayLikeCurlReferenceActivity).apply {
					setBackgroundColor(Color.argb(150, 0, 0, 0))
				},
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT
				)
			)
			addView(
				progressPanel,
				FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.WRAP_CONTENT,
					Gravity.CENTER
				)
			)
		}
		reader.onPreparationCoverReady = loadingCover::setImageBitmap
		reader.onPreparationProgress = { completed, total ->
			progress.max = total.coerceAtLeast(1)
			progress.progress = completed
			status.text = "Preparing pages $completed / $total"
		}
		reader.onInteractionReadyChanged = { ready ->
			loadingOverlay.visibility = if (ready) View.GONE else View.VISIBLE
		}
		reader.onRenderFailure = { failure ->
			Toast.makeText(
				this,
				failure.message ?: "PlayLikeCurl rendering failed",
				Toast.LENGTH_SHORT
			).show()
		}
		setContentView(
			FrameLayout(this).apply {
				addView(
					reader,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT
					)
				)
				addView(
					loadingOverlay,
					FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.MATCH_PARENT
					)
				)
			}
		)
	}

	override fun onResume() {
		super.onResume()
		reader.resumeReference()
	}

	override fun onPause() {
		reader.pauseReference()
		super.onPause()
	}

	override fun onDestroy() {
		reader.disposeReference()
		super.onDestroy()
	}

	private companion object {
		const val ExtraDiagnosticPages = "playlikecurl.diagnostic-pages"
	}
}
