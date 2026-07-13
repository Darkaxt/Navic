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
	}

	private fun java.io.File.writeFixture(content: String) {
		parentFile?.mkdirs()
		writeText(content)
	}
}
