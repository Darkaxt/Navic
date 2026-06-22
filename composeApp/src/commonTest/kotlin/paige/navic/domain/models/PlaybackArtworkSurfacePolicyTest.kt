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
				"PlaybackSongCoverArt(" in source,
				"$path must route song artwork through PlaybackSongCoverArt so external/Aurral artwork can beat Navidrome."
			)
			assertFalse(
				rawSongCoverArtCall.containsMatchIn(source),
				"$path must not pass raw song.coverArtId directly to CoverArt."
			)
		}
	}

	@Test
	fun quickPickSongCardsUsePlaybackArtworkGridItem() {
		val source = File("src/commonMain/kotlin/paige/navic/ui/screens/library/components/QuickPickSongCard.kt").readText()

		assertTrue(
			"PlaybackSongArtGridItem(" in source,
			"Quick picks must route song artwork through PlaybackSongArtGridItem so external/Aurral artwork can beat Navidrome."
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
				"effectiveAurralArtworkPriority(" in source,
				"$path must treat Aurral enabled as the effective playback artwork priority, regardless of the stored setting."
			)
			assertTrue(
				"visiblePlaybackCoverArtId(" in source,
				"$path must suppress visible Navidrome cover IDs when cover artwork priority is Aurral-first."
			)
			assertTrue(
				"visiblePlaybackImageUrl(" in source,
				"$path must route visible external artwork through the Aurral-first playback policy."
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
			"effectiveAurralArtworkPriority(" in source,
			"$path must treat Aurral enabled as the effective palette artwork priority, regardless of the stored setting."
		)
		assertTrue(
			"visiblePlaybackCoverArtId(" in source,
			"$path must suppress visible Navidrome palette artwork when cover artwork priority is Aurral-first."
		)
		assertTrue(
			"visiblePlaybackImageUrl(" in source,
			"$path must route palette extraction through the same visible playback artwork policy."
		)
		assertFalse(
			"song?.coverArtId?.let { sessionManager.getCoverArtUrl(it) }" in source,
			"$path must not build a visible Navidrome artwork URL before applying the playback artwork policy."
		)
	}

	@Test
	fun sharedPlaybackArtworkHelperUsesAurralEnabledPriorityOverride() {
		val path = "src/commonMain/kotlin/paige/navic/ui/components/common/PlaybackSongArtwork.kt"
		val source = File(path).readText()

		assertTrue(
			"effectiveAurralArtworkPriority(" in source,
			"$path must make Aurral enabled override stored NativeFirst/NativeOnly preferences."
		)
		assertFalse(
			"val artworkPriority = preferenceManager.coverArtworkPriority" in source,
			"$path must not read coverArtworkPriority directly as the visible playback artwork authority."
		)
	}

	@Test
	fun sharedVisibleArtworkComponentsSuppressNativeCoverArtWhenAurralIsEnabled() {
		val visibleArtworkComponents = listOf(
			"src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt",
			"src/commonMain/kotlin/paige/navic/ui/components/common/BlendBackground.kt",
			"src/commonMain/kotlin/paige/navic/ui/components/layouts/MiniPlayer.kt"
		)

		visibleArtworkComponents.forEach { path ->
			val source = File(path).readText()
			assertTrue(
				"visibleCoverArtIdForAurralPolicy(" in source,
				"$path must not let raw native/Navidrome cover IDs become visible artwork while Aurral is enabled."
			)
			assertTrue(
				"visibleImageUrlForAurralPolicy(" in source,
				"$path must not let raw native/Navidrome image URLs become visible artwork while Aurral is enabled."
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
	}

	@Test
	fun artistSurfacesUseAurralArtistImagePolicyInsteadOfRawNavidromeArtistImages() {
		val artistSurfaces = listOf(
			"src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistListScreen.kt",
			"src/commonMain/kotlin/paige/navic/ui/components/sheets/ArtistSheet.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/genre/GenreDetailScreen.kt",
			"src/commonMain/kotlin/paige/navic/ui/screens/starred/components/Content.kt"
		)

		artistSurfaces.forEach { path ->
			val source = File(path).readText()
			assertTrue(
				"artistImageUrlForExternalArtworkPolicy(" in source,
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
			"coverArtId = selectedPlaybackCoverArtId" in screenSource,
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
