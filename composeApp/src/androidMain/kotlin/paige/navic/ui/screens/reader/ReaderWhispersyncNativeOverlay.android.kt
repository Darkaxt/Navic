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

internal fun readerWhispersyncViewportHighlightMask(
	receipt: ReaderWhispersyncAnchorReceipt,
	bitmapWidth: Int,
	bitmapHeight: Int,
	colorArgb: Int
): Bitmap? {
	val geometry = receipt.captureGeometry
	if (
		bitmapWidth <= 0 ||
		bitmapHeight <= 0 ||
		!geometry.viewportWidth.isFinite() ||
		geometry.viewportWidth <= 0.0 ||
		!geometry.viewportHeight.isFinite() ||
		geometry.viewportHeight <= 0.0
	) return null
	val scaleX = bitmapWidth.toDouble() / geometry.viewportWidth
	val scaleY = bitmapHeight.toDouble() / geometry.viewportHeight
	val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply {
		setHasAlpha(true)
		setPremultiplied(true)
	}
	val paint = Paint().apply {
		color = colorArgb
		style = Paint.Style.FILL
		isAntiAlias = false
	}
	val canvas = Canvas(bitmap)
	var drewRect = false
	receipt.pageLocalRects.forEach { rect ->
		val page = geometry.pages.firstOrNull { page -> page.role == rect.role }
			?: return@forEach
		val left = ((page.left + rect.left) * scaleX).coerceIn(0.0, bitmapWidth.toDouble())
		val top = ((page.top + rect.top) * scaleY).coerceIn(0.0, bitmapHeight.toDouble())
		val right = ((page.left + rect.left + rect.width) * scaleX)
			.coerceIn(0.0, bitmapWidth.toDouble())
		val bottom = ((page.top + rect.top + rect.height) * scaleY)
			.coerceIn(0.0, bitmapHeight.toDouble())
		if (right <= left || bottom <= top) return@forEach
		canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
		drewRect = true
	}
	return bitmap.takeIf { drewRect } ?: run {
		bitmap.recycle()
		null
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
