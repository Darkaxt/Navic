package paige.navic.ui.screens.reader

import android.graphics.Bitmap
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
	private var presentationCallback: ((Boolean) -> Unit)? = null
	private var attachmentListener: View.OnAttachStateChangeListener? = null
	private var windowAdded = false

	fun present(
		bitmap: Bitmap,
		surfaceRectInWindow: Rect,
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
		requestedBitmap = bitmap
		presentationCallback = onPresented
		imageView.setImageBitmap(bitmap)
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
		if (windowAdded) {
			runCatching { windowManager.removeViewImmediate(imageView) }
			windowAdded = false
		}
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
