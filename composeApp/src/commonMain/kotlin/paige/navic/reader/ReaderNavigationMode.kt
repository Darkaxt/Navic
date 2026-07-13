package paige.navic.reader

enum class ReaderNavigationMode {
	Paged,
	Scrolled
}

fun readerNavigationModeFor(settings: ReaderSettings): ReaderNavigationMode =
	when (normalizedReaderFlowMode(settings.flowMode, settings.paged)) {
		ReaderFlowScrolled,
		ReaderFlowScrolledGaps -> ReaderNavigationMode.Scrolled
		else -> ReaderNavigationMode.Paged
	}
