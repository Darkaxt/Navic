package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import paige.navic.reader.*

@Composable
internal fun KomikkuGeneralSettingsPage(
	settings: ReaderSettings,
	onSettingsChange: (ReaderSettings) -> Unit
) {
	KomikkuSettingsDialogPage(title = "General") {
		SettingsSection(title = "Typography") {
			SettingsSelectableChipRow(
				title = "Font",
				options = ReaderSupportedFontFamilies.map { fontFamily ->
					fontFamily to readerFontFamilyShortLabel(fontFamily)
				},
				selectedValue = normalizedReaderFontFamily(settings.fontFamily),
				onSelect = { fontFamily -> onSettingsChange(settings.copy(fontFamily = fontFamily)) }
			)
			SettingsSelectableChipRow(
				title = "Font source",
				options = ReaderSupportedFontSources.map { fontSource ->
					fontSource to readerFontSourceShortLabel(fontSource)
				},
				selectedValue = normalizedReaderFontSource(settings.fontSource),
				onSelect = { fontSource -> onSettingsChange(settings.copy(fontSource = fontSource)) }
			)
			SliderItem(
				label = "Font size",
				value = settings.fontSizePercent ?: DefaultReaderFontSizePercent,
				valueRange = 80..180,
				valueString = "${settings.fontSizePercent ?: DefaultReaderFontSizePercent}%",
				onChange = { fontSizePercent ->
					onSettingsChange(settings.copy(fontSizePercent = fontSizePercent))
				}
			)
			SliderItem(
				label = "Font weight",
				value = (settings.fontWeight ?: 400.0).roundToInt(),
				valueRange = 100..900,
				steps = 7,
				valueString = "${(settings.fontWeight ?: 400.0).roundToInt()}",
				onChange = { fontWeight ->
					onSettingsChange(settings.copy(fontWeight = fontWeight.toDouble()))
				}
			)
			SliderItem(
				label = "Heading size",
				value = ((settings.headingFontSize ?: 1.0) * 10.0).roundToInt(),
				valueRange = 5..20,
				steps = 14,
				valueString = readerTenthsString(settings.headingFontSize ?: 1.0),
				onChange = { headingFontSize ->
					onSettingsChange(settings.copy(headingFontSize = headingFontSize / 10.0))
				}
			)
			CheckboxItem(
				label = "Publisher styles",
				checked = settings.publisherStyles == true,
				onClick = {
					onSettingsChange(settings.copy(publisherStyles = settings.publisherStyles != true))
				}
			)
		}
		SettingsSection(title = "Spacing") {
			val lineHeightPercent = ((settings.lineHeight ?: DefaultReaderLineHeight) * 100.0).roundToInt()
			SliderItem(
				label = "Line height",
				value = lineHeightPercent,
				valueRange = 120..220,
				valueString = readerPercentAsDecimalString(lineHeightPercent),
				onChange = { nextLineHeightPercent ->
					onSettingsChange(settings.copy(lineHeight = nextLineHeightPercent / 100.0))
				}
			)
			SliderItem(
				label = "Paragraph spacing",
				value = settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
				valueRange = 0..200,
				valueString = "${settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent}%",
				onChange = { paragraphSpacingPercent ->
					onSettingsChange(settings.copy(paragraphSpacingPercent = paragraphSpacingPercent))
				}
			)
			SliderItem(
				label = "Letter spacing",
				value = ((settings.letterSpacing ?: 0.0) * 10.0).roundToInt(),
				valueRange = -30..70,
				steps = 9,
				valueString = readerTenthsString(settings.letterSpacing ?: 0.0),
				onChange = { letterSpacing ->
					onSettingsChange(settings.copy(letterSpacing = letterSpacing / 10.0))
				}
			)
			SliderItem(
				label = "Word spacing",
				value = ((settings.wordSpacing ?: 0.0) * 10.0).roundToInt(),
				valueRange = -40..120,
				steps = 15,
				valueString = readerTenthsString(settings.wordSpacing ?: 0.0),
				onChange = { wordSpacing ->
					onSettingsChange(settings.copy(wordSpacing = wordSpacing / 10.0))
				}
			)
			SliderItem(
				label = "Indent",
				value = ((settings.indent ?: 0.0) * 10.0).roundToInt(),
				valueRange = -5..80,
				steps = 16,
				valueString = readerTenthsString(settings.indent ?: 0.0),
				onChange = { indent ->
					onSettingsChange(settings.copy(indent = indent / 10.0))
				}
			)
		}
		SettingsSection(title = "Page layout") {
			SliderItem(
				label = "Side margin",
				value = ((settings.sideMargin ?: 6.0) * 10.0).roundToInt(),
				valueRange = 0..200,
				steps = 19,
				valueString = readerTenthsString(settings.sideMargin ?: 6.0),
				onChange = { sideMargin ->
					onSettingsChange(settings.copy(sideMargin = sideMargin / 10.0))
				}
			)
			SliderItem(
				label = "Top margin",
				value = (settings.topMargin ?: 90.0).roundToInt(),
				valueRange = 0..200,
				steps = 9,
				valueString = "${(settings.topMargin ?: 90.0).roundToInt()}px",
				onChange = { topMargin ->
					onSettingsChange(settings.copy(topMargin = topMargin.toDouble()))
				}
			)
			SliderItem(
				label = "Bottom margin",
				value = (settings.bottomMargin ?: 50.0).roundToInt(),
				valueRange = 0..200,
				steps = 9,
				valueString = "${(settings.bottomMargin ?: 50.0).roundToInt()}px",
				onChange = { bottomMargin ->
					onSettingsChange(settings.copy(bottomMargin = bottomMargin.toDouble()))
				}
			)
			SettingsSelectableChipRow(
				title = "Columns",
				options = listOf("0" to "Auto", "1" to "Single", "2" to "Double"),
				selectedValue = (settings.maxColumnCount ?: 0).toString(),
				onSelect = { maxColumnCount ->
					onSettingsChange(settings.copy(maxColumnCount = maxColumnCount.toIntOrNull() ?: 0))
				}
			)
			SliderItem(
				label = "Column threshold",
				value = (settings.columnThreshold ?: 720.0).roundToInt(),
				valueRange = 400..1200,
				steps = 39,
				valueString = "${(settings.columnThreshold ?: 720.0).roundToInt()}px",
				onChange = { columnThreshold ->
					onSettingsChange(settings.copy(columnThreshold = columnThreshold.toDouble()))
				}
			)
		}
		SettingsSection(title = "Theme and device") {
			SettingsSelectableChipRow(
				title = "Theme",
				options = ReaderSupportedThemes.map { theme -> theme to readerThemeShortLabel(theme) },
				selectedValue = normalizedReaderTheme(settings.theme),
				onSelect = { theme -> onSettingsChange(settings.copy(theme = theme)) }
			)
			SettingsSelectableChipRow(
				title = "Rotation",
				options = ReaderSupportedOrientations.map { orientation ->
					orientation to readerOrientationShortLabel(orientation)
				},
				selectedValue = normalizedReaderOrientation(settings.orientation),
				onSelect = { orientation -> onSettingsChange(settings.copy(orientation = orientation)) }
			)
			CheckboxItem(
				label = "Paper texture",
				checked = settings.paperTextureEnabled != false,
				onClick = {
					onSettingsChange(settings.copy(paperTextureEnabled = settings.paperTextureEnabled == false))
				}
			)
			CheckboxItem(
				label = "Page edges",
				checked = settings.pageEdgesEnabled != false,
				onClick = {
					onSettingsChange(settings.copy(pageEdgesEnabled = settings.pageEdgesEnabled == false))
				}
			)
			CheckboxItem(
				label = "Paper stains",
				checked = settings.paperStainsEnabled != false,
				onClick = {
					onSettingsChange(settings.copy(paperStainsEnabled = settings.paperStainsEnabled == false))
				}
			)
			CheckboxItem(
				label = "Cover backdrop",
				checked = settings.coverBackdropEnabled != false,
				onClick = {
					onSettingsChange(settings.copy(coverBackdropEnabled = settings.coverBackdropEnabled == false))
				}
			)
			CheckboxItem(
				label = "Fullscreen",
				checked = settings.fullscreen == true,
				onClick = { onSettingsChange(settings.copy(fullscreen = settings.fullscreen != true)) }
			)
			CheckboxItem(
				label = "Keep screen on",
				checked = settings.keepScreenOn == true,
				onClick = { onSettingsChange(settings.copy(keepScreenOn = settings.keepScreenOn != true)) }
			)
			CheckboxItem(
				label = "Volume keys",
				checked = settings.volumeKeyPageTurns == true,
				onClick = {
					onSettingsChange(settings.copy(volumeKeyPageTurns = settings.volumeKeyPageTurns != true))
				}
			)
		}
	}
}
