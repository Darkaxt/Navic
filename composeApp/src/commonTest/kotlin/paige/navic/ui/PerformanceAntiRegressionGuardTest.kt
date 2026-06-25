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

	@Test
	fun songListKeysItemsBySongId() {
		val source = File("src/commonMain/kotlin/paige/navic/ui/screens/song/components/Content.kt").readText()
		assertTrue(
			Regex("""items\s*\(\s*data\s*,\s*key\s*=\s*\{\s*it\.id\s*\}""").containsMatchIn(source),
			"Song list must key items by song id."
		)
	}

	@Test
	fun searchSongResultsAreKeyedById() {
		val source = File("src/commonMain/kotlin/paige/navic/ui/screens/search/SearchScreen.kt").readText()
		assertTrue(
			"key = { songs[it].id }" in source,
			"Search song results must be keyed by song id."
		)
	}

	@Test
	fun songRowsDoNotSubscribeToPlayerUiStatePerRow() {
		listOf(
			"src/commonMain/kotlin/paige/navic/ui/components/common/SongRow.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/collection/components/SongRow.kt"
		).forEach { path ->
			val source = File(path).readText()
			assertFalse(
				"player.uiState.collectAsState" in source,
				"$path must not subscribe to MediaPlayerViewModel.uiState per row; pass isCurrentTrack/isPlaying from the screen."
			)
		}
	}

	@Test
	fun songSubtitlesAreRememberedNotRebuiltPerRecomposition() {
		listOf(
			"src/commonMain/kotlin/paige/navic/ui/components/common/SongRow.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/song/components/Item.kt"
		).forEach { path ->
			val source = File(path).readText()
			assertTrue(
				Regex(
					"""val\s+subtitle\s*=\s*remember\s*\([^)]*\)\s*\{\s*buildString\s*\{""",
					RegexOption.DOT_MATCHES_ALL
				).containsMatchIn(source),
				"$path must remember subtitle buildString work so row recomposition does not rebuild unchanged subtitles."
			)
		}
	}

	@Test
	fun refreshViewModelsCancelPreviousCollectorBeforeRelaunch() {
		listOf(
			"src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistListViewModel.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/song/viewmodels/SongListViewModel.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/playlist/viewmodels/PlaylistListViewModel.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/genre/viewmodels/GenreListViewModel.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/radio/viewmodels/RadioListViewModel.kt"
		).forEach { path ->
			val source = File(path).readText()
			assertTrue(
				Regex("""\w+Job\s*\?\s*\.\s*cancel\s*\(\s*\)""").containsMatchIn(source),
				"$path must cancel its previous refresh Job before relaunching (see AlbumListViewModel)."
			)
		}
	}

	@Test
	fun artistPhotoCacheIsCollectedOnceViaCompositionLocalNotPerRow() {
		val state = File("src/commonMain/kotlin/paige/navic/ui/components/common/PlaybackArtworkState.kt").readText()
		assertTrue(
			"LocalArtistPhotoEntries.current?.let { return it }" in state,
			"rememberPlaybackArtistPhotoCacheEntries must read the shared LocalArtistPhotoEntries snapshot before falling back to collecting."
		)
		val app = File("src/commonMain/kotlin/paige/navic/App.kt").readText()
		assertTrue(
			"LocalArtistPhotoEntries provides artistPhotoEntries" in app,
			"App must provide the artist-photo snapshot once via LocalArtistPhotoEntries."
		)
	}

	@Test
	fun albumCardOnlyCollectsDownloadStatusWhenSelected() {
		val lines = File("src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt").readLines()
		val selectedLine = lines.indexOfFirst { it.contains("if (selected)") }
		val collectLine = lines.indexOfLast { it.contains("getCollectionDownloadStatus") }
		assertTrue(
			selectedLine in 0..<collectLine,
			"Album card must collect getCollectionDownloadStatus only inside the if (selected) branch, not for every card."
		)
	}

	@Test
	fun coverArtArtGridAreMadeSkippableViaStabilityConfig() {
		val conf = File("stability_config.conf")
		assertTrue(conf.isFile, "composeApp/stability_config.conf must exist to mark Map stable.")
		val text = conf.readText()
		assertTrue("kotlin.collections.Map" in text, "stability config must mark kotlin.collections.Map stable.")
		assertTrue(
			"stabilityConfigurationFile" in File("build.gradle.kts").readText(),
			"build.gradle.kts must wire stabilityConfigurationFile into composeCompiler."
		)
	}

	@Test
	fun miniPlayerDoesNotCollectFullUiState() {
		val source = File("src/commonMain/kotlin/paige/navic/ui/components/layouts/MiniPlayer.kt").readText()
		assertFalse(
			"player.uiState.collectAsState" in source,
			"MiniPlayer must not collect the full player.uiState; use the narrow flows to avoid recomposing on every progress tick."
		)
		assertTrue(
			"player.progressFlow.collectAsState" in source,
			"MiniPlayer progress overlay must isolate progress via progressFlow."
		)
	}
}
