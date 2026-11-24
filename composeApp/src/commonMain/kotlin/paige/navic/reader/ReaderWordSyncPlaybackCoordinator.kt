package paige.navic.reader

import paige.navic.domain.repositories.BinderyWordSyncReference
import paige.navic.util.core.Logger

data class ReaderWordSyncPlaybackIdentity(
	val audioResourceId: String,
	val audioTrackIndex: Int,
	val positionMs: Long,
	val playbackSpeed: Float
) {
	init {
		require(audioResourceId.isNotBlank() && audioResourceId == audioResourceId.trim())
		require(audioTrackIndex >= 0)
		require(positionMs >= 0L)
	}
}

data class ReaderWordSyncRawPoint(
	val provenanceId: String,
	val byteOffset: Int
) {
	init {
		require(provenanceId.isNotBlank() && provenanceId == provenanceId.trim())
		require(byteOffset >= 0)
	}
}

/** When the visible page's narration ends on one audio track, and which rule decided it. */
internal data class ReaderWordSyncPageEnd(
	val audioTrackIndex: Int,
	val audioTimeMs: Long,
	val source: String,
	val trailingWordCount: Int,
	val trailingStatuses: String
)

sealed interface ReaderWordSyncEffect {
	val generation: Long

	data class LoadIndex(
		override val generation: Long,
		val reference: BinderyWordSyncReference
	) : ReaderWordSyncEffect

	data class LoadChapter(
		override val generation: Long,
		val identity: paige.navic.domain.repositories.BinderyWhispersyncIdentity,
		val summary: WordSyncChapterSummary
	) : ReaderWordSyncEffect
}

data class ReaderVerifiedWordSyncChapter(
	val chapter: WordSyncChapter,
	val descriptor: ReaderRawTextProvenanceDescriptor
)

data class ReaderWordSyncDecision(
	val coordinator: ReaderWordSyncPlaybackCoordinator,
	val controllerStep: ReaderControllerStep,
	val effects: List<ReaderWordSyncEffect> = emptyList()
)

internal enum class ReaderWordSyncDiagnosticIndexState(val logValue: String) {
	Missing("missing"),
	Pending("pending"),
	Ready("ready")
}

internal data class ReaderWordSyncBoundaryInputDiagnostic(
	val referencePresent: Boolean,
	val indexState: ReaderWordSyncDiagnosticIndexState,
	val loadedChapterCount: Int,
	val pendingChapterCount: Int,
	val failedChapterCount: Int,
	val resourceMatchingTrackCount: Int,
	val trackIndexMatchingTrackCount: Int,
	val matchingTrackCount: Int,
	val presentableWordCount: Int
)

