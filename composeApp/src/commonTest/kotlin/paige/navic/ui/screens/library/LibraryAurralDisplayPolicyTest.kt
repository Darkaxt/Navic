package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.AurralAlbumRequest

class LibraryAurralDisplayPolicyTest {
	@Test
	fun libraryAlbumRowsUseAurralRequestsOnlyWhenHubIsConfigured() {
		val requests = listOf(
			AurralAlbumRequest(
				albumMbid = "album-mbid",
				albumName = "Album",
				artistName = "Artist",
				status = "downloading"
			)
		)

		assertEquals(
			requests,
			libraryAlbumAurralRequests(
				showAurralHub = true,
				requests = requests
			)
		)
		assertEquals(
			emptyList(),
			libraryAlbumAurralRequests(
				showAurralHub = false,
				requests = requests
			)
		)
	}
}
