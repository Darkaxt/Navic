package paige.navic.ui.screens.reader

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderRendererBusyPopup(
	visibleState: MutableTransitionState<Boolean>,
	bottomOffset: Dp
) {
	val anchor = LocalView.current
	val parentComposition = rememberCompositionContext()
	val density = LocalDensity.current
	val bottomOffsetPx = with(density) { bottomOffset.roundToPx() }
	val popupElevationPx = with(density) { 32.dp.toPx() }
	val popup = remember(anchor, parentComposition, visibleState, popupElevationPx) {
		val contentView = ComposeView(anchor.context).apply {
			setParentCompositionContext(parentComposition)
			setViewCompositionStrategy(
				ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
			)
			setContent {
				Box(
					modifier = Modifier.size(48.dp),
					contentAlignment = Alignment.Center
				) {
					ReaderRendererBusyIndicator(visibleState = visibleState)
				}
			}
		}
		PopupWindow(
			contentView,
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT,
			false
		).apply {
			setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
			windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
			isFocusable = false
			isTouchable = false
			isOutsideTouchable = false
			inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
			elevation = popupElevationPx
		}
	}

	SideEffect {
		val shouldShow = visibleState.currentState || visibleState.targetState
		if (shouldShow && !popup.isShowing && anchor.isAttachedToWindow) {
			popup.showAtLocation(
				anchor.rootView,
				Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
				0,
				bottomOffsetPx
			)
		} else if (!shouldShow && popup.isShowing) {
			popup.dismiss()
		}
	}

	DisposableEffect(popup) {
		onDispose {
			popup.dismiss()
			(popup.contentView as ComposeView).disposeComposition()
		}
	}
}
