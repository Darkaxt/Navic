package paige.navic.ui.components.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Book covers drawn as a box: the cover on the front, the page block on the right side and top.
 * Values agreed in the 3D book lab (2026-11-27). Lengths are lab pixels for a 150 px card and
 * scale with the card width.
 */
object BookCover3DStyle {
	const val LabCardWidth = 150f
	const val SideAngleDeg = 22f
	const val TopAngleDeg = 0f
	const val PerspectivePx = 900f
	const val ThicknessPer100PagesPct = 6f
	const val MinThicknessPct = 6f
	const val MaxThicknessPct = 60f
	const val TopCoverLinePx = 4.5f
	const val SideCoverLinePx = 1.5f
	const val PageLinePx = 1f
	const val PageWarmth = 0.45f
	const val SideBrightness = 0.86f
	const val TopBrightness = 1f
	const val HingeShade = 0.35f
	const val Gloss = 0.10f
	const val CornerRadiusPx = 1.5f

	/** Front cover width as a share of the card; the book also fits within [MaxCoverHeightShare]. */
	const val CoverWidthShare = 0.8f
	const val MaxCoverHeightShare = 1.24f

	/** Card slot: width / height. Leaves room above a 2:3 cover for the perspective. */
	const val SlotAspectRatio = 1f / 1.3f

	/** Page count used until Bindery reports one. */
	const val FallbackPages = 300

	/** Wider covers (audiobook squares) stay flat. */
	const val MaxBookAspectRatio = 0.85f
}

/** Thickness of the page block as a fraction of the cover width. */
fun bookThicknessFraction(pages: Int?): Float {
	val count = pages?.takeIf { it > 0 } ?: BookCover3DStyle.FallbackPages
	val pct = (BookCover3DStyle.ThicknessPer100PagesPct * count / 100f)
		.coerceIn(BookCover3DStyle.MinThicknessPct, BookCover3DStyle.MaxThicknessPct)
	return pct / 100f
}

/** A face as four screen points: texture top-left, top-right, bottom-right, bottom-left. */
data class BookFaceQuad(val topLeft: Offset, val topRight: Offset, val bottomRight: Offset, val bottomLeft: Offset) {
	/** Faces wound clockwise on screen look at the viewer. */
	val facesViewer: Boolean
		get() {
			val ax = topRight.x - topLeft.x
			val ay = topRight.y - topLeft.y
			val bx = bottomRight.x - topRight.x
			val by = bottomRight.y - topRight.y
			return ax * by - ay * bx > 0f
		}
}

data class BookCoverGeometry(
	/** Unprojected front cover size, laid out at the slot's top-left. */
	val coverSize: Size,
	val thickness: Float,
	/** Lab pixel -> device pixel. */
	val scale: Float,
	val front: BookFaceQuad,
	val right: BookFaceQuad?,
	val top: BookFaceQuad?
)

/**
 * Projects the book into a slot of [slot] size, exactly like the lab's CSS 3D: the book stands on
 * the slot's bottom edge, rotated about its centre, seen from the slot centre at the lab perspective.
 */
