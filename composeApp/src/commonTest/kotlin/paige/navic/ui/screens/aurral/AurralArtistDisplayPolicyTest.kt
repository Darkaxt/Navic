package paige.navic.ui.screens.aurral

import kotlin.test.Test
import kotlin.test.assertFalse
import paige.navic.domain.models.AurralPreviewTrack

class AurralArtistDisplayPolicyTest {
	@Test
	fun artistPageDoesNotShowGlobalPreviewRow() {
		assertFalse(
			shouldShowAurralArtistGlobalPreviewRow(
				listOf(
					AurralPreviewTrack(
						id = "preview-1",
						title = "Preview",
						album = "Missing Album",
						previewUrl = "https://aurral.example.com/preview.mp3"
					)
				)
			)
		)
	}
}
