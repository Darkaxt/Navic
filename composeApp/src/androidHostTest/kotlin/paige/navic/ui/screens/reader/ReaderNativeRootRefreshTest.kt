package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Duration
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
	shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class])
class ReaderNativeRootRefreshTest {
	@Test
	fun sameKeyRefreshPreservesPendingLiveWitnessAndPromotesSameMaterial() = runTest {
		verifyPendingRefresh(replace = false)
	}

	@Test
	fun differentKeyReplacementCannotPromoteOldLiveWitness() = runTest {
		verifyPendingRefresh(replace = true)
	}

	private suspend fun verifyPendingRefresh(replace: Boolean) {
		val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
		val rootType = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeFrameRoot")
		val root = rootType.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
			.apply { isAccessible = true }
			.newInstance(activity.get(), ReaderActivatedSessionSnapshotStore()) as View
		val viewer = rootType.getDeclaredField("viewerContainer").apply { isAccessible = true }.get(root)
		val viewerType = viewer.javaClass
		fun field(name: String): Any = viewerType.getDeclaredField(name).apply { isAccessible = true }.get(viewer)
		fun call(name: String, vararg args: Any?): Any? = viewerType.declaredMethods.single {
			it.name == name && it.parameterCount == args.size
		}.apply { isAccessible = true }.invoke(viewer, *args)
		val source = field("framePhysicalOwnership") as ReaderFramePhysicalOwnershipAdapter
		val reporter = field("presentationBindingReporter") as ReaderPresentationBindingReporter
		val lifecycle = field("presentationLifecycleDelivery") as ReaderPresentationLifecycleDelivery
		val key = ReaderViewerKey(ReaderViewerKind.WebViewPublication, "synthetic-root")
		val setContent = rootType.declaredMethods.single { it.name == "setViewerContent" }.apply { isAccessible = true }
		fun install(key: ReaderViewerKey) {
			val content: @Composable () -> Unit = {
				AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
					Class.forName("paige.navic.ui.screens.reader.ReaderEngineWebView")
						.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
						.newInstance(context) as WebView
				})
			}
			setContent.invoke(root, key, content)
		}
		val binding = ReaderPresentationBinding("synthetic-root", 2L, 3L, 4L,
			rasterGeneration = 5L, textureGeneration = 6L, preparationGeneration = 7L)
		val native = ReaderPresentationState(binding = binding,
			authority = ReaderPresentationAuthority.SettledNativePage(ReaderPresentationFrameOwner.NativePage(
				ReaderNativePagePresentationProof(binding, null, 8L, 800, 600, 5L, 6L))))
		var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = native))
		var bridge: ReaderPresentationHostBridge? = null
		try {
			activity.get().setContentView(root)
			install(key)
			root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
			root.layout(0, 0, 800, 600)
			shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48))
			val wrapper = rootType.getDeclaredField("currentViewerComposeView").apply { isAccessible = true }.get(root) as ComposeView
			val engine = assertIs<WebView>(call("currentLiveWebView"))
			assertTrue(wrapper.isAttachedToWindow && engine.isShown, "Real root composition must install the engine")
			val lease = viewerType.getDeclaredField("currentFrameInstallation").apply { isAccessible = true }.get(viewer)
				as ReaderFramePhysicalOwnershipAdapter.Lease
			val token = lease.token
			val predecessor = common.state.presentationDecision.frameOwner
			reporter.reset(1L, common.presentationVersion, common.state.presentation, false)
			assertTrue(reporter.bindPublication(binding))
			lifecycle.reset(common.presentationVersion, true, ReaderPresentationLifecycleState.Foreground)
			assertTrue(lifecycle.bindPublication(binding.publicationIdentity))
			var confirmations = 0
			var witnessMatchesEngine = false
			var physicalConfirmed: Boolean? = null
			val host = object : ReaderPresentationCommitHost {
				override val liveMaterialWitnessRequired = true
				override fun captureLiveFrameInstallation() = call("captureLiveFrameInstallation") as ReaderFramePhysicalOwnershipAdapter.LiveInstallation?
				override fun confirmLiveFramePresentation(installation: ReaderFramePhysicalOwnershipAdapter.LiveInstallation,
					witness: ReaderLiveEnginePhysicalWitness, owner: ReaderPresentationFrameOwner.LiveEngine): Boolean {
					confirmations++
					witnessMatchesEngine = witness.matches(engine)
					return (call("confirmLiveFramePresentation", installation, witness, owner) as Boolean)
						.also { physicalConfirmed = it }
				}
				override val isAttachedToWindow get() = root.isAttachedToWindow
				override val currentPresentationBinding = binding
				override val currentShellCoverGeneration: Long? = null
				override val shellCoverSelected = false
				override val measuredViewportWidth = 800
				override val measuredViewportHeight = 600
				override fun prepareOpaqueShellCover(coverGeneration: Long) = Unit
				override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
				override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
				override fun registerShellCoverDrawListener(onDraw: () -> Unit) = ReaderPresentationDrawRegistration {}
				override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
			}
			val events = mutableListOf<ReaderPresentationEvent>()
			val dispatcher = ReaderPresentationReceiptDispatcher(reporter, lifecycle, applyHostEffect = { effect ->
				bridge!!.update(effect.decision)
			})
			bridge = ReaderPresentationHostBridge(host,
				ReaderWebViewVisualHandoff(ReaderWebViewVisualHandoffHostAdapter(webViewProvider = { call("currentLiveWebView") as WebView? })),
				{ true }, transitionTimeoutScheduler = HostBridgeDeadlineScheduler(), transitionNowMillis = { 0L }) { event ->
				events += event
				dispatcher.dispatch(event) { dispatched ->
					common.onPresentationEvent(dispatched).also { common = it.controller }.presentationReceipt
				}
			}
			common = common.onPresentationEvent(ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine)).controller
			reporter.consumeComposeModel(common.presentationVersion, common.state.presentation, false)
			lifecycle.advanceComposeVersion(common.presentationVersion)
			assertIs<ReaderRequiredTransition.ExposeLiveEngine>(common.state.presentationDecision.requiredTransition)
			bridge.update(common.state.presentationDecision)
			assertTrue(events.isEmpty(), "Readiness must still be pending before the root refresh")
			install(if (replace) key.copy(identity = "synthetic-replacement") else key)
			if (!replace) {
				assertSame(wrapper, rootType.getDeclaredField("currentViewerComposeView").apply { isAccessible = true }.get(root))
				assertSame(engine, call("currentLiveWebView"))
				assertSame(lease, viewerType.getDeclaredField("currentFrameInstallation").apply { isAccessible = true }.get(viewer))
			}
			shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48))
			if (replace) {
				assertEquals(predecessor, common.state.presentationDecision.frameOwner)
				assertFalse(common.state.presentation.authority is ReaderPresentationAuthority.LiveEngineExposed)
				assertFalse(lease.state == ReaderLegacyResourceState.Visible)
				assertNull(wrapper.parent)
			} else {
				assertEquals(1, confirmations, "The genuine registered readiness callback must reach material confirmation")
				assertTrue(witnessMatchesEngine, "Readiness must witness the exact still-installed engine")
				assertIs<ReaderPresentationAuthority.LiveEngineExposed>(common.state.presentation.authority,
					"Same-key root refresh must not invalidate a genuine pending material witness; physicalConfirmed=$physicalConfirmed")
				assertEquals(ReaderLegacyResourceState.Visible, lease.state)
				assertEquals(token, lease.token)
				assertEquals(common.state.presentationDecision.frameOwner, lease.owner)
				assertEquals(1, events.count { it is ReaderPresentationEvent.LiveEngineExposureCommitted })
			}
		} finally {
			bridge?.dispose()
			val closed = (field("pageRasterPreparationController") as ReaderPageRasterPreparationController).destroy()
			rootType.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(root)
			shadowOf(Looper.getMainLooper()).idle()
			val disposal = runCatching {
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
			}.exceptionOrNull()
			if (disposal != null) {
				check(disposal is ReaderPageTeardownException &&
					generateSequence(disposal as Throwable?) { it.cause }.any {
						it.message == "GL surface is unavailable for terminal disposal"
					}) { "Unexpected root fixture disposal failure" }
			}
			check(closed.isCompleted)
			activity.pause().stop().destroy()
		}
	}
}
