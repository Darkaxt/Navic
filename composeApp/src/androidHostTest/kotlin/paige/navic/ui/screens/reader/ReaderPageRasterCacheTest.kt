package paige.navic.ui.screens.reader

import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageBitmapQuality

class ReaderPageRasterCacheTest {
	private val cacheSourceFile =
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageRasterCache.android.kt")

	@Test
	fun rasterEncodingAndDiskSyncHappenBeforeTheAtomicCacheCommitLock() {
		val source = cacheSourceFile.readText()
		val write = source
			.substringAfter("fun write(\n")
			.substringBefore("private fun commitWrite(")
		val encodeIndex = write.indexOf("codec.encode(value, temporary)")
		val syncIndex = write.indexOf("output.fd.sync()")
		val commitIndex = write.indexOf("commitWrite(")
		val commit = source
			.substringAfter("private fun commitWrite(")
			.substringBefore("private fun writeFailed(")

		assertTrue(encodeIndex >= 0, "Raster writes must encode the immutable value.")
		assertTrue(syncIndex > encodeIndex, "Disk synchronization must follow encoding.")
		assertTrue(
			commitIndex > syncIndex,
			"PNG encoding and fsync must precede the cache state commit."
		)
		assertTrue(
			"synchronized(this)" in commit,
			"The atomic cache commit must own the cache state monitor."
		)
		assertFalse(
			source.contains("@Synchronized\n\tfun write("),
			"The whole raster write must not be synchronized."
		)
	}

	@Test
	fun foregroundRasterReadIsNotBlockedByBackgroundEncoding() {
		val codec = ByteArrayRasterCodec()
		val fixture = fixture(codec = codec)
		val existingKey = key(chapterPageIndex = 1)
		val writingKey = key(chapterPageIndex = 2)
		assertTrue(fixture.cache.write(existingKey, metadata(), pngBytes("existing")))

		val encodeStarted = CountDownLatch(1)
		val releaseEncode = CountDownLatch(1)
		val readFinished = AtomicBoolean(false)
		codec.beforeEncode = {
			encodeStarted.countDown()
			releaseEncode.await()
		}
		val writer = Thread {
			fixture.cache.write(writingKey, metadata(), pngBytes("writing"))
		}
		writer.start()
		encodeStarted.await()

		val reader = Thread {
			val copied = fixture.cache.readCopy(existingKey) { cached -> cached.copyOf() }
			readFinished.set(copied?.value?.contentEquals(pngBytes("existing")) == true)
		}
		reader.start()
		var observationCount = 0
		while (!readFinished.get() && observationCount < 100) {
			Thread.sleep(5L)
			observationCount += 1
		}
		val completedBeforeEncodeRelease = readFinished.get()
		releaseEncode.countDown()
		writer.join()
		reader.join()

		assertTrue(
			completedBeforeEncodeRelease,
			"A foreground raster read must not wait for background PNG encoding or fsync."
		)
	}

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
			base.copy(schemaVersion = base.schemaVersion + 1)
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
		val diagnostics = mutableListOf<String>()
		val fixture = fixture(
			codec = ByteArrayRasterCodec(failEncoding = true),
			onDiagnostic = diagnostics::add
		)

		assertFalse(fixture.cache.write(key(), metadata(), pngBytes("broken")))

