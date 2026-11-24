package paige.navic.reader

import paige.navic.domain.repositories.BinderyWordSyncDiscovery
import paige.navic.domain.repositories.BinderyWordSyncReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWordSyncPlaybackCoordinatorTest {
	private val identity = WordSyncTestFixtures.identity()
	private val index = decodeWordSyncIndex(WordSyncTestFixtures.indexJson(), identity)
	private val chapter = decodeWordSyncChapter(
		WordSyncTestFixtures.chapterJson(),
		identity,
		index.chapters.single()
	)
	private val provenance = WordSyncPublicationProvenance(
		coordinateBasis = index.coordinateBasis,
		chapters = listOf(
			WordSyncPublicationChapterProvenance(
				chapterKey = chapter.chapterKey,
				ebookHref = chapter.ebookHref,
				spineIndex = chapter.spineIndex,
				sourceHash = "sha256:${"b".repeat(64)}",
				extractedTextHash = "sha256:${"c".repeat(64)}",
				extractedByteLength = 109,
				tokenCount = 3
			)
		)
	)
	private val reference = BinderyWordSyncReference(
		identity = identity,
		discovery = BinderyWordSyncDiscovery(
			status = "ready",
			schema = WordSyncIndexSchema,
			indexHref = "/api/v1/sync/artifacts/17/wordsync",
			format = "json",
			compression = "identity",
			timeScale = WordSyncTimeScale,
			shardCount = 1
		)
	)

	@Test
	fun coalescesIndexAndCurrentChapterLoads() {
		val cue = cueCommand()
		val configured = ReaderWordSyncPlaybackCoordinator().configure(reference)
		val first = configured.coordinate(
			controllerStep = ReaderControllerStep(ReaderController(), engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050)
		)
		val indexEffect = assertIs<ReaderWordSyncEffect.LoadIndex>(first.effects.single())
		assertEquals(listOf(cue), first.controllerStep.engineCommands)

		val duplicate = first.coordinator.coordinate(
			controllerStep = ReaderControllerStep(ReaderController(), engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050)
		)
		assertTrue(duplicate.effects.isEmpty())

		val indexed = duplicate.coordinator.onIndexVerified(
			generation = indexEffect.generation,
			index = index,
			provenance = provenance,
			controller = ReaderController()
		)
		val chapterEffect = assertIs<ReaderWordSyncEffect.LoadChapter>(indexed.effects.single())
		assertEquals(chapter.chapterKey, chapterEffect.summary.chapterKey)

		val chapterDuplicate = indexed.coordinator.coordinate(
			controllerStep = ReaderControllerStep(ReaderController(), engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050)
		)
		assertTrue(chapterDuplicate.effects.isEmpty())
	}

	@Test
	fun boundaryInputDiagnosticDistinguishesDataDemandFromTrackAuthority() {
		val playback = playbackIdentity(positionMs = 1_050)
		val configured = ReaderWordSyncPlaybackCoordinator().configure(reference)

		assertEquals(
			ReaderWordSyncBoundaryInputDiagnostic(
				referencePresent = true,
				indexState = ReaderWordSyncDiagnosticIndexState.Missing,
				loadedChapterCount = 0,
				pendingChapterCount = 0,
				failedChapterCount = 0,
				resourceMatchingTrackCount = 0,
				trackIndexMatchingTrackCount = 0,
				matchingTrackCount = 0,
				presentableWordCount = 0
			),
			configured.boundaryInputDiagnostic(playback)
		)

		val demand = configured.coordinate(
			controllerStep = ReaderControllerStep(
				ReaderController(),
				engineCommands = listOf(cueCommand())
			),
			playback = playback
		)
		assertEquals(
			ReaderWordSyncDiagnosticIndexState.Pending,
			demand.coordinator.boundaryInputDiagnostic(playback).indexState
		)

		val indexGeneration = assertIs<ReaderWordSyncEffect.LoadIndex>(demand.effects.single()).generation
		val indexed = demand.coordinator.onIndexVerified(
			generation = indexGeneration,
			index = index,
			provenance = provenance,
			controller = ReaderController()
		)
		assertEquals(
			ReaderWordSyncBoundaryInputDiagnostic(
				referencePresent = true,
				indexState = ReaderWordSyncDiagnosticIndexState.Ready,
				loadedChapterCount = 0,
				pendingChapterCount = 1,
				failedChapterCount = 0,
				resourceMatchingTrackCount = 0,
				trackIndexMatchingTrackCount = 0,
				matchingTrackCount = 0,
				presentableWordCount = 0
			),
			indexed.coordinator.boundaryInputDiagnostic(playback)
		)

		val chapterGeneration = assertIs<ReaderWordSyncEffect.LoadChapter>(indexed.effects.single()).generation
		val loaded = indexed.coordinator.onChapterVerified(
			generation = chapterGeneration,
			chapter = chapter,
			controller = ReaderController()
		).coordinator
		assertEquals(1, loaded.boundaryInputDiagnostic(playback).resourceMatchingTrackCount)
		assertEquals(1, loaded.boundaryInputDiagnostic(playback).trackIndexMatchingTrackCount)
		assertEquals(1, loaded.boundaryInputDiagnostic(playback).matchingTrackCount)
		assertEquals(
			chapter.tracks.flatMap { it.words }.count { it.status in 1..4 },
			loaded.boundaryInputDiagnostic(playback).presentableWordCount
		)
		val wrongTrackIdentity = playback.copy(audioTrackIndex = 1)
		val wrongTrack = loaded.boundaryInputDiagnostic(wrongTrackIdentity)
		assertEquals(
			chapter.tracks.count { it.audioResourceId == wrongTrackIdentity.audioResourceId },
			wrongTrack.resourceMatchingTrackCount
		)
		assertEquals(
			chapter.tracks.count { it.audioTrackIndex == wrongTrackIdentity.audioTrackIndex },
			wrongTrack.trackIndexMatchingTrackCount
		)
		assertEquals(
			chapter.tracks.count {
				it.audioTrackIndex == wrongTrackIdentity.audioTrackIndex
			},
			wrongTrack.matchingTrackCount
		)
		val wrongResourceIdentity = playback.copy(audioResourceId = "audio-other")
		val wrongResource = loaded.boundaryInputDiagnostic(wrongResourceIdentity)
		assertEquals(
			chapter.tracks.count { it.audioResourceId == wrongResourceIdentity.audioResourceId },
			wrongResource.resourceMatchingTrackCount
		)
		assertEquals(
			chapter.tracks.count { it.audioTrackIndex == wrongResourceIdentity.audioTrackIndex },
			wrongResource.trackIndexMatchingTrackCount
		)
		assertEquals(
			chapter.tracks.count {
				it.audioTrackIndex == wrongResourceIdentity.audioTrackIndex
			},
			wrongResource.matchingTrackCount
		)
	}

	@Test
	fun ignoresStaleGenerationResults() {
		val initial = ReaderWordSyncPlaybackCoordinator().configure(reference)
		val demand = initial.coordinate(
			controllerStep = ReaderControllerStep(ReaderController(), engineCommands = listOf(cueCommand())),
			playback = playbackIdentity(positionMs = 1_050)
		)
		val staleGeneration = assertIs<ReaderWordSyncEffect.LoadIndex>(demand.effects.single()).generation
		val replaced = demand.coordinator.configure(reference.copy(identity = identity.copy(artifactId = 18)))

		val stale = replaced.onIndexVerified(
			generation = staleGeneration,
			index = index,
			provenance = provenance,
			controller = ReaderController()
		)

		assertNull(stale.coordinator.index)
		assertTrue(stale.effects.isEmpty())
	}

	@Test
	fun verifiedChapterInstallsOnlyItsExactProvenanceDescriptor() {
		val demand = ReaderWordSyncPlaybackCoordinator()
			.configure(reference)
			.coordinate(
				controllerStep = ReaderControllerStep(ReaderController(), engineCommands = listOf(cueCommand())),
				playback = playbackIdentity(positionMs = 1_050)
			)
		val indexGeneration = assertIs<ReaderWordSyncEffect.LoadIndex>(demand.effects.single()).generation
		val indexed = demand.coordinator.onIndexVerified(
			generation = indexGeneration,
			index = index,
			provenance = provenance,
			controller = ReaderController()
		)
		val chapterGeneration = assertIs<ReaderWordSyncEffect.LoadChapter>(indexed.effects.single()).generation

		val installed = indexed.coordinator.onChapterVerified(
			generation = chapterGeneration,
			chapter = chapter,
			controller = ReaderController()
		)

		val command = assertIs<ReaderEngineCommand.InstallRawTextProvenance>(
			installed.controllerStep.engineCommands.single()
		)
		assertEquals(descriptor(), command.descriptor)
		assertEquals(
			RawTextProvenanceStatus.Pending,
			installed.controllerStep.controller.state.rawTextProvenanceById[descriptor().id]?.status
		)
		assertEquals(setOf(chapter.chapterKey), installed.coordinator.chapters.keys)
	}

	@Test
	fun verifiedChapterKeepsTheCanonicalSidecarsReadyProof() {
		// #24: the canonical sidecar already installed this exact proof; re-installing it reset it
		// to Pending and paused playback as soon as the WordSync chapter arrived.
		val d = descriptor()
		val sidecar = decodeWhispersyncSidecar(
			"""
			{
			  "coordinateBasis": {
			    "extractor": "bindery-epub-text",
			    "extractorVersion": "1",
			    "normalization": "raw-extracted-text-offsets",
			    "unit": "utf8-byte",
			    "scope": "spine",
			    "spines": [{
			      "href": "${d.href}",
			      "spineIndex": ${d.spineIndex},
			      "sourceHash": "${d.sourceHash}",
			      "extractedTextHash": "${d.extractedTextHash}",
			      "byteLength": ${d.byteLength},
			      "tokenCount": ${d.tokenCount}
			    }]
			  },
			  "cues": [{
			    "id": 1040,
			    "audioHref": "Audio/a.mp3",
			    "audioStart": 1,
			    "audioEnd": 2,
			    "ebookHref": "${d.href}",
			    "spineIndex": ${d.spineIndex},
			    "ebookStart": 0,
			    "ebookEnd": 1,
			    "ebookText": "x"
			  }]
			}
			""".trimIndent()
		)
		val loaded = ReaderController().loadWhispersyncSidecar(sidecar).controller
		assertEquals(listOf(d), loaded.state.whispersync.sidecar?.referencedRawTextProvenanceDescriptors())
		val ready = loaded.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(provenanceId = d.id, status = RawTextProvenanceStatus.Ready)
		).controller
		val demand = ReaderWordSyncPlaybackCoordinator()
			.configure(reference)
			.coordinate(
				controllerStep = ReaderControllerStep(ready, engineCommands = listOf(cueCommand())),
				playback = playbackIdentity(positionMs = 1_050)
			)
		val indexed = demand.coordinator.onIndexVerified(
			generation = assertIs<ReaderWordSyncEffect.LoadIndex>(demand.effects.single()).generation,
			index = index,
			provenance = provenance,
			controller = ready
		)

		val verified = indexed.coordinator.onChapterVerified(
			generation = assertIs<ReaderWordSyncEffect.LoadChapter>(indexed.effects.single()).generation,
			chapter = chapter,
			controller = ready
		)

		assertTrue(verified.controllerStep.engineCommands.none { it is ReaderEngineCommand.InstallRawTextProvenance })
		assertEquals(RawTextProvenanceStatus.Ready, verified.controllerStep.controller.state.rawTextProvenanceById[d.id]?.status)
		assertNull(verified.controllerStep.readaloudPlaybackCommand)
		assertEquals(setOf(chapter.chapterKey), verified.coordinator.chapters.keys)
	}

	@Test
	fun explicitTrackIndexOutranksAStaleRuntimeResourceIdentity() {
		val ready = readyCoordinatorAndController()
		val cue = cueCommand()
		val playback = playbackIdentity(positionMs = 1_050).copy(
			audioResourceId = "runtime-audio-alias"
		)

		val raw = ready.first.coordinate(
			controllerStep = ReaderControllerStep(ready.second, engineCommands = listOf(cue)),
			playback = playback
		)
		val command = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			raw.controllerStep.engineCommands.single()
		)
		assertEquals(ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8, command.fragment.coordinateMode)
		assertEquals(cue.fragment.overlayRequestId, command.fragment.overlayRequestId)
		assertEquals(playback.audioResourceId, command.fragment.resourceHref)
		assertEquals("Text/chapter.xhtml", command.fragment.textHref)
		assertEquals(0L, command.fragment.wordBoundarySequence)
		assertEquals(100, command.fragment.rawByteStart)
		assertEquals(102, command.fragment.rawByteEnd)
		assertEquals(2, command.fragment.rawSpineIndex)

		val boundaries = raw.coordinator.boundariesForPlayback(playback)
		assertTrue(boundaries.isNotEmpty())
		val scheduled = raw.coordinator.coordinateBoundary(
			controller = raw.controllerStep.controller,
			playback = playback.copy(positionMs = 1_350),
			boundary = boundaries.single { it.audioStartMs == 1_300L }
		)
		val scheduledCommand = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(
			scheduled.controllerStep.engineCommands.single()
		)
		assertEquals(playback.audioResourceId, scheduledCommand.fragment.resourceHref)

		val pendingCueSeek = ReaderWhispersyncPendingAudioSeek(
			overlayRequestId = 41,
			target = WhispersyncAudioSeekTarget(
				audioResource = playback.audioResourceId,
				positionMs = 900,
				segment = cueSegment()
			)
		)
		val seekController = raw.controllerStep.controller.copy(
			state = raw.controllerStep.controller.state.copy(
				whispersync = raw.controllerStep.controller.state.whispersync.copy(
					pendingAudioSeek = pendingCueSeek
				)
			)
		)
		val rawSeek = raw.coordinator.coordinateReaderEvent(
			controllerStep = ReaderControllerStep(seekController, engineCommands = listOf(cue)),
			rawPoint = ReaderWordSyncRawPoint(descriptor().id, byteOffset = 103)
		)
		assertEquals(
			playback.audioResourceId,
			rawSeek.controllerStep.controller.state.whispersync.pendingAudioSeek?.target?.audioResource
		)

		val absentTrack = raw.coordinator.coordinate(
			controllerStep = ReaderControllerStep(ready.second, engineCommands = listOf(cue)),
			playback = playback.copy(audioTrackIndex = 99)
		)
		assertEquals(listOf(cue), absentTrack.controllerStep.engineCommands)
	}

	@Test
	fun ambiguousIndexedResourcesForOneTrackIndexFailClosedBeforeAllChaptersLoad() {
		val duplicateSummary = index.chapters.single().let { summary ->
			summary.copy(
				chapterKey = "duplicate-chapter",
				spineIndex = summary.spineIndex + 1,
				ebookHref = "Text/duplicate.xhtml",
				audioRanges = summary.audioRanges.map { range ->
					range.copy(audioResourceId = "duplicate-artifact-resource")
				}
			)
		}
		val ready = readyCoordinatorAndController().first.copy(
			index = index.copy(chapters = index.chapters + duplicateSummary)
		)
		val playback = playbackIdentity(positionMs = 1_050).copy(
			audioResourceId = "runtime-audio-alias"
		)
		val cue = cueCommand()

		assertEquals(1, ready.chapters.size)
		assertTrue(ready.boundariesForPlayback(playback).isEmpty())
		assertEquals(0, ready.boundaryInputDiagnostic(playback).matchingTrackCount)
		val coordinated = ready.coordinate(
			controllerStep = ReaderControllerStep(
				readyCoordinatorAndController().second,
				engineCommands = listOf(cue)
			),
			playback = playback
		)
		assertEquals(listOf(cue), coordinated.controllerStep.engineCommands)
	}

	@Test
	fun exactBoundaryPresentationRequiresCurrentReadyProvenance() {
		val (ready, controller) = readyCoordinatorAndController()
		val playback = playbackIdentity(positionMs = 1_050)
		val current = ready.coordinate(
			controllerStep = ReaderControllerStep(
				controller,
				engineCommands = listOf(cueCommand())
			),
			playback = playback
		).coordinator

		assertTrue(current.hasExactBoundaryPresentation(controller, playback))
		assertFalse(
			current.hasExactBoundaryPresentation(
				controller,
				playback.copy(audioTrackIndex = 1)
			)
		)
		assertFalse(
			current.hasExactBoundaryPresentation(
				ReaderController().installRawTextProvenance(descriptor()).controller,
				playback
			)
		)
	}

	@Test
	fun wordGapKeepsExactPresentationInsteadOfCueFallback() {
		val (ready, controller) = readyCoordinatorAndController()
		val currentPlayback = playbackIdentity(positionMs = 1_050)
		val current = ready.coordinate(
			controllerStep = ReaderControllerStep(
				controller,
				engineCommands = listOf(cueCommand())
			),
			playback = currentPlayback
		).coordinator
		val gapPlayback = playbackIdentity(positionMs = 1_250)
		val cueProgress = ReaderEngineCommand.UpdateMediaOverlayProgress(cueCommand().fragment)

		assertTrue(current.hasExactBoundaryPresentation(controller, currentPlayback))
		// The gap between two words keeps the last word mark: no coarse cue flip-flop.
		assertTrue(current.hasExactBoundaryPresentation(controller, gapPlayback))
		// Before the first word and after the chapter's narration the cue fallback still applies.
		assertFalse(current.hasExactBoundaryPresentation(controller, playbackIdentity(positionMs = 950)))
		assertFalse(current.hasExactBoundaryPresentation(controller, playbackIdentity(positionMs = 60_000)))
		val fallback = current.coordinate(
			controllerStep = ReaderControllerStep(
				controller,
				engineCommands = listOf(cueProgress)
			),
			playback = gapPlayback
		)
		assertEquals(listOf(cueProgress), fallback.controllerStep.engineCommands)
	}

	@Test
	fun boundaryOfFirstWordPastVisiblePageEndPausesThere() {
		val (ready, base) = readyCoordinatorAndController()
		val controller = base.copy(
			state = base.state.copy(
				whispersync = base.state.whispersync.copy(
					transportPhase = ReaderWhispersyncTransportPhase.Playing,
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = "Text/chapter.xhtml",
						visibleStart = 10,
						visibleEnd = 20,
						rawProvenanceId = descriptor().id,
						rawSpineIndex = 2,
						rawByteStart = 100,
						rawByteEnd = 103
					)
				)
			)
		)
		val playback = playbackIdentity(positionMs = 1_050)
		val current = ready.coordinate(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(cueCommand())),
			playback = playback
		)
		val boundaries = current.coordinator.boundariesForPlayback(playback)

		val onPage = current.coordinator.coordinateBoundary(
			controller = current.controllerStep.controller,
			playback = playback.copy(positionMs = 1_000),
			boundary = boundaries.single { it.audioStartMs == 1_000L }
		)
		assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(onPage.controllerStep.engineCommands.single())
		assertNull(onPage.controllerStep.readaloudPlaybackCommand)

		// The last visible word's own end is silence inside the page, never the page end: the
		// narration between words keeps playing until the scheduler's PageEnd transition.
		val lastWordEnded = onPage.coordinator.coordinateClear(
			controller = onPage.controllerStep.controller,
			playback = playback.copy(positionMs = 1_250)
		)
		assertNull(lastWordEnded.controllerStep.readaloudPlaybackCommand)
		assertEquals(
			ReaderWhispersyncTransportPhase.Playing,
			lastWordEnded.controllerStep.controller.state.whispersync.transportPhase
		)

		// The visible page ends where the first word past it starts.
		val pageEnd = assertNotNull(onPage.coordinator.visiblePageEnd(onPage.controllerStep.controller, playback))
		assertEquals("next-word", pageEnd.source)
		assertEquals(1_300L, pageEnd.audioTimeMs)
		assertEquals(0, pageEnd.trailingWordCount)
		val early = onPage.coordinator.coordinatePageEnd(
			controller = onPage.controllerStep.controller,
			playback = playback.copy(positionMs = 1_290)
		)
		assertNull(early.controllerStep.readaloudPlaybackCommand)
		val reached = onPage.coordinator.coordinatePageEnd(
			controller = onPage.controllerStep.controller,
			playback = playback.copy(positionMs = 1_300)
		)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, reached.controllerStep.readaloudPlaybackCommand)
		assertEquals(
			ReaderWhispersyncTransportPhase.BoundaryPaused,
			reached.controllerStep.controller.state.whispersync.transportPhase
		)
		// Once paused the PageEnd transition is inert: no second Pause.
		val again = reached.coordinator.coordinatePageEnd(
			controller = reached.controllerStep.controller,
			playback = playback.copy(positionMs = 1_310)
		)
		assertNull(again.controllerStep.readaloudPlaybackCommand)

		val pastPage = current.coordinator.coordinateBoundary(
			controller = current.controllerStep.controller,
			playback = playback.copy(positionMs = 1_300),
			boundary = boundaries.single { it.audioStartMs == 1_300L }
		)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, pastPage.controllerStep.readaloudPlaybackCommand)
		assertEquals(listOf(ReaderEngineCommand.ClearMediaOverlay), pastPage.controllerStep.engineCommands)
		assertEquals(
			ReaderWhispersyncTransportPhase.BoundaryPaused,
			pastPage.controllerStep.controller.state.whispersync.transportPhase
		)
	}

	@Test
	fun trailingUnmatchedVisibleWordPlaysBeforePageEnd() {
		// The second word is on the page but the aligner did not match it: its narration still
		// belongs to the page, so the page ends after it, not at the last matched word's end.
		val unmatchedChapter = chapter.copy(
			tracks = chapter.tracks.map { track ->
				track.copy(words = track.words.map { word -> if (word.ebookStart == 103) word.copy(status = 0) else word })
			}
		)
		val (ready, base) = readyCoordinatorAndController()
		val coordinator = ready.copy(
			chapters = mapOf(
				unmatchedChapter.chapterKey to ReaderVerifiedWordSyncChapter(
					chapter = unmatchedChapter,
					descriptor = descriptor()
				)
			)
		)
		val controller = base.withVisibleBytes(rawByteStart = 100, rawByteEnd = 105)

		val pageEnd = assertNotNull(coordinator.visiblePageEnd(controller, playbackIdentity(positionMs = 1_050)))

		assertEquals("track-end", pageEnd.source)
		assertEquals(1_520L, pageEnd.audioTimeMs)
		assertEquals(1, pageEnd.trailingWordCount)
		assertEquals("0", pageEnd.trailingStatuses)
		val beforeTrailingWord = coordinator.coordinatePageEnd(controller, playbackIdentity(positionMs = 1_250))
		assertNull(beforeTrailingWord.controllerStep.readaloudPlaybackCommand)
		val ended = coordinator.coordinatePageEnd(controller, playbackIdentity(positionMs = 1_520))
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, ended.controllerStep.readaloudPlaybackCommand)
	}

	@Test
	fun chapterEndOnPageUsesTrackOrCueEnd() {
		val (ready, base) = readyCoordinatorAndController()
		val lastPage = base.withVisibleBytes(rawByteStart = 100, rawByteEnd = 109)
		val playback = playbackIdentity(positionMs = 1_050)

		// No word follows the page on this track: the page ends with the track's last word.
		val trackEnd = assertNotNull(ready.visiblePageEnd(lastPage, playback))
		assertEquals("track-end", trackEnd.source)
		assertEquals(1_520L, trackEnd.audioTimeMs)

		// A canonical cue over the page that outlasts the last word extends the page end.
		val withCue = lastPage.copy(
			state = lastPage.state.copy(
				whispersync = lastPage.state.whispersync.copy(
					sidecar = WhispersyncSidecar(
						timeline = WhispersyncTimeline(
						segments = listOf(
							cueSegment().copy(
								audioTrackIndex = 0,
								endMs = 1_800,
								textStart = 100,
								textEnd = 109,
								rawProvenanceId = descriptor().id
							)
						)
					)
					)
				)
			)
		)
		val cueEnd = assertNotNull(ready.visiblePageEnd(withCue, playback))
		assertEquals("cue-end", cueEnd.source)
		assertEquals(1_800L, cueEnd.audioTimeMs)

		// Another track's playback never inherits this page's end.
		assertNull(ready.visiblePageEnd(lastPage, playback.copy(audioResourceId = "audio-b", audioTrackIndex = 1)))
	}

	private fun ReaderController.withVisibleBytes(rawByteStart: Int, rawByteEnd: Int) = copy(
		state = state.copy(
			whispersync = state.whispersync.copy(
				transportPhase = ReaderWhispersyncTransportPhase.Playing,
				visibleTextRange = ReaderWhispersyncVisibleTextRange(
					textHref = "Text/chapter.xhtml",
					visibleStart = 10,
					visibleEnd = 20,
					rawProvenanceId = descriptor().id,
					rawSpineIndex = 2,
					rawByteStart = rawByteStart,
					rawByteEnd = rawByteEnd
				)
			)
		)
	)

	@Test
	fun failedIndexCannotSuppressCueLevelProgressFallback() {
		val (ready, controller) = readyCoordinatorAndController()
		val current = ready.coordinate(
			controllerStep = ReaderControllerStep(
				controller,
				engineCommands = listOf(cueCommand())
			),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator

		val failed = current.onIndexFailed(
			generation = current.generation,
			controller = controller
		)

		assertNull(failed.coordinator.reference)
		assertFalse(
			failed.coordinator.hasExactBoundaryPresentation(
				controller,
				playbackIdentity(positionMs = 1_050)
			)
		)
	}

	@Test
	fun scheduledBoundaryPublishesExactWordWithoutAnotherCuePulse() {
		val ready = readyCoordinatorAndController()
		val initial = ready.first.coordinate(
			controllerStep = ReaderControllerStep(
				ready.second,
				engineCommands = listOf(cueCommand())
			),
			playback = playbackIdentity(positionMs = 1_050)
		)
		val playback = playbackIdentity(positionMs = 1_350)
		val boundary = initial.coordinator.boundariesForPlayback(playback)
			.single { it.audioStartMs == 1_300L }

		val scheduled = initial.coordinator.coordinateBoundary(
			controller = ready.second,
			playback = playback,
			boundary = boundary
		)

		val command = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(
			scheduled.controllerStep.engineCommands.single()
		)
		assertEquals(ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8, command.fragment.coordinateMode)
		assertEquals(103, command.fragment.rawByteStart)
		assertEquals(104, command.fragment.rawByteEnd)
		assertEquals(41, command.fragment.overlayRequestId)
	}

	@Test
	fun persistentPlayedTextKeepsThePresentationThroughTheSilenceBetweenWords() {
		val ready = readyCoordinatorAndController()
		val active = ready.first.coordinate(
			controllerStep = ReaderControllerStep(
				ready.second,
				engineCommands = listOf(cueCommand())
			),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator

		// #38: the default Persistent played text keeps the page highlight through silence.
		val cleared = active.coordinateClear(
			controller = ready.second,
			playback = playbackIdentity(positionMs = 1_200)
		)

		assertTrue(cleared.controllerStep.engineCommands.isEmpty())
		assertEquals(ready.second, cleared.controllerStep.controller)
		val nextBoundary = cleared.coordinator.boundariesForPlayback(
			playbackIdentity(positionMs = 1_350)
		).single { it.audioStartMs == 1_300L }
		val next = cleared.coordinator.coordinateBoundary(
			controller = ready.second,
			playback = playbackIdentity(positionMs = 1_350),
			boundary = nextBoundary
		)
		assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(next.controllerStep.engineCommands.single())
	}

	@Test
	fun wordEndClearDoesNotRetireTheCueSessionNeededByTheNextWord() {
		val ready = readyCoordinatorAndController().let { it.first to it.second.currentCueHighlight() }
		val active = ready.first.coordinate(
			controllerStep = ReaderControllerStep(
				ready.second,
				engineCommands = listOf(cueCommand())
			),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator
		val cleared = active.coordinateClear(
			controller = ready.second,
			playback = playbackIdentity(positionMs = 1_200)
		)
		val nextBoundary = cleared.coordinator.boundariesForPlayback(
			playbackIdentity(positionMs = 1_350)
		).single { it.audioStartMs == 1_300L }

		val next = cleared.coordinator.coordinateBoundary(
			controller = ready.second,
			playback = playbackIdentity(positionMs = 1_350),
			boundary = nextBoundary
		)

		assertEquals(
			ReaderEngineCommand.ClearMediaOverlayPresentation(
				overlayRequestId = 41L,
				clearedThroughBoundarySequence = 0L
			),
			cleared.controllerStep.engineCommands.single()
		)
		assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(next.controllerStep.engineCommands.single())
	}

	@Test
	fun endedExactBoundaryCannotRestoreFromALateActivation() {
		val ready = readyCoordinatorAndController().let { it.first to it.second.currentCueHighlight() }
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				whispersync = ready.second.state.whispersync.copy(
					sync = ReaderOverlaySyncState(
						activeCueKey = "cue-1",
						activeOverlayRequestId = 41L,
						engineCommand = cueCommand(),
						engineCommandKey = 41L
					)
				)
			)
		)
		val remembered = ready.first.coordinate(
			controllerStep = ReaderControllerStep(
				controller,
				engineCommands = listOf(cueCommand())
			),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator
		val boundary = remembered.boundariesForPlayback(
			playbackIdentity(positionMs = 1_050)
		).single { it.audioStartMs == 1_000L }
		val exact = remembered.coordinateBoundary(
			controller = controller,
			playback = playbackIdentity(positionMs = 1_050),
			boundary = boundary
		)
		val fragment = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(
			exact.controllerStep.engineCommands.single()
		).fragment
		val cleared = exact.coordinator.coordinateClear(
			controller = controller,
			playback = playbackIdentity(positionMs = 1_200)
		)

		val late = cleared.coordinator.onEngineEvent(
			controller = cleared.controllerStep.controller,
			event = ReaderEngineEvent.MediaOverlayActive(fragment)
		)

		assertNull(late.controllerStep.controller.state.activeMediaOverlay)
		assertNull(late.controllerStep.controller.state.activeMediaOverlayAnchorReceipt)
	}

	@Test
	fun unmappedReaderEventNeverFallsBackToPlaybackWord() {
		val ready = readyCoordinatorAndController()
		val cue = cueCommand()
		val remembered = ready.first.coordinate(
			controllerStep = ReaderControllerStep(ready.second),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator

		val readerEvent = remembered.coordinateReaderEvent(
			controllerStep = ReaderControllerStep(ready.second, engineCommands = listOf(cue)),
			rawPoint = null
		)

		assertEquals(listOf(cue), readerEvent.controllerStep.engineCommands)
	}

	@Test
	fun readerPreparedStartUsesPreparedSameXhtmlCueInsteadOfStalePlayback() {
		val ready = readyCoordinatorAndController()
		val remembered = ready.first.coordinate(
			controllerStep = ReaderControllerStep(ready.second),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator
		val destinationCue = cueCommand().copy(
			fragment = cueCommand().fragment.copy(
				overlayRequestId = 42L,
				fragmentId = "cue-b",
				clipBeginSeconds = 1.3,
				textStart = 20,
				textEnd = 30
			)
		)
		val destinationSegment = cueSegment().copy(
			id = "cue-b",
			audioResourceId = "audio-a",
			audioTrackIndex = 0,
			startMs = 1_300L,
			endMs = 1_600L,
			textStart = 20,
			textEnd = 30
		)
		val destinationTarget = WhispersyncAudioSeekTarget(
			audioResource = "audio-a",
			positionMs = 1_300L,
			segment = destinationSegment,
			audioTrackIndex = 0
		)
		val commitIdentity = ReaderDestinationCommitIdentity("session-a", 2L)
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				destinationCommitIdentity = commitIdentity,
				whispersync = ready.second.state.whispersync.copy(
					preparedVisibleTarget = ReaderWhispersyncPreparedVisibleTarget(
						destinationCommitIdentity = commitIdentity,
						firstVisibleCue = ReaderOverlayCue("cue-b", destinationCue.fragment),
						audioSeekTarget = destinationTarget,
						preparationGeneration = 2L
					),
					pendingAudioSeek = ReaderWhispersyncPendingAudioSeek(
						overlayRequestId = 42L,
						target = destinationTarget
					),
					playbackStartPending = true
				)
			)
		)

		val exact = remembered.coordinate(
			controllerStep = ReaderControllerStep(
				controller = controller,
				engineCommands = listOf(destinationCue)
			)
		)

		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			exact.controllerStep.engineCommands.single()
		).fragment
		assertEquals(103, fragment.rawByteStart)
		assertEquals(104, fragment.rawByteEnd)
		assertEquals(1_300L, exact.controllerStep.controller.state.whispersync.pendingAudioSeek?.target?.positionMs)
	}

	@Test
	fun readerPreparedStartBeginsAtFirstVisibleWordWhenSegmentStartsOnPreviousPage() {
		val ready = readyCoordinatorAndController()
		val commitIdentity = ReaderDestinationCommitIdentity("session-a", 3L)
		// The prepared segment starts at word 0 (raw bytes 0..2), which belongs to the previous page.
		val segmentTarget = WhispersyncAudioSeekTarget(
			audioResource = "audio-a",
			positionMs = 1_000L,
			segment = cueSegment().copy(audioResourceId = "audio-a", audioTrackIndex = 0),
			audioTrackIndex = 0
		)
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				destinationCommitIdentity = commitIdentity,
				whispersync = ready.second.state.whispersync.copy(
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = "Text/chapter.xhtml",
						visibleStart = 20,
						visibleEnd = 30,
						rawProvenanceId = descriptor().id,
						rawSpineIndex = 2,
						rawByteStart = 103,
						rawByteEnd = 109,
						destinationCommitIdentity = commitIdentity
					),
					pendingAudioSeek = ReaderWhispersyncPendingAudioSeek(
						overlayRequestId = 41L,
						target = segmentTarget
					),
					playbackStartPending = true
				)
			)
		)

		val exact = ready.first.coordinate(
			ReaderControllerStep(controller = controller, engineCommands = listOf(cueCommand()))
		)

		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			exact.controllerStep.engineCommands.single()
		).fragment
		assertEquals(103, fragment.rawByteStart)
		assertEquals(104, fragment.rawByteEnd)
		assertEquals(1_300L, exact.controllerStep.controller.state.whispersync.pendingAudioSeek?.target?.positionMs)
	}

	@Test
	fun visibleRangeResumeStartsAtFirstVisibleWordWhenPageStartsBetweenWords() {
		val ready = readyCoordinatorAndController()
		val commitIdentity = ReaderDestinationCommitIdentity("session-a", 4L)
		val segmentTarget = WhispersyncAudioSeekTarget(
			audioResource = "audio-a",
			positionMs = 1_000L,
			segment = cueSegment().copy(audioResourceId = "audio-a", audioTrackIndex = 0),
			audioTrackIndex = 0
		)
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				destinationCommitIdentity = commitIdentity,
				whispersync = ready.second.state.whispersync.copy(
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = "Text/chapter.xhtml",
						visibleStart = 20,
						visibleEnd = 30,
						rawProvenanceId = descriptor().id,
						rawSpineIndex = 2,
						rawByteStart = 102,
						rawByteEnd = 109,
						destinationCommitIdentity = commitIdentity
					),
					pendingAudioSeek = ReaderWhispersyncPendingAudioSeek(
						overlayRequestId = 41L,
						target = segmentTarget
					),
					playbackStartPending = true
				)
			)
		)

		// The page turn's visible range starts on the whitespace after word 0 (raw 100..102).
		val exact = ready.first.coordinateVisibleRangeEvent(
			ReaderControllerStep(controller = controller, engineCommands = listOf(cueCommand())),
			rawPoint = ReaderWordSyncRawPoint(descriptor().id, byteOffset = 102)
		)

		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
			exact.controllerStep.engineCommands.single()
		).fragment
		assertEquals(103, fragment.rawByteStart)
		assertEquals(104, fragment.rawByteEnd)
		assertEquals(1_300L, exact.controllerStep.controller.state.whispersync.pendingAudioSeek?.target?.positionMs)
	}

	@Test
	fun exactWordSeekCarriesVerifiedTrackIdentity() {
		val exactTrackIndex = 3
		val exactChapter = chapter.copy(
			tracks = chapter.tracks.map { track ->
				track.copy(
					audioTrackIndex = exactTrackIndex,
					words = track.words.map { word ->
						word.copy(audioTrackIndex = exactTrackIndex)
					}
				)
			}
		)
		val ready = readyCoordinatorAndController().let { (coordinator, controller) ->
			coordinator.copy(
				chapters = mapOf(
					exactChapter.chapterKey to ReaderVerifiedWordSyncChapter(
						chapter = exactChapter,
						descriptor = descriptor()
					)
				)
			) to controller
		}
		val pending = ReaderWhispersyncPendingAudioSeek(
			overlayRequestId = 41,
			target = WhispersyncAudioSeekTarget(
				audioResource = "audio-a",
				positionMs = 900,
				segment = cueSegment().copy(audioTrackIndex = 0)
			)
		)
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				whispersync = ready.second.state.whispersync.copy(pendingAudioSeek = pending)
			)
		)

		val raw = ready.first.coordinateReaderEvent(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(cueCommand())),
			rawPoint = ReaderWordSyncRawPoint(descriptor().id, byteOffset = 103)
		)

		assertEquals(exactTrackIndex, raw.controllerStep.controller.state.whispersync.pendingAudioSeek?.target?.audioTrackIndex)
	}

	@Test
	fun canonicalSidecarCueDemandsTheWordSyncIndex() {
		// #24: canonical-coordinate sidecars (Alcatraz) emit WordSync-coordinate cues.
		val cue = canonicalCueCommand()
		val configured = ReaderWordSyncPlaybackCoordinator().configure(reference)
		val first = configured.coordinate(
			controllerStep = ReaderControllerStep(ReaderController(), engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050)
		)

		assertIs<ReaderWordSyncEffect.LoadIndex>(first.effects.single())
		assertEquals(listOf(cue), first.controllerStep.engineCommands)
	}

	@Test
	fun canonicalCueIsRefinedToTheWordAndIsTheRawFallback() {
		val ready = readyCoordinatorAndController()
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				chrome = ready.second.state.chrome.copy(
					readaloudPlayback = ReaderReadaloudPlaybackUiState(isPlaying = true)
				)
			)
		)
		val raw = ready.first.coordinate(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(canonicalCueCommand())),
			playback = playbackIdentity(positionMs = 1_050)
		)
		val word = raw.controllerStep.engineCommands.single().overlayFragmentOrNull()
		assertEquals(ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8, word?.coordinateMode)
		assertEquals(41, word?.overlayRequestId)
		assertTrue(word != canonicalCueCommand().fragment)

		val fallback = raw.coordinator.onEngineEvent(
			controller = raw.controllerStep.controller,
			event = ReaderEngineEvent.MediaOverlayInactive(
				overlayRequestId = 41,
				coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
				reason = "paint-rejected"
			)
		)
		val fallbackCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(fallback.controllerStep.engineCommands.single())
		assertEquals(canonicalCueCommand().fragment, fallbackCommand.fragment)
		assertNull(fallback.controllerStep.readaloudPlaybackCommand)
	}

	@Test
	fun refusedWordProgressWithoutFallbackRepresentsTheCueWithoutPausing() {
		val ready = readyCoordinatorAndController()
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				chrome = ready.second.state.chrome.copy(
					readaloudPlayback = ReaderReadaloudPlaybackUiState(isPlaying = true)
				)
			)
		)
		val remembered = ready.first.coordinate(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(canonicalCueCommand())),
			playback = playbackIdentity(positionMs = 1_050)
		)
		val inactive = { coordinator: ReaderWordSyncPlaybackCoordinator, reason: String ->
			coordinator.onEngineEvent(
				controller = remembered.controllerStep.controller,
				event = ReaderEngineEvent.MediaOverlayInactive(
					overlayRequestId = 41,
					coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
					reason = reason
				)
			)
		}
		// The single-use fallback is spent by the first refusal; later word refusals still re-present.
		val spent = inactive(remembered.coordinator, "progress-paint-rejected").coordinator

		for (reason in listOf("stale-progress-request", "progress-outside-visible-page", "progress-paint-rejected")) {
			val decision = inactive(spent, reason)
			val command = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(
				decision.controllerStep.engineCommands.single(),
				reason
			)
			assertEquals(canonicalCueCommand().fragment, command.fragment, reason)
			assertNull(decision.controllerStep.readaloudPlaybackCommand, reason)
		}

		// A refused presentation is not a word progress: it still reaches the controller.
		val refused = inactive(spent, "outside-visible-page")
		assertTrue(refused.controllerStep.engineCommands.none { it is ReaderEngineCommand.ApplyMediaOverlay })
	}

	@Test
	fun cuePresentationIsNotFencedByClearedWordSequences() {
		val ready = readyCoordinatorAndController()
		val cue = ReaderEngineCommand.ApplyMediaOverlay(
			canonicalCueCommand().fragment.copy(wordBoundarySequence = 0)
		)
		val controller = ready.second
		val remembered = ready.first.coordinate(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050)
		).coordinator
		val boundary = remembered.boundariesForPlayback(
			playbackIdentity(positionMs = 1_050)
		).single { it.audioStartMs == 1_000L }
		val exact = remembered.coordinateBoundary(
			controller = controller,
			playback = playbackIdentity(positionMs = 1_050),
			boundary = boundary
		)
		val word = assertIs<ReaderEngineCommand.UpdateMediaOverlayProgress>(
			exact.controllerStep.engineCommands.single()
		).fragment
		assertTrue((word.wordBoundarySequence ?: -1) >= 0)
		val cleared = exact.coordinator.coordinateClear(
			controller = controller,
			playback = playbackIdentity(positionMs = 1_200)
		)

		val awaiting = cleared.controllerStep.controller.let {
			it.copy(
				state = it.state.copy(
					whispersync = it.state.whispersync.copy(
						sync = ReaderOverlaySyncState(activeOverlayRequestId = 41)
					)
				)
			)
		}

		// The late word stays fenced...
		val lateWord = cleared.coordinator.onEngineEvent(
			controller = awaiting,
			event = ReaderEngineEvent.MediaOverlayActive(word)
		)
		assertNull(lateWord.controllerStep.controller.state.whispersync.sync.confirmedOverlayRequestId)
		// ...but the cue presentation (ordinal 0) is not a cleared word and reaches the controller.
		val presented = cleared.coordinator.onEngineEvent(
			controller = awaiting,
			event = ReaderEngineEvent.MediaOverlayActive(cue.fragment)
		)
		assertEquals(41L, presented.controllerStep.controller.state.whispersync.sync.confirmedOverlayRequestId)
	}

	@Test
	fun rawRejectionRetriesCueWithoutPausingPlayback() {
		val ready = readyCoordinatorAndController()
		val pending = ReaderWhispersyncPendingAudioSeek(
			overlayRequestId = 41,
			target = WhispersyncAudioSeekTarget(
				audioResource = "audio-a",
				positionMs = 1_000,
				segment = cueSegment()
			)
		)
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				chrome = ready.second.state.chrome.copy(
					readaloudPlayback = ReaderReadaloudPlaybackUiState(isPlaying = true)
				),
				whispersync = ready.second.state.whispersync.copy(pendingAudioSeek = pending)
			)
		)
		val raw = ready.first.coordinate(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(cueCommand())),
			playback = playbackIdentity(positionMs = 1_050)
		)

		val fallback = raw.coordinator.onEngineEvent(
			controller = raw.controllerStep.controller,
			event = ReaderEngineEvent.MediaOverlayInactive(
				overlayRequestId = 41,
				coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
				reason = "paint-rejected"
			)
		)

		val fallbackCommand = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(fallback.controllerStep.engineCommands.single())
		assertEquals(ReaderOverlayCoordinateMode.CueV1DomUtf16, fallbackCommand.fragment.coordinateMode)
		assertEquals(41, fallbackCommand.fragment.overlayRequestId)
		assertNull(fallback.controllerStep.readaloudPlaybackCommand)
		assertEquals(
			pending.copy(target = pending.target.copy(audioTrackIndex = 0)),
			fallback.controllerStep.controller.state.whispersync.pendingAudioSeek
		)
	}

	@Test
	fun rawSeekUsesExactWordAndWaitsForOverlayConfirmation() {
		val ready = readyCoordinatorAndController()
		val cue = cueCommand()
		val runtimeAudioResource = "runtime-audio-alias"
		val pendingCueSeek = ReaderWhispersyncPendingAudioSeek(
			overlayRequestId = 41,
			target = WhispersyncAudioSeekTarget(
				audioResource = runtimeAudioResource,
				positionMs = 900,
				segment = cueSegment(),
				audioTrackIndex = 0
			)
		)
		val controller = ready.second.copy(
			state = ready.second.state.copy(
				whispersync = ready.second.state.whispersync.copy(
					sync = ReaderOverlaySyncState(
						activeCueKey = "cue-1",
						activeOverlayRequestId = 41,
						engineCommand = cue,
						engineCommandKey = 41
					),
					pendingAudioSeek = pendingCueSeek
				)
			)
		)
		val raw = ready.first.coordinateReaderEvent(
			controllerStep = ReaderControllerStep(controller, engineCommands = listOf(cue)),
			rawPoint = ReaderWordSyncRawPoint(
				provenanceId = descriptor().id,
				byteOffset = 103
			)
		)

		val pendingRawSeek = raw.controllerStep.controller.state.whispersync.pendingAudioSeek
		assertEquals(1_300, pendingRawSeek?.target?.positionMs)
		assertEquals(runtimeAudioResource, pendingRawSeek?.target?.audioResource)
		assertNull(raw.controllerStep.whispersyncAudioSeekTarget)

		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(raw.controllerStep.engineCommands.single()).fragment
		assertEquals(runtimeAudioResource, fragment.resourceHref)
		val active = raw.coordinator.onEngineEvent(
			controller = raw.controllerStep.controller,
			event = ReaderEngineEvent.MediaOverlayActive(fragment)
		)
		assertEquals(1_300, active.controllerStep.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(
			runtimeAudioResource,
			active.controllerStep.whispersyncAudioSeekTarget?.audioResource
		)
		assertNull(active.controllerStep.controller.state.whispersync.pendingAudioSeek)
	}

	private fun ReaderController.currentCueHighlight() = copy(
		state = state.copy(
			chrome = state.chrome.copy(
				settings = state.chrome.settings.copy(
					whispersyncHighlightLoading = ReaderWhispersyncHighlightLoading.CurrentCue.value
				)
			)
		)
	)

	private fun readyCoordinatorAndController(): Pair<ReaderWordSyncPlaybackCoordinator, ReaderController> {
		val baseController = ReaderController().installRawTextProvenance(descriptor()).controller
		val readyController = baseController.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor().id,
				status = RawTextProvenanceStatus.Ready
			)
		).controller
		val coordinator = ReaderWordSyncPlaybackCoordinator(
			reference = reference,
			generation = 1,
			index = index,
			provenance = provenance,
			chapters = mapOf(
				chapter.chapterKey to ReaderVerifiedWordSyncChapter(
					chapter = chapter,
					descriptor = descriptor()
				)
			)
		)
		return coordinator to readyController
	}

	private fun descriptor() = ReaderRawTextProvenanceDescriptor(
		id = "wordsync-v1-spine-2",
		href = chapter.ebookHref,
		spineIndex = chapter.spineIndex,
		sourceHash = provenance.chapters.single().sourceHash,
		extractedTextHash = provenance.chapters.single().extractedTextHash,
		byteLength = provenance.chapters.single().extractedByteLength,
		tokenCount = provenance.chapters.single().tokenCount
	)

	private fun cueCommand() = ReaderEngineCommand.ApplyMediaOverlay(
		ReaderOverlayFragment(
			resourceHref = "Audio/a.mp3",
			overlayRequestId = 41,
			fragmentId = "cue-1",
			textHref = "Text/chapter.xhtml",
			clipBeginSeconds = 0.9,
			clipEndSeconds = 1.6,
			textStart = 10,
			textEnd = 30,
			label = "Chapter"
		)
	)

	private fun canonicalCueCommand() = ReaderEngineCommand.ApplyMediaOverlay(
		ReaderOverlayFragment(
			resourceHref = "Audio/a.mp3",
			coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
			overlayRequestId = 41,
			wordBoundarySequence = 7,
			textHref = "Text/chapter.xhtml",
			clipBeginSeconds = 0.9,
			clipEndSeconds = 1.6,
			label = "Chapter",
			rawProvenanceId = descriptor().id,
			rawSpineIndex = descriptor().spineIndex,
			rawByteStart = 0,
			rawByteEnd = 12
		)
	)

	private fun playbackIdentity(positionMs: Long) = ReaderWordSyncPlaybackIdentity(
		audioResourceId = "audio-a",
		audioTrackIndex = 0,
		positionMs = positionMs,
		playbackSpeed = 1f
	)

	private fun cueSegment() = WhispersyncSegment(
		id = "cue-1",
		audioResource = "audio-a",
		startMs = 900,
		endMs = 1_600,
		textHref = "Text/chapter.xhtml",
		textStart = 10,
		textEnd = 30
	)
}
