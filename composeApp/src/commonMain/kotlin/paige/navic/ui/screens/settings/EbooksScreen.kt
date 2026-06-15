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
import navic.composeapp.generated.resources.info_ebook_reader_clear_imported_font_success
import navic.composeapp.generated.resources.info_ebook_reader_import_font_success
import navic.composeapp.generated.resources.info_error
import navic.composeapp.generated.resources.option_ebook_reader_clear_imported_font
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
import navic.composeapp.generated.resources.option_ebook_reader_scroll
import navic.composeapp.generated.resources.option_ebook_reader_scroll_gaps
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_default
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_disabled
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_edge
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_kindle
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_l_shaped
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_right_left
import navic.composeapp.generated.resources.option_ebook_reader_smaller_tap_zones
import navic.composeapp.generated.resources.option_ebook_reader_show_tap_zones
import navic.composeapp.generated.resources.option_ebook_reader_theme
import navic.composeapp.generated.resources.option_ebook_reader_theme_black
import navic.composeapp.generated.resources.option_ebook_reader_theme_dark
import navic.composeapp.generated.resources.option_ebook_reader_theme_dusk
import navic.composeapp.generated.resources.option_ebook_reader_theme_light
import navic.composeapp.generated.resources.option_ebook_reader_theme_sepia
import navic.composeapp.generated.resources.option_ebook_reader_volume_keys
import navic.composeapp.generated.resources.option_ebook_reader_web_debugging
import navic.composeapp.generated.resources.option_off
import navic.composeapp.generated.resources.subtitle_ebook_reader_clear_imported_font
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
import navic.composeapp.generated.resources.subtitle_ebook_reader_smaller_tap_zones
import navic.composeapp.generated.resources.subtitle_ebook_reader_show_tap_zones
import navic.composeapp.generated.resources.subtitle_ebook_reader_tap_zone
import navic.composeapp.generated.resources.subtitle_ebook_reader_theme
import navic.composeapp.generated.resources.subtitle_ebook_reader_volume_keys
import navic.composeapp.generated.resources.subtitle_ebook_reader_web_debugging
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
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderSepiaTheme
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneDisabled
import paige.navic.reader.ReaderTapZoneEdge
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
import paige.navic.util.core.PlatformType
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
	val tapZone = ReaderTapZoneOption.forTapZone(settings.tapZone)
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
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_smaller_tap_zones)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_smaller_tap_zones)) },
						value = preferenceManager.readerSmallerTapZone,
						onSetValue = { enabled -> preferenceManager.readerSmallerTapZone = enabled }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_show_tap_zones)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_show_tap_zones)) },
						value = preferenceManager.readerShowTapZones,
						onSetValue = { enabled -> preferenceManager.readerShowTapZones = enabled }
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
					if (platformContext.platformType == PlatformType.Android) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_ebook_reader_web_debugging)) },
							subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_web_debugging)) },
							value = preferenceManager.readerWebContentsDebuggingEnabled,
							onSetValue = { enabled -> preferenceManager.readerWebContentsDebuggingEnabled = enabled }
						)
					}
				}
			}
		}
	}
}

private enum class ReaderFontFamilyOption(
	val fontFamily: String,
	val title: StringResource
) {
	Sans(ReaderSansFontFamily, Res.string.option_ebook_reader_font_family_sans),
	Serif(ReaderSerifFontFamily, Res.string.option_ebook_reader_font_family_serif),
	Book(ReaderBookFontFamily, Res.string.option_ebook_reader_font_family_book),
	Humanist(ReaderHumanistFontFamily, Res.string.option_ebook_reader_font_family_humanist),
	Dyslexic(ReaderDyslexicFontFamily, Res.string.option_ebook_reader_font_family_dyslexic),
	Typewriter(ReaderTypewriterFontFamily, Res.string.option_ebook_reader_font_family_typewriter),
	Mono(ReaderMonoFontFamily, Res.string.option_ebook_reader_font_family_mono),
	Publisher(ReaderPublisherFontFamily, Res.string.option_ebook_reader_font_family_publisher);

	companion object {
		fun forFontFamily(fontFamily: String?): ReaderFontFamilyOption =
			entries.firstOrNull { option -> option.fontFamily == fontFamily } ?: Sans
	}
}

private enum class ReaderFontSourceOption(
	val fontSource: String,
	val title: StringResource
) {
	Navic(ReaderFontSourceNavic, Res.string.option_ebook_reader_font_source_navic),
	System(ReaderFontSourceSystem, Res.string.option_ebook_reader_font_source_system),
	Publisher(ReaderFontSourcePublisher, Res.string.option_ebook_reader_font_source_publisher),
	Custom(ReaderFontSourceCustom, Res.string.option_ebook_reader_font_source_custom);

	companion object {
		fun forFontSource(fontSource: String?): ReaderFontSourceOption =
			entries.firstOrNull { option -> option.fontSource == fontSource } ?: Navic
	}
}

private enum class ReaderThemeOption(
	val theme: String,
	val title: StringResource
) {
	Light(ReaderLightTheme, Res.string.option_ebook_reader_theme_light),
	Sepia(ReaderSepiaTheme, Res.string.option_ebook_reader_theme_sepia),
	Dusk(ReaderDuskTheme, Res.string.option_ebook_reader_theme_dusk),
	Dark(ReaderDarkTheme, Res.string.option_ebook_reader_theme_dark),
	Black(ReaderBlackTheme, Res.string.option_ebook_reader_theme_black);

	companion object {
		fun forTheme(theme: String?): ReaderThemeOption =
			entries.firstOrNull { option -> option.theme == theme } ?: Light
	}
}

