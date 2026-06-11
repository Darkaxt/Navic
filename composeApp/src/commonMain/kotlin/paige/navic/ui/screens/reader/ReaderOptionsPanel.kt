package paige.navic.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.filled.Settings
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.filled.SkipPrevious
import paige.navic.icons.filled.Star
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.DataTable
import paige.navic.icons.outlined.Search
import paige.navic.icons.outlined.Star
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderAnnotation
import paige.navic.reader.ReaderAnnotationState
import paige.navic.reader.ReaderBookmark
import paige.navic.reader.ReaderBookmarkState
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderChromeState
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderOptionsTab
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderProgressSaveGate
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadingProgressState
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderSupportedDirections
import paige.navic.reader.ReaderSupportedFlowModes
import paige.navic.reader.ReaderSupportedFontFamilies
import paige.navic.reader.ReaderSupportedFontSources
import paige.navic.reader.ReaderSupportedOrientations
import paige.navic.reader.ReaderSupportedTapZones
import paige.navic.reader.ReaderSupportedThemes
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.bestReaderStartLocator
import paige.navic.reader.decodeReaderAnnotations
import paige.navic.reader.decodeReaderBookmarks
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.reader.encodeReaderAnnotations
import paige.navic.reader.encodeReaderBookmarks
import paige.navic.reader.encodeReaderReadingProgress
import paige.navic.reader.normalizedReaderOptionsTab
import paige.navic.reader.normalizedReaderSettings
import paige.navic.reader.readerFlowShortLabel
import paige.navic.reader.readerFontFamilyShortLabel
import paige.navic.reader.readerFontSourceShortLabel
import paige.navic.reader.readerOptionsTabLabel
import paige.navic.reader.readerOptionsTabs
import paige.navic.reader.readerOrientationShortLabel
import paige.navic.reader.readerReadaloudPlaybackSpeedLabel
import paige.navic.reader.readerThemeShortLabel
import paige.navic.reader.readerBookmarkFromLocator
import paige.navic.reader.readerDefaultSettings
import paige.navic.reader.readerDirectionShortLabel
import paige.navic.reader.readerReadaloudControlsVisible
import paige.navic.reader.readerTapZoneShortLabel
import paige.navic.reader.setReaderDefaultSettings
import paige.navic.reader.toBinderyReadingProgress
import paige.navic.reader.toReaderStartLocatorForReader
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderOptionsPanel(
	state: ReaderChromeState,
	showReadaloudControls: Boolean,
	selectedTab: ReaderOptionsTab,
	onTabSelected: (ReaderOptionsTab) -> Unit,
	onSettingsChange: (ReaderChromeState) -> Unit,
	onReadaloudToggle: () -> Unit,
	onReadaloudSpeedChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	onReadaloudSyncChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	modifier: Modifier = Modifier
) {
	val tabs = readerOptionsTabs(showReadaloudControls)
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			tabs.forEach { tab ->
				ReaderOptionsTabChip(
					label = readerOptionsTabLabel(tab),
					selected = selectedTab == tab,
					onClick = { onTabSelected(tab) },
				)
			}
		}

		when (selectedTab) {
			ReaderOptionsTab.Reading -> ReaderReadingOptions(
				state = state,
				onSettingsChange = onSettingsChange
			)
			ReaderOptionsTab.General -> ReaderGeneralOptions(
				state = state,
				onSettingsChange = onSettingsChange
			)
			ReaderOptionsTab.Media -> ReaderMediaOptions(
				state = state,
				onReadaloudToggle = onReadaloudToggle,
				onReadaloudSpeedChange = onReadaloudSpeedChange,
				onReadaloudSyncChange = onReadaloudSyncChange
			)
		}
	}
}

@Composable
private fun ReaderOptionsTabChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		shape = MaterialTheme.shapes.large,
		color = if (selected) {
			MaterialTheme.colorScheme.primaryContainer
		} else {
			MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
		},
		contentColor = if (selected) {
			MaterialTheme.colorScheme.onPrimaryContainer
		} else {
			MaterialTheme.colorScheme.onSurfaceVariant
		}
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
		)
	}
}

