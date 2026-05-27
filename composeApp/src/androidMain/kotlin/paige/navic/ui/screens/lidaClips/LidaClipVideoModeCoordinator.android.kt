package paige.navic.ui.screens.lidaClips

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import paige.navic.domain.models.shouldUseLidaClipsLandscapeVideoMode
import paige.navic.util.core.Logger

object LidaClipVideoModeCoordinator {
	private const val TAG = "LidaClipVideoMode"

	private var activeActivity: Activity? = null
	private var restoreState: RestoreState? = null

	fun register(activity: Activity, enabled: Boolean) {
		if (activeActivity !== activity) {
			activeActivity?.let(::restoreVideoMode)
			activeActivity = activity
		}

		if (shouldUseLidaClipsLandscapeVideoMode(enabled = enabled, videoActive = true)) {
			applyVideoMode(activity)
		} else {
			restoreVideoMode(activity)
		}
	}

	fun unregister(activity: Activity) {
		if (activeActivity !== activity) return

		restoreVideoMode(activity)
		activeActivity = null
	}

	private fun applyVideoMode(activity: Activity) {
		if (restoreState == null) {
			val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
			restoreState = RestoreState(
				requestedOrientation = activity.requestedOrientation,
				systemBarsBehavior = controller.systemBarsBehavior
			)
		}

		runCatching {
			activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
			val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
			controller.systemBarsBehavior =
				androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			controller.hide(WindowInsetsCompat.Type.systemBars())
		}.onFailure { error ->
			Logger.w(TAG, "Failed to apply LidaClips landscape video mode", error)
		}
	}

	private fun restoreVideoMode(activity: Activity) {
		val state = restoreState ?: return
		restoreState = null

		runCatching {
			activity.requestedOrientation = state.requestedOrientation
			val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
			controller.systemBarsBehavior = state.systemBarsBehavior
			controller.show(WindowInsetsCompat.Type.systemBars())
		}.onFailure { error ->
			Logger.w(TAG, "Failed to restore LidaClips landscape video mode", error)
		}
	}

	private data class RestoreState(
		val requestedOrientation: Int,
		val systemBarsBehavior: Int
	)
}
