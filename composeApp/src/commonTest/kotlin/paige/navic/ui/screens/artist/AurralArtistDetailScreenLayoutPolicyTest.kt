package paige.navic.ui.screens.artist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralArtistDetailScreenLayoutPolicyTest {
	@Test
	fun localPlaybackRowsRenderBeforeAurralDiscography() {
		val source = artistDetailScreenSource()
		val ownedPartial = source.indexOf("stringResource(Res.string.title_aurral_owned_partial_albums)")
		val missing = source.indexOf("stringResource(Res.string.title_aurral_missing_albums)")
		val frequent = source.indexOf("stringResource(Res.string.option_sort_frequent)")
		val lastFm = source.indexOf("stringResource(Res.string.title_lastfm_top_tracks)")

		assertTrue(ownedPartial >= 0, "Artist detail must render the Aurral owned/partial album row.")
		assertTrue(missing > ownedPartial, "Aurral missing albums should follow owned/partial albums.")
		assertTrue(
			frequent >= 0 && frequent < ownedPartial,
			"Frequently played local evidence should render before the Aurral album discography."
		)
		assertTrue(
			lastFm > frequent && lastFm < ownedPartial,
			"Most popular tracks should stay with local playback evidence before the Aurral album discography."
		)
	}

	@Test
	fun externalLinksRenderOnlyInHeaderMetadata() {
		val source = artistDetailScreenSource()

		assertTrue("AurralArtistProfileMetadata(" in source)
		assertTrue(
			source.indexOf("externalLinks = state.aurralArtistExternalLinks") <
				source.indexOf("ArtistActionButtons("),
			"Artist external links should be available above the Play row."
		)
		assertFalse(
			"AurralArtistExternalLinksSection(" in source,
			"Artist detail must not render a second external-links section below the Play row."
		)
	}

	private fun artistDetailScreenSource(): String =
		File("src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt").readText()
}
