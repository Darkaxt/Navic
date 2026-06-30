package paige.navic.ui.screens.collection

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionAurralAlbumPageSourceTest {
	@Test
	fun collectionViewModelPublishesAurralAlbumPageState() {
		val source = sourceFile("ui/screens/collection/viewmodels/CollectionDetailViewModel.kt").readText()

		assertTrue("private val _aurralAlbumPageState" in source)
		assertTrue("val aurralAlbumPageState" in source)
		assertTrue("aurralAlbumPageState as buildAurralAlbumPageState" in source)
		assertTrue("buildAurralAlbumPageState(" in source)
		assertTrue("lookupFailed" in source)
		assertTrue("AurralAlbumPageSource.AurralUnavailable" in source)
	}

	@Test
	fun collectionScreenRendersResolvedAurralHeaderProjection() {
		val screen = sourceFile("ui/screens/collection/CollectionDetailScreen.kt").readText()
		val heading = sourceFile("ui/screens/collection/components/HeadingRow.kt").readText()

		assertTrue("val aurralAlbumPageState by viewModel.aurralAlbumPageState" in screen)
		assertTrue("aurralAlbumHeaderProjection(" in screen)
		assertTrue("displayTitle =" in screen)
		assertTrue("displaySubtitle =" in screen)
		assertTrue("displayDetail =" in screen)
		assertTrue("coverImageUrl =" in screen)
		assertTrue("externalImageUrl = coverImageUrl" in heading)
		assertTrue("imageUrl = artworkSpec.imageUrl" in heading)
	}

	@Test
	fun moreByArtistUsesAurralReleaseGroupsWhenAlbumIsResolved() {
		val viewModel = sourceFile("ui/screens/collection/viewmodels/CollectionDetailViewModel.kt").readText()
		val screen = sourceFile("ui/screens/collection/CollectionDetailScreen.kt").readText()
		val row = sourceFile("ui/screens/collection/components/MoreByArtistRow.kt").readText()

		assertTrue("private val _aurralMoreByArtistRows" in viewModel)
		assertTrue("val aurralMoreByArtistRows" in viewModel)
		assertTrue("getArtistCoreEnrichment" in viewModel)
		assertTrue("aurralArtistOwnershipAlbumRows(" in viewModel)
		assertTrue("val aurralMoreByArtistRows by viewModel.aurralMoreByArtistRows" in screen)
		assertTrue("aurralArtistAlbums =" in screen)
		assertTrue("aurralArtistAlbums: List<AurralArtistOwnershipAlbumRow>" in row)
		assertTrue("row.releaseGroup" in row)
		assertTrue("row.localAlbum" in row)
	}

	@Test
	fun moreByArtistLocalMatchesNavigateWithAurralReleaseGroupIdentity() {
		val row = sourceFile("ui/screens/collection/components/MoreByArtistRow.kt").readText()
		val block = row.substringAfter("if (aurralArtistAlbums.isNotEmpty())")
			.substringBefore("} else {")

		assertTrue(
			"aurralOwnershipAlbumCollectionDetailRoute(" in block,
			"Resolved Aurral more-by-artist rows should keep release-group metadata when opening a local album."
		)
		assertFalse(
			"Screen.CollectionDetail(it.id, tab)" in block,
			"Resolved Aurral more-by-artist rows must not route local matches through a plain CollectionDetail."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/$path"),
			File("composeApp/src/commonMain/kotlin/paige/navic/$path"),
			File("../composeApp/src/commonMain/kotlin/paige/navic/$path")
		).first(File::exists)
}
