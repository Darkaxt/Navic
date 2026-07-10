package paige.navic.ui.screens.nowPlaying.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.NowPlayingDiscContentScale
import paige.navic.domain.models.NowPlayingDiscFitMode
import paige.navic.domain.models.NowPlayingArtworkRequestIdentity
import paige.navic.domain.models.NowPlayingVinylGrooveEndRadiusFraction
import paige.navic.domain.models.NowPlayingVinylGrooveStartRadiusFraction
import paige.navic.domain.models.NowPlayingVinylLabelRadiusFraction
import paige.navic.domain.models.NowPlayingVinylSpindleRadiusFraction
import paige.navic.domain.models.nowPlayingDiscContentScale
import paige.navic.domain.models.nowPlayingDiscFitMode
import paige.navic.domain.models.nowPlayingFallbackLabelStyle
import paige.navic.domain.models.nowPlayingArtworkPaddingDp
import paige.navic.domain.models.nowPlayingArtworkRotationDegreesForElapsedMillis
import paige.navic.domain.models.nowPlayingArtworkShapeForPlayback
import paige.navic.domain.models.nowPlayingVinylOverlayRotationDegrees
import paige.navic.domain.models.isNowPlayingVinylArtworkReady
import paige.navic.domain.models.shouldRotateNowPlayingArtwork
import paige.navic.domain.models.shouldShowNowPlayingVinylOverlay
import paige.navic.domain.models.shouldUseNowPlayingVinylPresentation
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.icons.Icons
import paige.navic.icons.filled.Note
import paige.navic.icons.outlined.Radio
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.CoverArtEdgeCompression
import paige.navic.ui.components.common.CoverArtNormalization
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.generatedArtworkSpec
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
import kotlin.math.min

