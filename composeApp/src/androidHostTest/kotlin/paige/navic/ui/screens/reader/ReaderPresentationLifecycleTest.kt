package paige.navic.ui.screens.reader

import android.content.ComponentCallbacks2
import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationMemoryPressureLevel

class ReaderPresentationLifecycleTest {
	@Test
	fun trimMemoryMapsEachRecognizedAndroidMeaningToOneTypedEvent() {
		val expected = mapOf(
			ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN to
				ReaderPresentationLifecycleEvent.VisibilityLost,
			ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE to
				ReaderPresentationLifecycleEvent.RunningMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Moderate
				),
			ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW to
				ReaderPresentationLifecycleEvent.RunningMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Low
				),
			ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL to
				ReaderPresentationLifecycleEvent.RunningMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Critical
				),
			ComponentCallbacks2.TRIM_MEMORY_BACKGROUND to
				ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Background
				),
			ComponentCallbacks2.TRIM_MEMORY_MODERATE to
				ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Moderate
				),
			ComponentCallbacks2.TRIM_MEMORY_COMPLETE to
				ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
					ReaderPresentationMemoryPressureLevel.Complete
				)
		)

		expected.forEach { (level, event) ->
			assertEquals(event, readerPresentationLifecycleEventForTrimMemory(level))
		}
		assertEquals(null, readerPresentationLifecycleEventForTrimMemory(0))
		assertEquals(null, readerPresentationLifecycleEventForTrimMemory(12))
	}

	@Test
	fun windowVisibilityMapsDirectlyToVisibilityLifecycle() {
		assertEquals(
			ReaderPresentationLifecycleEvent.VisibilityRestored,
			readerPresentationLifecycleEventForWindowVisibility(visible = true)
		)
		assertEquals(
			ReaderPresentationLifecycleEvent.VisibilityLost,
			readerPresentationLifecycleEventForWindowVisibility(visible = false)
		)
	}
}
