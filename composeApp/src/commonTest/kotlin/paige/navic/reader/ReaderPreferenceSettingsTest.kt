package paige.navic.reader

import com.russhwolf.settings.MapSettings
import paige.navic.domain.manager.PreferenceManager
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPreferenceSettingsTest {
	@Test
	fun readerDefaultSettingsRoundTripTapZonePreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerTapZone = ReaderTapZoneEdge
		preferences.readerSmallerTapZone = true

		assertEquals(ReaderTapZoneEdge, preferences.readerDefaultSettings().tapZone)
		assertEquals(true, preferences.readerDefaultSettings().smallerTapZone)

		preferences.setReaderDefaultSettings(
			ReaderSettings(
				tapZone = ReaderTapZoneDisabled,
				smallerTapZone = false
			)
		)

		assertEquals(ReaderTapZoneDisabled, preferences.readerTapZone)
		assertEquals(false, preferences.readerSmallerTapZone)
	}

	@Test
	fun readerDefaultSettingsRoundTripParagraphSpacingAndPublisherStyles() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerParagraphSpacingPercent = 75
		preferences.readerPublisherStylesEnabled = true

		val defaults = preferences.readerDefaultSettings()
		assertEquals(75, defaults.paragraphSpacingPercent)
		assertEquals(true, defaults.publisherStyles)

		preferences.setReaderDefaultSettings(
			ReaderSettings(
				paragraphSpacingPercent = 150,
				publisherStyles = false
			)
		)

		assertEquals(150, preferences.readerParagraphSpacingPercent)
		assertEquals(false, preferences.readerPublisherStylesEnabled)
	}

	@Test
	fun readerDefaultSettingsRoundTripKeepScreenOn() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerKeepScreenOn = true

		assertEquals(true, preferences.readerDefaultSettings().keepScreenOn)

		preferences.setReaderDefaultSettings(
			ReaderSettings(keepScreenOn = false)
		)

		assertEquals(false, preferences.readerKeepScreenOn)
	}

	@Test
	fun readerDefaultSettingsRoundTripReadaloudSyncEnabled() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerReadaloudSyncEnabled = false

		assertEquals(false, preferences.readerDefaultSettings().readaloudSyncEnabled)

		preferences.setReaderDefaultSettings(
			ReaderSettings(readaloudSyncEnabled = true)
		)

		assertEquals(true, preferences.readerReadaloudSyncEnabled)
	}

	@Test
	fun readerDefaultSettingsRoundTripDimOverlay() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerDimOverlayPercent = 30

		assertEquals(30, preferences.readerDefaultSettings().dimOverlayPercent)

		preferences.setReaderDefaultSettings(
			ReaderSettings(dimOverlayPercent = 60)
		)

		assertEquals(60, preferences.readerDimOverlayPercent)
	}

	@Test
	fun readerDefaultSettingsRoundTripOrientation() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerOrientation = ReaderOrientationPortrait

		assertEquals(ReaderOrientationPortrait, preferences.readerDefaultSettings().orientation)

		preferences.setReaderDefaultSettings(
			ReaderSettings(orientation = ReaderOrientationLockedLandscape)
		)

		assertEquals(ReaderOrientationLockedLandscape, preferences.readerOrientation)
	}

	@Test
	fun readerDefaultSettingsRoundTripExplicitFlowModePreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerFlowMode = ReaderFlowScrolledGaps

		assertEquals(ReaderFlowScrolledGaps, preferences.readerDefaultSettings().flowMode)
		assertEquals(false, preferences.readerDefaultSettings().paged)

		preferences.setReaderDefaultSettings(
			ReaderSettings(flowMode = ReaderFlowPagedVertical)
		)

		assertEquals(ReaderFlowPagedVertical, preferences.readerFlowMode)
		assertEquals(true, preferences.readerPaged)
	}

	@Test
	fun readerDefaultSettingsRoundTripReadingDirectionPreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerDirection = ReaderDirectionRtl

		assertEquals(ReaderDirectionRtl, preferences.readerDefaultSettings().direction)

		preferences.setReaderDefaultSettings(
			ReaderSettings(direction = ReaderDirectionLtr)
		)

		assertEquals(ReaderDirectionLtr, preferences.readerDirection)
	}

	@Test
	fun readerDefaultSettingsRoundTripVolumeKeyPageTurns() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerVolumeKeyPageTurns = true

		assertEquals(true, preferences.readerDefaultSettings().volumeKeyPageTurns)

		preferences.setReaderDefaultSettings(
			ReaderSettings(volumeKeyPageTurns = false)
		)

		assertEquals(false, preferences.readerVolumeKeyPageTurns)
	}
}
