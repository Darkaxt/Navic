package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageRelocationDrain
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationReservation
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection

internal data class ReaderPageRelocationReservationMetadata(
	val gestureId: Long,
	val sourceOrdinal: Int,
	val foliateSessionId: String,
	val reservedRasterGeneration: Long,
	val reservedTextureGeneration: Long
)

internal sealed interface ReaderPageRelocationStartResult {
	data object Admitted : ReaderPageRelocationStartResult

	data class TerminalPublished(
		val outcome: ReaderPageGestureTerminalOutcome,
		val detail: ReaderPageGestureTerminalDetail
	) : ReaderPageRelocationStartResult
}

internal sealed interface ReaderPageRelocationCommitResult {
	data class Published(
		val request: ReaderPageRelocationRequest
	) : ReaderPageRelocationCommitResult

	data object ReservationNotOwned : ReaderPageRelocationCommitResult
	data object GenerationOrSessionDrift : ReaderPageRelocationCommitResult
	data object TerminalNotPublished : ReaderPageRelocationCommitResult
}

internal class ReaderPageRelocationGestureCoordinator(
	private val queue: ReaderPageRelocationQueue,
	private val foregroundWebViewOwnership: ReaderForegroundWebViewOwnership =
		ReaderForegroundWebViewOwnership(),
	private val onQueued: (ReaderPageRelocationRequest) -> Unit = {},
	private val onRejected: (ReaderPageRelocationRequest) -> Unit = {}
) {
	private data class Owner(
		val reservation: ReaderPageRelocationReservation,
		val metadata: ReaderPageRelocationReservationMetadata,
		var liveClaim: ReaderForegroundWebViewLiveClaim? = null,
		var rendererAdmissionOpen: Boolean = false,
		var synchronousTerminal: ReaderPageRelocationStartResult.TerminalPublished? = null
	)

	private val owners = mutableMapOf<Long, Owner>()

	fun start(
		metadata: ReaderPageRelocationReservationMetadata,
		protocolActionMasked: Int,
		rendererAdmission: () -> Boolean,
		publishTerminal: (
			ReaderPageGestureTerminalOutcome,
			ReaderPageGestureTerminalDetail
		) -> Boolean
	): ReaderPageRelocationStartResult {
		require(metadata.gestureId > 0L)
		require(metadata.sourceOrdinal >= 0)
		require(metadata.foliateSessionId.isNotBlank())
		require(metadata.reservedRasterGeneration >= 0L)
		require(metadata.reservedTextureGeneration >= 0L)
		require(protocolActionMasked >= 0)
		val owner = when (val result = queue.reserve(metadata.gestureId)) {
			is ReaderPageRelocationReservationResult.CapacityReached -> {
				val detail = ReaderPageGestureTerminalDetail.RelocationCapacityUnavailable(
					result.occupied,
					result.capacity
				)
				check(
					publishTerminal(
						ReaderPageGestureTerminalOutcome.RejectedSettling,
						detail
					)
				) { "Relocation-capacity terminal was not published" }
				return ReaderPageRelocationStartResult.TerminalPublished(
					ReaderPageGestureTerminalOutcome.RejectedSettling,
					detail
				)
			}
			is ReaderPageRelocationReservationResult.DuplicateGesture -> {
				val detail =
					ReaderPageGestureTerminalDetail.RelocationReservationProtocolFailure(
						metadata.gestureId
					)
				check(
					publishTerminal(
						ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
						detail
					)
				) { "Duplicate-relocation terminal was not published" }
				return ReaderPageRelocationStartResult.TerminalPublished(
					ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
					detail
				)
			}
			is ReaderPageRelocationReservationResult.Reserved -> {
				Owner(result.reservation, metadata).also { created ->
					check(owners.put(metadata.gestureId, created) == null)
				}
			}
		}

		val liveClaim = try {
			foregroundWebViewOwnership.acquireLive(metadata.gestureId)
		} catch (_: Throwable) {
			check(releaseOwner(metadata.gestureId) === owner) {
				"Unavailable foreground ownership lost relocation reservation"
			}
			val detail = ReaderPageGestureTerminalDetail.RecoveryFailed
			check(
				publishTerminal(
					ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
					detail
				)
			) { "Foreground-ownership terminal was not published" }
			return ReaderPageRelocationStartResult.TerminalPublished(
				ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
				detail
			)
		}
		owner.liveClaim = liveClaim
		owner.rendererAdmissionOpen = true
		val accepted = try {
			rendererAdmission()
		} catch (failure: Throwable) {
			releaseOwner(metadata.gestureId)
			throw failure
		} finally {
			owner.rendererAdmissionOpen = false
		}
		owner.synchronousTerminal?.let { terminal ->
			check(owners[metadata.gestureId] !== owner)
			return terminal
		}
		if (!accepted) {
			check(releaseOwner(metadata.gestureId) === owner) {
				"Renderer returned false after relocation ownership vanished"
			}
			val detail = ReaderPageGestureTerminalDetail.TouchProtocolFailure(
				protocolActionMasked
			)
			check(
				publishTerminal(
					ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
					detail
				)
			) { "Touch-protocol terminal was not published" }
			return ReaderPageRelocationStartResult.TerminalPublished(
				ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
				detail
			)
		}
		check(owners[metadata.gestureId] === owner) {
			"Accepted renderer admission lost relocation ownership"
		}
		return ReaderPageRelocationStartResult.Admitted
	}

	fun commit(
		gestureId: Long,
		settledSourceTextureGeneration: Long,
		promotedRasterGeneration: Long,
		promotedTextureGeneration: Long,
		destinationOrdinal: Int,
		logicalDirection: ReaderPageTurnDirection,
		currentFoliateSessionId: String,
		publishDriftTerminal: (
			ReaderPageGestureTerminalOutcome,
			ReaderPageGestureTerminalDetail
		) -> Boolean,
		publishCommittedTerminal: () -> Boolean,
		dispatch: (
			ReaderPageRelocationRequest,
			ReaderForegroundWebViewLiveClaim
		) -> Unit
	): ReaderPageRelocationCommitResult {
		require(settledSourceTextureGeneration >= 0L)
		require(promotedRasterGeneration >= 0L)
		require(promotedTextureGeneration >= 0L)
		require(destinationOrdinal >= 0)
		require(currentFoliateSessionId.isNotBlank())
		val owner = owners[gestureId]
			?: return ReaderPageRelocationCommitResult.ReservationNotOwned
		if (
			currentFoliateSessionId != owner.metadata.foliateSessionId ||
			promotedRasterGeneration != owner.metadata.reservedRasterGeneration ||
			settledSourceTextureGeneration != owner.metadata.reservedTextureGeneration
		) {
			val detail =
				ReaderPageGestureTerminalDetail.RelocationGenerationOrSessionDrift(gestureId)
			val published = try {
				publishDriftTerminal(
					ReaderPageGestureTerminalOutcome.FailedRecovery,
					detail
				)
			} finally {
				check(releaseOwner(gestureId) === owner) {
					"Drift terminal lost its exact relocation owner"
				}
			}
			return if (published) {
				ReaderPageRelocationCommitResult.GenerationOrSessionDrift
			} else {
				ReaderPageRelocationCommitResult.TerminalNotPublished
			}
		}
		val liveClaim = checkNotNull(owner.liveClaim) {
			"Committed relocation omitted foreground ownership"
		}
		val transfer = try {
			queue.enqueueReserved(
				reservation = owner.reservation,
				rasterGeneration = promotedRasterGeneration,
				textureGeneration = promotedTextureGeneration,
				sourceOrdinal = owner.metadata.sourceOrdinal,
				destinationOrdinal = destinationOrdinal,
				logicalDirection = logicalDirection,
				foliateSessionId = currentFoliateSessionId
			)
		} catch (failure: Throwable) {
			check(releaseOwner(gestureId) === owner) {
				"Failed relocation enqueue lost its exact owner"
			}
			throw failure
		}
		val request = when (transfer) {
			is ReaderPageRelocationTransferResult.Enqueued -> transfer.request
			ReaderPageRelocationTransferResult.ReservationNotOwned -> {
				check(owners.remove(gestureId) === owner)
				releaseLiveClaim(owner)
				return ReaderPageRelocationCommitResult.ReservationNotOwned
			}
		}
		check(owners.remove(gestureId) === owner) {
			"Committed relocation lost its exact owner"
		}
		try {
			onQueued(request)
		} catch (failure: Throwable) {
			cancelUnpublishedTransfer(request, owner)
			throw failure
		}
		val published = try {
			publishCommittedTerminal()
		} catch (failure: Throwable) {
			cancelUnpublishedTransfer(request, owner)
			throw failure
		}
		if (!published) {
			cancelUnpublishedTransfer(request, owner)
			return ReaderPageRelocationCommitResult.TerminalNotPublished
		}
		dispatch(request, liveClaim)
		return ReaderPageRelocationCommitResult.Published(request)
	}

	fun finish(
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		detail: ReaderPageGestureTerminalDetail
	): Boolean {
		val owner = owners[gestureId] ?: return false
		if (owner.rendererAdmissionOpen) {
			check(owner.synchronousTerminal == null) {
				"Renderer published two synchronous terminals"
			}
			owner.synchronousTerminal = ReaderPageRelocationStartResult.TerminalPublished(
				outcome,
				detail
			)
		}
		check(releaseOwner(gestureId) === owner) {
			"Gesture terminal lost its exact relocation owner"
		}
		return true
	}

	private fun cancelUnpublishedTransfer(
		request: ReaderPageRelocationRequest,
		owner: Owner
	) {
		try {
			check(queue.cancelTransferred(request.token.value))
			onRejected(request)
		} finally {
			releaseLiveClaim(owner)
		}
	}

	private fun releaseLiveClaim(owner: Owner) {
		owner.liveClaim?.let(foregroundWebViewOwnership::releaseLive)
		owner.liveClaim = null
	}

	private fun releaseOwner(gestureId: Long): Owner? {
		val owner = owners.remove(gestureId) ?: return null
		val reservationReleased = try {
			queue.release(owner.reservation)
		} finally {
			releaseLiveClaim(owner)
		}
		check(reservationReleased) {
			"Gesture relocation reservation was already terminal"
		}
		return owner
	}

	fun cancelAll(): ReaderPageRelocationDrain {
		val drain = queue.cancelAll()
		check(drain.reservations.size == owners.size)
		check(drain.reservations.all { reservation ->
			owners[reservation.gestureId]?.reservation === reservation
		})
		val cancelledOwners = owners.values.toList()
		owners.clear()
		cancelledOwners.forEach(::releaseLiveClaim)
		return drain
	}

	fun reservationCount(): Int = owners.size
}
