package paige.navic.ui.screens.reader

import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import com.russhwolf.settings.MapSettings
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.data.remote.bindery.BinderyApiClient
import paige.navic.data.remote.bindery.ExternalTextPurpose
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyMetadataCache
import paige.navic.domain.repositories.BinderyMetadataCacheRecord
import paige.navic.domain.repositories.BinderySyncPair
import paige.navic.domain.repositories.BinderyWhispersyncArtifact
import paige.navic.domain.repositories.BinderyWordSyncDiscovery
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.BinderyWhispersyncIdentity
import paige.navic.domain.repositories.NoOpBinderyMetadataCache
import paige.navic.reader.BinderyReaderPublicationResolver
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.reader.ReadaloudPlaybackService
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.WordSyncIndex
import paige.navic.reader.WordSyncPublicationVerificationSession
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.shared.AudiobookPlaybackTimelineSnapshot
import paige.navic.ui.core.AudiobookMiniPlayerUiState
import paige.navic.ui.core.EnrichmentRequestOwner
import paige.navic.ui.navigation.Screen
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderPublicationRuntimeOwnershipTest {
	@Test
	fun lateCancelledPublicationCannotCallReplacementCallback() = runTest {
		val aStarted = CompletableDeferred<Unit>()
		val releaseA = CompletableDeferred<Unit>()
		val api = PublicationApi { resource ->
			if (resource == "resource-a") {
				aStarted.complete(Unit)
				try {
					releaseA.await()
				} catch (_: CancellationException) {
					withContext(NonCancellable) { releaseA.await() }
				}
			}
			"publication".encodeToByteArray()
		}
		val repository = configuredRepository(api)
		val readerA = reader("a")
		val readerB = reader("b")
		val currentReader = mutableStateOf(readerA)
		val readyOwners = mutableListOf<String>()
		val errors = mutableListOf<String>()

		withKoin(repository) {
			val fixture = compose {
				val reader = currentReader.value
				ReaderPublicationRuntimeHost(
					reader = reader,
					onPublicationReady = { _, _, _, _, _ -> readyOwners += reader.bookId },
					onError = errors::add
				)
			}
			flush(fixture)
			assertTrue(aStarted.isCompleted)

			currentReader.value = readerB
			fixture.composition.setContent {
				CompositionLocalProvider(LocalContext provides RuntimeEnvironment.getApplication()) {
					val reader = currentReader.value
					ReaderPublicationRuntimeHost(
						reader = reader,
						onPublicationReady = { _, _, _, _, _ -> readyOwners += reader.bookId },
						onError = errors::add
					)
				}
			}
			flushUntil(fixture, "replacement publication ready") {
				readyOwners == listOf("b")
			}
			assertEquals(listOf("b"), readyOwners)

			releaseA.complete(Unit)
			flush(fixture)

			assertEquals(listOf("b"), readyOwners)
			assertTrue(errors.isEmpty())
			fixture.composition.dispose()
		}
	}

	@Test
	fun publicationCancellationDoesNotBecomeFailureCallback() = runTest {
		val repository = configuredRepository(
			PublicationApi { throw CancellationException("cancelled") }
		)
		val errors = mutableListOf<String>()

		withKoin(repository) {
			val fixture = compose {
				ReaderPublicationRuntimeHost(
					reader = reader("c"),
					onPublicationReady = { _, _, _, _, _ -> },
					onError = errors::add
				)
			}
			flush(fixture)

			assertTrue(errors.isEmpty())
			fixture.composition.dispose()
		}
	}

	@Test
	fun binderyRepositoryPropagatesCancellationBeforeResultTranslation() = runTest {
		val repository = configuredRepository(
			PublicationApi { throw CancellationException("cancelled") }
		)

		assertFailsWith<CancellationException> {
			repository.getResourceBytes("resource")
		}
	}

	@Test
	fun cachedSidecarCancellationPropagatesWithoutStaleSuccessOrAvailabilityDrop() = runTest {
		var cancelFetch = false
		var nowMillis = 0L
		val preferences = configuredPreferences()
		val repository = configuredRepository(
			api = PublicationApi(
				resource = { "publication".encodeToByteArray() },
				sidecar = {
					if (cancelFetch) throw CancellationException("cancelled")
					whispersyncSidecarFixture()
				}
			),
			preferences = preferences,
			metadataCache = RecordingMetadataCache(),
			currentTimeMillis = { nowMillis }
		)
		val path = "sidecar-resource"
		repository.getWhispersyncSidecar(path).getOrThrow()
		cancelFetch = true
		nowMillis = 24L * 60L * 60L * 1_000L

		var returnedSuccess = false
		var propagatedCancellation = false
		try {
			returnedSuccess = repository.getWhispersyncSidecar(path).isSuccess
		} catch (_: CancellationException) {
			propagatedCancellation = true
		}

		assertEquals(
			CachedCancellationOutcome(
				propagatedCancellation = true,
				returnedSuccess = false,
				availabilityMarkedDown = false
			),
			CachedCancellationOutcome(
				propagatedCancellation = propagatedCancellation,
				returnedSuccess = returnedSuccess,
				availabilityMarkedDown = IntegrationService.Bindery in preferences.failedIntegrationServices
			)
		)
	}

	@Test
	fun cachedManifestCancellationPropagatesWithoutStaleSuccessOrAvailabilityDrop() = runTest {
		var cancelFetch = false
		var nowMillis = 0L
		val preferences = configuredPreferences()
		val repository = configuredRepository(
			api = PublicationApi(
				resource = { "publication".encodeToByteArray() },
				audiobookManifest = {
					if (cancelFetch) throw CancellationException("cancelled")
					BinderyManifest(id = "audio", title = "audio")
				}
			),
			preferences = preferences,
			metadataCache = RecordingMetadataCache(),
			currentTimeMillis = { nowMillis }
		)
		val manifestIdentity = "audio-resource"
		repository.getAudiobookManifest(manifestIdentity).getOrThrow()
		cancelFetch = true
		nowMillis = 24L * 60L * 60L * 1_000L

		var returnedSuccess = false
		var propagatedCancellation = false
		try {
			returnedSuccess = repository.getAudiobookManifest(manifestIdentity).isSuccess
		} catch (_: CancellationException) {
			propagatedCancellation = true
		}

		assertEquals(
			CachedCancellationOutcome(
				propagatedCancellation = true,
				returnedSuccess = false,
				availabilityMarkedDown = false
			),
			CachedCancellationOutcome(
				propagatedCancellation = propagatedCancellation,
				returnedSuccess = returnedSuccess,
				availabilityMarkedDown = IntegrationService.Bindery in preferences.failedIntegrationServices
			)
		)
	}

	@Test
	fun externalShellCoverCancellationPreventsPublicationSuccess() = runTest {
		val publicationResource = "publication-resource"
		val externalCoverResource = "external-cover-resource"
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { resource ->
				when (resource) {
					publicationResource -> readaloudFixture()
					externalCoverResource -> throw CancellationException("cancelled")
					else -> error("Unexpected synthetic resource")
				}
			},
			cacheRoot = createTempDirectory("reader-external-cover-cancellation").toFile()
		)

		assertFailsWith<CancellationException> {
			resolver.resolve(
				ReaderPublicationResourceRequest(
					bookId = "book",
					title = "title",
					resourceHref = publicationResource,
					sourceUrl = "remote",
					kind = ReaderPublicationKind.Ebook,
					mediaOverlayEnabled = false,
					externalShellCoverHref = externalCoverResource
				)
			)
		}
	}

	@Test
	fun publicationOrchestrationRejectsLateBookSyncFromReplacedSharedKeyReader() = runTest {
		val aStarted = CompletableDeferred<Unit>()
		val releaseA = CompletableDeferred<Unit>()
		val bFetched = CompletableDeferred<Unit>()
		var bookSyncFetchCount = 0
		val repository = configuredRepository(
			PublicationApi(
				bookSync = {
					bookSyncFetchCount += 1
					if (bookSyncFetchCount == 1) {
						aStarted.complete(Unit)
						try {
							releaseA.await()
						} catch (_: CancellationException) {
							withContext(NonCancellable) { releaseA.await() }
						}
						bookSyncFixture(artifactId = 1)
					} else {
						bFetched.complete(Unit)
						bookSyncFixture(artifactId = 2)
					}
				},
				resource = { "publication".encodeToByteArray() }
			)
		)
		val orchestration = ReaderPublicationReadyOrchestration(
			coroutineScope = this,
			binderyRepository = repository,
			audiobookPlaybackManager = RecordingAudiobookPlaybackManager(),
			preferenceManager = configuredPreferences(),
			ioDispatcher = UnconfinedTestDispatcher(testScheduler)
		)
		val readerA = sharedWhispersyncReader(owner = "a", artifactId = "1")
		val readerB = sharedWhispersyncReader(owner = "b", artifactId = "2")
		val recoveredOwners = mutableListOf<String>()

		orchestration.attach(readerA)
		orchestration.handle(
			orchestrationRequest(readerA, recoveredOwners, owner = "a")
		)
		runCurrent()
		assertTrue(aStarted.isCompleted, "A BookSync request did not start")
		orchestration.detach(readerA)
		orchestration.attach(readerB)
		orchestration.handle(
			orchestrationRequest(readerB, recoveredOwners, owner = "b")
		)
		runCurrent()
		assertTrue(bFetched.isCompleted, "B BookSync request did not complete")
		assertEquals(listOf("b"), recoveredOwners)

		releaseA.complete(Unit)
		runCurrent()
		assertEquals(listOf("b"), recoveredOwners)
		orchestration.close()
	}

	@Test
	fun publicationOrchestrationDoesNotMutateManagerFromCancelledCachedManifest() = runTest {
		var cancelManifest = false
		var nowMillis = 0L
		val manifestAttempted = CompletableDeferred<Unit>()
		val preferences = configuredPreferences()
		val repository = configuredRepository(
			api = PublicationApi(
				sidecar = { whispersyncSidecarFixture() },
				audiobookManifest = {
					if (cancelManifest) {
						manifestAttempted.complete(Unit)
						throw CancellationException("cancelled")
					}
					BinderyManifest(id = "audio", title = "audio")
				},
				resource = { "publication".encodeToByteArray() }
			),
			preferences = preferences,
			metadataCache = RecordingMetadataCache(),
			currentTimeMillis = { nowMillis }
		)
		repository.getAudiobookManifest("audio").getOrThrow()
		cancelManifest = true
		nowMillis = 24L * 60L * 60L * 1_000L
		val manager = RecordingAudiobookPlaybackManager()
		val orchestration = ReaderPublicationReadyOrchestration(
			coroutineScope = this,
			binderyRepository = repository,
			audiobookPlaybackManager = manager,
			preferenceManager = preferences,
			ioDispatcher = UnconfinedTestDispatcher(testScheduler)
		)
		val reader = sharedWhispersyncReader(owner = "current", artifactId = "1")
		orchestration.attach(reader)

		orchestration.handle(
			orchestrationRequest(
				reader = reader,
				recoveredOwners = mutableListOf(),
				owner = "current",
				needsWordSyncRecovery = false
			)
		)
		runCurrent()
		assertTrue(manifestAttempted.isCompleted, "Cached manifest refresh did not run")

		assertTrue(manager.loadedBookIds.isEmpty())
		orchestration.close()
	}

	@Test
	fun publicationOrchestrationRejectsDeferredSidecarOutcomesAfterReplacement() = runTest {
		DeferredCompletionOutcome.entries.forEach { assertDeferredSidecarReplacement(it) }
	}

	@Test
	fun publicationOrchestrationRejectsDeferredManifestOutcomesAfterReplacement() = runTest {
		DeferredCompletionOutcome.entries.forEach { assertDeferredManifestReplacement(it) }
	}

	private fun TestScope.assertDeferredSidecarReplacement(outcome: DeferredCompletionOutcome) {
		val aStarted = CompletableDeferred<Unit>()
		val releaseA = CompletableDeferred<Unit>()
		var sidecarFetchCount = 0
		val repository = configuredRepository(
			PublicationApi(
				sidecar = {
					sidecarFetchCount += 1
					if (sidecarFetchCount == 1) {
						aStarted.complete(Unit)
						try {
							releaseA.await()
						} catch (_: CancellationException) {
							withContext(NonCancellable) { releaseA.await() }
						}
						outcome.complete(whispersyncSidecarFixture())
					} else {
						whispersyncSidecarFixture()
					}
				},
				audiobookManifest = { BinderyManifest(id = "audio", title = "audio") },
				resource = { "publication".encodeToByteArray() }
			)
		)
		val manager = RecordingAudiobookPlaybackManager()
		val effects = RecordingOrchestrationEffects()
		val orchestration = ReaderPublicationReadyOrchestration(
			coroutineScope = this,
			binderyRepository = repository,
			audiobookPlaybackManager = manager,
			preferenceManager = configuredPreferences(),
			ioDispatcher = UnconfinedTestDispatcher(testScheduler)
		)
		val readerA = sharedWhispersyncReader(owner = "a", artifactId = "1")
		val readerB = sharedWhispersyncReader(owner = "b", artifactId = "2")

		orchestration.attach(readerA)
		orchestration.handle(orchestrationRequest(readerA, effects, owner = "a"))
		runCurrent()
		assertTrue(aStarted.isCompleted, "A sidecar request did not start for $outcome")

		orchestration.detach(readerA)
		orchestration.attach(readerB)
		orchestration.handle(orchestrationRequest(readerB, effects, owner = "b"))
		runCurrent()
		val expected = expectedReplacementEffects()
		assertEquals(expected, effects.snapshot(manager), "B did not complete for $outcome")

		releaseA.complete(Unit)
		runCurrent()
		assertEquals(expected, effects.snapshot(manager), "Late A sidecar affected B for $outcome")
		orchestration.close()
	}

	private fun TestScope.assertDeferredManifestReplacement(outcome: DeferredCompletionOutcome) {
		val aStarted = CompletableDeferred<Unit>()
		val releaseA = CompletableDeferred<Unit>()
		var manifestFetchCount = 0
		val repository = configuredRepository(
			PublicationApi(
				sidecar = { whispersyncSidecarFixture() },
				audiobookManifest = {
					manifestFetchCount += 1
					if (manifestFetchCount == 1) {
						aStarted.complete(Unit)
						try {
							releaseA.await()
						} catch (_: CancellationException) {
							withContext(NonCancellable) { releaseA.await() }
						}
						outcome.complete(BinderyManifest(id = "audio-a", title = "audio-a"))
					} else {
						BinderyManifest(id = "audio-b", title = "audio-b")
					}
				},
				resource = { "publication".encodeToByteArray() }
			)
		)
		val manager = RecordingAudiobookPlaybackManager()
		val effects = RecordingOrchestrationEffects()
		val orchestration = ReaderPublicationReadyOrchestration(
			coroutineScope = this,
			binderyRepository = repository,
			audiobookPlaybackManager = manager,
			preferenceManager = configuredPreferences(),
			ioDispatcher = UnconfinedTestDispatcher(testScheduler)
		)
		val readerA = sharedWhispersyncReader(owner = "a", artifactId = "1")
		val readerB = sharedWhispersyncReader(owner = "b", artifactId = "2")

		orchestration.attach(readerA)
		orchestration.handle(orchestrationRequest(readerA, effects, owner = "a"))
		runCurrent()
		assertTrue(aStarted.isCompleted, "A manifest request did not start for $outcome")
		assertEquals(listOf("a:loaded"), effects.sidecarCallbacks)

		orchestration.detach(readerA)
		orchestration.attach(readerB)
		orchestration.handle(orchestrationRequest(readerB, effects, owner = "b"))
		runCurrent()
		val expected = expectedReplacementEffects(sidecarCallbacks = listOf("a:loaded", "b:loaded"))
		assertEquals(expected, effects.snapshot(manager), "B did not complete for $outcome")

		releaseA.complete(Unit)
		runCurrent()
		assertEquals(expected, effects.snapshot(manager), "Late A manifest affected B for $outcome")
		orchestration.close()
	}

	@Test
	fun readaloudAbaRejectsOldControllerCallbackAndReleasesExactOwners() = runTest {
		val repository = configuredRepository(
			PublicationApi { readaloudFixture() }
		)
		val readerA = readaloudReader("a").copy(
			bookId = "shared-book",
			resourceHref = "task365-shared-readaloud-aba-v2",
			publicationUrl = "shared-publication"
		)
		val readerB = readaloudReader("b")
		val currentReader = mutableStateOf(readerA)
		val controllerFactory = RecordingReadaloudControllerFactory()
		val readyOwners = mutableListOf<String>()
		val playbackCallbacks = mutableMapOf<String, Int>()

		withKoin(repository) {
			fun setContent(fixture: ComposeFixture? = null): ComposeFixture {
				val content: @Composable () -> Unit = {
					val reader = currentReader.value
					ReaderReadaloudRuntimeHostWithControllerFactory(
						reader = reader,
						readaloudSyncEnabled = true,
						readerInteraction = null,
						readerInteractionKey = 0L,
						onPublicationReady = { readyOwners += reader.title },
						onEngineCommand = { _, _ -> },
						playbackCommand = null,
						playbackCommandKey = 0L,
						onPlaybackState = {
							playbackCallbacks[reader.title] = playbackCallbacks.getOrDefault(reader.title, 0) + 1
						},
						onError = {},
						controllerFactory = controllerFactory
					)
				}
				if (fixture == null) return compose(content)
				fixture.composition.setContent {
					CompositionLocalProvider(
						LocalContext provides RuntimeEnvironment.getApplication(),
						content = content
					)
				}
				return fixture
			}

			val fixture = setContent()
			flushUntil(fixture, "first A readaloud controller loaded") {
				controllerFactory.controllers.singleOrNull()?.loadedPlan != null
			}
			val a1Controller = controllerFactory.controllers.single()
			val oldPlan = a1Controller.loadedPlan ?: error("A1 controller did not load")

			currentReader.value = readerB
			setContent(fixture)
			flushUntil(fixture, "B readaloud controller loaded") {
				controllerFactory.controllers.getOrNull(1)?.loadedPlan != null
			}
			val bController = controllerFactory.controllers[1]

			currentReader.value = readerA
			setContent(fixture)
			flushUntil(fixture, "second A readaloud controller loaded") {
				controllerFactory.controllers.getOrNull(2)?.loadedPlan != null
			}
			val a2Controller = controllerFactory.controllers[2]

			val aCallbacksBeforeOldEmission = playbackCallbacks.getOrDefault("a", 0)
			a1Controller.emit(
				ReadaloudPlaybackPosition(
					sessionId = oldPlan.sessionId,
					trackIndex = 0,
					mediaId = oldPlan.mediaItems.firstOrNull()?.mediaId,
					positionMs = 100L,
					durationMs = 1_000L,
					isPlaying = false,
					playbackSpeed = 1f
				)
			)

			assertEquals(
				ReadaloudAbaOutcome(
					readyOwners = listOf("a", "b", "a"),
					aCallbacksAfterOldEmission = aCallbacksBeforeOldEmission,
					a1ReleaseCount = 1,
					bReleaseCount = 1,
					a2ReleaseCount = 0
				),
				ReadaloudAbaOutcome(
					readyOwners = readyOwners,
					aCallbacksAfterOldEmission = playbackCallbacks.getOrDefault("a", 0),
					a1ReleaseCount = a1Controller.releaseCount,
					bReleaseCount = bController.releaseCount,
					a2ReleaseCount = a2Controller.releaseCount
				)
			)
			fixture.composition.dispose()
		}
	}

	@Test
	fun replacementCancelsOwnedManifestBeforeGlobalMutation() = runTest {
		val owner = EnrichmentRequestOwner()
		val currentReader = mutableStateOf(reader("a"))
		val aStarted = CompletableDeferred<Unit>()
		val releaseA = CompletableDeferred<Unit>()
		val bCompleted = CompletableDeferred<Unit>()
		val globalLoads = mutableListOf<String>()

		fun setManifestContent(fixture: ComposeFixture? = null): ComposeFixture {
			val content: @Composable () -> Unit = {
				val operationReader = currentReader.value
				DisposableEffect(operationReader, owner) {
					onDispose { owner.cancel() }
				}
				LaunchedEffect(operationReader, owner) {
					owner.launch(this) {
						if (operationReader.bookId == "a") {
							aStarted.complete(Unit)
							try {
								releaseA.await()
							} catch (_: CancellationException) {
								withContext(NonCancellable) { releaseA.await() }
							}
						}
						owner.commit {
							if (currentReader.value == operationReader) {
								globalLoads += operationReader.bookId
							}
						}
						if (operationReader.bookId == "b") bCompleted.complete(Unit)
					}
				}
			}
			if (fixture == null) return compose(content)
			fixture.composition.setContent {
				CompositionLocalProvider(
					LocalContext provides RuntimeEnvironment.getApplication(),
					content = content
				)
			}
			return fixture
		}

		val fixture = setManifestContent()
		flush(fixture)
		assertTrue(aStarted.isCompleted)

		currentReader.value = reader("b")
		setManifestContent(fixture)
		flush(fixture)
		bCompleted.await()
		assertEquals(listOf("b"), globalLoads)

		releaseA.complete(Unit)
		flush(fixture)
		assertEquals(listOf("b"), globalLoads)
		fixture.composition.dispose()
	}

	@Test
	fun lateCancelledReadaloudCannotReplaceCurrentRuntimeOrCallbacks() = runTest {
		registerReadaloudService()
		val aStarted = CompletableDeferred<Unit>()
		val releaseA = CompletableDeferred<Unit>()
		val api = PublicationApi { resource ->
			if (resource == "resource-a") {
				aStarted.complete(Unit)
				try {
					releaseA.await()
				} catch (_: CancellationException) {
					withContext(NonCancellable) { releaseA.await() }
				}
			}
			readaloudFixture()
		}
		val repository = configuredRepository(api)
		val currentReader = mutableStateOf(readaloudReader("a"))
		val readyOwners = mutableListOf<String>()
		val errors = mutableListOf<String>()
		var playbackCallbackCount = 0

		withKoin(repository) {
			val fixture = compose {
				val reader = currentReader.value
				ReaderReadaloudRuntimeHost(
					reader = reader,
					readaloudSyncEnabled = true,
					readerInteraction = null,
					readerInteractionKey = 0L,
					onPublicationReady = { readyOwners += reader.bookId },
					onEngineCommand = { _, _ -> },
					playbackCommand = null,
					playbackCommandKey = 0L,
					onPlaybackState = { playbackCallbackCount += 1 },
					onError = errors::add
				)
			}
			flush(fixture)
			assertTrue(aStarted.isCompleted)

			currentReader.value = readaloudReader("b")
			fixture.composition.setContent {
				CompositionLocalProvider(LocalContext provides RuntimeEnvironment.getApplication()) {
					val reader = currentReader.value
					ReaderReadaloudRuntimeHost(
						reader = reader,
						readaloudSyncEnabled = true,
						readerInteraction = null,
						readerInteractionKey = 0L,
						onPublicationReady = { readyOwners += reader.bookId },
						onEngineCommand = { _, _ -> },
						playbackCommand = null,
						playbackCommandKey = 0L,
						onPlaybackState = { playbackCallbackCount += 1 },
						onError = errors::add
					)
				}
			}
			flushUntil(fixture, "replacement readaloud ready") {
				readyOwners == listOf("b")
			}
			assertEquals(listOf("b"), readyOwners)
			val bPlaybackCallbackCount = playbackCallbackCount

			releaseA.complete(Unit)
			flush(fixture)

			assertEquals(listOf("b"), readyOwners)
			assertEquals(bPlaybackCallbackCount, playbackCallbackCount)
			assertTrue(errors.isEmpty())
			fixture.composition.dispose()
		}
	}

	@Test
	fun readaloudCancellationDoesNotBecomeFailureOrUnavailableState() = runTest {
		registerReadaloudService()
		val repository = configuredRepository(
			PublicationApi { throw CancellationException("cancelled") }
		)
		val errors = mutableListOf<String>()
		var playbackCallbackCount = 0

		withKoin(repository) {
			val fixture = compose {
				ReaderReadaloudRuntimeHost(
					reader = readaloudReader("c"),
					readaloudSyncEnabled = true,
					readerInteraction = null,
					readerInteractionKey = 0L,
					onPublicationReady = {},
					onEngineCommand = { _, _ -> },
					playbackCommand = null,
					playbackCommandKey = 0L,
					onPlaybackState = { playbackCallbackCount += 1 },
					onError = errors::add
				)
			}
			flush(fixture)

			assertTrue(errors.isEmpty())
			assertEquals(0, playbackCallbackCount)
			fixture.composition.dispose()
		}
	}

	private fun reader(identity: String) = Screen.Reader(
		title = identity,
		publicationUrl = "remote-$identity",
		bookId = identity,
		resourceHref = "resource-$identity",
		kind = ReaderPublicationKind.Ebook
	)

	private fun readaloudReader(identity: String) = Screen.Reader(
		title = identity,
		publicationUrl = "remote-$identity",
		bookId = identity,
		resourceHref = "resource-$identity",
		kind = ReaderPublicationKind.Readaloud,
		mediaOverlayEnabled = true
	)

	private fun readaloudFixture(): ByteArray {
		val entries = mapOf(
			"META-INF/container.xml" to """
				<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
					<rootfiles><rootfile full-path="EPUB/package.opf"/></rootfiles>
				</container>
			""".trimIndent().encodeToByteArray(),
			"EPUB/package.opf" to """
				<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
					<metadata><meta property="media:duration">1s</meta></metadata>
					<manifest>
						<item id="text" href="text.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
						<item id="overlay" href="overlay.smil" media-type="application/smil+xml"/>
						<item id="audio" href="audio.mp3" media-type="audio/mpeg"/>
					</manifest>
					<spine><itemref idref="text"/></spine>
				</package>
			""".trimIndent().encodeToByteArray(),
			"EPUB/overlay.smil" to """
				<smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
					<body><seq><par><text src="text.xhtml#part"/><audio src="audio.mp3" clipBegin="0s" clipEnd="1s"/></par></seq></body>
				</smil>
			""".trimIndent().encodeToByteArray(),
			"EPUB/text.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p id=\"part\">Text</p></body></html>".encodeToByteArray(),
			"EPUB/audio.mp3" to byteArrayOf(1)
		)
		return ByteArrayOutputStream().use { output ->
			ZipOutputStream(output).use { zip ->
				entries.forEach { (name, bytes) ->
					zip.putNextEntry(ZipEntry(name))
					zip.write(bytes)
					zip.closeEntry()
				}
			}
			output.toByteArray()
		}
	}

	@Suppress("DEPRECATION")
	private fun registerReadaloudService() {
		val context = RuntimeEnvironment.getApplication()
		val serviceInfo = ServiceInfo().apply {
			packageName = context.packageName
			name = ReadaloudPlaybackService::class.java.name
		}
		val serviceIntent = Intent("androidx.media3.session.MediaSessionService")
			.setPackage(context.packageName)
		shadowOf(context.packageManager).apply {
			addOrUpdateService(serviceInfo)
			addResolveInfoForIntent(
				serviceIntent,
				ResolveInfo().apply { this.serviceInfo = serviceInfo }
			)
		}
	}

	private fun configuredPreferences() = PreferenceManager(MapSettings()).apply {
		binderyEnabled = true
		binderyOpdsBaseUrl = "https://configured.invalid/opds"
		binderyApiKey = "test-key"
	}

	private fun configuredRepository(
		api: BinderyApiClient,
		preferences: PreferenceManager = configuredPreferences(),
		metadataCache: BinderyMetadataCache = NoOpBinderyMetadataCache,
		currentTimeMillis: () -> Long = { 0L }
	): BinderyRepository = BinderyRepository(
		preferenceManager = preferences,
		apiClient = api,
		metadataCache = metadataCache,
		currentTimeMillis = currentTimeMillis
	)

	private suspend fun withKoin(repository: BinderyRepository, block: suspend () -> Unit) {
		stopKoin()
		startKoin {
			modules(module { single { repository } })
		}
		try {
			block()
		} finally {
			stopKoin()
		}
	}

	private fun TestScope.compose(content: @Composable () -> Unit): ComposeFixture {
		val dispatcher = UnconfinedTestDispatcher(testScheduler)
		val frameClock = BroadcastFrameClock()
		val recomposer = Recomposer(dispatcher + frameClock)
		backgroundScope.launch(dispatcher + frameClock) {
			recomposer.runRecomposeAndApplyChanges()
		}
		val composition = Composition(NoOpApplier(), recomposer)
		composition.setContent {
			CompositionLocalProvider(
				LocalContext provides RuntimeEnvironment.getApplication(),
				content = content
			)
		}
		return ComposeFixture(composition, frameClock)
	}

	private fun TestScope.flush(fixture: ComposeFixture) {
		Snapshot.sendApplyNotifications()
		runCurrent()
		fixture.frameClock.sendFrame(++fixture.frame * 1_000_000L)
		runCurrent()
	}

	private fun TestScope.flushUntil(
		fixture: ComposeFixture,
		description: String,
		condition: () -> Boolean
	) {
		val deadlineNanos = System.nanoTime() + 5_000_000_000L
		while (true) {
			flush(fixture)
			if (condition()) return
			check(System.nanoTime() < deadlineNanos) { "Timed out waiting for $description." }
			Thread.sleep(5L)
		}
	}
}

