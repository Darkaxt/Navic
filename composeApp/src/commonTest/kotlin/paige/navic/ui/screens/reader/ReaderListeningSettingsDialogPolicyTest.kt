package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPublicationFormat

class ReaderListeningSettingsDialogPolicyTest {
	@Test
	fun listeningTabIsCapabilityDriven() {
		assertFalse(
			komikkuSettingsTabs(
				publicationFormat = ReaderPublicationFormat.Epub,
				whispersyncCapable = false
			).contains(KomikkuSettingsTab.Listening)
		)

		assertTrue(
			komikkuSettingsTabs(
				publicationFormat = ReaderPublicationFormat.Epub,
				whispersyncCapable = true
			).contains(KomikkuSettingsTab.Listening)
		)
	}

	@Test
	fun pdfKeepsImageTabAndCanStillExposeListeningWhenRouteIsCapable() {
		val tabs = komikkuSettingsTabs(
			publicationFormat = ReaderPublicationFormat.Pdf,
			whispersyncCapable = true
		)

		assertTrue(tabs.contains(KomikkuSettingsTab.PdfImage))
		assertTrue(tabs.contains(KomikkuSettingsTab.Listening))
	}
}
