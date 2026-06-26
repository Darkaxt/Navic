package paige.navic.ui.screens.aurral

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.domain.models.aurralAcquisitionProgress

class AurralMissingAlbumActionPolicyTest {
	@Test
	fun requestedAlbumShowsAcceptedStatusInsteadOfAcquireOrSpinner() {
		val state = aurralMissingAlbumActionState(
			progress = aurralAcquisitionProgress("requested"),
			requesting = false
		)

		assertEquals(AurralMissingAlbumActionIcon.Requested, state.icon)
		assertFalse(state.enabled)
		assertFalse(state.showSpinner)
	}

	@Test
	fun unrequestedAlbumShowsAcquireAction() {
		val state = aurralMissingAlbumActionState(
			progress = null,
			requesting = false
		)

		assertEquals(AurralMissingAlbumActionIcon.Acquire, state.icon)
		assertTrue(state.enabled)
		assertFalse(state.showSpinner)
	}

	@Test
	fun inFlightClickShowsSpinnerAndDisablesDuplicateRequest() {
		val state = aurralMissingAlbumActionState(
			progress = null,
			requesting = true
		)

		assertEquals(AurralMissingAlbumActionIcon.Requesting, state.icon)
		assertFalse(state.enabled)
		assertTrue(state.showSpinner)
	}

	@Test
	fun completedAlbumKeepsPlayStatusDisabledFromAcquireEndpoint() {
		val state = aurralMissingAlbumActionState(
			progress = aurralAcquisitionProgress("completed"),
			requesting = false
		)

		assertEquals(AurralMissingAlbumActionIcon.Play, state.icon)
		assertFalse(state.enabled)
		assertFalse(state.showSpinner)
	}

	@Test
	fun requestClickUsesSharedSnackbarFeedback() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt")

		assertTrue(
			"val snackbarState = LocalSnackbarState.current" in source,
			"Missing-album requests must use the app-wide snackbar host for visible feedback."
		)
		assertTrue(
			"val albumRequestedMessage = stringResource(Res.string.notice_aurral_album_requested)" in source,
			"The success message should be a localized resource, not an inline literal."
		)
		assertTrue(
			"snackbarState.showSnackbar(albumRequestedMessage)" in source,
			"Aurral album request clicks must tell the user that the album was accepted for background handling."
		)
	}

	private fun commonMain(path: String): String =
		File("src/commonMain/kotlin/$path").readText()
}
