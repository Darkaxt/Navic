package paige.navic.ui.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.util.core.Logger
import paige.navic.util.core.toNetworkHeaders
import paige.navic.ui.theme.defaultFont
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
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
	contentDescription: String? = null,
	fallbackKind: String? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	onServerCoverLoadFailed: (suspend () -> Unit)? = null,
	square: Boolean = true,
	crossfadeMs: Int = 500,
	shadowElevation: Dp = 0.dp,
	interactionSource: MutableInteractionSource? = null,
	shape: Shape? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val shape = shape ?: preferenceManager.coverArtShape.shape
	val coilPlatformContext = LocalCoilPlatformContext.current
	val serverRequestHeaders = preferenceManager.serverRequestHeadersMap()
	val sessionManager = koinInject<SessionManager>()
	val resolvedImageUrl = imageUrl?.takeIf { it.isNotBlank() }
	val usesServerCoverArt = resolvedImageUrl == null
	val resolvedImageCacheKey = imageCacheKey ?: resolvedImageUrl ?: coverArtId
	val model = remember(coverArtId, resolvedImageUrl, resolvedImageCacheKey, serverRequestHeaders) {
		ImageRequest.Builder(coilPlatformContext)
			.data(resolvedImageUrl ?: coverArtId?.let { sessionManager.getCoverArtUrl(it) })
			.memoryCacheKey(resolvedImageCacheKey)
			.diskCacheKey(resolvedImageCacheKey)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.crossfade(crossfadeMs)
			.apply {
				if (usesServerCoverArt) {
					httpHeaders(serverRequestHeaders.toNetworkHeaders())
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

	val fallbackContent = remember(contentDescription, coverArtId, resolvedImageUrl) {
		coverArtFallbackContent(
			contentDescription = contentDescription,
			coverArtId = coverArtId,
			imageUrl = resolvedImageUrl
		)
	}

	if (coverArtId.isNullOrBlank() && resolvedImageUrl == null) {
		return CoverArtFallback(
			fallbackContent = fallbackContent,
			fallbackKind = fallbackKind,
			modifier = commonModifier
		)
	}
	SubcomposeAsyncImage(
		model = model,
		contentDescription = contentDescription,
		modifier = commonModifier,
		contentScale = ContentScale.Crop,
		error = {
			LaunchedEffect(it.result.throwable) {
				Logger.w(
					"CoverArt",
					"Failed to load cover art, falling back to placeholder",
					it.result.throwable
				)
				if (usesServerCoverArt) {
					onServerCoverLoadFailed?.invoke()
				}
			}
			CoverArtFallback(
				fallbackContent = fallbackContent,
				fallbackKind = fallbackKind,
				modifier = Modifier.fillMaxSize()
			)
		}
	)
}

@Composable
private fun CoverArtFallback(
	fallbackContent: CoverArtFallbackContent,
	fallbackKind: String?,
	modifier: Modifier = Modifier
) {
	val seed = stablePositiveHash(fallbackContent.seedSource)
	val palette = coverArtFallbackPalette(fallbackContent.seedSource)
	val kind = fallbackKind
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.uppercase()
		?.toList()
		?.joinToString(separator = " ")
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
		kind?.let { chipText ->
			Text(
				text = chipText,
				color = Color(0xFFF5F2EA).copy(alpha = .76f),
				style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
				maxLines = 1,
				autoSize = TextAutoSize.StepBased(minFontSize = 4.sp, maxFontSize = 11.sp),
				textAlign = TextAlign.Center,
				modifier = Modifier
					.align(Alignment.TopCenter)
					.padding(top = 12.dp)
					.background(palette.chip, RoundedCornerShape(50))
					.border(1.dp, palette.chipStroke, RoundedCornerShape(50))
					.padding(horizontal = 12.dp, vertical = 4.dp)
			)
		}
		fallbackContent.label?.let { label ->
			Text(
				text = label,
				color = Color(0xFFF5F2EA),
				style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
				maxLines = 3,
				autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 32.sp),
				fontFamily = defaultFont(grade = 90, round = 100f),
				textAlign = TextAlign.Center,
				modifier = Modifier
					.align(Alignment.Center)
					.padding(horizontal = 16.dp)
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
		val angle = (Math.PI * 2.0 * index) / count
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
