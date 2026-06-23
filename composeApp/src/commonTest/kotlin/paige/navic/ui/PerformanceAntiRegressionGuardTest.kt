package paige.navic.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source-level guards that lock in performance-critical structure so the regressions
 * targeted by the performance plan cannot silently return. Same idiom as
 * [paige.navic.domain.models.PlaybackArtworkSurfacePolicyTest].
 */
class PerformanceAntiRegressionGuardTest {
	private fun appSource() =
		File("src/commonMain/kotlin/paige/navic/App.kt").readText()

	@Test
	fun entryProviderIsRememberedNotRebuiltEveryRecomposition() {
		val source = appSource()
		assertTrue(
			Regex("""remember\s*\(\s*backStack\.size\s*\)\s*\{[^}]*entryProvider\s*\(""").containsMatchIn(source),
			"App.kt must remember(backStack.size) { entryProvider(backStack) } so the ~57-entry map isn't rebuilt on every recomposition."
		)
		assertFalse(
			"\t\t\t\t\tentryProvider = entryProvider(backStack)," in source,
			"App.kt must not pass an un-remembered entryProvider(backStack) to NavDisplay."
		)
	}
}
