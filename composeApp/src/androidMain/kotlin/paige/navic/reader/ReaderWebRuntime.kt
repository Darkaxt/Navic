package paige.navic.reader

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView

object ReaderWebRuntime {
	const val AssetEntrypointPath = "reader/index.html"
	const val AndroidBridgeName = "NavicAndroidBridge"
	const val LocalPublicationFileAccessEnabled = true

	val entrypointUrl: String = "file:///android_asset/$AssetEntrypointPath"

	@SuppressLint("SetJavaScriptEnabled")
	fun configure(
		webView: WebView,
		bridge: ReaderJavascriptBridge,
		enableDebugging: Boolean = false
	) {
		WebView.setWebContentsDebuggingEnabled(enableDebugging)
		webView.settings.javaScriptEnabled = true
		webView.settings.domStorageEnabled = true
		webView.settings.allowFileAccess = true
		webView.settings.allowFileAccessFromFileURLs = LocalPublicationFileAccessEnabled
		webView.settings.allowUniversalAccessFromFileURLs = false
		webView.settings.allowContentAccess = false
		webView.addJavascriptInterface(bridge, AndroidBridgeName)
		webView.loadUrl(entrypointUrl)
	}

	fun commandScript(command: ReaderBridgeCommand): String =
		command.toJavaScript()
}

class ReaderJavascriptBridge(
	private val onEvent: (ReaderBridgeEvent) -> Unit,
	private val onRawMessage: (String) -> Unit = {}
) {
	@JavascriptInterface
	fun postMessage(message: String) {
		onRawMessage(message)
		decodeReaderBridgeEvent(message)?.let(onEvent)
	}
}
