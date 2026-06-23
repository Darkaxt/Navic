package paige.navic.domain.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackArtworkSurfacePolicyTest {
	@Test
	fun highVolumeSongSurfacesUsePlaybackArtworkPolicyInsteadOfRawNavidromeCoverArt() {
		val playbackSongCoverSurfaces = listOf(
			"src/commonMain/kotlin/paige/navic/ui/components/common/SongRow.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/song/components/Item.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/queue/components/Item.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/collection/components/SongRow.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/UpNextRow.kt",
			"src/commonMain/kotlin/paige/navic/ui/components/sheets/SongSheet.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/search/SearchScreen.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/lyrics/LyricsScreen.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/musicBrainz/MusicBrainzInfoScreen.kt"
		)

		playbackSongCoverSurfaces.forEach { path ->
			val source = File(path).readText()
			val rawSongCoverArtCall = Regex(
				"""(?<!PlaybackSong)CoverArt\s*\([\s\S]{0,500}coverArtId\s*=\s*song\.coverArtId"""
			)
			assertTrue(
				"PlaybackSongCoverArt(" in source || "rememberPlaybackArtworkUiState(" in source,
				"$path must route song artwork through shared playback artwork state so external/Aurral artwork can beat Navidrome."
			)
			assertFalse(
				rawSongCoverArtCall.containsMatchIn(source),
				"$path must not pass raw song.coverArtId directly to CoverArt."
			)
		}
	}

	@Test
	fun quickPickSongCardsUsePlaybackArtworkState() {
		val source = File("src/commonMain/kotlin/paige/navic/ui/screens/library/components/QuickPickSongCard.kt").readText()

		assertTrue(
			"rememberPlaybackArtworkUiState(" in source,
			"Quick picks must route song artwork through shared playback artwork state so external/Aurral artwork can beat Navidrome."
		)
		assertFalse(
			"coverArtId = song.coverArtId" in source,
			"Quick picks must not pass raw song.coverArtId directly to ArtGridItem."
		)
	}

	@Test
	fun dynamicPlaybackBackgroundsUseVisiblePlaybackArtworkPolicy() {
		val dynamicBackgroundSurfaces = listOf(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/musicBrainz/MusicBrainzInfoScreen.kt"
		)

		dynamicBackgroundSurfaces.forEach { path ->
			val source = File(path).readText()
			assertTrue(
				"rememberPlaybackArtworkUiState(" in source,
				"$path must use shared playback artwork state before rendering dynamic backgrounds."
			)
			assertFalse(
				Regex("""BlendBackground\s*\([\s\S]{0,500}coverArtId\s*=\s*song\?\.coverArtId""")
					.containsMatchIn(source),
				"$path must not pass the raw Navidrome cover ID directly into dynamic playback backgrounds."
			)
		}
	}

	@Test
	fun nowPlayingPaletteExtractionUsesVisiblePlaybackArtworkPolicy() {
		val path = "src/commonMain/kotlin/paige/navic/ui/navigation/NowPlayingScene.kt"
		val source = File(path).readText()

		assertTrue(
			"rememberPlaybackArtworkUiState(" in source,
			"$path must use shared playback artwork state before palette extraction."
		)
		assertTrue(
			"dominantColorArtworkUrl(" in source,
			"$path must route palette extraction through the shared dominant color artwork policy."
		)
		assertFalse(
			"song?.coverArtId?.let { sessionManager.getCoverArtUrl(it) }" in source,
			"$path must not build a visible Navidrome artwork URL before applying the playback artwork policy."
		)
	}

	@Test
	fun sharedPlaybackArtworkHelperUsesCoverArtworkPriorityForSongCovers() {
		val path = "src/commonMain/kotlin/paige/navic/ui/components/common/PlaybackArtworkState.kt"
		val source = File(path).readText()

		assertTrue(
			"resolvedPlaybackArtwork(" in source,
			"$path must route song cover artwork through the shared resolution policy."
		)
		assertTrue(
			"artworkSourcePriority = preferenceManager.coverArtworkPriority" in source,
			"$path must use coverArtworkPriority for song/album cover surfaces."
		)
		assertTrue(
			"artworkSourcePriority = preferenceManager.artistArtworkPriority" in source,
			"$path must still use artistArtworkPriority for artist image surfaces."
		)
	}

	@Test
	fun genericArtworkComponentsGateNativeFallbackThroughAurralPolicy() {
		val visibleArtworkComponents = listOf(
			"src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt",
			"src/commonMain/kotlin/paige/navic/ui/components/common/BlendBackground.kt"
		)

		visibleArtworkComponents.forEach { path ->
			val source = File(path).readText()
			assertTrue(
				"visibleCoverArtIdForAurralPolicy(" in source,
				"$path must route native/Navidrome cover fallback through the Aurral visibility policy."
			)
			assertTrue(
				"visibleImageUrlForAurralPolicy(" in source,
				"$path must route visible image URLs through the Aurral visibility policy."
			)
			assertFalse(
				Regex("""\.data\s*\(\s*resolvedImageUrl\s*\?:\s*coverArtId\?\.let""").containsMatchIn(source),
				"$path must not build visible Navidrome image requests from raw coverArtId."
			)
			assertFalse(
				Regex("""\.data\s*\(\s*artwork\?\.imageUrl\s*\?:\s*artwork\?\.coverArtId\?\.let""").containsMatchIn(source),
				"$path must not build visible Navidrome image requests from raw playback cover IDs."
			)
		}
		val miniPlayerSource = File("src/commonMain/kotlin/paige/navic/ui/components/layouts/MiniPlayer.kt").readText()
		assertTrue(
			"rememberPlaybackArtworkUiState(" in miniPlayerSource,
			"MiniPlayer must receive policy-selected artwork from the shared playback state."
		)
		assertFalse(
			Regex("""\.data\s*\(\s*artwork\?\.imageUrl\s*\?:\s*artwork\?\.coverArtId\?\.let""")
				.containsMatchIn(miniPlayerSource),
			"MiniPlayer must not build visible Navidrome image requests from raw playback cover IDs."
		)
	}

	@Test
	fun artistSurfacesUseAurralArtistImagePolicyInsteadOfRawNavidromeArtistImages() {
		val artistSurfaces = listOf(
			"src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistListScreen.kt" to "rememberArtistArtworkUiState(",
			"src/commonMain/kotlin/paige/navic/ui/components/sheets/ArtistSheet.kt" to "AurralFirstArtistCoverArt(",
			"src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt" to "rememberAurralFirstArtistArtworkUiState(",
			"src/commonMain/kotlin/paige/navic/ui/screens/genre/GenreDetailScreen.kt" to "rememberArtistArtworkUiState(",
			"src/commonMain/kotlin/paige/navic/ui/screens/starred/components/Content.kt" to "rememberArtistArtworkUiState("
		)

		artistSurfaces.forEach { (path, expectedResolver) ->
			val source = File(path).readText()
			assertTrue(
				expectedResolver in source,
				"$path must route artist image URLs through the Aurral-first artist image policy."
			)
			assertFalse(
				Regex("""imageUrl\s*=\s*artist\.artistImageUrl""").containsMatchIn(source),
				"$path must not pass raw artist.artistImageUrl directly; it can be a Navidrome getArtistImage URL."
			)
		}
	}

	@Test
	fun aurralArtistHydrationDoesNotStopAtSmallFixedBatches() {
		val artistListViewModel = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/artist/viewmodels/ArtistListViewModel.kt"
		).readText()
		val mostPlayedViewModel = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/library/MostPlayedShortcutsViewModel.kt"
		).readText()

		assertFalse(
			"limit = AURRAL_ARTIST_PHOTO_LOOKUP_LIMIT" in artistListViewModel,
			"Artist list Aurral artwork hydration must not stop after a small fixed batch while Aurral is enabled."
		)
		assertFalse(
			"targets.size >= AURRAL_ARTIST_PHOTO_LOOKUP_LIMIT" in mostPlayedViewModel,
			"Most-played artist artwork hydration must not stop after a small fixed batch while Aurral is enabled."
		)
	}

	@Test
	fun aurralArtistHeroDoesNotFallbackToRawLocalNavidromeArtistImage() {
		val path = "src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralArtistScreen.kt"
		val source = File(path).readText()

		assertTrue(
			"artistImageUrlForExternalArtworkPolicy(" in source,
			"$path must use the shared artist image policy before falling back to local artist artwork."
		)
		assertFalse(
			"heroImageUrl = route.imageUrl ?: localArtist?.artistImageUrl" in source,
			"$path must not let raw local Navidrome artist image URLs become the Aurral hero image."
		)
	}

	@Test
	fun lyricsShareSheetReceivesPolicySelectedArtwork() {
		val screenPath = "src/commonMain/kotlin/paige/navic/ui/screens/lyrics/LyricsScreen.kt"
		val sheetPath = "src/commonMain/kotlin/paige/navic/ui/screens/lyrics/dialogs/LyricsShareSheet.kt"
		val screenSource = File(screenPath).readText()
		val sheetSource = File(sheetPath).readText()

		assertTrue(
			"coverArtId = playbackArtwork.coverArtId" in screenSource,
			"$screenPath must pass the policy-selected cover ID into the share sheet."
		)
		assertTrue(
			"coverArtId: String? = null" in sheetSource,
			"$sheetPath must accept a policy-selected cover ID instead of falling back to raw song.coverArtId."
		)
		assertFalse(
			"song.coverArtId?.let { sessionManager.getCoverArtUrl(it) }" in sheetSource,
			"$sheetPath must not build a Navidrome share-image URL directly from song.coverArtId."
		)
		assertFalse(
			"?: song.coverArtId" in sheetSource,
			"$sheetPath must not use raw song.coverArtId as the visible artwork cache fallback."
		)
		assertTrue(
			"visibleCoverArtIdForAurralPolicy(" in sheetSource,
			"$sheetPath must defensively suppress native/Navidrome cover IDs even if a future caller passes one directly."
		)
		assertFalse(
			"coverArtId?.let { sessionManager.getCoverArtUrl(it) }" in sheetSource,
			"$sheetPath must not build a server artwork URL from an unsanitized coverArtId."
		)
	}
}
