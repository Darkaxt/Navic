package paige.navic.ui.screens.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import java.io.File
import paige.navic.util.core.Logger

private const val LivingPlateTag = "ReaderLivingPlates"
private const val LivingPlateRevealMillis = 1_800L
private const val LivingPlateCrossfadeMillis = 500L
private const val LivingPlateRevealSoftEdge = 0.12f
private const val LivingPlateEdgeFadeFraction = 0.07f

/** One plate on screen, in this overlay's pixel coordinates, with its local files. */
internal data class ReaderLivingPlateTarget(
	val sceneId: String,
	val bounds: Rect,
	val imagePath: String,
	val loopPath: String,
	val revealPath: String
)

/**
 * Living plates (Darkaxt/Navic#77): a native layer drawn over the settled WebView page, below the
 * curl surface and the raster shield, so page captures and the curl never see it. Each plate first
 * shows greyscale, gains its colour in the order of its reveal map, then crossfades into its muted
 * seamless loop. The reveal runs once per plate per reader session; returning shows the loop at once.
 * Edges fade like the CSS plate mask (7% on every side).
 */
internal class ReaderLivingPlateOverlay(context: Context) : FrameLayout(context) {
	private val revealedScenes = mutableSetOf<String>()
	private var shownKey: String? = null

	init {
		isClickable = false
		isFocusable = false
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
		visibility = GONE
	}

	override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = false

	/** Shows exactly these plates; the same set already showing keeps playing. */
	fun show(targets: List<ReaderLivingPlateTarget>) {
		val key = targets.joinToString("|") { "${it.sceneId}@${it.bounds.flattenToString()}" }
		if (targets.isEmpty()) {
			clear()
			return
		}
		if (key == shownKey && visibility == VISIBLE) return
		clear()
		shownKey = key
		for (target in targets) {
			val plate = LivingPlateView(context, target, skipReveal = target.sceneId in revealedScenes) {
				revealedScenes += target.sceneId
			}
			addView(plate, LayoutParams(target.bounds.width(), target.bounds.height()).apply {
				leftMargin = target.bounds.left
				topMargin = target.bounds.top
			})
		}
		visibility = VISIBLE
		Logger.i(LivingPlateTag, "living-plates show scenes=${targets.joinToString(",") { it.sceneId }}")
	}

	/** Stops and removes every plate immediately (page turn, curl, pause, detach). */
	fun clear() {
		shownKey = null
		if (childCount == 0 && visibility == GONE) return
		for (index in 0 until childCount) (getChildAt(index) as? LivingPlateView)?.release()
		removeAllViews()
		visibility = GONE
	}

	override fun onDetachedFromWindow() {
		clear()
		super.onDetachedFromWindow()
	}
}

private class LivingPlateView(
	context: Context,
	private val target: ReaderLivingPlateTarget,
	skipReveal: Boolean,
	private val onRevealed: () -> Unit
) : FrameLayout(context), TextureView.SurfaceTextureListener {
	private val textureView = TextureView(context).also { it.alpha = 0f }
	private val revealView = RevealView(context)
	private var player: MediaPlayer? = null
	private var surface: Surface? = null
	private var animator: ValueAnimator? = null
	private var videoStarted = false
	private var revealDone = skipReveal
	private var released = false
	private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
	private var edgeShaders: Pair<Shader, Shader>? = null

	init {
		setWillNotDraw(false)
		setLayerType(LAYER_TYPE_HARDWARE, null)
		textureView.isOpaque = false
		textureView.surfaceTextureListener = this
		addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		addView(revealView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		revealView.load(target, skipReveal)
		if (skipReveal) revealView.progress = 1f else startReveal()
	}

	private fun startReveal() {
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = LivingPlateRevealMillis
			interpolator = LinearInterpolator()
			addUpdateListener { revealView.progress = it.animatedValue as Float }
			addListener(object : android.animation.AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: android.animation.Animator) {
					if (released) return
					revealDone = true
					onRevealed()
					crossfadeIfReady()
				}
			})
			start()
		}
	}

	override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
		if (released) return
		val output = Surface(texture).also { surface = it }
		runCatching {
			player = MediaPlayer().apply {
				setDataSource(target.loopPath)
				setSurface(output)
				isLooping = true
				setVolume(0f, 0f)
				setOnPreparedListener { prepared ->
					if (released) return@setOnPreparedListener
					prepared.start()
				}
				setOnInfoListener { _, what, _ ->
					if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
						videoStarted = true
						crossfadeIfReady()
					}
					false
				}
				setOnErrorListener { _, what, extra ->
					Logger.w(LivingPlateTag, "living-plate video error scene=${target.sceneId} what=$what extra=$extra")
					true
				}
				prepareAsync()
			}
		}.onFailure { error ->
			Logger.w(LivingPlateTag, "living-plate video unavailable scene=${target.sceneId}", error)
		}
	}

	private fun crossfadeIfReady() {
		if (released || !videoStarted || !revealDone) return
		textureView.animate().alpha(1f).setDuration(LivingPlateCrossfadeMillis).start()
		revealView.animate().alpha(0f).setDuration(LivingPlateCrossfadeMillis).withEndAction {
			revealView.visibility = INVISIBLE
		}.start()
	}

	override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

	override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
		releasePlayer()
		return true
	}

	override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		edgeShaders = null
	}

	// The same edge fade as the CSS plate mask: two gradients intersected over the plate.
	override fun dispatchDraw(canvas: Canvas) {
		val w = width.toFloat()
		val h = height.toFloat()
		if (w <= 0f || h <= 0f) return
		val save = canvas.saveLayer(0f, 0f, w, h, null)
		super.dispatchDraw(canvas)
		val shaders = edgeShaders ?: Pair(
			edgeGradient(0f, 0f, w, 0f),
			edgeGradient(0f, 0f, 0f, h)
		).also { edgeShaders = it }
		edgePaint.shader = shaders.first
		canvas.drawRect(0f, 0f, w, h, edgePaint)
		edgePaint.shader = shaders.second
		canvas.drawRect(0f, 0f, w, h, edgePaint)
		canvas.restoreToCount(save)
	}

	private fun edgeGradient(x0: Float, y0: Float, x1: Float, y1: Float): Shader =
		LinearGradient(
			x0, y0, x1, y1,
			intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
			floatArrayOf(0f, LivingPlateEdgeFadeFraction, 1f - LivingPlateEdgeFadeFraction, 1f),
			Shader.TileMode.CLAMP
		)

	private fun releasePlayer() {
		player?.runCatching {
			setOnInfoListener(null)
			setOnPreparedListener(null)
			release()
		}
		player = null
		surface?.release()
		surface = null
	}

	fun release() {
		if (released) return
		released = true
		animator?.cancel()
		animator = null
		textureView.animate().cancel()
		revealView.animate().cancel()
		releasePlayer()
		revealView.recycle()
	}
}

