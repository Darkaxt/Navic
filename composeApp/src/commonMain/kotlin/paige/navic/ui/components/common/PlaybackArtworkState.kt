package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.NowPlayingFallbackLabelStyle
import paige.navic.domain.models.PlaybackArtistPhotoCacheEntry
import paige.navic.domain.models.PlaybackArtworkSource
import paige.navic.domain.models.resolvedPlaybackArtistPhoto
import paige.navic.domain.models.resolvedPlaybackArtwork
import paige.navic.domain.repositories.aurralAbsoluteImageUrl
import paige.navic.domain.repositories.aurralRequestHeadersForUrl

@Immutable
data class PlaybackArtworkUiState(
	val coverArtId: String?,
	val imageUrl: String?,
	val imageCacheKey: String?,
	val imageRequestHeaders: Map<String, String>,
	val source: PlaybackArtworkSource
) {
	val hasArtwork: Boolean
		get() = !coverArtId.isNullOrBlank() || !imageUrl.isNullOrBlank()
}

@Composable
fun rememberPlaybackArtworkUiState(
	song: DomainSong?,
	musicBrainzArtworkUrl: String?,
	musicBrainzArtworkCacheKey: String?,
	serverCoverLoadFailed: Boolean
): PlaybackArtworkUiState {
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralBaseUrl = preferenceManager.aurralBaseUrl
	val artistPhotoEntries = rememberPlaybackArtistPhotoCacheEntries(aurralBaseUrl)
	val artistPhoto = remember(
		song?.artistId,
		song?.artistName,
		artistPhotoEntries
	) {
		resolvedPlaybackArtistPhoto(
			artistId = song?.artistId,
			artistName = song?.artistName,
			entries = artistPhotoEntries
		)
	}
	val resolution = remember(
		song?.coverArtId,
		artistPhoto?.imageUrl,
		artistPhoto?.cacheKey,
		musicBrainzArtworkUrl,
		musicBrainzArtworkCacheKey,
		preferenceManager.coverArtworkPriority,
		preferenceManager.aurralEnabled,
		preferenceManager.musicBrainzArtworkFallbackEnabled,
		serverCoverLoadFailed
	) {
		resolvedPlaybackArtwork(
			serverCoverArtId = song?.coverArtId,
			aurralArtistImageUrl = artistPhoto?.imageUrl,
			aurralArtistCacheKey = artistPhoto?.cacheKey,
			musicBrainzArtworkUrl = musicBrainzArtworkUrl,
			musicBrainzArtworkCacheKey = musicBrainzArtworkCacheKey,
			artworkSourcePriority = preferenceManager.coverArtworkPriority,
			aurralArtworkEnabled = preferenceManager.aurralEnabled,
			musicBrainzArtworkEnabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
			serverCoverLoadFailed = serverCoverLoadFailed
		)
	}
	val requestHeaders = remember(
		resolution.source,
		resolution.imageUrl,
		aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (resolution.source == PlaybackArtworkSource.AurralArtist) {
			aurralRequestHeadersForUrl(
				baseUrl = aurralBaseUrl,
				imageUrl = resolution.imageUrl,
				requestHeaders = preferenceManager.aurralRequestHeadersMap()
			)
		} else {
			emptyMap()
		}
	}
	return PlaybackArtworkUiState(
		coverArtId = resolution.coverArtId,
		imageUrl = resolution.imageUrl,
		imageCacheKey = resolution.imageCacheKey,
		imageRequestHeaders = requestHeaders,
		source = resolution.source
	)
}

@Composable
fun rememberArtistArtworkUiState(
	artist: DomainArtist?
): PlaybackArtworkUiState =
	rememberAurralFirstArtistArtworkUiState(
		artistId = artist?.id,
		artistMusicBrainzId = artist?.musicBrainzId,
		artistName = artist?.name,
		serverCoverArtId = artist?.coverArtId,
		externalArtistImageUrl = artist?.artistImageUrl,
		externalArtistCacheKey = artist?.artistImageUrl
	)

@Composable
fun rememberAurralFirstArtistArtworkUiState(
	artistId: String?,
	artistMusicBrainzId: String?,
	artistName: String?,
	serverCoverArtId: String?,
	externalArtistImageUrl: String?,
	externalArtistCacheKey: String?
): PlaybackArtworkUiState {
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralBaseUrl = preferenceManager.aurralBaseUrl
	val artistPhotoEntries = rememberPlaybackArtistPhotoCacheEntries(aurralBaseUrl)
	val artistPhoto = remember(
		artistId,
		artistMusicBrainzId,
		artistName,
		artistPhotoEntries
	) {
		resolvedPlaybackArtistPhoto(
			artistId = artistMusicBrainzId ?: artistId,
			artistName = artistName,
			entries = artistPhotoEntries
		) ?: resolvedPlaybackArtistPhoto(
			artistId = artistId,
			artistName = artistName,
			entries = artistPhotoEntries
		)
	}
	val resolvedExternalArtistImageUrl = artistPhoto?.imageUrl ?: externalArtistImageUrl
	val resolvedExternalArtistCacheKey = artistPhoto?.cacheKey ?: externalArtistCacheKey
	val resolution = remember(
		serverCoverArtId,
		resolvedExternalArtistImageUrl,
		resolvedExternalArtistCacheKey,
		preferenceManager.artistArtworkPriority,
		preferenceManager.aurralEnabled
	) {
		resolvedPlaybackArtwork(
			serverCoverArtId = serverCoverArtId,
			aurralArtistImageUrl = resolvedExternalArtistImageUrl,
			aurralArtistCacheKey = resolvedExternalArtistCacheKey,
			musicBrainzArtworkUrl = null,
			musicBrainzArtworkCacheKey = null,
			artworkSourcePriority = preferenceManager.artistArtworkPriority,
			aurralArtworkEnabled = preferenceManager.aurralEnabled,
			musicBrainzArtworkEnabled = false
		)
	}
	val requestHeaders = remember(
		resolution.source,
		resolution.imageUrl,
		aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (resolution.source == PlaybackArtworkSource.AurralArtist) {
			aurralRequestHeadersForUrl(
				baseUrl = aurralBaseUrl,
				imageUrl = resolution.imageUrl,
				requestHeaders = preferenceManager.aurralRequestHeadersMap()
			)
		} else {
			emptyMap()
		}
	}
	return PlaybackArtworkUiState(
		coverArtId = resolution.coverArtId,
		imageUrl = resolution.imageUrl,
		imageCacheKey = resolution.imageCacheKey,
		imageRequestHeaders = requestHeaders,
		source = resolution.source
	)
}

