package paige.navic.util.core

import android.util.Log

actual object Logger {
	private var issueLogSink: ((LoggerEvent) -> Unit)? = null

	actual fun configureIssueLogSink(sink: ((LoggerEvent) -> Unit)?) {
		issueLogSink = sink
	}

	actual fun d(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.d(tag, msg)
		}.getOrElse {
			println("D/$tag: $msg")
		}
		emit(AppLogLevel.Debug, tag, msg)
	}

	actual fun e(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.e(tag, msg)
		}.getOrElse {
			println("E/$tag: $msg")
		}
		emit(AppLogLevel.Error, tag, msg)
	}

	actual fun i(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.i(tag, msg)
		}.getOrElse {
			println("I/$tag: $msg")
		}
		emit(AppLogLevel.Info, tag, msg)
	}

	actual fun w(tag: String, msg: String, tr: Throwable?) {
		runCatching {
			Log.w(tag, msg)
		}.getOrElse {
			println("W/$tag: $msg")
		}
		emit(AppLogLevel.Warning, tag, msg)
	}

	private fun emit(level: AppLogLevel, tag: String, msg: String) {
		runCatching {
			issueLogSink?.invoke(LoggerEvent(level, tag, msg))
		}
	}
}
