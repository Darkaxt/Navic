package paige.navic.ui.screens.reader

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
private val SettingsItemsPaddingsHorizontal = 24.dp
private val SettingsItemsPaddingsVertical = 10.dp

private enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label) {
	Reading("Reading mode", "Reading"),
	General("General"),
	PdfImage("PDF/Image"),
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
		KomikkuTabbedDialog(
			onDismissRequest = {
				onDismissRequest()
				onShowMenus()
			},
			tabs = tabs,
			pagerState = pagerState,
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
						KomikkuSettingsChipRow(
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
						KomikkuSettingsChipRow(
							title = "Direction",
							options = ReaderSupportedDirections.map { direction -> direction to readerDirectionShortLabel(direction) },
							selectedValue = normalizedReaderDirection(settings.direction),
							onSelect = { direction ->
								onSettingsChange(settings.copy(direction = direction))
							}
						)
						KomikkuSettingsChipRow(
							title = "Progress rail",
							options = ReaderSupportedNavBarTypes.map { navBarType ->
								navBarType to readerNavBarTypeShortLabel(navBarType)
							},
							selectedValue = normalizedReaderNavBarType(settings.navBarType),
							onSelect = { navBarType ->
								onSettingsChange(settings.copy(navBarType = navBarType))
							}
						)
						KomikkuSettingsChipRow(
							title = "Tap zones",
							options = KomikkuTapZoneOptions,
							selectedValue = normalizedReaderTapZone(settings.tapZone),
							onSelect = { tapZone ->
								onSettingsChange(settings.copy(tapZone = tapZone))
							}
						)
						if (normalizedReaderTapZone(settings.tapZone) != ReaderTapZoneDisabled) {
							KomikkuSettingsChipRow(
								title = "Tapping inversion",
								options = KomikkuTapZoneInvertOptions,
								selectedValue = normalizedReaderTapZoneInvertMode(settings.tapZoneInvertMode),
								onSelect = { tapZoneInvertMode ->
									onSettingsChange(settings.copy(tapZoneInvertMode = tapZoneInvertMode))
								}
							)
						}
						KomikkuSettingsCheckboxItem(
							title = "Smaller tap zones",
							checked = settings.smallerTapZone == true,
							onCheckedChange = { smallerTapZone ->
								onSettingsChange(settings.copy(smallerTapZone = smallerTapZone))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Show tap zones",
							checked = settings.showTapZones == true,
							onCheckedChange = { showTapZones ->
								onSettingsChange(settings.copy(showTapZones = showTapZones))
							}
						)
					}
					KomikkuSettingsTab.General -> KomikkuSettingsDialogPage(
						title = "General"
					) {
						KomikkuSettingsChipRow(
							title = "Font",
							options = ReaderSupportedFontFamilies.map { fontFamily ->
								fontFamily to readerFontFamilyShortLabel(fontFamily)
							},
							selectedValue = normalizedReaderFontFamily(settings.fontFamily),
							onSelect = { fontFamily ->
								onSettingsChange(settings.copy(fontFamily = fontFamily))
							}
						)
						KomikkuSettingsChipRow(
							title = "Font source",
							options = ReaderSupportedFontSources.map { fontSource ->
								fontSource to readerFontSourceShortLabel(fontSource)
							},
							selectedValue = normalizedReaderFontSource(settings.fontSource),
							onSelect = { fontSource ->
								onSettingsChange(settings.copy(fontSource = fontSource))
							}
						)
						KomikkuSettingsSliderItem(
							title = "Font size",
							value = settings.fontSizePercent ?: 100,
							valueRange = 80..180,
							valueString = "${settings.fontSizePercent ?: 100}%",
							onChange = { fontSizePercent ->
								onSettingsChange(settings.copy(fontSizePercent = fontSizePercent))
							}
						)
						val lineHeightPercent = ((settings.lineHeight ?: 1.55) * 100.0).roundToInt()
						KomikkuSettingsSliderItem(
							title = "Line height",
							value = lineHeightPercent,
							valueRange = 120..220,
							valueString = readerPercentAsDecimalString(lineHeightPercent),
							onChange = { nextLineHeightPercent ->
								onSettingsChange(settings.copy(lineHeight = nextLineHeightPercent / 100.0))
							}
						)
						KomikkuSettingsSliderItem(
							title = "Paragraph spacing",
							value = settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent,
							valueRange = 0..200,
							valueString = "${settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent}%",
							onChange = { paragraphSpacingPercent ->
								onSettingsChange(settings.copy(paragraphSpacingPercent = paragraphSpacingPercent))
							}
						)
						KomikkuSettingsSliderItem(
							title = "Margins",
							value = settings.marginPercent ?: 0,
							valueRange = 0..24,
							valueString = "${settings.marginPercent ?: 0}%",
							onChange = { marginPercent ->
								onSettingsChange(settings.copy(marginPercent = marginPercent))
							}
						)
						KomikkuSettingsChipRow(
							title = "Theme",
							options = ReaderSupportedThemes.map { theme -> theme to readerThemeShortLabel(theme) },
							selectedValue = normalizedReaderTheme(settings.theme),
							onSelect = { theme ->
								onSettingsChange(settings.copy(theme = theme))
							}
						)
						KomikkuSettingsChipRow(
							title = "Rotation",
							options = ReaderSupportedOrientations.map { orientation ->
								orientation to readerOrientationShortLabel(orientation)
							},
							selectedValue = normalizedReaderOrientation(settings.orientation),
							onSelect = { orientation ->
								onSettingsChange(settings.copy(orientation = orientation))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Fullscreen",
							checked = settings.fullscreen == true,
							onCheckedChange = { fullscreen ->
								onSettingsChange(settings.copy(fullscreen = fullscreen))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Keep screen on",
							checked = settings.keepScreenOn == true,
							onCheckedChange = { keepScreenOn ->
								onSettingsChange(settings.copy(keepScreenOn = keepScreenOn))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Volume keys",
							checked = settings.volumeKeyPageTurns == true,
							onCheckedChange = { volumeKeyPageTurns ->
								onSettingsChange(settings.copy(volumeKeyPageTurns = volumeKeyPageTurns))
							}
						)
					}
					KomikkuSettingsTab.PdfImage -> KomikkuSettingsDialogPage(
						title = "PDF/Image"
					) {
						KomikkuSettingsChipRow(
							title = "Page fit",
							options = ReaderSupportedPdfFitModes.map { fitMode ->
								fitMode to readerPdfFitShortLabel(fitMode)
							},
							selectedValue = normalizedReaderPdfFitMode(settings.pdfFitMode),
							onSelect = { fitMode ->
								onSettingsChange(settings.copy(pdfFitMode = fitMode))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Crop borders",
							checked = settings.pdfCropBorders == true,
							onCheckedChange = { cropBorders ->
								onSettingsChange(settings.copy(pdfCropBorders = cropBorders))
							}
						)
						KomikkuSettingsSliderItem(
							title = "Page gap",
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
						KomikkuSettingsSliderItem(
							title = "Dim overlay",
							value = settings.dimOverlayPercent ?: 0,
							valueRange = 0..80,
							valueString = "${settings.dimOverlayPercent ?: 0}%",
							onChange = { dimOverlayPercent ->
								onSettingsChange(settings.copy(dimOverlayPercent = dimOverlayPercent))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Color filter",
							checked = settings.colorFilterEnabled == true,
							onCheckedChange = { colorFilterEnabled ->
								onSettingsChange(settings.copy(colorFilterEnabled = colorFilterEnabled))
							}
						)
						if (settings.colorFilterEnabled == true) {
							ReaderColorFilterChannel.entries.forEach { channel ->
								val channelValue = readerColorFilterChannelIntValue(settings.colorFilterArgb, channel)
								KomikkuSettingsSliderItem(
									title = channel.label,
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
							KomikkuSettingsChipRow(
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
						KomikkuSettingsCheckboxItem(
							title = "Grayscale",
							checked = settings.grayscaleEnabled == true,
							onCheckedChange = { grayscaleEnabled ->
								onSettingsChange(settings.copy(grayscaleEnabled = grayscaleEnabled))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Inverted colors",
							checked = settings.invertedColors == true,
							onCheckedChange = { invertedColors ->
								onSettingsChange(settings.copy(invertedColors = invertedColors))
							}
						)
						KomikkuSettingsCheckboxItem(
							title = "Publisher styles",
							checked = settings.publisherStyles == true,
							onCheckedChange = { publisherStyles ->
								onSettingsChange(settings.copy(publisherStyles = publisherStyles))
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
	modifier: Modifier = Modifier,
	content: @Composable (Int) -> Unit
) {
	val scope = rememberCoroutineScope()

	KomikkuAdaptiveSheet(
		onDismissRequest = onDismissRequest,
		modifier = modifier
	) {
		Column {
			Row {
				KomikkuSettingsTabRow(
					tabs = tabs,
					selectedTab = pagerState.currentPage,
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
						text = tab.compactLabel,
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
		Text(
			text = title,
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.Bold,
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					horizontal = SettingsItemsPaddingsHorizontal,
					vertical = SettingsItemsPaddingsVertical
				)
		)
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
	KomikkuSettingsChipRow(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KomikkuSettingsChipRow(
	title: String,
	options: List<Pair<String, String>>,
	selectedValue: String,
	onSelect: (String) -> Unit
) {
	Column {
		Text(
			text = title,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					horizontal = SettingsItemsPaddingsHorizontal,
					vertical = SettingsItemsPaddingsVertical
				)
		)
		FlowRow(
			modifier = Modifier.padding(
				start = SettingsItemsPaddingsHorizontal,
				top = 0.dp,
				end = SettingsItemsPaddingsHorizontal,
				bottom = SettingsItemsPaddingsVertical
			),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp)
		) {
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
}

@Composable
private fun KomikkuSettingsCheckboxItem(
	title: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onCheckedChange(!checked) }
			.padding(horizontal = SettingsItemsPaddingsHorizontal, vertical = SettingsItemsPaddingsVertical),
		horizontalArrangement = Arrangement.spacedBy(24.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Checkbox(
			checked = checked,
			onCheckedChange = null
		)
		Text(
			text = title,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface
		)
	}
}

@Composable
private fun KomikkuSettingsSliderItem(
	title: String,
	value: Int,
	valueRange: IntRange,
	valueString: String = value.toString(),
	onChange: (Int) -> Unit
) {
	val haptic = LocalHapticFeedback.current
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = SettingsItemsPaddingsHorizontal, vertical = SettingsItemsPaddingsVertical),
		verticalArrangement = Arrangement.spacedBy(2.dp)
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			KomikkuSettingsValuePill(valueString)
		}
		Slider(
			value = value.coerceIn(valueRange).toFloat(),
			onValueChange = { nextValue ->
				val next = nextValue.roundToInt().coerceIn(valueRange)
				if (next != value) {
					onChange(next)
					haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
				}
			},
			valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
			steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0)
		)
	}
}

@Composable
private fun KomikkuSettingsValuePill(text: String) {
	Surface(
		shape = RoundedCornerShape(50),
		color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
		)
	}
}
