package paige.navic.ui.screens.aurral

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralConfirmationType
import paige.navic.domain.repositories.aurralArtistMonitoringConfirmationItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralArtistMonitoringStateTest {
	@Test
	fun acceptedPendingDoesNotConfirmAndConfirmationSurvivesQueueEviction() {
		val initial = AurralArtistUiState(artist = DomainArtist("artist", "Artist", musicBrainzId = "mbid"))
		val pending = initial.withMonitoringConfirmation(item(AurralConfirmationStatus.Pending))
		assertFalse(pending.monitorConfirmed)
		assertTrue(pending.monitorPending)
		assertFalse(pending.canSubmitMonitoring)
		val confirmed = pending.withMonitoringConfirmation(item(AurralConfirmationStatus.Confirmed))
		assertTrue(confirmed.monitorConfirmed)
		assertFalse(confirmed.monitorPending)
		assertFalse(confirmed.canSubmitMonitoring)
		assertTrue(confirmed.withMonitoringConfirmation(null).monitorConfirmed)
	}

	@Test
	fun failureExposesErrorAndAllowsRetryWithoutNavigation() {
		val pending = AurralArtistUiState(artist = DomainArtist("artist", "Artist"))
			.withMonitoringConfirmation(item(AurralConfirmationStatus.Pending))
		val failed = pending.withMonitoringConfirmation(item(AurralConfirmationStatus.Failed))
		assertFalse(failed.monitorConfirmed)
		assertFalse(failed.monitorPending)
		assertEquals("Confirmation failed", failed.error?.message)
		assertTrue(failed.canSubmitMonitoring)
		val retrying = failed.copy(monitoring = true, error = null)
		assertFalse(retrying.canSubmitMonitoring)
	}

	@Test
	fun confirmedUnmonitoringIsNotInterpretedAsMonitoringEnabled() {
		val monitored = AurralArtistUiState(artist = DomainArtist("artist", "Artist"), monitorConfirmed = true)
		val stopped = monitored.withMonitoringConfirmation(item(AurralConfirmationStatus.Confirmed).copy(expectedMonitored = false))
		assertFalse(stopped.monitorConfirmed)
		assertTrue(stopped.canSubmitMonitoring)
	}

	@Test
	fun lateInitialLookupCannotOverrideQueueConfirmation() {
		val confirmed = AurralArtistUiState(artist = DomainArtist("artist", "Artist"))
			.withMonitoringConfirmation(item(AurralConfirmationStatus.Confirmed))
		val afterOldLookup = confirmed.copy(monitorConfirmed = false)
			.withMonitoringConfirmation(item(AurralConfirmationStatus.Confirmed))
		assertTrue(afterOldLookup.monitorConfirmed)
		assertFalse(afterOldLookup.canSubmitMonitoring)
	}

	@Test
	fun unrelatedArtistsConfirmationDoesNotChangeTheAction() {
		val initial = AurralArtistUiState(artist = DomainArtist("artist", "Artist", musicBrainzId = "other"))
		val selected = aurralArtistMonitoringConfirmationItem(
			queue = listOf(item(AurralConfirmationStatus.Confirmed)),
			artistMbid = initial.artist.musicBrainzId
		)
		val observed = initial.withMonitoringConfirmation(selected)
		assertFalse(observed.monitorConfirmed)
		assertTrue(observed.canSubmitMonitoring)
	}

	private fun item(status: AurralConfirmationStatus) = AurralConfirmationQueueItem(
		id = "monitor", type = AurralConfirmationType.ArtistMonitoring, status = status,
		title = "Artist", artistMbid = "mbid", expectedMonitored = true,
		message = "Confirmation failed", updatedAtMillis = 1
	)
}
