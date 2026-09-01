package paige.navic.ui.screens.reader

import android.content.ComponentCallbacks2
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationMemoryPressureLevel

internal fun readerPresentationLifecycleEventForTrimMemory(
	level: Int
): ReaderPresentationLifecycleEvent? = when (level) {
	ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
		ReaderPresentationLifecycleEvent.VisibilityLost
	ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ->
		ReaderPresentationLifecycleEvent.RunningMemoryPressure(
			ReaderPresentationMemoryPressureLevel.Moderate
		)
	ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
		ReaderPresentationLifecycleEvent.RunningMemoryPressure(
			ReaderPresentationMemoryPressureLevel.Low
		)
	ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
		ReaderPresentationLifecycleEvent.RunningMemoryPressure(
			ReaderPresentationMemoryPressureLevel.Critical
		)
	ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ->
		ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
			ReaderPresentationMemoryPressureLevel.Background
		)
	ComponentCallbacks2.TRIM_MEMORY_MODERATE ->
		ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
			ReaderPresentationMemoryPressureLevel.Moderate
		)
	ComponentCallbacks2.TRIM_MEMORY_COMPLETE ->
		ReaderPresentationLifecycleEvent.BackgroundMemoryPressure(
			ReaderPresentationMemoryPressureLevel.Complete
		)
	else -> null
}

internal fun readerPresentationLifecycleEventForWindowVisibility(
	visible: Boolean
): ReaderPresentationLifecycleEvent = if (visible) {
	ReaderPresentationLifecycleEvent.VisibilityRestored
} else {
	ReaderPresentationLifecycleEvent.VisibilityLost
}
