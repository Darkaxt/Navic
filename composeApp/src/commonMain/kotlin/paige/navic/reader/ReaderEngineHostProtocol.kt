package paige.navic.reader

sealed interface ReaderEngineHostCommand {
	data class FoliateBridge(val command: ReaderBridgeCommand) : ReaderEngineHostCommand
}

sealed interface ReaderEngineHostEvent {
	data class FoliateBridge(val event: ReaderBridgeEvent) : ReaderEngineHostEvent
}
