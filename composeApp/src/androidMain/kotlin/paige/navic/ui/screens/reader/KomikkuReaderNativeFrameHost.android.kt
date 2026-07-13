package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
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
import paige.navic.reader.ReaderPageTurnPhysicalDirection
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
import kotlin.math.max
import kotlin.math.min

private const val KomikkuReaderNativeFrameHostTag = "KomikkuReaderNativeFrameHost"
private const val PageTurnPrewarmRequiredStableFrames = 2

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	chromeOverlayVisible: Boolean,
	shellCoverVisible: Boolean,
	shellCoverUrl: String?,
	shellCoverTitle: String,
	coverBackdropEnabled: Boolean,
	viewerKey: ReaderViewerKey,
	grayscaleEnabled: Boolean,
	invertedColors: Boolean,
	verticalPageDragPreview: Boolean,
	pageTurnCanvasEnabled: Boolean,
	pageTurnSnapshotKey: Int,
	pageTurnVisualPageIndex: Int?,
	onViewerAction: (KomikkuNavigationRegion) -> Unit,
	onReadableDragPreview: (deltaX: Float, deltaY: Float, viewWidth: Int, viewHeight: Int, phase: ReaderPageDragPreviewPhase) -> Unit,
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
				setChromeOverlayVisible(chromeOverlayVisible)
				setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle, coverBackdropEnabled)
				setViewerLayerPaint(grayscaleEnabled, invertedColors)
				setVerticalPageDragPreview(verticalPageDragPreview)
				setPageTurnCanvasEnabled(pageTurnCanvasEnabled)
				setPageTurnSnapshotKey(pageTurnSnapshotKey)
				setPageTurnVisualPageIndex(pageTurnVisualPageIndex)
				setOnViewerAction { action -> currentOnViewerAction(action) }
				setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
					currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
				}
				setOnContentLongPress { x, y, width, height -> currentOnContentLongPress(x, y, width, height) }
			}
		},
		update = { root ->
			root.setNavigation(navigator)
			root.setNavigationOverlayVisible(navigationOverlayVisible)
			root.setChromeOverlayVisible(chromeOverlayVisible)
			root.setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle, coverBackdropEnabled)
			root.setViewerLayerPaint(grayscaleEnabled, invertedColors)
			root.setVerticalPageDragPreview(verticalPageDragPreview)
			root.setPageTurnCanvasEnabled(pageTurnCanvasEnabled)
			root.setPageTurnSnapshotKey(pageTurnSnapshotKey)
			root.setPageTurnVisualPageIndex(pageTurnVisualPageIndex)
			root.setViewerContent(viewerKey) { currentViewerContent() }
			root.setComposeOverlay { currentComposeOverlay() }
			root.setOnViewerAction { action -> currentOnViewerAction(action) }
			root.setOnReadableDragPreview { deltaX, deltaY, width, height, phase ->
				currentOnReadableDragPreview(deltaX, deltaY, width, height, phase)
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
		composeOverlay.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		composeOverlay.isClickable = false
		composeOverlay.isFocusable = false
		composeOverlay.elevation = 32f
		composeOverlay.translationZ = 32f

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

	fun setChromeOverlayVisible(visible: Boolean) {
		viewerContainer.chromeOverlayVisible = visible
	}

	fun setShellCover(visible: Boolean, coverUrl: String?, title: String, coverBackdropEnabled: Boolean) {
		shellCoverView.setShellCover(coverUrl = coverUrl, title = title, coverBackdropEnabled = coverBackdropEnabled)
		shellCoverView.visibility = if (visible) VISIBLE else GONE
		viewerContainer.setShellCoverVisible(visible)
	}

	fun setViewerLayerPaint(grayscaleEnabled: Boolean, invertedColors: Boolean) {
		val paint = if (grayscaleEnabled || invertedColors) {
			getCombinedReaderLayerPaint(grayscale = grayscaleEnabled, invertedColors = invertedColors)
		} else {
			null
		}
		viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
		composeOverlay.bringToFront()
	}

	fun setComposeOverlay(content: @Composable () -> Unit) {
		composeOverlay.setContent(content)
		composeOverlay.bringToFront()
	}

	fun setOnViewerAction(onAction: (KomikkuNavigationRegion) -> Unit) {
		viewerContainer.onAction = onAction
	}

	fun setVerticalPageDragPreview(verticalPageDragPreview: Boolean) {
		viewerContainer.verticalPageDragPreview = verticalPageDragPreview
	}

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		viewerContainer.setPageTurnCanvasEnabled(enabled)
	}

	fun setPageTurnSnapshotKey(snapshotKey: Int) {
		viewerContainer.setPageTurnSnapshotKey(snapshotKey)
	}

	fun setPageTurnVisualPageIndex(pageIndex: Int?) {
		viewerContainer.setPageTurnVisualPageIndex(pageIndex)
	}

	fun setOnReadableDragPreview(
		onReadableDragPreview: (
			deltaX: Float,
			deltaY: Float,
			viewWidth: Int,
			viewHeight: Int,
			phase: ReaderPageDragPreviewPhase
		) -> Unit
	) {
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

	override fun onDetachedFromWindow() {
		currentViewerComposeView?.disposeComposition()
		composeOverlay.disposeComposition()
		currentViewerComposeView = null
		currentViewerKey = null
		super.onDetachedFromWindow()
	}
}

