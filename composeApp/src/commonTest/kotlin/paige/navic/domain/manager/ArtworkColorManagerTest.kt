package paige.navic.domain.manager

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import paige.navic.data.database.dao.ArtworkColorDao
import paige.navic.data.database.entities.ArtworkColorEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkColorManagerTest {
	@Test
	fun changedSourceIdentityInvalidatesStoredColor() = runBlocking {
		val dao = FakeArtworkColorDao()
		var now = 1_000L
		val manager = ArtworkColorManager(dao) { now }
		manager.putColor("album:1", "url:first", Color.Red)

		assertNull(manager.getColor("album:1", "url:second"))
		assertNull(dao.entry)
	}

	@Test
	fun expiredEntryIsDeletedInsteadOfReturned() = runBlocking {
		val dao = FakeArtworkColorDao()
		var now = 1_000L
		val manager = ArtworkColorManager(dao) { now }
		manager.putColor("album:1", "url:first", Color.Blue)
		now += ArtworkColorCacheTtl.inWholeMilliseconds + 1

		assertNull(manager.getColor("album:1", "url:first"))
		assertNull(dao.entry)
	}

	@Test
	fun putPrunesExpiredRowsAndBoundsPersistentCache() = runBlocking {
		val dao = FakeArtworkColorDao()
		val manager = ArtworkColorManager(dao) { 50_000L }

		manager.putColor("album:1", "url:first", Color.Green)

		assertEquals(50_000L - ArtworkColorCacheTtl.inWholeMilliseconds, dao.lastCutoff)
		assertEquals(ArtworkColorCacheMaxEntries, dao.lastMaxEntries)
	}

	@Test
	fun clearDropsMemoryAndPersistentRows() = runBlocking {
		val dao = FakeArtworkColorDao()
		val manager = ArtworkColorManager(dao) { 1_000L }
		manager.putColor("album:1", "url:first", Color.Magenta)

		manager.clear()

		assertTrue(dao.cleared)
		assertNull(manager.getColor("album:1", "url:first"))
	}
}

private class FakeArtworkColorDao : ArtworkColorDao {
	var entry: ArtworkColorEntity? = null
	var lastCutoff: Long? = null
	var lastMaxEntries: Int? = null
	var cleared = false

	override suspend fun getColor(artworkKey: String): ArtworkColorEntity? =
		entry?.takeIf { it.artworkKey == artworkKey }

	override suspend fun upsertColor(color: ArtworkColorEntity) {
		entry = color
	}

	override suspend fun deleteColor(artworkKey: String) {
		if (entry?.artworkKey == artworkKey) entry = null
	}

	override suspend fun deleteOlderThan(cutoffEpochMillis: Long) {
		lastCutoff = cutoffEpochMillis
		if (entry?.updatedAtEpochMillis?.let { it < cutoffEpochMillis } == true) entry = null
	}

	override suspend fun trimToNewest(maxEntries: Int) {
		lastMaxEntries = maxEntries
	}

	override suspend fun clearAll() {
		entry = null
		cleared = true
	}
}
