package paige.navic.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPageAdjacentChapterDirection
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderTransitionResourceKind

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageRasterPreparationSourceTest {
	@Test
	fun rasterPreparationFreezesInventoriesDrainsAndRestoresExactOwners() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		val releases = linkedMapOf<
			ReaderRasterPreparationPhysicalRestartDescriptor,
			() -> Unit
		>()
		val terminals = mutableMapOf<
			ReaderRasterPreparationPhysicalRestartDescriptor,
			(delivery: () -> Unit) -> Unit
		>()
		var cancelledCompletionDelivered = false
		val restored = mutableListOf<ReaderRasterPreparationPhysicalRestartDescriptor>()
		val descriptors = ReaderRasterPreparationPhysicalOperation.entries
			.mapIndexed { index, operation ->
				rasterPreparationDescriptor(operation = operation, pageOrdinal = index)
			}
		descriptors.forEach { descriptor ->
			assertTrue(
				ownership.start(
					descriptor = descriptor,
					releasePhysicalPreparation = { onReleased ->
						if (
							descriptor.operation ==
								ReaderRasterPreparationPhysicalOperation.CacheInitialization
						) {
							checkNotNull(terminals[descriptor]).invoke {
								cancelledCompletionDelivered = true
							}
							onReleased()
						} else {
							releases[descriptor] = onReleased
						}
						true
					},
					restorePhysicalPreparation = { onCompleted ->
						terminals[descriptor] = onCompleted
						restored += descriptor
						true
					},
					startPhysicalPreparation = { onCompleted ->
						terminals[descriptor] = onCompleted
						true
					}
				)
			)
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(79L))

		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		val rows = checkNotNull(ownership.snapshotFrozenOwnership()).resources
		assertEquals(descriptors.size * 2, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertEquals(
			setOf(ReaderLegacyInventorySource.RasterPreparation),
			rows.map { it.physicalIdentity.source }.toSet()
		)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.filter { it.kind == ReaderTransitionResourceKind.CallbackRegistration }
			.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					ownership.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
		rows.filter { it.kind == ReaderTransitionResourceKind.Raster }
			.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					ownership.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
		assertEquals(descriptors.size - 1, releases.size)
		releases.values.forEach { release -> release() }
		assertFalse(cancelledCompletionDelivered)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.restoreAfterTransitionActivation(domain)
		)
		assertEquals(descriptors, restored)

		val secondDomain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(83L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.freezeForTransitionActivation(secondDomain)
		)
		val secondRows = checkNotNull(ownership.snapshotFrozenOwnership()).resources
		assertEquals(
			rows.map { it.physicalIdentity.sourceLocalToken }.toSet(),
			secondRows.map { it.physicalIdentity.sourceLocalToken }.toSet()
		)
		val source = readerRasterPreparationSource()
		assertFalse(source.contains("class ReaderRasterPreparationPhysicalOwnershipAdapter"))
		assertContains(source, "freezeRasterPreparationOwnershipForTransitionActivation(")
		ReaderRasterPreparationPhysicalOperation.entries.forEach { operation ->
			assertContains(source, "operation = ReaderRasterPreparationPhysicalOperation.$operation")
		}
	}

	@Test
	fun frozenRasterPreparationInventoryWaitsForPreFenceAcceptanceToSettle() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		val physicalStartEntered = CountDownLatch(1)
		val allowPhysicalAcceptance = CountDownLatch(1)
		val startResult = AtomicReference<Boolean?>(null)
		val startFailure = AtomicReference<Throwable?>(null)
		val starter = thread(name = "raster-preparation-acceptance") {
			try {
				startResult.set(
					ownership.start(
						descriptor = rasterPreparationDescriptor(),
						releasePhysicalPreparation = { true },
						restorePhysicalPreparation = { true },
						startPhysicalPreparation = {
							physicalStartEntered.countDown()
							check(allowPhysicalAcceptance.await(5, TimeUnit.SECONDS))
							true
						}
					)
				)
			} catch (failure: Throwable) {
				startFailure.set(failure)
			}
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(107L))

		try {
			assertTrue(physicalStartEntered.await(5, TimeUnit.SECONDS))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ownership.freezeForTransitionActivation(domain)
			)
			assertEquals(
				null,
				ownership.snapshotFrozenOwnership(),
				"A frozen source remains unsettled until its pre-fence acceptance returns"
			)
		} finally {
			allowPhysicalAcceptance.countDown()
			starter.join(5_000L)
		}
		assertFalse(starter.isAlive, "Physical acceptance did not settle")
		startFailure.get()?.let { failure ->
			throw AssertionError("Physical acceptance failed", failure)
		}
		assertEquals(true, startResult.get())
		val inventory = checkNotNull(ownership.snapshotFrozenOwnership())
		assertEquals(domain, inventory.domain)
		assertEquals(2, inventory.resources.size)
		assertEquals(
			setOf(ReaderLegacyResourceOrigin.Discovered),
			inventory.resources.mapTo(linkedSetOf()) { it.origin }
		)
	}

	@Test
	fun rasterPreparationDrainAcceptsCallbackThenFalseExactlyOnce() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { onReleased ->
					onReleased()
					false
				},
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { true }
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(109L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		val owner = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
			it.kind == ReaderTransitionResourceKind.Raster
		}
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(owner.physicalIdentity, confirmations::add)
		)
		assertEquals(listOf(owner.physicalIdentity), confirmations)
		assertEquals(
			ReaderLegacyResourceState.Released,
			checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
				it.physicalIdentity == owner.physicalIdentity
			}.state
		)
		assertTrue(
			ownership.drainFrozenOwnership(owner.physicalIdentity, confirmations::add) is
				ReaderPortCommandResult.Rejected
		)
		assertEquals(1, confirmations.size)
	}

	@Test
	fun rasterPreparationDrainAcceptsCallbackThenThrowExactlyOnce() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { onReleased ->
					onReleased()
					throw IllegalStateException("release failed after callback")
				},
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { true }
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(113L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		val owner = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
			it.kind == ReaderTransitionResourceKind.Raster
		}
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(owner.physicalIdentity, confirmations::add)
		)
		assertEquals(listOf(owner.physicalIdentity), confirmations)
		assertTrue(
			ownership.drainFrozenOwnership(owner.physicalIdentity, confirmations::add) is
				ReaderPortCommandResult.Rejected
		)
		assertEquals(1, confirmations.size)
	}

	@Test
	fun throwingRasterPreparationConfirmationDoesNotEscapeTheReleasePath() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		var releaseContinuedAfterConfirmation = false
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { onReleased ->
					onReleased()
					releaseContinuedAfterConfirmation = true
					true
				},
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { true }
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(127L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		val owner = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
			it.kind == ReaderTransitionResourceKind.Raster
		}
		var confirmationCount = 0

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(owner.physicalIdentity) {
				confirmationCount += 1
				throw IllegalStateException("confirmation failed")
			}
		)
		assertTrue(releaseContinuedAfterConfirmation)
		assertEquals(1, confirmationCount)
	}

	@Test
	fun throwingCallbackRegistrationConfirmationStillReturnsAccepted() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { true },
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { true }
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(128L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		val callback = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		var confirmationCount = 0

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(callback.physicalIdentity) {
				confirmationCount += 1
				throw IllegalStateException("callback confirmation failed")
			}
		)
		assertEquals(1, confirmationCount)
		assertTrue(
			ownership.drainFrozenOwnership(callback.physicalIdentity) {} is
				ReaderPortCommandResult.Rejected
		)
		assertEquals(1, confirmationCount)
	}

	@Test
	fun throwingCompletedFrozenOwnerConfirmationStillReturnsAccepted() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		var completePhysicalPreparation: (((() -> Unit) -> Unit))? = null
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { true },
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { onCompleted ->
					completePhysicalPreparation = onCompleted
					true
				}
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(129L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		checkNotNull(completePhysicalPreparation).invoke {}
		val owner = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
			it.kind == ReaderTransitionResourceKind.Raster
		}
		assertEquals(ReaderLegacyResourceState.ReleaseRequested, owner.state)
		var confirmationCount = 0

		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.drainFrozenOwnership(owner.physicalIdentity) {
				confirmationCount += 1
				throw IllegalStateException("completed owner confirmation failed")
			}
		)
		assertEquals(1, confirmationCount)
		assertTrue(
			ownership.drainFrozenOwnership(owner.physicalIdentity) {} is
				ReaderPortCommandResult.Rejected
		)
		assertEquals(1, confirmationCount)
	}

	@Test
	fun rejectedRasterPreparationReleaseBeforeCallbackRemainsRetryable() {
		listOf(false, true).forEachIndexed { index, throwBeforeCallback ->
			val ownership = ReaderRasterPreparationPhysicalOwnership()
			var releaseMayComplete = false
			assertTrue(
				ownership.start(
					descriptor = rasterPreparationDescriptor(pageOrdinal = index),
					releasePhysicalPreparation = { onReleased ->
						if (!releaseMayComplete) {
							if (throwBeforeCallback) {
								throw IllegalStateException("release failed before callback")
							}
							false
						} else {
							onReleased()
							true
						}
					},
					restorePhysicalPreparation = { true },
					startPhysicalPreparation = { true }
				)
			)
			val domain = ReaderLegacyPhysicalDomain(
				73L,
				ReaderLegacyFreezeToken(131L + index)
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ownership.freezeForTransitionActivation(domain)
			)
			val owner = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
				it.kind == ReaderTransitionResourceKind.Raster
			}
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

			assertTrue(
				ownership.drainFrozenOwnership(owner.physicalIdentity, confirmations::add) is
					ReaderPortCommandResult.Rejected
			)
			assertTrue(confirmations.isEmpty())
			val retryableOwner = checkNotNull(ownership.snapshotFrozenOwnership()).resources.single {
				it.physicalIdentity == owner.physicalIdentity
			}
			assertEquals(ReaderLegacyResourceState.Running, retryableOwner.state)
			releaseMayComplete = true
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ownership.drainFrozenOwnership(owner.physicalIdentity, confirmations::add)
			)
			assertEquals(listOf(owner.physicalIdentity), confirmations)
		}
	}

	@Test
	fun rasterPreparationRejectsNewPhysicalWorkAfterFence() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(89L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		var physicalStarts = 0

		val accepted = ownership.start(
			descriptor = rasterPreparationDescriptor(),
			releasePhysicalPreparation = { true },
			restorePhysicalPreparation = { true },
			startPhysicalPreparation = {
				physicalStarts += 1
				true
			}
		)

		assertFalse(accepted)
		assertEquals(0, physicalStarts)
		assertContains(
			readerRasterPreparationSource(),
			"physicalRasterPreparationOwnership.isFrozen"
		)
	}

	@Test
	fun fencedEmptyRasterPreparationIsConnectedNotMissing() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		assertFalse(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { true },
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { false }
			)
		)
		var synchronousCompletionDelivered = false
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { true },
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { onCompleted ->
					onCompleted { synchronousCompletionDelivered = true }
					true
				}
			)
		)
		assertTrue(synchronousCompletionDelivered)
		assertEquals(null, ownership.snapshotFrozenOwnership())
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(97L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))

		val inventory = checkNotNull(ownership.snapshotFrozenOwnership())

		assertEquals(ReaderLegacyInventorySource.RasterPreparation, inventory.source)
		assertEquals(domain, inventory.domain)
		assertTrue(inventory.resources.isEmpty())
	}

	@Test
	fun failedRasterPreparationRestorationPreservesRetryableOwners() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		val releases = mutableListOf<() -> Unit>()
		var secondRestorationAccepted = false
		val restorationAttempts = mutableMapOf<ReaderRasterPreparationPhysicalOperation, Int>()
		val descriptors = listOf(
			rasterPreparationDescriptor(ReaderRasterPreparationPhysicalOperation.Prewarm, 2),
			rasterPreparationDescriptor(ReaderRasterPreparationPhysicalOperation.Repair, 3)
		)
		descriptors.forEach { descriptor ->
			assertTrue(
				ownership.start(
					descriptor = descriptor,
					releasePhysicalPreparation = { onReleased ->
						releases += onReleased
						true
					},
					restorePhysicalPreparation = {
						val operation = descriptor.operation
						restorationAttempts[operation] =
							(restorationAttempts[operation] ?: 0) + 1
						operation != ReaderRasterPreparationPhysicalOperation.Repair ||
							secondRestorationAccepted
					},
					startPhysicalPreparation = { true }
				)
			)
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(101L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		val initialRows = checkNotNull(ownership.snapshotFrozenOwnership()).resources
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		initialRows.sortedBy { it.kind != ReaderTransitionResourceKind.CallbackRegistration }
			.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					ownership.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
		releases.forEach { release -> release() }
		assertEquals(initialRows.size, confirmations.size)

		assertTrue(
			ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected
		)
		val retryableRows = checkNotNull(ownership.snapshotFrozenOwnership()).resources
		val initialOwnerTokens = initialRows
			.filter { it.kind == ReaderTransitionResourceKind.Raster }
			.mapTo(linkedSetOf()) { it.physicalIdentity.sourceLocalToken }
		assertEquals(
			initialOwnerTokens,
			retryableRows.filter { it.kind == ReaderTransitionResourceKind.Raster }
				.mapTo(linkedSetOf()) { it.physicalIdentity.sourceLocalToken }
		)
		assertEquals(
			1,
			retryableRows.count { it.state == ReaderLegacyResourceState.Released }
		)
		assertEquals(1, restorationAttempts[ReaderRasterPreparationPhysicalOperation.Prewarm])
		assertEquals(1, restorationAttempts[ReaderRasterPreparationPhysicalOperation.Repair])

		secondRestorationAccepted = true
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.restoreAfterTransitionActivation(domain)
		)
		assertEquals(1, restorationAttempts[ReaderRasterPreparationPhysicalOperation.Prewarm])
		assertEquals(2, restorationAttempts[ReaderRasterPreparationPhysicalOperation.Repair])
		val nextDomain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(103L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.freezeForTransitionActivation(nextDomain)
		)
		val restoredOwnerTokens = checkNotNull(ownership.snapshotFrozenOwnership()).resources
			.filter { it.kind == ReaderTransitionResourceKind.Raster }
			.mapTo(linkedSetOf()) { it.physicalIdentity.sourceLocalToken }
		assertEquals(initialOwnerTokens, restoredOwnerTokens)
	}

	@Test
	fun restorationRejectsUntilPhysicalSettlementAndLogicalOutcomePublicationComplete() {
		val physicalStartEntered = CountDownLatch(1)
		val allowPhysicalStartToReturn = CountDownLatch(1)
		val settlementReady = CountDownLatch(1)
		val allowLogicalPublication = CountDownLatch(1)
		val workerFailure = AtomicReference<Throwable?>()
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		val worker = thread(name = "raster-preparation-publication-ack") {
			try {
				val settlement: Any = ownership.startWithOutcome(
					descriptor = rasterPreparationDescriptor(),
					releasePhysicalPreparation = { true },
					restorePhysicalPreparation = { true },
					startPhysicalPreparation = {
						physicalStartEntered.countDown()
						check(allowPhysicalStartToReturn.await(5, TimeUnit.SECONDS))
						false
					}
				)
				settlementReady.countDown()
				check(allowLogicalPublication.await(5, TimeUnit.SECONDS))
				val publish: (Any?) -> Unit = {}
				check(settlement.invokePrivateIfPresent("publishLogicalOutcome", publish))
			} catch (failure: Throwable) {
				workerFailure.set(failure)
			}
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(135L))
		try {
			assertTrue(physicalStartEntered.await(5, TimeUnit.SECONDS))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ownership.freezeForTransitionActivation(domain)
			)
			assertTrue(
				ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected
			)
			allowPhysicalStartToReturn.countDown()
			assertTrue(settlementReady.await(5, TimeUnit.SECONDS))
			assertTrue(
				ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected
			)
		} finally {
			allowPhysicalStartToReturn.countDown()
			allowLogicalPublication.countDown()
			worker.join(5_000L)
		}
		assertFalse(worker.isAlive, "Start settlement publication did not finish")
		workerFailure.get()?.let { failure ->
			throw AssertionError("Start settlement publication failed", failure)
		}
		assertTrue(checkNotNull(ownership.snapshotFrozenOwnership()).resources.isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun concurrentRasterPreparationRestoreStartsEachPhysicalOwnerAtMostOnce() {
		val restoreEntered = CountDownLatch(1)
		val allowFirstRestoreToFail = CountDownLatch(1)
		val restoreAttempts = AtomicInteger()
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { onReleased ->
					onReleased()
					true
				},
				restorePhysicalPreparation = {
					when (restoreAttempts.incrementAndGet()) {
						1 -> {
							restoreEntered.countDown()
							check(allowFirstRestoreToFail.await(5, TimeUnit.SECONDS))
							false
						}
						else -> true
					}
				},
				startPhysicalPreparation = { true }
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(137L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		drainRasterPreparationOwnership(ownership)
		val firstResult = AtomicReference<ReaderPortCommandResult?>()
		val firstFailure = AtomicReference<Throwable?>()
		val firstRestore = thread(name = "raster-preparation-first-restore") {
			try {
				firstResult.set(ownership.restoreAfterTransitionActivation(domain))
			} catch (failure: Throwable) {
				firstFailure.set(failure)
			}
		}

		try {
			assertTrue(restoreEntered.await(5, TimeUnit.SECONDS))
			assertTrue(
				ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected
			)
			assertEquals(1, restoreAttempts.get())
		} finally {
			allowFirstRestoreToFail.countDown()
			firstRestore.join(5_000L)
		}
		assertFalse(firstRestore.isAlive, "First restoration did not settle")
		firstFailure.get()?.let { failure ->
			throw AssertionError("First restoration failed unexpectedly", failure)
		}
		assertTrue(firstResult.get() is ReaderPortCommandResult.Rejected)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.restoreAfterTransitionActivation(domain)
		)
		assertEquals(2, restoreAttempts.get())
	}

	@Test
	fun throwingSynchronousAcceptedStartDeliveryDoesNotEscape() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		var deliveries = 0

		val start = runCatching {
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { true },
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { onCompleted ->
					onCompleted {
						deliveries += 1
						throw IllegalStateException("synchronous delivery failed")
					}
					true
				}
			)
		}

		assertNull(start.exceptionOrNull())
		assertEquals(true, start.getOrNull())
		assertEquals(1, deliveries)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(139L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		assertTrue(checkNotNull(ownership.snapshotFrozenOwnership()).resources.isEmpty())
	}

	@Test
	fun throwingLaterRasterPreparationDeliveryDoesNotEscape() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		var completePhysicalPreparation: (((() -> Unit) -> Unit))? = null
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { true },
				restorePhysicalPreparation = { true },
				startPhysicalPreparation = { onCompleted ->
					completePhysicalPreparation = onCompleted
					true
				}
			)
		)
		var deliveries = 0

		val completion = runCatching {
			checkNotNull(completePhysicalPreparation).invoke {
				deliveries += 1
				throw IllegalStateException("later delivery failed")
			}
		}

		assertNull(completion.exceptionOrNull())
		assertEquals(1, deliveries)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(149L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		assertTrue(checkNotNull(ownership.snapshotFrozenOwnership()).resources.isEmpty())
	}

	@Test
	fun throwingPostUnfreezeDeliveryDoesNotSkipLaterDeliveries() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		val deliveries = mutableListOf<Int>()
		listOf(1, 2).forEach { index ->
			assertTrue(
				ownership.start(
					descriptor = rasterPreparationDescriptor(pageOrdinal = index),
					releasePhysicalPreparation = { onReleased ->
						onReleased()
						true
					},
					restorePhysicalPreparation = { onCompleted ->
						onCompleted {
							deliveries += index
							if (index == 1) {
								throw IllegalStateException("first restored delivery failed")
							}
						}
						true
					},
					startPhysicalPreparation = { true }
				)
			)
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(151L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		drainRasterPreparationOwnership(ownership)

		val restoration = runCatching { ownership.restoreAfterTransitionActivation(domain) }

		assertNull(restoration.exceptionOrNull())
		assertEquals(ReaderPortCommandResult.Accepted, restoration.getOrNull())
		assertEquals(listOf(1, 2), deliveries)
	}

	@Test
	fun throwingDeliveryAfterRestorationDoesNotEscape() {
		val ownership = ReaderRasterPreparationPhysicalOwnership()
		var completeRestoredPreparation: (((() -> Unit) -> Unit))? = null
		assertTrue(
			ownership.start(
				descriptor = rasterPreparationDescriptor(),
				releasePhysicalPreparation = { onReleased ->
					onReleased()
					true
				},
				restorePhysicalPreparation = { onCompleted ->
					completeRestoredPreparation = onCompleted
					true
				},
				startPhysicalPreparation = { true }
			)
		)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(157L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
		drainRasterPreparationOwnership(ownership)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			ownership.restoreAfterTransitionActivation(domain)
		)
		var deliveries = 0

		val completion = runCatching {
			checkNotNull(completeRestoredPreparation).invoke {
				deliveries += 1
				throw IllegalStateException("restored delivery failed")
			}
		}

		assertNull(completion.exceptionOrNull())
		assertEquals(1, deliveries)
		val nextDomain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(163L))
		assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(nextDomain))
		assertTrue(checkNotNull(ownership.snapshotFrozenOwnership()).resources.isEmpty())
	}

	@Test
	fun fencedRepairStartResumesQueuedRepairExactlyOnceAfterRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = true)
		val fixture = RasterFenceControllerFixture.create(port)
		val repairResult = AtomicReference<ReaderPageRasterRepairResult?>()
		val workerFailure = AtomicReference<Throwable?>()
		fixture.prepareRepair(pageIndex = 20)
		val worker = thread(name = "raster-repair-fence-race") {
			try {
				fixture.controller.repairRasterPage(20, repairResult::set)
			} catch (failure: Throwable) {
				workerFailure.set(failure)
			}
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(167L))
		try {
			assertTrue(port.firstStartEntered.await(5, TimeUnit.SECONDS))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
		} finally {
			port.allowFirstStartToReturn.countDown()
			worker.join(5_000L)
		}
		try {
			assertFalse(worker.isAlive, "Repair start did not settle")
			workerFailure.get()?.let { failure ->
				throw AssertionError("Repair start failed unexpectedly", failure)
			}
			assertEquals(null, repairResult.get())
			assertTrue(
				checkNotNull(fixture.controller.snapshotFrozenRasterPreparationOwnership())
					.resources.isEmpty()
			)

			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)

			assertEquals(2, port.starts.get())
			assertIs<ReaderPageRasterRepairResult.Failed>(repairResult.get())
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun fencedPrewarmStartCleansTheActiveBatchWithoutInventingOwnership() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = true)
		val fixture = RasterFenceControllerFixture.create(port)
		val outcomes = mutableListOf<ReaderPageRasterBatchOutcome>()
		val workerFailure = AtomicReference<Throwable?>()
		val worker = thread(name = "raster-prewarm-fence-race") {
			try {
				fixture.startPrewarmBatch { outcome -> outcomes += outcome }
			} catch (failure: Throwable) {
				workerFailure.set(failure)
			}
		}
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(173L))
		try {
			assertTrue(port.firstStartEntered.await(5, TimeUnit.SECONDS))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
		} finally {
			port.allowFirstStartToReturn.countDown()
			worker.join(5_000L)
		}
		try {
			assertFalse(worker.isAlive, "Prewarm start did not settle")
			workerFailure.get()?.let { failure ->
				throw AssertionError("Prewarm start failed unexpectedly", failure)
			}
			assertEquals(1, outcomes.size)
			assertEquals(ReaderPageRasterBatchOutcome.Cancelled, outcomes.single())
			assertFalse(fixture.controller.privateField<Boolean>("prewarmInProgress"))
			assertTrue(
				checkNotNull(fixture.controller.snapshotFrozenRasterPreparationOwnership())
					.resources.isEmpty()
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)
			assertEquals(1, port.starts.get())
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun fencedBackgroundStartResumesDeferredSubmissionExactlyOnceAfterRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = false)
		val fixture = RasterFenceControllerFixture.create(port)
		val submission = fixture.prepareBackgroundSubmission()
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(179L))
		port.onFirstStart = {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
		}
		try {
			fixture.drainMainLooper()
			assertTrue(
				checkNotNull(fixture.controller.snapshotFrozenRasterPreparationOwnership())
					.resources.isEmpty()
			)

			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)
			fixture.drainMainLooper()

			assertEquals(2, port.starts.get())
			assertFalse(fixture.isBackgroundSubmissionActive(submission))
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun postedBackgroundStartObservedDuringFenceResumesExactlyOnceAfterRestoration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = false)
		val fixture = RasterFenceControllerFixture.create(port)
		val submission = fixture.prepareBackgroundSubmission()
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(181L))
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
			fixture.drainMainLooper()
			assertEquals(0, port.starts.get())
			assertTrue(fixture.isBackgroundSubmissionActive(submission))

			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)
			fixture.drainMainLooper()

			assertEquals(1, port.starts.get())
			assertFalse(fixture.isBackgroundSubmissionActive(submission))
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun lateFencedRepairPublicationAfterRestorationResumesExactlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = false)
		val fixture = RasterFenceControllerFixture.create(port)
		val result = AtomicReference<ReaderPageRasterRepairResult?>()
		fixture.prepareRepair(pageIndex = 20)
		fixture.markRepairActive(pageIndex = 20, onComplete = result::set)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(191L))
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)
			assertEquals(0, port.starts.get())
			assertEquals(null, result.get())

			assertTrue(
				fixture.controller.invokePrivateIfPresent(
					"publishFencedRasterRepairStart",
					20
				)
			)

			assertEquals(1, port.starts.get())
			assertIs<ReaderPageRasterRepairResult.Failed>(result.get())
			assertContains(
				readerRasterPreparationSource(),
				"publishFencedRasterRepairStart(pageIndex)"
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun lateFencedBackgroundPublicationAfterRestorationResumesExactlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = false)
		val fixture = RasterFenceControllerFixture.create(port)
		val submission = fixture.prepareBackgroundSubmission()
		fixture.dropPostedBackgroundStartWhileIneligible()
		fixture.markBackgroundPhysicalStartActive(submission)
		val prefetch = fixture.backgroundPrefetch()
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(193L))
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)
			assertEquals(0, port.starts.get())

			assertTrue(
				fixture.controller.invokePrivateIfPresent(
					"publishFencedBackgroundPrefetchStart",
					submission,
					prefetch
				)
			)
			fixture.drainMainLooper()

			assertEquals(1, port.starts.get())
			assertFalse(fixture.isBackgroundSubmissionActive(submission))
			assertEquals(
				3,
				readerRasterPreparationSource()
					.split("publishFencedBackgroundPrefetchStart").size - 1
			)
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun latePostedBackgroundFencePublicationAfterRestorationResumesExactlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(blockFirstStart = false)
		val fixture = RasterFenceControllerFixture.create(port)
		val submission = fixture.prepareBackgroundSubmission()
		fixture.dropPostedBackgroundStartWhileIneligible()
		val prefetch = fixture.backgroundPrefetch()
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(197L))
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
			)
			assertEquals(0, port.starts.get())

			assertTrue(
				fixture.controller.invokePrivateIfPresent(
					"publishFencedBackgroundPrefetchStart",
					submission,
					prefetch
				)
			)
			fixture.drainMainLooper()

			assertEquals(1, port.starts.get())
			assertFalse(fixture.isBackgroundSubmissionActive(submission))
		} finally {
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun overlappingRestoreAndLatePostedBackgroundPublicationResumeExactlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val port = FenceRacePassiveRasterPreparationPort(
			blockFirstStart = false,
			acceptStarts = true
		)
		val fixture = RasterFenceControllerFixture.create(port)
		val submission = fixture.prepareBackgroundSubmission()
		fixture.dropPostedBackgroundStartWhileIneligible()
		val prefetch = fixture.backgroundPrefetch()
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(199L))
		val restoreGate = fixture.blockRestoreBeforeDeferredBackgroundResume()
		val eligibilityGate = fixture.blockBackgroundEligibilityChecks(expectedChecks = 2)
		val restoreResult = AtomicReference<ReaderPortCommandResult?>()
		val restoreFailure = AtomicReference<Throwable?>()
		val publicationFailure = AtomicReference<Throwable?>()
		var restoreWorker: Thread? = null
		var publicationWorker: Thread? = null
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				fixture.controller.freezeRasterPreparationOwnershipForTransitionActivation(domain)
			)
			restoreWorker = thread(name = "raster-posted-background-overlap-restore") {
				try {
					restoreResult.set(
						fixture.controller
							.restoreRasterPreparationOwnershipAfterTransitionActivation(domain)
					)
				} catch (failure: Throwable) {
					restoreFailure.set(failure)
				}
			}
			assertTrue(restoreGate.restoreReachedBeforeDeferredResume.await(5, TimeUnit.SECONDS))
			publicationWorker = thread(name = "raster-posted-background-overlap-publication") {
				try {
					check(
						fixture.controller.invokePrivateIfPresent(
							"publishFencedBackgroundPrefetchStart",
							submission,
							prefetch
						)
					)
				} catch (failure: Throwable) {
					publicationFailure.set(failure)
				}
			}
			assertTrue(eligibilityGate.firstCheckEntered.await(5, TimeUnit.SECONDS))
			restoreGate.allowDeferredResume.countDown()
			assertTrue(eligibilityGate.allChecksEntered.await(5, TimeUnit.SECONDS))
			eligibilityGate.allowChecksToReturn.countDown()
			val completedRestoreWorker = checkNotNull(restoreWorker)
			val completedPublicationWorker = checkNotNull(publicationWorker)
			completedRestoreWorker.join(5_000L)
			completedPublicationWorker.join(5_000L)
			assertFalse(completedRestoreWorker.isAlive, "Restoration did not finish")
			assertFalse(completedPublicationWorker.isAlive, "Fence publication did not finish")
			restoreFailure.get()?.let { failure ->
				throw AssertionError("Restoration failed unexpectedly", failure)
			}
			publicationFailure.get()?.let { failure ->
				throw AssertionError("Fence publication failed unexpectedly", failure)
			}
			assertEquals(ReaderPortCommandResult.Accepted, restoreResult.get())

			fixture.drainMainLooper()

			assertEquals(1, port.starts.get())
		} finally {
			restoreGate.allowDeferredResume.countDown()
			eligibilityGate.allowChecksToReturn.countDown()
			restoreWorker?.join(5_000L)
			publicationWorker?.join(5_000L)
			fixture.close()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cacheInitializationPublishesItsLogicalOutcomeBeforeAcknowledgingSettlement() {
		val initialization = requiredReaderSourceSlice(
			source = readerRasterPreparationSource(),
			startDelimiter = "private fun initializeRasterCacheAndQueryPlan(",
			endDelimiter = "private fun continueAfterRasterCacheInitialization("
		)

		assertContains(initialization, "physicalRasterPreparationOwnership.startWithOutcome(")
		assertContains(initialization, "startSettlement.publishLogicalOutcome")
		assertFalse(initialization.contains("physicalRasterPreparationOwnership.start("))
	}

	@Test
	fun productionAcquisitionTriggerDistinguishesColdWarmAndLiveRefill() {
		assertEquals(
			ReaderPageRasterAcquisitionTrigger.InitialPreparation,
			readerPageRasterAcquisitionTrigger(
				hasPreparedBefore = false,
				persistentRasterEntries = 0
			)
		)
		assertEquals(
			ReaderPageRasterAcquisitionTrigger.WarmReopen,
			readerPageRasterAcquisitionTrigger(
				hasPreparedBefore = false,
				persistentRasterEntries = 1
			)
		)
		assertEquals(
			ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
			readerPageRasterAcquisitionTrigger(
				hasPreparedBefore = true,
				persistentRasterEntries = 1
			)
		)
		val preparation = readerRasterPreparationSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val initializationPath = preparation
			.substringAfter("private fun initializeRasterCacheAndQueryPlan(")
			.substringBefore("private fun consumeQaDeferral(")
		val prewarm = preparation
			.substringAfter("fun prewarmAdjacent(): Boolean {")
			.substringBefore("private fun initializeRasterCacheAndQueryPlan(")
		val initialization = "initializeRasterCache(webView)"
		val metrics = "bundleSource.rasterCacheMetrics().diskEntries"
		assertContains(bundle, "suspend fun initializeRasterCache(webView: WebView)")
		assertContains(initializationPath, initialization)
		assertContains(initializationPath, "if (!prewarmAcquisitionTriggerClassified)")
		assertContains(initializationPath, "prewarmAcquisitionTriggerClassified = true")
		assertContains(prewarm, "if (resumedDiagnostic == null)")
		assertContains(prewarm, "prewarmAcquisitionTriggerClassified = false")
		assertContains(preparation, "persistentRasterEntries = $metrics")
		assertTrue(
			initializationPath.indexOf(initialization) < initializationPath.indexOf(metrics),
			"Persistent cache initialization must precede warm-reopen trigger selection"
		)
		assertContains(preparation, "trigger = activeAcquisitionTrigger")
	}

	@Test
	fun prewarmReferenceReusesTheActivePhysicalLayoutBeforeRecapturing() {
		val preparation = readerRasterPreparationSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val productionPort = preparation
			.substringAfter("private class ReaderPageBundleRasterCurrentReferencePort(")
			.substringBefore("internal fun readerPageTurnCanStartPassivePrewarm(")
		val reference = preparation
			.substringAfter("private fun obtainRasterReference(")
			.substringBefore("private fun isPrewarmSessionActive(")
		val retained = "currentLayoutSnapshot(pageIndex, kind)?.let"
		val fresh = "currentReferencePort.captureFresh("

		assertContains(
			productionPort,
			"bundleSource.captureCurrentSurface(webView, generation, captureGeometry)"
		)
		assertContains(productionPort, "bundleSource.cacheCurrentSnapshot(pageIndex, kind, current, generation)")
		assertContains(productionPort, "snapshot.retain()")
		assertContains(bundle, "fun retainedCurrentLayoutSnapshot(")
		assertContains(bundle, "physicalLayoutAuthority?.takeIf")
		assertContains(reference, retained)
		assertContains(reference, fresh)
		assertContains(preparation, "plan.captureGeometry")
		assertContains(reference, "captureGeometry = captureGeometry")
		assertTrue(reference.indexOf(retained) < reference.indexOf(fresh))
		assertFalse(reference.contains("retainedSnapshot(pageIndex, kind)?.let"))
	}

	@Test
	fun publicationCapacityRetriesUseTheExactPreparationListener() {
		val preparation = readerRasterPreparationSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")

		assertContains(
			preparation,
			"bundleSource.setPublicationCapacityAvailableListener(onRequestPrewarm)"
		)
		assertContains(
			preparation,
			"bundleSource.clearPublicationCapacityAvailableListener(onRequestPrewarm)"
		)
		assertContains(bundle, "publicationLedger.setCapacityAvailableListener(listener)")
		assertContains(bundle, "publicationLedger.clearCapacityAvailableListener(listener)")
	}

	@Test
	fun batchProgressAdvancesOnlyFromVerifiedHydrationOrPublication() {
		val source = readerRasterBatchSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val hydration = source.substringAfter(
			"private fun hydrateTarget("
		).substringBefore("private fun submitMissingTargets(")
		val capture = source.substringAfter(
			"private fun captureReadyItem("
		).substringBefore("private fun advancePageTurnPreviewBatch(")

		assertContains(hydration, "bundleSource.hydrateSnapshotWithDurability(")
		assertContains(
			hydration,
			"ReaderPageRasterHydrationDurability.PersistentStoreVerified"
		)
		assertContains(
			hydration,
			"ReaderPageRasterPublicationResult.Durable"
		)
		assertContains(hydration, "bundleSource.ensurePersistentSnapshot(")
		assertContains(hydration, "recordDurability(session, target, publicationCompletion)")
		val ensurePersistent = bundle.substringAfter(
			"fun ensurePersistentSnapshot("
		).substringBefore("private fun cacheSnapshot(")
		assertContains(ensurePersistent, "persistCachedSnapshot(")
		assertContains(ensurePersistent, "isStillCurrent = isStillCurrent")
		assertContains(capture, "onCaptured = captured@{ publicationCompletion ->")
		assertContains(capture, "recordDurability(session, target, publicationCompletion)")
		assertContains(source, "ReaderPageRasterPublicationResult.CapacityReached")
		assertContains(source, "ReaderPageRasterCapacityPolicy.StopBackgroundRefill")
		assertContains(source, "stage = \"persistent-publication\"")
		assertContains(source, "reason = \"durable-write-failed\"")
		assertContains(source, "session.durabilityGate.retryPageIndices()")
		assertContains(source, "target.pageIndex in candidate.retryPageIndices")
		assertContains(source, "progressCompletedOffset + completed")
		assertContains(bundle, "publicationCompletionResults[request] = publicationCompletion")
		assertContains(bundle, "ReaderPageRasterWriteFailureReason.DiskCapacity")
		assertFalse(source.contains("private fun markCompleted("))
	}

	@Test
	fun passiveResolutionFallsBackOnlyForUntypedPublicationFailure() {
		val adapter = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val resolve = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "private fun resolveTarget(",
			endDelimiter = "private fun captureTarget("
		)
		val failedBranch = resolve.substringAfter(
			"ReaderPageRasterPublicationResult.Failed -> {"
		).substringBefore("\n\t\t\tnull ->")

		assertContains(failedBranch, "val writeFailureReason = completion.writeFailureReason")
		assertContains(failedBranch, "if (writeFailureReason == null)")
		assertContains(failedBranch, "captureTarget(batch, target, inputs)")
		assertContains(failedBranch, "finish(")
		assertContains(failedBranch, "persistentWriteFailureReason = writeFailureReason")
		assertContains(resolve, "null -> captureTarget(batch, target, inputs)")
	}

	@Test
	fun backgroundRefillReportsDiskCapacityAsABoundedCompletion() {
		val source = readerRasterPreparationSource()
		val background = source.substringAfter(
			"private fun startBackgroundPrefetch("
		).substringBefore("private fun isBackgroundPrefetchActive(")

		assertContains(
			background,
			"capacityPolicy = ReaderPageRasterCapacityPolicy.StopBackgroundRefill"
		)
		assertContains(
			background,
			"is ReaderPageRasterBatchOutcome.CapacityReached ->"
		)
		assertContains(
			background,
			"ReaderPagePrefetchDiagnosticState.CapacityReached"
		)
		assertContains(background, "\"background-prefetch-capacity-reached\"")
	}

	@Test
	fun staticRasterShieldPredicateExcludesIsolatedPassiveRepairOwnership() {
		val source = readerRasterPreparationSource()
		val predicate = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "internal fun hasStaticRasterShieldOwnership()",
			endDelimiter = "\n\n\tprivate "
		)

		listOf(
			"preparationShield != null",
			"preparationShieldSnapshot != null",
			"preparationShieldSession != null",
			"preparationShieldBatchLabel != null",
			"backgroundPrefetchShield != null",
			"backgroundPrefetchShieldSnapshot != null",
			"backgroundPrefetchShieldSessionId != null"
		).forEach { ownership -> assertContains(predicate, ownership) }
		assertFalse(predicate.contains("activeRasterRepairShieldSession"))
	}

	@Test
	fun preparationShieldIsReusedWithinOneRasterSession() {
		val source = readerRasterPreparationSource()

		assertContains(source, "reusePreparationShield(")
		assertContains(source, "batchLabel = batchLabel")
		assertContains(source, "event = \"shield-reused\"")
		assertFalse(source.contains("private fun attachPreparationShield(snapshot: ReaderPageSlideSnapshot) {\n\t\tremovePreparationShield()"))
	}

	@Test
	fun passivePrewarmCancellationDoesNotWaitForForegroundRestoration() {
		val source = readerRasterPreparationSource()
		val cancellation = source.substringAfter(
			"private fun cancelPrewarm(reason: String) {"
		).substringBefore("private fun reusePreparationShield(")

		assertContains(cancellation, "passiveRasterPreparationPortProvider()?.cancel()")
		assertContains(cancellation, "removePreparationShield(")
		assertFalse(cancellation.contains("rasterBatchController.cancel"))
		assertFalse(cancellation.contains("trackVisualRestoration("))
		assertFalse(cancellation.contains("restoreLiveComposition("))
	}

	@Test
	fun shieldCleanupIsRestorationFencedAcrossDetachAndDestroy() {
		val source = readerRasterPreparationSource()
		val attachment = source.substringAfter(
			"fun onWebViewAttachmentChanged(attached: Boolean) {"
		).substringBefore("\n\tfun onPointerInteractionChanged(")
		val teardown = source.substringAfter(
			"closeRendererAndAdapter = {"
		).substringBefore("closeBundleOwners = closeBundleOwners")
		val destroyFence = source.substringAfter(
			"private fun fenceForDestroy() {"
		).substringBefore("\n\tsuspend fun destroyAndJoin()")

		assertContains(attachment, "if (!attached)")
		assertContains(attachment, "cancelRasterRepairs(\"webview-detached\")")
		assertContains(attachment, "deferPrewarmForWebViewDetach()")
		assertContains(teardown, "awaitVisualRestorations()")
		assertTrue(
			teardown.indexOf("awaitVisualRestorations()") <
				teardown.indexOf("removePreparationShield(")
		)
		assertTrue(
			teardown.indexOf("awaitVisualRestorations()") <
				teardown.indexOf("removeBackgroundPrefetchShield()")
		)
		assertFalse(destroyFence.contains("removePreparationShield("))
		assertFalse(destroyFence.contains("removeBackgroundPrefetchShield("))
	}

	@Test
	fun invalidationImmediatelyRestoresTheFullPreparationCover() {
		val source = readerRasterPreparationSource()
		val invalidation = source.substringAfter(
			"fun invalidate(reason: String, clearVisualPageIndex: Boolean = false) {"
		).substringBefore("\n\tfun invalidateCurrentVisualSnapshot(")

		assertContains(invalidation, "hasPreparedBefore = false")
		assertContains(invalidation, "durableRasterPageIndices.clear()")
		assertContains(
			invalidation,
			"publishPreparationState(ReaderPagePreparationPhase.Idle)"
		)
	}

	@Test
	fun hydrationMissFallsThroughToPassiveCaptureWithoutChangingPresentationMode() {
		val batch = readerRasterBatchSource()
		val contract = batch.substringAfter(
			"internal interface ReaderPageRasterBatchPort"
		).substringBefore("internal class ReaderPageRasterBatchController")
		val hydration = batch.substringAfter(
			"private fun hydrateTarget(session: Session, targetIndex: Int)"
		).substringBefore("private fun submitMissingTargets(")
		val preparation = readerRasterPreparationSource()
		val foreground = preparation.substringAfter(
			"private fun startRasterBatch("
		).substringBefore("private fun obtainRasterReference(")

		assertContains(contract, "onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit")
		assertTrue(
			hydration.indexOf("session.onHydrationMiss(target)") <
				hydration.indexOf("session.missingTargets += target")
		)
		assertContains(foreground, "onHydrationMiss = {}")
		assertFalse(foreground.contains("enterBlockingPreparation(\"required-cache-miss"))
	}

	@Test
	fun isolatedPassiveAvailabilityResumesDeferredWorkWithoutForegroundOwnership() {
		val source = readerRasterPreparationSource()
		val passiveAvailable = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "fun onPassiveRasterPreparationAvailable() {",
			endDelimiter = "fun onRasterProfileEpochChanged("
		)

		assertTrue(passiveAvailable.isNotBlank(), "The passive host needs its own availability edge")
		assertContains(passiveAvailable, "passivePrewarmDeferral")
		assertContains(passiveAvailable, "deferredRasterRepairPageIndex")
		assertContains(passiveAvailable, "resumeDeferredBackgroundPrefetchStart()")
		assertContains(passiveAvailable, "adjacentChapterPrefetchCoordinator.onPassiveAvailable()")
		assertFalse(passiveAvailable.contains("foregroundWebViewOwnership"))
		assertFalse(passiveAvailable.contains("canAcquirePassive("))
		assertFalse(source.contains("fun onForegroundWebViewPassiveAvailable()"))
	}

	@Test
	fun preparationLifecycleLogsEveryRemovalAndInvalidationCause() {
		val source = readerRasterPreparationSource()

		assertContains(source, "cancelPrewarm(reason:")
		assertContains(source, "removePreparationShield(")
		assertContains(source, "event = \"invalidated\"")
		assertContains(source, "\"shield-attached\"")
		assertContains(source, "event = \"shield-removed\"")
		assertContains(source, "event = \"session-finished\"")
	}

	@Test
	fun visualPageMovesRetainTheExistingRasterGeneration() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(function, "if (pageIndex == null) {")
		assertContains(function, "cancelRasterRepairs(\"visual-index-cleared:")
		assertFalse(function.contains("if (currentVisualPageIndex != null)"))
		assertContains(function, "currentVisualPageIndex = null")
		assertContains(function, "beginBlockingBackgroundPrefetchSession()")
		assertContains(function, "cancelRasterRepairs(\"visual-index-changed:")
		assertContains(function, "cancelPrewarm(reason = \"visual-index-changed:")
		assertContains(function, "currentVisualPageIndex = pageIndex")
		assertFalse(function.contains("bundleSource.invalidate("))
	}

	@Test
	fun exactTurnInsideDurableBlockingWindowStartsAnInvisibleWindowValidation() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(function, "requiredWindow.all(durableRasterPageIndices::contains)")
		assertContains(function, "event = \"ordinary-turn-reused\"")
		assertContains(function, "readerPageCanReusePreparedWindow(")
		assertContains(function, "onRequestPrewarm()")
		assertTrue(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = true,
				rasterRepairPending = false,
				prewarmPending = false
			)
		)
	}

	@Test
	fun exactTurnWithPendingRasterRepairUsesCenterChangeRecovery() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")
		val reuseDecision = "readerPageCanReusePreparedWindow("
		val repairCancellation = "cancelRasterRepairs(\"visual-index-changed:"

		assertContains(
			function,
			"rasterRepairPending = rasterRepairCallbacks.isNotEmpty()"
		)
		assertContains(function, repairCancellation)
		assertTrue(
			function.indexOf(reuseDecision) < function.indexOf(repairCancellation),
			"A non-reusable exact turn must fall through to center-change repair cancellation"
		)
		assertFalse(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = true,
				rasterRepairPending = true,
				prewarmPending = false
			)
		)
	}

	@Test
	fun exactTurnWithDeferredPrewarmUsesCenterChangeRecovery() {
		val source = readerRasterPreparationSource()
		val function = source.substringAfter(
			"fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?) {"
		).substringBefore("\n\tfun prewarmAdjacent()")

		assertContains(
			function,
			"visualCenterChanging = currentVisualPageIndex != pageIndex"
		)
		assertContains(
			function,
			"prewarmPending = prewarmInProgress || deferredPrewarmSessionId != null"
		)
		assertFalse(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = true,
				rasterRepairPending = false,
				prewarmPending = true
			)
		)
	}

	@Test
	fun sameCenterExactUpdatePreservesDeferredPrewarmOwnership() {
		assertTrue(
			readerPageCanReusePreparedWindow(
				reason = "page-turn:exact",
				requiredWindowDurable = true,
				visualCenterChanging = false,
				rasterRepairPending = false,
				prewarmPending = true
			)
		)
	}

	@Test
	fun passivePrewarmCannotRequestForegroundPreviewCoverage() {
		val source = readerRasterPreparationSource()
		val followUp = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterFollowUp(",
			endDelimiter = "private fun startRasterBatch("
		)
		val batch = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)

		assertContains(followUp, "targets = followUpTargets")
		assertContains(batch, "passiveRasterPreparationPort.start(")
		listOf(
			"reusePreparationShield(",
			"activePrewarmPassiveLease",
			"onStagingStarted",
			"ReaderForegroundWebViewMutationGeneration",
			"rasterBatchController.start(",
			"restoreLiveComposition("
		).forEach { forbidden -> assertFalse(batch.contains(forbidden), forbidden) }
	}

	@Test
	fun immediateDeckCannotPublishReadyBeforeTheCurrentChapterIsDurable() {
		val source = readerRasterPreparationSource()
		val initialDeck = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterCalibration(",
			endDelimiter = "private fun startRasterFollowUp("
		)
		val followUp = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterFollowUp(",
			endDelimiter = "private fun startRasterBatch("
		)
		val batch = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)
		val finish = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun finishPrewarm(",
			endDelimiter = "private fun deferPrewarm("
		)

		assertFalse(initialDeck.contains("hasPreparedBefore ="))
		assertFalse(batch.contains("hasPreparedBefore ="))
		assertContains(followUp, "checkNotNull(plan.blockingTargetsOrNull())")
		assertContains(followUp, "totalRequired = blockingTargets.size")
		assertContains(finish, "hasPreparedBefore = candidateBlockingPageIndices.isNotEmpty()")
		assertContains(finish, "durableRasterPageIndices += candidateBlockingPageIndices")
	}

	@Test
	fun persistenceRetryCorrelationStartsOnlyAfterLedgerAdmission() {
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val publication = bundle.substringAfter(
			"val persistenceAttemptId = ReaderPagePersistenceAttemptId("
		).substringBefore("publicationValueTransferred = true")
		val registration = publication.indexOf("val registration = publicationLedger.begin(")
		val correlation = publication.indexOf(
			"readerPageRasterPublicationRetryCorrelation("
		)

		assertTrue(registration >= 0 && registration < correlation)
		assertFalse(
			publication.contains(
				"persistenceRetryCorrelations[key.digest]?.withRelation("
			)
		)
	}

	@Test
	fun retryRetiresFailedWorkAndAllocatesOneFreshAttemptWithoutClearingValidCache() {
		val source = readerRasterPreparationSource()
		val retry = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "fun retryPreparation(expectedPreparationGeneration: Long? = failedPreparationGeneration): Long? {",
			endDelimiter = "fun onProfileBootstrapFailed()"
		)

		assertContains(retry, "failedPreparationGeneration")
		assertContains(retry, "destroyed ||")
		assertContains(retry, "retryPreparationInProgress ||")
		assertContains(retry, "expectedPreparationGeneration != preparationGeneration")
		assertContains(retry, ") return null")
		assertTrue(retry.indexOf(") return null") < retry.indexOf("retryPreparationInProgress = true"))
		assertTrue(retry.indexOf("retryPreparationInProgress = true") < retry.indexOf("cancelPrewarm("))
		assertContains(retry, "failedPreparationGeneration = null")
		assertEquals(1, Regex("Math\\.incrementExact\\(").findAll(retry).count())
		assertContains(retry, "return preparationGeneration")
		assertContains(retry, "cancelPrewarm(")
		assertContains(retry, "cancelRasterRepairs(")
		assertContains(retry, "cancelBackgroundPrefetch(")
		assertContains(retry, "passiveRasterPreparationPortProvider()?.cancel()")
		assertEquals(1, Regex("onRequestPrewarm\\(\\)").findAll(retry).count())
		assertFalse(retry.contains("bundleSource.invalidate("))
		assertFalse(retry.contains("bundleSource.invalidatePage("))
		assertFalse(retry.contains("bundleSource.trimMemory("))
	}

	@Test
	fun preparationGenerationFencesManifestCapturePublicationStateAndCompletionCallbacks() {
		val preparation = readerRasterPreparationSource()
		val passive = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val batch = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)
		val passiveBatch = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private class Batch(",
			endDelimiter = "private val manifestIssuer"
		)
		val liveManifestPort = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "internal fun interface ReaderPassiveRasterLiveManifestPort",
			endDelimiter = "internal interface ReaderPassiveRasterPreparationPort"
		)
		val manifest = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private fun prepareTarget(batch: Batch) {",
			endDelimiter = "private fun resolveTarget("
		)
		val capture = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private fun captureTarget(",
			endDelimiter = "private fun admitCapturedTarget("
		)
		val admission = requiredReaderSourceSlice(
			source = passive,
			startDelimiter = "private fun admitCapturedTarget(",
			endDelimiter = "private fun completeTarget("
		)
		val bundleAdmission = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun admitPassiveRasterCapture(",
			endDelimiter = "fun protectEncodedWindow("
		)

		assertContains(preparation, "failedPreparationGeneration")
		assertContains(batch, "preparationGeneration")
		assertContains(passiveBatch, "preparationGeneration")
		assertContains(liveManifestPort, "preparationGeneration: Long")
		assertContains(manifest, "preparationGeneration = batch.preparationGeneration")
		listOf(batch, manifest, capture, admission).forEach { callbackBoundary ->
			assertContains(callbackBoundary, "isPreparationGenerationCurrent")
		}
		assertContains(admission, "preparationGeneration = batch.preparationGeneration")
		assertContains(bundleAdmission, "preparationGeneration")
		assertContains(bundleAdmission, "isPreparationGenerationCurrent")
		assertTrue(
			bundleAdmission.lastIndexOf("isPreparationGenerationCurrent") <
				bundleAdmission.indexOf("putSnapshot("),
			"Persistent completion must reject a terminal preparation before cache publication."
		)
	}

	@Test
	fun lowMemoryRetiresAllPassiveWorkWithoutInvalidatingLiveOrPersistentCache() {
		val source = readerRasterPreparationSource()
		val callbacks = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private val memoryCallbacks = object : ComponentCallbacks2 {",
			endDelimiter = "\tinit {"
		)
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val trim = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun trimMemory(reason: String) {",
			endDelimiter = "fun retainedSnapshot("
		)

		assertContains(callbacks, "cancelPrewarm(")
		assertContains(callbacks, "cancelRasterRepairs(")
		assertContains(callbacks, "cancelBackgroundPrefetch(")
		assertContains(callbacks, "onPassiveRasterMemoryPressure(")
		assertContains(callbacks, "bundleSource.trimMemory(")
		assertContains(trim, "removeCachedSnapshot(")
		assertContains(trim, "trimDecodedToProtectedWindow()")
		assertFalse(callbacks.contains("bundleSource.invalidate("))
		assertFalse(callbacks.contains("foregroundWebViewOwnership"))
		assertFalse(callbacks.contains("webViewProvider("))
		assertFalse(trim.contains("rasterCache.clear"))
		assertFalse(trim.contains("persistentRasterCache.clear"))
	}

	@Test
	fun singlePageRepairUsesOnlyTheIsolatedPassiveRasterPort() {
		val source = readerRasterPreparationSource()
		val repair = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)

		assertContains(repair, "passiveRasterPreparationPortProvider()")
		assertContains(repair, "passiveRasterPreparationPort.start(")
		assertContains(repair, "ReaderPageRasterAcquisitionTrigger.Repair")
		assertContains(repair, "ReaderPageRasterBatchTarget(")
		assertContains(repair, "pageIndex = pageIndex")
		assertContains(repair, "authority = ReaderPageRasterTargetAuthority.OffscreenPassive")
		assertContains(repair, "readerPageRasterRepairedResult(")
		assertContains(repair, "passive-raster-unavailable")
		listOf(
			"acquirePassiveRasterLease(",
			"foregroundWebViewOwnership",
			"ReaderForegroundWebViewMutationGeneration",
			"rasterRepairBatchController.start(",
			"rasterRepairBatchController.cancel",
			"onStagingStarted",
			"reusePreparationShield(",
			"removePreparationShield(",
			"restoreLiveComposition(",
			"exposePageTurnPreview",
			"publishPreparationState("
		).forEach { forbidden -> assertFalse(repair.contains(forbidden), forbidden) }
	}

	@Test
	fun coalescedRepairFaultUpgradesTheInFlightDiagnosticRoot() {
		val source = readerRasterPreparationSource()
		val attach = source.substringAfter(
			"fun attachRasterRepairQaFault("
		).substringBefore("\n\tfun repairRasterPage(")

		assertContains(attach, "rasterRepairQaFaultCorrelations.putIfAbsent(pageIndex, correlation)")
		assertContains(attach, "rasterRepairDiagnostics[pageIndex]?.let { operation ->")
		assertContains(attach, "if (operation.qaFaultCorrelation == null)")
		assertContains(attach, "operation.copy(qaFaultCorrelation = activeCorrelation)")
	}

	@Test
	fun adjacentChapterPrefetchUsesTheIndependentPassiveIdleAdapter() {
		val source = readerRasterPreparationSource()
		val background = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun startBackgroundPrefetch(",
			endDelimiter = "private fun isBackgroundPrefetchActive("
		)
		val eligibility = requiredReaderSourceSlice(
			source = source,
			startDelimiter = "private fun isBackgroundPrefetchActive(",
			endDelimiter = "\n\n\tprivate "
		)

		assertContains(source, "Looper.myQueue().addIdleHandler")
		assertContains(background, "submission.targets")
		assertContains(background, "passiveRasterPreparationPort.start(")
		assertContains(
			background,
			"capacityPolicy = ReaderPageRasterCapacityPolicy.StopBackgroundRefill"
		)
		assertContains(background, "event = \"background-prefetch-started\"")
		assertContains(background, "event = \"background-prefetch-progress\"")
		assertContains(background, "\"background-prefetch-completed\"")
		assertContains(eligibility, "activeRasterRepairPageIndex == null")
		assertContains(eligibility, "rasterRepairCallbacks.isEmpty()")
		listOf(
			"rasterBackgroundBatchController.start(",
			"showBackgroundPrefetchShield(",
			"activeBackgroundPassiveLease",
			"trackVisualRestoration(",
			"onStagingStarted",
			"foregroundMutationGeneration",
			"publishPreparationState(",
			"reusePreparationShield("
		).forEach { forbidden -> assertFalse(background.contains(forbidden), forbidden) }
	}

	@Test
	fun adjacentChapterPassiveContractCannotAcceptShieldOrForegroundOwnership() {
		val adapter = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val contract = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "internal interface ReaderPassiveRasterPreparationPort",
			endDelimiter = "internal class ReaderPassiveRasterPreparationAdapter"
		)

		assertContains(contract, "targets: List<ReaderPageRasterBatchTarget>")
		assertContains(contract, "rasterGeneration: Long")
		assertContains(contract, "isStillCurrent: () -> Boolean")
		assertContains(contract, "onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit")
		assertContains(contract, "fun cancel()")
		listOf(
			"WebView",
			"ReaderForegroundWebViewOwnership",
			"ReaderForegroundWebViewMutationGeneration",
			"ReaderPageStaticWindowShield",
			"onStagingStarted",
			"restoreLiveComposition",
			"preview"
		).forEach { forbidden -> assertFalse(contract.contains(forbidden), forbidden) }
	}

	@Test
	fun applicationPanelShieldPreservesAuthoritySelectedFrameComposition() {
		val preparation = readerRasterPreparationSource()
		val host = readerSource("KomikkuReaderNativeFrameHost.android.kt")
		val window = readerSource("ReaderPageStaticWindowShield.android.kt")
		val preservationArgument =
			"preserveCurrentPresentation = shouldPreserveCurrentPresentation()"

		assertContains(preparation, "private val shouldPreserveCurrentPresentation")
		assertEquals(
			4,
			Regex(Regex.escape(preservationArgument)).findAll(preparation).count()
		)
		assertContains(host, "shouldPreserveCurrentPresentation = {")
		assertContains(host, "shellCoverVisible ||")
		assertContains(
			host,
			"presentationDecision?.layer?.let { it != ReaderPresentationLayer.Neutral } == true"
		)
		assertFalse(host.contains("latestRasterPreparationState.presentation"))
		assertContains(window, "preserveCurrentPresentation: Boolean = false")
		assertContains(window, "captureCurrentPresentation(")
		assertContains(window, "val presentationRoot = host.rootView")
		assertContains(window, "presentationRoot.draw(canvas)")
		assertFalse(window.contains("host.draw(canvas)"))
		assertContains(window, "private var ownedBitmap: Bitmap? = null")
		assertContains(window, "ownedBitmap?.recycle()")
	}
	@Test
	fun passivePersistenceRechecksExactLeaseCurrentnessBeforePublication() {
		val batch = readerRasterBatchSource()
		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val hydration = requiredReaderSourceSlice(
			source = batch,
			startDelimiter = "private fun hydrateTarget(session: Session, targetIndex: Int)",
			endDelimiter = "private fun submitMissingTargets("
		)
		val ensure = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun ensurePersistentSnapshot(",
			endDelimiter = "private fun cacheSnapshot("
		)
		val schedule = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "private fun schedulePersistentSnapshot(",
			endDelimiter = "private fun scheduleRasterPublication("
		)

		assertContains(hydration, "isStillCurrent = { isSessionActive(session) }")
		assertContains(ensure, "isStillCurrent: () -> Boolean")
		assertContains(ensure, "isStillCurrent = isStillCurrent")
		assertContains(schedule, "runCatching(isStillCurrent).getOrDefault(false)")
		assertTrue(
			schedule.lastIndexOf("runCatching(isStillCurrent).getOrDefault(false)") <
				schedule.indexOf("publicationLedger.begin("),
			"Lease currentness must be rechecked before publication admission"
		)
	}

	@Test
	fun repairCannotAcquireForegroundPassiveOwnershipOrRestoreAPreview() {
		val preparation = readerRasterPreparationSource()
		val repair = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)
		val cancellation = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun cancelRasterRepairs(reason: String) {",
			endDelimiter = "private fun beginBlockingBackgroundPrefetchSession()"
		)

		assertContains(repair, "passiveRasterPreparationPort.start(")
		assertContains(cancellation, "passiveRasterPreparationPortProvider()?.cancel()")
		listOf(
			"acquirePassiveRasterLease(",
			"isPassiveRasterLeaseCurrent(",
			"foregroundWebViewOwnership",
			"mutationGeneration",
			"trackVisualRestoration(",
			"restoreLiveComposition(",
			"preparationShield"
		).forEach { forbidden ->
			assertFalse(repair.contains(forbidden), forbidden)
			assertFalse(cancellation.contains(forbidden), forbidden)
		}
	}

	@Test
	fun unavailablePassiveHostFailsRetryablyWithoutForegroundFallback() {
		val preparation = readerRasterPreparationSource()
		val prewarm = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "fun prewarmAdjacent(): Boolean {",
			endDelimiter = "private fun initializeRasterCacheAndQueryPlan("
		)
		val repair = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)
		val background = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startBackgroundPrefetch(",
			endDelimiter = "private fun isBackgroundPrefetchActive("
		)

		assertContains(prewarm, "passive-raster-unavailable")
		assertContains(prewarm, "ReaderPagePreparationPhase.Failed")
		assertContains(prewarm, "retryable = true")
		assertContains(repair, "passive-raster-unavailable")
		assertContains(repair, "ReaderPageRasterRepairResult.Failed")
		assertContains(background, "deferBackgroundPrefetchStart(submission, prefetch)")
		listOf(prewarm, repair, background).forEach { passivePath ->
			assertFalse(passivePath.contains("rasterBatchController.start("))
			assertFalse(passivePath.contains("rasterRepairBatchController.start("))
			assertFalse(passivePath.contains("rasterBackgroundBatchController.start("))
			assertFalse(passivePath.contains("acquirePassiveRasterLease("))
			assertFalse(passivePath.contains("restoreLiveComposition("))
			assertFalse(passivePath.contains("while ("))
		}
	}

	@Test
	fun everyOffscreenRasterPathUsesTheIsolatedPassiveAdapter() {
		val preparation = readerRasterPreparationSource()
		val prewarm = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "fun prewarmAdjacent(): Boolean {",
			endDelimiter = "private fun initializeRasterCacheAndQueryPlan("
		)
		val prewarmBatch = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startRasterBatch(",
			endDelimiter = "private fun obtainRasterReference("
		)
		val repair = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startNextRasterRepair() {",
			endDelimiter = "private fun deferRasterRepair("
		)
		val background = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private fun startBackgroundPrefetch(",
			endDelimiter = "private fun isBackgroundPrefetchActive("
		)
		val currentReference = requiredReaderSourceSlice(
			source = preparation,
			startDelimiter = "private class ReaderPageBundleRasterCurrentReferencePort(",
			endDelimiter = "internal fun readerPageTurnCanStartPassivePrewarm("
		)

		assertContains(preparation, "ReaderPassiveRasterPreparationPort")
		assertContains(prewarm, "passiveRasterPreparationPortProvider()")
		assertContains(prewarmBatch, "passiveRasterPreparationPort.start(")
		assertContains(background, "passiveRasterPreparationPort.start(")
		listOf(
			"acquirePassiveRasterLease(",
			"ReaderForegroundWebViewMutationGeneration",
			"rasterBatchController.start(",
			"rasterBackgroundBatchController.start(",
			"reusePreparationShield(",
			"showBackgroundPrefetchShield(",
			"onStagingStarted"
		).forEach { forbidden ->
			assertFalse(prewarm.contains(forbidden), forbidden)
			assertFalse(prewarmBatch.contains(forbidden), forbidden)
			assertFalse(background.contains(forbidden), forbidden)
		}
		assertContains(repair, "passiveRasterPreparationPortProvider()")
		assertContains(repair, "passiveRasterPreparationPort.start(")
		listOf(
			"acquirePassiveRasterLease(",
			"foregroundWebViewOwnership",
			"rasterRepairBatchController.start(",
			"reusePreparationShield(",
			"onStagingStarted",
			"restoreLiveComposition("
		).forEach { forbidden -> assertFalse(repair.contains(forbidden), forbidden) }
		assertContains(currentReference, "bundleSource.captureCurrentSurface(")
		listOf(
			"acquirePassiveRasterLease(",
			"rasterRepairBatchController.start(",
			"rasterBatchController.restoreLiveComposition(",
			"foregroundWebViewOwnership.canAcquirePassive()",
			"exposePageTurnPreview("
		).forEach { sharedPreviewRoute ->
			assertFalse(
				preparation.contains(sharedPreviewRoute),
				"Shared-foreground preview route remains reachable: $sharedPreviewRoute"
			)
		}

		val adapter = readerSource("ReaderPassiveRasterPreparationAdapter.android.kt")
		val adapterContract = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "internal interface ReaderPassiveRasterPreparationPort",
			endDelimiter = "internal class ReaderPassiveRasterPreparationAdapter"
		)
		assertContains(adapter, "ReaderPassiveRasterManifestIssuer")
		assertContains(adapter, "ReaderPassiveRasterPrototypeSession<Bitmap>")
		assertContains(adapter, "session.commit(manifest")
		assertContains(adapter, "committed.capture captured@{")
		assertContains(adapter, "bundleSource.admitPassiveRasterCapture(")
		assertContains(adapter, "fun pause()")
		assertContains(adapter, "fun resume()")
		assertContains(adapter, "fun close()")
		val durableCommittedAuthority = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "private fun confirmDurableCommittedTarget(",
			endDelimiter = "private fun authorityMismatch("
		)
		val committedCaptureTransfer = requiredReaderSourceSlice(
			source = adapter,
			startDelimiter = "private fun captureCommittedTarget(",
			endDelimiter = "private fun admitCapturedTarget("
		)
		assertContains(
			durableCommittedAuthority,
			"if (!batch.releaseCommittedCapture(committed))"
		)
		assertContains(durableCommittedAuthority, "ReaderPageRasterBatchOutcome.Failed(")
		assertContains(
			committedCaptureTransfer,
			"if (!batch.transferCommittedCapture(committed))"
		)
		assertContains(committedCaptureTransfer, "ReaderPageRasterBatchOutcome.Failed(")
		listOf(
			"WebView",
			"ReaderForegroundWebViewMutationGeneration",
			"ReaderPageStaticWindowShield",
			"onStagingStarted",
			"exposePageTurnPreviewFinal",
			"confirmPageTurnPreviewPresentation",
			"restorePageTurnLiveComposition",
			"ReaderBridgeEvent",
			"putSnapshot(",
			"submitDeck"
		).forEach { forbidden -> assertFalse(adapterContract.contains(forbidden), forbidden) }

		val bundle = readerSource("ReaderPageTurnBundleSource.android.kt")
		val admission = requiredReaderSourceSlice(
			source = bundle,
			startDelimiter = "fun admitPassiveRasterCapture(",
			endDelimiter = "fun protectEncodedWindow("
		)
		assertContains(admission, "ReaderPassiveRasterAdmissionContext(")
		assertContains(admission, "readerAdmitPassiveRaster(")
		assertContains(admission, "admitted.transferRaster()")
		assertContains(admission, "putSnapshot(")
		assertContains(admission, "generation == activeGeneration")
		assertContains(admission, "physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get()")
		assertFalse(admission.contains("capturePreparedRasterPage("))
		assertFalse(admission.contains("restoreLiveComposition("))

		val host = readerSource("KomikkuReaderNativeFrameHost.android.kt")
		assertContains(host, "ReaderPassiveRasterWebViewHost(")
		assertContains(host, "ReaderPassiveRasterPrototypeSession(")
		assertContains(host, "ReaderPassiveRasterPreparationAdapter(")
		assertContains(host, "passiveRasterPreparationAdapter?.pause()")
		assertContains(host, "passiveRasterPreparationAdapter?.resume()")
		assertContains(host, "replacePassiveRasterPreparationAdapter(")
		assertContains(host, "closePassiveRasterPreparationAdapter()")

		val pageTurns = readerAssetSource("navic-reader-page-turns.js")
		val bridge = readerAssetSource("navic-reader.js")
		val location = readerAssetSource("navic-reader-location.js")
		val committedRelocation = requiredReaderSourceSlice(
			source = location,
			startDelimiter = "function postLocationChanged(",
			endDelimiter = "function captureDuplicatePageBaselines("
		)
		val publicationOpen = requiredReaderSourceSlice(
			source = bridge,
			startDelimiter = "async openPublication(",
			endDelimiter = "async resolveReaderNavigationTarget("
		)
		val manifestInputs = requiredReaderSourceSlice(
			source = pageTurns,
			startDelimiter = "function pageTurnPassiveRasterManifestInputs(",
			endDelimiter = "function pageTurnLivePresentationTargetMatchesCurrent("
		)
		assertFalse(committedRelocation.contains("schedulePageTurnPassiveRasterCanonicalCommit("))
		assertContains(publicationOpen, "ensureCompletePaginationProfile(")
		assertFalse(publicationOpen.contains("schedulePageTurnPassiveRasterCanonicalCommit("))
		assertFalse(pageTurns.contains("function schedulePageTurnPassiveRasterCanonicalCommit("))
		assertFalse(pageTurns.contains("ReaderPassiveRasterCanonicalCommitScope"))
		assertFalse(pageTurns.contains("passiveRasterCanonicalCommitTargetValue"))
		assertFalse(pageTurns.contains("passiveRasterCanonicalForegroundGenerationIsCurrent"))
		assertContains(manifestInputs, "const target = this.pageTurnLivePresentationTargetValue")
		assertContains(manifestInputs, "pageTurnLivePresentationTargetCanonicalCommit.call(")
		assertFalse(manifestInputs.contains("readerCommitTextPage("))
		assertFalse(manifestInputs.contains("passiveRasterCanonicalCommitTargetValue"))
		assertContains(manifestInputs, "opaqueCaptureTarget")
		assertContains(manifestInputs, "visualPageOrdinal")
		assertContains(manifestInputs, "paginationFingerprint")
		assertContains(manifestInputs, "layoutFingerprint")
		assertContains(manifestInputs, "decorationFingerprint")
		assertContains(manifestInputs, "rasterGeneration: generation")
		assertFalse(manifestInputs.contains("target.rasterGeneration"))
		assertFalse(bridge.contains("passiveRasterCanonicalCommitTargetValue"))
		assertContains(bridge, "pageTurnPassiveRasterManifestInputs:")
	}
}

