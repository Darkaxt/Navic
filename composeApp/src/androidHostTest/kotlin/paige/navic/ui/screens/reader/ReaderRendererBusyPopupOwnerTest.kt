package paige.navic.ui.screens.reader

import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.ComponentActivity
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class ReaderRendererBusyPopupOwnerTest {
	@Test
	fun detachedPopupContentInheritsEveryAnchorViewTreeOwnerBeforeComposition() {
		val source = readerRendererBusyPopupSource()
		val construction = source
			.substringAfter("val contentView = ComposeView(anchor.context).apply {")
			.substringBefore("\n\t\tPopupWindow(")
		val inheritance = source.substringAfter(
			"internal fun inheritReaderRendererBusyPopupOwners("
		)

		assertContains(construction, "inheritReaderRendererBusyPopupOwners(anchor, this)")
		assertTrue(
			construction.indexOf("inheritReaderRendererBusyPopupOwners(anchor, this)") <
				construction.indexOf("setParentCompositionContext(parentComposition)")
		)
		assertTrue(
			construction.indexOf("inheritReaderRendererBusyPopupOwners(anchor, this)") <
				construction.indexOf("setContent {")
		)
		assertContains(
			inheritance,
			"contentView.setViewTreeLifecycleOwner(anchor.findViewTreeLifecycleOwner())"
		)
		assertContains(
			inheritance,
			"contentView.setViewTreeSavedStateRegistryOwner(anchor.findViewTreeSavedStateRegistryOwner())"
		)
		assertContains(
			inheritance,
			"contentView.setViewTreeViewModelStoreOwner(anchor.findViewTreeViewModelStoreOwner())"
		)
		assertContains(inheritance, "anchor.findViewTreeOnBackPressedDispatcherOwner()")
		assertContains(inheritance, "contentView::setViewTreeOnBackPressedDispatcherOwner")
	}
}

