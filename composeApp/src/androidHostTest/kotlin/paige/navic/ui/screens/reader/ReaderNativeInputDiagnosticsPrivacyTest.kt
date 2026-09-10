package paige.navic.ui.screens.reader

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import paige.navic.util.core.Logger
import paige.navic.util.core.LoggerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReaderNativeInputDiagnosticsPrivacyTest {
	@After
	fun tearDown() {
		Logger.configureIssueLogSink(null)
	}

	@Test
	fun nativeSwipeAndDragDiagnosticsDoNotForwardCoordinateMarkersToSink() {
		val viewerClass = Class.forName(
			"paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer"
		)
		val viewer = viewerClass.getDeclaredConstructor(Context::class.java).run {
			isAccessible = true
			newInstance(ApplicationProvider.getApplicationContext<Context>()) as FrameLayout
		}
		val events = mutableListOf<LoggerEvent>()
		Logger.configureIssueLogSink(events::add)
		ShadowLog.clear()
		val readableDeltaX = -91_357.25f
		val readableDeltaY = 824.5f
		val shellDeltaX = -73_519.75f
		val shellDeltaY = 642.125f
		val candidateDeltaX = -64_208.125f
		val candidateDeltaY = 512.25f
		val previewDeltaX = -53_107.875f
		val previewDeltaY = 256.5f

		viewerClass.privateMethod("dispatchHorizontalSwipeViewerAction", Float::class.java, Float::class.java)
			.invoke(viewer, readableDeltaX, readableDeltaY)
		viewerClass.privateField("shellCoverVisible").setBoolean(viewer, true)
		viewerClass.privateMethod("dispatchHorizontalSwipeViewerAction", Float::class.java, Float::class.java)
			.invoke(viewer, shellDeltaX, shellDeltaY)
		viewerClass.privateField("shellCoverVisible").setBoolean(viewer, false)
		viewerClass.privateMethod("logReaderDragCandidate", Float::class.java, Float::class.java)
			.invoke(viewer, candidateDeltaX, candidateDeltaY)
		viewerClass.privateMethod("logReaderReadableDragPreview", Float::class.java, Float::class.java)
			.invoke(viewer, previewDeltaX, previewDeltaY)

		val logTag = "KomikkuReaderNativeFrameHost"
		val messages = events
			.filter { event -> event.tag == logTag }
			.map(LoggerEvent::message)
		val androidMessages = ShadowLog.getLogsForTag(logTag).map { item -> item.msg }
		assertEquals(messages, androidMessages)
		assertEquals(
			listOf(
				"Reader native readable swipe action=Right",
				"Reader shell cover swipe action=Right",
				"Reader shell cover command action=Right",
				"Reader native drag candidate",
				"Reader native drag preview"
			),
			messages
		)
		val coordinateMarkers = listOf(
			readableDeltaX,
			readableDeltaY,
			shellDeltaX,
			shellDeltaY,
			candidateDeltaX,
			candidateDeltaY,
			previewDeltaX,
			previewDeltaY
		).map(Float::toString)
		messages.forEach { message ->
			coordinateMarkers.forEach { marker -> assertFalse(message.contains(marker)) }
			assertFalse(message.contains(" dx="))
			assertFalse(message.contains(" dy="))
			assertFalse(message.contains(" threshold="))
		}
		assertTrue(messages.isNotEmpty())
	}
}

private fun Class<*>.privateField(name: String) =
	getDeclaredField(name).apply { isAccessible = true }

private fun Class<*>.privateMethod(name: String, vararg parameters: Class<*>) =
	getDeclaredMethod(name, *parameters).apply { isAccessible = true }
