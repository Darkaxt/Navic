package paige.navic.ui.screens.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import java.util.UUID
import org.json.JSONObject
import org.json.JSONTokener

private const val ReaderPassiveRasterParityAssetDomain = "appassets.androidplatform.net"
private const val ReaderPassiveRasterParityAssetPrefix = "/assets/"
private const val ReaderPassiveRasterParityLiveAssetUrl =
	"https://$ReaderPassiveRasterParityAssetDomain${ReaderPassiveRasterParityAssetPrefix}" +
		"reader/passive-raster-prototype/live-fixture.html"
private const val ReaderPassiveRasterParityMaximumPolls = 1_800

public interface ReaderPassiveRasterParityHarness : AutoCloseable {
	public fun pause()
	public fun resume()
}

public fun createReaderPassiveRasterParityHarness(
	activity: Activity,
	webViewContainer: FrameLayout,
	statusView: TextView
): ReaderPassiveRasterParityHarness = ReaderPassiveRasterParityHarnessImpl(
	activity = activity,
	webViewContainer = webViewContainer,
	statusView = statusView
).also { harness ->
	webViewContainer.postOnAnimation {
		webViewContainer.postOnAnimation { harness.runSyntheticMatrix() }
	}
}

private data class ReaderPassiveRasterParityScenario(
	val profileKey: String,
	val targetKey: String,
	val portrait: Boolean,
	val replaceLiveSession: Boolean = false,
	val expectStaleRasterGenerationRejection: Boolean = false
)

