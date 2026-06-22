package paige.navic.ui.navigation

import androidx.navigation3.runtime.NavKey

sealed interface NavBackAction {
	data object Pop : NavBackAction
	data class ReplaceRoot(val destination: Screen) : NavBackAction
	data object Stay : NavBackAction
}

fun navBackActionFor(backStack: List<NavKey>): NavBackAction {
	if (backStack.size > 1) return NavBackAction.Pop
	val current = backStack.lastOrNull() as? Screen ?: return NavBackAction.Stay
	return fallbackRootDestinationFor(current)?.let(NavBackAction::ReplaceRoot) ?: NavBackAction.Stay
}

fun canNavigateBack(backStack: List<NavKey>): Boolean =
	navBackActionFor(backStack) !is NavBackAction.Stay

fun fallbackRootDestinationFor(screen: Screen): Screen? =
	when (screen) {
		is Screen.Reader -> screen.bookId
			.takeIf(String::isNotBlank)
			?.let { Screen.BinderyBook(it, screen.title) }
			?: Screen.BinderyBooks

		is Screen.BinderyBook -> Screen.BinderyBooks
		is Screen.BinderyCollection -> Screen.BinderyCollections
		is Screen.BinderyAuthor -> Screen.BinderyAuthors
		is Screen.BinderyCatalog -> Screen.Audiobooks
		is Screen.BinderyFinding -> Screen.Audiobooks
		is Screen.BinderyAudiobookDetail -> Screen.Audiobooks
		is Screen.BinderyAudiobookPlayer -> Screen.BinderyAudiobookDetail(screen.audiobookId, screen.title)

		is Screen.Settings -> if (screen == Screen.Settings.Root) null else Screen.Settings.Root

		is Screen.GenreDetail -> Screen.GenreList()
		is Screen.CollectionDetail -> Screen.Library()
		is Screen.SongDetail -> Screen.Library()
		is Screen.ArtistDetail -> Screen.ArtistList()
		is Screen.Search -> if (screen.nested) searchFallbackFor(screen.scope) else null
		is Screen.ShareList -> Screen.Library()
		is Screen.LidaClipPlayer -> Screen.Library()
		is Screen.AurralArtist -> Screen.AurralHub
		is Screen.AurralMissingAlbum -> Screen.AurralHub
		is Screen.AurralDiscoverCollection -> Screen.AurralDiscoverList
		is Screen.AurralDiscoverTag -> Screen.AurralDiscoverList

		else -> null
	}

fun shouldShowRootBackForScreen(screen: Screen?): Boolean =
	screen != null && fallbackRootDestinationFor(screen) != null

fun navBackStackAfterTabSelection(backStack: List<NavKey>, destination: Screen): List<NavKey> {
	if (backStack.lastOrNull() == destination) return backStack
	return backStack + destination
}

fun MutableList<NavKey>.performNavicBack(): Boolean =
	when (val action = navBackActionFor(this)) {
		NavBackAction.Pop -> {
			removeLastOrNull()
			true
		}

		is NavBackAction.ReplaceRoot -> {
			clear()
			add(action.destination)
			true
		}

		NavBackAction.Stay -> false
	}

fun MutableList<NavKey>.selectNavicRootTab(destination: Screen) {
	val nextStack = navBackStackAfterTabSelection(this, destination)
	clear()
	addAll(nextStack)
}

private fun searchFallbackFor(scope: SearchScope): Screen =
	when (scope) {
		SearchScope.Music -> Screen.Library()
		SearchScope.Audiobooks -> Screen.Audiobooks
	}
