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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import paige.navic.reader.DefaultReaderFontSizePercent
import paige.navic.reader.DefaultReaderLineHeight
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.MaxReaderWhispersyncHighlightLeadMs
import paige.navic.reader.MinReaderWhispersyncHighlightLeadMs
import paige.navic.reader.ReaderColorFilterModeSrcOver
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderDirectionLtr
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderListeningSettings
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
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
import paige.navic.reader.ReaderWhispersyncHighlightLoading
import paige.navic.reader.ReaderWhispersyncHighlightStyle
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
import paige.navic.reader.normalizedReaderWhispersyncHighlightLeadMs
import paige.navic.reader.defaultReaderListeningSettings
import paige.navic.reader.readerColorFilterModeShortLabel
import paige.navic.reader.readerDirectionShortLabel
import paige.navic.reader.readerFontFamilyShortLabel
import paige.navic.reader.readerFontSourceShortLabel
import paige.navic.reader.readerNavBarTypeShortLabel
import paige.navic.reader.readerOrientationShortLabel
import paige.navic.reader.readerPdfFitShortLabel
import paige.navic.reader.readerSettingsScopeLabel
import paige.navic.reader.readerThemeShortLabel
import paige.navic.reader.readerReadaloudPlaybackSpeedLabel

internal val TabbedDialogPaddingsVertical = 8.dp

private object SettingsItemsPaddings {
	val Horizontal = 24.dp
	val Vertical = 10.dp
	val SectionVertical = 6.dp
	val SliderVertical = 6.dp
}

internal enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label) {
	Reading("Reading mode", "Reading"),
	Listening("Listening mode", "Listening"),
	General("General"),
	PdfImage("PDF/Image", "PDF"),
	CustomFilter("Custom filter", "Filter")
}

