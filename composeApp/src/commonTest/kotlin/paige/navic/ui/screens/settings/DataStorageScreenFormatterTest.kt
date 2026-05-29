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

	@Test
	fun storageSizeTextKeepsSmallCacheSizesVisible() {
		assertEquals("0 B", storageSizeText(0))
		assertEquals("512 B", storageSizeText(512))
		assertEquals("1.5 KB", storageSizeText(1536))
		assertEquals("512 KB", storageSizeText(512L * 1024L))
	}

	@Test
	fun imageCacheStorageSizeTextUsesSharedFormatter() {
		assertEquals("1.5 KB", imageCacheStorageSizeText(1536))
		assertEquals("1.5 GB", imageCacheStorageSizeText(1536L * 1024L * 1024L))
	}
}
