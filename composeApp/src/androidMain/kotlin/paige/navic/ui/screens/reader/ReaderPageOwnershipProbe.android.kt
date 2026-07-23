package paige.navic.ui.screens.reader

import karacken.curl.PageSurfaceOwnershipResult
import java.util.concurrent.atomic.AtomicLong

internal class ReaderPageApplicationOwnershipEpoch(
	private val onAdvanced: (Long) -> Unit = {}
) {
	private val epoch = AtomicLong()

	fun current(): Long = epoch.get()

	fun ownerMutationCommitted(): Long =
		epoch.incrementAndGet().also(onAdvanced)

	fun captureStable(
		capture: (Long) -> ReaderPageApplicationOwnershipSnapshot
	): ReaderPageApplicationOwnershipSnapshot? {
		val before = current()
		val snapshot = capture(before)
		check(snapshot.ownershipEpoch == before)
		return snapshot.takeIf { current() == before }
	}
}

internal data class ReaderPageApplicationOwnershipSnapshot(
	val ownershipEpoch: Long,
	val adapterResidents: Int,
	val adapterResidentLimit: Int,
	val adapterDecodedBitmaps: Int,
	val adapterDecodedBitmapLimit: Int,
	val cacheDecodedBitmaps: Int,
	val cacheDecodedBitmapLimit: Int,
	val stagedPublications: Int,
	val stagedPublicationLimit: Int,
	val pendingCallbacks: Int,
	val pendingCallbackLimit: Int,
	val relocationReservations: Int,
	val queuedRelocations: Int,
	val relocationTokens: Int,
	val relocationTokenLimit: Int
) {
	init {
		require(relocationTokens == relocationReservations + queuedRelocations)
	}
}

internal data class ReaderPageRendererOwnershipSnapshot(
	val activeDeckLeases: Int,
	val activeDeckLeaseLimit: Int,
	val pendingDeckLeases: Int,
	val pendingDeckLeaseLimit: Int,
	val releaseInFlightDeckLeases: Int,
	val releaseInFlightDeckLeaseLimit: Int,
	val orphanDeckLeases: Int,
	val orphanDeckLeaseLimit: Int,
	val rendererTextures: Int,
	val rendererTextureLimit: Int,
	val pendingCallbacks: Int,
	val pendingCallbackLimit: Int
)

internal sealed interface ReaderPageRendererOwnershipResult {
	data class Available(
		val snapshot: ReaderPageRendererOwnershipSnapshot
	) : ReaderPageRendererOwnershipResult

	data class Unavailable(
		val status: PageSurfaceOwnershipResult.Status
	) : ReaderPageRendererOwnershipResult {
		init {
			require(status != PageSurfaceOwnershipResult.Status.AVAILABLE)
		}
	}
}

internal sealed interface ReaderPageOwnershipUnavailableReason {
	data object ApplicationEpochChanged :
		ReaderPageOwnershipUnavailableReason

	data class Renderer(
		val status: PageSurfaceOwnershipResult.Status
	) : ReaderPageOwnershipUnavailableReason {
		init {
			require(status != PageSurfaceOwnershipResult.Status.AVAILABLE)
		}
	}
}

