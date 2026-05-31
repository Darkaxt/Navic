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

	@Test
	fun lidaClipsOfflineStorageTextSummarizesClipsAndSize() {
		assertEquals("0 clips", lidaClipsOfflineClipCountText(0))
		assertEquals("1 clip", lidaClipsOfflineClipCountText(1))
		assertEquals("12 clips", lidaClipsOfflineClipCountText(12))
		assertEquals("1.5 GB", lidaClipsOfflineStorageSizeText(1536L * 1024L * 1024L))
	}

	@Test
	fun musicBrainzCacheTextSummarizesCachedSongsAndResultTypes() {
		assertEquals("0 songs", musicBrainzCacheValueText(0))
		assertEquals("1 song", musicBrainzCacheValueText(1))
		assertEquals("12 songs", musicBrainzCacheValueText(12))

		assertEquals(
			"No cached MusicBrainz results",
			musicBrainzCacheSummaryText(
				artworkSongs = 0,
				metadataSongs = 0,
				missingSongs = 0
			)
		)
		assertEquals(
			"1 artwork, 2 metadata, 3 misses",
			musicBrainzCacheSummaryText(
				artworkSongs = 1,
				metadataSongs = 2,
				missingSongs = 3
			)
		)
	}
}
