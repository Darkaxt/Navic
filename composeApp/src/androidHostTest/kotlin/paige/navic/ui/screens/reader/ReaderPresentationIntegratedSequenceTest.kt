package paige.navic.ui.screens.reader

import android.content.ComponentCallbacks2
import karacken.curl.PageSurfaceDeckReleaseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.*

class ReaderPresentationIntegratedSequenceTest {
	@Test
	fun cleanupQueueKeepsTransitionAndGestureProvenanceWhileDeduplicatingRetries() {
		val staleBinding = ReaderPresentationBinding(
			"task363-session",
			1L,
			2L,
			3L,
			ReaderDestinationCommitIdentity("task363-session", 1L),
			4L,
			5L,
			6L
		)
		val currentBinding = staleBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity("task363-session", 2L),
			rasterGeneration = 7L,
			textureGeneration = 8L,
			preparationGeneration = 9L
		)
		val currentProof = ReaderNativePagePresentationProof(
			binding = currentBinding,
			transitionToken = null,
			presentedFrame = 1L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 7L,
			textureGeneration = 8L
		)
		val currentState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(currentProof)
			),
			binding = currentBinding,
			nextTokenValue = 2L
		)
		val transitionProof = ReaderNativePagePresentationProof(
			binding = staleBinding,
			transitionToken = ReaderPresentationToken(1L),
			presentedFrame = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		val transitionCleanup = assertIs<ReaderPresentationEffect.ReleaseStalePresentation>(
			readerPresentationReduce(
				currentState,
				ReaderPresentationEvent.NativePagePresented(transitionProof)
			).effects.single()
		)
		val gestureSource = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(transitionProof.copy(transitionToken = null))
			),
			binding = staleBinding
		)
		val gestureClaim = assertNotNull(
			readerCurlClaimEvent(readerPresentationDecision(gestureSource), gestureId = 1L)
		)
		val gestureCleanup = assertIs<ReaderPresentationEffect.ReleaseStalePresentation>(
			readerPresentationReduce(currentState, gestureClaim).effects.single()
		)
		assertEquals(transitionCleanup.token?.value, gestureCleanup.token?.value)

		val queue = ReaderPresentationEffectQueue(capacity = 2)
		val transitionPending = queue.retain(listOf(transitionCleanup)).single()
		assertTrue(queue.retain(listOf(transitionCleanup)).isEmpty())
		val retainedGesture = queue.retain(listOf(gestureCleanup))

		assertEquals(1, retainedGesture.size)
		assertEquals(2, queue.pendingEffects().size)
		assertTrue(queue.acknowledge(transitionPending.identity))
		assertTrue(queue.acknowledge(retainedGesture.single().identity))
		assertTrue(queue.retain(listOf(transitionCleanup, gestureCleanup)).isEmpty())
		assertTrue(queue.pendingEffects().isEmpty())
	}

	@Test
	fun liveEngineHandbackKeepsFrameButFreezesPhysicalInputUntilNativeProof() {
		val binding = ReaderPresentationBinding(
			"task363-handback",
			1L,
			2L,
			3L,
			ReaderDestinationCommitIdentity("task363-handback", 1L),
			4L,
			5L,
			6L
		)
		val liveProof = ReaderLiveEnginePresentationProof(
			token = ReaderPresentationToken(1L),
			binding = binding,
			presentedFrameSequence = 1L
		)
		val readyFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Ready,
			generation = 6L,
			completedCount = 3,
			requiredCount = 3,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Ready,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				pendingTextureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready
			)
		)
		val exposedState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.LiveEngineExposed(
				ReaderPresentationFrameOwner.LiveEngine(liveProof)
			),
			binding = binding,
			preparationFacts = readyFacts,
			lastLiveEnginePresentedFrameSequence = 1L,
			nextTokenValue = 2L
		)
		val pending = readerPresentationReduce(
			exposedState,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(pending.decision.frameOwner)
		assertEquals(ReaderPresentationLayer.LiveEngine, pending.decision.layer)

		assertEquals(
			ReaderPagePhysicalDispatchMode.ChromeOnly,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = true,
				presentationInputPolicy = pending.decision.inputPolicy
			)
		)

		val handoff = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			pending.state.authority
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = handoff.token,
			presentedFrame = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		val settled = readerPresentationReduce(
			pending.state,
			ReaderPresentationEvent.NativePagePresented(nativeProof)
		)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(settled.state.authority)
		assertIs<ReaderPageNewPointerDecision.Accept>(
			assertIs<ReaderPresentationInputPolicy.NativePage>(settled.decision.inputPolicy)
				.policy.newPointer
		)
		assertEquals(
			ReaderPagePhysicalDispatchMode.PlayLikeCurl,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = true,
				presentationInputPolicy = settled.decision.inputPolicy
			)
		)
	}

	@Test
	fun invalidatingCoverCleanupRetiresObsoleteOwnersButKeepsCurrentRendererAlias() {
		val session = "cleanup-sequence"
		val bindingA = ReaderPresentationBinding(session, 1L, 2L, 3L,
			ReaderDestinationCommitIdentity(session, 1L), 44L, 45L, 46L)
		val bindingB = bindingA.copy(textureGeneration = 46L)
		val bindingC = bindingB.copy(viewportGeneration = 3L)
		val proofA = ReaderShellCoverCommitProof(ReaderPresentationToken(1L), bindingA, 1L, 1L, 1920, 1200)
		var controller = ReaderController(ReaderControllerState(presentation = ReaderPresentationState(
			authority = ReaderPresentationAuthority.ShellCover(proofA), binding = bindingA, nextTokenValue = 2L)))
		val owners = mutableMapOf(45L to bindingA, 46L to bindingB)
		val retired = mutableListOf<ReaderPresentationBinding>()
		val queue = ReaderPresentationEffectQueue()
		val gate = ReaderRendererOwnedGenerationReleaseGate(
			ownerForGeneration = owners::get,
			rasterGenerationForOwner = { owner: ReaderPresentationBinding -> assertNotNull(owner.rasterGeneration) },
			isProtectedGeneration = { generation -> owners[generation]?.let {
				controller.state.presentationDecision.retainsPresentationIdentity(null, it)
			} == true },
			requestRendererRelease = { PageSurfaceDeckReleaseResult.accepted() },
			retireOwner = { generation -> owners.remove(generation)?.let(retired::add); Unit })
		val handler = ReaderPresentationEffectHandler(releaseStalePresentation = { effect ->
			gate.request(effect.binding).also { accepted ->
				if (accepted) gate.completeRelease(assertNotNull(effect.binding.textureGeneration))
			}
		})
		fun dispatch(event: ReaderPresentationEvent) {
			val step = controller.onPresentationEvent(event)
			controller = step.controller
			controller.state.presentation.assertSequenceInvariants()
			queue.retain(step.presentationEffects)
			handler.deliver(queue.pendingEffects(), controller.state.presentationDecision) {
				assertTrue(queue.acknowledge(it))
			}
		}
		dispatch(ReaderPresentationEvent.BindingReplaced(bindingA, bindingB))
		assertTrue(retired.isEmpty())
		val invalidation = ReaderPresentationEvent.BindingReplaced(bindingB, bindingC)
		dispatch(invalidation)
		assertEquals(ReaderPresentationFrameOwner.Neutral, controller.state.presentationDecision.frameOwner)
		assertEquals(listOf(bindingA), retired)
		assertEquals(mapOf(46L to bindingB), owners, "The current target's renderer alias must remain owned")
		assertTrue(controller.state.presentation.rendererCleanupOwnership.isEmpty())
		assertTrue(queue.pendingEffects().isEmpty())
		dispatch(invalidation)
		assertEquals(listOf(bindingA), retired, "Replayed invalidation cannot retire the owner twice")
		assertEquals(mapOf(46L to bindingB), owners)
		assertTrue(queue.pendingEffects().isEmpty())
	}

	@Test
	fun completedNativeRequestDoesNotRearmAnUnrequestedTokenlessProof() {
		val fixture = ReaderPresentationSequenceFixture.openAtNativePage()
		try {
			fixture.assertQuiescent()
		} finally {
			fixture.dispose()
		}
	}

	@Test
	fun backgroundTextureCompletionRetainsOriginalCoverUntilRequestedExactNativeProof() {
		val fixture = ReaderPresentationSequenceFixture.openAtNativePage()
		try {
			fixture.requestShellCover()
			fixture.commitShellCoverDraw()
			fixture.beginBackgroundPreparation()
			fixture.completeBackgroundTextureDeck()
			fixture.reportPreparationReady()
			fixture.assertBackgroundCoverHasNoNativeRequest()
			fixture.requestCoalescedPageEntry(expectFrameRequest = true)
			fixture.commitNativePageDraw()
			fixture.assertQuiescent()
		} finally {
			fixture.dispose()
		}
	}

	@Test
	fun entryDuringNullDeckGapRejectsOldFenceAndReachesVisibleDeadlineTerminal() {
		val fixture = ReaderPresentationSequenceFixture.openAtNativePage()
		try {
			fixture.requestShellCover()
			fixture.commitShellCoverDraw()
			fixture.beginBackgroundPreparation()
			fixture.requestCoalescedPageEntry()
			fixture.assertOldFenceRejectedThenVisibleTimeout()
		} finally {
			fixture.dispose()
		}
	}

	@Test
	fun boundedPostWhispersyncReadyEntrySequenceHasOneOwnerAndNoDeadPendingState() {
		val fixture = ReaderPresentationSequenceFixture.openAtNativePage()
		try {
			fixture.turnPage()
			fixture.completeExactAcknowledgement()
			fixture.relocateFromTocWithoutTurnReceipt()
			fixture.turnPage()
			// A terminal curl is not a settled page: finish the exact Foliate/native proof first.
			fixture.completeExactAcknowledgement()
			fixture.requestShellCover()
			fixture.commitShellCoverDraw()
			fixture.beginBackgroundPreparation()
			fixture.completeBackgroundTextureDeck()
			fixture.reportPreparationReady()
			fixture.requestCoalescedPageEntry(expectFrameRequest = true)
			fixture.commitNativePageDraw()
			fixture.sendTrimMemoryUiHidden()
			fixture.restoreWindowVisibility()

			assertEquals(fixture.chapterOneSecondPageDestination, fixture.semanticDestination)
			assertEquals(fixture.semanticDestination, fixture.displayedBinding.destinationCommitIdentity)
			assertEquals(1, fixture.nextTurnCountAfterTocRelocation)
			assertTrue(fixture.decisions.all { it.frameOwner.layer() == it.layer })
			assertFalse(fixture.returnedCoverShowedBackgroundProgress)
			assertEquals(1, fixture.coalescedPageEntryCount)
			fixture.assertQuiescent()
			assertTrue(fixture.publicationStillOpen)
		} finally {
			fixture.dispose()
		}
	}
}

