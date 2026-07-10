package paige.navic.ui.screens.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_ebook_reader_column_mode_auto
import navic.composeapp.generated.resources.option_ebook_reader_column_mode_double
import navic.composeapp.generated.resources.option_ebook_reader_column_mode_single
import navic.composeapp.generated.resources.option_ebook_reader_direction_default
import navic.composeapp.generated.resources.option_ebook_reader_direction_ltr
import navic.composeapp.generated.resources.option_ebook_reader_direction_rtl
import navic.composeapp.generated.resources.option_ebook_reader_font_family_book
import navic.composeapp.generated.resources.option_ebook_reader_font_family_dyslexic
import navic.composeapp.generated.resources.option_ebook_reader_font_family_humanist
import navic.composeapp.generated.resources.option_ebook_reader_font_family_mono
import navic.composeapp.generated.resources.option_ebook_reader_font_family_publisher
import navic.composeapp.generated.resources.option_ebook_reader_font_family_sans
import navic.composeapp.generated.resources.option_ebook_reader_font_family_serif
import navic.composeapp.generated.resources.option_ebook_reader_font_family_typewriter
import navic.composeapp.generated.resources.option_ebook_reader_font_source_custom
import navic.composeapp.generated.resources.option_ebook_reader_font_source_navic
import navic.composeapp.generated.resources.option_ebook_reader_font_source_publisher
import navic.composeapp.generated.resources.option_ebook_reader_font_source_system
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type_bottom
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type_left
import navic.composeapp.generated.resources.option_ebook_reader_nav_bar_type_right
import navic.composeapp.generated.resources.option_ebook_reader_page_turn_animation_curl
import navic.composeapp.generated.resources.option_ebook_reader_page_turn_animation_standard
import navic.composeapp.generated.resources.option_ebook_reader_orientation_default
import navic.composeapp.generated.resources.option_ebook_reader_orientation_free
import navic.composeapp.generated.resources.option_ebook_reader_orientation_landscape
import navic.composeapp.generated.resources.option_ebook_reader_orientation_locked_landscape
import navic.composeapp.generated.resources.option_ebook_reader_orientation_locked_portrait
import navic.composeapp.generated.resources.option_ebook_reader_orientation_portrait
import navic.composeapp.generated.resources.option_ebook_reader_orientation_reverse_portrait
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_height
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_original
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_page
import navic.composeapp.generated.resources.option_ebook_reader_pdf_fit_width
import navic.composeapp.generated.resources.option_ebook_reader_paged
import navic.composeapp.generated.resources.option_ebook_reader_paged_vertical
import navic.composeapp.generated.resources.option_ebook_reader_scroll
import navic.composeapp.generated.resources.option_ebook_reader_scroll_gaps
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_default
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_disabled
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_edge
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_both
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_horizontal
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_none
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_invert_vertical
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_kindle
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_l_shaped
import navic.composeapp.generated.resources.option_ebook_reader_tap_zone_right_left
import navic.composeapp.generated.resources.option_ebook_reader_theme_aged_paper
import navic.composeapp.generated.resources.option_ebook_reader_theme_black
import navic.composeapp.generated.resources.option_ebook_reader_theme_dark
import navic.composeapp.generated.resources.option_ebook_reader_theme_dusk
import navic.composeapp.generated.resources.option_ebook_reader_theme_light
import navic.composeapp.generated.resources.option_ebook_reader_theme_sepia
import org.jetbrains.compose.resources.StringResource
import paige.navic.reader.ReaderAgedPaperTheme
import paige.navic.reader.ReaderBlackTheme
import paige.navic.reader.ReaderBookFontFamily
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderDragAnimationCurl
import paige.navic.reader.ReaderDragAnimationStandard
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
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.ReaderSepiaTheme
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

internal enum class ReaderFontFamilyOption(
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

internal enum class ReaderFontSourceOption(
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

internal enum class ReaderThemeOption(
	val theme: String,
	val title: StringResource
) {
	Light(ReaderLightTheme, Res.string.option_ebook_reader_theme_light),
	Sepia(ReaderSepiaTheme, Res.string.option_ebook_reader_theme_sepia),
	AgedPaper(ReaderAgedPaperTheme, Res.string.option_ebook_reader_theme_aged_paper),
	Dusk(ReaderDuskTheme, Res.string.option_ebook_reader_theme_dusk),
	Dark(ReaderDarkTheme, Res.string.option_ebook_reader_theme_dark),
	Black(ReaderBlackTheme, Res.string.option_ebook_reader_theme_black);

	companion object {
		fun forTheme(theme: String?): ReaderThemeOption =
			entries.firstOrNull { option -> option.theme == theme } ?: Light
	}
}

internal enum class ReaderFlowOption(
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

internal enum class ReaderDragAnimationOption(
	val dragAnimationMode: String,
	val title: StringResource
) {
	Standard(ReaderDragAnimationStandard, Res.string.option_ebook_reader_page_turn_animation_standard),
	Curl(ReaderDragAnimationCurl, Res.string.option_ebook_reader_page_turn_animation_curl);

	companion object {
		fun forDragAnimationMode(dragAnimationMode: String?): ReaderDragAnimationOption =
			entries.firstOrNull { option -> option.dragAnimationMode == dragAnimationMode } ?: Standard
	}
}

internal enum class ReaderColumnModeOption(
	val maxColumnCount: Int,
	val title: StringResource
) {
	Auto(0, Res.string.option_ebook_reader_column_mode_auto),
	Single(1, Res.string.option_ebook_reader_column_mode_single),
	Double(2, Res.string.option_ebook_reader_column_mode_double);

	companion object {
		fun forMaxColumnCount(maxColumnCount: Int?): ReaderColumnModeOption =
			entries.firstOrNull { option -> option.maxColumnCount == maxColumnCount } ?: Auto
	}
}

internal enum class ReaderPdfFitOption(
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

internal enum class ReaderDirectionOption(
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

internal enum class ReaderNavBarTypeOption(
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

internal enum class ReaderOrientationOption(
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

internal enum class ReaderTapZoneOption(
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

internal enum class ReaderTapZoneInvertOption(
	val tapZoneInvertMode: String,
	val title: StringResource
) {
	None(ReaderTapZoneInvertNone, Res.string.option_ebook_reader_tap_zone_invert_none),
	Horizontal(ReaderTapZoneInvertHorizontal, Res.string.option_ebook_reader_tap_zone_invert_horizontal),
	Vertical(ReaderTapZoneInvertVertical, Res.string.option_ebook_reader_tap_zone_invert_vertical),
	Both(ReaderTapZoneInvertBoth, Res.string.option_ebook_reader_tap_zone_invert_both);

	companion object {
		fun forTapZoneInvertMode(tapZoneInvertMode: String?): ReaderTapZoneInvertOption =
			entries.firstOrNull { option -> option.tapZoneInvertMode == tapZoneInvertMode } ?: None
	}
}
