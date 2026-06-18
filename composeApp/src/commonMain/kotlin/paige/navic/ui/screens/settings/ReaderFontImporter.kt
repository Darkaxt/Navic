package paige.navic.ui.screens.settings

import androidx.compose.runtime.Composable
import paige.navic.reader.ReaderCachedRemoteFont
import paige.navic.reader.ReaderImportedFont
import paige.navic.reader.ReaderRemoteFontDownloadState
import paige.navic.reader.ReaderRemoteFontManifestEntry

interface ReaderFontImporter {
	val supported: Boolean
	val cachedFontBytes: Long
	val remoteFonts: List<ReaderRemoteFontManifestEntry>
	val cachedRemoteFonts: List<ReaderCachedRemoteFont>
	val remoteFontDownloads: Map<String, ReaderRemoteFontDownloadState>
	val remoteFontLoading: Boolean
	val remoteFontError: String?
	fun launch()
	fun clearImportedFonts()
	fun refreshRemoteFonts()
	fun downloadRemoteFont(font: ReaderRemoteFontManifestEntry)
	fun pauseRemoteFontDownload(id: String)
	fun resumeRemoteFontDownload(font: ReaderRemoteFontManifestEntry)
	fun cancelRemoteFontDownload(id: String)
	fun deleteRemoteFont(id: String)
}

@Composable
expect fun rememberReaderFontImporter(
	onImported: (ReaderImportedFont) -> Unit,
	onError: (String) -> Unit
): ReaderFontImporter
