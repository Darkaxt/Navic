package paige.navic.ui.screens.reader

import java.io.File
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

		handler.deliver(listOf(pending, pending), decision, acknowledge)
		assertEquals(1, releaseCount)
		assertEquals(0, acknowledgementCount)
		assertEquals(listOf(pending), queue.pendingEffects())

		releaseSucceeds = true
		handler.deliver(queue.pendingEffects(), decision, acknowledge)
		assertEquals(2, releaseCount)
		assertEquals(1, acknowledgementCount)
		assertTrue(queue.pendingEffects().isEmpty())

		handler.deliver(listOf(pending, pending), decision, acknowledge)
		assertEquals(2, releaseCount)
		assertEquals(1, acknowledgementCount)
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
		assertContains(host, "releaseStalePresentationDeck(effect.binding)")
		assertContains(release, "generationOwners[textureGeneration] ?: return true")
		assertContains(release, "owner.profile.rasterGeneration != rasterGeneration")
		assertContains(release, "activeDeckGenerationId == textureGeneration")
		assertContains(release, "pendingDeckGenerationId == textureGeneration")
		assertContains(release, "releaseRendererOwnedGeneration(textureGeneration)")
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