fun bookCoverGeometry(slot: Size, coverAspectRatio: Float, pages: Int?): BookCoverGeometry {
	val s = slot.width / BookCover3DStyle.LabCardWidth
	val isBook = coverAspectRatio <= BookCover3DStyle.MaxBookAspectRatio
	val widthShare = if (isBook) BookCover3DStyle.CoverWidthShare else 1f
	var w = slot.width * widthShare
	var h = w / coverAspectRatio
	val maxH = min(slot.height, slot.width * BookCover3DStyle.MaxCoverHeightShare)
	if (h > maxH) {
		h = maxH
		w = h * coverAspectRatio
	}
	val d = if (isBook) w * bookThicknessFraction(pages) else 0f
	val yaw = if (isBook) -BookCover3DStyle.SideAngleDeg * PI.toFloat() / 180f else 0f
	val pitch = if (isBook) -BookCover3DStyle.TopAngleDeg * PI.toFloat() / 180f else 0f
	val perspective = BookCover3DStyle.PerspectivePx * s
	val centre = Offset(slot.width / 2f, slot.height - h / 2f)
	// The lab stage is 1.62 card widths tall with the book on its floor; its centre is the eye.
	val eye = Offset(slot.width / 2f, slot.height - slot.width * 0.81f)
	fun project(x: Float, y: Float, z: Float): Offset {
		// rotateX(pitch) rotateY(yaw): yaw applies first.
		val x1 = x * cos(yaw) + z * sin(yaw)
		val z1 = -x * sin(yaw) + z * cos(yaw)
		val y2 = y * cos(pitch) - z1 * sin(pitch)
		val z2 = y * sin(pitch) + z1 * cos(pitch)
		val k = if (isBook) perspective / (perspective - z2) else 1f
		val px = centre.x + x1
		val py = centre.y + y2
		return Offset(eye.x + (px - eye.x) * k, eye.y + (py - eye.y) * k)
	}
	val hw = w / 2f
	val hh = h / 2f
	val hd = d / 2f
	val front = BookFaceQuad(project(-hw, -hh, hd), project(hw, -hh, hd), project(hw, hh, hd), project(-hw, hh, hd))
	val right = BookFaceQuad(project(hw, -hh, hd), project(hw, -hh, -hd), project(hw, hh, -hd), project(hw, hh, hd))
		.takeIf { d > 0f && it.facesViewer }
	val top = BookFaceQuad(project(-hw, -hh, -hd), project(hw, -hh, -hd), project(hw, -hh, hd), project(-hw, -hh, hd))
		.takeIf { d > 0f && it.facesViewer }
	// The near corner projects below the floor: stand the drawn book on the slot's bottom edge,
	// centred, shrinking it only if it would not fit.
	val points = listOfNotNull(front, right, top).flatMap { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
	val minX = points.minOf { it.x }
	val maxX = points.maxOf { it.x }
	val minY = points.minOf { it.y }
	val maxY = points.maxOf { it.y }
	val fit = min(1f, min(slot.width / (maxX - minX), slot.height / (maxY - minY)))
	val anchor = Offset((minX + maxX) / 2f, maxY)
	val target = Offset(slot.width / 2f, slot.height)
	fun place(p: Offset) = target + (p - anchor) * fit
	fun BookFaceQuad.placed() = BookFaceQuad(place(topLeft), place(topRight), place(bottomRight), place(bottomLeft))
	return BookCoverGeometry(
		coverSize = Size(w, h),
		thickness = d,
		scale = s,
		front = front.placed(),
		right = right?.placed(),
		top = top?.placed()
	)
}

/** Homography taking the texture rectangle (0,0)-([texture]) onto [quad]. */
fun bookFaceMatrix(texture: Size, quad: BookFaceQuad): Matrix {
	val (x0, y0) = quad.topLeft
	val (x1, y1) = quad.topRight
	val (x2, y2) = quad.bottomRight
	val (x3, y3) = quad.bottomLeft
	val dx3 = x0 - x1 + x2 - x3
	val dy3 = y0 - y1 + y2 - y3
	var g = 0f
	var h = 0f
	if (abs(dx3) > 1e-4f || abs(dy3) > 1e-4f) {
		val dx1 = x1 - x2
		val dx2 = x3 - x2
		val dy1 = y1 - y2
		val dy2 = y3 - y2
		val den = dx1 * dy2 - dx2 * dy1
		g = (dx3 * dy2 - dx2 * dy3) / den
		h = (dx1 * dy3 - dx3 * dy1) / den
	}
	val a = x1 - x0 + g * x1
	val b = x3 - x0 + h * x3
	val d = y1 - y0 + g * y1
	val e = y3 - y0 + h * y3
	return Matrix().apply {
		values[Matrix.ScaleX] = a / texture.width
		values[Matrix.SkewX] = b / texture.height
		values[Matrix.TranslateX] = x0
		values[Matrix.SkewY] = d / texture.width
		values[Matrix.ScaleY] = e / texture.height
		values[Matrix.TranslateY] = y0
		values[Matrix.Perspective0] = g / texture.width
		values[Matrix.Perspective1] = h / texture.height
		values[Matrix.Perspective2] = 1f
	}
}

private val NeutralBoard = Color(0xFF3A3A3A)

private fun paperColour(): Color {
	val w = BookCover3DStyle.PageWarmth
	return Color((236 - 8 * w) / 255f, (232 - 14 * w) / 255f, (222 - 40 * w) / 255f)
}

/**
 * Draws the content (laid out at the slot's top-left with [BookCoverGeometry.coverSize]) as the
 * front of a book, plus the page block on the right side and top. [image] supplies the cover's own
 * edge colours for the thin cover lines; without it they are neutral.
 */
fun Modifier.bookCover3D(geometry: () -> BookCoverGeometry?, image: () -> ImageBitmap?): Modifier =
	drawWithContent {
		val g = geometry()
		if (g == null) {
			drawContent()
			return@drawWithContent
		}
		val cover = image()
		if (g.thickness > 0f) drawBookShadow(g)
		g.right?.let { drawPageBlock(it, g, cover, isTop = false) }
		g.top?.let { drawPageBlock(it, g, cover, isTop = true) }
		withTransform({ transform(bookFaceMatrix(g.coverSize, g.front)) }) {
			this@drawWithContent.drawContent()
			if (g.thickness > 0f) {
				val hinge = 5f * g.scale
				drawRect(
					brush = Brush.horizontalGradient(
						0f to Color.Black.copy(alpha = BookCover3DStyle.HingeShade),
						1f to Color.Transparent,
						endX = hinge
					),
					size = Size(hinge, g.coverSize.height)
				)
				drawRect(
					brush = Brush.linearGradient(
						0f to Color.White.copy(alpha = BookCover3DStyle.Gloss),
						0.4f to Color.Transparent,
						start = Offset.Zero,
						end = Offset(g.coverSize.width, g.coverSize.height * 0.47f)
					),
					size = g.coverSize
				)
			}
		}
	}

private fun DrawScope.drawBookShadow(g: BookCoverGeometry) {
	val left = min(g.front.bottomLeft.x, g.front.topLeft.x)
	val right = max(g.right?.bottomRight?.x ?: g.front.bottomRight.x, g.front.bottomRight.x)
	val y = max(g.front.bottomLeft.y, g.front.bottomRight.y)
	val width = right - left
	val height = 16f * g.scale
	withTransform({ scale(1f, height / width, pivot = Offset(left + width / 2f, y)) }) {
		drawCircle(
			brush = Brush.radialGradient(
				0f to Color.Black.copy(alpha = 0.55f),
				0.7f to Color.Transparent,
				center = Offset(left + width / 2f, y),
				radius = width / 2f
			),
			radius = width / 2f,
			center = Offset(left + width / 2f, y)
		)
	}
}

/**
 * Page block between two cover boards. Texture space (4x supersampled lab pixels):
 * side face: x runs front -> back, lines vertical; top face: y runs back -> front, lines horizontal,
 * with the cover wrapping over the spine edge on the left.
 */
private fun DrawScope.drawPageBlock(quad: BookFaceQuad, g: BookCoverGeometry, cover: ImageBitmap?, isTop: Boolean) {
	val unit = 4f // texture pixels per lab pixel
	val labThickness = g.thickness / g.scale
	val labLength = (if (isTop) g.coverSize.width else g.coverSize.height) / g.scale
	val texture = if (isTop) Size(labLength * unit, labThickness * unit) else Size(labThickness * unit, labLength * unit)
	val board = (if (isTop) BookCover3DStyle.TopCoverLinePx else BookCover3DStyle.SideCoverLinePx) * unit
	val brightness = if (isTop) BookCover3DStyle.TopBrightness else BookCover3DStyle.SideBrightness
	withTransform({ transform(bookFaceMatrix(texture, quad)) }) {
		val paper = paperColour()
		drawRect(paper, size = texture)
		val shade = paper.copy(red = paper.red * 0.84f, green = paper.green * 0.84f, blue = paper.blue * 0.84f)
		val step = BookCover3DStyle.PageLinePx * unit
		val across = if (isTop) texture.height else texture.width
		var p = step * 0.6f
		while (p < across) {
			if (isTop) drawRect(shade, topLeft = Offset(0f, p), size = Size(texture.width, step * 0.4f))
			else drawRect(shade, topLeft = Offset(p, 0f), size = Size(step * 0.4f, texture.height))
			p += step
		}
		// Darker towards the back board so the block reads as depth.
		drawRect(
			brush = if (isTop) {
				Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.7f to Color.Transparent, endY = texture.height)
			} else {
				Brush.horizontalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f), endX = texture.width)
			},
			size = texture
		)
		if (isTop) {
			drawCoverLine(cover, CoverEdge.Top, Offset(0f, 0f), Size(texture.width, board), shade = 0.15f)
			drawCoverLine(cover, CoverEdge.Top, Offset(0f, texture.height - board), Size(texture.width, board), shade = 0f)
			drawCoverLine(cover, CoverEdge.Left, Offset(0f, 0f), Size(board, texture.height), shade = 0f)
		} else {
			drawCoverLine(cover, CoverEdge.Right, Offset(0f, 0f), Size(board, texture.height), shade = 0f)
			drawCoverLine(cover, CoverEdge.Right, Offset(texture.width - board, 0f), Size(board, texture.height), shade = 0.15f)
		}
		if (brightness < 1f) drawRect(Color.Black.copy(alpha = 1f - brightness), size = texture)
	}
}

