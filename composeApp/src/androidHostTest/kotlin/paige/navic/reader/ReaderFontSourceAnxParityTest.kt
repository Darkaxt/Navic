package paige.navic.reader

import java.io.File
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReaderFontSourceAnxParityTest {

	private val root: File = sequence {
		var candidate = kotlin.io.path.Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderImportedFont.kt").exists()
	}.toFile()

	private val anxFontsProviderText: String by lazy {
		anxReferenceFile("lib/providers/fonts.dart").readText()
	}

	private val anxFontServiceText: String by lazy {
		anxReferenceFile("lib/service/font.dart").readText()
	}

	private val anxFontModelText: String by lazy {
		anxReferenceFile("lib/models/font_model.dart").readText()
	}

	private val navicFontModelText: String by lazy {
		root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderImportedFont.kt").readText()
	}

	private val navicFontCacheText: String by lazy {
		root.resolve("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderImportedFontCache.android.kt")
			.readText()
	}

	private val navicAndroidImporterText: String by lazy {
		root.resolve("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.android.kt")
			.readText()
	}

	private val navicEbooksSettingsText: String by lazy {
		root.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbooksScreen.kt")
			.readText()
	}

	private val navicReaderHelpersText: String by lazy {
		root.resolve("composeApp/src/androidMain/assets/reader/navic-reader-helpers.js").readText()
	}

	private val parityPlanText: String by lazy {
		root.resolve("docs/superpowers/specs/2026-06-17-anx-parity-7-phase-plan.md").readText()
	}

	private fun anxReferenceFile(relativePath: String): File =
		listOf(
			root.resolve("tmp/references/anx-reader/$relativePath"),
			root.resolve("../tmp/references/anx-reader/$relativePath")
		).firstOrNull { it.isFile }
			?: error("Could not locate Anx reference: $relativePath")

	@Test
	fun anxFontSourcesExposeRemoteManifestDownloadLocalImportAndWebViewUrl() {
		for (symbol in listOf(
			"const String fontManifestUrl",
			"RemoteFontModel",
			"FontDownloads",
			"startDownload(RemoteFontModel font)",
			"dio.download(",
			"tempFile.copy(finalFilePath)",
			"tempFile.delete()"
		)) {
			assertContains(anxFontsProviderText, symbol, message = "Anx font provider contract missing expected symbol: $symbol")
		}
		for (symbol in listOf(
			"FilePicker.platform.pickFiles",
			"allowedExtensions: ['ttf', 'otf']",
			"newFile.copy"
		)) {
			assertContains(anxFontServiceText, symbol, message = "Anx local font import contract missing expected symbol: $symbol")
		}
		for (symbol in listOf(
			"class FontModel",
			"final String label",
			"final String name",
			"String path",
			"Server().port}/fonts/",
			"litePath"
		)) {
			assertContains(anxFontModelText, symbol, message = "Anx WebView font URL model missing expected symbol: $symbol")
		}
	}

	@Test
	fun navicFontSourceParityIsLocalImportOnlyUntilRemoteManifestIsImplemented() {
		for (symbol in listOf("ReaderImportedFont", "val family: String", "val url: String", "val byteSize: Long")) {
			assertContains(navicFontModelText, symbol, message = "Navic imported font model missing local imported font field: $symbol")
		}
		for (symbol in listOf(
			"fun importFont(",
			"InputStream",
			"readerImportedFontExtension",
			"readerPublicationAssetUrl(\"$" + "ReaderImportedFontDirectoryName/",
			"fun clearImportedFonts()",
			"fun cachedFontsByteSize()"
		)) {
			assertContains(navicFontCacheText, symbol, message = "Navic imported font cache missing local import/cache surface: $symbol")
		}
		for (symbol in listOf(
			"ActivityResultContracts.OpenDocument",
			"fontCache.importFont",
			"fontCache.clearImportedFonts"
		)) {
			assertContains(navicAndroidImporterText, symbol, message = "Navic Android font importer missing local import UI surface: $symbol")
		}
		for (symbol in listOf("ReaderFontSourceCustom", "fontImporter.launch()", "fontImporter.clearImportedFonts()")) {
			assertContains(navicEbooksSettingsText, symbol, message = "Navic settings missing imported-font source surface: $symbol")
		}
		for (symbol in listOf("readerCustomFontUrl", "/reader-cache/fonts/", "readerFontFaceCss")) {
			assertContains(navicReaderHelpersText, symbol, message = "Navic WebView runtime missing imported font URL/CSS surface: $symbol")
		}

		val remoteManifestCacheImplemented = listOf(
			"ReaderRemoteFontManifest",
			"fetchRemoteFontManifest",
			"downloadRemoteFont",
			"listRemoteFonts",
			"deleteRemoteFont"
		).all { symbol ->
			navicFontModelText.contains(symbol) ||
				navicFontCacheText.contains(symbol)
		}
		val remoteManifestUiImplemented = listOf(
			"fetchRemoteFontManifest",
			"downloadRemoteFont",
			"listRemoteFonts"
		).all { symbol ->
			navicAndroidImporterText.contains(symbol) ||
				navicEbooksSettingsText.contains(symbol)
		}
		val remoteManifestProgressImplemented = listOf(
			"remoteFontProgress",
			"pauseRemoteFontDownload",
			"cancelRemoteFontDownload"
		).all { symbol ->
			navicAndroidImporterText.contains(symbol) ||
				navicEbooksSettingsText.contains(symbol)
		}
		val remoteManifestImplemented = remoteManifestCacheImplemented && remoteManifestUiImplemented && remoteManifestProgressImplemented
		if (remoteManifestImplemented) {
			assertContains(parityPlanText, "Font remote manifest parity: Exists")
		} else {
			assertContains(parityPlanText, "Font remote manifest parity: Failing")
			if (remoteManifestCacheImplemented && remoteManifestUiImplemented) {
				assertContains(parityPlanText, "Settings UI can refresh, download, select, and delete remote fonts")
				assertContains(parityPlanText, "Per-file remote font download progress/pause/cancel is not surfaced yet")
			} else if (remoteManifestCacheImplemented) {
				assertContains(parityPlanText, "Cache parser/downloader/list/delete is implemented")
				assertContains(parityPlanText, "Settings UI does not expose remote font browsing/download/selection")
			} else {
				assertContains(parityPlanText, "Remote manifest fetch/download/cache/list/delete is not implemented in Navic yet.")
			}
		}
		assertTrue(
			remoteManifestImplemented || parityPlanText.contains("Phase 7 cannot be closed as full font-source parity"),
			"Missing remote font manifest support must be documented as a non-closable Phase 7 gap, not hidden by local import coverage."
		)
	}

	@Test
	fun navicSettingsExposeRemoteFontManifestDownloadSelectionAndDeletion() {
		val commonImporterText = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.kt")
			.readText()

		for (symbol in listOf(
			"val remoteFonts: List<ReaderRemoteFontManifestEntry>",
			"val cachedRemoteFonts: List<ReaderCachedRemoteFont>",
			"fun refreshRemoteFonts()",
			"fun downloadRemoteFont(font: ReaderRemoteFontManifestEntry)",
			"fun deleteRemoteFont(id: String)"
		)) {
			assertContains(commonImporterText, symbol, message = "Common font importer missing remote font contract: $symbol")
		}
		for (symbol in listOf(
			"fontCache.fetchRemoteFontManifest",
			"fontCache.downloadRemoteFont",
			"fontCache.listRemoteFonts",
			"fontCache.deleteRemoteFont"
		)) {
			assertContains(navicAndroidImporterText, symbol, message = "Android font importer missing remote cache route: $symbol")
		}
		for (symbol in listOf(
			"fontImporter.refreshRemoteFonts()",
			"fontImporter.downloadRemoteFont(remoteFont)",
			"fontImporter.deleteRemoteFont(cachedRemoteFont.id)",
			"preferenceManager.readerCustomFontFamily = cachedRemoteFont.family",
			"preferenceManager.readerCustomFontUrl = cachedRemoteFont.fonts.firstOrNull()?.url.orEmpty()"
		)) {
			assertContains(navicEbooksSettingsText, symbol, message = "Ebooks settings missing remote font UI route: $symbol")
		}
	}

	@Test
	fun navicSettingsExposeAnxRemoteFontDownloadProgressPauseResumeAndCancel() {
		val commonImporterText = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.kt")
			.readText()

		for (symbol in listOf(
			"DownloadStatus.downloading",
			"DownloadStatus.paused",
			"DownloadStatus.failed",
			"progress",
			"pauseDownload(String fontId)",
			"resumeDownload(RemoteFontModel font)",
			"cancelDownload(String fontId)"
		)) {
			assertContains(anxFontsProviderText, symbol, message = "Anx FontDownloads contract missing expected symbol: $symbol")
		}
		for (symbol in listOf(
			"ReaderRemoteFontDownloadState",
			"val remoteFontDownloads: Map<String, ReaderRemoteFontDownloadState>",
			"fun pauseRemoteFontDownload(id: String)",
			"fun resumeRemoteFontDownload(font: ReaderRemoteFontManifestEntry)",
			"fun cancelRemoteFontDownload(id: String)"
		)) {
			assertContains(commonImporterText, symbol, message = "Common font importer missing remote download state/control contract: $symbol")
		}
		for (symbol in listOf(
			"remoteFontDownloadsState",
			"ReaderRemoteFontDownloadStatusDownloading",
			"ReaderRemoteFontDownloadStatusPaused",
			"ReaderRemoteFontDownloadStatusFailed",
			"override fun pauseRemoteFontDownload(id: String)",
			"override fun resumeRemoteFontDownload(font: ReaderRemoteFontManifestEntry)",
			"override fun cancelRemoteFontDownload(id: String)"
		)) {
			assertContains(navicAndroidImporterText, symbol, message = "Android font importer missing Anx remote download state/control route: $symbol")
		}
		for (symbol in listOf(
			"fontImporter.remoteFontDownloads[remoteFont.id]",
			"remoteFontDownloadValue",
			"fontImporter.pauseRemoteFontDownload(remoteFont.id)",
			"fontImporter.resumeRemoteFontDownload(remoteFont)",
			"fontImporter.cancelRemoteFontDownload(remoteFont.id)"
		)) {
			assertContains(navicEbooksSettingsText, symbol, message = "Ebooks settings missing Anx remote download progress/pause/resume/cancel UI route: $symbol")
		}
		assertContains(
			parityPlanText,
			"Font remote manifest parity: Exists",
			message = "Once Navic exposes Anx-style remote font download state/control routes, Phase 7 font-source parity should not remain documented as Failing."
		)
	}
}
