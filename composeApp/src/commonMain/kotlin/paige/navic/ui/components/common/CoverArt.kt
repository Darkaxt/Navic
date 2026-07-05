package paige.navic.ui.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.NowPlayingFallbackLabelStyle
import paige.navic.util.core.Logger
import paige.navic.util.core.toNetworkHeaders
import paige.navic.ui.theme.defaultFont
import paige.navic.util.ui.toImageBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

internal data class CoverArtFallbackContent(
	val label: String?,
	val initials: String?,
	val seedSource: String?
)

internal fun coverArtFallbackContent(
	contentDescription: String?,
	coverArtId: String?,
	imageUrl: String?
): CoverArtFallbackContent {
	val label = contentDescription?.trim()?.takeIf { it.isNotEmpty() }
	val seedSource = label
		?: coverArtId?.trim()?.takeIf { it.isNotEmpty() }
		?: imageUrl?.trim()?.takeIf { it.isNotEmpty() }
	return CoverArtFallbackContent(
		label = label,
		initials = label?.coverArtFallbackInitials(),
		seedSource = seedSource
	)
}

private fun String.coverArtFallbackInitials(): String {
	val words = split(Regex("\\s+"))
		.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
		.take(3)
		.joinToString(separator = "") { it.uppercase() }
	return words.takeIf { it.isNotEmpty() }
		?: trim().take(1).uppercase().takeIf { it.isNotEmpty() }
		?: ""
}

internal fun coverArtFallbackArcText(label: String?, maxChars: Int = 28): String? {
	val cleaned = label?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
	if (maxChars <= 3) return cleaned.take(maxChars).uppercase().takeIf { it.isNotEmpty() }
	return if (cleaned.length <= maxChars) {
		cleaned.uppercase()
	} else {
		"${cleaned.take(maxChars - 3).trimEnd()}...".uppercase()
	}
}

internal fun coverArtFallbackKindLabel(fallbackKind: String?): String? =
	fallbackKind
		?.trim()
		?.replace(Regex("\\s+"), " ")
		?.takeIf { it.isNotEmpty() }
		?.uppercase()

enum class CoverArtEdgeCompression {
	None,
	Soft
}

internal data class SoftEdgeCompressionBand(
	val srcOffset: IntOffset,
	val srcSize: IntSize,
	val dstOffset: IntOffset,
	val dstSize: IntSize
)

private val CoverArtFallbackSeedColors = listOf(
	Color(0xFF845336),
	Color(0xFF57553C),
	Color(0xFFA17E3E),
	Color(0xFF43454F),
	Color(0xFF604848),
	Color(0xFF5C6652),
	Color(0xFFA18B62),
	Color(0xFF8C4F4A),
	Color(0xFF898471),
	Color(0xFFC8B491),
	Color(0xFF65788F),
	Color(0xFF755E4A),
	Color(0xFF718062),
	Color(0xFFBC9D66)
)

private data class CoverArtFallbackPalette(
	val bgStart: Color,
	val bgEnd: Color,
	val blob1: Color,
	val blob2: Color,
	val blob3: Color,
	val blob4: Color,
	val chip: Color,
	val chipStroke: Color,
	val line1: Color,
	val line2: Color
)

private fun coverArtFallbackPalette(seedSource: String?): CoverArtFallbackPalette {
	val hash = stablePositiveHash(seedSource)
	val base1 = CoverArtFallbackSeedColors[hash % CoverArtFallbackSeedColors.size]
	val base2 = CoverArtFallbackSeedColors[(hash + 3) % CoverArtFallbackSeedColors.size]
	val base3 = CoverArtFallbackSeedColors[(hash + 7) % CoverArtFallbackSeedColors.size]
	val base4 = CoverArtFallbackSeedColors[(hash + 11) % CoverArtFallbackSeedColors.size]
	return CoverArtFallbackPalette(
		bgStart = base1.mixWith(Color(0xFF11131A), .58f),
		bgEnd = base2.mixWith(Color(0xFF090A0F), .70f),
		blob1 = base1.copy(alpha = .45f),
		blob2 = base2.copy(alpha = .35f),
		blob3 = base3.copy(alpha = .40f),
		blob4 = base4.copy(alpha = .30f),
		chip = base1.mixWith(Color.White, .22f).copy(alpha = .92f),
		chipStroke = Color.White.copy(alpha = .16f),
		line1 = base3.copy(alpha = .15f),
		line2 = base4.copy(alpha = .15f)
	)
}

