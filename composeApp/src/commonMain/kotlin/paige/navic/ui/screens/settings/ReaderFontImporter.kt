package paige.navic.ui.screens.settings

import androidx.compose.runtime.Composable
import paige.navic.reader.ReaderImportedFont

interface ReaderFontImporter {
	val supported: Boolean
	val cachedFontBytes: Long
	fun launch()
	fun clearImportedFonts()
}

@Composable
expect fun rememberReaderFontImporter(
	onImported: (ReaderImportedFont) -> Unit,
	onError: (String) -> Unit
): ReaderFontImporter
