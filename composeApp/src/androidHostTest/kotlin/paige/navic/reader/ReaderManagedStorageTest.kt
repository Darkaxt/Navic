package paige.navic.reader

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderManagedStorageTest {
	@Test
	fun initializationMigratesFontsAndDropsReconstructableSessions() {
		val root = createTempDirectory("navic-reader-managed-storage").toFile()
		val managedRoot = root.resolve("managed")
		val legacyRoot = root.resolve("legacy")
		managedRoot.resolve("fonts/imported-existing.ttf").writeFixture("MANAGED")
		legacyRoot.resolve("fonts/imported-existing.ttf").writeFixture("LEGACY")
		legacyRoot.resolve("fonts/imported-migrated.otf").writeFixture("MIGRATED")
		managedRoot.resolve("reader-publications/current/publication.epub").writeFixture("STALE")
		managedRoot.resolve("storyteller-readaloud/current/audio.mp3").writeFixture("STALE_AUDIO")
		managedRoot.resolve("reader-page-rasters/v1/keep.png").writeFixture("DURABLE_RASTER")
		legacyRoot.resolve("reader-publications/legacy/publication.epub").writeFixture("LEGACY_STALE")
		legacyRoot.resolve("storyteller-readaloud/legacy/audio.mp3").writeFixture("LEGACY_AUDIO")

		initializeReaderManagedStorage(managedRoot, legacyRoot)

		assertEquals("MANAGED", managedRoot.resolve("fonts/imported-existing.ttf").readText())
		assertEquals("MIGRATED", managedRoot.resolve("fonts/imported-migrated.otf").readText())
		assertFalse(legacyRoot.resolve("fonts/imported-migrated.otf").exists())
		assertFalse(managedRoot.resolve("reader-publications").exists())
		assertFalse(managedRoot.resolve("storyteller-readaloud").exists())
		assertFalse(legacyRoot.resolve("reader-publications").exists())
		assertFalse(legacyRoot.resolve("storyteller-readaloud").exists())
		assertTrue(managedRoot.resolve("fonts").isDirectory)
		assertEquals("DURABLE_RASTER", managedRoot.resolve("reader-page-rasters/v1/keep.png").readText())
	}

	@Test
	fun sessionLeaseIsScopedAndIdempotent() {
		val root = createTempDirectory("navic-reader-session-lease").toFile()
		val publication = root.resolve("reader-publications/book-1")
		val readaloud = root.resolve("storyteller-readaloud/book-1")
		val unrelated = root.resolve("unrelated")
		publication.resolve("publication.epub").writeFixture("EPUB")
		readaloud.resolve("audio.mp3").writeFixture("AUDIO")
		unrelated.resolve("keep.txt").writeFixture("KEEP")

		val lease = ReaderSessionLease.of(publication, readaloud, unrelated)

		assertEquals(2, lease.release())
		assertEquals(0, lease.release())
		assertFalse(publication.exists())
		assertFalse(readaloud.exists())
		assertEquals("KEEP", unrelated.resolve("keep.txt").readText())
	}

	@Test
	fun sessionAccountingCoversManagedAndLegacyRootsButExcludesFonts() {
		val root = createTempDirectory("navic-reader-session-accounting").toFile()
		val managedRoot = root.resolve("managed")
		val legacyRoot = root.resolve("legacy")
		managedRoot.resolve("reader-publications/book/publication.epub").writeFixture("EPUB")
		managedRoot.resolve("storyteller-readaloud/book/audio.mp3").writeFixture("AUDIO")
		managedRoot.resolve("fonts/user.ttf").writeFixture("DURABLE_FONT")
		legacyRoot.resolve("reader-publications/book/publication.epub").writeFixture("OLD")
		legacyRoot.resolve("storyteller-readaloud/book/audio.mp3").writeFixture("OLD_AUDIO")
		legacyRoot.resolve("other/keep.txt").writeFixture("KEEP")

		assertEquals(
			"EPUB".length + "AUDIO".length + "OLD".length + "OLD_AUDIO".length.toLong(),
			readerSessionStorageSizeBytes(managedRoot, legacyRoot)
		)

		assertEquals(4, clearReaderSessionStorage(managedRoot, legacyRoot))
		assertEquals(0L, readerSessionStorageSizeBytes(managedRoot, legacyRoot))
		assertEquals("DURABLE_FONT", managedRoot.resolve("fonts/user.ttf").readText())
		assertEquals("KEEP", legacyRoot.resolve("other/keep.txt").readText())
	}

	private fun java.io.File.writeFixture(content: String) {
		parentFile?.mkdirs()
		writeText(content)
	}
}
