package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.util.core.Logger

private const val ReaderPlayLikeCurlFoliateRasterSourceTag = "ReaderPlayLikeCurlFoliateRaster"

internal enum class ReaderPlayLikeCurlReaderDirection {
	Ltr,
	Rtl
}

internal enum class ReaderPlayLikeCurlFoliateLeaf {
	Full,
	Left,
	Right
}

internal data class ReaderPlayLikeCurlFoliatePageRequest(
	val logicalOrdinal: Int,
	val sourcePageIndex: Int,
	val leaf: ReaderPlayLikeCurlFoliateLeaf
)

internal data class ReaderPlayLikeCurlRasterImage(
	val bitmap: Bitmap,
	val paperColorArgb: Int
) {
	init {
		require((paperColorArgb ushr 24) == 0xFF) {
			"PlayLikeCurl paper color must be opaque"
		}
	}
}

internal fun readerPlayLikeCurlFoliatePageRequest(
	orientation: ReaderPlayLikeCurlOrientation,
	readerDirection: ReaderPlayLikeCurlReaderDirection,
	logicalOrdinal: Int,
	pageCount: Int,
	spreadAnchorParity: Int = 0
): ReaderPlayLikeCurlFoliatePageRequest {
	require(pageCount > 0) { "Foliate page count must be positive" }
	val boundedOrdinal = logicalOrdinal.coerceIn(0, pageCount - 1)
	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		return ReaderPlayLikeCurlFoliatePageRequest(
			logicalOrdinal = boundedOrdinal,
			sourcePageIndex = boundedOrdinal,
			leaf = ReaderPlayLikeCurlFoliateLeaf.Full
		)
	}

	val slot = readerPlayLikeCurlSpreadSlot(
		logicalOrdinal = boundedOrdinal,
		pageCount = pageCount,
		readerDirection = readerDirection,
		spreadAnchorParity = spreadAnchorParity
	)
	return ReaderPlayLikeCurlFoliatePageRequest(
		logicalOrdinal = boundedOrdinal,
		sourcePageIndex = slot.sourcePageIndex,
		leaf = when (slot.physicalLeaf) {
			ReaderPlayLikeCurlPhysicalLeaf.Left -> ReaderPlayLikeCurlFoliateLeaf.Left
			ReaderPlayLikeCurlPhysicalLeaf.Right -> ReaderPlayLikeCurlFoliateLeaf.Right
		}
	)
}

internal fun readerPlayLikeCurlFoliateLeafRect(
	geometry: ReaderPageTurnLeafGeometry,
	leaf: ReaderPlayLikeCurlFoliateLeaf
): ReaderPageTurnPixelRect? = when (leaf) {
	ReaderPlayLikeCurlFoliateLeaf.Full -> geometry.fullLeafRect
	ReaderPlayLikeCurlFoliateLeaf.Left -> geometry.leftLeafRect
	ReaderPlayLikeCurlFoliateLeaf.Right -> geometry.rightLeafRect
}

/**
 * Converts one retained Foliate snapshot into a PlayLikeCurl-owned immutable page bitmap.
 * The retained snapshot is always released before this function returns.
 */
internal fun readerPlayLikeCurlCopyRetainedFoliateLeaf(
	snapshot: ReaderPageSlideSnapshot,
	leaf: ReaderPlayLikeCurlFoliateLeaf
): ReaderPlayLikeCurlRasterImage? = try {
	val sourceRect = readerPlayLikeCurlFoliateLeafRect(snapshot.leafGeometry, leaf)
		?.takeIf { rect ->
			rect.left >= 0 &&
				rect.top >= 0 &&
				rect.right <= snapshot.bitmap.width &&
				rect.bottom <= snapshot.bitmap.height &&
				rect.width > 0 &&
				rect.height > 0
		}
		?: return null
	if (snapshot.bitmap.isRecycled) return null

	val targetWidth = sourceRect.width
	val targetHeight = sourceRect.height
	val paperColorArgb = snapshot.reverseFaceColor
	val target = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
	target.eraseColor(paperColorArgb)
	Canvas(target).drawBitmap(
		snapshot.bitmap,
		Rect(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom),
		Rect(0, 0, targetWidth, targetHeight),
		Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
	)
	target.setHasAlpha(false)
	ReaderPlayLikeCurlRasterImage(target, paperColorArgb)
} finally {
	snapshot.release()
}

/**
 * Production raster loader. It never captures Foliate: the passive raster scheduler must have
 * prepared the requested page before PlayLikeCurl is allowed to accept a gesture.
 */
internal class ReaderPlayLikeCurlFoliateRasterLoader(
	private val bundleSource: ReaderPageTurnBundleSource,
	private val profile: ReaderPlayLikeCurlRasterProfile,
	private val onMissingRaster: (Int) -> Unit = {}
) : ReaderPlayLikeCurlRasterLoader<ReaderPlayLikeCurlRasterImage> {
	private val transitionKind = when (profile.orientation) {
		ReaderPlayLikeCurlOrientation.Portrait -> ReaderPageTurnTransitionKind.PortraitSlide
		ReaderPlayLikeCurlOrientation.Landscape -> ReaderPageTurnTransitionKind.LandscapeSpreadSlide
	}

	override suspend fun load(key: ReaderPlayLikeCurlRasterKey): ReaderPlayLikeCurlRasterImage? {
		if (key.profile != profile || key.pageIndex !in 0 until profile.pageCount) return null
		val request = readerPlayLikeCurlFoliatePageRequest(
			orientation = profile.orientation,
			readerDirection = profile.readerDirection,
			logicalOrdinal = key.pageIndex,
			pageCount = profile.pageCount,
			spreadAnchorParity = profile.spreadAnchorParity
		)
		val snapshot = withContext(Dispatchers.Main.immediate) {
			bundleSource.retainedSnapshot(request.sourcePageIndex, transitionKind).also { retained ->
				if (retained == null) {
					onMissingRaster(request.sourcePageIndex)
					Logger.w(
						ReaderPlayLikeCurlFoliateRasterSourceTag,
						"Missing Foliate raster logical=${request.logicalOrdinal} " +
							"source=${request.sourcePageIndex} leaf=${request.leaf} " +
							"profileGeneration=${profile.rasterGeneration} " +
							"activeGeneration=${bundleSource.currentGeneration()} " +
							"cached=${bundleSource.cachedSnapshotPageIndices(transitionKind)}"
					)
				}
			}
		} ?: return null
		return withContext(Dispatchers.Default) {
			readerPlayLikeCurlCopyRetainedFoliateLeaf(snapshot, request.leaf)
		}
	}
}
