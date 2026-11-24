package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderWhispersyncAnchorReceipt

internal data class ReaderWhispersyncNativePresentationProof(
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
	val captureGeometry: ReaderPageTurnCaptureGeometry
)

internal fun ReaderWhispersyncAnchorReceipt.nativePresentationProof() =
	ReaderWhispersyncNativePresentationProof(
		foliateSessionId = foliateSessionId,
		destinationCommitToken = destinationCommitToken,
		visualPageOrdinal = visualPageOrdinal,
		spineIndex = spineIndex,
		rasterGeneration = rasterGeneration,
		textureGeneration = textureGeneration,
		presentationMutationGeneration = presentationMutationGeneration,
		presentationSequence = presentationSequence,
		anchorGeneration = anchorGeneration,
		boundarySequence = boundarySequence,
		layoutGeneration = layoutGeneration,
		viewGeneration = viewGeneration,
		commitSequence = commitSequence,
		committedSpineIndex = committedSpineIndex,
		committedChapterPageIndex = committedChapterPageIndex,
		committedChapterPageCount = committedChapterPageCount,
		paginationFingerprint = paginationFingerprint,
		layoutFingerprint = layoutFingerprint,
		readerSettingsRasterKey = readerSettingsRasterKey,
		captureGeometry = captureGeometry
	)

internal fun ReaderWhispersyncNativePresentationProof.hasSameDestinationPresentation(
	other: ReaderWhispersyncNativePresentationProof
): Boolean = copy(
	anchorGeneration = other.anchorGeneration,
	boundarySequence = other.boundarySequence
) == other

internal fun ReaderWhispersyncNativePresentationProof.isAdmissibleReplacementFor(
	current: ReaderWhispersyncNativePresentationProof
): Boolean {
	if (
		foliateSessionId != current.foliateSessionId ||
		textureGeneration != current.textureGeneration
	) return false
	if (hasSameDestinationPresentation(current)) {
		return anchorGeneration > current.anchorGeneration ||
			(
				anchorGeneration == current.anchorGeneration &&
					boundarySequence >= current.boundarySequence
			)
	}
	return presentationMutationGeneration > current.presentationMutationGeneration ||
		(
			presentationMutationGeneration == current.presentationMutationGeneration &&
				presentationSequence > current.presentationSequence
		)
}

internal data class ReaderWhispersyncNativeOverlayTarget(
	val role: ReaderPageTurnPageRole,
	val logicalOrdinal: Int
)

internal fun readerWhispersyncNativeOverlayTargets(
	receipt: ReaderWhispersyncAnchorReceipt,
	presentationProof: ReaderWhispersyncNativePresentationProof?,
	foliateSessionId: String,
	profile: ReaderPlayLikeCurlRasterProfile,
	currentOrdinal: Int,
	textureGeneration: Long
): List<ReaderWhispersyncNativeOverlayTarget>? {
	if (
		receipt.nativePresentationProof() != presentationProof ||
		presentationProof.foliateSessionId != foliateSessionId ||
		presentationProof.rasterGeneration != profile.rasterGeneration ||
		presentationProof.textureGeneration != textureGeneration
	) return null

	val roles = receipt.pageLocalRects.mapTo(linkedSetOf()) { rect -> rect.role }
	return when (profile.orientation) {
		ReaderPlayLikeCurlOrientation.Portrait -> {
			val request = readerPlayLikeCurlFoliatePageRequest(
				orientation = profile.orientation,
				readerDirection = profile.readerDirection,
				logicalOrdinal = currentOrdinal,
				pageCount = profile.pageCount
			)
			if (
				receipt.captureGeometry.mode != ReaderPageTurnLayoutMode.Single ||
				receipt.visualPageOrdinal != request.sourcePageIndex ||
				roles != setOf(ReaderPageTurnPageRole.Full)
			) null else listOf(
				ReaderWhispersyncNativeOverlayTarget(
					role = ReaderPageTurnPageRole.Full,
					logicalOrdinal = currentOrdinal
				)
			)
		}

		ReaderPlayLikeCurlOrientation.Landscape -> {
			// currentOrdinal and visualPageOrdinal are Foliate spread ordinals; targets are the
			// spread's leaf ordinals, matching the curl deck's current left/right pages.
			val spread = readerPlayLikeCurlVisualSpreadWindow(
				currentOrdinal = currentOrdinal,
				pageCount = profile.pageCount,
				readerDirection = profile.readerDirection
			).current
			if (
				receipt.captureGeometry.mode != ReaderPageTurnLayoutMode.Spread ||
				receipt.visualPageOrdinal != spread.sourcePageIndex ||
				roles.any { role -> role == ReaderPageTurnPageRole.Full }
			) return null
			roles.mapNotNull { role ->
				val ordinal = when (role) {
					ReaderPageTurnPageRole.Left -> spread.physicalLeftOrdinal
					ReaderPageTurnPageRole.Right -> spread.physicalRightOrdinal
					ReaderPageTurnPageRole.Full -> null
				} ?: return null
				ReaderWhispersyncNativeOverlayTarget(role, ordinal)
			}
		}
	}
}

