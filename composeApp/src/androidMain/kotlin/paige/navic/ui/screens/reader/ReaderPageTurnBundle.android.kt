package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Rect
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageBitmapQuality

internal enum class ReaderPageTurnTransitionKind {
	LandscapeSpreadSlide,
	PortraitSlide
}

internal data class ReaderPageSlideSnapshotKey(
	val visualPageIndex: Int,
	val kind: ReaderPageTurnTransitionKind,
	val bitmapQuality: ReaderPageBitmapQuality,
	val bitmapWidth: Int,
	val bitmapHeight: Int,
	val surfaceWidth: Int,
	val surfaceHeight: Int
)

internal class ReaderPageSlideSnapshot(
	val key: ReaderPageSlideSnapshotKey,
	val bitmap: Bitmap,
	val surfaceRectInWindow: Rect,
	val leafGeometry: ReaderPageTurnLeafGeometry,
	val reverseFaceColor: Int,
	val captureMillis: Long = 0L
) {
	private var cacheOwned = true
	private var retainCount = 0
	private var recycled = false

	@Synchronized
	fun retain() {
		check(!recycled) { "Cannot retain a recycled page snapshot" }
		retainCount += 1
	}

	@Synchronized
	fun release() {
		check(retainCount > 0) { "Page snapshot released without a matching retain" }
		retainCount -= 1
		recycleIfUnowned()
	}

	@Synchronized
	fun releaseCacheOwnership() {
		if (!cacheOwned) return
		cacheOwned = false
		recycleIfUnowned()
	}

	private fun recycleIfUnowned() {
		if (cacheOwned || retainCount > 0 || recycled) return
		recycled = true
		if (!bitmap.isRecycled) bitmap.recycle()
	}
}