private data class ComposeFixture(
	val composition: Composition,
	val frameClock: BroadcastFrameClock,
	var frame: Long = 0L
)

private class NoOpApplier : AbstractApplier<Unit>(Unit) {
	override fun insertTopDown(index: Int, instance: Unit) = Unit
	override fun insertBottomUp(index: Int, instance: Unit) = Unit
	override fun remove(index: Int, count: Int) = Unit
	override fun move(from: Int, to: Int, count: Int) = Unit
	override fun onClear() = Unit
}

private class PublicationApi(
	private val sidecar: suspend (String) -> String = { "{}" },
	private val audiobookManifest: suspend (String) -> BinderyManifest = {
		BinderyManifest(id = "audio", title = "audio")
	},
	private val bookSync: suspend (String) -> BinderyBookSync = { BinderyBookSync() },
	private val resource: suspend (String) -> ByteArray
) : BinderyApiClient {
	override suspend fun fetchRootCatalog(baseUrl: String, requestHeaders: Map<String, String>) =
		BinderyCatalog(title = "root")

	override suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) = BinderyCatalog(title = "catalog")

	override suspend fun fetchManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	) = BinderyManifest(id = "manifest", title = "manifest")

	override suspend fun fetchBookResources(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	) = BinderyResourceCatalog(title = "resources")

	override suspend fun fetchAudiobookVersions(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		limit: Int
	) = emptyList<BinderyAudiobookVersion>()

	override suspend fun fetchAudiobookVersion(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	) = BinderyAudiobookVersion(id = 1)

	override suspend fun fetchAudiobookManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	) = audiobookManifest(audiobookId)

	override suspend fun fetchAudiobookManifestPath(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) = BinderyManifest(id = "audio", title = "audio")

	override suspend fun fetchBookSync(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	) = bookSync(bookId)

	override suspend fun fetchWhispersyncSidecarJson(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) = sidecar(path)

	override suspend fun fetchWordSyncIndexJson(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		identity: BinderyWhispersyncIdentity,
		advertisedHref: String
	) = "{}"

	override suspend fun fetchWordSyncChapterJson(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		identity: BinderyWhispersyncIdentity,
		chapterKey: String,
		advertisedHref: String
	) = "{}"

	override suspend fun fetchResourceBytes(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) = resource(path)

	override suspend fun fetchReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		alias: String?
	) = BinderyReadingProgress(bookId = bookId, kind = BinderyReadingProgressKind.Ebook)

	override suspend fun putReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		progress: BinderyReadingProgress
	) = Unit

	override suspend fun fetchBookFindings(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	) = BinderyCatalog(title = "findings")

	override suspend fun fetchExternalText(url: String, purpose: ExternalTextPurpose) = ""

	override suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) = Unit
}

