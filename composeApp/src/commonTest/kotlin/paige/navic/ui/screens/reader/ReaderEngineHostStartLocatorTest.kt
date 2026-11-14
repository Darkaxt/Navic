package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.reader.ReaderLocator

class ReaderEngineHostStartLocatorTest {
	private val openTime = ReaderLocator(href = "ch01.xhtml", cfi = "epubcfi(/6/4!/4/2)", progress = 0.02)
	private val reading = ReaderLocator(href = "ch03.xhtml", cfi = "epubcfi(/6/8!/4/40)", progress = 0.11)

	@Test
	fun firstHostOpensAtTheRequestedLocator() {
		assertEquals(
			openTime,
			readerEngineHostStartLocator(
				composedStartLocator = openTime,
				composedReadingLocator = openTime,
				startLocator = openTime
			)
		)
	}

	@Test
	fun rebuiltHostResumesAtTheLiveReadingPosition() {
		// Rotation tears the native host down; the fresh host was composed after turns.
		assertEquals(
			reading,
			readerEngineHostStartLocator(
				composedStartLocator = openTime,
				composedReadingLocator = reading,
				startLocator = openTime
			)
		)
	}

	@Test
	fun laterTurnsNeverChangeTheOpenLocatorOfALiveHost() {
		// The composed reading position is captured once, so turns cannot reopen the book.
		val first = readerEngineHostStartLocator(openTime, openTime, openTime)
		val afterTurns = readerEngineHostStartLocator(openTime, openTime, openTime)
		assertEquals(first, afterTurns)
	}

	@Test
	fun aNewRequestedLocatorWinsOverTheComposedReadingPosition() {
		val chosen = ReaderLocator(href = "ch09.xhtml", progress = 0.5)
		assertEquals(
			chosen,
			readerEngineHostStartLocator(
				composedStartLocator = openTime,
				composedReadingLocator = reading,
				startLocator = chosen
			)
		)
	}

	@Test
	fun missingReadingPositionFallsBackToTheRequestedLocator() {
		assertEquals(openTime, readerEngineHostStartLocator(openTime, null, openTime))
	}
}
