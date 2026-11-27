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
import android.os.Build
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
/** A plate coming back to its loop frame meets it with this quick crossfade. */
private const val LivingPlateRestoreFadeMillis = 150L
/** Plates whose state is remembered for turning back to them (the curl keeps as many pages). */
private const val LivingPlateMemoryLimit = 8
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
 * seamless loop. Leaving the page halts the plate and remembers it; coming back continues from
 * there (the ink where it had spread, the loop at its frame), so it never goes back to grey.
 * Edges fade like the CSS plate mask (7% on every side).
 */
internal class ReaderLivingPlateOverlay(context: Context) : FrameLayout(context) {
	private var shownKey: String? = null
	private val memories = object : LinkedHashMap<String, LivingPlateMemory>(16, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LivingPlateMemory>?): Boolean {
			val drop = size > LivingPlateMemoryLimit
			if (drop) eldest?.value?.frame?.recycle()
			return drop
		}
	}

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
		if (key == shownKey && visibility == VISIBLE) {
			resumeHeld()
			return
		}
		clear()
		shownKey = key
		val tuning = livingPlateInkTuning()
		val restored = ArrayList<String>()
		for (target in targets) {
			// A plate seen before continues where it was left; a new one colours in from grey.
			val memory = memories.remove(memoryKey(target))?.let { memory ->
				if (memory.tuning == tuning) memory else null.also { memory.frame?.recycle() }
			}
			if (memory != null) restored += target.sceneId
			val plate = LivingPlateView(context, target, tuning, memory)
			addView(plate, LayoutParams(target.bounds.width(), target.bounds.height()).apply {
				leftMargin = target.bounds.left
				topMargin = target.bounds.top
			})
		}
		visibility = VISIBLE
		Logger.i(
			LivingPlateTag,
			"living-plates show scenes=${targets.joinToString(",") { it.sceneId + if (it.advanced) "+advanced" else "" }} " +
				"restored=${restored.joinToString(",")}"
		)
	}

	/**
	 * Halts the plates shown that fall in [regionInWindow] and records them exactly as drawn (loop
	 * frame, ink half spread, soft edges) into a new transparent [width]x[height] bitmap; null when
	 * none does. The curl draws the recording on the page being left, so a turn carries the plate
	 * on; a turn that falls back resumes the halted plates from the same state ([resumeHeld]).
	 */
	fun renderCurrentPlates(regionInWindow: Rect, width: Int, height: Int): Bitmap? {
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
			plate.pause()
			drawn = plate.drawCurrent(target) || drawn
			target.restoreToCount(save)
		}
		if (!drawn) {
			still?.recycle()
			return null
		}
		return still
	}

	/** A turn fell back onto this page: halted plates continue where they stopped. False when none show. */
	fun resumeHeld(): Boolean {
		if (childCount == 0 || visibility != VISIBLE) return false
		for (index in 0 until childCount) (getChildAt(index) as? LivingPlateView)?.resume()
		return true
	}

	/** Stops and removes every plate immediately (page turn, curl, pause, detach), remembering each. */
	fun clear() {
		shownKey = null
		if (childCount == 0 && visibility == GONE) return
		for (index in 0 until childCount) {
			val plate = getChildAt(index) as? LivingPlateView ?: continue
			plate.memory()?.let { memories.put(memoryKey(plate.target), it)?.frame?.recycle() }
			plate.release()
		}
		removeAllViews()
		visibility = GONE
	}

	/** Living plates off or the layout replaced: every plate colours in from grey again. */
	fun forget() {
		clear()
		for (memory in memories.values) memory.frame?.recycle()
		memories.clear()
	}

	private fun memoryKey(target: ReaderLivingPlateTarget): String =
		"${target.imagePath}@${target.bounds.width()}x${target.bounds.height()}:${target.loopPath}"

	override fun onDetachedFromWindow() {
		clear()
		super.onDetachedFromWindow()
	}
}

/** Where a plate stood when its page was left, so coming back continues from there. */
private class LivingPlateMemory(
	val revealMillis: Long,
	val revealDone: Boolean,
	val videoMillis: Int,
	val ink: FloatArray?,
	val inkWidth: Int,
	val inkHeight: Int,
	val tuning: LivingPlateInkTuning,
	/** The loop frame on screen (half resolution), shown until the loop is back at it. */
	val frame: Bitmap?
)

