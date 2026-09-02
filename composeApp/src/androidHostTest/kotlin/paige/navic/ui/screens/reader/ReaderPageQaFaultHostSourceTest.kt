package paige.navic.ui.screens.reader

import java.io.File
import karacken.curl.PageSurfaceDeckReleaseResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPendingPresentationEffect
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationEffectIdentity
import paige.navic.reader.ReaderPresentationEffectQueue
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.identity
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce

class ReaderPageQaFaultHostSourceTest {
	@Test
	fun viewerReplacementRemainsAfterVisualLocationSynchronization() {
		val updateBlock = hostSource()
			.substringAfter("update = { root ->")
			.substringBefore("onRelease = { root ->")
		val visualLocation = updateBlock.indexOf("root.setPageTurnVisualLocation(")
		val preparationRetry = updateBlock.indexOf("root.setPagePreparationRetryKey(")
		val viewerReplacement = updateBlock.indexOf("root.setViewerContent(viewerKey)")
		val composeOverlay = updateBlock.indexOf("root.setComposeOverlay(")

		assertTrue(visualLocation >= 0, "update block must synchronize visual location")
		assertTrue(preparationRetry >= 0, "update block must publish the preparation retry key")
		assertTrue(viewerReplacement >= 0, "update block must replace viewer content")
		assertTrue(composeOverlay >= 0, "update block must update the Compose overlay")
		assertTrue(
			visualLocation < preparationRetry &&
				preparationRetry < viewerReplacement &&
				viewerReplacement < composeOverlay,
			"viewer replacement must remain terminal after visual-location synchronization"
		)
	}

	@Test
	fun viewerReplacementFencePublishesOnlyTheFreshGenerationOnce() {
		val fence = ReaderPresentationViewerReplacementFence()
		val oldDeck = presentationDeck(rasterProfileEpoch = 7L, generationId = 70L)
		val lateOldDeck = presentationDeck(rasterProfileEpoch = 8L, generationId = 80L)
		val freshDeck = presentationDeck(rasterProfileEpoch = 9L, generationId = 90L)
		val publicationEvents = mutableListOf<Long>()
		var publicationPending = true

		fun report(deck: ReaderPagePreparedActiveDeck) {
			if (publicationPending && fence.admits(deck)) {
				publicationPending = false
				publicationEvents += 2L
			}
		}

		fence.begin(rasterProfileEpoch = oldDeck.rasterProfileEpoch)
		report(oldDeck)
		fence.observeRasterProfileEpoch(lateOldDeck.rasterProfileEpoch)
		report(lateOldDeck)
		assertTrue(publicationEvents.isEmpty())

		fence.completeAfterViewerInvalidation()
		assertFalse(fence.isPending)
		assertTrue(publicationEvents.isEmpty())

		report(lateOldDeck)
		assertTrue(publicationEvents.isEmpty())
		report(freshDeck)
		report(freshDeck)

		assertEquals(listOf(2L), publicationEvents)
	}

	@Test
	fun hostClearsShadowFenceOnlyAfterTerminalViewerInvalidationWithoutReporting() {
		val source = hostSource()
		val viewerReplacement = source
			.substringAfter("fun setViewerContent(viewerKey: ReaderViewerKey")
			.substringBefore("fun canAcceptNewPointer()")
		val binding = source
			.substringAfter("private fun currentPresentationBindingOrNull()")
			.substringBefore("private fun reportPresentationIdentityIfAvailable()")

		assertContains(source, "presentationViewerReplacementFence.begin(")
		assertContains(source, "presentationViewerReplacementFence.observeRasterProfileEpoch(epoch)")
		assertContains(binding, "if (!presentationViewerReplacementFence.admits(deck)) return null")
		val invalidateLegacyDeck = viewerReplacement.indexOf("viewerContainer.replaceViewerContent(viewerView)")
		val clearShadowFence = viewerReplacement.indexOf(
			"viewerContainer.completePresentationViewerReplacement()"
		)
		assertTrue(invalidateLegacyDeck >= 0)
		assertTrue(clearShadowFence > invalidateLegacyDeck)
		assertFalse(viewerReplacement.contains("reportPresentationIdentityIfAvailable"))
	}

