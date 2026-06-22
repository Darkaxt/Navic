package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.effectiveAurralArtworkPriority
import paige.navic.domain.models.externalFallbackArtworkCacheKey
import paige.navic.domain.models.externalFallbackArtworkUrl
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.models.visiblePlaybackCoverArtId
import paige.navic.domain.models.visiblePlaybackImageUrl
import paige.navic.domain.repositories.MusicBrainzArtworkRepository

internal data class PlaybackSongArtworkState(
	val coverArtId: String?,
	val imageUrl: String?,
	val imageCacheKey: String?,
	val onServerCoverLoadFailed: (suspend () -> Unit)?
)

@Composable
internal fun rememberPlaybackSongArtworkState(
	song: DomainSong
): PlaybackSongArtworkState {
	val preferenceManager = koinInject<PreferenceManager>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsState()
	val serverCoverLoadFailedSongIds by musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.collectAsState()
	val artworkPriority = effectiveAurralArtworkPriority(
		aurralEnabled = preferenceManager.aurralEnabled,
		configuredPriority = preferenceManager.coverArtworkPriority
	)
	val externalArtworkEnabled = artworkPriority != ArtworkSourcePriority.NativeOnly
	val musicBrainzArtwork = musicBrainzArtworkBySongId[song.id].takeIf { externalArtworkEnabled }
	val serverCoverLoadFailed = song.id in serverCoverLoadFailedSongIds
	val externalArtworkUrl = externalFallbackArtworkUrl(
		serverCoverArtId = song.coverArtId,
		externalArtworkUrl = musicBrainzArtwork?.imageUrl,
		serverCoverLoadFailed = serverCoverLoadFailed
	).takeIf { externalArtworkEnabled }
	val externalArtworkCacheKey = externalFallbackArtworkCacheKey(
		serverCoverArtId = song.coverArtId,
		externalArtworkCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	).takeIf { externalArtworkEnabled }
	val failureHandler = if (externalArtworkEnabled) {
		suspend {
			musicBrainzArtworkRepository.reportServerCoverLoadFailed(song.id)
			musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)
			Unit
		}
	} else {
		null
	}

	val visibleCoverArtId = visiblePlaybackCoverArtId(
		serverCoverArtId = song.coverArtId,
		externalArtworkUrl = externalArtworkUrl,
		priority = artworkPriority
	)
	val visibleImageUrl = visiblePlaybackImageUrl(
		serverCoverArtId = song.coverArtId,
		externalArtworkUrl = externalArtworkUrl,
		priority = artworkPriority
	)
	return PlaybackSongArtworkState(
		coverArtId = visibleCoverArtId,
		imageUrl = visibleImageUrl,
		imageCacheKey = if (visibleImageUrl == null) null else externalArtworkCacheKey,
		onServerCoverLoadFailed = failureHandler
	)
}

@Composable
fun PlaybackSongCoverArt(
	song: DomainSong,
	modifier: Modifier = Modifier,
	contentDescription: String? = song.title,
	fallbackKind: String? = "Track",
	normalization: CoverArtNormalization = CoverArtNormalization.TrimWhitespace,
	shadowElevation: Dp = 0.dp,
	shape: Shape? = null,
	contentScale: ContentScale = ContentScale.Crop,
	square: Boolean = true
) {
	val artwork = rememberPlaybackSongArtworkState(song)
	CoverArt(
		coverArtId = artwork.coverArtId,
		imageUrl = artwork.imageUrl,
		imageCacheKey = artwork.imageCacheKey,
		contentDescription = contentDescription,
		fallbackKind = fallbackKind,
		onServerCoverLoadFailed = artwork.onServerCoverLoadFailed,
		normalization = normalization,
		modifier = modifier,
		shadowElevation = shadowElevation,
		shape = shape,
		contentScale = contentScale,
		square = square
	)
}
