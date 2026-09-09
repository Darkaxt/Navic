package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun VisualContentLifecycleEffect(visible: Boolean, owner: Any = Unit, onActiveChanged: (Boolean) -> Unit) {
	LifecycleResumeEffect(visible, owner) {
		onActiveChanged(visible)
		onPauseOrDispose { onActiveChanged(false) }
	}
}
