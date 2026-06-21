package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_delete
import navic.composeapp.generated.resources.action_download
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_resume
import navic.composeapp.generated.resources.info_ebook_reader_clear_imported_font_success
import navic.composeapp.generated.resources.info_ebook_reader_import_font_success
import navic.composeapp.generated.resources.info_downloaded
import navic.composeapp.generated.resources.info_download_status_failed
import navic.composeapp.generated.resources.info_error
import navic.composeapp.generated.resources.info_status_downloading
import navic.composeapp.generated.resources.option_ebook_reader_clear_imported_font
import navic.composeapp.generated.resources.option_ebook_reader_column_mode
import navic.composeapp.generated.resources.option_ebook_reader_column_mode_auto
import navic.composeapp.generated.resources.option_ebook_reader_column_mode_double
import navic.composeapp.generated.resources.option_ebook_reader_column_mode_single
import navic.composeapp.generated.resources.option_ebook_reader_column_threshold
import navic.composeapp.generated.resources.option_ebook_reader_font_family
import navic.composeapp.generated.resources.option_ebook_reader_font_family_book
import navic.composeapp.generated.resources.option_ebook_reader_font_family_dyslexic
import navic.composeapp.generated.resources.option_ebook_reader_font_family_humanist
import navic.composeapp.generated.resources.option_ebook_reader_font_family_mono
import navic.composeapp.generated.resources.option_ebook_reader_font_family_publisher
import navic.composeapp.generated.resources.option_ebook_reader_font_family_sans
import navic.composeapp.generated.resources.option_ebook_reader_font_family_serif
import navic.composeapp.generated.resources.option_ebook_reader_font_family_typewriter
import navic.composeapp.generated.resources.option_ebook_reader_font_source
import navic.composeapp.generated.resources.option_ebook_reader_font_source_custom
import navic.composeapp.generated.resources.option_ebook_reader_font_source_navic
import navic.composeapp.generated.resources.option_ebook_reader_font_source_publisher
import navic.composeapp.generated.resources.option_ebook_reader_font_source_system
import navic.composeapp.generated.resources.option_ebook_reader_import_font
import navic.composeapp.generated.resources.option_ebook_reader_imported_font_storage
import navic.composeapp.generated.resources.option_ebook_reader_font_size
import navic.composeapp.generated.resources.option_ebook_reader_dim_overlay
import navic.composeapp.generated.resources.option_ebook_reader_direction
import navic.composeapp.generated.resources.option_ebook_reader_direction_default
import navic.composeapp.generated.resources.option_ebook_reader_direction_ltr
import navic.composeapp.generated.resources.option_ebook_reader_direction_rtl
import navic.composeapp.generated.resources.option_ebook_reader_flow
import navic.composeapp.generated.resources.option_ebook_reader_fullscreen
import navic.composeapp.generated.resources.option_ebook_reader_grayscale
import navic.composeapp.generated.resources.option_ebook_reader_inverted_colors
import navic.composeapp.generated.resources.option_ebook_reader_line_height
import navic.composeapp.generated.resources.option_ebook_reader_keep_screen_on
import navic.composeapp.generated.resources.option_ebook_reader_margin
import navic.composeapp.generated.resources.option_ebook_reader_media_overlay
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type_bottom
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type_left
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type_right
import navic.composeapp.generated.resources.option_ebook_reader_orientation
import navic.composeapp.generated.resources.option_ebook_reader_orientation_default
import navic.composeapp.generated.resources.option_ebook_reader_orientation_free
import navic.composeapp.generated.resources.option_ebook_reader_orientation_landscape
import navic.composeapp.generated.resources.option_ebook_reader_orientation_locked_landscape
import navic.composeapp.generated.resources.option_ebook_reader_orientation_locked_portrait
import navic.composeapp.generated.resources.option_ebook_reader_orientation_portrait
import navic.composeapp.generated.resources.option_ebook_reader_orientation_reverse_portrait
import navic.composeapp.generated.resources.option_ebook_reader_paged
import navic.composeapp.generated.resources.option_ebook_reader_paged_vertical
import navic.composeapp.generated.resources.option_ebook_reader_paragraph_spacing
import navic.composeapp.generated.resources.option_ebook_reader_pdf_crop_borders
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_height
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_original
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_page
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_width
import navic.composeapp.generated.resources.option_ebook_reader_pdf_page_gap
import navic.composeapp.generated.resources.option_ebook_reader_publisher_styles
import navic.composeapp.generated.resources.option_ebook_reader_remote_font_catalog
import navic.composeapp.generated.resources.option_ebook_reader_remote_font_delete
import navic.composeapp.generated.resources.option_ebook_reader_scroll
import navic.composeapp.generated.resources.option_ebook_reader_scroll_gaps
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_default
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_disabled
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_edge
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_both
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_horizontal
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_none
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_vertical
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_kindle
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_l_shaped
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_right_left
import navic.composeapp.generated.resources.option_ebook_reader_smaller_tap_zones
import navic.composeapp.generated.resources.option_ebook_reader_theme
import navic.composeapp.generated.resources.option_ebook_reader_theme_black
import navic.composeapp.generated.resources.option_ebook_reader_theme_dark
import navic.composeapp.generated.resources.option_ebook_reader_theme_dusk
import navic.composeapp.generated.resources.option_ebook_reader_theme_light
import navic.composeapp.generated.resources.option_ebook_reader_theme_sepia
import navic.composeapp.generated.resources.option_ebook_reader_volume_keys
import navic.composeapp.generated.resources.option_off
import navic.composeapp.generated.resources.subtitle_ebook_reader_clear_imported_font
import navic.composeapp.generated.resources.subtitle_ebook_reader_column_mode
import navic.composeapp.generated.resources.subtitle_ebook_reader_column_threshold
import navic.composeapp.generated.resources.subtitle_ebook_reader_font_family
import navic.composeapp.generated.resources.subtitle_ebook_reader_font_source
import navic.composeapp.generated.resources.subtitle_ebook_reader_import_font
import navic.composeapp.generated.resources.subtitle_ebook_reader_imported_font_storage
import navic.composeapp.generated.resources.subtitle_ebook_reader_font_size
import navic.composeapp.generated.resources.subtitle_ebook_reader_dim_overlay
import navic.composeapp.generated.resources.subtitle_ebook_reader_direction
import navic.composeapp.generated.resources.subtitle_ebook_reader_fullscreen
import navic.composeapp.generated.resources.subtitle_ebook_reader_grayscale
import navic.composeapp.generated.resources.subtitle_ebook_reader_inverted_colors
import navic.composeapp.generated.resources.subtitle_ebook_reader_line_height
import navic.composeapp.generated.resources.subtitle_ebook_reader_keep_screen_on
import navic.composeapp.generated.resources.subtitle_ebook_reader_margin
import navic.composeapp.generated.resources.subtitle_ebook_reader_media_overlay
import navic.composeapp.generated.resources.subtitle_ebook_reader_nav_bar_type
import navic.composeapp.generated.resources.subtitle_ebook_reader_orientation
import navic.composeapp.generated.resources.subtitle_ebook_reader_paged
import navic.composeapp.generated.resources.subtitle_ebook_reader_paragraph_spacing
import navic.composeapp.generated.resources.subtitle_ebook_reader_pdf_crop_borders
import navic.composeapp.generated.resources.subtitle_ebook_reader_pdf_fit
import navic.composeapp.generated.resources.subtitle_ebook_reader_pdf_page_gap
import navic.composeapp.generated.resources.subtitle_ebook_reader_publisher_styles
import navic.composeapp.generated.resources.subtitle_ebook_reader_remote_font_catalog
import navic.composeapp.generated.resources.subtitle_ebook_reader_remote_font_delete
import navic.composeapp.generated.resources.subtitle_ebook_reader_smaller_tap_zones
import navic.composeapp.generated.resources.subtitle_ebook_reader_tap_zone
import navic.composeapp.generated.resources.subtitle_ebook_reader_tap_zone_invert
import navic.composeapp.generated.resources.subtitle_ebook_reader_theme
import navic.composeapp.generated.resources.subtitle_ebook_reader_volume_keys
import navic.composeapp.generated.resources.title_ebook_reader
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.domain.manager.PreferenceManager
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderBookFontFamily
import paige.navic.reader.ReaderBlackTheme
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderDuskTheme
import paige.navic.reader.ReaderDyslexicFontFamily
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceCustom
import paige.navic.reader.ReaderFontSourceNavic
import paige.navic.reader.ReaderFontSourcePublisher
import paige.navic.reader.ReaderFontSourceSystem
import paige.navic.reader.ReaderHumanistFontFamily
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderMonoFontFamily
import paige.navic.reader.ReaderNavBarTypeBottom
import paige.navic.reader.ReaderNavBarTypeVerticalLeft
import paige.navic.reader.ReaderNavBarTypeVerticalRight
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderOrientationFree
import paige.navic.reader.ReaderOrientationLandscape
import paige.navic.reader.ReaderOrientationLockedLandscape
import paige.navic.reader.ReaderOrientationLockedPortrait
import paige.navic.reader.ReaderOrientationPortrait
import paige.navic.reader.ReaderOrientationReversePortrait
import paige.navic.reader.ReaderPdfFitHeight
import paige.navic.reader.ReaderPdfFitOriginal
import paige.navic.reader.ReaderPdfFitPage
import paige.navic.reader.ReaderPdfFitWidth
import paige.navic.reader.ReaderPublisherFontFamily
import paige.navic.reader.ReaderRemoteFontDownloadState
import paige.navic.reader.ReaderRemoteFontDownloadStatusDownloading
import paige.navic.reader.ReaderRemoteFontDownloadStatusFailed
import paige.navic.reader.ReaderRemoteFontDownloadStatusPaused
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderSepiaTheme
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneDisabled
import paige.navic.reader.ReaderTapZoneEdge
import paige.navic.reader.ReaderTapZoneInvertBoth
import paige.navic.reader.ReaderTapZoneInvertHorizontal
import paige.navic.reader.ReaderTapZoneInvertNone
import paige.navic.reader.ReaderTapZoneInvertVertical
import paige.navic.reader.ReaderTapZoneKindle
import paige.navic.reader.ReaderTapZoneLShaped
import paige.navic.reader.ReaderTapZoneRightLeft
import paige.navic.reader.ReaderTypewriterFontFamily
import paige.navic.reader.readerDefaultSettings
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsEbooksScreen() {
	val platformContext = LocalPlatformContext.current
	val snackbarState = LocalSnackbarState.current
	val coroutineScope = rememberCoroutineScope()
	val preferenceManager = koinInject<PreferenceManager>()
	val settings = preferenceManager.readerDefaultSettings()
	val fontFamily = ReaderFontFamilyOption.forFontFamily(settings.fontFamily)
	val fontSource = ReaderFontSourceOption.forFontSource(settings.fontSource)
	val theme = ReaderThemeOption.forTheme(settings.theme)
	val direction = ReaderDirectionOption.forDirection(settings.direction)
	val navBarType = ReaderNavBarTypeOption.forNavBarType(settings.navBarType)
	val flow = ReaderFlowOption.forFlowMode(settings.flowMode, settings.paged)
	val columnMode = ReaderColumnModeOption.forMaxColumnCount(settings.maxColumnCount)
	val tapZone = ReaderTapZoneOption.forTapZone(settings.tapZone)
	val tapZoneInvertMode = ReaderTapZoneInvertOption.forTapZoneInvertMode(settings.tapZoneInvertMode)
	val orientation = ReaderOrientationOption.forOrientation(settings.orientation)
	val pdfFit = ReaderPdfFitOption.forPdfFitMode(settings.pdfFitMode)
	val lineHeightPercent = (((settings.lineHeight ?: 1.55) * 100.0).roundToInt())
	val importFontSuccessMessage = stringResource(Res.string.info_ebook_reader_import_font_success)
	val clearImportedFontSuccessMessage = stringResource(Res.string.info_ebook_reader_clear_imported_font_success)
	val importFontErrorFallback = stringResource(Res.string.info_error)
	val importedFontValue = settings.customFontFamily?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.option_off)
	val fontImporter = rememberReaderFontImporter(
		onImported = { imported ->
			preferenceManager.readerFontSource = ReaderFontSourceCustom
			preferenceManager.readerCustomFontFamily = imported.family
			preferenceManager.readerCustomFontUrl = imported.url
			coroutineScope.launch {
				snackbarState.showSnackbar(importFontSuccessMessage)
			}
		},
		onError = { message ->
			coroutineScope.launch {
				snackbarState.showSnackbar(message.takeIf { it.isNotBlank() } ?: importFontErrorFallback)
			}
		}
	)
	val importedFontStorageValue = if (fontImporter.cachedFontBytes > 0L) {
		storageSizeText(fontImporter.cachedFontBytes)
	} else {
		stringResource(Res.string.option_off)
	}
	val remoteFontCatalogValue = when {
		fontImporter.remoteFontLoading -> stringResource(Res.string.info_status_downloading)
		fontImporter.remoteFonts.isNotEmpty() -> fontImporter.remoteFonts.size.toString()
		fontImporter.remoteFontError != null -> stringResource(Res.string.info_error)
		else -> stringResource(Res.string.action_refresh)
	}
	val hasImportedFont = !settings.customFontFamily.isNullOrBlank() ||
		!settings.customFontUrl.isNullOrBlank() ||
		fontImporter.cachedFontBytes > 0L

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_ebook_reader)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp, bottom = 32.dp)
			) {
				FormTitle(stringResource(Res.string.title_ebook_reader))
				Form(Modifier.fillMaxWidth()) {
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_font_family)) },
						items = ReaderFontFamilyOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_font_family),
						selection = fontFamily,
						onSelect = { option -> preferenceManager.readerFontFamily = option.fontFamily }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_font_source)) },
						items = ReaderFontSourceOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_font_source),
						selection = fontSource,
						onSelect = { option -> preferenceManager.readerFontSource = option.fontSource }
					)
					if (fontImporter.supported) {
						SettingValueRow(
							title = { Text(stringResource(Res.string.option_ebook_reader_import_font)) },
							value = importedFontValue,
							subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_import_font)) },
							onClick = { fontImporter.launch() }
						)
						SettingValueRow(
							title = { Text(stringResource(Res.string.option_ebook_reader_imported_font_storage)) },
							value = importedFontStorageValue,
							subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_imported_font_storage)) }
						)
						if (hasImportedFont) {
							SettingValueRow(
								title = { Text(stringResource(Res.string.option_ebook_reader_clear_imported_font)) },
								value = "",
								subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_clear_imported_font)) },
								onClick = {
									fontImporter.clearImportedFonts()
									preferenceManager.readerFontSource = ReaderFontSourceNavic
									preferenceManager.readerCustomFontFamily = ""
									preferenceManager.readerCustomFontUrl = ""
									coroutineScope.launch {
										snackbarState.showSnackbar(clearImportedFontSuccessMessage)
									}
								}
							)
						}
						SettingValueRow(
							title = { Text(stringResource(Res.string.option_ebook_reader_remote_font_catalog)) },
							value = remoteFontCatalogValue,
							subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_remote_font_catalog)) },
							onClick = { fontImporter.refreshRemoteFonts() }
						)
						val cachedRemoteFontIds = fontImporter.cachedRemoteFonts.map { cachedRemoteFont -> cachedRemoteFont.id }.toSet()
						fontImporter.remoteFonts.forEach { remoteFont ->
							if (remoteFont.id !in cachedRemoteFontIds) {
								val remoteFontDownload = fontImporter.remoteFontDownloads[remoteFont.id]
								val remoteFontDownloadValue = readerRemoteFontDownloadValue(remoteFontDownload)
								SettingValueRow(
									title = { Text(remoteFont.name) },
									value = remoteFontDownloadValue,
									subtitle = { Text(remoteFont.description.ifBlank { remoteFont.license.name }) },
									onClick = {
										when (remoteFontDownload?.status) {
											ReaderRemoteFontDownloadStatusDownloading ->
												fontImporter.pauseRemoteFontDownload(remoteFont.id)
											ReaderRemoteFontDownloadStatusPaused ->
												fontImporter.resumeRemoteFontDownload(remoteFont)
											ReaderRemoteFontDownloadStatusFailed ->
												fontImporter.downloadRemoteFont(remoteFont)
											else ->
												fontImporter.downloadRemoteFont(remoteFont)
										}
									}
								)
								if (remoteFontDownload?.status == ReaderRemoteFontDownloadStatusDownloading ||
									remoteFontDownload?.status == ReaderRemoteFontDownloadStatusPaused
								) {
									SettingValueRow(
										title = { Text("${stringResource(Res.string.action_cancel)}: ${remoteFont.name}") },
										value = stringResource(Res.string.action_cancel),
										subtitle = { Text(remoteFontDownloadValue) },
										onClick = { fontImporter.cancelRemoteFontDownload(remoteFont.id) }
									)
								}
							}
						}
						fontImporter.cachedRemoteFonts.forEach { cachedRemoteFont ->
							SettingValueRow(
								title = { Text(cachedRemoteFont.name) },
								value = stringResource(Res.string.info_downloaded),
								subtitle = { Text(storageSizeText(cachedRemoteFont.byteSize)) },
								onClick = {
									preferenceManager.readerFontSource = ReaderFontSourceCustom
									preferenceManager.readerCustomFontFamily = cachedRemoteFont.family
									preferenceManager.readerCustomFontUrl = cachedRemoteFont.fonts.firstOrNull()?.url.orEmpty()
								}
							)
							SettingValueRow(
								title = { Text("${stringResource(Res.string.option_ebook_reader_remote_font_delete)}: ${cachedRemoteFont.name}") },
								value = stringResource(Res.string.action_delete),
								subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_remote_font_delete)) },
								onClick = {
									fontImporter.deleteRemoteFont(cachedRemoteFont.id)
									if (cachedRemoteFont.fonts.any { font -> font.url == settings.customFontUrl }) {
										preferenceManager.readerFontSource = ReaderFontSourceNavic
										preferenceManager.readerCustomFontFamily = ""
										preferenceManager.readerCustomFontUrl = ""
									}
								}
							)
						}
					}
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_font_size)) },
						items = readerFontSizeOptions.toImmutableList(),
						label = { percent -> "$percent%" },
						description = stringResource(Res.string.subtitle_ebook_reader_font_size),
						selection = settings.fontSizePercent ?: 100,
						onSelect = { percent -> preferenceManager.readerFontSizePercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_line_height)) },
						items = readerLineHeightOptions.toImmutableList(),
						label = { percent -> readerLineHeightLabel(percent) },
						description = stringResource(Res.string.subtitle_ebook_reader_line_height),
						selection = lineHeightPercent,
						onSelect = { percent -> preferenceManager.readerLineHeightPercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_paragraph_spacing)) },
						items = readerParagraphSpacingOptions.toImmutableList(),
						label = { percent -> "$percent%" },
						description = stringResource(Res.string.subtitle_ebook_reader_paragraph_spacing),
						selection = settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
						onSelect = { percent -> preferenceManager.readerParagraphSpacingPercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_margin)) },
						items = readerMarginOptions.toImmutableList(),
						label = { percent -> "$percent%" },
						description = stringResource(Res.string.subtitle_ebook_reader_margin),
						selection = settings.marginPercent ?: 0,
						onSelect = { percent -> preferenceManager.readerMarginPercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_dim_overlay)) },
						items = readerDimOverlayOptions.toImmutableList(),
						label = { percent -> readerDimOverlayLabel(percent) },
						description = stringResource(Res.string.subtitle_ebook_reader_dim_overlay),
						selection = settings.dimOverlayPercent ?: 0,
						onSelect = { percent -> preferenceManager.readerDimOverlayPercent = percent }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_grayscale)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_grayscale)) },
						value = preferenceManager.readerGrayscaleEnabled,
						onSetValue = { enabled -> preferenceManager.readerGrayscaleEnabled = enabled }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_inverted_colors)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_inverted_colors)) },
						value = preferenceManager.readerInvertedColors,
						onSetValue = { enabled -> preferenceManager.readerInvertedColors = enabled }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_theme)) },
						items = ReaderThemeOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_theme),
						selection = theme,
						onSelect = { option -> preferenceManager.readerTheme = option.theme }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_orientation)) },
						items = ReaderOrientationOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_orientation),
						selection = orientation,
						onSelect = { option -> preferenceManager.readerOrientation = option.orientation }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_fullscreen)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_fullscreen)) },
						value = preferenceManager.readerFullscreen,
						onSetValue = { enabled -> preferenceManager.readerFullscreen = enabled }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_direction)) },
						items = ReaderDirectionOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_direction),
						selection = direction,
						onSelect = { option -> preferenceManager.readerDirection = option.direction }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_nav_bar_type)) },
						items = ReaderNavBarTypeOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_nav_bar_type),
						selection = navBarType,
						onSelect = { option -> preferenceManager.readerNavBarType = option.navBarType }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_flow)) },
						items = ReaderFlowOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_paged),
						selection = flow,
						onSelect = { option ->
							preferenceManager.readerFlowMode = option.flowMode
							preferenceManager.readerPaged = option.paged
						}
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_column_mode)) },
						items = ReaderColumnModeOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_column_mode),
						selection = columnMode,
						onSelect = { option -> preferenceManager.readerMaxColumnCount = option.maxColumnCount }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_column_threshold)) },
						items = readerColumnThresholdOptions.toImmutableList(),
						label = { threshold -> "${threshold}px" },
						description = stringResource(Res.string.subtitle_ebook_reader_column_threshold),
						selection = (settings.columnThreshold ?: 720.0).roundToInt(),
						onSelect = { threshold -> preferenceManager.readerColumnThreshold = threshold.toFloat() }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_pdf_fit)) },
						items = ReaderPdfFitOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_pdf_fit),
						selection = pdfFit,
						onSelect = { option -> preferenceManager.readerPdfFitMode = option.pdfFitMode }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_pdf_crop_borders)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_pdf_crop_borders)) },
						value = preferenceManager.readerPdfCropBorders,
						onSetValue = { enabled -> preferenceManager.readerPdfCropBorders = enabled }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_pdf_page_gap)) },
						items = readerPdfPageGapOptions.toImmutableList(),
						label = { percent -> "$percent%" },
						description = stringResource(Res.string.subtitle_ebook_reader_pdf_page_gap),
						selection = settings.pdfPageGapPercent ?: 0,
						onSelect = { percent -> preferenceManager.readerPdfPageGapPercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_tap_zone)) },
						items = ReaderTapZoneOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_tap_zone),
						selection = tapZone,
						onSelect = { option -> preferenceManager.readerTapZone = option.tapZone }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_tap_zone_invert)) },
						items = ReaderTapZoneInvertOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_tap_zone_invert),
						selection = tapZoneInvertMode,
						onSelect = { option -> preferenceManager.readerTapZoneInvertMode = option.tapZoneInvertMode }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_smaller_tap_zones)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_smaller_tap_zones)) },
						value = preferenceManager.readerSmallerTapZone,
						onSetValue = { enabled -> preferenceManager.readerSmallerTapZone = enabled }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_publisher_styles)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_publisher_styles)) },
						value = preferenceManager.readerPublisherStylesEnabled,
						onSetValue = { enabled -> preferenceManager.readerPublisherStylesEnabled = enabled }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_keep_screen_on)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_keep_screen_on)) },
						value = preferenceManager.readerKeepScreenOn,
						onSetValue = { enabled -> preferenceManager.readerKeepScreenOn = enabled }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_volume_keys)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_volume_keys)) },
						value = preferenceManager.readerVolumeKeyPageTurns,
						onSetValue = { enabled -> preferenceManager.readerVolumeKeyPageTurns = enabled }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_media_overlay)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_media_overlay)) },
						value = preferenceManager.readerMediaOverlayEnabled,
						onSetValue = { enabled -> preferenceManager.readerMediaOverlayEnabled = enabled }
					)
				}
			}
		}
	}
}

