package paige.navic.ui.screens.reader

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageBitmapQuality

class ReaderPageRasterCacheTest {
	@Test
	fun completeRasterIdentitySeparatesEveryLayoutInput() {
		val base = key()
		val variants = listOf(
			base.copy(publicationHash = "publication-2"),
			base.copy(paginationHash = "pagination-2"),
			base.copy(spineIndex = 8),
			base.copy(hrefHash = "href-2"),
			base.copy(chapterPageIndex = 4),
			base.copy(visualPageOrdinal = 12),
			base.copy(viewportWidth = 1_201),
			base.copy(viewportHeight = 1_801),
			base.copy(layoutHash = "layout-2"),
			base.copy(decorationHash = "decoration-2"),
			base.copy(quality = ReaderPageBitmapQuality.Native),
			base.copy(schemaVersion = 2)
		)

		variants.forEach { variant ->
			assertNotEquals(base.identity, variant.identity)
			assertNotEquals(base.digest, variant.digest)
		}
	}

	@Test
	fun pageAndManifestArePublishedAtomically() {
		val fixture = fixture()
		val value = pngBytes("page-one")

		assertTrue(fixture.cache.write(key(), metadata(), value))

		assertContentEquals(value, fixture.cache.pathFor(key()).readBytes())
		assertTrue(fixture.cache.manifestPath().readText().contains(key().digest))
		assertFalse(fixture.root.walkTopDown().any { file -> file.name.endsWith(".tmp") })
	}

	@Test
	fun failedImageWriteDoesNotPublishManifestEntry() {
		val fixture = fixture(codec = ByteArrayRasterCodec(failEncoding = true))

		assertFalse(fixture.cache.write(key(), metadata(), pngBytes("broken")))

		assertFalse(fixture.cache.pathFor(key()).exists())
		assertFalse(fixture.cache.manifestPath().takeIf(File::exists)?.readText().orEmpty().contains(key().digest))
	}

	@Test
	fun corruptRasterIsDeletedAndReportedAsMiss() {
		val fixture = fixture(maxDecodedEntries = 0)
		assertTrue(fixture.cache.write(key(), metadata(), pngBytes("valid")))
		fixture.cache.pathFor(key()).writeText("not-an-image")

		assertNull(fixture.cache.read(key()))

		assertFalse(fixture.cache.pathFor(key()).exists())
		assertFalse(fixture.cache.manifestPath().readText().contains(key().digest))
	}

	@Test
	fun diskLruEvictsLeastRecentlyUsedRaster() {
		var now = 1_000L
		val fixture = fixture(maxDiskBytes = 10L, maxDecodedEntries = 0, clock = { now++ })
		val first = key(chapterPageIndex = 1)
		val second = key(chapterPageIndex = 2)
		val third = key(chapterPageIndex = 3)
		assertTrue(fixture.cache.write(first, metadata(), pngBytes("a")))
		assertTrue(fixture.cache.write(second, metadata(), pngBytes("b")))
		assertContentEquals(pngBytes("a"), fixture.cache.read(first)?.value)

		assertTrue(fixture.cache.write(third, metadata(), pngBytes("c")))

		assertTrue(fixture.cache.pathFor(first).isFile)
		assertFalse(fixture.cache.pathFor(second).exists())
		assertTrue(fixture.cache.pathFor(third).isFile)
		assertEquals(2, fixture.cache.metrics().diskEntries)
	}

	@Test
	fun activatingProfileRemovesOnlyObsoletePublicationRasters() {
		val fixture = fixture(maxDecodedEntries = 0)
		val active = key(chapterPageIndex = 1)
		val obsoleteQuality = active.copy(chapterPageIndex = 2, quality = ReaderPageBitmapQuality.Native)
		val obsoleteLayout = active.copy(chapterPageIndex = 3, layoutHash = "old-layout")
		val otherPublication = active.copy(publicationHash = "other", chapterPageIndex = 4)
		listOf(active, obsoleteQuality, obsoleteLayout, otherPublication).forEach { rasterKey ->
			assertTrue(fixture.cache.write(rasterKey, metadata(), pngBytes(rasterKey.digest.take(1))))
		}

		val removed = fixture.cache.retainProfile(active.profile)

		assertEquals(2, removed)
		assertTrue(fixture.cache.pathFor(active).isFile)
		assertFalse(fixture.cache.pathFor(obsoleteQuality).exists())
		assertFalse(fixture.cache.pathFor(obsoleteLayout).exists())
		assertTrue(fixture.cache.pathFor(otherPublication).isFile)
	}

