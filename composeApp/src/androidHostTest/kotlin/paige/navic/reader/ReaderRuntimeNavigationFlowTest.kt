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
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerOptionsPanelText, "state.settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertContains(ebooksSettingsText, "settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertContains(searchSettingsText, "readerSettings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertFalse(readerOptionsPanelText.contains("state.settings.paragraphSpacingPercent ?: 0"))
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
	fun androidReaderUsesSectionIndexFallbackForProgressOnlyFixedLayoutResume() {
		val bridgeText = readerBridgeText()
		val goToProgress = bridgeText
			.substringAfter("async goToProgress(progress) {")
			.substringBefore("\n  async nextPage()")

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
			.substringBefore("\n  attachScrolledEdgeTurnGestures")

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
	fun androidReaderQueuesRapidFixedLayoutPageTurnTaps() {
		val bridgeText = readerBridgeText()
		val turnPage = bridgeText
			.substringAfter("turnPage(direction) {")
			.substringBefore("\n  async performPageTurn(direction) {")
		val startPageTurn = bridgeText
			.substringAfter("startPageTurn(direction) {")
			.substringBefore("\n  startNextQueuedPageTurn()")
		val performPageTurn = bridgeText
			.substringAfter("async performPageTurn(direction) {")
			.substringBefore("\n  attachScrolledEdgeTurnGestures")

		assertContains(bridgeText, "this.pageTurnPromise = null")
		assertContains(bridgeText, "pageTurnQueue = []")
		assertContains(turnPage, "if (this.pageTurnPromise)")
		assertContains(turnPage, "page-turn:queued")
		assertContains(turnPage, "this.pageTurnQueue.push({ direction, resolve, reject })")
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
			.substringAfter("async performPageTurn(direction) {")
			.substringBefore("\n  attachScrolledEdgeTurnGestures")

		assertContains(performPageTurn, "const directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)")
		assertContains(performPageTurn, "if (directFixedLayoutPageTarget != null)")
		assertContains(performPageTurn, "page-turn:fixed-direct")
		assertContains(performPageTurn, "await this.view.goTo(directFixedLayoutPageTarget)")
		assertTrue(
			performPageTurn.indexOf("await this.view.goTo(directFixedLayoutPageTarget)") <
				performPageTurn.indexOf("await this.view?.next?.()"),
			"Fixed-layout/PDF tap navigation should go directly to the indexed adjacent page instead of waiting for next/prev to fail."
		)
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
	fun androidReaderMapsExplicitReadingDirectionToFoliateRuntime() {
		val bridgeText = readerBridgeText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerDirectionMode(settings)")
		assertContains(bridgeText, "applyReaderDirection")
		assertContains(bridgeText, "this.view.book.dir")
		assertContains(bridgeText, "doc.documentElement")
		assertContains(readerOptionsPanelText, "Direction")
		assertContains(readerOptionsPanelText, "ReaderSupportedDirections")
		assertContains(readerOptionsPanelText, "state.settings.copy(direction = direction)")
		assertContains(ebooksSettingsText, "readerDirection")
		assertContains(ebooksSettingsText, "ReaderDirectionOption")
		assertContains(searchSettingsText, "ebooks.direction")
		assertContains(searchSettingsText, "ReaderSupportedDirections")
	}

}
