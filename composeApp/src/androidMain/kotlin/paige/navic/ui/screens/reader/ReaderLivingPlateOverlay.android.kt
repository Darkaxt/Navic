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
import android.widget.FrameLayout
import java.io.File
import java.util.concurrent.Executors
import org.koin.mp.KoinPlatform
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.core.Logger

private const val LivingPlateTag = "ReaderLivingPlates"
// The page draws a living plate grey; the overlay colours it in with ink spreading from the centre
// (4 s by default, tunable in Developer settings), then the loop starts from its first frame (the
// plate itself) and crossfades in.
private const val LivingPlateAdvancedDissolveMillis = 900L
private const val LivingPlateEdgeFadeFraction = 0.07f
/** The ink mask is computed at half the plate's resolution and scaled up. */
private const val LivingPlateInkMaskScale = 2
private val livingPlateInkExecutor = Executors.newSingleThreadExecutor { runnable ->
	Thread(runnable, "living-plate-ink").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
}

private fun livingPlateInkTuning(): LivingPlateInkTuning {
	val prefs = KoinPlatform.getKoinOrNull()?.getOrNull<PreferenceManager>() ?: return LivingPlateInkTuning()
	return LivingPlateInkTuning(
		colourMillis = prefs.livingPlateColourMillis.coerceIn(200, 20_000),
		pace = prefs.livingPlatePace.coerceIn(0, 3),
		tendrilsPercent = prefs.livingPlateInkTendrils.coerceIn(0, 60),
		raggedPercent = prefs.livingPlateInkRagged.coerceIn(0, 40),
		specksPercent = prefs.livingPlateInkSpecks.coerceIn(0, 100),
		edgePerMille = prefs.livingPlateInkEdge.coerceIn(2, 300),
		seed = prefs.livingPlateInkSeed.coerceIn(1, 999),
		loopFadeMillis = prefs.livingPlateLoopFadeMillis.coerceIn(0, 5_000)
	)
}

/** One plate on screen, in this overlay's pixel coordinates, with its local files. */
internal data class ReaderLivingPlateTarget(
	val sceneId: String,
	val bounds: Rect,
	val imagePath: String,
	val loopPath: String,
	/** The warp loop's reveal map (part of what makes a plate playable; the ink colouring ignores it). */
	val revealPath: String?,
	/** A Magic-level video-model loop (#79): its first frame is its poster, reached by a slow dissolve. */
	val advanced: Boolean = false
)

/**
 * Living plates (Darkaxt/Navic#77): a native layer drawn over the settled WebView page, below the
 * curl surface and the raster shield, so page captures and the curl never see it. Each plate first
 * shows greyscale, gains its colour as ink spreading from its centre, then crossfades into its muted
 * seamless loop. The page draws the plate grey, so every arrival colours it in again.
 * Edges fade like the CSS plate mask (7% on every side).
 */