@Composable
private fun ReaderReadingOptions(
	state: ReaderChromeState,
	onSettingsChange: (ReaderChromeState) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		ReaderSettingsChipRow("Reading mode") {
			ReaderSupportedFlowModes.forEach { flowMode ->
				ReaderOptionChip(
					label = readerFlowShortLabel(flowMode, flowMode.readerFlowModePaged()),
					selected = state.settings.flowMode == flowMode,
					onClick = {
						onSettingsChange(
							state.copy(
								settings = state.settings.copy(
									flowMode = flowMode,
									paged = flowMode.readerFlowModePaged()
								)
							)
						)
					}
				)
			}
		}
		ReaderSettingsChipRow("Direction") {
			ReaderSupportedDirections.forEach { direction ->
				ReaderOptionChip(
					label = readerDirectionShortLabel(direction),
					selected = state.settings.direction == direction,
					onClick = { onSettingsChange(state.copy(settings = state.settings.copy(direction = direction))) }
				)
			}
		}
		ReaderSettingsChipRow("Font") {
			ReaderSupportedFontFamilies.forEach { fontFamily ->
				ReaderOptionChip(
					label = readerFontFamilyShortLabel(fontFamily),
					selected = state.settings.fontFamily == fontFamily,
					onClick = { onSettingsChange(state.copy(settings = state.settings.copy(fontFamily = fontFamily))) }
				)
			}
		}
		ReaderSettingsChipRow("Font source") {
			ReaderSupportedFontSources.forEach { fontSource ->
				ReaderOptionChip(
					label = readerFontSourceShortLabel(fontSource),
					selected = state.settings.fontSource == fontSource,
					onClick = { onSettingsChange(state.copy(settings = state.settings.copy(fontSource = fontSource))) }
				)
			}
		}
		ReaderControlStepper(
			label = "Font size",
			value = "${state.settings.fontSizePercent ?: 100}%",
			onDecrease = { onSettingsChange(state.adjustFontSize(-8)) },
			onIncrease = { onSettingsChange(state.adjustFontSize(8)) }
		)
		ReaderControlStepper(
			label = "Line height",
			value = "${state.settings.lineHeight ?: 1.55}",
			onDecrease = { onSettingsChange(state.adjustLineHeight(-0.1)) },
			onIncrease = { onSettingsChange(state.adjustLineHeight(0.1)) }
		)
		ReaderControlStepper(
			label = "Paragraph spacing",
			value = "${state.settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent}%",
			onDecrease = { onSettingsChange(state.adjustParagraphSpacing(-25)) },
			onIncrease = { onSettingsChange(state.adjustParagraphSpacing(25)) }
		)
		ReaderControlStepper(
			label = "Margins",
			value = "${state.settings.marginPercent ?: 0}%",
			onDecrease = { onSettingsChange(state.adjustMargin(-4)) },
			onIncrease = { onSettingsChange(state.adjustMargin(4)) }
		)
	}
}

@Composable
private fun ReaderGeneralOptions(
	state: ReaderChromeState,
	onSettingsChange: (ReaderChromeState) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		ReaderSettingsChipRow("Theme") {
			ReaderSupportedThemes.forEach { theme ->
				ReaderOptionChip(
					label = readerThemeShortLabel(theme),
					selected = state.settings.theme == theme,
					onClick = { onSettingsChange(state.copy(settings = state.settings.copy(theme = theme))) }
				)
			}
		}
		ReaderSettingsChipRow("Rotation") {
			ReaderSupportedOrientations.forEach { orientation ->
				ReaderOptionChip(
					label = readerOrientationShortLabel(orientation),
					selected = state.settings.orientation == orientation,
					onClick = { onSettingsChange(state.copy(settings = state.settings.copy(orientation = orientation))) }
				)
			}
		}
		ReaderSettingsChipRow("Tap zones") {
			ReaderSupportedTapZones.forEach { tapZone ->
				ReaderOptionChip(
					label = readerTapZoneShortLabel(tapZone),
					selected = state.settings.tapZone == tapZone,
					onClick = { onSettingsChange(state.copy(settings = state.settings.copy(tapZone = tapZone))) }
				)
			}
		}
		ReaderSettingsChipRow("Display") {
			ReaderToggleChip(
				label = "Fullscreen",
				checked = state.settings.fullscreen == true,
				onClick = { onSettingsChange(state.toggleFullscreen()) }
			)
			ReaderToggleChip(
				label = "Smaller tap zones",
				checked = state.settings.smallerTapZone == true,
				onClick = { onSettingsChange(state.toggleSmallerTapZone()) }
			)
			ReaderToggleChip(
				label = "Show tap zones",
				checked = state.settings.showTapZones == true,
				onClick = { onSettingsChange(state.toggleShowTapZones()) }
			)
			ReaderToggleChip(
				label = "Publisher styles",
				checked = state.settings.publisherStyles == true,
				onClick = { onSettingsChange(state.togglePublisherStyles()) }
			)
		}
		ReaderSettingsChipRow("Controls") {
			ReaderToggleChip(
				label = "Keep screen on",
				checked = state.settings.keepScreenOn == true,
				onClick = { onSettingsChange(state.toggleKeepScreenOn()) }
			)
			ReaderToggleChip(
				label = "Volume keys",
				checked = state.settings.volumeKeyPageTurns == true,
				onClick = { onSettingsChange(state.toggleVolumeKeyPageTurns()) }
			)
		}
		ReaderControlStepper(
			label = "Dim overlay",
			value = "${state.settings.dimOverlayPercent ?: 0}%",
			onDecrease = { onSettingsChange(state.adjustDimOverlay(-10)) },
			onIncrease = { onSettingsChange(state.adjustDimOverlay(10)) }
		)
	}
}

