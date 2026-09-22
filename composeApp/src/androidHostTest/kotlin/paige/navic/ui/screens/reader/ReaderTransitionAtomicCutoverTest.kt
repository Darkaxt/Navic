package paige.navic.ui.screens.reader

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderExternalRelocationIntent
import paige.navic.reader.ReaderExternalRelocationSource
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderSemanticExecutableResult
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionInputLease
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration

class ReaderTransitionAtomicCutoverTest {
	@Test
	fun resourceRegistrationUsesOwnerIndependentRetirementOrder() {
		val id = transitionId()
		val transitionKey = ReaderTransitionResourceKey(
			ReaderTransitionResourceOwnerId.TransitionOwned(id),
			ReaderTransitionResourceKind.Deck,
			41L
		)
		val adoptedKey = ReaderTransitionResourceKey(
			ReaderTransitionResourceOwnerId.AdoptedPredecessor(ReaderAdoptedPredecessorSeedId.fromValidatedImport(43L)),
			ReaderTransitionResourceKind.Deck,
			47L
		)
		val first = ReaderTransitionResourceRegistration(
			transitionKey,
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val second = ReaderTransitionResourceRegistration(
			adoptedKey,
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val ledger = ReaderTransitionReleaseLedger()

		assertTrue(ledger.register(first))
		assertTrue(ledger.register(second))
		assertEquals(first, ledger.requestRelease(first.key)?.registration)
		assertEquals(second, ledger.requestRelease(second.key)?.registration)
		assertTrue(ledger.confirmReleased(second))
		assertTrue(ledger.confirmReleased(first))
		assertEquals(2L, ledger.retentionSnapshot().contiguousReleasedThrough)
	}

	@Test
	fun committedPresentationRequiresExactResourceRegistration() {
		val binding = binding()
		val id = transitionId(binding)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), binding, 31L, 37L, 1200, 800
			)
		)
		val key = ReaderTransitionResourceKey(
			ReaderTransitionResourceOwnerId.TransitionOwned(id),
			ReaderTransitionResourceKind.FrameHandoff,
			41L
		)

		val wrongRegistration = ReaderTransitionResourceRegistration(
			key.copy(opaqueId = 43L),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		assertFailsWith<IllegalArgumentException> {
			paige.navic.reader.ReaderCommittedTransition(
				id,
				owner,
				binding,
				key,
				wrongRegistration
			)
		}
	}

	@Test
	fun allocationMismatchRejectsBeforePhysicalWork() {
		var physicalCalls = 0
		val allocator = ReaderMaterialGenerationAllocator(
			readerSessionGeneration = 3L,
			prepare = { physicalCalls += 1 }
		)
		val id = transitionId()
		val semanticBinding = binding().copy(
			preparationGeneration = null,
			rasterGeneration = null,
			textureGeneration = null
		)
		val wrong = semanticBinding.copy(publicationGeneration = 99L)

		val rejected = allocator.allocate(
			ReaderTransitionCommand.AllocateMaterialBinding(id, wrong)
		)
		assertIs<ReaderPortCommandResult.Rejected>(rejected)
		assertEquals(0, physicalCalls)

		val exactId = id.copy(expectedBinding = ReaderExpectedPresentationBinding.Exact(semanticBinding))
		val accepted = allocator.allocate(
			ReaderTransitionCommand.AllocateMaterialBinding(exactId, semanticBinding)
		)
		assertIs<ReaderPortCommandResult.Accepted>(accepted)
		assertEquals(0, physicalCalls, "allocation itself must not start raster work")
	}

