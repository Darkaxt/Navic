package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPageTurnContentReadyKeyTest {
	@Test
	fun measuringAndFailedProfilesDoNotSignalRasterReadiness() {
		assertNull(
			readerPageTurnContentReadyKey(
				ReaderPaginationProfileStatus(
					status = "measuring",
					fingerprint = "book-layout",
					pageCount = 120
				)
			)
		)
		assertNull(
			readerPageTurnContentReadyKey(
				ReaderPaginationProfileStatus(
					status = "failed",
					fingerprint = "book-layout",
					pageCount = 120
				)
			)
		)
	}

	@Test
	fun cachedAndReadyProfilesProduceStableContentKeys() {
		val cached = readerPageTurnContentReadyKey(
			ReaderPaginationProfileStatus(
				status = "cached",
				fingerprint = "book-layout",
				pageCount = 120
			)
		)
		val ready = readerPageTurnContentReadyKey(
			ReaderPaginationProfileStatus(
				status = "ready",
				fingerprint = "book-layout",
				pageCount = 120
			)
		)

		assertEquals("book-layout:120", cached)
		assertEquals(cached, ready)
	}
}