@Composable
fun NowPlayingArtwork(
	modifier: Modifier = Modifier,
	isLandscape: Boolean,
	isWideLandscape: Boolean = false,
	song: DomainSong,
	onClick: (() -> Unit)? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val playerState by player.uiState.collectAsState()
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsState()
	val serverCoverLoadFailedSongIds by musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.collectAsState()
	val musicBrainzArtwork = musicBrainzArtworkBySongId[song.id]
	val serverCoverLoadFailed = song.id in serverCoverLoadFailedSongIds
	val playbackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = musicBrainzArtwork?.imageUrl,
		musicBrainzArtworkCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
		serverCoverLoadFailed = serverCoverLoadFailed
	)
	val generatedArtwork = generatedArtworkSpec(
		kindLabel = "Track",
		primaryLabel = song.title,
		seed = song.id,
		variant = GeneratedArtworkVariant.NowPlayingDisc
	)
	val hasArtwork = playbackArtwork.hasArtwork
	val hasGeneratedArtwork = true
	val artworkRequestIdentity = remember(
		song.id,
		playbackArtwork.coverArtId,
		playbackArtwork.imageUrl,
		playbackArtwork.imageCacheKey
	) {
		NowPlayingArtworkRequestIdentity(
			songId = song.id,
			coverArtId = playbackArtwork.coverArtId,
			imageUrl = playbackArtwork.imageUrl,
			imageCacheKey = playbackArtwork.imageCacheKey
		)
	}
	var resolvedVinylArtworkRequest by remember {
		mutableStateOf<NowPlayingArtworkRequestIdentity?>(null)
	}
	val isVinylArtworkReady = isNowPlayingVinylArtworkReady(
		hasCoverArt = hasArtwork,
		hasGeneratedArtwork = hasGeneratedArtwork,
		requestedArtwork = artworkRequestIdentity,
		resolvedArtwork = resolvedVinylArtworkRequest
	)
	val vinylHasCoverArt = hasArtwork && isVinylArtworkReady
	val vinylHasGeneratedArtwork = !hasArtwork && hasGeneratedArtwork

	val isRadio = song.id.startsWith("radio_")
	val isActiveArtwork = playerState.currentSong?.id == song.id
	val isRotatingArtwork = shouldRotateNowPlayingArtwork(
		enabled = preferenceManager.nowPlayingRotatingArtwork,
		isPaused = playerState.isPaused,
		isActiveArtwork = isActiveArtwork,
		hasCoverArt = vinylHasCoverArt,
		hasGeneratedArtwork = vinylHasGeneratedArtwork
	)
	val isVinylPresentation = shouldUseNowPlayingVinylPresentation(
		isWideLandscape = isWideLandscape,
		isRotatingArtwork = isRotatingArtwork,
		hasCoverArt = vinylHasCoverArt,
		hasGeneratedArtwork = vinylHasGeneratedArtwork
	)
	val rotationDegrees = rememberNowPlayingArtworkRotationDegrees(
		enabled = isRotatingArtwork
	)
	val artworkShape = nowPlayingArtworkShapeForPlayback(
		configuredShape = preferenceManager.coverArtShape,
		isRotating = isVinylPresentation
	)
	var resolvedImageSize by remember(
		song.id,
		playbackArtwork.coverArtId,
		playbackArtwork.imageUrl,
		playbackArtwork.imageCacheKey
	) {
		mutableStateOf<Pair<Int, Int>?>(null)
	}
	val discFitMode = nowPlayingDiscFitMode(
		isWideLandscape = isWideLandscape,
		isVinylArtwork = isVinylPresentation,
		hasRealArtwork = vinylHasCoverArt
	)
	val coverArtContentScale = when (
		nowPlayingDiscContentScale(
			fitMode = discFitMode,
			imageWidth = resolvedImageSize?.first,
			imageHeight = resolvedImageSize?.second
		)
	) {
		NowPlayingDiscContentScale.Crop -> ContentScale.Crop
		NowPlayingDiscContentScale.Fit -> ContentScale.Fit
	}
	val edgeCompression = if (discFitMode == NowPlayingDiscFitMode.SoftEdgeCompress) {
		CoverArtEdgeCompression.Soft
	} else {
		CoverArtEdgeCompression.None
	}

	val padding by animateDpAsState(
		targetValue = nowPlayingArtworkPaddingDp(
			size = preferenceManager.nowPlayingArtworkSize,
			isPausedOrInactive = playerState.isPaused || !isActiveArtwork,
			shrinkWhenPausedOrInactive = preferenceManager.shrinkNowPlayingArtworkOnPause
		).dp
	)
	val artworkModifier = Modifier
		.aspectRatio(1f)
		.then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
		.padding(padding)
	val discRotationDegrees = nowPlayingVinylOverlayRotationDegrees(
		isRotatingArtwork = isRotatingArtwork,
		artworkRotationDegrees = rotationDegrees
	)
	val discModifier = artworkModifier.then(
		if (discRotationDegrees == 0f) Modifier else Modifier.rotate(discRotationDegrees)
	)
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier.then(
			if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
		)
	) {
		Box(modifier = discModifier) {
			CoverArt(
				coverArtId = playbackArtwork.coverArtId,
				imageUrl = playbackArtwork.imageUrl,
				imageCacheKey = playbackArtwork.imageCacheKey,
				imageRequestHeaders = playbackArtwork.imageRequestHeaders,
				contentDescription = song.title,
				generatedArtwork = generatedArtwork,
				fallbackLabelStyle = nowPlayingFallbackLabelStyle(isRotatingArtwork),
				onServerCoverLoadFailed = {
					musicBrainzArtworkRepository.reportServerCoverLoadFailed(song.id)
					musicBrainzArtworkRepository.prefetchArtworkForPlayingSong(song)
				},
				useCachedLoadingPlaceholder = true,
				normalization = CoverArtNormalization.TrimWhitespace,
				contentScale = coverArtContentScale,
				edgeCompression = edgeCompression,
				onImageSizeResolved = { width, height ->
					resolvedImageSize = width to height
					resolvedVinylArtworkRequest = artworkRequestIdentity
				},
				modifier = Modifier.fillMaxSize(),
				shadowElevation = 8.dp,
				shape = artworkShape.shape
			)
			if (
				shouldShowNowPlayingVinylOverlay(
					isVinylPresentation = isVinylPresentation,
					hasCoverArt = vinylHasCoverArt,
					hasGeneratedArtwork = vinylHasGeneratedArtwork
				)
			) {
				VinylRecordOverlay(
					modifier = Modifier.fillMaxSize()
				)
			}
		}
		if (!hasArtwork && !hasGeneratedArtwork) {
			Icon(
				imageVector = if (isRadio) Icons.Outlined.Radio else Icons.Filled.Note,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
				modifier = Modifier.size(96.dp)
			)
		}
	}
}

