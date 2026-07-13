package paige.navic.reader

import kotlin.math.roundToInt

enum class ReaderPageTurnLayoutMode { Single, Spread }

enum class ReaderPageTurnPageRole { Full, Left, Right }

enum class ReaderPageTurnPhysicalDirection { TowardLeft, TowardRight }

data class ReaderPageTurnPageRect(
	val role: ReaderPageTurnPageRole,
	val left: Double,
	val top: Double,
	val width: Double,
	val height: Double
)

data class ReaderPageTurnPixelRect(
	val left: Int,
	val top: Int,
	val right: Int,
	val bottom: Int
) {
	val width: Int get() = right - left
	val height: Int get() = bottom - top
}

data class ReaderPageTurnLeafGeometry(
	val fullLeafRect: ReaderPageTurnPixelRect?,
	val leftLeafRect: ReaderPageTurnPixelRect?,
	val gutterRect: ReaderPageTurnPixelRect?,
	val rightLeafRect: ReaderPageTurnPixelRect?
) {
	fun activeLeafRect(
		direction: ReaderPageTurnPhysicalDirection,
		spread: Boolean
	): ReaderPageTurnPixelRect? = if (spread) {
		when (direction) {
			ReaderPageTurnPhysicalDirection.TowardLeft -> rightLeafRect
			ReaderPageTurnPhysicalDirection.TowardRight -> leftLeafRect
		}
	} else {
		fullLeafRect
	}
}

