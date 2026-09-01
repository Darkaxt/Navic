package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

private fun hostSource(): String {
	var current: File? =
		File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate KomikkuReaderNativeFrameHost.android.kt")
}
