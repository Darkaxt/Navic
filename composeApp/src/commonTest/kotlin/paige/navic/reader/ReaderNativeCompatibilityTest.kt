package paige.navic.reader

import com.russhwolf.settings.MapSettings
import paige.navic.domain.manager.PreferenceManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** JVM characterization controls; the same assertions also run in the Native test target. */
class ReaderNativeCompatibilityTest {
	@Test
	fun openingAtLastGenerationThrowsWithoutChangingController() {
		val controller = ReaderController(state = ReaderControllerState(readerSessionGeneration = Long.MAX_VALUE))
		assertFailsWith<ArithmeticException> { controller.open(request()) }
		assertEquals(Long.MAX_VALUE, controller.state.readerSessionGeneration)
	}

	@Test
	fun openingAtPenultimateGenerationReachesMaximumExactly() {
		val controller = ReaderController(state = ReaderControllerState(readerSessionGeneration = Long.MAX_VALUE - 1))
		assertEquals(Long.MAX_VALUE, controller.open(request()).controller.state.readerSessionGeneration)
		assertEquals(1L, ReaderController().open(request()).controller.state.readerSessionGeneration)
	}

	@Test
	fun preferenceEncodingRetainsLexicalOrderAndExactFormat() {
		val preferences = PreferenceManager(MapSettings())
		preferences.setReaderBookSettings("z", ReaderSettings(fontSizePercent = 120))
		preferences.setReaderBookSettings("a", ReaderSettings(fontSizePercent = 110))
		// Boolean assertions prevent serialization contents entering a failure receipt.
		assertTrue(preferences.readerBookSettingsJson ==
			"""{"version":1,"books":{"a":{"fontSizePercent":110},"z":{"fontSizePercent":120}}}""")
		assertEquals(110, preferences.readerBookSettings("a")?.fontSizePercent)
		preferences.clearReaderBookSettings("a")
		preferences.clearReaderBookSettings("z")
		assertTrue(preferences.readerBookSettingsJson.isEmpty())
	}

	private fun request() = ReaderEngineOpenRequest(
		publication = ReaderPublicationIdentity(bookId = "", resourceHref = "", format = ReaderPublicationFormat.Epub),
		url = "",
		settings = defaultReaderSettings()
	)
}
