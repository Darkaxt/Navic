package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import paige.navic.ui.navigation.Screen

/**
 * Page previews already on disk for a book: [cachedPages] distinct previews of its most recently used
 * layout, out of the [totalPages] previews that layout needs (one per spread in two-page layouts; 0 when
 * nothing is cached yet).
 */
data class ReaderPageCacheCoverage(
	val cachedPages: Int,
	val totalPages: Int
) {
	val percent: Int
		get() = if (totalPages <= 0) 0 else ((cachedPages * 100.0) / totalPages).toInt().coerceIn(0, 100)
}

/**
 * Reads the coverage off the main thread, again whenever [refreshKey] changes; null while loading or
 * where the platform has no page cache.
 */
@Composable
expect fun rememberReaderPageCacheCoverage(
	destinations: List<Screen.Reader>,
	refreshKey: Any? = null
): ReaderPageCacheCoverage?
