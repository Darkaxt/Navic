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
}
