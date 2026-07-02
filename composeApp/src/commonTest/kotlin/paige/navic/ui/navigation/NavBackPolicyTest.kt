package paige.navic.ui.navigation

import paige.navic.reader.ReaderPublicationKind
import kotlin.test.Test
import kotlin.test.assertEquals

class NavBackPolicyTest {
	@Test
	fun systemBackDoesNotDrainTheLastRootDestination() {
		assertEquals(
			NavBackAction.Stay,
			navBackActionFor(listOf(Screen.Library()))
		)
		assertEquals(
			NavBackAction.Stay,
			navBackActionFor(listOf(Screen.Audiobooks))
		)
	}

	@Test
	fun musicRootSystemBackReturnsToLibraryInsteadOfClosingTheApp() {
		listOf(
			Screen.Starred(),
			Screen.PlaylistList(),
			Screen.ArtistList(),
			Screen.Activity,
			Screen.AlbumList(),
			Screen.GenreList(),
			Screen.SongList(),
			Screen.RadioList(),
			Screen.AurralHub,
			Screen.AurralDiscoverList,
			Screen.Settings.Root,
			Screen.NowPlaying,
			Screen.Lyrics,
			Screen.MusicBrainzInfo,
			Screen.Queue,
			Screen.PlaybackSpeed,
			Screen.ShareList
		).forEach { screen ->
			assertEquals(
				NavBackAction.ReplaceRoot(Screen.Library()),
				navBackActionFor(listOf(screen)),
				"$screen should return to Library instead of letting Android close Navic"
			)
		}
	}

	@Test
	fun audiobookRootSystemBackReturnsToAudiobooksInsteadOfClosingTheApp() {
		listOf(
			Screen.BinderyBooks,
			Screen.BinderyCollections,
			Screen.BinderyAuthors
		).forEach { screen ->
			assertEquals(
				NavBackAction.ReplaceRoot(Screen.Audiobooks),
				navBackActionFor(listOf(screen)),
				"$screen should return to Audiobooks instead of letting Android close Navic"
			)
		}
	}

	@Test
	fun readerRootBackFallsBackToOwningBookBeforeLeavingTheApp() {
		val reader = Screen.Reader(
			title = "The Hobbit",
			publicationUrl = "https://bindery.example/api/v1/book/3809/file?bookFileId=633",
			bookId = "3809",
			resourceHref = "/api/v1/book/3809/file?bookFileId=633",
			kind = ReaderPublicationKind.Ebook
		)

		assertEquals(
			NavBackAction.ReplaceRoot(Screen.BinderyBook("3809", "The Hobbit")),
			navBackActionFor(listOf(reader))
		)
	}

	@Test
	fun binderyDetailRootBackFallsBackToTheRelevantBinderyRoot() {
		assertEquals(
			NavBackAction.ReplaceRoot(Screen.BinderyBooks),
			navBackActionFor(listOf(Screen.BinderyBook("3809", "The Hobbit")))
		)
		assertEquals(
			NavBackAction.ReplaceRoot(Screen.BinderyCollections),
			navBackActionFor(listOf(Screen.BinderyCollection("/opds/collections/1", "Series")))
		)
		assertEquals(
			NavBackAction.ReplaceRoot(Screen.BinderyAuthors),
			navBackActionFor(listOf(Screen.BinderyAuthor("/opds/authors/1", "Author")))
		)
	}

	@Test
	fun settingsDetailRootBackFallsBackToSettingsRoot() {
		assertEquals(
			NavBackAction.ReplaceRoot(Screen.Settings.Root),
			navBackActionFor(listOf(Screen.Settings.Ebooks))
		)
	}

	@Test
	fun rootTopBarBackAffordanceAppearsForDetailRootsOnly() {
		assertEquals(true, shouldShowRootBackForScreen(Screen.BinderyBook("3809", "The Hobbit")))
		assertEquals(true, shouldShowRootBackForScreen(Screen.Settings.Ebooks))
		assertEquals(false, shouldShowRootBackForScreen(Screen.BinderyBooks))
		assertEquals(false, shouldShowRootBackForScreen(Screen.Library()))
		assertEquals(false, shouldShowRootBackForScreen(Screen.Audiobooks))
		assertEquals(false, shouldShowRootBackForScreen(Screen.AlbumList()))
		assertEquals(false, shouldShowRootBackForScreen(Screen.ArtistList()))
		assertEquals(false, shouldShowRootBackForScreen(Screen.Settings.Root))
	}

	@Test
	fun bottomTabSelectionReplacesRootHistoryWithSelectedRoot() {
		val stack = listOf(
			Screen.BinderyBooks,
			Screen.BinderyBook("3809", "The Hobbit"),
			Screen.Reader(
				title = "The Hobbit",
				publicationUrl = "https://bindery.example/api/v1/book/3809/file?bookFileId=633",
				bookId = "3809",
				resourceHref = "/api/v1/book/3809/file?bookFileId=633",
				kind = ReaderPublicationKind.Ebook
			)
		)

		assertEquals(
			listOf(Screen.BinderyCollections),
			navBackStackAfterTabSelection(stack, Screen.BinderyCollections)
		)
	}

	@Test
	fun bottomTabSelectionDoesNotDuplicateTheCurrentDestination() {
		val stack = listOf(Screen.BinderyBooks)

		assertEquals(
			stack,
			navBackStackAfterTabSelection(stack, Screen.BinderyBooks)
		)
	}

	@Test
	fun bottomTabSelectionDoesNotAccumulateWhenBouncingBetweenRootTabs() {
		var stack = listOf<Screen>(Screen.Library())

		stack = navBackStackAfterTabSelection(stack, Screen.AlbumList()).map { it as Screen }
		stack = navBackStackAfterTabSelection(stack, Screen.Library()).map { it as Screen }
		stack = navBackStackAfterTabSelection(stack, Screen.AlbumList()).map { it as Screen }

		assertEquals(
			listOf(Screen.AlbumList()),
			stack
		)
	}

	@Test
	fun bottomTabSelectionReusesExistingDestinationInsteadOfGrowingDuplicateRoots() {
		val stack = listOf(
			Screen.Library(),
			Screen.AlbumList(),
			Screen.ArtistList()
		)

		assertEquals(
			listOf(Screen.AlbumList()),
			navBackStackAfterTabSelection(stack, Screen.AlbumList())
		)
	}
}
