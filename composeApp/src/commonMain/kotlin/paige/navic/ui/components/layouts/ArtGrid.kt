package paige.navic.ui.components.layouts

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import paige.navic.ui.components.common.BookCover3DStyle
import paige.navic.ui.components.common.bookCover3D
import paige.navic.ui.components.common.bookCoverGeometry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.LocalSharedTransitionScope
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAcquisitionProgress
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainSong
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.ArtworkRenderSpec
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.generatedArtworkSpec
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
import paige.navic.ui.core.UiState
import paige.navic.util.ui.EmphasizedDecelerateEasing
import paige.navic.util.ui.shimmerLoading

@Composable
fun ArtGrid(
	modifier: Modifier = Modifier,
	state: LazyGridState = rememberLazyGridState(),
	contentPadding: PaddingValues,
	horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
	verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
	fixedColumns: Int? = null,
	content: LazyGridScope.() -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val artGridItemSize = preferenceManager.artGridItemSize
	BackToTopScrollHandler(state)
	LazyVerticalGrid(
		modifier = modifier.fillMaxSize(),
		state = state,
		columns = fixedColumns?.let { columns ->
			GridCells.Fixed(columns)
		} ?: if (platformContext.sizeClass.widthSizeClass <= WindowWidthSizeClass.Compact) {
			GridCells.Fixed(preferenceManager.gridSize.value)
		} else {
			GridCells.Adaptive(artGridItemSize.dp)
		},
		contentPadding = contentPadding + PaddingValues(
			start = 16.dp,
			top = 16.dp,
			end = 16.dp
		),
		horizontalArrangement = horizontalArrangement,
		verticalArrangement = verticalArrangement,
		content = content
	)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtGridItem(
	modifier: Modifier = Modifier,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)? = null,
	coverArtId: String?,
	imageUrl: String? = null,
	imageCacheKey: String? = null,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	imageDiagnosticLabel: String? = null,
	artworkSpec: ArtworkRenderSpec? = null,
	title: String,
	coverContentDescription: String = title,
	subtitle: String? = null,
	acquisitionProgress: AurralAcquisitionProgress? = null,
	ownershipStatus: AurralOwnershipStatus? = null,
	coverOverlay: (@Composable BoxScope.() -> Unit)? = null,
	coverAspectRatio: Float = 1f,
	coverContentScale: ContentScale = ContentScale.Crop,
	fallbackKind: String? = null,
	onServerCoverLoadFailed: (suspend () -> Unit)? = null,
	onImageSizeResolved: ((width: Int, height: Int) -> Unit)? = null,
	/** Draw the cover as a 3D book (front, page side and top); the slot keeps a fixed shape. */
	asBook: Boolean = false,
	/** Page count for the book's thickness; a medium thickness when unknown. */
	bookPages: Int? = null,
	id: String,
	// this parameter is a shitty workaround for shared element
	// transitions being performed when switching between tabs
	// this can just be an empty string if the tab is unknown
	tab: String
) {
	val interactionSource = remember { MutableInteractionSource() }
	with(LocalSharedTransitionScope.current) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.combinedClickable(
					interactionSource = interactionSource,
					indication = null,
					onClick = onClick,
					onLongClick = onLongClick
				)
				.then(modifier)
		) {
			val resolvedCoverArtId = artworkSpec?.coverArtId ?: coverArtId
			val resolvedImageUrl = artworkSpec?.imageUrl ?: imageUrl
			val resolvedImageCacheKey = artworkSpec?.imageCacheKey ?: imageCacheKey
			val resolvedImageRequestHeaders = artworkSpec?.imageRequestHeaders ?: imageRequestHeaders
			val resolvedContentDescription = artworkSpec?.contentDescription ?: coverContentDescription
			val sharedCoverModifier = Modifier.sharedElement(
				sharedContentState = this@with.rememberSharedContentState("${tab}-${id}-cover"),
				boundsTransform = BoundsTransform { _, _ ->
					tween(
						durationMillis = 500,
						easing = EmphasizedDecelerateEasing
					)
				},
				animatedVisibilityScope = LocalNavAnimatedContentScope.current
			)
			val overlays: @Composable BoxScope.() -> Unit = {
				acquisitionProgress?.let { progress ->
					AurralAcquisitionProgressBar(
						progress = progress,
						modifier = Modifier.align(Alignment.BottomCenter)
					)
				}
				ownershipStatus?.let { status ->
					AurralOwnershipStatusDot(
						status = status,
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(8.dp)
					)
				}
				coverOverlay?.invoke(this)
			}
			if (asBook) {
				BookCoverSlot(
					coverKey = resolvedImageUrl ?: resolvedCoverArtId,
					bookPages = bookPages,
					overlays = overlays
				) { coverShape, onSize, onBitmap ->
					CoverArt(
						coverArtId = resolvedCoverArtId,
						imageUrl = resolvedImageUrl,
						imageCacheKey = resolvedImageCacheKey,
						imageRequestHeaders = resolvedImageRequestHeaders,
						imageDiagnosticLabel = imageDiagnosticLabel,
						contentDescription = resolvedContentDescription,
						generatedArtwork = artworkSpec?.generatedArtwork,
						fallbackKind = fallbackKind,
						onServerCoverLoadFailed = onServerCoverLoadFailed,
						modifier = Modifier.fillMaxSize().then(sharedCoverModifier),
						square = false,
						shape = coverShape,
						contentScale = ContentScale.Crop,
						interactionSource = interactionSource,
						onImageSizeResolved = { width, height ->
							onSize(width, height)
							onImageSizeResolved?.invoke(width, height)
						},
						onImageBitmapResolved = onBitmap
					)
				}
			} else Box(Modifier.fillMaxWidth()) {
				CoverArt(
					coverArtId = resolvedCoverArtId,
					imageUrl = resolvedImageUrl,
					imageCacheKey = resolvedImageCacheKey,
					imageRequestHeaders = resolvedImageRequestHeaders,
					imageDiagnosticLabel = imageDiagnosticLabel,
					contentDescription = resolvedContentDescription,
					generatedArtwork = artworkSpec?.generatedArtwork,
					fallbackKind = fallbackKind,
					onServerCoverLoadFailed = onServerCoverLoadFailed,
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(coverAspectRatio)
						.then(sharedCoverModifier),
					square = false,
					contentScale = coverContentScale,
					interactionSource = interactionSource,
					onImageSizeResolved = onImageSizeResolved
				)
				overlays()
			}
			Text(
				text = title,
				style = MaterialTheme.typography.titleSmallEmphasized,
				modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			subtitle?.let {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.fillMaxWidth(),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}

/**
 * A fixed-shape slot that draws its cover as a 3D book standing on the slot's bottom edge.
 * The cover is laid out at its own aspect ratio and projected by [bookCover3D].
 */
@Composable
private fun BookCoverSlot(
	coverKey: String?,
	bookPages: Int?,
	overlays: @Composable BoxScope.() -> Unit,
	cover: @Composable (shape: Shape?, onSize: (Int, Int) -> Unit, onBitmap: (ImageBitmap) -> Unit) -> Unit
) {
	var coverAspect by remember(coverKey) { mutableFloatStateOf(2f / 3f) }
	var coverImage by remember(coverKey) { mutableStateOf<ImageBitmap?>(null) }
	BoxWithConstraints(
		Modifier
			.fillMaxWidth()
			.aspectRatio(BookCover3DStyle.SlotAspectRatio)
	) {
		val density = LocalDensity.current
		val slot = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
		val geometry = remember(slot, coverAspect, bookPages) {
			bookCoverGeometry(slot, coverAspect, bookPages)
		}
		val shape = remember(geometry) {
			if (geometry.thickness > 0f) {
				val radius = BookCover3DStyle.CornerRadiusPx * geometry.scale
				RoundedCornerShape(topStart = 0f, topEnd = radius, bottomEnd = radius, bottomStart = 0f)
			} else null
		}
		Box(
			Modifier
				.matchParentSize()
				.bookCover3D(geometry = { geometry }, image = { coverImage })
		) {
			Box(
				Modifier.size(
					width = with(density) { geometry.coverSize.width.toDp() },
					height = with(density) { geometry.coverSize.height.toDp() }
				)
			) {
				cover(
					shape,
					{ width, height ->
						if (width > 0 && height > 0) coverAspect = width.toFloat() / height.toFloat()
					},
					{ coverImage = it }
				)
				overlays()
			}
		}
	}
}

@Composable
fun PlaybackSongArtGridItem(
	modifier: Modifier = Modifier,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)? = null,
	song: DomainSong,
	subtitle: String? = song.artistName,
	acquisitionProgress: AurralAcquisitionProgress? = null,
	ownershipStatus: AurralOwnershipStatus? = null,
	coverOverlay: (@Composable BoxScope.() -> Unit)? = null,
	id: String = song.id,
	tab: String
) {
	val artwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = null,
		musicBrainzArtworkCacheKey = null,
		serverCoverLoadFailed = false
	)
	val artworkSpec = ArtworkRenderSpec(
		coverArtId = artwork.coverArtId,
		imageUrl = artwork.imageUrl,
		imageCacheKey = artwork.imageCacheKey,
		imageRequestHeaders = artwork.imageRequestHeaders,
		contentDescription = song.title,
		generatedArtwork = generatedArtworkSpec(
			kindLabel = "Track",
			primaryLabel = song.title,
			seed = song.id,
			variant = GeneratedArtworkVariant.GridCard
		)
	)
	ArtGridItem(
		modifier = modifier,
		onClick = onClick,
		onLongClick = onLongClick,
		coverArtId = null,
		artworkSpec = artworkSpec,
		title = song.title,
		subtitle = subtitle,
		acquisitionProgress = acquisitionProgress,
		ownershipStatus = ownershipStatus,
		coverOverlay = coverOverlay,
		id = id,
		tab = tab
	)
}

