package paige.navic.domain.models

const val BinderyDefaultBookGridColumns = 5
const val BinderyMinBookGridColumns = 5
const val BinderyMaxBookGridColumns = 8
private const val BinderyCarouselCardWidthDp = 150

fun normalizedBinderyBookGridColumns(columns: Int): Int =
	columns.coerceIn(BinderyMinBookGridColumns, BinderyMaxBookGridColumns)

@Suppress("UNUSED_PARAMETER")
fun binderyCarouselCardWidthDp(columns: Int): Int =
	BinderyCarouselCardWidthDp

@Suppress("UNUSED_PARAMETER")
fun binderyCarouselCardWidthDp(
	columns: Int,
	availableWidthDp: Int
): Int = BinderyCarouselCardWidthDp
