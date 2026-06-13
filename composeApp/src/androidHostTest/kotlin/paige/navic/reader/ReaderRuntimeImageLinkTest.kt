package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeImageLinkTest {
	@Test
	fun androidReaderDoesNotForceCoverPagesToViewportInsidePaginatedIframe() {
		val bridgeText = readerBridgeText()

		assertFalse(
			bridgeText.contains("data-navic-cover-page"),
			"Cover-page viewport expansion inside Foliate's paginated iframe can trigger ResizeObserver loops and blank the first page."
		)
		assertFalse(
			bridgeText.contains("width: 100vw !important") && bridgeText.contains("height: 100vh !important"),
			"Reader content CSS must not force EPUB cover documents to viewport size inside the paginated iframe."
		)
	}

	@Test
	fun androidReaderUsesShellCoverInsteadOfNumberingTheEpubCoverPage() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "ReaderShellCoverLayerSelector")
		assertContains(bridgeText, "ensureReaderShellCoverLayer")
		assertContains(bridgeText, "loadShellCover")
		assertContains(bridgeText, "book.getCover?.()")
		assertContains(bridgeText, "firstReadableContentTarget")
		assertContains(bridgeText, "showShellCover")
		assertContains(bridgeText, "hideShellCover")
		assertContains(bridgeText, "this.shellCoverVisible")
		assertContains(
			bridgeText,
			"if (this.shellCoverVisible) {",
			message = "The organic page number layer must be suppressed while the shell cover is visible."
		)
		assertContains(
			bridgeText,
			"if (this.shellCoverVisible && direction === 'next')",
			message = "Next from the shell cover should reveal the first real content page instead of advancing twice."
		)
		assertContains(
			bridgeText,
			"if (direction === 'previous' && this.canReturnToShellCover())",
			message = "Previous from the first real content page should return to the shell cover surface."
		)
		assertFalse(
			bridgeText.contains("classifyReaderCoverDocument"),
			"Shell-cover support must not reintroduce EPUB iframe cover classification."
		)
	}

	@Test
	fun androidReaderShowsShellCoverBeforeSavedResumeLocation() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "startLocatorTargetsShellCover")
		assertContains(bridgeText, "const startLocatorIsShellCover = this.startLocatorTargetsShellCover(startLocator)")
		assertContains(
			bridgeText,
			"const shellCoverUrl = this.externalShellCover ? null : await this.loadShellCover()",
			message = "Every EPUB open should try to show the program-level cover before revealing reader content."
		)
		assertContains(
			bridgeText,
			"} else if (locator) {",
			message = "Non-cover resume locators must still load behind the shell cover."
		)
		assertContains(
			bridgeText,
			"await this.view.goTo(locator)",
			message = "The saved location should be preserved behind the initial cover overlay."
		)
		assertFalse(
			bridgeText.contains("const shouldStartAtShellCover ="),
			"Shell cover loading should not be gated by the absence of a saved locator."
		)
		assertContains(bridgeText, "detailTargetsCover")
		assertContains(bridgeText, "location-changed:cover-skipped")
	}

	@Test
	fun androidReaderSkipsWebShellCoverWhenNativeCoverSurfaceIsAvailable() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "externalShellCover = false")
		assertContains(bridgeText, "this.externalShellCover = Boolean(externalShellCover)")
		assertContains(
			bridgeText,
			"this.close()\n      this.externalShellCover = Boolean(externalShellCover)",
			message = "The external shell-cover flag must be applied after close() resets runtime state."
		)
		assertContains(
			bridgeText,
			"const shellCoverUrl = this.externalShellCover ? null : await this.loadShellCover()",
			message = "Native cover mode must not create a second WebView shell-cover layer underneath the Compose cover surface."
		)
		assertContains(bridgeText, "const hasShellCoverSurface = this.externalShellCover || Boolean(shellCoverUrl)")
		assertContains(bridgeText, "} else if (hasShellCoverSurface) {")
		assertContains(
			bridgeText,
			"if (shellCoverUrl) this.showShellCover()",
			message = "Only the JS fallback cover layer should be shown by the WebView runtime."
		)
	}

	@Test
	fun commonReaderUsesNativeShellCoverSurfaceWhenResolverProvidesCoverUrl() {
		val readerScreenText = readerScreenFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(
			runtimeHostText,
			"resolved.shellCoverUrl",
			message = "The Android resolver's EPUB cover URL must be surfaced to common reader UI."
		)
		assertContains(readerScreenText, "nativeShellCoverUrl")
		assertContains(readerScreenText, "externalShellCover = nativeShellCoverUrl != null")
		assertContains(readerScreenText, "nativeShellCoverUrl = nativeShellCoverUrl")
		assertContains(readerScreenText, "canReturnToShellCover = readerShouldReturnToNativeShellCover(")
		assertContains(readerScreenText, "readerShouldReturnToNativeShellCover(")
		assertContains(webViewHostText, "externalShellCover: Boolean")
		assertContains(webViewHostText, "nativeShellCoverUrl: String?")
		assertContains(webViewHostText, "canReturnToShellCover: Boolean")
		assertContains(webViewHostText, "externalShellCover = externalShellCover")
		assertContains(webViewHostText, "ReaderShellCoverView")
		assertContains(webViewHostText, "shellCoverView")
		assertContains(webViewHostText, "updateShellCover(nativeShellCoverUrl, title)")
	}

	@Test
	fun commonReaderDoesNotLetSecondaryPublicationPreparationClearNativeShellCover() {
		val readerScreenText = readerScreenFile().readText()
		val handlePublicationPrepared = readerScreenText
			.substringAfter("fun handlePublicationPrepared(publicationUrl: String, shellCoverUrl: String?) {")
			.substringBefore("\n\tfun hideReaderPanels")

		assertContains(
			handlePublicationPrepared,
			"if (shellCoverUrl != null || nativeShellCoverUrl == null)",
			message = "A secondary readaloud/runtime preparation with no cover URL must not clear an already resolved native shell cover."
		)
		assertFalse(
			readerScreenText.contains("handlePublicationPrepared(publicationUrl, null)"),
			"Passing a literal null cover from a secondary runtime can force the reader back to the JS/WebView shell-cover layer."
		)
	}

	@Test
	fun androidPublicationRuntimeLogsNativeShellCoverResolution() {
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()

		assertContains(
			runtimeHostText,
			"""shellCover=${'$'}{if (resolved.shellCoverUrl.isNullOrBlank()) "missing" else "present"}""",
			message = "ADB logs must expose whether EPUB native shell-cover extraction succeeded before the WebView opens."
		)
		assertContains(
			runtimeHostText,
			"shellCover=unavailable",
			message = "Direct/local publication opens must log that no resolver cover extraction ran."
		)
	}

	@Test
	fun androidReaderShellCoverUsesFullscreenBlackSurface() {
		val bridgeText = readerBridgeText()
		val shellCoverLayer = bridgeText
			.substringAfter("const updateReaderShellCoverLayer = (layer, coverUrl, settings, title = '') => {")
			.substringBefore("\nconst updateReaderSurfaceTextureLayer")

		assertContains(shellCoverLayer, "background: '#000000'")
		assertContains(shellCoverLayer, "'background-color': '#000000'")
		assertContains(shellCoverLayer, "padding: '0px'")
		assertContains(shellCoverLayer, "width: '100%'")
		assertContains(shellCoverLayer, "height: '100%'")
		assertContains(shellCoverLayer, "'object-fit': 'contain'")
		assertContains(shellCoverLayer, "'max-height': '100%'")
		assertContains(shellCoverLayer, "'box-shadow': 'none'")
	}

	@Test
	fun androidReaderShellCoverTapsAndPreviousDoNotFallThroughToEpubCover() {
		val bridgeText = readerBridgeText()
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()
		val canReturnToShellCover = bridgeText
			.substringAfter("canReturnToShellCover() {")
			.substringBefore("\n  applyReaderViewportLayout")

		assertContains(readerScreenText, "nativeShellCoverUrl = nativeShellCoverUrl")
		assertContains(webViewHostText, "ReaderSurfaceHost")
		assertContains(webViewHostText, "ReaderShellCoverView")
		assertContains(webViewHostText, "shellCoverView")
		assertFalse(
			webViewHostText.contains("shellCoverWebView"),
			"Cover image taps must be owned by the reader surface over a native cover view, not by a second WebView."
		)
		assertFalse(
			webViewHostText.contains("readerShellCoverHtml"),
			"Shell cover should not be an HTML/CSS wrapper now that cover is a reader-owned surface."
		)
		assertContains(webViewHostText, "ReaderBridgeCommand.NextPage -> hideShellCover()")
		assertContains(webViewHostText, "ReaderBridgeCommand.PreviousPage -> Unit")
		assertContains(webViewHostText, "dispatchReaderWideTap")
		assertContains(webViewHostText, "readerTapZonePageTurnCommand(")
		assertContains(webViewHostText, "ReaderBridgeCommand.NextPage")
		assertContains(webViewHostText, "ReaderBridgeCommand.PreviousPage")
		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay("),
			"Reader taps must be owned by the Android reader surface host, not a Compose overlay that can sit behind the WebView surface."
		)
		assertFalse(
			runtimeText.contains("readerTargetInsideShellCover") || runtimeText.contains("isInteractiveReaderTarget"),
			"Cover image taps must be owned above the WebView, not special-cased inside content hit testing."
		)
		assertContains(
			canReturnToShellCover,
			"const firstContent = Number(this.firstReadableContentTarget())",
			message = "Previous from the first readable section should return to shell cover before Foliate can enter its EPUB cover."
		)
		assertContains(
			canReturnToShellCover,
			"Math.floor(sectionIndex) <= firstContent",
			message = "The shell-cover boundary must be section-based, not only page-index based."
		)
	}

	@Test
	fun androidReaderStylesEbookHyperlinksAsInlineFastForwardAffordances() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "a:any-link")
		assertContains(bridgeText, "classifyReaderLinks")
		assertContains(bridgeText, "data-navic-link-kind")
		assertContains(bridgeText, "color: inherit !important")
		assertContains(bridgeText, "text-decoration: none !important")
		assertFalse(
			bridgeText.contains("content: ' >>'"),
			"Hyperlinks must not expose the literal ASCII fast-forward marker."
		)
		assertContains(bridgeText, "a:any-link[data-navic-link-kind=\"text\"]::after")
		assertContains(bridgeText, "a:any-link[data-navic-link-kind=\"media\"]::after")
		assertContains(bridgeText, "content: ' »'")
		assertContains(bridgeText, "vertical-align: sub")
		assertContains(bridgeText, "font-size: 0.72em")
		assertContains(bridgeText, "closestElement")
		assertContains(bridgeText, "parentElement?.closest")
		assertContains(bridgeText, "attachLinkNavigation")
		assertContains(bridgeText, "attachContentDocumentBehaviors")
		assertContains(bridgeText, "if (detail.doc)")
		assertContains(bridgeText, "closestElement(event.target, 'a[href]')")
		assertContains(bridgeText, "readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(bridgeText, "event.stopPropagation()")
		assertContains(bridgeText, "await this.goTo(href)")
		assertContains(bridgeText, "link:navigate")
	}

	@Test
	fun androidReaderSettingsDoNotQueryPaginationBeforeEpubFrameExists() {
		val bridgeText = readerBridgeText()
		val applySettings = bridgeText
			.substringAfter("applySettings(settings) {")
			.substringBefore("\n  applyThemeToLoadedContent")

		assertFalse(
			applySettings.contains("readerPagePosition("),
			"Settings are applied during EPUB open before Foliate always has an active iframe; querying pagination there can abort publication loading."
		)
		assertContains(bridgeText, "scheduleReaderPageNumberRefresh")
		assertContains(bridgeText, "tryUpdateReaderPageNumberLayer")
	}

	@Test
	fun androidReaderInjectsThemeColorsIntoPublicationDocuments() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "const palette = readerThemePalette(settings.theme)")
		assertContains(bridgeText, "this.readerSettings = settings")
		assertContains(bridgeText, "this.applyDocumentTheme(content.doc, settings, content.index)")
		assertContains(bridgeText, "applyThemeToLoadedContent")
		assertContains(bridgeText, "applyDocumentTheme(doc, settings = this.readerSettings, index = undefined)")
		assertContains(bridgeText, "background-color: var(--reader-background) !important")
		assertContains(bridgeText, "background: var(--reader-background) !important")
		assertContains(bridgeText, "[style*=\"background\"]")
		assertContains(bridgeText, "canvas, svg")
		assertContains(bridgeText, "--theme-bg-color: \${palette.background}")
		assertContains(bridgeText, "shadowRoot.querySelectorAll('#background, #background > *')")
		assertContains(bridgeText, "--reader-background: \${palette.background}")
		assertContains(bridgeText, "--reader-foreground: \${palette.foreground}")
		assertContains(bridgeText, "--reader-accent: \${palette.accent}")
		assertContains(bridgeText, "html, body")
		assertContains(bridgeText, "background-color: var(--reader-background) !important")
		assertContains(bridgeText, "color: var(--reader-foreground) !important")
	}

	@Test
	fun androidReaderAppliesToggleableSepiaOverlayToWhiteBackedImages() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "ReaderThemeSepia")
		assertContains(bridgeText, "readerThemeKey(settings?.theme)")
		assertContains(bridgeText, "attachSepiaImageOverlayToggle")
		assertContains(bridgeText, "readerImageFromMediaTarget")
		assertContains(bridgeText, "data-navic-sepia-overlay")
		assertContains(bridgeText, "img:not([data-navic-sepia-overlay=\"off\"])")
		assertContains(bridgeText, "mix-blend-mode: multiply")
		assertContains(bridgeText, "mix-blend-mode: normal !important")
		assertContains(bridgeText, "event.stopImmediatePropagation()")
		assertContains(bridgeText, "image:sepia-overlay")
		val sepiaImageRule = bridgeText.substringAfter("img:not([data-navic-sepia-overlay=\"off\"]) {")
			.substringBefore("}")
		assertFalse(
			sepiaImageRule.contains("background-color"),
			"Sepia image overlay must not fill transparent image pixels with a second sepia background."
		)
	}

	@Test
	fun androidReaderDoesNotNavigateImageAnchorsWhenTogglingSepiaOverlay() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertContains(bridgeText, "const isReaderMediaAnchor =")
		assertContains(bridgeText, "const isReaderMediaTapTarget =")
		assertContains(linkNavigation, "const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(linkNavigation, "if (mediaTapTarget) {")
		assertContains(linkNavigation, "event.preventDefault()")
		assertContains(linkNavigation, "event.stopImmediatePropagation()")
		assertFalse(
			linkNavigation
				.substringAfter("if (mediaTapTarget) {")
				.substringBefore("return")
				.contains("await this.goTo(href)"),
			"Reader media anchor taps must never fall through to href navigation while toggling image tint."
		)
		assertTrue(
			linkNavigation.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				linkNavigation.indexOf("const rawHref"),
			"Reader link navigation must ignore media/image anchors before resolving hrefs."
		)
		assertContains(bridgeText, "toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(bridgeText, "const image = readerImageFromMediaTarget(mediaTapTarget)")
	}

	@Test
	fun androidReaderLetsMediaTogglesWinOverReadableTapZones() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()
		val tapZoneTargetGuard = bridgeText
			.substringAfter("shouldIgnoreReaderTapZoneTarget(event, sourceTarget) {")
			.substringBefore("\n  handleReaderTapZoneTap")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay("),
			"Readable media taps must not depend on a Compose overlay consuming the WebView gesture stream."
		)
		assertContains(webViewHostText, "ReaderSurfaceHost")
		assertContains(webViewHostText, "val childHandled = super.dispatchTouchEvent(event)")
		assertContains(webViewHostText, "readerContentHandledTap(contentHitType)")
		assertContains(webViewHostText, "ReaderBridgeEvent.ContentTapHandled")
		assertContains(webViewHostText, "markContentTapHandled()")
		assertContains(webViewHostText, "readerContentTapHandled()")
		assertContains(webViewHostText, "WebView.HitTestResult.SRC_ANCHOR_TYPE")
		assertContains(webViewHostText, "WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE")
		assertContains(tapZoneTargetGuard, "readerPointInsideAnchorText(anchor, event)")
		assertContains(tapZoneTargetGuard, "readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(bridgeText, "this.shouldIgnoreReaderTapZoneTarget(event, sourceTarget)")
		assertContains(bridgeText, "post({ type: 'readerContentTapHandled'")
		assertContains(linkNavigation, "const toggled = this.toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(sepiaToggle, "this.toggleSepiaImageOverlayFromEvent(doc, tapEvent, state.mediaTapTarget)")
	}

	@Test
	fun readerNormalLinksReportContentTapHandledBeforeNavigation() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val normalLinkNavigation = linkNavigation
			.substringAfter("const rawHref = anchor.getAttribute('href')")
			.substringBefore("await this.goTo(href)")

		assertContains(normalLinkNavigation, "post({ type: 'readerContentTapHandled', source: 'link' })")
		assertTrue(
			normalLinkNavigation.indexOf("post({ type: 'readerContentTapHandled', source: 'link' })") <
				normalLinkNavigation.indexOf("event.preventDefault()"),
			"Normal EPUB links must notify native content ownership before navigation work can race with reader center-menu dispatch."
		)
	}

	@Test
	fun androidReaderCancelsPendingCenterChromeWhenContentHandlesTap() {
		val webViewHostText = readerWebViewHostFile().readText()
		val dispatchWideTap = webViewHostText
			.substringAfter("private fun dispatchReaderWideTap(event: MotionEvent) {")
			.substringBefore("\n\tfun markContentTapHandled()")
		val markContentTapHandled = webViewHostText
			.substringAfter("fun markContentTapHandled() {")
			.substringBefore("\n\tprivate fun readerContentTapHandled()")

		assertContains(webViewHostText, "private const val ReaderCenterTapDelayMs")
		assertContains(webViewHostText, "private var pendingCenterTap: Runnable? = null")
		assertContains(webViewHostText, "private fun scheduleReaderCenterTap(")
		assertContains(webViewHostText, "private fun cancelPendingReaderCenterTap()")
		assertContains(markContentTapHandled, "cancelPendingReaderCenterTap()")
		assertContains(dispatchWideTap, "dispatchReaderPageTurnCommand(command)")
		assertContains(dispatchWideTap, "scheduleReaderCenterTap(")
		assertFalse(
			dispatchWideTap.contains("else {\n\t\t\tonReaderCenterTap()\n\t\t}"),
			"Interactive image/content taps report readerContentTapHandled asynchronously, so center chrome dispatch must be delayed and cancelable instead of immediate."
		)
	}

	@Test
	fun androidReaderGivesWebViewContentEnoughTimeToCancelCenterChrome() {
		val webViewHostText = readerWebViewHostFile().readText()
		val delayMs = Regex("""private const val ReaderCenterTapDelayMs = (\d+)L""")
			.find(webViewHostText)
			?.groupValues
			?.get(1)
			?.toLong()
			?: error("ReaderCenterTapDelayMs constant not found")
		val markContentTapHandled = webViewHostText
			.substringAfter("fun markContentTapHandled() {")
			.substringBefore("\n\tprivate fun readerContentTapHandled()")

		assertTrue(
			delayMs >= 280L,
			"Android WebView can deliver content click/touch JS bridge messages after ACTION_UP; center chrome delay must allow that without delaying edge page turns."
		)
		assertTrue(
			markContentTapHandled.indexOf("contentTapHandledUntilMs = SystemClock.uptimeMillis() + ReaderContentTapHandledSuppressMs") <
				markContentTapHandled.indexOf("cancelPendingReaderCenterTap()"),
			"Content ownership must be marked before canceling pending center chrome so the runnable suppresses itself even if cancellation races."
		)
	}

	@Test
	fun androidReaderAllowsSlowWebViewBridgeDeliveryBeforeOpeningCenterChrome() {
		val webViewHostText = readerWebViewHostFile().readText()
		val delayMs = Regex("""private const val ReaderCenterTapDelayMs = (\d+)L""")
			.find(webViewHostText)
			?.groupValues
			?.get(1)
			?.toLong()
			?: error("ReaderCenterTapDelayMs constant not found")
		val suppressMs = Regex("""private const val ReaderContentTapHandledSuppressMs = (\d+)L""")
			.find(webViewHostText)
			?.groupValues
			?.get(1)
			?.toLong()
			?: error("ReaderContentTapHandledSuppressMs constant not found")

		assertTrue(
			delayMs >= 650L,
			"Center chrome must wait long enough for real Android WebView bridge delivery from image/link content actions before opening."
		)
		assertTrue(
			suppressMs >= delayMs + 200L,
			"Content-handled suppression must outlive the delayed center chrome runnable so late bridge delivery cannot leak a menu."
		)
	}

	@Test
	fun androidReaderRechecksLatestContentHitBeforeDelayedCenterChromeDispatch() {
		val webViewHostText = readerWebViewHostFile().readText()
		val scheduleCenterTap = webViewHostText
			.substringAfter("private fun scheduleReaderCenterTap(x: Int, y: Int, hitType: Int) {")
			.substringBefore("\n\tprivate fun cancelPendingReaderCenterTap()")

		assertContains(
			scheduleCenterTap,
			"val latestHitType = readerWebView?.hitTestResult?.type ?: hitType",
			message = "Android WebView hit testing can update after ACTION_UP, so delayed center chrome must re-read the content hit before opening."
		)
		assertContains(
			scheduleCenterTap,
			"readerContentHandledCenterTap(latestHitType)",
			message = "Delayed center chrome must still be suppressible by image/link hit types discovered after the native tap was classified."
		)
		assertTrue(
			scheduleCenterTap.indexOf("readerContentHandledCenterTap(latestHitType)") <
				scheduleCenterTap.indexOf("onReaderCenterTap()"),
			"Latest image/link hit suppression must run before center chrome dispatch."
		)
	}

	@Test
	fun androidReaderMarksContentHandledOnReaderSurfaceThread() {
		val webViewHostText = readerWebViewHostFile().readText()
		val contentHandledBranch = webViewHostText
			.substringAfter("if (event == ReaderBridgeEvent.ContentTapHandled) {")
			.substringBefore("\n\t\t\t\t} else {")

		assertContains(
			contentHandledBranch,
			"surfaceHost.post",
			message = "Android JavaScript bridge callbacks are not guaranteed to run on the UI thread; content ownership must mark the reader surface from its own thread."
		)
		assertContains(contentHandledBranch, "markContentTapHandled()")
		assertFalse(
			contentHandledBranch.contains("surfaceHostRef.get()?.markContentTapHandled()"),
			"Content ownership must not mutate View-backed reader state directly from the JavaScript bridge thread."
		)
	}

	@Test
	fun androidReaderClaimsInteractiveTouchBeforeNativeCenterChromeCanDispatch() {
		val bridgeText = readerBridgeText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  attachLinkNavigation")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")

		assertContains(bridgeText, "claimReaderInteractiveContentTouch(doc, event)")
		assertContains(linkNavigation, "doc.addEventListener('touchstart', event => {")
		assertContains(linkNavigation, "this.claimReaderInteractiveContentTouch(doc, event)")
		assertContains(linkNavigation, "doc.addEventListener('touchend', event => {")
		assertContains(
			claimInteractiveTouch,
			"post({ type: 'readerContentTapHandled', source: 'media-touch' })",
			message = "Image/media touches must claim content ownership before Android can dispatch center chrome."
		)
		assertContains(
			claimInteractiveTouch,
			"post({ type: 'readerContentTapHandled', source: 'link-touch' })",
			message = "Chapter/frontmatter link touches must claim content ownership before Android can dispatch center chrome."
		)
		assertTrue(
			linkNavigation.indexOf("doc.addEventListener('touchstart'") <
				linkNavigation.indexOf("doc.addEventListener('click'"),
			"Interactive content ownership must be claimed during the touch phase, not only during synthetic click handling."
		)
	}

	@Test
	fun androidReaderClaimsAnchorTouchBeforeTextRectNavigationHitTesting() {
		val bridgeText = readerBridgeText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  handleReaderTapZoneTap")

		assertContains(
			claimInteractiveTouch,
			"if (anchor) {",
			message = "Any actual anchor touch target should claim content ownership before native center chrome can fire."
		)
		assertFalse(
			claimInteractiveTouch.contains("if (anchor && readerPointInsideAnchorText(anchor, event))"),
			"Text-rect hit testing can gate link navigation, but must not delay the touch-phase content ownership signal."
		)
		assertContains(
			claimInteractiveTouch,
			"textHit: readerPointInsideAnchorText(anchor, event)",
			message = "Anchor diagnostics should still record whether the touch was inside rendered link text."
		)
	}

	@Test
	fun androidReaderClaimsInteractiveContentOnPointerAndMouseDownBeforeNativeCenterChrome() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")

		assertContains(
			linkNavigation,
			"doc.addEventListener('pointerdown', event => {",
			message = "Android WebView can expose content interaction before click through pointer events; interactive content must claim ownership before native center chrome can fire."
		)
		assertContains(
			linkNavigation,
			"doc.addEventListener('mousedown', event => {",
			message = "Mouse-style WebView click paths must also claim content ownership before native center chrome can fire."
		)
		assertTrue(
			linkNavigation.indexOf("doc.addEventListener('pointerdown'") <
				linkNavigation.indexOf("doc.addEventListener('click'"),
			"Pointer ownership must run before final click navigation/toggle work."
		)
		assertTrue(
			linkNavigation.indexOf("doc.addEventListener('mousedown'") <
				linkNavigation.indexOf("doc.addEventListener('click'"),
			"Mouse ownership must run before final click navigation/toggle work."
		)
	}

	@Test
	fun androidReaderClaimsSepiaImageTouchAtGestureStart() {
		val bridgeText = readerBridgeText()
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")
		val sepiaTouchStart = sepiaToggle
			.substringAfter("doc.addEventListener('touchstart', event => {")
			.substringBefore("}, { capture: true, passive: true })")

		assertContains(
			sepiaTouchStart,
			"this.claimReaderInteractiveContentTouch(doc, event)",
			message = "Image gestures must claim content ownership at touchstart; waiting for click/touchend still lets native center chrome leak on Android."
		)
		assertTrue(
			sepiaTouchStart.indexOf("this.claimReaderInteractiveContentTouch(doc, event)") <
				sepiaTouchStart.indexOf("touchState = {"),
			"The image touch claim should happen before gesture bookkeeping so early Android bridge delivery is not gated by later toggle logic."
		)
	}

	@Test
	fun readerHarnessCssSmokeRequiresContentActionBridgeOwnership() {
		val harnessText = listOf(
			File("tools/reader-harness/src/run-reader-harness.mjs"),
			File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { it.exists() }.readText()
		val assertionsText = listOf(
			File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).first { it.exists() }.readText()

		assertContains(harnessText, "__navicReaderPostedMessages")
		assertContains(
			harnessText,
			"imageContentTapHandledCount",
			message = "CSS smoke must record native bridge ownership for image tint toggles, otherwise Android chrome races are invisible locally."
		)
		assertContains(
			harnessText,
			"textLinkContentTapHandledCount",
			message = "CSS smoke must record native bridge ownership for text link navigation, otherwise Android chrome races are invisible locally."
		)
		assertContains(
			harnessText,
			"imageTouchContentTapHandledCount",
			message = "CSS smoke must record touch-phase bridge ownership for image taps, because Android chrome races the touch sequence."
		)
		assertContains(
			harnessText,
			"textLinkTouchContentTapHandledCount",
			message = "CSS smoke must record touch-phase bridge ownership for link taps, because Android chrome races the touch sequence."
		)
		assertContains(
			assertionsText,
			"Expected image tint toggle to send readerContentTapHandled",
			message = "The renderer assertion must fail when image actions do not notify native content ownership."
		)
		assertContains(
			assertionsText,
			"Expected styled text link click to send readerContentTapHandled",
			message = "The renderer assertion must fail when text links do not notify native content ownership."
		)
		assertContains(
			assertionsText,
			"Expected image touch to send readerContentTapHandled",
			message = "The renderer assertion must fail when image touch events do not notify native content ownership before Android chrome."
		)
		assertContains(
			assertionsText,
			"Expected styled text link touch to send readerContentTapHandled",
			message = "The renderer assertion must fail when link touch events do not notify native content ownership before Android chrome."
		)
		assertContains(
			harnessText,
			"imageNativeCenterContentHit",
			message = "CSS smoke must simulate Android's delayed center-menu hit test for image taps, not only count bridge posts."
		)
		assertContains(
			harnessText,
			"imageNativeScaledContentHit",
			message = "CSS smoke must simulate Android MotionEvent view-pixel coordinates, not only browser CSS coordinates."
		)
		assertContains(
			harnessText,
			"textLinkNativeCenterContentHit",
			message = "CSS smoke must simulate Android's delayed center-menu hit test for link taps, not only count bridge posts."
		)
		assertContains(
			harnessText,
			"textLinkNativeScaledContentHit",
			message = "CSS smoke must prove link hit-testing survives Android view-pixel to CSS-pixel normalization."
		)
		assertContains(
			assertionsText,
			"Expected native center hit-test to suppress image chrome",
			message = "The renderer assertion must fail when Android-style center chrome would still open over an image interaction."
		)
		assertContains(
			assertionsText,
			"Expected scaled native center hit-test to suppress image chrome",
			message = "The renderer assertion must fail when native Android view-pixel coordinates miss image content."
		)
		assertContains(
			assertionsText,
			"Expected native center hit-test to suppress link chrome",
			message = "The renderer assertion must fail when Android-style center chrome would still open over a link interaction."
		)
		assertContains(
			assertionsText,
			"Expected scaled native center hit-test to suppress link chrome",
			message = "The renderer assertion must fail when native Android view-pixel coordinates miss link content."
		)
	}

	@Test
	fun androidReaderUsesCoordinateContentHitTestBeforeDelayedCenterChrome() {
		val bridgeText = readerBridgeText()
		val webViewHostText = readerWebViewHostFile().readText()
		val scheduleCenterTap = webViewHostText
			.substringAfter("private fun scheduleReaderCenterTap(")
			.substringBefore("\n\tprivate fun cancelPendingReaderCenterTap()")
		val exportedBridge = bridgeText
			.substringAfter("window.NavicReaderBridge = {")
			.substringBefore("\n}")

		assertContains(
			bridgeText,
			"readerContentActionAtRootPoint",
			message = "Android native center chrome needs a coordinate hit-test into Foliate content documents when WebView hitTestResult is UNKNOWN."
		)
		assertContains(
			exportedBridge,
			"readerContentActionAtPoint",
			message = "The Android WebView host must be able to ask the reader runtime whether a pending center tap landed on a link/image."
		)
		assertContains(
			scheduleCenterTap,
			"evaluateJavascript",
			message = "Delayed center chrome must query the Web runtime before opening, because bridge content-tap posts can race native ACTION_UP."
		)
		assertContains(
			scheduleCenterTap,
			"readerContentActionAtPoint",
			message = "The native query must call the coordinate content hit-test exposed by NavicReaderBridge."
		)
		assertContains(
			scheduleCenterTap,
			"readerContentActionAtPoint(\$x,\$y,\${webView.width},\${webView.height})",
			message = "Android must pass native WebView dimensions so JavaScript can normalize MotionEvent pixels to CSS viewport pixels."
		)
		assertContains(
			scheduleCenterTap,
			"Reader surface delayed center tap ignored for runtime content hit",
			message = "ADB logs need to distinguish runtime coordinate suppression from ordinary WebView hitTestResult suppression."
		)
		assertContains(
			scheduleCenterTap,
			"centerTapSequence",
			message = "Asynchronous evaluateJavascript callbacks must not open chrome for stale canceled center taps."
		)
	}

	@Test
	fun androidReaderQueriesRuntimeContentHitBeforeDelayedCenterChromeCanMutateDocument() {
		val webViewHostText = readerWebViewHostFile().readText()
		val scheduleCenterTap = webViewHostText
			.substringAfter("private fun scheduleReaderCenterTap(x: Int, y: Int, hitType: Int) {")
			.substringBefore("\n\tprivate fun cancelPendingReaderCenterTap()")

		assertContains(
			webViewHostText,
			"private fun queryReaderContentActionAtPoint(",
			message = "Runtime content hit-test JavaScript should be a reusable native helper, not buried inside the delayed chrome runnable."
		)
		assertTrue(
			scheduleCenterTap.indexOf("queryReaderContentActionAtPoint(") in 0 until scheduleCenterTap.indexOf("val pending = Runnable"),
			"Image/link center taps must start the runtime content hit-test before delayed chrome dispatch can run or link navigation can mutate the document."
		)
		assertContains(
			scheduleCenterTap,
			"Reader surface center tap ignored for immediate runtime content hit",
			message = "ADB logs need to distinguish immediate coordinate suppression from the delayed fallback query."
		)
	}

	@Test
	fun androidReaderRemembersRecentContentTouchForNativeCenterChromeAfterDocumentMutation() {
		val bridgeText = readerBridgeText()
		val harnessText = listOf(
			File("tools/reader-harness/src/run-reader-harness.mjs"),
			File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { it.exists() }.readText()
		val assertionsText = listOf(
			File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).first { it.exists() }.readText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")
		val contentActionAtRootPoint = bridgeText
			.substringAfter("readerContentActionAtRootPoint(rootX, rootY, viewWidth = null, viewHeight = null) {")
			.substringBefore("\n  handleReaderTapZoneTap")

		assertContains(
			bridgeText,
			"rememberReaderContentActionTouch",
			message = "Touch-phase image/link ownership must be remembered locally so Android delayed center chrome still suppresses after DOM mutation or navigation."
		)
		assertContains(
			bridgeText,
			"recentReaderContentActionAtRootPoint",
			message = "Native coordinate hit testing must check recent content-owned touch points before falling back to the current DOM."
		)
		assertTrue(
			claimInteractiveTouch.indexOf("rememberReaderContentActionTouch") <
				claimInteractiveTouch.indexOf("post({ type: 'readerContentTapHandled'"),
			"Content touch ownership should be remembered before posting to Android so the local fallback survives bridge timing races."
		)
		assertTrue(
			contentActionAtRootPoint.indexOf("recentReaderContentActionAtRootPoint(rootPoint)") <
				contentActionAtRootPoint.indexOf("for (const entry of this.contentEntries())"),
			"Recent content-owned touch points must be checked before querying the mutated current document tree."
		)
		assertContains(
			harnessText,
			"imageRecentTouchContentHitAfterRemoval",
			message = "CSS smoke must model an image touch whose DOM node disappears before Android's delayed center hit test."
		)
		assertContains(
			harnessText,
			"textLinkRecentTouchContentHitAfterRemoval",
			message = "CSS smoke must model a link touch whose DOM node disappears before Android's delayed center hit test."
		)
		assertContains(
			assertionsText,
			"Expected recent image touch ownership",
			message = "Renderer assertions must fail when recent image touch ownership does not suppress native chrome."
		)
		assertContains(
			assertionsText,
			"Expected recent text link touch ownership",
			message = "Renderer assertions must fail when recent link touch ownership does not suppress native chrome."
		)
	}

	@Test
	fun androidReaderConsumesTouchImageTogglesBeforeSyntheticLinkClicks() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertContains(bridgeText, "toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(sepiaToggle, "touchstart")
		assertContains(sepiaToggle, "touchend")
		assertContains(bridgeText, "markReaderMediaTapHandled(doc, event, image || mediaTapTarget)")
		assertContains(bridgeText, "readerShouldSuppressMediaSyntheticClick(doc, event, anchor)")
		assertContains(bridgeText, "__navicSuppressNextMediaClickUntil")
		assertContains(bridgeText, "performance.now() <= suppressUntil")
		assertContains(sepiaToggle, "const anchor = closestElement(event.target, 'a[href]')")
		assertContains(sepiaToggle, "mediaTapTarget: readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(sepiaToggle, "state.mediaTapTarget")
		assertContains(sepiaToggle, "__navicLastMediaTapHandledAt")
		assertContains(bridgeText, "readerLastMediaTapRectContainsPoint")
		assertContains(bridgeText, "__navicLastMediaTapRect")
		assertContains(bridgeText, "markReaderMediaTapHandled(doc, event, image || mediaTapTarget)")
		assertTrue(
			linkNavigation.indexOf("readerShouldSuppressMediaSyntheticClick(doc, event, anchor)") <
				linkNavigation.indexOf("const rawHref"),
			"Reader link navigation must suppress synthetic clicks from image tint toggles before resolving hrefs."
		)
	}

	@Test
	fun androidReaderGivesMediaHitTestingPriorityOverAdjacentLinks() {
		val bridgeText = readerBridgeText()
		val mediaHitTesting = bridgeText
			.substringAfter("const readerMediaTapTargetForEvent = (doc, event, anchor) =>")
			.substringBefore("\n\n// Ported from Komikku")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  readerTapZone")

		assertContains(mediaHitTesting, "const { clientX, clientY } = readerEventClientPoint(event)")
		assertContains(mediaHitTesting, "doc?.elementsFromPoint?.(clientX, clientY)")
		assertContains(mediaHitTesting, "getBoundingClientRect")
		assertContains(mediaHitTesting, "readerPointInsideRect")
		assertContains(mediaHitTesting, "readerMediaElementFromCandidate")
		assertContains(linkNavigation, "const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(linkNavigation, "if (mediaTapTarget) {")
		assertTrue(
			linkNavigation.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				linkNavigation.indexOf("const rawHref"),
			"Reader link navigation must let media/image hit testing consume the tap before href resolution."
		)
		assertContains(bridgeText, "toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(bridgeText, "const image = readerImageFromMediaTarget(mediaTapTarget)")
	}

	@Test
	fun androidReaderDoesNotLetTextAnchorsConsumeAdjacentImageTaps() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")

		assertContains(bridgeText, "const readerPointInsideAnchorText = (anchor, event) =>")
		assertContains(bridgeText, "doc.createTreeWalker(anchor, NodeFilter.SHOW_TEXT")
		assertContains(bridgeText, "range.getClientRects()")
		assertContains(linkNavigation, "if (!readerPointInsideAnchorText(anchor, event)) {")
		assertContains(linkNavigation, "link:text-hit-miss")
		assertTrue(
			linkNavigation.indexOf("if (!readerPointInsideAnchorText(anchor, event)) {") <
				linkNavigation.indexOf("const rawHref"),
			"Text-link navigation must reject taps outside real text before resolving hrefs."
		)
		assertTrue(
			linkNavigation.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				linkNavigation.indexOf("if (!readerPointInsideAnchorText(anchor, event)) {"),
			"Media hit testing must still run before text-anchor hit testing."
		)
	}

	@Test
	fun androidReaderSuppressesSyntheticAnchorClicksAfterImageTintToggle() {
		val bridgeText = readerBridgeText()
		val suppression = bridgeText
			.substringAfter("const readerShouldSuppressMediaSyntheticClick = (doc, event, anchor) =>")
			.substringBefore("\n\n// Ported from Komikku")
		val immediateSuppressBlock = suppression
			.substringAfter("if (suppressUntil && performance.now() <= suppressUntil) {")
			.substringBefore("\n  }")

		assertContains(suppression, "const suppressUntil = Number(win.__navicSuppressNextMediaClickUntil || 0)")
		assertContains(suppression, "if (suppressUntil && performance.now() <= suppressUntil) {")
		assertContains(immediateSuppressBlock, "win.__navicSuppressNextMediaClickUntil = 0")
		assertContains(immediateSuppressBlock, "return true")
		assertTrue(
			immediateSuppressBlock.indexOf("win.__navicSuppressNextMediaClickUntil = 0") <
				immediateSuppressBlock.indexOf("return true"),
			"Any immediate anchor click after an image tint toggle must be consumed before adjacent-link hit testing."
		)
		assertFalse(
			immediateSuppressBlock.contains("readerLastMediaTapRectContainsPoint"),
			"Immediate synthetic-click suppression must not depend on where the adjacent link reports its hit box."
		)
	}

}
