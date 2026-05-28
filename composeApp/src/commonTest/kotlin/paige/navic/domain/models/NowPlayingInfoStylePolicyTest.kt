package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingInfoStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingInfoStylePolicyTest {
	@Test
	fun essentialStyleKeepsArtistOnlySubtitle() {
		assertEquals(
			"Artist",
			nowPlayingInfoSubtitle(
				style = NowPlayingInfoStyle.Essential,
				albumTitle = "Album",
				artistName = "Artist"
			)
		)
	}

	@Test
	fun albumAndArtistStyleShowsAlbumContextWhenAvailable() {
		assertEquals(
			"Album \u2022 Artist",
			nowPlayingInfoSubtitle(
				style = NowPlayingInfoStyle.AlbumAndArtist,
				albumTitle = "Album",
				artistName = "Artist"
			)
		)
	}

	@Test
	fun albumAndArtistStyleFallsBackToArtistWhenAlbumIsMissing() {
		assertEquals(
			"Artist",
			nowPlayingInfoSubtitle(
				style = NowPlayingInfoStyle.AlbumAndArtist,
				albumTitle = null,
				artistName = "Artist"
			)
		)
		assertEquals(
			"Artist",
			nowPlayingInfoSubtitle(
				style = NowPlayingInfoStyle.AlbumAndArtist,
				albumTitle = " ",
				artistName = "Artist"
			)
		)
	}
}
