package paige.navic.shared

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Asks Navidrome to start transcoding upcoming queue items before they play.
 *
 * A HEAD request on a cold transcoded stream makes Navidrome run the transcode into its cache
 * without sending audio. When the item is reached (including a manual skip), the completed cache
 * entry is served immediately with an exact length and byte ranges, so
 * [NavidromeCachedTranscodeDataSource] does not have to wait and the song is seekable from the start.
 * Raw (untranscoded) streams and local files need no warm-up.
 */
@OptIn(UnstableApi::class)
internal class NavidromeTranscodeWarmer(
	private val httpFactory: HttpDataSource.Factory,
	private val executor: Executor,
	private val lookahead: Int = 2,
	private val rememberedKeys: Int = 32,
) {
	private val warmed = object : LinkedHashMap<String, Unit>(16, 0.75f, false) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > rememberedKeys
	}

	/** Call on the player's application thread after queue or position changes. */
	fun warmUpcoming(player: Player) {
		val timeline = player.currentTimeline
		if (timeline.isEmpty) return
		var index = player.currentMediaItemIndex
		val uris = buildList {
			repeat(lookahead) {
				index = timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
				if (index == androidx.media3.common.C.INDEX_UNSET) return@buildList
				player.getMediaItemAt(index).localConfiguration?.uri?.let(::add)
			}
		}
		warm(uris)
	}

	internal fun warm(uris: List<Uri>) {
		uris.forEach { uri ->
			val key = uri.transcodeWarmupKey() ?: return@forEach
			synchronized(warmed) {
				if (warmed.containsKey(key)) return@forEach
				warmed[key] = Unit
			}
			executor.execute { head(uri) }
		}
	}

	private fun head(uri: Uri) {
		val source = httpFactory.createDataSource()
		try {
			source.open(DataSpec.Builder().setUri(uri).setHttpMethod(DataSpec.HTTP_METHOD_HEAD).build())
		} catch (_: IOException) {
			// Best effort: playback still opens the live stream and waits for the cache itself.
		} finally {
			try {
				source.close()
			} catch (_: IOException) {
			}
		}
	}
}

/** Song + transcode parameters, without the per-request auth token/salt; null when nothing is transcoded. */
internal fun Uri.transcodeWarmupKey(): String? {
	if (scheme != "http" && scheme != "https") return null
	if (!isNavidromeStreamEndpoint()) return null
	val format = getQueryParameter("format")?.takeIf { it.isNotBlank() && it != "raw" } ?: return null
	val id = getQueryParameter("id") ?: return null
	return "$id|$format|${getQueryParameter("maxBitRate").orEmpty()}"
}