private data class ReadaloudAbaOutcome(
	val readyOwners: List<String>,
	val aCallbacksAfterOldEmission: Int,
	val a1ReleaseCount: Int,
	val bReleaseCount: Int,
	val a2ReleaseCount: Int
)

private class RecordingReadaloudControllerFactory : ReaderReadaloudControllerFactory {
	val controllers = mutableListOf<RecordingReadaloudController>()

	override fun create(
		context: android.content.Context,
		onPositionChanged: (ReadaloudPlaybackPosition) -> Unit
	): ReaderReadaloudController = RecordingReadaloudController(onPositionChanged).also(controllers::add)
}

private class RecordingReadaloudController(
	private val onPositionChanged: (ReadaloudPlaybackPosition) -> Unit
) : ReaderReadaloudController {
	var loadedPlan: ReadaloudPlaybackPlan? = null
	var releaseCount: Int = 0

	override fun load(plan: ReadaloudPlaybackPlan, playWhenReady: Boolean) {
		loadedPlan = plan
	}

	override fun play() = Unit
	override fun pause() = Unit
	override fun stopAndReset() = Unit
	override fun seekTo(positionMs: Long) = Unit
	override fun seekTo(trackIndex: Int, positionMs: Long) = Unit
	override fun setPlaybackSpeed(value: Float) = Unit
	override fun release() {
		releaseCount += 1
	}

	fun emit(position: ReadaloudPlaybackPosition) {
		onPositionChanged(position)
	}
}

