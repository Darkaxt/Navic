package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeNavigationFlowTest {
	@Test
	fun androidReaderContentLayoutLogsComputedParagraphAndTextureState() {
		val bridgeText = readerBridgeText()
		val contentLayoutLogger = bridgeText
			.substringAfter("logContentLayout(label) {")
			.substringBefore("\n  contentEntries")

		assertContains(contentLayoutLogger, "paragraphBlockCount")
		assertContains(contentLayoutLogger, "firstParagraphMarginEnd")
		assertContains(contentLayoutLogger, "surfaceTextureLayer")
		assertContains(contentLayoutLogger, "surfaceTextureAsset")
	}

	@Test
	fun commonReaderParagraphSpacingControlsUseReadableDefaultFallback() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(settingsDialogText, "settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertContains(ebooksSettingsText, "settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertContains(searchSettingsText, "readerSettings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertFalse(settingsDialogText.contains("settings.paragraphSpacingPercent ?: 0"))
		assertFalse(ebooksSettingsText.contains("settings.paragraphSpacingPercent ?: 0"))
		assertFalse(searchSettingsText.contains("readerSettings.paragraphSpacingPercent ?: 0"))
	}

	@Test
	fun androidReaderPublicationRuntimeLogsCacheHitMissState() {
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()
		val readaloudHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(runtimeHostText, "val resolved = BinderyReaderPublicationResolver")
		assertContains(runtimeHostText, "cache=${'$'}{if (resolved.fromCache) \"hit\" else \"miss\"}")
		assertContains(runtimeHostText, "cacheKey=${'$'}{resolved.cacheKey}")
		assertContains(runtimeHostText, "fileBytes=${'$'}{resolved.publicationFile.length()}")
		assertContains(readaloudHostText, "cache=${'$'}{if (loadedRuntime.fromCache) \"hit\" else \"miss\"}")
		assertContains(readaloudHostText, "cacheKey=${'$'}{loadedRuntime.cacheKey}")
	}

	@Test
	fun androidReaderResolvesTocHrefNavigationBeforeCommittingLocation() {
		val bridgeText = readerBridgeText()
		val goTo = bridgeText
			.substringAfter("async goTo(locator) {")
			.substringBefore("\n  progressTargetForSections")

		assertContains(goTo, "resolveReaderNavigationTarget(locator)")
		assertContains(goTo, "this.view.renderer?.goTo")
		assertContains(goTo, "go-to:resolved")
		assertContains(goTo, "go-to:fallback")
		assertContains(bridgeText, "async resolveReaderNavigationTarget(locator) {")
		assertContains(bridgeText, "this.view.resolveNavigation?.(target)")
		assertContains(bridgeText, "this.view.book?.resolveHref?.(target)")
		assertContains(goTo, "this.scheduleControlledRelocationFallback('go-to')")
		assertTrue(
			goTo.indexOf("this.view.renderer?.goTo") <
				goTo.indexOf("this.scheduleControlledRelocationFallback('go-to')"),
			"TOC href navigation must move Foliate to the resolved section before Navic commits a location update."
		)
		assertFalse(
			goTo.contains("await this.view.goTo(locator)\n      this.scheduleCommittedRelocation"),
			"TOC href navigation must not blindly commit stale relocation state after a raw view.goTo(locator)."
		)
	}

	@Test
	fun androidReaderUsesSectionIndexFallbackForProgressOnlyFixedLayoutResume() {
		val bridgeText = readerBridgeText()
		val goToProgress = bridgeText
			.substringAfter("async function goToProgress(progress) {")
			.substringBefore("\nfunction nextPage()")

		assertContains(bridgeText, "progressTargetForSections")
		assertContains(goToProgress, "const progressTarget = this.progressTargetForSections(fraction)")
		assertContains(goToProgress, "if (progressTarget != null)")
		assertContains(goToProgress, "await this.view.goTo(progressTarget)")
		assertContains(goToProgress, "await this.view.goToFraction(fraction)")
		assertContains(bridgeText, "this.view?.book?.sections?.length")
	}

	@Test
	fun androidReaderUsesSectionIndexTargetForFixedLayoutPageTurns() {
		val bridgeText = readerBridgeText()
		val turnPage = bridgeText
			.substringAfter("turnPage(direction) {")
			.substringBefore("\nfunction attachScrolledEdgeTurnGestures")

		assertContains(bridgeText, "fixedLayoutAdjacentPageTarget(direction)")
		assertContains(bridgeText, "fixedLayoutCurrentPageIndex()")
		assertContains(turnPage, "const directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)")
		assertContains(turnPage, "if (directFixedLayoutPageTarget != null)")
		assertContains(turnPage, "await this.view.goTo(directFixedLayoutPageTarget)")
		assertContains(turnPage, "page-turn:fixed-direct")
		assertFalse(
			turnPage.contains("page-turn:fixed-fallback"),
			"Fixed-layout/PDF taps should navigate by direct adjacent section target, not by slow fallback after next/prev."
		)
	}

	@Test
	fun androidReaderCoalescesDuplicateFixedLayoutPageTurnTapsBeforeQueueing() {
		val bridgeText = readerBridgeText()
		val turnPage = bridgeText
			.substringAfter("turnPage(direction) {")
			.substringBefore("\nasync function performPageTurn(direction) {")
		val startPageTurn = bridgeText
			.substringAfter("startPageTurn(direction) {")
			.substringBefore("\n  startNextQueuedPageTurn()")
		val performPageTurn = bridgeText
			.substringAfter("async function performPageTurn(direction) {")
			.substringBefore("\nfunction attachScrolledEdgeTurnGestures")

		assertContains(bridgeText, "this.pageTurnPromise = null")
		assertContains(bridgeText, "pageTurnQueue = []")
		assertContains(turnPage, "if (this.pageTurnPromise)")
		assertContains(turnPage, "this.view?.isFixedLayout === true && this.pageTurnDirection === direction")
		assertContains(turnPage, "page-turn:coalesced")
		assertContains(turnPage, "return this.pageTurnPromise")
		assertContains(turnPage, "page-turn:queued")
		assertContains(turnPage, "this.pageTurnQueue.push({ direction, resolve, reject })")
		assertTrue(
			turnPage.indexOf("page-turn:coalesced") <
				turnPage.indexOf("page-turn:queued"),
			"Duplicate same-direction PDF/fixed-layout taps must be coalesced before the generic queue path can advance multiple pages."
		)
		assertContains(startPageTurn, "const turnPromise = Promise.resolve().then(() => this.performPageTurn(direction))")
		assertContains(startPageTurn, "this.pageTurnPromise = completionPromise")
		assertContains(startPageTurn, "if (this.pageTurnPromise === completionPromise)")
		assertContains(startPageTurn, "this.pageTurnPromise = null")
		assertContains(bridgeText, "startNextQueuedPageTurn()")
		assertContains(performPageTurn, "const directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)")
		assertContains(performPageTurn, "await this.view.goTo(directFixedLayoutPageTarget)")
		assertTrue(
			startPageTurn.indexOf("this.pageTurnPromise = completionPromise") <
				startPageTurn.indexOf("return completionPromise"),
			"Rapid fixed-layout taps must mark the active turn before returning to the bridge."
		)
	}

	@Test
	fun androidReaderDoesNotLetReflowableFoliatePageTurnPromisesOwnTheNativeInputLock() {
		val bridgeText = readerBridgeText()
		val performPageTurn = bridgeText
			.substringAfter("async function performPageTurn(direction) {")
			.substringBefore("\nfunction attachScrolledEdgeTurnGestures")
		val issueReflowablePageTurn = bridgeText
			.substringAfter("function issueReflowablePageTurn(direction) {")
			.substringBefore("\nasync function performPageTurn(direction) {")

		assertContains(
			bridgeText,
			"function issueReflowablePageTurn(direction) {",
			message = "Reflowable EPUB next/previous commands must be issued through a non-blocking bridge helper."
		)
		assertContains(issueReflowablePageTurn, "const navigationPromise = direction === 'next'")
		assertContains(issueReflowablePageTurn, "navigationPromise?.catch")
		assertContains(
			performPageTurn,
			"this.issueReflowablePageTurn(direction)",
			message = "Native tap-zone input must not be locked until Foliate's reflowable animation promise resolves."
		)
		assertFalse(
			performPageTurn.contains("await this.view?.next?.()") ||
				performPageTurn.contains("await this.view?.prev?.()"),
			"Awaiting Foliate reflowable next/prev can wedge the native controller after a section-boundary load."
		)
	}

	@Test
	fun androidReaderDefersReflowablePageTurnsUntilRendererContentIsReadyAfterResize() {
		val bridgeText = readerBridgeText()
		val issueReflowablePageTurn = bridgeText
			.substringAfter("function issueReflowablePageTurn(direction) {")
			.substringBefore("\nasync function performPageTurn(direction) {")

		assertContains(bridgeText, "readerReflowablePageTurnReady()")
		assertContains(issueReflowablePageTurn, "const readiness = this.readerReflowablePageTurnReadiness()")
		assertContains(issueReflowablePageTurn, "if (!readiness.ready)")
		assertContains(issueReflowablePageTurn, "page-turn:deferred-renderer-not-ready")
		assertContains(issueReflowablePageTurn, "this.applyReaderViewportLayout(`page-turn:${'$'}{direction}:deferred`)")
		assertContains(issueReflowablePageTurn, "this.retryDeferredReflowablePageTurn(direction)")
		assertContains(
			bridgeText.substringAfter("close() {").substringBefore("\n  async loadShellCover"),
			"this.clearDeferredReflowablePageTurn()",
			message = "Deferred page-turn observers must not survive reader close/reload after a window resize."
		)
		val performPageTurn = bridgeText
			.substringAfter("async function performPageTurn(direction) {")
			.substringBefore("\nfunction attachScrolledEdgeTurnGestures")
		assertContains(
			performPageTurn,
			"if (!pageTurnIssued) return",
			message = "A deferred reflowable turn must not mark recentPageTurnDirection before Foliate actually navigates."
		)
		assertTrue(
			issueReflowablePageTurn.indexOf("if (!readiness.ready)") <
				issueReflowablePageTurn.indexOf("const navigationPromise = direction === 'next'"),
			"Reflowable tap turns must not call Foliate next/prev while a tablet resize has left the renderer without a valid content document."
		)
	}

	@Test
	fun androidReaderKeepsAnxPaginatorNullDocumentGuardForSectionSwapPageTurns() {
		val navicPaginator = readerAssetRoot()
			.resolve("vendor/foliate-js/paginator.js")
			.readText()
		val root = File(".")
		val anxPaginator = listOf(
			root.resolve("tmp/references/anx-reader/assets/foliate-js/src/paginator.js"),
			root.resolve("../tmp/references/anx-reader/assets/foliate-js/src/paginator.js")
		).firstOrNull { it.isFile }
			?: error("Could not locate Anx paginator reference")
		val anxPaginatorText = anxPaginator
			.readText()
		val navicVisibleRange = navicPaginator
			.substringAfter("const getVisibleRange = (doc, start, end, mapRect) => {")
			.substringBefore("\nconst getDirection")
		val anxVisibleRange = anxPaginatorText
			.substringAfter("const getVisibleRange = (doc, start, end, mapRect) => {")
			.substringBefore("\nconst getDirection")

		assertContains(anxVisibleRange, "if (!doc) return")
		assertContains(navicVisibleRange, "if (!doc) return")
		assertTrue(
			navicVisibleRange.indexOf("if (!doc) return") <
				navicVisibleRange.indexOf("doc.createTreeWalker"),
			"Reflowable page turns can briefly run while Foliate has no active content document; the Anx guard must run before createTreeWalker."
		)
	}

	@Test
	fun androidReaderDoesNotClampCommittedFixedLayoutPageTurnsAgainstStaleDisplayState() {
		val bridgeText = readerBridgeText()
		val committedPageTurnPosition = bridgeText
			.substringAfter("committedPageTurnPosition(pagePosition, reason) {")
			.substringBefore("\n  readerPageNumberFontFamily")

		assertContains(committedPageTurnPosition, "if (pagePosition.pageCountSource === 'fixed-layout') return pagePosition")
		assertTrue(
			committedPageTurnPosition.indexOf("pagePosition.pageCountSource === 'fixed-layout'") <
				committedPageTurnPosition.indexOf("const currentPageIndex = Number(this.currentPagePosition?.pageIndex)"),
			"PDF/fixed-layout page positions are exact and must not be rewritten by stale reflowable page-turn clamping."
		)
	}

	@Test
	fun androidReaderUsesDirectFixedLayoutTargetsForTapPageTurns() {
		val bridgeText = readerBridgeText()
		val performPageTurn = bridgeText
			.substringAfter("async function performPageTurn(direction) {")
			.substringBefore("\nfunction attachScrolledEdgeTurnGestures")

		assertContains(performPageTurn, "const directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)")
		assertContains(performPageTurn, "if (directFixedLayoutPageTarget != null)")
		assertContains(performPageTurn, "page-turn:fixed-direct")
		assertContains(performPageTurn, "await this.view.goTo(directFixedLayoutPageTarget)")
		assertTrue(
			performPageTurn.indexOf("await this.view.goTo(directFixedLayoutPageTarget)") <
				performPageTurn.indexOf("this.issueReflowablePageTurn(direction)"),
			"Fixed-layout/PDF tap navigation should go directly to the indexed adjacent page before the reflowable command path."
		)
		assertContains(performPageTurn, "this.issueReflowablePageTurn(direction)")
		assertFalse(
			performPageTurn.contains("beforePageIndex === afterPageIndex"),
			"Fixed-layout tap navigation should not need a try-next/then-fallback path."
		)
	}

	@Test
	fun androidReaderMapsExplicitReadingFlowModesToFoliateRuntime() {
		val bridgeText = readerBridgeText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerFlowMode(settings)")
		assertContains(bridgeText, "ReaderFlowPagedVertical")
		assertContains(bridgeText, "ReaderFlowScrolledGaps")
		assertContains(bridgeText, "setAttribute('flow', readerFoliateFlow(flowMode))")
		assertContains(bridgeText, "--reader-scroll-gap")
		assertContains(bridgeText, "writing-mode: vertical-rl")
		assertContains(ebooksSettingsText, "readerFlowMode")
		assertContains(ebooksSettingsText, "PagedVertical(ReaderFlowPagedVertical")
		assertContains(ebooksSettingsText, "ScrollGaps(ReaderFlowScrolledGaps")
		assertContains(searchSettingsText, "readerFlowMode")
		assertContains(searchSettingsText, "ReaderSupportedFlowModes")
	}

	@Test
	fun androidReaderUsesAdaptiveFoliatePageBoxForLargeEpubViewports() {
		val bridgeText = readerBridgeText()
		val viewportLayout = bridgeText
			.substringAfter("applyReaderViewportLayout(label = 'unknown') {")
			.substringBefore("\n  applyReaderViewportLayoutToProfilerView")
		val profileLayout = bridgeText
			.substringAfter("applyReaderViewportLayoutToProfilerView(profileView, settings = this.readerSettings) {")
			.substringBefore("\n  applyPdfImageSettings")
		val renderMetadata = bridgeText
			.substringAfter("readerPaginationRenderMetadata() {")
			.substringBefore("\n  readerPaginationRenderFingerprint")

		assertContains(bridgeText, "readerAdaptiveFoliatePageBox")
		assertContains(viewportLayout, "const pageBox = readerAdaptiveFoliatePageBox")
		assertContains(viewportLayout, "renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)")
		assertContains(viewportLayout, "renderer.setAttribute('max-block-size', pageBox.maxBlockSize)")
		assertContains(viewportLayout, "renderer.setAttribute('max-column-count', pageBox.maxColumnCount)")
		assertContains(
			viewportLayout,
			"renderer.dataset.navicAdaptivePageBox",
			message = "ADB/WebView diagnostics need the calculated page box because excessive blank paper is viewport-dependent."
		)
		assertContains(profileLayout, "const pageBox = readerAdaptiveFoliatePageBox")
		assertContains(profileLayout, "renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)")
		assertContains(profileLayout, "renderer.setAttribute('max-block-size', pageBox.maxBlockSize)")
		assertContains(profileLayout, "renderer.setAttribute('max-column-count', pageBox.maxColumnCount)")
		assertContains(
			renderMetadata,
			"adaptivePageBox",
			message = "Pagination fingerprints must include adaptive page-box math so cached page counts are per viewport/layout policy."
		)
		assertContains(renderMetadata, "readerAdaptiveFoliatePageBox")
	}

	@Test
	fun androidReaderMapsExplicitReadingDirectionToFoliateRuntime() {
		val bridgeText = readerBridgeText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerDirectionMode(settings)")
		assertContains(bridgeText, "applyReaderDirection")
		assertContains(bridgeText, "this.view.book.dir")
		assertContains(bridgeText, "doc.documentElement")
		assertContains(settingsDialogText, "Direction")
		assertContains(settingsDialogText, "ReaderSupportedDirections")
		assertContains(settingsDialogText, "settings.copy(direction = direction)")
		assertContains(ebooksSettingsText, "readerDirection")
		assertContains(ebooksSettingsText, "ReaderDirectionOption")
		assertContains(searchSettingsText, "ebooks.direction")
		assertContains(searchSettingsText, "ReaderSupportedDirections")
	}

}