private fun stablePositiveHash(value: String?): Int {
	var hash = 0
	value.orEmpty().forEach { char ->
		hash = char.code + ((hash shl 5) - hash)
	}
	return if (hash == Int.MIN_VALUE) 0 else abs(hash)
}

private fun Color.mixWith(other: Color, weight: Float): Color {
	val ratio = weight.coerceIn(0f, 1f)
	return Color(
		red = red + (other.red - red) * ratio,
		green = green + (other.green - green) * ratio,
		blue = blue + (other.blue - blue) * ratio,
		alpha = alpha + (other.alpha - alpha) * ratio
	)
}

@Composable
fun CoverArt(
	modifier: Modifier = Modifier,
	coverArtId: String?,
	imageUrl: String? = null,
	imageCacheKey: String? = null,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	imageDiagnosticLabel: String? = null,
	contentDescription: String? = null,
	generatedArtwork: GeneratedArtworkSpec? = null,
	fallbackKind: String? = null,
	fallbackLabelStyle: NowPlayingFallbackLabelStyle = NowPlayingFallbackLabelStyle.Center,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	onServerCoverLoadFailed: (suspend () -> Unit)? = null,
	square: Boolean = true,
	crossfadeMs: Int = 500,
	shadowElevation: Dp = 0.dp,
	interactionSource: MutableInteractionSource? = null,
	shape: Shape? = null,
	colorFilter: ColorFilter? = null,
	artworkResolving: Boolean = false,
	normalization: CoverArtNormalization = CoverArtNormalization.None,
	contentScale: ContentScale = ContentScale.Crop,
	edgeCompression: CoverArtEdgeCompression = CoverArtEdgeCompression.None,
	onImageSizeResolved: ((width: Int, height: Int) -> Unit)? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val shape = shape ?: preferenceManager.coverArtShape.shape
	val coilPlatformContext = LocalCoilPlatformContext.current
	val serverRequestHeaders = preferenceManager.serverRequestHeadersMap()
	val sessionManager = koinInject<SessionManager>()
	// Pure renderer: the caller is responsible for source selection (see resolveStaticArtwork /
	// resolvedPlaybackArtwork). CoverArt no longer applies any Aurral/Navidrome policy itself.
	val resolvedImageUrl = imageUrl
	val visibleCoverArtId = coverArtId
	val usesServerCoverArt = resolvedImageUrl == null && visibleCoverArtId != null
	val resolvedRequestHeaders = if (usesServerCoverArt) serverRequestHeaders else imageRequestHeaders
	val resolvedImageCacheKey = normalizedCoverArtCacheKey(
		cacheKey = imageCacheKey ?: resolvedImageUrl ?: visibleCoverArtId,
		normalization = normalization
	)
	LaunchedEffect(
		imageDiagnosticLabel,
		coverArtId,
		resolvedImageUrl,
		resolvedImageCacheKey,
		resolvedRequestHeaders
	) {
		if (imageDiagnosticLabel != null) {
			Logger.i(
				"CoverArt",
				"request [$imageDiagnosticLabel] " +
					"usesServer=$usesServerCoverArt " +
					"coverArtId=${coverArtDiagnosticValue(coverArtId)} " +
					"imageUrl=${coverArtDiagnosticValue(resolvedImageUrl)} " +
					"cacheKey=${coverArtDiagnosticValue(resolvedImageCacheKey)} " +
					"headerKeys=${coverArtDiagnosticHeaderKeys(resolvedRequestHeaders)}"
			)
		}
	}
	val model = remember(visibleCoverArtId, resolvedImageUrl, resolvedImageCacheKey, resolvedRequestHeaders) {
		ImageRequest.Builder(coilPlatformContext)
			.data(resolvedImageUrl ?: visibleCoverArtId?.let { sessionManager.getCoverArtUrl(it) })
			.memoryCacheKey(resolvedImageCacheKey)
			.diskCacheKey(resolvedImageCacheKey)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.crossfade(crossfadeMs)
			.applyCoverArtNormalization(normalization)
			.apply {
				if (resolvedRequestHeaders.isNotEmpty()) {
					httpHeaders(resolvedRequestHeaders.toNetworkHeaders())
				}
			}
			.build()
	}

	val commonModifier = modifier
		.then(if (square) Modifier.aspectRatio(1f) else Modifier)
		.shadow(shadowElevation, shape)
		.clip(shape)
		.background(MaterialTheme.colorScheme.surfaceContainer)
		.then(if (onClick != null)
			Modifier.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
				interactionSource = interactionSource
			)
		else Modifier)
		.then(if (interactionSource != null)
			Modifier.indication(interactionSource, ripple())
		else Modifier)

	val fallbackContent = remember(contentDescription, visibleCoverArtId, resolvedImageUrl) {
		coverArtFallbackContent(
			contentDescription = contentDescription,
			coverArtId = visibleCoverArtId,
			imageUrl = resolvedImageUrl
		)
	}
	val resolvedGeneratedArtwork = remember(generatedArtwork, fallbackKind, fallbackLabelStyle, fallbackContent) {
		generatedArtwork ?: GeneratedArtworkSpec(
			kindLabel = fallbackKind,
			primaryLabel = fallbackContent.label,
			seed = fallbackContent.seedSource,
			variant = if (fallbackLabelStyle == NowPlayingFallbackLabelStyle.Arc) {
				GeneratedArtworkVariant.NowPlayingDisc
			} else {
				GeneratedArtworkVariant.GridCard
			}
		)
	}

	if (visibleCoverArtId.isNullOrBlank() && resolvedImageUrl == null) {
		return CoverArtFallback(
			fallbackContent = fallbackContent,
			generatedArtwork = resolvedGeneratedArtwork,
			modifier = commonModifier,
			showLoadingIndicator = artworkResolving
		)
	}
	SubcomposeAsyncImage(
		model = model,
		contentDescription = contentDescription,
		modifier = commonModifier,
		contentScale = contentScale,
		colorFilter = colorFilter,
		success = { state ->
			val image = state.result.image
			onImageSizeResolved?.invoke(image.width, image.height)
			val compressedImageBitmap = remember(image) {
				image.toImageBitmap()
			}
			if (edgeCompression == CoverArtEdgeCompression.Soft && colorFilter == null && compressedImageBitmap != null) {
				SoftEdgeCompressedCoverArtImage(
					image = compressedImageBitmap,
					modifier = Modifier.fillMaxSize()
				)
			} else {
				SubcomposeAsyncImageContent()
			}
		},
		onSuccess = { state ->
			val image = state.result.image
			onImageSizeResolved?.invoke(image.width, image.height)
		},
		loading = {
			CoverArtFallback(
				fallbackContent = fallbackContent,
				generatedArtwork = resolvedGeneratedArtwork,
				modifier = Modifier.fillMaxSize(),
				showLoadingIndicator = true
			)
		},
		error = {
			LaunchedEffect(it.result.throwable) {
				if (imageDiagnosticLabel != null) {
					Logger.w(
						"CoverArt",
						"Failed to load cover art, falling back to placeholder" +
							" [$imageDiagnosticLabel] usesServer=$usesServerCoverArt " +
							"coverArtId=${coverArtDiagnosticValue(coverArtId)} " +
							"imageUrl=${coverArtDiagnosticValue(resolvedImageUrl)} " +
							"cacheKey=${coverArtDiagnosticValue(resolvedImageCacheKey)} " +
							"headerKeys=${coverArtDiagnosticHeaderKeys(resolvedRequestHeaders)}",
						it.result.throwable
					)
				}
				if (usesServerCoverArt) {
					onServerCoverLoadFailed?.invoke()
				}
			}
			CoverArtFallback(
				fallbackContent = fallbackContent,
				generatedArtwork = resolvedGeneratedArtwork,
				modifier = Modifier.fillMaxSize()
			)
		}
	)
}

