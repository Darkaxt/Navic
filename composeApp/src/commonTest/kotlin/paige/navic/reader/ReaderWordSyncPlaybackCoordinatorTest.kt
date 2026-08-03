package paige.navic.reader

import paige.navic.domain.repositories.BinderyWordSyncDiscovery
import paige.navic.domain.repositories.BinderyWordSyncReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
				extractedByteLength = 9,
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
	fun substitutesOneRawCommandOnlyForExactPlaybackIdentity() {
		val ready = readyCoordinatorAndController()
		val cue = cueCommand()

		val raw = ready.first.coordinate(
			controllerStep = ReaderControllerStep(ready.second, engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050)
		)
		val command = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(raw.controllerStep.engineCommands.single())
		val fragment = command.fragment
		assertEquals(ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8, fragment.coordinateMode)
		assertEquals(cue.fragment.overlayRequestId, fragment.overlayRequestId)
		assertEquals("audio-a", fragment.resourceHref)
		assertEquals("Text/chapter.xhtml", fragment.textHref)
		assertEquals(0, fragment.rawByteStart)
		assertEquals(2, fragment.rawByteEnd)
		assertEquals(2, fragment.rawSpineIndex)

		val wrongResource = raw.coordinator.coordinate(
			controllerStep = ReaderControllerStep(ready.second, engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050).copy(audioResourceId = "audio-other")
		)
		assertEquals(listOf(cue), wrongResource.controllerStep.engineCommands)

		val wrongTrack = raw.coordinator.coordinate(
			controllerStep = ReaderControllerStep(ready.second, engineCommands = listOf(cue)),
			playback = playbackIdentity(positionMs = 1_050).copy(audioTrackIndex = 1)
		)
		assertEquals(listOf(cue), wrongTrack.controllerStep.engineCommands)
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
			rawPoint = ReaderWordSyncRawPoint(descriptor().id, byteOffset = 3)
		)

		assertEquals(exactTrackIndex, raw.controllerStep.controller.state.whispersync.pendingAudioSeek?.target?.audioTrackIndex)
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
		val pendingCueSeek = ReaderWhispersyncPendingAudioSeek(
			overlayRequestId = 41,
			target = WhispersyncAudioSeekTarget(
				audioResource = "audio-a",
				positionMs = 900,
				segment = cueSegment()
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
				byteOffset = 3
			)
		)

		val pendingRawSeek = raw.controllerStep.controller.state.whispersync.pendingAudioSeek
		assertEquals(1_300, pendingRawSeek?.target?.positionMs)
		assertNull(raw.controllerStep.whispersyncAudioSeekTarget)

		val fragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(raw.controllerStep.engineCommands.single()).fragment
		val active = raw.coordinator.onEngineEvent(
			controller = raw.controllerStep.controller,
			event = ReaderEngineEvent.MediaOverlayActive(fragment)
		)
		assertEquals(1_300, active.controllerStep.whispersyncAudioSeekTarget?.positionMs)
		assertNull(active.controllerStep.controller.state.whispersync.pendingAudioSeek)
	}

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
