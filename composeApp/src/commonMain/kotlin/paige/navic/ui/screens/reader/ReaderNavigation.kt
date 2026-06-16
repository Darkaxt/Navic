package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneDisabled
import paige.navic.reader.ReaderTapZoneEdge
import paige.navic.reader.ReaderTapZoneInvertBoth
import paige.navic.reader.ReaderTapZoneInvertHorizontal
import paige.navic.reader.ReaderTapZoneInvertVertical
import paige.navic.reader.ReaderTapZoneKindle
import paige.navic.reader.ReaderTapZoneLShaped
import paige.navic.reader.ReaderTapZoneRightLeft
import paige.navic.reader.normalizedReaderTapZone
import paige.navic.reader.normalizedReaderTapZoneInvertMode
import paige.navic.reader.readerDefaultTapZoneMode

internal fun komikkuNavigatorForReaderSettings(settings: ReaderSettings): KomikkuReaderNavigator {
	val smallerTapZone = settings.smallerTapZone == true
	val tapZone = normalizedReaderTapZone(settings.tapZone).let { normalized ->
		if (normalized == ReaderTapZoneDefault) {
			readerDefaultTapZoneMode(settings.flowMode)
		} else {
			normalized
		}
	}
	val navigation = when (tapZone) {
		ReaderTapZoneLShaped -> KomikkuLNavigation(smallerTapZone)
		ReaderTapZoneKindle -> KomikkuKindlishNavigation(smallerTapZone)
		ReaderTapZoneEdge -> KomikkuEdgeNavigation(smallerTapZone)
		ReaderTapZoneRightLeft -> KomikkuRightAndLeftNavigation(smallerTapZone)
		ReaderTapZoneDisabled -> KomikkuDisabledNavigation(smallerTapZone)
		else -> KomikkuRightAndLeftNavigation(smallerTapZone)
	}
	navigation.invertMode = komikkuTappingInvertMode(settings.tapZoneInvertMode)
	return KomikkuReaderNavigator(navigation)
}

internal fun komikkuTappingInvertMode(tapZoneInvertMode: String?): KomikkuTappingInvertMode =
	when (normalizedReaderTapZoneInvertMode(tapZoneInvertMode)) {
		ReaderTapZoneInvertHorizontal -> KomikkuTappingInvertMode.HORIZONTAL
		ReaderTapZoneInvertVertical -> KomikkuTappingInvertMode.VERTICAL
		ReaderTapZoneInvertBoth -> KomikkuTappingInvertMode.BOTH
		else -> KomikkuTappingInvertMode.NONE
	}
