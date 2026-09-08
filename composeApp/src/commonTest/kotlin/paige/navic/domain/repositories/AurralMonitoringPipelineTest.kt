package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import paige.navic.data.remote.aurral.*
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.IntegrationService
import paige.navic.util.core.synchronized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AurralMonitoringPipelineTest {
	@Test
	fun confirmedMutationPublishesQueueAndLocalStateWithoutLookupEvenWhileCacheClearWaits() = runTest {
		for (monitored in listOf(true, false)) {
			val fixture = Fixture(backgroundScope)
			val clearStarted = CompletableDeferred<Unit>()
			val releaseClear = CompletableDeferred<Unit>()
			fixture.api.mutate = { AurralArtistMonitoringOutcome.Confirmed(monitored) }
			fixture.cache.clear = { clearStarted.complete(Unit); releaseClear.await() }
			val submission = async { fixture.set(monitored) }
			try {
				runCurrent()
				assertTrue(clearStarted.isCompleted)
				assertFalse(submission.isCompleted)
				assertEquals(AurralConfirmationStatus.Confirmed, fixture.queue().single().status)
				assertEquals(monitored, fixture.local.libraryArtistMonitorStates.value["artist-id"])
				assertEquals(0, fixture.api.lookups)
			} finally { releaseClear.complete(Unit) }
			submission.await().getOrThrow()
			runCurrent()
			assertEquals(0, fixture.api.lookups)
		}
	}

	@Test
	fun queuedMutationOnlyObservesEveryTenSecondsWithoutBlindCycleOrResubmit() = runTest {
		val fixture = Fixture(backgroundScope)
		fixture.set(true).getOrThrow()
		runCurrent()
		assertEquals(AurralConfirmationStatus.Pending, fixture.queue().single().status)
		assertTrue(fixture.local.libraryArtistMonitorStates.value.isEmpty())
		advanceTimeBy(190_000)
		runCurrent()
		assertEquals(20, fixture.api.lookups)
		assertEquals(1, fixture.api.mutations)
		assertEquals(AurralConfirmationStatus.Pending, fixture.queue().single().status)
		fixture.api.lookup = { true }
		advanceTimeBy(10_000)
		runCurrent()
		assertEquals(AurralConfirmationStatus.Confirmed, fixture.queue().single().status)
		assertEquals(true, fixture.local.libraryArtistMonitorStates.value["artist-id"])
		assertEquals(1, fixture.api.mutations)
		advanceTimeBy(600_000)
		runCurrent()
		assertEquals(21, fixture.api.lookups)
	}

	@Test
	fun cancelledSubmissionAtAuthOrMutationNeverBecomesFailure() = runTest {
		for (boundary in listOf("auth", "mutation")) {
			val fixture = Fixture(backgroundScope)
			val entered = CompletableDeferred<Unit>()
			val suspendThenWrapCancellation: suspend () -> Nothing = {
				entered.complete(Unit)
				try { awaitCancellation() } catch (error: CancellationException) {
					throw IllegalStateException("wrapped cancellation", error)
				}
			}
			when (boundary) {
				"auth" -> {
					fixture.preferences.aurralUsername = "user"
					fixture.preferences.aurralPassword = "password"
					fixture.api.loginAction = suspendThenWrapCancellation
				}
				"mutation" -> fixture.api.mutate = suspendThenWrapCancellation
			}
			var returned = false
			var cancelled = false
			val submission = launch(start = CoroutineStart.UNDISPATCHED) {
				try { fixture.set(true); returned = true }
				catch (error: CancellationException) { cancelled = true; throw error }
			}
			assertTrue(entered.isCompleted, boundary)
			submission.cancelAndJoin()
			runCurrent()
			assertTrue(cancelled, boundary)
			assertFalse(returned, boundary)
			assertTrue(fixture.queue().isEmpty(), boundary)
			assertTrue(fixture.preferences.failedIntegrationServices.isEmpty(), boundary)
			assertTrue(fixture.local.libraryArtistMonitorStates.value.isEmpty(), boundary)
			if (boundary == "auth") assertEquals(0, fixture.api.mutations)
		}
	}

	@Test
	fun acceptedServerWorkRemainsTrackedAfterCallerCancelsCacheMaintenance() = runTest {
		val fixture = Fixture(backgroundScope)
		val cacheEntered = CompletableDeferred<Unit>()
		fixture.cache.clear = {
			cacheEntered.complete(Unit)
			try { awaitCancellation() } catch (error: CancellationException) {
				throw IllegalStateException("cache wrapped cancellation", error)
			}
		}
		var returned = false
		val submission = launch(start = CoroutineStart.UNDISPATCHED) {
			fixture.set(true)
			returned = true
		}
		assertTrue(cacheEntered.isCompleted)
		submission.cancelAndJoin()
		runCurrent()
		assertFalse(returned)
		assertEquals(AurralConfirmationStatus.Pending, fixture.queue().single().status)
		assertTrue(fixture.preferences.failedIntegrationServices.isEmpty())
		assertTrue(fixture.confirmed.isEmpty())
		fixture.api.lookup = { true }
		advanceTimeBy(10_000)
		runCurrent()
		assertEquals(AurralConfirmationStatus.Confirmed, fixture.queue().single().status)
		assertEquals(listOf("artist-id" to true), fixture.confirmed)
		assertEquals(1, fixture.api.mutations)
	}

	@Test
	fun replacedSubmissionCannotPublishItsLateConfirmedResult() = runTest {
		val fixture = Fixture(backgroundScope)
		val releaseOld = CompletableDeferred<Unit>()
		fixture.api.mutate = {
			withContext(NonCancellable) { releaseOld.await() }
			AurralArtistMonitoringOutcome.Confirmed(true)
		}
		val old = launch(start = CoroutineStart.UNDISPATCHED) { fixture.set(true) }
		try {
			fixture.api.mutate = { AurralArtistMonitoringOutcome.Confirmed(false) }
			fixture.set(false).getOrThrow()
			assertEquals(false, fixture.queue().single().expectedMonitored)
			releaseOld.complete(Unit)
			old.join()
			assertEquals(AurralConfirmationStatus.Confirmed, fixture.queue().single().status)
			assertEquals(false, fixture.local.libraryArtistMonitorStates.value["artist-id"])
			assertEquals(listOf("artist-id" to false), fixture.confirmed)
			assertTrue(fixture.preferences.failedIntegrationServices.isEmpty())
		} finally { releaseOld.complete(Unit); old.cancelAndJoin() }
	}

	@Test
	fun oldObserverCleanupCannotOverwriteOrUnregisterReplacement() = runTest {
		val fixture = Fixture(backgroundScope)
		val releaseOld = CompletableDeferred<Unit>()
		val oldEntered = CompletableDeferred<Unit>()
		val replacementCancelled = CompletableDeferred<Unit>()
		fixture.api.lookup = {
			oldEntered.complete(Unit)
			try { awaitCancellation() } catch (_: CancellationException) {
				withContext(NonCancellable) { releaseOld.await() }
			}
			true
		}
		try {
			fixture.set(true).getOrThrow()
			runCurrent()
			assertTrue(oldEntered.isCompleted)
			fixture.api.lookup = {
				try { awaitCancellation() } finally { replacementCancelled.complete(Unit) }
			}
			fixture.set(false).getOrThrow()
			runCurrent()
			releaseOld.complete(Unit)
			runCurrent()
			assertEquals(false, fixture.queue().single().expectedMonitored)
			assertEquals(AurralConfirmationStatus.Pending, fixture.queue().single().status)
			assertTrue(fixture.confirmed.isEmpty())
			fixture.preferences.aurralEnabled = false
			fixture.manager.cancel(clearQueue = true)
			runCurrent()
			assertTrue(replacementCancelled.isCompleted)
			assertTrue(fixture.queue().isEmpty())
		} finally { releaseOld.complete(Unit); fixture.manager.cancel(clearQueue = true) }
	}

	@Test
	fun disableDuringWrappedPollCancellationCannotRepopulateClearedQueue() = runTest {
		val fixture = Fixture(backgroundScope)
		val entered = CompletableDeferred<Unit>()
		fixture.api.lookup = {
			entered.complete(Unit)
			try { awaitCancellation() } catch (error: CancellationException) {
				throw IllegalStateException("wrapped cancellation", error)
			}
		}
		fixture.set(true).getOrThrow()
		runCurrent()
		assertTrue(entered.isCompleted)
		fixture.preferences.aurralEnabled = false
		fixture.manager.cancel(clearQueue = true)
		runCurrent()
		assertTrue(fixture.queue().isEmpty())
		assertTrue(fixture.confirmed.isEmpty())
		assertTrue(fixture.preferences.failedIntegrationServices.isEmpty())
		assertEquals(1, fixture.api.lookups)
	}

	@Test
	fun configurationChangeRejectsLateObservationWithoutNewPollOrFailure() = runTest {
		for (changeUrl in listOf(true, false)) {
			val fixture = Fixture(backgroundScope)
			val response = CompletableDeferred<Boolean?>()
			fixture.api.lookup = { response.await() }
			fixture.set(true).getOrThrow()
			runCurrent()
			if (changeUrl) fixture.preferences.aurralBaseUrl = "https://other.example.com"
			else fixture.preferences.aurralPassword = "changed"
			response.complete(true)
			runCurrent()
			advanceTimeBy(20_000)
			runCurrent()
			assertTrue(fixture.queue().isEmpty())
			assertTrue(fixture.confirmed.isEmpty())
			assertTrue(fixture.preferences.failedIntegrationServices.isEmpty())
			assertEquals(1, fixture.api.lookups)
		}
	}

	@Test
	fun failedObservationIsRetryableFailureNotFalseConfirmationOrResubmission() = runTest {
		val fixture = Fixture(backgroundScope)
		fixture.api.lookup = { error("confirmation lookup failed") }
		fixture.set(true).getOrThrow()
		runCurrent()
		assertEquals(AurralConfirmationStatus.Failed, fixture.queue().single().status)
		assertTrue(fixture.confirmed.isEmpty())
		advanceTimeBy(600_000)
		runCurrent()
		assertEquals(1, fixture.api.mutations)
		assertEquals(1, fixture.api.lookups)
	}

	@Test
	fun genuineMutationFailureStillMarksServiceDown() = runTest {
		val fixture = Fixture(backgroundScope)
		fixture.api.mutate = { error("mutation failed") }
		assertTrue(fixture.set(true).isFailure)
		assertEquals(AurralConfirmationStatus.Failed, fixture.queue().single().status)
		assertEquals(setOf(IntegrationService.Aurral), fixture.preferences.failedIntegrationServices)
		assertTrue(fixture.confirmed.isEmpty())
	}

	@Test
	fun concurrentArtistsDoNotLoseQueueUpdatesOrConfirmationCallbacks() = runTest {
		val fixture = Fixture(backgroundScope)
		fixture.api.mutate = { AurralArtistMonitoringOutcome.Confirmed(true) }
		val gate = CompletableDeferred<Unit>()
		coroutineScope {
			val jobs = (1..12).map { id ->
				async(Dispatchers.Default) {
					gate.await()
					fixture.actions.setArtistMonitoring(DomainArtist("local-$id", "Artist $id", musicBrainzId = "artist-$id"), true).getOrThrow()
				}
			}
			gate.complete(Unit)
			jobs.awaitAll()
		}
		assertEquals(12, fixture.queue().size)
		assertTrue(fixture.queue().all { it.status == AurralConfirmationStatus.Confirmed })
		assertEquals(12, fixture.confirmed.size)
	}

	private class Fixture(scope: CoroutineScope) {
		val preferences = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val api = MonitoringFakeApi()
		val cache = MonitoringCache()
		val local = AurralRepositoryLocalState { 1_000L }
		val confirmed = mutableListOf<Pair<String, Boolean>>()
		val manager = AurralConfirmationQueueManager(
			preferences, api, { 1_000L }, local::bumpArtistStateRevision,
			{ id, name, value -> local.rememberOptimisticArtistMonitoring(id, name, value); confirmed += id to value }, scope
		)
		val actions = AurralMutationRepositoryActions(preferences, api, AurralRepositoryAuth(preferences, api), local, manager, cache, { 1_000L }, true)
		fun queue() = manager.confirmationQueue.value
		suspend fun set(monitored: Boolean) = actions.setArtistMonitoring(DomainArtist("local", "Koji Kondo", musicBrainzId = "artist-id"), monitored)
	}

	private class MonitoringCache : AurralMetadataCache {
		var clear: suspend () -> Unit = {}
		override suspend fun get(cacheKey: String): AurralMetadataCacheRecord? = null
		override suspend fun put(record: AurralMetadataCacheRecord) = Unit
		override suspend fun clearBaseUrl(baseUrl: String) = clear()
	}

	private class MonitoringFakeApi : AurralApiClient {
		private val countLock = Any()
		var mutate: suspend () -> AurralArtistMonitoringOutcome = { AurralArtistMonitoringOutcome.AwaitingConfirmation }
		var lookup: suspend () -> Boolean? = { null }
		var loginAction: suspend () -> AurralAuthSessionDto? = { null }
		var mutations = 0
		var lookups = 0
		override suspend fun monitorArtist(baseUrl: String, requestHeaders: Map<String, String>, artistMbid: String, payload: AurralArtistMonitorPayload, ensureCurrent: () -> Unit): AurralArtistMonitoringOutcome {
			synchronized(countLock) { mutations++ }
			return mutate()
		}
		override suspend fun fetchLibraryArtistMonitoring(baseUrl: String, requestHeaders: Map<String, String>, artistMbid: String): Boolean? {
			synchronized(countLock) { lookups++ }
			return lookup()
		}
		override suspend fun login(baseUrl: String, requestHeaders: Map<String, String>, username: String, password: String) = loginAction()
		override suspend fun testConnection(baseUrl: String, requestHeaders: Map<String, String>) = AurralConnectionResult.Connected
		override suspend fun fetchServiceStatus(baseUrl: String, requestHeaders: Map<String, String>) = AurralServiceStatus()
		override suspend fun fetchArtistEnrichment(baseUrl: String, requestHeaders: Map<String, String>, artistMbid: String, artistName: String) = AurralArtistEnrichment(artistMbid = artistMbid, artistName = artistName)
		override suspend fun requestAlbum(baseUrl: String, requestHeaders: Map<String, String>, payload: AurralAlbumRequestPayload) = Unit
		override suspend fun fetchReleaseGroupCoverImageUrl(baseUrl: String, requestHeaders: Map<String, String>, releaseGroupMbid: String, artistName: String, albumTitle: String): String? = null
	}
}
