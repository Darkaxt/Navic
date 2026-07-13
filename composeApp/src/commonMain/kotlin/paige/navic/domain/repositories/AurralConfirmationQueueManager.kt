package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.core.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AurralConfirmationQueueManager"
private const val AURRAL_MONITOR_CONFIRMATION_POLLS_PER_CYCLE = 18
private const val AURRAL_CONFIRMATION_QUEUE_RETAINED_ITEMS = 20
private val AURRAL_MONITOR_CONFIRMATION_DELAY: Duration = 10.seconds
private val AURRAL_CONFIRMATION_REEMIT_DELAY: Duration = 3.minutes

enum class AurralConfirmationType {
	ArtistMonitoring
}

enum class AurralConfirmationStatus {
	Pending,
	Confirmed,
	Failed
}

data class AurralConfirmationQueueItem(
	val id: String,
	val type: AurralConfirmationType,
	val status: AurralConfirmationStatus,
	val title: String,
	val artistMbid: String? = null,
	val expectedMonitored: Boolean? = null,
	val message: String? = null,
	val updatedAtMillis: Long
)

private enum class AurralConfirmationPollResult {
	Pending,
	Confirmed,
	Failed
}

internal class AurralConfirmationQueueManager(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient,
	private val nowMillis: () -> Long,
	private val onArtistStateChanged: () -> Unit,
	private val onArtistMonitoringConfirmed: (artistMbid: String, artistName: String, monitored: Boolean) -> Unit,
	private val confirmationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
	private val confirmationJobs = mutableMapOf<String, Job>()
	private val _confirmationQueue = MutableStateFlow<List<AurralConfirmationQueueItem>>(emptyList())
	val confirmationQueue = _confirmationQueue.asStateFlow()

	fun upsert(item: AurralConfirmationQueueItem) {
		val retained = _confirmationQueue.value
			.filterNot { queued -> queued.id == item.id }
			.takeLast(AURRAL_CONFIRMATION_QUEUE_RETAINED_ITEMS - 1)
		_confirmationQueue.value = retained + item
	}

	fun cancel(clearQueue: Boolean) {
		confirmationJobs.values.forEach { job -> job.cancel() }
		confirmationJobs.clear()
		if (clearQueue && _confirmationQueue.value.isNotEmpty()) {
			_confirmationQueue.value = emptyList()
			onArtistStateChanged()
		}
	}

	fun startArtistMonitoringConfirmationWorker(
		confirmationId: String,
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		payload: AurralArtistMonitorPayload
	) {
		confirmationJobs.remove(confirmationId)?.cancel()
		confirmationJobs[confirmationId] = confirmationScope.launch {
			try {
				while (true) {
					if (!canRunAurralBackgroundWork()) {
						removeConfirmationQueueItem(confirmationId)
						return@launch
					}
					when (
						pollArtistMonitoringConfirmation(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = artistMbid,
							expectedMonitored = monitored
						)
					) {
						AurralConfirmationPollResult.Confirmed -> {
							upsert(
								AurralConfirmationQueueItem(
									id = confirmationId,
									type = AurralConfirmationType.ArtistMonitoring,
									status = AurralConfirmationStatus.Confirmed,
									title = artistName,
									artistMbid = artistMbid,
									expectedMonitored = monitored,
									message = if (monitored) {
										"Aurral confirmed artist monitoring."
									} else {
										"Aurral confirmed monitoring stopped."
									},
									updatedAtMillis = nowMillis()
								)
							)
							onArtistMonitoringConfirmed(artistMbid, artistName, monitored)
							return@launch
						}

						AurralConfirmationPollResult.Failed -> {
							markArtistMonitoringConfirmationFailed(
								confirmationId = confirmationId,
								artistMbid = artistMbid,
								artistName = artistName,
								monitored = monitored,
								message = "Aurral monitor confirmation failed."
							)
							return@launch
						}

						AurralConfirmationPollResult.Pending -> Unit
					}

					delay(AURRAL_CONFIRMATION_REEMIT_DELAY)
					if (!canRunAurralBackgroundWork()) {
						removeConfirmationQueueItem(confirmationId)
						return@launch
					}
					runCatching {
						apiClient.monitorArtist(
							baseUrl = baseUrl,
							requestHeaders = requestHeaders,
							artistMbid = artistMbid,
							payload = payload
						)
					}.onFailure { error ->
						markArtistMonitoringConfirmationFailed(
							confirmationId = confirmationId,
							artistMbid = artistMbid,
							artistName = artistName,
							monitored = monitored,
							message = error.message ?: error::class.simpleName ?: "Aurral monitor request failed."
						)
						Logger.w(TAG, "Aurral artist monitoring re-request failed for $artistName", error)
						return@launch
					}
				}
			} finally {
				confirmationJobs.remove(confirmationId)
			}
		}
	}

	private fun canRunAurralBackgroundWork(): Boolean =
		preferenceManager.aurralEnabled &&
			configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null

	private fun removeConfirmationQueueItem(confirmationId: String) {
		val updated = _confirmationQueue.value.filterNot { item -> item.id == confirmationId }
		if (updated.size != _confirmationQueue.value.size) {
			_confirmationQueue.value = updated
			onArtistStateChanged()
		}
	}

	private suspend fun pollArtistMonitoringConfirmation(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		artistMbid: String,
		expectedMonitored: Boolean
	): AurralConfirmationPollResult {
		repeat(AURRAL_MONITOR_CONFIRMATION_POLLS_PER_CYCLE) { attempt ->
			if (!canRunAurralBackgroundWork()) {
				return AurralConfirmationPollResult.Pending
			}
			val monitored = runCatching {
				apiClient.fetchLibraryArtistMonitoring(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistMbid = artistMbid
				)
			}.getOrElse { error ->
				Logger.w(TAG, "Aurral artist monitoring confirmation lookup failed", error)
				return AurralConfirmationPollResult.Failed
			}
			if (monitored == expectedMonitored) return AurralConfirmationPollResult.Confirmed
			if (monitored == null) return AurralConfirmationPollResult.Failed
			if (attempt < AURRAL_MONITOR_CONFIRMATION_POLLS_PER_CYCLE - 1) {
				delay(AURRAL_MONITOR_CONFIRMATION_DELAY)
			}
		}
		return AurralConfirmationPollResult.Pending
	}

	private fun markArtistMonitoringConfirmationFailed(
		confirmationId: String,
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		message: String
	) {
		upsert(
			AurralConfirmationQueueItem(
				id = confirmationId,
				type = AurralConfirmationType.ArtistMonitoring,
				status = AurralConfirmationStatus.Failed,
				title = artistName,
				artistMbid = artistMbid,
				expectedMonitored = monitored,
				message = message,
				updatedAtMillis = nowMillis()
			)
		)
	}
}
