package paige.navic.ui.screens.reader

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageBitmapQuality

class ReaderPageRasterDigestHostTest {
	@Test
	fun repeatedManifestAndCacheLookupsReuseTheImmutableKeyDigest() {
		val key = key()
		val first = key.digest
		assertEquals(GoldenDigest, first)
		repeat(32) {
			assertSame(first, key.digest, "A407.Digest.RepeatedLookupMustNotRebuildHashAndHex")
		}
	}

	@Test
	fun concurrentFirstLookupsPublishOneComputedDigest() {
		val key = key()
		val start = CountDownLatch(1)
		val ready = CountDownLatch(8)
		val values = AtomicReferenceArray<String>(8)
		val workers = (0 until 8).map { index ->
			thread(name = "synthetic-raster-digest-$index") {
				ready.countDown()
				check(start.await(5, TimeUnit.SECONDS))
				values.set(index, key.digest)
			}
		}
		try {
			assertTrue(ready.await(5, TimeUnit.SECONDS))
		} finally {
			start.countDown()
			workers.forEach { it.join(5_000) }
		}
		assertTrue(workers.none { it.isAlive })
		assertEquals(GoldenDigest, values.get(0))
		(1 until 8).forEach { index ->
			assertSame(values.get(0), values.get(index), "A407.Digest.ConcurrentLookupMustSharePublishedResult")
		}
	}

	@Test
	fun goldenIdentityAndAllCopiedFieldsRemainIndependent() {
		val original = key()
		assertEquals(GoldenIdentity, original.identity)
		assertEquals(GoldenDigest, original.digest)
		assertEquals(original, original.copy())
		assertEquals(original.hashCode(), original.copy().hashCode())
		assertEquals(GoldenDigest, original.copy().digest)
		val variants = listOf(
			original.copy(publicationHash = "publication-2"),
			original.copy(paginationHash = "pagination-2"),
			original.copy(spineIndex = 8),
			original.copy(hrefHash = "href-2"),
			original.copy(chapterPageIndex = 4),
			original.copy(visualPageOrdinal = 12),
			original.copy(viewportWidth = 1_201),
			original.copy(viewportHeight = 1_801),
			original.copy(layoutHash = "layout-2"),
			original.copy(decorationHash = "decoration-2"),
			original.copy(quality = ReaderPageBitmapQuality.Native),
			original.copy(schemaVersion = 7)
		)
		variants.forEach { changed ->
			assertNotEquals(original.identity, changed.identity)
			assertNotEquals(original.digest, changed.digest)
			assertEquals(GoldenDigest, original.digest)
		}
		assertEquals(12, variants.map { it.digest }.toSet().size)
	}

	@Test
	fun manifestBytesAndReadbackKeepOriginalDigestOrderingAndSchema() {
		val root = createTempDirectory("navic-digest-golden").toFile()
		val manifest = ReaderPageRasterManifest(root)
		val first = entry(key(), 1)
		val second = entry(key().copy(chapterPageIndex = 4), 2)
		assertTrue(manifest.write(listOf(second, first)))
		val metadata = """{"surfaceLeft":0,"surfaceTop":0,"surfaceRight":1200,"surfaceBottom":1800,"fullLeafRect":null,"leftLeafRect":null,"gutterRect":null,"rightLeafRect":null,"reverseFaceColor":0}"""
		val expected = """{"schemaVersion":6,"entries":[{"key":$GoldenIdentity,"rasterFileName":"raster-1.png","byteSize":1,"lastAccessEpochMillis":1,"metadata":$metadata},{"key":${GoldenIdentity.replace("\"chapterPageIndex\":3", "\"chapterPageIndex\":4")},"rasterFileName":"raster-2.png","byteSize":2,"lastAccessEpochMillis":2,"metadata":$metadata}]}"""
		assertEquals(expected, manifest.file.readText())
		assertEquals(listOf(first, second), manifest.read())
		assertTrue(manifest.write(listOf(first, second)))
		assertEquals(expected, manifest.file.readText())
	}

	/** Fixed synthetic workloads, not a runtime entry-count claim or a latency pass/fail oracle. */
	@Test
	fun syntheticManifestSerializationCostObservation() {
		val cpu = ManagementFactory.getThreadMXBean()
		val cpuAvailable = cpu.isCurrentThreadCpuTimeSupported && cpu.isThreadCpuTimeEnabled
		val observations = mutableListOf<String>()
		for (size in listOf(8, 64, 256)) {
			for (reuse in listOf(false, true)) {
				val manifest = ReaderPageRasterManifest(createTempDirectory("navic-digest-bench").toFile())
				fun entries() = (0 until size).map { index ->
					val page = (index * 73) % size
					entry(key().copy(chapterPageIndex = page, visualPageOrdinal = page), page)
				}
				val reused = entries()
				assertTrue(manifest.write(if (reuse) reused else entries())) // One bounded JVM warm-up.
				repeat(3) { sample ->
					val input = if (reuse) reused else entries()
					val cost = ReaderPublicationCost()
					val cpuStart = if (cpuAvailable) cpu.currentThreadCpuTime else -1L
					val started = System.nanoTime()
					assertTrue(manifest.write(input, cost))
					val wall = System.nanoTime() - started
					val cpuNanos = if (cpuAvailable) cpu.currentThreadCpuTime - cpuStart else -1L
					val spans = buildString { cost.appendTo(this) }.trim().split(' ')
						.associate { it.substringBefore('=') to it.substringAfter('=').toLong() }
					assertEquals(size, manifest.read().size)
					observations += """{"entries":$size,"reusedKeys":$reuse,"sample":$sample,"serializeMs":${spans.getValue("manifestSerializeMs")},"syncMs":${spans.getValue("manifestSyncMs")},"promoteMs":${spans.getValue("manifestPromoteMs")},"wholeWriteWallNanos":$wall,"wholeWriteThreadCpuNanos":$cpuNanos}"""
				}
			}
		}
		println("A407_DIGEST_BENCH_JSON:[${observations.joinToString(",")}]")
	}

	private fun key() = ReaderPageRasterKey(
		publicationHash = "publication-1", paginationHash = "pagination-1", spineIndex = 7,
		hrefHash = "href-1", chapterPageIndex = 3, visualPageOrdinal = 11,
		viewportWidth = 1_200, viewportHeight = 1_800, layoutHash = "layout-1",
		decorationHash = "decoration-1", quality = ReaderPageBitmapQuality.Balanced
	)

	private fun entry(key: ReaderPageRasterKey, index: Int) = ReaderPageRasterManifestEntry(
		key = key, rasterFileName = "raster-$index.png", byteSize = index.toLong(),
		lastAccessEpochMillis = index.toLong(), metadata = ReaderPageRasterMetadata(
			0, 0, 1_200, 1_800, null, null, null, null, 0
		)
	)

	private companion object {
		const val GoldenIdentity = """{"publicationHash":"publication-1","paginationHash":"pagination-1","spineIndex":7,"hrefHash":"href-1","chapterPageIndex":3,"visualPageOrdinal":11,"viewportWidth":1200,"viewportHeight":1800,"layoutHash":"layout-1","decorationHash":"decoration-1","quality":"50","schemaVersion":6}"""
		const val GoldenDigest = "3d894bcab028135ce3a332531c2d98051a710122c99f5636aee94bc2ea26c930"
	}
}
