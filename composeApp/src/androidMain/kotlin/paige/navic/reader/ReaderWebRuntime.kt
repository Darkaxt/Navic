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
	private var forceWebContentsDebuggingEnabled: Boolean = false

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
		val effectiveDebugging = forceWebContentsDebuggingEnabled || enableDebugging
		if (currentWebContentsDebuggingEnabled == effectiveDebugging) {
			return
		}
		currentWebContentsDebuggingEnabled = effectiveDebugging
		Logger.i(ReaderWebRuntimeTag, "WebView debugging enabled=$effectiveDebugging")
		WebView.setWebContentsDebuggingEnabled(effectiveDebugging)
	}

	fun setForceWebContentsDebuggingEnabled(enabled: Boolean) {
		if (forceWebContentsDebuggingEnabled == enabled) {
			return
		}
		forceWebContentsDebuggingEnabled = enabled
		currentWebContentsDebuggingEnabled = null
		setWebContentsDebuggingEnabled(WebContentsDebuggingDefaultEnabled)
	}

	fun commandScript(command: ReaderBridgeDispatchCommand): String =
		command.toJavaScript()
}

class ReaderJavascriptBridge(
	private val onEvent: (ReaderBridgeEvent) -> Unit,
	private val onRejected: (ReaderBridgeDecodeResult.Rejected) -> Unit = { rejection ->
		Logger.w(
			ReaderWebRuntimeTag,
			"Reader bridge message rejected: failure=${rejection.failure} raw=${rejection.rawMessage}"
		)
	}
) {
	private val messageProcessor = ReaderBridgeMessageProcessor(
		onEvent = onEvent,
		onRejected = onRejected
	)

	@JavascriptInterface
	fun postMessage(message: String) {
		messageProcessor.process(message)
	}
}