private class ReaderPassiveRasterParityHarnessImpl(
	private val activity: Activity,
	private val webViewContainer: FrameLayout,
	private val statusView: TextView
) : ReaderPassiveRasterParityHarness {
	private val scenarios = listOf(
		ReaderPassiveRasterParityScenario("portrait-day", "section-0-page-0", true),
		ReaderPassiveRasterParityScenario("landscape-day", "section-0-page-1", false),
		ReaderPassiveRasterParityScenario(
			"landscape-night-large",
			"section-1-page-0",
			false
		),
		ReaderPassiveRasterParityScenario(
			"landscape-night-large",
			"section-2-page-0",
			false
		),
		ReaderPassiveRasterParityScenario(
			"landscape-day",
			"section-1-page-1",
			false,
			replaceLiveSession = true
		),
		ReaderPassiveRasterParityScenario(
			"landscape-day",
			"section-2-page-1",
			false,
			expectStaleRasterGenerationRejection = true
		)
	)
	private var callbackGeneration = 1L
	private var scenarioIndex = 0
	private var rasterGeneration = 0L
	private var passiveExpectedCommitSequence = 0L
	private var captureAttempts = 0
	private var captureSuccesses = 0
	private var captureFailures = 0
	private var rendererLosses = 0
	private var staleRasterGenerationChecks = 0
	private var staleRasterGenerationPasses = 0
	private var bitmapValidationSuccesses = 0
	private var bitmapValidationFailures = 0
	private var rendererLossRecoveryScheduled = false
	private var lastFailurePhase = "none"
	private var readyPolls = 0
	private var operationInFlight = false
	private var paused = false
	private var destroyed = false
	private var activeGeometry: ReaderPassiveRasterGeometry? = null
	private var liveReplacementScenarioIndex = -1
	private var lastLiveSessionId: String? = null
	private var liveWebView: ReaderPassiveRasterLiveWebView? = null
	private var passiveHost: ReaderPassiveRasterWebViewHost? = null
	private var passiveSession: ReaderPassiveRasterPrototypeSession<Bitmap>? = null

	internal fun runSyntheticMatrix() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || PixelCopy.SUCCESS != 0) {
			publishStatus("unsupported")
			return
		}
		if (operationInFlight) return
		if (destroyed || paused || scenarioIndex >= scenarios.size) {
			publishStatus(if (destroyed) "destroyed" else if (paused) "paused" else "complete")
			return
		}
		val scenario = scenarios[scenarioIndex]
		val geometry = geometryFor(scenario.portrait)
		if (geometry == null) {
			captureFailures = captureFailures.incrementBoundedCounter()
			lastFailurePhase = "geometry-unavailable"
			publishStatus("unavailable")
			return
		}
		ensureHosts(geometry, scenario.replaceLiveSession)
		val generation = callbackGeneration
		val live = liveWebView
		if (live == null || !live.isReady || passiveHost?.isReady != true) {
			readyPolls += 1
			if (readyPolls >= ReaderPassiveRasterParityMaximumPolls) {
				readyPolls = 0
				completeScenario(success = false, failurePhase = "runtime-ready-timeout")
				return
			}
			webViewContainer.postOnAnimation {
				if (callbackIsCurrent(generation)) runSyntheticMatrix()
			}
			return
		}
		readyPolls = 0
		operationInFlight = true
		rasterGeneration += 1L
		live.issueManifest(
			profileKey = scenario.profileKey,
			targetKey = scenario.targetKey,
			rasterGeneration = rasterGeneration
		) { issued ->
			if (!callbackIsCurrent(generation)) return@issueManifest
			if (issued == null ||
				(scenario.replaceLiveSession && issued.manifest.liveFoliateSessionId == lastLiveSessionId)
			) {
				completeScenario(success = false, failurePhase = "live-manifest")
				return@issueManifest
			}
			lastLiveSessionId = issued.manifest.liveFoliateSessionId
			captureIssuedManifest(issued.manifest, scenario)
		}
	}

	private fun captureIssuedManifest(
		manifest: ReaderPassiveRasterCaptureManifest,
		scenario: ReaderPassiveRasterParityScenario
	) {
		val session = passiveSession
		val host = passiveHost
		if (session == null || host == null) {
			completeScenario(success = false, failurePhase = "passive-runtime")
			return
		}
		captureAttempts = captureAttempts.incrementBoundedCounter()
		passiveExpectedCommitSequence += 1L
		val expectedCommitSequence = passiveExpectedCommitSequence
		val generation = callbackGeneration
		if (!session.capture(manifest) { result ->
			if (!callbackIsCurrent(generation)) {
				result?.raster?.release()
				return@capture
			}
			if (result == null) {
				completeScenario(success = false, failurePhase = "passive-capture")
				return@capture
			}
			val context = ReaderPassiveRasterAdmissionContext(
				expectedManifestSequence = manifest.manifestSequence,
				currentCaptureEpoch = manifest.captureEpoch,
				currentLiveFoliateSessionId = manifest.liveFoliateSessionId,
				activePublicationSessionGeneration = manifest.publicationSessionGeneration,
				currentDestinationCommitToken = manifest.destinationCommitToken,
				currentOpaqueCaptureTarget = manifest.opaqueCaptureTarget,
				currentVisualPageOrdinal = manifest.visualPageOrdinal,
				currentRasterProfileKey = manifest.rasterProfileKey,
				currentPaginationFingerprint = manifest.paginationFingerprint,
				currentLayoutFingerprint = manifest.layoutFingerprint,
				currentDecorationFingerprint = manifest.decorationFingerprint,
				currentViewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
				currentRasterGeneration = if (
					scenario.expectStaleRasterGenerationRejection
				) {
					manifest.rasterGeneration + 1L
				} else {
					manifest.rasterGeneration
				},
				activePassiveSessionId = host.passiveSessionId,
				expectedPassiveCommitSequence = expectedCommitSequence
			)
			if (scenario.expectStaleRasterGenerationRejection) {
				staleRasterGenerationChecks = staleRasterGenerationChecks.incrementBoundedCounter()
			}
			when (val admission = readerAdmitPassiveRaster(context, result)) {
				is ReaderPassiveRasterAdmission.Admitted -> {
					if (scenario.expectStaleRasterGenerationRejection) {
						admission.releaseRaster()
						completeScenario(success = false, failurePhase = "stale-raster-admitted")
						return@capture
					}
					val bitmap = admission.transferRaster()
					val bitmapIsValid = bitmap?.let(::readerPassiveRasterBitmapHasVariation) == true
					bitmap?.recycle()
					if (bitmapIsValid) {
						bitmapValidationSuccesses =
							bitmapValidationSuccesses.incrementBoundedCounter()
						completeScenario(success = true)
					} else {
						bitmapValidationFailures =
							bitmapValidationFailures.incrementBoundedCounter()
						completeScenario(success = false, failurePhase = "bitmap-validation")
					}
				}
				is ReaderPassiveRasterAdmission.Rejected -> {
					if (
						scenario.expectStaleRasterGenerationRejection &&
						admission.reason == ReaderPassiveRasterRejection.RasterGeneration
					) {
						staleRasterGenerationPasses =
							staleRasterGenerationPasses.incrementBoundedCounter()
						completeScenario(success = true, admitted = false)
					} else {
						completeScenario(
							success = false,
							failurePhase = "admission-${admission.reason.name}"
						)
					}
				}
			}
		}) {
			completeScenario(success = false, failurePhase = "capture-not-started")
		}
	}

	private fun completeScenario(
		success: Boolean,
		failurePhase: String? = null,
		admitted: Boolean = success
	) {
		operationInFlight = false
		if (success) {
			if (admitted) {
				captureSuccesses = captureSuccesses.incrementBoundedCounter()
			}
			lastFailurePhase = "none"
		} else {
			captureFailures = captureFailures.incrementBoundedCounter()
			lastFailurePhase = failurePhase ?: "unspecified"
		}
		scenarioIndex += 1
		publishStatus(if (scenarioIndex >= scenarios.size) "complete" else "running")
		webViewContainer.postOnAnimation(::runSyntheticMatrix)
	}

	private fun ensureHosts(
		geometry: ReaderPassiveRasterGeometry,
		replaceLiveSession: Boolean
	) {
		if (activeGeometry != geometry) {
			destroyHosts()
			activeGeometry = geometry
			createLiveHost(geometry)
			createPassiveHost(geometry)
			if (replaceLiveSession) liveReplacementScenarioIndex = scenarioIndex
			return
		}
		if (replaceLiveSession && liveReplacementScenarioIndex != scenarioIndex) {
			liveWebView?.destroy()
			createLiveHost(geometry)
			liveReplacementScenarioIndex = scenarioIndex
		}
	}

	private fun createLiveHost(geometry: ReaderPassiveRasterGeometry) {
		liveWebView = ReaderPassiveRasterLiveWebView(
			activity = activity,
			container = webViewContainer,
			viewportGeometry = geometry,
			onRendererGone = ::handleRendererProcessGone
		)
	}

	private fun createPassiveHost(geometry: ReaderPassiveRasterGeometry) {
		val host = ReaderPassiveRasterWebViewHost(
			activity = activity,
			passiveSessionId = UUID.randomUUID().toString(),
			viewportGeometry = geometry,
			onRendererGone = ::handleRendererProcessGone
		)
		passiveHost = host
		passiveSession = ReaderPassiveRasterPrototypeSession(host, Bitmap::recycle)
		passiveExpectedCommitSequence = 0L
	}

	private fun handleRendererProcessGone() {
		if (destroyed || rendererLossRecoveryScheduled) return
		rendererLossRecoveryScheduled = true
		val captureWasActive = operationInFlight
		operationInFlight = false
		callbackGeneration += 1L
		rendererLosses = rendererLosses.incrementBoundedCounter()
		if (captureWasActive) {
			captureFailures = captureFailures.incrementBoundedCounter()
			lastFailurePhase = "renderer-loss"
		}
		destroyHosts()
		activeGeometry = null
		liveReplacementScenarioIndex = -1
		lastLiveSessionId = null
		scenarioIndex = 0
		readyPolls = 0
		publishStatus("renderer-recovering")
		webViewContainer.postOnAnimation {
			rendererLossRecoveryScheduled = false
			if (!destroyed && !paused) runSyntheticMatrix()
		}
	}

	private fun geometryFor(portrait: Boolean): ReaderPassiveRasterGeometry? {
		val availableWidth = webViewContainer.width
		val availableHeight = webViewContainer.height
		if (availableWidth <= 0 || availableHeight <= 0) return null
		val width: Int
		val height: Int
		if (portrait) {
			width = minOf(availableWidth, availableHeight * 2 / 3).coerceAtLeast(1)
			height = minOf(availableHeight, width * 3 / 2).coerceAtLeast(1)
		} else {
			height = minOf(availableHeight, availableWidth * 2 / 3).coerceAtLeast(1)
			width = minOf(availableWidth, height * 3 / 2).coerceAtLeast(1)
		}
		return ReaderPassiveRasterGeometry(
			viewportWidth = width,
			viewportHeight = height,
			captureLeft = 0,
			captureTop = 0,
			captureRight = width,
			captureBottom = height
		)
	}

	private fun publishStatus(state: String) {
		val hostCaptureMetrics = passiveHost?.captureMetrics()
			?: ReaderPassiveRasterWebViewCaptureMetrics(
				captureRequests = 0,
				preconditionFailures = 0,
				pixelCopyAttempts = 0,
				pixelCopySuccesses = 0,
				pixelCopyFailures = 0,
				lastPixelCopyResult = null,
				lastCaptureLatencyMillis = null,
				maximumCaptureLatencyMillis = 0L,
				lastPreconditionFailure = null,
				lastGeometryWidthDelta = 0,
				lastGeometryHeightDelta = 0
			)
		val status = buildString {
			appendLine("Passive raster parity")
			appendLine("status=$state")
			appendLine("captureAttempts=$captureAttempts")
			appendLine("captureSuccesses=$captureSuccesses")
			appendLine("captureFailures=$captureFailures")
			appendLine("rendererLosses=$rendererLosses")
			appendLine("staleRasterGenerationChecks=$staleRasterGenerationChecks")
			appendLine("staleRasterGenerationPasses=$staleRasterGenerationPasses")
			appendLine("bitmapValidationSuccesses=$bitmapValidationSuccesses")
			appendLine("bitmapValidationFailures=$bitmapValidationFailures")
			appendLine("lastFailurePhase=$lastFailurePhase")
			appendLine("hostCaptureRequests=${hostCaptureMetrics.captureRequests}")
			appendLine("hostPreconditionFailures=${hostCaptureMetrics.preconditionFailures}")
			appendLine(
				"lastPreconditionFailure=${hostCaptureMetrics.lastPreconditionFailure?.name ?: "none"}"
			)
			appendLine("lastGeometryWidthDelta=${hostCaptureMetrics.lastGeometryWidthDelta}")
			appendLine("lastGeometryHeightDelta=${hostCaptureMetrics.lastGeometryHeightDelta}")
			appendLine("pixelCopyAttempts=${hostCaptureMetrics.pixelCopyAttempts}")
			appendLine("pixelCopySuccesses=${hostCaptureMetrics.pixelCopySuccesses}")
			appendLine("pixelCopyFailures=${hostCaptureMetrics.pixelCopyFailures}")
			appendLine(
				"lastCaptureLatencyMillis=${hostCaptureMetrics.lastCaptureLatencyMillis ?: -1L}"
			)
			appendLine(
				"maximumCaptureLatencyMillis=${hostCaptureMetrics.maximumCaptureLatencyMillis}"
			)
			append("lastPixelCopyResult=${hostCaptureMetrics.lastPixelCopyResult ?: -1}")
		}
		statusView.text = status
		statusView.contentDescription = status
	}

	override fun pause() {
		if (destroyed || paused) return
		paused = true
		operationInFlight = false
		callbackGeneration += 1L
		liveWebView?.pause()
		passiveSession?.pause()
		publishStatus("paused")
	}

	override fun resume() {
		if (destroyed || !paused) return
		paused = false
		callbackGeneration += 1L
		liveWebView?.resume()
		passiveSession?.resume()
		publishStatus("running")
		webViewContainer.postOnAnimation(::runSyntheticMatrix)
	}

	override fun close() {
		if (destroyed) return
		destroyed = true
		callbackGeneration += 1L
		destroyHosts()
		publishStatus("destroyed")
	}

	private fun destroyHosts() {
		passiveSession?.close()
		passiveSession = null
		passiveHost = null
		liveWebView?.destroy()
		liveWebView = null
		webViewContainer.removeAllViews()
	}

	private fun callbackIsCurrent(generation: Long): Boolean =
		!destroyed && !paused && callbackGeneration == generation
}