private enum class CoverEdge { Top, Left, Right }

/** A thin cover line using the cover's own edge pixels, stretched along the line. */
private fun DrawScope.drawCoverLine(cover: ImageBitmap?, edge: CoverEdge, topLeft: Offset, size: Size, shade: Float) {
	if (size.width <= 0f || size.height <= 0f) return
	if (cover == null || cover.width < 4 || cover.height < 4) {
		drawRect(NeutralBoard, topLeft = topLeft, size = size)
	} else {
		val edgePx = max(1, min(cover.width, cover.height) / 60)
		val (srcOffset, srcSize) = when (edge) {
			CoverEdge.Top -> IntOffset(0, 0) to IntSize(cover.width, edgePx)
			CoverEdge.Left -> IntOffset(0, 0) to IntSize(edgePx, cover.height)
			CoverEdge.Right -> IntOffset(cover.width - edgePx, 0) to IntSize(edgePx, cover.height)
		}
		// drawImage takes integer destinations; draw at 1/16 resolution steps through a scale.
		val k = 16f
		withTransform({ scale(1f / k, 1f / k, pivot = Offset.Zero) }) {
			drawImage(
				image = cover,
				srcOffset = srcOffset,
				srcSize = srcSize,
				dstOffset = IntOffset((topLeft.x * k).roundToInt(), (topLeft.y * k).roundToInt()),
				dstSize = IntSize((size.width * k).roundToInt().coerceAtLeast(1), (size.height * k).roundToInt().coerceAtLeast(1))
			)
		}
	}
	if (shade > 0f) drawRect(Color.Black.copy(alpha = shade), topLeft = topLeft, size = size)
}
