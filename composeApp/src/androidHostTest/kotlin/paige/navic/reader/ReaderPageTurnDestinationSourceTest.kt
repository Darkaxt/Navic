package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageTurnDestinationSourceTest {
	@Test
	fun runtimeExposesExactVisualPageNavigation() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()

		assertContains(runtime, "case 'goToVisualPage'")
		assertContains(runtime, "command.pageIndex")
		assertContains(runtime, "command.settleToken")
		assertContains(turns, "async function goToVisualPage(")
		assertContains(turns, "readerPageLocatorForVisualIndex")
	}

	@Test
	fun exactVisualPageNavigationUsesOneRendererTarget() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")

		assertContains(exactNavigation, "readerGoToExactVisualPage(this.view, locator)")
		assertContains(preview, "export async function readerGoToExactVisualPage(view, locator, reason = 'page-turn:exact')")
		assertContains(preview, "renderer.goToTextPage(locator.spineIndex, locator.chapterPageIndex, reason)")
		assertContains(paginator, "async goToTextPage(index, pageIndex, reason = 'navigation')")
		assertContains(paginator, "this.#scrollToPage(textPageIndex + 1, reason)")
		assertContains(exactNavigation, "this.view.history?.pushState?.(")
		assertFalse(exactNavigation.contains("anchor: locator.anchor"))
		assertFalse(exactNavigation.contains("this.view?.next?.("))
		assertFalse(exactNavigation.contains("this.view?.prev?.("))
	}

	@Test
	fun passiveAndLiveDestinationRenderingShareExactPageNavigation() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val prepare = preview
			.substringAfter("async function preparePageTurnPreview(")
			.substringBefore("\n}\n")

		assertContains(turns, "readerGoToExactVisualPage(this.view, locator)")
		assertContains(prepare, "readerGoToExactVisualPage(previewView, locator, 'page-turn-preview')")
		assertFalse(prepare.contains("anchor: locator.anchor"))
	}

	@Test
	fun settlementRequiresTokenAndExactVisualPage() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")

		assertContains(runtime, "nativePageTurnSettledState")
		assertContains(runtime, "nativePageTurnSettledState: () => runtime.nativePageTurnSettledState")
		assertContains(turns, "token: settleToken")
		assertContains(turns, "pageIndex: settledPageIndex")
		assertContains(turns, "spineIndex: locator.spineIndex")
		assertContains(turns, "chapterPageIndex: locator.chapterPageIndex")
		assertContains(turns, "paginationProfile: this.paginationProfile")
		assertContains(turns, "Math.floor(spineIndex) !== pending.spineIndex")
		assertContains(turns, "Math.floor(chapterPageIndex) !== pending.chapterPageIndex")
		assertContains(turns, "readerTrace('page-turn:exact-settle-pending'")
		assertContains(turns, "requestedSpineIndex: pending.spineIndex")
		assertContains(turns, "actualSpineIndex: Number.isFinite(spineIndex) ? Math.floor(spineIndex) : null")
		assertTrue(
			exactNavigation.indexOf("await readerGoToExactVisualPage(this.view, locator)") <
				exactNavigation.indexOf("this.maybeCompleteNativePageTurnSettlement(this.currentPagePosition)"),
			"Settlement must be published only after the exact renderer navigation resolves."
		)
	}

	@Test
	fun runtimeExposesPendingExactSettlementIdentityForDiagnostics() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()

		assertContains(
			runtime,
			"nativePageTurnPendingState: () => runtime.pendingExactPageTurnSettlement"
		)
	}

	@Test
	fun exactTurnFreezesItsPaginationProfileUntilStableDestinationSettles() {
		val pagination = readerAssetRoot().resolve("navic-reader-pagination.js").readText()
		val ensureProfile = pagination
			.substringAfter("function readerEnsurePaginationProfile(")
			.substringBefore("\n}\n")

		assertContains(ensureProfile, "this.pendingExactPageTurnSettlement?.paginationProfile")
		assertContains(ensureProfile, "this.paginationProfile = exactTurnProfile")
		assertContains(ensureProfile, "return exactTurnProfile")
	}

	@Test
	fun exactCommittedPositionUsesPendingTargetWhenPhysicalIdentityMatches() {
		val pagination = readerAssetRoot().resolve("navic-reader-pagination.js").readText()
		val committedPosition = pagination
			.substringAfter("function committedPageTurnPosition(")
			.substringBefore("\n}\n")

		assertContains(committedPosition, "this.pendingExactPageTurnSettlement")
		assertContains(committedPosition, "candidateSpineIndex === pendingExact.spineIndex")
		assertContains(committedPosition, "candidateChapterPageIndex === pendingExact.chapterPageIndex")
		assertContains(committedPosition, "pageIndex: pendingExact.pageIndex")
	}

	@Test
	fun ordinaryPageTurnCancelsAStaleExactSettlementBeforePlanningItsTarget() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val start = turns
			.substringAfter("function startPageTurn(direction)")
			.substringBefore("function startNextQueuedPageTurn()")

		val cancel = start.indexOf("this.cancelPendingExactPageTurnSettlement('ordinary-page-turn')")
		val target = start.indexOf("const currentPageIndex = Number(this.currentPagePosition?.pageIndex)")
		assertTrue(cancel >= 0, "A stale exact destination must not survive into a normal page turn.")
		assertTrue(cancel < target, "The stale exact destination must be cancelled before the next target is derived.")
	}

	@Test
	fun cancelledExactSettlementIsObservableByTheNativeController() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(turns, "function cancelPendingExactPageTurnSettlement(reason = 'superseded')")
		assertContains(turns, "cancelled: true")
		assertContains(controller, "settled.optBoolean(\"cancelled\", false)")
		assertContains(controller, "activeSettleToken = null")
		assertContains(controller, "slideCoordinator = null")
		assertContains(controller, "detachOverlay()")
	}

	@Test
	fun destinationNavigationDoesNotUseCancellationTimeouts() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")

		assertFalse(exactNavigation.contains("setTimeout"))
		assertFalse(exactNavigation.contains("Promise.race"))
		assertFalse(exactNavigation.contains("AbortSignal.timeout"))
	}

	@Test
	fun passiveRendererIsSingleUsePerPublicationAndHasNoLiveObservers() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(preview, "data-navic-page-turn-preview")
		assertContains(preview, "this.pageTurnPreviewView")
		assertContains(preview, "this.pageTurnPreviewView &&")
		assertContains(preview, "await previewView.open(this.publicationUrl)")
		assertContains(preview, "previewView.addEventListener('load'")
		assertFalse(preview.contains("previewView.addEventListener('relocate'"))
		assertFalse(preview.contains("previewView.addEventListener('create-overlay'"))
		assertFalse(preview.contains("previewView.addEventListener('draw-annotation'"))
		assertFalse(preview.contains("previewView.history"))
		assertFalse(preview.contains("postCurrentLocationSnapshot"))
		assertFalse(preview.contains("mediaOverlay"))
	}

	@Test
	fun passiveRendererUsesLiveLayoutAndThemeMethods() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(preview, "this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)")
		assertContains(preview, "this.applyDocumentTheme(detail.doc, this.readerSettings, detail.index)")
		assertContains(preview, "this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)")
	}

	@Test
	fun passiveRendererHasGenerationBasedReadinessAndDeterministicTeardown() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(runtime, "NavicReaderPageTurnPreviewMethods")
		assertContains(runtime, "beginPageTurnPreviewPreparation")
		assertContains(runtime, "pageTurnPreviewState")
		assertContains(runtime, "restorePageTurnLiveComposition")
		assertContains(preview, "pageTurnPreviewGeneration")
		assertContains(preview, "requestAnimationFrame")
		assertContains(preview, "previewView.close?.()")
		assertContains(preview, "previewView.remove?.()")
		assertFalse(preview.contains("setTimeout"))
		assertFalse(preview.contains("Promise.race"))
	}

	@Test
	fun passiveRasterBatchStagesOneExactOrdinalUntilNativeAdvancesIt() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val batch = preview
			.substringAfter("function beginPageTurnPreviewBatch(")
			.substringBefore("function exposePageTurnPreviewFinal(")

		assertContains(runtime, "beginPageTurnPreviewBatch: (token, pageIndexes) =>")
		assertContains(runtime, "pageTurnPreviewBatchState: token =>")
		assertContains(runtime, "advancePageTurnPreviewBatch: (token, pageIndex) =>")
		assertContains(batch, "readerPageLocatorForVisualIndex(this.paginationProfile, pageIndex)")
		assertContains(batch, "readerGoToExactVisualPage(previewView, locator, 'page-turn-raster-batch')")
		assertContains(batch, "status: 'ready'")
		assertContains(batch, "spineIndex: locator.spineIndex")
		assertContains(batch, "href: locator.href")
		assertContains(batch, "chapterPageIndex: locator.chapterPageIndex")
		assertContains(batch, "visualPageOrdinal: locator.pageIndex")
		assertContains(batch, "if (state.status !== 'ready' || state.pageIndex !== completedPageIndex) return state")
		assertFalse(batch.contains("post("), "The passive raster renderer must not publish reader events.")
		assertFalse(batch.contains("setTimeout"))
	}

	@Test
	fun rasterPreparationPlanUsesPaginationChaptersAndStablePriorityOrder() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val plan = preview
			.substringAfter("function pageTurnRasterPreparationPlan(")
			.substringBefore("function pageTurnPreviewContext(")

		assertContains(runtime, "pageTurnRasterPreparationPlan: pageIndex =>")
		assertContains(plan, "this.paginationProfile?.chapters")
		assertContains(plan, "addTarget(centerPageIndex, 'current')")
		assertContains(plan, "addTarget(centerPageIndex + step, 'next-transition')")
		assertContains(plan, "addTarget(centerPageIndex - step, 'previous-transition')")
		assertContains(plan, "addChapter(chapters[currentChapterIndex], 'current-chapter')")
		assertContains(plan, "addChapter(chapters[currentChapterIndex + 1], 'next-chapter')")
		assertContains(plan, "addChapter(chapters[currentChapterIndex - 1], 'previous-chapter')")
		assertContains(plan, "layoutMode === 'spread' ? 2 : 1")
		assertContains(plan, "pageStartIndex")
		assertContains(plan, "pageCount")
		assertFalse(plan.contains("setTimeout"))
	}

	@Test
	fun passiveRasterIdentityIncludesEveryCacheInvalidationDimension() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val identity = preview
			.substringAfter("function pageTurnRasterDescriptor(")
			.substringBefore("function pageTurnPreviewContext(")

		assertContains(runtime, "pageTurnRasterDescriptor: pageIndex =>")
		assertContains(identity, "publicationUrl: String(this.publicationUrl || '')")
		assertContains(identity, "paginationFingerprint: String(this.paginationFingerprint || '')")
		assertContains(identity, "layoutFingerprint: stableHash(JSON.stringify(layoutState))")
		assertContains(identity, "decorationFingerprint: stableHash(JSON.stringify(decorationState))")
		assertContains(identity, "readerPageLocatorForVisualIndex(this.paginationProfile, normalizedPageIndex)")
		assertContains(identity, "spineIndex: locator.spineIndex")
		assertContains(identity, "chapterPageIndex: locator.chapterPageIndex")
		assertContains(identity, "visualPageOrdinal: locator.pageIndex")
	}

	@Test
	fun passiveRendererExposesReadOnlyParityContext() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(runtime, "pageTurnPreviewContext: () => runtime.pageTurnPreviewContext()")
		assertContains(preview, "function pageTurnPreviewContext()")
		assertContains(preview, "pageIndex: Number(this.currentPagePosition?.pageIndex)")
		assertContains(preview, "previewGeneration: this.pageTurnPreviewGeneration")
		assertContains(preview, "previewState: this.pageTurnPreviewStateValue")
	}

	@Test
	fun firstPassiveRendererCreationDoesNotInvalidatePreparationGeneration() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(
			preview,
			"if (this.pageTurnPreviewView || this.pageTurnPreviewPublicationUrl)"
		)
		assertContains(preview, "this.destroyPageTurnPreviewRenderer('publication-replaced')")
	}

	@Test
	fun exposedPassiveRendererReusesLiveDecorationLayerStack() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val expose = preview
			.substringAfter("function exposePageTurnPreviewFinal(")
			.substringBefore("\n}\n")

		assertContains(expose, "'z-index': '1'")
		assertFalse(expose.contains("'z-index': '2147483638'"))
	}

	@Test
	fun exposedDestinationStagesAndRestoresExactPageNumberLabels() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val expose = preview.substringAfter("function exposePageTurnPreviewFinal(").substringBefore("\n}")
		val restore = preview.substringAfter("function restorePageTurnLiveComposition(").substringBefore("\n}")

		assertContains(expose, "pageTurnPreviewLivePagePosition")
		assertContains(expose, "pageIndex: state.pageIndex")
		assertContains(expose, "this.updateReaderPageNumberLayer(previewPagePosition)")
		assertContains(restore, "this.updateReaderPageNumberLayer(this.pageTurnPreviewLivePagePosition)")
	}

	@Test
	fun runtimeExposesExactPhysicalTransitionPlanForNativeCapture() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(runtime, "pageTurnTransitionPlan: (physicalDirection, currentPageIndexOverride = null) =>")
		assertContains(runtime, "runtime.pageTurnTransitionPlan(physicalDirection, currentPageIndexOverride)")
		assertContains(preview, "function pageTurnTransitionPlan(")
		assertContains(preview, "readerPageTurnPlan({")
		assertContains(preview, "currentPageIndex: currentPageIndex")
		assertContains(preview, "pageCount: this.currentPagePosition?.pageCount")
		assertContains(preview, "layoutMode: geometry.mode")
		assertContains(preview, "readerDirection")
	}

	@Test
	fun previewContextReportsTheResolvedLayoutModeForNativeRotationHandshake() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val context = preview
			.substringAfter("function pageTurnPreviewContext()")
			.substringBefore("function pageTurnTransitionPlan(")

		assertContains(context, "layoutMode: this.pageTurnCaptureGeometry().mode")
	}

	@Test
	fun transitionPlannerAcceptsTheNativeVisualPageOverride() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val planner = preview
			.substringAfter("function pageTurnTransitionPlan(")
			.substringBefore("function beginPageTurnPreviewPreparation(")

		assertContains(planner, "currentPageIndexOverride = null")
		assertContains(planner, "Number.isFinite(Number(currentPageIndexOverride))")
		assertContains(planner, "currentPageIndex: currentPageIndex")
		assertContains(planner, "pageIndex: currentPageIndex")
	}

	@Test
	fun cacheOwnedSnapshotsAreBorrowedAndReleasedExactlyOnceByTransitions() {
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()

		assertContains(bundle, "data class ReaderPageTurnTransitionPlan")
		assertContains(bundle, "class ReaderPageSlideSnapshot")
		assertContains(bundle, "class ReaderPageSlideTransition")
		assertContains(bundle, "source.retain()")
		assertContains(bundle, "destination.retain()")
		assertContains(bundle, "if (closed) return")
		assertContains(bundle, "source.release()")
		assertContains(bundle, "destination.release()")
		assertFalse(bundle.contains("ReaderPageTurnBitmapBundle"))
	}

	@Test
	fun snapshotCacheIsBoundedAndReleasesEvictedEntries() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)")
		assertContains(source, "private const val MaxCachedSnapshots = 5")
		assertContains(source, "eldest.value.releaseCacheOwnership()")
		assertContains(source, "snapshotCache.clear()")
	}

	@Test
	fun stagedCaptureRejectsAndRecyclesStaleGenerationsWithoutTimeouts() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "captureSurface(")
		assertContains(source, "captureStagedSurface(")
		assertContains(source, "if (generation != activeGeneration)")
		assertContains(source, "staleSnapshot?.releaseCacheOwnership()")
		assertContains(source, "webView.draw(canvas)")
		assertFalse(source.contains("postDelayed"))
		assertFalse(source.contains("setTimeout"))
	}

	@Test
	fun stagedDestinationCaptureWaitsForTheExposedPreviewToBeComposited() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val capture = source
			.substringAfter("internal fun captureStagedSurface(")
			.substringBefore("fun invalidate(")

		assertContains(capture, "webView.postVisualStateCallback")
		assertContains(capture, "WebView.VisualStateCallback")
		assertContains(capture, "else webView.postOnAnimation(draw)")
		assertFalse(capture.contains("Looper.getMainLooper()) draw()"))
		assertFalse(capture.contains("postDelayed"))
	}

	@Test
	fun snapshotWaveStagesOnlyOneDestinationSnapshot() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "exposePageTurnPreviewFinal")
		assertContains(source, "ReaderPageSlideSnapshot(")
		assertFalse(source.contains("capturePortraitUnderneath"))
		assertFalse(source.contains("underneathPageIndex"))
		assertContains(source, "webView.postOnAnimation")
		assertFalse(source.contains("postDelayed"))
	}

	@Test
	fun persistentRasterHydrationCopiesDecodedBitmapAndRebindsLiveSurface() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val hydration = source
			.substringAfter("fun hydrateSnapshot(")
			.substringBefore("private fun capturePreparedDestination(")

		assertContains(hydration, "cache.readCopy(key)")
		assertContains(hydration, "cached.copy(Bitmap.Config.ARGB_8888, false)")
		assertContains(hydration, "Rect(reference.surfaceRectInWindow)")
		assertContains(hydration, "readerPageRasterLeafGeometry(")
		assertContains(hydration, "persist = false")
		assertContains(hydration, "generation != activeGeneration")
		assertFalse(hydration.contains("postDelayed"))
		assertFalse(hydration.contains("withTimeout"))
	}
}
