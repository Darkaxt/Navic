package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderEngineCapability
import paige.navic.reader.supportsReaderEngineCapability
import paige.navic.ui.navigation.Screen

internal data class ReaderWhispersyncLaunchAttachment(
	val sidecarPath: String,
	val artifactId: String,
	val audiobookId: String?,
	val audiobookBookFileId: String,
	val audiobookTitle: String? = null
)

internal fun Screen.Reader.whispersyncLaunchAttachment(): ReaderWhispersyncLaunchAttachment? {
	if (!publicationFormat.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) return null
	val sidecarPath = whispersyncSidecarUrl.normalizedWhispersyncRouteValue() ?: return null
	val artifactId = whispersyncArtifactId.normalizedWhispersyncRouteValue()
		?: sidecarPath.derivedWhispersyncArtifactId()
		?: return null
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

private fun String.derivedWhispersyncArtifactId(): String? {
	val path = substringBefore('#')
		.substringBefore('?')
		.trim()
		.trimEnd('/')
	return path.substringAfterLast('/', missingDelimiterValue = path)
		.trim()
		.takeIf { it.isNotEmpty() }
}
