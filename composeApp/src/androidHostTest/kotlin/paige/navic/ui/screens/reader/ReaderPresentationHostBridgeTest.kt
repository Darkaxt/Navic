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

		fixture.bridge.update(fixture.pendingDecision)
		fixture.host.registrations.single().draw()
		fixture.host.runAllAnimationFrames()
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
				decision = requested.controller.state.presentationDecision
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
				decision = stale.controller.state.presentationDecision
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
				decision = committed.controller.state.presentationDecision
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
	val pendingDecision = readerPresentationReduce(
		nativeState,
		ReaderPresentationEvent.ShellCoverRequested(coverGeneration = 22L)
	).decision
	val transition = assertIs<ReaderRequiredTransition.CommitShellCover>(
		pendingDecision.requiredTransition
	)
	val host = FakeReaderPresentationCommitHost(binding)
	val events = mutableListOf<ReaderPresentationEvent>()
	val bridge = ReaderPresentationHostBridge(host, events::add)

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
