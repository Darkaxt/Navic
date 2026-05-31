package paige.navic.ui.screens.aurral

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralServiceStatus

class AurralHubDisplayPolicyTest {
	@Test
	fun summaryCardsExposeDiscoveryRequestsAndFlows() {
		val cards = aurralHubSummaryCards(
			AurralServiceStatus(
				discoveryRecommendationsCount = 12,
				discoveryUpdating = true,
				requestsCount = 3,
				flowsCount = 3,
				enabledFlowsCount = 2,
				sharedPlaylistsCount = 1,
				flowTracksTotal = 10,
				flowTracksPending = 4,
				flowTracksDownloading = 2,
				flowTracksDone = 3,
				flowTracksFailed = 1,
				acquisitionQueue = listOf(
					queueItem("1", "processing"),
					queueItem("2", "available"),
					queueItem("3", "failed")
				)
			)
		)

		assertEquals(AurralHubSection.Discover, cards[0].section)
		assertEquals("12 recommendations", cards[0].value)
		assertEquals("updating", cards[0].detail)
		assertTrue(cards[0].active)

		assertEquals(AurralHubSection.Requests, cards[1].section)
		assertEquals("3 requests", cards[1].value)
		assertEquals("1 active, 1 ready, 1 failed", cards[1].detail)
		assertTrue(cards[1].active)

		assertEquals(AurralHubSection.Flows, cards[2].section)
		assertEquals("2 / 3 enabled", cards[2].value)
		assertEquals("10 tracks: 4 pending, 2 downloading, 3 ready, 1 failed; 1 shared playlist", cards[2].detail)
		assertTrue(cards[2].active)
	}

	private fun queueItem(
		id: String,
		status: String
	) = AurralAcquisitionQueueItem(
		id = id,
		type = "album",
		albumId = null,
		albumMbid = null,
		albumName = "Album $id",
		artistId = null,
		artistMbid = null,
		artistName = "Artist $id",
		status = status,
		requestedAt = null,
		inQueue = status == "processing"
	)
}
