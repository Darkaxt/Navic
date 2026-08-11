package paige.navic.reader

import kotlin.math.ceil
import kotlin.math.floor

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
	val paginationFingerprint: String,
	val layoutFingerprint: String,
	val readerSettingsRasterKey: String,
	val captureGeometry: ReaderPageTurnCaptureGeometry,
	val pageLocalRects: List<ReaderWhispersyncPageLocalRect>
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
	): List<ReaderPageTurnPixelRect> {
		if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
		val page = captureGeometry.pages.firstOrNull { it.role == role }
			?.takeIf { it.width.isFinite() && it.width > 0.0 && it.height.isFinite() && it.height > 0.0 }
			?: return emptyList()
		return pageLocalRects.asSequence()
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
}