	@Test
	fun presentationEffectFailureRetainsRetryAndSuccessAcknowledgesExactlyOnce() {
		val effect = stalePresentationEffect()
		val queue = ReaderPresentationEffectQueue(capacity = 2)
		val pending = queue.retain(listOf(effect)).single()
		var releaseSucceeds = false
		var releaseCount = 0
		var acknowledgementCount = 0
		val handler = ReaderPresentationEffectHandler { handledEffect ->
			assertEquals(effect, handledEffect)
			releaseCount += 1
			releaseSucceeds
		}
		val decision = readerPresentationDecision(ReaderPresentationState())
		val acknowledge: (ReaderPresentationEffectIdentity) -> Unit = { identity ->
			if (queue.acknowledge(identity)) acknowledgementCount += 1
		}

		val firstDeliveryHandled = handler.deliver(
			listOf(pending, pending),
			decision,
			acknowledge
		)
		assertFalse(firstDeliveryHandled)
		assertEquals(1, releaseCount)
		assertEquals(0, acknowledgementCount)
		assertEquals(listOf(pending), queue.pendingEffects())

		releaseSucceeds = true
		val secondDeliveryHandled = handler.deliver(
			queue.pendingEffects(),
			decision,
			acknowledge
		)
		assertTrue(secondDeliveryHandled)
		assertEquals(2, releaseCount)
		assertEquals(1, acknowledgementCount)
		assertTrue(queue.pendingEffects().isEmpty())

		handler.deliver(listOf(pending, pending), decision, acknowledge)
		assertEquals(2, releaseCount)
		assertEquals(1, acknowledgementCount)
	}

	@Test
	fun presentationEffectReleaseExceptionReturnsFailureAndRetriesQueuedEffect() {
		val effect = stalePresentationEffect()
		val queue = ReaderPresentationEffectQueue(capacity = 1)
		val pending = queue.retain(listOf(effect)).single()
		var releaseAttempts = 0
		var acknowledgementCount = 0
		val handler = ReaderPresentationEffectHandler {
			releaseAttempts += 1
			if (releaseAttempts == 1) error("injected renderer release failure")
			true
		}
		val decision = readerPresentationDecision(ReaderPresentationState())
		val acknowledge: (ReaderPresentationEffectIdentity) -> Unit = { identity ->
			if (queue.acknowledge(identity)) acknowledgementCount += 1
		}

		assertFalse(handler.deliver(queue.pendingEffects(), decision, acknowledge))
		assertEquals(1, releaseAttempts)
		assertEquals(0, acknowledgementCount)
		assertEquals(listOf(pending), queue.pendingEffects())

		assertTrue(handler.deliver(queue.pendingEffects(), decision, acknowledge))
		assertEquals(2, releaseAttempts)
		assertEquals(1, acknowledgementCount)
		assertTrue(queue.pendingEffects().isEmpty())
	}