@Composable
private fun rememberPlaybackArtistPhotoCacheEntries(
	aurralBaseUrl: String
): List<PlaybackArtistPhotoCacheEntry> {
	val artistPhotoCacheDao = koinInject<ArtistPhotoCacheDao>()
	val cachedArtistPhotos by artistPhotoCacheDao.observeArtistPhotoCache()
		.collectAsStateWithLifecycle(emptyList())
	val artistPhotoEntries by produceState<List<PlaybackArtistPhotoCacheEntry>>(
		initialValue = emptyList(),
		cachedArtistPhotos,
		aurralBaseUrl
	) {
		value = withContext(Dispatchers.Default) {
			cachedArtistPhotos.map { entry ->
				entry.toPlaybackArtistPhotoCacheEntry(aurralBaseUrl)
			}
		}
	}
	return artistPhotoEntries
}

@Composable
fun PlaybackSongCoverArt(
	song: DomainSong,
	modifier: Modifier = Modifier,
	contentDescription: String? = song.title,
	fallbackKind: String? = "Track",
	fallbackLabelStyle: NowPlayingFallbackLabelStyle = NowPlayingFallbackLabelStyle.Center,
	imageDiagnosticLabel: String? = null,
	shape: Shape? = null,
	shadowElevation: Dp = 0.dp,
	normalization: CoverArtNormalization = CoverArtNormalization.None,
	contentScale: ContentScale = ContentScale.Crop,
	crossfadeMs: Int = 500,
	square: Boolean = true,
	colorFilter: ColorFilter? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null
) {
	val playbackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = null,
		musicBrainzArtworkCacheKey = null,
		serverCoverLoadFailed = false
	)
	CoverArt(
		modifier = modifier,
		coverArtId = playbackArtwork.coverArtId,
		imageUrl = playbackArtwork.imageUrl,
		imageCacheKey = playbackArtwork.imageCacheKey,
		imageRequestHeaders = playbackArtwork.imageRequestHeaders,
		imageDiagnosticLabel = imageDiagnosticLabel,
		contentDescription = contentDescription,
		fallbackKind = fallbackKind,
		fallbackLabelStyle = fallbackLabelStyle,
		onClick = onClick,
		onLongClick = onLongClick,
		square = square,
		crossfadeMs = crossfadeMs,
		shadowElevation = shadowElevation,
		shape = shape,
		colorFilter = colorFilter,
		normalization = normalization,
		contentScale = contentScale
	)
}

@Composable
fun AurralFirstArtistCoverArt(
	artist: DomainArtist,
	modifier: Modifier = Modifier,
	contentDescription: String? = artist.name,
	fallbackKind: String? = "Artist",
	fallbackLabelStyle: NowPlayingFallbackLabelStyle = NowPlayingFallbackLabelStyle.Center,
	imageDiagnosticLabel: String? = null,
	shape: Shape? = null,
	shadowElevation: Dp = 0.dp,
	normalization: CoverArtNormalization = CoverArtNormalization.None,
	contentScale: ContentScale = ContentScale.Crop,
	crossfadeMs: Int = 500,
	square: Boolean = true,
	colorFilter: ColorFilter? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null
) {
	val artistArtwork = rememberArtistArtworkUiState(artist)
	CoverArt(
		modifier = modifier,
		coverArtId = artistArtwork.coverArtId,
		imageUrl = artistArtwork.imageUrl,
		imageCacheKey = artistArtwork.imageCacheKey,
		imageRequestHeaders = artistArtwork.imageRequestHeaders,
		imageDiagnosticLabel = imageDiagnosticLabel,
		contentDescription = contentDescription,
		fallbackKind = fallbackKind,
		fallbackLabelStyle = fallbackLabelStyle,
		onClick = onClick,
		onLongClick = onLongClick,
		square = square,
		crossfadeMs = crossfadeMs,
		shadowElevation = shadowElevation,
		shape = shape,
		colorFilter = colorFilter,
		normalization = normalization,
		contentScale = contentScale
	)
}

private fun ArtistPhotoCacheEntity.toPlaybackArtistPhotoCacheEntry(
	aurralBaseUrl: String
): PlaybackArtistPhotoCacheEntry =
	PlaybackArtistPhotoCacheEntry(
		cacheKey = cacheKey,
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = name,
		normalizedName = normalizedName,
		imageUrl = aurralAbsoluteImageUrl(aurralBaseUrl, imageUrl) ?: imageUrl,
		source = source,
		updatedAtMillis = updatedAtMillis
	)
