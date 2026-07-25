package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect

internal fun readerPageRasterLeafGeometry(
	metadata: ReaderPageRasterMetadata,
	bitmapWidth: Int,
	bitmapHeight: Int
): ReaderPageTurnLeafGeometry? {
	if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
	if (
		metadata.surfaceLeft != 0 ||
		metadata.surfaceTop != 0 ||
		metadata.surfaceRight != bitmapWidth ||
		metadata.surfaceBottom != bitmapHeight
	) return null

	fun ReaderPageRasterRect.valid(): Boolean =
		left >= 0 && top >= 0 && right <= bitmapWidth && bottom <= bitmapHeight &&
			right > left && bottom > top

	fun ReaderPageRasterRect.validGutter(): Boolean =
		left >= 0 && top >= 0 && right <= bitmapWidth && bottom <= bitmapHeight &&
			right >= left && bottom > top

	val full = metadata.fullLeafRect?.takeIf { rect -> rect.valid() }
	val left = metadata.leftLeafRect?.takeIf { rect -> rect.valid() }
	val gutter = metadata.gutterRect?.takeIf { rect -> rect.validGutter() }
	val right = metadata.rightLeafRect?.takeIf { rect -> rect.valid() }
	if (metadata.fullLeafRect != null && full == null) return null
	if (metadata.leftLeafRect != null && left == null) return null
	if (metadata.gutterRect != null && gutter == null) return null
	if (metadata.rightLeafRect != null && right == null) return null

	val single = full != null && left == null && gutter == null && right == null
	val spread = full == null && left != null && right != null && left.right <= right.left &&
		(gutter == null || (gutter.left >= left.right && gutter.right <= right.left))
	if (!single && !spread) return null

	return ReaderPageTurnLeafGeometry(
		fullLeafRect = full?.toPixelRect(),
		leftLeafRect = left?.toPixelRect(),
		gutterRect = gutter?.toPixelRect(),
		rightLeafRect = right?.toPixelRect()
	)
}

private fun ReaderPageRasterRect.toPixelRect() = ReaderPageTurnPixelRect(
	left = left,
	top = top,
	right = right,
	bottom = bottom
)
