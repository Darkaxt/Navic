package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.Copy
import paige.navic.icons.outlined.Note
import paige.navic.reader.ReaderSelectionActionState

@Composable
internal fun KomikkuReaderSelectionActions(
	selectionActions: ReaderSelectionActionState,
	onHighlightSelection: () -> Unit,
	onCopySelection: (String) -> Unit,
	onStartSelectionNote: () -> Unit,
	modifier: Modifier = Modifier
) {
	AnimatedVisibility(
		visible = selectionActions.visible,
		enter = fadeIn(),
		exit = fadeOut(),
		modifier = modifier
	) {
		Surface(
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.96f),
			contentColor = MaterialTheme.colorScheme.onSurface,
			shape = MaterialTheme.shapes.small,
			tonalElevation = 6.dp
		) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
			) {
				ReaderSelectionActionButton(
					label = "Highlight",
					icon = Icons.Outlined.Book,
					enabled = selectionActions.canHighlight,
					onClick = onHighlightSelection
				)
				ReaderSelectionActionButton(
					label = "Copy",
					icon = Icons.Outlined.Copy,
					enabled = selectionActions.canCopy && selectionActions.selectedText != null,
					onClick = { selectionActions.selectedText?.let(onCopySelection) }
				)
				ReaderSelectionActionButton(
					label = "Note",
					icon = Icons.Outlined.Note,
					enabled = selectionActions.canNote,
					onClick = onStartSelectionNote
				)
			}
		}
	}
}

@Composable
private fun ReaderSelectionActionButton(
	label: String,
	icon: ImageVector,
	enabled: Boolean,
	onClick: () -> Unit
) {
	TextButton(
		enabled = enabled,
		onClick = onClick
	) {
		Icon(icon, contentDescription = label)
		Spacer(modifier = Modifier.width(6.dp))
		Text(
			text = label,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}
