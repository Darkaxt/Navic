package paige.navic.ui.screens.aurral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_create_aurral_flow
import navic.composeapp.generated.resources.info_aurral_flow_action_queued
import navic.composeapp.generated.resources.info_aurral_flow_action_updated
import navic.composeapp.generated.resources.option_aurral_flow_name
import navic.composeapp.generated.resources.option_aurral_flow_size
import navic.composeapp.generated.resources.title_aurral_based_on_library
import navic.composeapp.generated.resources.title_aurral_because_you_like
import navic.composeapp.generated.resources.title_aurral_create_flow
import navic.composeapp.generated.resources.title_aurral_discover
import navic.composeapp.generated.resources.title_aurral_explore_by_tag
import navic.composeapp.generated.resources.title_aurral_flows
import navic.composeapp.generated.resources.title_aurral_global_top
import navic.composeapp.generated.resources.title_aurral_recent_releases
import navic.composeapp.generated.resources.title_aurral_recently_added
import navic.composeapp.generated.resources.title_aurral_recommended_for_you
import navic.composeapp.generated.resources.title_aurral_requests
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralFlowActionResult
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Add
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.dialogs.FormDialog

@Composable
fun AurralCreateFlowDialog(
	defaultName: String,
	creating: Boolean,
	onDismissRequest: () -> Unit,
	onCreate: (String, Int) -> Unit
) {
	var name by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
	var sizeText by rememberSaveable { mutableStateOf("30") }
	val size = sizeText.trim().toIntOrNull()
	val valid = name.trim().isNotEmpty() && size != null && size > 0

	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Add, null) },
		title = { Text(stringResource(Res.string.title_aurral_create_flow)) },
		buttons = {
			FormButton(
				onClick = { onCreate(name, size ?: 30) },
				enabled = valid && !creating,
				color = MaterialTheme.colorScheme.primary
			) {
				if (creating) {
					CircularProgressIndicator(modifier = Modifier.size(20.dp))
				} else {
					Text(stringResource(Res.string.action_create_aurral_flow))
				}
			}
			FormButton(
				onClick = onDismissRequest,
				enabled = !creating
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				TextField(
					value = name,
					onValueChange = { name = it },
					label = { Text(stringResource(Res.string.option_aurral_flow_name)) },
					singleLine = true
				)
				TextField(
					value = sizeText,
					onValueChange = { sizeText = it.filter(Char::isDigit).take(3) },
					label = { Text(stringResource(Res.string.option_aurral_flow_size)) },
					singleLine = true
				)
			}
		}
	)
}

@Composable
internal fun aurralFlowActionMessage(result: AurralFlowActionResult): String =
	result.message
		?: if (result.tracksQueued > 0) {
			stringResource(Res.string.info_aurral_flow_action_queued, result.tracksQueued)
		} else {
			stringResource(Res.string.info_aurral_flow_action_updated)
		}

@Composable
internal fun AurralHubSummaryRow(card: AurralHubSummaryCard) {
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.weight(1f)) {
			Text(
				text = when (card.section) {
					AurralHubSection.Discover -> stringResource(Res.string.title_aurral_discover)
					AurralHubSection.Requests -> stringResource(Res.string.title_aurral_requests)
					AurralHubSection.Flows -> stringResource(Res.string.title_aurral_flows)
				},
				fontWeight = FontWeight.Medium
			)
			Text(
				text = card.detail,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
		Text(
			text = card.value,
			modifier = Modifier.padding(start = 16.dp),
			style = MaterialTheme.typography.bodyMedium,
			color = if (card.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis
		)
	}
}

@Composable
internal fun AurralHubSectionTitle(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
	)
}

internal fun AurralDiscoveryCollectionKind.aurralHubTitleResource(): StringResource =
	when (this) {
		AurralDiscoveryCollectionKind.RecentlyAddedArtists -> Res.string.title_aurral_recently_added
		AurralDiscoveryCollectionKind.RecentReleases -> Res.string.title_aurral_recent_releases
		AurralDiscoveryCollectionKind.RecommendedArtists -> Res.string.title_aurral_recommended_for_you
		AurralDiscoveryCollectionKind.BasedOnArtists -> Res.string.title_aurral_based_on_library
		AurralDiscoveryCollectionKind.GlobalTopArtists -> Res.string.title_aurral_global_top
		AurralDiscoveryCollectionKind.GenreArtists -> Res.string.title_aurral_because_you_like
		AurralDiscoveryCollectionKind.TopTags -> Res.string.title_aurral_explore_by_tag
	}

@Composable
internal fun AurralHubDiscoverTagRow(
	tag: String,
	onOpenTag: (String) -> Unit
) {
	FormRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
		onClick = { onOpenTag(tag) }
	) {
		Text(
			text = "#$tag",
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			color = MaterialTheme.colorScheme.primary
		)
	}
}

@Composable
internal fun AurralHubQueueRow(item: AurralAcquisitionQueueItem) {
	val progress = aurralAcquisitionProgress(item.status)
	FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
		Column(Modifier.fillMaxWidth()) {
			Row(Modifier.fillMaxWidth()) {
				Column(Modifier.weight(1f)) {
					Text(
						text = item.albumName,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = item.artistName,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				Text(
					text = item.status,
					modifier = Modifier.padding(start = 16.dp),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			val color = when {
				progress.failed -> MaterialTheme.colorScheme.error
				progress.completed -> MaterialTheme.colorScheme.primary
				else -> MaterialTheme.colorScheme.tertiary
			}
			if (progress.active) {
				LinearProgressIndicator(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp),
					color = color
				)
			} else {
				LinearProgressIndicator(
					progress = { 1f },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
						.height(3.dp),
					color = color
				)
			}
		}
	}
}
