package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind

internal enum class ReaderTransitionMode { Shadow, Active }

internal fun interface ReaderTransitionClockRegistration {
	fun cancel()
}

internal interface ReaderTransitionClock {
	fun nowMillis(): Long
	fun schedule(atMillis: Long, action: () -> Unit): ReaderTransitionClockRegistration?
}

internal data class ReaderPhysicalReleaseRetentionSnapshot(
	val activeLeaseCount: Int,
	val issuedCount: Int,
	val confirmingCount: Int,
	val terminalTombstoneCount: Int,
	val terminalTombstoneCapacity: Int,
	val retirementFenceState: ReaderTransitionRetirementFenceState
)

private class ReaderPhysicalReleaseBookkeeper<Lease>(
	private val keyOf: (Lease) -> ReaderTransitionResourceKey,
	private val release: (Lease, () -> Unit) -> Unit
) {
	private val leases = linkedMapOf<ReaderTransitionResourceKey, Lease>()
	private val issued = linkedSetOf<ReaderTransitionResourceKey>()
	private val terminalTombstones = ReaderTransitionTerminalTombstones()

	fun register(lease: Lease): Boolean {
		val key = keyOf(lease)
		if (terminalTombstones.rejectsRegistration(key)) return false
		val existing = leases[key]
		check(existing == null || existing == lease) {
			"A transition resource key cannot identify two physical release leases"
		}
		if (existing != null) return false
		leases[key] = lease
		return true
	}

	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (ReaderTransitionFact) -> Unit
	): Boolean {
		check(command.transitionId == command.key.transitionId)
		val lease = leases[command.key]
		if (lease == null) {
			if (terminalTombstones.containsOrFenced(command.key)) return false
			error("Physical release requires an exact registered lease")
		}
		if (!issued.add(command.key)) return false
		release(lease) {
			if (issued.remove(command.key)) {
				check(leases.remove(command.key) == lease) {
					"Physical release confirmation must match its exact registered lease"
				}
				check(terminalTombstones.record(command.key, fromActiveRegistration = true)) {
					"Physical release confirmation cannot retire an already terminal resource"
				}
				onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
			}
		}
		return true
	}

	fun retentionSnapshot(): ReaderPhysicalReleaseRetentionSnapshot {
		val terminal = terminalTombstones.terminalSnapshot()
		return ReaderPhysicalReleaseRetentionSnapshot(
			activeLeaseCount = leases.size,
			issuedCount = issued.size,
			confirmingCount = 0,
			terminalTombstoneCount = terminal.terminalTombstoneCount,
			terminalTombstoneCapacity = terminal.terminalTombstoneCapacity,
			retirementFenceState = terminal.retirementFenceState
		)
	}
}

internal class ReaderDeckPhysicalReleasePort(
	release: (ReaderDeckLease, () -> Unit) -> Unit
) {
	private val bookkeeper = ReaderPhysicalReleaseBookkeeper(ReaderDeckLease::resourceKey, release)

	fun register(lease: ReaderDeckLease): Boolean = bookkeeper.register(lease)

	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (ReaderTransitionFact) -> Unit
	): Boolean = bookkeeper.release(command, onFact)

	fun retentionSnapshot(): ReaderPhysicalReleaseRetentionSnapshot = bookkeeper.retentionSnapshot()
}

internal class ReaderRasterPhysicalReleasePort(
	release: (ReaderRasterPreparationLease, () -> Unit) -> Unit
) {
	private val bookkeeper = ReaderPhysicalReleaseBookkeeper(
		ReaderRasterPreparationLease::resourceKey,
		release
	)

	fun register(lease: ReaderRasterPreparationLease): Boolean = bookkeeper.register(lease)

	fun release(
		command: ReaderTransitionCommand.ReleaseResource,
		onFact: (ReaderTransitionFact) -> Unit
	): Boolean = bookkeeper.release(command, onFact)

	fun retentionSnapshot(): ReaderPhysicalReleaseRetentionSnapshot = bookkeeper.retentionSnapshot()
}

internal interface ReaderRasterPreparationCommandPort {
	fun prepare(
		lease: ReaderRasterPreparationLease,
		callbacks: ReaderRasterLeaseFactEmitter
	)

	fun release(lease: ReaderRasterPreparationLease, onReleased: () -> Unit)
	fun cancel(transitionId: ReaderTransitionId)
}

internal interface ReaderRendererDeckCommandPort {
	fun reserve(
		lease: ReaderDeckLease,
		callbacks: ReaderDeckLeaseFactEmitter
	)

	fun release(lease: ReaderDeckLease, onReleased: () -> Unit)
	fun cancelPreparation(transitionId: ReaderTransitionId)
}

