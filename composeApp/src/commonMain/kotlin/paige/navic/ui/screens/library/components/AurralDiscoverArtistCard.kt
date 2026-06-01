package paige.navic.ui.screens.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.ui.components.common.AurralArtistMonitorBadge
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.screens.aurral.aurralDiscoverArtistDetail
import paige.navic.ui.screens.aurral.aurralDiscoverArtistMonitorActionState

@Composable
fun AurralDiscoverArtistCard(
	artist: AurralDiscoverArtist,
	confirmationQueue: List<AurralConfirmationQueueItem> = emptyList(),
	modifier: Modifier = Modifier,
	onOpenArtist: (AurralDiscoverArtist) -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
	val requestHeaders = preferenceManager.aurralRequestHeadersMap()
	val imageRequestHeaders = if (baseUrl != null) {
		aurralRequestHeadersForUrl(baseUrl, artist.imageUrl, requestHeaders)
	} else {
		emptyMap()
	}
	val monitorState = aurralDiscoverArtistMonitorActionState(artist, confirmationQueue)

	Column(
		modifier = modifier.clickable(
			onClick = dropUnlessResumed {
				platformContext.clickSound()
				onOpenArtist(artist)
			}
		)
	) {
		Box(Modifier.fillMaxWidth()) {
			CoverArt(
				coverArtId = null,
				imageUrl = artist.imageUrl,
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = artist.name,
				fallbackKind = "Artist",
				modifier = Modifier.fillMaxWidth()
			)
			AurralArtistMonitorBadge(
				state = monitorState,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(8.dp)
			)
		}
		Text(
			text = artist.name,
			style = MaterialTheme.typography.titleSmallEmphasized,
			fontWeight = FontWeight.Medium,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
		)
		Text(
			text = aurralDiscoverArtistDetail(artist),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth()
		)
	}
}
