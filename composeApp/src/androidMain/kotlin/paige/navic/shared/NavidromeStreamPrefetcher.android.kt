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
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import paige.navic.domain.models.playbackErrorCauseDetails

/**
 * Fills the local playback cache ahead of playback, one transfer at a time on a background thread.
 *
 * - The next [UpcomingPrefetchCount] queued songs are cached, in play order, as soon as the current one
 *   starts (as the untouched original on unmetered networks, otherwise at the requested quality).
 * - Each transfer is timed, so [isUnready] can tell, before a song starts, that the link cannot
 *   deliver it faster than it plays. Such songs are skipped by the player and [defer]red: they are
 *   cached again whenever the connection changes ([retry]).
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
	/** True while the player has the song open; its transcoded copy is kept until it is released. */
	private val isInUse: (songId: String) -> Boolean = { false },
	private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
		Thread(runnable, "navic-stream-prefetch").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
	},
	private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
	private val cacheDataSourceFactory = cacheDataSourceFactory(cache, upstreamFactory)
	private val pending = mutableSetOf<String>()
	/** The running transfer, measured from its live byte count (cache spans only appear when a chunk closes). */
	private class Transfer(val key: String, val startMs: Long) {
		@Volatile var startBytes = -1L
		@Volatile var bytes = 0L
		@Volatile var length = C.LENGTH_UNSET.toLong()
	}

	@Volatile
	private var running: Transfer? = null
	private val failedKeys = ConcurrentHashMap.newKeySet<String>()
	private val deferred = LinkedHashMap<String, Uri>()
	private val upcoming = mutableListOf<Uri>()

	@Volatile
	private var activeWriter: CacheWriter? = null

	@Volatile
	private var closed = false

	/** Call on the player's application thread after transitions and queue changes. */
	fun prefetchNext(player: Player) {
		val timeline = player.currentTimeline
		val uris = mutableListOf<Uri>()
		if (!timeline.isEmpty) {
			var index = player.currentMediaItemIndex
			val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
			while (uris.size < UpcomingPrefetchCount) {
				index = timeline.getNextWindowIndex(index, repeat, player.shuffleModeEnabled)
				if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) break
				player.getMediaItemAt(index).localConfiguration?.uri?.let(uris::add)
			}
		}
		synchronized(upcoming) { upcoming.clear(); upcoming.addAll(uris) }
		uris.forEach(::prefetch)
	}

	/** Re-tries the upcoming and deferred songs, e.g. when the connection changes. */
	fun retry() {
		failedKeys.clear()
		synchronized(upcoming) { upcoming.toList() }.forEach(::prefetch)
		synchronized(deferred) { deferred.values.toList() }.forEach(::prefetch)
	}

	/** Remembers a skipped song so it is cached as soon as the connection allows. */
	fun defer(streamUri: Uri) {
		val key = NavidromeStreamCacheKey.of(streamUri) ?: return
		synchronized(deferred) {
			deferred.remove(key.songId)
			deferred[key.songId] = streamUri
			while (deferred.size > MaxDeferredSongs) deferred.remove(deferred.keys.first())
		}
		prefetch(streamUri)
	}

	/**
	 * True when a not yet cached stream would stall: its transfer failed, or at the measured rate the
	 * rest would not arrive before playback reaches it (needs [MinMeasureMillis] of evidence).
	 * [durationMs] is the song's length; unknown lengths never count as unready.
	 */
	fun isUnready(streamUri: Uri, durationMs: Long): Boolean {
		val requested = NavidromeStreamCacheKey.of(streamUri) ?: return false
		if (isDownloaded(requested.songId)) return false
		val original = requested.original()
		if (cache.isFullyCached(original.value) || cache.isFullyCached(requested.value)) return false
		val keys = setOf(requested.value, original.value)
		val transfer = running?.takeIf { it.key in keys }
			?: return keys.any { it in failedKeys }
		val elapsedMs = clock() - transfer.startMs
		val length = transfer.length
		if (elapsedMs < MinMeasureMillis || durationMs <= 0 || length <= 0 || transfer.startBytes < 0) return false
		val bytesPerMs = (transfer.bytes - transfer.startBytes).toDouble() / elapsedMs
		return transfer.bytes + bytesPerMs * durationMs * RateSafetyFactor < length
	}

	internal fun prefetch(streamUri: Uri) {
		val requested = NavidromeStreamCacheKey.of(streamUri) ?: return
		if (isDownloaded(requested.songId)) return
		val original = requested.original()
		val wantOriginal = isUnmetered()
		val key = if (wantOriginal) original else requested
		if (cache.isFullyCached(original.value) || cache.isFullyCached(key.value)) return
		val uri = if (wantOriginal) NavidromeStreamCacheKey.originalUri(streamUri) else streamUri
		submit(key) {
			if (write("prefetch", key, uri)) synchronized(deferred) { deferred.remove(key.songId) }
		}
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
				if (complete && !isInUse(original.songId)) keys.forEach { cache.removeResource(it.value) }
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
		val transfer = Transfer(key.value, clock())
		val writer = CacheWriter(
			cacheDataSourceFactory.createDataSource(),
			DataSpec.Builder().setUri(uri).setKey(key.value).build(),
			null
		) { requestLength, bytesCached, _ ->
			if (transfer.startBytes < 0) transfer.startBytes = bytesCached
			transfer.bytes = bytesCached
			if (requestLength > 0) transfer.length = requestLength
		}
		activeWriter = writer
		running = transfer
		return try {
			writer.cache()
			failedKeys.remove(key.value)
			onTransfer(event, key, "complete")
			true
		} catch (_: InterruptedIOException) {
			onTransfer(event, key, "cancelled")
			false
		} catch (error: IOException) {
			failedKeys.add(key.value)
			// R8 obfuscates exception class names (e.g. "so2"); the cause chain stays readable.
			onTransfer(event, key, "failed:${error.javaClass.simpleName}:${error.message.orEmpty().take(120)}<-${playbackErrorCauseDetails(error).take(240)}")
			false
		} finally {
			activeWriter = null
			running = null
		}
	}
}

/** Songs cached ahead of the current one. */
internal const val UpcomingPrefetchCount = 3
private const val MaxDeferredSongs = 20
/** A transfer must run this long before its rate is trusted. */
private const val MinMeasureMillis = 15_000L
/** Rate margin: the link must deliver the rest with 10% to spare. */
private const val RateSafetyFactor = 0.9
