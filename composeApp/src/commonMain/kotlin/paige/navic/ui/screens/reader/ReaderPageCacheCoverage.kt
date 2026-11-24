package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import paige.navic.ui.navigation.Screen

/**
 * Page previews already on disk for a book: [cachedPages] distinct pages of its most recently used layout,
 * out of [totalPages] (0 when nothing is cached yet).
 */
data class ReaderPageCacheCoverage(
	val cachedPages: Int,
	val totalPages: Int
) {
	val percent: Int
		get() = if (totalPages <= 0) 0 else ((cachedPages * 100.0) / totalPages).toInt().coerceIn(0, 100)
}

/** Reads the coverage off the main thread; null while loading or where the platform has no page cache. */
@Composable
expect fun rememberReaderPageCacheCoverage(destinations: List<Screen.Reader>): ReaderPageCacheCoverage?
