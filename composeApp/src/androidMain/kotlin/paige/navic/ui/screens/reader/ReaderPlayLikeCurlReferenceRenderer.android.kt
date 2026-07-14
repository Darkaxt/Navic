package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** GLES2 plumbing around PlayLikeCurl's original three-page model. */
internal class ReaderPlayLikeCurlReferenceRenderer(
	private val context: Context,
	private val model: ReaderPlayLikeCurlReferenceModel
) : GLSurfaceView.Renderer {
	private val leftPage = GpuPage(model.leftPage)
	private val frontPage = GpuPage(model.frontPage)
	private val rightPage = GpuPage(model.rightPage)

	private val projectionMatrix = FloatArray(16)
	private val modelMatrix = FloatArray(16)
	private val mvpMatrix = FloatArray(16)

	private var program = 0
	private var positionAttribute = 0
	private var textureCoordinateAttribute = 0
	private var matrixUniform = 0
	private var textureUniform = 0
	private var orientation = ReaderPlayLikeCurlOrientation.Portrait

	override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
		program = createProgram(VertexShader, FragmentShader)
		positionAttribute = GLES20.glGetAttribLocation(program, "aPosition")
		textureCoordinateAttribute = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
		matrixUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix")
		textureUniform = GLES20.glGetUniformLocation(program, "uTexture")

		GLES20.glClearColor(0f, 0f, 0f, 0.5f)
		GLES20.glClearDepthf(1f)
		GLES20.glEnable(GLES20.GL_DEPTH_TEST)
		GLES20.glDepthFunc(GLES20.GL_LEQUAL)

		leftPage.initializeGl()
		frontPage.initializeGl()
		rightPage.initializeGl()
	}

	override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
		val safeHeight = height.coerceAtLeast(1)
		GLES20.glViewport(0, 0, width, safeHeight)
		orientation = if (safeHeight > width) {
			ReaderPlayLikeCurlOrientation.Portrait
		} else {
			ReaderPlayLikeCurlOrientation.Landscape
		}
		Matrix.perspectiveM(
			projectionMatrix,
			0,
			45f,
			ReaderPlayLikeCurlReferenceGeometry.projectionAspect(width, safeHeight),
			0.1f,
			100f
		)
		Matrix.setIdentityM(modelMatrix, 0)
		Matrix.translateM(modelMatrix, 0, 0f, 0f, -2f)
		Matrix.translateM(modelMatrix, 0, -0.5f, -0.5f, 0f)
		Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

		leftPage.invalidateAsset()
		frontPage.invalidateAsset()
		rightPage.invalidateAsset()
	}

	override fun onDrawFrame(gl: GL10?) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
		GLES20.glUseProgram(program)
		GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0)
		GLES20.glUniform1i(textureUniform, 0)

		drawPage(leftPage, model.leftPage)
		drawPage(frontPage, model.frontPage)
		drawPage(rightPage, model.rightPage)
	}

	private fun drawPage(page: GpuPage, state: ReaderPlayLikeCurlPageState) {
		page.ensureAsset(state.pageIndex, orientation)
		ReaderPlayLikeCurlReferenceGeometry.update(
			page = page.geometry,
			curlPosition = state.curlPosition,
			active = state.role.isActive(model.activePage)
		)
		page.uploadPositions()

		GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, page.textureId)
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, page.positionBufferId)
		GLES20.glEnableVertexAttribArray(positionAttribute)
		GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0)
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, page.textureBufferId)
		GLES20.glEnableVertexAttribArray(textureCoordinateAttribute)
		GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0)
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, page.indexBufferId)
		GLES20.glDrawElements(
			GLES20.GL_TRIANGLES,
			page.geometry.indices.size,
			GLES20.GL_UNSIGNED_SHORT,
			0
		)
		GLES20.glDisableVertexAttribArray(positionAttribute)
		GLES20.glDisableVertexAttribArray(textureCoordinateAttribute)
	}

	private inner class GpuPage(
		private val state: ReaderPlayLikeCurlPageState
	) {
		var geometry = ReaderPlayLikeCurlReferenceGeometry.createPage(
			role = state.role,
			bitmapWidth = 1,
			bitmapHeight = 1,
			orientation = ReaderPlayLikeCurlOrientation.Portrait
		)
			private set

		private val positionBuffer = directFloatBuffer(geometry.positions.size)
		private val textureBuffer = directFloatBuffer(geometry.textureCoordinates.size)
		private val indexBuffer = directShortBuffer(geometry.indices.size)
		private val bufferIds = IntArray(3)
		private val textureIds = IntArray(1)

		var positionBufferId = 0
			private set
		var textureBufferId = 0
			private set
		var indexBufferId = 0
			private set
		var textureId = 0
			private set

		private var uploadedPageIndex = -1
		private var uploadedOrientation: ReaderPlayLikeCurlOrientation? = null

		fun initializeGl() {
			GLES20.glGenBuffers(bufferIds.size, bufferIds, 0)
			positionBufferId = bufferIds[0]
			textureBufferId = bufferIds[1]
			indexBufferId = bufferIds[2]
			GLES20.glGenTextures(1, textureIds, 0)
			textureId = textureIds[0]

			textureBuffer.clear()
			textureBuffer.put(geometry.textureCoordinates).position(0)
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureBufferId)
			GLES20.glBufferData(
				GLES20.GL_ARRAY_BUFFER,
				geometry.textureCoordinates.size * Float.SIZE_BYTES,
				textureBuffer,
				GLES20.GL_STATIC_DRAW
			)

			indexBuffer.clear()
			indexBuffer.put(geometry.indices).position(0)
			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
			GLES20.glBufferData(
				GLES20.GL_ELEMENT_ARRAY_BUFFER,
				geometry.indices.size * Short.SIZE_BYTES,
				indexBuffer,
				GLES20.GL_STATIC_DRAW
			)

			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId)
			GLES20.glBufferData(
				GLES20.GL_ARRAY_BUFFER,
				geometry.positions.size * Float.SIZE_BYTES,
				null,
				GLES20.GL_DYNAMIC_DRAW
			)
			invalidateAsset()
		}

		fun invalidateAsset() {
			uploadedPageIndex = -1
			uploadedOrientation = null
		}

		fun ensureAsset(pageIndex: Int, requestedOrientation: ReaderPlayLikeCurlOrientation) {
			if (uploadedPageIndex == pageIndex && uploadedOrientation == requestedOrientation) return
			val assetPath = "playlikecurl-reference/${requestedOrientation.assetDirectory}/page${pageIndex + 1}.png"
			val bitmap = context.assets.open(assetPath).use(BitmapFactory::decodeStream)
				?: error("Could not decode PlayLikeCurl reference asset $assetPath")

			geometry = ReaderPlayLikeCurlReferenceGeometry.createPage(
				role = state.role,
				bitmapWidth = bitmap.width,
				bitmapHeight = bitmap.height,
				orientation = requestedOrientation
			)
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
			bitmap.recycle()
			uploadedPageIndex = pageIndex
			uploadedOrientation = requestedOrientation
		}

		fun uploadPositions() {
			positionBuffer.clear()
			positionBuffer.put(geometry.positions).position(0)
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId)
			GLES20.glBufferSubData(
				GLES20.GL_ARRAY_BUFFER,
				0,
				geometry.positions.size * Float.SIZE_BYTES,
				positionBuffer
			)
		}
	}

	private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
		val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
		val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
		return GLES20.glCreateProgram().also { createdProgram ->
			GLES20.glAttachShader(createdProgram, vertexShader)
			GLES20.glAttachShader(createdProgram, fragmentShader)
			GLES20.glLinkProgram(createdProgram)
			val linkStatus = IntArray(1)
			GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
			check(linkStatus[0] == GLES20.GL_TRUE) {
				"Could not link PlayLikeCurl GLES2 program: ${GLES20.glGetProgramInfoLog(createdProgram)}"
			}
			GLES20.glDeleteShader(vertexShader)
			GLES20.glDeleteShader(fragmentShader)
		}
	}

	private fun compileShader(type: Int, source: String): Int =
		GLES20.glCreateShader(type).also { shader ->
			GLES20.glShaderSource(shader, source)
			GLES20.glCompileShader(shader)
			val compileStatus = IntArray(1)
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
			check(compileStatus[0] == GLES20.GL_TRUE) {
				"Could not compile PlayLikeCurl GLES2 shader: ${GLES20.glGetShaderInfoLog(shader)}"
			}
		}

	private fun directFloatBuffer(size: Int): FloatBuffer = ByteBuffer
		.allocateDirect(size * Float.SIZE_BYTES)
		.order(ByteOrder.nativeOrder())
		.asFloatBuffer()

	private fun directShortBuffer(size: Int): ShortBuffer = ByteBuffer
		.allocateDirect(size * Short.SIZE_BYTES)
		.order(ByteOrder.nativeOrder())
		.asShortBuffer()

	private val ReaderPlayLikeCurlOrientation.assetDirectory: String
		get() = name.lowercase()

	private fun ReaderPlayLikeCurlPageRole.isActive(activePage: ReaderPlayLikeCurlActivePage): Boolean =
		when (this) {
			ReaderPlayLikeCurlPageRole.Left -> activePage == ReaderPlayLikeCurlActivePage.Left
			ReaderPlayLikeCurlPageRole.Front -> activePage == ReaderPlayLikeCurlActivePage.Current
			ReaderPlayLikeCurlPageRole.Right -> activePage == ReaderPlayLikeCurlActivePage.Right
		}

	private companion object {
		const val VertexShader = """
			uniform mat4 uMvpMatrix;
			attribute vec3 aPosition;
			attribute vec2 aTextureCoordinate;
			varying vec2 vTextureCoordinate;
			void main() {
				gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
				vTextureCoordinate = aTextureCoordinate;
			}
		"""

		const val FragmentShader = """
			precision mediump float;
			uniform sampler2D uTexture;
			varying vec2 vTextureCoordinate;
			void main() {
				gl_FragColor = texture2D(uTexture, vTextureCoordinate);
			}
		"""
	}
}