private enum class ReaderFlowOption(
	val flowMode: String,
	val paged: Boolean,
	val title: StringResource
) {
	Paged(ReaderFlowPaged, true, Res.string.option_ebook_reader_paged),
	PagedVertical(ReaderFlowPagedVertical, true, Res.string.option_ebook_reader_paged_vertical),
	Scroll(ReaderFlowScrolled, false, Res.string.option_ebook_reader_scroll),
	ScrollGaps(ReaderFlowScrolledGaps, false, Res.string.option_ebook_reader_scroll_gaps);

	companion object {
		fun forFlowMode(flowMode: String?, paged: Boolean?): ReaderFlowOption =
			entries.firstOrNull { option -> option.flowMode == flowMode }
				?: if (paged == false) Scroll else Paged
	}
}

private enum class ReaderPdfFitOption(
	val pdfFitMode: String,
	val title: StringResource
) {
	Width(ReaderPdfFitWidth, Res.string.option_ebook_reader_pdf_fit_width),
	Page(ReaderPdfFitPage, Res.string.option_ebook_reader_pdf_fit_page),
	Height(ReaderPdfFitHeight, Res.string.option_ebook_reader_pdf_fit_height),
	Original(ReaderPdfFitOriginal, Res.string.option_ebook_reader_pdf_fit_original);

	companion object {
		fun forPdfFitMode(pdfFitMode: String?): ReaderPdfFitOption =
			entries.firstOrNull { option -> option.pdfFitMode == pdfFitMode } ?: Width
	}
}

private enum class ReaderDirectionOption(
	val direction: String,
	val title: StringResource
) {
	Default(ReaderDirectionDefault, Res.string.option_ebook_reader_direction_default),
	LeftToRight(ReaderDirectionLtr, Res.string.option_ebook_reader_direction_ltr),
	RightToLeft(ReaderDirectionRtl, Res.string.option_ebook_reader_direction_rtl);

	companion object {
		fun forDirection(direction: String?): ReaderDirectionOption =
			entries.firstOrNull { option -> option.direction == direction } ?: Default
	}
}

private enum class ReaderNavBarTypeOption(
	val navBarType: String,
	val title: StringResource
) {
	Right(ReaderNavBarTypeVerticalRight, Res.string.option_ebook_reader_nav_bar_type_right),
	Left(ReaderNavBarTypeVerticalLeft, Res.string.option_ebook_reader_nav_bar_type_left),
	Bottom(ReaderNavBarTypeBottom, Res.string.option_ebook_reader_nav_bar_type_bottom);

	companion object {
		fun forNavBarType(navBarType: String?): ReaderNavBarTypeOption =
			entries.firstOrNull { option -> option.navBarType == navBarType } ?: Right
	}
}

private enum class ReaderOrientationOption(
	val orientation: String,
	val title: StringResource
) {
	Default(ReaderOrientationDefault, Res.string.option_ebook_reader_orientation_default),
	Free(ReaderOrientationFree, Res.string.option_ebook_reader_orientation_free),
	Portrait(ReaderOrientationPortrait, Res.string.option_ebook_reader_orientation_portrait),
	Landscape(ReaderOrientationLandscape, Res.string.option_ebook_reader_orientation_landscape),
	LockedPortrait(ReaderOrientationLockedPortrait, Res.string.option_ebook_reader_orientation_locked_portrait),
	LockedLandscape(ReaderOrientationLockedLandscape, Res.string.option_ebook_reader_orientation_locked_landscape),
	ReversePortrait(ReaderOrientationReversePortrait, Res.string.option_ebook_reader_orientation_reverse_portrait);

	companion object {
		fun forOrientation(orientation: String?): ReaderOrientationOption =
			entries.firstOrNull { option -> option.orientation == orientation } ?: Default
	}
}

private enum class ReaderTapZoneOption(
	val tapZone: String,
	val title: StringResource
) {
	Default(ReaderTapZoneDefault, Res.string.option_ebook_reader_tap_zone_default),
	Edge(ReaderTapZoneEdge, Res.string.option_ebook_reader_tap_zone_edge),
	Kindle(ReaderTapZoneKindle, Res.string.option_ebook_reader_tap_zone_kindle),
	LShaped(ReaderTapZoneLShaped, Res.string.option_ebook_reader_tap_zone_l_shaped),
	RightLeft(ReaderTapZoneRightLeft, Res.string.option_ebook_reader_tap_zone_right_left),
	Disabled(ReaderTapZoneDisabled, Res.string.option_ebook_reader_tap_zone_disabled);

	companion object {
		fun forTapZone(tapZone: String?): ReaderTapZoneOption =
			entries.firstOrNull { option -> option.tapZone == tapZone } ?: Default
	}
}

private val readerFontSizeOptions = listOf(90, 100, 112, 125, 140, 160, 180)
private val readerLineHeightOptions = listOf(120, 135, 155, 170, 190, 220)
private val readerParagraphSpacingOptions = listOf(0, 25, 50, 75, 100, 150, 200)
private val readerMarginOptions = listOf(0, 4, 8, 12, 16, 24)
private val readerDimOverlayOptions = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80)
private val readerPdfPageGapOptions = listOf(0, 4, 8, 12, 16, 24, 32, 48)

@Composable
private fun readerLineHeightLabel(percent: Int): String =
	"${percent / 100}.${(percent % 100).toString().padStart(2, '0')}".trimEnd('0').trimEnd('.')

@Composable
private fun readerDimOverlayLabel(percent: Int): String =
	if (percent <= 0) stringResource(Res.string.option_off) else "$percent%"