private data class ReaderPassiveRasterIssuedManifest(
	val manifest: ReaderPassiveRasterCaptureManifest
)

@SuppressLint("SetJavaScriptEnabled")
private class ReaderPassiveRasterLiveWebView(
	activity: Activity,
	private val container: ViewGroup,
	private val viewportGeometry: ReaderPassiveRasterGeometry,
	private val onRendererGone: () -> Unit
) {
	private val assetLoader = WebViewAssetLoader.Builder()
		.setDomain(ReaderPassiveRasterParityAssetDomain)
		.addPathHandler(
			ReaderPassiveRasterParityAssetPrefix,
			WebViewAssetLoader.AssetsPathHandler(activity)
		)
		.build()
	private var callbackGeneration = 1L
	private var destroyed = false
	private var runtimeReady = false
	private val webView = WebView(activity)

	val isReady: Boolean
		get() = runtimeReady && !destroyed && webView.isAttachedToWindow

	init {
		require(Looper.myLooper() == Looper.getMainLooper())
		webView.apply {
			isFocusable = false
			isFocusableInTouchMode = false
			isClickable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = false
			settings.cacheMode = WebSettings.LOAD_NO_CACHE
			settings.useWideViewPort = false
			settings.loadWithOverviewMode = false
			settings.textZoom = 100
			settings.allowFileAccess = false
			settings.allowContentAccess = false
			settings.blockNetworkLoads = true
			webViewClient = object : WebViewClient() {
				override fun shouldInterceptRequest(
					view: WebView,
					request: WebResourceRequest
				): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
					?: super.shouldInterceptRequest(view, request)

				override fun onPageFinished(view: WebView, url: String?) {
					if (url == ReaderPassiveRasterParityLiveAssetUrl && !destroyed) {
						pollReady(callbackGeneration, 0)
					}
				}

				override fun onRenderProcessGone(
					view: WebView,
					detail: RenderProcessGoneDetail
				): Boolean {
					retireAfterRendererLoss()
					return true
				}
			}
		}
		container.addView(webView, 0,
			ViewGroup.LayoutParams(
				viewportGeometry.viewportWidth,
				viewportGeometry.viewportHeight
			)
		)
		webView.loadUrl(ReaderPassiveRasterParityLiveAssetUrl)
	}

	fun issueManifest(
		profileKey: String,
		targetKey: String,
		rasterGeneration: Long,
		onIssued: (ReaderPassiveRasterIssuedManifest?) -> Unit
	) {
		if (!isReady) {
			onIssued(null)
			return
		}
		val generation = callbackGeneration
		val payload = JSONObject().apply {
			put("profileKey", profileKey)
			put("targetKey", targetKey)
			put("rasterGeneration", rasterGeneration)
		}
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicLiveRasterFixture?.startLiveManifest?.($payload) ?? null)"
		) { encoded ->
			if (!callbackIsCurrent(generation)) {
				onIssued(null)
				return@evaluateJavascript
			}
			val operationId = parityJsonObject(encoded)
				?.optString("operationId")
				?.takeIf(String::isNotBlank)
			if (operationId == null) onIssued(null)
			else pollManifest(generation, operationId, 0, onIssued)
		}
	}

	private fun pollManifest(
		generation: Long,
		operationId: String,
		pollCount: Int,
		onIssued: (ReaderPassiveRasterIssuedManifest?) -> Unit
	) {
		if (!callbackIsCurrent(generation) || pollCount >= ReaderPassiveRasterParityMaximumPolls) {
			onIssued(null)
			return
		}
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicLiveRasterFixture?.readOperationResult?.(" +
				"${JSONObject.quote(operationId)}, true) ?? null)"
		) { encoded ->
			if (!callbackIsCurrent(generation)) {
				onIssued(null)
				return@evaluateJavascript
			}
			val result = parityJsonObject(encoded)
			when (result?.optString("state")) {
				"pending" -> webView.postOnAnimation {
					pollManifest(generation, operationId, pollCount + 1, onIssued)
				}
				"complete" -> {
					val value = result.optJSONObject("value")
					val runtimeObservedGeometry = parityGeometry(
						value?.optJSONObject("manifest")
							?.optJSONObject("viewportAndCaptureGeometry")
					)
					val captureGeometry = runtimeObservedGeometry?.let { observedGeometry ->
						readerPassiveRasterCanonicalCaptureGeometry(
							configuredGeometry = viewportGeometry,
							measuredWidth = webView.width,
							measuredHeight = webView.height,
							runtimeObservedGeometry = observedGeometry
						)
					}
					onIssued(
						captureGeometry?.let { geometry ->
							parseIssuedManifest(value, geometry)
						}
					)
				}
				else -> onIssued(null)
			}
		}
	}

	private fun pollReady(generation: Long, pollCount: Int) {
		if (!callbackIsCurrent(generation) || pollCount >= ReaderPassiveRasterParityMaximumPolls) return
		webView.evaluateJavascript(
			"window.NavicLiveRasterFixture?.ready === true"
		) { encoded ->
			if (!callbackIsCurrent(generation)) return@evaluateJavascript
			if (encoded == "true") {
				runtimeReady = true
			} else {
				webView.postOnAnimation { pollReady(generation, pollCount + 1) }
			}
		}
	}

	fun pause() {
		if (destroyed) return
		callbackGeneration += 1L
		runtimeReady = false
		webView.onPause()
	}

	fun resume() {
		if (destroyed) return
		callbackGeneration += 1L
		webView.onResume()
		pollReady(callbackGeneration, 0)
	}

	fun destroy() {
		if (destroyed) return
		destroyed = true
		callbackGeneration += 1L
		runtimeReady = false
		container.removeView(webView)
		webView.stopLoading()
		webView.onPause()
		webView.removeAllViews()
		webView.destroy()
	}

	private fun retireAfterRendererLoss() {
		if (destroyed) return
		destroyed = true
		callbackGeneration += 1L
		runtimeReady = false
		container.removeView(webView)
		webView.destroy()
		onRendererGone()
	}

	private fun callbackIsCurrent(generation: Long): Boolean =
		!destroyed && callbackGeneration == generation
}

