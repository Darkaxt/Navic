package paige.navic.ui.screens.aurral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_search_aurral_artists
import navic.composeapp.generated.resources.info_aurral_search_empty
import navic.composeapp.generated.resources.info_aurral_search_failed
import navic.composeapp.generated.resources.option_aurral_artist_search
import navic.composeapp.generated.resources.title_aurral_search
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.AurralArtistSearchResult
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Search
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.core.UiState

@Composable
fun AurralArtistSearchDialog(
	query: String,
	artistState: UiState<AurralArtistSearchResult?>,
	actionState: UiState<Unit?>,
	activeArtistId: String?,
	canMonitorArtist: Boolean,
	confirmationQueue: List<AurralConfirmationQueueItem> = emptyList(),
	preferenceManager: PreferenceManager,
	onQueryChange: (String) -> Unit,
	onSearchArtists: () -> Unit,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit,
	onDismissRequest: () -> Unit
) {
	val trimmedQuery = query.trim()
	val artists = artistState.data?.artists?.let { aurralHubSearchArtists(it) }.orEmpty()
	val searching = artistState is UiState.Loading
	val actionInProgress = actionState is UiState.Loading

	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Search, null) },
		title = { Text(stringResource(Res.string.title_aurral_search)) },
		buttons = {
			FormButton(
				onClick = onSearchArtists,
				enabled = trimmedQuery.isNotEmpty() && !searching,
				color = MaterialTheme.colorScheme.primary
			) {
				if (searching) {
					CircularProgressIndicator(modifier = Modifier.size(20.dp))
				} else {
					Text(stringResource(Res.string.action_search_aurral_artists))
				}
			}
			FormButton(
				onClick = onDismissRequest,
				enabled = !searching
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			Column {
				TextField(
					value = query,
					onValueChange = onQueryChange,
					label = { Text(stringResource(Res.string.option_aurral_artist_search)) },
					singleLine = true,
					enabled = !searching,
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
					keyboardActions = KeyboardActions(
						onSearch = {
							if (trimmedQuery.isNotEmpty()) onSearchArtists()
						}
					),
					modifier = Modifier.fillMaxWidth()
				)
				if (searching) {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
				when {
					artists.isNotEmpty() -> Form(Modifier.fillMaxWidth()) {
						artists.forEach { artist ->
							AurralHubDiscoverArtistRow(
								artist = artist,
								canMonitorArtist = canMonitorArtist,
								actionInProgress = actionInProgress,
								active = activeArtistId == artist.id,
								monitorState = aurralDiscoverArtistMonitorActionState(artist, confirmationQueue),
								preferenceManager = preferenceManager,
								onMonitorArtist = onMonitorArtist,
								onOpenArtist = onOpenArtist
							)
						}
					}

					trimmedQuery.isNotEmpty() && artistState is UiState.Success && artistState.data != null ->
						Form(Modifier.fillMaxWidth()) {
							FormRow(contentPadding = PaddingValues(14.dp)) {
								Text(stringResource(Res.string.info_aurral_search_empty))
							}
						}
				}
				if (artistState is UiState.Error) {
					Form(Modifier.fillMaxWidth()) {
						FormRow(contentPadding = PaddingValues(14.dp)) {
							Text(
								text = stringResource(
									Res.string.info_aurral_search_failed,
									artistState.error.message
										?: artistState.error::class.simpleName
										?: "Unknown error"
								),
								color = MaterialTheme.colorScheme.error
							)
						}
					}
				}
			}
		}
	)
}