@Composable
private fun VinylRecordOverlay(modifier: Modifier = Modifier) {
	val colorScheme = MaterialTheme.colorScheme
	Canvas(modifier = modifier) {
		val radius = min(size.width, size.height) / 2f
		if (radius <= 0f) return@Canvas

		val center = Offset(size.width / 2f, size.height / 2f)
		val startRadius = radius * NowPlayingVinylGrooveStartRadiusFraction
		val endRadius = radius * NowPlayingVinylGrooveEndRadiusFraction
		val grooveCount = 48
		val baseStrokeWidth = (radius * 0.0028f).coerceAtLeast(0.65f)
		val accentStrokeWidth = (radius * 0.004f).coerceAtLeast(0.9f)

		drawCircle(
			color = colorScheme.scrim.copy(alpha = 0.16f),
			radius = radius * 0.985f,
			center = center
		)
		drawCircle(
			color = colorScheme.onSurface.copy(alpha = 0.16f),
			radius = radius * 0.99f,
			center = center,
			style = Stroke(width = radius * 0.018f)
		)

		repeat(grooveCount) { index ->
			val progress = index / (grooveCount - 1).toFloat()
			val grooveRadius = startRadius + (endRadius - startRadius) * progress
			val alpha = if (index % 7 == 0) 0.22f else 0.13f
			drawCircle(
				color = colorScheme.onSurface.copy(alpha = alpha),
				radius = grooveRadius,
				center = center,
				style = Stroke(
					width = if (index % 7 == 0) accentStrokeWidth else baseStrokeWidth,
					cap = StrokeCap.Round
				)
			)
		}

		drawCircle(
			color = colorScheme.surface.copy(alpha = 0.22f),
			radius = radius * NowPlayingVinylLabelRadiusFraction,
			center = center
		)
		drawCircle(
			color = colorScheme.onSurface.copy(alpha = 0.32f),
			radius = radius * NowPlayingVinylLabelRadiusFraction,
			center = center,
			style = Stroke(width = radius * 0.008f)
		)
		drawCircle(
			color = colorScheme.surface.copy(alpha = 0.92f),
			radius = radius * NowPlayingVinylSpindleRadiusFraction,
			center = center
		)
		drawCircle(
			color = colorScheme.onSurface.copy(alpha = 0.28f),
			radius = radius * NowPlayingVinylSpindleRadiusFraction,
			center = center,
			style = Stroke(width = radius * 0.004f)
		)
	}
}

@Composable
private fun rememberNowPlayingArtworkRotationDegrees(enabled: Boolean): Float {
	var rotationDegrees by remember { mutableFloatStateOf(0f) }
	LaunchedEffect(enabled) {
		if (!enabled) {
			rotationDegrees = 0f
			return@LaunchedEffect
		}

		val startFrameNanos = withFrameNanos { it }
		while (true) {
			withFrameNanos { frameNanos ->
				val elapsedMillis = ((frameNanos - startFrameNanos) / 1_000_000L).coerceAtLeast(0L)
				rotationDegrees = nowPlayingArtworkRotationDegreesForElapsedMillis(elapsedMillis)
			}
		}
	}
	return if (enabled) rotationDegrees else 0f
}
