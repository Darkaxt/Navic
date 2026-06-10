package paige.navic.ui.screens.bindery

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceMetadata

class BinderyAudiobookPlayerPolicyTest {
	@Test
	fun mainTransportUsesTimeSkipsAroundPlayPause() {
		assertEquals(
			listOf(
				BinderyAudiobookTransportControl.SeekBackward30,
				BinderyAudiobookTransportControl.SeekBackward10,
				BinderyAudiobookTransportControl.PlayPause,
				BinderyAudiobookTransportControl.SeekForward10,
				BinderyAudiobookTransportControl.SeekForward30
			),
			binderyAudiobookTransportControls()
		)
	}

	@Test
	fun selectedEditionFiltersChapterIndexByBookFileId() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Book One - Chapter 1", "file-one"),
				audioItem("two-a", "Book Two - Chapter 1", "file-two"),
				audioItem("one-b", "Book One - Chapter 2", "file-one"),
				BinderyReadingOrderItem(
					href = "cover.jpg",
					title = "Cover",
					type = "image/jpeg"
				)
			)
		)

		val chapters = binderyAudiobookChapters(
			manifest = manifest,
			versionRowId = "audiobook:file-one"
		)

		assertEquals(listOf("Book One - Chapter 1", "Book One - Chapter 2"), chapters.map { it.title })
		assertEquals(listOf(0, 1), chapters.map { it.index })
		assertEquals(listOf("one-a", "one-b"), chapters.map { it.href })
	}

	@Test
	fun chapterIndexFallsBackToAllAudioWhenEditionIsUnknown() {
		val manifest = BinderyManifest(
			id = "book-1",
			title = "Book",
			readingOrder = listOf(
				audioItem("one-a", "Chapter 1", "file-one"),
				audioItem("two-a", "Chapter 2", "file-two")
			)
		)

		val chapters = binderyAudiobookChapters(
			manifest = manifest,
			versionRowId = "audiobook:missing"
		)

		assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })
	}

	private fun audioItem(
		href: String,
		title: String,
		bookFileId: String
	): BinderyReadingOrderItem =
		BinderyReadingOrderItem(
			href = href,
			title = title,
			type = "audio/mpeg",
			durationSeconds = 60.0,
			properties = mapOf("bookFileId" to bookFileId),
			metadata = BinderyResourceMetadata(resourceKey = href)
		)
}