private fun sharedWhispersyncReader(owner: String, artifactId: String) = Screen.Reader(
	title = owner,
	publicationUrl = "shared-publication",
	bookId = "1",
	resourceHref = "shared-resource",
	kind = ReaderPublicationKind.Ebook,
	mediaOverlayEnabled = true,
	whispersyncSidecarUrl = "sidecar-resource",
	whispersyncArtifactId = artifactId,
	whispersyncAudiobookId = "audio",
	whispersyncAudiobookBookFileId = "20"
)

private fun orchestrationRequest(
	reader: Screen.Reader,
	recoveredOwners: MutableList<String>,
	owner: String,
	needsWordSyncRecovery: Boolean = true
) = ReaderPublicationReadyOrchestrationRequest(
	reader = reader,
	attachment = reader.whispersyncLaunchAttachment()!!,
	wordSyncVerifier = object : WordSyncPublicationVerifier {
		override fun verify(index: WordSyncIndex): WordSyncPublicationVerificationSession =
			error("Verification is not exercised by the ownership test")
	},
	needsWordSyncRecovery = needsWordSyncRecovery,
	audiobookIdentity = "audio",
	playbackSpeed = 1f,
	callbacks = ReaderPublicationReadyOrchestrationCallbacks(
		onWordSyncRecovered = { _, _ -> recoveredOwners += owner },
		onSidecarLoaded = {},
		onSidecarUnavailable = {},
		onPlaybackPlanChanged = {},
		onAudiobookUnavailable = {}
	)
)

