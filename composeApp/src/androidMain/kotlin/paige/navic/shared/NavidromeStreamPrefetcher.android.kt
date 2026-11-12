package paige.navic.shared

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheWriter
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fills the local playback cache ahead of playback, one transfer at a time on a background thread.
 *
 * - The next queued song is cached as soon as the current one starts (as the untouched original on
 *   unmetered networks, otherwise at the requested streaming quality).
 * - On unmetered networks, cached transcodes are upgraded: the original file is downloaded and the
 *   transcoded copy removed, so the songs streamed while out are replaced by full-quality copies.
 */
@OptIn(UnstableApi::class)
internal class NavidromeStreamPrefetcher(
	private val cache: Cache,
	private val upstreamFactory: DataSource.Factory,
	private val isUnmetered: () -> Boolean,
	/** Fresh authenticated original-file URI for a song on the current server, or null when signed out. */
	private val originalUriForSong: (songId: String) -> Uri?,
	private val isDownloaded: (songId: String) -> Boolean,
	private val onTransfer: (event: String, key: NavidromeStreamCacheKey, outcome: String) -> Unit = { _, _, _ -> },
	private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
		Thread(runnable, "navic-stream-prefetch").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
	},
) {
	private val cacheDataSourceFactory = cacheDataSourceFactory(cache, upstreamFactory)
	private val pending = mutableSetOf<String>()

	@Volatile
	private var activeWriter: CacheWriter? = null

	@Volatile
	private var closed = false

	/** Call on the player's application thread after transitions and queue changes. */
	fun prefetchNext(player: Player) {
		val timeline = player.currentTimeline
		if (timeline.isEmpty) return
		val next = timeline.getNextWindowIndex(player.currentMediaItemIndex, player.repeatMode, player.shuffleModeEnabled)
		if (next == C.INDEX_UNSET) return
		val uri = player.getMediaItemAt(next).localConfiguration?.uri ?: return
		prefetch(uri)
	}

	internal fun prefetch(streamUri: Uri) {
		val requested = NavidromeStreamCacheKey.of(streamUri) ?: return
		if (isDownloaded(requested.songId)) return
		val original = requested.original()
		val wantOriginal = isUnmetered()
		val key = if (wantOriginal) original else requested
		if (cache.isFullyCached(original.value) || cache.isFullyCached(key.value)) return
		val uri = if (wantOriginal) NavidromeStreamCacheKey.originalUri(streamUri) else streamUri
		submit(key) { write("prefetch", key, uri) }
	}

	/** Queue the original download of every cached transcode on this server; no-op on metered networks. */
	fun upgradeCachedTranscodes(currentServerSongUri: (songId: String) -> Uri? = originalUriForSong) {
		if (!isUnmetered()) return
		// Only songs that were streamed completely; partially played (skipped) songs are left to LRU eviction.
		val transcoded = cache.keys.mapNotNull(NavidromeStreamCacheKey::parse).filterNot { it.isOriginal }
		val upgradable = transcoded.filter { cache.isFullyCached(it.value) }.map { it.server to it.songId }.toSet()
		transcoded.groupBy { it.server to it.songId }.filterKeys { it in upgradable }.forEach { (_, keys) ->
			val original = keys.first().original()
			submit(original) {
				if (!isUnmetered() || isDownloaded(original.songId)) return@submit
				val uri = currentServerSongUri(original.songId)
					?.takeIf { NavidromeStreamCacheKey.of(it)?.server == original.server }
					?: return@submit
				val complete = cache.isFullyCached(original.value) || write("upgrade", original, uri)
				if (complete) keys.forEach { cache.removeResource(it.value) }
			}
		}
	}

	fun close() {
		closed = true
		activeWriter?.cancel()
		(executor as? ExecutorService)?.shutdownNow()
	}

	private fun submit(key: NavidromeStreamCacheKey, work: () -> Unit) {
		if (closed) return
		synchronized(pending) { if (!pending.add(key.value)) return }
		executor.execute {
			try {
				if (!closed) work()
			} finally {
				synchronized(pending) { pending.remove(key.value) }
			}
		}
	}

	/** Returns true when the resource is completely cached afterwards. */
	private fun write(event: String, key: NavidromeStreamCacheKey, uri: Uri): Boolean {
		val writer = CacheWriter(
			cacheDataSourceFactory.createDataSource(),
			DataSpec.Builder().setUri(uri).setKey(key.value).build(),
			null,
			null
		)
		activeWriter = writer
		return try {
			writer.cache()
			onTransfer(event, key, "complete")
			true
		} catch (_: InterruptedIOException) {
			onTransfer(event, key, "cancelled")
			false
		} catch (error: IOException) {
			onTransfer(event, key, "failed:${error.javaClass.simpleName}")
			false
		} finally {
			activeWriter = null
		}
	}
}
