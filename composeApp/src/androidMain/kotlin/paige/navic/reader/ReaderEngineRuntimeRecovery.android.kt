package paige.navic.ui.screens.reader

internal class ReaderEngineRuntimeStartGate {
	private var started = false

	fun startIfVisible(visible: Boolean): Boolean {
		if (!visible || started) return false
		started = true
		return true
	}
}

internal const val ReaderRendererLossLimit = 3

internal class ReaderEngineRuntimeRecovery {
	private var runtimeLoadPending = false
	private var retryWhenVisible = false
	private var rendererLossesSinceReady = 0

	/** Returns true when renderer losses repeat without a ready runtime, i.e. in-place recovery failed. */
	fun onRendererLost(): Boolean {
		rendererLossesSinceReady += 1
		return rendererLossesSinceReady >= ReaderRendererLossLimit
	}

	fun onRuntimeLoadStarted() {
		runtimeLoadPending = true
		retryWhenVisible = false
	}

	fun onRuntimeReady() {
		runtimeLoadPending = false
		retryWhenVisible = false
		rendererLossesSinceReady = 0
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
