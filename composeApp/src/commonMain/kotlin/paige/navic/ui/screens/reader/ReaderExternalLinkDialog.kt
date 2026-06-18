package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderExternalLinkPromptState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderExternalLinkDialog(
	link: ReaderExternalLinkPromptState,
	onOpenExternalLink: (String) -> Unit,
	onDismissExternalLinkPrompt: () -> Unit
) {
	BasicAlertDialog(onDismissRequest = onDismissExternalLinkPrompt) {
		Surface(
			shape = RoundedCornerShape(24.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.78f)
		) {
			Column(
				verticalArrangement = Arrangement.spacedBy(14.dp),
				modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
			) {
				Text(
					text = "External link",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				Text(
					text = link.href,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
					maxLines = 4,
					overflow = TextOverflow.Ellipsis
				)
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.align(Alignment.End)
				) {
					TextButton(onClick = onDismissExternalLinkPrompt) {
						Text("Close")
					}
					TextButton(onClick = { onOpenExternalLink(link.href) }) {
						Text("Open")
					}
				}
			}
		}
	}
}