internal data class KomikkuReadingModeOption(
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

internal val KomikkuTapZoneOptions = listOf(
	ReaderTapZoneDefault to "Default",
	ReaderTapZoneLShaped to "L shaped",
	ReaderTapZoneKindle to "Kindle-ish",
	ReaderTapZoneEdge to "Edge",
	ReaderTapZoneRightLeft to "Right and Left",
	ReaderTapZoneDisabled to "Disabled"
)

internal val KomikkuTapZoneInvertOptions = listOf(
	ReaderTapZoneInvertNone to "None",
	ReaderTapZoneInvertHorizontal to "Horizontal",
	ReaderTapZoneInvertVertical to "Vertical",
	ReaderTapZoneInvertBoth to "Both"
)

internal val ReaderWhispersyncSpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

internal data class ReaderWhispersyncHighlightColorOption(
	val label: String,
	val argb: Int
)

internal val ReaderWhispersyncHighlightColorOptions = listOf(
	ReaderWhispersyncHighlightColorOption("Amber", 0x66F6C343),
	ReaderWhispersyncHighlightColorOption("Yellow", 0x66FDE047),
	ReaderWhispersyncHighlightColorOption("Green", 0x665BE49B),
	ReaderWhispersyncHighlightColorOption("Blue", 0x6642A5F5),
	ReaderWhispersyncHighlightColorOption("Pink", 0x66F472B6)
)

internal fun komikkuSettingsTabs(
	publicationFormat: ReaderPublicationFormat,
	whispersyncCapable: Boolean
): List<KomikkuSettingsTab> =
	buildList {
		add(KomikkuSettingsTab.Reading)
		if (whispersyncCapable) add(KomikkuSettingsTab.Listening)
		add(KomikkuSettingsTab.General)
		if (publicationFormat == ReaderPublicationFormat.Pdf) add(KomikkuSettingsTab.PdfImage)
		add(KomikkuSettingsTab.CustomFilter)
	}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomikkuReaderSettingsDialog(
	settings: ReaderSettings,
	initialTab: Int,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	whispersyncCapable: Boolean = false,
	listeningSettings: ReaderListeningSettings = defaultReaderListeningSettings(),
	readaloudPlaybackState: ReaderReadaloudPlaybackUiState = ReaderReadaloudPlaybackUiState(),
	onSettingsChange: (ReaderSettings) -> Unit,
	onListeningSettingsChange: (ReaderListeningSettings) -> Unit = {},
	onWhispersyncPlaybackCommand: (ReaderReadaloudPlaybackCommand) -> Unit = {},
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onShowMenus: () -> Unit,
	onHideMenus: () -> Unit,
	onDismissRequest: () -> Unit
) {
	// Ported from Komikku ReaderSettingsDialog: tabbed overlay above content, never a docked panel.
	val tabs = komikkuSettingsTabs(publicationFormat, whispersyncCapable)
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
					KomikkuSettingsTab.Reading -> KomikkuReadingSettingsPage(
						settings = settings,
						settingsScope = settingsScope,
						hasBookSettings = hasBookSettings,
						onSettingsChange = onSettingsChange,
						onSettingsScopeChange = onSettingsScopeChange,
						onResetBookSettings = onResetBookSettings
					)
					KomikkuSettingsTab.Listening -> KomikkuListeningSettingsPage(
						listeningSettings = listeningSettings,
						onListeningSettingsChange = onListeningSettingsChange,
						onWhispersyncPlaybackCommand = onWhispersyncPlaybackCommand
					)
					KomikkuSettingsTab.General -> KomikkuGeneralSettingsPage(
						settings = settings,
						onSettingsChange = onSettingsChange
					)
					KomikkuSettingsTab.PdfImage -> KomikkuPdfImageSettingsPage(
						settings = settings,
						onSettingsChange = onSettingsChange
					)
					KomikkuSettingsTab.CustomFilter -> KomikkuCustomFilterSettingsPage(
						settings = settings,
						onSettingsChange = onSettingsChange
					)
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
internal fun KomikkuSettingsDialogPage(
	title: String,
	content: @Composable () -> Unit
) {
	Column {
		HeadingItem(title)
		content()
	}
}

@Composable
internal fun SettingsSection(
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
internal fun KomikkuSettingsReadingModeRow(
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

internal enum class ReaderColorFilterChannel(
	val label: String,
	val shift: Int,
	val mask: Int
) {
	Red("Red", 16, 0x00FF0000),
	Green("Green", 8, 0x0000FF00),
	Blue("Blue", 0, 0x000000FF),
	Alpha("Alpha", 24, -0x1000000)
}

internal fun setReaderColorFilterChannel(
	argb: Int?,
	channel: ReaderColorFilterChannel,
	value: Int
): Int {
	val color = argb ?: 0
	val next = value.coerceIn(0, 255)
	return (color and channel.mask.inv()) or (next shl channel.shift)
}

internal fun readerColorFilterChannelIntValue(
	argb: Int?,
	channel: ReaderColorFilterChannel
): Int =
	((argb ?: 0) ushr channel.shift) and 0xFF

internal fun readerPercentAsDecimalString(value: Int): String {
	val whole = value / 100
	val fraction = (value % 100).toString().padStart(2, '0')
	return "$whole.$fraction"
}

internal fun readerTenthsString(value: Double): String =
	((value * 10.0).roundToInt() / 10.0).toString()

@Composable
internal fun SettingsSelectableChipRow(
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
internal fun CheckboxItem(label: String, checked: Boolean, onClick: () -> Unit) {
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
internal fun SliderItem(
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
			modifier = Modifier.semantics {
				contentDescription = settingSliderContentDescription(title)
			},
			valueRange = valueRange,
			steps = steps
		)
	}
}

private fun settingSliderContentDescription(title: String): String = "Reader setting slider $title"

internal fun readerMillisecondsAsSecondsString(value: Int): String {
	val normalized = normalizedReaderWhispersyncHighlightLeadMs(value)
	val sign = if (normalized < 0) "-" else ""
	val absolute = kotlin.math.abs(normalized)
	val seconds = absolute / 1000
	val tenths = (absolute % 1000) / 100
	return if (tenths == 0) {
		"$sign$seconds s"
	} else {
		"$sign$seconds.$tenths s"
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