@Composable
private fun SoftEdgeCompressedCoverArtImage(
	image: androidx.compose.ui.graphics.ImageBitmap,
	modifier: Modifier = Modifier
) {
	Canvas(modifier = modifier) {
		val canvasWidth = size.width.roundToInt().coerceAtLeast(1)
		val canvasHeight = size.height.roundToInt().coerceAtLeast(1)
		val imageWidth = image.width.coerceAtLeast(1)
		val imageHeight = image.height.coerceAtLeast(1)
		val scale = min(
			canvasWidth.toFloat() / imageWidth.toFloat(),
			canvasHeight.toFloat() / imageHeight.toFloat()
		)
		val fittedWidth = (imageWidth * scale).roundToInt().coerceAtLeast(1)
		val fittedHeight = (imageHeight * scale).roundToInt().coerceAtLeast(1)
		val left = ((canvasWidth - fittedWidth) / 2f).roundToInt()
		val top = ((canvasHeight - fittedHeight) / 2f).roundToInt()

		if (top > 0) {
			val bottomHeight = canvasHeight - top - fittedHeight
			softEdgeCompressionBands(
				axis = SoftEdgeCompressionAxis.Vertical,
				canvasPrimarySize = canvasHeight,
				canvasSecondarySize = canvasWidth,
				imagePrimarySize = imageHeight,
				imageSecondarySize = imageWidth,
				leadingGapSize = top,
				trailingGapSize = bottomHeight
			).forEach { band ->
				drawImage(
					image = image,
					srcOffset = band.srcOffset,
					srcSize = band.srcSize,
					dstOffset = band.dstOffset,
					dstSize = band.dstSize
				)
			}
		}

		if (left > 0) {
			val rightWidth = canvasWidth - left - fittedWidth
			softEdgeCompressionBands(
				axis = SoftEdgeCompressionAxis.Horizontal,
				canvasPrimarySize = canvasWidth,
				canvasSecondarySize = canvasHeight,
				imagePrimarySize = imageWidth,
				imageSecondarySize = imageHeight,
				leadingGapSize = left,
				trailingGapSize = rightWidth
			).forEach { band ->
				drawImage(
					image = image,
					srcOffset = band.srcOffset,
					srcSize = band.srcSize,
					dstOffset = band.dstOffset,
					dstSize = band.dstSize
				)
			}
		}

		drawImage(
			image = image,
			srcOffset = IntOffset.Zero,
			srcSize = IntSize(imageWidth, imageHeight),
			dstOffset = IntOffset(left, top),
			dstSize = IntSize(fittedWidth, fittedHeight)
		)
	}
}

