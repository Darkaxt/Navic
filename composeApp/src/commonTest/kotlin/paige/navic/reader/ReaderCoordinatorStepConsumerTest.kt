package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

class ReaderCoordinatorStepConsumerTest {
	@Test
	fun coordinatorStepConsumerUpdatesCoordinatorAndSendsProgressIntentToSaveSink() {
		val nextCoordinator = ReaderCoordinator()
		val progress = BinderyReadingProgress(
			bookId = "book-1",
			kind = BinderyReadingProgressKind.Ebook,
			resourceHref = "publication.epub",
			textHref = "EPUB/Text/chapter-04.xhtml",
			cfi = "epubcfi(/6/10!/4/3:12)",
			fragmentId = "p9",
			progressFraction = 0.62
		)
		var appliedCoordinator: ReaderCoordinator? = null
		val savedProgress = mutableListOf<BinderyReadingProgress>()

		applyReaderCoordinatorStep(
			step = ReaderCoordinatorStep(
				coordinator = nextCoordinator,
				progressToSave = progress
			),
			updateCoordinator = { appliedCoordinator = it },
			saveProgress = { savedProgress += it }
		)

		assertEquals(nextCoordinator, appliedCoordinator)
		assertEquals(listOf(progress), savedProgress)
	}

	@Test
	fun coordinatorStepConsumerDoesNotSaveWhenStepHasNoProgressIntent() {
		val nextCoordinator = ReaderCoordinator()
		var appliedCoordinator: ReaderCoordinator? = null
		val savedProgress = mutableListOf<BinderyReadingProgress>()

		applyReaderCoordinatorStep(
			step = ReaderCoordinatorStep(coordinator = nextCoordinator),
			updateCoordinator = { appliedCoordinator = it },
			saveProgress = { savedProgress += it }
		)

		assertEquals(nextCoordinator, appliedCoordinator)
		assertEquals(emptyList(), savedProgress)
	}
}
