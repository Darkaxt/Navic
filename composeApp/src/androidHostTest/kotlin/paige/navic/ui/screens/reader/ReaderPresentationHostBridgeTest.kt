package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce

class ReaderPresentationHostBridgeTest {
	@Test
	fun exactDrawAndAnimationBoundaryEmitOneTokenBoundCommit() {
		val fixture = BridgeFixture()

		fixture.bridge.update(fixture.pendingDecision)
		fixture.bridge.update(fixture.pendingDecision)

		assertTrue(fixture.host.predecessorSelected)
		assertTrue(fixture.host.coverPrepared)
		assertEquals(1, fixture.host.registrations.size)
		fixture.host.registrations.single().draw()
		fixture.host.registrations.single().draw()

		assertTrue(fixture.events.isEmpty())
		assertEquals(1, fixture.host.registrations.single().unregisterCount)
		assertEquals(1, fixture.host.animationFrames.size)

		fixture.host.runNextAnimationFrame()
		fixture.host.runAllAnimationFrames()

		val event = assertIs<ReaderPresentationEvent.ShellCoverCommitted>(fixture.events.single())
		assertEquals(
			ReaderShellCoverCommitProof(
				token = fixture.transition.token,
				binding = fixture.binding,
				coverGeneration = fixture.transition.coverGeneration,
				presentedFrame = 1L,
				viewportWidth = 1920,
				viewportHeight = 1200
			),
			event.proof
		)
		assertTrue(fixture.host.predecessorSelected)
		assertFalse(fixture.host.coverSelected)
		assertEquals(1, fixture.events.size)
	}

	@Test
	fun bindingCoverGenerationAndGeometryMustRemainCurrentThroughFrameBoundary() {
		val wrongBinding = BridgeFixture().apply {
			host.currentBinding = binding.copy(publicationGeneration = 99L)
		}
		wrongBinding.bridge.update(wrongBinding.pendingDecision)
		assertTrue(wrongBinding.host.registrations.isEmpty())
		assertTrue(wrongBinding.events.isEmpty())

		val wrongGeneration = BridgeFixture().apply {
			host.preparedCoverGenerationOverride = transition.coverGeneration + 1L
		}
		wrongGeneration.bridge.update(wrongGeneration.pendingDecision)
		assertTrue(wrongGeneration.host.registrations.isEmpty())
		assertTrue(wrongGeneration.events.isEmpty())

		val zeroGeometry = BridgeFixture().apply {
			host.viewportWidth = 0
		}
		zeroGeometry.bridge.update(zeroGeometry.pendingDecision)
		assertTrue(zeroGeometry.host.registrations.isEmpty())
		assertTrue(zeroGeometry.events.isEmpty())

		val staleGeometry = BridgeFixture()
		staleGeometry.bridge.update(staleGeometry.pendingDecision)
		staleGeometry.host.registrations.single().draw()
		staleGeometry.host.viewportWidth = 1919
		staleGeometry.host.runNextAnimationFrame()

		assertTrue(staleGeometry.events.isEmpty())
		assertEquals(1, staleGeometry.host.cancelPreparationCount)
		assertTrue(staleGeometry.host.predecessorSelected)
	}

	@Test
	fun replacementAndTransitionRemovalCancelOldListenerExactlyOnce() {
		val fixture = BridgeFixture()
		fixture.bridge.update(fixture.pendingDecision)
		val oldRegistration = fixture.host.registrations.single()

		val replacement = fixture.pendingDecision(
			token = ReaderPresentationToken(fixture.transition.token.value + 1L),
			coverGeneration = fixture.transition.coverGeneration + 1L
		)
		fixture.bridge.update(replacement)

		assertEquals(1, oldRegistration.unregisterCount)
		assertEquals(1, fixture.host.cancelPreparationCount)
		assertEquals(2, fixture.host.registrations.size)
		oldRegistration.draw()
		fixture.host.runAllAnimationFrames()
		assertTrue(fixture.events.isEmpty())

		val replacementRegistration = fixture.host.registrations.last()
		fixture.bridge.update(fixture.nativeDecision)
		fixture.bridge.update(fixture.nativeDecision)

		assertEquals(1, replacementRegistration.unregisterCount)
		assertEquals(2, fixture.host.cancelPreparationCount)
		replacementRegistration.draw()
		fixture.host.runAllAnimationFrames()
		assertTrue(fixture.events.isEmpty())
		assertTrue(fixture.host.predecessorSelected)
	}

