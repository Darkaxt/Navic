package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderSettingsImpactTest {
	@Test
	fun hostOnlySettingsKeepOneEngineAndRasterProjection() {
		val current = defaultReaderSettings()
		val updates = listOf(
			current,
			current.copy(dimOverlayPercent = 40),
			current.copy(colorFilterEnabled = true),
			current.copy(grayscaleEnabled = true),
			current.copy(invertedColors = true),
			current.copy(orientation = ReaderOrientationLandscape),
			current.copy(navBarType = ReaderNavBarTypeBottom),
			current.copy(tapZoneInvertMode = ReaderTapZoneInvertBoth),
			current.copy(pageBitmapQuality = ReaderPageBitmapQuality.High.persistedValue),
			current.copy(fullscreen = false),
			current.copy(keepScreenOn = true),
			current.copy(readaloudSyncEnabled = false),
			current.copy(whispersyncHighlightLeadMs = 1_500),
			current.copy(volumeKeyPageTurns = true),
			current.copy(webContentsDebuggingEnabled = true)
		)

		updates.forEach { updated ->
			assertEquals(current.readerEngineSettingsProjection(), updated.readerEngineSettingsProjection())
			assertEquals(current.readerPageRasterSnapshotKey(), updated.readerPageRasterSnapshotKey())
		}
	}

	@Test
	fun WebViewSettingsChangeBothEngineAndRasterProjection() {
		val current = defaultReaderSettings()
		val updates = listOf(
			current.copy(fontSizePercent = 160),
			current.copy(theme = ReaderDarkTheme),
			current.copy(paperTextureEnabled = !checkNotNull(current.paperTextureEnabled)),
			current.copy(tapZone = ReaderTapZoneKindle),
			current.copy(whispersyncHighlightColorArgb = 0x66112233)
		)

		updates.forEach { updated ->
			assertNotEquals(current.readerEngineSettingsProjection(), updated.readerEngineSettingsProjection())
			assertNotEquals(current.readerPageRasterSnapshotKey(), updated.readerPageRasterSnapshotKey())
		}
	}
}