data class ReaderWordSyncPlaybackCoordinator(
	val reference: BinderyWordSyncReference? = null,
	val generation: Long = 0L,
	val index: WordSyncIndex? = null,
	val provenance: WordSyncPublicationProvenance? = null,
	val chapters: Map<String, ReaderVerifiedWordSyncChapter> = emptyMap(),
	private val indexLoadPending: Boolean = false,
	private val pendingChapterKeys: Set<String> = emptySet(),
	private val failedChapterKeys: Set<String> = emptySet(),
	private val lastCueCommand: ReaderEngineCommand? = null,
	private val lastPlayback: ReaderWordSyncPlaybackIdentity? = null,
	private val fallbackByRequestId: Map<Long, ReaderEngineCommand.ApplyMediaOverlay> = emptyMap(),
	private val activeBoundaryRequestId: Long? = null,
	private val activeBoundarySequence: Long? = null,
	private val clearedThroughBoundaryByRequestId: Map<Long, Long> = emptyMap()
) {
	fun configure(nextReference: BinderyWordSyncReference?): ReaderWordSyncPlaybackCoordinator {
		if (nextReference == reference) return this
		return ReaderWordSyncPlaybackCoordinator(
			reference = nextReference,
			generation = generation + 1L
		)
	}

	fun coordinate(
		controllerStep: ReaderControllerStep,
		playback: ReaderWordSyncPlaybackIdentity? = null
	): ReaderWordSyncDecision = coordinate(
		controllerStep = controllerStep,
		playback = playback,
		rawPoint = null,
		readerEvent = false
	)

	fun coordinateReaderEvent(
		controllerStep: ReaderControllerStep,
		rawPoint: ReaderWordSyncRawPoint?
	): ReaderWordSyncDecision = coordinate(
		controllerStep = controllerStep,
		playback = null,
		rawPoint = rawPoint,
		readerEvent = true
	)

	internal fun coordinateVisibleRangeEvent(
		controllerStep: ReaderControllerStep,
		rawPoint: ReaderWordSyncRawPoint?
	): ReaderWordSyncDecision = coordinate(
		controllerStep = controllerStep,
		playback = null,
		rawPoint = rawPoint,
		readerEvent = true,
		visibleRangeEvent = true
	)

	private fun coordinate(
		controllerStep: ReaderControllerStep,
		playback: ReaderWordSyncPlaybackIdentity?,
		rawPoint: ReaderWordSyncRawPoint?,
		readerEvent: Boolean,
		visibleRangeEvent: Boolean = false
	): ReaderWordSyncDecision {
		// A visible-range event that resumes after a page turn is a reader-prepared start too.
		val preparedStartEvent = !readerEvent || visibleRangeEvent
		val cueCommand = controllerStep.engineCommands.singleOrNull().asCueOverlayCommand()
		val clearsOverlay = controllerStep.engineCommands.singleOrNull() == ReaderEngineCommand.ClearMediaOverlay
		val readerPreparedStart = controllerStep.controller.state.whispersync.playbackStartPending
		val preparedPlayback = controllerStep.readerPreparedWordSyncPlayback(
			playbackSpeed = playback?.playbackSpeed ?: lastPlayback?.playbackSpeed ?: 1f
		)
		val playbackForCue = playback ?: if (readerPreparedStart) preparedPlayback else lastPlayback
		val remembered = copy(
			lastCueCommand = when {
				clearsOverlay -> null
				cueCommand != null -> cueCommand
				else -> lastCueCommand
			},
			lastPlayback = playback ?: lastPlayback,
			fallbackByRequestId = if (clearsOverlay) emptyMap() else fallbackByRequestId,
			activeBoundaryRequestId = if (clearsOverlay) null else activeBoundaryRequestId,
			activeBoundarySequence = if (clearsOverlay) null else activeBoundarySequence,
			clearedThroughBoundaryByRequestId =
				if (clearsOverlay) emptyMap() else clearedThroughBoundaryByRequestId
		)
		val loadDecision = remembered.requestDataForDemand(
			controller = controllerStep.controller,
			cueCommand = cueCommand ?: remembered.lastCueCommand,
			playback = playbackForCue
		)
		val coordinatorWithDemand = loadDecision.coordinator
		if (cueCommand == null) {
			return ReaderWordSyncDecision(
				coordinator = coordinatorWithDemand,
				controllerStep = controllerStep,
				effects = loadDecision.effects
			)
		}
		val cueFragment = cueCommand.overlayFragmentOrNull() ?: return ReaderWordSyncDecision(
			coordinator = coordinatorWithDemand,
			controllerStep = controllerStep,
			effects = loadDecision.effects
		)
		// A reader-prepared Play starts at the first word of the visible page. The prepared
		// Whispersync segment may begin on the previous page, and its first word would be off-page.
		val preparedVisibleRange = controllerStep.controller.state
			.takeIf { readerPreparedStart && playback == null && preparedStartEvent }
			?.let { state ->
				state.whispersync.visibleTextRange
					?.takeIf { range -> range.destinationCommitIdentity == state.destinationCommitIdentity }
			}
		val candidate = preparedVisibleRange
			?.let { range ->
				coordinatorWithDemand.candidateForVisibleRange(
					range = range,
					audioTrackIndex = preparedPlayback?.audioTrackIndex
				)
			}
			?.let { visible -> coordinatorWithDemand.admittedCandidate(controllerStep.controller, cueFragment, visible) }
			?: coordinatorWithDemand.rawCandidate(
				controller = controllerStep.controller,
				cueFragment = cueFragment,
				playback = playbackForCue,
				rawPoint = rawPoint,
				readerEvent = readerEvent
			)
		val requestId = cueFragment.overlayRequestId
		if (readerPreparedStart && preparedStartEvent) {
			Logger.i(
				WhispersyncSyncLogTag,
				"WordSync prepared start visibleRaw=${preparedVisibleRange?.rawByteStart}..${preparedVisibleRange?.rawByteEnd} " +
					"word=${candidate?.word?.ebookStart}..${candidate?.word?.ebookEnd} " +
					"wordAudioMs=${candidate?.word?.audioStartMs} preparedMs=${preparedPlayback?.positionMs} " +
					"livePlayback=${playback != null}"
			)
		}
		if (candidate == null || requestId == null) {
			return ReaderWordSyncDecision(
				coordinator = coordinatorWithDemand.copy(fallbackByRequestId = emptyMap()),
				controllerStep = controllerStep,
				effects = loadDecision.effects
			)
		}

		val cueBoundary = playbackForCue
			?.let(coordinatorWithDemand::boundariesForPlayback)
			?.singleOrNull { boundary -> boundary.word === candidate.word }
		val boundarySequence = cueBoundary?.sequence
		val rawFragment = candidate.toOverlayFragment(
			paintByteEnd = cueBoundary?.paintByteEnd ?: candidate.word.ebookEnd,
			cueFragment = cueFragment,
			playback = playbackForCue,
			runtimeAudioResource = controllerStep.wordSyncRuntimeAudioResource(
				word = candidate.word,
				overlayRequestId = requestId,
				playback = playbackForCue
			),
			boundarySequence = boundarySequence
		)
		val rawCommand = when (cueCommand) {
			is ReaderEngineCommand.ApplyMediaOverlay -> ReaderEngineCommand.ApplyMediaOverlay(rawFragment)
			is ReaderEngineCommand.UpdateMediaOverlayProgress ->
				ReaderEngineCommand.UpdateMediaOverlayProgress(rawFragment)
			else -> return ReaderWordSyncDecision(coordinatorWithDemand, controllerStep, loadDecision.effects)
		}
		val fallback = ReaderEngineCommand.ApplyMediaOverlay(cueFragment)
		val exactStep = controllerStep.withExactWordSyncSeek(candidate.word, rawCommand)
		return ReaderWordSyncDecision(
			coordinator = coordinatorWithDemand.copy(
				fallbackByRequestId = mapOf(requestId to fallback),
				activeBoundaryRequestId = requestId.takeIf { boundarySequence != null },
				activeBoundarySequence = boundarySequence
			),
			controllerStep = exactStep,
			effects = loadDecision.effects
		)
	}

	internal fun hasExactBoundaryPresentation(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity?
	): Boolean {
		if (reference == null || playback == null) return false
		val cueFragment = lastCueCommand.asCueOverlayCommand()
			?.overlayFragmentOrNull()
			?: return false
		// Word-exact owns the presentation from the latest started word until the chapter's narration
		// ends: inter-word gaps keep the last word mark instead of re-presenting the coarse cue.
		val position = playback.positionMs
		val latestStarted = verifiedTracksForPlayback(playback)
			.filter { verifiedTrack -> verifiedTrack.track.words.any { it.audioEndMs > position } }
			.flatMap { verifiedTrack ->
				verifiedTrack.track.words
					.filter { word -> word.status in 1..4 && word.audioStartMs <= position }
					.map { word -> ReaderWordSyncCandidate(verifiedTrack.verified, word) }
			}
			.maxByOrNull { candidate -> candidate.word.audioStartMs }
			?: return false
		return admittedCandidate(controller, cueFragment, latestStarted) != null
	}

	internal fun boundaryInputDiagnostic(
		playback: ReaderWordSyncPlaybackIdentity?
	): ReaderWordSyncBoundaryInputDiagnostic {
		var resourceMatchingTrackCount = 0
		var trackIndexMatchingTrackCount = 0
		var matchingTrackCount = 0
		var presentableWordCount = 0
		val authoritativeArtifactResourceId = playback?.let { activePlayback ->
			authoritativeArtifactResourceId(activePlayback.audioTrackIndex)
		}
		if (playback != null) {
			chapters.values.forEach { verified ->
				verified.chapter.tracks.forEach { track ->
					val resourceMatches = track.audioResourceId == playback.audioResourceId
					val trackIndexMatches = track.audioTrackIndex == playback.audioTrackIndex
					if (resourceMatches) resourceMatchingTrackCount += 1
					if (trackIndexMatches) trackIndexMatchingTrackCount += 1
					if (
						trackIndexMatches &&
						track.audioResourceId == authoritativeArtifactResourceId
					) {
						matchingTrackCount += 1
						presentableWordCount += track.words.count { word -> word.status in 1..4 }
					}
				}
			}
		}
		return ReaderWordSyncBoundaryInputDiagnostic(
			referencePresent = reference != null,
			indexState = when {
				index != null -> ReaderWordSyncDiagnosticIndexState.Ready
				indexLoadPending -> ReaderWordSyncDiagnosticIndexState.Pending
				else -> ReaderWordSyncDiagnosticIndexState.Missing
			},
			loadedChapterCount = chapters.size,
			pendingChapterCount = pendingChapterKeys.size,
			failedChapterCount = failedChapterKeys.size,
			resourceMatchingTrackCount = resourceMatchingTrackCount,
			trackIndexMatchingTrackCount = trackIndexMatchingTrackCount,
			matchingTrackCount = matchingTrackCount,
			presentableWordCount = presentableWordCount
		)
	}

	internal fun boundariesForPlayback(
		playback: ReaderWordSyncPlaybackIdentity
	): List<ReaderWordSyncBoundary> = verifiedTracksForPlayback(playback)
		.flatMap { verifiedTrack ->
			verifiedTrack.track.readerWordSyncBoundaries()
		}
		.sortedWith(
			compareBy<ReaderWordSyncBoundary> { it.audioStartMs }
				.thenBy { it.word.spineIndex }
				.thenBy { it.word.ebookStart }
		)
		.withReaderWordSyncFlowTiming()
		.mapIndexed { index, boundary -> boundary.copy(sequence = index.toLong()) }

	internal fun coordinateClear(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity
	): ReaderWordSyncDecision {
		val requestId = activeBoundaryRequestId
		val boundarySequence = activeBoundarySequence
		if (requestId == null || boundarySequence == null) {
			return ReaderWordSyncDecision(copy(lastPlayback = playback), ReaderControllerStep(controller))
		}
		val clearedThrough = maxOf(
			clearedThroughBoundaryByRequestId[requestId] ?: -1L,
			boundarySequence
		)
		val fenced = copy(
			lastPlayback = playback,
			activeBoundaryRequestId = null,
			activeBoundarySequence = null,
			clearedThroughBoundaryByRequestId =
				clearedThroughBoundaryByRequestId + (requestId to clearedThrough)
		)
		// A word end is not the page end: unmatched narration of the page's tail may follow it.
		// The page ends on the scheduler's PageEnd transition (visiblePageEnd / coordinatePageEnd).
		// Persistent played text keeps the page highlight through the silence between words and
		// cues (#38): only the late-word fence is recorded, the presentation stays.
		if (
			normalizedReaderWhispersyncHighlightLoading(controller.state.chrome.settings.whispersyncHighlightLoading) ==
			ReaderWhispersyncHighlightLoading.PersistentPlayedText
		) {
			return ReaderWordSyncDecision(fenced, ReaderControllerStep(controller))
		}
		return ReaderWordSyncDecision(
			coordinator = fenced,
			controllerStep = ReaderControllerStep(
				controller = controller.copy(
					state = controller.state.copy(
						activeMediaOverlay = null,
						activeMediaOverlayAnchorReceipt = null,
						activeMediaOverlayAnchorRefused = false,
						audioMetadataLabel = null
					)
				),
				engineCommands = listOf(
					ReaderEngineCommand.ClearMediaOverlayPresentation(
						overlayRequestId = requestId,
						clearedThroughBoundarySequence = clearedThrough
					)
				)
			)
		)
	}

	/**
	 * The audio time at which the visible page's narration ends. All track words count (any
	 * status: the aligner still places unmatched/review audio in the ebook). After the page's
	 * last matched word, the first word placed at or past the visible end ends the page in the
	 * silence before it (midway from the page's last word end to its start); with none, the chapter's narration ends here: the later of the track's last word end
	 * and the coarse sidecar cue over the visible bytes (covers text the aligner emitted no word for).
	 */
	internal fun visiblePageEnd(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity
	): ReaderWordSyncPageEnd? {
		val range = controller.state.whispersync.visibleTextRange ?: return null
		val provenanceId = range.rawProvenanceId ?: return null
		val visibleStart = range.rawByteStart ?: return null
		val visibleEnd = range.rawByteEnd?.takeIf { it > visibleStart } ?: return null
		val words = verifiedTracksForPlayback(playback)
			.singleOrNull { it.verified.descriptor.id == provenanceId }
			?.track?.words ?: return null
		val lastVisible = words.indexOfLast { word ->
			word.status in 1..4 && word.ebookStart >= visibleStart && word.ebookStart < visibleEnd
		}
		if (lastVisible < 0) return null
		val after = words.subList(lastVisible + 1, words.size)
		val beyond = after.indexOfFirst { it.ebookStart >= visibleEnd }
		val trailing = if (beyond >= 0) after.subList(0, beyond) else after
		val trailingStatuses = trailing.map { it.status }.distinct().sorted().joinToString(",")
		if (beyond >= 0) {
			// Pause in the silence before the next page's first word, not at its ASR start: starts
			// run late and the pause lands after the command, so "My name" of the next page was heard
			// (tablet, v1.6.19). The midpoint keeps the page's own last word whole.
			val nextStart = after[beyond].audioStartMs
			val onPageEnd = (listOf(words[lastVisible]) + trailing).maxOf { maxOf(it.audioEndMs, it.audioStartMs) }
			val pageEndMs = if (nextStart > onPageEnd) onPageEnd + (nextStart - onPageEnd) / 2 else nextStart
			return ReaderWordSyncPageEnd(
				playback.audioTrackIndex, pageEndMs, "next-word", trailing.size, trailingStatuses
			)
		}
		val lastWordEnd = words.maxOf { maxOf(it.audioEndMs, it.audioStartMs) }
		val cueEnd = controller.state.whispersync.timeline?.segments.orEmpty()
			.filter { segment ->
				segment.rawProvenanceId == provenanceId &&
					(segment.audioTrackIndex == null || segment.audioTrackIndex == playback.audioTrackIndex) &&
					(segment.textEnd ?: -1).let { end -> end > visibleStart && end <= visibleEnd }
			}
			.maxOfOrNull { it.endMs }
		return if (cueEnd != null && cueEnd > lastWordEnd) {
			ReaderWordSyncPageEnd(playback.audioTrackIndex, cueEnd, "cue-end", trailing.size, trailingStatuses)
		} else {
			ReaderWordSyncPageEnd(playback.audioTrackIndex, lastWordEnd, "track-end", trailing.size, trailingStatuses)
		}
	}

	/** The scheduler's PageEnd transition: pause once the visible page's narration has ended. */
	internal fun coordinatePageEnd(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity
	): ReaderWordSyncDecision {
		val remembered = copy(lastPlayback = playback)
		val pageEnd = visiblePageEnd(controller, playback)
		if (
			controller.state.whispersync.transportPhase != ReaderWhispersyncTransportPhase.Playing ||
			pageEnd == null ||
			pageEnd.audioTrackIndex != playback.audioTrackIndex ||
			playback.positionMs < pageEnd.audioTimeMs
		) {
			return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		}
		Logger.i(
			WhispersyncSyncLogTag,
			"WordSync page end state=reached source=${pageEnd.source} pageEndMs=${pageEnd.audioTimeMs} " +
				"atMs=${playback.positionMs} trailingWords=${pageEnd.trailingWordCount} " +
				"trailingStatuses=${pageEnd.trailingStatuses}"
		)
		val ended = controller.pauseAtWhispersyncVisiblePageEnd(
			playbackState = controller.state.chrome.readaloudPlayback.copy(positionMs = playback.positionMs),
			sync = controller.state.whispersync.sync,
			detail = null,
			reason = "visible-narration-ended"
		)
		return remembered.coordinate(ended, playback)
	}

	internal fun coordinateBoundary(
		controller: ReaderController,
		playback: ReaderWordSyncPlaybackIdentity,
		boundary: ReaderWordSyncBoundary
	): ReaderWordSyncDecision {
		val remembered = copy(lastPlayback = playback)
		val word = boundary.word
		if (
			word.audioTrackIndex != playback.audioTrackIndex ||
			boundary.audioStartMs > playback.positionMs
		) {
			return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		}
		val cueFragment = lastCueCommand.asCueOverlayCommand()
			?.overlayFragmentOrNull()
			?: return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		val verified = verifiedTracksForPlayback(playback).singleOrNull { verifiedTrack ->
			word in verifiedTrack.track.words
		}?.verified ?: return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		if (
			word.status !in 1..4 ||
			word.ebookHref != cueFragment.textHref ||
			controller.state.rawTextProvenanceById[verified.descriptor.id]?.status !=
				RawTextProvenanceStatus.Ready
		) {
			return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		}
		val requestId = cueFragment.overlayRequestId
			?: return ReaderWordSyncDecision(remembered, ReaderControllerStep(controller))
		// The first word past the visible page ends it: pause on that word's boundary, where the
		// resume after the turn starts again.
		val visibleRange = controller.state.whispersync.visibleTextRange
		val visibleEnd = visibleRange?.rawByteEnd
		if (
			controller.state.whispersync.transportPhase == ReaderWhispersyncTransportPhase.Playing &&
			visibleRange?.rawProvenanceId == verified.descriptor.id &&
			visibleEnd != null && word.ebookStart >= visibleEnd
		) {
			val ended = controller.pauseAtWhispersyncVisiblePageEnd(
				playbackState = controller.state.chrome.readaloudPlayback.copy(positionMs = playback.positionMs),
				sync = controller.state.whispersync.sync,
				detail = cueFragment.label,
				reason = "first-word-past-page"
			)
			return remembered.coordinate(ended, playback)
		}
		val candidate = ReaderWordSyncCandidate(verified, word)
		val rawFragment = candidate.toOverlayFragment(
			cueFragment = cueFragment,
			playback = playback,
			boundarySequence = boundary.sequence,
			audioStartMs = boundary.flowStartMs,
			audioEndMs = boundary.flowEndMs,
			paintByteEnd = boundary.paintByteEnd
		)
		return ReaderWordSyncDecision(
			coordinator = remembered.copy(
				fallbackByRequestId = mapOf(
					requestId to ReaderEngineCommand.ApplyMediaOverlay(cueFragment)
				),
				activeBoundaryRequestId = requestId,
				activeBoundarySequence = boundary.sequence
			),
			controllerStep = ReaderControllerStep(
				controller = controller,
				engineCommands = listOf(
					ReaderEngineCommand.UpdateMediaOverlayProgress(rawFragment)
				)
			)
		)
	}

	fun onIndexVerified(
		generation: Long,
		index: WordSyncIndex,
		provenance: WordSyncPublicationProvenance,
		controller: ReaderController
	): ReaderWordSyncDecision {
		val expectedReference = reference
		if (
			generation != this.generation ||
			expectedReference == null ||
			index.identity != expectedReference.identity ||
			provenance.coordinateBasis != index.coordinateBasis
		) {
			return ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		}
		val indexed = copy(
			index = index,
			provenance = provenance,
			indexLoadPending = false
		)
		val loadDecision = indexed.requestDataForDemand(
			controller = controller,
			cueCommand = lastCueCommand,
			playback = lastPlayback
		)
		return ReaderWordSyncDecision(
			coordinator = loadDecision.coordinator,
			controllerStep = ReaderControllerStep(controller),
			effects = loadDecision.effects
		)
	}

	fun onIndexFailed(generation: Long, controller: ReaderController): ReaderWordSyncDecision =
		if (generation != this.generation) {
			ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		} else {
			ReaderWordSyncDecision(
				copy(indexLoadPending = false, reference = null),
				ReaderControllerStep(controller)
			)
		}

	fun onChapterVerified(
		generation: Long,
		chapter: WordSyncChapter,
		controller: ReaderController
	): ReaderWordSyncDecision {
		val summary = index?.chapters?.singleOrNull { it.chapterKey == chapter.chapterKey }
		val chapterProvenance = provenance?.chapters?.singleOrNull {
			it.chapterKey == chapter.chapterKey &&
				it.ebookHref == chapter.ebookHref &&
				it.spineIndex == chapter.spineIndex
		}
		if (
			generation != this.generation ||
			reference?.identity != chapter.identity ||
			summary == null ||
			chapterProvenance == null
		) {
			return ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		}
		val descriptor = chapterProvenance.toReaderRawTextProvenanceDescriptor()
		Logger.i(
			WhispersyncSyncLogTag,
			"WordSync chapter verified spine=${chapter.spineIndex} ebook=${chapter.ebookStart}..${chapter.ebookEnd} " +
				"descriptorBytes=${descriptor.byteLength}"
		)
		val sidecarInstalledReady =
			controller.state.whispersync.sidecar?.referencedRawTextProvenanceDescriptors().orEmpty()
				.any { it == descriptor } &&
				controller.state.rawTextProvenanceById[descriptor.id]?.status == RawTextProvenanceStatus.Ready
		val installed = if (sidecarInstalledReady) {
			ReaderControllerStep(controller)
		} else {
			controller.installRawTextProvenance(descriptor)
		}
		return ReaderWordSyncDecision(
			coordinator = copy(
				chapters = chapters + (
					chapter.chapterKey to ReaderVerifiedWordSyncChapter(chapter, descriptor)
				),
				pendingChapterKeys = pendingChapterKeys - chapter.chapterKey,
				failedChapterKeys = failedChapterKeys - chapter.chapterKey
			),
			controllerStep = installed
		)
	}

	fun onChapterFailed(
		generation: Long,
		chapterKey: String,
		controller: ReaderController
	): ReaderWordSyncDecision =
		if (generation != this.generation || chapterKey !in pendingChapterKeys) {
			ReaderWordSyncDecision(this, ReaderControllerStep(controller))
		} else {
			ReaderWordSyncDecision(
				coordinator = copy(
					pendingChapterKeys = pendingChapterKeys - chapterKey,
					failedChapterKeys = failedChapterKeys + chapterKey
				),
				controllerStep = ReaderControllerStep(controller)
			)
		}

	fun onEngineEvent(
		controller: ReaderController,
		event: ReaderEngineEvent
	): ReaderWordSyncDecision {
		if (event is ReaderEngineEvent.MediaOverlayActive) {
			val requestId = event.fragment.overlayRequestId
			val boundarySequence = event.fragment.wordBoundarySequence
			val clearedThrough = requestId?.let(clearedThroughBoundaryByRequestId::get)
			// The cue presentation carries its cue ordinal, not a word boundary sequence (#24).
			val cue = lastCueCommand.asCueOverlayCommand()?.overlayFragmentOrNull()
			val isCuePresentation = cue != null && cue.overlayRequestId == requestId &&
				cue.wordBoundarySequence == boundarySequence &&
				cue.rawByteStart == event.fragment.rawByteStart &&
				cue.rawByteEnd == event.fragment.rawByteEnd
			if (
				!isCuePresentation &&
				requestId != null &&
				boundarySequence != null &&
				clearedThrough != null &&
				boundarySequence <= clearedThrough
			) {
				return ReaderWordSyncDecision(this, ReaderControllerStep(controller))
			}
		}
		if (
			event is ReaderEngineEvent.MediaOverlayInactive &&
			event.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8
		) {
			val requestId = event.overlayRequestId
			// A refused word progress never pauses playback: the current cue is presented again (#24).
			// Presentation-level refusals (outside-visible-page, anchor/paint rejected) still pause.
			val currentCue = lastCueCommand.asCueOverlayCommand()?.overlayFragmentOrNull()
				?.takeIf { requestId != null && it.overlayRequestId == requestId }
			val fallback = requestId?.let(fallbackByRequestId::get)
				?: currentCue?.takeIf { event.reason in WordSyncProgressRefusalReasons }
					?.let(ReaderEngineCommand::ApplyMediaOverlay)
			if (requestId != null && fallback != null) {
				return ReaderWordSyncDecision(
					coordinator = copy(fallbackByRequestId = fallbackByRequestId - requestId),
					controllerStep = ReaderControllerStep(
						controller = controller.copy(
							state = controller.state.copy(
								activeMediaOverlay = null,
								activeMediaOverlayAnchorReceipt = null,
								audioMetadataLabel = null
							)
						),
						engineCommands = listOf(fallback)
					)
				)
			}
		}

		val reduced = controller.onEngineEvent(event)
		val rawPoint = when (event) {
			is ReaderEngineEvent.TextPoint -> event.rawPointOrNull()
			is ReaderEngineEvent.VisibleTextRange -> event.rawPointOrNull()
			else -> null
		}
		return when (event) {
			is ReaderEngineEvent.TextPoint -> coordinateReaderEvent(reduced, rawPoint)
			is ReaderEngineEvent.VisibleTextRange -> coordinateVisibleRangeEvent(reduced, rawPoint)
			else -> coordinate(reduced)
		}
	}

	private fun requestDataForDemand(
		controller: ReaderController,
		cueCommand: ReaderEngineCommand?,
		playback: ReaderWordSyncPlaybackIdentity?
	): ReaderWordSyncLoadDecision {
		val activeReference = reference ?: return ReaderWordSyncLoadDecision(this)
		if (index == null) {
			if (indexLoadPending || cueCommand == null) return ReaderWordSyncLoadDecision(this)
			return ReaderWordSyncLoadDecision(
				coordinator = copy(indexLoadPending = true),
				effects = listOf(ReaderWordSyncEffect.LoadIndex(generation, activeReference))
			)
		}
		val summary = demandedSummary(cueCommand, playback) ?: return ReaderWordSyncLoadDecision(this)
		if (
			summary.chapterKey in chapters ||
			summary.chapterKey in pendingChapterKeys ||
			summary.chapterKey in failedChapterKeys
		) {
			return ReaderWordSyncLoadDecision(this)
		}
		return ReaderWordSyncLoadDecision(
			coordinator = copy(pendingChapterKeys = pendingChapterKeys + summary.chapterKey),
			effects = listOf(
				ReaderWordSyncEffect.LoadChapter(
					generation = generation,
					identity = activeReference.identity,
					summary = summary
				)
			)
		)
	}

	private fun demandedSummary(
		cueCommand: ReaderEngineCommand?,
		playback: ReaderWordSyncPlaybackIdentity?
	): WordSyncChapterSummary? {
		val chapters = index?.chapters.orEmpty()
		val audioSummary = playback?.let { demand ->
			val artifactResourceId = authoritativeArtifactResourceId(demand.audioTrackIndex)
			chapters.filter { summary ->
				summary.audioRanges.any { range ->
					range.audioResourceId == artifactResourceId &&
						range.audioTrackIndex == demand.audioTrackIndex &&
						demand.positionMs >= range.startMs && demand.positionMs < range.endMs
				}
			}.singleOrNull()
		}
		if (audioSummary != null) return audioSummary
		val textHref = cueCommand.overlayFragmentOrNull()?.textHref ?: return null
		return chapters.firstOrNull { it.ebookHref == textHref }
	}

	private fun rawCandidate(
		controller: ReaderController,
		cueFragment: ReaderOverlayFragment,
		playback: ReaderWordSyncPlaybackIdentity?,
		rawPoint: ReaderWordSyncRawPoint?,
		readerEvent: Boolean
	): ReaderWordSyncCandidate? {
		val candidate = (
			if (readerEvent) {
				rawPoint?.let(::candidateForRawPoint)
			} else {
				playback?.let(::candidateForPlayback)
			}
		) ?: return null
		return admittedCandidate(controller, cueFragment, candidate)
	}

	private fun admittedCandidate(
		controller: ReaderController,
		cueFragment: ReaderOverlayFragment,
		candidate: ReaderWordSyncCandidate
	): ReaderWordSyncCandidate? {
		if (candidate.word.status !in 1..4) return null
		if (candidate.word.ebookHref != cueFragment.textHref) return null
		val status = controller.state.rawTextProvenanceById[candidate.verified.descriptor.id]
		if (status?.status != RawTextProvenanceStatus.Ready) return null
		return candidate
	}

	private fun candidateForVisibleRange(
		range: ReaderWhispersyncVisibleTextRange,
		audioTrackIndex: Int?
	): ReaderWordSyncCandidate? {
		val provenanceId = range.rawProvenanceId ?: return null
		val byteStart = range.rawByteStart ?: return null
		val byteEnd = range.rawByteEnd?.takeIf { it > byteStart } ?: return null
		val verified = chapters.values.singleOrNull { it.descriptor.id == provenanceId } ?: return null
		// WordSync offsets and raw provenance bytes share the document coordinate basis (#52).
		val word = verified.chapter.firstMatchedWordInEbookRange(byteStart, byteEnd, audioTrackIndex)
			?: return null
		return ReaderWordSyncCandidate(verified, word)
	}

	private fun candidateForPlayback(playback: ReaderWordSyncPlaybackIdentity): ReaderWordSyncCandidate? =
		verifiedTracksForPlayback(playback).firstNotNullOfOrNull { verifiedTrack ->
			verifiedTrack.track.words.firstOrNull { word ->
				playback.positionMs >= word.audioStartMs && playback.positionMs < word.audioEndMs
			}?.let { word -> ReaderWordSyncCandidate(verifiedTrack.verified, word) }
		}

	private fun authoritativeArtifactResourceId(audioTrackIndex: Int): String? {
		val activeIndex = index ?: return null
		var artifactResourceId: String? = null
		for (summary in activeIndex.chapters) {
			for (range in summary.audioRanges) {
				if (range.audioTrackIndex != audioTrackIndex) continue
				val knownResourceId = artifactResourceId
				if (knownResourceId == null) {
					artifactResourceId = range.audioResourceId
				} else if (knownResourceId != range.audioResourceId) {
					return null
				}
			}
		}
		return artifactResourceId
	}

	private fun verifiedTracksForPlayback(
		playback: ReaderWordSyncPlaybackIdentity
	): List<ReaderVerifiedWordSyncTrack> {
		val artifactResourceId = authoritativeArtifactResourceId(playback.audioTrackIndex)
			?: return emptyList()
		return chapters.values.flatMap { verified ->
			verified.chapter.tracks.mapNotNull { track ->
				if (
					track.audioTrackIndex != playback.audioTrackIndex ||
					track.audioResourceId != artifactResourceId
				) {
					null
				} else {
					ReaderVerifiedWordSyncTrack(verified, track)
				}
			}
		}
	}

	private fun candidateForRawPoint(point: ReaderWordSyncRawPoint): ReaderWordSyncCandidate? {
		val verified = chapters.values.singleOrNull { it.descriptor.id == point.provenanceId } ?: return null
		val word = verified.chapter.wordAtEbookOffset(point.byteOffset) ?: return null
		return ReaderWordSyncCandidate(verified, word)
	}
}

