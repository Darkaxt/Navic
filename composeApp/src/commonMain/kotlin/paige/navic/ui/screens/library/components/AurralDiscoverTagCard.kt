package paige.navic.ui.screens.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import paige.navic.LocalPlatformContext

@Composable
fun AurralDiscoverTagCard(
	tag: String,
	modifier: Modifier = Modifier,
	onOpenTag: (String) -> Unit
) {
	val platformContext = LocalPlatformContext.current
	Surface(
		modifier = modifier
			.height(96.dp)
			.clickable(
				onClick = dropUnlessResumed {
					platformContext.clickSound()
					onOpenTag(tag)
				}
			),
		shape = MaterialTheme.shapes.small,
		color = MaterialTheme.colorScheme.surfaceVariant
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(12.dp),
			verticalArrangement = Arrangement.SpaceBetween
		) {
			Text(
				text = "#",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.primary
			)
			Text(
				text = tag,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Medium,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
