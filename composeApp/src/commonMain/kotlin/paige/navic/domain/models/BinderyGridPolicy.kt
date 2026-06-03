package paige.navic.domain.models

import kotlin.math.roundToInt

const val BinderyDefaultBookGridColumns = 5
const val BinderyMinBookGridColumns = 5
const val BinderyMaxBookGridColumns = 8
private const val BinderyCarouselHorizontalPaddingDp = 32
private const val BinderyCarouselItemSpacingDp = 12
private const val BinderyCarouselMinCardWidthDp = 96

fun normalizedBinderyBookGridColumns(columns: Int): Int =
	columns.coerceIn(BinderyMinBookGridColumns, BinderyMaxBookGridColumns)

fun binderyCarouselCardWidthDp(columns: Int): Int =
	when (normalizedBinderyBookGridColumns(columns)) {
		5 -> 180
		6 -> 168
		7 -> 156
		else -> 150
	}

fun binderyCarouselCardWidthDp(
	columns: Int,
	availableWidthDp: Int
): Int {
	val visibleColumns = normalizedBinderyBookGridColumns(columns)
	val spacing = BinderyCarouselItemSpacingDp * (visibleColumns - 1)
	val availableForCards = availableWidthDp - BinderyCarouselHorizontalPaddingDp - spacing
	return (availableForCards / visibleColumns.toFloat())
		.roundToInt()
		.coerceAtLeast(BinderyCarouselMinCardWidthDp)
}
