package paige.navic.ui.components.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import paige.navic.domain.models.AurralAcquisitionProgress

@Composable
fun AurralAcquisitionProgressBar(
	progress: AurralAcquisitionProgress,
	modifier: Modifier = Modifier
) {
	val color = when {
		progress.failed -> MaterialTheme.colorScheme.error
		progress.completed -> MaterialTheme.colorScheme.primary
		else -> MaterialTheme.colorScheme.tertiary
	}
	val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
	if (progress.active) {
		LinearProgressIndicator(
			modifier = modifier
				.fillMaxWidth()
				.height(4.dp),
			color = color,
			trackColor = trackColor
		)
	} else {
		LinearProgressIndicator(
			progress = { 1f },
			modifier = modifier
				.fillMaxWidth()
				.height(4.dp),
			color = color,
			trackColor = trackColor
		)
	}
}
