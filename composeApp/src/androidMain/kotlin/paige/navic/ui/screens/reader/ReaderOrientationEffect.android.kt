package paige.navic.ui.screens.reader

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderOrientationFree
import paige.navic.reader.ReaderOrientationLandscape
import paige.navic.reader.ReaderOrientationLockedLandscape
import paige.navic.reader.ReaderOrientationLockedPortrait
import paige.navic.reader.ReaderOrientationPortrait
import paige.navic.reader.ReaderOrientationReversePortrait
import paige.navic.reader.normalizedReaderOrientation
import paige.navic.util.core.Logger

@Composable
actual fun ReaderOrientationEffect(orientation: String?) {
	val activity = LocalActivity.current
	val normalizedOrientation = normalizedReaderOrientation(orientation)

	DisposableEffect(activity, normalizedOrientation) {
		if (activity == null || normalizedOrientation == ReaderOrientationDefault) {
			onDispose {}
		} else {
			val previousOrientation = activity.requestedOrientation
			runCatching {
				activity.requestedOrientation = normalizedOrientation.toActivityOrientation()
			}.onFailure { error ->
				Logger.w("ReaderOrientation", "Failed to apply reader orientation", error)
			}

			onDispose {
				runCatching {
					activity.requestedOrientation = previousOrientation
				}.onFailure { error ->
					Logger.w("ReaderOrientation", "Failed to restore reader orientation", error)
				}
			}
		}
	}
}

private fun String.toActivityOrientation(): Int =
	when (this) {
		ReaderOrientationFree -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
		ReaderOrientationPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
		ReaderOrientationLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
		ReaderOrientationLockedPortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		ReaderOrientationLockedLandscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		ReaderOrientationReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
		else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
	}
