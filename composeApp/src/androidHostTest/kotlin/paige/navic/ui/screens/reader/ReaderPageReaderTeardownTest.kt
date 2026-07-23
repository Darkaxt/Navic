package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageReaderTeardownTest {
	@Test
	fun readerCloseFencesRasterBeforeRendererAndWaitsBeforeBundleOwners() = runTest {
		val events = mutableListOf<String>()
		val releaseRenderer = CompletableDeferred<Unit>()
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = { events += "raster-fence" },
			closeRendererAndAdapter = {
				events += "renderer-start"
				withContext(NonCancellable) { releaseRenderer.await() }
				events += "renderer-end"
			},
			closeBundleOwners = {
				events += "bundle"
			}
		)

		val first = teardown.start()
		val second = teardown.start()
		assertSame(first, second)
		runCurrent()
		assertEquals(listOf("raster-fence", "renderer-start"), events)
		assertFalse(first.isCompleted)

		releaseRenderer.complete(Unit)
		first.await()
		assertEquals(
			listOf(
				"raster-fence",
				"renderer-start",
				"renderer-end",
				"bundle"
			),
			events
		)
	}

	@Test
	fun readerCloseStillClosesBundleWhenRendererCloseFails() = runTest {
		val events = mutableListOf<String>()
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = { events += "raster-fence" },
			closeRendererAndAdapter = {
				events += "renderer"
				error("renderer-close")
			},
			closeBundleOwners = {
				events += "bundle"
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}
		assertEquals(
			ReaderPageTeardownStage.RendererDisposal,
			failure.stage
		)
		assertEquals("renderer-close", failure.cause?.message)
		assertEquals(listOf("raster-fence", "renderer", "bundle"), events)
	}

	@Test
	fun repeatedFailureInstanceDoesNotAbortRemainingTeardown() = runTest {
		val shared = ReaderPageTeardownException(
			ReaderPageTeardownStage.RendererDisposal
		)
		val events = mutableListOf<String>()
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = {},
			closeRendererAndAdapter = { throw shared },
			closeBundleOwners = {
				events += "bundle"
				throw shared
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}

		assertSame(shared, failure)
		assertEquals(listOf("bundle"), events)
		assertTrue(failure.suppressed.isEmpty())
	}
}
