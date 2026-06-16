package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPageDragPreviewPhase
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.readerNativeReaderSwipeAction
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.readerShellCoverSwipeAction
import paige.navic.util.core.Logger
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.min

private const val KomikkuReaderNativeFrameHostTag = "KomikkuReaderNativeFrameHost"

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	shellCoverVisible: Boolean,
	shellCoverUrl: String?,
	shellCoverTitle: String,
	viewerKey: ReaderViewerKey,
	grayscaleEnabled: Boolean,
	invertedColors: Boolean,
	onViewerAction: (KomikkuNavigationRegion) -> Unit,
	onReadableDragPreview: (deltaX: Float, viewWidth: Int, phase: ReaderPageDragPreviewPhase) -> Unit,
	onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit,
	modifier: Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
) {
	val currentViewerContent by rememberUpdatedState(viewerContent)
	val currentComposeOverlay by rememberUpdatedState(composeOverlay)
	val currentOnViewerAction by rememberUpdatedState(onViewerAction)
	val currentOnReadableDragPreview by rememberUpdatedState(onReadableDragPreview)
	val currentOnContentLongPress by rememberUpdatedState(onContentLongPress)

	AndroidView(
		modifier = modifier,
		factory = { context ->
			KomikkuReaderNativeFrameRoot(context).apply {
				setViewerContent(viewerKey) { currentViewerContent() }
				setComposeOverlay { currentComposeOverlay() }
				setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle)
				setViewerLayerPaint(grayscaleEnabled, invertedColors)
				setOnViewerAction { action -> currentOnViewerAction(action) }
				setOnReadableDragPreview { deltaX, width, phase ->
					currentOnReadableDragPreview(deltaX, width, phase)
				}
				setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
			}
		},
		update = { root ->
			root.setNavigation(navigator)
			root.setNavigationOverlayVisible(navigationOverlayVisible)
			root.setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle)
			root.setViewerLayerPaint(grayscaleEnabled, invertedColors)
			root.setViewerContent(viewerKey) { currentViewerContent() }
			root.setComposeOverlay { currentComposeOverlay() }
			root.setOnViewerAction { action -> currentOnViewerAction(action) }
			root.setOnReadableDragPreview { deltaX, width, phase ->
				currentOnReadableDragPreview(deltaX, width, phase)
			}
			root.setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
		}
	)
}

private class KomikkuReaderNativeFrameRoot(context: Context) : FrameLayout(context) {
	private val readerContainer = FrameLayout(context)
	private val viewerContainer = KomikkuReaderNativeViewerContainer(context)
	private val shellCoverView = KomikkuReaderNativeShellCoverView(context)
	private val navigationOverlay = KomikkuReaderNativeNavigationOverlayView(context)
	private val composeOverlay = ComposeView(context)
	private var currentViewerKey: ReaderViewerKey? = null
	private var currentViewerComposeView: ComposeView? = null