	@Test
	fun detachAndDisposeFenceDrawAndFrameCallbacks() {
		val detached = BridgeFixture()
		detached.bridge.update(detached.pendingDecision)
		val detachedRegistration = detached.host.registrations.single()
		detachedRegistration.draw()
		detached.bridge.onHostDetached()
		detached.host.runAllAnimationFrames()

		assertEquals(1, detachedRegistration.unregisterCount)
		assertTrue(detached.events.isEmpty())

		val disposed = BridgeFixture()
		disposed.bridge.update(disposed.pendingDecision)
		val disposedRegistration = disposed.host.registrations.single()
		disposed.bridge.dispose()
		disposed.bridge.update(disposed.pendingDecision)
		disposedRegistration.draw()
		disposed.host.runAllAnimationFrames()

		assertEquals(1, disposedRegistration.unregisterCount)
		assertEquals(1, disposed.host.registrations.size)
		assertTrue(disposed.events.isEmpty())
	}

	@Test
	fun productionNativeProofSettlesAuthorityBeforeReturnToCoverCanCommit() {
		val fixture = BridgeFixture()
		var presentation = paige.navic.reader.ReaderPresentationState()
		val eventOrder = mutableListOf<ReaderPresentationEvent>()
		fun reduce(event: ReaderPresentationEvent) {
			eventOrder += event
			presentation = readerPresentationReduce(presentation, event).state
		}
		reduce(ReaderPresentationEvent.PublicationOpened(fixture.binding))
		val publisher = ReaderNativePagePresentationPublisher(::reduce)
		val snapshot = ReaderNativePagePresentationHostSnapshot(
			binding = fixture.binding,
			transitionToken = null,
			deck = ReaderPagePreparedActiveDeck(
				rasterProfileEpoch = fixture.binding.profileGeneration,
				rasterEpoch = requireNotNull(fixture.binding.rasterGeneration),
				sourceCenterPageIndex = 4,
				generationId = requireNotNull(fixture.binding.textureGeneration),
				preparationGeneration = requireNotNull(fixture.binding.preparationGeneration)
			),
			visualPageIndex = 4,
			viewportWidth = 1920,
			viewportHeight = 1200,
			hostAttached = true,
			windowVisible = true,
			viewerReplacementAdmitted = true,
			rendererDeckReady = true,
			nativePresentationVisible = true,
			shellCoverSelected = false
		)

		publisher.update(snapshot.currentCandidateOrNull())
		publisher.update(snapshot.currentCandidateOrNull())

		assertEquals(2, eventOrder.size)
		assertIs<ReaderPresentationEvent.PublicationOpened>(eventOrder[0])
		val nativeEvent = assertIs<ReaderPresentationEvent.NativePagePresented>(eventOrder[1])
		assertEquals(fixture.binding, nativeEvent.proof.binding)
		assertEquals(1920, nativeEvent.proof.viewportWidth)
		assertEquals(1200, nativeEvent.proof.viewportHeight)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.authority)

