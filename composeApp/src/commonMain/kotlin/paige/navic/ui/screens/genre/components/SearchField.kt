package paige.navic.ui.screens.genre.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_clear_search
import navic.composeapp.generated.resources.title_search
import org.jetbrains.compose.resources.stringResource
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.Search

@Composable
fun GenreListScreenSearchField(
	query: String,
	onQueryChange: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	OutlinedTextField(
		value = query,
		onValueChange = onQueryChange,
		modifier = modifier.fillMaxWidth(),
		singleLine = true,
		leadingIcon = {
			Icon(Icons.Outlined.Search, contentDescription = null)
		},
		trailingIcon = {
			if (query.isNotEmpty()) {
				IconButton(onClick = { onQueryChange("") }) {
					Icon(
						Icons.Outlined.Close,
						contentDescription = stringResource(Res.string.action_clear_search)
					)
				}
			}
		},
		placeholder = { Text(stringResource(Res.string.title_search)) },
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		shape = MaterialTheme.shapes.large
	)
}
