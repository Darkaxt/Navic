package paige.navic.ui.screens.reader

import androidx.compose.ui.graphics.Color
import paige.navic.reader.ReaderAgedPaperTheme
import paige.navic.reader.ReaderBlackTheme
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderDuskTheme
import paige.navic.reader.ReaderSepiaTheme
import paige.navic.reader.normalizedReaderTheme

internal fun readerThemeForegroundColor(theme: String?): Color =
	when (normalizedReaderTheme(theme)) {
		ReaderSepiaTheme -> Color(0xFF2B2118)
		ReaderAgedPaperTheme -> Color(0xFF261B10)
		ReaderDuskTheme -> Color(0xFFECE7F6)
		ReaderDarkTheme -> Color(0xFFF2F0EA)
		ReaderBlackTheme -> Color(0xFFF3F3F3)
		else -> Color(0xFF1D1B18)
	}
