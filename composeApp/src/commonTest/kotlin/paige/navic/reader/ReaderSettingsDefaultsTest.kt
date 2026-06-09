package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsDefaultsTest {
	@Test
	fun readerSettingsDefaultsNormalizePersistedValues() {
		assertEquals(
			ReaderSettings(
				fontFamily = ReaderSerifFontFamily,
				fontSizePercent = 180,
				lineHeight = 1.2,
				marginPercent = 24,
				theme = "light",
				paged = false,
				webContentsDebuggingEnabled = true
			),
			normalizedReaderSettings(
				fontFamily = ReaderSerifFontFamily,
				fontSizePercent = 260,
				lineHeightPercent = 80,
				marginPercent = 60,
				theme = "sepia",
				paged = false,
				webContentsDebuggingEnabled = true
			)
		)
	}

	@Test
	fun readerSettingsDefaultsKeepValidConfiguredValues() {
		assertEquals(
			ReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 112,
				lineHeight = 1.7,
				marginPercent = 8,
				theme = "dark",
				paged = true,
				webContentsDebuggingEnabled = false
			),
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 112,
				lineHeightPercent = 170,
				marginPercent = 8,
				theme = "dark",
				paged = true,
				webContentsDebuggingEnabled = false
			)
		)
	}
}
