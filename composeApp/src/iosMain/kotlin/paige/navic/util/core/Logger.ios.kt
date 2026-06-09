package paige.navic.util.core

actual object Logger {
	private var issueLogSink: ((LoggerEvent) -> Unit)? = null

	actual fun configureIssueLogSink(sink: ((LoggerEvent) -> Unit)?) {
		issueLogSink = sink
	}

	private fun log(level: AppLogLevel, tag: String, msg: String, tr: Throwable?) {
		println("[$tag] $msg")
		tr?.printStackTrace()
		runCatching {
			issueLogSink?.invoke(LoggerEvent(level, tag, msg, tr))
		}
	}

	actual fun d(tag: String, msg: String, tr: Throwable?) {
		log(AppLogLevel.Debug, tag, msg, tr)
	}

	actual fun e(tag: String, msg: String, tr: Throwable?) {
		log(AppLogLevel.Error, tag, msg, tr)
	}

	actual fun i(tag: String, msg: String, tr: Throwable?) {
		log(AppLogLevel.Info, tag, msg, tr)
	}

	actual fun w(tag: String, msg: String, tr: Throwable?) {
		log(AppLogLevel.Warning, tag, msg, tr)
	}
}