private fun orchestrationRequest(
	reader: Screen.Reader,
	effects: RecordingOrchestrationEffects,
	owner: String
) = ReaderPublicationReadyOrchestrationRequest(
	reader = reader,
	attachment = reader.whispersyncLaunchAttachment()!!,
	wordSyncVerifier = null,
	needsWordSyncRecovery = false,
	audiobookIdentity = "audio",
	playbackSpeed = 1f,
	callbacks = ReaderPublicationReadyOrchestrationCallbacks(
		onWordSyncRecovered = { _, _ -> error("WordSync recovery was not requested") },
		onSidecarLoaded = { effects.sidecarCallbacks += "$owner:loaded" },
		onSidecarUnavailable = { effects.sidecarCallbacks += "$owner:unavailable" },
		onPlaybackPlanChanged = { plan ->
			effects.playbackPlanCallbacks += "$owner:${if (plan == null) "unavailable" else "loaded"}"
		},
		onAudiobookUnavailable = { effects.audiobookUnavailableCallbacks += owner }
	)
)

private fun bookSyncFixture(artifactId: Long) = BinderyBookSync(
	bookId = 1,
	syncPairs = listOf(
		BinderySyncPair(
			bookId = 1,
			ebookBookFileId = 10,
			audiobookBookFileId = 20,
			whispersync = BinderyWhispersyncArtifact(
				artifactId = artifactId,
				wordSync = BinderyWordSyncDiscovery(
					status = "ready",
					schema = "bindery.whispersync.wordsync.index.v1",
					opdsIndexHref = "word-index",
					format = "chapter-sharded-json",
					compression = "http",
					timeScale = 1000
				)
			)
		)
	)
)