	@Test
	fun ownerAndInputCommitIsOneAtomicPublication() {
		val binding = binding()
		val id = transitionId(binding)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), binding, 31L, 37L, 1200, 800
			)
		)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(id),
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val publisher = ReaderOwnerAndInputPublicationBarrier()

		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			publisher.publish(
				commitCommand(
					id, owner, binding, registration, ReaderTransitionInputLease.CoverActions, 1L
				)
			)
		)
		assertEquals(1, publisher.atomicCommitCount)
		assertEquals(owner, publisher.snapshot?.owner)
		assertEquals(ReaderTransitionInputLease.CoverActions, publisher.snapshot?.lease)
		assertFalse(publisher.observedPartialApplication)
	}

	@Test
	fun successorOwnerAndInputCommitAtomicallyReplacesPriorSnapshot() {
		val firstBinding = binding()
		val firstId = transitionId(firstBinding)
		val firstOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), firstBinding, 31L, 37L, 1200, 800
			)
		)
		val firstRegistration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(firstId),
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val secondBinding = firstBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L)
		)
		val secondId = transitionId(secondBinding, sequence = 8L)
		val secondOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(43L), secondBinding, 47L, 53L, 1200, 800
			)
		)
		val secondRegistration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(secondId),
				ReaderTransitionResourceKind.FrameHandoff,
				59L
			),
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val publisher = ReaderOwnerAndInputPublicationBarrier()
		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			publisher.publish(
				commitCommand(
					firstId,
					firstOwner,
					firstBinding,
					firstRegistration,
					ReaderTransitionInputLease.CoverActions,
					1L
				)
			)
		)

		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			publisher.publish(
				commitCommand(
					secondId,
					secondOwner,
					secondBinding,
					secondRegistration,
					ReaderTransitionInputLease.ChromeOnly,
					2L
				)
			)
		)
		assertEquals(2, publisher.atomicCommitCount)
		assertEquals(secondOwner, publisher.snapshot?.owner)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, publisher.snapshot?.lease)
		assertFalse(publisher.observedPartialApplication)
	}

	@Test
	fun combinedPublicationUsesPhysicalSafetyNarrowingBeforeItsSingleWrite() {
		val binding = binding()
		val id = transitionId(binding)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(29L), binding, 31L, 37L, 1200, 800
			)
		)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.TransitionOwned(id),
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val published = mutableListOf<ReaderOwnerAndInputPublicationSnapshot>()
		val barrier = ReaderOwnerAndInputPublicationBarrier(
			narrowOrVeto = { ReaderTransitionInputLease.ChromeOnly },
			publishAtomically = { snapshot -> published += snapshot; true }
		)

		assertIs<paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied>(
			barrier.publish(
				commitCommand(
					id,
					owner,
					binding,
					registration,
					ReaderTransitionInputLease.CoverActions,
					1L
				)
			)
		)
		assertEquals(ReaderTransitionInputLease.ChromeOnly, published.single().lease)
		assertEquals(published.single(), barrier.snapshot)
		assertEquals(1, barrier.atomicCommitCount)
	}

	@Test
	fun semanticRegistryAndActiveSlotFreezeAsExactRestorableSourceRows() {
		val tokens = ReaderLegacySourceLocalTokenAllocator()
		val registry = ReaderSemanticExecutableRequestRegistry(
			readerSessionGeneration = 3L,
			tokenAllocator = tokens
		)
		var delayedCallback: ((paige.navic.reader.ReaderPresentationEventReceipt) -> Unit)? = null
		var delayedOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand? = null
		val activeHandle = registry.register { origin, _, callback ->
			delayedOrigin = origin
			delayedCallback = callback
			ReaderSemanticExecutableResult.Accepted
		}
		val pendingHandle = registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
		val executor = ReaderSemanticCommandExecutor(registry, tokens)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, activeHandle),
					activeHandle
				),
				onRegistration = { }
			) { }
		)
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 3L,
			freezeToken = ReaderLegacyFreezeToken(71L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			executor.freezeForTransitionActivation(domain)
		)
		val rows = executor.snapshotFrozenOwnership()
		assertEquals(2, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertTrue(rows.all {
			it.physicalIdentity.source == ReaderLegacyInventorySource.SemanticCommandSlot
		})
		assertEquals(
			ReaderSemanticCommandResult.RejectedBeforeMutation(
				paige.navic.reader.ReaderTransitionFailureReason.PortRejected
			),
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, pendingHandle),
					pendingHandle
				),
				onRegistration = { }
			) { }
		)
		assertEquals(rows, executor.snapshotFrozenOwnership())
		assertFailsWith<IllegalStateException> { registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted } }
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				executor.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		delayedCallback?.invoke(semanticReceipt(checkNotNull(delayedOrigin)))
		assertEquals(0, executor.activeSlotCount)
		assertEquals(0, registry.activeHandleCount)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			executor.restoreAfterTransitionActivation(domain)
		)
		assertTrue(registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }.value > 0L)
	}

	@Test
	fun synchronousSemanticCallbacksRemainObservableAfterSlotRetirement() {
		val trace = mutableListOf<String>()
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val origins = mutableListOf<paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand>()
		val receipts = mutableListOf<paige.navic.reader.ReaderPresentationEventReceipt>()
		val handle: ReaderSemanticRequestHandle = registry.register { origin, _, callback ->
			trace += "invoke"
			origins += origin
			val receipt = semanticReceipt(origin)
			callback(receipt)
			callback(receipt)
			ReaderSemanticExecutableResult.Accepted
		}
		val executor = ReaderSemanticCommandExecutor(registry)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)

		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
					handle
				),
				onRegistration = { }
			) { receipt -> receipts += receipt }
		)
		assertEquals(listOf("invoke"), trace)
		assertEquals(1, origins.size)
		assertEquals(id, origins.single().transitionId)
		assertEquals(2, receipts.size)
		assertTrue(receipts.all { it.originatingTransitionId == id })
		assertEquals(0, registry.activeHandleCount)
		assertEquals(0, executor.activeSlotCount)
	}

	@Test
	fun retiredSemanticRegistrationCannotRetireNewerSlotWithSameTransitionIdentity() {
		lateinit var oldOrigin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
		lateinit var oldCallback: (paige.navic.reader.ReaderPresentationEventReceipt) -> Unit
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val firstHandle = registry.register { origin, _, callback ->
			oldOrigin = origin
			oldCallback = callback
			ReaderSemanticExecutableResult.Accepted
		}
		val secondHandle = registry.register { _, _, _ ->
			ReaderSemanticExecutableResult.Accepted
		}
		val executor = ReaderSemanticCommandExecutor(registry)
		val id = transitionId().copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)
		lateinit var firstRegistration: ReaderSemanticCommandRegistration
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, firstHandle),
					firstHandle
				),
				onRegistration = { firstRegistration = it },
				onReceipt = { }
			)
		)
		assertEquals(1, executor.activeSlotCount)
		firstRegistration.retire()
		firstRegistration.retire()
		assertEquals(0, executor.activeSlotCount)
		lateinit var secondRegistration: ReaderSemanticCommandRegistration
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			executor.synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					id,
					ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, secondHandle),
					secondHandle
				),
				onRegistration = { secondRegistration = it },
				onReceipt = { }
			)
		)
		assertEquals(1, executor.activeSlotCount)

		oldCallback(semanticReceipt(oldOrigin))

		assertEquals(1, executor.activeSlotCount)
		secondRegistration.retire()
		assertEquals(0, executor.activeSlotCount)
	}

	@Test
	fun workerMutationStartBeforeRejectOrThrowAlwaysClassifiesPostMutation() {
		val worker = Executors.newSingleThreadExecutor()
		try {
			repeat(16) { cycle ->
				listOf(false, true).forEach { throws ->
					val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
					val mutationRecorded = CountDownLatch(1)
					lateinit var mutationFuture: java.util.concurrent.Future<*>
					val handle = registry.register { _, mutationStart, _ ->
						mutationFuture = worker.submit {
							mutationStart.mutationStarted()
							mutationRecorded.countDown()
						}
						assertTrue(mutationRecorded.await(2L, TimeUnit.SECONDS))
						if (throws) error("private semantic detail")
						ReaderSemanticExecutableResult.Rejected
					}
					val executor = ReaderSemanticCommandExecutor(registry)
					lateinit var registration: ReaderSemanticCommandRegistration
					val id = transitionId(sequence = cycle.toLong() + 1L).copy(
						operation = ReaderTransitionOperation.ExternalSemanticRelocation
					)

					val result = executor.synchronize(
						ReaderTransitionCommand.RequestSemanticSynchronization(
							id,
							ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
							handle
						),
						onRegistration = { registration = it },
						onReceipt = { }
					)

					assertEquals(
						if (throws) {
							ReaderSemanticCommandResult.ThrewAfterMutationStarted
						} else {
							ReaderSemanticCommandResult.RejectedAfterMutationStarted
						},
						result
					)
					mutationFuture.get(2L, TimeUnit.SECONDS)
					registration.retire()
					assertEquals(0, executor.activeSlotCount)
				}
			}
		} finally {
			worker.shutdownNow()
			assertTrue(worker.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun callbackAndExplicitRetirementRaceWithFreezeSnapshotsWithoutCorruption() {
		val workers = Executors.newFixedThreadPool(3)
		try {
			repeat(32) { cycle ->
				val tokens = ReaderLegacySourceLocalTokenAllocator()
				val registry = ReaderSemanticExecutableRequestRegistry(3L, tokens)
				lateinit var origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
				lateinit var callback: (paige.navic.reader.ReaderPresentationEventReceipt) -> Unit
				val handle = registry.register { suppliedOrigin, _, suppliedCallback ->
					origin = suppliedOrigin
					callback = suppliedCallback
					ReaderSemanticExecutableResult.Accepted
				}
				val executor = ReaderSemanticCommandExecutor(registry, tokens)
				lateinit var registration: ReaderSemanticCommandRegistration
				val id = transitionId(sequence = cycle.toLong() + 1L).copy(
					operation = ReaderTransitionOperation.ExternalSemanticRelocation
				)
				assertEquals(
					ReaderSemanticCommandResult.Accepted,
					executor.synchronize(
						ReaderTransitionCommand.RequestSemanticSynchronization(
							id,
							ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
							handle
						),
						onRegistration = { registration = it },
						onReceipt = { }
					)
				)
				val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(cycle.toLong() + 1L))
				val start = CyclicBarrier(4)
				val futures = listOf(
					workers.submit {
						start.await(2L, TimeUnit.SECONDS)
						callback(semanticReceipt(origin))
					},
					workers.submit {
						start.await(2L, TimeUnit.SECONDS)
						registration.retire()
					},
					workers.submit {
						start.await(2L, TimeUnit.SECONDS)
						assertEquals(
							ReaderPortCommandResult.Accepted,
							executor.freezeForTransitionActivation(domain)
						)
					}
				)
				start.await(2L, TimeUnit.SECONDS)
				repeat(32) {
					val snapshot = executor.retirementSnapshot()
					assertTrue(snapshot.activeSlotCount in 0..1)
					executor.snapshotFrozenOwnership()
				}
				futures.forEach { it.get(2L, TimeUnit.SECONDS) }
				assertEquals(0, executor.activeSlotCount)
				assertEquals(0, executor.retirementSnapshot().activeSlotCount)
				assertEquals(
					ReaderPortCommandResult.Accepted,
					executor.restoreAfterTransitionActivation(domain)
				)
			}
		} finally {
			workers.shutdownNow()
			assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun oldLeaseRetirementRacingNewSameTransitionSlotNeverRemovesNewSlot() {
		val workers = Executors.newFixedThreadPool(2)
		try {
			repeat(32) { cycle ->
				val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
				val oldHandle = registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
				val newHandle = registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
				val executor = ReaderSemanticCommandExecutor(registry)
				val id = transitionId(sequence = cycle.toLong() + 1L).copy(
					operation = ReaderTransitionOperation.ExternalSemanticRelocation
				)
				lateinit var oldRegistration: ReaderSemanticCommandRegistration
				executor.synchronize(
					ReaderTransitionCommand.RequestSemanticSynchronization(
						id,
						ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, oldHandle),
						oldHandle
					),
					onRegistration = { oldRegistration = it },
					onReceipt = { }
				)
				lateinit var newRegistration: ReaderSemanticCommandRegistration
				val start = CyclicBarrier(3)
				val retire = workers.submit {
					start.await(2L, TimeUnit.SECONDS)
					oldRegistration.retire()
				}
				val admit = workers.submit {
					start.await(2L, TimeUnit.SECONDS)
					assertEquals(
						ReaderSemanticCommandResult.Accepted,
						executor.synchronize(
							ReaderTransitionCommand.RequestSemanticSynchronization(
								id,
								ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, newHandle),
								newHandle
							),
							onRegistration = { newRegistration = it },
							onReceipt = { }
						)
					)
				}
				start.await(2L, TimeUnit.SECONDS)
				retire.get(2L, TimeUnit.SECONDS)
				admit.get(2L, TimeUnit.SECONDS)
				oldRegistration.retire()
				assertEquals(1, executor.activeSlotCount)
				newRegistration.retire()
				assertEquals(0, executor.activeSlotCount)
			}
		} finally {
			workers.shutdownNow()
			assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS))
		}
	}

	@Test
	fun presentationReceiptRequiresExplicitCommandOrNonCommandOrigin() {
		assertTrue(
			paige.navic.reader.ReaderPresentationEventReceipt::class.java.declaredConstructors.none {
				it.isSynthetic
			},
			"Receipt origin cannot be supplied by a default-argument constructor"
		)
	}

	@Test
	fun everySemanticIntentCarriesOneExecutableRequestHandle() {
		val intentClasses = listOf(
			paige.navic.reader.ReaderPageTurnIntent::class.java,
			paige.navic.reader.ReaderExternalRelocationIntent::class.java,
			paige.navic.reader.ReaderCoverEntryIntent::class.java
		)
		intentClasses.forEach { intentClass ->
			assertTrue(
				intentClass.declaredFields.any { it.name == "requestHandle" },
				intentClass.name
			)
		}
		val semanticIntentConstructor = paige.navic.reader.ReaderExternalRelocationIntent::class.java
			.declaredConstructors
			.single { it.parameterCount == 2 }
		semanticIntentConstructor.isAccessible = true
		val semanticIntent = semanticIntentConstructor.newInstance(
				ReaderExternalRelocationSource.Toc,
				53L
			)
		val constructor = ReaderTransitionCommand.RequestSemanticSynchronization::class.java
			.declaredConstructors
			.single { it.parameterCount == 3 }
		constructor.isAccessible = true
		assertTrue(runCatching {
			constructor.newInstance(
				transitionId(),
				semanticIntent,
				null
			)
		}.isFailure)
	}

	@Test
	fun semanticSlotFenceCompactsContiguousRetirementInsteadOfExhaustingHistory() {
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val executor = ReaderSemanticCommandExecutor(registry)
		repeat(40) { index ->
			val handle = registry.register { origin, _, callback ->
				callback(semanticReceipt(origin))
				ReaderSemanticExecutableResult.Accepted
			}
			val id = transitionId(sequence = index.toLong() + 1L)
			assertEquals(
				ReaderSemanticCommandResult.Accepted,
				executor.synchronize(
					ReaderTransitionCommand.RequestSemanticSynchronization(
						id,
						ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle),
						handle
					),
					onRegistration = { }
				) {}
			)
		}
		assertEquals(0, executor.activeSlotCount)
		assertEquals(40L, executor.retirementSnapshot().contiguousRetiredThrough)
		assertTrue(executor.retirementSnapshot().outOfOrderRetiredSequences.isEmpty())
	}

	@Test
	fun activatedSemanticDispatchRegistersPrivateRequestAndSuppressesLegacyUntilCommand() {
		val registry = ReaderSemanticExecutableRequestRegistry(readerSessionGeneration = 3L)
		val gateway = ReaderTransitionGateway()
		val facts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()
		var privateRequestCalls = 0
		var legacyCalls = 0
		gateway.attachActivated(
			enqueue = facts::add,
			registerSemanticRequest = registry::register
		)

		val result = gateway.dispatchSemanticActivatedOrLegacy(
			intent = { handle ->
				ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, handle)
			},
			request = { origin, _, callback ->
				privateRequestCalls += 1
				callback(semanticReceipt(origin))
				ReaderSemanticExecutableResult.Accepted
			},
			activatedResult = { "pending" },
			legacyDispatch = {
				legacyCalls += 1
				"legacy"
			}
		)

		assertEquals("pending", result)
		assertEquals(0, privateRequestCalls)
		assertEquals(0, legacyCalls)
		val intentFact = assertIs<paige.navic.reader.ReaderTransitionFact.Intent>(facts.single())
		val semanticIntent = assertIs<ReaderExternalRelocationIntent>(intentFact.intent)
		val transitionId = transitionId().copy(
			operation = ReaderTransitionOperation.ExternalSemanticRelocation
		)
		val receipts = mutableListOf<paige.navic.reader.ReaderPresentationEventReceipt>()
		assertEquals(
			ReaderSemanticCommandResult.Accepted,
			ReaderSemanticCommandExecutor(registry).synchronize(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					transitionId,
					semanticIntent,
					semanticIntent.requestHandle
				),
				onRegistration = { },
				onReceipt = receipts::add
			)
		)
		assertEquals(1, privateRequestCalls)
		assertEquals(0, legacyCalls)
		assertEquals(transitionId, receipts.single().originatingTransitionId)
	}

	@Test
	fun activatedGatewayNeverContinuesToLegacyConsequence() {
		val gateway = ReaderTransitionGateway()
		val facts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()
		var legacyCalls = 0
		gateway.attachActivated(
			enqueue = { facts += it },
			registerSemanticRequest = ReaderSemanticExecutableRequestRegistry(3L)::register
		)

		val result = gateway.dispatchActivatedOrLegacy(
			fact = paige.navic.reader.ReaderTransitionFact.Retry(null),
			activatedResult = { "activated" },
			legacyDispatch = { legacyCalls += 1; "legacy" }
		)

		assertEquals("activated", result)
		assertEquals(0, legacyCalls)
		assertEquals(1, facts.size)
		assertEquals(ReaderTransitionGatewayMode.Activated, gateway.mode)
	}

	@Test
	fun preparedFrameDoesNotPublishSuccessBeforeCombinedCommitAcknowledgement() {
		val binding = binding()
		val predecessorId = transitionId(binding, sequence = 6L).copy(
			operation = ReaderTransitionOperation.BootstrapNativePage
		)
		val predecessor = ReaderPresentationFrameOwner.NativePage(
			paige.navic.reader.ReaderNativePagePresentationProof(
				binding = binding,
				transitionToken = ReaderPresentationToken(23L),
				presentedFrame = 29L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = 11L,
				textureGeneration = 13L
			)
		)
		val predecessorKey = ReaderTransitionResourceKey(
			predecessorId,
			ReaderTransitionResourceKind.Deck,
			31L
		)
		val id = transitionId(binding)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.FrameHandoff, 47L),
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val geometry = paige.navic.reader.ReaderTransitionFrameGeometry(3L, 5L, 0, 0, 1200, 800)
		val specification = paige.navic.reader.ReaderTransitionFrameTargetSpecification.ShellCover(
			id,
			3L,
			binding.publicationGeneration,
			binding,
			paige.navic.reader.ReaderShellCoverHostToken(37L),
			41L,
			binding.viewportGeneration,
			geometry,
			1L
		)
		val target = paige.navic.reader.ReaderTransitionFrameTarget.ShellCover(
			paige.navic.reader.ReaderTransitionFrameTargetHandle(
				3L,
				binding.publicationGeneration,
				43L
			),
			specification,
			registration
		)
		val basePhase = paige.navic.reader.ReaderTransitionLivenessTable.phase(
			id,
			paige.navic.reader.ReaderTransitionPhaseKind.AwaitingProof,
			predecessor
		)
		val phase = basePhase.copy(
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(paige.navic.reader.ReaderTransitionProofKind.PreparedFrame),
				callbackSources = setOf(
					paige.navic.reader.ReaderTransitionFactKind.PreparedFrame,
					paige.navic.reader.ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation,
					paige.navic.reader.ReaderTransitionCommandStage.TimerBinding
				)
			)
		)
		val journal = paige.navic.reader.ReaderTransitionJournal(
			active = paige.navic.reader.ReaderActiveTransition(
				id = id,
				phase = phase,
				pendingCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation
				),
				predecessorResourceKey = predecessorKey,
				frameTarget = target
			),
			committed = paige.navic.reader.ReaderCommittedTransition(
				predecessorId,
				predecessor,
				binding,
				predecessorKey,
				ReaderTransitionResourceRegistration(
					predecessorKey,
					ReaderResourceRetirementOrder(3L, 5L, 1L)
				)
			)
		)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(37L), binding, 41L, 43L, 1200, 800
			)
		)

		val reduction = journal.reduce(
			paige.navic.reader.ReaderTransitionFact.PreparedFrame(
				id,
				target,
				owner,
				registration
			)
		)

		assertEquals(
			paige.navic.reader.ReaderTransitionPhaseKind.Committing,
			reduction.state.active?.phase?.kind
		)
		assertEquals(null, reduction.state.lastOutcome)
		assertEquals(predecessor, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(reduction.state.committed).committed.owner)
		assertEquals(1, reduction.commands.count {
			it is ReaderTransitionCommand.CommitOwnerAndInputLease
		})
		assertTrue(reduction.commands.none { it is ReaderTransitionCommand.ReleaseResource })
	}

	@Test
	fun rejectedCombinedCommitRetainsPredecessorAndReleasesOnlySuccessor() {
		val fixture = preparedCommitFixture()
		val rejected = fixture.committingState.reduce(
			paige.navic.reader.ReaderTransitionFact.OwnerAndInputPublicationRejected(
				fixture.id,
				paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
					fixture.target.handle,
					fixture.successorRegistration
				),
				fixture.commitCommand.publicationIdentity,
				paige.navic.reader.ReaderTransitionFailureReason.AtomicPublicationRejected
			)
		)

		assertEquals(null, rejected.state.active)
		assertIs<paige.navic.reader.ReaderTransitionOutcome.Failed>(rejected.state.lastOutcome)
		assertEquals(fixture.predecessorOwner, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(rejected.state.committed).committed.owner)
		assertEquals(
			listOf(fixture.successorRegistration.key),
			rejected.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
		assertTrue(rejected.commands.none {
			it is ReaderTransitionCommand.ReleaseResource && it.key == fixture.predecessorKey
		})
	}

	@Test
	fun acceptedCombinedCommitPublishesSuccessBeforePredecessorRelease() {
		val fixture = preparedCommitFixture()
		val accepted = fixture.committingState.reduce(
			paige.navic.reader.ReaderTransitionFact.OwnerAndInputPublicationApplied(
				fixture.id,
				paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
					fixture.target.handle,
					fixture.successorRegistration
				),
				fixture.successorOwner,
				fixture.binding,
				fixture.commitCommand.requestedLease,
				fixture.commitCommand.publicationIdentity
			)
		)

		assertEquals(null, accepted.state.active)
		val outcome = assertIs<paige.navic.reader.ReaderTransitionOutcome.Succeeded>(
			accepted.state.lastOutcome
		)
		assertEquals(fixture.successorOwner, outcome.committedOwner)
		assertEquals(fixture.successorOwner, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(accepted.state.committed).committed.owner)
		assertEquals(
			listOf(fixture.predecessorKey),
			accepted.commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().map { it.key }
		)
	}

	@Test
	fun staleOrWrongCombinedCommitAcknowledgementIsInert() {
		val fixture = preparedCommitFixture()
		val wrongIdentity = paige.navic.reader.ReaderOwnerAndInputPublicationIdentity(
			fixture.commitCommand.publicationIdentity.value + 1L
		)
		val stale = fixture.committingState.reduce(
			paige.navic.reader.ReaderTransitionFact.OwnerAndInputPublicationApplied(
				fixture.id,
				paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
					fixture.target.handle,
					fixture.successorRegistration
				),
				fixture.successorOwner,
				fixture.binding,
				fixture.commitCommand.requestedLease,
				wrongIdentity
			)
		)

		assertEquals(fixture.committingState, stale.state)
		assertTrue(stale.commands.isEmpty())
		assertEquals(fixture.predecessorOwner, assertIs<paige.navic.reader.ReaderCommittedPresentation.Transition>(stale.state.committed).committed.owner)
	}

	@Test
	fun bindingOnlyFrameRequestIsUnrepresentable() {
		val requestClass = ReaderTransitionCommand.RequestFramePresentation::class.java
		val targetClass = protocolClass("paige.navic.reader.ReaderTransitionFrameTarget")
		assertTrue(
			requestClass.declaredConstructors.single().parameterTypes.contains(targetClass),
			"presentation must require an exact prepared target"
		)
		assertTrue(runCatching {
			Class.forName("paige.navic.reader.ReaderTransitionFrameRequest")
		}.isFailure, "binding-only frame-request hierarchy must not exist")
	}

	@Test
	fun shellCoverCommandCarriesExactTokenGenerationAndFrameResource() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$ShellCover",
			setOf("hostToken", "coverGeneration", "viewportGeneration", "geometry", "requestSequence")
		)
		assertProtocolClassExists("paige.navic.reader.ReaderTransitionCommand\$PrepareFrameTarget")
	}

	@Test
	fun liveExposureCommandCarriesExactHandoffTokenAndFrameResource() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$LiveWebView",
			setOf("handoffToken", "direction", "claimIdentity", "viewportGeneration", "geometry", "requestSequence")
		)
	}

	@Test
	fun nativeFrameCommandConsumesExactDeckTargetWithoutPolling() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$NativePage",
			setOf("allocation", "hostToken", "deckTarget", "geometry", "requestSequence")
		)
	}

	@Test
	fun curlSettlementFrameCommandPreservesGestureAndExactDeckTarget() {
		assertTargetSpecificationFields(
			"paige.navic.reader.ReaderTransitionFrameTargetSpecification\$CurlSettlementTerminalFrame",
			setOf("allocation", "gestureId", "settlement", "deckTarget", "geometry", "requestSequence")
		)
	}

	@Test
	fun sameBindingDifferentFrameTargetCannotSatisfyPreparedFrame() {
		val prepared = paige.navic.reader.ReaderTransitionFact.PreparedFrame::class.java
		val targetClass = protocolClass("paige.navic.reader.ReaderTransitionFrameTarget")
		assertTrue(
			prepared.declaredConstructors.single().parameterTypes.contains(targetClass),
			"prepared proof must carry exact target identity, not binding alone"
		)
	}

	@Test
	fun preparedFrameCannotFabricateResourceRegistration() {
		val prepared = paige.navic.reader.ReaderTransitionFact.PreparedFrame::class.java
		val constructors = prepared.declaredConstructors
		assertEquals(1, constructors.size, "prepared frame must have no default-registration constructor")
		assertTrue(
			constructors.single().parameterTypes.contains(
				ReaderTransitionResourceRegistration::class.java
			),
			"prepared proof must echo the coordinator-supplied registration"
		)
	}

	@Test
	fun transitionalInputChangeUsesAtomicRetainedOwnerPublication() {
		assertProtocolClassExists(
			"paige.navic.reader.ReaderTransitionCommand\$PublishRetainedOwnerAndInputLease"
		)
		assertTrue(
			ReaderTransitionCommand::class.java.declaredClasses.none { it.simpleName == "ApplyInputLease" },
			"activated input mutation must only use atomic retained-owner publication"
		)
	}

	@Test
	fun operationSetRemainsExactlyEleven() {
		assertEquals(11, ReaderTransitionOperation.entries.size)
	}

	private fun assertTargetSpecificationFields(className: String, expected: Set<String>) {
		val actual = protocolClass(className).declaredFields.map { it.name }.toSet()
		assertTrue(actual.containsAll(expected), "missing exact target fields")
	}

	private fun assertProtocolClassExists(className: String) {
		assertTrue(runCatching { Class.forName(className) }.isSuccess, "missing amended protocol type")
	}

	private fun protocolClass(className: String): Class<*> =
		runCatching { Class.forName(className) }.getOrElse {
			throw AssertionError("missing amended protocol type", it)
		}

	private data class PreparedCommitFixture(
		val id: ReaderTransitionId,
		val binding: ReaderPresentationBinding,
		val predecessorOwner: ReaderPresentationFrameOwner,
		val predecessorKey: ReaderTransitionResourceKey,
		val successorOwner: ReaderPresentationFrameOwner,
		val successorRegistration: ReaderTransitionResourceRegistration,
		val target: paige.navic.reader.ReaderTransitionFrameTarget.ShellCover,
		val committingState: paige.navic.reader.ReaderTransitionJournal,
		val commitCommand: ReaderTransitionCommand.CommitOwnerAndInputLease
	)

	private fun preparedCommitFixture(): PreparedCommitFixture {
		val binding = binding()
		val predecessorId = transitionId(binding, sequence = 6L).copy(
			operation = ReaderTransitionOperation.BootstrapNativePage
		)
		val predecessorOwner = ReaderPresentationFrameOwner.NativePage(
			paige.navic.reader.ReaderNativePagePresentationProof(
				binding,
				ReaderPresentationToken(23L),
				29L,
				1200,
				800,
				11L,
				13L
			)
		)
		val predecessorKey = ReaderTransitionResourceKey(
			predecessorId,
			ReaderTransitionResourceKind.Deck,
			31L
		)
		val id = transitionId(binding)
		val successorRegistration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.FrameHandoff, 47L),
			ReaderResourceRetirementOrder(3L, 5L, 2L)
		)
		val specification = paige.navic.reader.ReaderTransitionFrameTargetSpecification.ShellCover(
			id,
			3L,
			binding.publicationGeneration,
			binding,
			paige.navic.reader.ReaderShellCoverHostToken(37L),
			41L,
			binding.viewportGeneration,
			paige.navic.reader.ReaderTransitionFrameGeometry(3L, 5L, 0, 0, 1200, 800),
			1L
		)
		val target = paige.navic.reader.ReaderTransitionFrameTarget.ShellCover(
			paige.navic.reader.ReaderTransitionFrameTargetHandle(
				3L,
				binding.publicationGeneration,
				43L
			),
			specification,
			successorRegistration
		)
		val basePhase = paige.navic.reader.ReaderTransitionLivenessTable.phase(
			id,
			paige.navic.reader.ReaderTransitionPhaseKind.AwaitingProof,
			predecessorOwner
		)
		val awaitingFrame = basePhase.copy(
			contract = basePhase.contract.copy(
				awaitedProofs = setOf(paige.navic.reader.ReaderTransitionProofKind.PreparedFrame),
				callbackSources = setOf(
					paige.navic.reader.ReaderTransitionFactKind.PreparedFrame,
					paige.navic.reader.ReaderTransitionFactKind.CommandRejected
				),
				admissibleCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation,
					paige.navic.reader.ReaderTransitionCommandStage.TimerBinding
				)
			)
		)
		val journal = paige.navic.reader.ReaderTransitionJournal(
			active = paige.navic.reader.ReaderActiveTransition(
				id,
				awaitingFrame,
				pendingCommandStages = setOf(
					paige.navic.reader.ReaderTransitionCommandStage.FramePresentation
				),
				predecessorResourceKey = predecessorKey,
				frameTarget = target
			),
			committed = paige.navic.reader.ReaderCommittedTransition(
				predecessorId,
				predecessorOwner,
				binding,
				predecessorKey,
				ReaderTransitionResourceRegistration(
					predecessorKey,
					ReaderResourceRetirementOrder(3L, 5L, 1L)
				)
			)
		)
		val successorOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(37L),
				binding,
				41L,
				43L,
				1200,
				800
			)
		)
		val prepared = journal.reduce(
			paige.navic.reader.ReaderTransitionFact.PreparedFrame(
				id,
				target,
				successorOwner,
				successorRegistration
			)
		)
		return PreparedCommitFixture(
			id,
			binding,
			predecessorOwner,
			predecessorKey,
			successorOwner,
			successorRegistration,
			target,
			prepared.state,
			assertIs(prepared.commands.single())
		)
	}

	private fun commitCommand(
		id: ReaderTransitionId,
		owner: ReaderPresentationFrameOwner,
		binding: ReaderPresentationBinding,
		registration: ReaderTransitionResourceRegistration,
		lease: ReaderTransitionInputLease,
		publicationSequence: Long
	) = ReaderTransitionCommand.CommitOwnerAndInputLease(
		transitionId = id,
		targetHandle = paige.navic.reader.ReaderTransitionFrameTargetHandle(
			id.readerSessionGeneration,
			binding.publicationGeneration,
			publicationSequence
		),
		owner = owner,
		binding = binding,
		preparedFrameResource = registration,
		requestedLease = lease,
		publicationIdentity = paige.navic.reader.ReaderOwnerAndInputPublicationIdentity(
			publicationSequence
		)
	)

	private fun binding() = ReaderPresentationBinding(
		foliateSessionId = "fixture",
		publicationGeneration = 2L,
		viewportGeneration = 3L,
		profileGeneration = 5L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 1L),
		preparationGeneration = 7L,
		rasterGeneration = 11L,
		textureGeneration = 13L
	)

	private fun semanticReceipt(
		origin: paige.navic.reader.ReaderPresentationEventOrigin.SemanticCommand
	) = paige.navic.reader.ReaderPresentationEventReceipt(
		event = paige.navic.reader.ReaderPresentationEvent.Retry,
		preVersion = paige.navic.reader.ReaderPresentationReceiptVersion(
			origin.transitionId.readerSessionGeneration,
			null,
			0L
		),
		version = paige.navic.reader.ReaderPresentationReceiptVersion(
			origin.transitionId.readerSessionGeneration,
			null,
			1L
		),
		disposition = paige.navic.reader.ReaderPresentationEventDisposition.Accepted,
		postState = paige.navic.reader.ReaderPresentationState(),
		effects = emptyList(),
		origin = origin
	)

	private fun transitionId(
		binding: ReaderPresentationBinding = binding(),
		sequence: Long = 7L
	) = ReaderTransitionId(
		readerSessionGeneration = 3L,
		coordinatorEpoch = 5L,
		sequence = sequence,
		operation = ReaderTransitionOperation.ShellCoverCommit,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
		parent = if (sequence == 1L) null else paige.navic.reader.ReaderTransitionParentIdentity(
			3L,
			5L,
			sequence - 1L
		)
	)
}
