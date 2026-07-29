package paige.navic.ui.screens.reader

internal class ReaderEngineRuntimeStartGate {
	private var started = false

	fun startIfVisible(visible: Boolean): Boolean {
		if (!visible || started) return false
		started = true
		return true
	}
}

internal class ReaderEngineRuntimeRecovery {
	private var runtimeLoadPending = false
	private var retryWhenVisible = false

	fun onRuntimeLoadStarted() {
		runtimeLoadPending = true
		retryWhenVisible = false
	}

	fun onRuntimeReady() {
		runtimeLoadPending = false
		retryWhenVisible = false
	}

	fun onWindowVisibilityChanged(visible: Boolean): Boolean {
		if (!visible) {
			if (runtimeLoadPending) retryWhenVisible = true
			return false
		}
		if (!runtimeLoadPending || !retryWhenVisible) return false
		retryWhenVisible = false
		return true
	}

	fun reset() {
		runtimeLoadPending = false
		retryWhenVisible = false
	}
}
