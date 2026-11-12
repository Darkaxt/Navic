package paige.navic.shared

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import org.koin.mp.KoinPlatform
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DefaultStreamedSongCacheSizeMb
import paige.navic.domain.models.cacheSizeMbToBytes
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Identity of a Navidrome stream in the local playback cache: server, song and quality.
 * Auth parameters (u/t/s/p) are never part of a key.
 */
internal data class NavidromeStreamCacheKey(val server: String, val songId: String, val quality: String) {
	val isOriginal: Boolean get() = quality == ORIGINAL
	val value: String get() = "nd:$server:$songId:$quality"
	fun original(): NavidromeStreamCacheKey = copy(quality = ORIGINAL)

	companion object {
		const val ORIGINAL = "original"
		private val transcodeParams = setOf("format", "maxBitRate", "estimateContentLength", "timeOffset")

		fun of(uri: Uri): NavidromeStreamCacheKey? {
			if (uri.scheme != "http" && uri.scheme != "https") return null
			if (!uri.isNavidromeStreamEndpoint()) return null
			if (!uri.getQueryParameter("timeOffset").isNullOrBlank()) return null
			val id = uri.getQueryParameter("id")?.takeIf { it.isNotBlank() } ?: return null
			val format = uri.getQueryParameter("format")?.takeIf { it.isNotBlank() && it != "raw" }
			val quality = if (format == null) ORIGINAL else "$format-${uri.getQueryParameter("maxBitRate").orEmpty()}"
			return NavidromeStreamCacheKey(uri.serverIdentity(), id, quality)
		}

		fun parse(value: String): NavidromeStreamCacheKey? {
			if (!value.startsWith("nd:")) return null
			val quality = value.substringAfterLast(':')
			val rest = value.removePrefix("nd:").substringBeforeLast(':')
			val songId = rest.substringAfterLast(':')
			val server = rest.substringBeforeLast(':')
			if (server.isBlank() || songId.isBlank() || quality.isBlank()) return null
			return NavidromeStreamCacheKey(server, songId, quality)
		}

		/** The same stream request for the untouched original file (keeps the auth parameters). */
		fun originalUri(uri: Uri): Uri {
			val builder = uri.buildUpon().clearQuery()
			uri.queryParameterNames.filterNot { it in transcodeParams }.forEach { name ->
				uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
			}
			return builder.appendQueryParameter("format", "raw").build()
		}
	}
}

private fun Uri.serverIdentity(): String {
	val base = encodedPath.orEmpty().substringBeforeLast("/rest/")
	return "${host.orEmpty()}:${if (port == -1) 0 else port}$base"
}

/**
 * Process-wide streamed-song cache. SimpleCache allows exactly one instance per folder.
 * Its size limit comes from the `streamedSongCacheSizeMb` preference and can change at runtime.
 */
@OptIn(UnstableApi::class)
internal object NavidromePlaybackCache {
	@Volatile
	private var cache: SimpleCache? = null

	@Volatile
	private var evictor: ResizableLruCacheEvictor? = null

	/** Limit set from Settings during this process; wins over the stored preference at creation. */
	@Volatile
	private var requestedMaxBytes: Long? = null

	fun get(context: Context): Cache = cache ?: synchronized(this) {
		cache ?: run {
			val lruEvictor = ResizableLruCacheEvictor(initialMaxBytes())
			SimpleCache(
				File(context.applicationContext.cacheDir, "playback-audio"),
				lruEvictor,
				StandaloneDatabaseProvider(context.applicationContext)
			).also {
				evictor = lruEvictor
				cache = it
			}
		}
	}

	/** Bytes currently stored on disk. */
	fun sizeBytes(context: Context): Long = get(context).cacheSpace

	/** Changes the limit, trimming least-recently-used songs immediately when it is lowered. */
	fun setMaxBytes(bytes: Long) {
		synchronized(this) {
			requestedMaxBytes = bytes
			val current = cache ?: return
			evictor?.setMaxBytes(current, bytes)
		}
	}

	/** Removes every cached song; safe while playback runs (open readers re-fetch from the server). */
	fun clear(context: Context) {
		val current = get(context)
		current.keys.forEach { key -> current.removeResource(key) }
	}

