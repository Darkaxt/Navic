package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView

private const val ReaderPageStaticWindowShieldTimeoutMillis = 1_000L

internal class ReaderPageStaticWindowShield(
	private val host: ViewGroup
) {
	private val windowManager = host.context.getSystemService(WindowManager::class.java)
	private val imageView = ImageView(host.context).apply {
		scaleType = ImageView.ScaleType.FIT_XY
		isClickable = false
		isFocusable = false
		importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
	}
	private var nextRequest = 0L
	private var activeRequest: Long? = null
	private var requestedBitmap: Bitmap? = null
	private var ownedBitmap: Bitmap? = null
	private var presentationCallback: ((Boolean) -> Unit)? = null
	private var attachmentListener: View.OnAttachStateChangeListener? = null
	private var windowAdded = false

	fun present(
		bitmap: Bitmap,
		surfaceRectInWindow: Rect,
		preserveCurrentPresentation: Boolean = false,
		onPresented: (Boolean) -> Unit
	) {
		cancelPresentation()
		if (
			bitmap.isRecycled ||
			surfaceRectInWindow.width() <= 0 ||
			surfaceRectInWindow.height() <= 0 ||
			host.windowToken == null
		) {
			onPresented(false)
			return
		}
		val request = Math.incrementExact(nextRequest).also { nextRequest = it }
		activeRequest = request
		presentationCallback = onPresented
		val capturedPresentation = if (preserveCurrentPresentation) {
			captureCurrentPresentation(bitmap, surfaceRectInWindow)
		} else {
			null
		}
		if (preserveCurrentPresentation && capturedPresentation == null) {
			complete(request, false)
			return
		}
		val presentedBitmap = capturedPresentation ?: bitmap
		requestedBitmap = presentedBitmap
		replacePresentedBitmap(presentedBitmap, capturedPresentation)
		val params = WindowManager.LayoutParams(
			surfaceRectInWindow.width(),
			surfaceRectInWindow.height(),
			WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
				WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
			PixelFormat.TRANSLUCENT
		).apply {
			token = host.windowToken
			gravity = Gravity.TOP or Gravity.START
			x = surfaceRectInWindow.left
			y = surfaceRectInWindow.top
			title = "Reader page capture shield"
		}
		val windowUpdated = runCatching {
			if (windowAdded) {
				windowManager.updateViewLayout(imageView, params)
			} else {
				windowManager.addView(imageView, params)
				windowAdded = true
			}
		}.isSuccess
		if (!windowUpdated) {
			complete(request, false)
			return
		}
		imageView.postDelayed(
			{ complete(request, false) },
			ReaderPageStaticWindowShieldTimeoutMillis
		)
		awaitWindowAttachment(request)
	}

	fun cancelPresentation() {
		attachmentListener?.let(imageView::removeOnAttachStateChangeListener)
		attachmentListener = null
		val callback = presentationCallback
		activeRequest = null
		requestedBitmap = null
		presentationCallback = null
		callback?.invoke(false)
	}

	fun dismiss() {
		cancelPresentation()
		imageView.setImageDrawable(null)
		releaseOwnedBitmap()
		if (windowAdded) {
			runCatching { windowManager.removeViewImmediate(imageView) }
			windowAdded = false
		}
	}

	private fun captureCurrentPresentation(
		referenceBitmap: Bitmap,
		surfaceRectInWindow: Rect
	): Bitmap? {
		val presentationRoot = host.rootView
		if (presentationRoot.width <= 0 || presentationRoot.height <= 0) return null
		val rootLocation = IntArray(2)
		presentationRoot.getLocationInWindow(rootLocation)
		val sourceLeft = surfaceRectInWindow.left - rootLocation[0]
		val sourceTop = surfaceRectInWindow.top - rootLocation[1]
		val sourceRight = sourceLeft + surfaceRectInWindow.width()
		val sourceBottom = sourceTop + surfaceRectInWindow.height()
		if (
			sourceLeft < 0 ||
			sourceTop < 0 ||
			sourceRight > presentationRoot.width ||
			sourceBottom > presentationRoot.height
		) return null

		val captured = runCatching {
			Bitmap.createBitmap(
				referenceBitmap.width,
				referenceBitmap.height,
				Bitmap.Config.ARGB_8888
			)
		}.getOrNull() ?: return null
		return runCatching {
			captured.density = referenceBitmap.density
			val canvas = Canvas(captured)
			canvas.scale(
				captured.width / surfaceRectInWindow.width().toFloat(),
				captured.height / surfaceRectInWindow.height().toFloat()
			)
			canvas.translate(-sourceLeft.toFloat(), -sourceTop.toFloat())
			presentationRoot.draw(canvas)
			captured
		}.getOrElse {
			captured.recycle()
			null
		}
	}

	private fun replacePresentedBitmap(bitmap: Bitmap, owned: Bitmap?) {
		val previousOwnedBitmap = ownedBitmap
		imageView.setImageBitmap(bitmap)
		ownedBitmap = owned
		if (previousOwnedBitmap !== owned) previousOwnedBitmap?.recycle()
	}

	private fun releaseOwnedBitmap() {
		ownedBitmap?.recycle()
		ownedBitmap = null
	}

	private fun awaitWindowAttachment(request: Long) {
		if (imageView.isAttachedToWindow) {
			awaitCommittedWindowFrame(request)
			return
		}
		val listener = object : View.OnAttachStateChangeListener {
			override fun onViewAttachedToWindow(view: View) {
				if (attachmentListener !== this) return
				view.removeOnAttachStateChangeListener(this)
				attachmentListener = null
				awaitCommittedWindowFrame(request)
			}

			override fun onViewDetachedFromWindow(view: View) = Unit
		}
		attachmentListener = listener
		imageView.addOnAttachStateChangeListener(listener)
		if (imageView.isAttachedToWindow) {
			listener.onViewAttachedToWindow(imageView)
		}
	}

	private fun awaitCommittedWindowFrame(request: Long) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val observer = imageView.viewTreeObserver
			if (!observer.isAlive) {
				complete(request, false)
				return
			}
			observer.registerFrameCommitCallback {
				imageView.postOnAnimation { complete(request, true) }
			}
			imageView.postInvalidateOnAnimation()
		} else {
			imageView.postOnAnimation {
				imageView.postOnAnimation { complete(request, true) }
			}
		}
	}

	private fun complete(request: Long, presented: Boolean) {
		if (activeRequest != request) return
		attachmentListener?.let(imageView::removeOnAttachStateChangeListener)
		attachmentListener = null
		val callback = presentationCallback
		val effectivePresentation = presented &&
			windowAdded &&
			imageView.isAttachedToWindow &&
			requestedBitmap?.isRecycled == false
		activeRequest = null
		presentationCallback = null
		if (!effectivePresentation) requestedBitmap = null
		callback?.invoke(effectivePresentation)
	}
}
