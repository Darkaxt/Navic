package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.BinderyMaxBookGridColumns
import paige.navic.domain.models.BinderyMinBookGridColumns
import paige.navic.reader.ReaderBlackTheme
import paige.navic.reader.ReaderBookFontFamily
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderDuskTheme
import paige.navic.reader.ReaderDyslexicFontFamily
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceCustom
import paige.navic.reader.ReaderFontSourcePublisher
import paige.navic.reader.ReaderFontSourceSystem
import paige.navic.reader.ReaderHumanistFontFamily
import paige.navic.reader.ReaderMonoFontFamily
import paige.navic.reader.ReaderOrientationFree
import paige.navic.reader.ReaderOrientationLandscape
import paige.navic.reader.ReaderOrientationLockedLandscape
import paige.navic.reader.ReaderOrientationLockedPortrait
import paige.navic.reader.ReaderOrientationPortrait
import paige.navic.reader.ReaderOrientationReversePortrait
import paige.navic.reader.ReaderPdfFitHeight
import paige.navic.reader.ReaderPdfFitOriginal
import paige.navic.reader.ReaderPdfFitPage
import paige.navic.reader.ReaderPublisherFontFamily
import paige.navic.reader.ReaderSepiaTheme
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.ReaderSupportedDirections
import paige.navic.reader.ReaderSupportedPdfFitModes
import paige.navic.reader.ReaderSupportedFlowModes
import paige.navic.reader.ReaderSupportedFontFamilies
import paige.navic.reader.ReaderSupportedFontSources
import paige.navic.reader.ReaderSupportedOrientations
import paige.navic.reader.ReaderSupportedTapZones
import paige.navic.reader.ReaderSupportedThemes
import paige.navic.reader.ReaderTapZoneDisabled
import paige.navic.reader.ReaderTapZoneEdge
import paige.navic.reader.ReaderTapZoneKindle
import paige.navic.reader.ReaderTapZoneLShaped
import paige.navic.reader.ReaderTapZoneRightLeft
import paige.navic.reader.ReaderTypewriterFontFamily
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.ui.screens.settings.components.SettingValueRow

internal data class SearchableSettingsRow(
	val text: SettingsSearchEntryText,
	val content: @Composable () -> Unit
)
internal fun switchRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	value: Boolean,
	onSetValue: (Boolean) -> Unit
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			SettingSwitchRow(
				title = { Text(title) },
				subtitle = { subtitle?.let { Text(it) } },
				value = value,
				onSetValue = onSetValue
			)
		}
	)

internal fun <Item> selectionRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	items: List<Item>,
	label: @Composable (Item) -> String,
	selection: Item,
	onSelect: (Item) -> Unit
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			SettingSelectionRow(
				title = { Text(title) },
				items = items.toImmutableList(),
				label = label,
				description = subtitle,
				selection = selection,
				onSelect = onSelect
			)
		}
	)

internal fun valueRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	value: String
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			SettingValueRow(
				title = { Text(title) },
				subtitle = { subtitle?.let { Text(it) } },
				value = value
			)
		}
	)

