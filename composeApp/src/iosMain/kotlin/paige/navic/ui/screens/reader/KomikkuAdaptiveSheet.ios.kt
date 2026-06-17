package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun KomikkuAdaptiveSheet(
	onDismissRequest: () -> Unit,
	modifier: Modifier,
	content: @Composable () -> Unit
) {
	BoxWithConstraints {
		val isTabletUi = maxWidth >= 720.dp
		Dialog(
			onDismissRequest = onDismissRequest,
			properties = DialogProperties(
				usePlatformDefaultWidth = false,
				decorFitsSystemWindows = true
			)
		) {
			if (isTabletUi) {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center
				) {
					Surface(
						modifier = Modifier
							.requiredWidthIn(max = 460.dp)
							.systemBarsPadding()
							.padding(vertical = 16.dp)
							.then(modifier),
						shape = MaterialTheme.shapes.extraLarge,
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						contentColor = MaterialTheme.colorScheme.onSurface,
						content = content
					)
				}
			} else {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.BottomCenter
				) {
					Surface(
						modifier = Modifier
							.widthIn(max = 460.dp)
							.then(modifier),
						shape = MaterialTheme.shapes.extraLarge,
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						contentColor = MaterialTheme.colorScheme.onSurface,
						content = content
					)
				}
			}
		}
	}
}
