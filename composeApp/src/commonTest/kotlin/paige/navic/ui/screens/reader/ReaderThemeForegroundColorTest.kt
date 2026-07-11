package paige.navic.ui.screens.reader

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.reader.ReaderAgedPaperTheme
import paige.navic.reader.ReaderBlackTheme
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderDuskTheme
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderSepiaTheme

class ReaderThemeForegroundColorTest {
	@Test
	fun foregroundColorsMatchTheReaderWebViewPalette() {
		assertEquals(Color(0xFF1D1B18), readerThemeForegroundColor(ReaderLightTheme))
		assertEquals(Color(0xFF2B2118), readerThemeForegroundColor(ReaderSepiaTheme))
		assertEquals(Color(0xFF261B10), readerThemeForegroundColor(ReaderAgedPaperTheme))
		assertEquals(Color(0xFFECE7F6), readerThemeForegroundColor(ReaderDuskTheme))
		assertEquals(Color(0xFFF2F0EA), readerThemeForegroundColor(ReaderDarkTheme))
		assertEquals(Color(0xFFF3F3F3), readerThemeForegroundColor(ReaderBlackTheme))
	}

	@Test
	fun unknownThemeUsesTheReaderLightForeground() {
		assertEquals(Color(0xFF1D1B18), readerThemeForegroundColor("unknown"))
		assertEquals(Color(0xFF1D1B18), readerThemeForegroundColor(null))
	}
}