@Composable
private fun ReaderMediaOptions(
	state: ReaderChromeState,
	onReadaloudToggle: () -> Unit,
	onReadaloudSpeedChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	onReadaloudSyncChange: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	val playback = state.readaloudPlayback
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			ReaderReadaloudButton(
				state = playback,
				onClick = onReadaloudToggle
			)
			Column(Modifier.weight(1f)) {
				Text(
					text = if (playback.isPlaying) "Readaloud playing" else "Readaloud paused",
					style = MaterialTheme.typography.labelLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = readerPlaybackPositionLabel(playback),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
		ReaderValueRow(
			label = "Audio label",
			value = playback.activeAudioLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "No active label"
		)
		ReaderToggleRow(
			label = "Sync highlight",
			checked = playback.syncEnabled,
			onCheckedChange = {
				playback.toggleSyncCommand()?.let(onReadaloudSyncChange)
			}
		)
		ReaderReadaloudMetadataRows(playback.activeAudioMetadata)
		ReaderControlStepper(
			label = "Speed",
			value = readerReadaloudPlaybackSpeedLabel(playback.playbackSpeed),
			onDecrease = {
				playback.adjustSpeedCommand(-0.25f)?.let(onReadaloudSpeedChange)
			},
			onIncrease = {
				playback.adjustSpeedCommand(0.25f)?.let(onReadaloudSpeedChange)
			}
		)
	}
}

@Composable
private fun ReaderReadaloudMetadataRows(metadata: paige.navic.reader.ReadaloudPlaybackMetadataLabels?) {
	metadata ?: return
	ReaderOptionalValueRow("Chapter", metadata.chapterLabel)
	ReaderOptionalValueRow("Section", metadata.sectionLabel)
	ReaderOptionalValueRow("Narrator", metadata.narratorLabel)
	ReaderOptionalValueRow("Quality", metadata.qualityLabel)
	ReaderOptionalValueRow("Source", metadata.sourceProviderLabel)
	ReaderOptionalValueRow("Release", metadata.sourceReleaseLabel)
	ReaderOptionalValueRow("Source URL", metadata.sourceUrlLabel)
	ReaderOptionalValueRow("Format", metadata.formatLabel)
}

@Composable
private fun ReaderOptionalValueRow(
	label: String,
	value: String?
) {
	val displayValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
	ReaderValueRow(
		label = label,
		value = displayValue
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsChipRow(
	label: String,
	content: @Composable () -> Unit
) {
	Column(
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.fillMaxWidth()
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.fillMaxWidth()
		) {
			content()
		}
	}
}

@Composable
private fun ReaderOptionChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit
) {
	FilterChip(
		selected = selected,
		onClick = onClick,
		label = {
			Text(
				text = label,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	)
}

@Composable
private fun ReaderToggleChip(
	label: String,
	checked: Boolean,
	onClick: () -> Unit
) {
	FilterChip(
		selected = checked,
		onClick = onClick,
		label = {
			Text(
				text = label,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	)
}

private fun String.readerFlowModePaged(): Boolean =
	this != ReaderFlowScrolled && this != ReaderFlowScrolledGaps

@Composable
private fun ReaderControlStepper(
	label: String,
	value: String,
	onDecrease: () -> Unit,
	onIncrease: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		IconButton(onClick = onDecrease) {
			Text(
				text = "-",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
		}
		Text(
			text = value,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1
		)
		IconButton(onClick = onIncrease) {
			Text(
				text = "+",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
		}
	}
}

@Composable
private fun ReaderCycleRow(
	label: String,
	value: String,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		color = MaterialTheme.colorScheme.surface,
		modifier = Modifier.fillMaxWidth()
	) {
		ReaderValueRow(
			label = label,
			value = value,
			modifier = Modifier.padding(vertical = 8.dp)
		)
	}
}

@Composable
private fun ReaderToggleRow(
	label: String,
	checked: Boolean,
	onCheckedChange: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		ReaderToggleChip(
			label = if (checked) "On" else "Off",
			checked = checked,
			onClick = onCheckedChange
		)
	}
}

@Composable
private fun ReaderValueRow(
	label: String,
	value: String,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Text(
			text = value,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

private fun readerPlaybackPositionLabel(state: ReaderReadaloudPlaybackUiState): String {
	val duration = state.durationMs
	val position = readerPlaybackTimeLabel(state.positionMs)
	return if (duration == null || duration <= 0L) {
		position
	} else {
		"$position / ${readerPlaybackTimeLabel(duration)}"
	}
}

private fun readerPlaybackTimeLabel(positionMs: Long): String {
	val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
	val minutes = totalSeconds / 60L
	val seconds = totalSeconds % 60L
	return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
internal fun ReaderReadaloudButton(
	state: ReaderReadaloudPlaybackUiState,
	onClick: () -> Unit
) {
	IconButton(
		onClick = onClick,
		enabled = state.isAvailable,
		modifier = Modifier.size(48.dp)
	) {
		Icon(
			imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.Play,
			contentDescription = null,
			modifier = Modifier.size(28.dp),
			tint = if (state.isAvailable) {
				MaterialTheme.colorScheme.primary
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			}
		)
	}
}
