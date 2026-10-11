package paige.navic.ui.components.common

import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import paige.navic.shared.PlaybackPreparationCoordinator
import paige.navic.shared.PlaybackStartFeedback
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class PlaybackStartDialogTest {
	private val motionScale = object : MotionDurationScale {
		var value by mutableFloatStateOf(1f)
		override val scaleFactor: Float get() = value
	}
	@get:Rule val compose = createEmptyComposeRule(effectContext = motionScale)

	@Test
	fun disabledAnimationsStillPresentTheOverlayAndAllowBackCancellation() {
		motionScale.value = 0f
		verifyOverlay("phone-disabled-animations", dark = false, fontScale = 1f)
	}

	@Test
	fun phoneOverlayBlocksOutsideTapsAndBackCancelsOwningPreparation() {
		verifyOverlay("phone-light", dark = false, fontScale = 1f)
	}

	@Test
	fun backDuringRevealFinishesTheFadeWithoutKeepingPreparationAlive() {
		verifyOverlay("phone-cancel-during-reveal", dark = false, fontScale = 1f, cancelDuringReveal = true)
	}

	@Test
	@Config(qualifiers = "w1280dp-h800dp-land-mdpi")
	fun tabletLandscapeKeepsLargeTextInsideTheVinyl() {
		verifyOverlay("tablet-dark-large-text", dark = true, fontScale = 2f)
	}

	@Test
	@Config(qualifiers = "w640dp-h320dp-land-mdpi")
	fun compactLandscapeKeepsLargeTextInsideTheVinyl() {
		verifyOverlay("phone-landscape-large-text", dark = false, fontScale = 2f)
	}

	private fun verifyOverlay(name: String, dark: Boolean, fontScale: Float, cancelDuringReveal: Boolean = false) {
		compose.mainClock.autoAdvance = !cancelDuringReveal
		val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
		val feedback = PlaybackStartFeedback()
		val preparation = PlaybackPreparationCoordinator(scope, feedback)
		var backgroundClicks = 0
		var backgroundBacks = 0
		preparation.launch(bulk = true) { awaitCancellation() }
		try {
			activity.get().setContent {
				val density = LocalDensity.current
				CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
					MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
						BackHandler { backgroundBacks++ }
						Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
							Button(onClick = { backgroundClicks++ }, modifier = Modifier.testTag("background-play")) {
								Text("Play")
							}
							PlaybackStartDialog(feedback, onCancel = { preparation.cancelFromUser {} })
						}
					}
				}
			}
			if (cancelDuringReveal) compose.mainClock.advanceTimeBy(64L)
			compose.waitForIdle()
			compose.onNode(isDialog()).assertIsDisplayed()
			compose.onNodeWithText("Cancel").assertDoesNotExist()
			compose.onNodeWithText("Close").assertDoesNotExist()
			val image = compose.onNode(isDialog()).captureToImage()
			val bitmap = image.asAndroidBitmap()
			val directory = File(System.getProperty("navic.overlay.captures",
				"build/test-results/testAndroidHostTest/overlay-captures"))
			directory.mkdirs()
			File(directory, "$name.png").outputStream().use {
				bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
			}
			val record = compose.onNodeWithTag("playback-preparation-vinyl").fetchSemanticsNode().boundsInRoot
			val label = compose.onNodeWithTag("playback-preparation-message").fetchSemanticsNode().boundsInRoot
			assertTrue(record.contains(label.topLeft) && record.contains(label.bottomRight), "$name: label escaped vinyl")
			assertEquals(record.width, record.height, "$name: record must remain circular")
			val textLayouts = mutableListOf<TextLayoutResult>()
			compose.onNodeWithTag("playback-preparation-message")
				.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
			val layout = textLayouts.single()
			assertFalse(layout.hasVisualOverflow,
				"$name: status text clipped; record=$record label=$label text=${layout.size} lines=${layout.lineCount}")
			val dialog = assertNotNull(ShadowDialog.getLatestDialog()) as ComponentDialog
			val attributes = assertNotNull(dialog.window).attributes
			assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
			assertTrue(attributes.dimAmount > 0f && attributes.dimAmount < 1f)
			assertEquals(0, attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
			assertEquals(0, attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
			val outside = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
			try { dialog.dispatchTouchEvent(outside) } finally { outside.recycle() }
			assertTrue(feedback.isPending)
			assertEquals(0, backgroundClicks)
			assertTrue(image.width > 0 && image.height > 0)
			val colors = mutableSetOf<Int>()
			for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
				colors += bitmap.getPixel(x, y)
			}
			assertTrue(colors.size > 5, "$name: rendered overlay is blank")
			if (!cancelDuringReveal) {
				feedback.starting()
				compose.waitForIdle()
				compose.onNodeWithText("Starting playback...").assertIsDisplayed()
			}
			dialog.onBackPressedDispatcher.onBackPressed()
			shadowOf(Looper.getMainLooper()).idle()
			compose.waitForIdle()
			assertFalse(feedback.isPending)
			assertEquals(0, backgroundBacks, "Modal Back must not navigate the screen underneath")
			if (cancelDuringReveal) {
				compose.onNode(isDialog()).assertIsDisplayed()
				compose.mainClock.advanceTimeBy(300L)
				compose.waitForIdle()
			}
			compose.onNode(isDialog()).assertDoesNotExist()
		} finally {
			preparation.cancel()
			scope.cancel()
			activity.pause().stop().destroy()
		}
	}
}
