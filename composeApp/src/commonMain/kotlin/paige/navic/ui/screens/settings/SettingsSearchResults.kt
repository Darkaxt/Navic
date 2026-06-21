package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_no_search_results
import org.jetbrains.compose.resources.stringResource
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow

@Composable
fun SettingsSearchResults(query: String) {
	val rows = searchableSettingsRows()
	val rowById = rows.associateBy { it.text.id }
	val resultRows = filteredSettingsSearchResultItems(
		entries = rows.map { it.text },
		query = query
	).mapNotNull { result ->
		rowById[result.entry.id]?.let { row -> result.path to row }
	}

	CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
		if (resultRows.isEmpty()) {
			Form {
				FormRow {
					Text(
						stringResource(Res.string.info_no_search_results),
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			return@CompositionLocalProvider
		}

		resultRows.forEach { (path, row) ->
			Form(bottomPadding = 12.dp) {
				Text(
					text = path,
					modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary
				)
				row.content()
			}
		}
	}
}