/** Fake physical callbacks and opaque Foliate facts, never a second presentation authority. */
private class ReaderPresentationSequenceFixture {
	private val session = "sequence-session"
	private val publication = ReaderPublicationIdentity("sequence-publication", resourceHref = "")
	private val initialBinding = ReaderPresentationBinding(session, 1L, 2L, 3L,
		ReaderDestinationCommitIdentity(session, 1L), 4L, 5L, 6L)
	private var controller = ReaderController(ReaderControllerState(
		readerSessionGeneration = 1L, publication = publication, nativeShellCoverUrl = "opaque-cover",
		canReturnToShellCover = true, shellCoverVisible = false))
	private val reporter = ReaderPresentationBindingReporter()
	private val lifecycleDelivery = ReaderPresentationLifecycleDelivery()
	private val deadlines = HostBridgeDeadlineScheduler()
	private val frames = SequencePresentedFrames()
	private val commands = mutableListOf<ReaderEngineCommand>()
	private val receipts = mutableListOf<ReaderPresentationEventReceipt>()
	private val effects = ReaderPresentationEffectQueue()
	private var appliedDecision: ReaderPresentationDecision? = null
	private val host = FakeReaderPresentationCommitHost(initialBinding, ::applyFrameOwner)
	private val bridge = ReaderPresentationHostBridge(host,
		transitionTimeoutScheduler = deadlines, transitionNowMillis = { 0L }, onEvent = ::dispatch)
	private val dispatcher = ReaderPresentationReceiptDispatcher(reporter, lifecycleDelivery,
		applyDecision = { selected, _ -> host.applyPresentationFrameOwner(selected) })
	// Metadata-only renderer ownership; the existing gate and handler fence/deduplicate releases.
	private val materialBindings = mutableMapOf<Long, ReaderPresentationBinding>()
	private val retiredBindings = mutableListOf<ReaderPresentationBinding>()
	private val releaseGate = ReaderRendererOwnedGenerationReleaseGate(
		ownerForGeneration = materialBindings::get,
		rasterGenerationForOwner = { binding: ReaderPresentationBinding -> assertNotNull(binding.rasterGeneration) },
		isProtectedGeneration = { generation -> materialBindings[generation]?.let {
			decision.retainsPresentationIdentity(null, it)
		} == true },
		requestRendererRelease = { PageSurfaceDeckReleaseResult.accepted() },
		retireOwner = { generation -> materialBindings.remove(generation)?.let(retiredBindings::add); Unit })
	private val effectHandler = ReaderPresentationEffectHandler(releaseStalePresentation = { effect ->
		releaseGate.request(effect.binding).also { accepted ->
			if (accepted) releaseGate.completeRelease(assertNotNull(effect.binding.textureGeneration))
		}
	})
	private var retainedCoverBinding: ReaderPresentationBinding? = null
	private var submittedDeck: ReaderPagePreparedActiveDeck? = null
	private var submittedFence: ReaderAcceptedDeckCallbackFence? = null
	private var deck: ReaderPagePreparedActiveDeck? = null
	private val publisher = ReaderNativePagePresentationPublisher(frames, ::currentCandidate, onEvent = ::dispatch)
	private var pendingRelocation: ReaderEngineEvent.Relocated? = null
	private var tocCommandBoundary = 0
	private var pageEntryRequestBoundary = 0L
	val decisions = mutableListOf<ReaderPresentationDecision>()
	private val backgroundCoverDecisions = mutableListOf<ReaderPresentationDecision>()
	val chapterOneDestination = ReaderDestinationCommitIdentity(session, 3L)
	val chapterOneSecondPageDestination = ReaderDestinationCommitIdentity(session, 4L)
	private val state get() = controller.state.presentation
	private val decision get() = controller.state.presentationDecision
	val semanticDestination get() = controller.state.destinationCommitIdentity
	val displayedBinding get() = assertIs<ReaderPresentationFrameOwner.NativePage>(
		assertNotNull(appliedDecision).frameOwner).proof.binding
	val nextTurnCountAfterTocRelocation get() = commands.drop(tocCommandBoundary)
		.filterIsInstance<ReaderEngineCommand.TurnPage>().count { it.direction == ReaderPageTurnDirection.Next }
	val coalescedPageEntryCount get() = (controller.state.shellCoverDismissalRequestSequence -
		pageEntryRequestBoundary).toInt()
	val returnedCoverShowedBackgroundProgress get() = backgroundCoverDecisions.any {
		it.preparationPresentation != ReaderPreparationPresentation.Hidden
	}
	val publicationStillOpen get() = controller.state.publication == publication &&
		state.lifecycle == ReaderPresentationLifecycleState.Foreground && state.binding != null

