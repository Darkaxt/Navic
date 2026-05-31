package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryDiscoveryRowPolicyTest {
	@Test
	fun albumRowsExposeOnlyBackedDiscoveryRows() {
		assertEquals(
			listOf(LibraryDiscoveryAlbumRow.NewestAlbums, LibraryDiscoveryAlbumRow.StarredAlbums),
			libraryDiscoveryAlbumRows(
				newestAlbumCount = 4,
				starredAlbumCount = 2
			)
		)
		assertEquals(
			listOf(LibraryDiscoveryAlbumRow.NewestAlbums),
			libraryDiscoveryAlbumRows(
				newestAlbumCount = 4,
				starredAlbumCount = 0
			)
		)
		assertEquals(
			emptyList(),
			libraryDiscoveryAlbumRows(
				newestAlbumCount = 0,
				starredAlbumCount = 0
			)
		)
	}
}