@Composable
fun ArtGridPlaceholder(
	modifier: Modifier = Modifier,
	coverAspectRatio: Float = 1f
) {
	Column(modifier = modifier) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(coverAspectRatio)
				// placeholders shouldn't use continuous corners
				// because it's less performant
				.clip(RoundedCornerShape(16.0.dp))
				.shimmerLoading(),
			contentAlignment = Alignment.Center
		) {
			CircularProgressIndicator(
				modifier = Modifier.size(22.dp),
				color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
				strokeWidth = 2.dp
			)
		}
		Box(
			modifier = Modifier
				.padding(top = 6.dp)
				.fillMaxWidth(0.8f)
				.height(16.dp)
				.clip(CircleShape)
				.shimmerLoading()
		)
		Box(
			modifier = Modifier
				.padding(top = 4.dp)
				.fillMaxWidth(0.6f)
				.height(14.dp)
				.clip(CircleShape)
				.shimmerLoading()
		)
	}
}

fun LazyGridScope.artGridPlaceholder(
	itemCount: Int = 8,
	coverAspectRatio: Float = 1f
) {
	items(itemCount) {
		ArtGridPlaceholder(
			modifier = Modifier.fillMaxWidth(),
			coverAspectRatio = coverAspectRatio
		)
	}
}

fun <T> LazyGridScope.artGridError(
	state: UiState.Error<T>
) {
	item(span = { GridItemSpan(maxLineSpan) }) {
		ErrorBox(
			modifier = Modifier.animateItem(fadeInSpec = null),
			error = state
		)
	}
}
