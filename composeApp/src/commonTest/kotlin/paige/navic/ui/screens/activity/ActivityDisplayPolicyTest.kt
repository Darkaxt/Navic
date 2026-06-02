package paige.navic.ui.screens.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.LidaClipDownloadEntity
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus

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

		assertEquals("No active Aurral requests", summary.value)
		assertEquals("Queue is clear", summary.detail)
		assertFalse(summary.active)
		assertFalse(summary.failed)
	}

	@Test
	fun aurralActivitySectionIsShownOnlyWhenThereIsQueueWork() {
		assertFalse(shouldShowAurralActivitySection(null))
		assertFalse(shouldShowAurralActivitySection(AurralServiceStatus()))
		assertTrue(
			shouldShowAurralActivitySection(
				AurralServiceStatus(acquisitionQueue = listOf(aurralQueueItem("1", "processing")))
			)
		)
		assertTrue(shouldShowAurralActivitySection(AurralServiceStatus(flowTracksPending = 1)))
	}

	@Test
	fun integrationStatusErrorsAreNotShownInTheActivityQueuePage() {
		assertNull(
			activityQueueSectionError(
				ActivitySection.Aurral,
				Exception("Aurral health returned HTTP 404")
			)
		)
		assertNull(
			activityQueueSectionError(
				ActivitySection.Aurral,
				Exception("Aurral auth returned HTTP 404")
			)
		)
		assertNull(
			activityQueueSectionError(
				ActivitySection.LidaClips,
				Exception("LidaClips health returned HTTP 404")
			)
		)
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
	fun lidaClipsSummaryReportsOnlyClientQueueState() {
		val summary = lidaClipsActivitySummary(downloads = emptyList())

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("No active clip downloads", summary.value)
		assertEquals("Queue is clear", summary.detail)
		assertFalse(summary.active)
		assertFalse(summary.failed)
	}

	@Test
	fun lidaClipsActivitySectionRequiresEnabledConfiguredIntegration() {
		assertTrue(
			shouldShowLidaClipsActivitySection(
				lidaClipsEnabled = true,
				baseUrl = "https://clips.example.com"
			)
		)
		assertFalse(
			shouldShowLidaClipsActivitySection(
				lidaClipsEnabled = false,
				baseUrl = "https://clips.example.com"
			)
		)
		assertFalse(
			shouldShowLidaClipsActivitySection(
				lidaClipsEnabled = true,
				baseUrl = ""
			)
		)
		assertFalse(
			shouldShowLidaClipsActivitySection(
				lidaClipsEnabled = true,
				baseUrl = "not a url"
			)
		)
	}

	@Test
	fun lidaClipsSummaryReportsClientDownloadQueue() {
		val summary = lidaClipsActivitySummary(
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
	fun lidaClipsSummaryReportsFailedClientQueueRows() {
		val summary = lidaClipsActivitySummary(
			downloads = listOf(
				lidaClipDownload("song-failed", "Failed Track", DownloadStatus.FAILED)
			)
		)

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("1 clip download", summary.value)
		assertEquals("1 failed", summary.detail)
		assertFalse(summary.active)
		assertTrue(summary.failed)
	}

	@Test
	fun lidaClipsSummaryKeepsCompletedClientClipRowsVisible() {
		val summary = lidaClipsActivitySummary(
			downloads = listOf(
				lidaClipDownload("song-ready", "Ready Track", DownloadStatus.DOWNLOADED)
			)
		)

		assertEquals(ActivitySection.LidaClips, summary.section)
		assertEquals("1 clip download", summary.value)
		assertEquals("1 ready", summary.detail)
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
