package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.ChevronForward
import paige.navic.icons.outlined.Close
import paige.navic.reader.ReaderEngineNavigationState

@Composable
internal fun KomikkuReaderHistoryCapsule(
	navigation: ReaderEngineNavigationState,
	onHistoryBack: () -> Unit,
	onHistoryForward: () -> Unit,
	onDismissHistory: () -> Unit,
	modifier: Modifier = Modifier
) {
	if (!navigation.visible) return

	Surface(
		modifier = modifier.pointerInput(Unit) {},
		shape = RoundedCornerShape(32.dp),
		color = MaterialTheme.colorScheme
			.surfaceColorAtElevation(4.dp)
			.copy(alpha = 0.86f),
		tonalElevation = 4.dp,
		shadowElevation = 2.dp
	) {
		Row(
			modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically
		) {
			if (navigation.canGoBack) {
				IconButton(onClick = onHistoryBack) {
					Icon(
						imageVector = Icons.Outlined.ArrowBack,
						contentDescription = "History back",
						tint = MaterialTheme.colorScheme.primary
					)
				}
			}
			IconButton(onClick = onDismissHistory) {
				Icon(
					imageVector = Icons.Outlined.Close,
					contentDescription = "Close history controls",
					tint = MaterialTheme.colorScheme.primary
				)
			}
			if (navigation.canGoForward) {
				IconButton(onClick = onHistoryForward) {
					Icon(
						imageVector = Icons.Outlined.ChevronForward,
						contentDescription = "History forward",
						tint = MaterialTheme.colorScheme.primary
					)
				}
			}
		}
	}
}
