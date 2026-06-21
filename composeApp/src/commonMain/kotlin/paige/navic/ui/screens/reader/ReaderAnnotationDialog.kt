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
import paige.navic.reader.ReaderAnnotationPopupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderAnnotationDialog(
	annotation: ReaderAnnotationPopupState,
	onDismissAnnotationPopup: () -> Unit
) {
	val selectedText = annotation.text?.trim()?.takeIf { it.isNotEmpty() }
	val noteText = annotation.note?.trim()?.takeIf { it.isNotEmpty() }
	val fallbackText = annotation.value
		?.takeIf { it.isNotBlank() }
		?: annotation.rangeCfi
		?: annotation.index?.let { "Annotation $it" }
		.orEmpty()
	BasicAlertDialog(onDismissRequest = onDismissAnnotationPopup) {
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
					text = if (noteText != null) "Note" else "Annotation",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				if (selectedText != null) {
					Text(
						text = selectedText,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
						maxLines = 5,
						overflow = TextOverflow.Ellipsis
					)
				}
				Text(
					text = noteText ?: fallbackText,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (noteText != null) 0.9f else 0.78f),
					maxLines = 8,
					overflow = TextOverflow.Ellipsis
				)
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.align(Alignment.End)
				) {
					TextButton(onClick = onDismissAnnotationPopup) {
						Text("Close")
					}
				}
			}
		}
	}
}
