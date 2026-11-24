package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.readerEpubPublicationAssetUrl
import paige.navic.reader.readerPageRasterStorageRoot
import paige.navic.ui.navigation.Screen

@Composable
actual fun rememberReaderPageCacheCoverage(
	destinations: List<Screen.Reader>,
	refreshKey: Any?
): ReaderPageCacheCoverage? {
	val context = LocalContext.current.applicationContext
	val publicationHashes = remember(destinations) {
		destinations
			.filter { destination -> destination.publicationFormat == ReaderPublicationFormat.Epub }
			.map { destination -> destination.readerEpubPublicationAssetUrl().readerPageRasterHash() }
			.toSet()
	}
	val coverage by produceState<ReaderPageCacheCoverage?>(null, publicationHashes, refreshKey) {
		value = if (publicationHashes.isEmpty()) {
			null
		} else {
			withContext(Dispatchers.IO) {
				runCatching {
					readerPageCacheCoverage(readerPageRasterStorageRoot(context), publicationHashes)
				}.getOrNull()
			}
		}
	}
	return coverage
}

/**
 * Pagination, layout and decoration fingerprints are computed in the page engine, so the layout the
 * book will open with is unknown here: count the most recently used pagination of these publications.
 */
internal fun readerPageCacheCoverage(root: File, publicationHashes: Set<String>): ReaderPageCacheCoverage {
	val entries = ReaderPageRasterManifest(root).read()
		.filter { entry -> entry.key.publicationHash in publicationHashes }
	val latest = entries
		.groupBy { entry -> entry.key.publicationHash to entry.key.paginationHash }
		.values
		.maxByOrNull { group -> group.maxOf { entry -> entry.lastAccessEpochMillis } }
		?: return ReaderPageCacheCoverage(cachedPages = 0, totalPages = 0)
	val ordinals = latest.map { entry -> entry.key.visualPageOrdinal }.distinct()
	// Two-page layouts keep one preview per spread, every second page: the cached ordinals share
	// their parity there, while a one-page layout caches neighbouring pages.
	val step = if (ordinals.size > 1 && ordinals.all { it % 2 == ordinals.first() % 2 }) 2 else 1
	val pageCount = latest.maxOf { entry -> entry.key.pageCount }
	val totalPages = (pageCount + step - 1) / step
	val cachedPages = ordinals.size
	return ReaderPageCacheCoverage(
		cachedPages = if (totalPages > 0) cachedPages.coerceAtMost(totalPages) else cachedPages,
		totalPages = totalPages
	)
}
