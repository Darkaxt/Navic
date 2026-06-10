package paige.navic.reader

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import paige.navic.util.core.Logger

private const val ReaderWebRuntimeTag = "ReaderWebRuntime"

object ReaderWebRuntime {
	const val AssetEntrypointPath = "reader/index.html"
	const val AssetLoaderDomain = "appassets.androidplatform.net"
	const val AssetLoaderOrigin = "https://appassets.androidplatform.net"
	const val AssetLoaderAssetsPathPrefix = "/assets/"
	const val AndroidBridgeName = "NavicAndroidBridge"
	const val LocalPublicationFileAccessEnabled = false
	const val WebContentsDebuggingDefaultEnabled = false

	val entrypointUrl: String = "$AssetLoaderOrigin$AssetLoaderAssetsPathPrefix$AssetEntrypointPath"

	private var currentWebContentsDebuggingEnabled: Boolean? = null

	@SuppressLint("SetJavaScriptEnabled")
	fun configure(
		webView: WebView,
		bridge: ReaderJavascriptBridge,
		enableDebugging: Boolean = WebContentsDebuggingDefaultEnabled
	) {
		setWebContentsDebuggingEnabled(enableDebugging)
		webView.settings.javaScriptEnabled = true
		webView.settings.domStorageEnabled = true
		webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
		webView.settings.useWideViewPort = true
		webView.settings.loadWithOverviewMode = false
		webView.settings.textZoom = 100
		webView.settings.allowFileAccess = LocalPublicationFileAccessEnabled
		webView.settings.allowContentAccess = false
		webView.clearCache(true)
		webView.addJavascriptInterface(bridge, AndroidBridgeName)
		webView.loadUrl(entrypointUrl)
	}

	fun setWebContentsDebuggingEnabled(enableDebugging: Boolean) {
		if (currentWebContentsDebuggingEnabled == enableDebugging) {
			return
		}
		currentWebContentsDebuggingEnabled = enableDebugging
		Logger.i(ReaderWebRuntimeTag, "WebView debugging enabled=$enableDebugging")
		WebView.setWebContentsDebuggingEnabled(enableDebugging)
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
