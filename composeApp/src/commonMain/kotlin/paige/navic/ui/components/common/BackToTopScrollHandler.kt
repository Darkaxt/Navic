package paige.navic.ui.components.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import paige.navic.LocalBottomBarScrollManager

@Composable
fun BackToTopScrollHandler(
	state: LazyGridState,
	enabled: Boolean = true
) {
	val scrollManager = LocalBottomBarScrollManager.current
	val scope = rememberCoroutineScope()
	val enabledState = rememberUpdatedState(enabled)
	val handler = remember(state, scrollManager, scope) {
		{
			val scrolled = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
			if (!enabledState.value || (!scrolled && !scrollManager.isTriggered)) {
				false
			} else {
				scrollManager.reset()
				if (scrolled) {
					scope.launch { state.animateScrollToItem(0) }
				}
				true
			}
		}
	}
	DisposableEffect(scrollManager, handler) {
		scrollManager.registerBackToTopHandler(handler)
		onDispose { scrollManager.unregisterBackToTopHandler(handler) }
	}
}

@Composable
fun BackToTopScrollHandler(
	state: LazyListState,
	enabled: Boolean = true
) {
	val scrollManager = LocalBottomBarScrollManager.current
	val scope = rememberCoroutineScope()
	val enabledState = rememberUpdatedState(enabled)
	val handler = remember(state, scrollManager, scope) {
		{
			val scrolled = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
			if (!enabledState.value || (!scrolled && !scrollManager.isTriggered)) {
				false
			} else {
				scrollManager.reset()
				if (scrolled) {
					scope.launch { state.animateScrollToItem(0) }
				}
				true
			}
		}
	}
	DisposableEffect(scrollManager, handler) {
		scrollManager.registerBackToTopHandler(handler)
		onDispose { scrollManager.unregisterBackToTopHandler(handler) }
	}
}

@Composable
fun BackToTopScrollHandler(
	state: ScrollState,
	enabled: Boolean = true
) {
	val scrollManager = LocalBottomBarScrollManager.current
	val scope = rememberCoroutineScope()
	val enabledState = rememberUpdatedState(enabled)
	val handler = remember(state, scrollManager, scope) {
		{
			val scrolled = state.value > 0
			if (!enabledState.value || (!scrolled && !scrollManager.isTriggered)) {
				false
			} else {
				scrollManager.reset()
				if (scrolled) {
					scope.launch { state.animateScrollTo(0) }
				}
				true
			}
		}
	}
	DisposableEffect(scrollManager, handler) {
		scrollManager.registerBackToTopHandler(handler)
		onDispose { scrollManager.unregisterBackToTopHandler(handler) }
	}
}