private val readerFontSizeOptions = listOf(90, 100, 112, 125, 140, 160, 180)
private val readerLineHeightOptions = listOf(120, 135, 155, 170, 190, 220)
private val readerParagraphSpacingOptions = listOf(0, 25, 50, 75, 100, 150, 200)
private val readerMarginOptions = listOf(0, 4, 8, 12, 16, 24)
private val readerColumnThresholdOptions = listOf(400, 520, 640, 720, 840, 960, 1080, 1200)
private val readerDimOverlayOptions = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80)
private val readerPdfPageGapOptions = listOf(0, 4, 8, 12, 16, 24, 32, 48)

@Composable
private fun readerLineHeightLabel(percent: Int): String =
	"${percent / 100}.${(percent % 100).toString().padStart(2, '0')}".trimEnd('0').trimEnd('.')

@Composable
private fun readerDimOverlayLabel(percent: Int): String =
	if (percent <= 0) stringResource(Res.string.option_off) else "$percent%"

@Composable
private fun readerRemoteFontDownloadValue(download: ReaderRemoteFontDownloadState?): String =
	when (download?.status) {
		ReaderRemoteFontDownloadStatusDownloading ->
			"${stringResource(Res.string.info_status_downloading)} ${remoteFontProgress(download)}%"
		ReaderRemoteFontDownloadStatusPaused ->
			"${stringResource(Res.string.action_resume)} ${remoteFontProgress(download)}%"
		ReaderRemoteFontDownloadStatusFailed ->
			stringResource(Res.string.info_download_status_failed)
		else ->
			stringResource(Res.string.action_download)
	}

private fun remoteFontProgress(download: ReaderRemoteFontDownloadState?): Int =
	(((download?.progress ?: 0.0) * 100.0).roundToInt()).coerceIn(0, 100)
