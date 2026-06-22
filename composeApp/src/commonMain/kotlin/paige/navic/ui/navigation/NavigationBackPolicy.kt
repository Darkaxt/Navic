package paige.navic.ui.navigation

import androidx.navigation3.runtime.NavKey

internal fun navicRootBackDestinationFor(screen: NavKey?): Screen? =
	when (screen) {
		is Screen.Reader -> Screen.BinderyBook(
			bookId = screen.bookId,
			title = screen.title
		)
		is Screen.BinderyBook -> Screen.BinderyBooks
		is Screen.BinderyCollection -> Screen.BinderyCollections
		is Screen.BinderyAuthor -> Screen.BinderyAuthors
		is Screen.BinderyCatalog,
		is Screen.BinderyFinding,
		is Screen.BinderyAudiobookDetail -> Screen.Audiobooks
		is Screen.AurralArtist,
		Screen.AurralDiscoverList,
		is Screen.AurralDiscoverCollection,
		is Screen.AurralDiscoverTag -> Screen.AurralHub
		is Screen.AurralMissingAlbum -> Screen.AurralArtist(
			artistMbid = screen.artistMbid,
			artistName = screen.artistName
		)
		is Screen.CollectionDetail -> Screen.Library()
		is Screen.SongDetail -> Screen.Library()
		is Screen.ArtistDetail -> Screen.ArtistList()
		is Screen.Search -> when (screen.scope) {
			SearchScope.Music -> Screen.Library()
			SearchScope.Audiobooks -> Screen.Audiobooks
		}
		is Screen.Settings -> when (screen) {
			Screen.Settings.Root -> null
			else -> Screen.Settings.Root
		}
		Screen.NowPlaying,
		Screen.Lyrics,
		Screen.MusicBrainzInfo,
		Screen.Queue,
		Screen.PlaybackSpeed,
		is Screen.LidaClipPlayer,
		Screen.ShareList -> Screen.Library()
		else -> null
	}
