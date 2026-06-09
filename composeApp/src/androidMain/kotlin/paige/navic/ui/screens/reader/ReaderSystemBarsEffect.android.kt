package paige.navic.ui.screens.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import paige.navic.util.core.Logger

@Composable
actual fun ReaderSystemBarsEffect(
	fullscreen: Boolean,
	systemBarsVisible: Boolean
) {
	val activity = LocalActivity.current
	val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }

	DisposableEffect(activity) {
		if (activity == null || controller == null) {
			onDispose {}
		} else {
			val previousSystemBarsBehavior = controller.systemBarsBehavior
			onDispose {
				runCatching {
					controller.systemBarsBehavior = previousSystemBarsBehavior
					controller.show(WindowInsetsCompat.Type.systemBars())
				}.onFailure { error ->
					Logger.w("ReaderSystemBars", "Failed to restore reader system bars", error)
				}
			}
		}
	}

	SideEffect {
		if (controller == null) return@SideEffect
		runCatching {
			if (fullscreen && !systemBarsVisible) {
				controller.systemBarsBehavior =
					WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
				controller.hide(WindowInsetsCompat.Type.systemBars())
			} else {
				controller.show(WindowInsetsCompat.Type.systemBars())
			}
		}.onFailure { error ->
			Logger.w("ReaderSystemBars", "Failed to apply reader system bars", error)
		}
	}
}
