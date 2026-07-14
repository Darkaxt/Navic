package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPageTurnPixelRect

internal class ReaderPageCurlGlRenderer : GLSurfaceView.Renderer {
	private val activeMesh = ReaderPageCurlGeometry.createReferenceMesh()
	private val meshPositionBuffer = floatBuffer(activeMesh.positions.size)
	private val meshTextureBuffer = floatBuffer(activeMesh.textureCoordinates.size)
	private val meshIndexBuffer = shortBuffer(activeMesh.indices)
	private val quadPositionBuffer = floatBuffer(QuadPositions)
	private val quadTextureBuffer = floatBuffer(QuadTextureCoordinates.size)
	private val quadIndexBuffer = shortBuffer(QuadIndices)
	private val textureNames = IntArray(2)
	private var textureSet: ReaderPageCurlTextureSet? = null
	private var uploadedIdentity: String? = null
	private var progress = 0f
	private var finalBase = false
	private var viewportWidth = 1
	private var viewportHeight = 1
	private var program = 0
	private var positionAttribute = 0
	private var textureAttribute = 0
	private var viewportUniform = 0
	private var rectUniform = 0
	private var depthUniform = 0
	private var alphaUniform = 0
	private var samplerUniform = 0

	fun setTextureSet(textureSet: ReaderPageCurlTextureSet) {
		this.textureSet = textureSet
		finalBase = false
		progress = 0f
	}

	fun setProgress(progress: Float) {
		this.progress = progress.coerceIn(0f, 1f)
		finalBase = false
	}

	fun showFinalBase() {
		finalBase = true
		progress = 1f
	}

	fun clearTextureSet() {
		textureSet = null
		progress = 0f
		finalBase = false
	}