/** Original component composition, not normal ROOT/pointer/token acceptance. The only
 * controlled input is the actual MutableTransitionState passed to the production popup.
 * No replacement content, manual show, busy callback, readiness or timer is supplied. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderRendererBusyPopupCompositionHostTest {
	@Test
	fun targetChangeAfterInitialCommitShowsOriginalPopupWithoutRootRevision() {
		val fixture = OriginalPopupFixture(initiallyVisible = false)
		try {
			fixture.mount()
			assertTrue(fixture.initialSideEffectReached, "C408.PopupSubscription.InitialCommitReached")
			assertTrue(fixture.parentIdle, "C408.PopupSubscription.InitialParentIdle")
			assertFalse(fixture.hasFrameAwaiters, "C408.PopupSubscription.InitialNoFrameDemand")
			assertFalse(fixture.hasShowingPopup, "C408.PopupSubscription.InitialNoShowingPopup")
			assertTrue(!fixture.visibleState.currentState && !fixture.visibleState.targetState,
				"C408.PopupSubscription.InitialTransitionHidden")

			// This write happens AFTER the original popup's initial SideEffect returned.
			// The parent body never reads these properties and has no revision state.
			fixture.visibleState.targetState = true
			fixture.deliverOriginalStateFrames()
			assertTrue(fixture.visibleState.targetState, "C408.PopupSubscription.RealTargetChanged")
			assertTrue(fixture.hasShowingPopup,
				"C408.PopupSubscription.PostCommitTargetMustShowOriginalPopup")
			// Showing records invokePopup BEFORE Android's first queued traversal.
			// r36 reached showing but failed attachment; service only that original
			// window queue, independently of animation frames or a root revision.
			val original = fixture.showingOriginalPopup()
			fixture.serviceOriginalPopupAttachment(original)
			assertSame(original, fixture.assertOriginalAttachedPopup(),
				"C408.PopupSubscription.PostShowTraversalRetainsOriginalWindow")
		} finally { fixture.close() }
	}

	@Test
	fun postCommitPopupTraversalAttachesOriginalContentBeforeEntranceFramesComplete() {
		val fixture = OriginalPopupFixture(initiallyVisible = false)
		try {
			fixture.mount()
			fixture.visibleState.targetState = true
			fixture.deliverOriginalStateFrames()
			val original = fixture.showingOriginalPopup()
			val content = original.contentView
			assertFalse(content.isAttachedToWindow,
				"C408.PopupTraversal.StateFramesAloneDoNotAttachWindow")
			fixture.serviceOriginalPopupAttachment(original)
			assertSame(original, fixture.assertOriginalAttachedPopup(),
				"C408.PopupTraversal.NoReplacementWindow")
			assertSame(content, original.contentView, "C408.PopupTraversal.NoReplacementContent")
			assertTrue(fixture.visibleState.targetState && !fixture.visibleState.currentState &&
				!fixture.visibleState.isIdle, "C408.PopupTraversal.AttachmentIsNotEntranceCompletion")
			assertTrue(fixture.hasFrameAwaiters, "C408.PopupTraversal.AttachedOriginalEntranceDemandsFrames")
		} finally { fixture.close() }
	}

	@Test
	fun attachedOriginalPopupEntranceCompletesOnlyThroughBoundedOwnedFrameDemand() {
		val fixture = OriginalPopupFixture(initiallyVisible = false)
		try {
			fixture.mount()
			fixture.visibleState.targetState = true
			fixture.deliverOriginalStateFrames()
			val original = fixture.showingOriginalPopup()
			fixture.serviceOriginalPopupAttachment(original)
			fixture.assertOriginalAttachedPopup()
			assertTrue(fixture.hasFrameAwaiters && !fixture.visibleState.isIdle,
				"C408.PopupEntrance.OriginalDemandBeforeService")
			fixture.serviceOriginalEntranceFrames(original)
			assertTrue(fixture.visibleState.currentState && fixture.visibleState.targetState &&
				fixture.visibleState.isIdle, "C408.PopupEntrance.OriginalTransitionFullyVisible")
			assertTrue(fixture.parentIdle && !fixture.hasFrameAwaiters,
				"C408.PopupEntrance.FinalIdleAndNoFrameDemand")
			assertSame(original, fixture.assertOriginalAttachedPopup(),
				"C408.PopupEntrance.NoSyntheticDismissalOrReplacement")
		} finally { fixture.close() }
	}

	@Test
	fun initiallyVisibleOriginalPopupInheritsAttachedParentOwnersWithoutTakingInput() {
		val fixture = OriginalPopupFixture(initiallyVisible = true)
		try {
			fixture.mount()
			fixture.assertOriginalAttachedPopup()
		} finally { fixture.close() }
	}

	@Test
	fun disposingInitiallyVisibleOriginalPopupDismissesItsOriginalContent() {
		val fixture = OriginalPopupFixture(initiallyVisible = true)
		try {
			fixture.mount()
			val original = fixture.assertOriginalAttachedPopup()
			val content = original.contentView as ComposeView
			fixture.closeComposition()
			assertFalse(original.isShowing, "C408.PopupClose.OriginalWindowDismissed")
			assertFalse(content.hasComposition, "C408.PopupClose.OriginalContentDisposed")
		} finally { fixture.close() }
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private class OriginalPopupFixture(initiallyVisible: Boolean) {
		val visibleState = MutableTransitionState(initiallyVisible)
		private val activity = run {
			// Same original attached-window setup as NormalRootFixture; no platform
			// popup/composition/animation shadows are substituted.
			val key = "robolectric.areWindowsMarkedVisible"
			val old = System.getProperty(key)
			System.setProperty(key, "true")
			try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
			finally { if (old == null) System.clearProperty(key) else System.setProperty(key, old) }
		}
		private val scheduler = TestCoroutineScheduler()
		private val frameClock = BroadcastFrameClock()
		private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler) + frameClock)
		private val recomposer = Recomposer(scope.coroutineContext)
		private val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
		private val compose = ComposeView(activity.get()).apply {
			compositionContext = recomposer
			setParentCompositionContext(recomposer)
		}
		private var frame = 0L
		private var compositionClosed = false
		var initialSideEffectReached = false
			private set
		val parentIdle get() = recomposer.currentState.value == Recomposer.State.Idle
		val hasFrameAwaiters get() = frameClock.hasAwaiters
		val hasShowingPopup get() = recordedPopup()?.isShowing == true

		fun mount() {
			activity.get().setContentView(compose)
			compose.setContent {
				ReaderRendererBusyPopup(visibleState = visibleState, bottomOffset = 96.dp)
				SideEffect { initialSideEffectReached = true }
			}
			commitOriginalFrame()
			val decor = activity.get().window.decorView
			assertTrue(decor.isAttachedToWindow && decor.width > 0 && decor.height > 0,
				"C408.PopupFixture.OriginalTraversedDecor")
			assertTrue(compose.rootView === decor && compose.parent is ViewGroup,
				"C408.PopupFixture.OriginalAttachedParent")
			compose.requestLayout()
			decor.measure(View.MeasureSpec.makeMeasureSpec(decor.width, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(decor.height, View.MeasureSpec.EXACTLY))
			decor.layout(decor.left, decor.top, decor.right, decor.bottom)
			commitOriginalFrame()
			shadowOf(Looper.getMainLooper()).idle()
			commitOriginalFrame()
			assertTrue(compose.isAttachedToWindow && compose.hasComposition,
				"C408.PopupFixture.OriginalParentCompositionAttached")
		}

		/** Same finite three 16ms state-delivery frames as the ORIGINAL root profile.
		 * This is not the quarantined 120ms-animation synchronization: no until-idle
		 * loop, delay advancement, new root revision, extra layout or looper drain. */
		fun deliverOriginalStateFrames() {
			val originalDispatcherTime = scheduler.currentTime
			repeat(3) { commitOriginalFrame() }
			assertTrue(originalDispatcherTime == scheduler.currentTime,
				"C408.PopupSubscription.NoDispatcherTimeAdvanced")
		}

		private fun commitOriginalFrame(requireOwnedDemand: Boolean = false) {
			Snapshot.sendApplyNotifications()
			scheduler.runCurrent()
			if (requireOwnedDemand) assertTrue(frameClock.hasAwaiters,
				"C408.PopupEntrance.NoFrameWithoutOriginalDemand")
			frameClock.sendFrame(++frame * 16_000_000L)
			scheduler.runCurrent()
		}

		// Only original invokePopup recordings are read, never remembered slots or
		// closures. No record is NOT proof of callback/token nonentry.
		private fun recordedPopup(): PopupWindow? = shadowOf(activity.get().application).latestPopupWindow

		fun showingOriginalPopup(): PopupWindow {
			val popup = assertNotNull(recordedPopup(), "C408.PopupTraversal.OriginalInvokePopupRecorded")
			assertTrue(popup.isShowing, "C408.PopupTraversal.OriginalWindowAlreadyShowing")
			return popup
		}

		/** SDK 28 PopupWindow.invokePopup -> WindowManager.addView -> ViewRootImpl.setView
		 * schedules a traversal; performTraversals dispatches attached-to-window on its
		 * first pass. Robolectric 4.16.1 ShadowPopupWindow records before the direct
		 * invokePopup call. ShadowPausedLooper.idle executes only currently due work.
		 * No show/addView/attach/measure/content replacement is performed by this fixture. */
		fun serviceOriginalPopupAttachment(popup: PopupWindow) {
			assertSame(popup, showingOriginalPopup(), "C408.PopupTraversal.OriginalWindowBeforeService")
			val content = popup.contentView
			val originalDispatcherTime = scheduler.currentTime
			shadowOf(Looper.getMainLooper()).idle()
			Snapshot.sendApplyNotifications()
			scheduler.runCurrent()
			assertTrue(originalDispatcherTime == scheduler.currentTime,
				"C408.PopupTraversal.NoDispatcherTimeAdvanced")
			assertSame(popup, showingOriginalPopup(), "C408.PopupTraversal.OriginalWindowAfterService")
			assertSame(content, popup.contentView, "C408.PopupTraversal.OriginalContentAfterService")
			assertOriginalAttachedPopup()
		}

		/** Production enter is fadeIn(tween(120)); its static Canvas has no infinite
		 * animation. At 16ms: ceil(120/16)=8 elapsed ticks, one registration/apply and
		 * one start timestamp, then at most one completion-apply tick (11 total).
		 * Every tick requires this original parent clock's demand; no delay time moves. */
		fun serviceOriginalEntranceFrames(popup: PopupWindow) {
			val maximumDemandedFrames = (120 + 16 - 1) / 16 + 2 + 1
			val originalDispatcherTime = scheduler.currentTime
			val originalContent = popup.contentView
			var demandedFrames = 0
			while (frameClock.hasAwaiters && demandedFrames < maximumDemandedFrames) {
				assertSame(popup, assertOriginalAttachedPopup(), "C408.PopupEntrance.OriginalWindowRetained")
				assertSame(originalContent, popup.contentView, "C408.PopupEntrance.OriginalContentRetained")
				commitOriginalFrame(requireOwnedDemand = true)
				Snapshot.sendApplyNotifications()
				scheduler.runCurrent()
				demandedFrames++
			}
			assertTrue(demandedFrames > 0, "C408.PopupEntrance.OriginalDemandActuallyServiced")
			assertFalse(frameClock.hasAwaiters, "C408.PopupEntrance.DemandEndsWithinSourceTweenBound")
			assertTrue(originalDispatcherTime == scheduler.currentTime,
				"C408.PopupEntrance.NoDispatcherTimeAdvanced")
		}

		fun assertOriginalAttachedPopup(): PopupWindow {
			val popup = recordedPopup()
			assertTrue(popup != null && popup.isShowing, "C408.PopupOwner.OriginalWindowShowing")
			val original = checkNotNull(popup) { "C408.PopupOwner.OriginalWindowPresent" }
			val content = original.contentView
			assertTrue(content is ComposeView && content.context === compose.context &&
				content.isAttachedToWindow && content.hasComposition,
				"C408.PopupOwner.OriginalContentAttachedAndComposed")
			assertFalse(original.isFocusable || original.isTouchable || original.isOutsideTouchable,
				"C408.PopupOwner.NoInputTheft")
			assertTrue(original.inputMethodMode == PopupWindow.INPUT_METHOD_NOT_NEEDED,
				"C408.PopupOwner.NoInputMethod")
			assertTrue(compose.findViewTreeLifecycleOwner() != null &&
				content.findViewTreeLifecycleOwner() === compose.findViewTreeLifecycleOwner(),
				"C408.PopupOwner.OriginalLifecycleInherited")
			assertTrue(compose.findViewTreeSavedStateRegistryOwner() != null &&
				content.findViewTreeSavedStateRegistryOwner() === compose.findViewTreeSavedStateRegistryOwner(),
				"C408.PopupOwner.OriginalSavedStateInherited")
			assertTrue(compose.findViewTreeViewModelStoreOwner() != null &&
				content.findViewTreeViewModelStoreOwner() === compose.findViewTreeViewModelStoreOwner(),
				"C408.PopupOwner.OriginalViewModelStoreInherited")
			assertTrue(compose.findViewTreeOnBackPressedDispatcherOwner() != null &&
				content.findViewTreeOnBackPressedDispatcherOwner() === compose.findViewTreeOnBackPressedDispatcherOwner(),
				"C408.PopupOwner.OriginalBackDispatcherInherited")
			return original
		}

		fun closeComposition() {
			if (compositionClosed) return
			compositionClosed = true
			compose.disposeComposition()
			shadowOf(Looper.getMainLooper()).idle()
		}

		fun close() {
			try { closeComposition() }
			finally {
				try {
					activity.pause().stop().destroy()
					shadowOf(Looper.getMainLooper()).idle()
				} finally {
					compose.compositionContext = null
					recomposer.cancel()
					runner.cancel()
					scope.cancel()
					scheduler.runCurrent()
					assertTrue(recomposer.currentState.value == Recomposer.State.ShutDown && runner.isCompleted,
						"C408.PopupFixture.OriginalRecomposerClosed")
				}
			}
		}
	}
}

private fun readerRendererBusyPopupSource(): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderRendererBusyPopup.android.kt"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate ReaderRendererBusyPopup.android.kt")
}