		val requested = ReaderController(
			ReaderControllerState(
				presentation = presentation,
				nativeShellCoverUrl = "test-cover",
				canReturnToShellCover = true
			)
		).onPageTurnBoundary(paige.navic.reader.ReaderPageTurnDirection.Previous)
		assertIs<ReaderRequiredTransition.CommitShellCover>(
			requested.controller.state.presentationDecision.requiredTransition
		)
		assertEquals(
			ReaderPresentationLayer.NativePage,
			requested.controller.state.presentationDecision.layer
		)
		assertFalse(
			readerNativeShellCoverSelected(
				shellCoverIntent = true,
				decision = requested.controller.state.presentationDecision,
				hasValidatedNativePredecessor = true,
				shellCoverAlreadySelected = false
			)
		)
	}

	@Test
	fun nativeProofCandidateRequiresExactVisibleCurrentDeckAndHostFacts() {
		val fixture = BridgeFixture()
		val deck = ReaderPagePreparedActiveDeck(
			rasterProfileEpoch = fixture.binding.profileGeneration,
			rasterEpoch = requireNotNull(fixture.binding.rasterGeneration),
			sourceCenterPageIndex = 4,
			generationId = requireNotNull(fixture.binding.textureGeneration),
			preparationGeneration = requireNotNull(fixture.binding.preparationGeneration)
		)
		val current = ReaderNativePagePresentationHostSnapshot(
			binding = fixture.binding,
			transitionToken = null,
			deck = deck,
			visualPageIndex = 4,
			viewportWidth = 1920,
			viewportHeight = 1200,
			hostAttached = true,
			windowVisible = true,
			viewerReplacementAdmitted = true,
			rendererDeckReady = true,
			nativePresentationVisible = true,
			shellCoverSelected = false
		)

		assertNotNull(current.currentCandidateOrNull())
		listOf(
			current.copy(hostAttached = false),
			current.copy(windowVisible = false),
			current.copy(viewerReplacementAdmitted = false),
			current.copy(rendererDeckReady = false),
			current.copy(nativePresentationVisible = false),
			current.copy(shellCoverSelected = true),
			current.copy(viewportWidth = 0),
			current.copy(visualPageIndex = 5),
			current.copy(deck = deck.copy(generationId = deck.generationId + 1L)),
			current.copy(deck = deck.copy(preparationGeneration = deck.preparationGeneration + 1L))
		).forEach { stale ->
			assertNull(stale.currentCandidateOrNull())
		}
	}

	@Test
	fun unavailableFallbackAdmitsOnlyStartupOrNoPredecessorPresentation() {
		val unavailable = readerPresentationDecision(paige.navic.reader.ReaderPresentationState())

		assertTrue(
			readerNativeShellCoverSelected(
				shellCoverIntent = true,
				decision = unavailable,
				hasValidatedNativePredecessor = false,
				shellCoverAlreadySelected = false
			)
		)
		assertTrue(
			readerNativeShellCoverSelected(
				shellCoverIntent = true,
				decision = unavailable,
				hasValidatedNativePredecessor = true,
				shellCoverAlreadySelected = true
			)
		)
		assertFalse(
			readerNativeShellCoverSelected(
				shellCoverIntent = true,
				decision = unavailable,
				hasValidatedNativePredecessor = true,
				shellCoverAlreadySelected = false
			)
		)
	}

	@Test
	fun emittedReceiptRemainsCancellableUntilAuthorityAcceptance() {
		val removed = BridgeFixture()
		removed.emitCoverReceipt()
		assertTrue(removed.host.coverPrepared)
		removed.bridge.update(removed.nativeDecision)
		removed.bridge.update(removed.nativeDecision)
		assertEquals(1, removed.host.cancelPreparationCount)
		assertFalse(removed.host.coverPrepared)

		val replaced = BridgeFixture()
		replaced.emitCoverReceipt()
		replaced.bridge.update(
			replaced.pendingDecision(
				token = ReaderPresentationToken(replaced.transition.token.value + 1L),
				coverGeneration = replaced.transition.coverGeneration + 1L
			)
		)
		assertEquals(1, replaced.host.cancelPreparationCount)
		assertEquals(2, replaced.host.registrations.size)

		val detached = BridgeFixture()
		detached.emitCoverReceipt()
		detached.bridge.onHostDetached()
		detached.bridge.onHostDetached()
		assertEquals(1, detached.host.cancelPreparationCount)

		val disposed = BridgeFixture()
		disposed.emitCoverReceipt()
		disposed.bridge.dispose()
		disposed.bridge.dispose()
		assertEquals(1, disposed.host.cancelPreparationCount)
	}

	@Test
	fun rejectedReceiptCancelsPreparedCoverButExactAcceptanceFinalizesIt() {
		val rejected = BridgeFixture()
		rejected.emitCoverReceipt()
		rejected.bridge.update(rejected.pendingDecision)
		assertEquals(1, rejected.host.cancelPreparationCount)
		assertEquals(0, rejected.host.completePreparationCount)
		assertFalse(rejected.host.coverPrepared)

		val mismatched = BridgeFixture()
		val emitted = mismatched.emitCoverReceipt()
		val mismatchedReduction = readerPresentationReduce(
			mismatched.pendingState,
			ReaderPresentationEvent.ShellCoverCommitted(
				emitted.proof.copy(presentedFrame = emitted.proof.presentedFrame + 1L)
			)
		)
		mismatched.host.coverSelected = true
		mismatched.bridge.update(mismatchedReduction.decision)
		assertEquals(1, mismatched.host.cancelPreparationCount)
		assertEquals(0, mismatched.host.completePreparationCount)

		val accepted = BridgeFixture()
		val receipt = accepted.emitCoverReceipt()
		val reduction = readerPresentationReduce(
			accepted.pendingState,
			receipt
		)
		accepted.host.coverSelected = true
		accepted.bridge.update(reduction.decision)
		accepted.bridge.onHostDetached()

		assertEquals(0, accepted.host.cancelPreparationCount)
		assertEquals(1, accepted.host.completePreparationCount)
		assertTrue(accepted.host.coverSelected)
		assertFalse(accepted.host.coverPrepared)
	}

	@Test
	fun productionLayerAndProofRetentionSeamKeepsNativeUntilAcceptedSelection() {
		val fixture = BridgeFixture()
		val host = LayerBackedReaderPresentationCommitHost(fixture.binding)
		val events = mutableListOf<ReaderPresentationEvent>()
		val bridge = ReaderPresentationHostBridge(host, events::add)
		val retainedProof = fixture.nativeProof
		val retainedPreparationGeneration = fixture.binding.preparationGeneration

		bridge.update(fixture.pendingDecision)
		assertEquals(
			ReaderShellCoverHostLayer.PreparedBehindPredecessor,
			host.currentLayer
		)
		assertTrue(host.predecessorSelected)
		assertFalse(host.coverSelected)
		assertEquals(listOf("prepared-behind-native"), host.layerEvents)
		host.registrations.single().draw()
		host.runNextAnimationFrame()

		val receipt = assertIs<ReaderPresentationEvent.ShellCoverCommitted>(events.single())
		assertTrue(host.predecessorSelected)
		assertFalse(host.coverSelected)
		val accepted = readerPresentationReduce(fixture.pendingState, receipt)
		host.selectAcceptedCover()
		bridge.update(accepted.decision)

		assertEquals(ReaderShellCoverHostLayer.Selected, host.currentLayer)
		assertFalse(host.predecessorSelected)
		assertTrue(host.coverSelected)
		assertEquals(
			listOf("prepared-behind-native", "cover-selected"),
			host.layerEvents
		)
		assertEquals(0, host.rasterInvalidationCount)
		assertEquals(0, host.preparationInvalidationCount)
		assertEquals(1, host.completePreparationCount)
		assertEquals(fixture.nativeProof, retainedProof)
		assertEquals(fixture.binding.preparationGeneration, retainedPreparationGeneration)
	}

	@Test
	fun shellCoverTransactionRetainsNativeAndPreparationProofUntilExactReceipt() {
		val fixture = BridgeFixture()
		val facts = ReaderPagePreparationFacts(
			generation = 41L,
			completedCount = 2,
			requiredCount = 3
		)
		val controller = ReaderController(
			ReaderControllerState(
				presentation = fixture.nativeState.copy(preparationFacts = facts),
				nativeShellCoverUrl = "test-cover",
				canReturnToShellCover = true
			)
		)

		val requested = controller.onPageTurnBoundary(
			paige.navic.reader.ReaderPageTurnDirection.Previous
		)

		assertTrue(requested.controller.state.shellCoverVisible)
		assertEquals(
			ReaderPresentationLayer.NativePage,
			requested.controller.state.presentationDecision.layer
		)
		assertFalse(
			readerNativeShellCoverSelected(
				shellCoverIntent = requested.controller.state.shellCoverVisible,
				decision = requested.controller.state.presentationDecision,
				hasValidatedNativePredecessor = true,
				shellCoverAlreadySelected = false
			)
		)
		assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			requested.controller.state.presentation.authority
		)
		assertEquals(fixture.nativeProof, requested.controller.state.presentation.authorityFrameProof())
		assertEquals(facts, requested.controller.state.presentation.preparationFacts)

		val transition = assertIs<ReaderRequiredTransition.CommitShellCover>(
			requested.controller.state.presentationDecision.requiredTransition
		)
		val stale = requested.controller.onPresentationEvent(
			ReaderPresentationEvent.ShellCoverCommitted(
				ReaderShellCoverCommitProof(
					token = ReaderPresentationToken(transition.token.value + 1L),
					binding = transition.binding,
					coverGeneration = transition.coverGeneration,
					presentedFrame = 1L,
					viewportWidth = 1920,
					viewportHeight = 1200
				)
			)
		)
		assertTrue(stale.controller.state.shellCoverVisible)
		assertFalse(
			readerNativeShellCoverSelected(
				shellCoverIntent = stale.controller.state.shellCoverVisible,
				decision = stale.controller.state.presentationDecision,
				hasValidatedNativePredecessor = true,
				shellCoverAlreadySelected = false
			)
		)
		assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			stale.controller.state.presentation.authority
		)

		val committed = requested.controller.onPresentationEvent(
			ReaderPresentationEvent.ShellCoverCommitted(
				ReaderShellCoverCommitProof(
					token = transition.token,
					binding = transition.binding,
					coverGeneration = transition.coverGeneration,
					presentedFrame = 1L,
					viewportWidth = 1920,
					viewportHeight = 1200
				)
			)
		)

		assertTrue(committed.controller.state.shellCoverVisible)
		assertTrue(
			readerNativeShellCoverSelected(
				shellCoverIntent = committed.controller.state.shellCoverVisible,
				decision = committed.controller.state.presentationDecision,
				hasValidatedNativePredecessor = true,
				shellCoverAlreadySelected = false
			)
		)
		assertIs<ReaderPresentationAuthority.ShellCover>(
			committed.controller.state.presentation.authority
		)
		assertEquals(facts, committed.controller.state.presentation.preparationFacts)
		assertEquals(
			fixture.binding.preparationGeneration,
			committed.controller.state.presentation.binding?.preparationGeneration
		)
		assertEquals(0, fixture.host.rasterInvalidationCount)
		assertEquals(0, fixture.host.preparationInvalidationCount)
	}
}

