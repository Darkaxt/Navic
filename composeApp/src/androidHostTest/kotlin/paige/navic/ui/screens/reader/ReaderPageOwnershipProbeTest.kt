package paige.navic.ui.screens.reader

import java.io.File
import karacken.curl.PageSurfaceOwnershipResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageOwnershipProbeTest {
	@Test
	fun foregroundWebViewOwnershipCountsHaveIndependentBoundsAndClosedBaseline() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPageOwnershipProbe.android.kt"
		).readText()

		listOf(
			"foregroundPassiveOwners",
			"foregroundPassiveOwnerLimit",
			"foregroundLiveClaims",
			"foregroundLiveClaimLimit",
			"foregroundRestorationCallbacks",
			"foregroundRestorationCallbackLimit"
		).forEach { field -> assertContains(source, field) }
	}

	@Test
	fun combinesEveryOwnerCountAndOwnerExportedBound() {
		var observed: ReaderPageOwnershipSnapshot? = null
		val probe = ReaderPageOwnershipProbe(
			applicationSnapshot = {
				application(
					adapterResidents = 5,
					adapterResidentLimit = 7,
					adapterDecodedBitmaps = 4,
					adapterDecodedBitmapLimit = 6,
					cacheDecodedBitmaps = 2,
					cacheDecodedBitmapLimit = 3,
					stagedPublications = 1,
					stagedPublicationLimit = 2,
					pendingCallbacks = 2,
					pendingCallbackLimit = 4,
					foregroundPassiveOwners = 1,
					foregroundPassiveOwnerLimit = 1,
					foregroundLiveClaims = 3,
					foregroundLiveClaimLimit = 4,
					foregroundRestorationCallbacks = 1,
					foregroundRestorationCallbackLimit = 1,
					relocationReservations = 1,
					queuedRelocations = 2,
					relocationTokens = 3,
					relocationTokenLimit = 4
				)
			},
			rendererHost = immediateHost(
				renderer(
					activeDeckLeases = 1,
					pendingDeckLeases = 1,
					releaseInFlightDeckLeases = 2,
					orphanDeckLeases = 1,
					orphanDeckLeaseLimit = 0,
					rendererTextures = 6,
					pendingCallbacks = 1
				)
			)
		)

		probe.request { observed = it.getOrThrow() }

		val snapshot = requireNotNull(observed)
		assertEquals(5, snapshot.adapterResidents)
		assertEquals(7, snapshot.bounds.adapterResidentLimit)
		assertEquals(4, snapshot.adapterDecodedBitmaps)
		assertEquals(6, snapshot.bounds.adapterDecodedBitmapLimit)
		assertEquals(2, snapshot.cacheDecodedBitmaps)
		assertEquals(3, snapshot.bounds.cacheDecodedBitmapLimit)
		assertEquals(2, snapshot.releaseInFlightDeckLeases)
		assertEquals(4, snapshot.bounds.releaseInFlightDeckLeaseLimit)
		assertEquals(1, snapshot.orphanDeckLeases)
		assertEquals(0, snapshot.bounds.orphanDeckLeaseLimit)
		assertEquals(6, snapshot.rendererTextures)
		assertEquals(8, snapshot.bounds.rendererTextureLimit)
		assertEquals(3, snapshot.pendingCallbacks)
		assertEquals(16, snapshot.bounds.pendingCallbackLimit)
		assertEquals(1, snapshot.foregroundPassiveOwners)
		assertEquals(1, snapshot.bounds.foregroundPassiveOwnerLimit)
		assertEquals(3, snapshot.foregroundLiveClaims)
		assertEquals(4, snapshot.bounds.foregroundLiveClaimLimit)
		assertEquals(1, snapshot.foregroundRestorationCallbacks)
		assertEquals(1, snapshot.bounds.foregroundRestorationCallbackLimit)
		assertEquals(3, snapshot.relocationTokens)
		assertFalse(snapshot.withinBounds())
		assertTrue(snapshot.copy(orphanDeckLeases = 0).withinBounds())
		assertFalse(snapshot.isClosedBaseline())
	}

	@Test
	fun everyOwnershipCategoryIndependentlyBreaksClosedBound() {
		val zeroBounds = ReaderPageOwnershipBounds(
			adapterResidentLimit = 0,
			adapterDecodedBitmapLimit = 0,
			cacheDecodedBitmapLimit = 0,
			stagedPublicationLimit = 0,
			activeDeckLeaseLimit = 0,
			pendingDeckLeaseLimit = 0,
			releaseInFlightDeckLeaseLimit = 0,
			orphanDeckLeaseLimit = 0,
			rendererTextureLimit = 0,
			pendingCallbackLimit = 0,
			foregroundPassiveOwnerLimit = 0,
			foregroundLiveClaimLimit = 0,
			foregroundRestorationCallbackLimit = 0,
			relocationTokenLimit = 0
		)
		val closed = ReaderPageOwnershipSnapshot(
			adapterResidents = 0,
			adapterDecodedBitmaps = 0,
			cacheDecodedBitmaps = 0,
			stagedPublications = 0,
			activeDeckLeases = 0,
			pendingDeckLeases = 0,
			releaseInFlightDeckLeases = 0,
			orphanDeckLeases = 0,
			rendererTextures = 0,
			pendingCallbacks = 0,
			foregroundPassiveOwners = 0,
			foregroundLiveClaims = 0,
			foregroundRestorationCallbacks = 0,
			relocationReservations = 0,
			queuedRelocations = 0,
			relocationTokens = 0,
			bounds = zeroBounds
		)
		assertTrue(closed.withinBounds())
		assertTrue(closed.isClosedBaseline())

		val exceeded = listOf(
			closed.copy(adapterResidents = 1),
			closed.copy(adapterDecodedBitmaps = 1),
			closed.copy(cacheDecodedBitmaps = 1),
			closed.copy(stagedPublications = 1),
			closed.copy(activeDeckLeases = 1),
			closed.copy(pendingDeckLeases = 1),
			closed.copy(releaseInFlightDeckLeases = 1),
			closed.copy(orphanDeckLeases = 1),
			closed.copy(rendererTextures = 1),
			closed.copy(pendingCallbacks = 1),
			closed.copy(foregroundPassiveOwners = 1),
			closed.copy(foregroundLiveClaims = 1),
			closed.copy(foregroundRestorationCallbacks = 1),
			closed.copy(relocationReservations = 1, relocationTokens = 1)
		)
		assertEquals(14, exceeded.size)
		exceeded.forEach {
			assertFalse(it.withinBounds())
			assertFalse(it.isClosedBaseline())
		}
	}

	@Test
	fun applicationMutationDuringRendererRequestRejectsMixedSnapshot() {
		var epoch = 1L
		var rendererCompletion:
			((ReaderPageRendererOwnershipResult) -> Unit)? = null
		var observed: Result<ReaderPageOwnershipSnapshot>? = null
		val probe = ReaderPageOwnershipProbe(
			applicationSnapshot = { application(ownershipEpoch = epoch) },
			rendererHost = object : ReaderPageRendererOwnershipHost {
				override fun requestOwnershipSnapshot(
					onResult: (ReaderPageRendererOwnershipResult) -> Unit
				) {
					rendererCompletion = onResult
				}
			}
		)
		probe.request { observed = it }

		epoch = 2L
		requireNotNull(rendererCompletion).invoke(
			ReaderPageRendererOwnershipResult.Available(renderer())
		)

		val failure = assertIs<ReaderPageOwnershipUnavailableException>(
			requireNotNull(observed).exceptionOrNull()
		)
		assertEquals(
			ReaderPageOwnershipUnavailableReason.ApplicationEpochChanged,
			failure.reason
		)
	}

	@Test
	fun everyRendererUnavailableStatusRemainsTyped() {
		listOf(
			PageSurfaceOwnershipResult.Status.SURFACE_UNAVAILABLE,
			PageSurfaceOwnershipResult.Status.QUEUE_REJECTED,
			PageSurfaceOwnershipResult.Status.CALLBACK_CAPACITY
		).forEach { status ->
			var observed: Result<ReaderPageOwnershipSnapshot>? = null
			ReaderPageOwnershipProbe(
				applicationSnapshot = { application() },
				rendererHost = object : ReaderPageRendererOwnershipHost {
					override fun requestOwnershipSnapshot(
						onResult: (ReaderPageRendererOwnershipResult) -> Unit
					) = onResult(
						ReaderPageRendererOwnershipResult.Unavailable(status)
					)
				}
			).request { observed = it }

			val failure = assertIs<ReaderPageOwnershipUnavailableException>(
				requireNotNull(observed).exceptionOrNull()
			)
			assertEquals(
				ReaderPageOwnershipUnavailableReason.Renderer(status),
				failure.reason
			)
		}
	}

	@Test
	fun applicationEpochPublishesOnlyStableCapture() {
		val advanced = mutableListOf<Long>()
		val epoch = ReaderPageApplicationOwnershipEpoch(advanced::add)
		assertEquals(0L, epoch.current())
		assertEquals(
			application(ownershipEpoch = 0L),
			epoch.captureStable { application(ownershipEpoch = it) }
		)

		val unstable = epoch.captureStable { captured ->
			epoch.ownerMutationCommitted()
			application(ownershipEpoch = captured)
		}
		assertNull(unstable)
		assertEquals(listOf(1L), advanced)
	}

	@Test
	fun callbackCapacityRetainsColdDemandUntilCapacityEdge() {
		var requests = 0
		var listener: (() -> Unit)? = null
		var admitted = 0
		var forwardedCapacityEdges = 0
		val host = object : ReaderPageRendererOwnershipHost {
			override fun requestOwnershipSnapshot(
				onResult: (ReaderPageRendererOwnershipResult) -> Unit
			) {
				requests += 1
				if (requests == 1) {
					onResult(
						ReaderPageRendererOwnershipResult.Unavailable(
							PageSurfaceOwnershipResult.Status.CALLBACK_CAPACITY
						)
					)
				} else {
					onResult(
						ReaderPageRendererOwnershipResult.Available(renderer())
					)
				}
			}

			override fun setCallbackCapacityListener(value: () -> Unit) {
				check(listener == null)
				listener = value
			}

			override fun clearCallbackCapacityListener(value: () -> Unit) {
				check(listener === value)
				listener = null
			}
		}
		val admission = ReaderPageColdOwnershipAdmission(
			ownershipProbe = ReaderPageOwnershipProbe(
				applicationSnapshot = { application() },
				rendererHost = host
			),
			rendererHost = host,
			acceptsColdBaseline = ReaderPageOwnershipSnapshot::isClosedBaseline,
			onUnavailable = {},
			onAdmitted = { admitted += 1 },
			onCallbackCapacityAvailable = { forwardedCapacityEdges += 1 }
		)

		admission.requestColdBaseline()
		assertEquals(1, requests)
		assertEquals(0, admitted)
		requireNotNull(listener).invoke()
		assertEquals(2, requests)
		assertEquals(1, admitted)
		assertEquals(1, forwardedCapacityEdges)
		admission.close()
		assertNull(listener)
	}

	@Test
	fun lateColdCallbackAfterCloseCannotAdmitOrReportUnavailable() {
		var listener: (() -> Unit)? = null
		var completion: ((ReaderPageRendererOwnershipResult) -> Unit)? = null
		var admitted = 0
		var unavailable = 0
		val host = object : ReaderPageRendererOwnershipHost {
			override fun requestOwnershipSnapshot(
				onResult: (ReaderPageRendererOwnershipResult) -> Unit
			) {
				completion = onResult
			}

			override fun setCallbackCapacityListener(value: () -> Unit) {
				check(listener == null)
				listener = value
			}

			override fun clearCallbackCapacityListener(value: () -> Unit) {
				check(listener === value)
				listener = null
			}
		}
		val admission = ReaderPageColdOwnershipAdmission(
			ownershipProbe = ReaderPageOwnershipProbe(
				applicationSnapshot = { application() },
				rendererHost = host
			),
			rendererHost = host,
			acceptsColdBaseline = ReaderPageOwnershipSnapshot::isClosedBaseline,
			onUnavailable = { unavailable += 1 },
			onAdmitted = { admitted += 1 }
		)
		admission.requestColdBaseline()

		admission.close()
		requireNotNull(completion).invoke(
			ReaderPageRendererOwnershipResult.Available(renderer())
		)

		assertNull(listener)
		assertEquals(0, admitted)
		assertEquals(0, unavailable)
	}

	private fun immediateHost(
		snapshot: ReaderPageRendererOwnershipSnapshot
	): ReaderPageRendererOwnershipHost =
		object : ReaderPageRendererOwnershipHost {
			override fun requestOwnershipSnapshot(
				onResult: (ReaderPageRendererOwnershipResult) -> Unit
			) = onResult(ReaderPageRendererOwnershipResult.Available(snapshot))
		}

	private fun application(
		ownershipEpoch: Long = 1L,
		adapterResidents: Int = 0,
		adapterResidentLimit: Int = 0,
		adapterDecodedBitmaps: Int = 0,
		adapterDecodedBitmapLimit: Int = 0,
		cacheDecodedBitmaps: Int = 0,
		cacheDecodedBitmapLimit: Int = 0,
		stagedPublications: Int = 0,
		stagedPublicationLimit: Int = 0,
		pendingCallbacks: Int = 0,
		pendingCallbackLimit: Int = 0,
		foregroundPassiveOwners: Int = 0,
		foregroundPassiveOwnerLimit: Int = 1,
		foregroundLiveClaims: Int = 0,
		foregroundLiveClaimLimit: Int = 0,
		foregroundRestorationCallbacks: Int = 0,
		foregroundRestorationCallbackLimit: Int = 1,
		relocationReservations: Int = 0,
		queuedRelocations: Int = 0,
		relocationTokens: Int = relocationReservations + queuedRelocations,
		relocationTokenLimit: Int = 0
	) = ReaderPageApplicationOwnershipSnapshot(
		ownershipEpoch = ownershipEpoch,
		adapterResidents = adapterResidents,
		adapterResidentLimit = adapterResidentLimit,
		adapterDecodedBitmaps = adapterDecodedBitmaps,
		adapterDecodedBitmapLimit = adapterDecodedBitmapLimit,
		cacheDecodedBitmaps = cacheDecodedBitmaps,
		cacheDecodedBitmapLimit = cacheDecodedBitmapLimit,
		stagedPublications = stagedPublications,
		stagedPublicationLimit = stagedPublicationLimit,
		pendingCallbacks = pendingCallbacks,
		pendingCallbackLimit = pendingCallbackLimit,
		foregroundPassiveOwners = foregroundPassiveOwners,
		foregroundPassiveOwnerLimit = foregroundPassiveOwnerLimit,
		foregroundLiveClaims = foregroundLiveClaims,
		foregroundLiveClaimLimit = foregroundLiveClaimLimit,
		foregroundRestorationCallbacks = foregroundRestorationCallbacks,
		foregroundRestorationCallbackLimit = foregroundRestorationCallbackLimit,
		relocationReservations = relocationReservations,
		queuedRelocations = queuedRelocations,
		relocationTokens = relocationTokens,
		relocationTokenLimit = relocationTokenLimit
	)

	private fun renderer(
		activeDeckLeases: Int = 0,
		pendingDeckLeases: Int = 0,
		releaseInFlightDeckLeases: Int = 0,
		orphanDeckLeases: Int = 0,
		orphanDeckLeaseLimit: Int = 0,
		rendererTextures: Int = 0,
		pendingCallbacks: Int = 0
	) = ReaderPageRendererOwnershipSnapshot(
		activeDeckLeases = activeDeckLeases,
		activeDeckLeaseLimit = 1,
		pendingDeckLeases = pendingDeckLeases,
		pendingDeckLeaseLimit = 1,
		releaseInFlightDeckLeases = releaseInFlightDeckLeases,
		releaseInFlightDeckLeaseLimit = 4,
		orphanDeckLeases = orphanDeckLeases,
		orphanDeckLeaseLimit = orphanDeckLeaseLimit,
		rendererTextures = rendererTextures,
		rendererTextureLimit = 8,
		pendingCallbacks = pendingCallbacks,
		pendingCallbackLimit = 12
	)
}