private enum class DeferredCompletionOutcome {
	Success,
	OrdinaryFailure,
	Cancellation;

	fun <T> complete(value: T): T = when (this) {
		Success -> value
		OrdinaryFailure -> throw IllegalStateException("ordinary failure")
		Cancellation -> throw CancellationException("cancelled")
	}
}

private data class OrchestrationEffectSnapshot(
	val sidecarCallbacks: List<String>,
	val playbackPlanCallbacks: List<String>,
	val audiobookUnavailableCallbacks: List<String>,
	val managerLoadTitles: List<String>,
	val managerDispatchCount: Int
)

private class RecordingOrchestrationEffects {
	val sidecarCallbacks = mutableListOf<String>()
	val playbackPlanCallbacks = mutableListOf<String>()
	val audiobookUnavailableCallbacks = mutableListOf<String>()

	fun snapshot(manager: RecordingAudiobookPlaybackManager) = OrchestrationEffectSnapshot(
		sidecarCallbacks = sidecarCallbacks.toList(),
		playbackPlanCallbacks = playbackPlanCallbacks.toList(),
		audiobookUnavailableCallbacks = audiobookUnavailableCallbacks.toList(),
		managerLoadTitles = manager.loadedBookTitles.toList(),
		managerDispatchCount = manager.dispatchCount
	)
}

