package paige.navic.shared

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opens Navidrome transcoded streams from the server's completed transcoding cache.
 *
 * A cold Navidrome transcode is served live, without a length and without range support,
 * so Ogg/Opus cannot be seeked. Asking Navidrome to estimate the length instead
 * (`estimateContentLength`) truncates songs, because the estimate is usually smaller than the
 * real encoded size and the server stops at the declared length. Navidrome keeps transcoding
 * into its cache independently of the client, and a completed cache entry is served with its
 * exact length and byte ranges. This source keeps the live response open as a fallback while
 * probing with HEAD until the cache entry is complete, then switches to the cached response.
 */
@OptIn(UnstableApi::class)
internal class NavidromeCachedTranscodeDataSource(
	private val upstream: HttpDataSource,
	private val newProbeSource: () -> HttpDataSource,
	private val policy: Policy,
) : DataSource {

	internal class Policy(
		val readyTimeoutMs: Long = 8_000,
		val pollIntervalMs: Long = 250,
		val maxConsecutiveFallbacks: Int = 2,
		val fallbackCooldownMs: Long = 10 * 60_000,
		val clock: () -> Long = SystemClock::elapsedRealtime,
		val sleep: (Long) -> Unit = Thread::sleep,
		val onUncachedFallback: (songId: String?, waitedMs: Long) -> Unit = { _, _ -> },
	) {
		/**
		 * Servers without a usable transcoding cache (or a run of songs too long to transcode within
		 * [readyTimeoutMs]) pause probing for [fallbackCooldownMs] after repeated fallbacks.
		 */
		internal val consecutiveFallbacks = AtomicInteger(0)
		@Volatile
		private var probingPausedUntil = 0L

		internal fun shouldProbe(): Boolean {
			if (consecutiveFallbacks.get() < maxConsecutiveFallbacks) return true
			if (clock() < probingPausedUntil) return false
			consecutiveFallbacks.set(0)
			return true
		}

		internal fun recordFallback() {
			if (consecutiveFallbacks.incrementAndGet() >= maxConsecutiveFallbacks) {
				probingPausedUntil = clock() + fallbackCooldownMs
			}
		}
	}

	@OptIn(UnstableApi::class)
	internal class Factory(
		private val httpFactory: HttpDataSource.Factory,
		private val policy: Policy = Policy(),
	) : DataSource.Factory {
		override fun createDataSource(): DataSource =
			NavidromeCachedTranscodeDataSource(httpFactory.createDataSource(), httpFactory::createDataSource, policy)
	}

	override fun addTransferListener(transferListener: TransferListener) {
		upstream.addTransferListener(transferListener)
	}

	override fun open(dataSpec: DataSpec): Long {
		val liveLength = upstream.open(dataSpec)
		if (!isLiveNavidromeTranscode(dataSpec, liveLength) || !policy.shouldProbe()) return liveLength

		val startedAt = policy.clock()
		while (policy.clock() - startedAt < policy.readyTimeoutMs) {
			try {
				policy.sleep(policy.pollIntervalMs)
			} catch (interrupted: InterruptedException) {
				Thread.currentThread().interrupt()
				throw InterruptedIOException("Interrupted while waiting for Navidrome transcode").apply {
					initCause(interrupted)
				}
			}
			if (cachedTranscodeReady(dataSpec)) {
				upstream.close()
				policy.consecutiveFallbacks.set(0)
				return upstream.open(dataSpec)
			}
		}
		policy.recordFallback()
		policy.onUncachedFallback(dataSpec.uri.songIdOrNull(), policy.clock() - startedAt)
		return liveLength
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

	override fun getUri(): Uri? = upstream.uri

	override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

	override fun close() {
		upstream.close()
	}

	private fun isLiveNavidromeTranscode(dataSpec: DataSpec, openedLength: Long): Boolean =
		dataSpec.position == 0L &&
			dataSpec.length == C.LENGTH_UNSET.toLong() &&
			dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET &&
			dataSpec.uri.isNavidromeStreamEndpoint() &&
			openedLength == C.LENGTH_UNSET.toLong() &&
			!upstream.responseHeaders.acceptsByteRanges()

	private fun cachedTranscodeReady(dataSpec: DataSpec): Boolean {
		val probe = newProbeSource()
		return try {
			probe.open(dataSpec.buildUpon().setHttpMethod(DataSpec.HTTP_METHOD_HEAD).build())
			probe.responseHeaders.acceptsByteRanges()
		} catch (interrupted: InterruptedIOException) {
			throw interrupted
		} catch (_: IOException) {
			false
		} finally {
			try {
				probe.close()
			} catch (_: IOException) {
			}
		}
	}
}

internal fun Uri.isNavidromeStreamEndpoint(): Boolean {
	val path = path ?: return false
	return path.endsWith("/rest/stream") || path.endsWith("/rest/stream.view")
}

private fun Uri.songIdOrNull(): String? = try {
	getQueryParameter("id")
} catch (_: UnsupportedOperationException) {
	null
}

internal fun Map<String, List<String>>.acceptsByteRanges(): Boolean =
	entries.any { (name, values) ->
		name.equals("Accept-Ranges", ignoreCase = true) && values.any { it.trim().equals("bytes", ignoreCase = true) }
	}