private class LivingPlateView(
	context: Context,
	val target: ReaderLivingPlateTarget,
	private val tuning: LivingPlateInkTuning,
	memory: LivingPlateMemory?
) : FrameLayout(context), TextureView.SurfaceTextureListener {
	private val textureView = TextureView(context).also { it.alpha = 0f }
	private val revealView = RevealView(context)
	private var player: MediaPlayer? = null
	private var surface: Surface? = null
	private var animator: ValueAnimator? = null
	/** Playing, waiting for its first frame to reach the texture before the plate fades to it. */
	private var awaitingLoopFrame = false
	private var loopFrameShown = false
	private var videoPrepared = false
	private var revealDone = false
	private var released = false
	private var paused = false
	/** Coming back: the loop resumes at this position, and meets the remembered frame quickly. */
	private var resumeVideoMillis = 0
	private var restoredLoop = false
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
		if (memory == null || !restore(memory, mw, mh)) livingPlateInkExecutor.execute {
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

	/** Continues from [memory]; false (nothing changed) when it cannot, so the plate starts over. */
	private fun restore(memory: LivingPlateMemory, mw: Int, mh: Int): Boolean {
		val ink = memory.ink
		if (revealView.colourImage == null || (ink != null && (memory.inkWidth != mw || memory.inkHeight != mh))) {
			memory.frame?.recycle()
			return false
		}
		ink?.let { revealView.setInk(it, mw, mh, tuning.edge) }
		resumeVideoMillis = memory.videoMillis
		if (memory.revealDone) {
			revealView.still = memory.frame
			revealView.progress = 1f
			revealDone = true
			restoredLoop = true
		} else {
			memory.frame?.recycle()
			startReveal(tuning.colourMillis.toLong(), memory.revealMillis)
		}
		return true
	}

	/** Where this plate stands now, for coming back to it; null before its colouring begins. */
	fun memory(): LivingPlateMemory? {
		if (released || revealView.colourImage == null || (!revealDone && animator == null)) return null
		val frame = if (revealDone && textureView.alpha > 0f && textureView.isAvailable) {
			runCatching {
				textureView.getBitmap(
					(target.bounds.width() / 2).coerceAtLeast(1),
					(target.bounds.height() / 2).coerceAtLeast(1)
				)
			}.getOrNull()
		} else {
			// Left again before the loop came back: the remembered frame is still the right one.
			revealView.takeStill()
		}
		val videoMillis = if (videoPrepared) player?.runCatching { currentPosition }?.getOrNull() ?: 0 else resumeVideoMillis
		return LivingPlateMemory(
			revealMillis = animator?.currentPlayTime ?: 0L,
			revealDone = revealDone,
			videoMillis = videoMillis,
			ink = revealView.ink,
			inkWidth = revealView.inkWidth,
			inkHeight = revealView.inkHeight,
			tuning = tuning,
			frame = frame
		)
	}

	private fun startReveal(millis: Long, fromMillis: Long = 0L) {
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
			if (fromMillis > 0L) currentPlayTime = fromMillis.coerceAtMost(millis)
			if (paused) pause()
		}
	}

	/** Halts the colouring and the loop where they are (a turn may still fall back). */
	fun pause() {
		if (released || paused) return
		paused = true
		animator?.pause()
		player?.runCatching { if (isPlaying) pause() }
	}

	fun resume() {
		if (released || !paused) return
		paused = false
		animator?.resume()
		if (revealDone && videoPrepared) player?.runCatching { if (!isPlaying) start() }
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
					val resumeAt = resumeVideoMillis
					if (resumeAt > 0) {
						// Coming back: the loop continues from the frame it was left at.
						resumeVideoMillis = 0
						setOnSeekCompleteListener {
							if (released) return@setOnSeekCompleteListener
							videoPrepared = true
							startVideoIfReady()
						}
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							seekTo(resumeAt.toLong(), MediaPlayer.SEEK_CLOSEST)
						} else {
							seekTo(resumeAt)
						}
						return@setOnPreparedListener
					}
					videoPrepared = true
					startVideoIfReady()
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
		if (released || paused || !videoPrepared || !revealDone || prepared.isPlaying) return
		prepared.start()
		// The fade waits for a frame decoded after start() to reach the texture: decoders may
		// report rendering start for a held frame, and a resumed loop's seeked frame lands later.
		awaitingLoopFrame = !loopFrameShown
	}

	private fun crossfadeIfReady() {
		if (released || !loopFrameShown || !videoPrepared || !revealDone) return
		val fade = when {
			restoredLoop -> LivingPlateRestoreFadeMillis
			target.advanced -> LivingPlateAdvancedDissolveMillis
			else -> tuning.loopFadeMillis.toLong()
		}
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

	override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
		if (released || !awaitingLoopFrame) return
		awaitingLoopFrame = false
		loopFrameShown = true
		Logger.i(LivingPlateTag, "living-plate loop frame shown scene=${target.sceneId} restored=$restoredLoop")
		crossfadeIfReady()
	}

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

	/**
	 * The plate as drawn now, with its soft edges, at its own size: the loop frame under the
	 * colouring at their current fades. False before the plate decodes.
	 */
	fun drawCurrent(canvas: Canvas): Boolean {
		if (revealView.colourImage == null) return false
		val w = target.bounds.width().toFloat()
		val h = target.bounds.height().toFloat()
		if (w <= 0f || h <= 0f) return false
		val save = canvas.saveLayer(0f, 0f, w, h, null)
		val frameAlpha = textureView.alpha
		if (frameAlpha > 0f && textureView.isAvailable) {
			// Half resolution: the curl samples it at half the page raster's resolution anyway.
			textureView.getBitmap((w / 2f).toInt().coerceAtLeast(1), (h / 2f).toInt().coerceAtLeast(1))?.let { frame ->
				val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (frameAlpha * 255f).toInt() }
				canvas.drawBitmap(frame, null, RectF(0f, 0f, w, h), paint)
				frame.recycle()
			}
		}
		val colourAlpha = if (revealView.visibility == VISIBLE) revealView.alpha else 0f
		if (colourAlpha > 0f) {
			val layer = canvas.saveLayerAlpha(0f, 0f, w, h, (colourAlpha * 255f).toInt())
			revealView.draw(canvas)
			canvas.restoreToCount(layer)
		}
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
			setOnSeekCompleteListener(null)
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
	val ink: FloatArray? get() = inkValues
	val inkWidth: Int get() = mask?.width ?: 0
	val inkHeight: Int get() = mask?.height ?: 0
	/** A remembered loop frame shown in place of the plate until the loop is back at it. */
	var still: Bitmap? = null
		set(value) {
			field = value
			invalidate()
		}
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

	fun takeStill(): Bitmap? {
		val frame = still?.takeUnless(Bitmap::isRecycled)
		still = null
		return frame
	}

	override fun onDraw(canvas: Canvas) {
		val image = colour ?: return
		dst.set(0f, 0f, width.toFloat(), height.toFloat())
		still?.takeUnless(Bitmap::isRecycled)?.let { frame ->
			canvas.drawBitmap(frame, null, dst, bitmapPaint)
			return
		}
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
		still?.recycle()
		still = null
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
