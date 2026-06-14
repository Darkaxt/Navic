package paige.navic.reader

import paige.navic.domain.repositories.BinderyReadingProgress

fun applyReaderCoordinatorStep(
	step: ReaderCoordinatorStep,
	updateCoordinator: (ReaderCoordinator) -> Unit,
	saveProgress: (BinderyReadingProgress) -> Unit
) {
	updateCoordinator(step.coordinator)
	step.progressToSave?.let(saveProgress)
}
