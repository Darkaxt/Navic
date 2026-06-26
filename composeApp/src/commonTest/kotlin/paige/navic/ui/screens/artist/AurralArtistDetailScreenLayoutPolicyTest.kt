package paige.navic.ui.screens.artist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AurralArtistDetailScreenLayoutPolicyTest {
	@Test
	fun aurralDiscographyRendersBeforeLocalPlaybackRows() {
		val source = artistDetailScreenSource()
		val ownedPartial = source.indexOf("stringResource(Res.string.title_aurral_owned_partial_albums)")
		val missing = source.indexOf("stringResource(Res.string.title_aurral_missing_albums)")
		val frequent = source.indexOf("stringResource(Res.string.option_sort_frequent)")
		val lastFm = source.indexOf("stringResource(Res.string.title_lastfm_top_tracks)")

		assertTrue(ownedPartial >= 0, "Artist detail must render the Aurral owned/partial album row.")
		assertTrue(missing > ownedPartial, "Aurral missing albums should follow owned/partial albums.")
		assertTrue(
			frequent > missing,
			"Local frequently played evidence must not lead the Aurral-first artist page."
		)
		assertTrue(
			lastFm > frequent,
			"Last.fm/local fallback rows should stay after the Aurral discography and local playback evidence."
		)
	}

	@Test
	fun externalLinksRenderAsNamedAurralSection() {
		val source = artistDetailScreenSource()
		val strings = File("src/commonMain/composeResources/values/strings.xml").readText()

		assertTrue(
			"title_aurral_external_links" in strings,
			"External links need a dedicated Aurral section title instead of only inline metadata."
		)
		assertTrue(
			"AurralArtistExternalLinksSection(" in source,
			"Artist detail should render external links as a named section."
		)
	}

	private fun artistDetailScreenSource(): String =
		File("src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt").readText()
}
