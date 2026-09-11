package paige.navic.reader

sealed interface ReaderEngineHostCommand {
	data class FoliateBridge(val command: ReaderBridgeCommand) : ReaderEngineHostCommand
	data class FoliateBridgeSequence(
		val commands: List<ReaderBridgeCommand>
	) : ReaderEngineHostCommand {
		init {
			require(commands.size > 1)
		}
	}
}

sealed interface ReaderEngineHostEvent {
	data class FoliateBridge(val event: ReaderBridgeEvent) : ReaderEngineHostEvent
	data class SettingsPresentationCommitted(val snapshotKey: Int) : ReaderEngineHostEvent
	data class NativeWhispersyncCueMapSeekRequested(
		val sourceOrdinal: Int,
		val revisionDigest: String,
		val presentationGeneration: Long,
		val destinationCommitIdentity: ReaderDestinationCommitIdentity
	) : ReaderEngineHostEvent
	data class NativeWhispersyncCueMapHoldOutcome(
		val sourceOrdinal: Int,
		val revisionDigest: String,
		val presentationGeneration: Long,
		val outcome: ReaderWhispersyncCueMapHoldOutcome
	) : ReaderEngineHostEvent
}

// Payload-free observations for WordSync logging, not command or presentation authority.
internal data class ReaderWordSyncCommandDiagnostic(val published: Boolean) {
	val commandLogValue: String
		get() = if (published) "update-overlay" else "none"
}

internal sealed interface ReaderWordSyncOverlayDiagnostic {
	data class Active(val anchorPresent: Boolean) : ReaderWordSyncOverlayDiagnostic
	data class Inactive(val reason: ReaderWordSyncOverlayInactiveReason) : ReaderWordSyncOverlayDiagnostic
}

internal enum class ReaderWordSyncOverlayInactiveReason(val logValue: String) {
	AnimationOutsideVisiblePage("animation-outside-visible-page"),
	AnimationPaintRejected("animation-paint-rejected"),
	UserRelocationActive("user-relocation-active"),
	OutsideVisiblePage("outside-visible-page"),
	PaintRejected("paint-rejected"),
	InvalidCoordinateMode("invalid-coordinate-mode"),
	StaleProgressRequest("stale-progress-request"),
	ProgressOutsideVisiblePage("progress-outside-visible-page"),
	ProgressPaintRejected("progress-paint-rejected"),
	DocumentLoaded("document-loaded"),
	AnchorRejected("anchor-rejected"),
	Absent("absent"),
	Other("other")
}

sealed interface ReaderEngineRenderer {
	data object Empty : ReaderEngineRenderer

	data class FoliatePublication(
		val publicationUrl: String,
		val title: String,
		val kind: ReaderPublicationKind,
		val mediaOverlayEnabled: Boolean,
		val externalShellCover: Boolean,
		val suppressWebShellCover: Boolean = false,
		val nativeShellCoverTint: String? = null,
		val settings: ReaderSettings,
		val startLocator: ReaderLocator?,
		val rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor>,
		val command: ReaderEngineHostCommand?,
		val commandKey: Long
	) : ReaderEngineRenderer {
		companion object {
			fun from(viewState: ReaderEngineViewState.WebViewPublication): FoliatePublication =
				FoliatePublication(
					publicationUrl = viewState.publicationUrl,
					title = viewState.title,
					kind = viewState.kind,
					mediaOverlayEnabled = viewState.mediaOverlayEnabled,
					externalShellCover = viewState.externalShellCover,
					suppressWebShellCover = viewState.suppressWebShellCover,
					nativeShellCoverTint = viewState.nativeShellCoverTint,
					settings = viewState.settings,
					startLocator = viewState.startLocator,
					rawTextProvenanceDescriptors = viewState.rawTextProvenanceDescriptors,
					command = viewState.command,
					commandKey = viewState.commandKey
				)
		}
	}
}
