package paige.navic.ui.screens.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderAnnotation
import paige.navic.reader.ReaderBookmark
import paige.navic.reader.ReaderTocItem
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderContentsDialog(
	toc: List<ReaderTocItem>,
	bookmarks: List<ReaderBookmark>,
	annotations: List<ReaderAnnotation>,
	onNavigateTo: (ReaderTocItem) -> Unit,
	onNavigateToBookmark: (ReaderBookmark) -> Unit,
	onNavigateToAnnotation: (ReaderAnnotation) -> Unit,
	onDismissRequest: () -> Unit
) {
	val listState = rememberLazyListState()
	var selectedTab by remember { mutableStateOf(ReaderContentsTab.Contents) }
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
				ReaderContentsTabs(
					selectedTab = selectedTab,
					onSelectedTab = { tab -> selectedTab = tab }
				)
				LazyColumn(
					state = listState,
					modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
					contentPadding = PaddingValues(vertical = 16.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					when (selectedTab) {
						ReaderContentsTab.Contents -> if (toc.isEmpty()) {
							item { KomikkuSettingsDialogLine("No table of contents available") }
						} else {
							items(
								items = toc,
								key = { item -> "${item.level}:${item.href.orEmpty()}:${item.title}" }
							) { item ->
								ReaderTocRow(
									item = item,
									onNavigateTo = onNavigateTo
								)
							}
						}
						ReaderContentsTab.Bookmarks -> if (bookmarks.isEmpty()) {
							item { KomikkuSettingsDialogLine("No bookmarks saved") }
						} else {
							items(
								items = bookmarks,
								key = ReaderBookmark::id
							) { bookmark ->
								ReaderSavedMarkRow(
									title = bookmark.displayTitle,
									detail = bookmark.detailLabel(),
									onClick = { onNavigateToBookmark(bookmark) }
								)
							}
						}
						ReaderContentsTab.Notes -> if (annotations.isEmpty()) {
							item { KomikkuSettingsDialogLine("No highlights or notes saved") }
						} else {
							items(
								items = annotations,
								key = ReaderAnnotation::id
							) { annotation ->
								ReaderSavedMarkRow(
									title = annotation.displayTitle,
									detail = annotation.detailLabel(),
									onClick = { onNavigateToAnnotation(annotation) }
								)
							}
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

private enum class ReaderContentsTab(
	val label: String
) {
	Contents("Contents"),
	Bookmarks("Bookmarks"),
	Notes("Notes")
}

@Composable
private fun ReaderContentsTabs(
	selectedTab: ReaderContentsTab,
	onSelectedTab: (ReaderContentsTab) -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		ReaderContentsTab.entries.forEach { tab ->
			FilterChip(
				selected = selectedTab == tab,
				onClick = { onSelectedTab(tab) },
				label = {
					Text(
						text = tab.label,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			)
		}
	}
}

@Composable
private fun ReaderTocRow(
	item: ReaderTocItem,
	onNavigateTo: (ReaderTocItem) -> Unit
) {
	Text(
		text = item.title,
		style = MaterialTheme.typography.bodyLarge,
		color = if (item.href.isNullOrBlank()) {
			MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
		} else {
			MaterialTheme.colorScheme.onSurface
		},
		maxLines = 2,
		overflow = TextOverflow.Ellipsis,
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

@Composable
private fun ReaderSavedMarkRow(
	title: String,
	detail: String?,
	onClick: () -> Unit
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick)
			.padding(horizontal = 10.dp, vertical = 10.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis
		)
		detail?.let { value ->
			Text(
				text = value,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

private fun ReaderBookmark.detailLabel(): String? =
	progress?.let { progressValue -> "${(progressValue * 100.0).roundToInt()}%" }
		?: href?.takeIf { it.isNotBlank() }
		?: cfi?.takeIf { it.isNotBlank() }

private fun ReaderAnnotation.detailLabel(): String? =
	note?.takeIf { it.isNotBlank() }
		?: text.takeIf { it.isNotBlank() }
