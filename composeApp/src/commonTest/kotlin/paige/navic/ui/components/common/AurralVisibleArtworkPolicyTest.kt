package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AurralVisibleArtworkPolicyTest {
	@Test
	fun nativeCoverArtIdIsVisibleWhenAurralIsDisabled() {
		assertEquals(
			"native-cover-id",
			visibleCoverArtIdForAurralPolicy(
				coverArtId = " native-cover-id ",
				imageUrl = null,
				aurralEnabled = false
			)
		)
	}

	@Test
	fun nativeCoverArtIdIsSuppressedWhenAurralIsEnabled() {
		assertNull(
			visibleCoverArtIdForAurralPolicy(
				coverArtId = "native-cover-id",
				imageUrl = null,
				aurralEnabled = true
			)
		)
	}

	@Test
	fun nativeCoverArtIdIsSuppressedWhenAurralImageIsAlreadySelected() {
		assertNull(
			visibleCoverArtIdForAurralPolicy(
				coverArtId = "native-cover-id",
				imageUrl = "https://aurral.example.com/cover.webp",
				aurralEnabled = true
			)
		)
	}

	@Test
	fun navidromeImageUrlsAreSuppressedWhenAurralIsEnabled() {
		assertNull(
			visibleImageUrlForAurralPolicy(
				imageUrl = " https://navidrome.example.com/rest/getArtistImage?id=jason-ross ",
				aurralEnabled = true
			)
		)
		assertNull(
			visibleImageUrlForAurralPolicy(
				imageUrl = "https://music.example.com/rest/getCoverArt?id=album-1",
				aurralEnabled = true
			)
		)
	}

	@Test
	fun externalAurralImageUrlsStayVisibleWhenAurralIsEnabled() {
		assertEquals(
			"https://aurral.example.com/artists/jason-ross.webp",
			visibleImageUrlForAurralPolicy(
				imageUrl = " https://aurral.example.com/artists/jason-ross.webp ",
				aurralEnabled = true
			)
		)
	}

	@Test
	fun navidromeImageUrlsStayVisibleWhenAurralIsDisabled() {
		assertEquals(
			"https://navidrome.example.com/rest/getArtistImage?id=jason-ross",
			visibleImageUrlForAurralPolicy(
				imageUrl = " https://navidrome.example.com/rest/getArtistImage?id=jason-ross ",
				aurralEnabled = false
			)
		)
	}

	@Test
	fun blankCoverArtIdIsNeverVisible() {
		assertNull(
			visibleCoverArtIdForAurralPolicy(
				coverArtId = " ",
				imageUrl = null,
				aurralEnabled = false
			)
		)
	}
}
