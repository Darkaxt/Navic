package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderSelectionNoteDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderSelectionNoteDialog(
	draft: ReaderSelectionNoteDraft,
	onSaveSelectionNote: (String) -> Unit,
	onDismissSelectionNote: () -> Unit
) {
	var noteText by remember(draft.cfi, draft.text) { mutableStateOf(draft.note) }
	BasicAlertDialog(onDismissRequest = onDismissSelectionNote) {
		Surface(
			shape = RoundedCornerShape(24.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.82f)
		) {
			Column(
				verticalArrangement = Arrangement.spacedBy(16.dp),
				modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
			) {
				Text(
					text = "Note",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				Text(
					text = draft.text,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
					maxLines = 3,
					overflow = TextOverflow.Ellipsis
				)
				OutlinedTextField(
					value = noteText,
					onValueChange = { noteText = it },
					label = { Text("Annotation") },
					minLines = 3,
					maxLines = 6,
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 116.dp)
				)
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.align(Alignment.End)
				) {
					TextButton(onClick = onDismissSelectionNote) {
						Text("Cancel")
					}
					TextButton(
						enabled = noteText.isNotBlank(),
						onClick = { onSaveSelectionNote(noteText) }
					) {
						Text("Save")
					}
				}
			}
		}
	}
}