private class KomikkuReaderNativeShellCoverView(context: Context) : View(context) {
	private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
	private val backdropImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
		alpha = 124
		colorFilter = ColorMatrixColorFilter(
			ColorMatrix().apply {
				setSaturation(0.78f)
			}
		)
	}
	private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(142, 6, 5, 4)
	}
	private val source = Rect()
	private val destination = RectF()
	private val backdropDestination = RectF()
	private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = 42f
	}
	private var coverUrl: String? = null
	private var title: String = ""
	private var coverBackdropEnabled: Boolean = true
	private var bitmap: Bitmap? = null

	fun setShellCover(coverUrl: String?, title: String, coverBackdropEnabled: Boolean) {
		if (this.coverUrl == coverUrl && this.title == title && this.coverBackdropEnabled == coverBackdropEnabled) return
		this.coverUrl = coverUrl
		this.title = title
		this.coverBackdropEnabled = coverBackdropEnabled
		bitmap = coverUrl
			?.let { context.readerShellCoverFileFor(it) }
			?.absolutePath
			?.let { path -> BitmapFactory.decodeFile(path) }
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(Color.rgb(16, 14, 10))
		val currentBitmap = bitmap
		if (currentBitmap == null || currentBitmap.width <= 0 || currentBitmap.height <= 0) {
			val shellGeometry = resolveNativeReaderShellCoverGeometry(
				viewWidth = width,
				viewHeight = height,
				bitmapWidth = 720,
				bitmapHeight = 1000
			)
			canvas.drawText(title.ifBlank { "Cover" }, width / 2f, height / 2f, titlePaint)
			return
		}
		val shellGeometry = resolveNativeReaderShellCoverGeometry(
			viewWidth = width,
			viewHeight = height,
			bitmapWidth = currentBitmap.width,
			bitmapHeight = currentBitmap.height
		)
		if (coverBackdropEnabled) {
			drawDiffuseCoverBackdrop(canvas, currentBitmap, shellGeometry)
		}
		drawContainedNativeShellCover(canvas, currentBitmap, shellGeometry)
	}

	private fun drawDiffuseCoverBackdrop(
		canvas: Canvas,
		currentBitmap: Bitmap,
		shellGeometry: NativeReaderShellCoverGeometry
	) {
		val backdropRect = shellGeometry.backdropRect
		val scale = max(
			backdropRect.width() / currentBitmap.width.toFloat(),
			backdropRect.height() / currentBitmap.height.toFloat()
		)
		val drawWidth = currentBitmap.width * scale
		val drawHeight = currentBitmap.height * scale
		val left = backdropRect.centerX() - drawWidth / 2f
		val top = backdropRect.centerY() - drawHeight / 2f
		backdropDestination.set(left, top, left + drawWidth, top + drawHeight)
		source.set(0, 0, currentBitmap.width, currentBitmap.height)
		val checkpoint = canvas.save()
		canvas.clipRect(backdropRect)
		canvas.drawBitmap(currentBitmap, source, backdropDestination, backdropImagePaint)
		canvas.restoreToCount(checkpoint)
		canvas.drawRect(backdropRect, dimPaint)
	}

	private fun drawContainedNativeShellCover(
		canvas: Canvas,
		currentBitmap: Bitmap,
		shellGeometry: NativeReaderShellCoverGeometry
	) {
		destination.set(shellGeometry.foregroundImageRect)
		canvas.drawBitmap(currentBitmap, null, destination, imagePaint)
	}
}