internal fun sliderRow(
	id: String,
	path: String,
	title: String,
	subtitle: String? = null,
	keywords: List<String> = emptyList(),
	valueText: String,
	value: Float,
	onValueChange: (Float) -> Unit,
	valueRange: ClosedFloatingPointRange<Float>,
	steps: Int = 0
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			subtitle = subtitle,
			keywords = keywords
		),
		content = {
			FormRow {
				Column(Modifier.fillMaxWidth()) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						Column(Modifier.weight(1f)) {
							Text(title)
							subtitle?.let {
								Text(
									text = it,
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						Text(
							valueText,
							modifier = Modifier.padding(start = 16.dp),
							fontFamily = FontFamily.Monospace,
							fontWeight = FontWeight(400),
							fontSize = 13.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Slider(
						value = value,
						onValueChange = onValueChange,
						valueRange = valueRange,
						steps = steps
					)
				}
			}
		}
	)

internal fun textFieldRow(
	id: String,
	path: String,
	title: String,
	value: String,
	keywords: List<String> = emptyList(),
	keyboardType: KeyboardType = KeyboardType.Text,
	isPassword: Boolean = false,
	digitsOnly: Boolean = false,
	onValueChange: (String) -> Unit
): SearchableSettingsRow =
	SearchableSettingsRow(
		text = SettingsSearchEntryText(
			id = id,
			path = path,
			title = title,
			keywords = keywords
		),
		content = {
			var fieldValue by remember(value) { mutableStateOf(value) }
			FormRow {
				TextField(
					value = fieldValue,
					onValueChange = { newValue ->
						if (!digitsOnly || newValue.all { it.isDigit() }) {
							fieldValue = newValue
							onValueChange(newValue)
						}
					},
					placeholder = { Text(title) },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					visualTransformation = if (isPassword) {
						PasswordVisualTransformation()
					} else {
						VisualTransformation.None
					},
					keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
					colors = TextFieldDefaults.colors(
						focusedIndicatorColor = Color.Transparent,
						unfocusedIndicatorColor = Color.Transparent
					),
					shape = MaterialTheme.shapes.medium
				)
			}
		}
	)

internal val nowPlayingUpNextCountSearchOptions = listOf(1, 2, 3, 5)
internal val pauseBetweenSongsSearchOptions = listOf(0, 5, 10, 15, 20, 30, 40, 50, 60)
internal val medleyModeSearchOptions = listOf(0, 15, 30, 45, 60)
internal val smartRewindSearchOptions = listOf(-1, 1, 2, 3, 5, 10, 15, 30)
internal val audioFadeSearchOptions = listOf(0, 250, 500, 1000, 2000)
internal val autoFillQueueTargetSizeSearchOptions = listOf(10, 25, 50, 100)
internal val downloadConcurrencySearchOptions = listOf(1, 2, 3, 5, 10)
internal val binderyBookGridColumnSearchOptions = (BinderyMinBookGridColumns..BinderyMaxBookGridColumns).toList()
internal val readerFontFamilySearchOptions = ReaderSupportedFontFamilies
internal val readerFontSourceSearchOptions = ReaderSupportedFontSources
internal val readerFontSizeSearchOptions = listOf(90, 100, 112, 125, 140, 160, 180)
internal val readerLineHeightSearchOptions = listOf(120, 135, 155, 170, 190, 220)
internal val readerParagraphSpacingSearchOptions = listOf(0, 25, 50, 75, 100, 150, 200)
internal val readerMarginSearchOptions = listOf(0, 4, 8, 12, 16, 24)
internal val readerDimOverlaySearchOptions = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80)
internal val readerThemeSearchOptions = ReaderSupportedThemes
internal val readerOrientationSearchOptions = ReaderSupportedOrientations
internal val readerDirectionSearchOptions = ReaderSupportedDirections
internal val readerFlowSearchOptions = ReaderSupportedFlowModes
internal val readerPdfFitSearchOptions = ReaderSupportedPdfFitModes
internal val readerPdfPageGapSearchOptions = listOf(0, 4, 8, 12, 16, 24, 32, 48)
internal val readerTapZoneSearchOptions = ReaderSupportedTapZones

@Composable
internal fun readerFontFamilySearchLabel(fontFamily: String): String =
	when (fontFamily) {
		ReaderSerifFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_serif)
		ReaderBookFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_book)
		ReaderHumanistFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_humanist)
		ReaderDyslexicFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_dyslexic)
		ReaderTypewriterFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_typewriter)
		ReaderMonoFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_mono)
		ReaderPublisherFontFamily -> stringResource(Res.string.option_ebook_reader_font_family_publisher)
		else -> stringResource(Res.string.option_ebook_reader_font_family_sans)
	}

@Composable
internal fun readerFontSourceSearchLabel(fontSource: String): String =
	when (fontSource) {
		ReaderFontSourceSystem -> stringResource(Res.string.option_ebook_reader_font_source_system)
		ReaderFontSourcePublisher -> stringResource(Res.string.option_ebook_reader_font_source_publisher)
		ReaderFontSourceCustom -> stringResource(Res.string.option_ebook_reader_font_source_custom)
		else -> stringResource(Res.string.option_ebook_reader_font_source_navic)
	}

