package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeCommonChromeTest {
	@Test
	fun androidReaderPackagesBundledFontSourcesForWebViewRendering() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val literata = root.resolve("fonts/navic-literata-regular.ttf")
		val atkinson = root.resolve("fonts/navic-atkinson-hyperlegible-regular.otf")
		val openDyslexic = root.resolve("fonts/navic-opendyslexic-regular.otf")

		assertTrue(literata.isFile, "Literata must be bundled for book font rendering")
		assertTrue(atkinson.isFile, "Atkinson Hyperlegible must be bundled for humanist font rendering")
		assertTrue(openDyslexic.isFile, "OpenDyslexic must be bundled for dyslexic font rendering")
		assertTrue(literata.length() > 16_000, "Literata asset should be a real font file")
		assertTrue(atkinson.length() > 16_000, "Atkinson asset should be a real font file")
		assertTrue(openDyslexic.length() > 16_000, "OpenDyslexic asset should be a real font file")
		assertContains(bridgeText, "readerFontFaceCss")
		assertContains(bridgeText, "@font-face")
		assertContains(bridgeText, "Navic Literata")
		assertContains(bridgeText, "Navic Atkinson Hyperlegible")
		assertContains(bridgeText, "Navic OpenDyslexic")
		assertContains(bridgeText, "American Typewriter")
		assertContains(bridgeText, "Courier Prime")
		assertContains(bridgeText, "fonts/navic-literata-regular.ttf")
		assertContains(bridgeText, "fonts/navic-atkinson-hyperlegible-regular.otf")
		assertContains(bridgeText, "fonts/navic-opendyslexic-regular.otf")
		assertContains(bridgeText, "ReaderFontSourceNavic")
		assertContains(bridgeText, "ReaderFontSourceSystem")
		assertContains(bridgeText, "ReaderFontSourcePublisher")
		assertContains(bridgeText, "ReaderFontSourceCustom")
		assertContains(bridgeText, "readerCustomFontUrl")
		assertContains(bridgeText, "readerFontFaceCss(settings)")
		assertContains(bridgeText, "readerEffectiveFontFamily(settings)")
		assertContains(bridgeText, "settings?.fontSource")
		assertContains(readerScreenText, "Font source")
		assertContains(readerScreenText, "ReaderSupportedFontSources")
		assertContains(ebooksSettingsText, "readerFontSource")
		assertContains(ebooksSettingsText, "ReaderFontSourceOption")
		assertContains(searchSettingsText, "ebooks.font-source")
		assertContains(searchSettingsText, "ReaderSupportedFontSources")
	}

	@Test
	fun commonReaderChromeExposesDimOverlayControl() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "KomikkuReaderContentOverlay")
		assertContains(readerScreenText, "Modifier.matchParentSize()")
		assertContains(readerScreenText, "drawRect(Color.Black.copy")
		assertContains(readerScreenText, "Dim overlay")
		assertContains(readerScreenText, "adjustDimOverlay")
		assertContains(ebooksSettingsText, "readerDimOverlayPercent")
		assertContains(ebooksSettingsText, "option_ebook_reader_dim_overlay")
		assertContains(searchSettingsText, "ebooks.dim-overlay")
		assertContains(searchSettingsText, "readerDimOverlayPercent")
	}

	@Test
	fun commonReaderCustomFilterPortsKomikkuColorFilterControls() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val preferencesText = listOf(
			File("src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate PreferenceManager.kt")
		val bridgeText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(readerScreenText, "readerColorFilterColor(controllerState.chrome.settings)")
		assertContains(readerScreenText, "readerColorFilterBlendMode(controllerState.chrome.settings.colorFilterMode)")
		assertContains(readerScreenText, "Color filter")
		assertContains(readerScreenText, "Grayscale")
		assertContains(readerScreenText, "Inverted colors")
		assertContains(readerScreenText, "ReaderSupportedColorFilterModes")
		assertContains(readerScreenText, "Red")
		assertContains(readerScreenText, "Green")
		assertContains(readerScreenText, "Blue")
		assertContains(readerScreenText, "Alpha")
		assertContains(readerScreenText, "updateReaderColorFilterChannel")
		assertContains(preferencesText, "readerColorFilterEnabled")
		assertContains(preferencesText, "readerColorFilterArgb")
		assertContains(preferencesText, "readerColorFilterMode")
		assertContains(preferencesText, "readerGrayscaleEnabled")
		assertContains(preferencesText, "readerInvertedColors")
		assertContains(bridgeText, "colorFilterEnabled")
		assertContains(bridgeText, "colorFilterArgb")
		assertContains(bridgeText, "colorFilterMode")
		assertContains(bridgeText, "grayscaleEnabled")
		assertContains(bridgeText, "invertedColors")
		assertContains(ebooksSettingsText, "option_ebook_reader_grayscale")
		assertContains(ebooksSettingsText, "readerGrayscaleEnabled")
		assertContains(ebooksSettingsText, "option_ebook_reader_inverted_colors")
		assertContains(ebooksSettingsText, "readerInvertedColors")
		assertContains(searchSettingsText, "ebooks.grayscale")
		assertContains(searchSettingsText, "ebooks.inverted-colors")
	}

	@Test
	fun androidReaderExposesKomikkuStyleOrientationControl() {
		val orientationEffectText = readerAndroidFile("ReaderOrientationEffect.android.kt").readText()
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(orientationEffectText, "SCREEN_ORIENTATION_FULL_SENSOR")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_REVERSE_PORTRAIT")
		assertContains(orientationEffectText, "activity.requestedOrientation = previousOrientation")
		assertContains(readerScreenText, "ReaderOrientationEffect(orientation = settings.orientation)")
		assertContains(readerScreenText, "Rotation")
		assertContains(readerScreenText, "ReaderSupportedOrientations")
		assertContains(ebooksSettingsText, "readerOrientation")
		assertContains(ebooksSettingsText, "option_ebook_reader_orientation")
		assertContains(searchSettingsText, "ebooks.orientation")
		assertContains(searchSettingsText, "ReaderSupportedOrientations")
	}

	@Test
	fun commonReaderChromeExposesVolumeKeyPageTurnControl() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "onPreviewKeyEvent")
		assertContains(readerScreenText, "Key.VolumeUp")
		assertContains(readerScreenText, "Key.VolumeDown")
		assertContains(readerScreenText, "volumeKeyPageTurns")
		assertContains(readerScreenText, "Volume keys")
		assertContains(ebooksSettingsText, "readerVolumeKeyPageTurns")
		assertContains(ebooksSettingsText, "option_ebook_reader_volume_keys")
		assertContains(searchSettingsText, "ebooks.volume-keys")
		assertContains(searchSettingsText, "readerVolumeKeyPageTurns")
	}

	@Test
	fun commonReaderDefaultSettingsRememberKeyTracksReaderPreferenceInputs() {
		val readerScreenText = readerScreenFile().readText()
		val defaultSettingsRemember = readerScreenText
			.substringAfter("val defaultReaderSettings = remember(")
			.substringBefore("\n\t) {")
		val expectedPreferenceInputs = listOf(
			"readerFontFamily",
			"readerFontSource",
			"readerCustomFontFamily",
			"readerCustomFontUrl",
			"readerFontSizePercent",
			"readerLineHeightPercent",
			"readerParagraphSpacingPercent",
			"readerMarginPercent",
			"readerDimOverlayPercent",
			"readerColorFilterEnabled",
			"readerColorFilterArgb",
			"readerColorFilterMode",
			"readerGrayscaleEnabled",
			"readerInvertedColors",
			"readerOrientation",
			"readerTheme",
			"readerDirection",
			"readerFlowMode",
			"readerPaged",
			"readerTapZone",
			"readerSmallerTapZone",
			"readerShowTapZones",
			"readerPublisherStylesEnabled",
			"readerFullscreen",
			"readerKeepScreenOn",
			"readerReadaloudSyncEnabled",
			"readerVolumeKeyPageTurns",
			"readerWebContentsDebuggingEnabled",
			"readerBookSettingsJson"
		)

		assertContains(readerScreenText, "koinInject<PreferenceManager>()")
		assertContains(readerScreenText, "readerSettingsForBook(reader.bookId)")
		expectedPreferenceInputs.forEach { preferenceName ->
			assertContains(
				defaultSettingsRemember,
				"preferenceManager.$preferenceName",
				message = "ReaderScreen default settings must refresh when $preferenceName changes."
			)
		}
	}

	@Test
	fun commonSettingsEbooksScreenCanImportCustomFontIntoPreferences() {
		val ebooksScreenText = settingsFile("EbooksScreen.kt").readText()

		assertContains(ebooksScreenText, "rememberReaderFontImporter(")
		assertContains(ebooksScreenText, "fontImporter.launch()")
		assertContains(ebooksScreenText, "readerFontSource = ReaderFontSourceCustom")
		assertContains(ebooksScreenText, "readerCustomFontFamily = imported.family")
		assertContains(ebooksScreenText, "readerCustomFontUrl = imported.url")
		assertContains(ebooksScreenText, "option_ebook_reader_import_font")
	}

	@Test
	fun commonSettingsEbooksScreenCanClearImportedFontAndShowsFontCacheStorage() {
		val ebooksScreenText = settingsFile("EbooksScreen.kt").readText()
		val fontImporterText = settingsFile("ReaderFontImporter.kt").readText()
		val androidImporterText = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.android.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Android ReaderFontImporter.android.kt")

		assertContains(fontImporterText, "val cachedFontBytes: Long")
		assertContains(fontImporterText, "fun clearImportedFonts()")
		assertContains(androidImporterText, "ReaderImportedFontCache(readerPublicationCacheRoot(context))")
		assertContains(androidImporterText, "fontCache.cachedFontsByteSize()")
		assertContains(androidImporterText, "fontCache.clearImportedFonts()")
		assertContains(ebooksScreenText, "storageSizeText(fontImporter.cachedFontBytes)")
		assertContains(ebooksScreenText, "option_ebook_reader_imported_font_storage")
		assertContains(ebooksScreenText, "option_ebook_reader_clear_imported_font")
		assertContains(ebooksScreenText, "fontImporter.clearImportedFonts()")
		assertContains(ebooksScreenText, "preferenceManager.readerFontSource = ReaderFontSourceNavic")
		assertContains(ebooksScreenText, "preferenceManager.readerCustomFontFamily = \"\"")
		assertContains(ebooksScreenText, "preferenceManager.readerCustomFontUrl = \"\"")
	}

	@Test
	fun commonReaderShellUsesKomikkuEquivalentOverlayStack() {
		val readerScreenText = readerScreenFile().readText()

		assertContains(readerScreenText, "KomikkuReaderNativeFrameHost(")
		assertContains(readerScreenText, "KomikkuComposeOverlay(")
		assertContains(readerScreenText, "KomikkuReaderAppBars(")
		assertContains(readerScreenText, "KomikkuReaderTopBar(")
		assertContains(readerScreenText, "KomikkuReaderSettingsDialog(")
		assertContains(readerScreenText, "BasicAlertDialog(")
		assertContains(readerScreenText, "Modifier.matchParentSize()")
		assertContains(readerScreenText, "modifier = Modifier.align(Alignment.End)")
		assertContains(readerScreenText, "Ported from Komikku ReaderAppBars")
		assertContains(readerScreenText, "Ported from Komikku ReaderSettingsDialog")
		assertFalse(
			readerScreenText.contains("Scaffold(") || readerScreenText.contains("bottomBar ="),
			"Reader shell must follow Komikku's overlay stack; chrome cannot be hosted as a Scaffold bottomBar that resizes content."
		)
		assertFalse(
			readerScreenText.contains("ModalBottomSheet(") || readerScreenText.contains("rememberModalBottomSheetState"),
			"Reader settings must be an overlay dialog above the full-window reader, not a modal bottom sheet tied to app chrome behavior."
		)
		assertFalse(
			readerScreenText.contains(".padding(innerPadding)"),
			"Reader content must remain full-window; chrome/settings padding must never be applied to the content host."
		)
		assertFalse(
			readerScreenText.contains("ReaderOptionsPanel("),
			"The active Komikku reader must not resurrect the old docked options panel."
		)
	}

	@Test
	fun commonReaderChromeUsesKomikkuEquivalentSideProgressRail() {
		val readerScreenText = readerScreenFile().readText()
		val appBarsBody = readerScreenText.substringAfter("private fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val sideRailBody = readerScreenText.substringAfter("private fun KomikkuChapterNavigatorVertical(")
			.substringBefore("\n}\n\ninternal fun Screen.Reader.toReaderEngineOpenRequest(")
		val bottomChromeBody = readerScreenText.substringAfter("private fun KomikkuReaderBottomBar(")
			.substringBefore("\n}\n\n@OptIn(ExperimentalMaterial3Api::class)")

		assertContains(readerScreenText, "KomikkuChapterNavigatorVertical(")
		assertContains(appBarsBody, "KomikkuNavBarType.VerticalRight")
		assertContains(appBarsBody, "modifier = Modifier\n\t\t\t\t\t\t.weight(1f)\n\t\t\t\t\t\t.align(Alignment.End)")
		assertContains(readerScreenText, "KomikkuReaderVerticalRailHeightFraction")
		assertContains(readerScreenText, "private fun KomikkuChapterProgressSlider(")
		assertContains(readerScreenText, "MutableInteractionSource")
		assertContains(readerScreenText, "collectIsDraggedAsState")
		assertContains(readerScreenText, "HapticFeedbackType.TextHandleMove")
		assertContains(readerScreenText, "roundToInt()")
		assertContains(appBarsBody, "contentAlignment = Alignment.CenterEnd")
		assertContains(appBarsBody, "Modifier.fillMaxHeight(KomikkuReaderVerticalRailHeightFraction)")
		assertContains(sideRailBody, "KomikkuChapterProgressSlider(")
		assertContains(sideRailBody, "valueRange = 1..totalPages")
		assertContains(sideRailBody, "onPageIndexChange(page - 1)")
		assertContains(sideRailBody, "Text(text = currentPageText)")
		assertContains(sideRailBody, "Text(text = totalPages.toString())")
		assertContains(sideRailBody, "Icons.Filled.SkipPrevious")
		assertContains(sideRailBody, "Icons.Filled.SkipNext")
		assertFalse(
			sideRailBody.contains("ReaderProgressSeekControl("),
			"The Komikku side rail must not reuse the old bottom progress control."
		)
		assertFalse(
			bottomChromeBody.contains("ReaderProgressSeekControl(") ||
				bottomChromeBody.contains("LinearProgressIndicator("),
			"Komikku-equivalent progress belongs in the side rail overlay, not inside the bottom chrome surface."
		)
	}

	@Test
	fun commonReaderChromeSeparatesTopPanelFromBottomActions() {
		val readerScreenText = readerScreenFile().readText()
		val appBarsBody = readerScreenText.substringAfter("private fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val topChromeBody = readerScreenText.substringAfter("private fun KomikkuReaderTopBar(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderBottomBar(")
		val bottomChromeBody = readerScreenText.substringAfter("private fun KomikkuReaderBottomBar(")
			.substringBefore("\n}\n\n@OptIn(ExperimentalMaterial3Api::class)")

		assertContains(appBarsBody, "KomikkuReaderTopBar(")
		assertContains(topChromeBody, "bookmarked: Boolean")
		assertContains(topChromeBody, "canBookmark: Boolean")
		assertContains(topChromeBody, "onToggleBookmarked")
		assertContains(topChromeBody, "Icons.Outlined.Bookmark")
		assertContains(topChromeBody, "chapterTitle")
		assertFalse(
			bottomChromeBody.contains("chapterTitle") ||
				bottomChromeBody.contains("state.progressLabel"),
			"Bottom chrome must be an action strip; title/progress belongs in the top panel and side rail."
		)
	}

	@Test
	fun commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction() {
		val readerScreenText = readerScreenFile().readText()
		val bottomChromeBody = readerScreenText.substringAfter("private fun KomikkuReaderBottomBar(")
			.substringBefore("\n}\n\n@OptIn(ExperimentalMaterial3Api::class)")
		val bottomActionRow = bottomChromeBody
			.substringAfter("Row(")
			.substringBefore("}")

		assertContains(bottomActionRow, "horizontalArrangement = Arrangement.SpaceEvenly")
		assertContains(bottomActionRow, "verticalAlignment = Alignment.CenterVertically")
		assertFalse(
			bottomActionRow.contains("horizontalScroll("),
			"Komikku's bottom actions are centered/distributed, not a left-aligned horizontally scrolling toolbar."
		)
		assertFalse(
			bottomActionRow.contains("Icons.Filled.Star") || bottomActionRow.contains("Icons.Outlined.Star"),
			"The bookmark/star affordance belongs in the top chrome; duplicating it in the bottom action row makes the menu feel inconsistent."
		)
	}

	@Test
	fun commonReaderContentsDialogUsesKomikkuLazyChapterListContract() {
		val readerScreenText = readerScreenFile().readText()
		val contentsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderContentsDialog(")
			.substringBefore("\n}\n\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuReaderSettingsDialog(")
		val komikkuChapterListText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/ChapterListDialog.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/ChapterListDialog.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ChapterListDialog.kt reference")

		assertContains(komikkuChapterListText, "LazyColumn(")
		assertContains(komikkuChapterListText, "rememberLazyListState(")
		assertContains(komikkuChapterListText, "Modifier.heightIn(min = 200.dp, max = 500.dp)")
		assertContains(contentsDialogBody, "rememberLazyListState(")
		assertContains(contentsDialogBody, "LazyColumn(")
		assertContains(contentsDialogBody, "Modifier.heightIn(min = 200.dp, max = 500.dp)")
		assertContains(contentsDialogBody, "items(")
		assertContains(contentsDialogBody, "key = { item ->")
		assertFalse(
			contentsDialogBody.contains(".verticalScroll(rememberScrollState())"),
			"Komikku chapter/contents navigation must use a bounded lazy list instead of eagerly composing every entry in a scrolling Column."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")
		val settingsDialogHeaderBody = settingsDialogBody.substringBefore("HorizontalPager(")

		assertContains(settingsDialogBody, "KomikkuSettingsTabRow(")
		assertFalse(
			settingsDialogHeaderBody.contains("tabs.forEachIndexed"),
			"The Komikku settings dialog must not hand-roll title-sized text tabs; use the compact tab row helper."
		)
		assertContains(readerScreenText, "private fun KomikkuSettingsTabRow(")

		val tabRowBody = readerScreenText.substringAfter("private fun KomikkuSettingsTabRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(tabRowBody, "TabRow(")
		assertContains(tabRowBody, "Tab(")
		assertContains(tabRowBody, "MaterialTheme.typography.labelLarge")
		assertContains(tabRowBody, "maxLines = 1")
		assertContains(tabRowBody, "overflow = TextOverflow.Ellipsis")
		assertFalse(
			tabRowBody.contains("MaterialTheme.typography.titleMedium"),
			"Settings tabs must stay compact and single-line instead of inheriting panel title typography."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesKomikkuTabbedPagerContent() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(settingsDialogBody, "rememberPagerState(")
		assertContains(settingsDialogBody, "HorizontalPager(")
		assertContains(settingsDialogBody, "pagerState.animateScrollToPage(index)")
		assertContains(settingsDialogBody, "selectedTab = pagerState.currentPage")
		assertContains(settingsDialogBody, "when (tabs[page])")
		assertFalse(
			settingsDialogBody.contains("var selectedTab by remember"),
			"Komikku settings tabs should be backed by pager state, not local selectedTab state."
		)
		assertFalse(
			settingsDialogBody.contains("when (tabs[selectedTab])"),
			"Komikku settings content should be paged horizontally instead of swapped by a raw when block."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesReusableKomikkuTabbedDialogPrimitive() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = readerScreenText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(settingsDialogBody, "KomikkuTabbedDialog(")
		assertContains(settingsDialogBody, "modifier = Modifier.heightIn(max = maxHeight * 0.75f)")
		assertFalse(
			settingsDialogBody.contains("BasicAlertDialog(") ||
				settingsDialogBody.contains("Surface(") ||
				settingsDialogBody.contains("HorizontalPager(") ||
				settingsDialogBody.contains("KomikkuSettingsTabRow("),
			"ReaderSettingsDialog should mirror Komikku by delegating shell, tabs, and pager ownership to a reusable TabbedDialog primitive."
		)
		assertContains(tabbedDialogBody, "BasicAlertDialog(")
		assertContains(tabbedDialogBody, "Surface(")
		assertContains(tabbedDialogBody, "KomikkuSettingsTabRow(")
		assertContains(tabbedDialogBody, "HorizontalPager(")
		assertContains(tabbedDialogBody, "content(page)")
		assertContains(tabbedDialogBody, "footer()")
	}

	@Test
	fun commonReaderSettingsDialogHidesChromeOnCustomFilterLikeKomikku() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(settingsDialogBody, "onShowMenus: () -> Unit")
		assertContains(settingsDialogBody, "onHideMenus: () -> Unit")
		assertContains(settingsDialogBody, "LaunchedEffect(pagerState.currentPage)")
		assertContains(settingsDialogBody, "tabs[pagerState.currentPage] == KomikkuSettingsTab.CustomFilter")
		assertContains(settingsDialogBody, "onHideMenus()")
		assertContains(settingsDialogBody, "onShowMenus()")
		assertContains(readerScreenText, "onShowMenus = {")
		assertContains(readerScreenText, "coordinator.showMenus()")
		assertContains(readerScreenText, "onHideMenus = {")
		assertContains(readerScreenText, "coordinator.hideMenus()")
	}

	@Test
	fun commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = readerScreenText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(settingsDialogBody, "BoxWithConstraints")
		assertContains(settingsDialogBody, ".heightIn(max = maxHeight * 0.75f)")
		assertContains(settingsDialogBody, "TabbedDialogPaddingsVertical")
		assertContains(settingsDialogBody, ".verticalScroll(rememberScrollState())")
		assertContains(settingsDialogBody, ".padding(vertical = TabbedDialogPaddingsVertical)")
		assertFalse(
			settingsDialogBody.contains("modifier = Modifier.fillMaxWidth(0.78f)"),
			"Komikku settings should use the shared bounded dialog modifier instead of only fixed-width surface sizing."
		)
		assertContains(tabbedDialogBody, "HorizontalPager(")
		assertFalse(
			tabbedDialogBody.contains("modifier = Modifier.animateContentSize()"),
			"Pager content should be bounded and scrollable like Komikku's TabbedDialog body, not unbounded animated content."
		)
	}

	@Test
	fun commonReaderOptionsUseKomikkuStyleChipGroups() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogBody = readerScreenText.substringAfter("private fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(readerScreenText, "KomikkuSettingsChipRow")
		assertContains(readerScreenText, "KomikkuSettingsSwitchRow")
		assertContains(readerScreenText, "KomikkuSettingsStepperRow")
		assertContains(readerScreenText, "FilterChip(")
		assertContains(readerScreenText, "FlowRow(")
		assertContains(readerScreenText, "KomikkuReadingModeOptions")
		assertContains(settingsDialogBody, "ReaderSupportedDirections")
		assertContains(settingsDialogBody, "ReaderSupportedFontFamilies")
		assertContains(settingsDialogBody, "ReaderSupportedFontSources")
		assertContains(settingsDialogBody, "ReaderSupportedThemes")
		assertContains(settingsDialogBody, "ReaderSupportedOrientations")
		assertContains(settingsDialogBody, "KomikkuTapZoneOptions")
		assertFalse(
			settingsDialogBody.contains("ReaderCycleRow("),
			"Reader settings should use Komikku-style selectable chip groups instead of cyclic value rows."
		)
	}

	@Test
	fun commonReaderOptionsSeparatePdfImageSettingsByPublicationFormat() {
		val readerScreenText = readerScreenFile().readText()
		val readerChromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(readerChromeStateText, "ReaderOptionsTab.PdfImage")
		assertContains(readerChromeStateText, "publicationFormat: ReaderPublicationFormat")
		assertContains(readerScreenText, "publicationFormat = reader.publicationFormat")
		assertContains(readerScreenText, "publicationFormat: ReaderPublicationFormat")
		assertContains(readerScreenText, "PDF/Image")
		assertContains(readerScreenText, "Page fit")
		assertContains(readerScreenText, "ReaderSupportedPdfFitModes")
		assertContains(readerScreenText, "Crop borders")
		assertContains(readerScreenText, "Page gap")
		assertContains(readerScreenText, "pdfFitMode = fitMode")
		assertContains(readerScreenText, "pdfCropBorders = cropBorders")
		assertContains(readerScreenText, "pdfPageGapPercent")
	}

	@Test
	fun commonReaderOptionsSupportKomikkuStylePerBookSettingsScope() {
		val readerScreenText = readerScreenFile().readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val preferenceManagerText = listOf(
			File("src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate PreferenceManager.kt")

		assertContains(preferenceManagerText, "readerBookSettingsJson")
		assertContains(preferenceText, "readerSettingsForBook")
		assertContains(preferenceText, "setReaderBookSettings")
		assertContains(preferenceText, "clearReaderBookSettings")
		assertContains(readerScreenText, "readerSettingsForBook(reader.bookId)")
		assertContains(readerScreenText, "readerBookSettingsJson")
		assertContains(readerScreenText, "setReaderBookSettings(reader.bookId")
		assertContains(readerScreenText, "clearReaderBookSettings(reader.bookId)")
		assertContains(readerScreenText, "For this book")
		assertContains(readerScreenText, "Global")
		assertContains(readerScreenText, "Reset book")
	}

	@Test
	fun commonReadaloudRuntimeCapabilitiesRemainAvailableBehindControllerBoundary() {
		val readerScreenText = readerScreenFile().readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(chromeStateText, "activeAudioLabel")
		assertContains(chromeStateText, "activeAudioMetadata")
		assertContains(chromeStateText, "adjustSpeedCommand")
		assertContains(chromeStateText, "toggleSyncCommand")
		assertContains(chromeStateText, "ReaderReadaloudPlaybackCommand.SetSpeed")
		assertContains(chromeStateText, "ReaderReadaloudPlaybackCommand.SetSyncEnabled")
		assertContains(runtimeHostText, "activeAudioLabel =")
		assertContains(runtimeHostText, "activeLabelForPlaybackPosition")
		assertContains(runtimeHostText, "activeAudioMetadata =")
		assertContains(runtimeHostText, "metadataLabelsForPlaybackPosition")
		assertContains(runtimeHostText, "controller.setPlaybackSpeed")
		assertContains(runtimeHostText, "setSyncEnabled")
		assertFalse(
			readerScreenText.contains("ReaderReadaloudRuntimeHost("),
			"The active Komikku reader must not reattach the legacy readaloud host until it is mounted through the controller adapter."
		)
	}

	@Test
	fun androidReadaloudMediaItemsPreserveSourceReleaseMetadataInMedia3Extras() {
		val mediaItemsText = readerAndroidPackageFile("ReadaloudMediaItems.android.kt").readText()

		assertContains(mediaItemsText, "descriptor.toReadaloudMediaExtras()")
		assertContains(mediaItemsText, "putString(\"chapterLabel\", extras.chapterLabel)")
		assertContains(mediaItemsText, "putString(\"sectionLabel\", extras.sectionLabel)")
		assertContains(mediaItemsText, "putString(\"sourceProvider\", extras.sourceProvider)")
		assertContains(mediaItemsText, "putString(\"sourceRelease\", extras.sourceRelease)")
		assertContains(mediaItemsText, "putString(\"sourceUrl\", extras.sourceUrl)")
	}
}