private class BridgeFixture {
	val binding = ReaderPresentationBinding(
		foliateSessionId = "session-current",
		publicationGeneration = 2L,
		viewportGeneration = 3L,
		profileGeneration = 4L,
		rasterGeneration = 5L,
		textureGeneration = 6L,
		preparationGeneration = 7L
	)
	val nativeProof = ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = null,
		presentedFrame = 8L,
		viewportWidth = 1920,
		viewportHeight = 1200,
		rasterGeneration = 5L,
		textureGeneration = 6L
	)
	val nativeState = paige.navic.reader.ReaderPresentationState(
		authority = ReaderPresentationAuthority.SettledNativePage(
			ReaderPresentationFrameOwner.NativePage(nativeProof)
		),
		binding = binding,
		nextTokenValue = 21L
	)
	val nativeDecision = readerPresentationDecision(nativeState)
	val pendingState = readerPresentationReduce(
		nativeState,
		ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 22L)
	).state
	val pendingDecision = readerPresentationDecision(pendingState)
	val transition = assertIs<ReaderRequiredTransition.CommitShellCover>(
		pendingDecision.requiredTransition
	)
	val host = FakeReaderPresentationCommitHost(binding)
	val events = mutableListOf<ReaderPresentationEvent>()
	val bridge = ReaderPresentationHostBridge(host, events::add)

	fun emitCoverReceipt(): ReaderPresentationEvent.ShellCoverCommitted {
		bridge.update(pendingDecision)
		host.registrations.last().draw()
		host.runNextAnimationFrame()
		return assertIs<ReaderPresentationEvent.ShellCoverCommitted>(events.last())
	}

	fun pendingDecision(
		token: ReaderPresentationToken,
		coverGeneration: Long
	): ReaderPresentationDecision = pendingDecision.copy(
		authority = ReaderPresentationAuthority.ShellCoverCommitPending(
			retainedFrame = paige.navic.reader.ReaderShellCoverRetainedFrame.NativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			token = token,
			binding = binding,
			coverGeneration = coverGeneration
		),
		requiredTransition = ReaderRequiredTransition.CommitShellCover(
			token = token,
			binding = binding,
			coverGeneration = coverGeneration
		)
	)
}

