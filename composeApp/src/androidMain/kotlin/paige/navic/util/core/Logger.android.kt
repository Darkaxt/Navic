package paige.navic.util.core

import android.util.Log

actual object Logger {
	private var issueLogSink: ((LoggerEvent) -> Unit)? = null

	actual fun configureIssueLogSink(sink: ((LoggerEvent) -> Unit)?) {
		issueLogSink = sink
	}

	actual fun d(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.d(tag, msg, tr)
		}.getOrElse {
			println("D/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
		emit(AppLogLevel.Debug, tag, msg, tr)
	}

	actual fun e(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.e(tag, msg, tr)
		}.getOrElse {
			println("E/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
		emit(AppLogLevel.Error, tag, msg, tr)
	}

	actual fun i(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.i(tag, msg, tr)
		}.getOrElse {
			println("I/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
		emit(AppLogLevel.Info, tag, msg, tr)
	}

	actual fun w(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.w(tag, msg, tr)
		}.getOrElse {
			println("W/$tag: $msg${tr?.let { throwable -> "\n$throwable" }.orEmpty()}")
		}
		emit(AppLogLevel.Warning, tag, msg, tr)
	}

	private fun emit(level: AppLogLevel, tag: String, msg: String, tr: Throwable?) {
		runCatching {
			issueLogSink?.invoke(LoggerEvent(level, tag, msg, tr))
		}
	}
}