@Composable
internal fun readerThemeSearchLabel(theme: String): String =
	when (theme) {
		ReaderSepiaTheme -> stringResource(Res.string.option_ebook_reader_theme_sepia)
		ReaderDuskTheme -> stringResource(Res.string.option_ebook_reader_theme_dusk)
		ReaderDarkTheme -> stringResource(Res.string.option_ebook_reader_theme_dark)
		ReaderBlackTheme -> stringResource(Res.string.option_ebook_reader_theme_black)
		else -> stringResource(Res.string.option_ebook_reader_theme_light)
	}

@Composable
internal fun readerOrientationSearchLabel(orientation: String): String =
	when (orientation) {
		ReaderOrientationFree -> stringResource(Res.string.option_ebook_reader_orientation_free)
		ReaderOrientationPortrait -> stringResource(Res.string.option_ebook_reader_orientation_portrait)
		ReaderOrientationLandscape -> stringResource(Res.string.option_ebook_reader_orientation_landscape)
		ReaderOrientationLockedPortrait -> stringResource(Res.string.option_ebook_reader_orientation_locked_portrait)
		ReaderOrientationLockedLandscape -> stringResource(Res.string.option_ebook_reader_orientation_locked_landscape)
		ReaderOrientationReversePortrait -> stringResource(Res.string.option_ebook_reader_orientation_reverse_portrait)
		else -> stringResource(Res.string.option_ebook_reader_orientation_default)
	}

@Composable
internal fun readerDirectionSearchLabel(direction: String): String =
	when (direction) {
		ReaderDirectionLtr -> stringResource(Res.string.option_ebook_reader_direction_ltr)
		ReaderDirectionRtl -> stringResource(Res.string.option_ebook_reader_direction_rtl)
		else -> stringResource(Res.string.option_ebook_reader_direction_default)
	}

@Composable
internal fun readerFlowSearchLabel(flowMode: String): String =
	when (flowMode) {
		ReaderFlowPagedVertical -> stringResource(Res.string.option_ebook_reader_paged_vertical)
		ReaderFlowScrolled -> stringResource(Res.string.option_ebook_reader_scroll)
		ReaderFlowScrolledGaps -> stringResource(Res.string.option_ebook_reader_scroll_gaps)
		else -> stringResource(Res.string.option_ebook_reader_paged)
	}

@Composable
internal fun readerPdfFitSearchLabel(pdfFitMode: String): String =
	when (pdfFitMode) {
		ReaderPdfFitPage -> stringResource(Res.string.option_ebook_reader_pdf_fit_page)
		ReaderPdfFitHeight -> stringResource(Res.string.option_ebook_reader_pdf_fit_height)
		ReaderPdfFitOriginal -> stringResource(Res.string.option_ebook_reader_pdf_fit_original)
		else -> stringResource(Res.string.option_ebook_reader_pdf_fit_width)
	}

@Composable
internal fun readerTapZoneSearchLabel(tapZone: String): String =
	when (tapZone) {
		ReaderTapZoneEdge -> stringResource(Res.string.option_ebook_reader_tap_zone_edge)
		ReaderTapZoneKindle -> stringResource(Res.string.option_ebook_reader_tap_zone_kindle)
		ReaderTapZoneLShaped -> stringResource(Res.string.option_ebook_reader_tap_zone_l_shaped)
		ReaderTapZoneRightLeft -> stringResource(Res.string.option_ebook_reader_tap_zone_right_left)
		ReaderTapZoneDisabled -> stringResource(Res.string.option_ebook_reader_tap_zone_disabled)
		else -> stringResource(Res.string.option_ebook_reader_tap_zone_default)
	}

@Composable
internal fun readerLineHeightSearchLabel(percent: Int): String =
	"${percent / 100}.${(percent % 100).toString().padStart(2, '0')}".trimEnd('0').trimEnd('.')

@Composable
internal fun readerDimOverlaySearchLabel(percent: Int): String =
	if (percent <= 0) stringResource(Res.string.option_off) else "$percent%"
internal val quickPicksLimitSearchOptions = listOf(10, 20, 30, 50)
internal val quickPicksMinDurationSearchOptions = listOf(0, 30, 60, 120, 180)

@Composable
internal fun quickPicksMinDurationSearchLabel(seconds: Int): String =
	when {
		seconds <= 0 -> stringResource(Res.string.option_off)
		seconds < 60 -> "${seconds}s"
		else -> "${seconds / 60} min"
	}