private fun drainRasterPreparationOwnership(
	ownership: ReaderRasterPreparationPhysicalOwnership
) {
	val rows = checkNotNull(ownership.snapshotFrozenOwnership()).resources
	rows.sortedBy { it.kind != ReaderTransitionResourceKind.CallbackRegistration }
		.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				ownership.drainFrozenOwnership(row.physicalIdentity) {}
			)
		}
}

private class FenceRacePassiveRasterPreparationPort(
	private val blockFirstStart: Boolean,
	private val acceptStarts: Boolean = false
) : ReaderPassiveRasterPreparationPort {
	val firstStartEntered = CountDownLatch(1)
	val allowFirstStartToReturn = CountDownLatch(1)
	val starts = AtomicInteger()
	var onFirstStart: () -> Unit = {}

	override val isAvailable: Boolean = true

	override fun start(
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		rasterGeneration: Long,
		preparationGeneration: Long,
		isPreparationGenerationCurrent: (Long) -> Boolean,
		isStillCurrent: () -> Boolean,
		trigger: ReaderPageRasterAcquisitionTrigger,
		capacityPolicy: ReaderPageRasterCapacityPolicy,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean {
		val attempt = starts.incrementAndGet()
		if (attempt == 1) {
			firstStartEntered.countDown()
			onFirstStart()
			if (blockFirstStart) {
				check(allowFirstStartToReturn.await(5, TimeUnit.SECONDS))
			}
		}
		reference.release()
		return acceptStarts
	}

	override fun cancel() = Unit
	override fun pause() = Unit
	override fun resume() = Unit
	override fun close() = Unit
}

private class BackgroundEligibilityCheckGate(expectedChecks: Int) {
	val firstCheckEntered = CountDownLatch(1)
	val allChecksEntered = CountDownLatch(expectedChecks)
	val allowChecksToReturn = CountDownLatch(1)

	fun awaitCheck() {
		firstCheckEntered.countDown()
		allChecksEntered.countDown()
		check(allowChecksToReturn.await(5, TimeUnit.SECONDS))
	}
}

private class FenceRaceWebView(activity: Activity) : WebView(activity) {
	@Volatile
	private var attachmentCheckGate: BackgroundEligibilityCheckGate? = null

	fun blockAttachmentChecks(expectedChecks: Int): BackgroundEligibilityCheckGate =
		BackgroundEligibilityCheckGate(expectedChecks).also { gate ->
			attachmentCheckGate = gate
		}

	override fun isAttachedToWindow(): Boolean {
		attachmentCheckGate?.awaitCheck()
		return super.isAttachedToWindow()
	}
}

private class RestoreBeforeDeferredResumeGate : LinkedHashMap<
	Int,
	MutableList<(ReaderPageRasterRepairResult) -> Unit>
>() {
	val restoreReachedBeforeDeferredResume = CountDownLatch(1)
	val allowDeferredResume = CountDownLatch(1)

	override val keys: MutableSet<Int>
		get() {
			restoreReachedBeforeDeferredResume.countDown()
			check(allowDeferredResume.await(5, TimeUnit.SECONDS))
			return super.keys
		}
}

private class RasterFenceControllerFixture private constructor(
	val controller: ReaderPageRasterPreparationController,
	private val webView: FenceRaceWebView,
	private val bundleSource: ReaderPageTurnBundleSource,
	private val snapshot: ReaderPageSlideSnapshot,
	private val destroyActivity: () -> Unit
) {
	fun prepareRepair(pageIndex: Int) {
		controller.setPrivateField("currentVisualPageIndex", pageIndex)
		controller.setPrivateField("preparedPageCount", pageIndex + 2)
		controller.setPrivateField("preparedRepairPageIndices", setOf(pageIndex))
	}

	fun markRepairActive(
		pageIndex: Int,
		onComplete: (ReaderPageRasterRepairResult) -> Unit
	) {
		controller.privateField<
			MutableMap<Int, MutableList<(ReaderPageRasterRepairResult) -> Unit>>
		>("rasterRepairCallbacks")[pageIndex] = mutableListOf(onComplete)
		controller.setPrivateField("activeRasterRepairPageIndex", pageIndex)
		controller.setPrivateField("activeRasterRepairSessionId", 1L)
	}

	fun startPrewarmBatch(onOutcome: (ReaderPageRasterBatchOutcome) -> Unit) {
		controller.setPrivateField("currentVisualPageIndex", 20)
		controller.setPrivateField("prewarmSession", 1L)
		controller.setPrivateField("prewarmInProgress", true)
		val callback: (ReaderPageRasterBatchOutcome) -> Unit = { outcome ->
			onOutcome(outcome)
			controller.invokePrivate("finishPrewarm", outcome)
		}
		snapshot.retain()
		controller.invokePrivate(
			"startRasterBatch",
			webView,
			1L,
			"fence-race",
			ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
			snapshot,
			listOf(ReaderPageRasterBatchTarget(20, ReaderPageRasterPriority.Current)),
			0,
			1,
			callback
		)
	}

	fun prepareBackgroundSubmission(): ReaderPageAdjacentChapterPrefetchSubmission {
		val generation = bundleSource.currentGeneration()
		val chapter = ReaderPageAdjacentChapterPrefetchChapter(
			identity = ReaderPageAdjacentChapterIdentity(
				direction = ReaderPageAdjacentChapterDirection.Next,
				chapterIndex = 5,
				pageStartIndex = 36,
				pageCount = 4
			),
			targets = listOf(
				ReaderPageRasterBatchTarget(36, ReaderPageRasterPriority.NextChapter)
			)
		)
		val prefetchClass = Class.forName(
			"paige.navic.ui.screens.reader.ReaderPageRasterBackgroundPrefetch"
		)
		val constructor = prefetchClass.declaredConstructors.single { it.parameterCount == 9 }
		constructor.isAccessible = true
		val prefetch = constructor.newInstance(
			webView,
			20,
			ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
			4,
			12,
			24,
			listOf(chapter),
			generation,
			0L
		)
		controller.setPrivateField("durableBackgroundPrefetch", prefetch)
		controller.onRasterProfileEpochChanged(7L)
		controller.onPreparedActiveDeckChanged(
			ReaderPagePreparedActiveDeck(
				rasterProfileEpoch = 7L,
				rasterEpoch = generation,
				sourceCenterPageIndex = 20,
				generationId = 41L,
				preparationGeneration = 0L
			)
		)
		val coordinator = controller.privateField<ReaderPageAdjacentChapterPrefetchCoordinator>(
			"adjacentChapterPrefetchCoordinator"
		)
		return checkNotNull(
			coordinator.privateField<ReaderPageAdjacentChapterPrefetchSubmission?>(
				"activeSubmission"
			)
		)
	}

	fun backgroundPrefetch(): Any =
		controller.privateField<Any>("durableBackgroundPrefetch")

	fun blockRestoreBeforeDeferredBackgroundResume(): RestoreBeforeDeferredResumeGate =
		RestoreBeforeDeferredResumeGate().also { gate ->
			controller.setPrivateField("rasterRepairCallbacks", gate)
		}

	fun blockBackgroundEligibilityChecks(
		expectedChecks: Int
	): BackgroundEligibilityCheckGate = webView.blockAttachmentChecks(expectedChecks)

	fun dropPostedBackgroundStartWhileIneligible() {
		controller.setPrivateField("prewarmInProgress", true)
		drainMainLooper()
		controller.setPrivateField("prewarmInProgress", false)
	}

	fun markBackgroundPhysicalStartActive(
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	) {
		controller.setPrivateField("backgroundBatchSubmission", submission)
	}

	fun isBackgroundSubmissionActive(
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	): Boolean = controller
		.privateField<ReaderPageAdjacentChapterPrefetchCoordinator>(
			"adjacentChapterPrefetchCoordinator"
		)
		.isActive(submission)

	fun drainMainLooper() {
		Shadows.shadowOf(Looper.getMainLooper()).idle()
	}

	suspend fun close() {
		controller.destroyAndJoin()
		bundleSource.closeAndJoin()
		snapshot.releaseCacheOwnership()
		destroyActivity()
	}

	companion object {
		fun create(
			port: ReaderPassiveRasterPreparationPort
		): RasterFenceControllerFixture {
			val activityController = Robolectric.buildActivity(Activity::class.java).setup()
			val activity = activityController.get()
			val host = FrameLayout(activity)
			val webView = FenceRaceWebView(activity)
			host.addView(webView)
			activity.setContentView(host)
			Shadows.shadowOf(Looper.getMainLooper()).idle()
			val bundleSource = ReaderPageTurnBundleSource().also {
				it.invalidate("task394-fence-race")
			}
			val snapshot = rasterPreparationFenceSnapshot()
			val retainSnapshot: (Int, ReaderPageTurnTransitionKind) -> ReaderPageSlideSnapshot? =
				{ _, _ ->
					snapshot.retain()
					snapshot
				}
			val controller = ReaderPageRasterPreparationController(
				host = host,
				webViewProvider = { webView },
				bundleSource = bundleSource,
				fenceBundleOwners = {},
				closeBundleOwners = {},
				passiveRasterPreparationPortProvider = { port },
				currentLayoutSnapshot = retainSnapshot,
				initializeRasterCache = {},
				retainedSnapshot = retainSnapshot
			)
			return RasterFenceControllerFixture(
				controller = controller,
				webView = webView,
				bundleSource = bundleSource,
				snapshot = snapshot,
				destroyActivity = activityController::destroy
			)
		}
	}
}

private fun rasterPreparationFenceSnapshot(): ReaderPageSlideSnapshot = ReaderPageSlideSnapshot(
	key = ReaderPageSlideSnapshotKey(
		visualPageIndex = 20,
		kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
		bitmapQuality = ReaderPageBitmapQuality.Balanced,
		bitmapWidth = 20,
		bitmapHeight = 30,
		surfaceWidth = 20,
		surfaceHeight = 30
	),
	bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
	surfaceRectInWindow = Rect(0, 0, 20, 30),
	leafGeometry = ReaderPageTurnLeafGeometry(
		fullLeafRect = ReaderPageTurnPixelRect(0, 0, 20, 30),
		leftLeafRect = ReaderPageTurnPixelRect(0, 0, 9, 30),
		gutterRect = ReaderPageTurnPixelRect(9, 0, 11, 30),
		rightLeafRect = ReaderPageTurnPixelRect(11, 0, 20, 30)
	),
	reverseFaceColor = 0xffead9ae.toInt()
)

private fun Any.setPrivateField(name: String, value: Any?) {
	javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
}

@Suppress("UNCHECKED_CAST")
private fun <Value> Any.privateField(name: String): Value =
	javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as Value

private fun Any.invokePrivateIfPresent(name: String, vararg arguments: Any?): Boolean {
	val method = javaClass.declaredMethods.singleOrNull {
		it.name == name && it.parameterCount == arguments.size
	} ?: return false
	method.isAccessible = true
	try {
		method.invoke(this, *arguments)
	} catch (failure: InvocationTargetException) {
		throw checkNotNull(failure.cause)
	}
	return true
}

private fun Any.invokePrivate(name: String, vararg arguments: Any?): Any? {
	val method = javaClass.declaredMethods.single {
		it.name == name && it.parameterCount == arguments.size
	}
	method.isAccessible = true
	return try {
		method.invoke(this, *arguments)
	} catch (failure: InvocationTargetException) {
		throw checkNotNull(failure.cause)
	}
}

private fun rasterPreparationDescriptor(
	operation: ReaderRasterPreparationPhysicalOperation =
		ReaderRasterPreparationPhysicalOperation.Repair,
	pageOrdinal: Int? = 2
) = ReaderRasterPreparationPhysicalRestartDescriptor(
	binding = ReaderPresentationBinding(
		foliateSessionId = "raster-preparation-session",
		publicationGeneration = 3L,
		viewportGeneration = 5L,
		profileGeneration = 7L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(
			"raster-preparation-session",
			11L
		),
		preparationGeneration = 13L,
		rasterGeneration = 17L,
		textureGeneration = 19L
	),
	operation = operation,
	preparationGeneration = 13L,
	rasterGeneration = 17L,
	pageOrdinal = pageOrdinal
)

private fun requiredReaderSourceSlice(
	source: String,
	startDelimiter: String,
	endDelimiter: String
): String {
	val afterStart = source.substringAfter(startDelimiter, missingDelimiterValue = "")
	assertTrue(afterStart.isNotBlank(), "Missing exact start delimiter: $startDelimiter")
	val slice = afterStart.substringBefore(endDelimiter, missingDelimiterValue = "")
	assertTrue(slice.isNotBlank(), "Missing exact end delimiter: $endDelimiter")
	return slice
}

private fun readerRasterBatchSource(): String = readerSource(
	"ReaderPageRasterBatchController.android.kt"
)

private fun readerRasterPreparationSource(): String = readerSource(
	"ReaderPageRasterPreparationController.android.kt"
)

private fun readerSource(fileName: String): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate $fileName")
}

private fun readerAssetSource(fileName: String): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/assets/reader/$fileName"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate reader asset $fileName")
}
