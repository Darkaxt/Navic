package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationPhase
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlFoliateControllerSourceTest {
	private val controllerFile =
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt")
	private val hostFile =
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt")
	private val readerRootFile =
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt")
	private val recoveryFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageDeckRecoveryCoordinator.android.kt"
	)
	private val recoveredBuildOperationFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageRecoveredDeckBuildOperation.android.kt"
	)
	private val rasterPreparationFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageRasterPreparationController.android.kt"
	)
	private val bundleSourceFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageTurnBundleSource.android.kt"
	)
	private val inputSettlementHostControllerFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageInputSettlementHostController.android.kt"
	)
	private val tapTurnControllerFacadeFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageTapTurnControllerFacade.android.kt"
	)
	private val visualHandoffFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderWebViewVisualHandoff.android.kt"
	)
	private val inlineRasterShieldFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageInlineRasterShield.android.kt"
	)
	private val diagnosticFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPageDiagnostic.android.kt"
	)
	private val referenceViewFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"ReaderPlayLikeCurlReferenceView.android.kt"
	)
	private val readerAssetRoot = File("src/androidMain/assets/reader")

	@Test
	fun productionControllerUsesFoliateRastersAndImportedSurface() {
		assertTrue(controllerFile.isFile, "Production PlayLikeCurl controller must exist")
		val source = controllerFile.readText()

		assertContains(source, "PageSurfaceView")
		assertContains(source, "ReaderPlayLikeCurlFoliateRasterLoader")
		assertContains(source, "pageTurnRasterPreparationPlan")
		assertContains(source, "ReaderPlayLikeCurlRasterAdapter")
		assertContains(source, "qaMissTargetOrdinalProvider = { currentOrdinal }")
		assertContains(source, "acquisitionInterceptor = loader::consumeQaMiss")
		assertContains(source, "readerPlayLikeCurlLibraryDeck")
		assertContains(source, "put(\"type\", \"goToVisualPage\")")
		assertFalse(source.contains("ReaderPlayLikeCurlAssetBitmapSource"))
		assertFalse(source.contains("ReaderPlayLikeCurlDiagnosticBitmapSource"))
	}

	@Test
	fun protectedLandscapePhysicalSourcesRemainRepairEligible() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()
		val preparation = rasterPreparationFile.readText()

		assertContains(controller, "readerPlayLikeCurlProtectedSourcePageIndices(profile, logicalOrdinals)")
		assertContains(controller, "onProtectedRasterSourcePageIndicesChanged(sourcePageIndices)")
		assertContains(host, "onProtectedRasterSourcePageIndicesChanged =")
		assertContains(
			host,
			"pageRasterPreparationController.onProtectedRasterSourcePageIndicesChanged(it)"
		)
		assertContains(preparation, "private fun eligibleRasterRepairPageIndices()")
		assertContains(preparation, "protectedSourcePageIndices = protectedRasterSourcePageIndices")
		assertContains(preparation, "val repairPages = eligibleRasterRepairPageIndices()")
	}

	@Test
	fun productionAdaptersShareTheSurfaceDerivedResidencyBudget() {
		val controller = controllerFile.readText()
		val reference = referenceViewFile.readText()

		assertContains(controller, "private const val MAX_RASTER_ADAPTER_OWNERS = 2")
		assertContains(controller, "private val rasterResidencyBudget =")
		assertContains(controller, "surfaceView.deckLeaseLimit *")
		assertContains(controller, "ReaderPageMaximumProtectedRasterEntriesPerLease")
		assertContains(controller, "ReaderPlayLikeCurlAdapterOwnerPool")
		assertContains(controller, "ownerLimit = MAX_RASTER_ADAPTER_OWNERS")
		assertContains(controller, "private fun createRasterAdapterOrDefer(")
		assertContains(controller, "private fun retireRasterAdapter(")
		assertContains(controller, "adapter.closeAndJoin()")
		assertContains(controller, ".isDrained()")
		assertContains(controller, "rendererDeckLeaseLimit = surfaceView.deckLeaseLimit")
		assertContains(controller, "residencyBudget = rasterResidencyBudget")
		assertContains(controller, "onCapacityAvailable = ::signalRasterCapacityAvailable")
		assertContains(controller, "private suspend fun <T> awaitRasterPreparation(")
		assertContains(controller, "catch (cancelled: CancellationException)")
		assertContains(controller, "Result.failure(failure)")
		assertEquals(
			2,
			Regex("awaitRasterPreparation\\(preparation\\)").findAll(controller).count()
		)
		assertFalse(controller.contains("val deck = preparation.await()"))
		assertContains(reference, "rendererDeckLeaseLimit = deckLeaseLimit")
		assertContains(reference, "catch (cancelled: CancellationException)")
		assertContains(reference, "Reference raster preparation failed")
		assertContains(reference, "failureClass=")
		assertContains(reference, "registerMainTerminalExecutor")
		assertContains(reference, "Looper.myLooper() == Looper.getMainLooper()")
		assertEquals(
			2,
			Regex("rendererDeckLeaseLimit\\s*=").findAll(controller + reference).count(),
			"Every production adapter must receive the enforcing surface lease limit."
		)
	}

	@Test
	fun productionControllerTracesEveryInteractionReadinessBoundary() {
		val source = controllerFile.readText()

		assertContains(source, "logActivationState(")
		assertContains(source, "\"enabled\"")
		assertContains(source, "\"host-attached\"")
		assertContains(source, "\"capabilities-available\"")
		assertContains(source, "\"preparation-ready\"")
		assertContains(source, "\"deck-submitted\"")
		assertContains(source, "\"deck-prepared\"")
		assertContains(source, "\"refresh-gated\"")
	}

	@Test
	fun productionControllerTracesRasterDeckLoadingProgressAndLatency() {
		val source = controllerFile.readText()
		val prepare = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("override fun isCurrentRepairWindow(")

		assertContains(prepare, "\"deck-load-started\"")
		assertContains(prepare, "deck-load-progress")
		assertContains(prepare, "\"deck-load-completed\"")
		assertContains(prepare, "\"deck-load-failed\"")
		assertContains(prepare, "pageIndices.joinToString")
		assertContains(prepare, "elapsedMillis")
		assertContains(prepare, "onProgress =")
	}

	@Test
	fun productionControllerTracesGestureOwnershipAndExactSettlementWithoutMoveSpam() {
		val controllerSource = controllerFile.readText()
		val hostSource = hostFile.readText()

		assertContains(hostSource, "emitGestureDiagnostic(")
		assertContains(hostSource, "ReaderPageDiagnostic.gesture(")
		assertContains(controllerSource, "PlayLikeCurl settlement started")
		assertContains(controllerSource, "PlayLikeCurl settlement completed")
		assertContains(controllerSource, "PlayLikeCurl settlement cancelled")
		assertContains(controllerSource, "PlayLikeCurl exact page dispatched")
		assertFalse(hostSource.contains("Reader PlayLikeCurl gesture move"))
	}

	@Test
	fun liveRelocationUsesOneInjectedForegroundOwnerFromReservationThroughDispatch() {
		val source = controllerFile.readText()
		val constructor = source
			.substringAfter("internal class ReaderPlayLikeCurlFoliateController(")
			.substringBefore(") : ReaderPageTapTurnPort")
		val gestureCoordinator = source
			.substringAfter("private val relocationGestureCoordinator =")
			.substringBefore("private val relocationLiveDispatchCoordinator")
		val liveDispatch = source
			.substringAfter("private val relocationLiveDispatchCoordinator")
			.substringBefore("private val relocationVisualHandoffCoordinator")
		val settlement = source
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")

		assertContains(
			constructor,
			"private val foregroundWebViewOwnership: ReaderForegroundWebViewOwnership ="
		)
		assertContains(constructor, "ReaderForegroundWebViewOwnership()")
		assertContains(gestureCoordinator, "foregroundWebViewOwnership = foregroundWebViewOwnership")
		assertContains(liveDispatch, "foregroundWebViewOwnership = foregroundWebViewOwnership")
		assertContains(settlement, "dispatch = ::transferAndDispatchRelocation")
	}

	@Test
	fun hostOwnsOneForegroundWebViewCoordinatorAcrossPreparationAndLiveDispatch() {
		val host = hostFile.readText()
		val viewer = host
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private fun View.findDescendantWebView")
		val playLikeCurlWiring = viewer
			.substringAfter("private val playLikeCurlController: ReaderPlayLikeCurlFoliateController =")
			.substringBefore("private val ownershipProbe")
		val preparationWiring = viewer
			.substringAfter("private val pageRasterPreparationController: ReaderPageRasterPreparationController =")
			.substringBefore("private val pageRasterHostEventController")
		val passiveAvailability = viewer
			.substringAfter("private fun onForegroundWebViewPassiveAvailable()")
			.substringBefore("private fun requestPageTurnPrewarmWhenReady()")
		val prewarm = viewer
			.substringAfter("private fun requestPageTurnPrewarmWhenReady()")
			.substringBefore("private fun pageTurnPrewarmLayoutSignature")
		val teardown = viewer.substringAfter("private fun teardownTask4Resources()")

		assertEquals(
			1,
			Regex("ReaderForegroundWebViewOwnership\\(").findAll(viewer).count(),
			"The host must create exactly one foreground WebView owner."
		)
		assertContains(
			viewer,
			"onPassiveAvailable = ::onForegroundWebViewPassiveAvailable"
		)
		assertContains(
			passiveAvailability,
			"pageRasterPreparationController.onForegroundWebViewPassiveAvailable()"
		)
		assertEquals(
			2,
			Regex("!foregroundWebViewOwnership\\.canAcquirePassive\\(\\)")
				.findAll(passiveAvailability)
				.count(),
			"The callback must recheck passive ownership after consuming a typed deferral."
		)
		assertTrue(
			passiveAvailability.indexOf(
				"pageRasterPreparationController.onForegroundWebViewPassiveAvailable()"
			) < passiveAvailability.lastIndexOf(
				"!foregroundWebViewOwnership.canAcquirePassive()"
			),
			"A deferral may acquire a passive lease before host prewarm scheduling."
		)
		assertTrue(
			passiveAvailability.lastIndexOf(
				"!foregroundWebViewOwnership.canAcquirePassive()"
			) < passiveAvailability.indexOf("requestPageTurnPrewarmWhenReady()"),
			"The host must not schedule prewarm once deferral recovery owns foreground."
		)
		assertContains(playLikeCurlWiring, "foregroundWebViewOwnership = foregroundWebViewOwnership")
		assertContains(preparationWiring, "foregroundWebViewOwnership = foregroundWebViewOwnership")
		assertTrue(
			viewer.indexOf("private val foregroundWebViewOwnership") <
				viewer.indexOf("private val playLikeCurlController"),
			"The shared owner must exist before the live controller."
		)
		assertTrue(
			viewer.indexOf("private val foregroundWebViewOwnership") <
				viewer.indexOf("private val pageRasterPreparationController"),
			"The shared owner must exist before the preparation controller."
		)
		assertEquals(
			5,
			Regex("!foregroundWebViewOwnership\\.canAcquirePassive\\(\\)")
				.findAll(viewer)
				.count(),
			"Passive availability must guard destination-deck resumption and prewarm recovery."
		)
		assertEquals(
			2,
			Regex("!foregroundWebViewOwnership\\.canAcquirePassive\\(\\)")
				.findAll(prewarm)
				.count(),
			"Prewarm must gate both admission and an already-installed listener."
		)
		assertTrue(
			prewarm.indexOf("!foregroundWebViewOwnership.canAcquirePassive()") <
				prewarm.indexOf("pageTurnPrewarmStableFrameCount"),
			"Stable frames must not grant foreground ownership."
		)
		assertContains(teardown, "teardown.invokeOnCompletion")
		assertContains(teardown, "runCatching(foregroundWebViewOwnership::close)")
		assertContains(teardown, "typedFailure.addSuppressed(ownershipCloseFailure)")
		assertTrue(
			teardown.indexOf("val teardown = pageRasterPreparationController.destroy()") <
				teardown.indexOf("runCatching(foregroundWebViewOwnership::close)"),
			"Preparation fencing and PlayLikeCurl draining must finish before owner close."
		)
	}

	@Test
	fun committedRelocationCanQueueBehindAnInFlightHeadWithoutFailingOwnership() {
		val source = controllerFile.readText()
		val transfer = source
			.substringAfter("private fun transferAndDispatchRelocation(")
			.substringBefore("private fun dispatchRelocation(")

		assertContains(transfer, "relocationLiveDispatchCoordinator.transfer(request, claim)")
		assertContains(transfer, "dispatchNextRelocation()")
		assertFalse(
			transfer.contains("check(dispatchNextRelocation())"),
			"A later committed relocation may validly wait behind the in-flight queue head."
		)
	}

	@Test
	fun restorationGatedDispatchStoresGenerationBeforeResolvingTheAttachedWebView() {
		val source = controllerFile.readText()
		val coordinator = source
			.substringAfter("internal class ReaderPageRelocationLiveDispatchCoordinator(")
			.substringBefore("internal class ReaderPlayLikeCurlFoliateController(")
		val exact = source
			.substringAfter("private fun dispatchExactVisualPage(")
			.substringBefore("private fun rejectDispatchedRelocation(")

		assertContains(coordinator, "foregroundWebViewOwnership.whenLiveReady(entry.claim)")
		assertContains(coordinator, "isDispatchCurrent(request)")
		assertContains(coordinator, "foregroundWebViewOwnership.beginLiveMutation(claim)")
		assertContains(coordinator, "mutationGenerations[token] = generation")
		assertTrue(
			coordinator.indexOf("foregroundWebViewOwnership.beginLiveMutation(claim)") <
				coordinator.indexOf("dispatchExact(request, generation)")
		)
		assertContains(exact, "webViewProvider()?.takeIf { it.isAttachedToWindow }")
		assertContains(
			exact,
			"ReaderPageRelocationDiagnosticRejectionReason.WebViewUnavailable"
		)
		assertTrue(
			exact.indexOf("webViewProvider()?.takeIf { it.isAttachedToWindow }") <
				exact.indexOf("webView.evaluateJavascript(")
		)
		assertContains(exact, "put(\"settleForegroundMutationGeneration\", generation.value)")
		assertContains(exact, "ReaderPageRelocationDiagnosticState.Dispatched")
		assertContains(exact, "relocationDispatchTimeout.arm(request)")
		assertTrue(
			exact.indexOf("webView.evaluateJavascript(") <
				exact.indexOf("ReaderPageRelocationDiagnosticState.Dispatched")
		)
		assertTrue(
			exact.indexOf("ReaderPageRelocationDiagnosticState.Dispatched") <
				exact.indexOf("relocationDispatchTimeout.arm(request)")
		)
	}

	@Test
	fun exactRelocationClaimsAreCurrentForAckValidationReplacementAndLifecycleDrain() {
		val source = controllerFile.readText()
		val origin = source
			.substringAfter("fun visualLocationOrigin(")
			.substringBefore("fun synchronizeVisualPageIndex(")
		val validation = source
			.substringAfter("private fun livePresentationValidationIsCurrent(")
			.substringBefore("private fun relocationVisualState()")
		val replacement = source
			.substringAfter("private fun replaceRelocationDiagnosticIdentity(")
			.substringBefore("private fun completeRelocationVisualHandoff(")
		val cancellation = source
			.substringAfter("private fun cancelRelocationsWithDiagnostics(")
			.substringBefore("private fun drainRelocationOwnership(")
		val completed = source
			.substringAfter("private fun completeRelocationVisualHandoff(")
			.substringBefore("private fun releaseTerminalContentFailure(")

		assertContains(origin, "relocationLiveDispatchCoordinator.isCurrent(")
		assertContains(
			validation,
			"relocationLiveDispatchCoordinator.isCurrent("
		)
		assertContains(validation, "foregroundMutationGeneration")
		assertContains(replacement, "relocationLiveDispatchCoordinator.replace(original, replacement)")
		assertContains(cancellation, "relocationLiveDispatchCoordinator.releaseAll()")
		assertContains(source, "relocationLiveDispatchCoordinator.fail(")
		assertContains(
			completed,
			"relocationLiveDispatchCoordinator.complete(request)"
		)
		assertFalse(completed.contains("foregroundWebViewOwnership.releaseLive("))
	}

	@Test
	fun relocationBridgePreservesCompleteTokenSessionAndGenerationIdentity() {
		val controller = controllerFile.readText()
		val runtime = readerAssetRoot.resolve("navic-reader.js").readText()
		val turns = readerAssetRoot.resolve("navic-reader-page-turns.js").readText()
		val location = readerAssetRoot.resolve("navic-reader-location.js").readText()
		val core = readerAssetRoot.resolve("navic-reader-bridge-core.js").readText()

		assertContains(controller, "private fun dispatchNextRelocation()")
		assertContains(controller, "put(\"settleToken\", request.token.value)")
		assertContains(controller, "put(\"settleGestureId\", request.gestureId)")
		assertContains(controller, "put(\"settleSessionId\", request.foliateSessionId)")
		assertContains(controller, "put(\"settleRasterGeneration\", request.rasterGeneration)")
		assertContains(controller, "put(\"settleTextureGeneration\", request.textureGeneration)")
		assertContains(runtime, "return this.goToVisualPage(command)")
		assertContains(runtime, "foliateSessionId = ''")
		assertContains(turns, "pendingExactPageTurnSettlements")
		assertContains(turns, "activeExactPageTurnSettlementToken")
		assertContains(turns, "pending.paginationProfile !== this.paginationProfile")
		assertContains(location, "foliateSessionId: this.foliateSessionId")
		assertContains(location, "pageTurnSettleToken: settlement?.token")
		assertContains(location, "pageTurnSettleSessionId: settlement?.foliateSessionId")
		assertContains(location, "pageTurnSettleRasterGeneration: settlement?.rasterGeneration")
		assertContains(location, "pageTurnSettleTextureGeneration: settlement?.textureGeneration")
		assertContains(location, "const delivered = post(message)")
		assertTrue(
			location.indexOf("const delivered = post(message)") <
				location.indexOf("this.consumeNativePageTurnSettlement(settlement.token)")
		)
		listOf(
			"message?.foliateSessionId || ''",
			"message?.pageTurnSettleToken || ''",
			"message?.pageTurnSettleSessionId || ''",
			"message?.pageTurnSettleRasterGeneration",
			"message?.pageTurnSettleTextureGeneration"
		).forEach { identity -> assertContains(core, identity) }
	}

	@Test
	fun visualLocationOriginUsesQueueIdentityRatherThanReasonText() {
		val host = hostFile.readText()
		val controller = controllerFile.readText()
		val origin = controller
			.substringAfter("fun visualLocationOrigin(")
			.substringBefore("fun synchronizeVisualPageIndex(")
		val synchronization = controller
			.substringAfter("fun synchronizeVisualPageIndex(")
			.substringBefore("private fun completeAcknowledgedRelocation(")
		val pendingExact = synchronization
			.substringAfter("ReaderPageVisualLocationOrigin.PendingExactPageTurn ->")
			.substringBefore("ReaderPageVisualLocationOrigin.StaleAcknowledgement")
		val stale = synchronization
			.substringAfter("ReaderPageVisualLocationOrigin.StaleAcknowledgement ->")
			.substringBefore("ReaderPageVisualLocationOrigin.External ->")
		val hostSynchronization = host
			.substringAfterLast("fun setPageTurnVisualLocation(")
			.substringBefore("fun setShellCoverVisible(")

		assertFalse(host.contains("readerPageVisualLocationOrigin(reason)"))
		assertContains(host, "pageTurnSettlementAck == acknowledgement")
		assertContains(host, "ReaderPageVisualLocationOrigin.External")
		assertContains(controller, "fun visualLocationOrigin(")
		assertContains(origin, "relocationQueue.matchesDispatchedHead(")
		assertContains(origin, "readerPageVisualLocationOrigin(")
		assertContains(origin, "foliateSessionRelocationPending =")
		assertContains(origin, "acknowledgementPresent = acknowledgement != null")
		assertContains(origin, "relocationInFlight = relocationQueue.hasInFlightHead()")
		assertContains(
			controller,
			"ReaderPageVisualLocationOrigin.PendingExactPageTurn"
		)
		assertContains(controller, "relocationQueue.occupiedCount() == 0")
		assertContains(
			controller,
			"ReaderPageVisualLocationOrigin.StaleAcknowledgement"
		)
		assertFalse(origin.contains("page-turn:exact"))
		assertContains(synchronization, "val origin = visualLocationOrigin(")
		assertContains(
			synchronization,
			"if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement)"
		)
		assertFalse(pendingExact.contains("invalidate("))
		assertFalse(stale.contains("currentOrdinal ="))
		assertFalse(stale.contains("invalidate("))
		assertContains(
			hostSynchronization,
			"if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement)"
		)
		assertTrue(
			hostSynchronization.indexOf(
				"if (origin != ReaderPageVisualLocationOrigin.StaleAcknowledgement)"
			) < hostSynchronization.indexOf(
				"pageRasterPreparationController.synchronizeVisualPageIndex("
			),
			"A stale exact acknowledgement must not move raster-preparation authority."
		)
	}

	@Test
	fun exactCrossSpineRelocationRetainsRendererSectionIdentityDuringProfileFallback() {
		val pagination = File(readerAssetRoot, "navic-reader-pagination.js").readText()
		val sectionPosition = pagination
			.substringAfter("function reflowableSectionPagePosition(")
			.substringBefore("function reflowableLocationPagePosition(")
		val profilePosition = pagination
			.substringAfter("function readerPaginationProfilePosition(")
			.substringBefore("function reflowableStableBookPageModel(")
		val pagePosition = pagination
			.substringAfter("function reflowablePagePosition(detail)")
			.substringBefore("function readerPagePosition(detail)")
		val chapterPosition = pagination
			.substringAfter("function chapterPagePosition(detail, fallback = null)")
			.substringBefore("function detailSectionKey(detail)")

		assertContains(sectionPosition, "detail = this.lastRelocateDetail")
		assertContains(
			sectionPosition,
			"detail?.section?.current ?? detail?.index"
		)
		assertContains(sectionPosition, "spineIndex:")
		assertContains(sectionPosition, "chapterPageIndex:")
		assertContains(sectionPosition, "chapterPageCount:")
		assertContains(
			profilePosition,
			"sectionPosition = this.reflowableSectionPagePosition(detail)"
		)
		assertContains(pagePosition, "this.reflowableSectionPagePosition(detail)")
		assertContains(pagePosition, "const pagePosition =")
		assertContains(pagePosition, "spineIndex: sectionPosition.spineIndex")
		assertContains(
			pagePosition,
			"chapterPageIndex: sectionPosition.chapterPageIndex"
		)
		assertContains(
			pagePosition,
			"chapterPageCount: sectionPosition.chapterPageCount"
		)
		assertContains(chapterPosition, "this.reflowableSectionPagePosition(detail)")
	}

	@Test
	fun exactAcknowledgementStartsVisualHandoffWithAuthoritativeWebViewLocation() {
		val source = controllerFile.readText()
		val synchronize = source
			.substringAfter("fun synchronizeVisualPageIndex(")
			.substringBefore("private fun drainRelocationOwnership(")
		val exact = synchronize
			.substringAfter("ReaderPageVisualLocationOrigin.ExactPageTurn -> {")
			.substringBefore("ReaderPageVisualLocationOrigin.StaleAcknowledgement")

		assertContains(source, "private var currentOrdinal = 0")
		assertContains(source, "private var currentWebViewOrdinal: Int? = null")
		assertContains(synchronize, "currentWebViewOrdinal = normalized")
		assertContains(exact, "relocationQueue.acknowledge(")
		assertContains(exact, "relocationVisualHandoffCoordinator.onAcknowledged(acknowledged)")
		assertTrue(
			exact.indexOf("relocationQueue.acknowledge(") <
				exact.indexOf("relocationVisualHandoffCoordinator.onAcknowledged(acknowledged)")
		)
		assertFalse(
			exact.contains("currentOrdinal = normalized"),
			"A historical WebView acknowledgement must not rewind the speculative GL ordinal."
		)
		assertFalse(
			exact.contains("hideSurface()"),
			"Acknowledgement must retain the GL shield until visual-state handoff completes."
		)
	}

	@Test
	fun replacementRelocationRetainsOriginalFaultAsRecovery() {
		val source = controllerFile.readText()
		val replacement = source
			.substringAfter("private fun replaceRelocationDiagnosticIdentity(")
			.substringBefore("private fun completeRelocationVisualHandoff(")

		assertContains(
			source,
			"ReaderPageRelocationQaFaultCorrelationStore()"
		)
		assertContains(replacement, "relocationQaFaultCorrelations.transfer(")
		assertContains(replacement, "originalToken = original.token.value")
		assertContains(replacement, "replacementToken = replacement.token.value")
	}

	@Test
	fun dispatchedRelocationHasBoundedIdentitySafeAcknowledgementRecovery() {
		val source = controllerFile.readText()
		val dispatch = source
			.substringAfter("private fun dispatchExactVisualPage(")
			.substringBefore("private fun releaseGeneration(")
		val exactAcknowledgement = source
			.substringAfter("ReaderPageVisualLocationOrigin.ExactPageTurn -> {")
			.substringBefore("ReaderPageVisualLocationOrigin.PendingExactPageTurn")
		val rejection = dispatch
			.substringAfter("private fun rejectDispatchedRelocation(")
		val cancellation = source
			.substringAfter("private fun cancelRelocationsWithDiagnostics()")
			.substringBefore("private fun drainRelocationOwnership(")
		val metrics = source
			.substringAfter("fun applicationOwnershipMetrics()")
			.substringBefore("override fun requestOwnershipSnapshot(")

		assertContains(source, "ReaderPageRelocationDispatchTimeout(")
		assertContains(dispatch, "ReaderPageRelocationDiagnosticState.Dispatched")
		assertContains(dispatch, "relocationDispatchTimeout.arm(request)")
		assertTrue(
			dispatch.indexOf("ReaderPageRelocationDiagnosticState.Dispatched") <
				dispatch.indexOf("relocationDispatchTimeout.arm(request)")
		)
		assertContains(exactAcknowledgement, "relocationDispatchTimeout.cancel(acknowledged)")
		assertTrue(
			exactAcknowledgement.indexOf("relocationQueue.acknowledge(") <
				exactAcknowledgement.indexOf("relocationDispatchTimeout.cancel(acknowledged)")
		)
		assertContains(rejection, "relocationQueue.matchesDispatchedHead(")
		assertContains(rejection, "token = request.token.value")
		assertContains(rejection, "rasterGeneration = request.rasterGeneration")
		assertContains(rejection, "textureGeneration = request.textureGeneration")
		assertContains(rejection, "foliateSessionId = request.foliateSessionId")
		assertContains(rejection, "destinationOrdinal = request.destinationOrdinal")
		assertContains(rejection, "readerPageRelocationDispatchRecoveryOrdinal(")
		assertContains(rejection, "currentFoliateSessionId = currentFoliateSessionId")
		assertContains(rejection, "currentWebViewOrdinal = currentWebViewOrdinal")
		assertContains(rejection, "cancelActiveGesture(")
		assertContains(
			rejection,
			"ReaderPageLifecycleCancellationReason.RasterProfileInvalidated"
		)
		assertTrue(
			rejection.indexOf("cancelActiveGesture(") <
				rejection.indexOf("currentOrdinal = readerPageRelocationDispatchRecoveryOrdinal(")
		)
		assertContains(rejection, "relocationRejectionReason = reason")
		assertContains(
			rejection,
			"onOwnershipDiagnosticRequested(ReaderPageOwnershipPhase.SteadyState)"
		)
		assertTrue(
			rejection.indexOf("invalidate(") <
				rejection.indexOf(
					"onOwnershipDiagnosticRequested(ReaderPageOwnershipPhase.SteadyState)"
				)
		)
		assertContains(
			source,
			"ReaderPageRelocationDiagnosticRejectionReason.AcknowledgementTimeout"
		)
		assertContains(cancellation, "relocationDispatchTimeout.cancelAll()")
		assertTrue(
			cancellation.indexOf("relocationDispatchTimeout.cancelAll()") <
				cancellation.indexOf("relocationGestureCoordinator.cancelAll()")
		)
		assertContains(metrics, "relocationDispatchTimeout.pendingCallbackCount()")
		assertContains(metrics, "relocationDispatchTimeout.pendingCallbackLimit")
	}

	@Test
	fun productionRoutesTypedVisualHandoffRetriesAndCancelsBeforeQueueDrain() {
		val source = controllerFile.readText()
		val host = hostFile.readText()
		val attach = source
			.substringAfter("fun onHostAttached()")
			.substringBefore("fun onHostSizeChanged()")
		val contentReady = source
			.substringAfter("fun onHostContentReady()")
			.substringBefore("fun onWebViewAttachmentChanged(")
		val webViewAttach = source
			.substringAfter("fun onWebViewAttachmentChanged(")
			.substringBefore("fun onHostResumedChanged(")
		val resumed = source
			.substringAfter("fun onHostResumedChanged(")
			.substringBefore("fun onHostWindowHidden()")
		val preparationReady = source
			.substringAfter("ReaderPagePreparationPhase.Ready -> {")
			.substringBefore("ReaderPagePreparationPhase.Failed -> {")
		val deckPrepared = source
			.substringAfter("override fun onDeckPrepared(")
			.substringBefore("override fun onDeckRejected(")
		val invalidate = source
			.substringAfter("fun invalidate(")
			.substringBefore("fun destroy()")
		val destroyFence = source
			.substringAfter("private val destroyFence = ReaderPageControllerDestroyFence(")
			.substringBefore("fun destroy(): Deferred<Unit>")

		assertContains(attach, "retryRelocationVisualHandoffAttached()")
		assertContains(contentReady, "retryRelocationVisualHandoffAttached()")
		assertContains(webViewAttach, "retryRelocationVisualHandoffAttached()")
		assertContains(resumed, "retryRelocationVisualHandoffResumed()")
		assertContains(
			preparationReady,
			"activeDeckGenerationId?.let(::retryRelocationVisualHandoffForPreparedDeck)"
		)
		assertContains(deckPrepared, "retryRelocationVisualHandoffForPreparedDeck(generationId)")
		assertContains(source, "ReaderPageRelocationVisualRetryEvent.Attached(")
		assertContains(source, "ReaderPageRelocationVisualRetryEvent.Resumed(")
		assertContains(source, "ReaderPageRelocationVisualRetryEvent.Reprepared(")
		assertTrue(
			invalidate.indexOf("relocationVisualHandoffCoordinator.cancelForQueueInvalidation()") <
				invalidate.indexOf("drainRelocationOwnership(")
		)
		assertContains(
			destroyFence,
			"closeVisualHandoff = relocationVisualHandoffCoordinator::close"
		)
		assertTrue(
			destroyFence.indexOf("closeVisualHandoff =") <
				destroyFence.indexOf("cancelRelocations ="),
			"Visual handoff ownership must close before relocation ownership drains."
		)
		assertContains(host, "playLikeCurlController.onHostResumedChanged(true)")
		assertContains(host, "playLikeCurlController.onHostResumedChanged(false)")
	}

	@Test
	fun structuredDiagnosticsStayOnAuthoritativeTransitionOwners() {
		val controller = controllerFile.readText()
		val handoff = visualHandoffFile.readText()
		val recovery = recoveryFile.readText()
		val diagnostic = diagnosticFile.readText()

		assertContains(controller, "onQueued = { request ->")
		assertContains(controller, "onRejected = { request ->")
		assertContains(controller, "onAwaiting = { request ->")
		assertContains(controller, "onCompleted = { request ->")
		assertContains(controller, "if (terminal) relocationDiagnosticStarts.remove(")
		assertContains(controller, "handoffAttemptId = event.handoffAttemptId")
		assertContains(controller, "private val handoffDiagnosticTargets")
		assertContains(
			controller,
			"handoffDiagnosticTargets[event.handoffAttemptId] ="
		)
		assertContains(controller, "target = target")
		assertContains(controller, "ReaderPageRepairDiagnosticState.Completed")
		assertContains(controller, "deckDiagnosticTracker?.prepared(")
		assertContains(controller, "deckDiagnosticTracker?.submitted(")
		assertContains(handoff, "if (firstAttempt) onAwaiting(request)")
		assertContains(recovery, "waiting.diagnosticOperation")
		assertContains(diagnostic, "internal class ReaderPageDeckDiagnosticTracker(")
		assertContains(diagnostic, "handoffAttemptId=${'$'}handoffAttemptId")
		assertContains(diagnostic, "private fun diagnosticDigestPrefix(")
		assertContains(diagnostic, "?: \"invalid\"")
		val recoveryAdmission = controller
			.substringAfter("if (!deckRecoveryCoordinator.accept(result)) {")
			.substringBefore("return@post")
		assertFalse(recoveryAdmission.contains("diagnostics?.repair("))
	}

	@Test
	fun livePresentationValidationBindsExactGenerationProfileAndPreparedRasters() {
		val controller = controllerFile.readText()
		val validation = controller
			.substringAfter("private fun validateLivePresentation(")
			.substringBefore("private fun livePresentationValidationIsCurrent(")

		assertContains(validation, "activeDeckGenerationId != request.textureGeneration")
		assertContains(validation, "generationOwners[request.textureGeneration]")
		assertFalse(
			validation.contains("activePages"),
			"Decoded refill pages must not replace the immutable visible-generation owner."
		)
		assertContains(validation, "profile.rasterGeneration != request.rasterGeneration")
		assertContains(validation, "profile.transitionKind()")
		assertContains(validation, "profile.pageRequest(request.destinationOrdinal).sourcePageIndex")
		assertContains(validation, "profile.pageRequest(request.sourceOrdinal).sourcePageIndex")
		assertContains(validation, "bundleSource.retainedCurrentLayoutSnapshot(")
		assertContains(validation, "expectedGeneration = request.rasterGeneration")
		assertContains(validation, "expectedQuality = profile.quality")
		assertContains(validation, "expectedTarget = expectedTarget")
		assertContains(validation, "expectedSource = expectedSource")
		assertContains(validation, "rendererSurface = surfaceView")
		assertContains(validation, "isStillCurrent = {")
		assertContains(validation, "livePresentationValidationIsCurrent(")
		assertContains(validation, "foregroundMutationGeneration")
		assertContains(validation, "if (retained == null)")
		assertContains(validation, "expectedTarget.release()")
		assertContains(validation, "expectedSource?.release()")
		assertTrue(
			validation.indexOf("generationOwners[request.textureGeneration]") <
				validation.indexOf("bundleSource.retainedCurrentLayoutSnapshot("),
			"Snapshot lookup must be derived from the exact active generation profile."
		)
	}

	@Test
	fun queueHandoffWaitsForCommittedWebViewExposureBeforeCompletion() {
		val source = visualHandoffFile.readText()
		val controller = controllerFile.readText()
		val accepted = source
			.substringAfter("ReaderPageRelocationContentValidationResult.Accepted -> {")
			.substringBefore("ReaderPageRelocationContentValidationResult.ContentRejected")
		val finalization = source
			.substringAfter("private fun finalizePresentation(")
			.substringBefore("private fun complete(")
		val complete = source
			.substringAfter("private fun complete(request: ReaderPageRelocationRequest)")
			.substringBefore("private fun recover(")
		val controllerFinalization = controller
			.substringAfter("private fun finalizeHandoffPresentation(")
			.substringBefore("private fun hideSurfaceBehindInlineRasterShield(")
		val fade = controllerFinalization
			.substringAfter("private fun fadeInlineHandoffShield(")
			.substringBefore("private fun handoffPresentationIsCurrent(")
		val presentationRecovery = controller
			.substringAfter(
				"if (reason == ReaderWebViewVisualHandoffFailure.PresentationFailed) {"
			)
			.substringBefore("\n\t\tcheck(")
		val controllerCompletion = controller
			.substringAfter("private fun completeRelocationVisualHandoff(")
			.substringBefore("private fun releaseTerminalContentFailure(")

		assertContains(source, "FinalizingPresentation")
		assertContains(accepted, "finalizePresentation(request)")
		assertFalse(accepted.contains("queue.completeHandoff("))
		assertContains(finalization, "presentationFinalizer(request)")
		assertContains(finalization, "if (!exposedFrameCommitted")
		assertContains(
			finalization,
			"ReaderWebViewVisualHandoffFailure.PresentationFailed"
		)
		assertContains(finalization, "validationIsCurrent(request)")
		assertContains(finalization, "complete(request)")
		assertTrue(
			finalization.indexOf("validationIsCurrent(request)") <
				finalization.indexOf("complete(request)")
		)
		assertContains(complete, "queue.completeHandoff(request.token.value)")
		assertContains(complete, "val next = queue.commandToDispatch()")
		assertContains(complete, "next?.let(dispatch)")
		assertFalse(complete.contains("hideSurface("))
		assertContains(
			controllerFinalization,
			"relocationLiveDispatchCoordinator.mutationGeneration(request)"
		)
		assertContains(controllerFinalization, "handoffPresentationIsCurrent(")
		assertContains(fade, "inlineRasterShield.fadeOut(")
		assertTrue(
			fade.lastIndexOf("handoffPresentationIsCurrent(") <
				fade.lastIndexOf("inlineRasterShield.dismiss()")
		)
		assertTrue(
			fade.lastIndexOf("inlineRasterShield.dismiss()") <
				fade.lastIndexOf("onFinalized(finalized)")
		)
		assertFalse(
			presentationRecovery.contains("relocationLiveDispatchCoordinator.fail(")
		)
		assertFalse(presentationRecovery.contains("requestLivePresentationRecovery(reason)"))
		assertContains(
			presentationRecovery,
			"retryRelocationVisualHandoffForPreparedDeck(request.textureGeneration)"
		)
		assertContains(
			controllerCompletion,
			"relocationLiveDispatchCoordinator.complete(request)"
		)
	}

	@Test
	fun relocationAdmissionAndCommitUseTheProductionOwnershipCoordinator() {
		val controller = controllerFile.readText()
		val touch = controller
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val tap = controller
			.substringAfter("private fun startTapTurn(")
			.substringBefore("private fun revealSurfaceAfterNextPresentedFrame(")
		val settlement = controller
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val finish = controller
			.substringAfter("private fun finishGesture(")
			.substringBefore("private fun finishActiveGesture(")

		assertContains(touch, "relocationGestureCoordinator.start(")
		assertContains(touch, "rendererAdmission = {")
		assertContains(touch, "surfaceView.onPageTouchEvent(event, gestureId)")
		assertContains(tap, "relocationGestureCoordinator.start(")
		assertContains(tap, "surfaceView.turn(pageChange, gestureId)")
		assertContains(settlement, "relocationGestureCoordinator.commit(")
		assertContains(settlement, "settledSourceTextureGeneration = generationId")
		assertContains(settlement, "promotedTextureGeneration = promotedGeneration")
		assertContains(settlement, "publishCommittedTerminal = {")
		assertContains(settlement, "dispatch = ::transferAndDispatchRelocation")
		assertContains(
			finish,
			"relocationGestureCoordinator.finish(gestureId, outcome, detail)"
		)
	}

	@Test
	fun recoveredDeckSubmissionWaitsForTheActiveGestureTerminal() {
		val controller = controllerFile.readText()
		val touch = controller
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val tap = controller
			.substringAfter("private fun startTapTurn(")
			.substringBefore("private fun revealSurfaceAfterNextPresentedFrame(")
		val submission = controller
			.substringAfter("override fun submitRecoveredDeck(")
			.substringBefore("private fun acceptRecoveredDeckOwnership(")
		val terminal = controller
			.substringAfter("private fun publishGestureTerminal(")
			.substringBefore("private fun scheduleRecoveredDeckSubmissionRetry(")
		val retry = controller
			.substringAfter("private fun scheduleRecoveredDeckSubmissionRetry(")
			.substringBefore("private fun finishActiveGesture(")

		val mutationFenceCheck =
			"settlementMutationFence.blocksExternalDeckMutation(activeGestureId)"

		listOf("drag" to touch, "tap" to tap).forEach { (path, source) ->
			assertContains(source, "activeGestureId = gestureId")
			assertFalse(
				source.contains("deckRecoveryCoordinator.cancelAll()"),
				"$path must retain a valid repaired window until admission is terminal."
			)
		}
		assertContains(submission, mutationFenceCheck)
		assertContains(
			submission,
			"ReaderPageRecoveredDeckSubmissionResult.AwaitingRendererCapacity"
		)
		assertTrue(
			submission.indexOf(mutationFenceCheck) <
				submission.indexOf("surfaceView.submitDeckWithResult("),
			"Recovered ownership must wait instead of entering the settlement pending slot."
		)
		assertContains(terminal, "val activeGestureEnded = activeGestureId == gestureId")
		assertContains(terminal, "if (activeGestureEnded) activeGestureId = null")
		assertContains(
			terminal,
			"if (activeGestureEnded && activeGestureId == null)"
		)
		assertContains(terminal, "scheduleRecoveredDeckSubmissionRetry()")
		assertFalse(terminal.contains("onDeckSubmissionCapacityAvailable()"))
		assertContains(retry, "val posted = mainHandler.post {")
		assertContains(retry, "!$mutationFenceCheck")
		assertContains(retry, "!surfaceView.isSettlementRunning")
		assertContains(retry, "deckRecoveryCoordinator.onDeckSubmissionCapacityAvailable()")
		val rejectedPost = retry.substringAfter("if (!posted)")
		assertContains(rejectedPost, "recoveredDeckSubmissionRetryPosted.set(false)")
		assertContains(
			rejectedPost,
			"deckRecoveryCoordinator.cancelAll()",
			message = "A rejected main-thread retry must release the retained generation."
		)
		assertTrue(
			terminal.indexOf("activeGestureId = null") <
				terminal.indexOf("scheduleRecoveredDeckSubmissionRetry()"),
			"Deferred recovery must be scheduled only after gesture ownership ends."
		)
	}

	@Test
	fun whispersyncClearTakesPriorityWhileNonEmptyReplacementWaitsForGestureIdle() {
		val source = controllerFile.readText()
		val publication = source
			.substringAfter("private fun publishLatestWhispersyncOverlayIfIdle()")
			.substringBefore("private fun clearWhispersyncOverlayIfNeeded(")

		val clearDecision = publication.indexOf("if (receipt == null || targets == null)")
		val interactionGate = publication.indexOf("activeGestureId != null")
		val maskBuild = publication.indexOf("val overlays = mutableListOf")
		assertTrue(
			clearDecision >= 0 && interactionGate > clearDecision && maskBuild > interactionGate,
			"A semantic clear must reach the renderer during a curl, while non-empty mask replacement remains deferred."
		)
		assertContains(publication, "clearWhispersyncOverlayIfNeeded(generationId)")
	}

	@Test
	fun externalDeckMutationsWaitForControllerSettlementReconciliation() {
		val controller = controllerFile.readText()
		val settlementStarted = controller
			.substringAfter("override fun onSettlementStarted(")
			.substringBefore("override fun onSettlementCompleted(")
		val settlementCompleted = controller
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val settlementCancelled = controller
			.substringAfter("override fun onSettlementCancelled(")
			.substringBefore("override fun onRenderFailure(")
		val refresh = controller
			.substringAfter("private fun refreshPreparedDeck(")
			.substringBefore("private fun gateForDecodedWorkingSetRefill(")
		val preparation = controller
			.substringAfter("private fun prepareProfile(")
			.substringBefore("override fun isCurrentRepairWindow(")
		val recoveredRole = controller
			.substringAfter("override fun currentRecoveredDeckRole(")
			.substringBefore("override fun submitRecoveredDeck(")
		val recoveredSubmission = controller
			.substringAfter("override fun submitRecoveredDeck(")
			.substringBefore("private fun acceptRecoveredDeckOwnership(")
		val deferredRefreshCheck = "settlementMutationFence.deferRefreshIfBlocked("
		val relocationFenceCheck = "isAwaitingAuthoritativeRelocation()"

		assertContains(controller, "private val settlementMutationFence")
		assertContains(
			controller,
			"relocationQueue.hasInFlightHead() && currentWebViewOrdinal != currentOrdinal"
		)
		assertContains(settlementStarted, "settlementMutationFence.onSettlementStarted(")
		listOf(settlementCompleted, settlementCancelled).forEach { settlement ->
			assertContains(settlement, "try {")
			assertContains(settlement, "finally {")
			assertContains(settlement, "completeSettlementReconciliation(")
		}
		assertContains(
			settlementCancelled,
			"if (settlementMutationFence.hasUnreconciledSettlement)"
		)
		assertTrue(
			Regex(Regex.escape(deferredRefreshCheck)).findAll(refresh).count() >= 2,
			"Refresh must be fenced before and after the asynchronous WebView plan."
		)
		assertContains(refresh, "val expectedTurnVersion = committedTurnVersion")
		assertContains(refresh, "committedTurnVersion != expectedTurnVersion")
		assertTrue(
			Regex(Regex.escape(relocationFenceCheck)).findAll(refresh).count() >= 2,
			"Relocation authority must be checked before and after the WebView plan."
		)
		assertContains(preparation, deferredRefreshCheck)
		assertContains(preparation, relocationFenceCheck)
		assertContains(
			recoveredRole,
			"settlementMutationFence.hasUnreconciledSettlement"
		)
		assertContains(recoveredRole, "surfaceView.isSettlementRunning")
		assertContains(
			recoveredSubmission,
			"settlementMutationFence.blocksExternalDeckMutation(activeGestureId)"
		)
	}

	@Test
	fun edgeTapsUseThePreparedImportedDeckWithoutFallingThroughToFoliate() {
		val hostSource = hostFile.readText()
		val dispatch = hostSource
			.substringAfter("private fun dispatchPlayLikeCurlSingleTapAction(")
			.substringBefore("override fun onInterceptTouchEvent")
		val curlBranch = dispatch
			.substringAfter("if (pageChange != null)")
			.substringBefore("onAction(action)")

		assertContains(dispatch, "action: KomikkuNavigationRegion")
		assertContains(dispatch, "gestureId: Long")
		assertContains(curlBranch, "tapTurnController.turn(gestureId, pageChange)")
		assertFalse(curlBranch.contains("playLikeCurlController.turn("))
		assertContains(curlBranch, "ReaderPageTurnStartResult.TerminalPublished")
		assertFalse(curlBranch.contains("beginGesture("))
		assertFalse(curlBranch.contains("currentPageGestureId"))
		assertFalse(curlBranch.contains("dispatchLegacySingleTapAction("))
	}

	@Test
	fun importedTapDirectionUsesTheReaderDirectionContract() {
		val source = hostFile.readText()
		val mapper = source
			.substringAfter("private fun playLikeCurlPageChangeFor(")
			.substringBefore("private fun dispatchPlayLikeCurlSingleTapAction(")

		assertContains(source, "pageTurnReadingDirection")
		assertContains(mapper, "readerTapZonePageTurnDirectionFor(")
		assertContains(mapper, "ReaderPageTurnDirection.Next -> PageChange.NEXT")
		assertContains(mapper, "ReaderPageTurnDirection.Previous -> PageChange.PREVIOUS")
	}

	@Test
	fun rejectedCommittedTerminalInvalidatesPromotedRendererState() {
		val source = controllerFile.readText()
		val settlement = source
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val recovery = source
			.substringAfter("private fun recoverRejectedSettlement(")
			.substringBefore("private fun promotePendingDeck(")

		val sourceCapture = settlement.indexOf("val sourceOrdinal = currentOrdinal")
		val promotion = settlement.indexOf("promotePendingDeck(currentPageOrdinal)")
		val rejected = settlement.indexOf("result !is ReaderPageRelocationCommitResult.Published")
		val recoveryCall = settlement.indexOf(
			"recoverRejectedSettlement(sourceOrdinal, promotedGeneration)",
			rejected
		)
		assertTrue(sourceCapture >= 0)
		assertTrue(promotion > sourceCapture)
		assertTrue(rejected > promotion)
		assertTrue(recoveryCall > rejected)
		assertContains(recovery, "recoverRejectedReaderSettlement(")
		assertContains(recovery, "restoreSourceOrdinal = { ordinal -> currentOrdinal = ordinal }")
		assertContains(recovery, "invalidateRenderer = { reason -> invalidate(reason) }")
		assertContains(recovery, "requestPrewarm = onRequestPrewarm")
		assertFalse(recovery.contains("activeDeckGenerationId = snapshot.activeDeckGenerationId"))
		assertFalse(recovery.contains("pendingDeckGenerationId = snapshot.pendingDeckGenerationId"))
	}

	@Test
	fun promotedDeckPublishesItsDestinationSourceOrdinal() {
		val source = controllerFile.readText()
		val publication = source
			.substringAfter("private fun publishPreparedActiveDeck(")
			.substringBefore("private fun notifyPreparedActiveDeckChanged(")
		val promotion = source
			.substringAfter("private fun promotePendingDeck(")
			.substringBefore("private fun discardPendingDeck(")

		assertContains(publication, "sourceOrdinal: Int = currentOrdinal")
		assertContains(
			publication,
			"sourceCenterPageIndex = pages.profile.pageRequest(sourceOrdinal).sourcePageIndex"
		)
		assertContains(promotion, "publishPreparedActiveDeck(currentPageOrdinal)")
	}

	@Test
	fun preparedDestinationDeckRearmsOnlyReplacementPrewarm() {
		val host = hostFile.readText()
		val callback = host
			.substringAfter("private fun onPreparedActiveDeckChanged(")
			.substringBefore("private fun resumeDestinationDeckPrewarmIfReady()")
		val resume = host
			.substringAfter("private fun resumeDestinationDeckPrewarmIfReady()")
			.substringBefore("private fun onForegroundWebViewPassiveAvailable()")
		val passiveAvailable = host
			.substringAfter("private fun onForegroundWebViewPassiveAvailable()")
			.substringBefore("private fun requestPageTurnPrewarmWhenReady()")

		assertContains(
			host,
			"onPreparedActiveDeckChanged = ::onPreparedActiveDeckChanged"
		)
		assertContains(
			host,
			"private var preparedActiveDeck: ReaderPagePreparedActiveDeck? = null"
		)
		assertContains(host, "private var destinationDeckPrewarmPending = false")
		assertContains(callback, "val previous = preparedActiveDeck")
		assertContains(callback, "val ownership = foregroundWebViewOwnership.snapshot()")
		assertContains(callback, "preparedActiveDeck = deck")
		assertContains(callback, "deck != null &&")
		assertContains(callback, "previous != null &&")
		assertContains(callback, "previous != deck &&")
		assertContains(callback, "ownership.liveClaims > 0 ||")
		assertContains(callback, "ownership.restorationCallbacks > 0")
		assertContains(callback, "destinationDeckPrewarmPending = true")
		assertContains(
			callback,
			"pageRasterPreparationController.onPreparedActiveDeckChanged(deck)"
		)
		assertContains(callback, "resumeDestinationDeckPrewarmIfReady()")
		assertContains(resume, "if (!destinationDeckPrewarmPending) return")
		assertContains(resume, "if (preparedActiveDeck == null) return")
		assertContains(resume, "if (!foregroundWebViewOwnership.canAcquirePassive()) return")
		assertContains(resume, "destinationDeckPrewarmPending = false")
		assertContains(resume, "requestPageTurnPrewarmWhenReady()")
		assertContains(passiveAvailable, "resumeDestinationDeckPrewarmIfReady()")
	}

	@Test
	fun invalidatingLifecycleClearsPendingDestinationDeckPrewarm() {
		val host = hostFile.readText()
		val reset = host
			.substringAfter("private fun clearDestinationDeckPrewarm()")
			.substringBefore("private fun onRasterProfileEpochChanged(")
		val lifecycle = host
			.substringAfter("private fun dispatchPageHostLifecycleEvent(")
			.substringBefore("private fun completeHostGesture(")

		assertContains(reset, "preparedActiveDeck = null")
		assertContains(reset, "destinationDeckPrewarmPending = false")
		assertContains(lifecycle, "clearDestinationDeckPrewarm()")
		assertTrue(
			lifecycle.indexOf("clearDestinationDeckPrewarm()") <
				lifecycle.indexOf("pageInputSettlementHostController.onLifecycleEvent(event)")
		)
	}

	@Test
	fun profileEpochInvalidationClearsPendingDestinationDeckPrewarm() {
		val host = hostFile.readText()
		val profileEpoch = host
			.substringAfter("private fun onRasterProfileEpochChanged(")
			.substringBefore("private fun attachPageRasterRepairQaFault(")

		assertContains(profileEpoch, "clearDestinationDeckPrewarm()")
		assertTrue(
			profileEpoch.indexOf("clearDestinationDeckPrewarm()") <
				profileEpoch.indexOf("rasterProfileEpoch = epoch")
		)
	}

	@Test
	fun committedTurnsPublishOneVersionedWindowBeforePersistentFarEdgeRefill() {
		val source = controllerFile.readText()
		val preparation = source
			.substringAfter("fun onPreparationStateChanged(")
			.substringBefore("fun onHostAttached()")
		val settlement = source
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val schedule = source
			.substringAfter("private fun schedulePersistentRefill(")
			.substringBefore("private fun requestRasterRepair(")
		val publisher = source
			.substringAfter("private fun publishProtectedWindow(")
			.substringBefore("private fun publishProtectedRasterOrdinals(")
		val committedPublication = settlement
			.substringAfter("publishCommittedTerminal = {")
			.substringBefore("dispatch = ::transferAndDispatchRelocation")
		val adapterFence = source
			.substringAfter("private fun rasterPublicationFence(")
			.substringBefore("private fun publishProtectedWindow(")

		assertContains(source, "private val persistentRefillCoordinator")
		assertContains(source, "private var committedTurnVersion = 0L")
		assertContains(source, "private var protectedWindowVersion = 0L")
		assertContains(source, "private var currentProtectedWindow = emptyList<Int>()")
		assertContains(settlement, "committedTurnVersion = Math.incrementExact(committedTurnVersion)")
		assertContains(settlement, "schedulePersistentRefill(")
		assertContains(
			committedPublication,
			"val destinationWindow = profile.preparedPageIndices(currentPageOrdinal)"
		)
		assertContains(committedPublication, "publishProtectedWindow(destinationWindow)")
		assertContains(
			committedPublication,
			"if (!hasDecodedWorkingSetForCurrentOrdinal()) {"
		)
		assertContains(
			committedPublication,
			"refillDecodedWorkingSet(currentPageOrdinal, \"settlement-committed\")"
		)
		assertContains(committedPublication, "deferredDecodedRefillCenterOrdinal = null")
		assertContains(
			preparation,
			"takeIf { centerOrdinal -> centerOrdinal == currentOrdinal }"
		)
		assertTrue(
			committedPublication.indexOf("currentOrdinal = currentPageOrdinal") <
				committedPublication.indexOf("publishGestureTerminal("),
			"The destination must become authoritative before the committed terminal is observable."
		)
		assertTrue(
			committedPublication.indexOf("publishProtectedWindow(") <
				committedPublication.indexOf("publishGestureTerminal("),
			"The destination window must advance before relocation dispatch can request its far edge."
		)
		assertTrue(
			committedPublication.indexOf("refillDecodedWorkingSet(") <
				committedPublication.indexOf("publishGestureTerminal("),
			"The destination refill must start before a rapid follow-up pointer can observe the commit."
		)
		assertTrue(
			settlement.indexOf("schedulePersistentRefill(") >
				settlement.indexOf("relocationGestureCoordinator.commit("),
			"Persistent hydration must remain asynchronous after the synchronous window advance."
		)
		assertContains(schedule, "rasterScope.launch(Dispatchers.Main.immediate)")
		assertContains(schedule, "refillDecodedWorkingSet(")
		assertTrue(
			schedule.indexOf("persistentRefillCoordinator.onTurnCommitted(") <
				schedule.indexOf("refillDecodedWorkingSet("),
			"Persistent hydration must feed the promoted decoded deck before Foliate acknowledgement."
		)
		assertContains(schedule, "requestGeneration == expectedGeneration")
		assertContains(schedule, "committedTurnVersion == fence.committedTurnVersion")
		assertContains(schedule, "protectedWindowVersion == fence.protectedWindowVersion")
		assertContains(schedule, "currentProtectedWindow == fence.protectedWindow")
		assertContains(adapterFence, "val expectedTurnVersion = committedTurnVersion")
		assertContains(adapterFence, "val expectedWindowVersion = protectedWindowVersion")
		assertContains(adapterFence, "requestGeneration == expectedRequestGeneration")
		assertContains(adapterFence, "committedTurnVersion == expectedTurnVersion")
		assertContains(adapterFence, "protectedWindowVersion == expectedWindowVersion")
		assertContains(adapterFence, "currentProtectedWindow == expectedWindow")
		assertContains(source, "rasterAdapter?.hasDecoded(profile, logicalOrdinal)")
		assertContains(source, "publicationDispatcher = Dispatchers.Main.immediate")
		assertContains(publisher, "applyProtectedWindow(immutableWindow, version)")
		assertContains(publisher, "publishProtectedRasterOrdinals(window)")
		assertEquals(
			2,
			Regex("publishProtectedRasterOrdinals\\(").findAll(source).count(),
			"Only the central protected-window publisher may replace raster protection."
		)
	}

	@Test
	fun deckDeliveryRechecksImmutableFenceOnEveryHostPostOutcome() {
		val source = controllerFile.readText()
		val refill = source
			.substringAfter("private fun refillDecodedWorkingSet(")
			.substringBefore("private fun ReaderPlayLikeCurlRasterProfile.preparedPageIndices(")
		val preparation = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("override fun isCurrentRepairWindow(")

		assertContains(refill, "!publicationFence.isCurrent()")
		assertEquals(
			3,
			Regex("publicationFence\\.isCurrent\\(\\)").findAll(preparation).count(),
			"Exceptional, unavailable, and successful delivery must reject an expired fence."
		)
	}

	@Test
	fun settlementSubmitsThePreparedReplacementWithoutBlockingTheAcceptedAnimation() {
		val source = controllerFile.readText()
		val settlementStarted = source
			.substringAfter("override fun onSettlementStarted(")
			.substringBefore("override fun onSettlementCompleted(")

		assertContains(
			settlementStarted,
			"submitLibraryDeck(",
			message = "Settlement must offer the already-decoded replacement for automatic promotion."
		)
		assertContains(settlementStarted, "ReaderDeckSubmissionRole.Pending")
		assertContains(
			settlementStarted,
			"prefetchDecodedWorkingSet(gestureId, targetOrdinal)"
		)
		assertTrue(
			settlementStarted.indexOf("submitLibraryDeck(") <
				settlementStarted.indexOf("prefetchDecodedWorkingSet(")
		)
		assertFalse(
			settlementStarted.contains("adapter.prepare("),
			"Settlement must never decode or perform IO synchronously on the gesture frame."
		)
		assertContains(settlementStarted, "ReaderTextureDeckState.Settling")
		assertContains(settlementStarted, "ReaderPageInteractionState.Settling")
	}

	@Test
	fun destinationDecodedWindowPrefetchIsGestureScopedAndReusedAfterCommit() {
		val source = controllerFile.readText()
		val settlementStarted = source
			.substringAfter("override fun onSettlementStarted(")
			.substringBefore("override fun onSettlementCompleted(")
		val settlementCompleted = source
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val reserve = source
			.substringAfter("private fun reserveNextDecodedWorkingSet(")
			.substringBefore("private fun prefetchDecodedWorkingSet(")
		val prefetch = source
			.substringAfter("private fun prefetchDecodedWorkingSet(")
			.substringBefore("private fun startDecodedWorkingSetPrefetch(")
		val startPrefetch = source
			.substringAfter("private fun startDecodedWorkingSetPrefetch(")
			.substringBefore("private fun observeIdleDecodedWorkingSetReserve(")
		val observeReserve = source
			.substringAfter("private fun observeIdleDecodedWorkingSetReserve(")
			.substringBefore("private fun commitDecodedWorkingSetPrefetch(")
		val commit = source
			.substringAfter("private fun commitDecodedWorkingSetPrefetch(")
			.substringBefore("private fun takeCommittedDecodedWorkingSetPrefetch(")
		val takeCommittedPrefetch = source
			.substringAfter("private fun takeCommittedDecodedWorkingSetPrefetch(")
			.substringBefore("private fun discardDecodedWorkingSetPrefetch(")
		val refill = source
			.substringAfter("private fun refillDecodedWorkingSet(")
			.substringBefore("private fun scheduleDecodedWorkingSetRefillRetry(")
		val initialPreparation = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("override fun isCurrentRepairWindow(")
		val discard = source
			.substringAfter("private fun discardDecodedWorkingSetPrefetch(")
			.substringBefore("private fun isDecodedWorkingSetPrefetchCurrent(")
		val fence = source
			.substringAfter("private fun isDecodedWorkingSetPrefetchCurrent(")
			.substringBefore("private fun refillDecodedWorkingSet(")
		val protectedWindowPublication = source
			.substringAfter("private fun applyProtectedWindow(")
			.substringBefore("private fun publishProtectedRasterOrdinals(")
		val finishGesture = source
			.substringAfter("private fun finishGesture(")
			.substringBefore("private fun publishGestureTerminal(")
		val sessionChange = source
			.substringAfter("fun setFoliateSessionId(")
			.substringBefore("fun visualLocationOrigin(")
		val invalidate = source
			.substringAfter("fun invalidate(")
			.substringBefore("private val destroyFence")

		assertContains(source, "private var decodedWorkingSetPrefetch:")
		assertContains(source, "gestureId: Long")
		assertContains(source, "sourceOrdinal: Int")
		assertContains(source, "targetOrdinal: Int")
		assertContains(source, "foliateSessionId: String")
		assertContains(source, "expectedRequestGeneration: Long")
		assertContains(source, "expectedCommittedTurnVersion: Long")
		assertContains(source, "pageIndices: List<Int>")
		assertContains(source, "transferredToRefill")
		assertContains(source, "preparation: Deferred<")
		assertContains(settlementStarted, "prefetchDecodedWorkingSet(gestureId, targetOrdinal)")
		assertContains(reserve, "PageChange.NEXT")
		assertContains(reserve, "startDecodedWorkingSetPrefetch(null, targetOrdinal)")
		assertFalse(reserve.contains("publishProtectedWindow("))
		assertFalse(reserve.contains("publishGestureTerminal("))
		assertContains(prefetch, "activeGestureId == gestureId")
		assertContains(prefetch, "existing.preparation.isActive")
		assertContains(prefetch, "existing.gestureId = gestureId")
		assertContains(prefetch, "startDecodedWorkingSetPrefetch(gestureId, targetOrdinal)")
		assertContains(startPrefetch, "ReaderPlayLikeCurlMissingRasterPolicy.CacheOnly")
		assertContains(startPrefetch, "observeIdleDecodedWorkingSetReserve(prefetch)")
		assertContains(startPrefetch, "adapter.updateProtectedPageIndices(")
		assertContains(startPrefetch, "profile.preparedPageIndices(targetOrdinal)")
		assertContains(startPrefetch, "adapter.prepare(")
		assertContains(observeReserve, "if (!prefetch.transferredToRefill) deck?.close()")
		assertContains(observeReserve, "deck?.close()")
		assertTrue(
			takeCommittedPrefetch.indexOf("prefetch.transferredToRefill = true") <
				takeCommittedPrefetch.indexOf("decodedWorkingSetPrefetch = null")
		)
		assertContains(initialPreparation, "reserveNextDecodedWorkingSet()")
		assertContains(refill, "reserveNextDecodedWorkingSet()")
		listOf(prefetch, startPrefetch, observeReserve).forEach { operation ->
			assertFalse(operation.contains("currentOrdinal ="))
			assertFalse(operation.contains("activePages ="))
			assertFalse(operation.contains("publishProtectedWindow("))
			assertFalse(operation.contains("submitLibraryDeck("))
			assertFalse(operation.contains("publishGestureTerminal("))
		}

		val committedPublication = settlementCompleted
			.substringAfter("publishCommittedTerminal = {")
			.substringBefore("publishGestureTerminal(")
		assertContains(committedPublication, "commitDecodedWorkingSetPrefetch(")
		assertContains(committedPublication, "gestureId,")
		assertContains(committedPublication, "currentPageOrdinal,")
		assertContains(committedPublication, "destinationWindow")
		assertTrue(
			committedPublication.indexOf("publishProtectedWindow(destinationWindow)") <
				committedPublication.indexOf("commitDecodedWorkingSetPrefetch(")
		)
		assertContains(
			protectedWindowPublication,
			"rasterAdapter?.updateProtectedPageIndices(profile, window)"
		)
		assertTrue(
			committedPublication.indexOf("commitDecodedWorkingSetPrefetch(") <
				committedPublication.indexOf("refillDecodedWorkingSet(")
		)
		assertContains(commit, "prefetch.committed = true")
		assertContains(refill, "takeCommittedDecodedWorkingSetPrefetch(")
		assertContains(refill, "prefetch?.preparation ?: adapter.prepare(")
		assertContains(refill, "teardownScope.launch")
		assertContains(refill, "withContext(NonCancellable + Dispatchers.Main.immediate)")
		assertContains(refill, "prefetch?.publicationFence ?: rasterPublicationFence(")

		assertContains(finishGesture, "discardDecodedWorkingSetPrefetch(")
		assertTrue(
			finishGesture.indexOf("discardDecodedWorkingSetPrefetch(") <
				finishGesture.indexOf("relocationGestureCoordinator.finish(")
		)
		assertTrue(
			sessionChange.indexOf("discardDecodedWorkingSetPrefetch(") <
				sessionChange.indexOf("drainRelocationOwnership(")
		)
		assertContains(invalidate, "discardDecodedWorkingSetPrefetch(")
		assertContains(discard, "prefetch.publicationAllowed = false")
		assertContains(discard, "prefetch.preparation.cancel()")
		assertContains(discard, "deck?.close()")
		assertContains(fence, "prefetch.publicationAllowed")
		assertContains(fence, "currentFoliateSessionId != prefetch.foliateSessionId")
		assertContains(fence, "activeGestureId == prefetch.gestureId")
		assertContains(fence, "currentOrdinal == prefetch.sourceOrdinal")
		assertContains(fence, "currentOrdinal == prefetch.targetOrdinal")
		val uncommittedFence = fence
			.substringAfter("return if (!prefetch.committed) {")
			.substringBefore("} else {")
		val committedFence = fence.substringAfter("} else {")
		assertContains(
			uncommittedFence,
			"requestGeneration == prefetch.expectedRequestGeneration"
		)
		assertFalse(
			committedFence.contains("requestGeneration == prefetch.expectedRequestGeneration"),
			"A committed destination prefetch must survive benign plan refresh generations."
		)
		assertContains(fence, "committedTurnVersion ==")
		assertContains(fence, "Math.incrementExact(prefetch.expectedCommittedTurnVersion)")
	}

	@Test
	fun rasterTextureAndInteractionReadinessHaveSeparateOwners() {
		val controllerSource = controllerFile.readText()
		val hostSource = hostFile.readText()

		assertContains(controllerSource, "ReaderPageRendererReadinessState")
		assertContains(controllerSource, "ReaderTextureDeckState")
		assertContains(controllerSource, "ReaderPageInteractionState.BackgroundPrefetch")
		assertFalse(
			controllerSource.contains("private var interactionReady"),
			"Interaction readiness must be an explicit state, not a second boolean authority."
		)
		assertContains(hostSource, "latestRasterPreparationState")
		assertContains(hostSource, "latestRendererReadinessState")
		assertContains(hostSource, "publishMergedPagePreparationState()")
		assertContains(hostSource, "readerMergedPagePreparationState(")
		assertContains(hostSource, "rasterState.withRendererReadiness(rendererState)")
		assertContains(hostSource, "shellCoverView.isClickable = layers.shellCover")
		assertContains(hostSource, "composeOverlay.isClickable = false")
		assertFalse(
			hostSource.contains("composeOverlay.isClickable = visible"),
			"Preparation gestures must reach the fail-closed native pointer ledger."
		)
	}

	@Test
	fun productionControllerWaitsForTheActiveRasterProducerInsteadOfRequestingItAgain() {
		val source = controllerFile.readText()
		val refresh = source
			.substringAfter("private fun refreshPreparedDeck()")
			.substringBefore("private fun prepareProfile(")
		val unavailableDeck = source
			.substringAfter("if (deck == null)")
			.substringBefore("return@withContext")
		val prewarm = source
			.substringAfter("private fun requestPrewarmIfIdle(")
			.substringBefore("private fun logActivationState(")

		assertContains(source, "private var preparationPhase = ReaderPagePreparationPhase.Idle")
		assertContains(source, "preparationPhase = state.phase")
		assertContains(refresh, "ReaderPagePreparationPhase.Preparing")
		assertContains(refresh, "\"preparation-in-progress\"")
		assertContains(refresh, "requestPrewarmIfIdle(")
		assertContains(unavailableDeck, "requestPrewarmIfIdle(")
		assertContains(
			prewarm,
			"preparationPhase != ReaderPagePreparationPhase.Preparing",
			message = "Ready preparation may be stale for the newly committed protected window."
		)
		assertFalse(
			unavailableDeck.contains("onRequestPrewarm()"),
			"A missing deck while the raster producer is active must not recursively start another producer."
		)
	}

	@Test
	fun staleDeckPlanCallbackCannotRaceAnActiveRasterProducer() {
		val source = controllerFile.readText()
		val callback = source
			.substringAfter(") { encoded ->")
			.substringBefore("val plan = readerPageRasterPreparationPlan(encoded)")

		assertContains(callback, "preparationPhase == ReaderPagePreparationPhase.Preparing")
		assertContains(callback, "\"preparation-in-progress-after-plan\"")
		assertContains(callback, "return@evaluateJavascript")
	}

	@Test
	fun productionControllerPreparesTheProtectedWorkingSetAroundTheFoliateCenter() {
		val source = controllerFile.readText()
		val prepare = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("override fun isCurrentRepairWindow(")

		assertContains(
			prepare,
			"profile.preparedPageIndices(centerOrdinal)",
			ignoreCase = false,
			"The controller must prepare the active deck plus one promotion in both directions."
		)
		assertContains(source, "readerPlayLikeCurlPreparedPageIndices(")
		assertFalse(
			prepare.contains("listOf(centerOrdinal - step, centerOrdinal, centerOrdinal + step)"),
			"Expanding three overlapping deck windows requests Foliate snapshots that were never produced."
		)
		assertFalse(
			prepare.contains(".flatMap"),
			"The real EPUB adapter must not recursively expand adjacent deck windows."
		)
	}

	@Test
	fun promotedDeckCanAcceptTheNextGestureBeforeFoliateAcknowledgesTheExactPage() {
		val source = controllerFile.readText()
		val availability = source
			.substringAfter("val isAvailable: Boolean")
			.substringBefore("fun setEnabled(")

		assertFalse(
			availability.contains("pendingExactOrdinal == null"),
			"Foliate acknowledgement must not create an interaction dead zone after deck promotion."
		)
	}

	@Test
	fun exactSettlementRefillsDecodedPagesWithoutRestartingWebViewCapture() {
		val controller = controllerFile.readText()
		val synchronize = controller
			.substringAfter("fun synchronizeVisualPageIndex(")
			.substringBefore("private fun drainRelocationOwnership(")
		val ready = controller
			.substringAfter("ReaderPagePreparationPhase.Ready -> {")
			.substringBefore("ReaderPagePreparationPhase.Failed -> {")
		val host = hostFile.readText()
		val hostSynchronize = host
			.substringAfterLast("fun setPageTurnVisualLocation(")
			.substringBefore("fun setShellCoverVisible(")

		assertContains(synchronize, "ReaderPageVisualLocationOrigin.ExactPageTurn")
		assertContains(synchronize, "refillDecodedWorkingSet(")
		val exact = synchronize
			.substringAfter("ReaderPageVisualLocationOrigin.ExactPageTurn -> {")
			.substringBefore("ReaderPageVisualLocationOrigin.StaleAcknowledgement")
		assertFalse(exact.contains("onRequestPrewarm()"))
		assertContains(ready, "if (activePages == null)")
		assertFalse(hostSynchronize.contains("requestPageTurnPrewarmWhenReady()"))
	}

	@Test
	fun exactSettlementBlocksNewPointersOnlyUntilDecodedRefillCompletes() {
		val controller = controllerFile.readText()
		val preparation = controller
			.substringAfter("fun onPreparationStateChanged(")
			.substringBefore("fun onHostAttached()")
		val refill = controller
			.substringAfter("private fun refillDecodedWorkingSet(")
			.substringBefore("private fun ReaderPlayLikeCurlRasterProfile.preparedPageIndices(")
		val waitsForPreparation = refill
			.substringAfter("val waitsForPreparation =")
			.substringBefore("val needsCurrentWindowRetry =")
		val settlement = controller
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val committedPublication = settlement
			.substringAfter("publishCommittedTerminal = {")
			.substringBefore("dispatch = ::transferAndDispatchRelocation")
		val availability = controller
			.substringAfter("val isAvailable: Boolean")
			.substringBefore("private val canContinueAcceptedPointer")
		val workingSetReadiness = controller
			.substringAfter("private fun hasDecodedWorkingSetForCurrentOrdinal()")
			.substringBefore("val isAvailable: Boolean")
		val unavailableOutcome = controller
			.substringAfter("private fun unavailableGestureOutcome()")
			.substringBefore("fun updatePaginationReadiness(")

		assertContains(
			refill,
			"interaction = ReaderPageInteractionState.RefillingWorkingSet"
		)
		assertContains(
			committedPublication,
			"gateForDecodedWorkingSetRefill(profile, destinationWindow)"
		)
		assertTrue(
			committedPublication.indexOf("gateForDecodedWorkingSetRefill(") <
				committedPublication.indexOf("publishGestureTerminal("),
			"A committed terminal must not expose an incomplete destination window to a new pointer."
		)
		assertContains(refill, "deferredDecodedRefillCenterOrdinal = centerOrdinal")
		assertContains(refill, "scheduleDecodedWorkingSetRefillRetry(")
		assertContains(
			waitsForPreparation,
			"deck == null",
			message = "A decoded deck rejected by an expired fence must retain its immediate retry."
		)
		assertContains(refill, "deck == null && needsCurrentWindowRetry")
		assertContains(refill, "decoded-refill-awaiting-raster:${'$'}refill")
		assertContains(
			refill,
			"deck != null && needsCurrentWindowRetry",
			message = "An unavailable raster must await repair or prewarm instead of hot-looping retries."
		)
		assertContains(refill, "mainHandler.post {")
		assertContains(refill, "!hasDecodedWorkingSetForCurrentOrdinal()")
		assertContains(refill, "decoded-refill-fence-retry:${'$'}refill")
		assertContains(preparation, "val deferredCenterOrdinal = deferredDecodedRefillCenterOrdinal")
		assertContains(
			preparation,
			"takeIf { centerOrdinal -> centerOrdinal == currentOrdinal }"
		)
		assertContains(refill, "if (previous.generations.isEmpty())")
		assertContains(
			refill,
			"reason = \"decoded-refill-completed:${'$'}refill\""
		)
		assertContains(refill, "interaction = preparedInteractionState()")
		assertContains(
			workingSetReadiness,
			"pages.deck.pageIndices.containsAll(profile.preparedPageIndices(currentOrdinal))"
		)
		assertContains(availability, "hasDecodedWorkingSetForCurrentOrdinal()")
		assertContains(
			unavailableOutcome,
			"ReaderPageGestureTerminalOutcome.RejectedPreparing"
		)
	}

	@Test
	fun missingFarEdgeRequestsOneRepairAndRestoresARecoveredDeck() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()
		val repair = controller
			.substringAfter("private fun requestRasterRepair(")
			.substringBefore("private fun prepareProfile(")
		val refill = controller
			.substringAfter("private fun refillDecodedWorkingSet(")
			.substringBefore("private fun requestRasterRepair(")
		val failedLoad = controller
			.substringAfter("if (deck == null) {")
			.substringBefore("return@launch")
		val hostRepair = host
			.substringAfter("private fun requestPageRasterRepair(")
			.substringBefore("private fun onRendererReadinessChanged(")

		assertContains(controller, "onRequestRasterRepair:")
		assertContains(controller, "onMissingRaster =")
		assertContains(repair, "onRequestRasterRepair(sourcePageIndex)")
		assertContains(repair, "onAttachRasterRepairQaFault(sourcePageIndex, correlation)")
		assertContains(repair, "if (operationToken == null) return")
		assertTrue(
			repair.indexOf("onAttachRasterRepairQaFault(sourcePageIndex, correlation)") <
				repair.indexOf("if (operationToken == null) return"),
			"A fault-bearing coalesced recipient must attach its immutable root before returning."
		)
		assertContains(repair, "event = \"page-repair-requested\"")
		assertContains(repair, "event = \"page-repair-completed\"")
		assertContains(repair, "deckRecoveryCoordinator.accept(result)")
		assertContains(repair, "result=stale-repair-result attempt=${'$'}{currentRecipient.attempt}")
		assertContains(repair, "attempt = 1")
		assertContains(repair, "requestPrewarmIfIdle(\"page-repair-stale-result\")")
		assertFalse(repair.contains("refillDecodedWorkingSet("))
		assertFalse(repair.contains("refreshPreparedDeck()"))
		assertFalse(repair.contains("onRequestPrewarm()"))
		assertContains(refill, "if (activeDeckGenerationId == null)")
		assertContains(refill, "submitLibraryDeck(")
		assertContains(failedLoad, "if (rasterRepairRequests.isEmpty())")
		assertContains(host, "onRequestRasterRepair = ::requestPageRasterRepair")
		assertContains(hostRepair, "pageRasterPreparationController.repairRasterPage(pageIndex, onComplete)")
		assertFalse(
			hostRepair.contains("onLifecycleEvent("),
			"A targeted raster repair must not cancel the settlement that requested it."
		)
		assertFalse(hostRepair.contains("ReaderPageHostLifecycleEvent.RasterProfileInvalidated"))
	}

	@Test
	fun completedRasterPlanMetadataDoesNotGateTheCurrentRepairTarget() {
		val source = controllerFile.readText()
		val currentWindow = source
			.substringAfter("override fun isCurrentRepairWindow(")
			.substringBefore("override fun hasUsablePreparedActiveDeck()")
		val recoveredBuild = source
			.substringAfter("override fun requestRecoveredDeckBuild(")
			.substringBefore("override fun cancelRecoveredDeckBuild(")

		assertContains(currentWindow, "readerPlayLikeCurlRepairTargetMatches(")
		assertFalse(
			currentWindow.contains("preparedRepairPageIndices = repairedPageIndices"),
			"The completed preparation plan does not describe the current protected window."
		)
		assertContains(recoveredBuild, "profile.preparedPageIndices(logicalCenter)")
		assertContains(recoveredBuild, "publicationFence.isCurrent()")
	}

	@Test
	fun repairedRasterWindowBuildsAndSubmitsThroughTheRecoveryCoordinator() {
		val controller = controllerFile.readText()
		val recovery = recoveryFile.readText()
		val buildOperation = recoveredBuildOperationFile.readText()
		val preparation = rasterPreparationFile.readText()
		val build = controller
			.substringAfter("override fun requestRecoveredDeckBuild(")
			.substringBefore("override fun cancelRecoveredDeckBuild(")
		val submission = controller
			.substringAfter("override fun submitRecoveredDeck(")
			.substringBefore("override fun releaseUnsubmittedRecoveredDeck(")
		val unsubmittedRelease = controller
			.substringAfter("override fun releaseUnsubmittedRecoveredDeck(")
			.substringBefore("private fun rollbackAcceptedRecoveredDeck(")
		val acceptedRollback = controller
			.substringAfter("private fun rollbackAcceptedRecoveredDeck(")
			.substringBefore("override fun cancelSubmittedRecoveredDeck(")
		val cancellation = controller
			.substringAfter("override fun cancelRecoveredDeckBuild(")
			.substringBefore("override fun currentRecoveredDeckRole(")
		val roleSelection = controller
			.substringAfter("override fun currentRecoveredDeckRole(")
			.substringBefore("override fun submitRecoveredDeck(")
		val submittedCancellation = controller
			.substringAfter("override fun cancelSubmittedRecoveredDeck(")
			.substringBefore("override fun isPrepared(")
		val renderFailure = controller
			.substringAfter("override fun onRenderFailure(")
			.substringBefore("val isAvailable: Boolean")
		val releaseGeneration = controller
			.substringAfter("private fun releaseGeneration(")
			.substringBefore("private fun promotePendingDeck(")

		assertContains(preparation, "ReaderPageRasterRepairResult.Repaired")
		assertContains(preparation, "rasterEpoch = generation")
		assertContains(controller, ") : ReaderPageTapTurnPort,")
		assertContains(controller, "ReaderPageDeckRecoveryHost,")
		assertContains(controller, "ReaderPageRendererOwnershipHost")
		assertContains(controller, "deckRecoveryCoordinator.accept(result)")
		assertContains(build, "publicationFence.isCurrent()")
		assertContains(build, "operation = ReaderPageRecoveredDeckBuildOperation(")
		assertContains(build, "recoveredBuildOperations[requestId] = operation")
		assertContains(build, "recoveredBuildOperations[requestId] !== operation")
		assertContains(buildOperation, "CoroutineStart.UNDISPATCHED")
		assertContains(buildOperation, "withContext(publicationDispatcher)")
		assertContains(buildOperation, "withContext(NonCancellable)")
		assertContains(buildOperation, "undeliveredResult?.close()")
		assertContains(build, "builtRecoveredDecks[generationId]")
		assertContains(cancellation, "operation.cancel()")
		assertContains(
			roleSelection,
			"settlementMutationFence.hasUnreconciledSettlement"
		)
		assertContains(roleSelection, "surfaceView.isSettlementRunning")
		assertContains(submission, "submissionCallbackFence.submit(generationId)")
		assertContains(submission, "surfaceView.submitDeckWithResult(built.deck) {")
		assertContains(submission, "ownershipTransferred = true")
		assertContains(submission, "acceptRecoveredDeckOwnership(generationId, role)")
		assertContains(submission, "if (ownershipTransferred)")
		assertContains(submission, "PageSurfaceDeckSubmissionResult.Status.REJECTED")
		assertContains(submission, "DeckRejectionReason.RESOURCE_CAPACITY")
		assertContains(
			submission,
			"ReaderPageRecoveredDeckSubmissionResult.AwaitingRendererCapacity"
		)
		assertContains(submission, "val accepted = checkNotNull(builtRecoveredDecks.remove(generationId))")
		assertContains(submission, "generationOwners[generationId] !== accepted.pages")
		assertContains(submission, "rollbackAcceptedRecoveredDeck(generationId, role, failure)")
		assertContains(unsubmittedRelease, "builtRecoveredDecks.remove(generationId) ?: return")
		assertContains(unsubmittedRelease, "releaseGeneration(generationId)")
		assertContains(acceptedRollback, "tombstoneSubmittedRecoveredDeck(generationId, role)")
		assertContains(acceptedRollback, "strandedActive !== generationOwners[generationId]")
		assertContains(acceptedRollback, "activePages = null")
		assertContains(acceptedRollback, "surfaceView.releaseDeck(generationId)")
		assertContains(submittedCancellation, "readerRecoveredDeckCancellationRoleMatches(")
		assertContains(submittedCancellation, "tombstoneSubmittedRecoveredDeck(")
		assertContains(renderFailure, "generationId in recoveredDeckGenerations")
		assertContains(renderFailure, "deckRecoveryCoordinator.ownsSubmittedGeneration(")
		assertContains(renderFailure, "tombstoneSubmittedRecoveredDeck(")
		assertContains(releaseGeneration, "val releasedCurrentActive =")
		assertContains(releaseGeneration, "if (activePages === pages) activePages = null")
		assertTrue(
			submission.indexOf("surfaceView.submitDeckWithResult(built.deck)") <
				submission.indexOf("builtRecoveredDecks.remove(generationId)"),
			"Capacity rejection must retain recovered generation ownership for retry."
		)
		assertTrue(
			submission.indexOf("surfaceView.submitDeckWithResult(built.deck)") <
				submission.indexOf("activePages = accepted.pages"),
			"Recovered pages must become active only after synchronous rejection is impossible."
		)
		assertContains(recovery, "val role = host.currentRecoveredDeckRole()")
		assertContains(recovery, "data class WaitingForSubmissionCapacity(")
		assertContains(recovery, "fun onDeckSubmissionCapacityAvailable(): Boolean")
		assertContains(recovery, "waiting.generationId")
		assertTrue(
			recovery.indexOf("ReaderPageDeckRecoveryState.WaitingForPreparation(") <
				recovery.indexOf("host.submitRecoveredDeck(generationId, role)")
		)
		assertFalse(
			recovery.substringAfter("data class WaitingForBuild(")
				.substringBefore(") : ReaderPageDeckRecoveryState")
				.contains("ReaderDeckSubmissionRole")
		)
		assertFalse(
			recovery.substringAfter("data class WaitingForSubmissionCapacity(")
				.substringBefore(") : ReaderPageDeckRecoveryState")
				.contains("ReaderDeckSubmissionRole"),
			"Capacity retry must recompute the active or pending role."
		)
	}

	@Test
	fun animationSurfacePreservesFoliatePlacementAndStaticDecorations() {
		val source = controllerFile.readText()
		val submit = source
			.substringAfter("private fun submitLibraryDeck(")
			.substringBefore("private fun PreparedPages.page(")

		assertContains(source, "private fun updateSurfaceBounds()")
		assertContains(submit, "updateSurfaceBounds()")
		assertTrue(
			submit.indexOf("updateSurfaceBounds()") <
				submit.indexOf("surfaceView.submitDeck(deck)"),
			"The renderer surface must expose the complete Foliate composition before submission."
		)
		assertContains(source, "params.width = ViewGroup.LayoutParams.MATCH_PARENT")
		assertContains(source, "params.height = ViewGroup.LayoutParams.MATCH_PARENT")
		assertContains(source, "image.layout.displayRectInHost(image.leaf)")
		assertContains(source, "rendererLeftInWindow = location[0]")
		assertContains(source, "PageImage.filler(")
		assertFalse(source.contains("readerPlayLikeCurlPortraitSurfaceWidth("))
	}

	@Test
	fun rendererRejectionAlwaysRestoresSurfaceAfterPublishingItsTerminal() {
		val source = controllerFile.readText()
		val rejection = source
			.substringAfter("override fun onGestureRejected(")
			.substringBefore("override fun onGestureCancelled(")

		assertContains(rejection, "ReaderPageGestureTerminalOutcome.RejectedBoundary")
		assertContains(rejection, "ReaderPageGestureTerminalDetail.RendererRejected(")
		assertTrue(
			rejection.indexOf("finishGesture(") <
				rejection.indexOf("hideSurfaceAfterGesture(gestureId)")
		)
		assertEquals(
			1,
			Regex("hideSurfaceAfterGesture\\(gestureId\\)").findAll(rejection).count(),
			"Every winning renderer rejection must restore the content surface once."
		)
		listOf(
			"dispatchExactVisualPage(",
			"submitLibraryDeck(",
			"promotePendingDeck("
		).forEach { forbidden -> assertFalse(rejection.contains(forbidden)) }
	}

	@Test
	fun boundaryRejectionReturnsLogicalTurnToNativeCoverController() {
		val source = controllerFile.readText()
		val host = hostFile.readText()
		val constructor = source.substringAfter(
			"internal class ReaderPlayLikeCurlFoliateController("
		).substringBefore(") : ReaderPageTapTurnPort")
		val rejection = source.substringAfter(
			"override fun onGestureRejected("
		).substringBefore("override fun onGestureCancelled(")
		val wiring = host.substringAfter(
			"private val playLikeCurlController: ReaderPlayLikeCurlFoliateController ="
		).substringBefore("private val ownershipProbe")

		assertContains(constructor, "private val onBoundaryTurn: (ReaderPageTurnDirection) -> Unit")
		assertContains(rejection, "pageChange: PageChange")
		assertContains(rejection, "PageChange.PREVIOUS -> ReaderPageTurnDirection.Previous")
		assertContains(rejection, "PageChange.NEXT -> ReaderPageTurnDirection.Next")
		assertContains(rejection, "boundaryTurn?.let(onBoundaryTurn)")
		assertTrue(
			rejection.indexOf("hideSurfaceAfterGesture(gestureId)") <
				rejection.indexOf("boundaryTurn?.let(onBoundaryTurn)")
		)
		assertContains(wiring, "onBoundaryTurn = { direction ->")
		assertContains(wiring, "onPageTurnBoundary(direction)")
		assertFalse(wiring.contains("onAction("))
	}

	@Test
	fun rendererCancellationFencesLateFrameRevealAfterItsWinningTerminal() {
		val source = controllerFile.readText()
		val cancellation = source
			.substringAfter("override fun onGestureCancelled(")
			.substringBefore("override fun onSettlementStarted(")

		assertContains(cancellation, "if (!finishGesture(")
		assertContains(cancellation, "hideSurfaceAfterGesture(gestureId)")
		assertTrue(
			cancellation.indexOf("finishGesture(") <
				cancellation.indexOf("hideSurfaceAfterGesture(gestureId)")
		)
	}

	@Test
	fun failedLivePresentationLatchesEveryReadinessAuthorityUnavailable() {
		listOf(
			ReaderPageInteractionState.BlockingInitialPreparation,
			ReaderPageInteractionState.BlockingProfileRegeneration,
			ReaderPageInteractionState.BackgroundPrefetch,
			ReaderPageInteractionState.RefillingWorkingSet,
			ReaderPageInteractionState.Ready,
			ReaderPageInteractionState.Settling
		).forEach { proposed ->
			assertEquals(
				ReaderPageInteractionState.Failed,
				readerPageLivePresentationInteractionState(
					hasFailedLivePresentation = true,
					proposed = proposed
				)
			)
		}
		assertFalse(
			readerPageLivePresentationAvailable(
				hasFailedLivePresentation = true,
				otherwiseAvailable = true
			)
		)

		val source = controllerFile.readText()
		val readiness = source.substringAfter(
			"private fun updateReadiness("
		).substringBefore("private fun requestPrewarmIfIdle(")
		val availability = source.substringAfter(
			"val isAvailable: Boolean"
		).substringBefore("private val canPresentAcceptedGesture")
		assertContains(readiness, "readerPageLivePresentationInteractionState(")
		assertContains(readiness, "failedLivePresentationGeneration != null")
		assertContains(
			availability,
			"hasFailedLivePresentation = failedLivePresentationGeneration != null"
		)
	}

	@Test
	fun terminalContentFailureRemainsReleaseEligibleAfterRecoveryDeckReplacement() {
		assertTrue(
			readerTerminalContentFailureRecoveryStillCurrent(
				destroyed = false,
				failedGenerationMatches = true,
				currentOrdinal = 15,
				destinationOrdinal = 15,
				hasNewerSurfacePresentationOwner = false
			)
		)
		assertFalse(
			readerTerminalContentFailureRecoveryStillCurrent(
				destroyed = true,
				failedGenerationMatches = true,
				currentOrdinal = 15,
				destinationOrdinal = 15,
				hasNewerSurfacePresentationOwner = false
			)
		)
		assertFalse(
			readerTerminalContentFailureRecoveryStillCurrent(
				destroyed = false,
				failedGenerationMatches = true,
				currentOrdinal = 13,
				destinationOrdinal = 15,
				hasNewerSurfacePresentationOwner = false
			)
		)
		assertFalse(
			readerTerminalContentFailureRecoveryStillCurrent(
				destroyed = false,
				failedGenerationMatches = true,
				currentOrdinal = 15,
				destinationOrdinal = 15,
				hasNewerSurfacePresentationOwner = true
			)
		)
	}

	@Test
	fun cancelledNewerGestureAtFailedDestinationReleasesRetainedCurlSurface() {
		assertTrue(
			readerCancelledGestureCanReleaseTerminalContentFailure(
				failedGestureId = 6L,
				cancelledGestureId = 7L,
				currentOrdinal = 10,
				settledOrdinal = 10,
				presentedSurfaceGestureId = 7L
			)
		)
		assertFalse(
			readerCancelledGestureCanReleaseTerminalContentFailure(
				failedGestureId = 7L,
				cancelledGestureId = 7L,
				currentOrdinal = 10,
				settledOrdinal = 10,
				presentedSurfaceGestureId = 7L
			)
		)
		assertFalse(
			readerCancelledGestureCanReleaseTerminalContentFailure(
				failedGestureId = 6L,
				cancelledGestureId = 7L,
				currentOrdinal = 10,
				settledOrdinal = 8,
				presentedSurfaceGestureId = 7L
			)
		)
		assertFalse(
			readerCancelledGestureCanReleaseTerminalContentFailure(
				failedGestureId = 6L,
				cancelledGestureId = 7L,
				currentOrdinal = 10,
				settledOrdinal = 10,
				presentedSurfaceGestureId = 6L
			)
		)

		val source = controllerFile.readText()
		val cancelledSettlement = source.substringAfter(
			"if (pageChange == PageChange.NONE)"
		).substringBefore("updateReadiness(")
		assertContains(
			cancelledSettlement,
			"releaseTerminalContentFailureAfterCancelledGesture("
		)
		assertTrue(
			cancelledSettlement.indexOf(
				"releaseTerminalContentFailureAfterCancelledGesture("
			) < cancelledSettlement.indexOf("hideSurfaceAfterGesture(gestureId)")
		)
	}

	@Test
	fun exhaustedContentFailureFailsClosedInsteadOfRetainingRejectedPixels() {
		val source = controllerFile.readText()
		val release = source.substringAfter(
			"private fun releaseTerminalContentFailure("
		).substringBefore("private fun publishRelocationVisualRecovery(")

		assertContains(release, "blockTerminalContentFailureRecovery(")
		assertContains(release, "hideSurface()")
		assertContains(release, "inlineRasterShield.dismiss()")
		assertContains(
			release,
			"interaction = ReaderPageInteractionState.BlockingProfileRegeneration"
		)
		assertContains(release, "requestPrewarmIfIdle(reason)")
		assertFalse(release.contains("releaseTerminalContentFailureToRetainedCurlSurface"))
		assertFalse(source.contains("private fun releaseTerminalContentFailureToRetainedCurlSurface("))
	}

	@Test
	fun exhaustedContentFailureRevokesRejectedRasterOwnershipBeforeRegeneration() {
		val source = controllerFile.readText()
		val host = hostFile.readText()
		val shield = inlineRasterShieldFile.readText()
		val validation = source.substringAfter(
			"private fun validateLivePresentation("
		).substringBefore("private fun livePresentationValidationIsCurrent(")
		val release = source.substringAfter(
			"private fun releaseTerminalContentFailure("
		).substringBefore("private fun publishRelocationVisualRecovery(")
		val reveal = source.substringAfter(
			"private fun revealSurfaceAfterNextPresentedFrame("
		).substringBefore("fun cancelGestureAfterHostTerminal(")
		val hostLayers = host.substringAfter("init {")
			.substringBefore("fun setShellCoverView(")
		val pointerRouting = host.substringAfter(
			"private fun applyPointerRoute("
		).substringBefore("private fun updateGestureDiagnostic(")
		val replacement = source.substringAfter(
			"private fun replaceRelocationDiagnosticIdentity("
		).substringBefore("private fun completeRelocationVisualHandoff(")
		val longPress = host.substringAfter(
			"private val playLikeCurlGestureDetector"
		).substringAfter("override fun onLongTapConfirmed(event: MotionEvent)")
			.substringBefore("private fun logReaderTapAction(")
		val viewerSuppression = host.substringAfter(
			"private fun suppressViewerContentPointerStream(source: MotionEvent?)"
		).substringBefore("private fun dispatchContentCancel(")

		assertContains(
			source,
			"onRejectedContentReleased = ::releaseTerminalContentFailure"
		)
		assertContains(validation, "retainInlineHandoffSnapshot(request, expectedTarget)")
		assertTrue(
			validation.indexOf("retainInlineHandoffSnapshot(request, expectedTarget)") <
				validation.indexOf("bundleSource.validateLivePresentation(")
		)
		assertContains(validation, "clearRetainedInlineHandoffSnapshot(request)")
		assertContains(release, "takeInlineHandoffSnapshot(request)?.release()")
		assertFalse(release.contains("runCatching"))
		assertContains(release, "hasNewerSurfacePresentationOwner(request.gestureId)")
		assertFalse(release.contains("inlineRasterShield.present("))
		assertFalse(release.contains("hideSurfaceBehindInlineRasterShield()"))
		assertContains(release, "ReaderPageRelocationDiagnosticState.Rejected")
		assertContains(
			release,
			"ReaderPageRelocationDiagnosticRejectionReason.ContentRejected"
		)
		assertContains(release, "failedLivePresentationGeneration = null")
		assertContains(release, "livePresentationRecoveryRequest.request()")
		assertContains(
			release,
			"interaction = ReaderPageInteractionState.BlockingProfileRegeneration"
		)
		assertContains(release, "inlineRasterShield.dismiss()")
		assertContains(release, "hideSurface()")
		assertContains(release, "retainsRejectedSurfaceInputShield = false")
		assertContains(hostLayers, "playLikeCurlController.inlineRasterShieldView")
		assertTrue(
			hostLayers.indexOf("viewerContentContainer") <
				hostLayers.indexOf("playLikeCurlController.inlineRasterShieldView")
		)
		assertTrue(
			hostLayers.indexOf("playLikeCurlController.inlineRasterShieldView") <
				hostLayers.indexOf("playLikeCurlController.surfaceView")
		)
		assertContains(pointerRouting, "shouldDispatchToViewerContent")
		assertContains(pointerRouting, "shouldSuppressViewerContentInput")
		assertContains(
			pointerRouting,
			"revokeViewerContentPointerStreamIfSuppressed(event)"
		)
		assertContains(pointerRouting, "playLikeCurlGestureDetector.onTouchEvent(event)")
		assertContains(viewerSuppression, "shouldDispatchToViewerContent = false")
		assertContains(viewerSuppression, "action = MotionEvent.ACTION_CANCEL")
		assertContains(
			viewerSuppression,
			"viewerContentContainer.dispatchTouchEvent(cancel)"
		)
		assertContains(longPress, "revokeViewerContentPointerStreamIfSuppressed(event)")
		assertContains(longPress, "shouldSuppressViewerContentInput")
		assertTrue(
			longPress.indexOf("shouldSuppressViewerContentInput") <
				longPress.indexOf("onContentLongPress(")
		)
		assertContains(replacement, "failedLivePresentationGeneration?.matches(original)")
		assertContains(replacement, "FailedLivePresentationGeneration(replacement)")
		assertContains(source, "retainsRejectedSurfaceInputShield = true")
		assertContains(
			source,
			"onPresentationOwnershipStarted = onViewerContentInputSuppressed"
		)
		assertContains(shield, "onPresentationOwnershipStarted()")
		assertContains(shield, "snapshot.surfaceRectInWindow")
		assertContains(shield, "getLocationInWindow")
		assertContains(shield, "registerFrameCommitCallback")
		assertContains(shield, "ReaderPageInlineRasterShieldTimeoutMillis")
		assertContains(reveal, "inlineRasterShield.dismiss()")
	}

	@Test
	fun preparationActiveContentRejectionRetainsOneFreshDeckRecovery() {
		val recovery = ReaderPageLivePresentationRecoveryRequest()
		recovery.request()
		assertFalse(
			recovery.shouldForcePreparation(ReaderPagePreparationPhase.Preparing)
		)
		assertTrue(recovery.pending)
		assertTrue(recovery.shouldForcePreparation(ReaderPagePreparationPhase.Ready))
		assertTrue(recovery.claimPreparation())
		assertFalse(recovery.pending)
		assertFalse(recovery.claimPreparation())

		val source = controllerFile.readText()
		val preparation = source.substringAfter(
			"fun onPreparationStateChanged(state: ReaderPagePreparationState)"
		).substringBefore("fun onHostAttached()")
		val recoveryFailure = source.substringAfter(
			"if (reason == ReaderWebViewVisualHandoffFailure.ContentRejected) {"
		).substringBefore("return")
		val recoveryHelper = source.substringAfter(
			"private fun requestLivePresentationRecovery("
		).substringBefore("private fun requestPrewarmIfIdle(")
		val prepareProfile = source.substringAfter(
			"private fun prepareProfile("
		).substringBefore("override fun isCurrentRepairWindow(")
		assertContains(source, "private val livePresentationRecoveryRequest")
		assertContains(recoveryFailure, "requestLivePresentationRecovery(reason)")
		assertContains(
			recoveryHelper,
			"reason: ReaderWebViewVisualHandoffFailure"
		)
		assertContains(recoveryHelper, "livePresentationRecoveryRequest.request()")
		assertContains(
			preparation,
			"livePresentationRecoveryRequest.shouldForcePreparation(state.phase)"
		)
		assertContains(preparation, "refreshPreparedDeck()")
		assertContains(prepareProfile, "livePresentationRecoveryRequest.claimPreparation()")
		assertTrue(
			prepareProfile.indexOf("livePresentationRecoveryRequest.claimPreparation()") <
				prepareProfile.indexOf("val preparation = adapter.prepare(")
		)
	}

	@Test
	fun liveHandoffUsesReceiptBoundTransientValidationBeforeSurfaceHide() {
		val source = controllerFile.readText()
		val validator = source.substringAfter(
			"private fun validateLivePresentation("
		).substringBefore("private fun relocationVisualState()")
		val visualState = source.substringAfter(
			"private fun relocationVisualState()"
		).substringBefore("private fun livePresentationValidationIsCurrent(")

		assertContains(source, "validateContent = ::validateLivePresentation")
		assertContains(validator, "bundleSource.validateLivePresentation(")
		assertContains(validator, "isStillCurrent = {")
		assertContains(validator, "livePresentationValidationIsCurrent(")
		assertContains(validator, "foregroundMutationGeneration")
		assertContains(validator, "onValidated = { result ->")
		assertContains(validator, "val fencedResult = if (")
		assertContains(
			validator,
			"ReaderPageRelocationContentValidationResult.Invalidated"
		)
		assertContains(validator, "onValidated(fencedResult)")
		assertContains(validator, "retainInlineHandoffSnapshot(request, expectedTarget)")
		assertTrue(
			validator.indexOf("retainInlineHandoffSnapshot(request, expectedTarget)") <
				validator.indexOf("onValidated(fencedResult)")
		)
		assertContains(
			validator,
			"onValidated(ReaderPageRelocationContentValidationResult.Invalidated)"
		)
		assertContains(validator, "activeDeckGenerationId == request.textureGeneration")
		assertContains(
			validator,
			"generationOwners[request.textureGeneration] === generationOwner"
		)
		assertContains(validator, "generationOwner.profile.rasterGeneration == request.rasterGeneration")
		assertContains(validator, "relocationQueue.matchesAcknowledgedHead(")
		assertContains(visualState, "activeDeckGenerationId?.takeIf")
		assertContains(visualState, "generationId in preparedDeckGenerations")
		assertContains(visualState, "rasterGeneration = preparedGeneration?.second")
		assertContains(visualState, "textureGeneration = preparedGeneration?.first")
		assertFalse(validator.contains("hideSurface("))
	}

	@Test
	fun liveValidationRequiresNoRasterShieldAndAVisibleCurlSurfaceThroughThirdReceipt() {
		val source = controllerFile.readText()
		val host = hostFile.readText()
		val constructor = source.substringAfter(
			"internal class ReaderPlayLikeCurlFoliateController("
		).substringBefore(") : ReaderPageTapTurnPort")
		val currentness = source.substringAfter(
			"private fun livePresentationValidationIsCurrent("
		).substringBefore("private fun relocationVisualState()")
		val wiring = host.substringAfter(
			"private val playLikeCurlController: ReaderPlayLikeCurlFoliateController ="
		).substringBefore("private val ownershipProbe")

		assertContains(constructor, "private val hasStaticRasterShieldOwnership: () -> Boolean = { true }")
		assertContains(currentness, "!hasStaticRasterShieldOwnership()")
		assertContains(currentness, "surfaceView.visibility == View.VISIBLE")
		assertContains(currentness, "surfaceView.alpha > 0f")
		assertContains(currentness, "surfaceView.isAttachedToWindow")
		assertContains(currentness, "surfaceView.isShown")
		assertContains(currentness, "surfaceView.holder.surface.isValid")
		assertContains(
			wiring,
			"hasStaticRasterShieldOwnership = {\n" +
				"\t\t\tpageRasterPreparationController.hasStaticRasterShieldOwnership()\n" +
				"\t\t}"
		)
	}

	@Test
	fun exhaustedLiveValidationFailsInteractionClosedAndRequestsPreparation() {
		val source = controllerFile.readText()
		val recovery = source.substringAfter(
			"private fun publishRelocationVisualRecovery("
		).substringBefore("private fun dispatchRelocation(")
		val contentFailure = recovery.substringAfter(
			"if (reason == ReaderWebViewVisualHandoffFailure.ContentRejected) {"
		).substringBefore("return")

		assertContains(
			contentFailure,
			"failedLivePresentationGeneration = FailedLivePresentationGeneration(request)"
		)
		assertContains(contentFailure, "interaction = ReaderPageInteractionState.Failed")
		assertContains(contentFailure, "onViewerContentInputSuppressed()")
		assertContains(contentFailure, "requestLivePresentationRecovery(reason)")
		assertFalse(contentFailure.contains("hideSurface("))
		assertFalse(contentFailure.contains("token"))
		val preparedInteraction = source.substringAfter(
			"private fun preparedInteractionState()"
		).substringBefore("private fun blockingPreparationState()")
		assertContains(
			preparedInteraction,
			"failedLivePresentationGeneration != null"
		)
		val deckPrepared = source.substringAfter(
			"override fun onDeckPrepared(generationId: Long)"
		).substringBefore("override fun onDeckRejected(")
		assertFalse(deckPrepared.contains("failedLivePresentationGeneration = null"))
		val completed = source.substringAfter(
			"private fun completeRelocationVisualHandoff("
		).substringBefore("private fun publishRelocationVisualRecovery(")
		assertContains(completed, "if (failedLivePresentationGeneration != null)")
		assertContains(completed, "failedLivePresentationGeneration = null")
		assertContains(completed, "interaction = preparedInteractionState()")
	}

	@Test
	fun terminalNonContentVisualRecoveryFailsExactLiveOwnershipAndDrainsOnce() {
		val source = controllerFile.readText()
		val recovery = source.substringAfter(
			"private fun publishRelocationVisualRecovery("
		).substringBefore("private fun dispatchRelocation(")
		val contentRecovery = recovery.substringAfter(
			"if (reason == ReaderWebViewVisualHandoffFailure.ContentRejected) {"
		).substringBefore("return")
		val liveDispatch = source.substringAfter(
			"internal class ReaderPageRelocationLiveDispatchCoordinator("
		).substringBefore("internal class ReaderPlayLikeCurlFoliateController(")
		val failure = liveDispatch.substringAfter("fun fail(")
			.substringBefore("fun releaseAll()")
		val rejection = source.substringAfter(
			"private fun rejectDispatchedRelocation("
		).substringBefore("private fun releaseGeneration(")
		val invalidation = source.substringAfter("fun invalidate(")
			.substringBefore("private val destroyFence")

		assertFalse(contentRecovery.contains("relocationLiveDispatchCoordinator.fail("))
		assertContains(
			recovery,
			"relocationLiveDispatchCoordinator.fail(\n" +
				"\t\t\t\trequest,\n" +
				"\t\t\t\tReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated"
		)
		assertFalse(recovery.contains("requestPrewarmIfIdle("))
		assertContains(failure, "val entry = remove(request) ?: return false")
		assertContains(failure, "foregroundWebViewOwnership.releaseLive(entry.claim)")
		assertEquals(1, Regex("claims\\.remove\\(token\\)").findAll(failure).count())
		assertEquals(
			1,
			Regex("mutationGenerations\\.remove\\(token\\)").findAll(failure).count()
		)
		assertEquals(
			1,
			Regex("foregroundWebViewOwnership\\.releaseLive\\(entry\\.claim\\)")
				.findAll(failure).count()
		)
		assertFalse(recovery.contains("foregroundWebViewOwnership.releaseLive("))
		assertFalse(recovery.contains("relocationQueue.cancelAll("))
		assertContains(rejection, "relocationRejectionReason = reason")
		assertContains(invalidation, "drainRelocationOwnership(")
	}

	@Test
	fun reentrantCommittedTerminalDrainFailsLateTransferBeforeItCanOrphanOwnership() {
		val source = controllerFile.readText()
		val transfer = source.substringAfter(
			"private fun transferAndDispatchRelocation("
		).substringBefore("private fun dispatchRelocation(")
		val liveDispatch = source.substringAfter(
			"internal class ReaderPageRelocationLiveDispatchCoordinator("
		).substringBefore("internal class ReaderPlayLikeCurlFoliateController(")
		val failure = liveDispatch.substringAfter("fun fail(")
			.substringBefore("fun releaseAll()")

		assertContains(
			transfer,
			"if (relocationQueue.occupiedCount() == 0) {"
		)
		assertContains(
			transfer,
			"relocationLiveDispatchCoordinator.fail(\n" +
				"\t\t\t\t\trequest,\n" +
				"\t\t\t\t\tReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated"
		)
		assertTrue(
			transfer.indexOf("relocationLiveDispatchCoordinator.transfer(request, claim)") <
				transfer.indexOf("if (relocationQueue.occupiedCount() == 0) {")
		)
		assertTrue(
			transfer.indexOf("if (relocationQueue.occupiedCount() == 0) {") <
				transfer.indexOf("dispatchNextRelocation()")
		)
		assertContains(failure, "val entry = remove(request) ?: return false")
		assertContains(failure, "claims.remove(token)")
		assertContains(failure, "mutationGenerations.remove(token)")
		assertContains(failure, "foregroundWebViewOwnership.releaseLive(entry.claim)")
		assertFalse(transfer.contains("foregroundWebViewOwnership.releaseLive("))
	}

	@Test
	fun rejectedRapidPointerCannotHideCommittedVisualHandoffShield() {
		val source = controllerFile.readText()
		val rejection = source
			.substringAfter("override fun onGestureRejected(")
			.substringBefore("override fun onGestureCancelled(")
		val unavailable = source
			.substringAfter("if (!isAvailable || metadata == null) {")
			.substringBefore("return ReaderPageCurlDispatchResult.TerminalPublished")
		val gestureHide = source
			.substringAfter("private fun hideSurfaceAfterGesture(")
			.substringBefore("private fun finalizeHandoffPresentation(")
		val newerOwner = source
			.substringAfter("private fun hasNewerSurfacePresentationOwner(")
			.substringBefore("private fun finalizeHandoffPresentation(")
		val handoffFinalization = source
			.substringAfter("private fun finalizeHandoffPresentation(")
			.substringBefore("private fun hideSurface()")

		assertContains(source, "finalizePresentation = ::finalizeHandoffPresentation")
		assertContains(rejection, "hideSurfaceAfterGesture(gestureId)")
		assertContains(unavailable, "hideSurfaceAfterGesture(gestureId)")
		assertContains(gestureHide, "relocationQueue.ownershipSnapshot().queued > 0")
		assertContains(gestureHide, "presentedFrameGestureId != gestureId")
		assertContains(gestureHide, "presentedSurfaceGestureId != gestureId")
		assertContains(handoffFinalization, "request.gestureId")
		assertContains(handoffFinalization, "hasNewerSurfacePresentationOwner(request.gestureId)")
		assertContains(newerOwner, "owner > gestureId")
		assertTrue(
			gestureHide.indexOf("relocationQueue.ownershipSnapshot().queued > 0") <
				gestureHide.indexOf("hideSurface()"),
			"A rejected rapid pointer must not expose WebView while a committed handoff owns the shield."
		)
	}

	@Test
	fun everyRendererFailurePublishesSpecificTerminalBeforeUnsafeLifecycleCancellation() {
		val source = controllerFile.readText()
		val failure = source
			.substringAfter("override fun onRenderFailure(failure: RenderFailure) {")
			.substringBefore("\n\t\t})")

		assertContains(failure, "readerRenderFailureOwnsCurrentPresentation(")
		assertContains(failure, "Ignored superseded PlayLikeCurl render failure")
		assertTrue(
			failure.indexOf("readerRenderFailureOwnsCurrentPresentation(") <
				failure.indexOf("updateReadiness("),
			"A superseded generation failure must be rejected before it can fail the current presentation."
		)
		assertContains(failure, "finishActiveGesture(")
		assertContains(failure, "onUnsafeLifecycleEvent(")
		assertFalse(
			failure.contains("if (!failure.isRecoverable())"),
			"Recoverable renderer failures must still cancel the active native settlement as GlFailed."
		)
		assertContains(failure, "ReaderPageHostLifecycleEvent.UnsafeContextLost")
		assertContains(failure, "ReaderPageHostLifecycleEvent.GlFailed")
		assertTrue(
			failure.indexOf("finishActiveGesture(") < failure.indexOf("onUnsafeLifecycleEvent("),
			"The renderer-specific terminal must win before lifecycle cancellation."
		)
	}

	@Test
	fun settlementCompletionRequiresWinningTerminalBeforeNavigationOrDeckMutation() {
		val source = controllerFile.readText()
		val settlement = source
			.substringAfter("override fun onSettlementCompleted(")
			.substringBefore("override fun onSettlementCancelled(")
		val nonCommittedFence = settlement.indexOf("if (!finishGesture(")
		val committedFence = settlement.indexOf("relocationGestureCoordinator.commit(")

		assertTrue(
			nonCommittedFence >= 0,
			"Snap-back settlement callbacks must be fenced by the terminal CAS."
		)
		assertTrue(
			nonCommittedFence < settlement.indexOf("discardPendingDeck("),
			"Snap-back side effects must occur only after the terminal fence wins."
		)
		assertTrue(
			committedFence >= 0,
			"Committed settlement must transfer ownership through the relocation coordinator."
		)
		assertContains(settlement, "publishCommittedTerminal = {")
		assertContains(settlement, "dispatch = ::transferAndDispatchRelocation")
		assertTrue(
			committedFence < settlement.indexOf("currentOrdinal = currentPageOrdinal"),
			"Committed state must advance only after relocation publication succeeds."
		)
	}

	@Test
	fun staleSettlementCancellationHasNoPostCasEffects() {
		val source = controllerFile.readText()
		val cancellation = source
			.substringAfter("override fun onSettlementCancelled(")
			.substringBefore("override fun onRenderFailure(")
		val fence = cancellation.indexOf("if (!finishGesture(")

		assertTrue(fence >= 0, "Late settlement cancellation must be fenced by the terminal CAS.")
		listOf(
			"discardPendingDeck(",
			"updateReadiness(",
			"hideSurfaceAfterGesture(gestureId)"
		).forEach { sideEffect ->
			assertTrue(
				fence < cancellation.indexOf(sideEffect),
				"Cancellation side effect $sideEffect must occur only after the terminal fence wins."
			)
		}
	}

	@Test
	fun rendererPointerAdmissionRequiresAPreparedActiveGeneration() {
		val source = controllerFile.readText()
		val touch = source
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val reveal = source
			.substringAfter("private fun revealSurfaceAfterNextPresentedFrame(")
			.substringBefore("fun cancelGesture(")
		val hiddenReveal = reveal.substringAfter("presentedFrameGestureId = gestureId")
		val tap = source
			.substringAfter("private fun startTapTurn(")
			.substringBefore("private fun revealSurfaceAfterNextPresentedFrame(")
		val admittedTap = tap
			.substringAfter("ReaderPageRelocationStartResult.Admitted -> {")
			.substringBefore("is ReaderPageRelocationStartResult.TerminalPublished -> {")
		val hide = source
			.substringAfter("private fun hideSurface()")
			.substringBefore("private fun preparedInteractionState(")
		val presentation = source
			.substringAfter("private val canPresentAcceptedGesture: Boolean")
			.substringBefore("fun setPageOperationPolicy(")

		assertContains(presentation, "pageOperationPolicy.continueActivePointer")
		assertContains(presentation, "pageOperationPolicy.continueSettlement")
		assertContains(touch, "if (!isAvailable || metadata == null)")
		assertContains(touch, "relocationGestureCoordinator.start(")
		assertContains(source, "deckRecoveryCoordinator.canAcceptPointer")
		assertContains(reveal, "canPresentAcceptedGesture")
		assertContains(reveal, "surfaceView.requestNextPresentedFrame")
		assertContains(reveal, "activeGestureId != gestureId")
		assertContains(reveal, "gestureStillOwnsReveal")
		assertFalse(tap.contains("surfaceView.alpha = 1f"))
		assertContains(admittedTap, "revealSurfaceAfterNextPresentedFrame(gestureId)")
		assertContains(reveal, "if (surfaceView.alpha != 0f)")
		assertTrue(
			hiddenReveal.indexOf("surfaceView.requestNextPresentedFrame") <
				hiddenReveal.indexOf("surfaceView.alpha = 1f"),
			"A hidden surface must not be revealed until its requested GL frame is presented."
		)
		assertContains(hide, "surfaceView.cancelPresentedFrameRequest")
		assertTrue(
			hide.indexOf("surfaceView.cancelPresentedFrameRequest") <
				hide.indexOf("surfaceView.alpha = 0f"),
			"Hiding must fence a late frame-presentation callback."
		)
	}

	@Test
	fun unresolvedVisualHandoffFencesTheNextPageTurn() {
		val source = controllerFile.readText()
		val availability = source
			.substringAfter("private val isAvailable: Boolean")
			.substringBefore("private val canPresentAcceptedGesture: Boolean")
		val admission = source
			.substringAfter("internal fun readerPlayLikeCurlTurnAdmissionAvailable(")
			.substringBefore("internal fun readerTerminalContentFailureRecoveryStillCurrent(")
		val touch = source
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val tap = source
			.substringAfter("private fun startTapTurn(")
			.substringBefore("private fun revealSurfaceAfterNextPresentedFrame(")

		assertContains(availability, "readerPlayLikeCurlTurnAdmissionAvailable(")
		assertContains(admission, "!relocationQueue.hasInFlightHead()")
		assertContains(touch, "if (!isAvailable || metadata == null)")
		assertContains(tap, "if (!isAvailable || metadata == null)")
	}

	@Test
	fun delayedTapTurnPublishesThePolicySpecificUnavailableOutcome() {
		val source = controllerFile.readText()
		val startTapTurn = source
			.substringAfter("private fun startTapTurn(")
			.substringBefore("private fun revealSurfaceAfterNextPresentedFrame(")
		val unavailable = startTapTurn
			.substringAfter("if (!isAvailable || metadata == null) {")
			.substringBefore("return when (")

		assertContains(unavailable, "unavailableGestureOutcome()")
		assertFalse(
			unavailable.contains("ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable"),
			"Delayed taps must preserve RejectedPreparing and RejectedSettling policy outcomes."
		)
	}

	@Test
	fun unavailableClaimRestoresTheSurfaceAfterPublishingItsTerminal() {
		val source = controllerFile.readText()
		val touch = source
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val unavailable = touch
			.substringAfter("if (!isAvailable || metadata == null) {")
			.substringBefore("return ReaderPageCurlDispatchResult.TerminalPublished")

		assertContains(unavailable, "publishGestureTerminal(")
		assertContains(unavailable, "hideSurfaceAfterGesture(gestureId)")
		assertTrue(
			unavailable.indexOf("publishGestureTerminal(") <
				unavailable.indexOf("hideSurfaceAfterGesture(gestureId)")
		)
	}

	@Test
	fun controllerPassesDirectionAndParityToEverySpreadAuthority() {
		val source = controllerFile.readText()
		val settlement = source
			.substringAfter("override fun onSettlementStarted(")
			.substringBefore("override fun onSettlementCompleted(")
		val profileMapping = source
			.substringAfter("private fun ReaderPlayLikeCurlRasterProfile.preparedPageIndices(")
			.substringBefore("private fun publishProtectedWindow(")
		val deckFactory = source
			.substringAfter("private fun buildLibraryDeck(")
			.substringBefore("private fun setSurfaceReadingDirection(")
		val submit = source
			.substringAfter("private fun submitLibraryDeck(")
			.substringBefore("private fun updateSurfaceBounds(")

		listOf(settlement, profileMapping, deckFactory).forEach { callSite ->
			assertContains(callSite, "readerDirection =")
			assertContains(callSite, "spreadAnchorParity =")
		}
		assertContains(submit, "setSurfaceReadingDirection(pages.profile)")
		assertTrue(
			submit.indexOf("setSurfaceReadingDirection(pages.profile)") <
				submit.indexOf("surfaceView.submitDeck(deck)")
		)
	}

	@Test
	fun nativeHostMountsAndDrivesTheImportedSurface() {
		val source = hostFile.readText()

		assertContains(source, "ReaderPlayLikeCurlFoliateController")
		assertContains(source, "playLikeCurlController.surfaceView")
		assertContains(source, "playLikeCurlController.onHostContentReady()")
		assertContains(source, "private fun dispatchPlayLikeCurlPointerEvent(")
		assertContains(source, "private fun applyPointerRoute(")
		assertContains(source, "playLikeCurlController.onPageTouchEvent(")
		assertContains(source, "route.gestureId")
		assertContains(source, "playLikeCurlController.synchronizeVisualPageIndex(")
		assertContains(source, "acknowledgement")
	}

	@Test
	fun productionGesturePathDoesNotDriveTheRetiredRenderer() {
		val source = hostFile.readText()
		val importedDispatch = source
			.substringAfter("private fun dispatchPlayLikeCurlPointerEvent(")
			.substringBefore("private fun applyPointerRoute(")
		val apply = source
			.substringAfter("private fun applyPointerRoute(")
			.substringBefore("private fun dispatchContentCancel(")

		assertFalse(importedDispatch.contains("pageTurnController.update("))
		assertFalse(importedDispatch.contains("pageTurnController.release("))
		assertFalse(apply.contains("pageTurnController.update("))
		assertFalse(apply.contains("pageTurnController.release("))
	}

	@Test
	fun importedSurfaceClaimsOnlyAfterProvisionalContentDownIsCancelled() {
		val source = hostFile.readText()
		val apply = source
			.substringAfter("private fun applyPointerRoute(")
			.substringBefore("private fun dispatchContentCancel(")
		val content = apply
			.substringAfter("ReaderPagePointerRoute.Content -> {")
			.substringBefore("is ReaderPagePointerRoute.ContentTerminal -> {")
		val claim = apply
			.substringAfter("is ReaderPagePointerRoute.ClaimCurl -> {")
			.substringBefore("is ReaderPagePointerRoute.Curl -> {")
		val controller = controllerFile.readText()
		val touch = controller
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val nonDown = touch
			.substringAfter("if (event.actionMasked != MotionEvent.ACTION_DOWN) {")
			.substringBefore("return ReaderPageCurlDispatchResult.Accepted")

		assertContains(content, "MotionEvent.obtain(event)")
		assertContains(content, "viewerContentContainer.dispatchTouchEvent(event)")
		assertFalse(content.contains("super.dispatchTouchEvent(event)"))
		assertFalse(content.contains("playLikeCurlController.onPageTouchEvent("))
		val cancelIndex = claim.indexOf("dispatchContentCancel(event)")
		val downIndex = claim.indexOf("val downResult =")
		val accepted = claim
			.substringAfter("ReaderPageCurlDispatchResult.Accepted -> {")
			.substringBefore("ReaderPageCurlDispatchResult.TerminalPublished -> {")
		val terminal = claim.substringAfter("ReaderPageCurlDispatchResult.TerminalPublished -> {")
		assertTrue(cancelIndex >= 0)
		assertTrue(downIndex > cancelIndex)
		assertFalse(claim.contains("showSurfaceForGesture"))
		assertContains(accepted, "dispatchClaimedReaderPageCurlEvent(event)")
		assertContains(accepted, "dispatchedEvent,")
		assertContains(accepted, "playLikeCurlGestureOwned = true")
		assertFalse(terminal.contains("playLikeCurlController.onPageTouchEvent("))
		assertContains(terminal, "playLikeCurlGestureOwned = false")
		assertContains(nonDown, "revealSurfaceAfterNextPresentedFrame(gestureId)")
		assertTrue(
			nonDown.indexOf("surfaceView.onPageTouchEvent(event, gestureId)") <
				nonDown.indexOf("revealSurfaceAfterNextPresentedFrame(gestureId)"),
			"The claimed MOVE must update deformation before presentation is armed."
		)
	}

	@Test
	fun classifiedImportedDragCannotFallThroughToTapNavigation() {
		val source = hostFile.readText()
		val claim = source
			.substringAfter("is ReaderPagePointerRoute.ClaimCurl -> {")
			.substringBefore("is ReaderPagePointerRoute.Curl -> {")
		val curl = source
			.substringAfter("is ReaderPagePointerRoute.Curl -> {")
			.substringBefore("is ReaderPagePointerRoute.Terminal -> {")
		val typedTap = source
			.substringAfter("private fun onPlayLikeCurlSingleTapConfirmed(")
			.substringBefore("private fun dispatchLegacySingleTapAction(")

		assertContains(claim, "dispatchContentCancel(event)")
		assertContains(claim, "recycleRetainedContentDown()")
		assertContains(claim, "playLikeCurlGestureOwned = true")
		assertFalse(claim.contains("dispatchLegacySingleTapAction("))
		assertFalse(curl.contains("super.dispatchTouchEvent(event)"))
		assertFalse(curl.contains("playLikeCurlGestureDetector.onTouchEvent(event)"))
		assertContains(typedTap, "?: return false")
		assertFalse(typedTap.contains("dispatchLegacySingleTapAction("))
	}

	@Test
	fun acceptedGestureFrameDoesNotStartRasterOrTexturePreparation() {
		val controller = controllerFile.readText()
		val touch = controller
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val host = hostFile.readText()
		val importedDispatch = host
			.substringAfter("private fun dispatchPlayLikeCurlPointerEvent(")
			.substringBefore("private fun applyPointerRoute(")
		val apply = host
			.substringAfter("private fun applyPointerRoute(")
			.substringBefore("private fun dispatchContentCancel(")
		val gesturePath = touch + importedDispatch + apply

		assertContains(touch, "surfaceView.onPageTouchEvent(event, gestureId)")
		listOf(
			"onRequestPrewarm()",
			"refreshPreparedDeck()",
			"refillDecodedWorkingSet(",
			"rasterAdapter",
			"submitDeck(",
			"requestPageTurnPrewarmWhenReady()",
			"requestPageRasterRepair(",
			"BitmapFactory",
			"File("
		).forEach { forbidden ->
			assertFalse(
				gesturePath.contains(forbidden),
				"Gesture-frame path must not perform preparation work: $forbidden"
			)
		}
	}

	@Test
	fun rendererCallbacksCarryTheOriginatingGestureIdentityToOneTerminalLedger() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()

		assertContains(controller, "surfaceView.onPageTouchEvent(event, gestureId)")
		assertContains(controller, "surfaceView.turn(pageChange, gestureId)")
		assertContains(controller, "surfaceView.cancelGesture(gestureId)")
		assertTrue(
			Regex("override fun onGestureRejected\\(\\s*gestureId: Long,").containsMatchIn(controller),
			"Gesture rejection callbacks must preserve the originating gesture ID."
		)
		assertTrue(
			Regex("override fun onGestureCancelled\\(\\s*gestureId: Long,").containsMatchIn(controller),
			"Gesture cancellation callbacks must preserve the originating gesture ID."
		)
		assertTrue(
			Regex("override fun onSettlementCompleted\\(\\s*gestureId: Long,").containsMatchIn(controller),
			"Settlement callbacks must preserve the originating gesture ID."
		)
		assertContains(controller, "finishGesture(")
		val facade = inputSettlementHostControllerFile.readText()
		assertContains(host, "private fun completePageGesture(")
		assertContains(host, "pageInputSettlementHostController.complete(")
		assertContains(host, "detail = detail")
		assertContains(host, "won = won")
		assertFalse(host.contains("private val pageGestureLifecycle"))
		assertFalse(host.contains("pageGestureLifecycle.completeGesture("))
		assertContains(facade, "pointerRouter.complete(gestureId, outcome)")
		assertFalse(facade.contains("lifecycle.completeGesture("))
	}

	@Test
	fun pointerInterruptionDoesNotRepublishAHostOwnedCurlTerminal() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()
		val cancelAfterHostTerminal = controller
			.substringAfter("fun cancelGestureAfterHostTerminal(gestureId: Long) {")
			.substringBefore("\n\t}")
		val publishTerminal = controller
			.substringAfter("private fun publishGestureTerminal(")
			.substringBefore("\n\t}")
		val pointerInterruption = host
			.substringAfter("override fun cancelForPointerInterruption(gestureId: Long) {")
			.substringBefore("\n\t\t\t}")

		assertContains(pointerInterruption, "cancelGestureAfterHostTerminal(gestureId)")
		assertFalse(pointerInterruption.contains("cancelGesture(gestureId)"))
		assertContains(cancelAfterHostTerminal, "hostOwnedTerminalGestureIds.add(gestureId)")
		assertContains(cancelAfterHostTerminal, "cancelGesture(gestureId)")
		assertContains(cancelAfterHostTerminal, "hostOwnedTerminalGestureIds.remove(gestureId)")
		assertContains(publishTerminal, "gestureId in hostOwnedTerminalGestureIds -> true")
	}

	@Test
	fun tapTurnReturnContractHasOneControllerCallbackPublicationOwner() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()
		val inputFacade = inputSettlementHostControllerFile.readText()
		val tapFacade = tapTurnControllerFacadeFile.readText()
		val start = controller
			.substringAfter("override fun start(")
			.substringBefore("private fun startTapTurn(")
		val startTapTurn = controller
			.substringAfter("private fun startTapTurn(")
			.substringBefore("private fun revealSurfaceAfterNextPresentedFrame(")
		val finish = controller
			.substringAfter("private fun finishGesture(")
			.substringBefore("private fun finishActiveGesture(")
		val facadeTurn = tapFacade.substringAfter("fun turn(")
		val typedDispatch = host
			.substringAfter("private fun dispatchPlayLikeCurlSingleTapAction(")
			.substringBefore("override fun onInterceptTouchEvent")
		val typedCallback = host
			.substringAfter("private fun onPlayLikeCurlSingleTapConfirmed(")
			.substringBefore("private fun dispatchLegacySingleTapAction(")
		val adapter = host
			.substringAfter("private fun completeHostGesture(")
			.substringBefore("private fun logGestureTerminal(")

		assertContains(controller, "internal sealed interface ReaderPageGestureTerminalDetail")
		assertContains(controller, "internal sealed interface ReaderPageTurnStartResult")
		assertContains(controller, "val detail: ReaderPageGestureTerminalDetail")
		assertFalse(controller.contains("val detail: String"))
		assertFalse(controller.contains("source=\$sourceLogicalPageId"))
		assertFalse(controller.contains("target=\$targetLogicalPageId"))
		assertFalse(controller.contains("page=\$currentLogicalPageId"))
		assertFalse(controller.contains("failure.message"))
		assertFalse(controller.contains("failure.cause"))
		assertContains(start, "): ReaderPageTurnStartResult")
		assertContains(start, "tapTurnGestureId = gestureId")
		assertContains(start, "startTapTurn(pageChange, gestureId)")
		assertContains(startTapTurn, "relocationGestureCoordinator.start(")
		assertContains(startTapTurn, "surfaceView.turn(pageChange, gestureId)")
		assertContains(startTapTurn, "ReaderPageTurnStartResult.Settling")
		assertContains(startTapTurn, "ReaderPageRelocationStartResult.TerminalPublished")
		assertContains(finish, "relocationGestureCoordinator.finish(gestureId, outcome, detail)")
		assertContains(finish, "publishGestureTerminal(gestureId, outcome, detail)")
		assertContains(finish, "onGestureTerminal(gestureId, outcome, detail)")
		assertFalse(controller.contains("ReaderPageInputSettlementHostController"))
		assertContains(facadeTurn, "port.start(gestureId, pageChange)")
		assertContains(facadeTurn, "publishTerminal(gestureId, outcome, detail)")
		assertContains(typedDispatch, "tapTurnController.turn(gestureId, pageChange)")
		assertFalse(typedDispatch.contains("playLikeCurlController.turn("))
		assertContains(typedDispatch, "ReaderPageTapDispatchResult.TerminalPublished")
		assertContains(typedCallback, "ReaderPageTapDispatchResult.TerminalPublished -> Unit")
		assertContains(adapter, "pageInputSettlementHostController.complete(")
		assertContains(adapter, "detail = detail")
		assertContains(adapter, "won = won")
		assertContains(inputFacade, "pointerRouter.complete(gestureId, outcome)")
	}

	@Test
	fun productionHostFreezesDispatchModeAndRoutesEveryImportedAction() {
		val hostSource = hostFile.readText()
		val outerDispatch = hostSource
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean")
			.substringBefore("private fun dispatchLegacyReaderPointerEvent(")
		val importedDispatch = hostSource
			.substringAfter("private fun dispatchPlayLikeCurlPointerEvent(")
			.substringBefore("private fun applyPointerRoute(")
		val pointerMapping = importedDispatch
			.substringAfter("val pointerEvent: ReaderPageHostPointerEvent? =")
			.substringBefore("val pointerDispatch =")
		val expectedMappings = linkedMapOf(
			"MotionEvent.ACTION_DOWN" to "ReaderPageHostPointerEvent.Down(",
			"MotionEvent.ACTION_MOVE" to "ReaderPageHostPointerEvent.Move(",
			"MotionEvent.ACTION_UP" to "ReaderPageHostPointerEvent.PositionedUp(",
			"MotionEvent.ACTION_CANCEL" to "ReaderPageHostPointerEvent.Cancel",
			"MotionEvent.ACTION_POINTER_DOWN" to "ReaderPageHostPointerEvent.SecondaryPointerDown",
			"MotionEvent.ACTION_POINTER_UP" to "ReaderPageHostPointerEvent.SecondaryPointerUp"
		)

		assertContains(outerDispatch, "if (event.actionMasked == MotionEvent.ACTION_DOWN)")
		assertContains(outerDispatch, "shouldUsePlayLikeCurlPointerRouter()")
		assertContains(outerDispatch, "ReaderPagePhysicalDispatchMode.PlayLikeCurl ->")
		assertContains(outerDispatch, "dispatchPlayLikeCurlPointerEvent(event)")
		assertContains(outerDispatch, "ReaderPagePhysicalDispatchMode.Legacy ->")
		assertContains(outerDispatch, "dispatchLegacyReaderPointerEvent(event)")
		assertEquals(
			1,
			Regex("shouldUsePlayLikeCurlPointerRouter\\(\\)").findAll(outerDispatch).count()
		)
		expectedMappings.forEach { (androidAction, typedEvent) ->
			val branch = Regex(
				Regex.escape(androidAction) + "\\s*->\\s*" + Regex.escape(typedEvent)
			)
			assertTrue(branch.containsMatchIn(pointerMapping), "$androidAction must map to $typedEvent")
		}
		assertContains(pointerMapping, "downTimeMillis = event.downTime")
		assertContains(pointerMapping, "x = event.x")
		assertContains(pointerMapping, "y = event.y")
		assertContains(
			pointerMapping,
			"touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()"
		)
		assertContains(importedDispatch, "pageInputSettlementHostController::dispatchPointer")
		assertFalse(hostSource.contains("currentPageGestureId"))
		assertFalse(hostSource.contains("pageGestureLifecycle"))
		assertContains(hostSource, "takeDelayedTap(")
		assertContains(hostSource, "takeOldestDelayedTap(")
		assertContains(hostSource, "claimContentAction(")
	}

	@Test
	fun productionHostAppliesRoutesWithoutContentOrCurlFallthrough() {
		val hostSource = hostFile.readText()
		val importedDispatch = hostSource
			.substringAfter("private fun dispatchPlayLikeCurlPointerEvent(")
			.substringBefore("private fun applyPointerRoute(")
		val intercept = hostSource
			.substringAfter("override fun onInterceptTouchEvent(event: MotionEvent): Boolean")
			.substringBefore("override fun onTouchEvent(event: MotionEvent): Boolean")
		val apply = hostSource
			.substringAfter("private fun applyPointerRoute(")
			.substringBefore("private fun dispatchContentCancel(")
		val content = apply
			.substringAfter("ReaderPagePointerRoute.Content -> {")
			.substringBefore("is ReaderPagePointerRoute.ContentTerminal -> {")
		val contentTerminal = apply
			.substringAfter("is ReaderPagePointerRoute.ContentTerminal -> {")
			.substringBefore("is ReaderPagePointerRoute.ClaimCurl -> {")
		val claimCurl = apply
			.substringAfter("is ReaderPagePointerRoute.ClaimCurl -> {")
			.substringBefore("is ReaderPagePointerRoute.Curl -> {")
		val terminal = apply
			.substringAfter("is ReaderPagePointerRoute.Terminal -> {")
			.substringBefore("ReaderPagePointerRoute.Consume ->")
		val viewerSuppression = hostSource
			.substringAfter(
				"private fun suppressViewerContentPointerStream(source: MotionEvent?)"
			)
			.substringBefore("private fun dispatchContentCancel(")
		val contentCancel = hostSource
			.substringAfter("private fun dispatchContentCancel(")
			.substringBefore("private fun recycleRetainedContentDown()")

		assertContains(importedDispatch, "applyPointerRoute(event, pointerDispatch)")
		assertContains(intercept, "ReaderPagePhysicalDispatchMode.PlayLikeCurl")
		assertContains(intercept, "return false")
		assertContains(intercept, "interceptLegacyReaderPointerEvent(event)")
		assertContains(content, "MotionEvent.obtain(event)")
		assertContains(content, "viewerContentContainer.dispatchTouchEvent(event)")
		assertFalse(
			content.contains("val handled ="),
			"The provisional router must retain Android stream ownership even when content rejects DOWN."
		)
		assertContains(content, "\n\t\t\ttrue\n\t\t}")
		assertFalse(content.contains("super.dispatchTouchEvent(event)"))
		assertContains(content, "playLikeCurlGestureDetector.onTouchEvent(event)")
		assertFalse(content.contains("legacyGestureDetector.onTouchEvent(event)"))
		assertContains(contentTerminal, "viewerContentContainer.dispatchTouchEvent(event)")
		assertFalse(contentTerminal.contains("super.dispatchTouchEvent(event)"))
		assertContains(contentTerminal, "completeHostGesture(")
		assertContains(terminal, "dispatchContentCancel(event)")
		assertFalse(terminal.contains("super.dispatchTouchEvent(event)"))
		assertContains(contentCancel, "suppressViewerContentPointerStream(source)")
		assertContains(viewerSuppression, "viewerContentContainer.dispatchTouchEvent(cancel)")
		assertFalse(viewerSuppression.contains("super.dispatchTouchEvent(cancel)"))
		assertContains(apply, "ReaderPagePointerRoute.Consume -> true")
		assertContains(apply, "ReaderPagePointerRoute.Ignore -> true")

		val cancelIndex = claimCurl.indexOf("dispatchContentCancel(event)")
		val downIndex = claimCurl.indexOf("originalDown,")
		val moveIndex = claimCurl.indexOf("dispatchClaimedReaderPageCurlEvent(event)")
		val ownerIndex = claimCurl.indexOf("playLikeCurlGestureOwned = true")
		assertTrue(cancelIndex >= 0)
		assertTrue(downIndex > cancelIndex)
		assertTrue(moveIndex > downIndex)
		assertTrue(ownerIndex > moveIndex)
		assertFalse(claimCurl.contains("showSurfaceForGesture"))
		assertContains(claimCurl, "event.actionMasked == MotionEvent.ACTION_UP")
		assertContains(claimCurl, "clearPlayLikeCurlPointerTapFlagsAfterUp()")
	}

	@Test
	fun pendingPreparationRestorationSuppressesRawViewerContent() {
		val preparation = rasterPreparationFile.readText()
		val host = hostFile.readText()
		val pointerRouting = host
			.substringAfter("private fun applyPointerRoute(")
			.substringBefore("private fun updateGestureDiagnostic(")
		val longPress = host
			.substringAfter("override fun onLongTapConfirmed(event: MotionEvent)")
			.substringBefore("private fun logReaderTapAction(")
		val viewerSuppression = host
			.substringAfter("private fun revokeViewerContentPointerStreamIfSuppressed(")
			.substringBefore("private fun suppressViewerContentPointerStream()")

		assertContains(
			preparation,
			"internal val shouldSuppressViewerContentInput: Boolean"
		)
		assertContains(preparation, "get() = pendingVisualRestorations.isNotEmpty()")
		assertContains(host, "private val shouldSuppressViewerContentInput: Boolean")
		assertContains(
			host,
			"playLikeCurlController.shouldSuppressViewerContentInput ||"
		)
		assertContains(
			host,
			"pageRasterPreparationController.shouldSuppressViewerContentInput"
		)
		assertContains(pointerRouting, "!shouldSuppressViewerContentInput")
		assertContains(longPress, "if (shouldSuppressViewerContentInput) return")
		assertContains(viewerSuppression, "if (!shouldSuppressViewerContentInput) return")
	}

	@Test
	fun productionSeparatesLegacyAndTypedDelayedTapListeners() {
		val hostSource = hostFile.readText()
		val legacy = hostSource
			.substringAfter("private fun onLegacySingleTapConfirmed(")
			.substringBefore("private fun onPlayLikeCurlSingleTapConfirmed(")
		val typed = hostSource
			.substringAfter("private fun onPlayLikeCurlSingleTapConfirmed(")
			.substringBefore("private fun dispatchLegacySingleTapAction(")

		assertContains(hostSource, "private val legacyGestureDetector")
		assertContains(hostSource, "private val playLikeCurlGestureDetector")
		assertContains(hostSource, "override fun onDoubleTap(event: MotionEvent)")
		assertContains(hostSource, "override fun onDoubleTapEvent(event: MotionEvent)")
		assertContains(legacy, "dispatchLegacySingleTapAction(action)")
		assertFalse(legacy.contains("takeDelayedTap("))
		assertFalse(legacy.contains("claimContentAction("))
		assertContains(typed, "takeDelayedTap(")
		assertContains(typed, "takeOldestDelayedTap(")
		assertContains(typed, "tap.gestureId")
		assertContains(typed, "tap.x")
		assertContains(typed, "tap.y")
		assertContains(typed, "completeHostDelayedTap(")
		assertFalse(typed.contains("nativeTapCandidate"))
		assertFalse(typed.contains("dispatchLegacySingleTapAction("))
	}

	@Test
	fun canvasStartupShellCommitsCurrentRasterBeforePublishingStateDismissal() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()
		val presentation = controller
			.substringAfter("fun presentStartupShellCurrentPage(")
			.substringBefore("\n\t}")
		val admission = host
			.substringAfter("private fun commitStartupShellPresentationIfReady()")
			.substringBefore("\n\t}")

		assertContains(presentation, "activeDeckGenerationId")
		assertContains(presentation, "preparedDeckGenerations")
		assertContains(presentation, "currentWebViewOrdinal == currentOrdinal")
		assertContains(presentation, "relocationQueue.occupiedCount() == 0")
		assertContains(presentation, "retainedCurrentLayoutSnapshot(")
		assertContains(presentation, "inlineRasterShield.present(")
		assertTrue(
			presentation.indexOf("inlineRasterShield.present(") <
				presentation.indexOf("onCommitted(true)")
		)
		assertContains(admission, "latestRasterPreparationState.phase")
		assertContains(admission, "latestRendererReadinessState.textureDeck")
		assertContains(admission, "startupShellHandoff.beginAttempt(")
		assertContains(admission, "presentStartupShellCurrentPage")
		assertContains(admission, "startupShellHandoff.completeAttempt(")
		assertContains(admission, "dismissStartupShellPresentation()")
		assertContains(admission, "onStartupShellPrepared()")
		assertContains(host, "startupShellHandoff.resetForNewViewer()")
		assertContains(host, "startupShellHandoff.close()")
	}

	@Test
	fun preparedCanvasShellTransitionPreservesDeckAndUsesExistingGestureRelocation() {
		val controller = controllerFile.readText()
		val host = hostFile.readText()
		val readerRoot = readerRootFile.readText()
		val shellVisibility = host
			.substringAfter("fun setShellCoverVisible(visible: Boolean)")
			.substringBefore("fun setPageOperationPolicy(")
		val shellSwipe = host
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent)")
			.substringBefore("private fun updateReadableViewerDragOffset(")
		val touch = controller
			.substringAfter("fun onPageTouchEvent(")
			.substringBefore("override fun start(")
		val reveal = controller
			.substringAfter("private fun revealSurfaceAfterNextPresentedFrame(")
			.substringBefore("fun cancelGestureAfterHostTerminal(")

		assertContains(shellVisibility, "consumeStartupShellPreparedHandoff()")
		assertContains(shellVisibility, "invalidateCurrentVisualSnapshot")
		assertFalse(readerRoot.contains("canvasShellTransition ="))
		assertContains(shellSwipe, "canvasShellTransitionConsumesPageAction")
		assertFalse(
			shellSwipe
				.substringAfter("if (canvasShellTransitionConsumesPageAction())")
				.substringBefore("else")
				.contains("updateShellCoverDragOffset(")
		)
		assertContains(touch, "relocationGestureCoordinator.start(")
		assertContains(touch, "surfaceView.onPageTouchEvent(event, gestureId)")
		assertContains(reveal, "surfaceView.requestNextPresentedFrame")
		assertContains(reveal, "inlineRasterShield.dismiss()")
		assertFalse(controller.contains("startDiscreteRelocation("))
	}

	@Test
	fun productionLifecycleWiringClassifiesInvalidationsBeforeMutation() {
		val hostSource = hostFile.readText()
		val expectedEvents = listOf(
			"CanvasDisabled",
			"WindowHidden",
			"ShellCoverShown",
			"RendererReplaced",
			"ViewportChanged",
			"ReaderSettingsChanged",
			"ExternalRelocation",
			"UnsafeContextLost",
			"GlFailed"
		)
		expectedEvents.forEach { event ->
			assertContains(hostSource, "ReaderPageHostLifecycleEvent.$event")
		}
		val readiness = hostSource
			.substringAfter("private fun onRendererReadinessChanged(")
			.substringBefore("private fun publishMergedPagePreparationState(")
		assertFalse(readiness.contains("onLifecycleEvent("))
		assertEquals(
			1,
			Regex("abandonPhysicalPointerStream\\(").findAll(hostSource).count()
		)
	}

	@Test
	fun readerTeardownFencesBundleBeforeRendererAndClosesOwnersInOrder() {
		val preparation = rasterPreparationFile.readText()
		val bundle = bundleSourceFile.readText()
		val host = hostFile.readText()
		val destroy = preparation
			.substringAfter("fun destroy(): Deferred<Unit>")
			.substringBefore("suspend fun destroyAndJoin()")
		val bundleWiring = bundle
			.substringAfter("private val teardown = ReaderPageTurnBundleTeardown(")
			.substringBefore("fun fenceForClose()")
		val bundleClose = bundle
			.substringAfter("fun fenceForClose()")
			.substringBefore("private fun restoreLiveComposition(")
		val hostTeardown = host.substringAfter("private fun teardownTask4Resources()")

		assertContains(preparation, "closeRendererAndAdapter: suspend () -> Unit")
		assertContains(preparation, "fenceCallbacks = ::fenceForDestroy")
		assertContains(
			preparation,
			"private val fenceBundleOwners: () -> Unit = bundleSource::fenceForClose"
		)
		assertContains(preparation, "fenceBundleOwners = fenceBundleOwners")
		assertContains(
			preparation,
			"initializationJobs.forEach { job -> job.cancelAndJoin() }"
		)
		assertContains(destroy, "if (!destroyed) destroyed = true")
		assertContains(destroy, "return teardown.start()")
		assertContains(bundleWiring, "publicationScheduler.closeAndJoin()")
		assertContains(bundleWiring, "publicationEntryCount = publicationLedger::entryCount")
		assertContains(bundleWiring, "rasterScheduler?.closeAndJoin()")
		assertContains(bundleWiring, "persistenceJobs.forEach { job -> job.join() }")
		assertContains(bundleWiring, "rasterPersistenceJobs.isEmpty()")
		assertContains(bundleWiring, "pendingDescriptorOwners.pendingCount() == 0")
		assertContains(bundleWiring, "hydrationScheduler.closeAndJoin()")
		assertContains(bundle, "trackRasterPersistenceJob(persistenceJob)")
		assertContains(bundle, "pendingDescriptorOwners.acquire(snapshot)")
		assertContains(bundle, "pendingDescriptorOwners.claim(descriptorOwner)")
		assertContains(bundle, "pendingDescriptorOwners.complete(claimedOwner)")
		assertContains(bundle, "pendingDescriptorOwners.cancelAll()")
		assertContains(bundle, "requireRasterInitializationOpen()")
		assertContains(bundle, "closeUnpublishedRasterOwners(")
		assertContains(bundleClose, "rasterJob.cancel()")
		assertContains(bundleClose, "pendingDescriptorOwners.close()")
		assertTrue(
			bundleWiring.indexOf("persistentStore?.close()") <
				bundleWiring.indexOf("rasterCache?.let")
		)
		assertContains(bundleClose, "fun close(): Deferred<Unit>")
		assertContains(hostTeardown, "val teardown = pageRasterPreparationController.destroy()")
		assertContains(hostTeardown, "task4Teardown = teardown")
		assertContains(hostTeardown, "teardown.invokeOnCompletion")
		assertFalse(hostTeardown.contains("playLikeCurlController.destroy()"))
		assertContains(host, "closeRendererAndAdapter = {")
		assertContains(host, "playLikeCurlController.destroyAndJoin()")
		listOf(preparation, bundle, host).forEach { source ->
			assertFalse(source.contains("runBlocking"))
			assertFalse(source.contains("Thread.join"))
			assertFalse(source.contains("CountDownLatch"))
		}
	}

	@Test
	fun productionFinalLifecycleSeparatesCancellationFromDeliveryClose() {
		val hostSource = hostFile.readText()
		val root = hostSource
			.substringAfter("private class KomikkuReaderNativeFrameRoot")
			.substringBefore("private class KomikkuReaderNativeShellCoverView")
		val viewer = hostSource
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private fun View.findDescendantWebView")
		val observer = viewer
			.substringAfter("private val hostLifecycleObserver")
			.substringBefore("override fun onAttachedToWindow")
		val detach = viewer
			.substringAfterLast("override fun onDetachedFromWindow()")
			.substringBefore("fun closeReader()")
		val closeReader = viewer
			.substringAfter("fun closeReader()")
			.substringBefore("private fun beginFinalHostLifecycle(")
		val begin = viewer
			.substringAfter("private fun beginFinalHostLifecycle(")
			.substringBefore("private fun closePhysicalPointerDelivery()")
		val closeDelivery = viewer
			.substringAfter("private fun closePhysicalPointerDelivery()")
			.substringBefore("private fun teardownTask4Resources()")
		val teardown = viewer.substringAfter("private fun teardownTask4Resources()")
		val rootClose = root.substringAfter("fun closeReader()")

		assertContains(hostSource, "onRelease = { root ->")
		assertContains(hostSource, "if (nativeFrameRoot === root) nativeFrameRoot = null")
		assertContains(hostSource, "root.closeReader()")
		assertContains(hostSource, "findViewTreeLifecycleOwner()")
		assertContains(observer, "ReaderPageHostLifecycleEvent.Destroyed")
		assertFalse(observer.contains("closePhysicalPointerDelivery()"))
		assertContains(detach, "ReaderPageHostLifecycleEvent.Detached")
		assertContains(detach, "closePhysicalPointerDelivery()")
		assertContains(detach, "teardownTask4Resources()")
		assertContains(closeReader, "ReaderPageHostLifecycleEvent.ReaderClosed")
		assertContains(closeReader, "closePhysicalPointerDelivery()")
		assertContains(begin, "if (finalHostLifecycleEvent != null) return")
		assertContains(begin, "dispatchPageHostLifecycleEvent(event)")
		assertContains(begin, "clearLegacyNativeTapState(reason)")
		assertFalse(begin.contains("abandonPhysicalPointerStream("))

		val abandonIndex = closeDelivery.indexOf("abandonPhysicalPointerStream(reason)")
		val clearModeIndex = closeDelivery.indexOf("physicalDispatchMode = null")
		assertTrue(abandonIndex >= 0)
		assertTrue(clearModeIndex > abandonIndex)
		val removeIndex = teardown.indexOf("removePageTurnPrewarmLayoutListener()")
		val eventIndex = teardown.indexOf("pageRasterHostEventController.close()")
		val rasterIndex = teardown.indexOf("pageRasterPreparationController.destroy()")
		assertTrue(removeIndex >= 0)
		assertTrue(eventIndex > removeIndex)
		assertTrue(rasterIndex > eventIndex)
		assertFalse(teardown.contains("playLikeCurlController.destroy()"))
		assertContains(rootClose, "viewerContainer.closeReader()")
		assertFalse(
			rootClose.contains("disposeComposition()"),
			"Root close must not re-enter nested Compose hierarchy mutation while AndroidView is releasing."
		)
	}

	@Test
	fun destroyDelegatesToBehaviorTestedFenceAndDrainsRendererOwners() {
		val source = controllerFile.readText()
		val wiring = source
			.substringAfter("private val destroyFence =")
			.substringBefore("private suspend fun disposeRendererAndOwners()")
		val disposal = source
			.substringAfter("private suspend fun disposeRendererAndOwners()")
			.substringBefore("suspend fun destroyAndJoin()")

		assertFalse(source.contains("private var destroyTask"))
		assertContains(wiring, "ReaderPageControllerDestroyFence(")
		assertContains(wiring, "cancelRecovery = deckRecoveryCoordinator::cancelAll")
		assertContains(
			wiring,
			"closeVisualHandoff = relocationVisualHandoffCoordinator::close"
		)
		assertContains(
			wiring,
			"cancelRelocations = ::cancelRelocationsWithDiagnostics"
		)
		assertContains(wiring, "fun destroy(): Deferred<Unit> = destroyFence.start()")
		assertContains(disposal, "surfaceView.disposeForLifecycleOwner { result ->")
		assertContains(disposal, "check(rendererDisposed.complete(result))")
		assertContains(disposal, "terminalRendererResult.ownership")
		assertContains(disposal, "if (rendererResult == null && rendererDisposed.isCompleted)")
		assertContains(disposal, "val rendererOwnershipReleased =")
		assertContains(disposal, "if (rendererOwnershipReleased)")
		assertContains(disposal, "adapterMetrics += adapter.metrics()")
		assertContains(disposal, "budgetMetrics = rasterResidencyBudget.metrics()")
		assertContains(disposal, "rasterAdapterOwners.remove(adapter)")
		assertContains(disposal, "mainTerminalExecutor.closeAndJoin()")
		assertContains(disposal, "teardownJob.complete()")
		assertContains(disposal, "surfaceView.pendingMainTerminalActionCount == 0")
		assertContains(disposal, "rendererSnapshot.releaseInFlightDeckLeases")
		assertContains(disposal, "ReaderPageTeardownStage.RendererDisposal")
		assertContains(disposal, "strandedGenerations.forEach")
		val rendererAwaitIndex = disposal.indexOf("rendererDisposed.await()")
		val deckCloseIndex =
			disposal.indexOf("preparedPageSets.toList().forEach { pages ->")
		val adapterFenceIndex = disposal.indexOf("adapter.close()")
		val workerCancelJoinIndex = disposal.indexOf("rasterJob.cancelAndJoin()")
		val adapterJoinIndex = disposal.indexOf("adapter.closeAndJoin()")
		assertTrue(rendererAwaitIndex >= 0)
		assertTrue(deckCloseIndex > rendererAwaitIndex)
		assertTrue(adapterFenceIndex > deckCloseIndex)
		assertTrue(workerCancelJoinIndex > adapterFenceIndex)
		assertTrue(adapterJoinIndex > workerCancelJoinIndex)
		assertFalse(disposal.contains("invalidate(\"destroyed\")"))
		assertContains(source, "ReaderMainTerminalActionExecutor(")
		assertContains(source, "actionLimit = surfaceView.mainTerminalActionLimit")
		assertContains(source, "surfaceView.registerMainTerminalExecutor(")
		assertContains(source, "mainTerminalExecutor::execute")
		val refill = source
			.substringAfter("private fun refillDecodedWorkingSet(")
			.substringBefore("private fun ReaderPlayLikeCurlRasterProfile.preparedPageIndices(")
		val prepare = source
			.substringAfter("private fun prepareProfile(")
			.substringBefore("override fun isCurrentRepairWindow(")
		assertContains(
			refill,
			"withContext(NonCancellable + Dispatchers.Main.immediate)"
		)
		assertContains(prepare, "withContext(Dispatchers.Main.immediate)")
		assertFalse(refill.contains("host.post {"))
		assertFalse(prepare.contains("host.post {"))
	}

	@Test
	fun productionDestroyRetainsImportedDispatchModeForPhysicalTail() {
		val hostSource = hostFile.readText()
		val viewer = hostSource
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private fun View.findDescendantWebView")
		val observer = viewer
			.substringAfter("private val hostLifecycleObserver")
			.substringBefore("override fun onAttachedToWindow")
		val begin = viewer
			.substringAfter("private fun beginFinalHostLifecycle(")
			.substringBefore("private fun closePhysicalPointerDelivery()")
		val dispatch = viewer
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean")
			.substringBefore("private fun dispatchLegacyReaderPointerEvent(")

		assertContains(observer, "ReaderPageHostLifecycleEvent.Destroyed")
		assertFalse(observer.contains("closePhysicalPointerDelivery()"))
		assertFalse(begin.contains("physicalDispatchMode = null"))
		assertContains(dispatch, "ReaderPagePhysicalDispatchMode.PlayLikeCurl")
		assertContains(dispatch, "dispatchPlayLikeCurlPointerEvent(event)")
		assertContains(dispatch, "event.actionMasked == MotionEvent.ACTION_UP ||")
		assertContains(dispatch, "event.actionMasked == MotionEvent.ACTION_CANCEL")
	}
}
