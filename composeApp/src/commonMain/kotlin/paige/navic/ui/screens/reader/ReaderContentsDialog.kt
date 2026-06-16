package paige.navic.ui.screens.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderTocItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderContentsDialog(
	toc: List<ReaderTocItem>,
	onNavigateTo: (ReaderTocItem) -> Unit,
	onDismissRequest: () -> Unit
) {
	val listState = rememberLazyListState()
	BasicAlertDialog(onDismissRequest = onDismissRequest) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.78f)
		) {
			Column(
				modifier = Modifier
					.heightIn(max = 520.dp)
					.padding(horizontal = 24.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Text(
					text = "Contents",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
				if (toc.isEmpty()) {
					KomikkuSettingsDialogLine("No table of contents available")
				} else {
					LazyColumn(
						state = listState,
						modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
						contentPadding = PaddingValues(vertical = 16.dp),
						verticalArrangement = Arrangement.spacedBy(4.dp)
					) {
						items(
							items = toc,
							key = { item -> "${item.level}:${item.href.orEmpty()}:${item.title}" }
						) { item ->
							Text(
								text = item.title,
								style = MaterialTheme.typography.bodyLarge,
								color = if (item.href.isNullOrBlank()) {
									MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
								} else {
									MaterialTheme.colorScheme.onSurface
								},
								modifier = Modifier
									.fillMaxWidth()
									.clip(RoundedCornerShape(14.dp))
									.clickable(enabled = !item.href.isNullOrBlank()) {
										onNavigateTo(item)
									}
									.padding(
										start = (item.level.coerceAtLeast(0) * 16).dp,
										top = 10.dp,
										end = 10.dp,
										bottom = 10.dp
									)
							)
						}
					}
				}
				TextButton(
					onClick = onDismissRequest,
					modifier = Modifier.align(Alignment.End)
				) {
					Text("Close")
				}
			}
		}
	}
}
