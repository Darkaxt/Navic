package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.publicationIdentity
import paige.navic.reader.readerPresentationDecision

class ReaderPresentationAuthoritativeHostTest {
	@Test
	fun enclosingCachedComposeDecisionCannotOverwriteAcceptedStartupReceiptTruth() {
		val binding = paige.navic.reader.ReaderPresentationBinding(
			foliateSessionId = "authoritative-host-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			preparationGeneration = 4L
		)
		val controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 7L,
				shellCoverVisible = true,
				presentation = ReaderPresentationState(nextTokenValue = 11L)
			)
		)
		val cachedDecision = readerPresentationDecision(controller.state.presentation)
		val event = ReaderPresentationEvent.PublicationOpened(binding)
		val receipt = assertNotNull(
			controller.onPresentationEvent(event).presentationReceipt
		)
		val exactDecision = readerPresentationDecision(receipt.postState)
		val reporter = ReaderPresentationBindingReporter()
		reporter.reset(
			expectedReaderSessionGeneration = 7L,
			minimumComposeVersion = controller.presentationVersion,
			initialPresentationState = controller.state.presentation,
			initialShellCoverVisible = controller.state.shellCoverVisible
		)
		assertTrue(reporter.bindPublication(binding))
		assertTrue(
			reporter.consumeReceipt(
				reporter.captureEpoch(),
				event,
				receipt
			)
		)
		val host = RecordingPresentationCommitHost()
		val bridge = ReaderPresentationHostBridge(host) { null }

		host.applyPresentationDecision(exactDecision)
		bridge.update(cachedDecision)

		assertEquals(exactDecision, host.appliedDecision)
	}

	@Test
	fun composeVersionsAreMonotonicAgainstExactReceiptAuthority() {
		val binding = paige.navic.reader.ReaderPresentationBinding(
			foliateSessionId = "compose-version-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			preparationGeneration = 4L
		)
		val event = ReaderPresentationEvent.PublicationOpened(binding)
		val controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 8L,
				presentation = ReaderPresentationState(nextTokenValue = 11L)
			)
		)
		val receipt = assertNotNull(
			controller.onPresentationEvent(event).presentationReceipt
		)
		val reporter = ReaderPresentationBindingReporter()
		reporter.reset(
			expectedReaderSessionGeneration = 8L,
			minimumComposeVersion = controller.presentationVersion,
			initialPresentationState = controller.state.presentation
		)
		assertTrue(reporter.bindPublication(binding))
		assertTrue(
			reporter.consumeReceipt(
				reporter.captureEpoch(),
				event,
				receipt
			)
		)

		assertFalse(
			reporter.consumeComposeVersion(receipt.version.copy(eventSequence = 0L))
		)
		assertFalse(reporter.consumeComposeVersion(receipt.version))
		assertTrue(
			reporter.consumeComposeVersion(receipt.version.copy(eventSequence = 2L))
		)
	}

	@Test
	fun unboundPublicationAllowsHostRefreshButRejectsComposeDecisionApplication() {
		val binding = paige.navic.reader.ReaderPresentationBinding(
			foliateSessionId = "unbound-host-refresh",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			preparationGeneration = 4L
		)
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 11L,
			publicationIdentity = binding.publicationIdentity,
			eventSequence = 1L
		)
		val reporter = ReaderPresentationBindingReporter()
		reporter.reset(
			expectedReaderSessionGeneration = 11L,
			minimumComposeVersion = version
		)

		assertTrue(
			reporter.admitsComposeHostUpdate(
				version = version,
				publicationChanged = false
			)
		)
		assertFalse(reporter.consumeComposeVersion(version))
	}

	@Test
	fun staleComposeVersionCannotResetASameSessionHostEpoch() {
		val binding = paige.navic.reader.ReaderPresentationBinding(
			foliateSessionId = "compose-reset-fence",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			preparationGeneration = 4L
		)
		val event = ReaderPresentationEvent.PublicationOpened(binding)
		val controller = ReaderController(
			state = ReaderControllerState(
				readerSessionGeneration = 9L,
				presentation = ReaderPresentationState(nextTokenValue = 11L)
			),
			presentationEventSequence = 1L
		)
		val receipt = assertNotNull(
			controller.onPresentationEvent(event).presentationReceipt
		)
		val reporter = ReaderPresentationBindingReporter()
		reporter.reset(
			expectedReaderSessionGeneration = 9L,
			minimumComposeVersion = controller.presentationVersion,
			initialPresentationState = controller.state.presentation
		)
		assertTrue(reporter.bindPublication(binding))
		assertTrue(
			reporter.consumeReceipt(
				reporter.captureEpoch(),
				event,
				receipt
			)
		)
		val staleCompose = receipt.version.copy(eventSequence = 1L)

		assertFalse(
			reporter.admitsComposeHostUpdate(
				version = staleCompose,
				publicationChanged = true
			)
		)
	}

	@Test
	fun replacedHostEpochKeepsItsComposeVersionAsAnApplyOnceFloor() {
		val reporter = ReaderPresentationBindingReporter()
		val version = ReaderPresentationReceiptVersion(
			readerSessionGeneration = 10L,
			publicationIdentity = null,
			eventSequence = 4L
		)

		reporter.reset(
			expectedReaderSessionGeneration = 10L,
			minimumComposeVersion = version
		)

		assertFalse(
			reporter.consumeComposeVersion(version.copy(eventSequence = 3L))
		)
		assertTrue(reporter.consumeComposeVersion(version))
		assertFalse(reporter.consumeComposeVersion(version))
	}

	@Test
	fun composeAndReceiptTruthShareTheCompleteRootApplicationPath() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val root = source
			.substringAfter("private class KomikkuReaderNativeFrameRoot")
			.substringBefore("private class KomikkuReaderNativeViewerContainer")
		val rootUpdate = root
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun handlePresentationEffects(")
		val rootApplication = root
			.substringAfter("private val presentationHostEffectApplier")
			.substringBefore("private var preparedShellCoverGeneration")
		val effects = root
			.substringAfter("fun handlePresentationEffects(")
			.substringBefore("fun setShellCover(")
		val viewer = source.substringAfter("private class KomikkuReaderNativeViewerContainer")
		val receiptAdoption = viewer
			.substringAfter("private val presentationReceiptDispatcher")
			.substringBefore("private var rendererLossEpoch")
		val viewerApplication = viewer
			.substringAfter("fun applyPresentationDecision(")
			.substringBefore("private fun setLocalPageSafetyPolicy(")
		val viewerSynchronization = viewer
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun releaseStalePresentation(")

		assertContains(
			rootUpdate,
			"onAuthoritativeHostEffect = presentationHostEffectApplier::apply"
		)
		assertContains(
			receiptAdoption,
			"onAuthoritativePresentationHostEffect(effect)"
		)
		assertContains(rootApplication, "commitHostModel =")
		assertContains(rootApplication, "presentationDecision = application.decision")
		assertContains(rootApplication, "applyViewerDecision = viewerContainer::applyPresentationDecision")
		assertContains(rootApplication, "shellCoverLayerController.selectCover")
		assertContains(rootApplication, "shellCoverLayerController.coverHidden()")
		assertContains(rootApplication, "applyNativeCoverVisibility = ::updateNativeCoverVisibility")
		assertContains(
			viewerSynchronization,
			"presentationReceiptDispatcher.synchronizeComposeModel("
		)
		assertContains(
			viewerApplication,
			"updateInputSettlementPolicies(rendererLossCancellationIdentity)"
		)
		assertContains(effects, "if (!viewerContainer.matchesAuthoritativePresentationVersion(version)) return")
		assertContains(effects, "val authoritativeDecision = presentationDecision ?: return")
		assertContains(effects, "deliver(effects, authoritativeDecision, onHandled)")
	}

	@Test
	fun replacedHostEpochCancelsOldBridgeTransactionBeforeNewDecisionUpdate() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val update = source
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun handlePresentationEffects(")

		assertContains(
			update,
			"ReaderPresentationEpochPreparation.Replaced -> " +
				"presentationHostBridge.onHostDetached()"
		)
	}

	private class RecordingPresentationCommitHost : ReaderPresentationCommitHost {
		var appliedDecision: ReaderPresentationDecision? = null
			private set

		override val isAttachedToWindow: Boolean = true
		override val currentPresentationBinding = null
		override val currentShellCoverGeneration: Long? = null
		override val shellCoverSelected: Boolean = false
		override val measuredViewportWidth: Int = 1200
		override val measuredViewportHeight: Int = 800

		fun applyPresentationDecision(decision: ReaderPresentationDecision) {
			appliedDecision = decision
		}

		override fun prepareOpaqueShellCover(coverGeneration: Long) = Unit
		override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
		override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
		override fun registerShellCoverDrawListener(
			onDraw: () -> Unit
		): ReaderPresentationDrawRegistration = ReaderPresentationDrawRegistration {}
		override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
	}
}
