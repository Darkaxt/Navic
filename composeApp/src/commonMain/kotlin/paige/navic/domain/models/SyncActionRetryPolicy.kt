package paige.navic.domain.models

import dev.zt64.subsonic.api.model.SubsonicErrorCode
import kotlinx.coroutines.CancellationException
import paige.navic.data.database.entities.SyncActionEntity

enum class SyncFailureDisposition {
	Terminal,
	Transient
}

const val MAX_SYNC_RETRY_DELAY_MS = 6L * 60L * 60L * 1_000L
private const val INITIAL_SYNC_RETRY_DELAY_MS = 30_000L

fun classifySyncFailure(
	httpStatus: Int? = null,
	subsonicErrorCode: SubsonicErrorCode? = null
): SyncFailureDisposition = when {
	httpStatus != null && httpStatus in 400..499 -> SyncFailureDisposition.Terminal
	httpStatus != null && httpStatus >= 500 -> SyncFailureDisposition.Transient
	subsonicErrorCode != null && subsonicErrorCode != SubsonicErrorCode.GENERIC ->
		SyncFailureDisposition.Terminal
	else -> SyncFailureDisposition.Transient
}

fun syncRetryDelayMs(attemptCount: Int): Long {
	val exponent = (attemptCount - 1).coerceIn(0, 10)
	return (INITIAL_SYNC_RETRY_DELAY_MS * (1L shl exponent))
		.coerceAtMost(MAX_SYNC_RETRY_DELAY_MS)
}

fun SyncActionEntity.afterSyncFailure(
	disposition: SyncFailureDisposition,
	nowEpochMs: Long,
	errorSummary: String
): SyncActionEntity {
	val nextAttemptCount = attemptCount + 1
	return copy(
		attemptCount = nextAttemptCount,
		nextAttemptAtEpochMs = if (disposition == SyncFailureDisposition.Transient) {
			nowEpochMs + syncRetryDelayMs(nextAttemptCount)
		} else {
			0L
		},
		lastError = errorSummary,
		deadLettered = disposition == SyncFailureDisposition.Terminal
	)
}

suspend fun processOrderedSyncActions(
	actions: List<SyncActionEntity>,
	execute: suspend (SyncActionEntity) -> Unit,
	onSuccess: suspend (SyncActionEntity) -> Unit,
	onFailure: suspend (SyncActionEntity, Throwable) -> Unit
) {
	for (action in actions) {
		try {
			execute(action)
			onSuccess(action)
		} catch (error: CancellationException) {
			throw error
		} catch (error: Throwable) {
			onFailure(action, error)
		}
	}
}