	@Test
	fun decodedMemoryTierIsBoundedAndDiskFilesAreNeverSymlinks() {
		val codec = ByteArrayRasterCodec()
		val fixture = fixture(codec = codec, maxDecodedEntries = 1)
		val first = key(chapterPageIndex = 1)
		val second = key(chapterPageIndex = 2)
		assertTrue(fixture.cache.write(first, metadata(), pngBytes("a")))
		assertTrue(fixture.cache.write(second, metadata(), pngBytes("b")))

		assertContentEquals(pngBytes("a"), fixture.cache.read(first)?.value)

		assertTrue(codec.decodeCalls > 0)
		assertEquals(1, fixture.cache.metrics().decodedEntries)
		assertFalse(fixture.root.walkTopDown().filter(File::isFile).any { file -> java.nio.file.Files.isSymbolicLink(file.toPath()) })
	}

	@Test
	fun manifestRestoresRasterMetadataAcrossCacheInstances() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key()
		val rasterMetadata = metadata()
		assertTrue(fixture.cache.write(rasterKey, rasterMetadata, pngBytes("persisted")))

		val reopened = ReaderPageRasterCache(
			root = fixture.root,
			codec = ByteArrayRasterCodec(),
			maxDiskBytes = 1_024L,
			maxDecodedEntries = 1
		)
		val restored = reopened.read(rasterKey)

		assertEquals(rasterMetadata, restored?.metadata)
		assertContentEquals(pngBytes("persisted"), restored?.value)
	}

	@Test
	fun copiedReadTransfersIndependentValueOwnershipToCaller() {
		val fixture = fixture(maxDecodedEntries = 1)
		val rasterKey = key()
		val original = pngBytes("owned-by-cache")
		assertTrue(fixture.cache.write(rasterKey, metadata(), original))

		val copied = fixture.cache.readCopy(rasterKey) { cached -> cached.copyOf() }

		assertEquals(metadata(), copied?.metadata)
		assertContentEquals(original, copied?.value)
		assertNotSame(original, copied?.value)
		fixture.cache.close()
		assertContentEquals(pngBytes("owned-by-cache"), copied?.value)
	}

	private fun fixture(
		codec: ByteArrayRasterCodec = ByteArrayRasterCodec(),
		maxDiskBytes: Long = 1_024L,
		maxDecodedEntries: Int = 2,
		clock: () -> Long = System::currentTimeMillis
	): CacheFixture {
		val root = createTempDirectory("navic-reader-page-raster-cache").toFile()
		return CacheFixture(
			root = root,
			cache = ReaderPageRasterCache(
				root = root,
				codec = codec,
				maxDiskBytes = maxDiskBytes,
				maxDecodedEntries = maxDecodedEntries,
				clock = clock
			)
		)
	}

	private fun key(chapterPageIndex: Int = 3) = ReaderPageRasterKey(
		publicationHash = "publication-1",
		paginationHash = "pagination-1",
		spineIndex = 7,
		hrefHash = "href-1",
		chapterPageIndex = chapterPageIndex,
		visualPageOrdinal = 11,
		viewportWidth = 1_200,
		viewportHeight = 1_800,
		layoutHash = "layout-1",
		decorationHash = "decoration-1",
		quality = ReaderPageBitmapQuality.Balanced
	)

	private fun metadata() = ReaderPageRasterMetadata(
		surfaceLeft = 10,
		surfaceTop = 20,
		surfaceRight = 1_210,
		surfaceBottom = 1_820,
		fullLeafRect = ReaderPageRasterRect(0, 0, 600, 900),
		leftLeafRect = null,
		gutterRect = null,
		rightLeafRect = null,
		reverseFaceColor = 0xffead9ae.toInt()
	)

	private fun pngBytes(label: String): ByteArray = "PNG:$label".encodeToByteArray()

	private data class CacheFixture(
		val root: File,
		val cache: ReaderPageRasterCache<ByteArray>
	)

	private class ByteArrayRasterCodec(
		private val failEncoding: Boolean = false
	) : ReaderPageRasterCodec<ByteArray> {
		var decodeCalls = 0
			private set

		override fun encode(value: ByteArray, target: File): Boolean {
			if (failEncoding) return false
			target.writeBytes(value)
			return true
		}

		override fun decode(source: File): ByteArray? {
			decodeCalls += 1
			return source.readBytes().takeIf { bytes -> bytes.decodeToString().startsWith("PNG:") }
		}

		override fun release(value: ByteArray) = Unit
	}
}
