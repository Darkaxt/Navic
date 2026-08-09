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
		assertContains(turns, "command.settleGestureId")
		assertContains(turns, "command.settleSessionId")
		assertContains(turns, "command.settleRasterGeneration")
		assertContains(turns, "command.settleTextureGeneration")
		assertContains(turns, "!Number.isInteger(pageIndex) || pageIndex < 0")
		assertContains(turns, "!Number.isSafeInteger(settleGestureId) || settleGestureId <= 0")
		assertContains(turns, "settleSessionId !== this.foliateSessionId")
		assertContains(turns, "!Number.isInteger(settleRasterGeneration)")
		assertContains(turns, "!Number.isInteger(settleTextureGeneration)")
		assertContains(turns, "readerPageLocatorForVisualIndex")
	}

	@Test
	fun exactVisualPageNavigationUsesOneRendererCommitTarget() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val viewport = readerAssetRoot().resolve("navic-reader-viewport.js").readText()
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")
		val exactCommit = turns
			.substringAfter("async function commitPendingExactPageTurnSettlement(")
			.substringBefore("\n}\n\nasync function goToVisualPage(")

		assertContains(exactNavigation, "return await this.commitPendingExactPageTurnSettlement(token)")
		assertContains(preview, "export async function readerGoToExactVisualPage(view, locator, reason = 'page-turn:exact')")
		assertContains(preview, "return readerCommitTextPage(")
		assertContains(exactCommit, "await readerGoToExactVisualPage(")
		assertContains(exactCommit, "readerTextPageCommitIsValid(")
		assertContains(exactCommit, "readerTextPageCommitMatches(result, expectedPosition)")
		assertContains(exactCommit, "readerRememberTextPageCommit(")
		assertContains(exactCommit, "readerRememberTextPageVisibleContent(pending)")
		assertContains(paginator, "async commitTextPage(index, pageIndex, reason = 'navigation')")
		assertContains(paginator, "await this.#acquireExactNavigationLock()")
		assertContains(paginator, "async #acquireExactNavigationLock()")
		assertContains(paginator, "throw new Error('Exact page navigation timed out waiting for paginator unlock')")
		assertContains(paginator, "await this.#scrollToAnchor(actualTargetAnchor, reason)")
		assertContains(exactCommit, "this.view.history?.pushState?.(")
		val applyStableLayout = exactNavigation.indexOf(
			"this.applyReaderViewportLayout('page-turn:exact', { renderSynchronously: true })"
		)
		val navigateExactPage = exactNavigation.indexOf(
			"return await this.commitPendingExactPageTurnSettlement(token)"
		)
		val exactOperationStart = exactNavigation.indexOf(
			"this.exactPageTurnNavigationInProgress = true"
		)
		val exactNavigationStart = exactCommit.indexOf(
			"this.exactPageTurnNavigationInProgress = true"
		)
		val exactNavigationEnd = exactCommit.indexOf(
			"this.exactPageTurnNavigationInProgress = false"
		)
		val exactCommitAwait = exactCommit.indexOf("await readerGoToExactVisualPage(")
		val exactSettlement = exactCommit.indexOf(
			"this.maybeCompleteNativePageTurnSettlement("
		)
		assertTrue(applyStableLayout >= 0, "Exact navigation must establish the final renderer layout.")
		assertTrue(navigateExactPage >= 0, "Exact navigation must invoke the receipt transaction.")
		assertTrue(exactOperationStart >= 0, "Layout must join the token-scoped operation.")
		assertTrue(exactNavigationStart >= 0, "Exact navigation must publish its active state.")
		assertTrue(exactNavigationEnd >= 0, "Exact navigation must clear its active state.")
		assertTrue(
			exactOperationStart < applyStableLayout,
			"Synchronous layout invalidation must observe the transaction as in progress."
		)
		assertTrue(
			applyStableLayout < navigateExactPage,
			"Applying viewport layout after exact commitment would invalidate its receipt."
		)
		assertContains(viewport, "function applyReaderViewportLayout(label = 'unknown', options = {})")
		assertContains(viewport, "if (options.renderSynchronously) renderer.render?.()")
		assertContains(viewport, "else requestAnimationFrame(() => renderer?.render?.())")
		assertContains(
			exactCommit,
			"this.scheduleSettledControlledPageTurnRelocation('exact')"
		)
		assertTrue(exactCommitAwait >= 0)
		assertTrue(exactSettlement >= 0)
		assertTrue(exactCommitAwait < exactSettlement)
		assertFalse(exactNavigation.substring(navigateExactPage).contains("applyReaderViewportLayout("))
		assertFalse(exactCommit.contains("this.view?.next?.("))
		assertFalse(exactCommit.contains("this.view?.prev?.("))
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
		assertTrue(refresh >= 0)
		assertTrue(preload >= 0)
		assertTrue(refresh < preload)
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
		assertContains(createView, "const view = new View({")
		assertContains(createView, "this.#view = view")
		assertTrue(createCandidate >= 0, "The section load must create and install a candidate paginator view.")
		assertTrue(commitView >= 0)
		assertTrue(applyStyles >= 0)
		assertTrue(resolveAnchor >= 0)
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
			.substringAfter("async commitTextPage(index, pageIndex, reason = 'navigation') {")
			.substringBefore("\n    validateTextPageCommit(receipt) {")
		val exactCompatibilityWrapper = paginator
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
			.substringAfter("attributeChangedCallback(name, oldValue, value) {")
			.substringBefore("\n    open(book) {")
		val setStyles = paginator
			.substringAfter("setStyles(styles) {")
			.substringBefore("\n    focusView()")
		val acquireExactLock = paginator
			.substringAfter("async #acquireExactNavigationLock() {")
			.substringBefore("\n    #interruptedTextPageCommit(")
		val turn = paginator
			.substringAfter("async #turnPage(dir, distance) {")
			.substringBefore("\n    async prev(distance) {")
		val goTo = paginator
			.substringAfter("async #goTo({ index, anchor, select }")
			.substringBefore("\n    async goTo(target) {")

		assertContains(exact, "throw new TypeError('Text-page section and page coordinates must be non-negative integers')")
		assertContains(exact, "status: 'unsupported'")
		assertContains(exact, "reason: 'unsupported-flow'")
		assertContains(exact, "if (!await this.#acquireExactNavigationLock())")
		assertContains(exact, "const navigationGeneration = ++this.#navigationGeneration")
		assertContains(exact, "const measuredPageCount = this.pages - 2")
		assertContains(exact, "const actualTargetPageIndex = measuredPageCount > 0")
		assertContains(exact, "textPageIndex: actualTargetPageIndex")
		assertContains(exact, "view.expand()")
		assertContains(exact, "this.#recordContainerLayoutSignature()")
		assertContains(exact, "const fontLayoutGeneration = this.#layoutGeneration")
		assertContains(exact, "await view.document?.fonts?.ready")
		assertContains(exact, "layoutGeneration: fontLayoutGeneration")
		assertContains(exact, "this.#renderCurrentView()")
		assertContains(exact, "await this.#scrollToAnchor(actualTargetAnchor, reason)")
		val expand = exact.indexOf("view.expand()")
		val layoutSignature = exact.indexOf("this.#recordContainerLayoutSignature()")
		val fontGeneration = exact.indexOf("const fontLayoutGeneration = this.#layoutGeneration")
		val fontReady = exact.indexOf("await view.document?.fonts?.ready")
		val receiptGeneration = exact.indexOf("layoutGeneration: fontLayoutGeneration")
		assertTrue(expand >= 0)
		assertTrue(layoutSignature >= 0)
		assertTrue(fontGeneration >= 0)
		assertTrue(fontReady >= 0)
		assertTrue(receiptGeneration >= 0)
		assertTrue(
			expand < layoutSignature &&
				layoutSignature < fontGeneration &&
				fontGeneration < fontReady &&
				fontReady < receiptGeneration,
			"Font readiness must be fenced by the generation captured after initial observer measurements settle."
		)
		val selectActualPage = exact.indexOf(
			"const actualTargetPageIndex = measuredPageCount > 0"
		)
		val exactPlacement = exact.indexOf(
			"await this.#scrollToAnchor(actualTargetAnchor, reason)"
		)
		assertTrue(selectActualPage >= 0)
		assertTrue(exactPlacement >= 0)
		assertTrue(
			selectActualPage < exactPlacement,
			"Out-of-range requests must choose a valid actual page before the only exact-placement relocation."
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
		val attributeCommit = attributeChanged.indexOf("this.commitTextPageAnchor()")
		val attributeInvalidation = attributeChanged.indexOf(
			"this.#advanceTextLayoutGeneration('attribute-change')"
		)
		assertTrue(attributeCommit >= 0)
		assertTrue(attributeInvalidation >= 0)
		assertTrue(
			attributeCommit < attributeInvalidation,
			"Explicit paginator layout changes must retire an exact page identity before reflow invalidation."
		)
		val styleCommit = setStyles.indexOf("this.commitTextPageAnchor()")
		val styleMutation = setStyles.indexOf("\$style.textContent")
		assertTrue(styleCommit >= 0)
		assertTrue(styleMutation >= 0)
		assertTrue(
			styleCommit < styleMutation,
			"Typography changes must retire an exact page identity before mutating document styles."
		)
		val finalOwnershipFence = exact.lastIndexOf(
			"ownership({ view, viewGeneration, layoutGeneration })"
		)
		assertTrue(finalOwnershipFence >= 0)
		assertTrue(exactPlacement >= 0)
		assertTrue(
			finalOwnershipFence > exactPlacement,
			"Exact navigation invalidated or superseded during final placement must not issue a receipt."
		)
		assertContains(exact, "status: committed ? 'committed' : 'mismatch'")
		assertContains(exact, "try {")
		assertContains(exact, "finally {")
		assertContains(exact, "this.#locked = false")
		assertContains(exactCompatibilityWrapper, "await this.commitTextPage(index, pageIndex, reason)")
		assertContains(exactCompatibilityWrapper, "return result.status === 'committed'")
		assertFalse(exactCompatibilityWrapper.contains("#goTo("))
		assertFalse(exactCompatibilityWrapper.contains("#scrollToAnchor("))
		assertContains(acquireExactLock, "if (this.#destroyed) return false")
		assertContains(acquireExactLock, "return true")
		assertContains(turn, "this.#locked = true")
		assertContains(turn, "const generation = ++this.#navigationGeneration")
		assertContains(turn, "}, generation)")
		assertContains(turn, "if (this.#destroyed || generation !== this.#navigationGeneration) return false")
		val turnDelay = turn.indexOf("await wait(100)")
		val turnOwnershipFence = turn.indexOf(
			"if (this.#destroyed || generation !== this.#navigationGeneration) return false"
		)
		assertTrue(turnDelay >= 0)
		assertTrue(turnOwnershipFence >= 0)
		assertTrue(
			turnOwnershipFence > turnDelay,
			"A turn destroyed or superseded during settlement delay must not report success."
		)
		assertContains(turn, "try {")
		assertContains(turn, "finally {")
		assertContains(turn, "this.#locked = false")
		assertContains(goTo, "const targetSection = this.sections[index]")
		assertContains(goTo, "targetSection?.unload?.()")
		assertContains(goTo, ".then(() => targetSection.load())")
		assertContains(goTo, "throw e")
		val loadFailure = goTo.indexOf(".catch(e =>")
		val releaseTarget = goTo.lastIndexOf("onCancel()")
		assertTrue(loadFailure >= 0)
		assertTrue(releaseTarget >= 0)
		assertTrue(
			releaseTarget > loadFailure,
			"A rejected section load must release its target-section ownership."
		)
		assertFalse(goTo.contains("return {}"))
	}

	@Test
	fun passiveRasterReadinessRequiresAValidatedPaginatorCommitReceipt() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val resolve = preview
			.substringAfter("async function resolvePageTurnPreviewLocator(")
			.substringBefore("async function ensurePageTurnPreviewRenderer(")

		assertContains(preview, "from './navic-reader-paginator-commit.js'")
		assertContains(resolve, "await readerCommitTextPage(")
		assertContains(resolve, "readerTextPageCommitIsValid(view?.renderer, result)")
		assertContains(resolve, "readerTextPageCommitMatches(result, expectedPosition)")
		assertContains(resolve, "receipt: result.receipt")
		assertContains(resolve, "ReaderPageTurnMaximumCommitTransactionAttempts")
		assertFalse(resolve.contains("readerGoToExactVisualPage("))
		assertFalse(resolve.contains("readerWaitForStableTextPagePosition"))
		val passiveLayout = resolve.indexOf("this.applyReaderViewportLayoutToProfilerView(")
		val passiveCommit = resolve.indexOf("await readerCommitTextPage(")
		assertTrue(passiveLayout >= 0)
		assertTrue(passiveCommit >= 0)
		assertTrue(
			passiveLayout < passiveCommit,
			"Passive viewport layout must precede the paginator transaction."
		)
		assertFalse(
			resolve.substringAfter("await readerCommitTextPage(").contains(
				"this.applyReaderViewportLayoutToProfilerView("
			),
			"Passive layout must not be reapplied after exact commitment."
		)
	}

	@Test
	fun failedPassivePreviewRestorationCannotPublishCapturedPixels() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val capture = source
			.substringAfter("private fun capturePreparedPage(")
			.substringBefore("fun cacheCurrentSnapshot(")
		val restore = source
			.substringAfter("private fun restoreLiveComposition(")
			.substringBefore("\n\t}\n}")

		assertContains(capture, "capturePreparedSurface(")
		val captureAndRestore = capture.substringAfter("capturePreparedSurface(")
		assertTrue(
			Regex(
				"""restoreLiveComposition\(\s*webView,\s*token,\s*mutationGeneration,""" +
					"""\s*isStillCurrent\s*\)\s*\{\s*restored\s*->"""
			).containsMatchIn(captureAndRestore),
			"Captured pixels must be restored through the token, mutation generation, and currentness fence."
		)
		assertContains(capture, "!restored ||")
		assertContains(restore, "token: String")
		assertContains(restore, "mutationGeneration: ReaderForegroundWebViewMutationGeneration")
		assertContains(restore, "isStillCurrent: () -> Boolean")
		assertContains(restore, "onRestored: (Boolean) -> Unit")
		assertContains(restore, "restorePageTurnLiveComposition?.(")
		assertContains(restore, "mutationGeneration.value")
		assertContains(restore, "restored.isJavascriptTrue()")
		assertContains(restore, "!isStillCurrent()")
		assertContains(restore, "onRestored(false)")
		assertContains(restore, "onRestored(webView.isAttachedToWindow && isStillCurrent())")
	}

	@Test
	fun passiveAndLiveRenderingSharePaginatorCommitReceipts() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val resolve = preview
			.substringAfter("async function resolvePageTurnPreviewLocator(")
			.substringBefore("async function ensurePageTurnPreviewRenderer(")
		val liveCommit = turns
			.substringAfter("async function commitPendingExactPageTurnSettlement(")
			.substringBefore("\n}\n\nasync function goToVisualPage(")
		val prepare = preview
			.substringAfter("async function preparePageTurnPreview(")
			.substringBefore("function pageTurnPreviewBatchState(")
		val prepareBatchItem = preview
			.substringAfter("async function preparePageTurnPreviewBatchItem(")
			.substringBefore("function advancePageTurnPreviewBatch(")

		assertContains(turns, "from './navic-reader-paginator-commit.js'")
		assertContains(liveCommit, "readerGoToExactVisualPage(")
		assertContains(liveCommit, "readerTextPageCommitIsValid(")
		assertContains(liveCommit, "readerTextPageCommitMatches(result, expectedPosition)")
		assertContains(liveCommit, "readerRememberTextPageCommit(")
		assertContains(liveCommit, "ReaderLivePageTurnMaximumCommitTransactionAttempts")
		assertContains(liveCommit, "ReaderLivePageTurnMaximumPaginationProfileRepairs")
		assertFalse(liveCommit.contains("exactTextPagePosition"))
		assertFalse(liveCommit.contains("requestAnimationFrame"))
		assertContains(resolve, "readerCommitTextPage(")
		assertContains(resolve, "receipt: result.receipt")
		assertContains(resolve, "repairPaginationProfileFromExactPosition")
		assertFalse(resolve.contains("readerGoToExactVisualPage("))
		assertFalse(resolve.contains("exactTextPagePosition"))
		assertFalse(resolve.contains("view.renderer.page"))
		assertFalse(resolve.contains("view.renderer.pages"))
		assertFalse(resolve.contains("requestAnimationFrame"))
		val applyLayout = "this.applyReaderViewportLayoutToProfilerView("
		val passiveLayoutIndex = resolve.indexOf(applyLayout)
		val passiveCommitIndex = resolve.indexOf("const result = await readerCommitTextPage(")
		assertTrue(passiveLayoutIndex >= 0)
		assertTrue(passiveCommitIndex >= 0)
		assertTrue(
			passiveLayoutIndex < passiveCommitIndex,
			"The passive layout must be applied before exact commitment."
		)
		assertFalse(
			resolve.substringAfter("const result = await readerCommitTextPage(").contains(applyLayout),
			"Reapplying paginator attributes after commitment invalidates its receipt."
		)
		assertContains(prepare, "const commitment = await this.resolvePageTurnPreviewLocator(")
		assertContains(prepare, "readerRememberTextPageCommit(")
		assertContains(prepareBatchItem, "const commitment = await this.resolvePageTurnPreviewLocator(")
		assertContains(prepareBatchItem, "readerRememberTextPageCommit(")
		assertFalse(prepare.contains("anchor: locator.anchor"))
	}

	@Test
	fun livePresentationRequiresPrivateReceiptBoundVisibleContentProof() {
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val commitment = readerAssetRoot().resolve("navic-reader-paginator-commit.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val exactCommit = turns
			.substringAfter("async function commitPendingExactPageTurnSettlement(")
			.substringBefore("\n}\n\nasync function goToVisualPage(")
		val completion = turns
			.substringAfter("function maybeCompleteNativePageTurnSettlement(")
			.substringBefore("\n}\n")
		val liveTarget = turns
			.substringAfter("function pageTurnLivePresentationTargetMatchesCurrent(")
			.substringBefore("\n}\n")
		val liveReceipt = turns
			.substringAfter("function issuePageTurnLivePresentationReceipt(")
			.substringBefore("\n}\n")
		val receiptIssue = paginator
			.substringAfter("const receipt = Object.freeze({")
			.substringBefore("const committed =")

		assertContains(paginator, "#textPageVisibleContent = new WeakMap()")
		assertContains(paginator, "#normalizedVisibleText()")
		assertContains(paginator, ".normalize('NFKC')")
		assertContains(paginator, ".replace(/\\s+/gu, ' ')")
		assertContains(paginator, "#rememberTextPageVisibleContent(receipt)")
		assertContains(paginator, "validateTextPageVisibleContent(receipt)")
		assertContains(commitment, "const textPageVisibleContentOwners = new WeakMap()")
		assertContains(commitment, "readerRememberTextPageVisibleContent")
		assertContains(commitment, "readerTextPageCommitOwnerHasExpectedVisibleContent")
		assertContains(exactCommit, "readerRememberTextPageVisibleContent(pending)")
		assertContains(
			completion,
			"readerTextPageCommitOwnerHasExpectedVisibleContent(pending)"
		)
		assertContains(liveTarget, "readerTextPageCommitOwnerHasExpectedVisibleContent(target)")
		assertContains(
			liveReceipt,
			"readerTextPageCommitOwnerHasExpectedVisibleContent("
		)
		assertFalse(preview.contains("readerRememberTextPageVisibleContent"))
		assertFalse(preview.contains("readerTextPageCommitOwnerHasExpectedVisibleContent"))
		assertFalse(receiptIssue.contains("text:"))
		assertFalse(receiptIssue.contains("normalized"))
		assertFalse(receiptIssue.contains("digest"))
		assertFalse(receiptIssue.contains("fingerprint"))
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
		assertTrue(staleFilter >= 0)
		assertTrue(staleFilter > exactFence)
		assertContains(exactBlock, "this.relocateSequence += 1")
		assertContains(exactBlock, "this.lastRelocateDetail = detail")
		assertContains(exactBlock, "return")
	}

	@Test
	fun settlementRequiresTokenExactVisualPageAndValidPaginatorReceipt() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val location = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val exactCommit = turns
			.substringAfter("async function commitPendingExactPageTurnSettlement(")
			.substringBefore("\n}\n\nasync function goToVisualPage(")
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
		assertContains(turns, "gestureId: settleGestureId")
		assertContains(turns, "foliateSessionId: settleSessionId")
		assertContains(turns, "rasterGeneration: settleRasterGeneration")
		assertContains(turns, "textureGeneration: settleTextureGeneration")
		assertContains(turns, "pageIndex: locator.pageIndex")
		assertContains(turns, "spineIndex: locator.spineIndex")
		assertContains(turns, "chapterPageIndex: locator.chapterPageIndex")
		assertContains(turns, "paginationProfile: this.paginationProfile")
		assertContains(
			completeSettlement,
			"readerTextPageCommitOwnerHasExpectedVisibleContent(pending)"
		)
		assertContains(completeSettlement, "pending.paginationProfile !== this.paginationProfile")
		assertContains(completeSettlement, "Math.floor(spineIndex) !== pending.spineIndex")
		assertContains(completeSettlement, "Math.floor(chapterPageIndex) !== pending.chapterPageIndex")
		assertContains(completeSettlement, "readerCopyTextPageCommit(pending, settlement)")
		assertContains(completeSettlement, "readerCopyTextPageCommit(settlement, presentationTarget)")
		assertContains(
			completeSettlement,
			"this.lastTracedExactPageTurnGestureId !== pending.gestureId"
		)
		assertContains(turns, "readerTrace('page-turn:exact-settle-pending'")
		assertContains(turns, "requestedSpineIndex: pending.spineIndex")
		assertContains(turns, "actualSpineIndex: Number.isFinite(spineIndex) ? Math.floor(spineIndex) : null")
		assertFalse(
			completeSettlement.contains("commitTextPageAnchor"),
			"Settlement must retain exact receipt authority through asynchronous relocation."
		)
		val commitAwait = exactCommit.indexOf("await readerGoToExactVisualPage(")
		val settleAfterCommit = exactCommit.indexOf(
			"this.maybeCompleteNativePageTurnSettlement("
		)
		assertTrue(commitAwait >= 0)
		assertTrue(settleAfterCommit >= 0)
		assertTrue(
			commitAwait < settleAfterCommit,
			"The synchronous exact position may settle only after renderer commitment resolves."
		)
		assertContains(postLocationChanged, "this.maybeCompleteNativePageTurnSettlement(pagePosition)")
		val pageModelUpdate = postLocationChanged.indexOf(
			"this.tryUpdateReaderPageNumberLayer("
		)
		val delayedSettlement = postLocationChanged.indexOf(
			"this.maybeCompleteNativePageTurnSettlement(pagePosition)"
		)
		assertTrue(pageModelUpdate >= 0)
		assertTrue(delayedSettlement >= 0)
		assertTrue(
			pageModelUpdate < delayedSettlement,
			"Settlement must use the delayed committed relocation's exact physical page identity."
		)
	}

	@Test
	fun synchronousExactSettlementForcesItsTokenizedLocationDelivery() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val locations = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val exactCommit = turns
			.substringAfter("async function commitPendingExactPageTurnSettlement(")
			.substringBefore("\n}\n\nasync function goToVisualPage(")
		val locationDelivery = locations
			.substringAfter("function postLocationChanged(")
			.substringBefore("\n}\n")
		val navigation = exactCommit.indexOf("await readerGoToExactVisualPage(")
		val validation = exactCommit.indexOf("readerTextPageCommitIsValid(")
		val settlement = exactCommit.indexOf(
			"const settledSynchronously = this.maybeCompleteNativePageTurnSettlement("
		)
		val delivery = exactCommit.indexOf(
			"const synchronousDelivery = this.postCurrentLocationSnapshot("
		)
		val successfulDelivery = exactCommit.indexOf(
			"if (synchronousDelivery?.posted) {"
		)
		val delayedDelivery = exactCommit.indexOf(
			"if (!this.scheduleSettledControlledPageTurnRelocation('exact')) {"
		)

		assertTrue(navigation >= 0)
		assertTrue(validation >= 0)
		assertTrue(settlement >= 0)
		assertTrue(delivery >= 0)
		assertTrue(successfulDelivery >= 0)
		assertTrue(delayedDelivery >= 0)
		assertTrue(navigation < validation)
		assertTrue(validation < settlement)
		assertTrue(settlement < delivery)
		assertTrue(delivery < successfulDelivery)
		assertTrue(successfulDelivery < delayedDelivery)
		assertContains(exactCommit, "if (settledSynchronously) {")
		assertContains(exactCommit, "forceDuplicatePost: true")
		assertContains(exactCommit, "preserveCurrentPagePosition: true")
		assertContains(
			locationDelivery,
			"options.preserveCurrentPagePosition === true"
		)
		val preservedPosition = locationDelivery.indexOf("? this.currentPagePosition")
		val recomputedPosition = locationDelivery.indexOf(
			": this.tryUpdateReaderPageNumberLayer("
		)
		assertTrue(preservedPosition >= 0)
		assertTrue(recomputedPosition >= 0)
		assertTrue(preservedPosition < recomputedPosition)
		assertContains(
			exactCommit.substring(successfulDelivery, delayedDelivery),
			"return locator"
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
		assertTrue(target >= 0)
		assertTrue(cancel < target, "The stale exact destination must be cancelled before the next target is derived.")
	}

	@Test
	fun cancellationRetiresPendingAndUndeliveredWorkWithoutSuccessfulCompletion() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val cancellation = turns
			.substringAfter("function cancelPendingExactPageTurnSettlement(reason = 'superseded')")
			.substringBefore("\n}\n")

		assertContains(runtime, "retiredExactPageTurnSettlements = new Map()")
		assertContains(cancellation, "const pending = this.activeExactPageTurnSettlement()")
		assertContains(cancellation, "const settled = this.nativePageTurnSettledState")
		assertContains(cancellation, "if (!pending && !settled) return false")
		assertContains(cancellation, "this.pendingExactPageTurnSettlements.delete(pending.token)")
		assertContains(cancellation, "this.nativePageTurnSettledState = null")
		assertContains(cancellation, "rememberRetiredExactPageTurnSettlement(this, retired)")
		assertFalse(cancellation.contains("rememberCompletedExactPageTurnSettlement"))
		assertFalse(cancellation.contains("cancelled: true"))
	}

	@Test
	fun supersededExactNavigationCannotClearOrPublishForItsSuccessor() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")
		val exactCommit = turns
			.substringAfter("async function commitPendingExactPageTurnSettlement(")
			.substringBefore("\n}\n\nasync function goToVisualPage(")

		assertContains(runtime, "exactPageTurnNavigationToken = null")
		assertContains(exactCommit, "this.exactPageTurnNavigationToken = token")
		assertContains(exactCommit, "this.exactPageTurnNavigationToken === token")
		assertContains(exactCommit, "this.activeExactPageTurnSettlementToken !== token")
		assertContains(exactCommit, "this.pendingExactPageTurnSettlements.get(token) !== pending")
		val navigation = exactCommit.indexOf("await readerGoToExactVisualPage(")
		val ownershipFence = exactCommit.indexOf(
			"this.activeExactPageTurnSettlementToken !== token",
			startIndex = navigation
		)
		val profileFence = exactCommit.indexOf(
			"pending.paginationProfile !== this.paginationProfile",
			startIndex = navigation
		)
		val receiptValidation = exactCommit.indexOf(
			"readerTextPageCommitIsValid(",
			startIndex = navigation
		)
		val history = exactCommit.indexOf("this.view.history?.pushState?.(")
		assertTrue(navigation >= 0)
		assertTrue(ownershipFence >= 0)
		assertTrue(profileFence >= 0)
		assertTrue(receiptValidation >= 0)
		assertTrue(history >= 0)
		assertTrue(ownershipFence > navigation)
		assertTrue(profileFence > ownershipFence)
		assertTrue(receiptValidation > profileFence)
		assertTrue(history > receiptValidation)
		assertContains(exactNavigation, "this.cancelPendingExactPageTurnSettlement('superseded')")
		assertContains(exactNavigation, "const currentPending = this.pendingExactPageTurnSettlements.get(token)")
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
		assertContains(runtime, "retiredExactPageTurnSettlements = new Map()")
		assertContains(exactNavigation, "this.pendingExactPageTurnSettlements.get(token)")
		assertContains(exactNavigation, "this.nativePageTurnSettledState?.token === token")
		assertContains(exactNavigation, "this.completedExactPageTurnSettlements.get(token)")
		assertContains(exactNavigation, "this.retiredExactPageTurnSettlements.get(token)")
		assertContains(exactNavigation, "exactPageTurnSettlementIdentityMatches(")
		assertContains(exactNavigation, "throw new TypeError('Visual page settlement token cannot be reused')")
		assertContains(completion, "runtime.completedExactPageTurnSettlements.set(token, settlement)")
		assertFalse(completion.contains("runtime.completedExactPageTurnSettlements.delete"))
		assertFalse(completion.contains("runtime.completedExactPageTurnSettlements.size"))
	}

	@Test
	fun undeliveredSettlementRetainsPendingStateAndRevalidatesFullVisualIdentity() {
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val settled = turns
			.substringAfter("const settlement = Object.freeze({")
			.substringBefore("\n  })")
		val completion = turns
			.substringAfter("function maybeCompleteNativePageTurnSettlement(")
			.substringBefore("\n}\n")
		val peek = turns
			.substringAfter("function peekNativePageTurnSettlement(")
			.substringBefore("\n}\n")
		val consume = turns
			.substringAfter("function consumeNativePageTurnSettlement(")
			.substringBefore("\n}\n")

		assertContains(settled, "spineIndex: pending.spineIndex")
		assertContains(settled, "chapterPageIndex: pending.chapterPageIndex")
		assertContains(settled, "paginationProfile: pending.paginationProfile")
		assertFalse(completion.contains("pendingExactPageTurnSettlements.delete"))
		assertFalse(completion.contains("activeExactPageTurnSettlementToken = null"))
		assertContains(
			peek,
			"readerTextPageCommitOwnerHasExpectedVisibleContent(settlement)"
		)
		assertContains(peek, "settlement.foliateSessionId !== this.foliateSessionId")
		assertContains(peek, "settlement.paginationProfile !== this.paginationProfile")
		assertContains(peek, "Math.floor(spineIndex) !== settlement.spineIndex")
		assertContains(peek, "Math.floor(chapterPageIndex) !== settlement.chapterPageIndex")
		assertContains(peek, "this.handleLiveTextPageCommitInvalidation()")
		assertFalse(peek.contains("this.consumeNativePageTurnSettlement(settlement.token)"))
		assertContains(consume, "this.pendingExactPageTurnSettlements.delete(normalizedToken)")
		assertContains(consume, "rememberCompletedExactPageTurnSettlement(this, settlement)")
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
		assertContains(preview, "await readerCommitTextPage(")
		assertContains(preview, "readerTextPageCommitIsValid(")
		assertContains(preview, "previewView.close?.()")
		assertContains(preview, "previewView.remove?.()")
		assertFalse(preview.contains("requestAnimationFrame"))
		assertFalse(preview.contains("setTimeout"))
		assertFalse(preview.contains("Promise.race"))
	}

	@Test
	fun restoringPreviewPresentationDoesNotMutateCommittedPaginatorLayout() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		assertContains(runtime, "restorePageTurnLiveComposition:")
		assertContains(runtime, "pageTurnCaptureGeometry:")
		assertContains(preview, "function restorePageTurnLiveComposition(")
		assertContains(preview, "function destroyPageTurnPreviewRenderer(")
		val restoreBridge = runtime
			.substringAfter("restorePageTurnLiveComposition:")
			.substringBefore("pageTurnCaptureGeometry:")
		val restore = preview
			.substringAfter("function restorePageTurnLiveComposition(")
			.substringBefore("function destroyPageTurnPreviewRenderer(")

		assertTrue(
			Regex(
				"""\(\s*token,\s*foregroundMutationGeneration\s*\)\s*=>"""
			).containsMatchIn(restoreBridge),
			"The public restore bridge must require the foreground mutation generation."
		)
		assertTrue(
			Regex(
				"""runtime\.restorePageTurnLiveComposition\(""" +
					"""\s*token,\s*foregroundMutationGeneration\s*\)"""
			).containsMatchIn(restoreBridge),
			"The public restore bridge must forward the token and foreground mutation generation."
		)
		assertContains(restore, "token = ''")
		assertContains(restore, "foregroundMutationGeneration = null")
		assertContains(restore, "const requestedToken = String(token || '')")
		assertContains(restore, "const requestedMutationGeneration = foregroundMutationGeneration ??")
		assertContains(restore, "readerPageTurnPreviewMutationGenerationIsCurrent(")
		assertContains(restore, "requestedMutationGeneration !== expectedMutationGeneration")
		assertContains(restore, "requestedToken !== this.pageTurnPreviewExposedToken")
		assertContains(restore, "setStylesImportant(previewView")
		assertContains(restore, "visibility: 'hidden'")
		assertContains(restore, "'z-index': '-1'")
		assertFalse(restore.contains("applyReaderViewportLayoutToProfilerView"))
		assertFalse(restore.contains("applyReaderViewportLayout("))
		assertFalse(restore.contains("renderer?.render"))
	}

		@Test
		fun passivePreparationModesAreMutuallyExclusiveAndRejectStaleRestarts() {
			val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
			val standalone = preview
				.substringAfter("function beginPageTurnPreviewPreparation(")
				.substringBefore("async function preparePageTurnPreview(")
			val batch = preview
				.substringAfter("function beginPageTurnPreviewBatch(")
				.substringBefore("async function preparePageTurnPreviewBatchItem(")
			val restart = preview
				.substringAfter("function restartInvalidatedPageTurnPreviewCommitment(")
				.substringBefore("function pageTurnPreviewState(")

			assertContains(standalone, "this.restorePageTurnLiveComposition()")
			assertContains(standalone, "forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)")
			assertContains(standalone, "this.pageTurnPreviewBatchStateValue = null")
			assertContains(batch, "this.restorePageTurnLiveComposition()")
			assertContains(batch, "forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)")
			assertContains(batch, "this.pageTurnPreviewStateValue = null")
			assertContains(restart, "state.generation !== this.pageTurnPreviewGeneration")
			assertContains(preview, "initialProfileRepairs")
			assertFalse(preview.contains("function readerExactVisualPageMatches("))
		}

		@Test
		fun passiveReadyPresentationDescriptorAndBatchBoundariesRevalidateJsLocalOwnership() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val stateGetter = preview
			.substringAfter("function pageTurnPreviewState(token = '') {")
			.substringBefore("function clearPageTurnPreviewPresentationReceipt(")
		val presentationGetter = preview
			.substringAfter("function pageTurnPreviewPresentationReceipt() {")
			.substringBefore("const readerCssColorToArgb")
		val descriptor = preview
			.substringAfter("function pageTurnRasterDescriptor(pageIndex) {")
			.substringBefore("function pageTurnRasterPreparationPlan(")
		val batchGetter = preview
			.substringAfter("function pageTurnPreviewBatchState(token = '') {")
			.substringBefore("function beginPageTurnPreviewBatch(")

		assertContains(preview, "readerRememberTextPageCommit(")
		assertContains(stateGetter, "readerTextPageCommitOwnerIsValid(state)")
		assertContains(stateGetter, "this.restartInvalidatedPageTurnPreviewCommitment(state)")
		assertContains(presentationGetter, "readerTextPageCommitOwnerIsValid(state)")
		assertContains(descriptor, "readerTextPageCommitOwnerIsValid(readyState)")
		assertContains(batchGetter, "readerTextPageCommitOwnerIsValid(state)")
		assertContains(preview, "readerForgetTextPageCommit(state)")
		assertContains(preview, "transactionAttempts")
		assertContains(preview, "profileRepairs")
	}

	@Test
	fun passiveRasterBatchStagesOneExactOrdinalUntilNativeAdvancesIt() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		assertContains(runtime, "beginPageTurnPreviewBatch:")
		assertContains(runtime, "pageTurnPreviewBatchState:")
		assertContains(runtime, "advancePageTurnPreviewBatch:")
		assertContains(runtime, "cancelPageTurnPreviewBatch:")
		assertContains(preview, "function beginPageTurnPreviewBatch(")
		assertContains(preview, "async function preparePageTurnPreviewBatchItem(")
		assertContains(preview, "function advancePageTurnPreviewBatch(")
		assertContains(preview, "function cancelPageTurnPreviewBatch(")
		val beginBatchBridge = runtime
			.substringAfter("beginPageTurnPreviewBatch:")
			.substringBefore("pageTurnPreviewBatchState:")
		val advanceBatchBridge = runtime
			.substringAfter("advancePageTurnPreviewBatch:")
			.substringBefore("cancelPageTurnPreviewBatch:")
		val beginBatch = preview
			.substringAfter("function beginPageTurnPreviewBatch(")
			.substringBefore("async function preparePageTurnPreviewBatchItem(")
		val prepareBatchItem = preview
			.substringAfter("async function preparePageTurnPreviewBatchItem(")
			.substringBefore("function advancePageTurnPreviewBatch(")
		val advanceBatch = preview
			.substringAfter("function advancePageTurnPreviewBatch(")
			.substringBefore("function cancelPageTurnPreviewBatch(")

		assertTrue(
			Regex(
				"""\(\s*token,\s*pageIndexes,\s*foregroundMutationGeneration\s*\)\s*=>"""
			).containsMatchIn(beginBatchBridge),
			"The batch bridge must require the foreground mutation generation."
		)
		assertTrue(
			Regex(
				"""runtime\.beginPageTurnPreviewBatch\(""" +
					"""\s*token,\s*pageIndexes,\s*foregroundMutationGeneration\s*\)"""
			).containsMatchIn(beginBatchBridge),
			"The batch bridge must forward the foreground mutation generation."
		)
		assertContains(runtime, "pageTurnPreviewBatchState: token =>")
		assertTrue(
			Regex(
				"""\(\s*token,\s*pageIndex,\s*foregroundMutationGeneration\s*\)\s*=>"""
			).containsMatchIn(advanceBatchBridge),
			"Advancing a batch must retain the foreground mutation-generation contract."
		)
		assertTrue(
			Regex(
				"""runtime\.advancePageTurnPreviewBatch\(""" +
					"""\s*token,\s*pageIndex,\s*foregroundMutationGeneration\s*\)"""
			).containsMatchIn(advanceBatchBridge),
			"The advance bridge must forward the foreground mutation generation."
		)
		assertContains(beginBatch, "foregroundMutationGeneration")
		assertContains(beginBatch, "readerPageTurnPreviewMutationGenerationCanBeAdopted(")
		assertContains(beginBatch, "readerAdoptPageTurnPreviewMutationGeneration(")
		assertContains(beginBatch, "foregroundMutationGeneration,")
		assertContains(preview, "readerPageLocatorForVisualIndex(")
		assertContains(preview, "readerCommitTextPage(")
		assertContains(prepareBatchItem, "this.resolvePageTurnPreviewLocator(")
		assertContains(prepareBatchItem, "'page-turn-raster-batch'")
		assertContains(prepareBatchItem, "status: 'ready'")
		assertContains(prepareBatchItem, "spineIndex: locator.spineIndex")
		assertContains(prepareBatchItem, "href: locator.href")
		assertContains(prepareBatchItem, "chapterPageIndex: locator.chapterPageIndex")
		assertContains(prepareBatchItem, "visualPageOrdinal: locator.pageIndex")
		assertContains(
			advanceBatch,
			"state.foregroundMutationGeneration !== foregroundMutationGeneration"
		)
		assertContains(advanceBatch, "readerPageTurnPreviewMutationGenerationIsCurrent(")
		assertContains(
			advanceBatch,
			"state.status !== 'ready' ||"
		)
		assertContains(advanceBatch, "state.pageIndex !== completedPageIndex")
		assertFalse(prepareBatchItem.contains("post("), "The passive raster renderer must not publish reader events.")
		assertFalse(beginBatch.contains("setTimeout"))
		assertFalse(prepareBatchItem.contains("setTimeout"))
		assertFalse(advanceBatch.contains("setTimeout"))
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
		assertTrue(blockingEnd >= 0)
		assertTrue(currentChapter >= 0)
		assertTrue(forward >= 0)
		assertTrue(backward >= 0)
		assertTrue(currentChapter > blockingEnd)
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
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val captureGeometry = preview
			.substringAfter("function pageTurnCaptureGeometry() {")
			.substringBefore("\n}\n")

		assertContains(runtime, "pageTurnCaptureGeometry: () => runtime.pageTurnCaptureGeometry()")
		assertFalse(runtime.contains("\n  pageTurnCaptureGeometry() {"))
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
		assertContains(
			source,
			"removeCachedSnapshot(eviction.key, eviction.value)?.releaseCacheOwnership()"
		)
		assertContains(source, "snapshotCache.clear()")
		assertContains(source, "snapshotDurability.clear()")
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
		val preparedCapture = capture.indexOf("capturePreparedSurface(")
		val restoreLive = capture.indexOf("restoreLiveComposition(", startIndex = preparedCapture)
		assertTrue(preparedCapture >= 0)
		assertTrue(restoreLive >= 0)
		assertTrue(
			preparedCapture < restoreLive,
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
		val physicalLayoutFence = capture.indexOf(
			"readerPageRasterPhysicalLayoutMatches(captured, reference)"
		)
		val snapshotPublication = capture.indexOf("putSnapshot(")
		assertTrue(physicalLayoutFence >= 0)
		assertTrue(snapshotPublication >= 0)
		assertTrue(physicalLayoutFence < snapshotPublication)
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
		assertContains(persistence, "fun publicationIsCurrent(): Boolean =")
		assertContains(persistence, "runCatching(isStillCurrent).getOrDefault(false)")
		assertContains(persistence, "val publicationCurrent = publicationIsCurrent()")
		assertContains(
			persistence,
			"val persistedForCurrentPublication = write.persisted && publicationCurrent"
		)
		assertContains(persistence, "if (!accepted || !persistedForCurrentPublication)")
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
		val draw = capture.indexOf("webView.draw(canvas)")
		val opaque = capture.indexOf("bitmap.setHasAlpha(false)")
		val premultiplied = capture.indexOf("bitmap.setPremultiplied(true)")
		val publish = capture.indexOf("onCaptured(bitmap)")
		assertTrue(draw >= 0)
		assertTrue(opaque >= 0)
		assertTrue(premultiplied >= 0)
		assertTrue(publish >= 0)
		assertTrue(draw < opaque)
		assertTrue(premultiplied < publish)
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
		assertContains(capture, "onPersisted = onCaptured")
		assertContains(
			insertion,
			"onPersisted: (ReaderPageRasterPublicationResult) -> Unit = {}"
		)
		val persist = insertion.indexOf("persistCachedSnapshot(")
		val trim = insertion.indexOf("trimSnapshotCacheToCapacity()")
		assertTrue(persist >= 0)
		assertTrue(trim >= 0)
		assertTrue(
			persist < trim,
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
	fun previewReceiptRequiresPostExposureNativeConfirmationAndClearsWithItsComposition() {
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val expose = preview
			.substringAfter("function exposePageTurnPreviewFinal(")
			.substringBefore("function confirmPageTurnPreviewPresentation(")
		val confirm = preview
			.substringAfter("function confirmPageTurnPreviewPresentation(")
			.substringBefore("function restorePageTurnLiveComposition(")
		val restore = preview
			.substringAfter("function restorePageTurnLiveComposition(")
			.substringBefore("function destroyPageTurnPreviewRenderer(")
		val destroy = preview
			.substringAfter("function destroyPageTurnPreviewRenderer(")
			.substringBefore("export const NavicReaderPageTurnPreviewMethods")

		assertContains(expose, "this.pageTurnPreviewExposedToken = state.token")
		assertFalse(expose.contains("this.issuePageTurnPreviewPresentationReceipt("))
		assertContains(confirm, "scope: ReaderPageTurnPresentationScopePreview")
		assertContains(confirm, "token: state.token")
		assertContains(confirm, "pageIndex: state.pageIndex")
		assertContains(confirm, "previewGeneration: state.generation")
		assertContains(confirm, "this.pageTurnPreviewExposedToken !== state.token")
		assertContains(confirm, "this.issuePageTurnPreviewPresentationReceipt(")
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
		val invalidation = turns
			.substringAfter("function handleLiveTextPageCommitInvalidation()")
			.substringBefore("function nextPage()")

		assertContains(turns, "from './navic-reader-paginator-commit.js'")
		assertContains(complete, "readerTextPageCommitOwnerHasExpectedVisibleContent(pending)")
		assertContains(complete, "readerCopyTextPageCommit(pending, settlement)")
		assertContains(complete, "readerCopyTextPageCommit(settlement, presentationTarget)")
		assertContains(complete, "scope: ReaderPageTurnPresentationScopeLive")
		assertContains(complete, "token: pending.token")
		assertContains(complete, "pageIndex: settledPageIndex")
		assertContains(complete, "foliateSessionId: pending.foliateSessionId")
		assertContains(complete, "rasterGeneration: pending.rasterGeneration")
		assertContains(complete, "textureGeneration: pending.textureGeneration")
		assertContains(complete, "relocationEpoch: this.relocateSequence")
		val settlementPublication = complete.indexOf(
			"this.nativePageTurnSettledState = settlement"
		)
		val liveReceiptIssuance = complete.indexOf(
			"this.issuePageTurnLivePresentationReceipt("
		)
		assertTrue(settlementPublication >= 0)
		assertTrue(liveReceiptIssuance >= 0)
		assertTrue(
			settlementPublication < liveReceiptIssuance,
			"The live receipt must follow exact settlement."
		)
		assertContains(turns, "function pageTurnLivePresentationReceipt()")
		assertContains(getter, "readerTextPageCommitOwnerHasExpectedVisibleContent(target)")
		assertContains(getter, "this.pageTurnLivePresentationTargetValue")
		assertContains(getter, "this.completedExactPageTurnSettlements.get(target.token)")
		assertContains(getter, "target.relocationEpoch !== this.relocateSequence")
		assertContains(getter, "readerPageTurnPresentationReceiptMatches(receipt, receiptTarget)")
		assertFalse(
			getter.contains("peekNativePageTurnSettlement"),
			"Receipt lookup must survive settlement acknowledgement consumption."
		)
		assertContains(cancellation, "this.clearPageTurnLivePresentationTarget()")
		assertContains(runtime, "this.attachLiveTextPageCommitInvalidationListener()")
		assertContains(runtime, "this.detachLiveTextPageCommitInvalidationListener()")
		assertContains(runtime, "runtime.clearPageTurnLivePresentationTarget()")
		assertContains(invalidation, "readerTextPageCommitOwnerHasExpectedVisibleContent(settled)")
		assertContains(invalidation, "readerTextPageCommitOwnerHasExpectedVisibleContent(liveTarget)")
		assertContains(invalidation, "readerTextPageCommitOwnerWasRemembered(pending)")
		assertContains(invalidation, "ReaderLivePageTurnMaximumCommitTransactionAttempts")
		val cancelDelayedRelocation = invalidation.indexOf(
			"this.cancelPendingCommittedRelocation()"
		)
		val beginRetryRelocation = invalidation.indexOf(
			"this.beginControlledRelocation('page-turn:exact')"
		)
		val retryCommit = invalidation.indexOf(
			"this.commitPendingExactPageTurnSettlement(pending.token)"
		)
		assertTrue(cancelDelayedRelocation >= 0)
		assertTrue(beginRetryRelocation >= 0)
		assertTrue(retryCommit >= 0)
		assertTrue(cancelDelayedRelocation < beginRetryRelocation)
		assertTrue(beginRetryRelocation < retryCommit)
		assertFalse(invalidation.contains("rememberCompletedExactPageTurnSettlement"))
		assertContains(restore, "const retainedLivePositionIsCurrent =")
		assertContains(restore, "this.pageTurnLivePresentationTargetMatchesCurrent(")
		assertContains(restore, "? this.currentPagePosition")
		assertContains(restore, "this.restorePageTurnLivePresentationReceipt()")
	}
}
