package paige.navic.shared

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Least-recently-used evictor (same policy as media3's LeastRecentlyUsedCacheEvictor) whose limit can
 * change at runtime. Evictor callbacks run under the cache's lock; [setMaxBytes] takes that same lock
 * before trimming, so span bookkeeping is never touched concurrently.
 */
@OptIn(UnstableApi::class)
internal class ResizableLruCacheEvictor(maxBytes: Long) : CacheEvictor {
	private val leastRecentlyUsed = TreeSet<CacheSpan> { lhs, rhs ->
		val byTouch = lhs.lastTouchTimestamp.compareTo(rhs.lastTouchTimestamp)
		if (byTouch != 0) byTouch else lhs.compareTo(rhs)
	}
	private var currentSize = 0L

	@Volatile
	var maxBytes: Long = maxBytes
		private set

	/** Changes the limit and immediately evicts least-recently-used spans down to it. */
	fun setMaxBytes(cache: Cache, bytes: Long) {
		synchronized(cache) {
			maxBytes = bytes
			evictCache(cache, 0)
		}
	}

	override fun requiresCacheSpanTouches(): Boolean = true

	override fun onCacheInitialized() = Unit

	override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
		if (length != C.LENGTH_UNSET.toLong()) evictCache(cache, length)
	}

	override fun onSpanAdded(cache: Cache, span: CacheSpan) {
		leastRecentlyUsed.add(span)
		currentSize += span.length
		evictCache(cache, 0)
	}

	override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
		leastRecentlyUsed.remove(span)
		currentSize -= span.length
	}

	override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
		onSpanRemoved(cache, oldSpan)
		onSpanAdded(cache, newSpan)
	}

	private fun evictCache(cache: Cache, requiredSpace: Long) {
		while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
			cache.removeSpan(leastRecentlyUsed.first())
		}
	}
}
