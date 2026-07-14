package paige.navic.ui.screens.reader

import paige.navic.reader.readerAndroidFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageCurlGlRendererSourceTest {
	private val viewSource by lazy { readerAndroidFile("ReaderPageCurlGlView.android.kt").readText() }
	private val rendererSource by lazy { readerAndroidFile("ReaderPageCurlGlRenderer.android.kt").readText() }
	private val controllerSource by lazy { readerAndroidFile("ReaderPageTurnController.android.kt").readText() }
	private val diagnosticSource by lazy {
		readerAndroidFile("ReaderPageCurlDiagnosticTextureFactory.android.kt")
			.takeIf { it.isFile }
			?.readText()
			.orEmpty()
	}

	@Test
	fun rendererIsPersistentGles2AndDrawsOnlyWhenRequested() {
		assertContains(viewSource, "setEGLContextClientVersion(2)")
		assertContains(viewSource, "RENDERMODE_WHEN_DIRTY")
		assertContains(viewSource, "preserveEGLContextOnPause = true")
		assertContains(viewSource, "requestRender()")
		assertFalse(viewSource.contains("Bitmap.createBitmap"))
	}

	@Test
	fun rendererUsesPreallocatedBuffersAndDynamicTextures() {
		assertContains(rendererSource, "ByteBuffer.allocateDirect")
		assertContains(rendererSource, "GLUtils.texImage2D")
		assertContains(rendererSource, "GLES20.GL_TEXTURE_2D")
		assertContains(rendererSource, "ReaderPageCurlGeometry.forward")
		assertContains(rendererSource, "ReaderPageCurlGeometry.backward")
		assertFalse(rendererSource.contains("BitmapFactory"))
		assertFalse(rendererSource.contains("Bitmap.createBitmap"))
		assertFalse(rendererSource.contains("decodeResource"))
	}

	@Test
	fun rendererIsPassiveAndCannotNavigateFoliate() {
		val forbidden = listOf("goTo(", "renderer.next", "renderer.prev", "evaluateJavascript")
		forbidden.forEach { call ->
			assertFalse(rendererSource.contains(call), "OpenGL renderer must not navigate through $call")
			assertFalse(viewSource.contains(call), "OpenGL view must not navigate through $call")
		}
	}

	@Test
	fun incompleteTextureSetStaysTransparentAndDrawOrderIsStable() {
		assertContains(rendererSource, "if (!textureSet.isComplete) return")
		val draw = rendererSource.substringAfter("private fun drawTextureSet(")
		assertTrue(draw.indexOf("drawStationaryBase(") < draw.indexOf("drawActiveLeaf("))
		assertContains(draw, "drawStationaryCompanionLeaf(")
		assertContains(draw, "drawUnderneathActiveLeaf(")
		assertTrue(draw.indexOf("drawUnderneathActiveLeaf(") < draw.indexOf("drawActiveLeaf("))
	}

	@Test
	fun textureUploadsAreKeyedByTransitionIdentity() {
		assertContains(rendererSource, "uploadedIdentity")
		assertContains(rendererSource, "textureSet.identity")
		assertContains(rendererSource, "if (uploadedIdentity != textureSet.identity)")
	}

	@Test
	fun readerDiagnosticsCanReplaceCaptureInputWithFourDeterministicLeaves() {
		assertContains(controllerSource, "ReaderWebRuntime.isWebContentsDebuggingEnabled()")
		assertContains(viewSource, "diagnosticsEnabled: Boolean")
		assertContains(viewSource, "ReaderPageCurlDiagnosticTextureFactory.from")
		assertContains(diagnosticSource, "SOURCE LEFT")
		assertContains(diagnosticSource, "SOURCE RIGHT")
		assertContains(diagnosticSource, "DESTINATION LEFT")
		assertContains(diagnosticSource, "DESTINATION RIGHT")
	}
}
