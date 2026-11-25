package paige.navic.ui.screens.reader

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import paige.navic.reader.*

@Composable
internal fun KomikkuReadingSettingsPage(
	settings: ReaderSettings,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit
) {
	KomikkuSettingsDialogPage(title = "For this book") {
		SettingsSelectableChipRow(
			title = "Scope",
			options = ReaderSupportedSettingsScopes.map { scope ->
				scope.name to readerSettingsScopeLabel(scope)
			},
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
				onSettingsChange(
					settings.copy(
						flowMode = option.flowMode,
						paged = option.paged,
						direction = option.direction
					)
				)
			}
		)
		SettingsSelectableChipRow(
			title = "Direction",
			options = ReaderSupportedDirections.map { direction ->
				direction to readerDirectionShortLabel(direction)
			},
			selectedValue = normalizedReaderDirection(settings.direction),
			onSelect = { direction -> onSettingsChange(settings.copy(direction = direction)) }
		)
		SettingsSelectableChipRow(
			title = "Progress rail",
			options = ReaderSupportedNavBarTypes.map { navBarType ->
				navBarType to readerNavBarTypeShortLabel(navBarType)
			},
			selectedValue = normalizedReaderNavBarType(settings.navBarType),
			onSelect = { navBarType -> onSettingsChange(settings.copy(navBarType = navBarType)) }
		)
		// Darkaxt/Navic#79: None = instant pages, Basic = page curl, Moderate = curl + living plates.
		SettingsSelectableChipRow(
			title = "Animations",
			options = ReaderSupportedAnimationLevels.map { level ->
				level to readerAnimationLevelShortLabel(level)
			},
			selectedValue = readerEffectiveAnimationLevel(settings.dragAnimationMode, settings.animationLevel),
			onSelect = { level -> onSettingsChange(settings.withReaderAnimationLevel(level)) }
		)
		SettingsSelectableChipRow(
			title = "Page cache scope",
			options = ReaderPageCacheScope.entries.map { scope ->
				scope.persistedValue to readerPageCacheScopeShortLabel(scope.persistedValue)
			},
			selectedValue = normalizeReaderPageCacheScope(settings.pageCacheScope).persistedValue,
			onSelect = { scope -> onSettingsChange(settings.copy(pageCacheScope = scope)) }
		)
		SettingsSelectableChipRow(
			title = "Page cache layouts",
			options = ReaderPageCacheLayouts.entries.map { layouts ->
				layouts.persistedValue to readerPageCacheLayoutsShortLabel(layouts.persistedValue)
			},
			selectedValue = normalizeReaderPageCacheLayouts(settings.pageCacheLayouts).persistedValue,
			onSelect = { layouts -> onSettingsChange(settings.copy(pageCacheLayouts = layouts)) }
		)
		SettingsSelectableChipRow(
			title = "Tap zones",
			options = KomikkuTapZoneOptions,
			selectedValue = normalizedReaderTapZone(settings.tapZone),
			onSelect = { tapZone -> onSettingsChange(settings.copy(tapZone = tapZone)) }
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
}

@Composable
internal fun KomikkuListeningSettingsPage(
	listeningSettings: ReaderListeningSettings,
	onListeningSettingsChange: (ReaderListeningSettings) -> Unit,
	onWhispersyncPlaybackCommand: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	KomikkuSettingsDialogPage(title = "Listening mode") {
		CheckboxItem(
			label = "Whispersync",
			checked = listeningSettings.listeningEnabled,
			onClick = {
				val nextEnabled = !listeningSettings.listeningEnabled
				onListeningSettingsChange(listeningSettings.copy(listeningEnabled = nextEnabled))
				onWhispersyncPlaybackCommand(
					if (nextEnabled) ReaderReadaloudPlaybackCommand.Play
					else ReaderReadaloudPlaybackCommand.StopAndReset
				)
			}
		)
		SettingsSelectableChipRow(
			title = "Playback speed",
			options = ReaderWhispersyncSpeedOptions.map { speed ->
				speed.toString() to readerReadaloudPlaybackSpeedLabel(speed)
			},
			selectedValue = listeningSettings.playbackSpeed.toString(),
			onSelect = { selected ->
				val speed = selected.toFloatOrNull() ?: return@SettingsSelectableChipRow
				onListeningSettingsChange(listeningSettings.copy(playbackSpeed = speed))
				onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.SetSpeed(speed))
			}
		)
		SliderItem(
			label = "Whispersync lead",
			value = normalizedReaderWhispersyncHighlightLeadMs(listeningSettings.highlightLeadMs),
			valueRange = MinReaderWhispersyncHighlightLeadMs..MaxReaderWhispersyncHighlightLeadMs,
			steps = 5,
			valueString = readerMillisecondsAsSecondsString(
				normalizedReaderWhispersyncHighlightLeadMs(listeningSettings.highlightLeadMs)
			),
			onChange = { highlightLeadMs ->
				onListeningSettingsChange(listeningSettings.copy(highlightLeadMs = highlightLeadMs))
			}
		)
		SettingsSelectableChipRow(
			title = "Highlight color",
			options = ReaderWhispersyncHighlightColorOptions.map { option ->
				option.argb.toString() to option.label
			},
			selectedValue = listeningSettings.highlightColorArgb.toString(),
			onSelect = { selected ->
				selected.toIntOrNull()?.let { color ->
					onListeningSettingsChange(listeningSettings.copy(highlightColorArgb = color))
				}
			}
		)
		SettingsSelectableChipRow(
			title = "Highlight loading",
			options = listOf(
				ReaderWhispersyncHighlightLoading.CurrentCue.value to "Current cue",
				ReaderWhispersyncHighlightLoading.PersistentPlayedText.value to "Persistent played text"
			),
			selectedValue = listeningSettings.highlightLoading.value,
			onSelect = { loading ->
				ReaderWhispersyncHighlightLoading.entries
					.firstOrNull { it.value == loading }
					?.let { onListeningSettingsChange(listeningSettings.copy(highlightLoading = it)) }
			}
		)
		SettingsSelectableChipRow(
			title = "Highlight style",
			options = listOf(
				ReaderWhispersyncHighlightStyle.Selection.value to "Selection",
				ReaderWhispersyncHighlightStyle.Marker.value to "Marker"
			),
			selectedValue = listeningSettings.highlightStyle.value,
			onSelect = { style ->
				ReaderWhispersyncHighlightStyle.entries
					.firstOrNull { it.value == style }
					?.let { onListeningSettingsChange(listeningSettings.copy(highlightStyle = it)) }
			}
		)
		SettingsSelectableChipRow(
			title = "Page boundary",
			options = listOf(
				ReaderWhispersyncPageBoundaryBehavior.PauseAtVisiblePageEnd.value to "Pause at page end",
				ReaderWhispersyncPageBoundaryBehavior.TurnPageAutomatically.value to "Turn page"
			),
			selectedValue = listeningSettings.pageBoundaryBehavior.value,
			onSelect = { behavior ->
				ReaderWhispersyncPageBoundaryBehavior.entries
					.firstOrNull { it.value == behavior }
					?.let { onListeningSettingsChange(listeningSettings.copy(pageBoundaryBehavior = it)) }
			}
		)
		SettingsSelectableChipRow(
			title = "Long press",
			options = listOf("seek-audio-to-text" to "Seek audio to text"),
			selectedValue = listeningSettings.longPressBehavior.value,
			onSelect = {}
		)
	}
}