	init {
		setBackgroundColor(Color.rgb(32, 35, 41))

		composeOverlay.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		composeOverlay.isClickable = false
		composeOverlay.isFocusable = false

		viewerContainer.setShellCoverView(shellCoverView)
		readerContainer.addView(
			viewerContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)

		navigationOverlay.isClickable = false
		navigationOverlay.isFocusable = false
		navigationOverlay.visibility = GONE
		shellCoverView.isClickable = true
		shellCoverView.isFocusable = false
		shellCoverView.visibility = GONE

		addView(
			readerContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			navigationOverlay,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		addView(
			composeOverlay,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun setNavigation(navigator: KomikkuReaderNavigator) {
		viewerContainer.navigator = navigator
		navigationOverlay.setNavigation(navigator)
	}

	fun setNavigationOverlayVisible(visible: Boolean) {
		navigationOverlay.visibility = if (visible) VISIBLE else GONE
	}

	fun setShellCover(visible: Boolean, coverUrl: String?, title: String) {
		shellCoverView.setShellCover(coverUrl = coverUrl, title = title)
		shellCoverView.visibility = if (visible) VISIBLE else GONE
	}

	fun setViewerLayerPaint(grayscaleEnabled: Boolean, invertedColors: Boolean) {
		val paint = if (grayscaleEnabled || invertedColors) {
			getCombinedReaderLayerPaint(grayscale = grayscaleEnabled, invertedColors = invertedColors)
		} else {
			null
		}
		viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
	}

	fun setOnViewerAction(onAction: (KomikkuNavigationRegion) -> Unit) {
		viewerContainer.onAction = onAction
	}

	fun setOnReadableDragPreview(onReadableDragPreview: (deltaX: Float, viewWidth: Int, phase: ReaderPageDragPreviewPhase) -> Unit) {
		viewerContainer.onReadableDragPreview = onReadableDragPreview
	}

	fun setOnContentLongPress(onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit) {
		viewerContainer.onContentLongPress = onContentLongPress
	}

	fun setViewerContent(viewerKey: ReaderViewerKey, content: @Composable () -> Unit) {
		if (currentViewerKey != viewerKey || currentViewerComposeView == null) {
			currentViewerComposeView?.disposeComposition()
			currentViewerComposeView = ComposeView(context).also { viewerView ->
				viewerView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
				viewerView.setContent(content)
				viewerContainer.replaceViewerContent(viewerView)
			}
			currentViewerKey = viewerKey
		} else {
			currentViewerComposeView?.setContent(content)
		}
	}

	fun setComposeOverlay(content: @Composable () -> Unit) {
		composeOverlay.setContent(content)
	}

	override fun onDetachedFromWindow() {
		currentViewerComposeView?.disposeComposition()
		currentViewerComposeView = null
		currentViewerKey = null
		composeOverlay.disposeComposition()
		super.onDetachedFromWindow()
	}
}

private class KomikkuReaderNativeShellCoverView(context: Context) : View(context) {
	private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val destination = RectF()
	private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = 42f
	}
	private var coverUrl: String? = null
	private var title: String = ""
	private var bitmap: Bitmap? = null

	fun setShellCover(coverUrl: String?, title: String) {
		if (this.coverUrl == coverUrl && this.title == title) return
		this.coverUrl = coverUrl
		this.title = title
		bitmap = coverUrl
			?.let { context.readerShellCoverFileFor(it) }
			?.absolutePath
			?.let { path -> BitmapFactory.decodeFile(path) }
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(Color.BLACK)
		val currentBitmap = bitmap
		if (currentBitmap == null || currentBitmap.width <= 0 || currentBitmap.height <= 0) {
			canvas.drawText(title.ifBlank { "Cover" }, width / 2f, height / 2f, titlePaint)
			return
		}
		val scale = min(
			width.toFloat() / currentBitmap.width.toFloat(),
			height.toFloat() / currentBitmap.height.toFloat()
		)
		val drawWidth = currentBitmap.width * scale
		val drawHeight = currentBitmap.height * scale
		val left = (width - drawWidth) / 2f
		val top = (height - drawHeight) / 2f
		destination.set(left, top, left + drawWidth, top + drawHeight)
		canvas.drawBitmap(currentBitmap, null, destination, imagePaint)
	}
}

private fun getCombinedReaderLayerPaint(grayscale: Boolean, invertedColors: Boolean): Paint =
	Paint().apply {
		colorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply {
				if (grayscale) {
					setSaturation(0f)
				}
				if (invertedColors) {
					postConcat(
						ColorMatrix(
							floatArrayOf(
								-1f, 0f, 0f, 0f, 255f,
								0f, -1f, 0f, 0f, 255f,
								0f, 0f, -1f, 0f, 255f,
								0f, 0f, 0f, 1f, 0f
							)
						)
					)
				}
			}
		)
	}

private fun Context.readerShellCoverFileFor(coverUrl: String): File? {
	val expectedPrefix = "${ReaderWebRuntime.AssetLoaderOrigin}$ReaderPublicationCachePathPrefix"
	if (!coverUrl.startsWith(expectedPrefix)) return null
	val relativePath = coverUrl
		.removePrefix(expectedPrefix)
		.substringBefore("?")
		.substringBefore("#")
		.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
	val file = File(readerPublicationCacheRoot(this), relativePath)
	return file.takeIf { it.isFile }
}

private class KomikkuReaderNativeViewerContainer(context: Context) : FrameLayout(context) {
	private val viewerContentContainer = FrameLayout(context)
	private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	private val shellCoverNavigator = KomikkuReaderNavigator(KomikkuRightAndLeftNavigation())
	var navigator: KomikkuReaderNavigator = KomikkuReaderNavigator(KomikkuDisabledNavigation())
	var onAction: (KomikkuNavigationRegion) -> Unit = {}
	var onReadableDragPreview: (deltaX: Float, viewWidth: Int, phase: ReaderPageDragPreviewPhase) -> Unit = { _, _, _ -> }
	var onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit = { _, _, _, _ -> }
	private var shellCoverView: View? = null
	private var swipeStartX: Float = 0f
	private var swipeStartY: Float = 0f
	private var horizontalSwipeDispatched: Boolean = false
	private var shellCoverDragDiagnosticLogged: Boolean = false
	private var nativeDragPreviewDiagnosticLogged: Boolean = false
	private var nativeTapCandidate: Boolean = false
	private var nativeTapLongConfirmed: Boolean = false
	private var nativeShortTapIntercepted: Boolean = false
	private var nativeSwipeIntercepted: Boolean = false

