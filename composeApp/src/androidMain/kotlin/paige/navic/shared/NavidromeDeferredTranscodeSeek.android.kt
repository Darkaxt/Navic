package paige.navic.shared

import android.net.Uri
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Makes seeking work in a song that is still being transcoded live by Navidrome.
 *
 * A live transcode has no length and no byte ranges, so the player treats the song as unseekable and
 * a seek would restart it from 0:00. Instead, the seek is held back while the song keeps playing, and
 * Navidrome is probed until its transcoding cache holds the finished file (byte-identical to the live
 * stream, with an exact length and range support). The song is then reloaded from that file and
 * seeked to the requested position. Nothing switches unless the user seeks.
 */
@OptIn(UnstableApi::class)
internal class NavidromeDeferredTranscodeSeek(
	private val player: Player,
	/** Blocking probe: the finished transcode's length once Navidrome's cached copy is complete, else null. */
	private val completedLength: (streamUri: Uri) -> Long?,
	/** Records the finished length for the song's local cache entry so the reloaded stream is seekable. */
	private val onCompletedLength: (streamUri: Uri, length: Long) -> Unit,
	/** Replaces the queue item (keeping its identity and shuffle position). */
	private val replaceItem: (index: Int, item: MediaItem) -> Unit,
	private val executor: Executor,
	private val handler: Handler,
	private val policy: Policy = Policy(),
	private val log: (event: String, songId: String?, detail: String) -> Unit = { _, _, _ -> },
) {
	internal class Policy(
		val pollIntervalMs: Long = 1_000,
		val timeoutMs: Long = 120_000,
		val clock: () -> Long = android.os.SystemClock::elapsedRealtime,
		val sleep: (Long) -> Unit = Thread::sleep,
	)

	private class Pending(val mediaId: String, val uri: Uri, @Volatile var positionMs: Long) {
		@Volatile
		var cancelled = false
	}

	private var pending: Pending? = null
	private var reloads = 0

	/**
	 * Returns true when the seek was taken over (the caller must not forward it to the player).
	 * Call on the player's application thread.
	 */
	fun interceptSeek(mediaItemIndex: Int, positionMs: Long): Boolean {
		if (mediaItemIndex != player.currentMediaItemIndex || positionMs <= 0L) return false
		if (player.isCurrentMediaItemSeekable || player.playbackState == Player.STATE_IDLE) return false
		val timeline = player.currentTimeline
		if (timeline.isEmpty || timeline.getWindow(mediaItemIndex, Timeline.Window()).isPlaceholder) return false
		val item = player.currentMediaItem ?: return false
		val uri = item.localConfiguration?.uri ?: return false
		val key = NavidromeStreamCacheKey.of(uri) ?: return false
		if (key.isOriginal) return false

		pending?.let { current ->
			if (current.mediaId == item.mediaId && !current.cancelled) {
				current.positionMs = positionMs
				log("seek-deferred", key.songId, "positionMs=$positionMs waiting=true")
				return true
			}
			current.cancelled = true
		}
		val request = Pending(item.mediaId, uri, positionMs)
		pending = request
		log("seek-deferred", key.songId, "positionMs=$positionMs waiting=false")
		executor.execute { awaitCompletedTranscode(request, key.songId) }
		return true
	}

	/** Drops a waiting seek, e.g. when the player moves to another song. */
	fun cancel() {
		pending?.cancelled = true
		pending = null
	}

	private fun awaitCompletedTranscode(request: Pending, songId: String) {
		val startedAt = policy.clock()
		while (!request.cancelled && policy.clock() - startedAt < policy.timeoutMs) {
			val length = try {
				completedLength(request.uri)
			} catch (_: Exception) {
				null
			}
			if (length != null && length > 0) {
				handler.post { resume(request, length) }
				return
			}
			try {
				policy.sleep(policy.pollIntervalMs)
			} catch (_: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
		}
		if (!request.cancelled) handler.post {
			if (pending === request) pending = null
			log("seek-abandoned", songId, "waitedMs=${policy.clock() - startedAt}")
		}
	}

	private fun resume(request: Pending, length: Long) {
		if (request.cancelled || pending !== request) return
		pending = null
		val index = player.currentMediaItemIndex
		val item = player.currentMediaItem
		if (item == null || item.mediaId != request.mediaId || item.localConfiguration?.uri != request.uri) return
		if (player.isCurrentMediaItemSeekable) {
			player.seekTo(index, request.positionMs)
			return
		}
		onCompletedLength(request.uri, length)
		// A different URI (same cache key) makes the player reload the source instead of updating it in place.
		val reloadUri = request.uri.buildUpon().clearQuery().apply {
			request.uri.queryParameterNames.filter { it != RELOAD_PARAM }.forEach { name ->
				request.uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) }
			}
			appendQueryParameter(RELOAD_PARAM, (++reloads).toString())
		}.build()
		replaceItem(index, item.buildUpon().setUri(reloadUri).build())
		player.seekTo(index, request.positionMs)
		log("seek-resumed", NavidromeStreamCacheKey.of(request.uri)?.songId, "positionMs=${request.positionMs} length=$length")
	}

	companion object {
		const val RELOAD_PARAM = "navicReload"
	}
}

/**
 * HEAD probe: the length of Navidrome's finished transcode once its cached copy is complete
 * (served with byte ranges), or null while the song is still being transcoded.
 */
@OptIn(UnstableApi::class)
internal fun completedNavidromeTranscodeLength(httpFactory: HttpDataSource.Factory, streamUri: Uri): Long? {
	val probe = httpFactory.createDataSource()
	return try {
		probe.open(DataSpec.Builder().setUri(streamUri).setHttpMethod(DataSpec.HTTP_METHOD_HEAD).build())
		val headers = probe.responseHeaders
		if (!headers.acceptsByteRanges()) return null
		headers.entries.firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
			?.value?.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0 }
	} catch (_: IOException) {
		null
	} finally {
		try {
			probe.close()
		} catch (_: IOException) {
		}
	}
}

/** Records a resource's final length when it is not known yet (a live transcode was cached without one). */
@OptIn(UnstableApi::class)
internal fun Cache.recordContentLength(key: String, length: Long) {
	if (ContentMetadata.getContentLength(getContentMetadata(key)) != C.LENGTH_UNSET.toLong()) return
	applyContentMetadataMutations(key, ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, length) })
}
