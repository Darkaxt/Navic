package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidaClipCachePolicyTest {
	@Test
	fun videoCacheDefaultsToBoundedLocalCache() {
		assertEquals(512, DefaultLidaClipsVideoCacheSizeMb)
		assertEquals(
			listOf(0, 128, 256, 512, 1024, 2048, 4096),
			LidaClipsVideoCacheSizeOptionsMb
		)
		assertEquals(0L, lidaClipVideoCacheSizeBytes(0))
		assertEquals(0L, lidaClipVideoCacheSizeBytes(-100))
		assertEquals(512L * 1024L * 1024L, lidaClipVideoCacheSizeBytes(512))
	}

	@Test
	fun cachedVideoFeatureIsEnabledOnlyWhenCacheIsConfiguredAndFileExists() {
		assertFalse(shouldUseCachedLidaClipVideo(cacheSizeMb = 0, cacheFileExists = true))
		assertFalse(shouldUseCachedLidaClipVideo(cacheSizeMb = 512, cacheFileExists = false))
		assertTrue(shouldUseCachedLidaClipVideo(cacheSizeMb = 512, cacheFileExists = true))
	}

	@Test
	fun offlineVideoFeatureUsesPersistentFilesWithoutDependingOnTemporaryCacheSize() {
		assertFalse(shouldUseOfflineLidaClipVideo(offlineFileExists = false))
		assertTrue(shouldUseOfflineLidaClipVideo(offlineFileExists = true))
	}

	@Test
	fun offlineFileNamesEncodeSongIdsAndSanitizeExtensions() {
		assertEquals(
			"song-736f6e672f31-42.mp4",
			lidaClipOfflineFileName(
				songId = "song/1",
				clipId = 42,
				extension = "mp4"
			)
		)
		assertEquals(
			"song-736f6e672f31-42.mp4",
			lidaClipOfflineFileName(
				songId = "song/1",
				clipId = 42,
				extension = "../mp4"
			)
		)
		assertEquals(
			"song-unknown-42.mp4",
			lidaClipOfflineFileName(
				songId = " ",
				clipId = 42,
				extension = "exe"
			)
		)
		assertEquals("song-736f6e672f31-", lidaClipOfflineFilePrefix("song/1"))
	}

	@Test
	fun cacheExtensionUsesSafeVideoExtensionWithMp4Fallback() {
		assertEquals(
			"webm",
			lidaClipCacheFileExtension(
				mimeType = null,
				fileName = "Artist - Title.webm",
				streamUrl = "https://clips.example.test/api/v1/stream/1"
			)
		)
		assertEquals(
			"mkv",
			lidaClipCacheFileExtension(
				mimeType = "video/x-matroska",
				fileName = null,
				streamUrl = "https://clips.example.test/api/v1/stream/1"
			)
		)
		assertEquals(
			"mp4",
			lidaClipCacheFileExtension(
				mimeType = null,
				fileName = null,
				streamUrl = "https://clips.example.test/video/clip.mp4?token=secret"
			)
		)
		assertEquals(
			"mp4",
			lidaClipCacheFileExtension(
				mimeType = "application/octet-stream",
				fileName = "clip.exe",
				streamUrl = "https://clips.example.test/api/v1/stream/1"
			)
		)
	}

	@Test
	fun prunePlanRemovesOldestFilesUntilCacheIsUnderLimit() {
		val files = listOf(
			LidaClipCacheFileInfo(path = "old.mp4", sizeBytes = 100, lastModifiedMillis = 1),
			LidaClipCacheFileInfo(path = "middle.mp4", sizeBytes = 100, lastModifiedMillis = 2),
			LidaClipCacheFileInfo(path = "new.mp4", sizeBytes = 100, lastModifiedMillis = 3)
		)

		assertEquals(
			listOf("old.mp4"),
			lidaClipCachePrunePlan(
				files = files,
				maxSizeBytes = 250,
				protectedPath = "new.mp4"
			)
		)
		assertEquals(
			listOf("middle.mp4"),
			lidaClipCachePrunePlan(
				files = files,
				maxSizeBytes = 250,
				protectedPath = "old.mp4"
			)
		)
	}
}
