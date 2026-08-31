package paige.navic.reader

import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderRawTextProvenanceBridgeTest {
	@Test
	fun installCommandBecomesDurableFoliateProvenanceState() {
		val descriptor = descriptor()
		val command = ReaderEngineCommand.InstallRawTextProvenance(descriptor)
		assertEquals(ReaderEngineCapability.MediaOverlay, command.requiredCapability)

		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(openRequest()))
			.engine
		val installed = opened.onCommand(command)
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(installed.viewState)
		assertEquals(listOf(descriptor), viewState.rawTextProvenanceDescriptors)
		assertNull(viewState.command)
	}

	@Test
	fun canonicalCueSidecarInstallsItsSpineProofIntoExistingProvenanceStoreBoundary() {
		val href = "OEBPS/Text/public-synthetic.xhtml"
		val sourceXhtml = "<html><body><p>Café &amp; café</p><p>Public synthetic cue</p></body></html>"
		val extractedText = "Café & café. Public synthetic cue."
		val locator = "Public synthetic cue"
		val rawStart = extractedText.substring(0, extractedText.indexOf(locator)).encodeUtf8().size
		val rawEnd = rawStart + locator.encodeUtf8().size
		val tokenCount = Regex("[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?")
			.findAll(extractedText)
			.count()
		val sourceHash = "sha256:${sourceXhtml.encodeUtf8().sha256().hex()}"
		val extractedTextHash = "sha256:${extractedText.encodeUtf8().sha256().hex()}"
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
			      "href": "$href",
			      "spineIndex": 2,
			      "sourceHash": "$sourceHash",
			      "extractedTextHash": "$extractedTextHash",
			      "byteLength": ${extractedText.encodeUtf8().size},
			      "tokenCount": $tokenCount
			    }]
			  },
			  "cues": [{
			    "id": 1040,
			    "audioHref": "Audio/public-synthetic.mp3",
			    "audioStart": 1,
			    "audioEnd": 2,
			    "ebookHref": "$href",
			    "spineIndex": 2,
			    "ebookStart": $rawStart,
			    "ebookEnd": $rawEnd,
			    "ebookText": "$locator"
			  }]
			}
			""".trimIndent()
		)
		val step = ReaderController().open(openRequest()).controller.loadWhispersyncSidecar(sidecar)
		val installs = step.engineCommands.filterIsInstance<ReaderEngineCommand.InstallRawTextProvenance>()

		assertEquals(
			1,
			installs.size,
			"a verified canonical sidecar spine must install one existing provenance-store descriptor"
		)
		val descriptor = installs.single().descriptor
		assertEquals(href, descriptor.href)
		assertEquals(2, descriptor.spineIndex)
		assertEquals(sourceHash, descriptor.sourceHash)
		assertEquals(extractedTextHash, descriptor.extractedTextHash)
		assertEquals(extractedText.encodeUtf8().size, descriptor.byteLength)
		assertEquals(tokenCount, descriptor.tokenCount)
		assertEquals(
			RawTextProvenanceStatus.Pending,
			step.controller.state.rawTextProvenanceById[descriptor.id]?.status
		)
	}

	@Test
	fun adapterMapsSafeRawStatusAndOptionalInverseFields() {
		val status = ReaderBridgeEvent.RawTextProvenanceStatusChanged(
			provenanceId = "chapter-raw-1",
			status = RawTextProvenanceStatus.Ready
		)
		val visible = ReaderBridgeEvent.VisibleTextRange(
			textHref = "OPS/Text/chapter.xhtml",
			visibleStart = 12,
			visibleEnd = 31,
			rawProvenanceId = "chapter-raw-1",
			rawSpineIndex = 3,
			rawByteStart = 44,
			rawByteEnd = 69
		)
		val point = ReaderBridgeEvent.TextPoint(
			textHref = "OPS/Text/chapter.xhtml",
			textOffset = 18,
			rawProvenanceId = "chapter-raw-1",
			rawByteOffset = 52
		)
		val adapter = FoliateEpubEngineAdapter()

		assertEquals(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = "chapter-raw-1",
				status = RawTextProvenanceStatus.Ready
			),
			adapter.onHostEvent(ReaderEngineHostEvent.FoliateBridge(status))
		)
		assertEquals(
			ReaderEngineEvent.VisibleTextRange(
				textHref = visible.textHref,
				visibleStart = visible.visibleStart,
				visibleEnd = visible.visibleEnd,
				rawProvenanceId = visible.rawProvenanceId,
				rawSpineIndex = visible.rawSpineIndex,
				rawByteStart = visible.rawByteStart,
				rawByteEnd = visible.rawByteEnd
			),
			adapter.onHostEvent(ReaderEngineHostEvent.FoliateBridge(visible))
		)
		assertEquals(
			ReaderEngineEvent.TextPoint(
				textHref = point.textHref,
				textOffset = point.textOffset,
				rawProvenanceId = point.rawProvenanceId,
				rawByteOffset = point.rawByteOffset
			),
			adapter.onHostEvent(ReaderEngineHostEvent.FoliateBridge(point))
		)
	}

	@Test
	fun controllerTracksRawReadinessByProvenanceIdAndClearsItOnOpen() {
		val descriptor = descriptor()
		val opened = ReaderController().open(openRequest()).controller
		val unsolicited = opened.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = "never-installed",
				status = RawTextProvenanceStatus.Ready
			)
		).controller
		assertEquals(emptyMap(), unsolicited.state.rawTextProvenanceById)

		val pending = opened.installRawTextProvenance(descriptor)
		assertEquals(
			RawTextProvenanceState(RawTextProvenanceStatus.Pending),
			pending.controller.state.rawTextProvenanceById[descriptor.id]
		)
		assertEquals(
			listOf(ReaderEngineCommand.InstallRawTextProvenance(descriptor)),
			pending.engineCommands
		)

		val ready = pending.controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		).controller
		assertEquals(
			RawTextProvenanceState(RawTextProvenanceStatus.Ready),
			ready.state.rawTextProvenanceById[descriptor.id]
		)

		val rejected = ready.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		assertEquals(
			RawTextProvenanceState(
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			),
			rejected.state.rawTextProvenanceById[descriptor.id]
		)

		val reopened = rejected.open(openRequest()).controller
		assertEquals(emptyMap(), reopened.state.rawTextProvenanceById)
	}

	@Test
	fun referencedCanonicalRejectionInvalidatesPresentationAndFailsTransportClosed() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val active = activeCanonicalController(sidecar)
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val destination = requireNotNull(active.state.destinationCommitIdentity)
		val staleGeneration = active.state.whispersync.cueMap.presentationGeneration

		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		)

		assertEquals(
			RawTextProvenanceState(
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			),
			rejected.controller.state.rawTextProvenanceById[descriptor.id]
		)
		val replacement = assertIs<ReaderEngineCommand.ReplaceWhispersyncCueMap>(
			rejected.engineCommands.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>().single()
		).presentation
		assertEquals(staleGeneration + 1L, replacement.presentationGeneration)
		assertEquals(destination, replacement.destinationCommitIdentity)
		assertTrue(replacement.cues.isEmpty())
		assertTrue(rejected.engineCommands.contains(ReaderEngineCommand.ClearMediaOverlay))
		assertNull(rejected.controller.state.activeMediaOverlay)
		assertNull(rejected.controller.state.activeMediaOverlayAnchorReceipt)
		assertNull(rejected.controller.state.whispersync.sync.activeCueKey)
		assertNull(rejected.controller.state.whispersync.pendingAudioSeek)
		assertNull(rejected.controller.state.whispersync.preparedVisibleTarget)
		assertFalse(rejected.controller.state.whispersync.playbackStartPending)
		assertEquals(
			ReaderWhispersyncPlaybackIntent.Enabled,
			rejected.controller.state.whispersync.playbackIntent
		)
		assertEquals(
			ReaderWhispersyncTransportPhase.Failed,
			rejected.controller.state.whispersync.transportPhase
		)
		assertEquals(
			ReaderWhispersyncStatusKind.Mismatch,
			rejected.controller.state.whispersync.status.kind
		)
		assertEquals(
			ReaderWhispersyncStatusMessage.Mismatch,
			rejected.controller.state.whispersync.status.message
		)
		assertEquals(
			"Canonical coordinate mapping rejected",
			rejected.controller.state.whispersync.status.detail
		)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, rejected.readaloudPlaybackCommand)

		val staleReceipt = rejected.controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCueMapRendered(
				sourceOrdinalsInDomReadingOrder = listOf(0),
				revisionDigest = sidecar.revisionDigest,
				presentationGeneration = staleGeneration,
				destinationCommitIdentity = destination
			)
		)
		assertEquals(rejected.controller, staleReceipt.controller)

		val ordinaryReplacement = rejected.controller.onEngineEvent(
			ReaderEngineEvent.SettingsPresentationCommitted(snapshotKey = 73)
		)
		val blockedPresentation = ordinaryReplacement.engineCommands
			.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
			.single()
			.presentation
		assertTrue(blockedPresentation.cues.isEmpty())
	}

	@Test
	fun unreferencedRejectionOnlyUpdatesThatInstalledDescriptor() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val active = activeCanonicalController(sidecar)
		val unreferenced = descriptor().copy(id = "unreferenced-public-synthetic")
		val installed = active.installRawTextProvenance(unreferenced).controller

		val rejected = installed.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = unreferenced.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.SourceHashMismatch
			)
		)

		assertEquals(
			installed.state.copy(
				rawTextProvenanceById = installed.state.rawTextProvenanceById + (
					unreferenced.id to RawTextProvenanceState(
						RawTextProvenanceStatus.Rejected,
						RawTextProvenanceReason.SourceHashMismatch
					)
				)
			),
			rejected.controller.state
		)
		assertTrue(rejected.engineCommands.isEmpty())
		assertNull(rejected.readaloudPlaybackCommand)
	}

	@Test
	fun pendingCanonicalProofKeepsQueuedCuePresentationEmpty() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val pending = activeCanonicalController(
			sidecar = sidecar,
			provenanceStatus = RawTextProvenanceStatus.Pending
		)

		val replacement = pending.onEngineEvent(
			ReaderEngineEvent.SettingsPresentationCommitted(snapshotKey = 74)
		).engineCommands.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
			.single()
			.presentation

		assertTrue(replacement.cues.isEmpty())
	}

	@Test
	fun canonicalMismatchRepairReinstallsRejectedReferenceAndWaitsForFreshRawRange() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val active = activeCanonicalController(sidecar)
		val unrelated = descriptor().copy(id = "unreferenced-retry-control")
		val withUnrelated = active.installRawTextProvenance(unrelated).controller
		val canonicalRejected = withUnrelated.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.ExtractedHashMismatch
			)
		).controller
		val bothRejected = canonicalRejected.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = unrelated.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.SourceUnavailable
			)
		).controller
		val mismatch = bothRejected.state.whispersync.status

		val retry = bothRejected.repairWhispersyncMismatch()

		assertEquals(
			listOf(descriptor),
			retry.engineCommands.filterIsInstance<ReaderEngineCommand.InstallRawTextProvenance>()
				.map(ReaderEngineCommand.InstallRawTextProvenance::descriptor)
		)
		assertEquals(
			ReaderEngineCommand.RequestVisibleTextRange("canonical-coordinate-retry"),
			retry.engineCommands.last()
		)
		assertEquals(
			RawTextProvenanceState(RawTextProvenanceStatus.Pending),
			retry.controller.state.rawTextProvenanceById[descriptor.id]
		)
		assertEquals(
			RawTextProvenanceState(
				RawTextProvenanceStatus.Rejected,
				RawTextProvenanceReason.SourceUnavailable
			),
			retry.controller.state.rawTextProvenanceById[unrelated.id]
		)
		assertEquals(mismatch, retry.controller.state.whispersync.status)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, retry.controller.state.whispersync.transportPhase)

		val proofReady = retry.controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		)
		assertEquals(mismatch, proofReady.controller.state.whispersync.status)
		assertTrue(proofReady.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })

		val segment = sidecar.timeline.segments.single()
		val destination = requireNotNull(proofReady.controller.state.destinationCommitIdentity)
		val freshRange = proofReady.controller.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = segment.textHref,
				visibleStart = 0,
				visibleEnd = 1,
				source = "canonical-coordinate-retry",
				rawProvenanceId = descriptor.id,
				rawSpineIndex = descriptor.spineIndex,
				rawByteStart = requireNotNull(segment.textStart),
				rawByteEnd = requireNotNull(segment.textEnd),
				destinationCommitIdentity = destination
			)
		)
		val validation = freshRange.engineCommands
			.filterIsInstance<ReaderEngineCommand.ValidateWhispersyncCanonicalCues>()
			.single()
		val accepted = freshRange.controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCanonicalPreflightResult(
				revisionDigest = validation.request.revisionDigest,
				validationGeneration = validation.request.validationGeneration,
				destinationCommitIdentity = validation.request.destinationCommitIdentity,
				provenanceId = validation.request.provenanceId,
				rawSpineIndex = validation.request.rawSpineIndex,
				status = ReaderWhispersyncCanonicalPreflightStatus.Ready
			)
		)
		assertEquals(
			ReaderWhispersyncStatusKind.Ready,
			accepted.controller.state.whispersync.status.kind
		)
		assertTrue(accepted.controller.state.whispersync.preparedVisibleTarget != null)
		assertTrue(
			accepted.engineCommands
				.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
				.single()
				.presentation.cues.isNotEmpty()
		)
	}

	@Test
	fun canonicalRerejectionAndQueuedDomOnlyRangeRemainTerminal() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val active = activeCanonicalController(sidecar)
		val destination = requireNotNull(active.state.destinationCommitIdentity)
		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val retry = rejected.repairWhispersyncMismatch().controller
		val rerejected = retry.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.ExtractedHashMismatch
			)
		).controller

		val queuedDomOnly = rerejected.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = sidecar.timeline.segments.single().textHref,
				visibleStart = 0,
				visibleEnd = 24,
				source = "canonical-coordinate-retry",
				destinationCommitIdentity = destination
			)
		)

		assertEquals(
			ReaderWhispersyncTransportPhase.Failed,
			queuedDomOnly.controller.state.whispersync.transportPhase
		)
		assertEquals(
			ReaderWhispersyncStatusKind.Mismatch,
			queuedDomOnly.controller.state.whispersync.status.kind
		)
		assertEquals(
			ReaderWhispersyncStatusMessage.Mismatch,
			queuedDomOnly.controller.state.whispersync.status.message
		)
		assertNull(queuedDomOnly.controller.state.whispersync.preparedVisibleTarget)
		assertNull(queuedDomOnly.whispersyncAudioSeekTarget)
		assertNull(queuedDomOnly.readaloudPlaybackCommand)
		assertTrue(
			queuedDomOnly.engineCommands.none {
				it is ReaderEngineCommand.ApplyMediaOverlay ||
					it is ReaderEngineCommand.UpdateMediaOverlayProgress
			}
		)
		assertTrue(
			queuedDomOnly.engineCommands
				.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
				.all { it.presentation.cues.isEmpty() }
		)
	}

	@Test
	fun canonicalRejectionBlocksLatePlayingAndStoppedTransportPulses() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val rejected = activeCanonicalController(sidecar).onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val segment = sidecar.timeline.segments.single()

		val playingPulse = rejected.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = segment.audioResource,
				positionMs = segment.startMs
			)
		)

		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, playingPulse.controller.state.whispersync.playbackIntent)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, playingPulse.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, playingPulse.controller.state.whispersync.status.kind)
		assertFalse(playingPulse.controller.state.chrome.readaloudPlayback.isPlaying)
		assertNull(playingPulse.controller.state.activeMediaOverlay)
		assertNull(playingPulse.controller.state.whispersync.sync.activeCueKey)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, playingPulse.readaloudPlaybackCommand)
		assertTrue(
			playingPulse.engineCommands.none {
				it is ReaderEngineCommand.ApplyMediaOverlay ||
					it is ReaderEngineCommand.UpdateMediaOverlayProgress
			}
		)

		val stoppedPulse = playingPulse.controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				audioResource = segment.audioResource,
				positionMs = segment.startMs
			)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, stoppedPulse.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, stoppedPulse.controller.state.whispersync.status.kind)
		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, stoppedPulse.controller.state.whispersync.playbackIntent)
		assertNull(stoppedPulse.readaloudPlaybackCommand)
		assertTrue(
			stoppedPulse.engineCommands.none {
				it is ReaderEngineCommand.ApplyMediaOverlay ||
					it is ReaderEngineCommand.UpdateMediaOverlayProgress
			}
		)
	}

	@Test
	fun canonicalPlaybackGapUsesNoActiveCueWhileConflictingSpineStillPauses() {
		val sidecar = publicSyntheticMultiSpineCanonicalSidecar()
		val descriptors = sidecar.referencedRawTextProvenanceDescriptors()
		val currentDescriptor = descriptors.first()
		val conflictingDescriptor = descriptors.last()
		val currentSegment = sidecar.timeline.segments.first {
			it.rawProvenanceId == currentDescriptor.id
		}
		val conflictingSegment = sidecar.timeline.segments.first {
			it.rawProvenanceId == conflictingDescriptor.id
		}
		val active = activeCanonicalController(sidecar, currentDescriptor = currentDescriptor).let { controller ->
			controller.copy(
				state = controller.state.copy(
					rawTextProvenanceById = controller.state.rawTextProvenanceById + (
						conflictingDescriptor.id to RawTextProvenanceState(RawTextProvenanceStatus.Ready)
					)
				)
			)
		}

		val gap = active.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = currentSegment.audioResource,
				positionMs = currentSegment.endMs + 500L
			)
		)
		assertNull(gap.readaloudPlaybackCommand)
		assertTrue(gap.controller.state.chrome.readaloudPlayback.isPlaying)
		assertEquals(ReaderWhispersyncTransportPhase.Playing, gap.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.NoActiveCue, gap.controller.state.whispersync.status.kind)
		assertTrue(gap.engineCommands.any { it == ReaderEngineCommand.ClearMediaOverlay })

		val conflict = active.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = conflictingSegment.audioResource,
				positionMs = conflictingSegment.startMs
			)
		)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, conflict.readaloudPlaybackCommand)
		assertFalse(conflict.controller.state.chrome.readaloudPlayback.isPlaying)
		assertNull(conflict.controller.state.activeMediaOverlay)
	}

	@Test
	fun mandatoryCanonicalPreflightRunsWithCueMapDisabledAndFencesStaleRejection() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val segment = sidecar.timeline.segments.single()
		val destination = ReaderDestinationCommitIdentity("public-synthetic-session", 11L)
		val initialRange = ReaderWhispersyncVisibleTextRange(
			textHref = segment.textHref,
			visibleStart = 0,
			visibleEnd = 24,
			rawProvenanceId = descriptor.id,
			rawSpineIndex = descriptor.spineIndex,
			rawByteStart = requireNotNull(segment.textStart),
			rawByteEnd = requireNotNull(segment.textEnd),
			destinationCommitIdentity = destination
		)
		val base = ReaderController().open(openRequest()).controller.let { opened ->
			opened.copy(
				state = opened.state.copy(
					destinationCommitIdentity = destination,
					whispersync = opened.state.whispersync.copy(visibleTextRange = initialRange)
				)
			)
		}

		val loaded = base.loadWhispersyncSidecar(sidecar)
		assertEquals(
			listOf("InstallRawTextProvenance"),
			loaded.engineCommands.mapNotNull { it::class.simpleName }
		)
		assertNull(loaded.controller.state.whispersync.preparedVisibleTarget)
		assertEquals(ReaderWhispersyncTransportPhase.Preparing, loaded.controller.state.whispersync.transportPhase)

		val provenanceReady = loaded.controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		)
		assertEquals(
			listOf("ValidateWhispersyncCanonicalCues"),
			provenanceReady.engineCommands.mapNotNull { it::class.simpleName }
		)
		assertTrue(provenanceReady.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap })
		assertNull(provenanceReady.controller.state.whispersync.preparedVisibleTarget)
		val blockedPulse = provenanceReady.controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = segment.audioResource,
				positionMs = segment.startMs
			)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Preparing, blockedPulse.controller.state.whispersync.transportPhase)
		assertNull(blockedPulse.controller.state.activeMediaOverlay)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, blockedPulse.readaloudPlaybackCommand)
		assertTrue(
			blockedPulse.engineCommands.none {
				it is ReaderEngineCommand.ApplyMediaOverlay ||
					it is ReaderEngineCommand.UpdateMediaOverlayProgress
			}
		)

		val rejectedEvent = canonicalPreflightEngineEvent(
			sidecar = sidecar,
			descriptor = descriptor,
			destination = destination,
			validationGeneration = 1L,
			status = "rejected",
			reason = "slice-mismatch"
		)
		assertNotNull(rejectedEvent)
		val preflightRejected = provenanceReady.controller.onEngineEvent(rejectedEvent)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, preflightRejected.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, preflightRejected.controller.state.whispersync.status.kind)
		assertNull(preflightRejected.controller.state.whispersync.preparedVisibleTarget)
		assertTrue(
			preflightRejected.engineCommands
				.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
				.all { it.presentation.cues.isEmpty() }
		)
		val rejectedRetry = preflightRejected.controller.repairWhispersyncMismatch()
		assertEquals(
			ReaderEngineCommand.RequestVisibleTextRange("canonical-coordinate-retry"),
			rejectedRetry.engineCommands.single()
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, rejectedRetry.controller.state.whispersync.transportPhase)
		assertEquals(
			RawTextProvenanceStatus.Ready,
			rejectedRetry.controller.state.rawTextProvenanceById[descriptor.id]?.status
		)

		val acceptedEvent = canonicalPreflightEngineEvent(
			sidecar = sidecar,
			descriptor = descriptor,
			destination = destination,
			validationGeneration = 1L,
			status = "ready"
		)
		assertNotNull(acceptedEvent)
		val accepted = provenanceReady.controller.onEngineEvent(acceptedEvent)
		assertEquals(ReaderWhispersyncTransportPhase.Ready, accepted.controller.state.whispersync.transportPhase)
		assertNotNull(accepted.controller.state.whispersync.preparedVisibleTarget)
		assertTrue(accepted.engineCommands.none { it is ReaderEngineCommand.ReplaceWhispersyncCueMap })

		val nextDestination = ReaderDestinationCommitIdentity("public-synthetic-session", 12L)
		val movedController = accepted.controller.copy(
			state = accepted.controller.state.copy(destinationCommitIdentity = nextDestination)
		)
		val moved = movedController.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = segment.textHref,
				visibleStart = 0,
				visibleEnd = 24,
				rawProvenanceId = descriptor.id,
				rawSpineIndex = descriptor.spineIndex,
				rawByteStart = requireNotNull(segment.textStart),
				rawByteEnd = requireNotNull(segment.textEnd),
				destinationCommitIdentity = nextDestination
			)
		)
		assertEquals(
			listOf("ValidateWhispersyncCanonicalCues"),
			moved.engineCommands.mapNotNull { it::class.simpleName }
		)
		val staleRejection = canonicalPreflightEngineEvent(
			sidecar = sidecar,
			descriptor = descriptor,
			destination = destination,
			validationGeneration = 1L,
			status = "rejected",
			reason = "slice-mismatch"
		)
		assertNotNull(staleRejection)
		assertEquals(moved.controller, moved.controller.onEngineEvent(staleRejection).controller)
	}

	@Test
	fun canonicalTerminalRecoveryWaitsForResyncFreshRawRangeAndAcceptedPreflight() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val active = activeCanonicalController(sidecar)
		val destination = requireNotNull(active.state.destinationCommitIdentity)
		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val retry = rejected.repairWhispersyncMismatch().controller
		val proofReady = retry.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		)
		assertTrue(proofReady.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })
		assertTrue(
			proofReady.engineCommands.any {
				it == ReaderEngineCommand.RequestVisibleTextRange("canonical-coordinate-retry")
			}
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, proofReady.controller.state.whispersync.transportPhase)

		val segment = sidecar.timeline.segments.single()
		val freshRawRange = proofReady.controller.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = segment.textHref,
				visibleStart = 0,
				visibleEnd = 24,
				source = "canonical-coordinate-retry",
				rawProvenanceId = descriptor.id,
				rawSpineIndex = descriptor.spineIndex,
				rawByteStart = requireNotNull(segment.textStart),
				rawByteEnd = requireNotNull(segment.textEnd),
				destinationCommitIdentity = destination
			)
		)
		val validation = freshRawRange.engineCommands
			.filterIsInstance<ReaderEngineCommand.ValidateWhispersyncCanonicalCues>()
			.single()
		assertEquals(ReaderWhispersyncTransportPhase.Failed, freshRawRange.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, freshRawRange.controller.state.whispersync.status.kind)

		val accepted = freshRawRange.controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCanonicalPreflightResult(
				revisionDigest = validation.request.revisionDigest,
				validationGeneration = validation.request.validationGeneration,
				destinationCommitIdentity = validation.request.destinationCommitIdentity,
				provenanceId = validation.request.provenanceId,
				rawSpineIndex = validation.request.rawSpineIndex,
				status = ReaderWhispersyncCanonicalPreflightStatus.Ready
			)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Ready, accepted.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Ready, accepted.controller.state.whispersync.status.kind)
		assertNotNull(accepted.controller.state.whispersync.preparedVisibleTarget)
		assertNull(accepted.whispersyncAudioSeekTarget)
		assertTrue(
			accepted.engineCommands.none {
				it is ReaderEngineCommand.ApplyMediaOverlay ||
					it is ReaderEngineCommand.UpdateMediaOverlayProgress
			}
		)
	}

	@Test
	fun canonicalTerminalSurvivesNavigationStopShellAndStatusPulsesUntilResync() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val active = activeCanonicalController(sidecar)
		val destination = requireNotNull(active.state.destinationCommitIdentity)
		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller

		val navigated = ReaderWhispersyncReducer.reserveUserNavigation(rejected)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, navigated.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, navigated.state.whispersync.status.kind)
		val domOnly = navigated.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = sidecar.timeline.segments.single().textHref,
				visibleStart = 0,
				visibleEnd = 24,
				destinationCommitIdentity = destination
			)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, domOnly.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, domOnly.controller.state.whispersync.status.kind)
		assertTrue(domOnly.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })

		val stopped = domOnly.controller.onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.StopAndReset
		)
		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, stopped.readaloudPlaybackCommand)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, stopped.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, stopped.controller.state.whispersync.status.kind)

		val shellController = rejected.copy(
			state = rejected.state.copy(shellCoverVisible = true)
		)
		val shellPulse = shellController.onReadaloudPlaybackState(
			active.state.chrome.readaloudPlayback.copy(isPlaying = true)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, shellPulse.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, shellPulse.controller.state.whispersync.status.kind)

		val statusPulse = rejected.reportWhispersyncLoadFailure(
			message = ReaderWhispersyncStatusMessage.AudioUnavailable,
			detail = "safe-synthetic-failure"
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, statusPulse.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, statusPulse.controller.state.whispersync.status.kind)
	}

	@Test
	fun canonicalTerminalSurvivesShellCoverOwnershipAndReturnRemainsRepairable() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val rejected = activeCanonicalController(sidecar).onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val terminalStatus = rejected.state.whispersync.status
		val withShellPresentation = rejected.copy(
			state = rejected.state.copy(
				activeMediaOverlay = sidecar.timeline.segments.single().toReaderOverlayFragment(),
				audioMetadataLabel = "Public synthetic audio"
			)
		)

		val covered = ReaderOverlayReducer.showNativeShellCover(withShellPresentation)
		assertTrue(covered.controller.state.shellCoverVisible)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, covered.controller.state.whispersync.transportPhase)
		assertEquals(terminalStatus, covered.controller.state.whispersync.status)
		assertEquals(
			ReaderWhispersyncCanonicalGenerationState.Terminal,
			covered.controller.state.whispersync.canonicalGenerationState
		)
		assertNull(covered.controller.state.activeMediaOverlay)
		assertNull(covered.controller.state.activeMediaOverlayAnchorReceipt)
		assertNull(covered.controller.state.audioMetadataLabel)
		assertTrue(covered.engineCommands.any { it == ReaderEngineCommand.ClearMediaOverlay })

		val returned = covered.controller.onViewerAction(ReaderViewerAction.NativeShellPrepared)
		assertFalse(returned.controller.state.shellCoverVisible)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, returned.controller.state.whispersync.transportPhase)
		assertEquals(terminalStatus, returned.controller.state.whispersync.status)
		assertTrue(returned.controller.state.whispersync.status.repairable)
		assertTrue(returned.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })

		val retry = returned.controller.repairWhispersyncMismatch()
		assertEquals(
			ReaderWhispersyncCanonicalGenerationState.RetryArmed,
			retry.controller.state.whispersync.canonicalGenerationState
		)
		assertEquals(terminalStatus, retry.controller.state.whispersync.status)
		assertTrue(
			retry.engineCommands.filterIsInstance<ReaderEngineCommand.InstallRawTextProvenance>()
				.any { it.descriptor.id == descriptor.id }
		)
		assertTrue(
			retry.engineCommands.any {
				it == ReaderEngineCommand.RequestVisibleTextRange("canonical-coordinate-retry")
			}
		)
	}

	@Test
	fun explicitCanonicalResyncWaitsForAllRejectedDescriptorsAndFreshBoundedRange() {
		val sidecar = publicSyntheticMultiSpineCanonicalSidecar()
		val descriptors = sidecar.referencedRawTextProvenanceDescriptors()
		val currentDescriptor = descriptors.first()
		val unrelatedDescriptor = descriptors.last()
		val active = activeCanonicalController(sidecar, currentDescriptor = currentDescriptor)
		val destination = requireNotNull(active.state.destinationCommitIdentity)
		val currentSegment = sidecar.timeline.segments.first {
			it.rawProvenanceId == currentDescriptor.id
		}
		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = unrelatedDescriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val retry = rejected.repairWhispersyncMismatch()
		assertEquals(ReaderWhispersyncTransportPhase.Failed, retry.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, retry.controller.state.whispersync.status.kind)
		assertEquals(
			RawTextProvenanceStatus.Pending,
			retry.controller.state.rawTextProvenanceById[unrelatedDescriptor.id]?.status
		)

		val outOfBounds = retry.controller.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = currentDescriptor.href,
				visibleStart = 0,
				visibleEnd = 24,
				rawProvenanceId = currentDescriptor.id,
				rawSpineIndex = currentDescriptor.spineIndex,
				rawByteStart = requireNotNull(currentSegment.textStart),
				rawByteEnd = currentDescriptor.byteLength + 1,
				destinationCommitIdentity = destination
			)
		)
		assertTrue(outOfBounds.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, outOfBounds.controller.state.whispersync.status.kind)

		val allReady = outOfBounds.controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = unrelatedDescriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		)
		assertTrue(
			allReady.engineCommands.any {
				it == ReaderEngineCommand.RequestVisibleTextRange("canonical-coordinate-retry")
			}
		)
		assertTrue(allReady.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })

		val fresh = allReady.controller.onEngineEvent(
			ReaderEngineEvent.VisibleTextRange(
				textHref = currentDescriptor.href,
				visibleStart = 0,
				visibleEnd = 24,
				rawProvenanceId = currentDescriptor.id,
				rawSpineIndex = currentDescriptor.spineIndex,
				rawByteStart = requireNotNull(currentSegment.textStart),
				rawByteEnd = requireNotNull(currentSegment.textEnd),
				destinationCommitIdentity = destination
			)
		)
		val validation = fresh.engineCommands
			.filterIsInstance<ReaderEngineCommand.ValidateWhispersyncCanonicalCues>()
			.single()
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, fresh.controller.state.whispersync.status.kind)
		val accepted = fresh.controller.onEngineEvent(
			ReaderEngineEvent.WhispersyncCanonicalPreflightResult(
				revisionDigest = validation.request.revisionDigest,
				validationGeneration = validation.request.validationGeneration,
				destinationCommitIdentity = validation.request.destinationCommitIdentity,
				provenanceId = validation.request.provenanceId,
				rawSpineIndex = validation.request.rawSpineIndex,
				status = ReaderWhispersyncCanonicalPreflightStatus.Ready
			)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Ready, accepted.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Ready, accepted.controller.state.whispersync.status.kind)
		assertNotNull(accepted.controller.state.whispersync.preparedVisibleTarget)

		val paused = accepted.controller.copy(
			state = accepted.controller.state.copy(
				whispersync = accepted.controller.state.whispersync.copy(
					userPaused = true,
					userPausedDestinationCommitIdentity = destination
				)
			)
		)
		assertEquals(
			ReaderReadaloudPlaybackCommand.Play,
			paused.onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)
				.readaloudPlaybackCommand
		)
	}

	@Test
	fun canonicalUserPausedResumeRequiresCurrentAdmittedAuthority() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val destination = ReaderDestinationCommitIdentity("public-synthetic-session", 1L)
		val active = activeCanonicalController(sidecar)
		fun ReaderController.userPaused() = copy(
			state = state.copy(
				whispersync = state.whispersync.copy(
					userPaused = true,
					userPausedDestinationCommitIdentity = destination
				)
			)
		)

		val pending = active.copy(
			state = active.state.copy(
				rawTextProvenanceById = active.state.rawTextProvenanceById + (
					descriptor.id to RawTextProvenanceState(RawTextProvenanceStatus.Pending)
				)
			)
		).userPaused().onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)
		assertNull(pending.readaloudPlaybackCommand)
		assertNull(pending.whispersyncAudioSeekTarget)

		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller.userPaused()
		val rejectedPlay = rejected.onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)
		assertNull(rejectedPlay.readaloudPlaybackCommand)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, rejectedPlay.controller.state.whispersync.status.kind)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, rejectedPlay.controller.state.whispersync.transportPhase)
	}

	@Test
	fun currentSpineCueGeometryAllowsUnrelatedPendingButAnyRejectionIsTerminal() {
		val sidecar = publicSyntheticMultiSpineCanonicalSidecar()
		val descriptors = sidecar.referencedRawTextProvenanceDescriptors()
		val currentDescriptor = descriptors.first()
		val unrelatedDescriptor = descriptors.last()
		val active = activeCanonicalController(sidecar, currentDescriptor = currentDescriptor)
		assertEquals(
			RawTextProvenanceStatus.Pending,
			active.state.rawTextProvenanceById[unrelatedDescriptor.id]?.status
		)

		val pendingPresentation = active.onEngineEvent(
			ReaderEngineEvent.SettingsPresentationCommitted(snapshotKey = 81)
		).engineCommands.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
			.single().presentation
		assertTrue(pendingPresentation.cues.isNotEmpty())
		assertTrue(pendingPresentation.cues.all { it.rawProvenanceId == currentDescriptor.id })

		val rejected = active.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = unrelatedDescriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, rejected.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, rejected.controller.state.whispersync.status.kind)
		assertTrue(
			rejected.engineCommands.filterIsInstance<ReaderEngineCommand.ReplaceWhispersyncCueMap>()
				.all { it.presentation.cues.isEmpty() }
		)
	}

	@Test
	fun canonicalRepairForcesExactlyOneEqualProofInstallThroughAdapter() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val rejected = activeCanonicalController(sidecar).onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller

		val repair = rejected.repairWhispersyncMismatch()
		val reinstall = repair.engineCommands
			.filterIsInstance<ReaderEngineCommand.InstallRawTextProvenance>()
			.single()
		val retainedAdapter = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(openRequest()))
			.engine
			.onCommand(ReaderEngineCommand.InstallRawTextProvenance(descriptor))
			.engine
		val dispatched = retainedAdapter.onCommand(reinstall)
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(dispatched.viewState)
		val bridge = assertIs<ReaderEngineHostCommand.FoliateBridge>(viewState.command)
		assertEquals(ReaderBridgeCommand.InstallRawTextProvenance(descriptor), bridge.command)
	}

	@Test
	fun freshCanonicalRevisionReusesUnchangedReadyProofAndStartsNewPreflight() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val active = activeCanonicalController(sidecar)
		val replacement = sidecar.copy(revisionDigest = freshRevisionDigest(sidecar.revisionDigest))

		val loaded = active.loadWhispersyncSidecar(replacement)
		assertEquals(
			RawTextProvenanceStatus.Ready,
			loaded.controller.state.rawTextProvenanceById[descriptor.id]?.status
		)
		assertTrue(loaded.engineCommands.none { it is ReaderEngineCommand.InstallRawTextProvenance })
		val validation = loaded.engineCommands
			.filterIsInstance<ReaderEngineCommand.ValidateWhispersyncCanonicalCues>()
			.single()
		assertEquals(replacement.revisionDigest, validation.request.revisionDigest)
	}

	@Test
	fun freshCanonicalRevisionForcesReachableInstallForUnchangedNonReadyProof() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val replacement = sidecar.copy(revisionDigest = freshRevisionDigest(sidecar.revisionDigest))
		val pending = activeCanonicalController(
			sidecar = sidecar,
			provenanceStatus = RawTextProvenanceStatus.Pending
		)
		val rejected = activeCanonicalController(sidecar).onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val retainedAdapter = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(openRequest()))
			.engine
			.onCommand(ReaderEngineCommand.InstallRawTextProvenance(descriptor))
			.engine

		listOf(pending, rejected).forEach { controller ->
			val loaded = controller.loadWhispersyncSidecar(replacement)
			assertEquals(
				RawTextProvenanceStatus.Pending,
				loaded.controller.state.rawTextProvenanceById[descriptor.id]?.status
			)
			val reinstall = loaded.engineCommands
				.filterIsInstance<ReaderEngineCommand.InstallRawTextProvenance>()
				.single()
			val dispatched = retainedAdapter.onCommand(reinstall)
			val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(dispatched.viewState)
			val bridge = assertIs<ReaderEngineHostCommand.FoliateBridge>(viewState.command)
			assertEquals(ReaderBridgeCommand.InstallRawTextProvenance(descriptor), bridge.command)
		}
	}

	@Test
	fun sameCanonicalSidecarReloadDoesNotClearTerminalGeneration() {
		val sidecar = publicSyntheticCanonicalSidecar()
		val descriptor = sidecar.referencedRawTextProvenanceDescriptors().single()
		val rejected = activeCanonicalController(sidecar).onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller

		val reloaded = rejected.loadWhispersyncSidecar(sidecar)
		assertEquals(
			ReaderWhispersyncCanonicalGenerationState.Terminal,
			reloaded.controller.state.whispersync.canonicalGenerationState
		)
		assertEquals(ReaderWhispersyncTransportPhase.Failed, reloaded.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Mismatch, reloaded.controller.state.whispersync.status.kind)
		assertTrue(reloaded.engineCommands.none { it is ReaderEngineCommand.InstallRawTextProvenance })
		assertTrue(reloaded.engineCommands.none { it is ReaderEngineCommand.ValidateWhispersyncCanonicalCues })
	}

	@Test
	fun freshCanonicalSidecarReplacementClearsPriorTerminalGeneration() {
		val rejectedSidecar = publicSyntheticCanonicalSidecar()
		val rejectedDescriptor = rejectedSidecar.referencedRawTextProvenanceDescriptors().single()
		val rejected = activeCanonicalController(rejectedSidecar).onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = rejectedDescriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		val replacement = publicSyntheticMultiSpineCanonicalSidecar()
		val loaded = rejected.loadWhispersyncSidecar(replacement)
		assertEquals(ReaderWhispersyncTransportPhase.Preparing, loaded.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncStatusKind.Ready, loaded.controller.state.whispersync.status.kind)
		assertTrue(
			loaded.engineCommands.filterIsInstance<ReaderEngineCommand.InstallRawTextProvenance>()
				.map { it.descriptor.id }
				.containsAll(replacement.referencedRawTextProvenanceDescriptors().map { it.id })
		)
	}

	@Test
	fun unsupportedFormatRejectsRawInstallAndStatusWithoutStateMutation() {
		val descriptor = descriptor()
		val pdfRequest = openRequest().copy(
			publication = openRequest().publication.copy(format = ReaderPublicationFormat.Pdf)
		)
		val controller = ReaderController().open(pdfRequest).controller

		val install = controller.installRawTextProvenance(descriptor)
		assertEquals(controller.state, install.controller.state)
		assertEquals(emptyList(), install.engineCommands)
		val status = controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		)
		assertEquals(controller.state, status.controller.state)
		assertNull(status.controller.state.rawTextProvenanceById[descriptor.id])
	}

	private fun freshRevisionDigest(current: String): String =
		if (current == "fedcba654321") "abcdef123456" else "fedcba654321"

	private fun canonicalPreflightEngineEvent(
		sidecar: WhispersyncSidecar,
		descriptor: ReaderRawTextProvenanceDescriptor,
		destination: ReaderDestinationCommitIdentity,
		validationGeneration: Long,
		status: String,
		reason: String? = null
	): ReaderEngineEvent? {
		val reasonField = reason?.let { ",\"reason\":\"$it\"" }.orEmpty()
		val bridgeEvent = decodeReaderBridgeEvent(
			"""
			{
			  "type":"whispersyncCanonicalPreflightResult",
			  "revisionDigest":"${sidecar.revisionDigest}",
			  "validationGeneration":$validationGeneration,
			  "provenanceId":"${descriptor.id}",
			  "rawSpineIndex":${descriptor.spineIndex},
			  "status":"$status",
			  "destinationFoliateSessionId":"${destination.foliateSessionId}",
			  "destinationCommitSequence":${destination.commitSequence}$reasonField
			}
			""".trimIndent()
		) ?: return null
		return FoliateEpubEngineAdapter().onHostEvent(
			ReaderEngineHostEvent.FoliateBridge(bridgeEvent)
		)
	}

	private fun activeCanonicalController(
		sidecar: WhispersyncSidecar,
		provenanceStatus: RawTextProvenanceStatus = RawTextProvenanceStatus.Ready,
		currentDescriptor: ReaderRawTextProvenanceDescriptor =
			sidecar.referencedRawTextProvenanceDescriptors().first()
	): ReaderController {
		val destination = ReaderDestinationCommitIdentity("public-synthetic-session", 1L)
		val descriptor = currentDescriptor
		val segment = sidecar.timeline.segments.first { candidate ->
			candidate.rawProvenanceId == descriptor.id
		}
		val textStart = requireNotNull(segment.textStart)
		val textEnd = requireNotNull(segment.textEnd)
		val opened = ReaderController().open(openRequest()).controller
		val loaded = opened.copy(
			state = opened.state.copy(
				destinationCommitIdentity = destination,
				whispersync = opened.state.whispersync.copy(
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = segment.textHref,
						visibleStart = 0,
						visibleEnd = 1,
						rawProvenanceId = descriptor.id,
						rawSpineIndex = descriptor.spineIndex,
						rawByteStart = textStart,
						rawByteEnd = textEnd,
						destinationCommitIdentity = destination
					)
				)
			)
		).loadWhispersyncSidecar(sidecar).controller
		val installed = if (provenanceStatus == RawTextProvenanceStatus.Pending) {
			loaded
		} else {
			loaded.onEngineEvent(
				ReaderEngineEvent.RawTextProvenanceStatusChanged(
					provenanceId = descriptor.id,
					status = provenanceStatus,
					reason = RawTextProvenanceReason.DocumentChanged
						.takeIf { provenanceStatus == RawTextProvenanceStatus.Rejected }
				)
			).controller
		}
		val target = WhispersyncOverlaySyncAdapter(sidecar.timeline).readerTargetForSegment(segment)
		val sync = ReaderOverlaySyncState().followReaderTarget(target).state
		val activeFragment = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(sync.engineCommand).fragment
		val admittedPreflight = installed.state.whispersync.canonicalPreflight?.copy(
			status = ReaderWhispersyncCanonicalPreflightStatus.Ready
		)
		return installed.copy(
			state = installed.state.copy(
				chrome = installed.state.chrome.copy(
					readaloudPlayback = ReaderReadaloudPlaybackUiState(
						isAvailable = true,
						isPlaying = true,
						audioResource = segment.audioResource,
						positionMs = segment.startMs
					)
				),
				destinationCommitIdentity = destination,
				whispersync = installed.state.whispersync.copy(
					canonicalPreflight = admittedPreflight,
					visibleTextRange = ReaderWhispersyncVisibleTextRange(
						textHref = segment.textHref,
						visibleStart = 0,
						visibleEnd = 1,
						rawProvenanceId = descriptor.id,
						rawSpineIndex = descriptor.spineIndex,
						rawByteStart = textStart,
						rawByteEnd = textEnd,
						destinationCommitIdentity = destination
					),
					sync = sync,
					pendingAudioSeek = ReaderWhispersyncPendingAudioSeek(
						overlayRequestId = requireNotNull(activeFragment.overlayRequestId),
						target = target.seekTarget
					),
					playbackIntent = ReaderWhispersyncPlaybackIntent.Enabled,
					transportPhase = ReaderWhispersyncTransportPhase.Playing,
					preparedVisibleTarget = ReaderWhispersyncPreparedVisibleTarget(
						destinationCommitIdentity = destination,
						firstVisibleCue = target.cue,
						audioSeekTarget = target.seekTarget,
						preparationGeneration = 1L
					),
					playbackStartPending = true,
					preparationGeneration = 1L,
					cueMap = ReaderWhispersyncCueMapState(
						enabled = true,
						presentationGeneration = 4L,
						audioActiveSourceOrdinal = segment.sourceOrdinal,
						renderedHighlightSourceOrdinal = segment.sourceOrdinal,
						sourceOrdinalsInDomReadingOrder = listOf(segment.sourceOrdinal)
					)
				),
				activeMediaOverlay = activeFragment,
				audioMetadataLabel = segment.label
			)
		)
	}

	private fun publicSyntheticCanonicalSidecar(): WhispersyncSidecar {
		val href = "OEBPS/Text/public-synthetic.xhtml"
		val sourceXhtml = "<html><body><p>Café &amp; café</p><p>Public synthetic cue</p></body></html>"
		val extractedText = "Café & café. Public synthetic cue."
		val locator = "Public synthetic cue"
		val rawStart = extractedText.substring(0, extractedText.indexOf(locator)).encodeUtf8().size
		val rawEnd = rawStart + locator.encodeUtf8().size
		val tokenCount = Regex("[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?")
			.findAll(extractedText)
			.count()
		val sourceHash = "sha256:${sourceXhtml.encodeUtf8().sha256().hex()}"
		val extractedTextHash = "sha256:${extractedText.encodeUtf8().sha256().hex()}"
		return decodeWhispersyncSidecar(
			"""
			{
			  "coordinateBasis": {
			    "extractor": "bindery-epub-text",
			    "extractorVersion": "1",
			    "normalization": "raw-extracted-text-offsets",
			    "unit": "utf8-byte",
			    "scope": "spine",
			    "spines": [{
			      "href": "$href",
			      "spineIndex": 2,
			      "sourceHash": "$sourceHash",
			      "extractedTextHash": "$extractedTextHash",
			      "byteLength": ${extractedText.encodeUtf8().size},
			      "tokenCount": $tokenCount
			    }]
			  },
			  "cues": [{
			    "id": 1040,
			    "audioHref": "Audio/public-synthetic.mp3",
			    "audioStart": 1,
			    "audioEnd": 2,
			    "ebookHref": "$href",
			    "spineIndex": 2,
			    "ebookStart": $rawStart,
			    "ebookEnd": $rawEnd,
			    "ebookText": "$locator"
			  }]
			}
			""".trimIndent()
		)
	}

	private fun publicSyntheticMultiSpineCanonicalSidecar(): WhispersyncSidecar {
		val firstHref = "OEBPS/Text/public-first.xhtml"
		val secondHref = "OEBPS/Text/public-second.xhtml"
		val firstSource = "<html><body><p>Public first cue</p></body></html>"
		val secondSource = "<html><body><p>Public second cue</p></body></html>"
		val firstExtracted = "Public first cue."
		val secondExtracted = "Public second cue."
		val firstLocator = "Public first cue"
		val secondLocator = "Public second cue"
		val firstStart = firstExtracted.substring(0, firstExtracted.indexOf(firstLocator)).encodeUtf8().size
		val secondStart = secondExtracted.substring(0, secondExtracted.indexOf(secondLocator)).encodeUtf8().size
		val firstTokenCount = Regex("[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?")
			.findAll(firstExtracted).count()
		val secondTokenCount = Regex("[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?")
			.findAll(secondExtracted).count()
		return decodeWhispersyncSidecar(
			"""
			{
			  "coordinateBasis": {
			    "extractor": "bindery-epub-text",
			    "extractorVersion": "1",
			    "normalization": "raw-extracted-text-offsets",
			    "unit": "utf8-byte",
			    "scope": "spine",
			    "spines": [
			      {
			        "href": "$firstHref",
			        "spineIndex": 2,
			        "sourceHash": "sha256:${firstSource.encodeUtf8().sha256().hex()}",
			        "extractedTextHash": "sha256:${firstExtracted.encodeUtf8().sha256().hex()}",
			        "byteLength": ${firstExtracted.encodeUtf8().size},
			        "tokenCount": $firstTokenCount
			      },
			      {
			        "href": "$secondHref",
			        "spineIndex": 3,
			        "sourceHash": "sha256:${secondSource.encodeUtf8().sha256().hex()}",
			        "extractedTextHash": "sha256:${secondExtracted.encodeUtf8().sha256().hex()}",
			        "byteLength": ${secondExtracted.encodeUtf8().size},
			        "tokenCount": $secondTokenCount
			      }
			    ]
			  },
			  "cues": [
			    {
			      "id": 2001,
			      "audioHref": "Audio/public-first.mp3",
			      "audioStart": 1,
			      "audioEnd": 2,
			      "ebookHref": "$firstHref",
			      "spineIndex": 2,
			      "ebookStart": $firstStart,
			      "ebookEnd": ${firstStart + firstLocator.encodeUtf8().size},
			      "ebookText": "$firstLocator"
			    },
			    {
			      "id": 2002,
			      "audioHref": "Audio/public-second.mp3",
			      "audioStart": 2,
			      "audioEnd": 3,
			      "ebookHref": "$secondHref",
			      "spineIndex": 3,
			      "ebookStart": $secondStart,
			      "ebookEnd": ${secondStart + secondLocator.encodeUtf8().size},
			      "ebookText": "$secondLocator"
			    }
			  ]
			}
			""".trimIndent()
		)
	}

	private fun descriptor(): ReaderRawTextProvenanceDescriptor =
		ReaderRawTextProvenanceDescriptor(
			id = "chapter-raw-1",
			href = "OPS/Text/chapter.xhtml",
			spineIndex = 3,
			sourceHash = "sha256:${"a".repeat(64)}",
			extractedTextHash = "sha256:${"b".repeat(64)}",
			byteLength = 144,
			tokenCount = 22
		)

	private fun openRequest(): ReaderEngineOpenRequest =
		ReaderEngineOpenRequest(
			publication = ReaderPublicationIdentity(
				bookId = "book-1",
				resourceHref = "book.epub",
				format = ReaderPublicationFormat.Epub
			),
			url = "https://appassets.androidplatform.net/reader-cache/book-1/book.epub"
		)
}
