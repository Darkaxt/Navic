package paige.navic.ui.screens.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
) = AurralDiscoverTagBrick(
	tag = tag,
	modifier = modifier,
	onOpenTag = onOpenTag
)

@Composable
fun AurralDiscoverTagBrick(
	tag: String,
	modifier: Modifier = Modifier,
	onOpenTag: (String) -> Unit
) {
	val platformContext = LocalPlatformContext.current
	Surface(
		modifier = modifier
			.wrapContentWidth()
			.heightIn(min = 32.dp)
			.clickable(
				onClick = dropUnlessResumed {
					platformContext.clickSound()
					onOpenTag(tag)
				}
			),
		shape = MaterialTheme.shapes.small,
		color = MaterialTheme.colorScheme.surfaceVariant
	) {
		Text(
			text = "#$tag",
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AurralDiscoverTagWall(
	tags: List<String>,
	modifier: Modifier = Modifier,
	horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
	verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
	onOpenTag: (String) -> Unit
) {
	FlowRow(
		modifier = modifier,
		horizontalArrangement = horizontalArrangement,
		verticalArrangement = verticalArrangement
	) {
		tags.forEach { tag ->
			AurralDiscoverTagBrick(
				tag = tag,
				onOpenTag = onOpenTag
			)
		}
	}
}
