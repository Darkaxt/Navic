package paige.navic.ui.screens.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal object SettingTrailingValueLayout {
	private const val MIN_WIDTH_DP = 96
	private const val MAX_WIDTH_DP = 160

	val minWidth get() = MIN_WIDTH_DP.dp
	val maxWidth get() = MAX_WIDTH_DP.dp

	fun reservedWidthDp(intrinsicLabelWidthDp: Int): Int =
		intrinsicLabelWidthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
}

@Composable
internal fun SettingTrailingValueText(
	text: String,
	modifier: Modifier = Modifier
) {
	Text(
		text = text,
		modifier = modifier
			.padding(start = 16.dp)
			.widthIn(
				min = SettingTrailingValueLayout.minWidth,
				max = SettingTrailingValueLayout.maxWidth
			),
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		textAlign = TextAlign.End,
		maxLines = 2,
		overflow = TextOverflow.Ellipsis
	)
}
