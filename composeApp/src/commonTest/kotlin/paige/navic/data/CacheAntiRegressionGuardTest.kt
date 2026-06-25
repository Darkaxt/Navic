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
}

