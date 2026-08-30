package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderySyncPair
import paige.navic.domain.repositories.BinderyWhispersyncArtifact
import paige.navic.domain.repositories.BinderyWhispersyncIdentity
import paige.navic.domain.repositories.BinderyWordSyncDiscovery
import paige.navic.domain.repositories.BinderyWordSyncReference
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationFormat
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

	@Test
	fun readerWhispersyncLaunchAttachmentPreservesOptionalWordSyncReference() {
		val wordSync = BinderyWordSyncReference(
			identity = BinderyWhispersyncIdentity(
				bookId = 3816,
				ebookBookFileId = 435,
				audiobookBookFileId = 694,
				artifactId = 3
			),
			discovery = BinderyWordSyncDiscovery(
				status = "ready",
				schema = "bindery.whispersync.wordsync.index.v1",
				opdsIndexHref = "/opds/books/3816/sync/3/words",
				format = "chapter-sharded-json",
				compression = "http",
				timeScale = 1000
			)
		)

		assertEquals(
			wordSync,
			readerRoute(
				whispersyncSidecarUrl = "/opds/books/3816/sync/3",
				whispersyncAudiobookBookFileId = "694",
				whispersyncWordSync = wordSync
			).whispersyncLaunchAttachment()?.wordSync
		)
		assertNull(
			readerRoute(
				whispersyncSidecarUrl = "/opds/books/3816/sync/3",
				whispersyncAudiobookBookFileId = "694"
			).whispersyncLaunchAttachment()?.wordSync
		)
	}

	@Test
	fun pairedReaderLaunchRecoversMissingWordSyncReferenceFromBookSync() {
		val wordSync = BinderyWordSyncReference(
			identity = BinderyWhispersyncIdentity(
				bookId = 3816,
				ebookBookFileId = 435,
				audiobookBookFileId = 694,
				artifactId = 3
			),
			discovery = BinderyWordSyncDiscovery(
				status = "ready",
				schema = "bindery.whispersync.wordsync.index.v1",
				opdsIndexHref = "/opds/books/3816/sync/3/words",
				format = "chapter-sharded-json",
				compression = "http",
				timeScale = 1000
			)
		)
		val attachment = readerRoute(
			whispersyncSidecarUrl = "/opds/books/3816/sync/3",
			whispersyncAudiobookBookFileId = "694"
		).whispersyncLaunchAttachment()
		val bookSync = BinderyBookSync(
			bookId = 3816,
			syncPairs = listOf(
				BinderySyncPair(
					bookId = 3816,
					ebookBookFileId = 430,
					audiobookBookFileId = 693,
					whispersync = BinderyWhispersyncArtifact(artifactId = 2)
				),
				BinderySyncPair(
					bookId = 3816,
					ebookBookFileId = 435,
					audiobookBookFileId = 694,
					whispersync = BinderyWhispersyncArtifact(
						artifactId = 3,
						wordSync = wordSync.discovery
					)
				)
			)
		)

		assertNull(attachment?.wordSync)
		val resolution = bookSync.wordSyncReferenceResolutionForLaunch(
			bookId = "3816",
			attachment = attachment!!
		)
		assertEquals(ReaderWordSyncReferenceResolutionReason.Resolved, resolution.reason)
		assertEquals(wordSync, resolution.reference)
	}

	@Test
	fun readerWhispersyncLaunchAttachmentRequiresMediaOverlayCapability() {
		listOf(
			ReaderPublicationFormat.Epub,
			ReaderPublicationFormat.Azw3,
			ReaderPublicationFormat.Mobi,
			ReaderPublicationFormat.Fb2
		).forEach { format ->
			val attachment = readerRoute(
				publicationFormat = format,
				whispersyncSidecarUrl = "/opds/books/3809/sync/8",
				whispersyncAudiobookBookFileId = "633"
			).whispersyncLaunchAttachment()

			assertEquals("8", attachment?.artifactId, format.name)
		}
		listOf(ReaderPublicationFormat.Pdf, ReaderPublicationFormat.Cbz).forEach { format ->
			assertNull(
				readerRoute(
					publicationFormat = format,
					whispersyncSidecarUrl = "/opds/books/3809/sync/8",
					whispersyncAudiobookBookFileId = "633"
				).whispersyncLaunchAttachment(),
				format.name
			)
		}
	}

	private fun readerRoute(
		publicationFormat: ReaderPublicationFormat = ReaderPublicationFormat.Epub,
		whispersyncSidecarUrl: String? = null,
		whispersyncArtifactId: String? = null,
		whispersyncAudiobookId: String? = null,
		whispersyncAudiobookBookFileId: String? = null,
		whispersyncAudiobookTitle: String? = null,
		whispersyncWordSync: BinderyWordSyncReference? = null
	): Screen.Reader =
		Screen.Reader(
			title = "The Hobbit",
			publicationUrl = "https://bindery.local/opds/books/3816/resources/ebook-435",
			bookId = "3816",
			resourceHref = "/opds/books/3816/resources/ebook-435",
			kind = ReaderPublicationKind.Ebook,
			publicationFormat = publicationFormat,
			whispersyncSidecarUrl = whispersyncSidecarUrl,
			whispersyncArtifactId = whispersyncArtifactId,
			whispersyncAudiobookId = whispersyncAudiobookId,
			whispersyncAudiobookBookFileId = whispersyncAudiobookBookFileId,
			whispersyncAudiobookTitle = whispersyncAudiobookTitle,
			whispersyncWordSync = whispersyncWordSync
		)
}
