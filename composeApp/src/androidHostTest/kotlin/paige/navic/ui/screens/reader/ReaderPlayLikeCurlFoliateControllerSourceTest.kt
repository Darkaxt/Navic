package paige.navic.ui.screens.reader

import java.io.File
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
	fun relocationBridgePreservesCompleteTokenSessionAndGenerationIdentity() {
		val controller = controllerFile.readText()
		val runtime = readerAssetRoot.resolve("navic-reader.js").readText()
		val turns = readerAssetRoot.resolve("navic-reader-page-turns.js").readText()
		val location = readerAssetRoot.resolve("navic-reader-location.js").readText()
		val core = readerAssetRoot.resolve("navic-reader-bridge-core.js").readText()

		assertContains(controller, "private fun dispatchNextRelocation()")
		assertContains(controller, "put(\"settleToken\", request.token.value)")
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

		assertFalse(host.contains("readerPageVisualLocationOrigin(reason)"))
		assertContains(host, "pageTurnSettlementAck == acknowledgement")
		assertContains(host, "ReaderPageVisualLocationOrigin.External")
		assertContains(controller, "fun visualLocationOrigin(")
		assertContains(controller, "relocationQueue.matchesDispatchedHead(")
		assertContains(controller, "if (relocationQueue.hasDispatchedHead())")
		assertFalse(
			controller.contains(
				"acknowledgement == null && relocationQueue.hasDispatchedHead()"
			),
			"A retained stale acknowledgement must not reclassify an in-flight exact relocation as external."
		)
		assertContains(
			controller,
			"ReaderPageVisualLocationOrigin.PendingExactPageTurn"
		)
		assertContains(controller, "relocationQueue.occupiedCount() == 0")
		assertContains(
			controller,
			"ReaderPageVisualLocationOrigin.StaleAcknowledgement"
		)
		val origin = controller
			.substringAfter("fun visualLocationOrigin(")
			.substringBefore("fun synchronizeVisualPageIndex(")
		assertTrue(
			origin.indexOf("if (foliateSessionRelocationPending)") <
				origin.indexOf("if (relocationQueue.hasDispatchedHead())"),
			"A genuine Foliate session replacement must still outrank pending exact relocation state."
		)
		assertTrue(
			origin.indexOf("relocationQueue.matchesDispatchedHead(") <
				origin.indexOf("if (relocationQueue.hasDispatchedHead())"),
			"An exact acknowledgement must be matched before transient dispatched state."
		)
		val synchronization = controller
			.substringAfter("fun synchronizeVisualPageIndex(")
			.substringBefore("private fun completeAcknowledgedRelocation(")
		val pendingExact = synchronization
			.substringAfter("ReaderPageVisualLocationOrigin.PendingExactPageTurn ->")
			.substringBefore("ReaderPageVisualLocationOrigin.StaleAcknowledgement")
		assertFalse(pendingExact.contains("invalidate("))
		assertFalse(
			controller
				.substringAfter("fun visualLocationOrigin(")
				.substringBefore("fun synchronizeVisualPageIndex(")
				.contains("page-turn:exact")
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
		assertContains(deckPrepared, "retryRelocationVisualHandoffForPreparedDeck(generationId)")
		assertContains(source, "ReaderPageRelocationVisualRetryEvent.Attached(")
		assertContains(source, "ReaderPageRelocationVisualRetryEvent.Resumed(")
		assertContains(source, "ReaderPageRelocationVisualRetryEvent.Reprepared(")
		assertTrue(
			invalidate.indexOf("relocationVisualHandoffCoordinator.cancelForQueueInvalidation()") <
				invalidate.indexOf("drainRelocationOwnership(\"invalidated:${'$'}reason\")")
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
	fun successfulQueueHandoffAloneOwnsGlShieldRemoval() {
		val source = visualHandoffFile.readText()
		val complete = source
			.substringAfter("private fun complete(request: ReaderPageRelocationRequest)")
			.substringBefore("private fun recover(")

		assertContains(complete, "queue.completeHandoff(request.token.value)")
		assertContains(complete, "val next = queue.commandToDispatch()")
		assertContains(complete, "if (next == null) hideSurface() else dispatch(next)")
		assertTrue(
			complete.indexOf("queue.completeHandoff(request.token.value)") <
				complete.indexOf("if (next == null) hideSurface() else dispatch(next)")
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
			.substringBefore("fun showSurfaceForGesture()")
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
		assertContains(settlement, "dispatch = { dispatchNextRelocation() }")
		assertContains(
			finish,
			"relocationGestureCoordinator.finish(gestureId, outcome, detail)"
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
	fun committedTurnsPublishOneVersionedWindowBeforePersistentFarEdgeRefill() {
		val source = controllerFile.readText()
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
			.substringBefore("dispatch = { dispatchNextRelocation() }")
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
			settlement.indexOf("schedulePersistentRefill(") >
				settlement.indexOf("relocationGestureCoordinator.commit("),
			"Persistent hydration must remain asynchronous after the synchronous window advance."
		)
		assertContains(schedule, "rasterScope.launch(Dispatchers.Main.immediate)")
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
		assertFalse(
			settlementStarted.contains("adapter.prepare("),
			"Settlement must never decode or perform IO on the gesture frame."
		)
		assertContains(settlementStarted, "ReaderTextureDeckState.Settling")
		assertContains(settlementStarted, "ReaderPageInteractionState.Settling")
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
		assertContains(
			hostSource,
			"latestRasterPreparationState.withRendererReadiness("
		)
		assertContains(hostSource, "shellCoverView.isClickable = shellCoverVisible")
		assertContains(hostSource, "composeOverlay.isClickable = visible")
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
			.substringBefore("dispatch = { dispatchNextRelocation() }")
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
		assertContains(
			preparation,
			"deferredDecodedRefillCenterOrdinal?.let { centerOrdinal ->"
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
	fun portraitAnimationSurfaceStopsBeforeTheStaticBackCoverBoard() {
		val source = controllerFile.readText()
		val submit = source
			.substringAfter("private fun submitLibraryDeck(")
			.substringBefore("private fun PreparedPages.page(")

		assertContains(source, "private fun updateSurfaceBounds(")
		assertContains(submit, "updateSurfaceBounds(pages, ordinal)")
		assertTrue(
			submit.indexOf("updateSurfaceBounds(pages, ordinal)") <
				submit.indexOf("surfaceView.submitDeck(deck)"),
			"The imported surface must match Foliate's page rectangle before the deck becomes visible."
		)
		assertContains(source, "readerPlayLikeCurlPortraitSurfaceWidth(")
		assertContains(source, "ReaderPlayLikeCurlOrientation.Landscape -> ViewGroup.LayoutParams.MATCH_PARENT")
	}

	@Test
	fun rendererRejectionAlwaysRestoresSurfaceAfterPublishingItsTerminal() {
		val source = controllerFile.readText()
		val rejection = source
			.substringAfter("override fun onGestureRejected(")
			.substringBefore("override fun onGestureCancelled(")

		assertContains(rejection, "ReaderPageGestureTerminalOutcome.RejectedBoundary")
		assertContains(rejection, "ReaderPageGestureTerminalDetail.RendererRejected(")
		assertTrue(rejection.indexOf("finishGesture(") < rejection.indexOf("hideSurface()"))
		assertFalse(
			rejection.contains("if (reason == GestureRejectionReason.BOUNDARY)"),
			"Every rejected revealed curl gesture must restore the content surface."
		)
		listOf(
			"dispatchExactVisualPage(",
			"submitLibraryDeck(",
			"promotePendingDeck("
		).forEach { forbidden -> assertFalse(rejection.contains(forbidden)) }
	}

	@Test
	fun everyRendererFailurePublishesSpecificTerminalBeforeUnsafeLifecycleCancellation() {
		val source = controllerFile.readText()
		val failure = source
			.substringAfter("override fun onRenderFailure(failure: RenderFailure) {")
			.substringBefore("\n\t\t})")

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
		assertContains(settlement, "dispatch = { dispatchNextRelocation() }")
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
			"hideSurface()"
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
		val show = source
			.substringAfter("fun showSurfaceForGesture()")
			.substringBefore("fun cancelGesture(")
		val continuation = source
			.substringAfter("private val canContinueAcceptedPointer: Boolean")
			.substringBefore("fun setPageOperationPolicy(")

		assertContains(continuation, "pageOperationPolicy.continueActivePointer")
		assertContains(touch, "if (!isAvailable || metadata == null)")
		assertContains(touch, "relocationGestureCoordinator.start(")
		assertContains(source, "deckRecoveryCoordinator.canAcceptPointer")
		assertContains(show, "canContinueAcceptedPointer")
		assertFalse(show.contains("!isAvailable"))
	}

	@Test
	fun delayedTapTurnPublishesThePolicySpecificUnavailableOutcome() {
		val source = controllerFile.readText()
		val startTapTurn = source
			.substringAfter("private fun startTapTurn(")
			.substringBefore("fun showSurfaceForGesture()")
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
		assertContains(unavailable, "hideSurface()")
		assertTrue(
			unavailable.indexOf("publishGestureTerminal(") < unavailable.indexOf("hideSurface()")
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

		assertContains(content, "MotionEvent.obtain(event)")
		assertContains(content, "viewerContentContainer.dispatchTouchEvent(event)")
		assertFalse(content.contains("super.dispatchTouchEvent(event)"))
		assertFalse(content.contains("playLikeCurlController.onPageTouchEvent("))
		val cancelIndex = claim.indexOf("dispatchContentCancel(event)")
		val showIndex = claim.indexOf("playLikeCurlController.showSurfaceForGesture()")
		val downIndex = claim.indexOf("val downResult =")
		val accepted = claim
			.substringAfter("ReaderPageCurlDispatchResult.Accepted -> {")
			.substringBefore("ReaderPageCurlDispatchResult.TerminalPublished -> {")
		val terminal = claim.substringAfter("ReaderPageCurlDispatchResult.TerminalPublished -> {")
		assertTrue(cancelIndex >= 0)
		assertTrue(showIndex > cancelIndex)
		assertTrue(downIndex > showIndex)
		assertContains(accepted, "dispatchClaimedReaderPageCurlEvent(event)")
		assertContains(accepted, "dispatchedEvent,")
		assertContains(accepted, "playLikeCurlGestureOwned = true")
		assertFalse(terminal.contains("playLikeCurlController.onPageTouchEvent("))
		assertContains(terminal, "playLikeCurlGestureOwned = false")
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
			.substringBefore("fun showSurfaceForGesture()")
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
		assertContains(contentCancel, "viewerContentContainer.dispatchTouchEvent(cancel)")
		assertFalse(contentCancel.contains("super.dispatchTouchEvent(cancel)"))
		assertContains(apply, "ReaderPagePointerRoute.Consume -> true")
		assertContains(apply, "ReaderPagePointerRoute.Ignore -> true")

		val cancelIndex = claimCurl.indexOf("dispatchContentCancel(event)")
		val showIndex = claimCurl.indexOf("playLikeCurlController.showSurfaceForGesture()")
		val downIndex = claimCurl.indexOf("originalDown,")
		val moveIndex = claimCurl.indexOf("dispatchClaimedReaderPageCurlEvent(event)")
		val ownerIndex = claimCurl.indexOf("playLikeCurlGestureOwned = true")
		assertTrue(cancelIndex >= 0)
		assertTrue(showIndex > cancelIndex)
		assertTrue(downIndex > showIndex)
		assertTrue(moveIndex > downIndex)
		assertTrue(ownerIndex > moveIndex)
		assertContains(claimCurl, "event.actionMasked == MotionEvent.ACTION_UP")
		assertContains(claimCurl, "clearPlayLikeCurlPointerTapFlagsAfterUp()")
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
		val rootClose = root
			.substringAfter("fun closeReader()")
			.substringBefore("override fun onDetachedFromWindow()")

		assertContains(hostSource, "onRelease = { root -> root.closeReader() }")
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
		assertTrue(
			rootClose.indexOf("viewerContainer.closeReader()") <
				rootClose.indexOf("currentViewerComposeView?.disposeComposition()")
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
		assertContains(refill, "withContext(Dispatchers.Main.immediate)")
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
