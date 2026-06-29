package paige.navic.ui.screens.reader

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import paige.navic.reader.DefaultReaderFontSizePercent
import paige.navic.reader.DefaultReaderLineHeight
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderColorFilterModeSrcOver
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderSupportedColorFilterModes
import paige.navic.reader.ReaderSupportedDirections
import paige.navic.reader.ReaderSupportedFontFamilies
import paige.navic.reader.ReaderSupportedFontSources
import paige.navic.reader.ReaderSupportedNavBarTypes
import paige.navic.reader.ReaderSupportedOrientations
import paige.navic.reader.ReaderSupportedPdfFitModes
import paige.navic.reader.ReaderSupportedSettingsScopes
import paige.navic.reader.ReaderSupportedTapZoneInvertModes
import paige.navic.reader.ReaderSupportedThemes
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
import paige.navic.reader.normalizedReaderDirection
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.normalizedReaderFontFamily
import paige.navic.reader.normalizedReaderFontSource
import paige.navic.reader.normalizedReaderNavBarType
import paige.navic.reader.normalizedReaderOrientation
import paige.navic.reader.normalizedReaderPdfFitMode
import paige.navic.reader.normalizedReaderTapZone
import paige.navic.reader.normalizedReaderTapZoneInvertMode
import paige.navic.reader.normalizedReaderTheme
import paige.navic.reader.readerColorFilterModeShortLabel
import paige.navic.reader.readerDirectionShortLabel
import paige.navic.reader.readerFontFamilyShortLabel
import paige.navic.reader.readerFontSourceShortLabel
import paige.navic.reader.readerNavBarTypeShortLabel
import paige.navic.reader.readerOrientationShortLabel
import paige.navic.reader.readerPdfFitShortLabel
import paige.navic.reader.readerSettingsScopeLabel
import paige.navic.reader.readerThemeShortLabel

internal val TabbedDialogPaddingsVertical = 8.dp

private object SettingsItemsPaddings {
	val Horizontal = 24.dp
	val Vertical = 10.dp
	val SectionVertical = 6.dp
	val SliderVertical = 6.dp
}

private enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label) {
	Reading("Reading mode", "Reading"),
	General("General"),
	PdfImage("PDF/Image", "PDF"),
	CustomFilter("Custom filter", "Filter")
}

private data class KomikkuReadingModeOption(
	val label: String,
	val flowMode: String,
	val paged: Boolean,
	val direction: String
)

private val KomikkuReadingModeOptions = listOf(
	KomikkuReadingModeOption(
		label = "Default",
		flowMode = ReaderFlowPaged,
		paged = true,
		direction = ReaderDirectionDefault
	),
	KomikkuReadingModeOption(
		label = "Paged (left to right)",
		flowMode = ReaderFlowPaged,
		paged = true,
		direction = ReaderDirectionLtr
	),
	KomikkuReadingModeOption(
		label = "Paged (right to left)",
		flowMode = ReaderFlowPaged,
		paged = true,
		direction = ReaderDirectionRtl
	),
	KomikkuReadingModeOption(
		label = "Paged (vertical)",
		flowMode = ReaderFlowPagedVertical,
		paged = true,
		direction = ReaderDirectionDefault
	),
	KomikkuReadingModeOption(
		label = "Long strip",
		flowMode = ReaderFlowScrolled,
		paged = false,
		direction = ReaderDirectionDefault
	),
	KomikkuReadingModeOption(
		label = "Long strip with gaps",
		flowMode = ReaderFlowScrolledGaps,
		paged = false,
		direction = ReaderDirectionDefault
	)
)

private val KomikkuTapZoneOptions = listOf(
	ReaderTapZoneDefault to "Default",
	ReaderTapZoneLShaped to "L shaped",
	ReaderTapZoneKindle to "Kindle-ish",
	ReaderTapZoneEdge to "Edge",
	ReaderTapZoneRightLeft to "Right and Left",
	ReaderTapZoneDisabled to "Disabled"
)

private val KomikkuTapZoneInvertOptions = listOf(
	ReaderTapZoneInvertNone to "None",
	ReaderTapZoneInvertHorizontal to "Horizontal",
	ReaderTapZoneInvertVertical to "Vertical",
	ReaderTapZoneInvertBoth to "Both"
)