private data class ReaderWordSyncLoadDecision(
	val coordinator: ReaderWordSyncPlaybackCoordinator,
	val effects: List<ReaderWordSyncEffect> = emptyList()
)

private data class ReaderVerifiedWordSyncTrack(
	val verified: ReaderVerifiedWordSyncChapter,
	val track: WordSyncTrack
)

private data class ReaderWordSyncCandidate(
	val verified: ReaderVerifiedWordSyncChapter,
	val word: WordSyncWord
) {
	fun toOverlayFragment(
		cueFragment: ReaderOverlayFragment,
		playback: ReaderWordSyncPlaybackIdentity?,
		runtimeAudioResource: String? = null,
		boundarySequence: Long? = null,
		// The smoothed flow span (ReaderWordSyncFlow): the WebView sweeps the word over it.
		audioStartMs: Long = word.audioStartMs,
		audioEndMs: Long = word.audioEndMs,
		paintByteEnd: Int = word.ebookEnd
	): ReaderOverlayFragment {
		val byteStart = word.ebookStart
		val byteEnd = maxOf(paintByteEnd, word.ebookEnd)
		val compatiblePlayback = playback?.takeIf {
			it.audioTrackIndex == word.audioTrackIndex
		}
		val progress = compatiblePlayback
			?.takeIf { audioEndMs > audioStartMs }
			?.let {
				((it.positionMs - audioStartMs).toDouble() /
					(audioEndMs - audioStartMs).toDouble()).coerceIn(0.0, 1.0)
			}
		return ReaderOverlayFragment(
			resourceHref = runtimeAudioResource ?: compatiblePlayback?.audioResourceId ?: word.audioResourceId,
			coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
			overlayRequestId = cueFragment.overlayRequestId,
			wordBoundarySequence = boundarySequence,
			textHref = word.ebookHref,
			clipBeginSeconds = audioStartMs / 1000.0,
			clipEndSeconds = audioEndMs / 1000.0,
			playbackSpeed = playback?.playbackSpeed ?: cueFragment.playbackSpeed,
			label = cueFragment.label,
			rawProvenanceId = verified.descriptor.id,
			rawSpineIndex = word.spineIndex,
			rawByteStart = byteStart,
			rawByteEnd = byteEnd,
			rawProgressFraction = progress
		)
	}
}