	override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
		program = createProgram(VertexShader, FragmentShader)
		positionAttribute = GLES20.glGetAttribLocation(program, "aPosition")
		textureAttribute = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
		viewportUniform = GLES20.glGetUniformLocation(program, "uViewport")
		rectUniform = GLES20.glGetUniformLocation(program, "uRect")
		depthUniform = GLES20.glGetUniformLocation(program, "uDepthEnabled")
		alphaUniform = GLES20.glGetUniformLocation(program, "uAlpha")
		samplerUniform = GLES20.glGetUniformLocation(program, "uTexture")
		GLES20.glGenTextures(textureNames.size, textureNames, 0)
		configureTexture(textureNames[SourceTexture])
		configureTexture(textureNames[DestinationTexture])
		uploadedIdentity = null
		GLES20.glDisable(GLES20.GL_DEPTH_TEST)
		GLES20.glEnable(GLES20.GL_BLEND)
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
		GLES20.glClearColor(0f, 0f, 0f, 0f)
	}

	override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
		viewportWidth = width.coerceAtLeast(1)
		viewportHeight = height.coerceAtLeast(1)
		GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
	}

	override fun onDrawFrame(gl: GL10?) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
		val textureSet = textureSet ?: return
		if (!textureSet.isComplete) return
		if (uploadedIdentity != textureSet.identity) uploadTextureSet(textureSet)
		if (uploadedIdentity != textureSet.identity) return
		drawTextureSet(textureSet)
	}

	private fun drawTextureSet(textureSet: ReaderPageCurlTextureSet) {
		if (finalBase || progress >= 1f) {
			drawStationaryBase(textureSet, DestinationTexture)
			return
		}
		drawStationaryBase(
			textureSet,
			if (textureSet.direction == ReaderPageTurnPhysicalDirection.TowardLeft) DestinationTexture else SourceTexture
		)
		drawStationaryCompanionLeaf(textureSet)
		drawUnderneathActiveLeaf(textureSet)
		drawActiveLeaf(textureSet)
	}

	private fun drawStationaryBase(textureSet: ReaderPageCurlTextureSet, textureIndex: Int) {
		drawQuad(
			textureIndex = textureIndex,
			displayLeft = textureSet.surfaceLeft,
			displayTop = textureSet.surfaceTop,
			displayWidth = textureSet.surfaceWidth,
			displayHeight = textureSet.surfaceHeight,
			bitmapLeft = 0,
			bitmapTop = 0,
			bitmapRight = textureSet.bitmapWidth,
			bitmapBottom = textureSet.bitmapHeight,
			bitmapWidth = textureSet.bitmapWidth,
			bitmapHeight = textureSet.bitmapHeight
		)
	}

	private fun drawStationaryCompanionLeaf(textureSet: ReaderPageCurlTextureSet) {
		val companion = textureSet.companionLeafRect ?: return
		drawBitmapLeaf(textureSet, SourceTexture, companion)
	}

	private fun drawUnderneathActiveLeaf(textureSet: ReaderPageCurlTextureSet) {
		val textureIndex = if (textureSet.direction == ReaderPageTurnPhysicalDirection.TowardLeft) {
			DestinationTexture
		} else {
			SourceTexture
		}
		drawBitmapLeaf(textureSet, textureIndex, textureSet.activeLeafRect)
	}

	private fun drawActiveLeaf(textureSet: ReaderPageCurlTextureSet) {
		val textureIndex = if (textureSet.direction == ReaderPageTurnPhysicalDirection.TowardLeft) {
			ReaderPageCurlGeometry.forward(progress, activeMesh)
			SourceTexture
		} else {
			ReaderPageCurlGeometry.backward(progress, activeMesh)
			DestinationTexture
		}
		meshPositionBuffer.clear()
		meshPositionBuffer.put(activeMesh.positions).position(0)
		updateMeshTextureCoordinates(textureSet.activeLeafRect, textureSet.bitmapWidth, textureSet.bitmapHeight)
		val activeLeaf = textureSet.activeLeafRect
		draw(
			textureIndex = textureIndex,
			positionBuffer = meshPositionBuffer,
			textureBuffer = meshTextureBuffer,
			indexBuffer = meshIndexBuffer,
			indexCount = activeMesh.indices.size,
			displayLeft = textureSet.surfaceLeft + activeLeaf.left * textureSet.scaleX,
			displayTop = textureSet.surfaceTop + activeLeaf.top * textureSet.scaleY,
			displayWidth = activeLeaf.width * textureSet.scaleX,
			displayHeight = activeLeaf.height * textureSet.scaleY,
			depthEnabled = true
		)
	}

	private fun drawBitmapLeaf(
		textureSet: ReaderPageCurlTextureSet,
		textureIndex: Int,
		bitmapRect: ReaderPageTurnPixelRect
	) {
		drawQuad(
			textureIndex = textureIndex,
			displayLeft = textureSet.surfaceLeft + bitmapRect.left * textureSet.scaleX,
			displayTop = textureSet.surfaceTop + bitmapRect.top * textureSet.scaleY,
			displayWidth = bitmapRect.width * textureSet.scaleX,
			displayHeight = bitmapRect.height * textureSet.scaleY,
			bitmapLeft = bitmapRect.left,
			bitmapTop = bitmapRect.top,
			bitmapRight = bitmapRect.right,
			bitmapBottom = bitmapRect.bottom,
			bitmapWidth = textureSet.bitmapWidth,
			bitmapHeight = textureSet.bitmapHeight
		)
	}

	private fun drawQuad(
		textureIndex: Int,
		displayLeft: Float,
		displayTop: Float,
		displayWidth: Float,
		displayHeight: Float,
		bitmapLeft: Int,
		bitmapTop: Int,
		bitmapRight: Int,
		bitmapBottom: Int,
		bitmapWidth: Int,
		bitmapHeight: Int
	) {
		updateQuadTextureCoordinates(
			left = bitmapLeft,
			top = bitmapTop,
			right = bitmapRight,
			bottom = bitmapBottom,
			width = bitmapWidth,
			height = bitmapHeight
		)
		draw(
			textureIndex = textureIndex,
			positionBuffer = quadPositionBuffer,
			textureBuffer = quadTextureBuffer,
			indexBuffer = quadIndexBuffer,
			indexCount = QuadIndices.size,
			displayLeft = displayLeft,
			displayTop = displayTop,
			displayWidth = displayWidth,
			displayHeight = displayHeight,
			depthEnabled = false
		)
	}

	private fun draw(
		textureIndex: Int,
		positionBuffer: FloatBuffer,
		textureBuffer: FloatBuffer,
		indexBuffer: ShortBuffer,
		indexCount: Int,
		displayLeft: Float,
		displayTop: Float,
		displayWidth: Float,
		displayHeight: Float,
		depthEnabled: Boolean
	) {
		if (displayWidth <= 0f || displayHeight <= 0f) return
		GLES20.glUseProgram(program)
		GLES20.glUniform2f(viewportUniform, viewportWidth.toFloat(), viewportHeight.toFloat())
		GLES20.glUniform4f(rectUniform, displayLeft, displayTop, displayWidth, displayHeight)
		GLES20.glUniform1f(depthUniform, if (depthEnabled) 1f else 0f)
		GLES20.glUniform1f(alphaUniform, 1f)
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureNames[textureIndex])
		GLES20.glUniform1i(samplerUniform, 0)
		positionBuffer.position(0)
		GLES20.glEnableVertexAttribArray(positionAttribute)
		GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, positionBuffer)
		textureBuffer.position(0)
		GLES20.glEnableVertexAttribArray(textureAttribute)
		GLES20.glVertexAttribPointer(textureAttribute, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
		indexBuffer.position(0)
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
	}

	private fun uploadTextureSet(textureSet: ReaderPageCurlTextureSet) {
		if (!textureSet.isComplete) return
		uploadTexture(textureNames[SourceTexture], textureSet.sourceBitmap)
		uploadTexture(textureNames[DestinationTexture], textureSet.destinationBitmap)
		uploadedIdentity = textureSet.identity
	}

	private fun uploadTexture(textureName: Int, bitmap: Bitmap) {
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName)
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
	}

	private fun configureTexture(textureName: Int) {
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName)
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
	}

	private fun updateQuadTextureCoordinates(
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		width: Int,
		height: Int
	) {
		quadTextureBuffer.clear()
		putTextureCoordinates(
			target = quadTextureBuffer,
			left = left,
			top = top,
			right = right,
			bottom = bottom,
			width = width,
			height = height,
			base = QuadTextureCoordinates
		)
		quadTextureBuffer.position(0)
	}

	private fun updateMeshTextureCoordinates(rect: ReaderPageTurnPixelRect, width: Int, height: Int) {
		meshTextureBuffer.clear()
		putTextureCoordinates(
			target = meshTextureBuffer,
			left = rect.left,
			top = rect.top,
			right = rect.right,
			bottom = rect.bottom,
			width = width,
			height = height,
			base = activeMesh.textureCoordinates
		)
		meshTextureBuffer.position(0)
	}

	private fun putTextureCoordinates(
		target: FloatBuffer,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		width: Int,
		height: Int,
		base: FloatArray
	) {
		val textureLeft = left / width.toFloat()
		val textureRight = right / width.toFloat()
		val textureBottom = 1f - bottom / height.toFloat()
		val textureTop = 1f - top / height.toFloat()
		for (index in base.indices step 2) {
			target.put(textureLeft + base[index] * (textureRight - textureLeft))
			target.put(textureBottom + base[index + 1] * (textureTop - textureBottom))
		}
	}

	private fun createProgram(vertexSource: String, fragmentSource: String): Int {
		val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
		val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
		return GLES20.glCreateProgram().also { result ->
			GLES20.glAttachShader(result, vertexShader)
			GLES20.glAttachShader(result, fragmentShader)
			GLES20.glLinkProgram(result)
			val linked = IntArray(1)
			GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0)
			check(linked[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(result) }
			GLES20.glDeleteShader(vertexShader)
			GLES20.glDeleteShader(fragmentShader)
		}
	}

	private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
		GLES20.glShaderSource(shader, source)
		GLES20.glCompileShader(shader)
		val compiled = IntArray(1)
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
		check(compiled[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
	}

	private companion object {
		const val SourceTexture = 0
		const val DestinationTexture = 1
		val QuadPositions = floatArrayOf(
			0f, 0f, 0f,
			1f, 0f, 0f,
			0f, 1f, 0f,
			1f, 1f, 0f
		)
		val QuadTextureCoordinates = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
		val QuadIndices = shortArrayOf(0, 1, 2, 1, 3, 2)

		const val VertexShader = """
			attribute vec3 aPosition;
			attribute vec2 aTextureCoordinate;
			uniform vec2 uViewport;
			uniform vec4 uRect;
			uniform float uDepthEnabled;
			varying vec2 vTextureCoordinate;
			varying float vShade;
			void main() {
				float depth = max(aPosition.z, 0.0) * uDepthEnabled;
				float perspective = 1.0 / (1.0 + depth * 0.9);
				vec2 local = vec2(
					(aPosition.x - 0.5) * perspective + 0.5,
					(aPosition.y - 0.5) * perspective + 0.5
				);
				vec2 pixel = uRect.xy + local * uRect.zw;
				vec2 clip = vec2(
					(pixel.x / uViewport.x) * 2.0 - 1.0,
					1.0 - (pixel.y / uViewport.y) * 2.0
				);
				gl_Position = vec4(clip, 0.0, 1.0);
				vTextureCoordinate = aTextureCoordinate;
				vShade = min(depth * 1.8, 0.24);
			}
		"""

		const val FragmentShader = """
			precision mediump float;
			uniform sampler2D uTexture;
			uniform float uAlpha;
			varying vec2 vTextureCoordinate;
			varying float vShade;
			void main() {
				vec4 color = texture2D(uTexture, vTextureCoordinate);
				gl_FragColor = vec4(color.rgb * (1.0 - vShade), color.a * uAlpha);
			}
		"""

		fun floatBuffer(size: Int): FloatBuffer = ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer()

		fun floatBuffer(values: FloatArray): FloatBuffer = floatBuffer(values.size).apply {
			put(values)
			position(0)
		}

		fun shortBuffer(values: ShortArray): ShortBuffer = ByteBuffer.allocateDirect(values.size * Short.SIZE_BYTES)
			.order(ByteOrder.nativeOrder())
			.asShortBuffer()
			.apply {
				put(values)
				position(0)
			}
	}
}