private fun komikkuSettingsTabs(publicationFormat: ReaderPublicationFormat): List<KomikkuSettingsTab> =
	if (publicationFormat == ReaderPublicationFormat.Pdf) {
		listOf(
			KomikkuSettingsTab.Reading,
			KomikkuSettingsTab.General,
			KomikkuSettingsTab.PdfImage,
			KomikkuSettingsTab.CustomFilter
		)
	} else {
		listOf(
			KomikkuSettingsTab.Reading,
			KomikkuSettingsTab.General,
			KomikkuSettingsTab.CustomFilter
		)
	}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderSettingsDialog(
	settings: ReaderSettings,
	initialTab: Int,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onShowMenus: () -> Unit,
	onHideMenus: () -> Unit,
	onDismissRequest: () -> Unit
) {
	// Ported from Komikku ReaderSettingsDialog: tabbed overlay above content, never a docked panel.
	val tabs = komikkuSettingsTabs(publicationFormat)
	val pagerState = rememberPagerState(
		initialPage = initialTab.coerceIn(tabs.indices),
		pageCount = { tabs.size }
	)

	LaunchedEffect(pagerState.currentPage) {
		if (tabs[pagerState.currentPage] == KomikkuSettingsTab.CustomFilter) {
			onHideMenus()
		} else {
			onShowMenus()
		}
	}

	BoxWithConstraints {
		val compactTabLabels = maxWidth < 520.dp
		val settingsDimAmount = if (tabs[pagerState.currentPage] == KomikkuSettingsTab.CustomFilter) 0f else 0.5f
		KomikkuTabbedDialog(
			onDismissRequest = {
				onDismissRequest()
				onShowMenus()
			},
			tabs = tabs,
			pagerState = pagerState,
			useCompactLabels = compactTabLabels,
			dimAmount = settingsDimAmount,
			modifier = Modifier.heightIn(max = maxHeight * 0.75f)
		) { page ->
			val settingsScrollState = rememberScrollState()
			Column(
				modifier = Modifier
					.padding(vertical = TabbedDialogPaddingsVertical)
					.komikkuVerticalScrollEdgeFade(settingsScrollState)
					.verticalScroll(settingsScrollState)
			) {
				when (tabs[page]) {
					KomikkuSettingsTab.Reading -> KomikkuSettingsDialogPage(
						title = "For this book"
					) {
						SettingsSelectableChipRow(
							title = "Scope",
							options = ReaderSupportedSettingsScopes.map { scope -> scope.name to readerSettingsScopeLabel(scope) },
							selectedValue = settingsScope.name,
							onSelect = { scopeName ->
								ReaderSettingsScope.entries
									.firstOrNull { scope -> scope.name == scopeName }
									?.let(onSettingsScopeChange)
							}
						)
						if (hasBookSettings) {
							TextButton(onClick = onResetBookSettings) {
								Text("Reset book")
							}
						}
						KomikkuSettingsReadingModeRow(
							settings = settings,
							onSelect = { option ->
								onSettingsChange(settings.copy(
									flowMode = option.flowMode,
									paged = option.paged,
									direction = option.direction
								))
							}
						)
						SettingsSelectableChipRow(
							title = "Direction",
							options = ReaderSupportedDirections.map { direction -> direction to readerDirectionShortLabel(direction) },
							selectedValue = normalizedReaderDirection(settings.direction),
							onSelect = { direction ->
								onSettingsChange(settings.copy(direction = direction))
							}
						)
						SettingsSelectableChipRow(
							title = "Progress rail",
							options = ReaderSupportedNavBarTypes.map { navBarType ->
								navBarType to readerNavBarTypeShortLabel(navBarType)
							},
							selectedValue = normalizedReaderNavBarType(settings.navBarType),
							onSelect = { navBarType ->
								onSettingsChange(settings.copy(navBarType = navBarType))
							}
						)
						SettingsSelectableChipRow(
							title = "Tap zones",
							options = KomikkuTapZoneOptions,
							selectedValue = normalizedReaderTapZone(settings.tapZone),
							onSelect = { tapZone ->
								onSettingsChange(settings.copy(tapZone = tapZone))
							}
						)
						if (normalizedReaderTapZone(settings.tapZone) != ReaderTapZoneDisabled) {
							SettingsSelectableChipRow(
								title = "Tapping inversion",
								options = KomikkuTapZoneInvertOptions,
								selectedValue = normalizedReaderTapZoneInvertMode(settings.tapZoneInvertMode),
								onSelect = { tapZoneInvertMode ->
									onSettingsChange(settings.copy(tapZoneInvertMode = tapZoneInvertMode))
								}
							)
						}
						CheckboxItem(
							label = "Smaller tap zones",
							checked = settings.smallerTapZone == true,
							onClick = {
								onSettingsChange(settings.copy(smallerTapZone = settings.smallerTapZone != true))
							}
						)
					}
					KomikkuSettingsTab.General -> KomikkuSettingsDialogPage(
						title = "General"
					) {
						SettingsSection(title = "Typography") {
							SettingsSelectableChipRow(
								title = "Font",
								options = ReaderSupportedFontFamilies.map { fontFamily ->
									fontFamily to readerFontFamilyShortLabel(fontFamily)
								},
								selectedValue = normalizedReaderFontFamily(settings.fontFamily),
								onSelect = { fontFamily ->
									onSettingsChange(settings.copy(fontFamily = fontFamily))
								}
							)
							SettingsSelectableChipRow(
								title = "Font source",
								options = ReaderSupportedFontSources.map { fontSource ->
									fontSource to readerFontSourceShortLabel(fontSource)
								},
								selectedValue = normalizedReaderFontSource(settings.fontSource),
								onSelect = { fontSource ->
									onSettingsChange(settings.copy(fontSource = fontSource))
								}
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
								value = (((settings.headingFontSize ?: 1.0) * 10.0).roundToInt()),
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
								value = (((settings.letterSpacing ?: 0.0) * 10.0).roundToInt()),
								valueRange = -30..70,
								steps = 9,
								valueString = readerTenthsString(settings.letterSpacing ?: 0.0),
								onChange = { letterSpacing ->
									onSettingsChange(settings.copy(letterSpacing = letterSpacing / 10.0))
								}
							)
							SliderItem(
								label = "Word spacing",
								value = (((settings.wordSpacing ?: 0.0) * 10.0).roundToInt()),
								valueRange = -40..120,
								steps = 15,
								valueString = readerTenthsString(settings.wordSpacing ?: 0.0),
								onChange = { wordSpacing ->
									onSettingsChange(settings.copy(wordSpacing = wordSpacing / 10.0))
								}
							)
							SliderItem(
								label = "Indent",
								value = (((settings.indent ?: 0.0) * 10.0).roundToInt()),
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
								value = (((settings.sideMargin ?: 6.0) * 10.0).roundToInt()),
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
								options = listOf(
									"0" to "Auto",
									"1" to "Single",
									"2" to "Double"
								),
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
								onSelect = { theme ->
									onSettingsChange(settings.copy(theme = theme))
								}
							)
							SettingsSelectableChipRow(
								title = "Rotation",
								options = ReaderSupportedOrientations.map { orientation ->
									orientation to readerOrientationShortLabel(orientation)
								},
								selectedValue = normalizedReaderOrientation(settings.orientation),
								onSelect = { orientation ->
									onSettingsChange(settings.copy(orientation = orientation))
								}
							)
							CheckboxItem(
								label = "Fullscreen",
								checked = settings.fullscreen == true,
								onClick = {
									onSettingsChange(settings.copy(fullscreen = settings.fullscreen != true))
								}
							)
							CheckboxItem(
								label = "Keep screen on",
								checked = settings.keepScreenOn == true,
								onClick = {
									onSettingsChange(settings.copy(keepScreenOn = settings.keepScreenOn != true))
								}
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
					KomikkuSettingsTab.PdfImage -> KomikkuSettingsDialogPage(
						title = "PDF/Image"
					) {
						SettingsSelectableChipRow(
							title = "Page fit",
							options = ReaderSupportedPdfFitModes.map { fitMode ->
								fitMode to readerPdfFitShortLabel(fitMode)
							},
							selectedValue = normalizedReaderPdfFitMode(settings.pdfFitMode),
							onSelect = { fitMode ->
								onSettingsChange(settings.copy(pdfFitMode = fitMode))
							}
						)
						CheckboxItem(
							label = "Crop borders",
							checked = settings.pdfCropBorders == true,
							onClick = {
								onSettingsChange(settings.copy(pdfCropBorders = settings.pdfCropBorders != true))
							}
						)
						SliderItem(
							label = "Page gap",
							value = settings.pdfPageGapPercent ?: 0,
							valueRange = 0..48,
							valueString = "${settings.pdfPageGapPercent ?: 0}%",
							onChange = { pdfPageGapPercent ->
								onSettingsChange(settings.copy(pdfPageGapPercent = pdfPageGapPercent))
							}
						)
					}
					KomikkuSettingsTab.CustomFilter -> KomikkuSettingsDialogPage(
						title = "Custom filter"
					) {
						SliderItem(
							label = "Dim overlay",
							value = settings.dimOverlayPercent ?: 0,
							valueRange = 0..80,
							valueString = "${settings.dimOverlayPercent ?: 0}%",
							onChange = { dimOverlayPercent ->
								onSettingsChange(settings.copy(dimOverlayPercent = dimOverlayPercent))
							}
						)
						CheckboxItem(
							label = "Color filter",
							checked = settings.colorFilterEnabled == true,
							onClick = {
								onSettingsChange(settings.copy(colorFilterEnabled = settings.colorFilterEnabled != true))
							}
						)
						if (settings.colorFilterEnabled == true) {
							ReaderColorFilterChannel.entries.forEach { channel ->
								val channelValue = readerColorFilterChannelIntValue(settings.colorFilterArgb, channel)
								SliderItem(
									label = channel.label,
									value = channelValue,
									valueRange = 0..255,
									valueString = "$channelValue",
									onChange = { value ->
										onSettingsChange(
											settings.copy(
												colorFilterArgb = setReaderColorFilterChannel(
													settings.colorFilterArgb,
													channel,
													value
												)
											)
										)
									}
								)
							}
							SettingsSelectableChipRow(
								title = "Mode",
								options = ReaderSupportedColorFilterModes.map { mode ->
									mode to readerColorFilterModeShortLabel(mode)
								},
								selectedValue = settings.colorFilterMode ?: ReaderColorFilterModeSrcOver,
								onSelect = { colorFilterMode ->
									onSettingsChange(settings.copy(colorFilterMode = colorFilterMode))
								}
							)
						}
						CheckboxItem(
							label = "Grayscale",
							checked = settings.grayscaleEnabled == true,
							onClick = {
								onSettingsChange(settings.copy(grayscaleEnabled = settings.grayscaleEnabled != true))
							}
						)
						CheckboxItem(
							label = "Inverted colors",
							checked = settings.invertedColors == true,
							onClick = {
								onSettingsChange(settings.copy(invertedColors = settings.invertedColors != true))
							}
						)
					}
				}
			}
		}
	}
}

private fun Modifier.komikkuVerticalScrollEdgeFade(settingsScrollState: ScrollState): Modifier =
	graphicsLayer {
		compositingStrategy = CompositingStrategy.Offscreen
	}.drawWithContent {
		drawContent()
		if (settingsScrollState.maxValue <= 0) return@drawWithContent
		val fadeHeight = 28.dp.toPx().coerceAtMost(size.height / 3f)
		if (fadeHeight <= 0f) return@drawWithContent
		if (settingsScrollState.value > 0) {
			drawRect(
				brush = Brush.verticalGradient(
					0f to Color.Transparent,
					1f to Color.Black,
					startY = 0f,
					endY = fadeHeight
				),
				blendMode = BlendMode.DstIn
			)
		}
		if (settingsScrollState.value < settingsScrollState.maxValue) {
			drawRect(
				brush = Brush.verticalGradient(
					0f to Color.Black,
					1f to Color.Transparent,
					startY = size.height - fadeHeight,
					endY = size.height
				),
				blendMode = BlendMode.DstIn
			)
		}
	}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KomikkuTabbedDialog(
	onDismissRequest: () -> Unit,
	tabs: List<KomikkuSettingsTab>,
	pagerState: PagerState,
	useCompactLabels: Boolean,
	dimAmount: Float,
	modifier: Modifier = Modifier,
	content: @Composable (Int) -> Unit
) {
	val scope = rememberCoroutineScope()

	KomikkuAdaptiveSheet(
		onDismissRequest = onDismissRequest,
		dimAmount = dimAmount,
		modifier = modifier
	) {
		Column {
			Row {
				KomikkuSettingsTabRow(
					tabs = tabs,
					selectedTab = pagerState.currentPage,
					useCompactLabels = useCompactLabels,
					modifier = Modifier.weight(1f),
					onSelectTab = { index ->
						scope.launch { pagerState.animateScrollToPage(index) }
					}
				)
			}
			HorizontalDivider()
			HorizontalPager(
				modifier = Modifier.animateContentSize(),
				state = pagerState,
				verticalAlignment = Alignment.Top
			) { page ->
				content(page)
			}
		}
	}
}

@Composable
private fun KomikkuSettingsTabRow(
	tabs: List<KomikkuSettingsTab>,
	selectedTab: Int,
	useCompactLabels: Boolean,
	modifier: Modifier = Modifier,
	onSelectTab: (Int) -> Unit
) {
	PrimaryTabRow(
		selectedTabIndex = selectedTab,
		containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
		divider = {},
		modifier = modifier
	) {
		tabs.forEachIndexed { index, tab ->
			Tab(
				selected = selectedTab == index,
				onClick = { onSelectTab(index) },
				text = {
					Text(
						text = if (useCompactLabels) tab.compactLabel else tab.label,
						style = MaterialTheme.typography.labelMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				},
				unselectedContentColor = MaterialTheme.colorScheme.onSurface
			)
		}
	}
}

@Composable
private fun KomikkuSettingsDialogPage(
	title: String,
	content: @Composable () -> Unit
) {
	Column {
		HeadingItem(title)
		content()
	}
}

@Composable
private fun SettingsSection(
	title: String,
	content: @Composable () -> Unit
) {
	Column {
		SettingsSectionHeading(title)
		content()
	}
}

@Composable
internal fun KomikkuSettingsDialogLine(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
	)
}

@Composable
private fun KomikkuSettingsReadingModeRow(
	settings: ReaderSettings,
	onSelect: (KomikkuReadingModeOption) -> Unit
) {
	val selectedOption = komikkuReadingModeOptionFor(settings)
	SettingsSelectableChipRow(
		title = "Reading mode",
		options = KomikkuReadingModeOptions.map { option -> option.label to option.label },
		selectedValue = selectedOption.label,
		onSelect = { selectedLabel ->
			KomikkuReadingModeOptions
				.firstOrNull { option -> option.label == selectedLabel }
				?.let(onSelect)
		}
	)
}

private fun komikkuReadingModeOptionFor(settings: ReaderSettings): KomikkuReadingModeOption {
	val flowMode = normalizedReaderFlowMode(settings.flowMode, settings.paged)
	val direction = normalizedReaderDirection(settings.direction)
	return when {
		flowMode == ReaderFlowPaged && direction == ReaderDirectionLtr ->
			KomikkuReadingModeOptions[1]
		flowMode == ReaderFlowPaged && direction == ReaderDirectionRtl ->
			KomikkuReadingModeOptions[2]
		flowMode == ReaderFlowPagedVertical ->
			KomikkuReadingModeOptions[3]
		flowMode == ReaderFlowScrolled ->
			KomikkuReadingModeOptions[4]
		flowMode == ReaderFlowScrolledGaps ->
			KomikkuReadingModeOptions[5]
		else -> KomikkuReadingModeOptions[0]
	}
}

private enum class ReaderColorFilterChannel(
	val label: String,
	val shift: Int,
	val mask: Int
) {
	Red("Red", 16, 0x00FF0000),
	Green("Green", 8, 0x0000FF00),
	Blue("Blue", 0, 0x000000FF),
	Alpha("Alpha", 24, -0x1000000)
}

private fun setReaderColorFilterChannel(
	argb: Int?,
	channel: ReaderColorFilterChannel,
	value: Int
): Int {
	val color = argb ?: 0
	val next = value.coerceIn(0, 255)
	return (color and channel.mask.inv()) or (next shl channel.shift)
}

private fun readerColorFilterChannelIntValue(
	argb: Int?,
	channel: ReaderColorFilterChannel
): Int =
	((argb ?: 0) ushr channel.shift) and 0xFF

private fun readerPercentAsDecimalString(value: Int): String {
	val whole = value / 100
	val fraction = (value % 100).toString().padStart(2, '0')
	return "$whole.$fraction"
}

private fun readerTenthsString(value: Double): String =
	((value * 10.0).roundToInt() / 10.0).toString()

@Composable
private fun SettingsSelectableChipRow(
	title: String,
	options: List<Pair<String, String>>,
	selectedValue: String,
	onSelect: (String) -> Unit
) {
	SettingsChipRow(title) {
		options.forEach { (value, label) ->
			FilterChip(
				selected = selectedValue == value,
				onClick = { onSelect(value) },
				label = {
					Text(
						text = label,
						style = MaterialTheme.typography.labelMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			)
		}
	}
}

@Composable
private fun HeadingItem(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleSmall,
		fontWeight = FontWeight.Bold,
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				horizontal = SettingsItemsPaddings.Horizontal,
				vertical = SettingsItemsPaddings.Vertical
			)
	)
}

@Composable
private fun SettingsSectionHeading(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelLarge,
		fontWeight = FontWeight.SemiBold,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				horizontal = SettingsItemsPaddings.Horizontal,
				vertical = SettingsItemsPaddings.SectionVertical
			)
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChipRow(
	label: String,
	content: @Composable FlowRowScope.() -> Unit
) {
	Column {
		HeadingItem(label)
		FlowRow(
			modifier = Modifier.padding(
				start = SettingsItemsPaddings.Horizontal,
				top = 0.dp,
				end = SettingsItemsPaddings.Horizontal,
				bottom = SettingsItemsPaddings.Vertical
			),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			content = content
		)
	}
}

@Composable
private fun CheckboxItem(label: String, checked: Boolean, onClick: () -> Unit) {
	BaseSettingsItem(
		label = label,
		widget = {
			Checkbox(
				checked = checked,
				onCheckedChange = null
			)
		},
		onClick = onClick
	)
}

@Composable
private fun BaseSettingsItem(
	label: String,
	widget: @Composable RowScope.() -> Unit,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(
				horizontal = SettingsItemsPaddings.Horizontal,
				vertical = SettingsItemsPaddings.Vertical
			),
		horizontalArrangement = Arrangement.spacedBy(24.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		widget()
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface
		)
	}
}

@Composable
private fun SliderItem(
	label: String,
	value: Int,
	valueRange: IntRange,
	steps: Int = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
	valueString: String = value.toString(),
	onChange: (Int) -> Unit
) {
	BaseSliderItem(
		value = value,
		valueRange = valueRange,
		title = label,
		valueString = valueString,
		onChange = onChange,
		steps = steps,
		titleStyle = MaterialTheme.typography.bodyMedium,
		pillColor = MaterialTheme.colorScheme.surfaceContainerHigh,
		modifier = Modifier.padding(
			horizontal = SettingsItemsPaddings.Horizontal,
			vertical = SettingsItemsPaddings.SliderVertical
		)
	)
}

@Composable
private fun BaseSliderItem(
	value: Int,
	valueRange: IntRange,
	title: String,
	onChange: (Int) -> Unit,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	steps: Int = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
	valueString: String = value.toString(),
	titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
	subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
	pillColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
	val haptic = LocalHapticFeedback.current
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.then(modifier),
		verticalArrangement = Arrangement.spacedBy(0.dp)
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					style = titleStyle,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				if (subtitle != null) {
					Text(
						text = subtitle,
						style = subtitleStyle,
						color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
					)
				}
			}
			Pill(
				text = valueString,
				style = MaterialTheme.typography.bodyMedium,
				color = pillColor
			)
		}
		KomikkuIntegerSlider(
			value = value.coerceIn(valueRange),
			onValueChange = {
				if (it == value) return@KomikkuIntegerSlider
				onChange(it)
				haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
			},
			valueRange = valueRange,
			steps = steps
		)
	}
}

@Composable
private fun Pill(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
	contentColor: Color = MaterialTheme.colorScheme.onSurface,
	style: TextStyle = LocalTextStyle.current
) {
	Surface(
		modifier = modifier.padding(start = 4.dp),
		shape = MaterialTheme.shapes.extraLarge,
		color = color,
		contentColor = contentColor
	) {
		Box(
			modifier = Modifier.padding(6.dp, 1.dp),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = text,
				style = style,
				maxLines = 1
			)
		}
	}
}