private fun ReaderControllerStep.wordSyncRuntimeAudioResource(
	word: WordSyncWord,
	overlayRequestId: Long,
	playback: ReaderWordSyncPlaybackIdentity?
): String? {
	val pendingTarget = controller.state.whispersync.pendingAudioSeek
		?.takeIf { pending -> pending.overlayRequestId == overlayRequestId }
		?.target
	val runtimeTarget = sequenceOf(pendingTarget, whispersyncAudioSeekTarget)
		.filterNotNull()
		.firstOrNull { target -> target.wordSyncTrackIndexOrNull() == word.audioTrackIndex }
	if (runtimeTarget != null) return runtimeTarget.audioResource
	return playback
		?.takeIf { activePlayback -> activePlayback.audioTrackIndex == word.audioTrackIndex }
		?.audioResourceId
}

private fun WhispersyncAudioSeekTarget.wordSyncTrackIndexOrNull(): Int? =
	audioTrackIndex ?: segment.audioTrackIndex

private fun ReaderControllerStep.readerPreparedWordSyncPlayback(
	playbackSpeed: Float
): ReaderWordSyncPlaybackIdentity? {
	val whispersync = controller.state.whispersync
	if (!whispersync.playbackStartPending) return null
	val target = whispersync.pendingAudioSeek?.target ?: return null
	val trackIndex = target.wordSyncTrackIndexOrNull() ?: return null
	return ReaderWordSyncPlaybackIdentity(
		audioResourceId = target.audioResource,
		audioTrackIndex = trackIndex,
		positionMs = target.positionMs,
		playbackSpeed = playbackSpeed
	)
}