	@Test
	fun staleRendererOwnedGenerationRetriesPostClaimRejectionAndRetiresOnlyOnCompletion() {
		val binding = presentationBinding()
		val generationId = requireNotNull(binding.textureGeneration)
		val owners = mutableMapOf(generationId to requireNotNull(binding.rasterGeneration))
		val bitmapLeases = mutableSetOf(generationId)
		val surfaceClaims = mutableSetOf<Long>()
		var activeGenerationId: Long? = generationId
		var pendingGenerationId: Long? = null
		var releaseInvocations = 0
		var rejectNextRelease = true
		val releaseGate = ReaderRendererOwnedGenerationReleaseGate(
			ownerForGeneration = owners::get,
			rasterGenerationForOwner = { rasterGeneration -> rasterGeneration },
			isProtectedGeneration = { candidate ->
				candidate == activeGenerationId || candidate == pendingGenerationId
			},
			requestRendererRelease = { candidate ->
				if (candidate in surfaceClaims) {
					PageSurfaceDeckReleaseResult.alreadyAccepted()
				} else {
					releaseInvocations += 1
					surfaceClaims += candidate
					if (rejectNextRelease) {
						rejectNextRelease = false
						surfaceClaims -= candidate
						PageSurfaceDeckReleaseResult.rejected(
							PageSurfaceDeckReleaseResult.RejectionReason.QUEUE_REJECTED
						)
					} else {
						PageSurfaceDeckReleaseResult.accepted()
					}
				}
			},
			retireOwner = { candidate ->
				owners.remove(candidate)
				bitmapLeases -= candidate
				surfaceClaims -= candidate
			}
		)

		assertFalse(releaseGate.request(binding))
		assertEquals(0, releaseInvocations)
		activeGenerationId = null
		pendingGenerationId = generationId
		assertFalse(releaseGate.request(binding))
		assertEquals(0, releaseInvocations)
		pendingGenerationId = null

		val effect = ReaderPresentationEffect.ReleaseStalePresentation(
			token = ReaderPresentationToken(20L),
			binding = binding
		)
		val queue = ReaderPresentationEffectQueue(capacity = 1)
		val pending = queue.retain(listOf(effect)).single()
		val handler = ReaderPresentationEffectHandler { handledEffect ->
			releaseGate.request(handledEffect.binding)
		}
		val decision = readerPresentationDecision(ReaderPresentationState())
		val acknowledge: (ReaderPresentationEffectIdentity) -> Unit = { identity ->
			queue.acknowledge(identity)
		}

		assertFalse(handler.deliver(queue.pendingEffects(), decision, acknowledge))
		assertEquals(1, releaseInvocations)
		assertEquals(listOf(pending), queue.pendingEffects())
		assertEquals(setOf(generationId), owners.keys)
		assertEquals(setOf(generationId), bitmapLeases)
		assertTrue(surfaceClaims.isEmpty())
		assertFalse(generationId in surfaceClaims)

		assertTrue(handler.deliver(queue.pendingEffects(), decision, acknowledge))
		assertEquals(2, releaseInvocations)
		assertTrue(queue.pendingEffects().isEmpty())
		assertEquals(setOf(generationId), owners.keys)
		assertEquals(setOf(generationId), bitmapLeases)
		assertEquals(setOf(generationId), surfaceClaims)
		assertTrue(generationId in surfaceClaims)

		assertTrue(releaseGate.request(binding))
		assertEquals(2, releaseInvocations)
		releaseGate.completeRelease(generationId)
		assertTrue(owners.isEmpty())
		assertTrue(bitmapLeases.isEmpty())
		assertTrue(surfaceClaims.isEmpty())
		assertFalse(generationId in surfaceClaims)

		assertTrue(releaseGate.request(binding))
		assertEquals(2, releaseInvocations)
	}

	@Test
	fun rejectedAutomaticCleanupRetriesOnRendererAvailabilityWithoutManualRequest() {
		val recoveredGeneration = 77L
		val staleGeneration = 78L
		val owners = mutableSetOf(recoveredGeneration, staleGeneration)
		val attempts = mutableMapOf<Long, Int>()
		val acceptedActions = mutableListOf<ReaderRendererCleanupRequest>()
		val coordinator = ReaderRendererCleanupRetryCoordinator(
			ownerExists = owners::contains,
			requestRelease = { generationId ->
				val attempt = attempts.getOrDefault(generationId, 0) + 1
				attempts[generationId] = attempt
				attempt > 1
			},
			onAccepted = acceptedActions::add,
			pendingLimit = 4
		)
		val recovered = ReaderRendererCleanupRequest.RecoveredCancellation(
			generationId = recoveredGeneration,
			role = ReaderDeckSubmissionRole.Pending
		)
		val stale = ReaderRendererCleanupRequest.StaleGeneration(staleGeneration)

		assertFalse(coordinator.request(recovered))
		assertFalse(coordinator.request(stale))
		assertEquals(2, coordinator.pendingCount)
		assertTrue(acceptedActions.isEmpty())

		coordinator.onRendererAvailabilityRestored()

		assertEquals(mapOf(recoveredGeneration to 2, staleGeneration to 2), attempts)
		assertEquals(listOf(recovered, stale), acceptedActions)
		assertEquals(2, coordinator.pendingCount)
		coordinator.onRendererAvailabilityRestored()
		assertEquals(mapOf(recoveredGeneration to 2, staleGeneration to 2), attempts)
		assertEquals(listOf(recovered, stale), acceptedActions)

		owners.clear()
		assertTrue(coordinator.complete(recoveredGeneration))
		assertTrue(coordinator.complete(staleGeneration))
		assertFalse(coordinator.complete(staleGeneration))
		assertEquals(0, coordinator.pendingCount)
	}

	@Test
	fun rejectedCleanupClearsOnOwnerLossAndSupersedesTypedAction() {
		val generationId = 79L
		val owners = mutableSetOf(generationId)
		val acceptedActions = mutableListOf<ReaderRendererCleanupRequest>()
		val coordinator = ReaderRendererCleanupRetryCoordinator(
			ownerExists = owners::contains,
			requestRelease = { false },
			onAccepted = acceptedActions::add,
			pendingLimit = 2
		)
		val stale = ReaderRendererCleanupRequest.StaleGeneration(generationId)
		val rollback = ReaderRendererCleanupRequest.LibraryRollback(generationId)

		assertFalse(coordinator.request(stale))
		assertFalse(coordinator.request(rollback))
		assertEquals(rollback, coordinator.pendingRequest(generationId))

		owners.clear()
		coordinator.onRendererAvailabilityRestored()

		assertEquals(0, coordinator.pendingCount)
		assertTrue(acceptedActions.isEmpty())
	}