internal fun softEdgeCompressionBands(
	axis: SoftEdgeCompressionAxis,
	canvasPrimarySize: Int,
	canvasSecondarySize: Int,
	imagePrimarySize: Int,
	imageSecondarySize: Int,
	leadingGapSize: Int,
	trailingGapSize: Int
): List<SoftEdgeCompressionBand> {
	if (canvasPrimarySize <= 0 || canvasSecondarySize <= 0 || imagePrimarySize <= 0 || imageSecondarySize <= 0) {
		return emptyList()
	}
	val leadingBands = softEdgeCompressionSideBands(
		axis = axis,
		canvasSecondarySize = canvasSecondarySize,
		imagePrimarySize = imagePrimarySize,
		imageSecondarySize = imageSecondarySize,
		gapSize = leadingGapSize,
		isLeading = true,
		dstPrimaryStart = 0
	)
	val trailingBands = softEdgeCompressionSideBands(
		axis = axis,
		canvasSecondarySize = canvasSecondarySize,
		imagePrimarySize = imagePrimarySize,
		imageSecondarySize = imageSecondarySize,
		gapSize = trailingGapSize,
		isLeading = false,
		dstPrimaryStart = canvasPrimarySize - trailingGapSize
	)
	return leadingBands + trailingBands
}

internal enum class SoftEdgeCompressionAxis {
	Horizontal,
	Vertical
}

