package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import paige.navic.reader.ReaderPageTurnPhysicalDirection

internal class ReaderPageCurlGlView(context: Context) : GLSurfaceView(context) {
	val pageRenderer = ReaderPageCurlGlRenderer()
	private var transitionActive = false

	init {
		setEGLContextClientVersion(2)
		setEGLConfigChooser(8, 8, 8, 8, 16, 0)
		holder.setFormat(PixelFormat.TRANSLUCENT)
		setZOrderOnTop(true)
		setRenderer(pageRenderer)
		renderMode = RENDERMODE_WHEN_DIRTY
		preserveEGLContextOnPause = true
		isClickable = false
		isFocusable = false
	}

	fun setTransition(
		transition: ReaderPageSlideTransition,
		direction: ReaderPageTurnPhysicalDirection,
		surfaceLeft: Int,
		surfaceTop: Int,
		diagnosticsEnabled: Boolean
	) {
		val capturedTextureSet = ReaderPageCurlTextureSet.from(
			transition = transition,
			direction = direction,
			surfaceLeft = surfaceLeft,
			surfaceTop = surfaceTop
		) ?: return
		val textureSet = if (diagnosticsEnabled) {
			ReaderPageCurlDiagnosticTextureFactory.from(capturedTextureSet)
		} else {
			capturedTextureSet
		}
		transitionActive = true
		queueEvent {
			pageRenderer.setTextureSet(textureSet)
			pageRenderer.setProgress(0f)
		}
		requestRender()
	}

	fun setProgress(progress: Float) {
		if (!transitionActive) return
		queueEvent { pageRenderer.setProgress(progress) }
		requestRender()
	}

	fun showFinalBase() {
		if (!transitionActive) return
		queueEvent { pageRenderer.showFinalBase() }
		requestRender()
	}

	fun clearTransition() {
		transitionActive = false
		queueEvent { pageRenderer.clearTextureSet() }
		requestRender()
	}
}
