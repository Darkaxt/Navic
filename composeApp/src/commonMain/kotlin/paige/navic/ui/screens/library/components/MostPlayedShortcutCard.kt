package paige.navic.ui.screens.library.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import paige.navic.LocalPlatformContext
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.models.generatedMixPlaylistArtworkLabel
import paige.navic.domain.models.isGeneratedMixPlaylistName
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.ui.components.common.ArtworkRenderSpec
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.generatedArtworkSpec
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.screens.library.MOST_PLAYED_ARTWORK_TAG
import paige.navic.ui.screens.library.mostPlayedDiagnosticHeaderSummary
import paige.navic.ui.screens.library.mostPlayedDiagnosticText
import paige.navic.ui.screens.library.mostPlayedDiagnosticUrlSummary
import paige.navic.util.core.Logger

@Composable
fun MostPlayedShortcutCard(
	modifier: Modifier = Modifier,
	shortcut: DomainMostPlayedShortcut,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	onOpen: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val presentation = mostPlayedShortcutPresentation(shortcut)
	val artwork = presentation.artwork
	val diagnosticLabel = if (shortcut.type == PlaybackOriginType.Artist) {
		"most-played artist id=${mostPlayedDiagnosticText(shortcut.id)} " +
			"title=${mostPlayedDiagnosticText(shortcut.title)}"
	} else {
		null
	}
	LaunchedEffect(
		diagnosticLabel,
		artwork.coverArtId,
		artwork.imageUrl,
		imageRequestHeaders
	) {
		if (diagnosticLabel != null) {
			Logger.i(
				MOST_PLAYED_ARTWORK_TAG,
				"card handoff $diagnosticLabel " +
					"sourceCover=${mostPlayedDiagnosticUrlSummary(shortcut.coverArtId)} " +
					"coverArtId=${mostPlayedDiagnosticUrlSummary(artwork.coverArtId)} " +
					"imageUrl=${mostPlayedDiagnosticUrlSummary(artwork.imageUrl)} " +
					"headers=${mostPlayedDiagnosticHeaderSummary(imageRequestHeaders)}"
			)
		}
	}
	val generatedArtwork = generatedArtworkSpec(
		kindLabel = presentation.kindLabel,
		primaryLabel = presentation.primaryLabel,
		seed = presentation.seed,
		variant = GeneratedArtworkVariant.GridCard
	)
	val artworkSpec = ArtworkRenderSpec(
		coverArtId = artwork.coverArtId,
		imageUrl = artwork.imageUrl,
		imageRequestHeaders = if (artwork.imageUrl != null) imageRequestHeaders else emptyMap(),
		contentDescription = shortcut.title,
		generatedArtwork = generatedArtwork
	)
	ArtGridItem(
		modifier = modifier,
		onClick = dropUnlessResumed {
			platformContext.clickSound()
			onOpen()
		},
		coverArtId = null,
		artworkSpec = artworkSpec,
		imageDiagnosticLabel = diagnosticLabel,
		title = shortcut.title,
		subtitle = mostPlayedShortcutSubtitle(shortcut),
		id = "${shortcut.type.name}-${shortcut.id}",
		tab = "most-played"
	)
}

data class MostPlayedShortcutArtwork(
	val coverArtId: String?,
	val imageUrl: String?
)

data class MostPlayedShortcutPresentation(
	val artwork: MostPlayedShortcutArtwork,
	val kindLabel: String,
	val primaryLabel: String,
	val seed: String
)

fun mostPlayedShortcutPresentation(shortcut: DomainMostPlayedShortcut): MostPlayedShortcutPresentation {
	val isGeneratedMix = shortcut.type == PlaybackOriginType.Playlist &&
		isGeneratedMixPlaylistName(shortcut.title)
	return if (isGeneratedMix) {
		MostPlayedShortcutPresentation(
			artwork = mostPlayedShortcutArtwork(null),
			kindLabel = "Mix",
			primaryLabel = generatedMixPlaylistArtworkLabel(shortcut.title),
			seed = shortcut.id
		)
	} else {
		MostPlayedShortcutPresentation(
			artwork = mostPlayedShortcutArtwork(shortcut.coverArtId),
			kindLabel = shortcut.type.displayLabel(),
			primaryLabel = shortcut.title,
			seed = "${shortcut.type.name}-${shortcut.id}"
		)
	}
}

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

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
