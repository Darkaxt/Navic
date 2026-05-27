package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DataStorageScreenFormatterTest {
	@Test
	fun downloadStorageSizeTextReturnsOnlyTheSizeValue() {
		val text = downloadStorageSizeText(512L * 1024L * 1024L)

		assertEquals("512 MB", text)
		assertFalse(text.contains("//"))
	}

	@Test
	fun downloadStorageSizeTextUsesGbForLargeDownloads() {
		assertEquals("1.5 GB", downloadStorageSizeText(1536L * 1024L * 1024L))
	}
}
