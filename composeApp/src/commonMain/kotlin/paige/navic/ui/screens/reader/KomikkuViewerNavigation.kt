package paige.navic.ui.screens.reader

// Ported from Komikku:
// tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt
// tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/navigation/*.kt

data class KomikkuPoint(
	val x: Float,
	val y: Float
)

data class KomikkuRectF(
	val left: Float,
	val top: Float,
	val right: Float,
	val bottom: Float
) {
	fun contains(x: Float, y: Float): Boolean =
		x >= left && x <= right && y >= top && y <= bottom

	fun invert(invertMode: KomikkuTappingInvertMode): KomikkuRectF {
		val horizontal = invertMode.shouldInvertHorizontal
		val vertical = invertMode.shouldInvertVertical
		return when {
			horizontal && vertical -> KomikkuRectF(1f - right, 1f - bottom, 1f - left, 1f - top)
			vertical -> KomikkuRectF(left, 1f - bottom, right, 1f - top)
			horizontal -> KomikkuRectF(1f - right, top, 1f - left, bottom)
			else -> this
		}
	}
}

enum class KomikkuTappingInvertMode(
	val shouldInvertHorizontal: Boolean = false,
	val shouldInvertVertical: Boolean = false
) {
	NONE,
	HORIZONTAL(shouldInvertHorizontal = true),
	VERTICAL(shouldInvertVertical = true),
	BOTH(shouldInvertHorizontal = true, shouldInvertVertical = true)
}

enum class KomikkuNavigationRegion(
	val label: String,
	val colorArgb: ULong
) {
	MENU("Menu", 0xCC95818Du),
	PREV("Previous", 0xCCFF7733u),
	NEXT("Next", 0xCC84E296u),
	LEFT("Left", 0xCC7D1128u),
	RIGHT("Right", 0xCCA6CFD5u)
}

data class KomikkuNavigationRect(
	val rectF: KomikkuRectF,
	val type: KomikkuNavigationRegion
) {
	fun invert(invertMode: KomikkuTappingInvertMode): KomikkuNavigationRect =
		if (invertMode == KomikkuTappingInvertMode.NONE) {
			this
		} else {
			copy(rectF = rectF.invert(invertMode))
		}
}

abstract class KomikkuViewerNavigation(
	private val smallerTapZone: Boolean = false
) {
	private val constantMenuRegion = KomikkuRectF(0f, 0f, 1f, 0.05f)

	var invertMode: KomikkuTappingInvertMode = KomikkuTappingInvertMode.NONE

	protected val regionSize1: Float
		get() = if (smallerTapZone) 0.25f else 0.33f
	protected val regionSize2: Float
		get() = 1f - regionSize1

	protected abstract val regionList: List<KomikkuNavigationRect>

	fun getRegions(): List<KomikkuNavigationRect> =
		regionList.map { it.invert(invertMode) }

	fun getAction(pos: KomikkuPoint): KomikkuNavigationRegion {
		val x = pos.x.coerceIn(0f, 1f)
		val y = pos.y.coerceIn(0f, 1f)
		val region = getRegions().firstOrNull { it.rectF.contains(x, y) }
		return when {
			region != null -> region.type
			constantMenuRegion.contains(x, y) -> KomikkuNavigationRegion.MENU
			else -> KomikkuNavigationRegion.MENU
		}
	}
}

/**
 * Visualization of default state without inversion:
 * +---+---+---+
 * | P | P | P |
 * +---+---+---+
 * | P | M | N |
 * +---+---+---+
 * | N | N | N |
 * +---+---+---+
 */
open class KomikkuLNavigation(smallerTapZone: Boolean = false) : KomikkuViewerNavigation(smallerTapZone) {
	override val regionList: List<KomikkuNavigationRect> = listOf(
		KomikkuNavigationRect(
			rectF = KomikkuRectF(0f, regionSize1, regionSize1, regionSize2),
			type = KomikkuNavigationRegion.PREV
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(0f, 0f, 1f, regionSize1),
			type = KomikkuNavigationRegion.PREV
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(regionSize2, regionSize1, 1f, regionSize2),
			type = KomikkuNavigationRegion.NEXT
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(0f, regionSize2, 1f, 1f),
			type = KomikkuNavigationRegion.NEXT
		)
	)
}

/**
 * Komikku's "Kindle-ish" navigation preset.
 */
class KomikkuKindlishNavigation(smallerTapZone: Boolean = false) : KomikkuViewerNavigation(smallerTapZone) {
	override val regionList: List<KomikkuNavigationRect> = listOf(
		KomikkuNavigationRect(
			rectF = KomikkuRectF(regionSize1, regionSize1, 1f, 1f),
			type = KomikkuNavigationRegion.NEXT
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(0f, regionSize1, regionSize1, 1f),
			type = KomikkuNavigationRegion.PREV
		)
	)
}

class KomikkuEdgeNavigation(smallerTapZone: Boolean = false) : KomikkuViewerNavigation(smallerTapZone) {
	override val regionList: List<KomikkuNavigationRect> = listOf(
		KomikkuNavigationRect(
			rectF = KomikkuRectF(0f, 0f, regionSize1, 1f),
			type = KomikkuNavigationRegion.NEXT
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(regionSize1, regionSize2, regionSize2, 1f),
			type = KomikkuNavigationRegion.PREV
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(regionSize2, 0f, 1f, 1f),
			type = KomikkuNavigationRegion.NEXT
		)
	)
}

class KomikkuRightAndLeftNavigation(smallerTapZone: Boolean = false) : KomikkuViewerNavigation(smallerTapZone) {
	override val regionList: List<KomikkuNavigationRect> = listOf(
		KomikkuNavigationRect(
			rectF = KomikkuRectF(0f, 0f, regionSize1, 1f),
			type = KomikkuNavigationRegion.LEFT
		),
		KomikkuNavigationRect(
			rectF = KomikkuRectF(regionSize2, 0f, 1f, 1f),
			type = KomikkuNavigationRegion.RIGHT
		)
	)
}

class KomikkuDisabledNavigation(smallerTapZone: Boolean = false) : KomikkuViewerNavigation(smallerTapZone) {
	override val regionList: List<KomikkuNavigationRect> = emptyList()
}

class KomikkuReaderNavigator(
	private val navigation: KomikkuViewerNavigation
) {
	fun getAction(point: KomikkuPoint): KomikkuNavigationRegion =
		navigation.getAction(point)

	fun getRegions(): List<KomikkuNavigationRect> =
		navigation.getRegions()
}
