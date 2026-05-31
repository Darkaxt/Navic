package paige.navic.ui.screens.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.LidaClipsHealthCheck
import paige.navic.domain.repositories.LidaClipsHealthStatus
import paige.navic.domain.repositories.LidaClipsRecentFailure
import paige.navic.domain.repositories.LidaClipsServiceStatus

class ActivityDisplayPolicyTest {
	@Test
	fun downloadSummaryCountsActiveAndFailedQueueItems() {
		val summary = navicDownloadActivitySummary(
			listOf(
				activityDownloadItem("song-1", DownloadStatus.DOWNLOADING),
				activityDownloadItem("song-2", DownloadStatus.QUEUED),
				activityDownloadItem("song-3", DownloadStatus.FAILED)
			)
		)

		assertEquals(ActivitySection.NavicDownloads, summary.section)
		assertEquals("3 items", summary.value)
		assertEquals("2 active, 1 failed", summary.detail)
		assertTrue(summary.active)
		assertTrue(summary.failed)
	}

	@Test
	fun downloadSummaryShowsClearQueueWhenNothingIsPending() {
		val summary = navicDownloadActivitySummary(emptyList())

		assertEquals("No active downloads", summary.value)
		assertEquals("Queue is clear", summary.detail)
		assertFalse(summary.active)
		assertFalse(summary.failed)
	}

	@Test
	fun aurralSummaryCombinesAlbumAcquisitionsAndFlowWork() {
		val summary = aurralActivitySummary(
			AurralServiceStatus(
				acquisitionQueue = listOf(
					aurralQueueItem("1", "processing"),
					aurralQueueItem("2", "failed")
				),
				flowTracksPending = 1,
				flowTracksDownloading = 1,
				flowTracksFailed = 1
			)
		)

		assertEquals(ActivitySection.Aurral, summary.section)
		assertEquals("2 requests", summary.value)
		assertEquals("1 active, 1 failed; 3 flow tracks: 1 pending, 1 downloading, 1 failed", summary.detail)
		assertTrue(summary.active)
		assertTrue(summary.failed)
	}

	@Test
	fun aurralSummaryWaitsForVerifiedServiceStatus() {
		val summary = aurralActivitySummary(null)

		assertEquals("Not checked", summary.value)
		assertEquals("Waiting for service status", summary.detail)
		assertFalse(summary.active)
		assertFalse(summary.failed)
	}

	@Test
	fun lidaClipsSummaryHighlightsRunningSyncFailuresAndHealthChecks() {
		val summary = lidaClipsActivitySummary(
			LidaClipsServiceStatus(
				activeClips = 10,
				officialClips = 7,
				fallbackClips = 3,
				syncPaused = false,
				syncRunning = true,
				health = LidaClipsHealthStatus(
					status = "degraded",
					checks = listOf(
						LidaClipsHealthCheck(
							name = "scanner",
							ok = false,
							error = "offline",
							address = null,
							path = null,
							skipped = false
						),
						LidaClipsHealthCheck(
							name = "optional",
							ok = false,
							error = "skipped",
							address = null,
							path = null,
							skipped = true
						)
					)
				),
				recentFailures = listOf(
					LidaClipsRecentFailure(null, "Artist", null, "Track", "not found", null, null),
					LidaClipsRecentFailure(42, null, null, null, "timeout", null, null)
				)
			)
		)

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("Sync running", summary.value)
		assertEquals("2 recent failures, 1 health check failed", summary.detail)
		assertTrue(summary.active)
		assertTrue(summary.failed)
	}

	private fun activityDownloadItem(
		id: String,
		status: DownloadStatus
	) = ActivityDownloadItem(
		songId = id,
		title = id,
		artistName = null,
		albumTitle = null,
		status = status,
		progress = 0f
	)

	private fun aurralQueueItem(
		id: String,
		status: String
	) = AurralAcquisitionQueueItem(
		id = id,
		type = "album",
		albumId = id,
		albumMbid = id,
		albumName = "Album $id",
		artistId = "artist-$id",
		artistMbid = "artist-$id",
		artistName = "Artist $id",
		status = status,
		requestedAt = null,
		inQueue = true
	)
}
