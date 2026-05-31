package paige.navic.ui.screens.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import paige.navic.LocalPlatformContext
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.ui.components.layouts.ArtGridItem

@Composable
fun MostPlayedShortcutCard(
	modifier: Modifier = Modifier,
	shortcut: DomainMostPlayedShortcut,
	onOpen: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	ArtGridItem(
		modifier = modifier,
		onClick = dropUnlessResumed {
			platformContext.clickSound()
			onOpen()
		},
		coverArtId = shortcut.coverArtId,
		title = shortcut.title,
		subtitle = mostPlayedShortcutSubtitle(shortcut),
		fallbackKind = shortcut.type.fallbackKind(),
		id = "${shortcut.type.name}-${shortcut.id}",
		tab = "most-played"
	)
}

private fun mostPlayedShortcutSubtitle(shortcut: DomainMostPlayedShortcut): String {
	val duration = queueTotalDurationLabel(shortcut.totalPlayedMillis / 1_000L)
	return listOfNotNull(
		shortcut.type.displayLabel(),
		shortcut.subtitle?.takeIf { it.isNotBlank() },
		duration
	).joinToString(" - ")
}

private fun PlaybackOriginType.displayLabel(): String =
	when (this) {
		PlaybackOriginType.Artist -> "Artist"
		PlaybackOriginType.Genre -> "Genre"
		PlaybackOriginType.Album -> "Album"
		PlaybackOriginType.Playlist -> "Playlist"
		PlaybackOriginType.Station -> "Station"
	}

private fun PlaybackOriginType.fallbackKind(): String = displayLabel()
