package paige.navic.ui.screens.reader

import paige.navic.reader.readerAssetRoot
import paige.navic.reader.readerAndroidFile
import paige.navic.reader.repoFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPassiveRasterPrototypeTest {
	@Test
	fun manifestIssuanceRequiresTheCurrentCanonicalLiveCommit() {
		val issuer = ReaderPassiveRasterManifestIssuer()
		val first = issuer.replaceCanonicalCommit(canonicalCommit())
		val firstManifest = assertNotNull(
			issuer.issue(
				liveCommit = first,
				opaqueCaptureTarget = "synthetic-target-a",
				visualPageOrdinal = 4
			)
		)
		val replacement = issuer.replaceCanonicalCommit(
			canonicalCommit().copy(destinationCommitToken = "commit-b")
		)

		assertNull(
			issuer.issue(
				liveCommit = first,
				opaqueCaptureTarget = "synthetic-target-a",
				visualPageOrdinal = 4
			)
		)
		val replacementManifest = assertNotNull(
			issuer.issue(
				liveCommit = replacement,
				opaqueCaptureTarget = "synthetic-target-b",
				visualPageOrdinal = 6
			)
		)
		assertEquals(1L, firstManifest.manifestSequence)
		assertEquals(2L, replacementManifest.manifestSequence)
		assertEquals("commit-b", replacementManifest.destinationCommitToken)
		assertEquals("synthetic-target-b", replacementManifest.opaqueCaptureTarget)

		issuer.clearCanonicalCommit()
		assertNull(
			issuer.issue(
				liveCommit = replacement,
				opaqueCaptureTarget = "synthetic-target-b",
				visualPageOrdinal = 6
			)
		)
	}

	@Test
	fun exactReceiptAndCurrentAuthorityAreAdmittedWithOnceOnlyTransfer() {
		val fixture = fixture()
		var releases = 0
		val capture = ReaderPassiveRasterCaptureResult(
			manifest = fixture.manifest,
			receipt = fixture.receipt,
			raster = ReaderPassiveRasterOwnership(37) { releases += 1 }
		)

		val admitted = assertIs<ReaderPassiveRasterAdmission.Admitted<Int>>(
			readerAdmitPassiveRaster(fixture.context, capture)
		)

		assertEquals(0, releases)
		assertEquals(37, admitted.transferRaster())
		assertNull(admitted.transferRaster())
		assertFalse(admitted.releaseRaster())
		assertEquals(0, releases)
	}

	@Test
	fun everyReceiptIdentityMismatchIsRejectedAndReleasesTheRasterExactlyOnce() {
		val fixture = fixture()
		val cases = listOf<Pair<ReaderPassiveRasterRejection, (ReaderPassiveRasterCaptureReceipt) -> ReaderPassiveRasterCaptureReceipt>>(
			ReaderPassiveRasterRejection.ManifestSequence to { it.copy(echoedManifestSequence = it.echoedManifestSequence + 1L) },
			ReaderPassiveRasterRejection.CaptureEpoch to { it.copy(echoedCaptureEpoch = it.echoedCaptureEpoch + 1L) },
			ReaderPassiveRasterRejection.LiveFoliateSession to { it.copy(echoedLiveFoliateSessionId = "other-live-session") },
			ReaderPassiveRasterRejection.PublicationGeneration to {
				it.copy(echoedPublicationSessionGeneration = it.echoedPublicationSessionGeneration + 1L)
			},
			ReaderPassiveRasterRejection.DestinationCommit to { it.copy(echoedDestinationCommitToken = "other-commit") },
			ReaderPassiveRasterRejection.OpaqueTarget to { it.copy(observedCaptureTarget = "other-target") },
			ReaderPassiveRasterRejection.VisualPageOrdinal to { it.copy(observedVisualPageOrdinal = it.observedVisualPageOrdinal + 1) },
			ReaderPassiveRasterRejection.RasterProfile to { it.copy(observedRasterProfileKey = "other-profile") },
			ReaderPassiveRasterRejection.PaginationFingerprint to {
				it.copy(observedPaginationFingerprint = "other-pagination")
			},
			ReaderPassiveRasterRejection.LayoutFingerprint to { it.copy(observedLayoutFingerprint = "other-layout") },
			ReaderPassiveRasterRejection.DecorationFingerprint to {
				it.copy(observedDecorationFingerprint = "other-decoration")
			},
			ReaderPassiveRasterRejection.Geometry to {
				it.copy(observedViewportAndCaptureGeometry = landscapeGeometry())
			},
			ReaderPassiveRasterRejection.RasterGeneration to {
				it.copy(echoedRasterGeneration = it.echoedRasterGeneration + 1L)
			},
			ReaderPassiveRasterRejection.PassiveSession to { it.copy(passiveSessionId = "other-passive-session") },
			ReaderPassiveRasterRejection.PassiveCommitSequence to {
				it.copy(passiveCommitSequence = it.passiveCommitSequence + 1L)
			}
		)

		cases.forEachIndexed { index, (expected, mutate) ->
			var releases = 0
			val capture = ReaderPassiveRasterCaptureResult(
				manifest = fixture.manifest,
				receipt = mutate(fixture.receipt),
				raster = ReaderPassiveRasterOwnership(index) { releases += 1 }
			)

			val rejected = assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(fixture.context, capture),
				expected.name
			)

			assertEquals(expected, rejected.reason, expected.name)
			assertEquals(1, releases, expected.name)
			assertFalse(capture.raster?.release() == true, expected.name)
		}
	}

	@Test
	fun everyStaleCurrentAuthorityIdentityRejectsAnOtherwiseExactReceipt() {
		val fixture = fixture()
		val cases = listOf<Pair<ReaderPassiveRasterRejection, (ReaderPassiveRasterAdmissionContext) -> ReaderPassiveRasterAdmissionContext>>(
			ReaderPassiveRasterRejection.ManifestSequence to { it.copy(expectedManifestSequence = it.expectedManifestSequence + 1L) },
			ReaderPassiveRasterRejection.CaptureEpoch to { it.copy(currentCaptureEpoch = it.currentCaptureEpoch + 1L) },
			ReaderPassiveRasterRejection.LiveFoliateSession to { it.copy(currentLiveFoliateSessionId = "replacement-live-session") },
			ReaderPassiveRasterRejection.PublicationGeneration to {
				it.copy(activePublicationSessionGeneration = it.activePublicationSessionGeneration + 1L)
			},
			ReaderPassiveRasterRejection.DestinationCommit to { it.copy(currentDestinationCommitToken = "replacement-commit") },
			ReaderPassiveRasterRejection.OpaqueTarget to { it.copy(currentOpaqueCaptureTarget = "replacement-target") },
			ReaderPassiveRasterRejection.VisualPageOrdinal to { it.copy(currentVisualPageOrdinal = it.currentVisualPageOrdinal + 1) },
			ReaderPassiveRasterRejection.RasterProfile to { it.copy(currentRasterProfileKey = "replacement-profile") },
			ReaderPassiveRasterRejection.PaginationFingerprint to {
				it.copy(currentPaginationFingerprint = "replacement-pagination")
			},
			ReaderPassiveRasterRejection.LayoutFingerprint to { it.copy(currentLayoutFingerprint = "replacement-layout") },
			ReaderPassiveRasterRejection.DecorationFingerprint to {
				it.copy(currentDecorationFingerprint = "replacement-decoration")
			},
			ReaderPassiveRasterRejection.Geometry to { it.copy(currentViewportAndCaptureGeometry = landscapeGeometry()) },
			ReaderPassiveRasterRejection.RasterGeneration to { it.copy(currentRasterGeneration = it.currentRasterGeneration + 1L) },
			ReaderPassiveRasterRejection.PassiveSession to { it.copy(activePassiveSessionId = "replacement-passive-session") },
			ReaderPassiveRasterRejection.PassiveCommitSequence to {
				it.copy(expectedPassiveCommitSequence = it.expectedPassiveCommitSequence + 1L)
			}
		)

		cases.forEachIndexed { index, (expected, mutate) ->
			var releases = 0
			val capture = ReaderPassiveRasterCaptureResult(
				manifest = fixture.manifest,
				receipt = fixture.receipt,
				raster = ReaderPassiveRasterOwnership(index) { releases += 1 }
			)

			val rejected = assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(mutate(fixture.context), capture),
				expected.name
			)

			assertEquals(expected, rejected.reason, expected.name)
			assertEquals(1, releases, expected.name)
		}
	}

	@Test
	fun receiptWithoutRasterIsRejectedWithoutTransfer() {
		val fixture = fixture()

		val rejected = assertIs<ReaderPassiveRasterAdmission.Rejected>(
			readerAdmitPassiveRaster<Int>(
				fixture.context,
				ReaderPassiveRasterCaptureResult(
					manifest = fixture.manifest,
					receipt = fixture.receipt,
					raster = null
				)
			)
		)

		assertEquals(ReaderPassiveRasterRejection.RasterUnavailable, rejected.reason)
	}

	@Test
	fun syntheticParityCoversPortraitLandscapeProfileOrientationChapterAndStaleReplacement() {
		val portrait = fixture()
		assertIs<ReaderPassiveRasterAdmission.Admitted<Int>>(
			readerAdmitPassiveRaster(portrait.context, capture(portrait, 1))
		).releaseRaster()

		val landscape = fixture(
			commit = canonicalCommit().copy(
				rasterProfileKey = "landscape-profile",
				viewportAndCaptureGeometry = landscapeGeometry()
			)
		)
		assertIs<ReaderPassiveRasterAdmission.Admitted<Int>>(
			readerAdmitPassiveRaster(landscape.context, capture(landscape, 2))
		).releaseRaster()

		val profileReplacement = capture(portrait, 3).copy(
			receipt = portrait.receipt.copy(observedRasterProfileKey = "large-type-profile")
		)
		assertEquals(
			ReaderPassiveRasterRejection.RasterProfile,
			assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(portrait.context, profileReplacement)
			).reason
		)

		val orientationReplacement = portrait.context.copy(
			currentViewportAndCaptureGeometry = landscapeGeometry()
		)
		assertEquals(
			ReaderPassiveRasterRejection.Geometry,
			assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(orientationReplacement, capture(portrait, 4))
			).reason
		)

		val chapterReplacement = portrait.context.copy(
			currentDestinationCommitToken = "chapter-boundary-commit",
			currentOpaqueCaptureTarget = "chapter-boundary-target",
			currentVisualPageOrdinal = 6
		)
		assertEquals(
			ReaderPassiveRasterRejection.DestinationCommit,
			assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(chapterReplacement, capture(portrait, 5))
			).reason
		)

		val staleSession = portrait.context.copy(currentLiveFoliateSessionId = "replacement-live-session")
		assertEquals(
			ReaderPassiveRasterRejection.LiveFoliateSession,
			assertIs<ReaderPassiveRasterAdmission.Rejected>(
				readerAdmitPassiveRaster(staleSession, capture(portrait, 6))
			).reason
		)
	}

	@Test
	fun passiveSessionAllowsOneCaptureAndReturnsOnceOwnedRaster() {
		val fixture = fixture()
		val runtime = FakePassiveRasterRuntime()
		val released = mutableListOf<Int>()
		val session = ReaderPassiveRasterPrototypeSession(runtime, released::add)
		val first = mutableListOf<ReaderPassiveRasterCaptureResult<Int>?>()

		assertTrue(session.capture(fixture.manifest, first::add))
		assertFalse(session.capture(fixture.manifest) { error("a concurrent capture must not start") })
		runtime.completeCommit()
		assertEquals(1, runtime.captureRequests)
		runtime.completeRaster(71)

		val result = assertNotNull(first.single())
		assertEquals(71, result.raster?.transfer())
		assertNull(result.raster?.transfer())
		assertTrue(released.isEmpty())
		assertEquals(
			ReaderPassiveRasterPrototypeMetrics(
				captureAttempts = 1,
				captureCompletions = 1,
				captureFailures = 0,
				staleCallbacks = 0,
				rasterReleases = 0,
				activeCaptures = 0,
				lifecycle = ReaderPassiveRasterLifecycle.Active
			),
			session.metrics()
		)
	}

	@Test
	fun pauseFencesTheCallbackAndReleasesALateRasterExactlyOnce() {
		val fixture = fixture()
		val runtime = FakePassiveRasterRuntime()
		val released = mutableListOf<Int>()
		val session = ReaderPassiveRasterPrototypeSession(runtime, released::add)
		val results = mutableListOf<ReaderPassiveRasterCaptureResult<Int>?>()
		assertTrue(session.capture(fixture.manifest, results::add))
		runtime.completeCommit()

		session.pause()
		assertEquals(
			listOf<ReaderPassiveRasterCaptureResult<Int>?>(null),
			results
		)
		assertEquals(1, runtime.pauseCalls)
		runtime.completeRaster(73)

		assertEquals(listOf(73), released)
		assertEquals(1, session.metrics().staleCallbacks)
		assertEquals(1, session.metrics().rasterReleases)
		assertEquals(ReaderPassiveRasterLifecycle.Paused, session.metrics().lifecycle)
		assertFalse(session.capture(fixture.manifest) { error("paused host must reject") })

		session.resume()
		assertEquals(1, runtime.resumeCalls)
		assertTrue(session.capture(fixture.manifest) { })
	}

	@Test
	fun destroyIsOnceOnlyAndFencesOutstandingCommitCallbacks() {
		val fixture = fixture()
		val runtime = FakePassiveRasterRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { error("no raster exists") }
		val results = mutableListOf<ReaderPassiveRasterCaptureResult<Int>?>()
		assertTrue(session.capture(fixture.manifest, results::add))

		session.close()
		session.close()
		runtime.completeCommit()

		assertEquals(
			listOf<ReaderPassiveRasterCaptureResult<Int>?>(null),
			results
		)
		assertEquals(1, runtime.destroyCalls)
		assertEquals(0, runtime.captureRequests)
		assertEquals(ReaderPassiveRasterLifecycle.Destroyed, session.metrics().lifecycle)
		assertFalse(session.capture(fixture.manifest) { error("destroyed host must reject") })
	}

	@Test
	fun physicalCaptureGeometryRequiresTheExactMeasuredWebViewSize() {
		val geometry = portraitGeometry()

		assertEquals(
			geometry,
			readerPassiveRasterPhysicalGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight
			)
		)
		assertNull(
			readerPassiveRasterPhysicalGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth + 1,
				measuredHeight = geometry.viewportHeight
			)
		)
		assertNull(
			readerPassiveRasterPhysicalGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight - 1
			)
		)
	}

	@Test
	fun canonicalCaptureGeometryValidatesJavascriptRuntimeObservationBeforeNormalization() {
		val geometry = portraitGeometry()
		val roundedRuntimeGeometry = geometry.copy(
			viewportWidth = geometry.viewportWidth + 1,
			viewportHeight = geometry.viewportHeight + 1,
			captureRight = geometry.captureRight + 1,
			captureBottom = geometry.captureBottom + 1
		)

		assertEquals(
			geometry,
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = roundedRuntimeGeometry
			)
		)
		assertNull(
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = roundedRuntimeGeometry.copy(
					viewportWidth = geometry.viewportWidth + 2,
					captureRight = geometry.captureRight + 2
				)
			)
		)
		assertNull(
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth + 1,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = roundedRuntimeGeometry
			)
		)
	}

	@Test
	fun syntheticBitmapValidationRequiresMoreThanOneSampledColor() {
		assertFalse(readerPassiveRasterSamplesContainVariation(intArrayOf()))
		assertFalse(readerPassiveRasterSamplesContainVariation(intArrayOf(0xff101010.toInt())))
		assertFalse(
			readerPassiveRasterSamplesContainVariation(
				intArrayOf(0xff101010.toInt(), 0xff101010.toInt())
			)
		)
		assertTrue(
			readerPassiveRasterSamplesContainVariation(
				intArrayOf(0xff101010.toInt(), 0xff202020.toInt())
			)
		)
	}

	@Test
	fun passiveWebViewHostAndAssetsExposeOnlyTheRasterChannel() {
		val hostText = readerAndroidFile("ReaderPassiveRasterWebViewHost.android.kt").readText()
		val prototypeText = readerAndroidFile("ReaderPassiveRasterPrototype.android.kt").readText()
		val assetRoot = readerAssetRoot().resolve("passive-raster-prototype")
		val html = assetRoot.resolve("index.html")
		val script = assetRoot.resolve("passive-raster-prototype.js")
		val passiveSession = assetRoot.resolve("passive-raster-foliate-session.js")
		val liveHtml = assetRoot.resolve("live-fixture.html")
		val liveScript = assetRoot.resolve("live-raster-fixture.js")
		val passiveChannelText = listOf(
			hostText,
			prototypeText,
			html.readText(),
			script.readText(),
			passiveSession.readText()
		).joinToString("\n")

		assertTrue(html.isFile)
		assertTrue(script.isFile)
		assertTrue(passiveSession.isFile)
		assertTrue(liveHtml.isFile)
		assertTrue(liveScript.isFile)
		assertContains(html.readText(), "Synthetic raster page")
		assertContains(html.readText(), "passive-raster-prototype.js")
		assertContains(script.readText(), "commitCapture")
		assertContains(script.readText(), "NavicPassiveRasterPrototype")
		assertFalse(passiveChannelText.contains("startLiveManifest"))
		assertFalse(passiveChannelText.contains("issueLiveManifest"))
		assertFalse(passiveChannelText.contains("NavicLiveRasterFixture"))
		assertContains(liveHtml.readText(), "live-raster-fixture.js")
		assertContains(liveScript.readText(), "startLiveManifest")
		assertContains(liveScript.readText(), "NavicLiveRasterFixture")
		assertContains(hostText, "private val webView")
		assertContains(hostText, "evaluateJavascript(")
		assertContains(hostText, "PixelCopy.request(")
		assertContains(hostText, "onRenderProcessGone")
		assertContains(hostText, "onRendererGone")
		assertContains(hostText, "webView.destroy()")
		listOf(
			"ReaderJavascriptBridge",
			"NavicAndroidBridge",
			"ReaderWebRuntime.configure",
			"addJavascriptInterface",
			"ReaderForegroundWebViewOwnership",
			"findReaderWebView",
			"preparePageTurnPreview",
			"exposePageTurnPreview",
			"restorePageTurnPreview",
			"confirmPageTurnPreview",
			"postMessage",
			"ReaderBridgeEvent",
			"locationChanged",
			"visibleTextRange",
			"overlayFragment",
			"selectionChanged",
			"WordSync",
			"ReaderPageTurnBundleSource",
			"ReaderPageRasterCache",
			"submitDeck"
		).forEach { forbidden ->
			assertFalse(
				passiveChannelText.contains(forbidden),
				"The passive raster prototype must not expose forbidden channel '$forbidden'."
			)
		}
	}

	@Test
	fun correctedPrototypeUsesFoliateRuntimeObservationsAndBoundedPolling() {
		val assetRoot = readerAssetRoot().resolve("passive-raster-prototype")
		val entrypoint = assetRoot.resolve("passive-raster-prototype.js").readText()
		val coreSessionFile = assetRoot.resolve("synthetic-raster-foliate-session.js")
		val coreSession = coreSessionFile.readText()
		val passiveSessionFile = assetRoot.resolve("passive-raster-foliate-session.js")
		val passiveSession = passiveSessionFile.readText()
		val liveFixture = assetRoot.resolve("live-raster-fixture.js").readText()
		val host = readerAndroidFile("ReaderPassiveRasterWebViewHost.android.kt").readText()

		assertTrue(coreSessionFile.isFile)
		assertTrue(passiveSessionFile.isFile)
		assertContains(coreSession, "../vendor/foliate-js/view.js")
		assertContains(coreSession, "document.createElement('foliate-view')")
		assertContains(coreSession, "createSyntheticPublication")
		assertContains(coreSession, ".open(publication)")
		assertContains(coreSession, ".commitTextPage(")
		assertContains(coreSession, ".validateTextPageCommit(")
		assertFalse(coreSession.contains("this.view.goTo(resolvedTarget.href)"))
		assertContains(coreSession, "exactTextPagePosition()")
		assertContains(passiveSession, "observedCaptureTarget: observation.opaqueCaptureTarget")
		assertContains(passiveSession, "observedVisualPageOrdinal: observation.visualPageOrdinal")
		assertContains(passiveSession, "observedRasterProfileKey: observation.rasterProfileKey")
		assertContains(passiveSession, "observedPaginationFingerprint: observation.paginationFingerprint")
		assertContains(passiveSession, "observedLayoutFingerprint: observation.layoutFingerprint")
		assertContains(passiveSession, "observedDecorationFingerprint: observation.decorationFingerprint")
		assertContains(
			passiveSession,
			"observedViewportAndCaptureGeometry: observation.viewportAndCaptureGeometry"
		)
		assertFalse(
			passiveSession.contains("observedVisualPageOrdinal: manifest.visualPageOrdinal")
		)
		assertFalse(
			passiveSession.contains("observedPaginationFingerprint: manifest.paginationFingerprint")
		)
		assertFalse(entrypoint.contains("startLiveManifest"))
		assertContains(entrypoint, "startCapture")
		assertContains(entrypoint, "readOperationResult")
		assertContains(liveFixture, "startLiveManifest")
		assertContains(host, "ReaderPassiveRasterMaximumResultPolls")
		assertContains(host, "readOperationResult")
		assertContains(host, "postOnAnimation")
		assertContains(host, "getJSONObject(\"observedViewportAndCaptureGeometry\")")
		assertContains(host, "readerPassiveRasterCanonicalCaptureGeometry")
		assertFalse(host.contains("Base64"))
		assertFalse(host.contains("opaqueCaptureTarget.split"))
		assertFalse(host.contains("opaqueCaptureTarget.substring"))
	}

	@Test
	fun readerDevParityActivityIsLaunchableOnlyFromReaderDevManifest() {
		val mainManifest = repoFile("androidApp/src/main/AndroidManifest.xml").readText()
		val readerDevManifest = repoFile("androidApp/src/readerDev/AndroidManifest.xml").readText()
		val activity = repoFile(
			"androidApp/src/readerDev/kotlin/paige/navic/androidApp/" +
				"ReaderPassiveRasterParityActivity.kt"
		).readText()
		val harness = readerAndroidFile("ReaderPassiveRasterParityHarness.android.kt").readText()
		val host = readerAndroidFile("ReaderPassiveRasterWebViewHost.android.kt").readText()

		assertFalse(mainManifest.contains("ReaderPassiveRasterParityActivity"))
		assertContains(readerDevManifest, ".ReaderPassiveRasterParityActivity")
		assertContains(activity, "createReaderPassiveRasterParityHarness")
		assertContains(harness, "ReaderPassiveRasterLiveWebView")
		assertContains(harness, "ReaderPassiveRasterWebViewHost")
		assertContains(harness, "container.addView(webView, 0,")
		assertContains(harness, "NavicLiveRasterFixture")
		assertContains(harness, "live-fixture.html")
		assertContains(harness, "runSyntheticMatrix")
		assertContains(harness, "rendererLosses")
		assertContains(harness, "renderer-recovering")
		assertContains(harness, "onRenderProcessGone")
		assertContains(harness, "PixelCopy")
		assertContains(harness, "captureAttempts")
		assertContains(harness, "captureSuccesses")
		assertContains(harness, "captureFailures")
		assertContains(harness, "staleRasterGenerationChecks")
		assertContains(harness, "staleRasterGenerationPasses")
		assertContains(harness, "bitmapValidationSuccesses")
		assertContains(harness, "bitmapValidationFailures")
		assertContains(harness, "transferRaster()")
		assertContains(host, "ReaderPassiveRasterOffscreenWindow")
		assertContains(host, "createVirtualDisplay")
		assertContains(host, "VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY")
		assertContains(host, "Presentation(")
		assertContains(host, "ImageReader.newInstance")
		assertContains(host, "windowAnimations = 0")
		assertContains(host, "offscreenWindow.captureWindow")
		assertFalse(host.contains("Dialog(activity)"))
		assertFalse(host.contains("activity.resources.displayMetrics.widthPixels +"))
		assertContains(host, "readerPassiveRasterCreateBitmap")
		assertFalse(harness.contains("opaqueCaptureTarget="))
		assertFalse(harness.contains("destinationCommitToken="))
		assertFalse(harness.contains("addJavascriptInterface"))
		assertFalse(harness.contains("postMessage"))
	}

	@Test
	fun readerDevParityStatusDistinguishesPrivacySafeCaptureFailureBoundaries() {
		val host = readerAndroidFile("ReaderPassiveRasterWebViewHost.android.kt").readText()
		val harness = readerAndroidFile("ReaderPassiveRasterParityHarness.android.kt").readText()
		val statusChannel = "$host\n$harness"

		assertContains(host, "ReaderPassiveRasterWebViewCaptureMetrics")
		assertContains(host, "ReaderPassiveRasterWebViewPreconditionFailure")
		assertContains(host, "captureMetrics()")
		assertContains(host, "preconditionFailures")
		assertContains(host, "lastPreconditionFailure")
		assertContains(host, "lastGeometryWidthDelta")
		assertContains(host, "lastGeometryHeightDelta")
		assertContains(host, "ViewSizeMismatch")
		assertContains(host, "WindowBounds")
		assertContains(host, "pixelCopyAttempts")
		assertContains(host, "pixelCopySuccesses")
		assertContains(host, "pixelCopyFailures")
		assertContains(host, "lastPixelCopyResult")
		assertContains(host, "lastCaptureLatencyMillis")
		assertContains(host, "maximumCaptureLatencyMillis")
		assertContains(harness, "lastFailurePhase")
		assertContains(harness, "hostCaptureMetrics")
		assertContains(harness, "lastPreconditionFailure")
		assertContains(harness, "lastGeometryWidthDelta")
		assertContains(harness, "lastGeometryHeightDelta")
		assertContains(harness, "ReaderPassiveRasterParityMaximumPolls = 1_800")
		listOf(
			"opaqueCaptureTarget=",
			"destinationCommitToken=",
			"liveFoliateSessionId=",
			"publicationSessionGeneration=",
			"rasterProfileKey="
		).forEach { forbidden -> assertFalse(statusChannel.contains(forbidden), forbidden) }
	}

	private data class Fixture(
		val manifest: ReaderPassiveRasterCaptureManifest,
		val receipt: ReaderPassiveRasterCaptureReceipt,
		val context: ReaderPassiveRasterAdmissionContext
	)

	private fun fixture(
		commit: ReaderPassiveRasterCanonicalCommit = canonicalCommit()
	): Fixture {
		val issuer = ReaderPassiveRasterManifestIssuer()
		val liveCommit = issuer.replaceCanonicalCommit(commit)
		val manifest = assertNotNull(
			issuer.issue(liveCommit, "synthetic-target-a", 4)
		)
		val receipt = receiptFor(
			manifest = manifest,
			passiveSessionId = "passive-session-a",
			passiveCommitSequence = 12L
		)
		return Fixture(
			manifest = manifest,
			receipt = receipt,
			context = ReaderPassiveRasterAdmissionContext(
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
				currentRasterGeneration = manifest.rasterGeneration,
				activePassiveSessionId = receipt.passiveSessionId,
				expectedPassiveCommitSequence = receipt.passiveCommitSequence
			)
		)
	}

	private fun canonicalCommit() = ReaderPassiveRasterCanonicalCommit(
		captureEpoch = 8L,
		liveFoliateSessionId = "live-session-a",
		publicationSessionGeneration = 13L,
		destinationCommitToken = "commit-a",
		rasterProfileKey = "portrait-profile",
		paginationFingerprint = "pagination-a",
		layoutFingerprint = "layout-a",
		decorationFingerprint = "decoration-a",
		viewportAndCaptureGeometry = portraitGeometry(),
		rasterGeneration = 21L
	)

	private fun portraitGeometry() = ReaderPassiveRasterGeometry(
		viewportWidth = 800,
		viewportHeight = 1200,
		captureLeft = 0,
		captureTop = 0,
		captureRight = 800,
		captureBottom = 1200
	)

	private fun landscapeGeometry() = ReaderPassiveRasterGeometry(
		viewportWidth = 1600,
		viewportHeight = 1000,
		captureLeft = 0,
		captureTop = 0,
		captureRight = 1600,
		captureBottom = 1000
	)

	private fun receiptFor(
		manifest: ReaderPassiveRasterCaptureManifest,
		passiveSessionId: String,
		passiveCommitSequence: Long
	) = ReaderPassiveRasterCaptureReceipt(
		passiveSessionId = passiveSessionId,
		echoedManifestSequence = manifest.manifestSequence,
		echoedCaptureEpoch = manifest.captureEpoch,
		echoedLiveFoliateSessionId = manifest.liveFoliateSessionId,
		echoedPublicationSessionGeneration = manifest.publicationSessionGeneration,
		echoedDestinationCommitToken = manifest.destinationCommitToken,
		observedCaptureTarget = manifest.opaqueCaptureTarget,
		observedVisualPageOrdinal = manifest.visualPageOrdinal,
		observedRasterProfileKey = manifest.rasterProfileKey,
		observedPaginationFingerprint = manifest.paginationFingerprint,
		observedLayoutFingerprint = manifest.layoutFingerprint,
		observedDecorationFingerprint = manifest.decorationFingerprint,
		observedViewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
		echoedRasterGeneration = manifest.rasterGeneration,
		passiveCommitSequence = passiveCommitSequence
	)

	private fun capture(fixture: Fixture, value: Int) = ReaderPassiveRasterCaptureResult(
		manifest = fixture.manifest,
		receipt = fixture.receipt,
		raster = ReaderPassiveRasterOwnership(value) { }
	)

	private inner class FakePassiveRasterRuntime : ReaderPassiveRasterRuntimePort<Int> {
		override val passiveSessionId = "passive-session-a"
		override var isReady = true
		var pauseCalls = 0
		var resumeCalls = 0
		var destroyCalls = 0
		var captureRequests = 0
		private var manifest: ReaderPassiveRasterCaptureManifest? = null
		private var commitSequence = 0L
		private var commitCallback: ((ReaderPassiveRasterCaptureReceipt?) -> Unit)? = null
		private var rasterCallback: ((Int?) -> Unit)? = null

		override fun commit(
			manifest: ReaderPassiveRasterCaptureManifest,
			captureTarget: String,
			passiveCommitSequence: Long,
			onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit
		) {
			assertEquals(manifest.opaqueCaptureTarget, captureTarget)
			this.manifest = manifest
			commitSequence = passiveCommitSequence
			commitCallback = onCommitted
		}

		override fun capture(
			geometry: ReaderPassiveRasterGeometry,
			onCaptured: (Int?) -> Unit
		) {
			captureRequests += 1
			rasterCallback = onCaptured
		}

		override fun pause() {
			pauseCalls += 1
		}

		override fun resume() {
			resumeCalls += 1
		}

		override fun destroy() {
			destroyCalls += 1
		}

		fun completeCommit() {
			val callback = assertNotNull(commitCallback)
			commitCallback = null
			callback(
				receiptFor(
					manifest = assertNotNull(manifest),
					passiveSessionId = passiveSessionId,
					passiveCommitSequence = commitSequence
				)
			)
		}

		fun completeRaster(value: Int) {
			val callback = assertNotNull(rasterCallback)
			rasterCallback = null
			callback(value)
		}
	}
}
