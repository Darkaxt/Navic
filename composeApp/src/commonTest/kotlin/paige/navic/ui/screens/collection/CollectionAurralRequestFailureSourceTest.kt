package paige.navic.ui.screens.collection

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CollectionAurralRequestFailureSourceTest {
	@Test
	fun collectionAlbumRequestFailuresRollbackAndNotifyTheUser() {
		val viewModel = sourceFile("ui/screens/collection/viewmodels/CollectionDetailViewModel.kt").readText()
		val screen = sourceFile("ui/screens/collection/CollectionDetailScreen.kt").readText()
		val strings = File("src/commonMain/composeResources/values/strings.xml").readText()

		assertTrue("_aurralAlbumRequestFailures" in viewModel)
		assertTrue("val aurralAlbumRequestFailures" in viewModel)
		assertTrue("ownershipStatus = AurralOwnershipStatus.Failed" in viewModel)
		assertTrue("status = \"failed\"" in viewModel)
		assertTrue("_aurralAlbumRequestFailures.emit(Unit)" in viewModel)
		assertTrue("notice_aurral_album_request_failed" in strings)
		assertTrue("val albumRequestFailedMessage = stringResource(Res.string.notice_aurral_album_request_failed)" in screen)
		assertTrue("viewModel.aurralAlbumRequestFailures.collect" in screen)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/$path"),
			File("composeApp/src/commonMain/kotlin/paige/navic/$path"),
			File("../composeApp/src/commonMain/kotlin/paige/navic/$path")
		).first(File::exists)
}
