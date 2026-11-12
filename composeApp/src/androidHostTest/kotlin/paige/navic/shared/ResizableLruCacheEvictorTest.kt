package paige.navic.shared

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ResizableLruCacheEvictorTest {
	private val chunk = 4L * 1024
	private val dir: File = Files.createTempDirectory("navic-lru-evictor").toFile()
	private val evictor = ResizableLruCacheEvictor(64L * 1024)
	private val cache = SimpleCache(dir, evictor, StandaloneDatabaseProvider(RuntimeEnvironment.getApplication()))

	@After
	fun release() {
		cache.release()
		dir.deleteRecursively()
	}

	/** Writes one complete [chunk]-sized resource, as CacheDataSink would. */
	private fun put(key: String) {
		val span = cache.startReadWrite(key, 0, chunk)
		val file = cache.startFile(key, 0, chunk)
		file.writeBytes(ByteArray(chunk.toInt()) { 1 })
		cache.commitFile(file, chunk)
		cache.releaseHoleSpan(span)
	}

	/** Reading a fully cached span touches it (no hole lock is taken, so nothing to release). */
	private fun touch(key: String) {
		assertTrue(cache.startReadWrite(key, 0, chunk).isCached)
	}

	@Test
	fun loweringTheLimitEvictsLeastRecentlyUsedFirst() {
		listOf("a", "b", "c", "d").forEach { put(it); Thread.sleep(5) }
		touch("a")

		evictor.setMaxBytes(cache, 2 * chunk)

		assertEquals(2 * chunk, cache.cacheSpace)
		assertTrue(cache.keys.containsAll(listOf("a", "d")))
		assertFalse("b" in cache.keys)
		assertFalse("c" in cache.keys)
	}

	@Test
	fun neverEvictsWhileUnderTheLimit() {
		listOf("a", "b", "c").forEach(::put)

		evictor.setMaxBytes(cache, 3 * chunk)
		assertEquals(3 * chunk, cache.cacheSpace)

		evictor.setMaxBytes(cache, 1024L * 1024)
		put("d")
		assertEquals(4 * chunk, cache.cacheSpace)
		assertEquals(setOf("a", "b", "c", "d"), cache.keys)
	}

	@Test
	fun newWritesRespectALoweredLimit() {
		evictor.setMaxBytes(cache, 2 * chunk)
		listOf("a", "b", "c").forEach { put(it); Thread.sleep(5) }

		assertTrue(cache.cacheSpace <= 2 * chunk)
		assertFalse("a" in cache.keys)
	}
}