private data class NativeReaderShellCoverGeometry(
	val backdropRect: RectF,
	val foregroundRect: RectF,
	val foregroundImageRect: RectF
)

private fun resolveNativeReaderShellCoverGeometry(
	viewWidth: Int,
	viewHeight: Int,
	bitmapWidth: Int,
	bitmapHeight: Int
): NativeReaderShellCoverGeometry {
	val resolvedWidth = max(1, viewWidth).toFloat()
	val resolvedHeight = max(1, viewHeight).toFloat()
	val foregroundRect = nativeShellCoverForegroundRect(
		viewWidth = resolvedWidth,
		viewHeight = resolvedHeight,
		bitmapWidth = max(1, bitmapWidth).toFloat(),
		bitmapHeight = max(1, bitmapHeight).toFloat()
	)
	return NativeReaderShellCoverGeometry(
		backdropRect = RectF(0f, 0f, resolvedWidth, resolvedHeight),
		foregroundRect = foregroundRect,
		foregroundImageRect = foregroundRect
	)
}

private fun nativeShellCoverForegroundRect(
	viewWidth: Float,
	viewHeight: Float,
	bitmapWidth: Float,
	bitmapHeight: Float
): RectF {
	val landscape = viewWidth > viewHeight
	val maxWidth = if (landscape) viewWidth * 0.38f else viewWidth * 0.72f
	val maxHeight = if (landscape) viewHeight * 0.86f else viewHeight * 0.78f
	val scale = min(
		maxWidth / bitmapWidth,
		maxHeight / bitmapHeight
	)
	val drawWidth = bitmapWidth * scale
	val drawHeight = bitmapHeight * scale
	val left = (viewWidth - drawWidth) / 2f
	val top = (viewHeight - drawHeight) / 2f
	return RectF(left, top, left + drawWidth, top + drawHeight)
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
	private val readablePageDragSlopPx = ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()
	private val shellCoverNavigator = KomikkuReaderNavigator(KomikkuRightAndLeftNavigation())
	var navigator: KomikkuReaderNavigator = KomikkuReaderNavigator(KomikkuDisabledNavigation())
	var onAction: (KomikkuNavigationRegion) -> Unit = {}
	var verticalPageDragPreview: Boolean = false
	var chromeOverlayVisible: Boolean = false
	var onReadableDragPreview: (
		deltaX: Float,
		deltaY: Float,
		viewWidth: Int,
		viewHeight: Int,
		phase: ReaderPageDragPreviewPhase
	) -> Unit = { _, _, _, _, _ -> }
	var onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit = { _, _, _, _ -> }
	private var shellCoverView: View? = null
	private var swipeStartX: Float = 0f
	private var swipeStartY: Float = 0f
	private var horizontalSwipeDispatched: Boolean = false
	private var shellCoverDragDiagnosticLogged: Boolean = false
	private var nativeDragPreviewDiagnosticLogged: Boolean = false
	private var nativeTapCandidate: Boolean = false
	private var nativeTapCancelledByDrag: Boolean = false
	private var nativeTapLongConfirmed: Boolean = false
	private var nativeSwipeIntercepted: Boolean = false
	private var pageTurnCanvasEnabled: Boolean = false
	private var pageTurnSnapshotKey: Int = Int.MIN_VALUE
	private var pageTurnVisualPageIndex: Int? = null
	private var shellCoverVisible: Boolean = false
	private var pageTurnPrewarmLayoutListener: ViewTreeObserver.OnPreDrawListener? = null
	private var pageTurnPrewarmLayoutSignature: Long? = null
	private var pageTurnPrewarmStableFrameCount: Int = 0
	private val pageTurnController = ReaderPageTurnController(
		host = this,
		webViewProvider = { viewerContentContainer.findDescendantWebView() },
		onRequestPrewarm = ::requestPageTurnPrewarmWhenReady,
		onCommitTurn = { direction ->
			when (direction) {
				ReaderPageTurnPhysicalDirection.TowardLeft -> onAction(KomikkuNavigationRegion.RIGHT)
				ReaderPageTurnPhysicalDirection.TowardRight -> onAction(KomikkuNavigationRegion.LEFT)
			}
		}
	)

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
		pageTurnController.invalidate("viewer-replaced")
		viewerContentContainer.removeAllViews()
		viewerContentContainer.addView(
			viewerView,
			LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
		)
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageTurnCanvasEnabled(enabled: Boolean) {
		if (pageTurnCanvasEnabled == enabled) return
		pageTurnCanvasEnabled = enabled
		if (enabled) {
			requestPageTurnPrewarmWhenReady()
		} else {
			removePageTurnPrewarmLayoutListener()
			pageTurnController.invalidate("canvas-disabled")
		}
	}

	fun setPageTurnSnapshotKey(snapshotKey: Int) {
		if (pageTurnSnapshotKey == snapshotKey) return
		pageTurnSnapshotKey = snapshotKey
		pageTurnController.invalidate("settings-changed")
		requestPageTurnPrewarmWhenReady()
	}

	fun setPageTurnVisualPageIndex(pageIndex: Int?) {
		val normalized = pageIndex?.takeIf { it >= 0 }
		if (pageTurnVisualPageIndex == normalized) return
		pageTurnVisualPageIndex = normalized
		pageTurnController.synchronizeVisualPageIndex(normalized)
		requestPageTurnPrewarmWhenReady()
	}

	fun setShellCoverVisible(visible: Boolean) {
		if (shellCoverVisible == visible) return
		shellCoverVisible = visible
		if (visible) {
			removePageTurnPrewarmLayoutListener()
			pageTurnController.invalidate("shell-cover-visible")
		}
		requestPageTurnPrewarmWhenReady()
	}

	private fun requestPageTurnPrewarmWhenReady() {
		if (!pageTurnCanvasEnabled || !isAttachedToWindow) return
		pageTurnPrewarmLayoutSignature = null
		pageTurnPrewarmStableFrameCount = 0
		if (pageTurnPrewarmLayoutListener != null) return
		val listener = ViewTreeObserver.OnPreDrawListener {
			if (!pageTurnCanvasEnabled || !isAttachedToWindow) {
				removePageTurnPrewarmLayoutListener()
				return@OnPreDrawListener true
			}
			val webView = viewerContentContainer.findDescendantWebView()
			if (
				webView == null ||
				!webView.isAttachedToWindow ||
				width <= 0 ||
				height <= 0 ||
				webView.width <= 0 ||
				webView.height <= 0 ||
				isLayoutRequested ||
				webView.isLayoutRequested
			) return@OnPreDrawListener true
			val signature = pageTurnPrewarmLayoutSignature(webView)
			if (signature == pageTurnPrewarmLayoutSignature) {
				pageTurnPrewarmStableFrameCount += 1
			} else {
				pageTurnPrewarmLayoutSignature = signature
				pageTurnPrewarmStableFrameCount = 1
			}
			if (pageTurnPrewarmStableFrameCount < PageTurnPrewarmRequiredStableFrames) {
				postInvalidateOnAnimation()
				return@OnPreDrawListener true
			}
			if (pageTurnController.prewarmAdjacent()) {
				removePageTurnPrewarmLayoutListener()
			}
			true
		}
		pageTurnPrewarmLayoutListener = listener
		viewTreeObserver.addOnPreDrawListener(listener)
		postInvalidateOnAnimation()
	}

	private fun pageTurnPrewarmLayoutSignature(webView: WebView): Long {
		var signature = width.toLong()
		signature = signature * 31L + height
		signature = signature * 31L + webView.width
		signature = signature * 31L + webView.height
		return signature
	}

	private fun removePageTurnPrewarmLayoutListener() {
		val listener = pageTurnPrewarmLayoutListener ?: return
		pageTurnPrewarmLayoutListener = null
		pageTurnPrewarmLayoutSignature = null
		pageTurnPrewarmStableFrameCount = 0
		if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		requestPageTurnPrewarmWhenReady()
	}

	private val gestureDetector = KomikkuGestureDetectorWithLongTap(
		context,
		object : KomikkuGestureDetectorWithLongTap.Listener() {
			override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
				if (width <= 0 || height <= 0) return false
				if (nativeTapLongConfirmed) return false
				if (nativeTapCancelledByDrag) return false
				val point = KomikkuPoint(
					x = (event.x / width.toFloat()).coerceIn(0f, 1f),
					y = (event.y / height.toFloat()).coerceIn(0f, 1f)
				)
				val action = if (shellCoverView?.visibility == VISIBLE) {
					shellCoverNavigator.getAction(point)
				} else {
					navigator.getAction(point)
				}
				if (chromeOverlayVisible && action != KomikkuNavigationRegion.MENU) {
					Logger.i(
						KomikkuReaderNativeFrameHostTag,
						"Reader native tap ignored under chrome action=$action x=${event.x} y=${event.y} width=$width height=$height"
					)
					return true
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
				nativeTapCancelledByDrag = false
				nativeTapLongConfirmed = false
				nativeSwipeIntercepted = false
				swipeStartX = event.x
				swipeStartY = event.y
				if (shellCoverView?.visibility != VISIBLE) return true
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
				return false
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
		if (!horizontalSwipeDispatched && !nativeSwipeIntercepted && !nativeTapCancelledByDrag) {
			gestureDetector.onTouchEvent(event)
		}
		val consumed = handled || nativeSwipeIntercepted || horizontalSwipeDispatched
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
				nativeTapCancelledByDrag = false
				shellCoverDragDiagnosticLogged = false
				nativeDragPreviewDiagnosticLogged = false
			}
			MotionEvent.ACTION_MOVE -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy, event)
					logReaderDragCandidate(dx, dy)
					if (nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)) {
						nativeTapCancelledByDrag = true
						val shellCoverVisible = shellCoverView?.visibility == VISIBLE
						if (shellCoverVisible) {
							updateShellCoverDragOffset(dx)
						} else if (usesNativePageTurnCanvas()) {
							pageTurnController.update(dx, width, swipeStartY, event.y, height)
							logReaderReadableDragPreview(dx, dy)
						} else {
							updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)
							logReaderReadableDragPreview(dx, dy)
						}
					}
				}
			}
			MotionEvent.ACTION_UP -> {
				if (!horizontalSwipeDispatched) {
					val dx = event.x - swipeStartX
					val dy = event.y - swipeStartY
					cancelPendingLongTapForDrag(dx, dy, event)
					logReaderDragCandidate(dx, dy)
					if (nativeTapCancelledByDrag || nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)) {
						val shellCoverVisible = shellCoverView?.visibility == VISIBLE
						if (shellCoverVisible) {
							updateShellCoverDragOffset(dx)
							dispatchHorizontalSwipeViewerAction(
								deltaX = dx,
								deltaY = dy
							)
						} else if (usesNativePageTurnCanvas()) {
							pageTurnController.release(dx, width, swipeStartY, event.y, height)
							horizontalSwipeDispatched = true
							nativeTapCancelledByDrag = true
							nativeSwipeIntercepted = true
						} else {
							logReaderReadableDragPreview(dx, dy)
							val readableSwipeAction = readableSwipeAction(
								deltaX = dx,
								deltaY = dy,
								thresholdPx = touchSlopPx
							)
							updateReadableViewerDragOffset(
								deltaX = dx,
								deltaY = dy,
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
				}
				clearSwipeTouchState()
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> {
				if (shellCoverView?.visibility != VISIBLE) {
					if (usesNativePageTurnCanvas()) {
						pageTurnController.cancel()
					} else {
						cancelReadableViewerDragPreview()
					}
				}
				clearSwipeTouchState()
			}
		}
	}

	private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {
		val shellCoverVisible = shellCoverView?.visibility == VISIBLE
		val thresholdPx = readerSwipeThresholdPx(shellCoverVisible)
		val action = if (shellCoverVisible) {
			readerShellCoverSwipeAction(deltaX, deltaY, thresholdPx)
		} else {
			readableSwipeAction(deltaX, deltaY, thresholdPx)
		} ?: return false
		horizontalSwipeDispatched = true
		nativeTapCandidate = false
		nativeTapCancelledByDrag = true
		nativeSwipeIntercepted = true
		if (shellCoverVisible) {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover swipe action=$action dx=$deltaX dy=$deltaY threshold=$thresholdPx"
			)
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader shell cover command action=$action"
			)
		} else {
			Logger.i(
				KomikkuReaderNativeFrameHostTag,
				"Reader native readable swipe action=$action dx=$deltaX dy=$deltaY threshold=$thresholdPx"
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
		deltaY: Float,
		phase: ReaderPageDragPreviewPhase
	) {
		onReadableDragPreview(deltaX, deltaY, width, height, phase)
	}

	private fun cancelReadableViewerDragPreview() {
		onReadableDragPreview(0f, 0f, width, height, ReaderPageDragPreviewPhase.Cancel)
	}

	private fun usesNativePageTurnCanvas(): Boolean =
		pageTurnCanvasEnabled &&
			!verticalPageDragPreview &&
			shellCoverView?.visibility != VISIBLE &&
			pageTurnController.isAvailable

	private fun logReaderDragCandidate(deltaX: Float, deltaY: Float) {
		val thresholdPx = readerSwipeThresholdPx(shellCoverVisible = shellCoverView?.visibility == VISIBLE)
		val magnitude = if (shellCoverView?.visibility == VISIBLE || !verticalPageDragPreview) {
			abs(deltaX)
		} else {
			abs(deltaY)
		}
		if (shellCoverDragDiagnosticLogged || magnitude <= thresholdPx) return
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
		val magnitude = if (verticalPageDragPreview) abs(deltaY) else abs(deltaX)
		if (nativeDragPreviewDiagnosticLogged || magnitude <= touchSlopPx) return
		nativeDragPreviewDiagnosticLogged = true
		Logger.i(
			KomikkuReaderNativeFrameHostTag,
			"Reader native drag preview dx=$deltaX dy=$deltaY threshold=$touchSlopPx"
		)
	}

	private fun cancelPendingLongTapForDrag(deltaX: Float, deltaY: Float, event: MotionEvent) {
		if (abs(deltaX) <= touchSlopPx && abs(deltaY) <= touchSlopPx) return
		if (!nativeTapCancelledByDrag) gestureDetector.cancelForDrag(event)
		nativeTapCandidate = false
		nativeTapCancelledByDrag = true
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
			readableSwipeAction(
				deltaX = x - swipeStartX,
				deltaY = y - swipeStartY,
				thresholdPx = readableDragActivationSlopPx()
			) != null
		}

	private fun readableDragActivationSlopPx(): Float = when {
		pageTurnCanvasEnabled && !verticalPageDragPreview -> touchSlopPx
		else -> readablePageDragSlopPx
	}

	private fun readableSwipeAction(
		deltaX: Float,
		deltaY: Float,
		thresholdPx: Float
	): ReaderTapZoneAction? =
		readerNativeReaderSwipeAction(
			deltaX = deltaX,
			deltaY = deltaY,
			thresholdPx = thresholdPx,
			verticalPageDragPreview = verticalPageDragPreview
		)

	private fun readerSwipeThresholdPx(shellCoverVisible: Boolean): Float =
		if (shellCoverVisible) {
			touchSlopPx
		} else {
			readablePageDragSlopPx
		}

	private fun clearNativeTapState() {
		nativeTapCandidate = false
		nativeTapCancelledByDrag = false
		nativeTapLongConfirmed = false
		nativeSwipeIntercepted = false
	}

	private fun clearSwipeTouchState() {
		shellCoverView?.translationX = 0f
		horizontalSwipeDispatched = false
		swipeStartX = 0f
		swipeStartY = 0f
		nativeDragPreviewDiagnosticLogged = false
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		if (oldw > 0 && oldh > 0 && (w != oldw || h != oldh)) {
			pageTurnController.invalidate("size-changed")
			requestPageTurnPrewarmWhenReady()
		}
	}

	override fun onWindowVisibilityChanged(visibility: Int) {
		super.onWindowVisibilityChanged(visibility)
		if (!pageTurnCanvasEnabled) return
		if (visibility == VISIBLE) {
			requestPageTurnPrewarmWhenReady()
		} else {
			removePageTurnPrewarmLayoutListener()
			pageTurnController.invalidate("window-hidden")
		}
	}

	override fun onDetachedFromWindow() {
		removePageTurnPrewarmLayoutListener()
		pageTurnController.destroy()
		super.onDetachedFromWindow()
	}
}

private fun View.findDescendantWebView(): WebView? {
	if (this is WebView) return this
	if (this !is ViewGroup) return null
	for (index in 0 until childCount) {
		getChildAt(index).findDescendantWebView()?.let { return it }
	}
	return null
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

	fun cancelForDrag(event: MotionEvent) {
		handler.removeCallbacks(longTapFn)
		lastDownEvent?.recycle()
		lastDownEvent = null
		val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
		super.onTouchEvent(cancel)
		cancel.recycle()
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
