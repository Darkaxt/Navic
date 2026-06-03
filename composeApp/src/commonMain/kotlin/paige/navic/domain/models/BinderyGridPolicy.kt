package paige.navic.domain.models

const val BinderyDefaultBookGridColumns = 5
const val BinderyMinBookGridColumns = 5
const val BinderyMaxBookGridColumns = 8

fun normalizedBinderyBookGridColumns(columns: Int): Int =
	columns.coerceIn(BinderyMinBookGridColumns, BinderyMaxBookGridColumns)

fun binderyCarouselCardWidthDp(columns: Int): Int =
	when (normalizedBinderyBookGridColumns(columns)) {
		5 -> 180
		6 -> 168
		7 -> 156
		else -> 150
	}
