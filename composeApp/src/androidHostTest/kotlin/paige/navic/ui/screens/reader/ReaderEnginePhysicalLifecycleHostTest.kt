package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderEnginePhysicalLifecycleHostTest {
	@Test
	fun destructionConfirmationFollowsExactOriginalPlatformDestroyOnce() {
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val engine = ReaderEngineWebView(activity.get())
		var confirmations = 0
		try {
			assertTrue(engine.bindPhysicalDestruction { returned ->
				assertSame(engine, returned)
				assertTrue(shadowOf(engine).wasDestroyCalled())
				confirmations += 1
			})
			assertEquals(0, confirmations)
			engine.destroy()
			engine.destroy()
			assertEquals(1, confirmations)
			assertFalse(engine.bindPhysicalDestruction { confirmations += 1 })
		} finally {
			engine.destroy()
			activity.pause().stop().destroy()
		}
	}

	@Test
	fun competingDestructionConsumerCannotReplaceOriginalConfirmation() {
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val engine = ReaderEngineWebView(activity.get())
		var original = 0
		var competing = 0
		try {
			val confirmation: (WebView) -> Unit = { original += 1 }
			assertTrue(engine.bindPhysicalDestruction(confirmation))
			assertTrue(engine.bindPhysicalDestruction(confirmation))
			assertFalse(engine.bindPhysicalDestruction { competing += 1 })
			engine.destroy()
			assertEquals(1, original)
			assertEquals(0, competing)
		} finally {
			engine.destroy()
			activity.pause().stop().destroy()
		}
	}

	@Test
	fun detachAndRuntimeRetirementDoNotConfirmPhysicalDestruction() {
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val host = FrozenForegroundHost(activity.get())
		val engine = ReaderEngineWebView(activity.get())
		var confirmations = 0
		try {
			activity.get().setContentView(host)
			host.addView(engine)
			assertTrue(engine.bindPhysicalDestruction { confirmations += 1 })
			host.removeView(engine)
			engine.clearCurrentDestinationQueryDelegate()
			assertEquals(1, host.detaches)
			assertEquals(0, confirmations)
			assertFalse(shadowOf(engine).wasDestroyCalled())
			engine.destroy()
			assertEquals(1, confirmations)
		} finally {
			host.removeView(engine)
			engine.destroy()
			activity.pause().stop().destroy()
		}
	}

	@Test
	fun installedVisibilityCompletesOriginalInitializationWithoutLegacyRecovery() {
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val host = FrozenForegroundHost(activity.get())
		val engine = ReaderEngineWebView(activity.get())
		var initialization = 0
		var legacy = 0
		try {
			activity.get().setContentView(host)
			host.addView(engine)
			engine.runtimeInitializationVisibilityListener = { visibility ->
				if (visibility == View.VISIBLE) {
					initialization += 1
					engine.completeRuntimeInitialization()
				}
			}
			engine.windowVisibilityListener = { legacy += 1 }
			engine.dispatchWindowVisibilityChanged(View.VISIBLE)
			assertEquals(1, initialization)
			assertEquals(1, host.initializations)
			assertEquals(0, legacy)
			engine.clearCurrentDestinationQueryDelegate()
			engine.dispatchWindowVisibilityChanged(View.VISIBLE)
			assertEquals(1, initialization)
			assertEquals(1, host.initializations)
			assertEquals(0, legacy)
		} finally {
			host.removeView(engine)
			engine.destroy()
			activity.pause().stop().destroy()
		}
	}

	private class FrozenForegroundHost(context: Context) : FrameLayout(context), ReaderForegroundWebViewAttachmentHost {
		var initializations = 0
		var detaches = 0
		override val foregroundLegacyConsequencesOpen = false
		private val source = object : ReaderForegroundWebViewSource {
			override val legacyConsequencesOpen = false
			override fun physicalSourceReturn(action: () -> Unit) {
				action()
				detaches += 1
			}
			override fun runtimeInitializationCompleted() { initializations += 1 }
		}
		override fun withForegroundWebViewAttachment(webView: WebView, attach: (ReaderForegroundWebViewSource?) -> Unit) {
			attach(source)
		}
	}
}
