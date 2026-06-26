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

	@Test
	fun albumRequestFeedbackPreemptsOlderSnackbarMessages() {
		listOf(
			commonMain("paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt") to
				"snackbarState.showSnackbar(albumRequestedMessage)",
			commonMain("paige/navic/ui/screens/artist/ArtistDetailScreen.kt") to
				"snackbarState.showSnackbar(message)",
			commonMain("paige/navic/ui/screens/collection/CollectionDetailScreen.kt") to
				"snackbarState.showSnackbar(albumRequestedMessage)"
		).forEach { (source, feedbackCall) ->
			val feedback = source.indexOf(feedbackCall)
			val dismiss = source.lastIndexOf("snackbarState.currentSnackbarData?.dismiss()", feedback)

			assertTrue(
				dismiss >= 0 && dismiss < feedback,
				"Album request feedback must dismiss any older snackbar before showing Album requested."
			)
		}
	}

	@Test
	fun artistDetailAlbumRequestShowsFeedbackAndOptimisticallyMarksRequested() {
		val screenSource = commonMain("paige/navic/ui/screens/artist/ArtistDetailScreen.kt")
		val viewModelSource = commonMain("paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt")
		val requestStart = viewModelSource.indexOf("fun requestAurralAlbum(row: AurralMissingAlbumRow)")
		val requestEnd = viewModelSource.indexOf("private fun updateAurralAlbumRequestStatus", requestStart)
		val requestAurralAlbum = viewModelSource.substring(requestStart, requestEnd)
		val optimisticUpdate = requestAurralAlbum.indexOf(
			"updateAurralAlbumRequestStatus(row, \"requested\", AurralArtistActionFeedback.AlbumRequested)"
		)
		val repositoryCall = requestAurralAlbum.indexOf("aurralRepository.requestAlbum(artist, row.releaseGroup)")

		assertTrue(
			"AurralArtistActionFeedback.AlbumRequested ->" in screenSource &&
				"stringResource(Res.string.notice_aurral_album_requested)" in screenSource,
			"Artist detail album requests must use the same visible Album requested feedback as the missing-album page."
		)
		assertTrue(
			optimisticUpdate >= 0 && optimisticUpdate < repositoryCall,
			"Artist detail album requests must mark the row requested before waiting for Aurral."
		)
		assertFalse(
			"status = \"failed\"" in requestAurralAlbum,
			"After the request is accepted locally, background Aurral/Lidarr failures must not flip the visible row back to failed."
		)
	}

	@Test
	fun collectionAlbumRecoveryRequestShowsFeedbackAndOptimisticallyMarksRequested() {
		val screenSource = commonMain("paige/navic/ui/screens/collection/CollectionDetailScreen.kt")
		val viewModelSource = commonMain("paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt")
		val requestStart = viewModelSource.indexOf("fun requestAurralRecoveryAlbum()")
		val requestEnd = viewModelSource.indexOf("fun selectAurralRecoveryCandidate", requestStart)
		val requestAurralAlbum = viewModelSource.substring(requestStart, requestEnd)
		val optimisticUpdate = requestAurralAlbum.indexOf("_aurralAlbumRecoveryMatch.value = album.copy(status = \"requested\")")
		val repositoryCall = requestAurralAlbum.indexOf("aurralRepository.requestAlbum(album)")

		assertTrue(
			"val snackbarState = LocalSnackbarState.current" in screenSource &&
				"val albumRequestedMessage = stringResource(Res.string.notice_aurral_album_requested)" in screenSource,
			"Collection album recovery must use the app-wide snackbar host for request feedback."
		)
		assertTrue(
			"snackbarState.showSnackbar(albumRequestedMessage)" in screenSource,
			"Collection album recovery clicks must acknowledge that Aurral accepted the request for background handling."
		)
		assertTrue(
			optimisticUpdate >= 0 && optimisticUpdate < repositoryCall,
			"Collection album recovery must switch the header icon to requested before waiting for Aurral."
		)
		assertFalse(
			"_aurralAlbumRecoveryMatch.value = album.copy(status = \"failed\")" in requestAurralAlbum,
			"After the request is accepted locally, background Aurral/Lidarr failures must not restore the failed/crossed header icon."
		)
	}

	@Test
	fun missingAlbumDetailKeepsAcceptedRequestVisibleWhenBackgroundRequestFails() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt")
		val failureStart = source.indexOf(".onFailure { error ->")
		val failureBody = source.substring(failureStart, source.indexOf("\n\t\t\t\t\t\t}", failureStart))

		assertFalse(
			"progress = route.requestStatus?.let(::aurralAcquisitionProgress)" in failureBody,
			"After the request is accepted locally, background Aurral/Lidarr failures must not restore the old request status."
		)
		assertFalse(
			"error = error" in failureBody,
			"Background request failures should be logged; they should not replace the immediate Album requested popup."
		)
	}

	private fun commonMain(path: String): String =
		File("src/commonMain/kotlin/$path").readText()
}