private class LayerBackedReaderPresentationCommitHost(
	private val binding: ReaderPresentationBinding
) : ReaderPresentationCommitHost {
	private var preparedCoverGeneration: Long? = null
	private val layer = ReaderShellCoverLayerController(
		onPrepareBehindPredecessor = { layerEvents += "prepared-behind-native" },
		onHidePreparedCover = { layerEvents += "prepared-hidden" },
		onSelectCover = { layerEvents += "cover-selected" },
		onInvalidateRasterDeck = { rasterInvalidationCount += 1 },
		onInvalidatePreparation = { preparationInvalidationCount += 1 }
	)
	val layerEvents = mutableListOf<String>()
	var rasterInvalidationCount = 0
	var preparationInvalidationCount = 0
	var completePreparationCount = 0
	val registrations = mutableListOf<FakeDrawRegistration>()
	private val animationFrames = mutableListOf<() -> Unit>()
	val currentLayer: ReaderShellCoverHostLayer
		get() = layer.currentLayer
	val predecessorSelected: Boolean
		get() = currentLayer != ReaderShellCoverHostLayer.Selected
	val coverSelected: Boolean
		get() = shellCoverSelected

	override val isAttachedToWindow: Boolean = true
	override val currentPresentationBinding: ReaderPresentationBinding
		get() = binding
	override val currentShellCoverGeneration: Long?
		get() = preparedCoverGeneration
	override val shellCoverSelected: Boolean
		get() = currentLayer == ReaderShellCoverHostLayer.Selected
	override val measuredViewportWidth: Int = 1920
	override val measuredViewportHeight: Int = 1200

	override fun prepareOpaqueShellCover(coverGeneration: Long) {
		preparedCoverGeneration = coverGeneration
		layer.prepareCoverBehindPredecessor()
	}

	override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) {
		if (preparedCoverGeneration != coverGeneration) return
		preparedCoverGeneration = null
		layer.hidePreparedCover()
	}

	override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) {
		if (preparedCoverGeneration != coverGeneration) return
		preparedCoverGeneration = null
		completePreparationCount += 1
	}

	override fun registerShellCoverDrawListener(
		onDraw: () -> Unit
	): ReaderPresentationDrawRegistration = FakeDrawRegistration(onDraw).also(registrations::add)

	override fun postShellCoverAnimationFrame(onFrame: () -> Unit) {
		animationFrames += onFrame
	}

	fun runNextAnimationFrame() {
		animationFrames.removeFirstOrNull()?.invoke()
	}

	fun selectAcceptedCover() {
		layer.selectCover(preserveNativePresentationProof = true)
	}
}