private fun softEdgeCompressionSideBands(
	axis: SoftEdgeCompressionAxis,
	canvasSecondarySize: Int,
	imagePrimarySize: Int,
	imageSecondarySize: Int,
	gapSize: Int,
	isLeading: Boolean,
	dstPrimaryStart: Int
): List<SoftEdgeCompressionBand> {
	if (gapSize <= 0) return emptyList()
	val bandCount = min(SoftEdgeCompressionBandCount, gapSize).coerceAtLeast(1)
	val sourceDepth = (imagePrimarySize * SoftEdgeCompressionOuterSourceFraction)
		.roundToInt()
		.coerceIn(1, imagePrimarySize)
	return (0 until bandCount).mapNotNull { index ->
		val dstStart = dstPrimaryStart + (gapSize * index / bandCount.toFloat()).roundToInt()
		val dstEnd = dstPrimaryStart + (gapSize * (index + 1) / bandCount.toFloat()).roundToInt()
		val dstSize = (dstEnd - dstStart).coerceAtLeast(1)
		val inner = index / bandCount.toFloat()
		val outer = (index + 1) / bandCount.toFloat()
		val sourceStartFraction = if (isLeading) {
			softEdgeCompressionEaseIn(inner)
		} else {
			1f - softEdgeCompressionEaseIn(outer)
		}
		val sourceEndFraction = if (isLeading) {
			softEdgeCompressionEaseIn(outer)
		} else {
			1f - softEdgeCompressionEaseIn(inner)
		}
		val srcStart = (sourceStartFraction * sourceDepth).roundToInt().coerceIn(0, imagePrimarySize - 1)
		val srcEnd = (sourceEndFraction * sourceDepth).roundToInt().coerceIn(srcStart + 1, imagePrimarySize)
		val srcPrimaryOffset = if (isLeading) srcStart else imagePrimarySize - srcEnd
		val srcPrimarySize = (srcEnd - srcStart).coerceAtLeast(1)
		if (axis == SoftEdgeCompressionAxis.Horizontal) {
			SoftEdgeCompressionBand(
				srcOffset = IntOffset(srcPrimaryOffset, 0),
				srcSize = IntSize(srcPrimarySize, imageSecondarySize),
				dstOffset = IntOffset(dstStart, 0),
				dstSize = IntSize(dstSize, canvasSecondarySize)
			)
		} else {
			SoftEdgeCompressionBand(
				srcOffset = IntOffset(0, srcPrimaryOffset),
				srcSize = IntSize(imageSecondarySize, srcPrimarySize),
				dstOffset = IntOffset(0, dstStart),
				dstSize = IntSize(canvasSecondarySize, dstSize)
			)
		}
	}
}

private fun softEdgeCompressionEaseIn(value: Float): Float {
	val clamped = value.coerceIn(0f, 1f)
	return clamped * clamped
}

private const val SoftEdgeCompressionBandCount = 8
private const val SoftEdgeCompressionOuterSourceFraction = 0.22f

private fun coverArtDiagnosticValue(value: String?): String {
	val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "none"
	val withoutFragment = trimmed.substringBefore('#')
	val hasQuery = '?' in withoutFragment
	val withoutQuery = withoutFragment.substringBefore('?')
	val shortened = if (withoutQuery.length <= 140) withoutQuery else "...${withoutQuery.takeLast(137)}"
	return shortened + if (hasQuery) "?query" else ""
}

private fun coverArtDiagnosticHeaderKeys(headers: Map<String, String>): String =
	headers.keys
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.sorted()
		.joinToString(",")
		.ifEmpty { "none" }

