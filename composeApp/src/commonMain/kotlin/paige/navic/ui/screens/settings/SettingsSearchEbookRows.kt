package paige.navic.ui.screens.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.LidaClipsVideoCacheSizeOptionsMb
import paige.navic.domain.models.lidaClipsVideoCacheSizeLabel
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.models.settings.*
import paige.navic.reader.DefaultReaderFontSizePercent
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDragAnimationStandard
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceNavic
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderNavBarTypeVerticalRight
import paige.navic.reader.ReaderPdfFitWidth
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneInvertNone
import kotlin.math.roundToInt

@Composable
internal fun settingsSearchEbookRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
		add(selectionRow(
			id = "ebooks.font-family",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_font_family),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_font_family),
			keywords = listOf("reader", "ebook", "EPUB", "typeface"),
			items = readerFontFamilySearchOptions,
			label = { fontFamily -> readerFontFamilySearchLabel(fontFamily) },
			selection = readerSettings.fontFamily ?: ReaderSansFontFamily,
			onSelect = { fontFamily -> preferenceManager.readerFontFamily = fontFamily }
		))
		add(selectionRow(
			id = "ebooks.font-source",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_font_source),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_font_source),
			keywords = listOf("reader", "ebook", "EPUB", "typeface", "publisher", "book fonts"),
			items = readerFontSourceSearchOptions,
			label = { fontSource -> readerFontSourceSearchLabel(fontSource) },
			selection = readerSettings.fontSource ?: ReaderFontSourceNavic,
			onSelect = { fontSource -> preferenceManager.readerFontSource = fontSource }
		))
		add(selectionRow(
			id = "ebooks.font-size",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_font_size),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_font_size),
			keywords = listOf("reader", "ebook", "EPUB", "text"),
			items = readerFontSizeSearchOptions,
			label = { percent -> "$percent%" },
			selection = readerSettings.fontSizePercent ?: DefaultReaderFontSizePercent,
			onSelect = { percent -> preferenceManager.readerFontSizePercent = percent }
		))
		add(selectionRow(
			id = "ebooks.line-height",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_line_height),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_line_height),
			keywords = listOf("reader", "ebook", "EPUB", "spacing"),
			items = readerLineHeightSearchOptions,
			label = { percent -> readerLineHeightSearchLabel(percent) },
			selection = readerLineHeightPercent,
			onSelect = { percent -> preferenceManager.readerLineHeightPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.paragraph-spacing",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_paragraph_spacing),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_paragraph_spacing),
			keywords = listOf("reader", "ebook", "EPUB", "paragraph", "spacing"),
			items = readerParagraphSpacingSearchOptions,
			label = { percent -> "$percent%" },
			selection = readerSettings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
			onSelect = { percent -> preferenceManager.readerParagraphSpacingPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.dim-overlay",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_dim_overlay),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_dim_overlay),
			keywords = listOf("reader", "ebook", "EPUB", "brightness", "dim", "Komikku"),
			items = readerDimOverlaySearchOptions,
			label = { percent -> readerDimOverlaySearchLabel(percent) },
			selection = readerSettings.dimOverlayPercent ?: 0,
			onSelect = { percent -> preferenceManager.readerDimOverlayPercent = percent }
		))
		add(switchRow(
			id = "ebooks.grayscale",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_grayscale),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_grayscale),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "grayscale", "monochrome", "Komikku"),
			value = preferenceManager.readerGrayscaleEnabled,
			onSetValue = { enabled -> preferenceManager.readerGrayscaleEnabled = enabled }
		))
		add(switchRow(
			id = "ebooks.inverted-colors",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_inverted_colors),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_inverted_colors),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "invert", "negative", "Komikku"),
			value = preferenceManager.readerInvertedColors,
			onSetValue = { enabled -> preferenceManager.readerInvertedColors = enabled }
		))
		add(selectionRow(
			id = "ebooks.theme",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_theme),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_theme),
			keywords = listOf("reader", "ebook", "EPUB", "dark", "light"),
			items = readerThemeSearchOptions,
			label = { theme -> readerThemeSearchLabel(theme) },
			selection = readerSettings.theme ?: ReaderLightTheme,
			onSelect = { theme -> preferenceManager.readerTheme = theme }
		))
		add(selectionRow(
			id = "ebooks.orientation",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_orientation),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_orientation),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "rotation", "orientation", "Komikku"),
			items = readerOrientationSearchOptions,
			label = { orientation -> readerOrientationSearchLabel(orientation) },
			selection = readerSettings.orientation ?: ReaderOrientationDefault,
			onSelect = { orientation -> preferenceManager.readerOrientation = orientation }
		))
		add(switchRow(
			id = "ebooks.fullscreen",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_fullscreen),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_fullscreen),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "fullscreen", "immersive", "Komikku", "system bars"),
			value = preferenceManager.readerFullscreen,
			onSetValue = { enabled -> preferenceManager.readerFullscreen = enabled }
		))
		add(selectionRow(
			id = "ebooks.direction",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_direction),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_direction),
			keywords = listOf("reader", "ebook", "EPUB", "direction", "RTL", "LTR", "manga", "Komikku"),
			items = readerDirectionSearchOptions,
			label = { direction -> readerDirectionSearchLabel(direction) },
			selection = readerSettings.direction ?: ReaderDirectionDefault,
			onSelect = { direction -> preferenceManager.readerDirection = direction }
		))
		add(selectionRow(
			id = "ebooks.nav-bar-type",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_nav_bar_type),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_nav_bar_type),
			keywords = listOf("reader", "ebook", "EPUB", "PDF", "Komikku", "progress", "rail", "seekbar", "navigator", "left", "right", "bottom"),
			items = readerNavBarTypeSearchOptions,
			label = { navBarType -> readerNavBarTypeSearchLabel(navBarType) },
			selection = readerSettings.navBarType ?: ReaderNavBarTypeVerticalRight,
			onSelect = { navBarType -> preferenceManager.readerNavBarType = navBarType }
		))
		add(selectionRow(
			id = "ebooks.flow",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_flow),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_paged),
			keywords = listOf("reader", "ebook", "EPUB", "paged", "vertical", "scroll", "gaps"),
			items = readerFlowSearchOptions,
			label = { flowMode ->
				readerFlowSearchLabel(flowMode)
			},
			selection = readerSettings.flowMode ?: ReaderFlowPaged,
			onSelect = { flowMode ->
				preferenceManager.readerFlowMode = flowMode
				preferenceManager.readerPaged = flowMode != ReaderFlowScrolled &&
					flowMode != ReaderFlowScrolledGaps
			}
		))
		add(selectionRow(
			id = "ebooks.page-turn-animation",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_page_turn_animation),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_page_turn_animation),
			keywords = listOf("reader", "ebook", "EPUB", "drag", "curl", "standard", "gesture", "Komikku", "page turn"),
			items = readerDragAnimationSearchOptions,
			label = { dragAnimationMode -> readerDragAnimationSearchLabel(dragAnimationMode) },
			selection = readerSettings.dragAnimationMode ?: ReaderDragAnimationStandard,
			onSelect = { dragAnimationMode -> preferenceManager.readerDragAnimationMode = dragAnimationMode }
		))
		add(selectionRow(
			id = "ebooks.pdf-fit",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_pdf_fit),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_pdf_fit),
			keywords = ebookReaderSettingDescriptor("ebooks.pdf-fit").keywords,
			items = readerPdfFitSearchOptions,
			label = { pdfFitMode -> readerPdfFitSearchLabel(pdfFitMode) },
			selection = readerSettings.pdfFitMode ?: ReaderPdfFitWidth,
			onSelect = { pdfFitMode -> preferenceManager.readerPdfFitMode = pdfFitMode }
		))
		add(switchRow(
			id = "ebooks.pdf-crop-borders",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_pdf_crop_borders),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_pdf_crop_borders),
			keywords = ebookReaderSettingDescriptor("ebooks.pdf-crop-borders").keywords,
			value = preferenceManager.readerPdfCropBorders,
			onSetValue = { enabled -> preferenceManager.readerPdfCropBorders = enabled }
		))
		add(selectionRow(
			id = "ebooks.pdf-page-gap",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_pdf_page_gap),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_pdf_page_gap),
			keywords = ebookReaderSettingDescriptor("ebooks.pdf-page-gap").keywords,
			items = readerPdfPageGapSearchOptions,
			label = { percent -> "$percent%" },
			selection = readerSettings.pdfPageGapPercent ?: 0,
			onSelect = { percent -> preferenceManager.readerPdfPageGapPercent = percent }
		))
		add(selectionRow(
			id = "ebooks.tap-zone",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_tap_zone),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_tap_zone),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "page turn"),
			items = readerTapZoneSearchOptions,
			label = { tapZone -> readerTapZoneSearchLabel(tapZone) },
			selection = readerSettings.tapZone ?: ReaderTapZoneDefault,
			onSelect = { tapZone -> preferenceManager.readerTapZone = tapZone }
		))
		add(selectionRow(
			id = "ebooks.tap-zone-invert",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_tap_zone_invert),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_tap_zone_invert),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "invert", "mirror"),
			items = readerTapZoneInvertSearchOptions,
			label = { tapZoneInvertMode -> readerTapZoneInvertSearchLabel(tapZoneInvertMode) },
			selection = readerSettings.tapZoneInvertMode ?: ReaderTapZoneInvertNone,
			onSelect = { tapZoneInvertMode -> preferenceManager.readerTapZoneInvertMode = tapZoneInvertMode }
		))
		add(switchRow(
			id = "ebooks.smaller-tap-zones",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_smaller_tap_zones),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_smaller_tap_zones),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "smaller", "zones"),
			value = preferenceManager.readerSmallerTapZone,
			onSetValue = { enabled -> preferenceManager.readerSmallerTapZone = enabled }
		))
		add(switchRow(
			id = "ebooks.publisher-styles",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_publisher_styles),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_publisher_styles),
			keywords = listOf("reader", "ebook", "EPUB", "publisher", "CSS", "style"),
			value = preferenceManager.readerPublisherStylesEnabled,
			onSetValue = { enabled -> preferenceManager.readerPublisherStylesEnabled = enabled }
		))
		add(switchRow(
			id = "ebooks.keep-screen-on",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_keep_screen_on),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_keep_screen_on),
			keywords = listOf("reader", "ebook", "EPUB", "screen", "awake", "battery"),
			value = preferenceManager.readerKeepScreenOn,
			onSetValue = { enabled -> preferenceManager.readerKeepScreenOn = enabled }
		))
		add(switchRow(
			id = "ebooks.volume-keys",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_volume_keys),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_volume_keys),
			keywords = listOf("reader", "ebook", "EPUB", "volume", "keys", "page turn", "Librera", "Komikku"),
			value = preferenceManager.readerVolumeKeyPageTurns,
			onSetValue = { enabled -> preferenceManager.readerVolumeKeyPageTurns = enabled }
		))
		add(switchRow(
			id = "ebooks.media-overlay",
			path = path(ebooks),
			title = stringResource(Res.string.option_ebook_reader_media_overlay),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_media_overlay),
			keywords = listOf("reader", "ebook", "Storyteller", "readaloud", "media overlay", "audio labels"),
			value = preferenceManager.readerMediaOverlayEnabled,
			onSetValue = { enabled -> preferenceManager.readerMediaOverlayEnabled = enabled }
		))
	}
}
