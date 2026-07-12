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
		val exactNavigation = turns
			.substringAfter("async function goToVisualPage(")
			.substringBefore("\n}\n")

		assertContains(exactNavigation, "this.view.renderer.goTo({")
		assertContains(exactNavigation, "index: locator.spineIndex")
		assertContains(exactNavigation, "anchor: locator.anchor")
		assertContains(exactNavigation, "this.view.history?.pushState?.(")
		assertFalse(exactNavigation.contains("this.view?.next?.("))
		assertFalse(exactNavigation.contains("this.view?.prev?.("))
	}

	@Test
	fun settlementRequiresTokenAndExactVisualPage() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val turns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()

		assertContains(runtime, "nativePageTurnSettledState")
		assertContains(runtime, "nativePageTurnSettledState: () => runtime.nativePageTurnSettledState")
		assertContains(turns, "token: settleToken")
		assertContains(turns, "pageIndex: settledPageIndex")
		assertTrue(
			turns.indexOf("await this.view.renderer.goTo") < turns.indexOf("this.nativePageTurnSettledState ="),
			"Settlement must be published only after the exact renderer navigation resolves."
		)
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
	fun immutableBitmapBundleOwnsEveryDestinationSurfaceExactlyOnce() {
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()

		assertContains(bundle, "data class ReaderPageTurnTransitionPlan")
		assertContains(bundle, "class ReaderPageTurnBitmapBundle")
		assertContains(bundle, "val currentBase: Bitmap")
		assertContains(bundle, "val turningFront: Bitmap")
		assertContains(bundle, "val turningReverse: Bitmap?")
		assertContains(bundle, "val underneath: Bitmap?")
		assertContains(bundle, "val finalBase: Bitmap")
		assertContains(bundle, "if (recycled) return")
		assertContains(bundle, "fun recycle()")
		assertContains(bundle, "distinctBy { System.identityHashCode(it) }")
	}

	@Test
	fun bitmapBundleCacheIsBoundedAndRecyclesEvictedEntries() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "LinkedHashMap<String, ReaderPageTurnBitmapBundle>(0, 0.75f, true)")
		assertContains(source, "private const val MaxCachedBundles = 3")
		assertContains(source, "eldest.value.recycle()")
		assertContains(source, "cache.clear()")
	}

	@Test
	fun stagedCaptureRejectsAndRecyclesStaleGenerationsWithoutTimeouts() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "captureSurface(")
		assertContains(source, "captureStagedSurface(")
		assertContains(source, "if (generation != activeGeneration)")
		assertContains(source, "bundle.recycle()")
		assertContains(source, "webView.draw(canvas)")
		assertFalse(source.contains("postDelayed"))
		assertFalse(source.contains("setTimeout"))
	}
}