	init {
		descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
		addView(
			viewerContentContainer,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun setShellCoverView(shellCoverView: View) {
		this.shellCoverView = shellCoverView
		(shellCoverView.parent as? ViewGroup)?.removeView(shellCoverView)
		addView(
			shellCoverView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	fun replaceViewerContent(viewerView: View) {
		viewerContentContainer.removeAllViews()
		viewerContentContainer.addView(
			viewerView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
	}

	private val gestureDetector = KomikkuGestureDetectorWithLongTap(
		context,
		object : KomikkuGestureDetectorWithLongTap.Listener() {
			override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
				if (width <= 0 || height <= 0) return false
				if (nativeTapLongConfirmed) return false
				val point = KomikkuPoint(
					x = (event.x / width.toFloat()).coerceIn(0f, 1f),
					y = (event.y / height.toFloat()).coerceIn(0f, 1f)
				)
				val action = if (shellCoverView?.visibility == VISIBLE) {
					shellCoverNavigator.getAction(point)
				} else {
					navigator.getAction(point)
				}
				Logger.i(
					KomikkuReaderNativeFrameHostTag,
					"Reader native tap action=$action x=${event.x} y=${event.y} width=$width height=$height"
				)
				dispatchSingleTapAction(action)
				return true
			}

			override fun onLongTapConfirmed(event: MotionEvent) {
				nativeTapLongConfirmed = true
				Logger.i(
					KomikkuReaderNativeFrameHostTag,
					"Reader native long tap x=${event.x} y=${event.y}"
				)
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
				onContentLongPress(event.x, event.y, width, height)
			}
		}
	)

	private fun dispatchSingleTapAction(action: KomikkuNavigationRegion) {
		if (action != KomikkuNavigationRegion.MENU) {
			onAction(action)
			return
		}
		if (shellCoverView?.visibility == VISIBLE) {
			onAction(action)
			return
		}
		onAction(KomikkuNavigationRegion.MENU)
	}

	override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				nativeTapCandidate = true
				nativeTapLongConfirmed = false
				nativeShortTapIntercepted = false
				nativeSwipeIntercepted = false
				swipeStartX = event.x
				swipeStartY = event.y
				return false
			}
			MotionEvent.ACTION_MOVE -> {
				if (nativeTapMovedBeyondSlop(event.x, event.y)) {
					nativeTapCandidate = false
				}
				if (
					shellCoverView?.visibility == VISIBLE &&
					!horizontalSwipeDispatched &&
					nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)
				) {
					nativeSwipeIntercepted = true
					return true
				}
				if (
					shellCoverView?.visibility != VISIBLE &&
					!horizontalSwipeDispatched &&
					nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)
				) {
					nativeSwipeIntercepted = true
					return true
				}
				return false
			}
			MotionEvent.ACTION_UP -> {
				if (nativeSwipeIntercepted) return true
				nativeShortTapIntercepted = nativeTapCandidate && !nativeTapLongConfirmed
				return nativeShortTapIntercepted
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				clearNativeTapState()
				return false
			}
			else -> return false
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean = true

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		val handled = super.dispatchTouchEvent(event)
		handleSwipeTouchEvent(event)
		if (!horizontalSwipeDispatched && !nativeSwipeIntercepted) {
			gestureDetector.onTouchEvent(event)
		}
		val consumed = handled || nativeShortTapIntercepted || nativeSwipeIntercepted || horizontalSwipeDispatched
		if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
			clearNativeTapState()
		}
		return consumed
	}

