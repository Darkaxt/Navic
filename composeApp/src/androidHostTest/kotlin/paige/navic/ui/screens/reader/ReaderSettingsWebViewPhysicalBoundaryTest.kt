package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.webkit.ValueCallback
import android.webkit.WebView
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderSettingsWebViewPhysicalBoundaryTest {
	private class PrimitiveWebView(context: Context) : WebView(context) {
		var javascriptCalls = 0
		var visualCalls = 0
		var synchronousVisual = false
		var afterVisualResult: (Long) -> Unit = {}
		var visual: Pair<Long, VisualStateCallback>? = null
		override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
			javascriptCalls++
		}
		override fun postVisualStateCallback(requestId: Long, callback: VisualStateCallback) {
			visualCalls++
			visual = requestId to callback
			if (synchronousVisual) {
				try { callback.onComplete(requestId) }
				finally { afterVisualResult(requestId) }
			}
		}
		fun deliverVisual() {
			val accepted = checkNotNull(visual)
			accepted.second.onComplete(accepted.first)
		}
	}

	private fun domain(sequence: Long = 23L) = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(sequence))
	private fun drain(ownership: ReaderForegroundWebViewOwnership) {
		ownership.snapshotFrozenOwnership().forEach {
			assertEquals(ReaderPortCommandResult.Accepted, ownership.drainFrozenOwnership(it.physicalIdentity) {})
		}
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty(), "only genuine started tails may prevent exact drain")
	}
	private fun acquire(ownership: ReaderForegroundWebViewOwnership, snapshot: (Int) -> Unit = {}): ReaderSettingsWebViewMutation {
		var mutation: ReaderSettingsWebViewMutation? = null
		ReaderSettingsWebViewMutationCoordinator(ownership, snapshot).acquireSettingsMutation(11L) {
			mutation = assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(it).mutation
		}
		return checkNotNull(mutation)
	}

	@Test
	fun task397TwoQueuedSettingsUseActualPrimitivesOnceAndRetainOldOuterReturn() {
		val ownership = ReaderForegroundWebViewOwnership()
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication()).apply { synchronousVisual = true }
		var snapshots = 0
		var events = 0
		var dispatched = 0
		var confirmations = 0
		var firstPrimitiveReturning = false
		val coordinator = ReaderSettingsWebViewMutationCoordinator(ownership) { snapshots++ }
		fun dispatchNext() {
			if (dispatched == 2) return
			val sequence = (++dispatched).toLong()
			coordinator.acquireSettingsMutation(sequence) { readiness ->
				val mutation = assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(readiness).mutation
				val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) { true }
				assertTrue(boundary.dispatch("void 0"))
				assertTrue(boundary.acknowledge(sequence, 29, { events++ }, ::dispatchNext))
			}
		}
		view.afterVisualResult = { sequence ->
			if (sequence == 1L) {
				ownership.freezeForTransitionActivation(domain())
				val tails = ownership.snapshotFrozenOwnership()
				assertTrue(tails.isNotEmpty(), "released logical owner still has a real primitive return tail")
				tails.forEach { ownership.drainFrozenOwnership(it.physicalIdentity) {
					assertTrue(firstPrimitiveReturning)
					confirmations++
				} }
				assertEquals(0, confirmations)
				firstPrimitiveReturning = true
			}
		}
		val failure = runCatching { dispatchNext() }.exceptionOrNull()
		assertTrue(failure == null, "logical exclusivity must release before successor acquisition")
		assertEquals(2, dispatched)
		assertEquals(2, snapshots)
		assertEquals(2, events)
		assertEquals(2, view.javascriptCalls)
		assertEquals(2, view.visualCalls)
		assertEquals(0, ownership.snapshot().liveClaims)
		assertTrue(confirmations > 0)
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty())
	}

	@Test
	fun task397ReleaseAvailabilityRefreezeParksSuccessorBeforeAcquisition() {
		lateinit var ownership: ReaderForegroundWebViewOwnership
		var fenced = false
		ownership = ReaderForegroundWebViewOwnership(onPassiveAvailable = {
			if (!fenced) { fenced = true; ownership.freezeForTransitionActivation(domain()) }
		})
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication()).apply { synchronousVisual = true }
		var dispatched = 0
		var snapshots = 0
		val coordinator = ReaderSettingsWebViewMutationCoordinator(ownership) { snapshots++ }
		fun dispatchNext() {
			if (dispatched == 2) return
			val sequence = (++dispatched).toLong()
			coordinator.acquireSettingsMutation(sequence) {
				val mutation = assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(it).mutation
				val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) { true }
				boundary.dispatch("void 0")
				boundary.acknowledge(sequence, 29, {}, ::dispatchNext)
			}
		}
		val failure = runCatching { dispatchNext() }.exceptionOrNull()
		assertTrue(failure == null, "release callback may freeze before any successor demand is consumed")
		assertTrue(fenced)
		assertEquals(1, dispatched)
		assertEquals(1, snapshots)
		drain(ownership)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain()))
		assertEquals(2, dispatched)
		assertEquals(2, snapshots)
		assertEquals(2, view.javascriptCalls)
		assertEquals(2, view.visualCalls)
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun task397HostAuthorityFreezeFencesJavascriptAndRestoresOnlyUnstartedDemand() {
		val ownership = ReaderForegroundWebViewOwnership()
		val mutation = acquire(ownership)
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication())
		var fenced = false
		val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) {
			if (!fenced) { fenced = true; ownership.freezeForTransitionActivation(domain()) }
			true
		}
		assertFalse(boundary.dispatch("void 0"))
		assertEquals(0, view.javascriptCalls)
		drain(ownership)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain()))
		assertEquals(1, view.javascriptCalls)
		assertFalse(boundary.dispatch("void 0"))
		mutation.cancel()
	}

	@Test
	fun task397AcknowledgementFreezeParksOnlyUnstartedVisualRegistration() {
		val ownership = ReaderForegroundWebViewOwnership()
		var snapshots = 0
		val mutation = acquire(ownership) { snapshots++ }
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication())
		var fence = false
		val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) {
			if (fence) { fence = false; ownership.freezeForTransitionActivation(domain()) }
			true
		}
		assertTrue(boundary.dispatch("void 0"))
		fence = true
		assertFalse(boundary.acknowledge(1L, 29, {}, {}))
		assertEquals(1, view.javascriptCalls)
		assertEquals(0, view.visualCalls)
		drain(ownership)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain()))
		assertEquals(1, view.visualCalls)
		view.deliverVisual()
		view.deliverVisual()
		assertEquals(1, snapshots)
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun task397SynchronousVisualResultKeepsOuterInvocationTailAcrossThrow() = visualTail(synchronous = true)

	@Test
	fun task397RetainedAsyncVisualResultKeepsAcceptedCallbackTailAcrossThrow() = visualTail(synchronous = false)

	private fun visualTail(synchronous: Boolean) {
		val ownership = ReaderForegroundWebViewOwnership()
		var snapshots = 0
		var events = 0
		var successors = 0
		var confirmations = 0
		val mutation = acquire(ownership) { snapshots++ }
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication()).apply { synchronousVisual = synchronous }
		val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) { true }
		assertTrue(boundary.dispatch("void 0"))
		val committed = {
			events++
			ownership.freezeForTransitionActivation(domain())
			val tails = ownership.snapshotFrozenOwnership()
			assertTrue(tails.size >= 2)
			tails.forEach { ownership.drainFrozenOwnership(it.physicalIdentity) { confirmations++ } }
			assertEquals(0, confirmations, "accepted visual callback has not returned")
			throw IllegalStateException("controlled event failure")
		}
		view.afterVisualResult = { assertEquals(0, confirmations, "synchronous outer registration has not returned") }
		val failure = if (synchronous) runCatching {
			boundary.acknowledge(1L, 29, committed, { successors++ })
		}.exceptionOrNull() else {
			assertTrue(boundary.acknowledge(1L, 29, committed, { successors++ }))
			assertEquals(0, snapshots)
			runCatching { view.deliverVisual() }.exceptionOrNull()
		}
		assertTrue(failure is IllegalStateException)
		assertTrue(confirmations >= 2)
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty())
		assertEquals(0, successors)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain()))
		view.deliverVisual()
		assertEquals(1, snapshots)
		assertEquals(1, events)
		assertEquals(1, successors)
		assertEquals(1, view.visualCalls)
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun task397ThrowingSnapshotWithoutFreezeFinishesOnlyRemainingSafeRecipients() {
		val ownership = ReaderForegroundWebViewOwnership()
		var snapshots = 0
		var events = 0
		var successors = 0
		val mutation = acquire(ownership) {
			snapshots++
			throw IllegalStateException("controlled snapshot failure")
		}
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication())
		val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) { true }
		boundary.dispatch("void 0")
		boundary.acknowledge(1L, 29, { events++ }, { successors++ })
		assertTrue(runCatching { view.deliverVisual() }.exceptionOrNull() is IllegalStateException)
		assertEquals(1, snapshots)
		assertEquals(1, events, "a throw alone must not strand unstarted recipient demand without a restore trigger")
		assertEquals(1, successors)
		assertEquals(0, ownership.snapshot().liveClaims)
		view.deliverVisual()
		assertEquals(1, snapshots)
		assertEquals(1, events)
		assertEquals(1, successors)
	}

	@Test
	fun task397SnapshotAndEventThrowsRefreezePreserveRemainingRecipientsExactlyOnce() {
		val ownership = ReaderForegroundWebViewOwnership()
		var snapshots = 0
		var events = 0
		var successors = 0
		val mutation = acquire(ownership) {
			snapshots++
			ownership.freezeForTransitionActivation(domain())
			throw IllegalStateException("controlled snapshot failure")
		}
		val view = PrimitiveWebView(RuntimeEnvironment.getApplication())
		val boundary = ReaderSettingsWebViewPhysicalBoundary(view, mutation) { true }
		boundary.dispatch("void 0")
		boundary.acknowledge(1L, 29, {
			events++
			ownership.freezeForTransitionActivation(domain(29L))
			throw IllegalStateException("controlled event failure")
		}, { successors++ })
		assertTrue(runCatching { view.deliverVisual() }.exceptionOrNull() is IllegalStateException)
		drain(ownership)
		assertTrue(runCatching { ownership.restoreAfterTransitionActivation(domain()) }.exceptionOrNull() is IllegalStateException)
		assertEquals(1, snapshots)
		assertEquals(1, events)
		assertEquals(0, successors)
		drain(ownership)
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain(29L)))
		assertEquals(1, snapshots)
		assertEquals(1, events)
		assertEquals(1, successors)
		assertEquals(0, ownership.snapshot().liveClaims)
	}
}
