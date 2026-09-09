package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralConfirmationType
import paige.navic.ui.screens.artist.viewmodels.ArtistState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArtistMonitoringConfirmationTest {
	private val initial = ArtistState(
		artist = DomainArtist("local", "Artist", musicBrainzId = "mbid"),
		albums = emptyList(), topSongs = emptyList(), aurralProfileLoading = true
	)

	@Test
	fun terminalConfirmationEndsVisibleSubmissionBeforeCacheMaintenanceCompletes() {
		assertTrue(aurralMonitoringSubmissionIsVisible(true, null))
		assertTrue(aurralMonitoringSubmissionIsVisible(true, AurralConfirmationStatus.Pending))
		assertFalse(aurralMonitoringSubmissionIsVisible(true, AurralConfirmationStatus.Confirmed))
		assertFalse(aurralMonitoringSubmissionIsVisible(true, AurralConfirmationStatus.Failed))
		assertFalse(aurralMonitoringSubmissionIsVisible(false, AurralConfirmationStatus.Pending))
	}

	@Test
	fun confirmationUpdatesActionBeforeEnrichmentFinishes() {
		val confirmed = initial.withConfirmedAurralMonitoring(listOf(item(AurralConfirmationStatus.Confirmed)))
		assertTrue(confirmed.aurralMonitored == true)
		assertTrue(confirmed.aurralProfileLoading)
		assertNull(confirmed.aurralFeedback)
	}

	@Test
	fun acceptanceAndFailureAreNotConfirmation() {
		for (status in listOf(AurralConfirmationStatus.Pending, AurralConfirmationStatus.Failed)) {
			assertSame(initial, initial.withConfirmedAurralMonitoring(listOf(item(status))))
		}
	}

	@Test
	fun confirmedStateSurvivesNextPendingFailedAndEvictedQueueEntry() {
		val oldEnrichment = initial.copy(aurralMonitored = false)
		val confirmedMonitoring = mapOf("mbid" to true)
		for (status in listOf(AurralConfirmationStatus.Pending, AurralConfirmationStatus.Failed)) {
			val unmonitoring = item(status).copy(expectedMonitored = false)
			assertTrue(oldEnrichment.withConfirmedAurralMonitoring(listOf(unmonitoring), confirmedMonitoring).aurralMonitored == true)
		}
		assertTrue(oldEnrichment.withConfirmedAurralMonitoring(emptyList(), confirmedMonitoring).aurralMonitored == true)
		assertFalse(oldEnrichment.withConfirmedAurralMonitoring(
			listOf(item(AurralConfirmationStatus.Confirmed).copy(expectedMonitored = false)), confirmedMonitoring
		).aurralMonitored!!)
	}

	@Test
	fun explicitFalseConfirmsUnmonitoringButMissingValueDoesNot() {
		val monitored = initial.copy(aurralMonitored = true)
		assertFalse(monitored.withConfirmedAurralMonitoring(
			listOf(item(AurralConfirmationStatus.Confirmed).copy(expectedMonitored = false))
		).aurralMonitored!!)
		assertSame(monitored, monitored.withConfirmedAurralMonitoring(
			listOf(item(AurralConfirmationStatus.Confirmed).copy(expectedMonitored = null))
		))
	}

	@Test
	fun resolvedIdentityIsUsedAndUnrelatedConfirmationIgnored() {
		val confirmation = listOf(item(AurralConfirmationStatus.Confirmed))
		val unrelated = initial.copy(aurralArtistMbid = "other")
		assertSame(unrelated, unrelated.withConfirmedAurralMonitoring(confirmation))
		assertTrue(initial.copy(artist = DomainArtist("local", "Artist"), aurralArtistMbid = "mbid")
			.withConfirmedAurralMonitoring(confirmation).aurralMonitored == true)
	}

	private fun item(status: AurralConfirmationStatus) = AurralConfirmationQueueItem(
		id = "monitor", type = AurralConfirmationType.ArtistMonitoring, status = status,
		title = "Artist", artistMbid = "mbid", expectedMonitored = true, updatedAtMillis = 1
	)
}