private fun expectedReplacementEffects(
	sidecarCallbacks: List<String> = listOf("b:loaded")
) = OrchestrationEffectSnapshot(
	sidecarCallbacks = sidecarCallbacks,
	playbackPlanCallbacks = listOf("b:loaded"),
	audiobookUnavailableCallbacks = emptyList(),
	managerLoadTitles = listOf("b"),
	managerDispatchCount = 1
)

private class RecordingAudiobookPlaybackManager : AudiobookPlaybackManager {
	private val mutableUiState = MutableStateFlow(AudiobookMiniPlayerUiState())
	override val uiState: StateFlow<AudiobookMiniPlayerUiState> = mutableUiState
	private val mutableTimelineRevision = MutableStateFlow(0L)
	override val playbackTimelineRevision: StateFlow<Long> = mutableTimelineRevision
	val loadedBookIds = mutableListOf<String>()
	val loadedBookTitles = mutableListOf<String>()
	var dispatchCount: Int = 0

	override fun currentPlaybackTimelineSnapshot(): AudiobookPlaybackTimelineSnapshot? = null

	override fun load(
		playbackPlan: ReadaloudPlaybackPlan?,
		bookId: String,
		bookTitle: String,
		versionRowId: String,
		coverUrl: String?,
		coverCacheKey: String?,
		imageRequestHeaders: Map<String, String>,
		playWhenReady: Boolean
	) {
		loadedBookIds += bookId
		loadedBookTitles += bookTitle
	}

