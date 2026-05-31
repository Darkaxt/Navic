package paige.navic.ui.screens.library

enum class LibraryDiscoveryAlbumRow {
	NewestAlbums,
	StarredAlbums
}

fun libraryDiscoveryAlbumRows(
	newestAlbumCount: Int,
	starredAlbumCount: Int
): List<LibraryDiscoveryAlbumRow> = buildList {
	if (newestAlbumCount > 0) add(LibraryDiscoveryAlbumRow.NewestAlbums)
	if (starredAlbumCount > 0) add(LibraryDiscoveryAlbumRow.StarredAlbums)
}