internal fun readerWhispersyncViewportHighlightMask(
	receipt: ReaderWhispersyncAnchorReceipt,
	bitmapWidth: Int,
	bitmapHeight: Int,
	colorArgb: Int
): Bitmap? = readerWhispersyncMaskBitmap(
	rects = receipt.surfaceMaskRects(bitmapWidth, bitmapHeight),
	bitmapWidth = bitmapWidth,
	bitmapHeight = bitmapHeight,
	colorArgb = colorArgb
)

/** Reading order of the first sliver of the current word; 0 is reserved for already-read text. */
internal const val ReaderWhispersyncFlowOrderStart = 0.05f

/** The renderer's flow head for [progress] through the current word (see PageRenderer). */
internal fun readerWhispersyncFlowHead(progress: Float): Float =
	0.01f + 0.95f * progress.coerceIn(0f, 1f)

/**
 * The flow-encoded page mask: opaque alpha marks highlighted text, red over alpha its reading
 * order. Already-read text is order 0 (always shown); the current word ramps from
 * [ReaderWhispersyncFlowOrderStart] to 1 left to right, so the renderer's head uniform sweeps
 * it in over the word's audio time. The highlight colour comes from the renderer uniform.
 */
internal fun readerWhispersyncHighlightMask(
	receipt: ReaderWhispersyncAnchorReceipt,
	target: ReaderWhispersyncNativeOverlayTarget,
	bitmapWidth: Int,
	bitmapHeight: Int
): Bitmap? {
	val bitmap = readerWhispersyncMaskBitmap(
		rects = receipt.maskRectsFor(target.role, bitmapWidth, bitmapHeight),
		bitmapWidth = bitmapWidth,
		bitmapHeight = bitmapHeight,
		colorArgb = ReaderWhispersyncFlowReadColorArgb
	) ?: return null
	val heads = receipt.headMaskSpansFor(target.role, bitmapWidth, bitmapHeight)
	if (heads.isEmpty()) return bitmap
	val canvas = Canvas(bitmap)
	val paint = Paint().apply {
		style = Paint.Style.FILL
		isAntiAlias = false
		xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
	}
	heads.forEach { head ->
		val rect = head.rect
		paint.shader = LinearGradient(
			rect.left.toFloat(),
			0f,
			rect.right.toFloat(),
			0f,
			readerWhispersyncFlowOrderColor(head.startFraction),
			readerWhispersyncFlowOrderColor(head.endFraction),
			Shader.TileMode.CLAMP
		)
		canvas.drawRect(
			rect.left.toFloat(),
			rect.top.toFloat(),
			rect.right.toFloat(),
			rect.bottom.toFloat(),
			paint
		)
	}
	return bitmap
}

private const val ReaderWhispersyncFlowReadColorArgb = 0xFF000000.toInt()

private fun readerWhispersyncFlowOrderColor(fraction: Double): Int {
	val order = ReaderWhispersyncFlowOrderStart +
		(1f - ReaderWhispersyncFlowOrderStart) * fraction.toFloat().coerceIn(0f, 1f)
	return (0xFF shl 24) or ((order * 255f).toInt().coerceIn(1, 255) shl 16)
}

private fun readerWhispersyncMaskBitmap(
	rects: List<ReaderPageTurnPixelRect>,
	bitmapWidth: Int,
	bitmapHeight: Int,
	colorArgb: Int
): Bitmap? {
	if (rects.isEmpty()) return null
	return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
		bitmap.setHasAlpha(true)
		bitmap.setPremultiplied(true)
		val paint = Paint().apply {
			color = colorArgb
			style = Paint.Style.FILL
			isAntiAlias = false
		}
		val canvas = Canvas(bitmap)
		rects.forEach { rect ->
			canvas.drawRect(
				rect.left.toFloat(),
				rect.top.toFloat(),
				rect.right.toFloat(),
				rect.bottom.toFloat(),
				paint
			)
		}
	}
}