private fun ReaderControllerStep.withExactWordSyncSeek(
	word: WordSyncWord,
	rawCommand: ReaderEngineCommand
): ReaderControllerStep {
	val requestId = rawCommand.overlayFragmentOrNull()?.overlayRequestId
	val runtimeAudioResource = rawCommand.overlayFragmentOrNull()?.resourceHref ?: word.audioResourceId
	val pending = controller.state.whispersync.pendingAudioSeek
	val exactPending = pending
		?.takeIf { it.overlayRequestId == requestId }
		?.let { pendingSeek ->
			pendingSeek.copy(
				target = pendingSeek.target.copy(
					audioResource = runtimeAudioResource,
					positionMs = word.audioStartMs,
					audioTrackIndex = word.audioTrackIndex
				)
			)
		}
		?: pending
	val exactImmediate = whispersyncAudioSeekTarget
		?.copy(
			audioResource = runtimeAudioResource,
			positionMs = word.audioStartMs,
			audioTrackIndex = word.audioTrackIndex
		)
	val rawFragment = rawCommand.overlayFragmentOrNull()
	val nextController = controller.copy(
		state = controller.state.copy(
			whispersync = controller.state.whispersync.copy(pendingAudioSeek = exactPending),
			activeMediaOverlay = when (rawCommand) {
				is ReaderEngineCommand.UpdateMediaOverlayProgress -> rawFragment
				else -> controller.state.activeMediaOverlay
			},
			audioMetadataLabel = when (rawCommand) {
				is ReaderEngineCommand.UpdateMediaOverlayProgress -> rawFragment?.label
				else -> controller.state.audioMetadataLabel
			}
		)
	)
	return copy(
		controller = nextController,
		engineCommands = listOf(rawCommand),
		whispersyncAudioSeekTarget = exactImmediate
	)
}