data class ReaderPageTurnCaptureGeometry(
	val viewportWidth: Double,
	val viewportHeight: Double,
	val mode: ReaderPageTurnLayoutMode,
	val pages: List<ReaderPageTurnPageRect>,
	val reverseFaceColorArgb: Long? = null
) {
	fun leafGeometry(bitmapWidth: Int, bitmapHeight: Int): ReaderPageTurnLeafGeometry? {
		if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
		val validPages = pages.filter {
			it.left.isFinite() && it.top.isFinite() && it.width.isFinite() && it.height.isFinite() &&
				it.width > 0.0 && it.height > 0.0
		}
		if (validPages.isEmpty()) return null
		val surfaceLeft = validPages.minOf { it.left }
		val surfaceTop = validPages.minOf { it.top }
		val surfaceRight = validPages.maxOf { it.left + it.width }
		val surfaceBottom = validPages.maxOf { it.top + it.height }
		val surfaceWidth = surfaceRight - surfaceLeft
		val surfaceHeight = surfaceBottom - surfaceTop
		if (surfaceWidth <= 0.0 || surfaceHeight <= 0.0) return null

		fun toBitmapRect(page: ReaderPageTurnPageRect): ReaderPageTurnPixelRect? = ReaderPageTurnPixelRect(
			left = ((page.left - surfaceLeft) / surfaceWidth * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth),
			top = ((page.top - surfaceTop) / surfaceHeight * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight),
			right = ((page.left + page.width - surfaceLeft) / surfaceWidth * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth),
			bottom = ((page.top + page.height - surfaceTop) / surfaceHeight * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight)
		).takeIf { it.width > 0 && it.height > 0 }

		fun pageRect(role: ReaderPageTurnPageRole): ReaderPageTurnPixelRect? =
			validPages.firstOrNull { it.role == role }?.let(::toBitmapRect)

		return when (mode) {
			ReaderPageTurnLayoutMode.Single -> ReaderPageTurnLeafGeometry(
				fullLeafRect = pageRect(ReaderPageTurnPageRole.Full)
					?: validPages.firstOrNull()?.let(::toBitmapRect),
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null
			)
			ReaderPageTurnLayoutMode.Spread -> {
				val left = pageRect(ReaderPageTurnPageRole.Left)
				val right = pageRect(ReaderPageTurnPageRole.Right)
				val gutter = if (left != null && right != null && right.left >= left.right) {
					ReaderPageTurnPixelRect(
						left = left.right,
						top = minOf(left.top, right.top),
						right = right.left,
						bottom = maxOf(left.bottom, right.bottom)
					)
				} else {
					null
				}
				ReaderPageTurnLeafGeometry(
					fullLeafRect = null,
					leftLeafRect = left,
					gutterRect = gutter,
					rightLeafRect = right
				)
			}
		}
	}

	fun pageFor(direction: ReaderPageTurnPhysicalDirection): ReaderPageTurnPageRect? =
		when (mode) {
			ReaderPageTurnLayoutMode.Single -> pages.firstOrNull { it.role == ReaderPageTurnPageRole.Full }
				?: pages.firstOrNull()
			ReaderPageTurnLayoutMode.Spread -> when (direction) {
				ReaderPageTurnPhysicalDirection.TowardLeft -> pages.firstOrNull { it.role == ReaderPageTurnPageRole.Right }
				ReaderPageTurnPhysicalDirection.TowardRight -> pages.firstOrNull { it.role == ReaderPageTurnPageRole.Left }
			}
		}

	fun sourceRectInWindow(
		direction: ReaderPageTurnPhysicalDirection,
		webViewWindowLeft: Int,
		webViewWindowTop: Int,
		webViewWidth: Int,
		webViewHeight: Int
	): ReaderPageTurnPixelRect? {
		val page = pageFor(direction) ?: return null
		return page.toWindowPixels(webViewWindowLeft, webViewWindowTop, webViewWidth, webViewHeight)
	}

	fun pageRectInWindow(
		role: ReaderPageTurnPageRole,
		webViewWindowLeft: Int,
		webViewWindowTop: Int,
		webViewWidth: Int,
		webViewHeight: Int
	): ReaderPageTurnPixelRect? = pages
		.firstOrNull { it.role == role }
		?.toWindowPixels(webViewWindowLeft, webViewWindowTop, webViewWidth, webViewHeight)

	fun surfaceRectInWindow(
		webViewWindowLeft: Int,
		webViewWindowTop: Int,
		webViewWidth: Int,
		webViewHeight: Int
	): ReaderPageTurnPixelRect? {
		val pixelPages = pages.mapNotNull {
			it.toWindowPixels(webViewWindowLeft, webViewWindowTop, webViewWidth, webViewHeight)
		}
		if (pixelPages.isEmpty()) return null
		return ReaderPageTurnPixelRect(
			left = pixelPages.minOf { it.left },
			top = pixelPages.minOf { it.top },
			right = pixelPages.maxOf { it.right },
			bottom = pixelPages.maxOf { it.bottom }
		).takeIf { it.width > 0 && it.height > 0 }
	}

	private fun ReaderPageTurnPageRect.toWindowPixels(
		webViewWindowLeft: Int,
		webViewWindowTop: Int,
		webViewWidth: Int,
		webViewHeight: Int
	): ReaderPageTurnPixelRect? {
		if (viewportWidth <= 0.0 || viewportHeight <= 0.0 || webViewWidth <= 0 || webViewHeight <= 0) return null
		val scaleX = webViewWidth / viewportWidth
		val scaleY = webViewHeight / viewportHeight
		val webRight = webViewWindowLeft + webViewWidth
		val webBottom = webViewWindowTop + webViewHeight
		val left = (webViewWindowLeft + this.left * scaleX).roundToInt().coerceIn(webViewWindowLeft, webRight)
		val top = (webViewWindowTop + this.top * scaleY).roundToInt().coerceIn(webViewWindowTop, webBottom)
		val right = (webViewWindowLeft + (this.left + width) * scaleX).roundToInt().coerceIn(webViewWindowLeft, webRight)
		val bottom = (webViewWindowTop + (this.top + height) * scaleY).roundToInt().coerceIn(webViewWindowTop, webBottom)
		return ReaderPageTurnPixelRect(left, top, right, bottom).takeIf { it.width > 0 && it.height > 0 }
	}
}
