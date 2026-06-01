package paige.navic.ui.screens.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.LidaClipsDownloadQueueItem
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
	fun downloadQueueControlsExposeRetryAndDiscardForFailedRowsOnly() {
		val controls = navicDownloadQueueControls(
			listOf(
				activityDownloadItem("song-1", DownloadStatus.DOWNLOADING),
				activityDownloadItem("song-2", DownloadStatus.QUEUED),
				activityDownloadItem("song-3", DownloadStatus.FAILED),
				activityDownloadItem("song-4", DownloadStatus.FAILED)
			)
		)

		assertEquals(2, controls.failedCount)
		assertTrue(controls.canRetryFailedDownloads)
		assertTrue(controls.canDiscardFailedDownloads)
		assertTrue(controls.canClearDownloadQueue)
	}

	@Test
	fun downloadQueueControlsStayHiddenWithoutFailures() {
		val controls = navicDownloadQueueControls(
			listOf(
				activityDownloadItem("song-1", DownloadStatus.DOWNLOADING),
				activityDownloadItem("song-2", DownloadStatus.QUEUED)
			)
		)

		assertEquals(0, controls.failedCount)
		assertFalse(controls.canRetryFailedDownloads)
		assertFalse(controls.canDiscardFailedDownloads)
		assertTrue(controls.canClearDownloadQueue)
	}

	@Test
	fun downloadQueueControlsHideClearActionWhenQueueIsEmpty() {
		val controls = navicDownloadQueueControls(emptyList())

		assertEquals(0, controls.failedCount)
		assertFalse(controls.canRetryFailedDownloads)
		assertFalse(controls.canDiscardFailedDownloads)
		assertFalse(controls.canClearDownloadQueue)
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
	fun aurralAcquisitionRowsCanCancelWhenAurralExposesADeleteTarget() {
		assertTrue(aurralAcquisitionQueueItemControls(aurralQueueItem("1", "processing")).canCancel)
		assertTrue(
			aurralAcquisitionQueueItemControls(
				aurralQueueItem("2", "failed").copy(albumId = null, artistMbid = "artist-mbid")
			).canCancel
		)
		assertFalse(
			aurralAcquisitionQueueItemControls(
				aurralQueueItem("3", "processing").copy(albumId = null, artistMbid = null)
			).canCancel
		)
	}

	@Test
	fun aurralAcquisitionRowsRetryOnlyFailedAlbumsWithRequiredMbids() {
		assertTrue(aurralAcquisitionQueueItemControls(aurralQueueItem("1", "failed")).canRetry)
		assertFalse(aurralAcquisitionQueueItemControls(aurralQueueItem("2", "processing")).canRetry)
		assertFalse(
			aurralAcquisitionQueueItemControls(
				aurralQueueItem("3", "failed").copy(albumMbid = null)
			).canRetry
		)
		assertFalse(
			aurralAcquisitionQueueItemControls(
				aurralQueueItem("4", "failed").copy(artistMbid = null)
			).canRetry
		)
	}

	@Test
	fun lidaClipsSummaryHighlightsRunningSyncFailuresAndHealthChecks() {
		val summary = lidaClipsActivitySummary(
			status = LidaClipsServiceStatus(
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
			),
			downloads = emptyList()
		)

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("Sync running", summary.value)
		assertEquals("Clip download queue is empty; 1 health check failed; 2 recent issues", summary.detail)
		assertTrue(summary.active)
		assertTrue(summary.failed)
	}

	@Test
	fun lidaClipsSummaryPrioritizesDownloadQueueOverRecentFailures() {
		val summary = lidaClipsActivitySummary(
			status = LidaClipsServiceStatus(
				activeClips = 10,
				officialClips = 7,
				fallbackClips = 3,
				syncPaused = false,
				syncRunning = false,
				downloadQueue = listOf(
					lidaClipsDownloadQueueItem(42, "Queued Track", "queued"),
					lidaClipsDownloadQueueItem(43, "Active Track", "downloading")
				),
				recentFailures = listOf(
					LidaClipsRecentFailure(null, "Artist", null, "Old failure", "not found", null, null)
				)
			),
			downloads = listOf(
				lidaClipDownload("song-queued", "Queued Track", DownloadStatus.QUEUED),
				lidaClipDownload("song-active", "Active Track", DownloadStatus.DOWNLOADING)
			)
		)

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("2 clip downloads", summary.value)
		assertEquals("1 queued, 1 downloading", summary.detail)
		assertTrue(summary.active)
		assertFalse(summary.failed)
	}

	@Test
	fun lidaClipsSummaryDoesNotTreatRecentFailuresAsTheDownloadQueue() {
		val summary = lidaClipsActivitySummary(
			status = LidaClipsServiceStatus(
				activeClips = 10,
				officialClips = 7,
				fallbackClips = 3,
				syncPaused = false,
				syncRunning = false,
				recentFailures = listOf(
					LidaClipsRecentFailure(null, "Artist", null, "Old failure", "not found", null, null)
				)
			),
			downloads = emptyList()
		)

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("No active clip downloads", summary.value)
		assertEquals("Clip download queue is empty; 1 recent issue", summary.detail)
		assertFalse(summary.active)
		assertFalse(summary.failed)
	}

	@Test
	fun lidaClipsSummaryIgnoresBackendDashboardQueueWhenClientQueueIsEmpty() {
		val summary = lidaClipsActivitySummary(
			status = LidaClipsServiceStatus(
				activeClips = 10,
				officialClips = 7,
				fallbackClips = 3,
				syncPaused = false,
				syncRunning = false,
				downloadQueue = listOf(
					lidaClipsDownloadQueueItem(42, "Backend Queued", "queued")
				)
			),
			downloads = emptyList()
		)

		assertEquals("No active clip downloads", summary.value)
		assertEquals("Clip download queue is empty", summary.detail)
		assertFalse(summary.active)
		assertFalse(summary.failed)
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

	private fun lidaClipsDownloadQueueItem(
		id: Int,
		title: String,
		status: String
	) = LidaClipsDownloadQueueItem(
		lidarrTrackId = id,
		artist = "Artist",
		album = "Album",
		track = title,
		status = status,
		durationSeconds = 180
	)

	private fun lidaClipDownload(
		songId: String,
		title: String,
		status: DownloadStatus
	) = LidaClipDownloadEntity(
		songId = songId,
		clipId = songId.hashCode(),
		title = title,
		artist = "Artist",
		album = "Album",
		track = title,
		durationSeconds = 180,
		mimeType = "video/mp4",
		qualityTier = "official",
		fileName = "$songId.mp4",
		streamUrl = "https://clips.example.com/api/v1/stream/$songId",
		status = status,
		progress = 0f,
		filePath = null,
		persistOffline = false,
		updatedAtMillis = 1_000L
	)
}
