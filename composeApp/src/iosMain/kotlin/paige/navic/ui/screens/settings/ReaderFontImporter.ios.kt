package paige.navic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import paige.navic.reader.ReaderImportedFont

@Composable
actual fun rememberReaderFontImporter(
	onImported: (ReaderImportedFont) -> Unit,
	onError: (String) -> Unit
): ReaderFontImporter =
	remember(onError) {
		object : ReaderFontImporter {
			override val supported: Boolean = false

			override fun launch() {
				onError("Font import is not available on this platform.")
			}
		}
	}
