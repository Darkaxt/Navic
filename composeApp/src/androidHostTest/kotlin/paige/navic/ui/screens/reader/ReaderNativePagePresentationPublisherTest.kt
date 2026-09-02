package paige.navic.ui.screens.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import karacken.curl.PageSurfaceView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationToken

class ReaderNativePagePresentationPublisherTest {
	@Test
	fun deckReadyOnlyArmsAndExactPresentedFramePublishesProof() {
		val source = ControllablePresentedFrameSource()
		var candidate: ReaderNativePagePresentationCandidate? = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = events::add
		)

		publisher.update()
		publisher.update()

		assertTrue(events.isEmpty())
		assertEquals(listOf(1L), source.requestedIds)
		source.present(1L)

		val event = assertIs<ReaderPresentationEvent.NativePagePresented>(events.single())
		assertEquals(candidate?.binding, event.proof.binding)
		assertEquals(1L, event.proof.presentedFrame)
		assertEquals(candidate?.transitionToken, event.proof.transitionToken)
		publisher.update()
		assertEquals(listOf(1L), source.requestedIds)
	}

	@Test
	fun replacementLossAndDisposalCancelOrFenceLateFrames() {
		val source = ControllablePresentedFrameSource()
		var candidate: ReaderNativePagePresentationCandidate? = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = events::add
		)
		publisher.update()

		candidate = candidate(sequence = 2L)
		publisher.update()
		assertEquals(listOf(1L), source.cancelledIds)
		source.present(1L)
		assertTrue(events.isEmpty())
		source.present(2L)
		assertEquals(candidate?.binding, assertIs<ReaderPresentationEvent.NativePagePresented>(events.single()).proof.binding)

		candidate = candidate(sequence = 3L)
		publisher.update()
		candidate = null
		publisher.update()
		assertEquals(listOf(1L, 3L), source.cancelledIds)
		source.present(3L)
		assertEquals(1, events.size)

		candidate = candidate(sequence = 4L)
		publisher.update()
		publisher.dispose()
		assertEquals(listOf(1L, 3L, 4L), source.cancelledIds)
		source.present(4L)
		assertEquals(1, events.size)
	}

	@Test
	fun callbackRevalidatesAllCandidateFactsAndSubsequentCurrentFrameCanPublish() {
		val source = ControllablePresentedFrameSource()
		var candidate: ReaderNativePagePresentationCandidate? = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = events::add
		)
		publisher.update()

		candidate = candidate?.copy(viewportWidth = 1199)
		source.present(1L)
		assertTrue(events.isEmpty())

		publisher.update()
		assertEquals(listOf(1L, 2L), source.requestedIds)
		source.present(2L)
		assertEquals(1199, assertIs<ReaderPresentationEvent.NativePagePresented>(events.single()).proof.viewportWidth)
	}

	private fun candidate(sequence: Long) = ReaderNativePagePresentationCandidate(
		binding = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture-session", sequence),
			rasterGeneration = 4L + sequence,
			textureGeneration = 5L + sequence,
			preparationGeneration = 6L + sequence
		),
		transitionToken = ReaderPresentationToken(20L + sequence).takeIf { sequence > 2L },
		visualPageIndex = sequence.toInt(),
		viewportWidth = 1200,
		viewportHeight = 800,
		preparationFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Ready,
			generation = 6L + sequence
		)
	)
}

class ReaderPresentedFrameCallerSourceTest {
	@Test
	fun productionSurfaceFrameConsumersAreExplicitAndShareThePageSurfaceBroker() {
		val sourceRoot = File("src/androidMain/kotlin")
		val callers = sourceRoot.walkTopDown()
			.filter { file ->
				file.isFile &&
					file.extension == "kt" &&
					"requestNextPresentedFrame" in file.readText()
			}
			.map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
			.toSet()

		assertEquals(
			setOf(
				"paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt",
				"paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt",
				"paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt"
			),
			callers
		)
		assertTrue(
			File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt")
				.readText()
				.contains("rendererSurface.requestNextPresentedFrame")
		)
		assertTrue(
			File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt")
				.readText()
				.contains("surfaceView.requestNextPresentedFrame")
		)
		assertTrue(
			File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt")
				.readText()
				.contains("surface.requestNextPresentedFrame")
		)
	}
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReaderPageSurfacePresentedFrameSourceTest {
	@Test
	fun productionAdapterBindsCallbackAndCancellationToPageSurfaceRequestId() {
		val surface = RecordingPageSurfaceView(
			ApplicationProvider.getApplicationContext()
		)
		val source = ReaderPageSurfacePresentedFrameSource(surface)
		val frames = mutableListOf<Long>()

		val requestId = source.requestNextPresentedFrame(frames::add)
		assertEquals(71L, requestId)
		assertTrue(frames.isEmpty())
		surface.present()
		assertEquals(listOf(71L), frames)
		assertTrue(source.cancelPresentedFrameRequest(71L))
		assertEquals(listOf(71L), surface.cancelledIds)
		assertFalse(source.cancelPresentedFrameRequest(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID))
	}

	private class RecordingPageSurfaceView(context: Context) : PageSurfaceView(context) {
		private var callback: Runnable? = null
		val cancelledIds = mutableListOf<Long>()

		override fun requestNextPresentedFrame(callback: Runnable): Long {
			this.callback = callback
			return 71L
		}

		override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
			if (requestId == NO_PRESENTED_FRAME_REQUEST_ID) return false
			cancelledIds += requestId
			return true
		}

		fun present() {
			callback?.run()
		}
	}
}

private class ControllablePresentedFrameSource : ReaderNativePagePresentedFrameSource {
	private var nextId = 1L
	private val callbacks = mutableMapOf<Long, (Long) -> Unit>()
	val requestedIds = mutableListOf<Long>()
	val cancelledIds = mutableListOf<Long>()

	override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
		val id = nextId++
		requestedIds += id
		callbacks[id] = onPresented
		return id
	}

	override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
		cancelledIds += requestId
		return true
	}

	fun present(requestId: Long) {
		callbacks[requestId]?.invoke(requestId)
	}
}
