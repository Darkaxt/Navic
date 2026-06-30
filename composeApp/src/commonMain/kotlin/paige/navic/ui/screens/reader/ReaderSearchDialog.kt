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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.Search
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderSearchState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderSearchDialog(
	search: ReaderSearchState,
	onSearchQuery: (String) -> Unit,
	onNavigateToSearchResult: (ReaderSearchResult) -> Unit,
	onDismissSearch: () -> Unit
) {
	var queryText by remember(search.query) { mutableStateOf(search.query) }
	val listState = rememberLazyListState()
	val searchFocusRequester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		searchFocusRequester.requestFocus()
	}

	BasicAlertDialog(onDismissRequest = onDismissSearch) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.78f)
		) {
			Column(
				modifier = Modifier
					.heightIn(max = 560.dp)
					.padding(horizontal = 24.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "Search",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						modifier = Modifier.weight(1f)
					)
					IconButton(onClick = onDismissSearch) {
						Icon(Icons.Outlined.Close, contentDescription = "Close")
					}
				}
				TextField(
					value = queryText,
					onValueChange = { queryText = it },
					singleLine = true,
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
					keyboardActions = KeyboardActions(onSearch = { onSearchQuery(queryText) }),
					trailingIcon = {
						IconButton(onClick = { onSearchQuery(queryText) }) {
							Icon(Icons.Outlined.Search, contentDescription = "Search")
						}
					},
					modifier = Modifier
						.fillMaxWidth()
						.focusRequester(searchFocusRequester)
				)
				when {
					!search.active && queryText.isBlank() -> KomikkuSettingsDialogLine("Start typing to search")
					search.active && search.results.isEmpty() -> KomikkuSettingsDialogLine("No matches")
					else -> LazyColumn(
						state = listState,
						modifier = Modifier.heightIn(min = 160.dp, max = 360.dp),
						contentPadding = PaddingValues(vertical = 10.dp),
						verticalArrangement = Arrangement.spacedBy(4.dp)
					) {
						items(
							items = search.results,
							key = { result -> result.id }
						) { result ->
							KomikkuReaderSearchResultRow(
								result = result,
								onClick = { onNavigateToSearchResult(result) }
							)
						}
					}
				}
				Row(
					horizontalArrangement = Arrangement.End,
					modifier = Modifier.fillMaxWidth()
				) {
					TextButton(onClick = { onSearchQuery(queryText) }) {
						Text("Search")
					}
					TextButton(onClick = onDismissSearch) {
						Text("Close")
					}
				}
			}
		}
	}
}

@Composable
private fun KomikkuReaderSearchResultRow(
	result: ReaderSearchResult,
	onClick: () -> Unit
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		Text(
			text = result.sectionTitle ?: result.href ?: result.cfi ?: result.id,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		result.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
			Text(
				text = excerpt,
				style = MaterialTheme.typography.bodySmall,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}