	private fun handleSwipeTouchEvent(event: MotionEvent) {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				swipeStartX = event.x
				swipeStartY = event.y
				horizontalSwipeDispatched = false
				shellCoverDragDiagnosticLogged = false
				nativeDragPreviewDiagnosticLogged = false
			}
			MotionEvent.ACTION_MOVE -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy)
					logReaderDragCandidate(dx, dy)
					val shellCoverVisible = shellCoverView?.visibility == VISIBLE
					if (shellCoverVisible) {
						updateShellCoverDragOffset(dx)
					} else {
						updateReadableViewerDragOffset(dx, ReaderPageDragPreviewPhase.Update)
						logReaderReadableDragPreview(dx, dy)
					}
				}
			}
			MotionEvent.ACTION_UP -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy)
					logReaderDragCandidate(dx, dy)
					val shellCoverVisible = shellCoverView?.visibility == VISIBLE
					if (shellCoverVisible) {
						updateShellCoverDragOffset(dx)
						dispatchHorizontalSwipeViewerAction(
							deltaX = dx,
							deltaY = dy
						)
					} else {
						logReaderReadableDragPreview(dx, dy)
						val readableSwipeAction = readerNativeReaderSwipeAction(
							deltaX = dx,
							deltaY = dy,
							thresholdPx = touchSlopPx
						)
						updateReadableViewerDragOffset(
							deltaX = dx,
							phase = if (readableSwipeAction != null) {
								ReaderPageDragPreviewPhase.Release
							} else {
								ReaderPageDragPreviewPhase.Cancel
							}
						)
						dispatchHorizontalSwipeViewerAction(
							deltaX = dx,
							deltaY = dy
						)
					}
				}
				clearSwipeTouchState()
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				if (shellCoverView?.visibility != VISIBLE) cancelReadableViewerDragPreview()
				clearSwipeTouchState()
			}
		}
	}

	private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {
		val shellCoverVisible = shellCoverView?.visibility == VISIBLE
		val action = if (shellCoverVisible) {
			readerShellCoverSwipeAction(deltaX, deltaY, touchSlopPx)
		} else {
			readerNativeReaderSwipeAction(deltaX, deltaY, touchSlopPx)
		} ?: return false
		horizontalSwipeDispatched = true
		nativeTapCandidate = false
		nativeSwipeIntercepted = true
		if (shellCoverVisible) {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover swipe action=$action dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
			)
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover command action=$action"
			)
		} else {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native readable swipe action=$action dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
			)
		}
		if (shellCoverVisible) {
			when (action) {
				ReaderTapZoneAction.Right -> onAction(KomikkuNavigationRegion.NEXT)
				ReaderTapZoneAction.Left -> onAction(KomikkuNavigationRegion.PREV)
				else -> return false
			}
		} else {
			when (action) {
				ReaderTapZoneAction.Right -> onAction(KomikkuNavigationRegion.RIGHT)
				ReaderTapZoneAction.Left -> onAction(KomikkuNavigationRegion.LEFT)
				else -> return false
			}
		}
		return true
	}

	private fun updateShellCoverDragOffset(deltaX: Float) {
		shellCoverView?.translationX = deltaX
	}

	private fun updateReadableViewerDragOffset(
		deltaX: Float,
		phase: ReaderPageDragPreviewPhase
	) {
		onReadableDragPreview(deltaX, width, phase)
	}

	private fun cancelReadableViewerDragPreview() {
		onReadableDragPreview(0f, width, ReaderPageDragPreviewPhase.Cancel)
	}

	private fun logReaderDragCandidate(deltaX: Float, deltaY: Float) {
		if (shellCoverDragDiagnosticLogged || abs(deltaX) <= touchSlopPx) return
		shellCoverDragDiagnosticLogged = true
		val shellCoverVisible = shellCoverView?.visibility == VISIBLE
		val label = if (shellCoverVisible) {
			"Reader shell cover drag candidate"
		} else {
			"Reader native drag candidate"
		}
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"$label dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun logReaderReadableDragPreview(deltaX: Float, deltaY: Float) {
		if (nativeDragPreviewDiagnosticLogged || abs(deltaX) <= touchSlopPx) return
		nativeDragPreviewDiagnosticLogged = true
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native drag preview dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun cancelPendingLongTapForDrag(deltaX: Float, deltaY: Float) {
		if (abs(deltaX) <= touchSlopPx && abs(deltaY) <= touchSlopPx) return
		gestureDetector.cancelPendingLongTap()
		nativeTapCandidate = false
	}

	private fun nativeTapMovedBeyondSlop(x: Float, y: Float): Boolean =
		abs(x - swipeStartX) > touchSlopPx || abs(y - swipeStartY) > touchSlopPx

	private fun nativeHorizontalSwipeMovedBeyondSlop(x: Float, y: Float): Boolean =
		if (shellCoverView?.visibility == VISIBLE) {
			readerShellCoverSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = touchSlopPx
			) != null
		} else {
			readerNativeReaderSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = touchSlopPx
			) != null
		}

	private fun clearNativeTapState() {
		nativeTapCandidate = false
		nativeTapLongConfirmed = false
		nativeShortTapIntercepted = false
		nativeSwipeIntercepted = false
	}

	private fun clearSwipeTouchState() {
		shellCoverView?.translationX = 0f
		horizontalSwipeDispatched = false
		swipeStartX = 0f
		swipeStartY = 0f
		nativeDragPreviewDiagnosticLogged = false
	}
}

