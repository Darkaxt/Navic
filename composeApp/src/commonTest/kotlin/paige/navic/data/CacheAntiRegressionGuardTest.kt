package paige.navic.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source-level guards for the in-memory cache / reload-on-return work
 * (plan: Navic navic-cache-refactor-plan.md). Same idiom as PerformanceAntiRegressionGuardTest.
 */
class CacheAntiRegressionGuardTest {
	@Test
	fun rawAlbumRelationQueriesUseOneDatabaseSnapshot() {
		val src = File("src/commonMain/kotlin/paige/navic/data/database/dao/AlbumDao.kt").readText()
		assertTrue(
			Regex("""@Transaction\s+@RawQuery\s+suspend fun getAlbumsByQuery\(""")
				.containsMatchIn(src),
			"Raw AlbumWithSongs reads must be transactional so parent albums and related songs come from one database snapshot."
		)
		assertTrue(
			Regex("""@Transaction\s+@RawQuery\(observedEntities\s*=\s*\[AlbumEntity::class,\s*SongEntity::class\]\)\s+fun getAlbumsByQueryFlow\(""")
				.containsMatchIn(src),
			"The reactive AlbumWithSongs query must be transactional; otherwise Room can rebuild its relation map while albums are being synchronized."
		)
	}

	@Test
	fun librarySectionRefreshesAreGatedOnAlreadyLoadedNotReentry() {
		val src = File("src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryScreen.kt").readText()
		assertTrue(
			Regex("""albumsState\.value\s*!\s*is\s*UiState\.Success""").containsMatchIn(src),
			"LibraryScreen must gate section refreshes on already-loaded state (!is UiState.Success) so returning to the tab doesn't re-query Room for every section."
		)
	}

	@Test
	fun playlistScreenReservesNetworkRefreshForPullToRefresh() {
		val src = File("src/commonMain/kotlin/paige/navic/ui/screens/playlist/PlaylistListScreen.kt").readText()
		val trueCount = Regex("refreshPlaylists\\(true\\)").findAll(src).count()
		assertEquals(
			3, trueCount,
			"refreshPlaylists(true) must be reserved for the 3 pull-to-refresh handlers; reactive/re-entry events must use false to avoid blocking on the network."
		)
	}

	@Test
	fun quickPicksRefreshIsGatedByLibraryRowVisibility() {
		val src = File("src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryScreen.kt").readText()
		assertTrue(
			src.contains("quickPicksRowVisible"),
			"Quick Picks refresh must check the Library row visibility preference, not only the legacy quickPicksEnabled value."
		)
		assertTrue(
			Regex("""if\s*\(\s*quickPicksEnabled\s*&&\s*quickPicksRowVisible\s*\)""").containsMatchIn(src),
			"Quick Picks refresh should be skipped when the row is hidden."
		)
	}
}