		assertFalse(fixture.cache.pathFor(key()).exists())
		assertFalse(fixture.cache.manifestPath().takeIf(File::exists)?.readText().orEmpty().contains(key().digest))
		assertTrue(diagnostics.single().contains("reason=encode-failed"))
		assertTrue(diagnostics.single().contains("availableBytes="))
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
		assertTrue(
			fixture.cache.metrics().diskBytes <= fixture.cache.metrics().diskByteLimit
		)
	}

	@Test
	fun encodedBlockingWindowIsPinnedAndFarthestBehindIsEvictedFirst() {
		val fixture = fixture(maxDiskBytes = 5L, maxDecodedEntries = 0)
		val farBehind = ownedKey(0)
		val nearBehind = ownedKey(3)
		val blocking = listOf(ownedKey(4), ownedKey(5), ownedKey(6))
		val forward = ownedKey(7)
		listOf(farBehind, nearBehind).forEach { rasterKey ->
			assertTrue(fixture.cache.write(rasterKey, metadata(), byteArrayOf(1)))
		}
		fixture.cache.protectEncodedWindow(
			profile = blocking.first().profile,
			centerPageOrdinal = 5,
			pinnedPageOrdinals = blocking.mapTo(linkedSetOf()) { key ->
				key.visualPageOrdinal
			}
		)
		blocking.forEach { rasterKey ->
			assertTrue(fixture.cache.write(rasterKey, metadata(), byteArrayOf(1)))
		}

		assertTrue(fixture.cache.write(forward, metadata(), byteArrayOf(1)))

		assertFalse(fixture.cache.contains(farBehind))
		assertTrue(fixture.cache.contains(nearBehind))
		blocking.forEach { rasterKey -> assertTrue(fixture.cache.contains(rasterKey)) }
		assertTrue(fixture.cache.contains(forward))
		assertEquals(5, fixture.cache.metrics().diskEntries)
	}

	@Test
	fun stagedEncodedWindowProtectionAppliesToTheNextInFlightWrite() {
		val fixture = fixture(maxDiskBytes = 4L, maxDecodedEntries = 0)
		val newBackwardEdge = ownedKey(0)
		val unpinned = ownedKey(4)
		val oldCenter = ownedKey(5)
		listOf(newBackwardEdge, unpinned, oldCenter).forEach { rasterKey ->
			assertTrue(fixture.cache.write(rasterKey, metadata(), byteArrayOf(1)))
		}
		fixture.cache.protectEncodedWindow(
			profile = oldCenter.profile,
			centerPageOrdinal = oldCenter.visualPageOrdinal,
			pinnedPageOrdinals = setOf(oldCenter.visualPageOrdinal)
		)
		fixture.cache.stageEncodedWindowProtection(
			profile = newBackwardEdge.profile,
			centerPageOrdinal = 1,
			pinnedPageOrdinals = setOf(newBackwardEdge.visualPageOrdinal)
		)

		fixture.cache.write(ownedKey(6), metadata(), byteArrayOf(1, 1))

		assertTrue(fixture.cache.contains(newBackwardEdge))
	}

	@Test
	fun unequalEncodedSizesNeverRetainFarBehindAfterDroppingNearBehind() {
		val fixture = fixture(maxDiskBytes = 5L, maxDecodedEntries = 0)
		val farBehind = ownedKey(0)
		val nearBehind = ownedKey(4)
		val pinned = ownedKey(5)
		assertTrue(fixture.cache.write(farBehind, metadata(), byteArrayOf(1)))
		assertTrue(fixture.cache.write(nearBehind, metadata(), byteArrayOf(1, 1, 1)))
		fixture.cache.protectEncodedWindow(
			profile = pinned.profile,
			centerPageOrdinal = pinned.visualPageOrdinal,
			pinnedPageOrdinals = setOf(pinned.visualPageOrdinal)
		)

		assertTrue(fixture.cache.write(pinned, metadata(), byteArrayOf(1, 1, 1)))

		assertTrue(fixture.cache.contains(pinned))
		assertFalse(fixture.cache.contains(nearBehind))
		assertFalse(fixture.cache.contains(farBehind))
	}

	@Test
	fun encodedPinsAreQualifiedByTheActiveRasterProfile() {
		val fixture = fixture(maxDiskBytes = 2L, maxDecodedEntries = 0)
		val active = ownedKey(5)
		val activeForward = ownedKey(6)
		val staleProfile = ownedKey(5).copy(layoutHash = "stale-layout")
		fixture.cache.protectEncodedWindow(
			profile = active.profile,
			centerPageOrdinal = active.visualPageOrdinal,
			pinnedPageOrdinals = setOf(active.visualPageOrdinal, activeForward.visualPageOrdinal)
		)
		assertTrue(fixture.cache.write(staleProfile, metadata(), byteArrayOf(1)))
		assertTrue(fixture.cache.write(active, metadata(), byteArrayOf(1)))

		assertTrue(fixture.cache.write(activeForward, metadata(), byteArrayOf(1)))

		assertFalse(fixture.cache.contains(staleProfile))
		assertTrue(fixture.cache.contains(active))
		assertTrue(fixture.cache.contains(activeForward))
	}

	@Test
	fun protectedChapterLargerThanDiskLimitIsNotRetained() {
		val value = pngBytes("oversized")
		val fixture = fixture(
			maxDiskBytes = value.size.toLong() - 1L,
			maxDecodedEntries = 1
		)
		val rasterKey = key(chapterPageIndex = 1)
		fixture.cache.protectChapter(rasterKey.chapter)

		val result = fixture.cache.write(rasterKey, metadata(), value)

		assertFalse(result.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, result.ownership)
		assertFalse(fixture.cache.contains(rasterKey))
		assertTrue(fixture.cache.metrics().diskBytes <= fixture.cache.metrics().diskByteLimit)
		assertEquals(0, fixture.cache.metrics().decodedEntries)
	}

	@Test
	fun writeExactlyAtDiskByteLimitIsRetained() {
		val value = pngBytes("exact")
		val fixture = fixture(
			maxDiskBytes = value.size.toLong(),
			maxDecodedEntries = 0
		)

		val result = fixture.cache.write(key(), metadata(), value)

		assertTrue(result.persisted)
		assertEquals(value.size.toLong(), fixture.cache.metrics().diskBytes)
		assertEquals(value.size.toLong(), fixture.cache.metrics().diskByteLimit)
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
	fun protectedDecodedWindowSurvivesFullChapterInsertion() {
		val codec = ByteArrayRasterCodec()
		val fixture = fixture(codec = codec, maxDecodedEntries = 2)
		val current = key(chapterPageIndex = 1).copy(visualPageOrdinal = 10)
		val adjacent = key(chapterPageIndex = 2).copy(visualPageOrdinal = 11)
		fixture.cache.protectDecodedPageIndices(setOf(10, 11))

		listOf(current, adjacent).forEach { rasterKey ->
			assertTrue(fixture.cache.write(rasterKey, metadata(), pngBytes(rasterKey.visualPageOrdinal.toString())))
		}
		(12..18).forEach { ordinal ->
			val rasterKey = key(chapterPageIndex = ordinal - 9).copy(visualPageOrdinal = ordinal)
			assertTrue(fixture.cache.write(rasterKey, metadata(), pngBytes(ordinal.toString())))
		}

		val decodeCallsBeforeReads = codec.decodeCalls
		assertContentEquals(pngBytes("10"), fixture.cache.read(current)?.value)
		assertContentEquals(pngBytes("11"), fixture.cache.read(adjacent)?.value)
		assertEquals(decodeCallsBeforeReads, codec.decodeCalls)
		assertEquals(2, fixture.cache.metrics().decodedEntries)
	}

	@Test
	fun memoryTrimDropsOnlyUnprotectedDecodedCopies() {
		val codec = ByteArrayRasterCodec()
		val fixture = fixture(codec = codec, maxDecodedEntries = 4)
		val protected = listOf(
			key(chapterPageIndex = 1).copy(visualPageOrdinal = 20),
			key(chapterPageIndex = 2).copy(visualPageOrdinal = 21)
		)
		val unprotected = listOf(
			key(chapterPageIndex = 3).copy(visualPageOrdinal = 22),
			key(chapterPageIndex = 4).copy(visualPageOrdinal = 23)
		)
		fixture.cache.protectDecodedPageIndices(protected.mapTo(mutableSetOf()) { it.visualPageOrdinal })
		(protected + unprotected).forEach { rasterKey ->
			assertTrue(fixture.cache.write(rasterKey, metadata(), pngBytes(rasterKey.visualPageOrdinal.toString())))
		}

		assertEquals(2, fixture.cache.trimDecodedToProtectedWindow())
		assertEquals(2, fixture.cache.metrics().decodedEntries)
		assertEquals(4, fixture.cache.metrics().diskEntries)
		val decodeCallsBeforeReads = codec.decodeCalls
		protected.forEach { rasterKey -> fixture.cache.read(rasterKey) }
		assertEquals(decodeCallsBeforeReads, codec.decodeCalls)
		fixture.cache.read(unprotected.first())
		assertEquals(decodeCallsBeforeReads + 1, codec.decodeCalls)
		assertEquals(4, fixture.cache.metrics().diskEntries)
	}

	@Test
	fun versionThreeManifestAndRasterArePrunedOnVersionFourCacheOpen() {
		assertEquals(4, ReaderPageRasterSchemaVersion)
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key()
		assertTrue(fixture.cache.write(rasterKey, metadata(), pngBytes("version-three")))
		val currentRaster = fixture.cache.pathFor(rasterKey)
		val versionThreeKey = rasterKey.copy(schemaVersion = 3)
		val obsoleteRaster = fixture.cache.pathFor(versionThreeKey)
		assertTrue(currentRaster.renameTo(obsoleteRaster))
		val manifest = fixture.cache.manifestPath()
		manifest.writeText(
			manifest.readText()
				.replace(currentRaster.name, obsoleteRaster.name)
				.replace(
					"\"schemaVersion\":$ReaderPageRasterSchemaVersion",
					"\"schemaVersion\":3"
				)
		)

		val reopened = ReaderPageRasterCache(
			root = fixture.root,
			codec = ByteArrayRasterCodec(),
			maxDiskBytes = 1_024L,
			maxDecodedEntries = 1
		)

		assertFalse(reopened.contains(rasterKey))
		assertFalse(obsoleteRaster.exists())
		assertEquals(0, reopened.metrics().diskEntries)
		assertEquals(0L, reopened.metrics().diskBytes)
		assertTrue(
			manifest.readText().contains(
				"\"schemaVersion\":4"
			)
		)
	}

	@Test
	fun versionFourManifestAndRasterRemainReadableOnCacheReopen() {
		assertEquals(4, ReaderPageRasterSchemaVersion)
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 4)
		val rasterMetadata = metadata()
		val value = pngBytes("version-four")
		assertTrue(fixture.cache.write(rasterKey, rasterMetadata, value))

		val reopened = ReaderPageRasterCache(
			root = fixture.root,
			codec = ByteArrayRasterCodec(),
			maxDiskBytes = 1_024L,
			maxDecodedEntries = 1
		)
		val restored = reopened.read(rasterKey)

		assertEquals(rasterMetadata, restored?.metadata)
		assertContentEquals(value, restored?.value)
		assertTrue(reopened.contains(rasterKey))
		assertEquals(1, reopened.metrics().diskEntries)
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
	fun publicationWritePersistsWithoutAdoptingTheCallersValue() {
		val fixture = fixture(maxDecodedEntries = 2)
		val rasterKey = key(chapterPageIndex = 4)
		val value = pngBytes("publication")

		val result = fixture.cache.write(
			key = rasterKey,
			metadata = metadata(),
			value = value,
			mode = ReaderPageRasterWriteMode.PersistOnly
		)

		assertTrue(result.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, result.ownership)
		assertEquals(0, fixture.cache.metrics().decodedEntries)
		assertTrue(fixture.cache.contains(rasterKey))
	}

	@Test
	fun schedulerWriteReportsWhenDecodedResidencyIsDisabled() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 5)

		val result = fixture.cache.write(
			key = rasterKey,
			metadata = metadata(),
			value = pngBytes("scheduler"),
			mode = ReaderPageRasterWriteMode.AdoptDecoded
		)

		assertTrue(result.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, result.ownership)
		assertEquals(0, fixture.cache.metrics().decodedEntries)
	}

	@Test
	fun protectedDecodedCapacityLeavesNewWriteCallerOwned() {
		val fixture = fixture(maxDecodedEntries = 1)
		val protected = key(chapterPageIndex = 5).copy(visualPageOrdinal = 20)
		val incoming = key(chapterPageIndex = 6).copy(visualPageOrdinal = 21)
		fixture.cache.protectDecodedPageIndices(setOf(20, 21))
		val first = fixture.cache.write(
			protected,
			metadata(),
			pngBytes("protected"),
			ReaderPageRasterWriteMode.AdoptDecoded
		)

		val second = fixture.cache.write(
			incoming,
			metadata(),
			pngBytes("incoming"),
			ReaderPageRasterWriteMode.AdoptDecoded
		)

		assertEquals(ReaderPageRasterValueOwnership.Store, first.ownership)
		assertTrue(second.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, second.ownership)
		assertEquals(1, fixture.cache.metrics().decodedEntries)
		assertTrue(fixture.cache.contains(incoming))
	}

	@Test
	fun schedulerWriteTransfersOwnershipOnlyAfterDecodedAdoption() {
		val fixture = fixture(maxDecodedEntries = 1)
		val rasterKey = key(chapterPageIndex = 6)

		val result = fixture.cache.write(
			key = rasterKey,
			metadata = metadata(),
			value = pngBytes("adopted"),
			mode = ReaderPageRasterWriteMode.AdoptDecoded
		)

		assertTrue(result.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Store, result.ownership)
		assertEquals(1, fixture.cache.metrics().decodedEntries)
	}

	@Test
	fun manifestFailureDoesNotPublishEntryOrLeavePromotedRaster() {
		val fixture = fixture(maxDecodedEntries = 1)
		val rasterKey = key(chapterPageIndex = 7)
		fixture.cache.manifestPath().delete()
		assertTrue(fixture.cache.manifestPath().mkdir())

		val result = fixture.cache.write(
			rasterKey,
			metadata(),
			pngBytes("manifest-failure"),
			ReaderPageRasterWriteMode.AdoptDecoded
		)

		assertFalse(result.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, result.ownership)
		assertFalse(fixture.cache.contains(rasterKey))
		assertEquals(0, fixture.cache.metrics().decodedEntries)
		assertFalse(fixture.root.listFiles().orEmpty().any { it.extension == "png" })
	}

	@Test
	fun rollbackManifestFailureLeavesReceiptOwnerIntact() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 8)
		val write = fixture.cache.write(
			rasterKey,
			metadata(),
			pngBytes("rollback-failure"),
			ReaderPageRasterWriteMode.PersistOnly
		)
		fixture.cache.manifestPath().delete()
		assertTrue(fixture.cache.manifestPath().mkdir())

		assertFailsWith<IllegalStateException> {
			fixture.cache.rollbackPublication(assertNotNull(write.receipt))
		}

		assertTrue(fixture.cache.contains(rasterKey))
		assertTrue(fixture.cache.pathFor(rasterKey).isFile)
	}

	@Test
	fun exactReceiptRollbackRemovesItsDurableEntry() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 7)
		val write = fixture.cache.write(
			rasterKey,
			metadata(),
			pngBytes("rollback"),
			ReaderPageRasterWriteMode.PersistOnly
		)

		assertTrue(fixture.cache.rollbackPublication(assertNotNull(write.receipt)))

		assertFalse(fixture.cache.contains(rasterKey))
		assertFalse(fixture.cache.manifestPath().readText().contains(rasterKey.digest))
		assertFalse(fixture.cache.pathFor(rasterKey).exists())
	}

	@Test
	fun staleReceiptCannotRemoveNewerSameKeyWrite() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 8)
		val stale = fixture.cache.write(
			rasterKey,
			metadata(),
			pngBytes("old"),
			ReaderPageRasterWriteMode.PersistOnly
		)
		val current = fixture.cache.write(
			rasterKey,
			metadata(),
			pngBytes("new"),
			ReaderPageRasterWriteMode.PersistOnly
		)

		assertFalse(
			fixture.cache.rollbackPublication(assertNotNull(stale.receipt))
		)

		assertTrue(current.persisted)
		assertContentEquals(pngBytes("new"), fixture.cache.read(rasterKey)?.value)
	}

	@Test
	fun retentionExclusionLeavesNoOrphanOrDecodedAdoption() {
		val fixture = fixture(maxDiskBytes = 7L, maxDecodedEntries = 1)
		val protected = key(chapterPageIndex = 9)
		val excluded = protected.copy(
			spineIndex = 8,
			hrefHash = "other-href",
			chapterPageIndex = 1,
			visualPageOrdinal = 12
		)
		assertTrue(fixture.cache.write(protected, metadata(), pngBytes("old")))
		fixture.cache.protectChapter(protected.chapter)

		val result = fixture.cache.write(
			excluded,
			metadata(),
			pngBytes("new"),
			ReaderPageRasterWriteMode.AdoptDecoded
		)

		assertFalse(result.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, result.ownership)
		assertFalse(fixture.cache.contains(excluded))
		assertEquals(1, fixture.cache.metrics().decodedEntries)
		assertEquals(1, fixture.root.listFiles().orEmpty().count { it.extension == "png" })
	}

	@Test
	fun rejectedCommitFenceDoesNotPromoteTemporaryFile() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 10)
		var commitCalled = false

		val result = fixture.cache.write(
			key = rasterKey,
			metadata = metadata(),
			value = pngBytes("stale"),
			mode = ReaderPageRasterWriteMode.PersistOnly,
			commitFence = ReaderPageRasterCommitFence {
				commitCalled = false
				ReaderPageRasterWriteResult(
					persisted = false,
					ownership = ReaderPageRasterValueOwnership.Caller
				)
			}
		)

		assertFalse(result.persisted)
		assertFalse(commitCalled)
		assertFalse(fixture.cache.contains(rasterKey))
		assertFalse(fixture.root.walkTopDown().any { it.name.endsWith(".tmp") })
	}

	@Test
	fun invalidationBeforeCommitFenceCannotPublishRaster() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 11)
		val value = pngBytes("before-fence")
		val ledger = ReaderPageRasterPublicationLedger<ByteArray> { }
		val registration = assertIs<
			ReaderPageRasterPublicationRegistration.Started
		>(ledger.begin(rasterKey.digest, value) { })
		assertSame(value, ledger.acquireForPersistence(registration.request))
		ledger.invalidate()

		val write = fixture.cache.write(
			key = rasterKey,
			metadata = metadata(),
			value = value,
			mode = ReaderPageRasterWriteMode.PersistOnly,
			commitFence = ledger.commitFence(registration.request)
		)

		assertFalse(write.persisted)
		assertFalse(ledger.complete(registration.request, write.persisted))
		assertFalse(fixture.cache.contains(rasterKey))
	}

	@Test
	fun newerSameKeyWriteSurvivesStalePublicationRollback() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key(chapterPageIndex = 12)
		val staleValue = pngBytes("publication")
		val ledger = ReaderPageRasterPublicationLedger<ByteArray> { }
		val registration = assertIs<
			ReaderPageRasterPublicationRegistration.Started
		>(ledger.begin(rasterKey.digest, staleValue) { })
		assertSame(
			staleValue,
			ledger.acquireForPersistence(registration.request)
		)
		val staleWrite = fixture.cache.write(
			key = rasterKey,
			metadata = metadata(),
			value = staleValue,
			mode = ReaderPageRasterWriteMode.PersistOnly,
			commitFence = ledger.commitFence(registration.request)
		)
		ledger.invalidate()
		val currentWrite = fixture.cache.write(
			rasterKey,
			metadata(),
			pngBytes("current"),
			ReaderPageRasterWriteMode.PersistOnly
		)

		assertFalse(
			ledger.complete(registration.request, staleWrite.persisted)
		)
		assertFalse(
			fixture.cache.rollbackPublication(
				assertNotNull(staleWrite.receipt)
			)
		)
		assertTrue(currentWrite.persisted)
		assertContentEquals(pngBytes("current"), fixture.cache.read(rasterKey)?.value)
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

	@Test
	fun conditionalRemovalCannotDeleteAReplacementRaster() {
		val fixture = fixture(maxDecodedEntries = 0)
		val rasterKey = key()
		val staleMetadata = metadata()
		val currentMetadata = staleMetadata.copy(
			leftLeafRect = ReaderPageRasterRect(0, 0, 40, 100),
			rightLeafRect = ReaderPageRasterRect(60, 0, 100, 100)
		)
		assertTrue(fixture.cache.write(rasterKey, staleMetadata, pngBytes("stale")))
		val staleRead = assertNotNull(
			fixture.cache.readCopy(rasterKey) { cached -> cached.copyOf() }
		)
		assertTrue(fixture.cache.write(rasterKey, currentMetadata, pngBytes("current")))

		assertFalse(fixture.cache.remove(rasterKey, staleRead.metadata))
		assertFalse(fixture.cache.contains(rasterKey, staleMetadata))
		assertTrue(fixture.cache.contains(rasterKey, currentMetadata))
		assertContentEquals(pngBytes("current"), fixture.cache.read(rasterKey)?.value)
	}

	@Test
	fun sharedIdentitySurvivesSingleKeyRemovalAndCapacityEviction() {
		val attempts = mutableListOf<Any>()
		val shared = Any()
		val distinct = Any()
		val cache = rasterCache<Any>(
			maxDecodedEntries = 2,
			release = attempts::add
		)
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)
		assertTrue(cache.write(ownedKey(2), metadata(), shared).persisted)

		assertTrue(cache.remove(ownedKey(1)))
		assertTrue(attempts.isEmpty())
		assertSame(shared, cache.read(ownedKey(2))?.value)

		assertTrue(cache.write(ownedKey(3), metadata(), distinct).persisted)
		cache.protectDecodedPageIndices(setOf(3))
		cache.trimDecodedToProtectedWindow()
		assertEquals(listOf(shared), attempts)
		assertSame(distinct, cache.read(ownedKey(3))?.value)
	}

	@Test
	fun fullyProtectedIdentityCapacityLeavesNewValueWithCaller() {
		val releases = mutableListOf<Any>()
		val protected = Any()
		val rejected = Any()
		val cache = rasterCache<Any>(
			maxDecodedEntries = 1,
			release = releases::add
		)
		cache.protectDecodedPageIndices(setOf(1))
		val first = cache.write(ownedKey(1), metadata(), protected)

		val second = cache.write(ownedKey(2), metadata(), rejected)

		assertTrue(first.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Store, first.ownership)
		assertTrue(second.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, second.ownership)
		assertSame(protected, cache.read(ownedKey(1))?.value)
		assertNull(cache.read(ownedKey(2)))
		assertTrue(releases.isEmpty())
		assertTrue(cache.remove(ownedKey(1)))
		assertEquals(listOf(protected), releases)
		assertFalse(releases.any { it === rejected })
	}

	@Test
	fun fullyProtectedDecodedWindowStillAllowsTransientCopiedRead() {
		val codec = ByteArrayRasterCodec()
		val fixture = fixture(codec = codec, maxDecodedEntries = 5)
		val protected = (0 until 5).map(::ownedKey)
		fixture.cache.protectDecodedPageIndices(
			protected.mapTo(linkedSetOf()) { key -> key.visualPageOrdinal }
		)
		protected.forEach { key ->
			assertTrue(fixture.cache.write(key, metadata(), pngBytes("protected-${key.visualPageOrdinal}")))
		}
		val transient = ownedKey(5)
		assertTrue(
			fixture.cache.write(
				transient,
				metadata(),
				pngBytes("transient"),
				ReaderPageRasterWriteMode.PersistOnly
			)
		)

		val copied = fixture.cache.readCopy(transient) { value -> value.copyOf() }

		assertContentEquals(pngBytes("transient"), copied?.value)
		assertEquals(metadata(), copied?.metadata)
		assertEquals(5, fixture.cache.metrics().decodedEntries)
		assertEquals(5, fixture.cache.metrics().uniqueDecodedBitmaps)
		assertEquals(1, codec.decodeCalls)
		assertEquals(1, codec.releaseCalls)
	}

	@Test
	fun profileRetentionReleasesSharedIdentityOnlyOnLastDetach() {
		val attempts = mutableListOf<Any>()
		val shared = Any()
		val cache = rasterCache<Any>(release = attempts::add)
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)
		assertTrue(cache.write(ownedKey(2), metadata(), shared).persisted)

		cache.retainProfile(ownedKey(1).profile.copy(layoutHash = "replacement"))

		assertEquals(listOf(shared), attempts)
		assertEquals(0, cache.metrics().uniqueDecodedBitmaps)
	}

	@Test
	fun closeUsesIdentityAndAttemptsEveryReleaseBeforeFailing() {
		class Value(val label: String)
		val attempts = mutableListOf<Value>()
		val cache = rasterCache<Value>(release = { value ->
			attempts += value
			error("release-failed-${value.label}")
		})
		val shared = Value("shared")
		val distinct = Value("distinct")
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)
		assertTrue(cache.write(ownedKey(2), metadata(), shared).persisted)
		assertTrue(cache.write(ownedKey(3), metadata(), distinct).persisted)

		val failure = assertFailsWith<IllegalStateException> { cache.close() }

		assertEquals(2, attempts.size)
		assertTrue(attempts.any { value -> value === shared })
		assertTrue(attempts.any { value -> value === distinct })
		assertEquals(1, failure.suppressed.size)
		assertEquals(0, cache.metrics().decodedEntries)
		assertEquals(0, cache.metrics().uniqueDecodedBitmaps)
		assertEquals(0, cache.metrics().pendingDecodedReleases)
		cache.close()
		assertEquals(2, attempts.size)
	}

	@Test
	fun protectedChapterTrimDetachesOnlyTheRetiredAlias() {
		var now = 1L
		val releases = mutableListOf<Any>()
		val shared = Any()
		val distinct = Any()
		val retired = ownedKey(1)
		val retained = ownedKey(2)
		val next = ownedKey(3).copy(spineIndex = 8, hrefHash = "next")
		val cache = rasterCache<Any>(
			maxDiskBytes = 2,
			maxDecodedEntries = 2,
			encodedBytesPerValue = 1,
			clock = { now++ },
			release = releases::add
		)
		cache.protectChapter(retired.chapter)
		assertTrue(cache.write(retired, metadata(), shared).persisted)
		assertTrue(cache.write(retained, metadata(), shared).persisted)
		assertEquals(2, cache.metrics().diskEntries)
		assertEquals(2L, cache.metrics().diskBytes)
		assertEquals(2L, cache.metrics().diskByteLimit)

		cache.protectChapter(next.chapter)
		assertTrue(cache.write(next, metadata(), distinct).persisted)

		assertEquals(2, cache.metrics().diskEntries)
		assertTrue(cache.metrics().diskBytes <= cache.metrics().diskByteLimit)
		assertFalse(cache.contains(retired))
		assertTrue(cache.contains(retained))
		assertTrue(cache.contains(next))
		assertSame(shared, cache.read(retained)?.value)
		assertTrue(releases.isEmpty())
		assertTrue(cache.remove(retained))
		assertEquals(listOf(shared), releases)
		cache.close()
		assertEquals(1, releases.count { it === distinct })
	}

	@Test
	fun corruptEntryRemovalDoesNotReleaseAStillReferencedIdentity() {
		val releases = mutableListOf<Any>()
		val shared = Any()
		val corrupt = ownedKey(1)
		val retained = ownedKey(2)
		val cache = rasterCache<Any>(
			maxDecodedEntries = 2,
			decode = { shared },
			release = releases::add
		)
		assertTrue(cache.write(corrupt, metadata(), shared).persisted)
		assertTrue(cache.write(retained, metadata(), shared).persisted)
		cache.protectDecodedPageIndices(setOf(retained.visualPageOrdinal))
		cache.trimDecodedToProtectedWindow()
		cache.pathFor(corrupt).writeText("corrupt")

		assertNull(cache.read(corrupt))
		assertSame(shared, cache.read(retained)?.value)
		assertTrue(releases.isEmpty())
		assertTrue(cache.remove(retained))
		assertEquals(listOf(shared), releases)
	}

	@Test
	fun sameDigestReplacementDoesNotDuplicateIdentityReferences() {
		val releases = mutableListOf<Any>()
		val shared = Any()
		val replacement = Any()
		val cache = rasterCache<Any>(release = releases::add)
		val rasterKey = ownedKey(1)
		assertTrue(cache.write(rasterKey, metadata(), shared).persisted)
		assertTrue(cache.write(rasterKey, metadata(), shared).persisted)

		assertTrue(cache.write(rasterKey, metadata(), replacement).persisted)
		assertEquals(listOf(shared), releases)
		assertSame(replacement, cache.read(rasterKey)?.value)
		assertTrue(cache.remove(rasterKey))
		assertEquals(listOf(shared, replacement), releases)
	}

	@Test
	fun writeStartingAfterReleaseCallbackEntryFailsBeforeEncode() {
		val releaseEntered = CountDownLatch(1)
		val allowRelease = CountDownLatch(1)
		val encodeCounts = IdentityHashMap<Any, Int>()
		val releases = mutableListOf<Any>()
		val shared = Any()
		val replacement = Any()
		val cache = rasterCache<Any>(
			maxDecodedEntries = 1,
			encode = { value, target ->
				synchronized(encodeCounts) {
					encodeCounts[value] = (encodeCounts[value] ?: 0) + 1
				}
				target.writeText("raster")
				true
			},
			release = { value ->
				synchronized(releases) { releases += value }
				if (value === shared) {
					releaseEntered.countDown()
					check(allowRelease.await(5, TimeUnit.SECONDS))
				}
			}
		)
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)
		val eviction = thread(start = true) {
			cache.write(ownedKey(2), metadata(), replacement)
		}
		assertTrue(releaseEntered.await(5, TimeUnit.SECONDS))
		var result: ReaderPageRasterWriteResult? = null
		val writeFinished = CountDownLatch(1)
		val writer = thread(start = true) {
			result = cache.write(ownedKey(3), metadata(), shared)
			writeFinished.countDown()
		}

		val completedWhileReleaseBlocked = writeFinished.await(1, TimeUnit.SECONDS)
		allowRelease.countDown()
		eviction.join(5_000)
		writer.join(5_000)

		assertTrue(completedWhileReleaseBlocked)
		val writeResult = assertNotNull(result)
		assertFalse(writeResult.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Store, writeResult.ownership)
		assertNull(writeResult.receipt)
		assertEquals(
			ReaderPageRasterWriteFailureReason.EncodeIdentityReleasing,
			writeResult.failureReason
		)
		assertEquals(1, synchronized(encodeCounts) { encodeCounts[shared] })
		assertFalse(eviction.isAlive)
		assertFalse(writer.isAlive)
		assertNull(cache.read(ownedKey(3)))
		cache.close()
		assertEquals(1, synchronized(releases) { releases.count { it === shared } })
	}

	@Test
	fun encodePinDefersLastAliasReleaseUntilEncodeAndAdoptionDecisionReturns() {
		val secondEncodeEntered = CountDownLatch(1)
		val allowSecondEncode = CountDownLatch(1)
		val releases = mutableListOf<Any>()
		val shared = Any()
		val sharedEncodeCount = AtomicInteger()
		val cache = rasterCache<Any>(
			encode = { value, target ->
				if (value === shared && sharedEncodeCount.incrementAndGet() == 2) {
					secondEncodeEntered.countDown()
					check(allowSecondEncode.await(5, TimeUnit.SECONDS))
				}
				target.writeText("raster")
				true
			},
			release = releases::add
		)
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)
		val writer = thread(start = true) {
			cache.write(ownedKey(2), metadata(), shared)
		}
		assertTrue(secondEncodeEntered.await(5, TimeUnit.SECONDS))

		assertTrue(cache.remove(ownedKey(1)))
		assertTrue(releases.isEmpty())
		allowSecondEncode.countDown()
		writer.join(5_000)
		assertFalse(writer.isAlive)
		assertSame(shared, cache.read(ownedKey(2))?.value)
		assertTrue(releases.isEmpty())
		assertTrue(cache.remove(ownedKey(2)))
		assertEquals(listOf(shared), releases)
	}

	@Test
	fun twoConcurrentEncodesOfSameIdentityReleaseOnlyAfterLastPin() {
		val bothEntered = CountDownLatch(2)
		val allowEncodes = CountDownLatch(1)
		val releases = mutableListOf<Any>()
		val shared = Any()
		val encodeCount = AtomicInteger()
		val cache = rasterCache<Any>(
			encode = { value, target ->
				if (value === shared && encodeCount.incrementAndGet() > 1) {
					bothEntered.countDown()
					check(allowEncodes.await(5, TimeUnit.SECONDS))
				}
				target.writeText("raster")
				true
			},
			release = releases::add
		)
		assertTrue(cache.write(ownedKey(0), metadata(), shared).persisted)
		val first = thread(start = true) {
			cache.write(ownedKey(1), metadata(), shared)
		}
		val second = thread(start = true) {
			cache.write(ownedKey(2), metadata(), shared)
		}
		assertTrue(bothEntered.await(5, TimeUnit.SECONDS))

		assertTrue(cache.remove(ownedKey(0)))
		assertTrue(releases.isEmpty())
		allowEncodes.countDown()
		first.join(5_000)
		second.join(5_000)
		assertFalse(first.isAlive)
		assertFalse(second.isAlive)
		assertTrue(cache.remove(ownedKey(1)))
		assertTrue(releases.isEmpty())
		assertTrue(cache.remove(ownedKey(2)))
		assertEquals(listOf(shared), releases)
	}

	@Test
	fun failedEncodeOfAlreadyOwnedIdentityStillReturnsStoreOwnership() {
		val shared = Any()
		var encodeCount = 0
		val releases = mutableListOf<Any>()
		val cache = rasterCache<Any>(
			encode = { _, target ->
				encodeCount += 1
				if (encodeCount == 1) target.writeText("raster")
				encodeCount == 1
			},
			release = releases::add
		)
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)

		val failed = cache.write(ownedKey(2), metadata(), shared)

		assertFalse(failed.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Store, failed.ownership)
		assertTrue(releases.isEmpty())
		assertTrue(cache.remove(ownedKey(1)))
		assertEquals(listOf(shared), releases)
	}

	@Test
	fun persistOnlyRejectsDecodedIdentityBeforeEncode() {
		val shared = Any()
		var encodeCount = 0
		val cache = rasterCache<Any>(
			encode = { _, target ->
				encodeCount += 1
				target.writeText("raster")
				true
			}
		)
		assertTrue(cache.write(ownedKey(1), metadata(), shared).persisted)

		assertFailsWith<IllegalStateException> {
			cache.write(
				key = ownedKey(2),
				metadata = metadata(),
				value = shared,
				mode = ReaderPageRasterWriteMode.PersistOnly
			)
		}
		assertEquals(1, encodeCount)
	}

	@Test
	fun ownershipObserverTracksDecodedAdmissionAndReleaseButNotNoOps() {
		var mutations = 0
		val cache = rasterCache<Any>(
			maxDecodedEntries = 1,
			onOwnershipMutated = { mutations += 1 }
		)
		val rasterKey = ownedKey(1)

		assertTrue(cache.write(rasterKey, metadata(), Any()).persisted)
		assertEquals(1, cache.metrics().decodedEntries)
		val afterAdmission = mutations
		assertTrue(afterAdmission > 0)

		assertNotNull(cache.read(rasterKey))
		assertEquals(afterAdmission, mutations)
		assertTrue(cache.remove(rasterKey))
		assertEquals(0, cache.metrics().decodedEntries)
		assertTrue(mutations > afterAdmission)
		val afterRelease = mutations
		assertFalse(cache.remove(rasterKey))
		assertEquals(afterRelease, mutations)
	}

	@Test
	fun closeLeavesNoEncodePinsOrPinnedIdentitiesAndRetainsBounds() {
		val cache = rasterCache<Any>(maxDiskBytes = 4, maxDecodedEntries = 2)
		assertTrue(cache.write(ownedKey(1), metadata(), Any()).persisted)

		cache.close()

		val metrics = cache.metrics()
		assertEquals(0, metrics.decodedEntries)
		assertEquals(0, metrics.uniqueDecodedBitmaps)
		assertEquals(2, metrics.uniqueDecodedBitmapLimit)
		assertEquals(0, metrics.pendingDecodedReleases)
		assertEquals(0, metrics.activeEncodePins)
		assertEquals(0, metrics.encodePinnedIdentities)
		assertEquals(4L, metrics.diskByteLimit)
		assertTrue(metrics.diskBytes <= metrics.diskByteLimit)
	}

	private fun assertTrue(result: ReaderPageRasterWriteResult) {
		kotlin.test.assertTrue(result.persisted)
	}

	private fun assertFalse(result: ReaderPageRasterWriteResult) {
		kotlin.test.assertFalse(result.persisted)
	}

	private fun <T : Any> rasterCache(
		maxDiskBytes: Long = 1_024L,
		maxDecodedEntries: Int = 3,
		encodedBytesPerValue: Int = 1,
		clock: () -> Long = System::currentTimeMillis,
		encode: (T, File) -> Boolean = { _, target ->
			target.writeBytes(ByteArray(encodedBytesPerValue) { 1 })
			true
		},
		decode: (File) -> T? = { null },
		release: (T) -> Unit = {},
		onOwnershipMutated: () -> Unit = {}
	): ReaderPageRasterCache<T> {
		val root = createTempDirectory("navic-reader-page-raster-owners").toFile()
		return ReaderPageRasterCache(
			root = root,
			codec = object : ReaderPageRasterCodec<T> {
				override fun encode(value: T, target: File): Boolean =
					encode(value, target)

				override fun decode(source: File): T? = decode(source)

				override fun release(value: T) = release(value)
			},
			maxDiskBytes = maxDiskBytes,
			maxDecodedEntries = maxDecodedEntries,
			clock = clock,
			onOwnershipMutated = onOwnershipMutated
		)
	}

	private fun fixture(
		codec: ByteArrayRasterCodec = ByteArrayRasterCodec(),
		maxDiskBytes: Long = 1_024L,
		maxDecodedEntries: Int = 2,
		clock: () -> Long = System::currentTimeMillis,
		onDiagnostic: (String) -> Unit = {},
		onOwnershipMutated: () -> Unit = {}
	): CacheFixture {
		val root = createTempDirectory("navic-reader-page-raster-cache").toFile()
		return CacheFixture(
			root = root,
			cache = ReaderPageRasterCache(
				root = root,
				codec = codec,
				maxDiskBytes = maxDiskBytes,
				maxDecodedEntries = maxDecodedEntries,
				clock = clock,
				onDiagnostic = onDiagnostic,
				onOwnershipMutated = onOwnershipMutated
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

	private fun ownedKey(page: Int): ReaderPageRasterKey =
		key(chapterPageIndex = page).copy(visualPageOrdinal = page)

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
		var beforeEncode: (() -> Unit)? = null
		var decodeCalls = 0
			private set
		var releaseCalls = 0
			private set

		override fun encode(value: ByteArray, target: File): Boolean {
			beforeEncode?.invoke()
			if (failEncoding) return false
			target.writeBytes(value)
			return true
		}

		override fun decode(source: File): ByteArray? {
			decodeCalls += 1
			return source.readBytes().takeIf { bytes -> bytes.decodeToString().startsWith("PNG:") }
		}

		override fun release(value: ByteArray) {
			releaseCalls += 1
		}
	}
}