private fun parityGeometry(value: JSONObject?): ReaderPassiveRasterGeometry? = runCatching {
	ReaderPassiveRasterGeometry(
		viewportWidth = value?.getInt("viewportWidth") ?: return null,
		viewportHeight = value.getInt("viewportHeight"),
		captureLeft = value.getInt("captureLeft"),
		captureTop = value.getInt("captureTop"),
		captureRight = value.getInt("captureRight"),
		captureBottom = value.getInt("captureBottom")
	)
}.getOrNull()

private fun parseIssuedManifest(
	value: JSONObject?,
	captureGeometry: ReaderPassiveRasterGeometry
): ReaderPassiveRasterIssuedManifest? =
	runCatching {
		val manifest = value?.getJSONObject("manifest") ?: return null
		ReaderPassiveRasterIssuedManifest(
			manifest = ReaderPassiveRasterCaptureManifest(
				manifestSequence = manifest.getLong("manifestSequence"),
				captureEpoch = manifest.getLong("captureEpoch"),
				liveFoliateSessionId = manifest.getString("liveFoliateSessionId"),
				publicationSessionGeneration =
					manifest.getLong("publicationSessionGeneration"),
				destinationCommitToken = manifest.getString("destinationCommitToken"),
				opaqueCaptureTarget = value.getString("captureTarget"),
				visualPageOrdinal = manifest.getInt("visualPageOrdinal"),
				rasterProfileKey = manifest.getString("rasterProfileKey"),
				paginationFingerprint = manifest.getString("paginationFingerprint"),
				layoutFingerprint = manifest.getString("layoutFingerprint"),
				decorationFingerprint = manifest.getString("decorationFingerprint"),
				viewportAndCaptureGeometry = captureGeometry,
				rasterGeneration = manifest.getLong("rasterGeneration")
			)
		)
	}.getOrNull()

