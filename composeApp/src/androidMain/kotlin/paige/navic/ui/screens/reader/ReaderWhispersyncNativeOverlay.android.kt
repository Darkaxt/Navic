package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRole
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
				pageCount = profile.pageCount,
				spreadAnchorParity = profile.spreadAnchorParity
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
			val spread = readerPlayLikeCurlVisualSpreadWindow(
				currentOrdinal = currentOrdinal,
				pageCount = profile.pageCount,
				spreadAnchorParity = profile.spreadAnchorParity,
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

internal fun readerWhispersyncHighlightMask(
	receipt: ReaderWhispersyncAnchorReceipt,
	target: ReaderWhispersyncNativeOverlayTarget,
	bitmapWidth: Int,
	bitmapHeight: Int,
	colorArgb: Int
): Bitmap? {
	val rects = receipt.maskRectsFor(target.role, bitmapWidth, bitmapHeight)
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