	@Test
	fun presentationEffectNeverReleasesSelectedOrRetainedAuthorityIdentity() {
		val binding = presentationBinding()
		val proof = nativePresentationProof(binding)
		val settled = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			binding = binding
		)
		val retainedDecision = readerPresentationReduce(
			settled,
			ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 40L)
		).decision
		val effect = ReaderPresentationEffect.ReleaseStalePresentation(
			ReaderPresentationToken(99L),
			binding
		)
		val pending = ReaderPendingPresentationEffect(effect.identity(), effect)
		var releaseCount = 0
		var acknowledgementCount = 0
		val handler = ReaderPresentationEffectHandler {
			releaseCount += 1
			true
		}

		listOf(readerPresentationDecision(settled), retainedDecision).forEach { decision ->
			handler.deliver(listOf(pending), decision) {
				acknowledgementCount += 1
			}
		}

		assertEquals(0, releaseCount)
		assertEquals(0, acknowledgementCount)
	}

	@Test
	fun logicalDeckAliasesAcknowledgeWithoutReleaseAndPhysicalReplacementReleasesOnceAfterProof() {
		val bindingD = presentationBinding()
		val coverProof = ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(20L),
			binding = bindingD,
			coverGeneration = 21L,
			presentedFrame = 22L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		val dismissed = readerPresentationReduce(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = bindingD,
				nextTokenValue = 23L
			),
			ReaderPresentationEvent.ShellCoverDismissalRequested
		)
		val transitionToken = ReaderPresentationToken(23L)
		val bindingD1 = bindingD.copy(preparationGeneration = 9L)
		val bindingD2 = bindingD1.copy(preparationGeneration = 10L)
		val movedD1 = readerPresentationReduce(
			dismissed.state,
			ReaderPresentationEvent.BindingReplaced(bindingD, bindingD1)
		)
		val movedD2 = readerPresentationReduce(
			movedD1.state,
			ReaderPresentationEvent.BindingReplaced(bindingD1, bindingD2)
		)
		val bindingE = bindingD2.copy(
			rasterGeneration = 60L,
			textureGeneration = 70L,
			preparationGeneration = 80L
		)
		val owners = mutableMapOf(
			requireNotNull(bindingD.textureGeneration) to requireNotNull(bindingD.rasterGeneration),
			requireNotNull(bindingE.textureGeneration) to requireNotNull(bindingE.rasterGeneration)
		)
		val surfaceClaims = mutableSetOf<Long>()
		var activeGenerationId = requireNotNull(bindingD.textureGeneration)
		val releaseRequests = mutableListOf<Long>()
		val releaseGate = ReaderRendererOwnedGenerationReleaseGate(
			ownerForGeneration = owners::get,
			rasterGenerationForOwner = { rasterGeneration -> rasterGeneration },
			isProtectedGeneration = { it == activeGenerationId },
			requestRendererRelease = { generationId ->
				if (!surfaceClaims.add(generationId)) {
					PageSurfaceDeckReleaseResult.alreadyAccepted()
				} else {
					releaseRequests += generationId
					PageSurfaceDeckReleaseResult.accepted()
				}
			},
			retireOwner = { generationId ->
				owners.remove(generationId)
				surfaceClaims -= generationId
			}
		)
		val queue = ReaderPresentationEffectQueue()
		var acknowledgementCount = 0
		val handler = ReaderPresentationEffectHandler { effect ->
			releaseGate.request(effect.binding)
		}
		val acknowledge: (ReaderPresentationEffectIdentity) -> Unit = { identity ->
			if (queue.acknowledge(identity)) acknowledgementCount += 1
		}

		assertTrue(movedD1.effects.isEmpty())
		queue.retain(movedD2.effects)
		assertTrue(
			handler.deliver(queue.pendingEffects(), movedD2.decision, acknowledge)
		)
		assertTrue(queue.pendingEffects().isEmpty())
		assertTrue(releaseRequests.isEmpty())

		val proofD = nativePresentationProof(bindingD2).copy(
			transitionToken = transitionToken,
			presentedFrame = 89L
		)
		val presentedD = readerPresentationReduce(
			movedD2.state,
			ReaderPresentationEvent.NativePagePresented(proofD)
		)
		queue.retain(presentedD.effects)
		assertTrue(handler.deliver(queue.pendingEffects(), presentedD.decision, acknowledge))
		assertTrue(queue.pendingEffects().isEmpty())
		assertTrue(releaseRequests.isEmpty())

		val movedE = readerPresentationReduce(
			presentedD.state,
			ReaderPresentationEvent.BindingReplaced(bindingD2, bindingE)
		)
		queue.retain(movedE.effects)
		assertFalse(handler.deliver(queue.pendingEffects(), movedE.decision, acknowledge))
		assertEquals(1, queue.pendingEffects().size)
		assertTrue(releaseRequests.isEmpty())

		val proofE = nativePresentationProof(bindingE).copy(presentedFrame = 90L)
		val presentedE = readerPresentationReduce(
			movedE.state,
			ReaderPresentationEvent.NativePagePresented(proofE)
		)
		assertTrue(presentedE.effects.isEmpty())
		activeGenerationId = requireNotNull(bindingE.textureGeneration)
		assertTrue(handler.deliver(queue.pendingEffects(), presentedE.decision, acknowledge))
		assertTrue(queue.pendingEffects().isEmpty())
		assertEquals(listOf(requireNotNull(bindingD.textureGeneration)), releaseRequests)

		assertTrue(queue.retain(movedE.effects).isEmpty())
		val lateAliasD = ReaderPresentationEffect.ReleaseStalePresentation(
			token = transitionToken,
			binding = bindingD2.copy(preparationGeneration = 11L)
		)
		queue.retain(listOf(lateAliasD))
		assertTrue(handler.deliver(queue.pendingEffects(), presentedE.decision, acknowledge))
		assertEquals(listOf(requireNotNull(bindingD.textureGeneration)), releaseRequests)
		releaseGate.completeRelease(requireNotNull(bindingD.textureGeneration))

		val currentAliasE = ReaderPresentationEffect.ReleaseStalePresentation(
			token = transitionToken,
			binding = bindingE.copy(preparationGeneration = 81L)
		)
		queue.retain(listOf(currentAliasE))
		assertTrue(handler.deliver(queue.pendingEffects(), presentedE.decision, acknowledge))
		assertTrue(queue.pendingEffects().isEmpty())
		assertEquals(listOf(requireNotNull(bindingD.textureGeneration)), releaseRequests)
		assertEquals(4, acknowledgementCount)
	}

	@Test
	fun viewportAndProfileBindingChangesEmitOneTypedReplacementWithoutSemanticMovement() {
		val original = presentationBinding()
		val viewport = original.copy(viewportGeneration = original.viewportGeneration + 1L)
		val profile = viewport.copy(
			profileGeneration = viewport.profileGeneration + 1L,
			rasterGeneration = requireNotNull(viewport.rasterGeneration) + 1L,
			textureGeneration = requireNotNull(viewport.textureGeneration) + 1L,
			preparationGeneration = requireNotNull(viewport.preparationGeneration) + 1L
		)

		assertEquals(
			ReaderPresentationEvent.BindingReplaced(original, viewport),
			readerPresentationBindingEvent(
				lastReportedBinding = original,
				currentBinding = viewport,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(
			ReaderPresentationEvent.BindingReplaced(viewport, profile),
			readerPresentationBindingEvent(
				lastReportedBinding = viewport,
				currentBinding = profile,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(
			null,
			readerPresentationBindingEvent(
				lastReportedBinding = profile,
				currentBinding = profile,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
	}

	@Test
	fun hostOrdersBindingReplacementBeforeFactsAndUsesExactSafeDeckRelease() {
		val host = hostSource()
		val report = host
			.substringAfter("private fun reportPresentationIdentityIfAvailable()")
			.substringBefore("private fun reportPresentationPreparationFacts(")
		val controller = readerSource("ReaderPlayLikeCurlFoliateController.android.kt")
		val release = controller
			.substringAfter("fun releaseStalePresentationDeck(")
			.substringBefore("\n\tfun ")

		assertTrue(report.indexOf("onPresentationEvent(bindingEvent)") >= 0)
		assertTrue(
			report.indexOf("onPresentationEvent(bindingEvent)") <
				report.indexOf("reportPresentationPreparationFacts(")
		)
		assertTrue(
			report.indexOf("reportPresentationPreparationFacts(") <
				report.indexOf("reportNativePagePresentationIfAvailable(")
		)
		assertContains(host, "ReaderPresentationEvent.NativePagePresented")
		assertContains(host, "releaseStalePresentationDeck(effect.binding)")
		assertContains(release, "rendererOwnedGenerationReleaseGate.request(binding)")
		assertContains(controller, "rendererOwnedGenerationReleaseGate.completeRelease(generationId)")
		assertFalse(controller.contains("private val rendererReleaseInFlight"))
		assertEquals(1, Regex("surfaceView::releaseDeck").findAll(controller).count())
		assertFalse(controller.contains("surfaceView.releaseDeck("))
		assertContains(controller, "ReaderRendererCleanupRetryCoordinator(")
		assertFalse(
			controller.contains(
				"check(rendererOwnedGenerationReleaseGate.requestOwnedGeneration"
			)
		)
		val acceptedLibraryRollback = controller
			.substringAfter("private fun rollbackAcceptedLibraryDeck(")
			.substringBefore("\n\tprivate fun ")
		assertFalse(acceptedLibraryRollback.contains("releaseGeneration(generationId)"))
		assertContains(
			acceptedLibraryRollback,
			"ReaderRendererCleanupRequest.LibraryRollback(generationId, role)"
		)
		val cleanupActions = controller
			.substringAfter("private fun onRendererCleanupQueueAccepted(")
			.substringBefore("private fun releaseRendererOwnedGeneration(")
		assertTrue(
			cleanupActions.indexOf("is ReaderRendererCleanupRequest.PendingDiscard") <
				cleanupActions.indexOf("pendingDeckGenerationId = null")
		)
	}

	@Test
	fun hostUsesOnePrivacySafeFaultSinkAndIdentitySafeRegistration() {
		val source = hostSource()

		assertEquals(
			1,
			Regex("ReaderPageQaFaultRegistry\\(").findAll(source).count()
		)
		assertContains(source, "ReaderPageQaFaultEventSink { event ->")
		assertContains(
			source,
			"onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted"
		)
		assertContains(
			source,
			"ReaderPageDiagnostic.qaFault(readerDiagnosticSession, event)"
		)
		assertContains(source, "ReaderPageQaFaultControl.attach(qaFaultRegistry)")
		assertContains(
			source,
			"ReaderPageQaFaultControl.detach(qaFaultRegistration)"
		)
		assertContains(source, "qaFaultRegistry.closeAndDrain()")
	}
}

private fun presentationBinding(): ReaderPresentationBinding = ReaderPresentationBinding(
	foliateSessionId = "presentation-session",
	publicationGeneration = 2L,
	viewportGeneration = 3L,
	profileGeneration = 4L,
	destinationCommitIdentity = ReaderDestinationCommitIdentity("presentation-session", 5L),
	rasterGeneration = 6L,
	textureGeneration = 7L,
	preparationGeneration = 8L
)

private fun nativePresentationProof(
	binding: ReaderPresentationBinding
): ReaderNativePagePresentationProof = ReaderNativePagePresentationProof(
	binding = binding,
	transitionToken = null,
	presentedFrame = 9L,
	viewportWidth = 1200,
	viewportHeight = 800,
	rasterGeneration = requireNotNull(binding.rasterGeneration),
	textureGeneration = requireNotNull(binding.textureGeneration)
)

private fun stalePresentationEffect(): ReaderPresentationEffect.ReleaseStalePresentation =
	ReaderPresentationEffect.ReleaseStalePresentation(
		token = ReaderPresentationToken(10L),
		binding = presentationBinding().copy(
			viewportGeneration = 11L,
			profileGeneration = 12L,
			rasterGeneration = 13L,
			textureGeneration = 14L,
			preparationGeneration = 15L
		)
	)

private fun presentationDeck(
	rasterProfileEpoch: Long,
	generationId: Long
): ReaderPagePreparedActiveDeck = ReaderPagePreparedActiveDeck(
	rasterProfileEpoch = rasterProfileEpoch,
	rasterEpoch = 3L,
	sourceCenterPageIndex = 4,
	generationId = generationId,
	preparationGeneration = 5L
)

private fun hostSource(): String = readerSource("KomikkuReaderNativeFrameHost.android.kt")

private fun readerSource(fileName: String): String {
	var current: File? =
		File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
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
