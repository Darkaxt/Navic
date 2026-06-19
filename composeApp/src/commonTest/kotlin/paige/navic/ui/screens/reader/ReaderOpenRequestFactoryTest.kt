package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.defaultReaderSettings
import paige.navic.ui.navigation.Screen

class ReaderOpenRequestFactoryTest {
	@Test
	fun openRequestUsesSavedBinderyProgressWhenRouteHasNoPreciseStartLocator() {
		val savedProgress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			textHref = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			fragmentId = "p9",
			progressFraction = 0.62
		)

		val request = hobbitReader().toReaderEngineOpenRequest(
			publicationUrl = "https://appassets.androidplatform.net/reader-cache/3693/publication.epub",
			shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/3693/cover.jpg",
			settings = defaultReaderSettings(),
			savedProgress = savedProgress
		)

		assertEquals(
			ReaderLocator(
				href = "EPUB/Text/chapter-04.xhtml#p9",
				cfi = "epubcfi(/6/10!/4/3:12)",
				progress = 0.62
			),
			request.startLocator
		)
	}

	@Test
	fun openRequestKeepsExplicitRouteStartLocatorOverOlderSavedProgress() {
		val savedProgress = BinderyReadingProgress(
			bookId = "3693",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "/opds/books/3693/resources/ebook-1",
			textHref = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			progressFraction = 0.42
		)
		val routeLocator = ReaderLocator(
			href = "EPUB/Text/chapter-08.xhtml",
			cfi = "epubcfi(/6/18!/4/3:12)"
		)

		val request = hobbitReader(
			startHref = routeLocator.href,
			startCfi = routeLocator.cfi
		).toReaderEngineOpenRequest(
			publicationUrl = "https://appassets.androidplatform.net/reader-cache/3693/publication.epub",
			shellCoverUrl = null,
			settings = defaultReaderSettings(),
			savedProgress = savedProgress
		)

		assertEquals(routeLocator, request.startLocator)
	}

	@Test
	fun openRequestCarriesExplicitRouteProgressForReaderDevResumeValidation() {
		val request = hobbitReader(
			startProgress = 0.37
		).toReaderEngineOpenRequest(
			publicationUrl = "https://appassets.androidplatform.net/reader-cache/3693/publication.epub",
			shellCoverUrl = null,
			settings = defaultReaderSettings(),
			savedProgress = null
		)

		assertEquals(ReaderLocator(progress = 0.37), request.startLocator)
	}

	private fun hobbitReader(
		startHref: String? = null,
		startCfi: String? = null,
		startProgress: Double? = null
	): Screen.Reader =
		Screen.Reader(
			title = "The Hobbit",
			publicationUrl = "https://bindery.local/opds/books/3693/resources/ebook-1",
			bookId = "3693",
			resourceHref = "/opds/books/3693/resources/ebook-1",
			kind = ReaderPublicationKind.Ebook,
			publicationFormat = ReaderPublicationFormat.Epub,
			mediaOverlayEnabled = false,
			startHref = startHref,
			startCfi = startCfi,
			startProgress = startProgress
		)
}