	init {
		reporter.reset(controller.state.readerSessionGeneration, controller.presentationVersion, state)
		lifecycleDelivery.reset(controller.presentationVersion, observedWindowVisible = null)
		assertTrue(reporter.bindPublication(initialBinding))
		assertTrue(lifecycleDelivery.bindPublication(initialBinding.publicationIdentity))
		dispatch(ReaderPresentationEvent.PublicationOpened(initialBinding))
		deliverRelocation(relocation(initialBinding, page = 0), initialBinding)
		reportPreparationReady()
		commitNativePageDraw()
	}

	private fun applyFrameOwner(selected: ReaderPresentationDecision) {
		appliedDecision = selected
		host.coverSelected = selected.frameOwner is ReaderPresentationFrameOwner.ShellCover
		host.predecessorSelected = !host.coverSelected
	}

	private fun accept(step: ReaderControllerStep, consumeReturnedReceipt: Boolean = true) {
		step.presentationReceipt?.let { receipt ->
			if (consumeReturnedReceipt) {
				assertNotNull(dispatcher.consumeReturned(reporter.captureEpoch(), receipt))
			}
			receipts += receipt
		}
		controller = step.controller
		commands += step.engineCommands
		effects.retain(step.presentationEffects)
		state.assertSequenceInvariants()
		decisions += decision
	}

	private fun dispatch(event: ReaderPresentationEvent): ReaderPresentationEventReceipt =
		assertNotNull(dispatcher.dispatch(event) { incoming ->
			val step = controller.onPresentationEvent(incoming)
			accept(step, consumeReturnedReceipt = false)
			step.presentationReceipt
		})

	private fun synchronizeHost() {
		host.currentBinding = state.binding
		bridge.update(decision)
		publisher.update()
		effectHandler.deliver(effects.pendingEffects(), decision) { identity ->
			assertTrue(effects.acknowledge(identity))
		}
		assertEquals(decision, appliedDecision)
		assertEquals(state.binding, reporter.lastReportedBinding)
		state.assertSequenceInvariants()
	}

	fun turnPage() {
		val before = commands.size
		accept(controller.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)))
		assertEquals(1, commands.drop(before).filterIsInstance<ReaderEngineCommand.TurnPage>().size)
		val binding = assertNotNull(state.binding)
		val next = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(session,
			assertNotNull(semanticDestination).commitSequence + 1L),
			rasterGeneration = assertNotNull(binding.rasterGeneration) + 10L,
			textureGeneration = assertNotNull(binding.textureGeneration) + 10L,
			preparationGeneration = assertNotNull(binding.preparationGeneration) + 10L)
		val token = state.nextTokenValue
		val claim = assertNotNull(readerCurlClaimEvent(decision, token))
		dispatch(claim)
		assertIs<ReaderPresentationInputPolicy.ClaimedCurl>(decision.inputPolicy)
		val event = relocation(next, assertNotNull(controller.state.chrome.currentLocator?.pageIndex) + 1,
			settleToken = "sequence-turn-$token")
		pendingRelocation = event
		dispatch(ReaderPresentationEvent.CurlTerminal(claim.frame.token, binding,
			assertNotNull(event.pageTurnSettlementReceiptOrNull())))
		assertIs<ReaderPresentationAuthority.CurlSettlementPending>(state.authority)
		synchronizeHost()
		assertTrue(deadlines.hasPending, "AwaitingFoliate must already have a deadline")
	}

	fun completeExactAcknowledgement() {
		val event = assertNotNull(pendingRelocation)
		val previous = assertNotNull(state.binding)
		val binding = previous.copy(destinationCommitIdentity = event.destinationCommitIdentity,
			rasterGeneration = event.pageTurnSettleRasterGeneration,
			textureGeneration = event.pageTurnSettleTextureGeneration,
			preparationGeneration = assertNotNull(previous.preparationGeneration) + 10L)
		deliverRelocation(event, binding)
		pendingRelocation = null
		val pending = assertIs<ReaderPresentationAuthority.CurlSettlementPending>(state.authority)
		assertEquals(ReaderCurlSettlementStage.AwaitingNativePresentation, pending.stage)
		assertNull(pending.expectedAcknowledgement)
		reportPreparationReady()
		commitNativePageDraw()
		assertFalse(deadlines.hasPending)
	}

	fun relocateFromTocWithoutTurnReceipt() {
		val locator = ReaderLocator(progress = 0.0, pageIndex = 0, pageCount = 10)
		accept(controller.navigateTo(locator))
		assertIs<ReaderEngineCommand.NavigateTo>(commands.last())
		val oldFrame = decision.frameOwner
		val previous = assertNotNull(state.binding)
		val binding = previous.copy(destinationCommitIdentity = chapterOneDestination,
			rasterGeneration = 34L, textureGeneration = 35L, preparationGeneration = 36L)
		deliverRelocation(relocation(binding, page = 0), binding)
		assertNull(controller.state.pageTurnSettlementAck, "TOC must not inherit the completed receipt")
		assertEquals(oldFrame, decision.frameOwner)
		assertIs<ReaderPageNewPointerDecision.Reject>(
			assertIs<ReaderPresentationInputPolicy.NativePage>(decision.inputPolicy).policy.newPointer)
		reportPreparationReady()
		assertNull(assertNotNull(currentCandidate()).transitionToken,
			"An unrelated TOC proof must not inherit the completed curl token")
		commitNativePageDraw()
		assertEquals(chapterOneDestination, displayedBinding.destinationCommitIdentity)
		tocCommandBoundary = commands.size
	}

	private fun relocation(binding: ReaderPresentationBinding, page: Int, settleToken: String? = null) =
		ReaderEngineEvent.Relocated(ReaderLocator(progress = 0.0, pageIndex = page, pageCount = 10),
			session, pageTurnSettleToken = settleToken,
			pageTurnSettleSessionId = session.takeIf { settleToken != null },
			pageTurnSettleRasterGeneration = binding.rasterGeneration.takeIf { settleToken != null },
			pageTurnSettleTextureGeneration = binding.textureGeneration.takeIf { settleToken != null },
			destinationCommitIdentity = binding.destinationCommitIdentity)

	private fun deliverRelocation(event: ReaderEngineEvent.Relocated, binding: ReaderPresentationBinding) {
		accept(controller.onEngineEvent(event))
		assertEquals(event.pageTurnSettlementReceiptOrNull(), controller.state.pageTurnSettlementAck)
		val reported = reporter.update(state.binding, binding, false, true,
			controller.state.pageTurnSettlementAck)
		if (reported != null) dispatch(reported)
		assertEquals(semanticDestination, state.binding?.destinationCommitIdentity)
		synchronizeHost()
	}

	fun requestShellCover() {
		val page = decision.frameOwner
		accept(controller.onPageTurnBoundary(ReaderPageTurnDirection.Previous))
		assertIs<ReaderRequiredTransition.CommitShellCover>(decision.requiredTransition)
		synchronizeHost()
		assertEquals(page, appliedDecision?.frameOwner)
		assertTrue(host.coverPrepared)
		assertFalse(host.coverSelected)
	}

	fun commitShellCoverDraw() {
		val page = decision.frameOwner
		val binding = state.binding
		val readyFacts = state.preparationFacts
		val readyDeck = deck
		host.registrations.last().draw()
		assertEquals(page, decision.frameOwner, "Draw alone cannot commit before the animation boundary")
		state.assertSequenceInvariants()
		host.runNextAnimationFrame()
		synchronizeHost()
		assertIs<ReaderPresentationFrameOwner.ShellCover>(decision.frameOwner)
		assertTrue(host.coverSelected)
		assertFalse(host.coverPrepared)
		assertEquals(binding, state.binding, "Cover commit cannot invalidate ready identity")
		assertEquals(readyFacts, state.preparationFacts)
		assertEquals(readyDeck, deck)
		assertEquals(binding, materialBindings[readyDeck?.generationId])
		assertTrue(frames.callbacks.isEmpty())
		assertFalse(deadlines.hasPending)
		backgroundCoverDecisions += decision
	}

	fun beginBackgroundPreparation() {
		val ready = assertNotNull(deck)
		val previous = assertNotNull(state.binding)
		val cover = decision.frameOwner
		val facts = state.preparationFacts
		retainedCoverBinding = previous
		// Actual active submission allocates only a texture generation and clears the
		// prepared-deck observation. The old accepted frame/Ready facts are not invalidated.
		submittedDeck = ready.copy(generationId = ready.generationId + 1L)
		submittedFence = ReaderAcceptedDeckCallbackFence(null,
			observedBinding(assertNotNull(submittedDeck)))
		materialBindings[assertNotNull(submittedDeck).generationId] = assertNotNull(submittedFence).binding
		deck = null
		val partial = observedBinding(null)
		assertNull(partial.rasterGeneration)
		assertNull(partial.textureGeneration)
		assertNull(reporter.update(previous, partial, false, false))
		assertEquals(previous, reporter.lastReportedBinding)
		assertEquals(facts, state.preparationFacts)
		assertEquals(cover, decision.frameOwner)
		synchronizeHost()
		assertEquals(previous, materialBindings[previous.textureGeneration])
		assertFalse(previous in retiredBindings)
		assertBackgroundCoverHasNoNativeRequest()
	}

	private fun observedBinding(observedDeck: ReaderPagePreparedActiveDeck?): ReaderPresentationBinding {
		val binding = assertNotNull(state.binding)
		return assertNotNull(readerPresentationHostBinding(ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true, windowVisible = true, foliateSessionId = session,
			publicationGeneration = binding.publicationGeneration,
			viewportGeneration = binding.viewportGeneration,
			viewportWidth = host.viewportWidth, viewportHeight = host.viewportHeight,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(binding.profileGeneration),
			destinationCommitIdentity = binding.destinationCommitIdentity,
			preparationGeneration = assertNotNull(binding.preparationGeneration),
			visualPageIndex = controller.state.chrome.currentLocator?.pageIndex,
			preparedDeck = observedDeck, preparedDeckAdmitted = true)))
	}

	fun completeBackgroundTextureDeck() {
		val completed = assertNotNull(submittedDeck)
		assertTrue(readerAcceptedDeckCallbackMatches(assertNotNull(submittedFence), decision,
			completed.preparationGeneration, completed.rasterEpoch, completed.generationId),
			"Completion must pass the actual captured renderer fence, without retagging")
		val cover = decision.frameOwner
		val previous = assertNotNull(state.binding)
		deck = completed
		val replacement = observedBinding(completed)
		assertEquals(previous.copy(textureGeneration = completed.generationId), replacement)
		materialBindings[completed.generationId] = replacement
		dispatch(assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(previous, replacement, false, false)))
		assertEquals(replacement, reporter.lastReportedBinding)
		assertEquals(cover, decision.frameOwner, "Texture completion cannot replace the committed cover receipt")
		submittedDeck = null
		submittedFence = null
		synchronizeHost()
	}

	fun assertOldFenceRejectedThenVisibleTimeout() {
		val submitted = assertNotNull(submittedDeck)
		val fence = assertNotNull(submittedFence)
		val cover = decision.frameOwner
		val token = decision.pendingTransitionToken
		assertFalse(readerAcceptedDeckCallbackMatches(fence, decision,
			submitted.preparationGeneration, submitted.rasterEpoch, submitted.generationId))
		// onDeckPrepared's rejected-fence route releases the actual accepted owner;
		// no synthetic completion or token replacement is delivered to common.
		assertTrue(releaseGate.request(fence.binding))
		releaseGate.completeRelease(submitted.generationId)
		submittedDeck = null
		submittedFence = null
		synchronizeHost()
		assertNull(currentCandidate())
		assertTrue(frames.callbacks.isEmpty())
		assertEquals(token, decision.pendingTransitionToken)
		assertTrue(deadlines.hasPending)
		deadlines.runPending()
		synchronizeHost()
		assertEquals(cover, decision.frameOwner)
		val failure = assertIs<ReaderDiagnosticPresentation.Failure>(decision.diagnosticPresentation)
		assertEquals(ReaderPresentationFailureReason.TimedOut, failure.reason)
		assertTrue(failure.retryable)
		assertTrue(failure.cancellable)
		assertEquals(ReaderRequiredTransition.None, decision.requiredTransition)
		assertFalse(deadlines.hasPending)
		assertTrue(frames.callbacks.isEmpty())
	}

	fun assertBackgroundCoverHasNoNativeRequest() {
		assertIs<ReaderPresentationFrameOwner.ShellCover>(decision.frameOwner)
		assertEquals(ReaderPresentationInputPolicy.ShellCover, decision.inputPolicy)
		assertEquals(ReaderRequiredTransition.None, decision.requiredTransition)
		assertNull(controller.state.pendingShellCoverDismissal)
		assertNull(currentCandidate())
		assertTrue(frames.callbacks.isEmpty())
		assertFalse(deadlines.hasPending)
		backgroundCoverDecisions += decision
		assertEquals(ReaderPreparationPresentation.Hidden, decision.preparationPresentation)
	}

	fun requestCoalescedPageEntry(expectFrameRequest: Boolean = false) {
		pageEntryRequestBoundary = controller.state.shellCoverDismissalRequestSequence
		val cover = decision.frameOwner
		val before = commands.size
		var coalescedToken: ReaderPresentationToken? = null
		repeat(4) { attempt ->
			accept(controller.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)))
			if (attempt == 0) coalescedToken = assertNotNull(decision.pendingTransitionToken)
			assertEquals(coalescedToken, decision.pendingTransitionToken)
			synchronizeHost()
			assertEquals(cover, decision.frameOwner)
			assertEquals(ReaderPreparationPresentation.Hidden, decision.preparationPresentation)
			assertEquals(if (expectFrameRequest) 1 else 0, frames.callbacks.size)
			assertTrue(deadlines.hasPending)
			if (!expectFrameRequest) assertNull(currentCandidate())
		}
		val navigation = commands.drop(before).filterIsInstance<ReaderEngineCommand.NavigateTo>().single()
		assertEquals(1, coalescedPageEntryCount)
		// Foliate acknowledges the same-target cover dismissal without any turn receipt.
		val binding = assertNotNull(state.binding)
		deliverRelocation(relocation(binding, assertNotNull(navigation.locator.pageIndex)).copy(
			locator = navigation.locator.copy(reason = navigation.relocationReason)), binding)
		assertNull(controller.state.pageTurnSettlementAck)
		assertEquals(cover, decision.frameOwner)
	}

	fun reportPreparationReady() {
		val binding = assertNotNull(state.binding)
		val page = controller.state.chrome.currentLocator?.pageIndex ?: 0
		materialBindings[assertNotNull(binding.textureGeneration)] = binding
		deck = ReaderPagePreparedActiveDeck(binding.profileGeneration,
			assertNotNull(binding.rasterGeneration), page, assertNotNull(binding.textureGeneration),
			assertNotNull(binding.preparationGeneration))
		dispatch(ReaderPresentationEvent.PreparationReported(binding, ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Ready, generation = assertNotNull(binding.preparationGeneration),
			completedCount = 3, requiredCount = 3, readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Ready,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready, pendingTextureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready))))
		synchronizeHost()
	}

	private fun currentCandidate(): ReaderNativePagePresentationCandidate? {
		if (state.authority is ReaderPresentationAuthority.CurlGesture ||
			(state.authority as? ReaderPresentationAuthority.CurlSettlementPending)?.stage ==
			ReaderCurlSettlementStage.AwaitingFoliate) return null
		val transition = decision.requiredTransition as? ReaderRequiredTransition.PresentNativePage
		// Match currentNativePagePresentationCandidateOrNull: tokens belong to current requests,
		// never to the retained predecessor's proof (including an earlier completed curl).
		val token = transition?.token?.takeIf { transition.binding == state.binding }
		return ReaderNativePagePresentationHostSnapshot(state.binding, token, deck, state.preparationFacts,
			controller.state.chrome.currentLocator?.pageIndex, host.viewportWidth, host.viewportHeight,
			host.attached, state.lifecycle == ReaderPresentationLifecycleState.Foreground,
			viewerReplacementAdmitted = true, rendererDeckReady = deck != null,
			nativePresentationVisible = deck != null, shellCoverSelected = host.coverSelected).currentCandidateOrNull()
	}

	fun commitNativePageDraw() {
		val prior = decision.frameOwner
		assertEquals(1, frames.callbacks.size)
		assertEquals(prior, appliedDecision?.frameOwner)
		if (prior is ReaderPresentationFrameOwner.ShellCover) {
			val retained = assertNotNull(retainedCoverBinding)
			assertEquals(retained, materialBindings[retained.textureGeneration])
			assertFalse(retained in retiredBindings)
		}
		frames.present()
		synchronizeHost()
		assertTrue(frames.callbacks.isEmpty(),
			"Completing a native request must not rearm an unrequested tokenless proof")
		assertIs<ReaderPresentationAuthority.SettledNativePage>(state.authority)
		assertEquals(state.binding, displayedBinding)
		assertEquals(semanticDestination, displayedBinding.destinationCommitIdentity)
		assertIs<ReaderPageNewPointerDecision.Accept>(
			assertIs<ReaderPresentationInputPolicy.NativePage>(decision.inputPolicy).policy.newPointer)
	}

	fun sendTrimMemoryUiHidden() {
		val prior = state
		val priorBinding = assertNotNull(prior.binding)
		val event = assertNotNull(readerPresentationLifecycleEventForTrimMemory(
			ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
		assertEquals(ReaderPresentationLifecycleEvent.VisibilityLost, event)
		lifecycleDelivery.observe(event)
		assertNotNull(lifecycleDelivery.retry(::dispatch))
		synchronizeHost()
		val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(state.authority)
		assertEquals(ReaderPresentationFrameOwner.Neutral, pending.retainedFrame)
		assertEquals(priorBinding, pending.nativePresentationRequest?.binding)
		assertEquals(priorBinding.preparationGeneration,
			pending.nativePresentationRequest?.retryAfterPreparationGeneration)
		assertEquals(priorBinding, state.binding)
		assertEquals(priorBinding.destinationCommitIdentity, semanticDestination)
		assertEquals(ReaderPagePreparationFacts(), state.preparationFacts)
		assertEquals(publication, controller.state.publication)
		deck = null
		materialBindings.remove(assertNotNull(priorBinding.textureGeneration))?.let(retiredBindings::add)
	}

	fun restoreWindowVisibility() {
		lifecycleDelivery.observe(readerPresentationLifecycleEventForWindowVisibility(true))
		assertNotNull(lifecycleDelivery.retry(::dispatch))
		synchronizeHost()
		assertEquals(ReaderPresentationFrameOwner.Neutral, decision.frameOwner)
		assertTrue(frames.callbacks.isEmpty())
		val previous = assertNotNull(state.binding)
		val fresh = previous.copy(
			rasterGeneration = assertNotNull(previous.rasterGeneration) + 1L,
			textureGeneration = assertNotNull(previous.textureGeneration) + 1L,
			preparationGeneration = assertNotNull(previous.preparationGeneration) + 1L
		)
		dispatch(assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(previous, fresh, false, false)))
		reportPreparationReady()
		assertEquals(1, frames.callbacks.size)
		commitNativePageDraw()
	}

	fun assertQuiescent() {
		assertTrue(decisions.all { it.diagnosticPresentation == ReaderDiagnosticPresentation.Hidden })
		assertNull(submittedDeck)
		assertNull(submittedFence)
		assertNull(decision.pendingTransitionToken)
		assertEquals(ReaderRequiredTransition.None, decision.requiredTransition)
		assertNull(pendingRelocation)
		assertNull(controller.state.pendingShellCoverDismissal)
		assertNull(controller.state.pageTurnSettlementAck)
		assertFalse(state.authority is ReaderPresentationAuthority.CurlSettlementPending)
		assertTrue(frames.callbacks.isEmpty())
		assertTrue(host.animationFrames.isEmpty())
		assertTrue(host.registrations.all { it.unregisterCount == 1 })
		assertFalse(host.coverPrepared)
		assertFalse(deadlines.hasPending)
		assertTrue(effects.pendingEffects().isEmpty())
		assertTrue(state.rendererCleanupOwnership.isEmpty())
		assertEquals(0, dispatcher.pendingHostEffectCount)
		assertEquals(0, lifecycleDelivery.pendingEventCount)
		retainedCoverBinding?.let { retained ->
			assertEquals(1, retiredBindings.count { it == retained })
			assertFalse(materialBindings.containsKey(retained.textureGeneration))
		}
		assertEquals(listOf(displayedBinding), materialBindings.values.toList())
		val count = receipts.size
		synchronizeHost()
		assertEquals(count, receipts.size, "A quiescent host must not replay a receipt")
		assertTrue(frames.callbacks.isEmpty())
	}

	fun dispose() { publisher.dispose(); bridge.dispose() }

	companion object {
		fun openAtNativePage() = ReaderPresentationSequenceFixture()
	}
}

private class SequencePresentedFrames : ReaderNativePagePresentedFrameSource {
	private var nextId = 1L
	val callbacks = linkedMapOf<Long, (Long) -> Unit>()
	override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long = nextId++.also {
		callbacks[it] = onPresented
	}
	override fun cancelPresentedFrameRequest(requestId: Long): Boolean = callbacks.remove(requestId) != null
	fun present() {
		val (id, callback) = callbacks.entries.single()
		callbacks.remove(id)
		callback(id)
	}
}
