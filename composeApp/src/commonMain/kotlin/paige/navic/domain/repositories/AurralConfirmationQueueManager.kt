package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.util.core.Logger
import paige.navic.util.core.synchronized
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AurralConfirmationQueueManager"
private const val AURRAL_CONFIRMATION_QUEUE_RETAINED_ITEMS = 20
private val AURRAL_MONITOR_CONFIRMATION_DELAY = 10.seconds

enum class AurralConfirmationType { ArtistMonitoring }

enum class AurralConfirmationStatus { Pending, Confirmed, Failed }

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

internal class AurralMonitoringOperation(
	val id: String,
	val baseUrl: String,
	val artistMbid: String,
	val artistName: String,
	val monitored: Boolean,
	val submissionJob: Job,
	val configurationIsCurrent: () -> Boolean
)

internal class AurralConfirmationQueueManager(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient,
	private val nowMillis: () -> Long,
	private val onArtistStateChanged: () -> Unit,
	private val onArtistMonitoringConfirmed: (artistMbid: String, artistName: String, monitored: Boolean) -> Unit,
	private val confirmationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
	private val lock = Any()
	private val owners = mutableMapOf<String, AurralMonitoringOperation>()
	private val submitting = mutableSetOf<AurralMonitoringOperation>()
	private val confirmationJobs = mutableMapOf<String, Job>()
	private val _confirmationQueue = MutableStateFlow<List<AurralConfirmationQueueItem>>(emptyList())
	val confirmationQueue = _confirmationQueue.asStateFlow()

	fun beginArtistMonitoring(
		baseUrl: String,
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		submissionJob: Job
	): AurralMonitoringOperation {
		val username = preferenceManager.aurralUsername.trim()
		val password = preferenceManager.aurralPassword.trim()
		val headers = preferenceManager.aurralRequestHeadersMap()
		val operation = AurralMonitoringOperation(
			id = aurralArtistMonitoringConfirmationId(artistMbid),
			baseUrl = baseUrl, artistMbid = artistMbid, artistName = artistName,
			monitored = monitored, submissionJob = submissionJob,
			configurationIsCurrent = {
				preferenceManager.aurralEnabled &&
					configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) == baseUrl &&
					preferenceManager.aurralUsername.trim() == username &&
					preferenceManager.aurralPassword.trim() == password &&
					preferenceManager.aurralRequestHeadersMap() == headers
			}
		)
		val replacedJobs = synchronized(lock) {
			if (!operation.configurationIsCurrent()) throw CancellationException("Aurral configuration changed")
			val previous = owners.put(operation.id, operation)
			val jobs = listOfNotNull(previous?.submissionJob, confirmationJobs.remove(operation.id))
			if (previous != null) submitting.remove(previous)
			submitting.add(operation)
			upsertLocked(operation.item(AurralConfirmationStatus.Pending, "Submitting artist monitoring to Aurral."))
			jobs
		}
		// Never run cancellation handlers while holding the publication lock.
		replacedJobs.forEach { it.cancel() }
		return operation
	}

	fun ensureCurrent(operation: AurralMonitoringOperation) = synchronized(lock) {
		ensureCurrentLocked(operation)
	}

	fun submissionAccepted(operation: AurralMonitoringOperation) = synchronized(lock) {
		ensureCurrentLocked(operation)
		preferenceManager.markIntegrationServiceAvailable(IntegrationService.Aurral)
		upsertLocked(operation.item(AurralConfirmationStatus.Pending, "Waiting for Aurral to confirm artist monitoring."))
	}

	fun confirm(operation: AurralMonitoringOperation) = synchronized(lock) {
		ensureCurrentLocked(operation)
		upsertLocked(operation.item(AurralConfirmationStatus.Confirmed,
			if (operation.monitored) "Aurral confirmed artist monitoring." else "Aurral confirmed monitoring stopped."))
		onArtistMonitoringConfirmed(operation.artistMbid, operation.artistName, operation.monitored)
	}

	fun fail(operation: AurralMonitoringOperation, error: Exception, submissionFailed: Boolean = false) = synchronized(lock) {
		if (error is CancellationException) throw error
		ensureCurrentLocked(operation)
		upsertLocked(operation.item(AurralConfirmationStatus.Failed,
			error.message ?: error::class.simpleName ?: "Aurral confirmation failed."))
		if (submissionFailed) preferenceManager.markIntegrationServiceDown(IntegrationService.Aurral)
	}

	fun abandon(operation: AurralMonitoringOperation) {
		val worker = synchronized(lock) {
			if (owners[operation.id] !== operation) return@synchronized null
			owners.remove(operation.id)
			submitting.remove(operation)
			val queued = _confirmationQueue.value.firstOrNull { it.id == operation.id }
			if (queued?.status == AurralConfirmationStatus.Pending) removeLocked(operation.id)
			confirmationJobs.remove(operation.id)
		}
		worker?.cancel()
	}

	fun finishSubmission(operation: AurralMonitoringOperation) = synchronized(lock) {
		submitting.remove(operation)
		if (owners[operation.id] === operation && operation.id !in confirmationJobs) owners.remove(operation.id)
		Unit
	}

	fun cancel(clearQueue: Boolean) {
		val jobs = synchronized(lock) {
			val pendingJobs = owners.values.map { it.submissionJob } + confirmationJobs.values
			owners.clear()
			submitting.clear()
			confirmationJobs.clear()
			if (clearQueue && _confirmationQueue.value.isNotEmpty()) {
				_confirmationQueue.value = emptyList()
				onArtistStateChanged()
			}
			pendingJobs
		}
		jobs.forEach { it.cancel() }
	}

	fun startArtistMonitoringConfirmationWorker(
		operation: AurralMonitoringOperation,
		requestHeaders: Map<String, String>
	) {
		val worker = synchronized(lock) {
			ensureCurrentLocked(operation)
			check(operation.id !in confirmationJobs)
			confirmationScope.launch(start = CoroutineStart.LAZY) {
				try {
					while (true) {
						currentCoroutineContext().ensureActive()
						ensureCurrent(operation)
						val monitored = apiClient.fetchArtistMonitoringConfirmation(
							operation.baseUrl, requestHeaders, operation.artistMbid
						)
						currentCoroutineContext().ensureActive()
						ensureCurrent(operation)
						if (monitored == operation.monitored) {
							confirm(operation)
							return@launch
						}
						// A queued server job has no terminal failure receipt; absence is not failure.
						delay(AURRAL_MONITOR_CONFIRMATION_DELAY)
					}
				} catch (error: CancellationException) {
					abandon(operation)
					throw error
				} catch (error: Exception) {
					try {
						currentCoroutineContext().ensureActive()
						fail(operation, error)
						Logger.w(TAG, "Aurral artist monitoring confirmation lookup failed", error)
					} catch (cancelled: CancellationException) {
						abandon(operation)
						throw cancelled
					}
				} finally {
					synchronized(lock) {
						if (owners[operation.id] === operation) {
							confirmationJobs.remove(operation.id)
							if (operation !in submitting) owners.remove(operation.id)
						}
					}
				}
			}.also { confirmationJobs[operation.id] = it }
		}
		worker.start()
	}

	private fun ensureCurrentLocked(operation: AurralMonitoringOperation) {
		if (owners[operation.id] !== operation || !operation.configurationIsCurrent()) {
			throw CancellationException("Aurral monitoring operation superseded or configuration changed")
		}
	}

	private fun upsertLocked(item: AurralConfirmationQueueItem) {
		val retained = _confirmationQueue.value.filterNot { it.id == item.id }
			.takeLast(AURRAL_CONFIRMATION_QUEUE_RETAINED_ITEMS - 1)
		_confirmationQueue.value = retained + item
	}

	private fun removeLocked(id: String) {
		_confirmationQueue.value = _confirmationQueue.value.filterNot { it.id == id }
		onArtistStateChanged()
	}

	private fun AurralMonitoringOperation.item(status: AurralConfirmationStatus, message: String) = AurralConfirmationQueueItem(
		id = id, type = AurralConfirmationType.ArtistMonitoring, status = status,
		title = artistName, artistMbid = artistMbid, expectedMonitored = monitored,
		message = message, updatedAtMillis = nowMillis()
	)
}