private class FakeReaderPresentationCommitHost(
	binding: ReaderPresentationBinding
) : ReaderPresentationCommitHost {
	var attached = true
	var currentBinding: ReaderPresentationBinding? = binding
	var preparedCoverGenerationOverride: Long? = null
	var preparedCoverGeneration: Long? = null
	var viewportWidth = 1920
	var viewportHeight = 1200
	var predecessorSelected = true
	var coverPrepared = false
	var coverSelected = false
	var cancelPreparationCount = 0
	var completePreparationCount = 0
	var rasterInvalidationCount = 0
	var preparationInvalidationCount = 0
	val registrations = mutableListOf<FakeDrawRegistration>()
	val animationFrames = mutableListOf<() -> Unit>()

	override val isAttachedToWindow: Boolean
		get() = attached
	override val currentPresentationBinding: ReaderPresentationBinding?
		get() = currentBinding
	override val currentShellCoverGeneration: Long?
		get() = preparedCoverGeneration
	override val shellCoverSelected: Boolean
		get() = coverSelected
	override val measuredViewportWidth: Int
		get() = viewportWidth
	override val measuredViewportHeight: Int
		get() = viewportHeight

	override fun prepareOpaqueShellCover(coverGeneration: Long) {
		coverPrepared = true
		preparedCoverGeneration = preparedCoverGenerationOverride ?: coverGeneration
	}

	override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) {
		if (preparedCoverGeneration != coverGeneration) return
		cancelPreparationCount += 1
		coverPrepared = false
		preparedCoverGeneration = null
	}

	override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) {
		if (preparedCoverGeneration != coverGeneration) return
		completePreparationCount += 1
		coverPrepared = false
		preparedCoverGeneration = null
	}

	override fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration =
		FakeDrawRegistration(onDraw).also(registrations::add)

	override fun postShellCoverAnimationFrame(onFrame: () -> Unit) {
		animationFrames += onFrame
	}

	fun runNextAnimationFrame() {
		animationFrames.removeFirstOrNull()?.invoke()
	}

	fun runAllAnimationFrames() {
		while (animationFrames.isNotEmpty()) runNextAnimationFrame()
	}
}

private class FakeDrawRegistration(
	private val onDraw: () -> Unit
) : ReaderPresentationDrawRegistration {
	var unregisterCount = 0
		private set

	override fun unregister() {
		unregisterCount += 1
	}

	fun draw() {
		onDraw()
	}
}

private fun paige.navic.reader.ReaderPresentationState.authorityFrameProof():
	ReaderNativePagePresentationProof? = when (val current = authority) {
	is ReaderPresentationAuthority.ShellCoverCommitPending ->
		(current.retainedFrame as? paige.navic.reader.ReaderShellCoverRetainedFrame.NativePage)
			?.frame
			?.proof
	else -> null
}