	override fun dispatch(command: ReaderReadaloudPlaybackCommand) {
		dispatchCount += 1
	}
}

private data class CachedCancellationOutcome(
	val propagatedCancellation: Boolean,
	val returnedSuccess: Boolean,
	val availabilityMarkedDown: Boolean
)

private class RecordingMetadataCache : BinderyMetadataCache {
	private val records = mutableMapOf<String, BinderyMetadataCacheRecord>()

	override suspend fun get(cacheKey: String): BinderyMetadataCacheRecord? = records[cacheKey]

	override suspend fun put(record: BinderyMetadataCacheRecord) {
		records[record.cacheKey] = record
	}

	override suspend fun clearPayload(
		baseUrl: String,
		payloadType: String,
		path: String?,
		pathPrefix: Boolean
	) = Unit

	override suspend fun clearBaseUrl(baseUrl: String) = Unit
}

private fun whispersyncSidecarFixture(): String =
	"""
	{
	  "artifactId": "synthetic-artifact",
	  "ebookBookFileId": "ebook-file",
	  "audiobookBookFileId": "audio-file",
	  "segments": [
	    {
	      "audioHref": "audio-resource",
	      "startMs": 0,
	      "endMs": 1000,
	      "textHref": "text-resource",
	      "fragmentId": "segment",
	      "textStart": 0,
	      "textEnd": 4,
	      "spokenText": "Test",
	      "ebookText": "Test"
	    }
	  ]
	}
	""".trimIndent()
