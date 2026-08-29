package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.FrameLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderEngineHostCommand
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderPaginationProfileStatus
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderWhispersyncCueMapCue
import paige.navic.reader.ReaderWhispersyncCueMapPresentation
import paige.navic.reader.defaultReaderSettings
import paige.navic.reader.readerPageRasterSnapshotKey
import paige.navic.reader.readerPageTurnContentReadyKey
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPassiveRasterPrototypeTest {
	@Test
	fun missingBridgeResultIsTypedAsTerminalRequestUnavailability() {
		assertEquals(
			ReaderPassiveRasterManifestResolution.Unavailable(
				ReaderPassiveRasterManifestUnavailableCause.BridgeRequestUnavailable
			),
			readerPassiveRasterManifestResolution("\"null\"")
		)
	}

	@Test
	fun missingCanonicalRenderedDestinationRetainsItsRetryAuthority() {
		val encoded = JSONObject.quote(
			JSONObject()
				.put("failureReason", "canonical-rendered-destination-absent")
				.toString()
		)

		assertEquals(
			ReaderPassiveRasterManifestResolution.Unavailable(
				ReaderPassiveRasterManifestUnavailableCause.CanonicalRenderedDestinationAbsent
			),
			readerPassiveRasterManifestResolution(encoded)
		)
	}

	@Test
	fun transientCurrentLiveProfileGapRetainsItsLayoutRetryAuthority() {
		val encoded = JSONObject.quote(
			JSONObject()
				.put("failureReason", "current-live-profile-or-layout-unavailable")
				.toString()
		)

		assertEquals(
			ReaderPassiveRasterManifestResolution.Unavailable(
				ReaderPassiveRasterManifestUnavailableCause.CurrentLiveProfileOrLayoutUnavailable
			),
			readerPassiveRasterManifestResolution(encoded)
		)
	}

	@Test
	fun detachedLiveWebViewRetainsItsAttachmentRetryAuthority() {
		var resolution: ReaderPassiveRasterManifestResolution? = null
		ReaderPageLivePassiveRasterManifestPort { null }.request(
			visualPageOrdinal = 4,
			captureEpoch = 8L,
			rasterGeneration = 12L,
			preparationGeneration = 16L,
			onResolved = { resolution = it }
		)

		assertEquals(
			ReaderPassiveRasterManifestResolution.Unavailable(
				ReaderPassiveRasterManifestUnavailableCause.LiveWebViewDetached
			),
			resolution
		)
	}

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
	fun passiveRealizedReceiptAdmitsItsObservedProfileAgainstTheCurrentLivePlan() {
		val fixture = fixture(
			canonicalCommit().copy(
				profileAuthority = ReaderPassiveRasterProfileAuthority.PassiveRealized
			)
		)
		val observedReceipt = fixture.receipt.copy(
			observedRasterProfileKey = "passive-observed-profile",
			observedPaginationFingerprint = "passive-observed-pagination",
			observedLayoutFingerprint = "passive-observed-layout",
			observedDecorationFingerprint = "passive-observed-decoration"
		)
		val capture = ReaderPassiveRasterCaptureResult(
			manifest = fixture.manifest,
			receipt = observedReceipt,
			raster = ReaderPassiveRasterOwnership(41) { }
		)

		val admitted = assertIs<ReaderPassiveRasterAdmission.Admitted<Int>>(
			readerAdmitPassiveRaster(fixture.context, capture)
		)

		assertSame(observedReceipt, admitted.receipt)
		assertEquals(41, admitted.transferRaster())
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
			ReaderPassiveRasterRejection.ProfileAuthority to {
				it.copy(
					currentProfileAuthority =
						ReaderPassiveRasterProfileAuthority.PassiveRealized
				)
			},
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
	fun cueMapPresentationUpdatesDoNotRecreateViewerOrRequestAnotherProductionRasterCapture() {
		val fixture = fixture()
		val runtime = FakePassiveRasterRuntime()
		val released = mutableListOf<Int>()
		val session = ReaderPassiveRasterPrototypeSession(runtime, released::add)
		val viewerSlot = ReaderViewerLifecycleSlot { viewState ->
			check(
				session.capture(fixture.manifest) { capture ->
					capture?.raster?.release()
				}
			)
			readerViewerFor(viewState)
		}
		val settings = defaultReaderSettings()
		val destination = ReaderDestinationCommitIdentity("session-a", 41L)
		val presentation = ReaderWhispersyncCueMapPresentation(
			enabled = true,
			revisionDigest = "5f04c2a19e7d",
			presentationGeneration = 8L,
			destinationCommitIdentity = destination,
			cues = listOf(
				ReaderWhispersyncCueMapCue(
					sourceOrdinal = 4,
					textHref = "Text/chapter.xhtml",
					textStart = 5,
					textEnd = 8
				)
			),
			preparedSourceOrdinal = 4
		)
		val initial = ReaderEngineViewState.WebViewPublication(
			publicationUrl = "https://appassets.androidplatform.net/reader-cache/book-1/publication.epub",
			title = "Reader",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = true,
			externalShellCover = false,
			nativeShellCoverUrl = null,
			canReturnToShellCover = false,
			settings = settings,
			startLocator = null
		)
		val paginationProfile = ReaderPaginationProfileStatus(
			status = "ready",
			fingerprint = "pagination-a",
			pageCount = 12
		)
		val initialViewer = viewerSlot.update(initial)
		runtime.completeCommit()
		runtime.completeRaster(81)
		val baselineCaptureRequests = runtime.captureRequests
		val baselineOwnership = Triple(
			initialViewer.key,
			settings.readerPageRasterSnapshotKey(),
			readerPageTurnContentReadyKey(paginationProfile)
		)

		val enabled = initial.copy(
			command = ReaderEngineHostCommand.FoliateBridge(
				ReaderBridgeCommand.ReplaceWhispersyncCueMap(presentation)
			),
			commandKey = 1L
		)
		val enabledViewer = viewerSlot.update(enabled)
		val styleUpdated = enabled.copy(
			command = ReaderEngineHostCommand.FoliateBridge(
				ReaderBridgeCommand.ReplaceWhispersyncCueMap(
					presentation.copy(
						audioActiveSourceOrdinal = 4,
						renderedHighlightSourceOrdinal = 4
					)
				)
			),
			commandKey = 2L
		)
		val updatedViewer = viewerSlot.update(styleUpdated)
		val updatedOwnership = Triple(
			updatedViewer.key,
			styleUpdated.settings.readerPageRasterSnapshotKey(),
			readerPageTurnContentReadyKey(paginationProfile)
		)

		assertEquals(1, baselineCaptureRequests)
		assertEquals(0, runtime.captureRequests - baselineCaptureRequests)
		assertEquals(initialViewer.key, enabledViewer.key)
		assertEquals(baselineOwnership, updatedOwnership)
		viewerSlot.dispose()
		session.close()
	}

	@Test
	fun cancelledCommitDrainsBeforeAReplacementCaptureCanStart() {
		val fixture = fixture()
		val runtime = FakePassiveRasterRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { error("no raster exists") }
		val first = mutableListOf<ReaderPassiveRasterCaptureResult<Int>?>()

		assertTrue(session.capture(fixture.manifest, first::add))
		assertTrue(session.cancelActiveCapture())

		assertEquals(listOf<ReaderPassiveRasterCaptureResult<Int>?>(null), first)
		assertEquals(1, runtime.cancelCommitCalls)
		assertFalse(session.isReady)
		assertFalse(session.capture(fixture.manifest) { error("draining capture must reject replacement") })

		runtime.completeCommitCancellation()

		assertTrue(session.isReady)
		assertTrue(session.capture(fixture.manifest) { })
	}

	@Test
	fun cancellationRequestFailureDestroysSessionBeforeItCanBeReused() {
		val fixture = fixture()
		val runtime = FakePassiveRasterRuntime().apply {
			cancelCommitFailure = IllegalStateException("cancellation-dispatch-failed")
		}
		val session = ReaderPassiveRasterPrototypeSession(runtime) { error("no raster exists") }
		val results = mutableListOf<ReaderPassiveRasterCaptureResult<Int>?>()

		assertTrue(session.capture(fixture.manifest, results::add))
		assertTrue(session.cancelActiveCapture())

		assertEquals(listOf<ReaderPassiveRasterCaptureResult<Int>?>(null), results)
		assertEquals(1, runtime.cancelCommitCalls)
		assertEquals(1, runtime.destroyCalls)
		assertEquals(ReaderPassiveRasterLifecycle.Destroyed, session.metrics().lifecycle)
		assertFalse(session.isReady)
		assertFalse(session.capture(fixture.manifest) { error("destroyed session must reject replacement") })

		runtime.completeCommit()
		session.close()

		assertEquals(listOf<ReaderPassiveRasterCaptureResult<Int>?>(null), results)
		assertEquals(1, runtime.destroyCalls)
		assertEquals(1, session.metrics().staleCallbacks)
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
		assertEquals(0, runtime.pauseCalls)
		runtime.completeRaster(73)

		assertEquals(1, runtime.pauseCalls)
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
	fun canonicalCaptureGeometryNormalizesOnePixelFullFrameRounding() {
		val geometry = portraitGeometry()
		val positiveRounding = geometry.copy(
			viewportWidth = geometry.viewportWidth + 1,
			viewportHeight = geometry.viewportHeight + 1,
			captureRight = geometry.captureRight + 1,
			captureBottom = geometry.captureBottom + 1
		)
		val negativeRounding = geometry.copy(
			viewportWidth = geometry.viewportWidth - 1,
			viewportHeight = geometry.viewportHeight - 1,
			captureRight = geometry.captureRight - 1,
			captureBottom = geometry.captureBottom - 1
		)

		assertSame(
			geometry,
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = geometry
			)
		)
		assertEquals(
			geometry,
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = positiveRounding
			)
		)
		assertEquals(
			geometry,
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = negativeRounding
			)
		)
		assertNull(
			readerPassiveRasterCanonicalCaptureGeometry(
				configuredGeometry = geometry,
				measuredWidth = geometry.viewportWidth + 1,
				measuredHeight = geometry.viewportHeight,
				runtimeObservedGeometry = geometry
			)
		)
	}

	@Test
	fun rendererLossFenceRejectsAnOldRuntimeAfterSameGeometryReplacement() {
		val fenceType = Class.forName(
			"paige.navic.ui.screens.reader.ReaderPassiveRasterRendererLossFence"
		)
		val fence = fenceType.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
		val replace = fenceType.getDeclaredMethod("replace", Any::class.java).apply {
			isAccessible = true
		}
		val isCurrent = fenceType.getDeclaredMethod("isCurrent", Any::class.java).apply {
			isAccessible = true
		}
		val firstRuntimeIdentity = Any()
		val replacementRuntimeIdentity = Any()

		replace.invoke(fence, firstRuntimeIdentity)
		assertTrue(isCurrent.invoke(fence, firstRuntimeIdentity) as Boolean)
		replace.invoke(fence, replacementRuntimeIdentity)

		assertFalse(isCurrent.invoke(fence, firstRuntimeIdentity) as Boolean)
		assertTrue(isCurrent.invoke(fence, replacementRuntimeIdentity) as Boolean)
	}

	@Test
	fun confirmedDecklessAuthorityLetsAsyncReadyRetryUsePassiveWithoutASecondLiveMutation() = runTest {
		val fence = ReaderConfirmedDecklessPassiveAuthority()
		val ownership = ReaderForegroundWebViewOwnership()
		val sessionId = "deckless-session"
		val ordinal = 7
		val rasterGeneration = 31L
		val claim = ownership.acquireExclusiveLive(rasterGeneration)
		val mutation = assertNotNull(ownership.beginLiveMutation(claim))
		var liveMutationCount = 1
		var passiveRequestCount = 0
		var passiveHostReady = false
		val retryRequested = CompletableDeferred<Unit>()
		val retry = backgroundScope.launch {
			retryRequested.await()
			val confirmedAuthorityIsCurrent = fence.isCurrent(
				foliateSessionId = sessionId,
				visualPageOrdinal = ordinal,
				rasterGeneration = rasterGeneration,
				attached = true,
				foregroundMutationIsCurrent =
					ownership::isMutationGenerationCurrent
			)
			if (confirmedAuthorityIsCurrent && passiveHostReady) {
				passiveRequestCount += 1
			} else {
				val retryClaim = ownership.acquireExclusiveLive(rasterGeneration + 1L)
				assertNotNull(ownership.beginLiveMutation(retryClaim))
				liveMutationCount += 1
				assertTrue(ownership.releaseLive(retryClaim))
			}
		}

		passiveHostReady = true
		fence.confirm(
			foliateSessionId = sessionId,
			visualPageOrdinal = ordinal,
			rasterGeneration = rasterGeneration,
			liveTargetToken = "initial-live-31-${mutation.value}",
			foregroundMutationGeneration = mutation.value
		)
		assertTrue(ownership.releaseLive(claim))
		retryRequested.complete(Unit)
		advanceUntilIdle()
		retry.join()

		assertEquals(1, liveMutationCount)
		assertEquals(1, passiveRequestCount)
	}

	@Test
	fun confirmedDecklessAuthorityFailsClosedAcrossEveryLiveIdentityFence() {
		val fence = ReaderConfirmedDecklessPassiveAuthority()
		val currentMutation: (Long) -> Boolean = { generation -> generation == 41L }
		fence.confirm(
			foliateSessionId = "session-a",
			visualPageOrdinal = 3,
			rasterGeneration = 9L,
			liveTargetToken = "target-a",
			foregroundMutationGeneration = 41L
		)
		fun current(
			sessionId: String = "session-a",
			ordinal: Int = 3,
			rasterGeneration: Long = 9L,
			attached: Boolean = true,
			mutationIsCurrent: (Long) -> Boolean = currentMutation
		): Boolean = fence.isCurrent(
			foliateSessionId = sessionId,
			visualPageOrdinal = ordinal,
			rasterGeneration = rasterGeneration,
			attached = attached,
			foregroundMutationIsCurrent = mutationIsCurrent
		)

		assertTrue(current())
		assertFalse(current(sessionId = "session-b"))
		assertFalse(current(ordinal = 4))
		assertFalse(current(rasterGeneration = 10L))
		assertFalse(current(attached = false))
		assertFalse(current(mutationIsCurrent = { false }))
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
		val productionSession = assetRoot.resolve("production-raster-foliate-session.js")
		val liveHtml = assetRoot.resolve("live-fixture.html")
		val liveScript = assetRoot.resolve("live-raster-fixture.js")
		val passiveChannelText = listOf(
			hostText,
			prototypeText,
			html.readText(),
			script.readText(),
			passiveSession.readText(),
			productionSession.readText()
		).joinToString("\n")

		assertTrue(html.isFile)
		assertTrue(script.isFile)
		assertTrue(passiveSession.isFile)
		assertTrue(productionSession.isFile)
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
	fun cancellationDispatchUncertaintyRetiresWebViewBeforeReportingDrain() {
		val host = readerAndroidFile("ReaderPassiveRasterWebViewHost.android.kt").readText()
		val dispatchCancellation = host
			.substringAfter("private fun dispatchCommitCancellation")
			.substringBefore("private fun pollRuntimeReady")
		val cancellationFailure = dispatchCancellation
			.substringAfter("catch (_: Throwable) {")
			.substringBefore("}")
		val pollCommit = host
			.substringAfter("private fun pollCommitResult")
			.substringBefore("private fun commitIsCurrent")
		val timeout = pollCommit
			.substringAfter("if (pollCount >= ReaderPassiveRasterMaximumResultPolls) {")
			.substringBefore("val operationId")
		val retirement = host
			.substringAfter("private fun retireAfterRendererLoss()")
			.substringBefore("private fun requestPixelCopy")

		assertContains(cancellationFailure, "retireAfterRendererLoss()")
		assertFalse(cancellationFailure.contains("finishCommit(commit, null)"))
		assertContains(timeout, "retireAfterRendererLoss()")
		assertFalse(timeout.contains("finishCommit(commit, null)"))
		assertTrue(
			retirement.indexOf("webView.destroy()") < retirement.indexOf("retireActiveCommit()"),
			"The unhealthy WebView must be destroyed before cancellation drain callbacks run."
		)
	}

	@Test
	fun runtimeReadinessTimeoutRetiresTheUnusableAdapter() {
		val host = readerAndroidFile("ReaderPassiveRasterWebViewHost.android.kt").readText()
		val readinessPoll = host
			.substringAfter("private fun pollRuntimeReady")
			.substringBefore("private fun pollCommitResult")
		val staleGenerationCheck = "if (!callbackIsCurrent(generation)) return"
		val timeoutCheck = "if (pollCount >= ReaderPassiveRasterMaximumResultPolls) {"
		val timeout = readinessPoll
			.substringAfter(timeoutCheck)
			.substringBefore("webView.evaluateJavascript")

		assertContains(readinessPoll, staleGenerationCheck)
		assertContains(readinessPoll, timeoutCheck)
		assertTrue(
			readinessPoll.indexOf(staleGenerationCheck) < readinessPoll.indexOf(timeoutCheck),
			"A stale readiness callback must not retire its replacement runtime."
		)
		assertContains(timeout, "retireAfterRendererLoss()")
	}

	@Test
	fun ordinaryCommitTimeoutRetiresRuntimeBeforeReportingCompletion() {
		val retirement = ReaderPassiveRasterUncertainCommitRetirement()
		val events = mutableListOf<String>()
		var runtimeReusable = true
		val retireRuntime: () -> Unit = {
			runtimeReusable = false
			events += "runtime-retired"
		}
		val reportCompletion: () -> Unit = {
			assertFalse(runtimeReusable)
			events += "completion-reported"
		}

		retirement.retireBeforeCompletion(retireRuntime, reportCompletion)
		retirement.retireBeforeCompletion(retireRuntime, reportCompletion)

		assertEquals(
			listOf("runtime-retired", "completion-reported"),
			events,
			"Timeout recovery must be ordered and once-only."
		)
	}

	@Test
	fun sameGeometryOwnerRecreatesOnlyARetiredPassiveAdapter() {
		val owner = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val adapter = readerAndroidFile("ReaderPassiveRasterPreparationAdapter.android.kt").readText()
		val prototype = readerAndroidFile("ReaderPassiveRasterPrototype.android.kt").readText()
		val replacement = owner
			.substringAfter("private fun replacePassiveRasterPreparationAdapter")
			.substringBefore("private fun closePassiveRasterPreparationAdapter")

		assertContains(replacement, "passiveRasterPreparationAdapter?.isRetired == false")
		assertContains(adapter, "override val isRetired")
		assertContains(prototype, "val isRetired")
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

	@Test
	fun bundleSourcePublishesOnlyAnExactlyMatchingPassiveCapture() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		val activity = activityController.get()
		val source = ReaderPageTurnBundleSource()
		source.updateBitmapQuality(ReaderPageBitmapQuality.Native)
		val commit = canonicalCommit().copy(
			profileAuthority = ReaderPassiveRasterProfileAuthority.PassiveRealized,
			viewportAndCaptureGeometry = ReaderPassiveRasterGeometry(
				viewportWidth = 20,
				viewportHeight = 30,
				captureLeft = 0,
				captureTop = 0,
				captureRight = 20,
				captureBottom = 30
			),
			rasterGeneration = source.currentGeneration()
		)
		val captureFixture = fixture(commit)
		val descriptor = passiveDescriptor(commit, captureFixture.manifest.visualPageOrdinal)
		val webView = PassiveDescriptorWebView(activity, descriptor)
		val host = FrameLayout(activity).also { it.addView(webView) }
		activity.setContentView(host)
		webView.layout(0, 0, 20, 30)
		Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		try {
			val reference = assertNotNull(cachePassiveReference(source))
			source.initializeRasterCache(webView)
			registerActiveBundleWebView(source, webView, reference)
			var ownershipReleases = 0
			val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
			val candidateLayout = ReaderPageSlideSnapshot(
				key = reference.key.copy(visualPageIndex = captureFixture.manifest.visualPageOrdinal),
				bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
				surfaceRectInWindow = Rect(reference.surfaceRectInWindow),
				leafGeometry = reference.leafGeometry,
				reverseFaceColor = reference.reverseFaceColor
			)
			assertTrue(readerPageRasterPhysicalLayoutMatches(candidateLayout, reference))
			candidateLayout.releaseCacheOwnership()
			val capture = ReaderPassiveRasterCaptureResult(
				manifest = captureFixture.manifest,
				receipt = captureFixture.receipt.copy(
					observedRasterProfileKey = "passive-observed-profile",
					observedPaginationFingerprint = "passive-observed-pagination",
					observedLayoutFingerprint = "passive-observed-layout",
					observedDecorationFingerprint = "passive-observed-decoration"
				),
				raster = ReaderPassiveRasterOwnership(bitmap) {
					ownershipReleases += 1
					if (!bitmap.isRecycled) bitmap.recycle()
				}
			)
			val published = CompletableDeferred<ReaderPageRasterPublicationResult>()
			var publicationCount = 0

			source.admitPassiveRasterCapture(
				capture = capture,
				currentAuthority = passiveAuthority(captureFixture, descriptor),
				pageIndex = captureFixture.manifest.visualPageOrdinal,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = reference,
				priority = ReaderPageRasterPriority.NextChapter,
				isStillCurrent = { true },
				onPublished = {
					publicationCount += 1
					published.complete(it.result)
				}
			)
			advanceUntilIdle()

			assertEquals(
				ReaderPageRasterPublicationResult.Durable,
				published.await(),
				"ownershipReleases=$ownershipReleases recycled=${bitmap.isRecycled} " +
					"attached=${webView.isAttachedToWindow} " +
					"descriptorRequests=${webView.passiveDescriptorRequests}"
			)
			assertEquals(0, ownershipReleases)
			assertEquals(1, publicationCount)
			assertEquals(1, webView.passiveDescriptorRequests)
			assertFalse(bitmap.isRecycled)
			assertTrue(
				source.hasSnapshot(
					captureFixture.manifest.visualPageOrdinal,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
		} finally {
			source.closeAndJoin()
			activityController.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun bundleSourceDownsamplesPhysicalPassiveCaptureForEveryProductionQuality() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		val activity = activityController.get()
		val host = FrameLayout(activity)
		activity.setContentView(host)
		val cases = listOf(
			ReaderPageBitmapQuality.Low to (20 to 30),
			ReaderPageBitmapQuality.Balanced to (40 to 60),
			ReaderPageBitmapQuality.High to (60 to 90),
			ReaderPageBitmapQuality.Native to (80 to 120)
		)
		try {
			cases.forEachIndexed { index, (quality, expectedSize) ->
				val source = ReaderPageTurnBundleSource()
				var physicalBitmap: Bitmap? = null
				try {
					source.updateBitmapQuality(quality)
					val commit = canonicalCommit().copy(
						viewportAndCaptureGeometry = productionGeometry(),
						rasterGeneration = source.currentGeneration()
					)
					val captureFixture = fixture(commit)
					val descriptor = passiveDescriptor(
						commit,
						captureFixture.manifest.visualPageOrdinal
					).copy(
						publicationUrl = "publication-passive-quality-${quality.persistedValue}",
						viewportWidth = productionGeometry().viewportWidth,
						viewportHeight = productionGeometry().viewportHeight
					)
					val webView = PassiveDescriptorWebView(activity, descriptor)
					host.removeAllViews()
					host.addView(webView)
					webView.layout(
						0,
						0,
						productionGeometry().viewportWidth,
						productionGeometry().viewportHeight
					)
					Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
					val reference = assertNotNull(
						cacheProductionPassiveReference(
							source = source,
							pageIndex = 20 + index,
							bitmapWidth = expectedSize.first,
							bitmapHeight = expectedSize.second
						)
					)
					source.initializeRasterCache(webView)
					registerActiveBundleWebView(source, webView, reference)
					var ownershipReleases = 0
					physicalBitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
					val capture = ReaderPassiveRasterCaptureResult(
						manifest = captureFixture.manifest,
						receipt = captureFixture.receipt,
						raster = ReaderPassiveRasterOwnership(assertNotNull(physicalBitmap)) { rejected ->
							ownershipReleases += 1
							if (!rejected.isRecycled) rejected.recycle()
						}
					)
					val published = CompletableDeferred<ReaderPageRasterPublicationResult>()

					source.admitPassiveRasterCapture(
						capture = capture,
						currentAuthority = passiveAuthority(captureFixture, descriptor),
						pageIndex = captureFixture.manifest.visualPageOrdinal,
						kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
						reference = reference,
						priority = ReaderPageRasterPriority.NextChapter,
						isStillCurrent = { true },
						onPublished = { completion -> published.complete(completion.result) }
					)
					advanceUntilIdle()

					assertEquals(ReaderPageRasterPublicationResult.Durable, published.await(), quality.name)
					assertEquals(0, ownershipReleases, quality.name)
					assertFalse(capture.raster?.release() == true, quality.name)
					val retained = assertNotNull(
						source.retainedCurrentLayoutSnapshot(
							pageIndex = captureFixture.manifest.visualPageOrdinal,
							kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide
						),
						quality.name
					)
					assertEquals(quality, retained.key.bitmapQuality, quality.name)
					assertEquals(expectedSize.first, retained.bitmap.width, quality.name)
					assertEquals(expectedSize.second, retained.bitmap.height, quality.name)
					assertEquals(
						quality != ReaderPageBitmapQuality.Native,
						assertNotNull(physicalBitmap).isRecycled,
						quality.name
					)
					retained.release()
				} finally {
					source.closeAndJoin()
					assertTrue(physicalBitmap?.isRecycled != false, quality.name)
				}
			}
		} finally {
			activityController.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun missingRetainedCurrentLiveReferenceDefersWithoutPassiveFallback() = runTest {
		val source = ReaderPageTurnBundleSource()
		val runtime = SuccessfulPassiveBitmapRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			if (!bitmap.isRecycled) bitmap.recycle()
		}
		var manifestRequests = 0
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPassiveRasterLiveManifestPort { _, _, _, _, onResolved ->
				manifestRequests += 1
				onResolved(
					ReaderPassiveRasterManifestResolution.Unavailable(
						ReaderPassiveRasterManifestUnavailableCause.BridgeRequestUnavailable
					)
				)
			},
			bundleSource = source,
			initialCaptureEpoch = 8L
		)
		var outcome: ReaderPageRasterBatchOutcome? = null
		val reference = passiveReference().also { it.retain() }
		val currentLiveTarget = assertNotNull(
			readerPageRasterPreparationPlan(
				"""{"context":{"centerPageIndex":4,"pageCount":30,"layoutMode":"single","readerDirection":"ltr","step":1},"targets":[{"pageIndex":4,"priority":"current","authority":"CurrentLive"}]}"""
			)
		).targets.single()

		assertTrue(
			adapter.start(
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				targets = listOf(currentLiveTarget),
				rasterGeneration = source.currentGeneration(),
				isStillCurrent = { true },
				trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
				onComplete = { outcome = it }
			)
		)

		assertEquals(
			ReaderPageRasterBatchOutcome.Deferred(
				stage = "current-live-reference",
				pageIndex = 4,
				reason = "retained-live-reference-unavailable"
			),
			outcome
		)
		assertEquals(0, manifestRequests)
		assertEquals(0, runtime.captureRequests)
		adapter.close()
		source.closeAndJoin()
	}

	@Test
	fun terminalInvalidManifestFailureFinishesTheBatchVisibly() = runTest {
		val source = ReaderPageTurnBundleSource()
		val runtime = SuccessfulPassiveBitmapRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			if (!bitmap.isRecycled) bitmap.recycle()
		}
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPassiveRasterLiveManifestPort { _, _, _, _, onResolved ->
				onResolved(
					ReaderPassiveRasterManifestResolution.Failed(
						"manifest-invalid"
					)
				)
			},
			bundleSource = source,
			initialCaptureEpoch = 8L
		)
		var outcome: ReaderPageRasterBatchOutcome? = null
		val reference = passiveReference().also { it.retain() }

		assertTrue(
			adapter.start(
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				targets = listOf(
					ReaderPageRasterBatchTarget(
						pageIndex = 4,
						priority = ReaderPageRasterPriority.CurrentChapter
					)
				),
				rasterGeneration = source.currentGeneration(),
				isStillCurrent = { true },
				trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
				onComplete = { outcome = it }
			)
		)

		assertEquals(
			ReaderPageRasterBatchOutcome.Failed(
				stage = "passive-manifest",
				pageIndex = 4,
				reason = "manifest-invalid"
			),
			outcome
		)
		assertEquals(0, runtime.captureRequests)
		adapter.close()
		source.closeAndJoin()
	}

	@Test
	fun passiveAdmissionRejectionReachesTheControllerAsATypedPrivacySafeFailure() = runTest {
		val source = ReaderPageTurnBundleSource()
		val commit = canonicalCommit().copy(
			viewportAndCaptureGeometry = ReaderPassiveRasterGeometry(
				viewportWidth = 20,
				viewportHeight = 30,
				captureLeft = 0,
				captureTop = 0,
				captureRight = 20,
				captureBottom = 30
			),
			rasterGeneration = source.currentGeneration()
		)
		val captureFixture = fixture(commit)
		val descriptor = passiveDescriptor(commit, captureFixture.manifest.visualPageOrdinal)
		val inputs = passiveAuthority(captureFixture, descriptor).manifestInputs
		var manifestRequests = 0
		var rasterReleases = 0
		val runtime = SuccessfulPassiveBitmapRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			rasterReleases += 1
			if (!bitmap.isRecycled) bitmap.recycle()
		}
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPassiveRasterLiveManifestPort { _, _, _, _, onResolved ->
				manifestRequests += 1
				onResolved(
					ReaderPassiveRasterManifestResolution.Available(
						if (manifestRequests == 1) inputs else inputs.copy(
							canonicalCommit = commit.copy(
								destinationCommitToken = "replacement-private-commit"
							)
						)
					)
				)
			},
			bundleSource = source,
			initialCaptureEpoch = commit.captureEpoch
		)
		var outcome: ReaderPageRasterBatchOutcome? = null
		val reference = passiveReference().also { it.retain() }
		try {
			assertTrue(
				adapter.start(
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = captureFixture.manifest.visualPageOrdinal,
							priority = ReaderPageRasterPriority.CurrentChapter
						)
					),
					rasterGeneration = source.currentGeneration(),
					isStillCurrent = { true },
					trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
					onComplete = { outcome = it }
				)
			)

			val failed = assertIs<ReaderPageRasterBatchOutcome.Failed>(outcome)
			assertEquals(ReaderPassiveRasterRejection.DestinationCommit, failed.passiveRasterRejection)
			assertNull(failed.persistentPublicationResult)
			assertEquals("raster-rejected-or-publication-failed", failed.reason)
			assertContains(failed.diagnostic, "passiveRasterRejection=DestinationCommit")
			assertFalse(failed.diagnostic.contains(commit.destinationCommitToken))
			assertFalse(failed.diagnostic.contains("replacement-private-commit"))
			assertEquals(2, manifestRequests)
			assertEquals(1, rasterReleases)
			assertEquals(1, session.metrics().rasterReleases)
			assertTrue(runtime.createdBitmaps.single().isRecycled)
		} finally {
			adapter.close()
			source.closeAndJoin()
		}
	}

	@Test
	fun encodeIdentityReleasingFailureReachesTheControllerWithItsExactCategory() = runTest {
		val source = ReaderPageTurnBundleSource()
		val runtime = SuccessfulPassiveBitmapRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			if (!bitmap.isRecycled) bitmap.recycle()
		}
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPassiveRasterLiveManifestPort { _, _, _, _, _ ->
				error("Current-live persistence must not request passive authority")
			},
			bundleSource = source,
			initialCaptureEpoch = 8L,
			currentLivePublicationPort = ReaderPageRasterCurrentLivePublicationPort {
				_, _, _, onPublished ->
				onPublished(
					ReaderPageRasterPublicationCompletion(
						result = ReaderPageRasterPublicationResult.Failed,
						writeFailureReason =
							ReaderPageRasterWriteFailureReason.EncodeIdentityReleasing
					)
				)
			}
		)
		var outcome: ReaderPageRasterBatchOutcome? = null
		val reference = passiveReference().also { it.retain() }
		try {
			assertTrue(
				adapter.start(
					kind = reference.key.kind,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = reference.key.visualPageIndex,
							priority = ReaderPageRasterPriority.Current,
							authority = ReaderPageRasterTargetAuthority.CurrentLive
						)
					),
					rasterGeneration = source.currentGeneration(),
					isStillCurrent = { true },
					trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
					onComplete = { outcome = it }
				)
			)

			val failed = assertIs<ReaderPageRasterBatchOutcome.Failed>(outcome)
			assertNull(failed.passiveRasterRejection)
			assertEquals(
				ReaderPageRasterPublicationResult.Failed,
				failed.persistentPublicationResult
			)
			assertEquals(
				ReaderPageRasterWriteFailureReason.EncodeIdentityReleasing,
				failed.persistentWriteFailureReason
			)
			val categoryBranch = when (failed.persistentWriteFailureReason) {
				ReaderPageRasterWriteFailureReason.EncodeIdentityReleasing ->
					"encode-identity-releasing"
				ReaderPageRasterWriteFailureReason.DiskCapacity -> "disk-capacity"
				null -> "untyped"
			}
			assertEquals("encode-identity-releasing", categoryBranch)
			assertEquals("durable-write-failed", failed.reason)
			assertEquals(
				"stage=persistent-publication pageIndex=${reference.key.visualPageIndex} " +
					"reason=durable-write-failed passiveRasterRejection=None " +
					"persistentPublicationResult=Failed " +
					"persistentWriteFailureReason=EncodeIdentityReleasing",
				failed.diagnostic
			)
			assertEquals(0, runtime.captureRequests)
		} finally {
			adapter.close()
			source.closeAndJoin()
		}
	}

	@Test
	fun typedManifestUnavailabilitySelectsReachableRecoveryOrTerminalOutcome() = runTest {
		val cases = listOf(
			ReaderPassiveRasterManifestUnavailableCause.CanonicalRenderedDestinationAbsent to
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "passive-manifest",
					pageIndex = 4,
					reason = "canonical-rendered-destination-absent"
				),
			ReaderPassiveRasterManifestUnavailableCause.CurrentLiveProfileOrLayoutUnavailable to
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "passive-manifest",
					pageIndex = 4,
					reason = "current-live-profile-or-layout-unavailable"
				),
			ReaderPassiveRasterManifestUnavailableCause.LiveWebViewDetached to
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "passive-manifest",
					pageIndex = 4,
					reason = "live-webview-detached"
				),
			ReaderPassiveRasterManifestUnavailableCause.BridgeRequestUnavailable to
				ReaderPageRasterBatchOutcome.Failed(
					stage = "passive-manifest",
					pageIndex = 4,
					reason = "bridge-request-unavailable"
				)
		)

		cases.forEach { (cause, expected) ->
			val source = ReaderPageTurnBundleSource()
			val runtime = SuccessfulPassiveBitmapRuntime()
			val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
				if (!bitmap.isRecycled) bitmap.recycle()
			}
			val adapter = ReaderPassiveRasterPreparationAdapter(
				session = session,
				liveManifestPort = ReaderPassiveRasterLiveManifestPort { _, _, _, _, onResolved ->
					onResolved(ReaderPassiveRasterManifestResolution.Unavailable(cause))
				},
				bundleSource = source,
				initialCaptureEpoch = 8L
			)
			var outcome: ReaderPageRasterBatchOutcome? = null
			val reference = passiveReference().also { it.retain() }

			assertTrue(
				adapter.start(
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = 4,
							priority = ReaderPageRasterPriority.CurrentChapter
						)
					),
					rasterGeneration = source.currentGeneration(),
					isStillCurrent = { true },
					trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
					onComplete = { outcome = it }
				)
			)

			assertEquals(expected, outcome, cause.name)
			assertEquals(0, runtime.captureRequests, cause.name)
			adapter.close()
			source.closeAndJoin()
		}
	}

	@Test
	fun currentLiveUsesRetainedReferenceBeforeOffscreenPassiveAdmission() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		val activity = activityController.get()
		val source = ReaderPageTurnBundleSource(
			hydrationStorePort = AlwaysMissPassiveHydrationStore
		)
		val commit = canonicalCommit().copy(
			viewportAndCaptureGeometry = productionGeometry(),
			rasterGeneration = source.currentGeneration()
		)
		val centerPage = 20
		val offscreenPage = 4
		val descriptor = passiveDescriptor(commit, centerPage).copy(
			publicationUrl = "publication-authority-routing",
			viewportWidth = productionGeometry().viewportWidth,
			viewportHeight = productionGeometry().viewportHeight
		)
		val webView = PassiveManifestBridgeWebView(activity, commit, descriptor)
		val host = FrameLayout(activity).also { it.addView(webView) }
		activity.setContentView(host)
		webView.layout(
			0,
			0,
			productionGeometry().viewportWidth,
			productionGeometry().viewportHeight
		)
		Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		val manifestPort = ReaderPassiveRasterLiveManifestPort {
			visualPageOrdinal,
			captureEpoch,
			rasterGeneration,
			preparationGeneration,
			onResolved ->
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?." +
					"pageTurnPassiveRasterManifestInputs?.(" +
					"$visualPageOrdinal, $captureEpoch, $rasterGeneration, " +
						"$preparationGeneration) ?? null)"
			) { encoded -> onResolved(readerPassiveRasterManifestResolution(encoded)) }
		}
		val runtime = SuccessfulPassiveBitmapRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			if (!bitmap.isRecycled) bitmap.recycle()
		}
		var livePublicationReference: ReaderPageSlideSnapshot? = null
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = manifestPort,
			bundleSource = source,
			initialCaptureEpoch = commit.captureEpoch,
			currentLivePublicationPort = ReaderPageRasterCurrentLivePublicationPort {
				reference,
				priority,
				isStillCurrent,
				onPublished ->
				livePublicationReference = reference
				assertEquals(ReaderPageRasterPriority.Current, priority)
				assertTrue(isStillCurrent())
				onPublished(
					ReaderPageRasterPublicationCompletion(
						ReaderPageRasterPublicationResult.Durable
					)
				)
			}
		)
		try {
			val reference = assertNotNull(
				cacheProductionPassiveReference(
					source = source,
					pageIndex = centerPage,
					bitmapWidth = 40,
					bitmapHeight = 60
				)
			)
			source.initializeRasterCache(webView)
			registerActiveBundleWebView(source, webView, reference)
			val targets = assertNotNull(
				readerPageRasterPreparationPlan(
					"""{"context":{"centerPageIndex":20,"pageCount":30,"layoutMode":"spread","readerDirection":"ltr","step":2},"targets":[{"pageIndex":20,"priority":"current","authority":"CurrentLive"},{"pageIndex":4,"priority":"next-transition","authority":"OffscreenPassive"}]}"""
				)
			).targets
			val completed = CompletableDeferred<ReaderPageRasterBatchOutcome>()
			val activeTargets = mutableListOf<Int>()
			val durableTargets = mutableListOf<Int>()
			reference.retain()

			assertTrue(
				adapter.start(
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					reference = reference,
					targets = targets,
					rasterGeneration = source.currentGeneration(),
					isStillCurrent = { true },
					trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
					onActiveTarget = { target -> activeTargets += target.pageIndex },
					onTargetDurable = { target -> durableTargets += target.pageIndex },
					onComplete = completed::complete
				)
			)
			assertEquals(listOf(centerPage, offscreenPage), activeTargets)
			assertEquals(listOf(centerPage), durableTargets)
			assertSame(reference, livePublicationReference)
			assertEquals(1, webView.manifestRequests)
			assertEquals(0, runtime.captureRequests)

			adapter.cancel()
			assertEquals(ReaderPageRasterBatchOutcome.Cancelled, completed.await())
		} finally {
			adapter.close()
			source.closeAndJoin()
			activityController.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun repeatedRealPassivePrewarmAndIdleBackgroundPreserveAllLiveAuthority() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		val activity = activityController.get()
		val source = ReaderPageTurnBundleSource(
			hydrationStorePort = AlwaysMissPassiveHydrationStore
		)
		val commit = canonicalCommit().copy(
			viewportAndCaptureGeometry = productionGeometry(),
			rasterGeneration = source.currentGeneration()
		)
		val targetPage = 4
		val descriptor = passiveDescriptor(commit, targetPage).copy(
			publicationUrl = "publication-real-passive-isolation",
			viewportWidth = productionGeometry().viewportWidth,
			viewportHeight = productionGeometry().viewportHeight
		)
		val webView = PassiveManifestBridgeWebView(activity, commit, descriptor)
		val host = FrameLayout(activity).also { it.addView(webView) }
		activity.setContentView(host)
		webView.layout(
			0,
			0,
			productionGeometry().viewportWidth,
			productionGeometry().viewportHeight
		)
		Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		val manifestPort = ReaderPassiveRasterLiveManifestPort {
			visualPageOrdinal,
			captureEpoch,
			rasterGeneration,
			preparationGeneration,
			onResolved ->
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?." +
					"pageTurnPassiveRasterManifestInputs?.(" +
					"$visualPageOrdinal, $captureEpoch, $rasterGeneration, " +
						"$preparationGeneration) ?? null)"
			) { encoded -> onResolved(readerPassiveRasterManifestResolution(encoded)) }
		}
		val runtime = SuccessfulPassiveBitmapRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { rejected ->
			if (!rejected.isRecycled) rejected.recycle()
		}
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = manifestPort,
			bundleSource = source,
			initialCaptureEpoch = commit.captureEpoch
		)
		try {
			val reference = assertNotNull(
				cacheProductionPassiveReference(
					source = source,
					pageIndex = 20,
					bitmapWidth = 40,
					bitmapHeight = 60
				)
			)
			source.initializeRasterCache(webView)
			registerActiveBundleWebView(source, webView, reference)
			source.protectDecodedPageIndices(setOf(reference.key.visualPageIndex))
			val liveAuthorityBefore = webView.liveAuthoritySnapshot()
			val triggers = listOf(
				ReaderPageRasterAcquisitionTrigger.InitialPreparation,
				ReaderPageRasterAcquisitionTrigger.InitialPreparation,
				ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
				ReaderPageRasterAcquisitionTrigger.WorkingSetRefill
			)

			triggers.forEach { trigger ->
				val completed = CompletableDeferred<ReaderPageRasterBatchOutcome>()
				reference.retain()
				assertTrue(
					adapter.start(
						kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
						reference = reference,
						targets = listOf(
							ReaderPageRasterBatchTarget(
								pageIndex = targetPage,
								priority = ReaderPageRasterPriority.NextChapter
							)
						),
						rasterGeneration = source.currentGeneration(),
						isStillCurrent = { true },
						trigger = trigger,
						capacityPolicy = if (
							trigger == ReaderPageRasterAcquisitionTrigger.WorkingSetRefill
						) {
							ReaderPageRasterCapacityPolicy.StopBackgroundRefill
						} else {
							ReaderPageRasterCapacityPolicy.FailClosed
						},
						onComplete = completed::complete
					),
					trigger.name
				)
				advanceUntilIdle()
				assertEquals(ReaderPageRasterBatchOutcome.Ready, completed.await(), trigger.name)
				assertTrue(
					source.hasSnapshot(
						targetPage,
						ReaderPageTurnTransitionKind.LandscapeSpreadSlide
					),
					trigger.name
				)
				source.trimMemory("repeat-real-passive-${trigger.name}")
				assertFalse(
					source.hasSnapshot(
						targetPage,
						ReaderPageTurnTransitionKind.LandscapeSpreadSlide
					),
					trigger.name
				)
			}

			assertEquals(4, runtime.captureRequests)
			assertEquals(8, webView.manifestRequests)
			assertTrue(runtime.createdBitmaps.all(Bitmap::isRecycled))
			assertEquals(liveAuthorityBefore, webView.liveAuthoritySnapshot())
		} finally {
			adapter.close()
			source.closeAndJoin()
			activityController.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun batchCancellationReleasesCaptureAwaitingSecondAuthorityExactlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		val activity = activityController.get()
		val source = ReaderPageTurnBundleSource(
			hydrationStorePort = AlwaysMissPassiveHydrationStore
		)
		val commit = canonicalCommit().copy(
			viewportAndCaptureGeometry = productionGeometry(),
			rasterGeneration = source.currentGeneration()
		)
		val captureFixture = fixture(commit)
		val descriptor = passiveDescriptor(commit, captureFixture.manifest.visualPageOrdinal).copy(
			publicationUrl = "publication-pending-passive-admission",
			viewportWidth = productionGeometry().viewportWidth,
			viewportHeight = productionGeometry().viewportHeight
		)
		val webView = PassiveDescriptorWebView(activity, descriptor)
		val host = FrameLayout(activity).also { it.addView(webView) }
		activity.setContentView(host)
		webView.layout(
			0,
			0,
			productionGeometry().viewportWidth,
			productionGeometry().viewportHeight
		)
		Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		val manifestPort = DelayedSecondManifestPort(
			ReaderPassiveRasterManifestInputs(
				canonicalCommit = commit,
				opaqueCaptureTarget = captureFixture.manifest.opaqueCaptureTarget,
				visualPageOrdinal = captureFixture.manifest.visualPageOrdinal,
				rasterDescriptor = descriptor
			)
		)
		val runtime = SuccessfulPassiveBitmapRuntime()
		var rasterReleases = 0
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			rasterReleases += 1
			if (!bitmap.isRecycled) bitmap.recycle()
		}
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = manifestPort,
			bundleSource = source,
			initialCaptureEpoch = commit.captureEpoch
		)
		try {
			val reference = assertNotNull(
				cacheProductionPassiveReference(
					source = source,
					pageIndex = 20,
					bitmapWidth = 40,
					bitmapHeight = 60
				)
			)
			source.initializeRasterCache(webView)
			registerActiveBundleWebView(source, webView, reference)
			val completed = mutableListOf<ReaderPageRasterBatchOutcome>()
			reference.retain()
			assertTrue(
				adapter.start(
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = captureFixture.manifest.visualPageOrdinal,
							priority = ReaderPageRasterPriority.NextChapter
						)
					),
					rasterGeneration = source.currentGeneration(),
					isStillCurrent = { true },
					trigger = ReaderPageRasterAcquisitionTrigger.InitialPreparation,
					onComplete = completed::add
				)
			)
			advanceUntilIdle()
			manifestPort.awaitSecondRequest()

			assertEquals(
				2,
				manifestPort.requests,
				"captureRequests=${runtime.captureRequests} completed=$completed metrics=${session.metrics()}"
			)
			assertEquals(1, runtime.createdBitmaps.size)
			assertEquals(0, rasterReleases)
			assertFalse(runtime.createdBitmaps.single().isRecycled)

			adapter.cancel()

			assertEquals(
				listOf<ReaderPageRasterBatchOutcome>(ReaderPageRasterBatchOutcome.Cancelled),
				completed
			)
			assertEquals(1, rasterReleases)
			assertTrue(runtime.createdBitmaps.single().isRecycled)
			assertFalse(
				source.hasSnapshot(
					captureFixture.manifest.visualPageOrdinal,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)

			val freshCompleted = mutableListOf<ReaderPageRasterBatchOutcome>()
			val freshCompletion = CompletableDeferred<Unit>()
			reference.retain()
			assertTrue(
				adapter.start(
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = captureFixture.manifest.visualPageOrdinal,
							priority = ReaderPageRasterPriority.NextChapter
						)
					),
					rasterGeneration = source.currentGeneration(),
					isStillCurrent = { true },
					trigger = ReaderPageRasterAcquisitionTrigger.WarmReopen,
					onComplete = { outcome ->
						freshCompleted += outcome
						freshCompletion.complete(Unit)
					}
				)
			)
			advanceUntilIdle()
			freshCompletion.await()
			assertEquals(
				listOf<ReaderPageRasterBatchOutcome>(ReaderPageRasterBatchOutcome.Ready),
				freshCompleted,
				"requests=${manifestPort.requests} captures=${runtime.captureRequests} " +
					"releases=$rasterReleases adapterAvailable=${adapter.isAvailable} " +
					"session=${session.metrics()} ownership=${source.ownershipMetrics()}"
			)
			assertTrue(
				source.hasSnapshot(
					captureFixture.manifest.visualPageOrdinal,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
			val cacheAfterFreshRetry = source.rasterCacheMetrics()
			val releasesAfterFreshRetry = rasterReleases

			manifestPort.resolveSecond()

			assertEquals(cacheAfterFreshRetry, source.rasterCacheMetrics())
			assertEquals(releasesAfterFreshRetry, rasterReleases)
			assertEquals(
				listOf<ReaderPageRasterBatchOutcome>(ReaderPageRasterBatchOutcome.Cancelled),
				completed
			)
		} finally {
			adapter.close()
			source.closeAndJoin()
			activityController.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun bundleSourceRejectsEveryPassiveAuthorityMismatchWithoutPublication() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val source = ReaderPageTurnBundleSource()
		val commit = canonicalCommit().copy(rasterGeneration = source.currentGeneration())
		val captureFixture = fixture(commit)
		val descriptor = passiveDescriptor(commit, captureFixture.manifest.visualPageOrdinal)
		val exactAuthority = passiveAuthority(captureFixture, descriptor)
		val mismatches = listOf<Pair<String, (ReaderPassiveRasterAdmissionAuthority) -> ReaderPassiveRasterAdmissionAuthority>>(
			"session" to { authority -> authority.copy(activePassiveSessionId = "passive-session-b") },
			"destination" to { authority ->
				authority.copy(
					manifestInputs = authority.manifestInputs.copy(
						canonicalCommit = commit.copy(destinationCommitToken = "commit-b")
					)
				)
			},
			"profile" to { authority ->
				authority.copy(
					manifestInputs = authority.manifestInputs.copy(
						canonicalCommit = commit.copy(rasterProfileKey = "landscape-profile")
					)
				)
			},
			"fingerprint" to { authority ->
				val mismatchedCommit = commit.copy(paginationFingerprint = "pagination-b")
				authority.copy(
					manifestInputs = authority.manifestInputs.copy(
						canonicalCommit = mismatchedCommit,
						rasterDescriptor = descriptor.copy(
							paginationFingerprint = mismatchedCommit.paginationFingerprint
						)
					)
				)
			},
			"geometry" to { authority ->
				authority.copy(
					manifestInputs = authority.manifestInputs.copy(
						canonicalCommit = commit.copy(
							viewportAndCaptureGeometry = landscapeGeometry()
						)
					)
				)
			},
			"raster-generation" to { authority ->
				authority.copy(
					manifestInputs = authority.manifestInputs.copy(
						canonicalCommit = commit.copy(rasterGeneration = commit.rasterGeneration + 1L)
					)
				)
			},
			"passive-sequence" to { authority ->
				authority.copy(
					expectedPassiveCommitSequence = authority.expectedPassiveCommitSequence + 1L
				)
			}
		)
		val reference = passiveReference()
		try {
			mismatches.forEach { (name, mutateAuthority) ->
				var releases = 0
				var publications = 0
				val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
				val capture = ReaderPassiveRasterCaptureResult(
					manifest = captureFixture.manifest,
					receipt = captureFixture.receipt,
					raster = ReaderPassiveRasterOwnership(bitmap) {
						releases += 1
						if (!bitmap.isRecycled) bitmap.recycle()
					}
				)
				var result: ReaderPageRasterPublicationResult? = null

				source.admitPassiveRasterCapture(
					capture = capture,
					currentAuthority = mutateAuthority(exactAuthority),
					pageIndex = captureFixture.manifest.visualPageOrdinal,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					reference = reference,
					priority = ReaderPageRasterPriority.NextChapter,
					isStillCurrent = { true },
					onPublished = {
						publications += 1
						result = it.result
					}
				)

				assertEquals(ReaderPageRasterPublicationResult.Failed, result, name)
				assertEquals(1, publications, name)
				assertEquals(1, releases, name)
				assertTrue(bitmap.isRecycled, name)
				assertFalse(
					source.hasSnapshot(
						captureFixture.manifest.visualPageOrdinal,
						ReaderPageTurnTransitionKind.LandscapeSpreadSlide
					),
					name
				)
			}
		} finally {
			source.closeAndJoin()
			reference.releaseCacheOwnership()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun bundleSourceRejectsStaleActiveAndPhysicalLayoutGenerations() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		val activity = activityController.get()
		val source = ReaderPageTurnBundleSource()
		val commit = canonicalCommit().copy(rasterGeneration = source.currentGeneration())
		val captureFixture = fixture(commit)
		val descriptor = passiveDescriptor(commit, captureFixture.manifest.visualPageOrdinal)
		val webView = PassiveDescriptorWebView(activity, descriptor)
		val host = FrameLayout(activity).also { it.addView(webView) }
		activity.setContentView(host)
		Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		var originalReference: ReaderPageSlideSnapshot? = null
		try {
			val stalePhysicalReference = assertNotNull(cachePassiveReference(source))
			originalReference = stalePhysicalReference
			stalePhysicalReference.retain()
			val replacementReference = assertNotNull(
				cachePassiveReference(source, pageIndex = 22, surfaceWidth = 22)
			)
			source.initializeRasterCache(webView)

			var publicationCount = 0
			val physicalBitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
			source.admitPassiveRasterCapture(
				capture = ReaderPassiveRasterCaptureResult(
					manifest = captureFixture.manifest,
					receipt = captureFixture.receipt,
					raster = ReaderPassiveRasterOwnership(physicalBitmap) {
						if (!physicalBitmap.isRecycled) physicalBitmap.recycle()
					}
				),
				currentAuthority = passiveAuthority(captureFixture, descriptor),
				pageIndex = captureFixture.manifest.visualPageOrdinal,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = stalePhysicalReference,
				priority = ReaderPageRasterPriority.NextChapter,
				isStillCurrent = { true },
				onPublished = {
					publicationCount += 1
					assertEquals(ReaderPageRasterPublicationResult.Failed, it.result)
				}
			)
			assertEquals(1, publicationCount)
			assertTrue(physicalBitmap.isRecycled)
			assertFalse(
				source.hasSnapshot(
					captureFixture.manifest.visualPageOrdinal,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)

			val staleGenerationCommit = canonicalCommit().copy(
				rasterGeneration = source.currentGeneration()
			)
			val staleGenerationFixture = fixture(staleGenerationCommit)
			val staleDescriptor = passiveDescriptor(
				staleGenerationCommit,
				staleGenerationFixture.manifest.visualPageOrdinal
			)
			source.invalidate("passive-admission-stale-generation")
			var generationReleases = 0
			val generationBitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
			var generationResult: ReaderPageRasterPublicationResult? = null
			source.admitPassiveRasterCapture(
				capture = ReaderPassiveRasterCaptureResult(
					manifest = staleGenerationFixture.manifest,
					receipt = staleGenerationFixture.receipt,
					raster = ReaderPassiveRasterOwnership(generationBitmap) {
						generationReleases += 1
						if (!generationBitmap.isRecycled) generationBitmap.recycle()
					}
				),
				currentAuthority = passiveAuthority(staleGenerationFixture, staleDescriptor),
				pageIndex = staleGenerationFixture.manifest.visualPageOrdinal,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = replacementReference,
				priority = ReaderPageRasterPriority.NextChapter,
				isStillCurrent = { true },
				onPublished = { generationResult = it.result }
			)
			assertEquals(ReaderPageRasterPublicationResult.Failed, generationResult)
			assertEquals(1, generationReleases)
			assertTrue(generationBitmap.isRecycled)
		} finally {
			originalReference?.release()
			source.closeAndJoin()
			activityController.destroy()
			Dispatchers.resetMain()
		}
	}

	private fun passiveDescriptor(
		commit: ReaderPassiveRasterCanonicalCommit,
		visualPageOrdinal: Int
	) = ReaderPageRasterDescriptor(
		publicationUrl = "publication-passive-admission",
		paginationFingerprint = commit.paginationFingerprint,
		layoutFingerprint = commit.layoutFingerprint,
		decorationFingerprint = commit.decorationFingerprint,
		viewportWidth = 20,
		viewportHeight = 30,
		pageCount = 44,
		spineIndex = 0,
		href = "chapter-passive-admission",
		chapterPageIndex = visualPageOrdinal,
		chapterPageCount = 44,
		visualPageOrdinal = visualPageOrdinal
	)

	private fun passiveAuthority(
		fixture: Fixture,
		descriptor: ReaderPageRasterDescriptor
	) = ReaderPassiveRasterAdmissionAuthority(
		manifestInputs = ReaderPassiveRasterManifestInputs(
			canonicalCommit = fixture.manifest.let { manifest ->
				ReaderPassiveRasterCanonicalCommit(
					captureEpoch = manifest.captureEpoch,
					liveFoliateSessionId = manifest.liveFoliateSessionId,
					publicationSessionGeneration = manifest.publicationSessionGeneration,
					destinationCommitToken = manifest.destinationCommitToken,
					rasterProfileKey = manifest.rasterProfileKey,
					paginationFingerprint = manifest.paginationFingerprint,
					layoutFingerprint = manifest.layoutFingerprint,
					decorationFingerprint = manifest.decorationFingerprint,
					viewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
					rasterGeneration = manifest.rasterGeneration,
					profileAuthority = manifest.profileAuthority
				)
			},
			opaqueCaptureTarget = fixture.manifest.opaqueCaptureTarget,
			visualPageOrdinal = fixture.manifest.visualPageOrdinal,
			rasterDescriptor = descriptor
		),
		activePassiveSessionId = fixture.receipt.passiveSessionId,
		expectedPassiveCommitSequence = fixture.receipt.passiveCommitSequence
	)

	private suspend fun registerActiveBundleWebView(
		source: ReaderPageTurnBundleSource,
		webView: WebView,
		reference: ReaderPageSlideSnapshot
	) {
		val hydrated = CompletableDeferred<ReaderPageSlideSnapshot?>()
		val request = source.hydrateSnapshot(
			webView = webView,
			pageIndex = reference.key.visualPageIndex,
			kind = reference.key.kind,
			reference = reference,
			onHydrated = hydrated::complete
		)
		assertNotNull(hydrated.await()).release()
		request.cancel()
	}

	private fun cacheProductionPassiveReference(
		source: ReaderPageTurnBundleSource,
		pageIndex: Int,
		bitmapWidth: Int,
		bitmapHeight: Int
	): ReaderPageSlideSnapshot? = source.cacheCurrentSnapshot(
		pageIndex = pageIndex,
		kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
		current = ReaderPageTurnCaptureResult(
			bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888),
			sourceRectInWindow = Rect(0, 0, 80, 120),
			geometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 80.0,
				viewportHeight = 120.0,
				mode = ReaderPageTurnLayoutMode.Spread,
				pages = listOf(
					ReaderPageTurnPageRect(
						role = ReaderPageTurnPageRole.Left,
						left = 0.0,
						top = 0.0,
						width = 40.0,
						height = 120.0
					),
					ReaderPageTurnPageRect(
						role = ReaderPageTurnPageRole.Right,
						left = 40.0,
						top = 0.0,
						width = 40.0,
						height = 120.0
					)
				)
			),
			elapsedMs = 1L
		)
	)

	private fun cachePassiveReference(
		source: ReaderPageTurnBundleSource,
		pageIndex: Int = 20,
		surfaceWidth: Int = 20
	): ReaderPageSlideSnapshot? = source.cacheCurrentSnapshot(
		pageIndex = pageIndex,
		kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
		current = ReaderPageTurnCaptureResult(
			bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
			sourceRectInWindow = Rect(0, 0, surfaceWidth, 30),
			geometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 20.0,
				viewportHeight = 30.0,
				mode = ReaderPageTurnLayoutMode.Spread,
				pages = listOf(
					ReaderPageTurnPageRect(
						role = ReaderPageTurnPageRole.Left,
						left = 0.0,
						top = 0.0,
						width = 10.0,
						height = 30.0
					),
					ReaderPageTurnPageRect(
						role = ReaderPageTurnPageRole.Right,
						left = 10.0,
						top = 0.0,
						width = 10.0,
						height = 30.0
					)
				)
			),
			elapsedMs = 1L
		)
	)

	private fun passiveReference(surfaceWidth: Int = 20): ReaderPageSlideSnapshot =
		ReaderPageSlideSnapshot(
			key = ReaderPageSlideSnapshotKey(
				visualPageIndex = 20,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				bitmapQuality = ReaderPageBitmapQuality.Balanced,
				bitmapWidth = 20,
				bitmapHeight = 30,
				surfaceWidth = surfaceWidth,
				surfaceHeight = 30
			),
			bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
			surfaceRectInWindow = Rect(0, 0, surfaceWidth, 30),
			leafGeometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = ReaderPageTurnPixelRect(0, 0, surfaceWidth, 30),
				leftLeafRect = ReaderPageTurnPixelRect(0, 0, (surfaceWidth / 2) - 1, 30),
				gutterRect = ReaderPageTurnPixelRect(
					(surfaceWidth / 2) - 1,
					0,
					(surfaceWidth / 2) + 1,
					30
				),
				rightLeafRect = ReaderPageTurnPixelRect(
					(surfaceWidth / 2) + 1,
					0,
					surfaceWidth,
					30
				)
			),
			reverseFaceColor = 0xffead9ae.toInt()
		)

	private data class LiveAuthoritySnapshot(
		val receipt: String,
		val activeAnchor: String,
		val overlay: String,
		val committedLocation: String,
		val foregroundMutationGeneration: Long
	)

	private object AlwaysMissPassiveHydrationStore : ReaderPageRasterHydrationStorePort {
		override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? = null

		override suspend fun remove(
			key: ReaderPageRasterKey,
			expectedMetadata: ReaderPageRasterMetadata
		): Boolean = false
	}

	private class DelayedSecondManifestPort(
		private val inputs: ReaderPassiveRasterManifestInputs
	) : ReaderPassiveRasterLiveManifestPort {
		var requests = 0
			private set
		private var secondCallback: ((ReaderPassiveRasterManifestResolution) -> Unit)? = null
		private var secondInputs: ReaderPassiveRasterManifestInputs? = null
		private val secondRequested = CompletableDeferred<Unit>()

		override fun request(
			visualPageOrdinal: Int,
			captureEpoch: Long,
			rasterGeneration: Long,
			preparationGeneration: Long,
			onResolved: (ReaderPassiveRasterManifestResolution) -> Unit
		) {
			requests += 1
			val current = inputs.copy(
				canonicalCommit = inputs.canonicalCommit.copy(
					captureEpoch = captureEpoch,
					rasterGeneration = rasterGeneration
				),
				visualPageOrdinal = visualPageOrdinal,
				rasterDescriptor = inputs.rasterDescriptor.copy(
					chapterPageIndex = visualPageOrdinal,
					visualPageOrdinal = visualPageOrdinal
				)
			)
			if (requests == 2) {
				check(secondCallback == null)
				secondCallback = onResolved
				secondInputs = current
				secondRequested.complete(Unit)
			} else {
				onResolved(ReaderPassiveRasterManifestResolution.Available(current))
			}
		}

		suspend fun awaitSecondRequest() = secondRequested.await()

		fun resolveSecond() {
			val callback = assertNotNull(secondCallback)
			val current = assertNotNull(secondInputs)
			secondCallback = null
			secondInputs = null
			callback(ReaderPassiveRasterManifestResolution.Available(current))
		}
	}

	private inner class SuccessfulPassiveBitmapRuntime : ReaderPassiveRasterRuntimePort<Bitmap> {
		override val passiveSessionId = "passive-session-a"
		override val isReady = true
		var captureRequests = 0
			private set
		val createdBitmaps = mutableListOf<Bitmap>()

		override fun commit(
			manifest: ReaderPassiveRasterCaptureManifest,
			captureTarget: String,
			passiveCommitSequence: Long,
			onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit
		) {
			assertEquals(manifest.opaqueCaptureTarget, captureTarget)
			onCommitted(receiptFor(manifest, passiveSessionId, passiveCommitSequence))
		}

		override fun capture(
			geometry: ReaderPassiveRasterGeometry,
			onCaptured: (Bitmap?) -> Unit
		) {
			captureRequests += 1
			val bitmap = Bitmap.createBitmap(
				geometry.captureWidth,
				geometry.captureHeight,
				Bitmap.Config.ARGB_8888
			)
			createdBitmaps += bitmap
			onCaptured(bitmap)
		}

		override fun cancelActiveCommit(onDrained: () -> Unit) = onDrained()
		override fun pause() = Unit
		override fun resume() = Unit
		override fun destroy() = Unit
	}

	private class PassiveManifestBridgeWebView(
		context: Context,
		private val canonicalCommit: ReaderPassiveRasterCanonicalCommit,
		private val descriptor: ReaderPageRasterDescriptor
	) : WebView(context) {
		private var liveReceipt = canonicalCommit.destinationCommitToken
		private var activeAnchor = "epubcfi(/6/4[chapter]!/4/2/1:0)"
		private var overlay = "annotation-overlay-a"
		private var committedLocation = "chapter=3;page=4;fraction=0.375"
		private var foregroundMutationGeneration = 41L
		var manifestRequests = 0
			private set

		fun liveAuthoritySnapshot() = LiveAuthoritySnapshot(
			receipt = liveReceipt,
			activeAnchor = activeAnchor,
			overlay = overlay,
			committedLocation = committedLocation,
			foregroundMutationGeneration = foregroundMutationGeneration
		)

		override fun evaluateJavascript(
			script: String,
			resultCallback: ValueCallback<String>?
		) {
			val encoded = when {
				script.contains("pageTurnPassiveRasterManifestInputs") -> {
					manifestRequests += 1
					val arguments = assertNotNull(
						Regex("""\((\d+), (\d+), (\d+), (\d+)\)""").find(script)
					).groupValues
					val visualPageOrdinal = arguments[1].toInt()
					val captureEpoch = arguments[2].toLong()
					val rasterGeneration = arguments[3].toLong()
					val commit = canonicalCommit.copy(
						captureEpoch = captureEpoch,
						rasterGeneration = rasterGeneration
					)
					val currentDescriptor = descriptor.copy(
						chapterPageIndex = visualPageOrdinal,
						visualPageOrdinal = visualPageOrdinal
					)
					JSONObject.quote(
						JSONObject().apply {
							put("captureEpoch", commit.captureEpoch)
							put("liveFoliateSessionId", commit.liveFoliateSessionId)
							put(
								"publicationSessionGeneration",
								commit.publicationSessionGeneration
							)
							put("destinationCommitToken", commit.destinationCommitToken)
							put("rasterProfileKey", commit.rasterProfileKey)
							put("paginationFingerprint", commit.paginationFingerprint)
							put("layoutFingerprint", commit.layoutFingerprint)
							put("decorationFingerprint", commit.decorationFingerprint)
							put("viewportAndCaptureGeometry", geometryJson(commit.viewportAndCaptureGeometry))
							put("rasterGeneration", commit.rasterGeneration)
							put("opaqueCaptureTarget", "synthetic-target-a")
							put("visualPageOrdinal", visualPageOrdinal)
							put("profileAuthority", commit.profileAuthority.serializedValue)
							put("rasterDescriptor", descriptorJson(currentDescriptor))
						}.toString()
					)
				}
				script.contains("pageTurnPassiveRasterDescriptor") ||
					script.contains("pageTurnRasterDescriptor") ->
					descriptorJson(descriptor).toString()
				else -> "null"
			}
			resultCallback?.onReceiveValue(encoded)
		}

		private fun geometryJson(geometry: ReaderPassiveRasterGeometry) = JSONObject().apply {
			put("viewportWidth", geometry.viewportWidth)
			put("viewportHeight", geometry.viewportHeight)
			put("captureLeft", geometry.captureLeft)
			put("captureTop", geometry.captureTop)
			put("captureRight", geometry.captureRight)
			put("captureBottom", geometry.captureBottom)
		}

		private fun descriptorJson(value: ReaderPageRasterDescriptor) = JSONObject().apply {
			put("publicationUrl", value.publicationUrl)
			put("paginationFingerprint", value.paginationFingerprint)
			put("layoutFingerprint", value.layoutFingerprint)
			put("decorationFingerprint", value.decorationFingerprint)
			put("viewportWidth", value.viewportWidth)
			put("viewportHeight", value.viewportHeight)
			put("pageCount", value.pageCount)
			put("spineIndex", value.spineIndex)
			put("href", value.href)
			put("chapterPageIndex", value.chapterPageIndex)
			put("chapterPageCount", value.chapterPageCount)
			put("visualPageOrdinal", value.visualPageOrdinal)
		}
	}

	private class PassiveDescriptorWebView(
		context: Context,
		private val descriptor: ReaderPageRasterDescriptor
	) : WebView(context) {
		var passiveDescriptorRequests = 0
			private set

		override fun evaluateJavascript(
			script: String,
			resultCallback: ValueCallback<String>?
		) {
			val encoded = if (script.contains("pageTurnPassiveRasterDescriptor")) {
				passiveDescriptorRequests += 1
				"""{
					"publicationUrl":"${descriptor.publicationUrl}",
					"paginationFingerprint":"${descriptor.paginationFingerprint}",
					"layoutFingerprint":"${descriptor.layoutFingerprint}",
					"decorationFingerprint":"${descriptor.decorationFingerprint}",
					"viewportWidth":${descriptor.viewportWidth},
					"viewportHeight":${descriptor.viewportHeight},
					"pageCount":${descriptor.pageCount},
					"spineIndex":${descriptor.spineIndex},
					"href":"${descriptor.href}",
					"chapterPageIndex":${descriptor.chapterPageIndex},
					"chapterPageCount":${descriptor.chapterPageCount},
					"visualPageOrdinal":${descriptor.visualPageOrdinal}
				}""".trimIndent()
			} else {
				"null"
			}
			resultCallback?.onReceiveValue(encoded)
		}
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
				expectedPassiveCommitSequence = receipt.passiveCommitSequence,
				currentProfileAuthority = manifest.profileAuthority
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

	private fun productionGeometry() = ReaderPassiveRasterGeometry(
		viewportWidth = 80,
		viewportHeight = 120,
		captureLeft = 0,
		captureTop = 0,
		captureRight = 80,
		captureBottom = 120
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
		var cancelCommitCalls = 0
		var cancelCommitFailure: Throwable? = null
		private var manifest: ReaderPassiveRasterCaptureManifest? = null
		private var commitSequence = 0L
		private var commitCallback: ((ReaderPassiveRasterCaptureReceipt?) -> Unit)? = null
		private var commitCancellationCallback: (() -> Unit)? = null
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

		override fun cancelActiveCommit(onDrained: () -> Unit) {
			cancelCommitCalls += 1
			cancelCommitFailure?.let { throw it }
			check(commitCancellationCallback == null)
			commitCancellationCallback = onDrained
		}

		fun completeCommitCancellation() {
			val callback = assertNotNull(commitCancellationCallback)
			commitCancellationCallback = null
			commitCallback = null
			callback()
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