private class KomikkuGestureDetectorWithLongTap(
	context: Context,
	private val listener: Listener
) : GestureDetector(context, listener) {
	private val handler = Handler(Looper.getMainLooper())
	private val slop = ViewConfiguration.get(context).scaledTouchSlop
	private val longTapTime = ViewConfiguration.getLongPressTimeout().toLong()
	private val doubleTapTime = ViewConfiguration.getDoubleTapTimeout().toLong()
	private var downX = 0f
	private var downY = 0f
	private var lastUp = 0L
	private var lastDownEvent: MotionEvent? = null
	private val longTapFn = Runnable {
		lastDownEvent?.let(listener::onLongTapConfirmed)
	}

	fun cancelPendingLongTap() {
		handler.removeCallbacks(longTapFn)
	}

	override fun onTouchEvent(ev: MotionEvent): Boolean {
		when (ev.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				lastDownEvent?.recycle()
				lastDownEvent = MotionEvent.obtain(ev)
				if (ev.downTime - lastUp > doubleTapTime) {
					downX = ev.x
					downY = ev.y
					handler.postDelayed(longTapFn, longTapTime)
				}
			}
			MotionEvent.ACTION_MOVE -> {
				if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
					handler.removeCallbacks(longTapFn)
				}
			}
			MotionEvent.ACTION_UP -> {
				lastUp = ev.eventTime
				handler.removeCallbacks(longTapFn)
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> handler.removeCallbacks(longTapFn)
		}
		return super.onTouchEvent(ev)
	}

	open class Listener : SimpleOnGestureListener() {
		open fun onLongTapConfirmed(event: MotionEvent) {
		}
	}
}

private class KomikkuReaderNativeNavigationOverlayView(context: Context) : View(context) {
	private var navigator: KomikkuReaderNavigator? = null
	private val regionPaint = Paint()
	private val textPaint = Paint().apply {
		textAlign = Paint.Align.CENTER
		color = Color.WHITE
		textSize = 48f
	}
	private val textBorderPaint = Paint().apply {
		textAlign = Paint.Align.CENTER
		color = Color.BLACK
		textSize = 48f
		style = Paint.Style.STROKE
		strokeWidth = 6f
	}

	fun setNavigation(navigator: KomikkuReaderNavigator) {
		this.navigator = navigator
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		navigator?.getRegions()?.forEach { region ->
			val rect = region.rectF
			val left = width * rect.left
			val top = height * rect.top
			val right = width * rect.right
			val bottom = height * rect.bottom
			regionPaint.color = region.type.colorArgb.toLong().toInt()
			canvas.drawRect(left, top, right, bottom, regionPaint)

			val centerX = left + (width * abs(rect.left - rect.right) / 2f)
			val centerY = top + (height * abs(rect.top - rect.bottom) / 2f)
			canvas.drawText(region.type.label, centerX, centerY, textBorderPaint)
			canvas.drawText(region.type.label, centerX, centerY, textPaint)
		}
	}
}
