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
	private val readerAssetRoot = File("src/androidMain/assets/reader")

	@Test
	fun productionControllerUsesFoliateRastersAndImportedSurface() {
		assertTrue(controllerFile.isFile, "Production PlayLikeCurl controller must exist")
		val source = controllerFile.readText()

		assertContains(source, "PageSurfaceView")
		assertContains(source, "ReaderPlayLikeCurlFoliateRasterLoader")
		assertContains(source, "pageTurnRasterPreparationPlan")
		assertContains(source, "ReaderPlayLikeCurlRasterAdapter")
		assertContains(source, "readerPlayLikeCurlLibraryDeck")
		assertContains(source, "put(\"type\", \"goToVisualPage\")")
		assertFalse(source.contains("ReaderPlayLikeCurlAssetBitmapSource"))
		assertFalse(source.contains("ReaderPlayLikeCurlDiagnosticBitmapSource"))
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
		assertContains(prepare, "\"deck-load-progress\"")
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

		assertContains(hostSource, "Reader pointer terminal")
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
		assertContains(controller, "relocationQueue.occupiedCount() == 0")
		assertContains(
			controller,
			"ReaderPageVisualLocationOrigin.StaleAcknowledgement"
		)
		assertFalse(
			controller
				.substringAfter("fun visualLocationOrigin(")
				.substringBefore("fun synchronizeVisualPageIndex(")
				.contains("page-turn:exact")
		)
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
		val destroy = source
			.substringAfter("fun destroy()")
			.substringBefore("private fun refreshPreparedDeck(")

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
		assertContains(destroy, "relocationVisualHandoffCoordinator.close()")
		assertContains(host, "playLikeCurlController.onHostResumedChanged(true)")
		assertContains(host, "playLikeCurlController.onHostResumedChanged(false)")
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
		val adapterFence = source
			.substringAfter("private fun rasterPublicationFence(")
			.substringBefore("private fun publishProtectedWindow(")

		assertContains(source, "private val persistentRefillCoordinator")
		assertContains(source, "private var committedTurnVersion = 0L")
		assertContains(source, "private var protectedWindowVersion = 0L")
		assertContains(source, "private var currentProtectedWindow = emptyList<Int>()")
		assertContains(settlement, "committedTurnVersion = Math.incrementExact(committedTurnVersion)")
		assertContains(settlement, "schedulePersistentRefill(")
		assertTrue(
			settlement.indexOf("currentOrdinal = currentPageOrdinal") <
				settlement.indexOf("schedulePersistentRefill(")
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
		assertContains(publisher, "publishProtectedRasterOrdinals(immutableWindow)")
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
			2,
			Regex("publicationFence\\.isCurrent\\(\\)").findAll(preparation).count(),
			"Both failed and successful preparation delivery must reject an expired fence."
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
		assertContains(hostSource, "raster.readiness.copy(")
	}

	@Test
	fun productionControllerWaitsForTheActiveRasterProducerInsteadOfRequestingItAgain() {
		val source = controllerFile.readText()
		val refresh = source
			.substringAfter("private fun refreshPreparedDeck()")
			.substringBefore("private fun prepareProfile(")
		val unavailableDeck = source
			.substringAfter("if (deck == null)")
			.substringBefore("return@launch")

		assertContains(source, "private var preparationPhase = ReaderPagePreparationPhase.Idle")
		assertContains(source, "preparationPhase = state.phase")
		assertContains(refresh, "ReaderPagePreparationPhase.Preparing")
		assertContains(refresh, "\"preparation-in-progress\"")
		assertContains(refresh, "requestPrewarmIfIdle(")
		assertContains(unavailableDeck, "requestPrewarmIfIdle(")
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
			.substringBefore("override fun releaseRecoveredDeck(")
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
		assertContains(controller, ") : ReaderPageTapTurnPort, ReaderPageDeckRecoveryHost")
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
		assertContains(submission, "builtRecoveredDecks.remove(generationId)")
		assertContains(submission, "surfaceView.submitDeck(built.deck)")
		assertContains(submission, "generationOwners[generationId] !== built.pages")
		assertContains(submittedCancellation, "readerRecoveredDeckCancellationRoleMatches(")
		assertContains(submittedCancellation, "tombstoneSubmittedRecoveredDeck(")
		assertContains(renderFailure, "generationId in recoveredDeckGenerations")
		assertContains(renderFailure, "deckRecoveryCoordinator.ownsSubmittedGeneration(")
		assertContains(renderFailure, "tombstoneSubmittedRecoveredDeck(")
		assertContains(releaseGeneration, "val releasedCurrentActive =")
		assertContains(releaseGeneration, "if (activePages === pages) activePages = null")
		assertTrue(
			submission.indexOf("surfaceView.submitDeck(built.deck)") <
				submission.indexOf("activePages = built.pages"),
			"Recovered pages must become active only after synchronous rejection is impossible."
		)
		assertContains(recovery, "val role = host.currentRecoveredDeckRole()")
		assertTrue(
			recovery.indexOf("ReaderPageDeckRecoveryState.WaitingForPreparation(") <
				recovery.indexOf("host.submitRecoveredDeck(generationId, role)")
		)
		assertFalse(
			recovery.substringAfter("data class WaitingForBuild(")
				.substringBefore(") : ReaderPageDeckRecoveryState")
				.contains("ReaderDeckSubmissionRole")
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
		assertContains(accepted, "playLikeCurlController.onPageTouchEvent(")
		assertContains(accepted, "event,")
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
			"MotionEvent.ACTION_UP" to "ReaderPageHostPointerEvent.Up",
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
		val moveIndex = claimCurl.lastIndexOf("event,")
		val ownerIndex = claimCurl.indexOf("playLikeCurlGestureOwned = true")
		assertTrue(cancelIndex >= 0)
		assertTrue(showIndex > cancelIndex)
		assertTrue(downIndex > showIndex)
		assertTrue(moveIndex > downIndex)
		assertTrue(ownerIndex > moveIndex)
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
		val curlIndex = teardown.indexOf("playLikeCurlController.destroy()")
		val rasterIndex = teardown.indexOf("pageRasterPreparationController.destroy()")
		assertTrue(removeIndex >= 0)
		assertTrue(curlIndex > removeIndex)
		assertTrue(rasterIndex > curlIndex)
		assertTrue(
			rootClose.indexOf("viewerContainer.closeReader()") <
				rootClose.indexOf("currentViewerComposeView?.disposeComposition()")
		)
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