/**
 * The static plate drawn greyscale, with its colour copy shown through a mask computed from the
 * reveal map: a pixel gains colour once the sweep passes its reveal value (0 = first), over a soft
 * edge. The mask is computed at reduced resolution and scaled up.
 */
private class RevealView(context: Context) : View(context) {
	private var colour: Bitmap? = null
	private var revealValues: FloatArray? = null
	private var mask: Bitmap? = null
	private var maskPixels: IntArray? = null
	private val greyPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
		colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
	}
	private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
	private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
	private val dst = RectF()

	var progress: Float = 0f
		set(value) {
			field = value.coerceIn(0f, 1f)
			updateMask()
			invalidate()
		}

	fun load(target: ReaderLivingPlateTarget, colourOnly: Boolean) {
		val w = target.bounds.width().coerceAtLeast(1)
		val h = target.bounds.height().coerceAtLeast(1)
		colour = decodeSampled(target.imagePath, w, h)
		if (colourOnly) return
		val reveal = decodeSampled(target.revealPath, w / 4, h / 4) ?: return
		val mw = (w / 4).coerceAtLeast(1)
		val mh = (h / 4).coerceAtLeast(1)
		val scaled = if (reveal.width == mw && reveal.height == mh) reveal else Bitmap.createScaledBitmap(reveal, mw, mh, true)
		val pixels = IntArray(mw * mh)
		scaled.getPixels(pixels, 0, mw, 0, 0, mw, mh)
		revealValues = FloatArray(pixels.size) { index -> Color.red(pixels[index]) / 255f }
		if (scaled !== reveal) scaled.recycle()
		reveal.recycle()
		mask = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
		maskPixels = IntArray(mw * mh)
		updateMask()
	}

	private fun updateMask() {
		val values = revealValues ?: return
		val pixels = maskPixels ?: return
		val bitmap = mask ?: return
		val edge = LivingPlateRevealSoftEdge
		val threshold = progress * (1f + edge) - edge
		for (index in values.indices) {
			val alpha = ((threshold + edge - values[index]) / edge).coerceIn(0f, 1f)
			pixels[index] = (alpha * 255f).toInt() shl 24
		}
		bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
	}

	override fun onDraw(canvas: Canvas) {
		val image = colour ?: return
		dst.set(0f, 0f, width.toFloat(), height.toFloat())
		val revealMask = mask
		if (revealMask == null || progress >= 1f) {
			canvas.drawBitmap(image, null, dst, bitmapPaint)
			return
		}
		canvas.drawBitmap(image, null, dst, greyPaint)
		val save = canvas.saveLayer(dst, null)
		canvas.drawBitmap(image, null, dst, bitmapPaint)
		canvas.drawBitmap(revealMask, null, dst, maskPaint)
		canvas.restoreToCount(save)
	}

	fun recycle() {
		colour?.recycle()
		colour = null
		mask?.recycle()
		mask = null
		revealValues = null
		maskPixels = null
	}

	private fun decodeSampled(path: String, targetWidth: Int, targetHeight: Int): Bitmap? = runCatching {
		if (!File(path).isFile) return null
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeFile(path, bounds)
		var sample = 1
		while (bounds.outWidth / (sample * 2) >= targetWidth && bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
		BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
	}.getOrNull()
}
