package paige.navic.ui.navigation

enum class ScreenProfileHint {
	Music,
	Audiobooks,
	Remembered
}

data class ScreenDestinationMetadata(
	val visibleRootBackDestination: Screen? = null,
	val areaRootDestination: Screen? = null,
	val profileHint: ScreenProfileHint = ScreenProfileHint.Remembered,
	val profileHintWhenBinderyDisabled: ScreenProfileHint = profileHint,
	val searchScope: SearchScope = SearchScope.Music,
	val selectedTabIconFallbackMotion: Boolean = false
)

fun Screen.destinationMetadata(): ScreenDestinationMetadata =
	when (this) {
		is Screen.Library -> musicMetadata(areaRoot = null)
		is Screen.Starred -> musicMetadata()
		is Screen.PlaylistList -> musicMetadata(visibleRoot = Screen.Library())
		is Screen.ArtistList -> musicMetadata(visibleRoot = Screen.Library())
		Screen.Activity -> musicAreaMetadata(visibleRoot = Screen.Library())
		is Screen.AlbumList -> musicMetadata(visibleRoot = Screen.Library())
		is Screen.GenreList -> musicMetadata(visibleRoot = Screen.Library())
		is Screen.GenreDetail -> musicMetadata(visibleRoot = Screen.GenreList())
		is Screen.SongList -> musicMetadata(visibleRoot = Screen.Library())
		is Screen.RadioList -> musicMetadata(visibleRoot = Screen.Library())

		Screen.Audiobooks -> audiobookMetadata(
			areaRoot = null,
			selectedTabIconFallbackMotion = true
		)
		Screen.BinderyBooks -> audiobookMetadata(
			visibleRoot = Screen.Audiobooks,
			selectedTabIconFallbackMotion = true
		)
		Screen.BinderyCollections -> audiobookMetadata(
			visibleRoot = Screen.Audiobooks,
			selectedTabIconFallbackMotion = true
		)
		Screen.BinderyAuthors -> audiobookMetadata(
			visibleRoot = Screen.Audiobooks,
			selectedTabIconFallbackMotion = true
		)
		is Screen.BinderyCatalog -> audiobookMetadata(visibleRoot = Screen.Audiobooks)
		is Screen.BinderyAuthor -> audiobookMetadata(visibleRoot = Screen.BinderyAuthors)
		is Screen.BinderyCollection -> audiobookMetadata(visibleRoot = Screen.BinderyCollections)
		is Screen.BinderyBook -> audiobookMetadata(visibleRoot = Screen.BinderyBooks)
		is Screen.BinderyAudiobookPlayer -> audiobookMetadata(
			visibleRoot = Screen.BinderyAudiobookDetail(audiobookId, title),
			areaRoot = Screen.BinderyAudiobookDetail(audiobookId, title)
		)
		is Screen.BinderyAudiobookDetail -> audiobookMetadata(visibleRoot = Screen.Audiobooks)
		is Screen.BinderyFinding -> audiobookMetadata(visibleRoot = Screen.Audiobooks)
		is Screen.Reader -> ScreenDestinationMetadata(
			visibleRootBackDestination = bookId
				.takeIf(String::isNotBlank)
				?.let { Screen.BinderyBook(it, title) }
				?: Screen.BinderyBooks,
			areaRootDestination = Screen.BinderyBooks
		)

		Screen.Login -> ScreenDestinationMetadata()
		Screen.NowPlaying -> musicAreaMetadata()
		Screen.Lyrics -> musicAreaMetadata()
		Screen.MusicBrainzInfo -> musicAreaMetadata()
		Screen.Queue -> musicAreaMetadata()
		Screen.PlaybackSpeed -> musicAreaMetadata()
		is Screen.LidaClipPlayer -> musicAreaMetadata(visibleRoot = Screen.Library())
		Screen.AurralHub -> musicAreaMetadata()
		Screen.AurralDiscoverList -> musicAreaMetadata()
		is Screen.AurralDiscoverCollection -> musicAreaMetadata(
			visibleRoot = Screen.AurralDiscoverList,
			areaRoot = Screen.AurralDiscoverList
		)
		is Screen.AurralDiscoverTag -> musicAreaMetadata(
			visibleRoot = Screen.AurralDiscoverList,
			areaRoot = Screen.AurralDiscoverList
		)
		is Screen.AurralArtist -> musicAreaMetadata(
			visibleRoot = Screen.AurralHub,
			areaRoot = Screen.AurralHub
		)
		is Screen.AurralMissingAlbum -> musicAreaMetadata(
			visibleRoot = Screen.AurralHub,
			areaRoot = Screen.AurralHub
		)
		is Screen.CollectionDetail -> musicAreaMetadata(visibleRoot = Screen.Library())
		is Screen.SongDetail -> musicAreaMetadata(visibleRoot = Screen.Library())
		is Screen.Search -> searchMetadata(scope)
		Screen.ShareList -> musicAreaMetadata(visibleRoot = Screen.Library())
		is Screen.ArtistDetail -> musicAreaMetadata(visibleRoot = Screen.ArtistList())

		is Screen.Settings -> ScreenDestinationMetadata(
			visibleRootBackDestination = takeUnless { it == Screen.Settings.Root }
				?.let { Screen.Settings.Root },
			areaRootDestination = Screen.Library()
		)
	}

private fun musicMetadata(
	visibleRoot: Screen? = null,
	areaRoot: Screen? = Screen.Library()
): ScreenDestinationMetadata = ScreenDestinationMetadata(
	visibleRootBackDestination = visibleRoot,
	areaRootDestination = areaRoot,
	profileHint = ScreenProfileHint.Music
)

private fun musicAreaMetadata(
	visibleRoot: Screen? = null,
	areaRoot: Screen? = Screen.Library()
): ScreenDestinationMetadata = ScreenDestinationMetadata(
	visibleRootBackDestination = visibleRoot,
	areaRootDestination = areaRoot
)

private fun audiobookMetadata(
	visibleRoot: Screen? = null,
	areaRoot: Screen? = Screen.Audiobooks,
	profileHintWhenBinderyDisabled: ScreenProfileHint = ScreenProfileHint.Remembered,
	selectedTabIconFallbackMotion: Boolean = false
): ScreenDestinationMetadata = ScreenDestinationMetadata(
	visibleRootBackDestination = visibleRoot,
	areaRootDestination = areaRoot,
	profileHint = ScreenProfileHint.Audiobooks,
	profileHintWhenBinderyDisabled = profileHintWhenBinderyDisabled,
	searchScope = SearchScope.Audiobooks,
	selectedTabIconFallbackMotion = selectedTabIconFallbackMotion
)

private fun searchMetadata(scope: SearchScope): ScreenDestinationMetadata =
	when (scope) {
		SearchScope.Music -> musicMetadata(visibleRoot = Screen.Library())
		SearchScope.Audiobooks -> audiobookMetadata(
			visibleRoot = Screen.Audiobooks,
			profileHintWhenBinderyDisabled = ScreenProfileHint.Music
		)
	}
