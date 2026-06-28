package paige.navic.ui.screens.reader

import paige.navic.ui.navigation.Screen

internal data class ReaderWhispersyncLaunchAttachment(
	val sidecarPath: String,
	val artifactId: String,
	val audiobookId: String?,
	val audiobookBookFileId: String,
	val audiobookTitle: String? = null
)

internal fun Screen.Reader.whispersyncLaunchAttachment(): ReaderWhispersyncLaunchAttachment? {
	val sidecarPath = whispersyncSidecarUrl.normalizedWhispersyncRouteValue() ?: return null
	val artifactId = whispersyncArtifactId.normalizedWhispersyncRouteValue() ?: return null
	val audiobookId = whispersyncAudiobookId.normalizedWhispersyncRouteValue()
	val audiobookBookFileId = whispersyncAudiobookBookFileId.normalizedWhispersyncRouteValue() ?: return null
	return ReaderWhispersyncLaunchAttachment(
		sidecarPath = sidecarPath,
		artifactId = artifactId,
		audiobookId = audiobookId,
		audiobookBookFileId = audiobookBookFileId,
		audiobookTitle = whispersyncAudiobookTitle.normalizedWhispersyncRouteValue()
	)
}

private fun String?.normalizedWhispersyncRouteValue(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }
