package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
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
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.readerPublicationCacheRoot
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.min

@Composable
actual fun KomikkuReaderNativeFrameHost(
	navigator: KomikkuReaderNavigator,
	navigationOverlayVisible: Boolean,
	shellCoverVisible: Boolean,
	shellCoverUrl: String?,
	shellCoverTitle: String,
	viewerKey: ReaderViewerKey,
	onViewerAction: (KomikkuNavigationRegion) -> Unit,
	modifier: Modifier,
	viewerContent: @Composable () -> Unit,
	composeOverlay: @Composable () -> Unit
) {
	val currentViewerContent by rememberUpdatedState(viewerContent)
	val currentComposeOverlay by rememberUpdatedState(composeOverlay)
	val currentOnViewerAction by rememberUpdatedState(onViewerAction)

	AndroidView(
		modifier = modifier,
		factory = { context ->
			KomikkuReaderNativeFrameRoot(context).apply {
				setViewerContent(viewerKey) { currentViewerContent() }
				setComposeOverlay { currentComposeOverlay() }
				setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle)
				setOnViewerAction { action -> currentOnViewerAction(action) }
			}
		},
		update = { root ->
			root.setNavigation(navigator)
			root.setNavigationOverlayVisible(navigationOverlayVisible)
			root.setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle)
			root.setViewerContent(viewerKey) { currentViewerContent() }
			root.setComposeOverlay { currentComposeOverlay() }
			root.setOnViewerAction { action -> currentOnViewerAction(action) }
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

	fun setOnViewerAction(onAction: (KomikkuNavigationRegion) -> Unit) {
		viewerContainer.onAction = onAction
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
	var navigator: KomikkuReaderNavigator = KomikkuReaderNavigator(KomikkuDisabledNavigation())
	var onAction: (KomikkuNavigationRegion) -> Unit = {}
	private var shellCoverView: View? = null
	private var swipeStartX: Float = 0f
	private var swipeStartY: Float = 0f
	private var horizontalSwipeDispatched: Boolean = false

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

	private val gestureDetector = GestureDetector(
		context,
		object : GestureDetector.SimpleOnGestureListener() {
			override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
				if (width <= 0 || height <= 0) return false
				val action = navigator.getAction(
					KomikkuPoint(
						x = (event.x / width.toFloat()).coerceIn(0f, 1f),
						y = (event.y / height.toFloat()).coerceIn(0f, 1f)
					)
				)
				onAction(action)
				return true
			}
		}
	)

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		val handled = super.dispatchTouchEvent(event)
		handleSwipeTouchEvent(event)
		gestureDetector.onTouchEvent(event)
		return handled
	}

	private fun handleSwipeTouchEvent(event: MotionEvent) {
		if (shellCoverView?.visibility != VISIBLE) return
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				swipeStartX = event.x
				swipeStartY = event.y
				horizontalSwipeDispatched = false
			}
			MotionEvent.ACTION_MOVE,
			MotionEvent.ACTION_UP -> {
				if (!horizontalSwipeDispatched) {
					dispatchHorizontalSwipeViewerAction(
						deltaX = event.x - swipeStartX,
						deltaY = event.y - swipeStartY
					)
				}
				if (event.actionMasked == MotionEvent.ACTION_UP) {
					clearSwipeTouchState()
				}
			}
			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> clearSwipeTouchState()
		}
	}

	private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {
		val absoluteX = abs(deltaX)
		val absoluteY = abs(deltaY)
		if (absoluteX <= touchSlopPx || absoluteX <= absoluteY) return false
		horizontalSwipeDispatched = true
		if (deltaX < 0f) {
			onAction(KomikkuNavigationRegion.NEXT)
		} else {
			onAction(KomikkuNavigationRegion.PREV)
		}
		return true
	}

	private fun clearSwipeTouchState() {
		horizontalSwipeDispatched = false
		swipeStartX = 0f
		swipeStartY = 0f
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
