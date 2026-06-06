package paige.navic.util.core

import android.util.Log

actual object Logger {
	actual fun e(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.e(tag, msg, tr)
		}.getOrElse {
			println("E/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
	}

	actual fun i(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.i(tag, msg, tr)
		}.getOrElse {
			println("I/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
	}

	actual fun w(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.w(tag, msg, tr)
		}.getOrElse {
			println("W/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
	}
}
