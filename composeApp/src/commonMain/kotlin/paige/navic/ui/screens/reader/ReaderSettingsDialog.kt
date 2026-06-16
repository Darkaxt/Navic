package paige.navic.ui.screens.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderChromeState
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
	val chromeState = ReaderChromeState(settings = settings)

	LaunchedEffect(pagerState.currentPage) {
		if (tabs[pagerState.currentPage] == KomikkuSettingsTab.CustomFilter) {
			onHideMenus()
		} else {
			onShowMenus()
		}
	}

	BoxWithConstraints {
		val dialogWidthFraction = if (maxWidth < 720.dp) 0.96f else 0.72f
		KomikkuTabbedDialog(
			onDismissRequest = onDismissRequest,
			tabs = tabs,
			pagerState = pagerState,
			modifier = Modifier.heightIn(max = maxHeight * 0.75f),
			widthFraction = dialogWidthFraction,
			footer = {
				TextButton(
					onClick = onDismissRequest,
					modifier = Modifier.align(Alignment.End)
				) {
					Text("Close")
				}
			}
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
						KomikkuSettingsSwitchRow(
							title = "Smaller tap zones",
							checked = settings.smallerTapZone == true,
							onCheckedChange = { smallerTapZone ->
								onSettingsChange(settings.copy(smallerTapZone = smallerTapZone))
							}
						)
						KomikkuSettingsSwitchRow(
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
						KomikkuSettingsStepperRow(
							title = "Font size",
							value = "${settings.fontSizePercent ?: 100}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustFontSize(-8).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustFontSize(8).settings)
							}
						)
						KomikkuSettingsStepperRow(
							title = "Line height",
							value = "${settings.lineHeight ?: 1.55}",
							onDecrease = {
								onSettingsChange(chromeState.adjustLineHeight(-0.1).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustLineHeight(0.1).settings)
							}
						)
						KomikkuSettingsStepperRow(
							title = "Paragraph spacing",
							value = "${settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustParagraphSpacing(-25).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustParagraphSpacing(25).settings)
							}
						)
						KomikkuSettingsStepperRow(
							title = "Margins",
							value = "${settings.marginPercent ?: 0}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustMargin(-4).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustMargin(4).settings)
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
						KomikkuSettingsSwitchRow(
							title = "Fullscreen",
							checked = settings.fullscreen == true,
							onCheckedChange = { fullscreen ->
								onSettingsChange(settings.copy(fullscreen = fullscreen))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Keep screen on",
							checked = settings.keepScreenOn == true,
							onCheckedChange = { keepScreenOn ->
								onSettingsChange(settings.copy(keepScreenOn = keepScreenOn))
							}
						)
						KomikkuSettingsSwitchRow(
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
						KomikkuSettingsSwitchRow(
							title = "Crop borders",
							checked = settings.pdfCropBorders == true,
							onCheckedChange = { cropBorders ->
								onSettingsChange(settings.copy(pdfCropBorders = cropBorders))
							}
						)
						KomikkuSettingsStepperRow(
							title = "Page gap",
							value = "${settings.pdfPageGapPercent ?: 0}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustPdfPageGap(-4).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustPdfPageGap(4).settings)
							}
						)
					}
					KomikkuSettingsTab.CustomFilter -> KomikkuSettingsDialogPage(
						title = "Custom filter"
					) {
						KomikkuSettingsStepperRow(
							title = "Dim overlay",
							value = "${settings.dimOverlayPercent ?: 0}%",
							onDecrease = {
								onSettingsChange(chromeState.adjustDimOverlay(-10).settings)
							},
							onIncrease = {
								onSettingsChange(chromeState.adjustDimOverlay(10).settings)
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Color filter",
							checked = settings.colorFilterEnabled == true,
							onCheckedChange = { colorFilterEnabled ->
								onSettingsChange(settings.copy(colorFilterEnabled = colorFilterEnabled))
							}
						)
						if (settings.colorFilterEnabled == true) {
							ReaderColorFilterChannel.entries.forEach { channel ->
								KomikkuSettingsStepperRow(
									title = channel.label,
									value = readerColorFilterChannelValue(settings.colorFilterArgb, channel),
									onDecrease = {
										onSettingsChange(
											settings.copy(
												colorFilterArgb = updateReaderColorFilterChannel(
													settings.colorFilterArgb,
													channel,
													-16
												)
											)
										)
									},
									onIncrease = {
										onSettingsChange(
											settings.copy(
												colorFilterArgb = updateReaderColorFilterChannel(
													settings.colorFilterArgb,
													channel,
													16
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
						KomikkuSettingsSwitchRow(
							title = "Grayscale",
							checked = settings.grayscaleEnabled == true,
							onCheckedChange = { grayscaleEnabled ->
								onSettingsChange(settings.copy(grayscaleEnabled = grayscaleEnabled))
							}
						)
						KomikkuSettingsSwitchRow(
							title = "Inverted colors",
							checked = settings.invertedColors == true,
							onCheckedChange = { invertedColors ->
								onSettingsChange(settings.copy(invertedColors = invertedColors))
							}
						)
						KomikkuSettingsSwitchRow(
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
	widthFraction: Float,
	footer: @Composable ColumnScope.() -> Unit = {},
	content: @Composable (Int) -> Unit
) {
	val scope = rememberCoroutineScope()

	BasicAlertDialog(
		onDismissRequest = onDismissRequest,
		properties = DialogProperties(usePlatformDefaultWidth = false)
	) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = modifier.fillMaxWidth(widthFraction)
		) {
			Column(
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				KomikkuSettingsTabRow(
					tabs = tabs,
					selectedTab = pagerState.currentPage,
					onSelectTab = { index ->
						scope.launch { pagerState.animateScrollToPage(index) }
					}
				)
				HorizontalPager(
					modifier = Modifier.weight(1f, fill = false),
					state = pagerState,
					verticalAlignment = Alignment.Top
				) { page ->
					content(page)
				}
				footer()
			}
		}
	}
}

@Composable
private fun KomikkuSettingsTabRow(
	tabs: List<KomikkuSettingsTab>,
	selectedTab: Int,
	onSelectTab: (Int) -> Unit
) {
	PrimaryTabRow(
		selectedTabIndex = selectedTab,
		containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
		divider = {},
		modifier = Modifier.fillMaxWidth()
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
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.Bold
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

private fun updateReaderColorFilterChannel(
	argb: Int?,
	channel: ReaderColorFilterChannel,
	delta: Int
): Int {
	val color = argb ?: 0
	val current = ((color ushr channel.shift) and 0xFF)
	val next = (current + delta).coerceIn(0, 255)
	return (color and channel.mask.inv()) or (next shl channel.shift)
}

private fun readerColorFilterChannelValue(
	argb: Int?,
	channel: ReaderColorFilterChannel
): String =
	"${(((argb ?: 0) ushr channel.shift) and 0xFF)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KomikkuSettingsChipRow(
	title: String,
	options: List<Pair<String, String>>,
	selectedValue: String,
	onSelect: (String) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurface
		)
		FlowRow(
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
private fun KomikkuSettingsSwitchRow(
	title: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(18.dp))
			.clickable { onCheckedChange(!checked) }
			.padding(horizontal = 10.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface
		)
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange
		)
	}
}

@Composable
private fun KomikkuSettingsStepperRow(
	title: String,
	value: String,
	onDecrease: () -> Unit,
	onIncrease: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 10.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		IconButton(onClick = onDecrease) {
			Text("-", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
		}
		Text(
			text = value,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
			maxLines = 1
		)
		IconButton(onClick = onIncrease) {
			Text("+", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
		}
	}
}
