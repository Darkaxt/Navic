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
		assertContains(runtime, "return this.goToVisualPage(command)")
		assertContains(turns, "async function goToVisualPage(command = {})")
		assertContains(turns, "command.pageIndex")
		assertContains(turns, "command.settleToken")
		assertContains(turns, "command.settleSessionId")
		assertContains(turns, "command.settleRasterGeneration")
		assertContains(turns, "command.settleTextureGeneration")
		assertContains(turns, "!Number.isInteger(pageIndex) || pageIndex < 0")
		assertContains(turns, "settleSessionId !== this.foliateSessionId")
		assertContains(turns, "!Number.isInteger(settleRasterGeneration)")
		assertContains(turns, "!Number.isInteger(settleTextureGeneration)")
		assertContains(turns, "readerPageLocatorForVisualIndex")
	}

	@Test
	fun exactVisualPageNavigationUsesOneRendererTarget() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val viewport = readerAssetRoot().resolve("navic-reader-viewport.js").readText()
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")

		assertContains(exactNavigation, "readerGoToExactVisualPage(this.view, locator)")
		assertContains(preview, "export async function readerGoToExactVisualPage(view, locator, reason = 'page-turn:exact')")
		assertContains(preview, "const committed = await renderer.goToTextPage(")
		assertContains(preview, "return committed === false ? null : locator")
		assertContains(paginator, "async goToTextPage(index, pageIndex, reason = 'navigation')")
		assertContains(paginator, "await this.#acquireExactNavigationLock()")
		assertContains(paginator, "async #acquireExactNavigationLock()")
		assertContains(paginator, "throw new Error('Exact page navigation timed out waiting for paginator unlock')")
		assertContains(paginator, "await this.#scrollToAnchor(requestedAnchor, reason)")
		assertContains(exactNavigation, "this.view.history?.pushState?.(")
		val applyStableLayout = exactNavigation.indexOf(
			"this.applyReaderViewportLayout('page-turn:exact', { renderSynchronously: true })"
		)
		val navigateExactPage = exactNavigation.indexOf("await readerGoToExactVisualPage(this.view, locator)")
		val exactNavigationStart = exactNavigation.indexOf(
			"this.exactPageTurnNavigationInProgress = true"
		)
		val exactNavigationEnd = exactNavigation.indexOf(
			"this.exactPageTurnNavigationInProgress = false"
		)
		assertTrue(applyStableLayout >= 0, "Exact navigation must establish the final renderer layout.")
		assertTrue(navigateExactPage >= 0, "Exact navigation must invoke the renderer target.")
		assertTrue(exactNavigationStart >= 0, "Exact navigation must publish its active state.")
		assertTrue(exactNavigationEnd >= 0, "Exact navigation must clear its active state.")
		assertTrue(
			applyStableLayout < navigateExactPage,
			"Applying viewport layout after exact scrolling resets Foliate to its previous anchor."
		)
		assertTrue(
			exactNavigationStart < applyStableLayout,
			"Synchronous layout relocation must remain isolated inside exact navigation."
		)
		assertContains(viewport, "function applyReaderViewportLayout(label = 'unknown', options = {})")
		assertContains(viewport, "if (options.renderSynchronously) renderer.render?.()")
		assertContains(viewport, "else requestAnimationFrame(() => renderer?.render?.())")
		assertContains(
			exactNavigation,
			"this.scheduleSettledControlledPageTurnRelocation('exact')"
		)
		assertTrue(
			exactNavigationStart < navigateExactPage
		)
		assertTrue(
			navigateExactPage < exactNavigationEnd
		)
		assertFalse(exactNavigation.contains("anchor: locator.anchor"))
		assertFalse(exactNavigation.contains("this.view?.next?.("))
		assertFalse(exactNavigation.contains("this.view?.prev?.("))
	}

	@Test
	fun viewportResizeRefreshesSurfaceLayoutBeforeRasterPlanning() {
		val viewport = readerAssetRoot().resolve("navic-reader-viewport.js").readText()
		val applyLayout = viewport
			.substringAfter("function applyReaderViewportLayout(label = 'unknown', options = {}) {")
			.substringBefore("\n}\n")

		assertContains(applyLayout, "if (label === 'resize' && this.currentPagePosition)")
		assertContains(applyLayout, "this.updateSurfacePaperTexture(")
		assertContains(applyLayout, "this.lastRelocateDetail || {}")
		assertContains(applyLayout, "this.currentPagePosition")
		assertContains(applyLayout, "'viewport-layout:resize'")
		val refresh = applyLayout.indexOf("this.updateSurfacePaperTexture(")
		val preload = applyLayout.indexOf("this.preloadPageDragPreviewTargets?.(")
		assertTrue(refresh >= 0 && refresh < preload)
	}

	@Test
	fun paginatorAppliesSectionStylesBeforeResolvingItsExactTextPage() {
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val display = paginator
			.substringAfter("async #display(promise) {")
			.substringBefore("\n    #canGoToIndex(index) {")
		val createView = paginator
			.substringAfter("#createView() {")
			.substringBefore("\n    #discardCandidateView(view) {")
		val goTo = paginator
			.substringAfter("async #goTo({ index, anchor, select }")
			.substringBefore("\n    async goTo(target) {")

		val createCandidate = display.indexOf("const view = this.#createView()")
		val commitView = display.indexOf("view.markCommitted()")
		val applyStyles = display.indexOf("this.setStyles(this.#styles)")
		val resolveAnchor = display.indexOf("await this.scrollToAnchor(")
		assertContains(createView, "this.#view = new View({")
		assertTrue(createCandidate >= 0, "The section load must create and install a candidate paginator view.")
		assertTrue(commitView > createCandidate, "Only the successfully loaded candidate may become committed.")
		assertTrue(applyStyles > commitView, "Saved reader styles must target the committed section.")
		assertTrue(resolveAnchor > applyStyles, "Exact page geometry must be measured only after reader styles apply.")
		assertFalse(
			goTo.contains("this.setStyles(this.#styles)"),
			"The section load callback runs before the new paginator view is installed and must not style the old view."
		)
	}

	@Test
	fun paginatorSerializesNavigationAndReleasesLocksAfterFailures() {
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val exact = paginator
			.substringAfter("async goToTextPage(index, pageIndex, reason = 'navigation') {")
			.substringBefore("\n    #scrollPrev(distance) {")
		val scrollToAnchor = paginator
			.substringAfter("async #scrollToAnchor(anchor, reason = 'anchor') {")
			.substringBefore("\n    #getVisibleRange() {")
		val commitTextPageAnchor = paginator
			.substringAfter("commitTextPageAnchor() {")
			.substringBefore("\n    async #scrollToAnchor(anchor, reason = 'anchor') {")
		val afterScroll = paginator
			.substringAfter("#afterScroll(reason) {")
			.substringBefore("\n    async #display(promise) {")
		val attributeChanged = paginator
			.substringAfter("attributeChangedCallback(name, _, value) {")
			.substringBefore("\n    open(book) {")
		val setStyles = paginator
			.substringAfter("setStyles(styles) {")
			.substringBefore("\n    focusView()")
		val acquireExactLock = paginator
			.substringAfter("async #acquireExactNavigationLock() {")
			.substringBefore("\n    async goToTextPage(")
		val turn = paginator
			.substringAfter("async #turnPage(dir, distance) {")
			.substringBefore("\n    async prev(distance) {")
		val goTo = paginator
			.substringAfter("async #goTo({ index, anchor, select }")
			.substringBefore("\n    async goTo(target) {")

		assertContains(exact, "if (!Number.isFinite(numericPageIndex)) return false")
		assertContains(exact, "if (this.scrolled) return false")
		assertContains(exact, "if (!await this.#acquireExactNavigationLock()) return false")
		assertContains(exact, "const generation = ++this.#navigationGeneration")
		assertContains(
			exact,
			"const requestedAnchor = Object.freeze({ textPageIndex: requestedPageIndex })"
		)
		assertContains(exact, "await this.#scrollToAnchor(requestedAnchor, reason)")
		assertFalse(
			exact.contains("this.pages"),
			"A newly loaded section can report transient page geometry; exact navigation must preserve the requested page identity rather than clamp it to the live count."
		)
		assertContains(scrollToAnchor, "const exactTextPageIndex = Number(anchor?.textPageIndex)")
		assertContains(scrollToAnchor, "if (anchor?.textPageIndex != null) {")
		assertContains(scrollToAnchor, "if (!Number.isInteger(exactTextPageIndex) || exactTextPageIndex < 0) return")
		assertContains(scrollToAnchor, "await this.#scrollToPage(exactTextPageIndex + 1, reason)")
		assertContains(commitTextPageAnchor, "this.#anchor?.textPageIndex")
		assertContains(commitTextPageAnchor, "this.#lastVisibleRange")
		assertContains(commitTextPageAnchor, "range?.startContainer?.isConnected")
		assertContains(commitTextPageAnchor, "this.#anchor = range")
		assertContains(afterScroll, "!String(reason || '').startsWith('page-turn')")
		assertContains(attributeChanged, "this.commitTextPageAnchor()")
		assertContains(setStyles, "this.commitTextPageAnchor()")
		assertTrue(
			attributeChanged.indexOf("this.commitTextPageAnchor()") <
				attributeChanged.indexOf("this.render()"),
			"Explicit paginator layout changes must retire an exact page identity to its semantic range before reflow."
		)
		assertTrue(
			setStyles.indexOf("this.commitTextPageAnchor()") <
				setStyles.indexOf("\$style.textContent"),
			"Typography changes must retire an exact page identity before mutating document styles."
		)
		assertTrue(
			Regex("""\bthis\.scrolled\b""").findAll(exact).count() >= 3,
			"Exact navigation must recheck paginated flow after each suspension point."
		)
		assertTrue(
			exact.lastIndexOf("this.#destroyed || generation !== this.#navigationGeneration") >
				exact.indexOf("await this.#scrollToAnchor(requestedAnchor, reason)"),
			"Exact navigation destroyed during its final scroll must not report success."
		)
		assertContains(exact, "return false")
		assertContains(exact, "return true")
		assertContains(exact, "try {")
		assertContains(exact, "finally {")
		assertContains(exact, "this.#locked = false")
		assertContains(acquireExactLock, "if (this.#destroyed) return false")
		assertContains(acquireExactLock, "return true")
		assertContains(turn, "this.#locked = true")
		assertContains(turn, "const generation = ++this.#navigationGeneration")
		assertContains(turn, "}, generation)")
		assertContains(turn, "if (this.#destroyed || generation !== this.#navigationGeneration) return false")
		assertTrue(
			turn.indexOf("if (this.#destroyed || generation !== this.#navigationGeneration) return false") >
				turn.indexOf("await wait(100)"),
			"A turn destroyed or superseded during settlement delay must not report success."
		)
		assertContains(turn, "try {")
		assertContains(turn, "finally {")
		assertContains(turn, "this.#locked = false")
		assertContains(goTo, "throw e")
		assertTrue(
			goTo.lastIndexOf("onCancel()") > goTo.indexOf(".catch(e =>"),
			"A rejected section load must release its target-section ownership."
		)
		assertFalse(goTo.contains("return {}"))
	}

	@Test
	fun passiveAndLiveDestinationRenderingShareExactPageNavigation() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val prepare = preview
			.substringAfter("async function preparePageTurnPreview(")
			.substringBefore("\n}\n")
		val prepareBatchItem = preview
			.substringAfter("async function preparePageTurnPreviewBatchItem(")
			.substringBefore("\n}\n")

		assertContains(turns, "readerGoToExactVisualPage(this.view, locator)")
		assertContains(prepare, "readerGoToExactVisualPage(previewView, locator, 'page-turn-preview')")
		assertContains(prepare, "if (!reached) throw new Error")
		assertContains(prepareBatchItem, "if (!reached) throw new Error")
		assertTrue(
			prepare.indexOf("generation !== this.pageTurnPreviewGeneration") <
				prepare.indexOf("if (!reached) throw new Error"),
			"Only superseded preview preparation may return silently after canceled navigation."
		)
		assertFalse(prepare.contains("anchor: locator.anchor"))
	}

	@Test
	fun synchronousExactRelocationIsCapturedBeforeOrdinaryStaleFiltering() {
		val location = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val onRelocate = location
			.substringAfter("function onRelocate(detail) {")
			.substringBefore("\n}\n")
		val exactFence = onRelocate.indexOf("if (this.exactPageTurnNavigationInProgress) {")
		val staleFilter = onRelocate.indexOf("this.pageTurnRelocationDetailIsStale(")
		val exactBlock = onRelocate
			.substringAfter("if (this.exactPageTurnNavigationInProgress) {")
			.substringBefore("\n  }")

		assertTrue(exactFence >= 0)
		assertTrue(staleFilter > exactFence)
		assertContains(exactBlock, "this.relocateSequence += 1")
		assertContains(exactBlock, "this.lastRelocateDetail = detail")
		assertContains(exactBlock, "return")
	}

	@Test
	fun settlementRequiresTokenAndExactVisualPage() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val location = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")
		val completeSettlement = turns
			.substringAfter("function maybeCompleteNativePageTurnSettlement(")
			.substringBefore("\n}\n")
		val postLocationChanged = location
			.substringAfter("function postLocationChanged(")
			.substringBefore("\n}\n")

		assertContains(runtime, "nativePageTurnSettledState")
		assertContains(runtime, "nativePageTurnSettledState: () => runtime.nativePageTurnSettledState")
		assertContains(runtime, "pendingExactPageTurnSettlements = new Map()")
		assertContains(runtime, "activeExactPageTurnSettlementToken = null")
		assertContains(runtime, "exactPageTurnNavigationInProgress = false")
		assertContains(turns, "this.pendingExactPageTurnSettlements.set(token, pending)")
		assertContains(turns, "this.activeExactPageTurnSettlementToken = token")
		assertContains(turns, "foliateSessionId: settleSessionId")
		assertContains(turns, "rasterGeneration: settleRasterGeneration")
		assertContains(turns, "textureGeneration: settleTextureGeneration")
		assertContains(turns, "pageIndex: locator.pageIndex")
		assertContains(turns, "spineIndex: locator.spineIndex")
		assertContains(turns, "chapterPageIndex: locator.chapterPageIndex")
		assertContains(turns, "paginationProfile: this.paginationProfile")
		assertContains(turns, "pending.paginationProfile !== this.paginationProfile")
		assertContains(turns, "Math.floor(spineIndex) !== pending.spineIndex")
		assertContains(turns, "Math.floor(chapterPageIndex) !== pending.chapterPageIndex")
		assertContains(turns, "readerTrace('page-turn:exact-settle-pending'")
		assertContains(turns, "requestedSpineIndex: pending.spineIndex")
		assertContains(turns, "actualSpineIndex: Number.isFinite(spineIndex) ? Math.floor(spineIndex) : null")
		assertFalse(
			completeSettlement.contains("commitTextPageAnchor"),
			"Settlement must retain the exact page identity through asynchronous paginator reflows."
		)
		assertTrue(
			exactNavigation.indexOf("await readerGoToExactVisualPage(this.view, locator)") <
				exactNavigation.indexOf("this.maybeCompleteNativePageTurnSettlement(this.currentPagePosition)"),
			"The synchronous exact position may settle only after renderer navigation resolves."
		)
		assertContains(postLocationChanged, "this.maybeCompleteNativePageTurnSettlement(pagePosition)")
		assertTrue(
			postLocationChanged.indexOf("this.tryUpdateReaderPageNumberLayer(") <
				postLocationChanged.indexOf("this.maybeCompleteNativePageTurnSettlement(pagePosition)"),
			"Settlement must use the delayed committed relocation's exact physical page identity."
		)
	}

	@Test
	fun runtimeExposesPendingExactSettlementIdentityForDiagnostics() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()

		assertContains(
			runtime,
			"nativePageTurnPendingState: () => runtime.activeExactPageTurnSettlement()"
		)
	}

	@Test
	fun exactTurnFreezesItsPaginationProfileUntilStableDestinationSettles() {
		val pagination = readerAssetRoot().resolve("navic-reader-pagination.js").readText()
		val ensureProfile = pagination
			.substringAfter("function readerEnsurePaginationProfile(")
			.substringBefore("\n}\n")

		assertContains(ensureProfile, "this.activeExactPageTurnSettlement()?.paginationProfile")
		assertContains(ensureProfile, "this.paginationProfile = exactTurnProfile")
		assertContains(ensureProfile, "return exactTurnProfile")
	}

	@Test
	fun exactCommittedPositionUsesPendingTargetWhenPhysicalIdentityMatches() {
		val pagination = readerAssetRoot().resolve("navic-reader-pagination.js").readText()
		val committedPosition = pagination
			.substringAfter("function committedPageTurnPosition(")
			.substringBefore("\n}\n")

		assertContains(committedPosition, "this.activeExactPageTurnSettlement()")
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
	fun cancellationClearsPendingAndUndeliveredSettledAcknowledgements() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val cancellation = turns
			.substringAfter("function cancelPendingExactPageTurnSettlement(reason = 'superseded')")
			.substringBefore("\n}\n")

		assertContains(cancellation, "const pending = this.activeExactPageTurnSettlement()")
		assertContains(cancellation, "const settled = this.nativePageTurnSettledState")
		assertContains(cancellation, "if (!pending && !settled) return false")
		assertContains(cancellation, "this.pendingExactPageTurnSettlements.delete(pending.token)")
		assertContains(cancellation, "this.nativePageTurnSettledState = null")
		assertContains(cancellation, "rememberCompletedExactPageTurnSettlement(this, pending)")
		assertContains(cancellation, "rememberCompletedExactPageTurnSettlement(this, settled)")
		assertFalse(cancellation.contains("cancelled: true"))
	}

	@Test
	fun supersededExactNavigationCannotClearOrPublishForItsSuccessor() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")

		assertContains(runtime, "exactPageTurnNavigationToken = null")
		assertContains(exactNavigation, "this.exactPageTurnNavigationToken = token")
		assertContains(exactNavigation, "this.exactPageTurnNavigationToken === token")
		assertContains(exactNavigation, "this.activeExactPageTurnSettlementToken !== token")
		assertContains(exactNavigation, "this.pendingExactPageTurnSettlements.get(token) !== pending")
		val navigation = exactNavigation.indexOf("await readerGoToExactVisualPage(this.view, locator)")
		val ownershipFence = exactNavigation.indexOf(
			"this.activeExactPageTurnSettlementToken !== token",
			startIndex = navigation
		)
		val history = exactNavigation.indexOf("this.view.history?.pushState?.(")
		assertTrue(navigation >= 0)
		assertTrue(ownershipFence > navigation)
		assertTrue(history > ownershipFence)
	}

	@Test
	fun exactNavigationFailureClearsOnlyItsControlledRelocation() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")
		val failure = exactNavigation.substringAfter("} catch (error) {")

		assertContains(failure, "const ownsPendingExactNavigation =")
		assertContains(failure, "this.controlledRelocateReason === 'page-turn:exact'")
		assertContains(failure, "this.consumeControlledRelocationReason('page-turn:exact-failed')")
	}

	@Test
	fun settlementTokensAreOneShotAndConflictingReuseIsRejected() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")
		val completion = turns
			.substringAfter("function rememberCompletedExactPageTurnSettlement(")
			.substringBefore("\n}\n")

		assertContains(runtime, "completedExactPageTurnSettlements = new Map()")
		assertContains(exactNavigation, "this.pendingExactPageTurnSettlements.get(token)")
		assertContains(exactNavigation, "this.nativePageTurnSettledState?.token === token")
		assertContains(exactNavigation, "this.completedExactPageTurnSettlements.get(token)")
		assertContains(exactNavigation, "exactPageTurnSettlementMatches(")
		assertContains(exactNavigation, "throw new TypeError('Visual page settlement token cannot be reused')")
		assertContains(completion, "runtime.completedExactPageTurnSettlements.set(token, settlement)")
		assertFalse(completion.contains("runtime.completedExactPageTurnSettlements.delete"))
		assertFalse(completion.contains("runtime.completedExactPageTurnSettlements.size"))
	}

	@Test
	fun undeliveredSettlementRetainsAndRevalidatesFullVisualIdentity() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val settled = turns
			.substringAfter("this.nativePageTurnSettledState = Object.freeze({")
			.substringBefore("\n  })")
		val peek = turns
			.substringAfter("function peekNativePageTurnSettlement(")
			.substringBefore("\n}\n")

		assertContains(settled, "spineIndex: pending.spineIndex")
		assertContains(settled, "chapterPageIndex: pending.chapterPageIndex")
		assertContains(settled, "paginationProfile: pending.paginationProfile")
		assertContains(peek, "settlement.foliateSessionId !== this.foliateSessionId")
		assertContains(peek, "settlement.paginationProfile !== this.paginationProfile")
		assertContains(peek, "Math.floor(spineIndex) !== settlement.spineIndex")
		assertContains(peek, "Math.floor(chapterPageIndex) !== settlement.chapterPageIndex")
		assertContains(peek, "this.consumeNativePageTurnSettlement(settlement.token)")
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
		(2..5).forEach { offset ->
			assertContains(
				plan,
				"addTarget(centerPageIndex + step * $offset, 'next-lookahead')"
			)
		}
		(2..5).forEach { offset ->
			assertContains(
				plan,
				"addTarget(centerPageIndex - step * $offset, 'previous-lookahead')"
			)
		}
		assertContains(plan, "chapterPages(currentChapter)")
		assertContains(plan, ".forEach(pageIndex => addTarget(pageIndex, 'current-chapter'))")
		assertContains(plan, "physicalPagesInRange(currentChapterEnd, pageCount)")
		assertContains(plan, ".forEach(pageIndex => addTarget(pageIndex, 'next-chapter'))")
		assertContains(plan, "physicalPagesInRange(0, currentChapterStart, true)")
		assertContains(plan, ".forEach(pageIndex => addTarget(pageIndex, 'previous-chapter'))")
		val blockingEnd = plan.indexOf(
			"addTarget(centerPageIndex - step * 5, 'previous-lookahead')"
		)
		val currentChapter = plan.indexOf("chapterPages(currentChapter)")
		val forward = plan.indexOf("physicalPagesInRange(currentChapterEnd, pageCount)")
		val backward = plan.indexOf("physicalPagesInRange(0, currentChapterStart, true)")
		assertTrue(blockingEnd >= 0 && currentChapter > blockingEnd)
		assertTrue(forward > currentChapter)
		assertTrue(backward > forward)
		assertContains(plan, "layoutMode === 'spread' ? 2 : 1")
		assertContains(plan, "pageStartIndex")
		assertContains(plan, "pageCount")
		assertFalse(plan.contains("next-chapter-remainder"))
		assertFalse(plan.contains("previous-chapter-remainder"))
		assertFalse(plan.contains("setTimeout"))
	}

	@Test
	fun rasterPreparationClampsEachChapterWithoutWrappingAcrossSpineRanges() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val plan = preview
			.substringAfter("function pageTurnRasterPreparationPlan(")
			.substringBefore("function pageTurnPreviewContext(")
		val physicalPagesInRange = plan
			.substringAfter("const physicalPagesInRange = (start, end, reverse = false) => {")
			.substringBefore("\n  }\n  const chapterPages = chapter => {")
		val chapterPages = plan
			.substringAfter("const chapterPages = chapter => {")
			.substringBefore("\n  }\n\n  addTarget(centerPageIndex")

		assertContains(physicalPagesInRange, "const pageStartIndex = Math.max(0")
		assertContains(
			physicalPagesInRange,
			"const pageEndIndex = Math.min(pageCount, Math.max(pageStartIndex"
		)
		assertContains(physicalPagesInRange, "pageIndex < pageEndIndex")
		assertContains(physicalPagesInRange, "Math.abs(pageIndex - centerPageIndex) % step === 0")
		assertContains(physicalPagesInRange, "return reverse ? pages.reverse() : pages")
		assertContains(chapterPages, "const pageStartIndex = Math.max(0")
		assertContains(chapterPages, "const chapterPageCount = Math.max(0")
		assertContains(
			chapterPages,
			"return physicalPagesInRange(pageStartIndex, pageStartIndex + chapterPageCount)"
		)
		assertFalse(physicalPagesInRange.contains("% pageCount"))
		assertFalse(chapterPages.contains("% pageCount"))
		assertFalse(chapterPages.contains("currentChapterIndex"))
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
	fun physicalRasterGeometryIsRecomputedFromTheCurrentViewport() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val captureGeometry = runtime
			.substringAfter("pageTurnCaptureGeometry() {")
			.substringBefore("\n  }\n}\n\nObject.assign")

		assertContains(captureGeometry, "const spreadMode = readerSurfaceSpreadMode({")
		assertContains(captureGeometry, "const layoutProfile = readerPaperLayoutProfile({")
		assertContains(captureGeometry, "const geometry = readerSurfacePageDecorationGeometry({")
		assertFalse(captureGeometry.contains("this.surfaceSpreadMode ||"))
		assertFalse(captureGeometry.contains("this.surfacePaperLayoutProfile ||"))
		assertFalse(captureGeometry.contains("this.surfacePageDecorationGeometry ||"))
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
		assertContains(restore, "this.pageTurnPreviewLivePagePosition")
		assertContains(restore, "this.updateReaderPageNumberLayer(restoredLivePagePosition)")
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
	fun rasterPreparationPlanCarriesTheGeometryFromTheSameRuntimeObservation() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val plan = preview
			.substringAfter("function pageTurnRasterPreparationPlan(")
			.substringBefore("function pageTurnPreviewContext()")

		assertContains(plan, "const geometry = this.pageTurnCaptureGeometry()")
		assertContains(plan, "captureGeometry: geometry")
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
	fun snapshotCacheIsBoundedAndReleasesUnprotectedEvictedEntries() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)")
		assertContains(source, "private const val MaxCachedSnapshots = 5")
		assertContains(source, "key.visualPageIndex !in protectedSnapshotPageIndices")
		assertContains(source, "eviction.value.releaseCacheOwnership()")
		assertContains(source, "snapshotCache.clear()")
	}

	@Test
	fun stagedCaptureRejectsAndRecyclesStaleGenerationsWithoutTimeouts() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "captureSurface(")
		assertContains(source, "captureStagedSurface(")
		assertContains(source, "if (generation != activeGeneration)")
		assertContains(source, "current.bitmap.takeUnless { it.isRecycled }?.recycle()")
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
	fun preparedCaptureUsesPresentedDestinationGeometryInsteadOfCurrentReference() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val bitmapSource = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()
		val capture = source
			.substringAfter("private fun capturePreparedPage(")
			.substringBefore("fun cacheCurrentSnapshot(")
		val preparedSurface = source
			.substringAfter("private fun capturePreparedSurface(")
			.substringBefore("private fun captureCompositedSurface(")

		assertContains(capture, "readerPagePreparedSnapshotGeometry(kind, it)")
		assertContains(capture, "surfaceRectInWindow = snapshotGeometry.surfaceRectInWindow")
		assertContains(capture, "leafGeometry = snapshotGeometry.leafGeometry")
		assertContains(capture, "reverseFaceColor = snapshotGeometry.reverseFaceColor")
		assertFalse(capture.contains("leafGeometry = reference.leafGeometry"))
		assertTrue(
			capture.indexOf("capturePreparedSurface(") <
				capture.lastIndexOf("restoreLiveComposition(webView, token)"),
			"The exposed destination must be captured before live composition is restored."
		)
		assertContains(preparedSurface, "bitmapSource.capturePresentedSurface(")
		assertFalse(preparedSurface.contains("captureCompositedSurface("))
		assertContains(bitmapSource, "pageTurnCaptureGeometry")
		assertContains(bitmapSource, "parseGeometry(encodedGeometry)")
		assertContains(bitmapSource, "resolved.surfaceRectInWindow(")
	}

	@Test
	fun capturedDestinationMustMatchTheBatchPhysicalLayoutBeforePublication() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val capture = source
			.substringAfter("fun capturePreparedRasterPage(")
			.substringBefore("private fun capturePreparedPage(")

		assertContains(
			capture,
			"readerPageRasterPhysicalLayoutMatches(captured, reference)"
		)
		assertTrue(
			capture.indexOf("readerPageRasterPhysicalLayoutMatches(captured, reference)") <
				capture.indexOf("putSnapshot(")
		)
	}

	@Test
	fun physicalLayoutReplacementFencesPendingAndDurableRasterWork() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val authority = source
			.substringAfter("private fun activatePhysicalLayout(")
			.substringBefore("private fun currentPhysicalLayoutEpoch(")
		val persistence = source
			.substringAfter("private fun schedulePersistentSnapshot(")
			.substringBefore("private fun rasterPersistenceSkipped(")
		val removal = source
			.substringAfter("private suspend fun removePersistentRaster(")
			.substringBefore("private fun isHydrationCurrent(")

		assertContains(authority, "rasterPhysicalLayoutEpoch.incrementAndGet()")
		assertContains(authority, "publicationLedger.invalidate()")
		assertContains(authority, "publicationScheduler.cancelBeforeEpoch(")
		assertContains(persistence, "currentPhysicalLayoutEpoch(snapshot.key.kind, layout)")
		assertContains(persistence, "physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get()")
		assertContains(persistence, "store?.contains(value.key, metadata)")
		assertContains(persistence, "val persistedForCurrentLayout = write.persisted &&")
		assertContains(persistence, "if (!accepted || !persistedForCurrentLayout)")
		assertContains(removal, "store.remove(key, expectedMetadata)")
	}

	@Test
	fun compositedCapturePublishesOpaquePreparedRasters() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val capture = source
			.substringAfter("private fun captureCompositedSurface(")
			.substringBefore("fun invalidatePage(")

		assertContains(capture, "canvas.drawColor(backgroundColor)")
		assertContains(capture, "webView.draw(canvas)")
		assertContains(capture, "bitmap.setHasAlpha(false)")
		assertContains(capture, "bitmap.setPremultiplied(true)")
		assertTrue(capture.indexOf("webView.draw(canvas)") < capture.indexOf("bitmap.setHasAlpha(false)"))
		assertTrue(capture.indexOf("bitmap.setPremultiplied(true)") < capture.indexOf("onCaptured(bitmap)"))
	}

	@Test
	fun persistentRasterHydrationCopiesDecodedBitmapAndRebindsLiveSurface() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val hydration = source
			.substringAfter("fun hydrateSnapshot(")
			.substringBefore("fun capturePreparedRasterPage(")

		assertContains(hydration, "persistentStore")
		assertContains(hydration, "productionStore?.readCopy(key)")
		assertContains(hydration, "cached.copy(Bitmap.Config.ARGB_8888, false)")
		assertContains(hydration, "readerPageRasterPhysicalLayout(reference)")
		assertContains(hydration, "physicalLayout = request.identity.physicalLayout")
		assertContains(hydration, "surfaceRectInWindow = Rect(surfaceRectInWindow)")
		assertContains(hydration, "readerPageRasterLeafGeometry(")
		assertContains(hydration, "persist = false")
		assertContains(hydration, "generation != activeGeneration")
		assertContains(hydration, "recipient.publicationFence")
		assertFalse(hydration.contains("rasterCache?.read"))
		assertFalse(hydration.contains("postDelayed"))
		assertFalse(hydration.contains("withTimeout"))
	}

	@Test
	fun preparedCaptureRetainsPersistenceBeforeLruEvictionAndCompletesAfterPublication() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val capture = source
			.substringAfter("fun capturePreparedRasterPage(")
			.substringBefore("private fun capturePreparedPage(")
		val insertion = source
			.substringAfter("private fun putSnapshot(")
			.substringBefore("private fun trimSnapshotCacheToCapacity()")

		assertFalse(capture.contains("putSnapshot(captured, priority, persist = false)"))
		assertContains(capture, "onPersisted = { persisted ->")
		assertContains(capture, "onCaptured(persisted)")
		assertContains(insertion, "onPersisted: (Boolean) -> Unit = {}")
		assertTrue(
			insertion.indexOf("schedulePersistentSnapshot(") < insertion.indexOf("trimSnapshotCacheToCapacity()"),
			"Persistence must retain the inserted snapshot before an LRU trim can release cache ownership"
		)
	}

	@Test
	fun presentationReceiptModuleLoadsThroughTheReaderBundleAndExposesBridgeGetters() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()

		assertContains(preview, "from './navic-reader-page-turn-presentation.js'")
		assertContains(turns, "from './navic-reader-page-turn-presentation.js'")
		assertContains(runtime, "pageTurnPresentationSequence = 0")
		assertContains(runtime, "pageTurnPreviewPresentationReceiptValue = null")
		assertContains(runtime, "pageTurnLivePresentationReceiptValue = null")
		assertContains(runtime, "pageTurnLivePresentationTargetValue = null")
		assertContains(
			runtime,
			"pageTurnPreviewPresentationReceipt: () => runtime.pageTurnPreviewPresentationReceipt()"
		)
		assertContains(
			runtime,
			"pageTurnLivePresentationReceipt: () => runtime.pageTurnLivePresentationReceipt()"
		)
	}

	@Test
	fun previewReceiptIsIssuedOnlyAfterExposureAndClearedWithItsComposition() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val expose = preview
			.substringAfter("function exposePageTurnPreviewFinal(")
			.substringBefore("function restorePageTurnLiveComposition(")
		val restore = preview
			.substringAfter("function restorePageTurnLiveComposition(")
			.substringBefore("function destroyPageTurnPreviewRenderer(")
		val destroy = preview
			.substringAfter("function destroyPageTurnPreviewRenderer(")
			.substringBefore("export const NavicReaderPageTurnPreviewMethods")

		assertContains(expose, "scope: ReaderPageTurnPresentationScopePreview")
		assertContains(expose, "token: state.token")
		assertContains(expose, "pageIndex: state.pageIndex")
		assertContains(expose, "previewGeneration: state.generation")
		assertTrue(
			expose.indexOf("this.pageTurnPreviewExposedToken = state.token") <
				expose.indexOf("this.issuePageTurnPreviewPresentationReceipt("),
			"A preview receipt may be issued only after the preview owns the exposed composition."
		)
		assertContains(preview, "function pageTurnPreviewPresentationReceipt()")
		assertContains(preview, "readerPageTurnPresentationReceiptMatches(receipt, target)")
		assertContains(restore, "this.clearPageTurnPreviewPresentationReceipt()")
		assertContains(destroy, "this.clearPageTurnPreviewPresentationReceipt()")
		assertContains(preview, "this.clearPageTurnPreviewPresentationReceipt()")
	}

	@Test
	fun liveReceiptFollowsExactSettlementAndRevalidatesCurrentLiveState() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val complete = turns
			.substringAfter("function maybeCompleteNativePageTurnSettlement(")
			.substringBefore("function peekNativePageTurnSettlement(")
		val cancellation = turns
			.substringAfter("function cancelPendingExactPageTurnSettlement(")
			.substringBefore("function nextPage()")
		val getter = turns
			.substringAfter("function pageTurnLivePresentationTargetMatchesCurrent(")
			.substringBefore("async function goToVisualPage(")
		val restore = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
			.substringAfter("function restorePageTurnLiveComposition(")
			.substringBefore("function destroyPageTurnPreviewRenderer(")

		assertContains(complete, "scope: ReaderPageTurnPresentationScopeLive")
		assertContains(complete, "token: pending.token")
		assertContains(complete, "pageIndex: settledPageIndex")
		assertContains(complete, "foliateSessionId: pending.foliateSessionId")
		assertContains(complete, "rasterGeneration: pending.rasterGeneration")
		assertContains(complete, "textureGeneration: pending.textureGeneration")
		assertContains(complete, "relocationEpoch: this.relocateSequence")
		assertTrue(
			complete.indexOf("this.nativePageTurnSettledState = Object.freeze({") <
				complete.indexOf("this.issuePageTurnLivePresentationReceipt("),
			"The live receipt must follow exact settlement."
		)
		assertContains(turns, "function pageTurnLivePresentationReceipt()")
		assertContains(getter, "this.pageTurnLivePresentationTargetValue")
		assertContains(getter, "this.completedExactPageTurnSettlements.get(target.token)")
		assertContains(getter, "target.relocationEpoch !== this.relocateSequence")
		assertContains(getter, "readerPageTurnPresentationReceiptMatches(receipt, receiptTarget)")
		assertFalse(
			getter.contains("peekNativePageTurnSettlement"),
			"Receipt lookup must survive settlement acknowledgement consumption."
		)
		assertContains(cancellation, "this.clearPageTurnLivePresentationTarget()")
		assertContains(runtime, "runtime.clearPageTurnLivePresentationTarget()")
		assertContains(restore, "const retainedLivePositionIsCurrent =")
		assertContains(restore, "this.pageTurnLivePresentationTargetMatchesCurrent(")
		assertContains(restore, "? this.currentPagePosition")
		assertContains(restore, "this.restorePageTurnLivePresentationReceipt()")
	}
}
