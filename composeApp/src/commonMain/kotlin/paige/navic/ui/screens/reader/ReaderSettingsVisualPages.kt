package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import paige.navic.reader.*

@Composable
internal fun KomikkuPdfImageSettingsPage(
	settings: ReaderSettings,
	onSettingsChange: (ReaderSettings) -> Unit
) {
	KomikkuSettingsDialogPage(title = "PDF/Image") {
		SettingsSelectableChipRow(
			title = "Page fit",
			options = ReaderSupportedPdfFitModes.map { fitMode ->
				fitMode to readerPdfFitShortLabel(fitMode)
			},
			selectedValue = normalizedReaderPdfFitMode(settings.pdfFitMode),
			onSelect = { fitMode -> onSettingsChange(settings.copy(pdfFitMode = fitMode)) }
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
}

@Composable
internal fun KomikkuCustomFilterSettingsPage(
	settings: ReaderSettings,
	onSettingsChange: (ReaderSettings) -> Unit
) {
	KomikkuSettingsDialogPage(title = "Custom filter") {
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
