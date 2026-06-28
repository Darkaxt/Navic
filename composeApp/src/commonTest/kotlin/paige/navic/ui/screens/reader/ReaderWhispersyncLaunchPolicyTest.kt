package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.reader.ReaderPublicationKind
import paige.navic.ui.navigation.Screen

class ReaderWhispersyncLaunchPolicyTest {
	@Test
	fun readerWhispersyncLaunchAttachmentRequiresSelectedAudiobookContract() {
		assertEquals(
			ReaderWhispersyncLaunchAttachment(
				sidecarPath = "https://bindery.local/opds/books/3816/sync/3",
				artifactId = "3",
				audiobookId = "69",
				audiobookBookFileId = "694",
				audiobookTitle = "Andy Serkis"
			),
			readerRoute(
				whispersyncSidecarUrl = " https://bindery.local/opds/books/3816/sync/3 ",
				whispersyncArtifactId = " 3 ",
				whispersyncAudiobookId = " 69 ",
				whispersyncAudiobookBookFileId = " 694 ",
				whispersyncAudiobookTitle = " Andy Serkis "
			).whispersyncLaunchAttachment()
		)

		assertEquals(
			ReaderWhispersyncLaunchAttachment(
				sidecarPath = "https://bindery.local/opds/books/3816/sync/12",
				artifactId = "12",
				audiobookId = null,
				audiobookBookFileId = "694",
				audiobookTitle = "Audiobook"
			),
			readerRoute(
				whispersyncSidecarUrl = "https://bindery.local/opds/books/3816/sync/12",
				whispersyncArtifactId = "12",
				whispersyncAudiobookId = null,
				whispersyncAudiobookBookFileId = "694",
				whispersyncAudiobookTitle = " Audiobook "
			).whispersyncLaunchAttachment()
		)

		assertNull(
			readerRoute(
				whispersyncSidecarUrl = "https://bindery.local/opds/books/3816/sync/3",
				whispersyncArtifactId = "3",
				whispersyncAudiobookId = "69",
				whispersyncAudiobookBookFileId = ""
			).whispersyncLaunchAttachment()
		)
	}

	@Test
	fun readerWhispersyncLaunchAttachmentDerivesArtifactIdFromSidecarPathWhenMissing() {
		assertEquals(
			ReaderWhispersyncLaunchAttachment(
				sidecarPath = "/opds/books/3809/sync/8",
				artifactId = "8",
				audiobookId = null,
				audiobookBookFileId = "633",
				audiobookTitle = null
			),
			readerRoute(
				whispersyncSidecarUrl = "/opds/books/3809/sync/8",
				whispersyncArtifactId = null,
				whispersyncAudiobookId = null,
				whispersyncAudiobookBookFileId = "633"
			).whispersyncLaunchAttachment()
		)
	}

	private fun readerRoute(
		whispersyncSidecarUrl: String? = null,
		whispersyncArtifactId: String? = null,
		whispersyncAudiobookId: String? = null,
		whispersyncAudiobookBookFileId: String? = null,
		whispersyncAudiobookTitle: String? = null
	): Screen.Reader =
		Screen.Reader(
			title = "The Hobbit",
			publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
			bookId = "3816",
			resourceHref = "/opds/books/3816/resources/ebook-435",
			kind = ReaderPublicationKind.Ebook,
			whispersyncSidecarUrl = whispersyncSidecarUrl,
			whispersyncArtifactId = whispersyncArtifactId,
			whispersyncAudiobookId = whispersyncAudiobookId,
			whispersyncAudiobookBookFileId = whispersyncAudiobookBookFileId,
			whispersyncAudiobookTitle = whispersyncAudiobookTitle
		)
}