private fun parityJsonObject(encoded: String?): JSONObject? = runCatching {
	val jsonText = JSONTokener(encoded).nextValue() as? String ?: return null
	JSONObject(jsonText)
}.getOrNull()

internal fun readerPassiveRasterSamplesContainVariation(samples: IntArray): Boolean {
	if (samples.size < 2) return false
	val first = samples.first()
	return samples.any { sample -> sample != first }
}

private fun readerPassiveRasterBitmapHasVariation(bitmap: Bitmap): Boolean = runCatching {
	if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
	val samplesPerAxis = 65
	val samples = IntArray(samplesPerAxis * samplesPerAxis)
	var sampleIndex = 0
	for (verticalIndex in 0 until samplesPerAxis) {
		val y = verticalIndex * (bitmap.height - 1) / (samplesPerAxis - 1)
		for (horizontalIndex in 0 until samplesPerAxis) {
			val x = horizontalIndex * (bitmap.width - 1) / (samplesPerAxis - 1)
			samples[sampleIndex] = bitmap.getPixel(x, y)
			sampleIndex += 1
		}
	}
	readerPassiveRasterSamplesContainVariation(samples)
}.getOrDefault(false)

private fun Int.incrementBoundedCounter(): Int = if (this == Int.MAX_VALUE) this else this + 1
