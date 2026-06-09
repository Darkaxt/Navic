package paige.navic.util.core

import kotlinx.serialization.Serializable

@Serializable
enum class AppLogLevel(val shortLabel: String) {
	Debug("D"),
	Info("I"),
	Warning("W"),
	Error("E")
}

data class LoggerEvent(
	val level: AppLogLevel,
	val tag: String,
	val message: String,
	val throwable: Throwable? = null
)

expect object Logger {
	fun configureIssueLogSink(sink: ((LoggerEvent) -> Unit)?)
	fun d(tag: String, msg: String, tr: Throwable? = null)
	fun e(tag: String, msg: String, tr: Throwable? = null)
	fun i(tag: String, msg: String, tr: Throwable? = null)
	fun w(tag: String, msg: String, tr: Throwable? = null)
}