	private fun initialMaxBytes(): Long = requestedMaxBytes ?: cacheSizeMbToBytes(
		KoinPlatform.getKoinOrNull()?.getOrNull<PreferenceManager>()?.streamedSongCacheSizeMb
			?: DefaultStreamedSongCacheSizeMb
	)
}

@OptIn(UnstableApi::class)
internal fun Cache.isFullyCached(key: String): Boolean {
	val length = ContentMetadata.getContentLength(getContentMetadata(key))
	return length != C.LENGTH_UNSET.toLong() && isCached(key, 0, length)
}

/**
 * Reads Navidrome song streams through the local playback cache, so a song is downloaded once and
 * replays (and seeks) from local storage. When a complete original copy is cached, it is used in
 * place of any transcoded request for the same song. Other URLs (radio, artwork) bypass the cache.
 *
 * The variant chosen at a song's first open is pinned: the extractor and decoder are bound to that
 * file's format, so every later open of the same request (seeks, load retries) must serve the same
 * bytes even if the original finishes downloading meanwhile. [Factory.retainPinsFor] releases pins
 * of songs that left the player's current/next window, so a later replay can pick the original.
 */
@OptIn(UnstableApi::class)
internal class NavidromeStreamCacheDataSource(
	private val cache: Cache,
	private val cachedSource: DataSource,
	private val bypassSource: DataSource,
	private val onOpen: (key: NavidromeStreamCacheKey, servedKey: NavidromeStreamCacheKey, fullyCached: Boolean) -> Unit = { _, _, _ -> },
	private val pins: MutableMap<NavidromeStreamCacheKey, NavidromeStreamCacheKey> = ConcurrentHashMap(),
) : DataSource {
	private var active: DataSource? = null

	@OptIn(UnstableApi::class)
	internal class Factory(
		private val cache: Cache,
		private val upstreamFactory: DataSource.Factory,
		private val bypassFactory: DataSource.Factory,
		private val onOpen: (NavidromeStreamCacheKey, NavidromeStreamCacheKey, Boolean) -> Unit = { _, _, _ -> },
	) : DataSource.Factory {
		private val cachedFactory = cacheDataSourceFactory(cache, upstreamFactory)
		private val pins = ConcurrentHashMap<NavidromeStreamCacheKey, NavidromeStreamCacheKey>()

		override fun createDataSource(): DataSource = NavidromeStreamCacheDataSource(
			cache, cachedFactory.createDataSource(), bypassFactory.createDataSource(), onOpen, pins
		)

		/** Drops the pinned variants of every song not in [songIds] (the player's current and next items). */
		fun retainPinsFor(songIds: Set<String>) {
			pins.keys.removeAll { it.songId !in songIds }
		}

		/** True while a song's served variant is pinned by the player, so its cached copies must stay. */
		fun isInUse(songId: String): Boolean = pins.keys.any { it.songId == songId }
	}

	override fun addTransferListener(transferListener: TransferListener) {
		cachedSource.addTransferListener(transferListener)
		bypassSource.addTransferListener(transferListener)
	}

	override fun open(dataSpec: DataSpec): Long {
		val key = NavidromeStreamCacheKey.of(dataSpec.uri)
		if (key == null) {
			active = bypassSource
			return bypassSource.open(dataSpec)
		}
		val original = key.original()
		val served = pins.getOrPut(key) {
			if (!key.isOriginal && cache.isFullyCached(original.value)) original else key
		}
		val useOriginal = served != key
		val spec = dataSpec.buildUpon()
			.setKey(served.value)
			.apply { if (useOriginal) setUri(NavidromeStreamCacheKey.originalUri(dataSpec.uri)) }
			.build()
		onOpen(key, served, useOriginal || cache.isFullyCached(served.value))
		active = cachedSource
		return cachedSource.open(spec)
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
		checkNotNull(active).read(buffer, offset, length)

	override fun getUri(): Uri? = active?.uri

	override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

	override fun close() {
		try {
			active?.close()
		} finally {
			active = null
		}
	}
}

@OptIn(UnstableApi::class)
internal fun cacheDataSourceFactory(cache: Cache, upstreamFactory: DataSource.Factory): CacheDataSource.Factory =
	CacheDataSource.Factory()
		.setCache(cache)
		.setUpstreamDataSourceFactory(upstreamFactory)
		.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