internal class ReaderTask4TransitionPorts(
	override val clock: ReaderTransitionClock,
	private val raster: ReaderRasterPreparationCommandPort,
	private val renderer: ReaderRendererDeckCommandPort
) : ReaderResumableTransitionPorts {
	private val deckPhysicalRelease = ReaderDeckPhysicalReleasePort(renderer::release)
	private val rasterPhysicalRelease = ReaderRasterPhysicalReleasePort(raster::release)

	override fun acceptsFact(fact: ReaderTransitionFact): Boolean = fact.isTask4CoordinatorFact()

	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		when (command) {
			is ReaderTransitionCommand.RequestRasterPreparation -> {
				val preparationGeneration = command.binding.preparationGeneration
				val rasterGeneration = command.binding.rasterGeneration
				if (preparationGeneration == null || rasterGeneration == null) {
					onFact(
						ReaderTransitionFact.RasterFailed(
							command.transitionId,
							ReaderTransitionFailureReason.PortRejected
						)
					)
					return
				}
				val lease = ReaderRasterPreparationLease(
					transitionId = command.transitionId,
					binding = command.binding,
					preparationGeneration = preparationGeneration,
					rasterGeneration = rasterGeneration,
					resourceKey = ReaderTransitionResourceKey(
						command.transitionId,
						ReaderTransitionResourceKind.Raster,
						rasterGeneration
					)
				)
				if (!rasterPhysicalRelease.register(lease)) return
				val callbacks = ReaderRasterLeaseFactEmitter(lease, onFact)
				callbacks.registerResource()
				raster.prepare(lease, callbacks)
			}
			is ReaderTransitionCommand.ReserveDeck -> {
				val textureGeneration = command.binding.textureGeneration
				if (textureGeneration == null) {
					onFact(
						ReaderTransitionFact.RasterFailed(
							command.transitionId,
							ReaderTransitionFailureReason.PortRejected
						)
					)
					return
				}
				val key = ReaderTransitionResourceKey(
					command.transitionId,
					ReaderTransitionResourceKind.Deck,
					textureGeneration
				)
				val lease = readerDeckLeaseOrNull(command, key)
				if (lease == null) {
					onFact(
						ReaderTransitionFact.RasterFailed(
							command.transitionId,
							ReaderTransitionFailureReason.PortRejected
						)
					)
					return
				}
				if (!deckPhysicalRelease.register(lease)) return
				val callbacks = ReaderDeckLeaseFactEmitter(lease, onFact)
				callbacks.onReserved(lease)
				renderer.reserve(lease, callbacks)
			}
			is ReaderTransitionCommand.ReleaseResource -> when (command.key.kind) {
				ReaderTransitionResourceKind.Deck -> deckPhysicalRelease.release(command, onFact)
				ReaderTransitionResourceKind.Raster -> rasterPhysicalRelease.release(command, onFact)
				ReaderTransitionResourceKind.CallbackRegistration,
				ReaderTransitionResourceKind.FrameHandoff -> error(
					"Task 4 ports cannot release unsupported resource kinds"
				)
			}
			is ReaderTransitionCommand.CancelOwnedWork -> {
				raster.cancel(command.transitionId)
				renderer.cancelPreparation(command.transitionId)
			}
			is ReaderTransitionCommand.RequestSemanticSynchronization,
			is ReaderTransitionCommand.RequestFramePresentation,
			is ReaderTransitionCommand.ApplyInputLease -> error(
				"Task 4 ports cannot execute semantic, frame, or input commands"
			)
		}
	}

	fun registerAdoptedLease(lease: ReaderDeckLease): Boolean {
		check(lease.provenance == ReaderTransitionResourceProvenance.AdoptedLegacy)
		return deckPhysicalRelease.register(lease)
	}
}

internal interface ReaderResumableTransitionPorts {
	val clock: ReaderTransitionClock

	fun acceptsFact(fact: ReaderTransitionFact): Boolean = true

	fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	)
}

internal class ReaderCutoverTransitionPorts(
	private val delegate: ReaderResumableTransitionPorts,
	private val deckCutover: ReaderDeckAdmissionCutover
) : ReaderResumableTransitionPorts {
	override val clock: ReaderTransitionClock
		get() = delegate.clock

	override fun acceptsFact(fact: ReaderTransitionFact): Boolean = delegate.acceptsFact(fact)

	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		when (command) {
			is ReaderTransitionCommand.ReserveDeck -> check(deckCutover.coordinatorAdmissionOpen) {
				"Coordinator deck admission is not active"
			}
			is ReaderTransitionCommand.ReleaseResource -> check(deckCutover.coordinatorCommandsAllowed) {
				"Coordinator resource release is not active"
			}
			else -> Unit
		}
		delegate.issue(command, onFact)
	}
}

internal class AndroidReaderTransitionClock(
	private val handler: Handler = Handler(Looper.getMainLooper())
) : ReaderTransitionClock {
	override fun nowMillis(): Long = SystemClock.uptimeMillis()

	override fun schedule(
		atMillis: Long,
		action: () -> Unit
	): ReaderTransitionClockRegistration? {
		val runnable = Runnable(action)
		val accepted = handler.postAtTime(runnable, atMillis)
		return if (accepted) {
			ReaderTransitionClockRegistration { handler.removeCallbacks(runnable) }
		} else {
			null
		}
	}
}

internal class ReaderShadowTransitionPorts(
	override val clock: ReaderTransitionClock = AndroidReaderTransitionClock()
) : ReaderResumableTransitionPorts {
	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		error("Shadow transition ports cannot issue mutating commands")
	}
}
