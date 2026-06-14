package paige.navic.reader

sealed interface ReaderEngineHostCommand {
	data class FoliateBridge(val command: ReaderBridgeCommand) : ReaderEngineHostCommand
}

sealed interface ReaderEngineHostEvent {
	data class FoliateBridge(val event: ReaderBridgeEvent) : ReaderEngineHostEvent
}

sealed interface ReaderEngineRenderer {
	data object Empty : ReaderEngineRenderer

	data class FoliatePublication(
		val publicationUrl: String,
		val title: String,
		val kind: ReaderPublicationKind,
		val mediaOverlayEnabled: Boolean,
		val externalShellCover: Boolean,
		val settings: ReaderSettings,
		val startLocator: ReaderLocator?,
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
					settings = viewState.settings,
					startLocator = viewState.startLocator,
					command = viewState.command,
					commandKey = viewState.commandKey
				)
		}
	}
}