internal data class ReaderPageOwnershipBounds(
	val adapterResidentLimit: Int,
	val adapterDecodedBitmapLimit: Int,
	val cacheDecodedBitmapLimit: Int,
	val stagedPublicationLimit: Int,
	val activeDeckLeaseLimit: Int,
	val pendingDeckLeaseLimit: Int,
	val releaseInFlightDeckLeaseLimit: Int,
	val orphanDeckLeaseLimit: Int,
	val rendererTextureLimit: Int,
	val pendingCallbackLimit: Int,
	val relocationTokenLimit: Int
) {
	init {
		require(
			listOf(
				adapterResidentLimit,
				adapterDecodedBitmapLimit,
				cacheDecodedBitmapLimit,
				stagedPublicationLimit,
				activeDeckLeaseLimit,
				pendingDeckLeaseLimit,
				releaseInFlightDeckLeaseLimit,
				orphanDeckLeaseLimit,
				rendererTextureLimit,
				pendingCallbackLimit,
				relocationTokenLimit
			).all { it >= 0 }
		)
	}

	fun contains(snapshot: ReaderPageOwnershipSnapshot): Boolean =
		snapshot.adapterResidents <= adapterResidentLimit &&
			snapshot.adapterDecodedBitmaps <= adapterDecodedBitmapLimit &&
			snapshot.cacheDecodedBitmaps <= cacheDecodedBitmapLimit &&
			snapshot.stagedPublications <= stagedPublicationLimit &&
			snapshot.activeDeckLeases <= activeDeckLeaseLimit &&
			snapshot.pendingDeckLeases <= pendingDeckLeaseLimit &&
			snapshot.releaseInFlightDeckLeases <=
				releaseInFlightDeckLeaseLimit &&
			snapshot.orphanDeckLeases <= orphanDeckLeaseLimit &&
			snapshot.rendererTextures <= rendererTextureLimit &&
			snapshot.pendingCallbacks <= pendingCallbackLimit &&
			snapshot.relocationTokens <= relocationTokenLimit
}

internal data class ReaderPageOwnershipSnapshot(
	val adapterResidents: Int,
	val adapterDecodedBitmaps: Int,
	val cacheDecodedBitmaps: Int,
	val stagedPublications: Int,
	val activeDeckLeases: Int,
	val pendingDeckLeases: Int,
	val releaseInFlightDeckLeases: Int,
	val orphanDeckLeases: Int,
	val rendererTextures: Int,
	val pendingCallbacks: Int,
	val relocationReservations: Int,
	val queuedRelocations: Int,
	val relocationTokens: Int,
	val bounds: ReaderPageOwnershipBounds
) {
	init {
		require(relocationTokens == relocationReservations + queuedRelocations)
	}

	fun withinBounds(): Boolean = bounds.contains(this)

	fun isClosedBaseline(): Boolean =
		adapterResidents == 0 &&
			adapterDecodedBitmaps == 0 &&
			cacheDecodedBitmaps == 0 &&
			stagedPublications == 0 &&
			activeDeckLeases == 0 &&
			pendingDeckLeases == 0 &&
			releaseInFlightDeckLeases == 0 &&
			orphanDeckLeases == 0 &&
			rendererTextures == 0 &&
			pendingCallbacks == 0 &&
			relocationReservations == 0 &&
			queuedRelocations == 0 &&
			relocationTokens == 0
}

internal interface ReaderPageRendererOwnershipHost {
	fun requestOwnershipSnapshot(
		onResult: (ReaderPageRendererOwnershipResult) -> Unit
	)

	fun setCallbackCapacityListener(listener: () -> Unit) = Unit

	fun clearCallbackCapacityListener(listener: () -> Unit) = Unit
}