@Composable
private fun CoverArtFallback(
	fallbackContent: CoverArtFallbackContent,
	generatedArtwork: GeneratedArtworkSpec,
	modifier: Modifier = Modifier,
	showLoadingIndicator: Boolean = false
) {
	val seedSource = generatedArtwork.seed ?: fallbackContent.seedSource
	val seed = stablePositiveHash(seedSource)
	val palette = coverArtFallbackPalette(seedSource)
	val kind = coverArtFallbackKindLabel(generatedArtwork.kindLabel)
	val label = generatedArtwork.primaryLabel ?: fallbackContent.label
	val fallbackLabelStyle = if (generatedArtwork.variant == GeneratedArtworkVariant.NowPlayingDisc) {
		NowPlayingFallbackLabelStyle.Arc
	} else {
		NowPlayingFallbackLabelStyle.Center
	}
	val metrics = generatedArtwork.variant.metrics()
	Box(
		modifier = modifier,
		contentAlignment = Alignment.Center
	) {
		Canvas(Modifier.matchParentSize()) {
			drawRect(
				Brush.linearGradient(
					colors = listOf(palette.bgStart, palette.bgEnd),
					start = Offset.Zero,
					end = Offset(size.width, size.height)
				)
			)
			drawPath(
				organicBlobPath(size.width, size.height, .24f, .22f, .34f, seed),
				color = palette.blob1
			)
			drawPath(
				organicBlobPath(size.width, size.height, .78f, .78f, .38f, seed + 1),
				color = palette.blob2
			)
			drawPath(
				organicBlobPath(size.width, size.height, .78f, .22f, .28f, seed + 2),
				color = palette.blob3
			)
			drawPath(
				organicBlobPath(size.width, size.height, .24f, .78f, .32f, seed + 3),
				color = palette.blob4
			)
			val radius = min(size.width, size.height)
			drawPath(
				organicBlobPath(size.width, size.height, .5f, .5f, .42f, seed + 4),
				color = palette.line1,
				style = Stroke(width = (radius * .002f).coerceAtLeast(.8f))
			)
			drawPath(
				organicBlobPath(size.width, size.height, .5f, .5f, .35f, seed + 5),
				color = palette.line2,
				style = Stroke(width = (radius * .0015f).coerceAtLeast(.65f))
			)
		}
		kind?.takeIf { fallbackLabelStyle == NowPlayingFallbackLabelStyle.Center }?.let { chipText ->
			Text(
				text = chipText,
				color = Color(0xFFF5F2EA).copy(alpha = .76f),
				style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
				maxLines = 1,
				autoSize = TextAutoSize.StepBased(minFontSize = 4.sp, maxFontSize = 11.sp),
				textAlign = TextAlign.Center,
				modifier = Modifier
					.align(Alignment.TopCenter)
					.padding(top = metrics.kindTopPadding)
					.background(palette.chip, RoundedCornerShape(50))
					.border(1.dp, palette.chipStroke, RoundedCornerShape(50))
					.padding(horizontal = metrics.kindHorizontalPadding, vertical = metrics.kindVerticalPadding)
			)
		}
		label?.let { text ->
			when (fallbackLabelStyle) {
				NowPlayingFallbackLabelStyle.Center -> Text(
					text = text,
					color = Color(0xFFF5F2EA),
					style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
					maxLines = metrics.primaryMaxLines,
					autoSize = TextAutoSize.StepBased(
						minFontSize = metrics.primaryMinFontSize,
						maxFontSize = metrics.primaryMaxFontSize
					),
					fontFamily = defaultFont(grade = 90, round = 100f),
					textAlign = TextAlign.Center,
					modifier = Modifier
						.align(Alignment.Center)
						.padding(horizontal = metrics.primaryHorizontalPadding)
				)

				NowPlayingFallbackLabelStyle.Arc -> CoverArtFallbackArcLabel(
					label = text,
					modifier = Modifier.matchParentSize()
				)
			}
		}
		if (showLoadingIndicator) {
			CircularProgressIndicator(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(10.dp)
					.size(22.dp),
				color = Color(0xFFF5F2EA).copy(alpha = .88f),
				strokeWidth = 2.dp
			)
		}
	}
}

private data class GeneratedArtworkMetrics(
	val kindTopPadding: Dp,
	val kindHorizontalPadding: Dp,
	val kindVerticalPadding: Dp,
	val primaryHorizontalPadding: Dp,
	val primaryMinFontSize: androidx.compose.ui.unit.TextUnit,
	val primaryMaxFontSize: androidx.compose.ui.unit.TextUnit,
	val primaryMaxLines: Int
)