// Whispersync segment cues, in DOM coordinates or, for canonical sidecars (coordinateBasis), in
// WordSync extracted-UTF-8 coordinates (#24). Both demand the WordSync index and are refined into
// word fragments. Only controller commands reach this point: the coordinator's own word fragments
// go straight to the engine and are never fed back as cues.
private fun ReaderEngineCommand?.asCueOverlayCommand(): ReaderEngineCommand? = when (this) {
	is ReaderEngineCommand.ApplyMediaOverlay -> takeIf { fragment.isWhispersyncCue() }
	is ReaderEngineCommand.UpdateMediaOverlayProgress -> takeIf { fragment.isWhispersyncCue() }
	else -> null
}

private fun ReaderOverlayFragment.isWhispersyncCue(): Boolean =
	coordinateMode == ReaderOverlayCoordinateMode.CueV1DomUtf16 ||
		(coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8 && rawProvenanceId != null)

private fun WordSyncPublicationChapterProvenance.toReaderRawTextProvenanceDescriptor() =
	ReaderRawTextProvenanceDescriptor(
		id = "wordsync-v1-spine-$spineIndex",
		href = ebookHref,
		spineIndex = spineIndex,
		sourceHash = sourceHash,
		extractedTextHash = extractedTextHash,
		byteLength = extractedByteLength,
		tokenCount = tokenCount
	)

private fun ReaderEngineEvent.TextPoint.rawPointOrNull(): ReaderWordSyncRawPoint? {
	val id = rawProvenanceId ?: return null
	val offset = rawByteOffset ?: return null
	return ReaderWordSyncRawPoint(id, offset)
}

private fun ReaderEngineEvent.VisibleTextRange.rawPointOrNull(): ReaderWordSyncRawPoint? {
	val id = rawProvenanceId ?: return null
	val offset = rawByteStart ?: return null
	return ReaderWordSyncRawPoint(id, offset)
}

private val WordSyncProgressRefusalReasons =
	setOf("stale-progress-request", "progress-outside-visible-page", "progress-paint-rejected")
