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
	val artwork = mostPlayedShortcutArtwork(shortcut.coverArtId)
	ArtGridItem(
		modifier = modifier,
		onClick = dropUnlessResumed {
			platformContext.clickSound()
			onOpen()
		},
		coverArtId = artwork.coverArtId,
		imageUrl = artwork.imageUrl,
		title = shortcut.title,
		subtitle = mostPlayedShortcutSubtitle(shortcut),
		fallbackKind = shortcut.type.fallbackKind(),
		id = "${shortcut.type.name}-${shortcut.id}",
		tab = "most-played"
	)
}

data class MostPlayedShortcutArtwork(
	val coverArtId: String?,
	val imageUrl: String?
)

fun mostPlayedShortcutArtwork(coverArtId: String?): MostPlayedShortcutArtwork {
	val trimmed = coverArtId?.trim()?.takeIf { it.isNotEmpty() }
	return if (trimmed != null && trimmed.isAbsoluteHttpUrl()) {
		MostPlayedShortcutArtwork(coverArtId = null, imageUrl = trimmed)
	} else {
		MostPlayedShortcutArtwork(coverArtId = trimmed, imageUrl = null)
	}
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

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
