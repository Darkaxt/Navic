package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeShellProgressTest {
	@Test
	fun androidReaderShellOwnsAnxStyleViewportSurface() {
		val root = readerAssetRoot()
		val indexText = root.resolve("index.html").readText()
		val bridgeText = readerBridgeText(root)

		assertContains(
			indexText,
			"height: 100vh",
			message = "Reader shell must use an Anx-style viewport height instead of inheriting a collapsed percentage height"
		)
		assertContains(indexText, "position: fixed;")
		assertContains(indexText, "inset: 0;")
		assertContains(indexText, "body > foliate-view")
		assertContains(bridgeText, "readerRoot = document.body")
		assertContains(bridgeText, "applyReaderViewportLayout")
		assertContains(bridgeText, "window.visualViewport")
		assertContains(bridgeText, "renderer?.render?.()")
	}

	@Test
	fun androidReaderBridgeExposesAnxStylePageTurnCommands() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "case 'nextPage'")
		assertContains(bridgeText, "case 'previousPage'")
		assertContains(bridgeText, "async nextPage()")
		assertContains(bridgeText, "async previousPage()")
		assertContains(bridgeText, "this.view?.next?.()")
		assertContains(bridgeText, "this.view?.prev?.()")
		assertContains(bridgeText, "page-turn:start")
		assertContains(bridgeText, "page-turn:done")
	}

	@Test
	fun androidReaderBridgeExposesProgressSeekCommand() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()

		assertContains(bridgeText, "case 'goToProgress'")
		assertContains(bridgeText, "async goToProgress(progress)")
		assertContains(bridgeText, "this.view?.goToFraction")
		assertContains(bridgeText, "progress-seek")
		assertContains(readerScreenText, "ReaderBridgeCommand.GoToProgress")
		assertContains(readerScreenText, "Slider(")
		assertContains(readerScreenText, "onProgressSeek: (Float) -> Unit")
	}

	@Test
	fun androidReaderBridgePortsAnxStyleScrolledEdgePageTurns() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "ScrollEdgeTurnSwipeThreshold")
		assertContains(bridgeText, "attachScrolledEdgeTurnGestures")
		assertContains(bridgeText, "doc.addEventListener('touchstart'")
		assertContains(bridgeText, "doc.addEventListener('touchmove'")
		assertContains(bridgeText, "doc.addEventListener('touchend'")
		assertContains(bridgeText, "renderer.scrolled")
		assertContains(bridgeText, "renderer.viewSize - renderer.end")
		assertContains(bridgeText, "renderer.start <= ScrollEdgeTurnSlop")
		assertContains(bridgeText, "page-turn:edge-swipe")
	}

	@Test
	fun commonReaderChromeExposesPageTurnControls() {
		val readerScreenText = readerScreenFile().readText()

		assertContains(readerScreenText, "onPreviousPage: () -> Unit")
		assertContains(readerScreenText, "onNextPage: () -> Unit")
		assertContains(readerScreenText, "ReaderBridgeCommand.PreviousPage")
		assertContains(readerScreenText, "ReaderBridgeCommand.NextPage")
		assertContains(readerScreenText, "Icons.Filled.SkipPrevious")
		assertContains(readerScreenText, "Icons.Filled.SkipNext")
	}

	@Test
	fun readerChromeIsImmersiveAndDrivenByCenterTapBridgeEvents() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()

		assertContains(bridgeText, "readerCenterTap")
		assertContains(bridgeText, "attachCenterTapGesture")
		assertContains(bridgeText, "doc.addEventListener('click'")
		assertContains(bridgeText, "doc.addEventListener('touchstart'")
		assertContains(bridgeText, "doc.addEventListener('touchend'")
		assertContains(bridgeText, "handleReaderTapZone")
		assertContains(bridgeText, "__navicLastTapHandledAt")
		assertContains(bridgeText, "CenterTapMovementSlop")
		assertContains(bridgeText, "readerTapZone")
		assertContains(bridgeText, "await this.previousPage()")
		assertContains(bridgeText, "await this.nextPage()")
		assertContains(readerScreenText, "ReaderBridgeEvent.CenterTap")
		assertContains(readerScreenText, "chromeVisible")
		assertContains(readerScreenText, "if (chromeVisible)")
		assertFalse(
			readerScreenText.contains("RootTopBar("),
			"ReaderScreen must not show the global search/settings/account top bar in the reading area."
		)
	}

	@Test
	fun androidReaderSupportsPdfSurfaceNavigationAndScrolling() {
		val bridgeText = readerBridgeText()
		val surfaceGesture = bridgeText
			.substringAfter("attachSurfaceTapGesture(element) {")
			.substringBefore("\n  attachLinkNavigation")

		assertContains(bridgeText, "attachSurfaceTapGesture")
		assertContains(bridgeText, "this.attachSurfaceTapGesture(this.view)")
		assertContains(bridgeText, "this.view?.isFixedLayout === true")
		assertContains(bridgeText, "overflow: fixedLayout ? 'auto' : 'hidden'")
		assertContains(bridgeText, "handleReaderTapZone(event, document, 'surface')")
		assertContains(bridgeText, "FixedLayoutSurfaceSwipeThreshold")
		assertContains(bridgeText, "turnFixedLayoutSwipePage(deltaX)")
		assertContains(surfaceGesture, "element.addEventListener('touchstart'")
		assertContains(surfaceGesture, "element.addEventListener('touchmove'")
		assertContains(surfaceGesture, "element.addEventListener('touchend'")
		assertContains(surfaceGesture, "element.addEventListener('touchcancel'")
		assertContains(surfaceGesture, "Math.abs(deltaX) >= FixedLayoutSurfaceSwipeThreshold")
		assertContains(surfaceGesture, "await this.turnFixedLayoutSwipePage(deltaX)")
		assertContains(surfaceGesture, "markReaderSurfaceTapHandled(element, event)")
		assertContains(bridgeText, "startLocator?.progress")
		assertContains(bridgeText, "await this.goToProgress(progress)")
	}

	@Test
	fun androidReaderMarksTapHandledBeforeAwaitingPageTurnNavigation() {
		val bridgeText = readerBridgeText()
		val handleTapZone = bridgeText
			.substringAfter("async handleReaderTapZone(event, doc, source = 'tap') {")
			.substringBefore("\n  attachCenterTapGesture")
		val centerTouchEnd = bridgeText
			.substringAfter("doc.addEventListener('touchend', async event => {")
			.substringBefore("\n    }, { passive: false })")
		val surfaceTouchEnd = bridgeText
			.substringAfter("element.addEventListener('touchend', async event => {")
			.substringBefore("\n    }, { passive: false })")
		val surfaceClick = bridgeText
			.substringAfter("element.addEventListener('click', async event => {")
			.substringBefore("\n    }, { passive: false })")

		assertContains(bridgeText, "markReaderDocumentTapHandled")
		assertContains(bridgeText, "markReaderSurfaceTapHandled")
		assertContains(bridgeText, "__navicSuppressNextTapClickUntil")
		assertContains(bridgeText, "__navicSuppressNextSurfaceClickUntil")
		assertContains(handleTapZone, "event.markHandled?.()")
		assertTrue(
			handleTapZone.indexOf("event.markHandled?.()") <
				handleTapZone.indexOf("await this.previousPage()"),
			"Touch handlers must mark a page tap as handled before awaiting navigation so Android WebView synthetic clicks cannot turn a second page."
		)
		assertTrue(
			handleTapZone.indexOf("event.markHandled?.()") <
				handleTapZone.indexOf("await this.nextPage()"),
			"Touch handlers must mark a page tap as handled before awaiting navigation so Android WebView synthetic clicks cannot turn a second page."
		)
		assertContains(centerTouchEnd, "markHandled: () => markReaderDocumentTapHandled(win, event)")
		assertContains(surfaceTouchEnd, "markHandled: () => markReaderSurfaceTapHandled(element, event)")
		assertContains(surfaceClick, "shouldSuppressReaderSurfaceClick(element, event)")
		assertContains(surfaceClick, "event.preventDefault()")
	}

	@Test
	fun androidReaderPreservesProgressOnlyResumeLocatorsForFixedLayoutPublications() {
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(readerScreenText, "startProgress = resumeStartLocator?.progress")
		assertContains(webViewHostText, "startProgress: Double?")
		assertContains(webViewHostText, "startProgress?.toString().orEmpty()")
		assertContains(webViewHostText, "progress = startProgress")
		assertContains(webViewHostText, "it.cfi != null || it.href != null || it.progress != null")
	}

	@Test
	fun androidReaderPublishesPostReadyLocationSnapshotAfterResumeSeek() {
		val bridgeText = readerBridgeText()
		val openPublication = bridgeText
			.substringAfter("async openPublication({ url, mediaOverlayEnabled = false, startLocator = null, settings = null }) {")
			.substringBefore("\n  close()")
		val onRelocate = bridgeText
			.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  attachContentDocumentBehaviors")

		assertContains(bridgeText, "lastRelocateDetail = null")
		assertContains(bridgeText, "postLocationChanged(detail")
		assertContains(bridgeText, "postCurrentLocationSnapshot('initial-resume')")
		assertContains(onRelocate, "this.lastRelocateDetail = detail")
		assertContains(onRelocate, "this.postLocationChanged(detail")
		assertTrue(
			openPublication.indexOf("post({ type: 'publicationReady' })") <
				openPublication.indexOf("this.postCurrentLocationSnapshot('initial-resume')"),
			"PublicationReady must be sent before the synthetic location snapshot so native progress saving is armed."
		)
	}

	@Test
	fun androidReaderReportsFixedLayoutPagePositionToChrome() {
		val bridgeText = readerBridgeText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(bridgeText, "fixedLayoutPagePosition(detail)")
		assertContains(bridgeText, "this.view?.isFixedLayout === true")
		assertContains(bridgeText, "this.view?.book?.sections?.length")
		assertContains(bridgeText, "pageIndex: pagePosition?.pageIndex")
		assertContains(bridgeText, "pageCount: pagePosition?.pageCount")
		assertContains(bridgeProtocolText, "val pageIndex: Int? = null")
		assertContains(bridgeProtocolText, "val pageCount: Int? = null")
		assertContains(chromeStateText, "readerPageProgressLabel")
		assertContains(chromeStateText, "\"Page ${'$'}{pageIndex + 1} of ${'$'}pageCount\"")
	}

	@Test
	fun androidReaderReportsDynamicReflowablePagePositionToChrome() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()

		assertContains(bridgeText, "reflowablePagePosition(detail)")
		assertContains(bridgeText, "reflowableLocationPagePosition(detail)")
		assertContains(bridgeText, "reflowableSectionPagePosition()")
		assertContains(bridgeText, "reflowableWholeBookPagePosition(detail)")
		assertContains(bridgeText, "const renderer = this.view?.renderer")
		assertContains(bridgeText, "if (!renderer || renderer.scrolled) return null")
		assertContains(bridgeText, "let page")
		assertContains(bridgeText, "let pages")
		assertContains(bridgeText, "page = Number(renderer.page)")
		assertContains(bridgeText, "pages = Number(renderer.pages)")
		assertContains(bridgeText, "reflowable-section-pages:pending")
		assertContains(bridgeText, "const pageCount = Math.max(1, Math.round(pages) - 2)")
		assertContains(bridgeText, "pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(page - 2)))")
		assertContains(bridgeText, "const sectionSizes = this.reflowableSectionSizes()")
		assertContains(bridgeText, "reflowableBookPageModel")
		assertContains(bridgeText, "reflowableStableBookPageModel(normalizedSectionIndex, sectionPosition, sectionSizes, canonicalPageCount)")
		assertContains(bridgeText, "Math.ceil(model.totalReadableSize / model.readableUnitsPerPage)")
		assertContains(bridgeText, "this.reflowableBookPageModel = null")
		assertContains(bridgeText, "reflowable-page-model:set")
		assertFalse(
			bridgeText.contains("currentSectionSize / sectionPosition.pageCount"),
			"Reflowable EPUB totals must not be estimated from the current section only, because that makes # / # change between chapters."
		)
		assertContains(bridgeText, "readerPageListPageCount()")
		assertContains(bridgeText, "const canonicalPageCount = this.readerPageListPageCount()")
		assertContains(bridgeText, "pageCountSource: 'page-list'")
		assertContains(bridgeText, "pageCountSource: 'location'")
		assertContains(bridgeText, "pageCountSource: model.source")
		assertContains(bridgeText, "const prefersFallbackPageCount = pagePosition.pageCountSource === 'section'")
		assertContains(bridgeText, "? 'page-list'")
		assertContains(bridgeText, ": 'synthetic-location'")
		assertFalse(
			bridgeText.contains("this.reflowableBookPageModel.source !== 'rendered-section' && canUseRenderedSection"),
			"Reflowable EPUB totals must not be upgraded from the currently rendered section, because normal relocation events may lack pageItem and then the denominator changes while reading."
		)
		assertFalse(
			bridgeText.contains("Math.floor(page - 1)"),
			"The first visible reflowable EPUB page must map to page 1, not page 2."
		)
		assertContains(bridgeText, "readerPagePosition(detail)")
		assertContains(bridgeText, "this.reflowableLocationPagePosition(detail) || this.reflowableWholeBookPagePosition(detail)")
		assertContains(bridgeText, "ensureReaderPageNumberLayer")
		assertContains(bridgeText, "dataset.navicPageNumberLayer")
		assertContains(bridgeText, "return `${'$'}{currentPage} / ${'$'}{pageCount}`")
		assertContains(bridgeText, "readerPageNumberPositionWithPageCount(pagePosition, this.currentPagePosition?.pageCount)")
		assertContains(bridgeText, "readerPageNumberLabel(pageNumberPosition)")
		assertFalse(
			bridgeText.contains("return String(currentPage)"),
			"Page number labels must keep the # / # design instead of falling back to a bare current page."
		)
		assertContains(bridgeText, "this.updateReaderPageNumberLayer(pagePosition)")
		assertContains(bridgeText, "font-family': 'var(--reader-page-number-font-family")
		assertFalse(
			readerScreenText.contains("ReaderPageNumberOverlay("),
			"Page numbers must be drawn in the reader surface, not as a native Material overlay."
		)
	}

	@Test
	fun androidReaderUsesStableLocationTotalsForReflowablePageNumbers() {
		val bridgeText = readerBridgeText()
		val closeBody = bridgeText
			.substringAfter("close() {")
			.substringBefore("applyReaderViewportLayout(label = 'unknown') {")

		assertContains(bridgeText, "reflowableLocationPagePosition(detail)")
		assertContains(bridgeText, "const location = detail?.location")
		assertContains(bridgeText, "Math.floor(clampedProgress * pageCount)")
		assertContains(bridgeText, "const advancedProgressWithinSection =")
		assertContains(bridgeText, "const progressedToNewBucketWithinSection =")
		assertContains(bridgeText, "this.reflowableLastLocationProgress")
		assertContains(bridgeText, "const advancedToLaterSection =")
		assertContains(bridgeText, "this.reflowableLastLocationSignature")
		assertContains(bridgeText, "this.reflowableLastLocationProgressBucket")
		assertContains(bridgeText, "const canApplyStartOffset = pagePosition.pageCountSource !== 'location'")
		assertContains(bridgeText, "pageCountSource: 'location'")
		assertContains(
			bridgeText,
			"return this.reflowableLocationPagePosition(detail) || this.reflowableWholeBookPagePosition(detail) || this.readerPageListPosition(detail) || this.reflowableSectionPagePosition()",
			message = "Reflowable EPUB labels must prefer Foliate's stable book-level location total before page-list or section fallbacks."
		)
		assertFalse(
			bridgeText.contains("return this.readerPageListPosition(detail) || this.reflowableWholeBookPagePosition(detail)"),
			"Reflowable EPUB labels must not opportunistically switch to sparse page-list totals."
		)
		assertContains(
			bridgeText,
			"const prefersFallbackPageCount = pagePosition.pageCountSource === 'section'",
			message = "Only section fallback positions should inherit the previous book denominator."
		)
		assertFalse(
			bridgeText.contains("const prefersOwnPageCount ="),
			"Book-level reflowable positions must keep their own denominator instead of being overwritten by a stale fallback count."
		)
		assertContains(
			closeBody,
			"this.reflowablePageIndexOffset = null",
			message = "The first-page offset must be scoped to one publication, otherwise a later book can start at the wrong visible page number."
		)
		assertContains(closeBody, "this.reflowableLastLocationSignature = null")
		assertContains(closeBody, "this.reflowableLastLocationPageIndex = null")
		assertContains(closeBody, "this.reflowableLastLocationSectionIndex = null")
		assertContains(closeBody, "this.reflowableLastLocationProgressBucket = null")
	}

}
