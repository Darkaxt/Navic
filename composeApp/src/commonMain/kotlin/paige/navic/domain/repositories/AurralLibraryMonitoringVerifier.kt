package paige.navic.domain.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import paige.navic.util.core.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal val AURRAL_VERIFIED_MONITORING_TTL: Duration = 6.hours
internal const val AURRAL_MONITORING_VERIFICATION_CONCURRENCY = 6

private val AURRAL_MBID_PATTERN =
	Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

/**
 * Aurral's library list (`GET api/library/artists`) can report `monitored:false` for every artist
 * even when Lidarr monitors them (seen on Aurral 2.13), while the per-artist endpoint reports the
 * real state. A `monitored:true` row is trusted; any other row is confirmed against the
 * per-artist endpoint, and the answer is remembered for [ttl] so the 10-minute list refresh does
 * not repeat the lookups.
 */
internal class AurralLibraryMonitoringVerifier(
	private val nowMillis: () -> Long,
	private val ttl: Duration = AURRAL_VERIFIED_MONITORING_TTL,
	private val concurrency: Int = AURRAL_MONITORING_VERIFICATION_CONCURRENCY
) {
	private data class Verified(val monitored: Boolean, val atMillis: Long)

	private val mutex = Mutex()
	private val verified = mutableMapOf<String, Verified>()

	suspend fun verify(
		artists: List<AurralDiscoverArtist>,
		lookup: suspend (artistMbid: String) -> Boolean?
	): List<AurralDiscoverArtist> = coroutineScope {
		val permits = Semaphore(concurrency.coerceAtLeast(1))
		artists.map { artist ->
			async {
				val mbid = artist.id.trim()
				if (artist.monitored == true || !AURRAL_MBID_PATTERN.matches(mbid)) return@async artist
				val monitored = remembered(mbid) ?: permits.withPermit { lookupOrNull(mbid, lookup) }
				if (monitored == null || monitored == artist.monitored) artist else artist.copy(monitored = monitored)
			}
		}.awaitAll()
	}

	private suspend fun remembered(mbid: String): Boolean? {
		val key = mbid.normalizedAurralCacheKey() ?: return null
		val now = nowMillis()
		return mutex.withLock {
			verified[key]?.takeIf { now >= it.atMillis && now - it.atMillis < ttl.inWholeMilliseconds }?.monitored
		}
	}

	private suspend fun lookupOrNull(mbid: String, lookup: suspend (String) -> Boolean?): Boolean? {
		val monitored = try {
			lookup(mbid)
		} catch (error: CancellationException) {
			throw error
		} catch (error: Exception) {
			Logger.w(TAG, "Aurral monitoring check failed for $mbid", error)
			null
		} ?: return null
		val key = mbid.normalizedAurralCacheKey() ?: return monitored
		mutex.withLock { verified[key] = Verified(monitored, nowMillis()) }
		return monitored
	}

	private companion object {
		const val TAG = "AurralMonitoringVerifier"
	}
}