internal class ReaderLivingPlateOverlay(context: Context) : FrameLayout(context) {
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
		val key = targets.joinToString("|") { "${it.sceneId}@${it.bounds.flattenToString()}:${it.loopPath}" }
		if (targets.isEmpty()) {
			clear()
			return
		}
		if (key == shownKey && visibility == VISIBLE) return
		clear()
		shownKey = key
		val tuning = livingPlateInkTuning()
		for (target in targets) {
			// Every arrival colours the grey page plate in again before the loop.
			val plate = LivingPlateView(context, target, tuning)
			addView(plate, LayoutParams(target.bounds.width(), target.bounds.height()).apply {
				leftMargin = target.bounds.left
				topMargin = target.bounds.top
			})
		}
		visibility = VISIBLE
		Logger.i(LivingPlateTag, "living-plates show scenes=${targets.joinToString(",") { it.sceneId + if (it.advanced) "+advanced" else "" }}")
	}

	/**
	 * The colour stills of the plates shown that fall in [regionInWindow], with their soft edges,
	 * scaled into a new transparent [width]x[height] bitmap; null when none does. The curl draws
	 * them on the page being left, so a turn keeps the plate coloured.
	 */
	fun renderColourStills(regionInWindow: Rect, width: Int, height: Int): Bitmap? {
		if (visibility != VISIBLE || childCount == 0 || regionInWindow.isEmpty || width <= 0 || height <= 0) return null
		val origin = IntArray(2).also(::getLocationInWindow)
		val scaleX = width / regionInWindow.width().toFloat()
		val scaleY = height / regionInWindow.height().toFloat()
		var still: Bitmap? = null
		var canvas: Canvas? = null
		var drawn = false
		for (index in 0 until childCount) {
			val plate = getChildAt(index) as? LivingPlateView ?: continue
			val bounds = Rect(plate.target.bounds).apply { offset(origin[0], origin[1]) }
			if (!Rect.intersects(bounds, regionInWindow)) continue
			val target = canvas ?: Canvas(
				Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { still = it }
			).also { canvas = it }
			val save = target.save()
			target.scale(scaleX, scaleY)
			target.translate((bounds.left - regionInWindow.left).toFloat(), (bounds.top - regionInWindow.top).toFloat())
			drawn = plate.drawColourStill(target) || drawn
			target.restoreToCount(save)
		}
		if (!drawn) {
			still?.recycle()
			return null
		}
		return still
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
	val target: ReaderLivingPlateTarget,
	private val tuning: LivingPlateInkTuning
) : FrameLayout(context), TextureView.SurfaceTextureListener {
	private val textureView = TextureView(context).also { it.alpha = 0f }
	private val revealView = RevealView(context)
	private var player: MediaPlayer? = null
	private var surface: Surface? = null
	private var animator: ValueAnimator? = null
	private var videoStarted = false
	private var videoPrepared = false
	private var revealDone = false
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
		revealView.load(target)
		// The ink is generated off the main thread; the plate stays grey until it is ready.
		val mw = (target.bounds.width() / LivingPlateInkMaskScale).coerceAtLeast(1)
		val mh = (target.bounds.height() / LivingPlateInkMaskScale).coerceAtLeast(1)
		livingPlateInkExecutor.execute {
			val started = System.nanoTime()
			val field = runCatching { livingPlateInkField(mw, mh, tuning) }
				.onFailure { Logger.w(LivingPlateTag, "living-plate ink unavailable scene=${target.sceneId}", it) }
				.getOrNull()
			val tookMillis = (System.nanoTime() - started) / 1_000_000
			post {
				if (released) return@post
				Logger.i(LivingPlateTag, "living-plate ink scene=${target.sceneId} mask=${mw}x$mh built=${tookMillis}ms colour=${tuning.colourMillis}ms pace=${tuning.pace}")
				// Without an ink field the colour fades in uniformly.
				field?.let { revealView.setInk(it, mw, mh, tuning.edge) }
				startReveal(tuning.colourMillis.toLong())
			}
		}
	}

	private fun startReveal(millis: Long) {
		animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = millis
			interpolator = android.animation.TimeInterpolator { livingPlateInkPace(it, tuning.pace) }
			addUpdateListener { revealView.progress = it.animatedValue as Float }
			addListener(object : android.animation.AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: android.animation.Animator) {
					if (released) return
					revealDone = true
					startVideoIfReady()
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
				// Prepared during the colouring, held at its first frame until the plate is coloured.
				setOnPreparedListener {
					if (released) return@setOnPreparedListener
					videoPrepared = true
					startVideoIfReady()
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

	private fun startVideoIfReady() {
		val prepared = player ?: return
		if (released || !videoPrepared || !revealDone || prepared.isPlaying) return
		prepared.start()
		// Some decoders report rendering start for the held first frame, before start().
		crossfadeIfReady()
	}

	private fun crossfadeIfReady() {
		if (released || !videoStarted || !revealDone) return
		val fade = if (target.advanced) LivingPlateAdvancedDissolveMillis else tuning.loopFadeMillis.toLong()
		textureView.animate().alpha(1f).setDuration(fade).start()
		revealView.animate().alpha(0f).setDuration(fade).withEndAction {
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

	/** The plate in full colour with its soft edges, at its own size; false before it decodes. */
	fun drawColourStill(canvas: Canvas): Boolean {
		val image = revealView.colourImage ?: return false
		val w = target.bounds.width().toFloat()
		val h = target.bounds.height().toFloat()
		if (w <= 0f || h <= 0f) return false
		val save = canvas.saveLayer(0f, 0f, w, h, null)
		canvas.drawBitmap(image, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
		val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
		mask.shader = edgeGradient(0f, 0f, w, 0f)
		canvas.drawRect(0f, 0f, w, h, mask)
		mask.shader = edgeGradient(0f, 0f, 0f, h)
		canvas.drawRect(0f, 0f, w, h, mask)
		canvas.restoreToCount(save)
		return true
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
 * The static plate drawn greyscale, with its colour copy shown through the ink mask: a pixel gains
 * colour once the timeline passes its ink value (0 = first), over a soft front. The mask is computed
 * at reduced resolution and scaled up.
 */
private class RevealView(context: Context) : View(context) {
	private var colour: Bitmap? = null
	val colourImage: Bitmap? get() = colour?.takeUnless(Bitmap::isRecycled)
	private var inkValues: FloatArray? = null
	private var inkEdge = 0.025f
	private var mask: Bitmap? = null
	private var maskPixels: IntArray? = null
	private val greyPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
		colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
	}
	private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
	private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG)
	private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
	private val dst = RectF()

	var progress: Float = 0f
		set(value) {
			field = value.coerceIn(0f, 1f)
			updateMask()
			invalidate()
		}

	fun load(target: ReaderLivingPlateTarget) {
		val w = target.bounds.width().coerceAtLeast(1)
		val h = target.bounds.height().coerceAtLeast(1)
		colour = decodeSampled(target.imagePath, w, h)
	}

	fun setInk(values: FloatArray, width: Int, height: Int, edge: Float) {
		if (colour == null) return
		inkValues = values
		inkEdge = edge
		mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		maskPixels = IntArray(width * height)
		updateMask()
	}

	private fun updateMask() {
		val values = inkValues ?: return
		val pixels = maskPixels ?: return
		val bitmap = mask ?: return
		val edge = inkEdge
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
		val inkMask = mask
		if (progress >= 1f) {
			canvas.drawBitmap(image, null, dst, bitmapPaint)
			return
		}
		canvas.drawBitmap(image, null, dst, greyPaint)
		if (inkMask == null) {
			fadePaint.alpha = (progress * 255f).toInt()
			canvas.drawBitmap(image, null, dst, fadePaint)
			return
		}
		val save = canvas.saveLayer(dst, null)
		canvas.drawBitmap(image, null, dst, bitmapPaint)
		canvas.drawBitmap(inkMask, null, dst, maskPaint)
		canvas.restoreToCount(save)
	}

	fun recycle() {
		colour?.recycle()
		colour = null
		mask?.recycle()
		mask = null
		inkValues = null
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
