package paige.navic.reader

import kotlin.math.ceil
import kotlin.math.floor

/** A head (current word) mask rect and the share of the word's reading order it covers. */
data class ReaderWhispersyncHeadMaskSpan(
	val rect: ReaderPageTurnPixelRect,
	val startFraction: Double,
	val endFraction: Double
)

data class ReaderWhispersyncPageLocalRect(
	val role: ReaderPageTurnPageRole,
	val left: Double,
	val top: Double,
	val width: Double,
	val height: Double
) {
	init {
		require(left.isFinite() && top.isFinite())
		require(width.isFinite() && width > 0.0)
		require(height.isFinite() && height > 0.0)
	}
}

data class ReaderWhispersyncAnchorReceipt(
	val foliateSessionId: String,
	val destinationCommitToken: String,
	val visualPageOrdinal: Int,
	val spineIndex: Int,
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val presentationMutationGeneration: Long,
	val presentationSequence: Long,
	val anchorGeneration: Long,
	val boundarySequence: Long,
	val layoutGeneration: Long,
	val viewGeneration: Long,
	val commitSequence: Long,
	val committedSpineIndex: Int,
	val committedChapterPageIndex: Int,
	val committedChapterPageCount: Int,
	val paginationFingerprint: String,
	val layoutFingerprint: String,
	val readerSettingsRasterKey: String,
	val captureGeometry: ReaderPageTurnCaptureGeometry,
	val pageLocalRects: List<ReaderWhispersyncPageLocalRect>,
	/** The current word alone, in reading order; the native flow mask sweeps it in. */
	val pageLocalHeadRects: List<ReaderWhispersyncPageLocalRect> = emptyList()
) {
	init {
		require(foliateSessionId.isNotBlank() && foliateSessionId == foliateSessionId.trim())
		require(destinationCommitToken.isNotBlank() && destinationCommitToken == destinationCommitToken.trim())
		require(visualPageOrdinal >= 0)
		require(spineIndex >= 0)
		require(rasterGeneration >= 0L)
		require(textureGeneration >= 0L)
		require(presentationMutationGeneration > 0L)
		require(presentationSequence > 0L)
		require(anchorGeneration > 0L)
		require(boundarySequence >= 0L)
		require(layoutGeneration >= 0L)
		require(viewGeneration >= 0L)
		require(commitSequence >= 0L)
		require(committedSpineIndex == spineIndex)
		require(committedChapterPageIndex >= 0)
		require(committedChapterPageCount > 0)
		require(committedChapterPageIndex < committedChapterPageCount)
		require(paginationFingerprint.isNotBlank())
		require(layoutFingerprint.isNotBlank())
		require(readerSettingsRasterKey.isNotBlank())
		require(pageLocalRects.isNotEmpty())
		require(pageLocalRects.all { rect -> captureGeometry.pages.any { it.role == rect.role } })
	}

	fun maskRectsFor(
		role: ReaderPageTurnPageRole,
		bitmapWidth: Int,
		bitmapHeight: Int
	): List<ReaderPageTurnPixelRect> = pixelRectsFor(pageLocalRects, role, bitmapWidth, bitmapHeight)

	/**
	 * The current word's mask rects on [role], each with the share of the word's reading order
	 * (0 to 1, across every head rect on every page) it spans, left to right.
	 */
	fun headMaskSpansFor(
		role: ReaderPageTurnPageRole,
		bitmapWidth: Int,
		bitmapHeight: Int
	): List<ReaderWhispersyncHeadMaskSpan> {
		val total = pageLocalHeadRects.sumOf { it.width }
		if (total <= 0.0) return emptyList()
		var consumed = 0.0
		return pageLocalHeadRects.mapNotNull { rect ->
			val start = consumed / total
			consumed += rect.width
			if (rect.role != role) return@mapNotNull null
			pixelRectsFor(listOf(rect), role, bitmapWidth, bitmapHeight).singleOrNull()?.let { pixel ->
				ReaderWhispersyncHeadMaskSpan(pixel, start, consumed / total)
			}
		}
	}

	private fun pixelRectsFor(
		rects: List<ReaderWhispersyncPageLocalRect>,
		role: ReaderPageTurnPageRole,
		bitmapWidth: Int,
		bitmapHeight: Int
	): List<ReaderPageTurnPixelRect> {
		if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
		val page = captureGeometry.pages.firstOrNull { it.role == role }
			?.takeIf { it.width.isFinite() && it.width > 0.0 && it.height.isFinite() && it.height > 0.0 }
			?: return emptyList()
		return rects.asSequence()
			.filter { it.role == role }
			.mapNotNull { rect ->
				val localLeft = rect.left.coerceIn(0.0, page.width)
				val localTop = rect.top.coerceIn(0.0, page.height)
				val localRight = (rect.left + rect.width).coerceIn(0.0, page.width)
				val localBottom = (rect.top + rect.height).coerceIn(0.0, page.height)
				if (localRight <= localLeft || localBottom <= localTop) return@mapNotNull null
				ReaderPageTurnPixelRect(
					left = floor(localLeft / page.width * bitmapWidth).toInt().coerceIn(0, bitmapWidth),
					top = floor(localTop / page.height * bitmapHeight).toInt().coerceIn(0, bitmapHeight),
					right = ceil(localRight / page.width * bitmapWidth).toInt().coerceIn(0, bitmapWidth),
					bottom = ceil(localBottom / page.height * bitmapHeight).toInt().coerceIn(0, bitmapHeight)
				).takeIf { it.width > 0 && it.height > 0 }
			}
			.toList()
	}

	/**
	 * Mask rects for a startup snapshot bitmap. Like [ReaderPageTurnCaptureGeometry.leafGeometry], that
	 * bitmap covers the union of the captured pages (a bounded spread starts at its outer inset), not
	 * the whole viewport; scaling against the viewport shifted every highlight toward the spine.
	 */
	fun surfaceMaskRects(bitmapWidth: Int, bitmapHeight: Int): List<ReaderPageTurnPixelRect> {
		if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
		val pages = captureGeometry.pages.filter {
			it.left.isFinite() && it.top.isFinite() && it.width.isFinite() && it.height.isFinite() &&
				it.width > 0.0 && it.height > 0.0
		}
		if (pages.isEmpty()) return emptyList()
		val surfaceLeft = pages.minOf { it.left }
		val surfaceTop = pages.minOf { it.top }
		val surfaceWidth = pages.maxOf { it.left + it.width } - surfaceLeft
		val surfaceHeight = pages.maxOf { it.top + it.height } - surfaceTop
		return pageLocalRects.mapNotNull { rect ->
			val page = pages.firstOrNull { it.role == rect.role } ?: return@mapNotNull null
			val localLeft = rect.left.coerceIn(0.0, page.width)
			val localTop = rect.top.coerceIn(0.0, page.height)
			val localRight = (rect.left + rect.width).coerceIn(0.0, page.width)
			val localBottom = (rect.top + rect.height).coerceIn(0.0, page.height)
			if (localRight <= localLeft || localBottom <= localTop) return@mapNotNull null
			val x = { local: Double -> (page.left + local - surfaceLeft) / surfaceWidth * bitmapWidth }
			val y = { local: Double -> (page.top + local - surfaceTop) / surfaceHeight * bitmapHeight }
			ReaderPageTurnPixelRect(
				left = floor(x(localLeft)).toInt().coerceIn(0, bitmapWidth),
				top = floor(y(localTop)).toInt().coerceIn(0, bitmapHeight),
				right = ceil(x(localRight)).toInt().coerceIn(0, bitmapWidth),
				bottom = ceil(y(localBottom)).toInt().coerceIn(0, bitmapHeight)
			).takeIf { it.width > 0 && it.height > 0 }
		}
	}
}