internal class ReaderPageOwnershipProbe(
	private val applicationSnapshot:
		() -> ReaderPageApplicationOwnershipSnapshot?,
	private val rendererHost: ReaderPageRendererOwnershipHost
) {
	fun request(
		onResult: (Result<ReaderPageOwnershipSnapshot>) -> Unit
	) {
		val before = applicationSnapshot()
		if (before == null) {
			onResult(applicationEpochChanged())
			return
		}
		rendererHost.requestOwnershipSnapshot { rendererResult ->
			val renderer = when (rendererResult) {
				is ReaderPageRendererOwnershipResult.Available ->
					rendererResult.snapshot
				is ReaderPageRendererOwnershipResult.Unavailable -> {
					onResult(
						Result.failure(
							ReaderPageOwnershipUnavailableException(
								ReaderPageOwnershipUnavailableReason.Renderer(
									rendererResult.status
								)
							)
						)
					)
					return@requestOwnershipSnapshot
				}
			}
			val application = applicationSnapshot()
			if (application == null ||
				application.ownershipEpoch != before.ownershipEpoch
			) {
				onResult(applicationEpochChanged())
				return@requestOwnershipSnapshot
			}
			onResult(Result.success(combine(application, renderer)))
		}
	}

	private fun applicationEpochChanged(): Result<ReaderPageOwnershipSnapshot> =
		Result.failure(
			ReaderPageOwnershipUnavailableException(
				ReaderPageOwnershipUnavailableReason.ApplicationEpochChanged
			)
		)

	private fun combine(
		application: ReaderPageApplicationOwnershipSnapshot,
		renderer: ReaderPageRendererOwnershipSnapshot
	): ReaderPageOwnershipSnapshot = ReaderPageOwnershipSnapshot(
		adapterResidents = application.adapterResidents,
		adapterDecodedBitmaps = application.adapterDecodedBitmaps,
		cacheDecodedBitmaps = application.cacheDecodedBitmaps,
		stagedPublications = application.stagedPublications,
		activeDeckLeases = renderer.activeDeckLeases,
		pendingDeckLeases = renderer.pendingDeckLeases,
		releaseInFlightDeckLeases = renderer.releaseInFlightDeckLeases,
		orphanDeckLeases = renderer.orphanDeckLeases,
		rendererTextures = renderer.rendererTextures,
		pendingCallbacks =
			application.pendingCallbacks + renderer.pendingCallbacks,
		relocationReservations = application.relocationReservations,
		queuedRelocations = application.queuedRelocations,
		relocationTokens = application.relocationTokens,
		bounds = ReaderPageOwnershipBounds(
			adapterResidentLimit = application.adapterResidentLimit,
			adapterDecodedBitmapLimit = application.adapterDecodedBitmapLimit,
			cacheDecodedBitmapLimit = application.cacheDecodedBitmapLimit,
			stagedPublicationLimit = application.stagedPublicationLimit,
			activeDeckLeaseLimit = renderer.activeDeckLeaseLimit,
			pendingDeckLeaseLimit = renderer.pendingDeckLeaseLimit,
			releaseInFlightDeckLeaseLimit =
				renderer.releaseInFlightDeckLeaseLimit,
			orphanDeckLeaseLimit = renderer.orphanDeckLeaseLimit,
			rendererTextureLimit = renderer.rendererTextureLimit,
			pendingCallbackLimit =
				application.pendingCallbackLimit +
					renderer.pendingCallbackLimit,
			relocationTokenLimit = application.relocationTokenLimit
		)
	)
}

internal class ReaderPageOwnershipUnavailableException(
	val reason: ReaderPageOwnershipUnavailableReason
) : IllegalStateException("Reader ownership snapshot unavailable: $reason")

internal class ReaderPageColdOwnershipAdmission(
	private val ownershipProbe: ReaderPageOwnershipProbe,
	private val rendererHost: ReaderPageRendererOwnershipHost,
	private val acceptsColdBaseline: (ReaderPageOwnershipSnapshot) -> Boolean,
	private val onUnavailable: (ReaderPageOwnershipUnavailableReason) -> Unit,
	private val onAdmitted: (ReaderPageOwnershipSnapshot) -> Unit,
	private val onCallbackCapacityAvailable: () -> Unit = {}
) : AutoCloseable {
	private var demand = false
	private var inFlight = false
	private var closed = false
	private val capacityListener: () -> Unit = {
		retryOnOwnershipEdge()
		onCallbackCapacityAvailable()
	}

	init {
		rendererHost.setCallbackCapacityListener(capacityListener)
	}

	fun requestColdBaseline() {
		if (closed) return
		demand = true
		tryRequest()
	}

	fun retryOnOwnershipEdge() {
		if (!closed && demand) tryRequest()
	}

	override fun close() {
		if (closed) return
		closed = true
		demand = false
		rendererHost.clearCallbackCapacityListener(capacityListener)
	}

	private fun tryRequest() {
		if (closed || !demand || inFlight) return
		inFlight = true
		ownershipProbe.request { result ->
			if (closed) return@request
			inFlight = false
			result.fold(
				onSuccess = { snapshot ->
					if (acceptsColdBaseline(snapshot)) {
						demand = false
						onAdmitted(snapshot)
					}
				},
				onFailure = { failure ->
					val unavailable =
						failure as ReaderPageOwnershipUnavailableException
					onUnavailable(unavailable.reason)
				}
			)
		}
	}
}
