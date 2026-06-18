package paige.navic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import paige.navic.reader.ReaderCachedRemoteFont
import paige.navic.reader.ReaderImportedFont
import paige.navic.reader.ReaderRemoteFontDownloadState
import paige.navic.reader.ReaderRemoteFontManifestEntry

@Composable
actual fun rememberReaderFontImporter(
	onImported: (ReaderImportedFont) -> Unit,
	onError: (String) -> Unit
): ReaderFontImporter =
	remember(onError) {
		object : ReaderFontImporter {
			override val supported: Boolean = false
			override val cachedFontBytes: Long = 0L
			override val remoteFonts: List<ReaderRemoteFontManifestEntry> = emptyList()
			override val cachedRemoteFonts: List<ReaderCachedRemoteFont> = emptyList()
			override val remoteFontDownloads: Map<String, ReaderRemoteFontDownloadState> = emptyMap()
			override val remoteFontLoading: Boolean = false
			override val remoteFontError: String? = null

			override fun launch() {
				onError("Font import is not available on this platform.")
			}

			override fun clearImportedFonts() = Unit

			override fun refreshRemoteFonts() {
				onError("Remote font download is not available on this platform.")
			}

			override fun downloadRemoteFont(font: ReaderRemoteFontManifestEntry) {
				onError("Remote font download is not available on this platform.")
			}

			override fun pauseRemoteFontDownload(id: String) = Unit

			override fun resumeRemoteFontDownload(font: ReaderRemoteFontManifestEntry) {
				onError("Remote font download is not available on this platform.")
			}

			override fun cancelRemoteFontDownload(id: String) = Unit

			override fun deleteRemoteFont(id: String) = Unit
		}
	}