private fun GeneratedArtworkVariant.metrics(): GeneratedArtworkMetrics =
	when (this) {
		GeneratedArtworkVariant.SheetThumbnail -> GeneratedArtworkMetrics(
			kindTopPadding = 4.dp,
			kindHorizontalPadding = 6.dp,
			kindVerticalPadding = 2.dp,
			primaryHorizontalPadding = 7.dp,
			primaryMinFontSize = 4.sp,
			primaryMaxFontSize = 11.sp,
			primaryMaxLines = 2
		)
		GeneratedArtworkVariant.DetailHero -> GeneratedArtworkMetrics(
			kindTopPadding = 14.dp,
			kindHorizontalPadding = 11.dp,
			kindVerticalPadding = 4.dp,
			primaryHorizontalPadding = 26.dp,
			primaryMinFontSize = 7.sp,
			primaryMaxFontSize = 24.sp,
			primaryMaxLines = 4
		)
		GeneratedArtworkVariant.NowPlayingDisc -> GeneratedArtworkMetrics(
			kindTopPadding = 12.dp,
			kindHorizontalPadding = 12.dp,
			kindVerticalPadding = 4.dp,
			primaryHorizontalPadding = 16.dp,
			primaryMinFontSize = 8.sp,
			primaryMaxFontSize = 32.sp,
			primaryMaxLines = 3
		)
		GeneratedArtworkVariant.GridCard,
		GeneratedArtworkVariant.CarouselCard -> GeneratedArtworkMetrics(
			kindTopPadding = 10.dp,
			kindHorizontalPadding = 10.dp,
			kindVerticalPadding = 3.dp,
			primaryHorizontalPadding = 16.dp,
			primaryMinFontSize = 6.sp,
			primaryMaxFontSize = 20.sp,
			primaryMaxLines = 3
		)
	}

@Composable
private fun CoverArtFallbackArcLabel(
	label: String,
	modifier: Modifier = Modifier
) {
	val arcText = coverArtFallbackArcText(label) ?: return
	val chars = arcText.toList()
	val sweepDegrees = when {
		chars.size <= 1 -> 0f
		chars.size <= 10 -> 72f
		chars.size <= 18 -> 108f
		else -> 132f
	}
	BoxWithConstraints(
		modifier = modifier,
		contentAlignment = Alignment.Center
	) {
		val radius = min(maxWidth.value, maxHeight.value).dp * 0.34f
		val startAngle = -90f - sweepDegrees / 2f
		chars.forEachIndexed { index, char ->
			val fraction = if (chars.size <= 1) 0.5f else index / (chars.size - 1).toFloat()
			val angle = startAngle + sweepDegrees * fraction
			val radians = angle * PI.toFloat() / 180f
			Text(
				text = char.toString(),
				color = Color(0xFFF5F2EA).copy(alpha = .92f),
				style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
				maxLines = 1,
				fontFamily = defaultFont(grade = 90, round = 100f),
				textAlign = TextAlign.Center,
				modifier = Modifier
					.align(Alignment.Center)
					.offset(
						x = radius * cos(radians),
						y = radius * sin(radians)
					)
					.graphicsLayer {
						rotationZ = angle + 90f
					}
			)
		}
	}
}

private fun organicBlobPath(
	width: Float,
	height: Float,
	centerXFraction: Float,
	centerYFraction: Float,
	radiusFraction: Float,
	seed: Int
): Path {
	val centerX = width * centerXFraction
	val centerY = height * centerYFraction
	val radius = min(width, height) * radiusFraction
	val count = 5 + (seed % 4)
	val points = List(count) { index ->
		val angle = (PI * 2.0 * index) / count
		val variance = .7f + ((seed.toLong() * (index + 1) * 11L) % 60L) / 100f
		val pointRadius = radius * variance
		Offset(
			x = centerX + cos(angle).toFloat() * pointRadius,
			y = centerY + sin(angle).toFloat() * pointRadius
		)
	}
	val mids = points.mapIndexed { index, point ->
		val next = points[(index + 1) % points.size]
		Offset(
			x = (point.x + next.x) / 2f,
			y = (point.y + next.y) / 2f
		)
	}
	return Path().apply {
		moveTo(mids.first().x, mids.first().y)
		for (index in 1..points.size) {
			val point = points[index % points.size]
			val mid = mids[index % mids.size]
			quadraticTo(point.x, point.y, mid.x, mid.y)
		}
		close()
	}
}
