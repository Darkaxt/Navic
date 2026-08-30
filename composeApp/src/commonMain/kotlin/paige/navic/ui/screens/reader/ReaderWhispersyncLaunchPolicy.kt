package paige.navic.ui.screens.reader

import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyWordSyncReference
import paige.navic.domain.repositories.wordSyncReferenceOrNull
import paige.navic.reader.ReaderEngineCapability
import paige.navic.reader.supportsReaderEngineCapability
import paige.navic.ui.navigation.Screen

internal data class ReaderWhispersyncLaunchAttachment(
	val sidecarPath: String,
	val artifactId: String,
	val audiobookId: String?,
	val audiobookBookFileId: String,
	val audiobookTitle: String? = null,
	val wordSync: BinderyWordSyncReference? = null
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
		audiobookTitle = whispersyncAudiobookTitle.normalizedWhispersyncRouteValue(),
		wordSync = whispersyncWordSync
	)
}

internal enum class ReaderWordSyncReferenceResolutionReason(val logValue: String) {
	Resolved("resolved"),
	InvalidLaunch("identity-invalid"),
	ReferenceMissing("reference-missing"),
	BookMismatch("book-mismatch"),
	AudiobookMismatch("audiobook-mismatch"),
	ArtifactMismatch("artifact-mismatch"),
	Ambiguous("ambiguous")
}

internal data class ReaderWordSyncReferenceResolution(
	val reference: BinderyWordSyncReference?,
	val reason: ReaderWordSyncReferenceResolutionReason,
	val candidateCount: Int
)

internal fun BinderyBookSync.wordSyncReferenceResolutionForLaunch(
	bookId: String,
	attachment: ReaderWhispersyncLaunchAttachment
): ReaderWordSyncReferenceResolution {
	val expectedBookId = bookId.trim().toLongOrNull()?.takeIf { it > 0L }
	val expectedAudiobookBookFileId = attachment.audiobookBookFileId.toLongOrNull()
		?.takeIf { it > 0L }
	val expectedArtifactId = attachment.artifactId.toLongOrNull()?.takeIf { it > 0L }
	if (
		expectedBookId == null ||
		expectedAudiobookBookFileId == null ||
		expectedArtifactId == null
	) {
		return ReaderWordSyncReferenceResolution(
			reference = null,
			reason = ReaderWordSyncReferenceResolutionReason.InvalidLaunch,
			candidateCount = 0
		)
	}
	val references = syncPairs.mapNotNull { pair -> pair.wordSyncReferenceOrNull() }
	if (references.isEmpty()) {
		return ReaderWordSyncReferenceResolution(
			reference = null,
			reason = ReaderWordSyncReferenceResolutionReason.ReferenceMissing,
			candidateCount = 0
		)
	}
	val bookMatches = references.filter { reference ->
		reference.identity.bookId == expectedBookId
	}
	if (bookMatches.isEmpty()) {
		return ReaderWordSyncReferenceResolution(
			reference = null,
			reason = ReaderWordSyncReferenceResolutionReason.BookMismatch,
			candidateCount = 0
		)
	}
	val audiobookMatches = bookMatches.filter { reference ->
		reference.identity.audiobookBookFileId == expectedAudiobookBookFileId
	}
	if (audiobookMatches.isEmpty()) {
		return ReaderWordSyncReferenceResolution(
			reference = null,
			reason = ReaderWordSyncReferenceResolutionReason.AudiobookMismatch,
			candidateCount = 0
		)
	}
	val artifactMatches = audiobookMatches.filter { reference ->
		reference.identity.artifactId == expectedArtifactId
	}
	return if (artifactMatches.size == 1) {
		ReaderWordSyncReferenceResolution(
			reference = artifactMatches.single(),
			reason = ReaderWordSyncReferenceResolutionReason.Resolved,
			candidateCount = 1
		)
	} else {
		ReaderWordSyncReferenceResolution(
			reference = null,
			reason = if (artifactMatches.isEmpty()) {
				ReaderWordSyncReferenceResolutionReason.ArtifactMismatch
			} else {
				ReaderWordSyncReferenceResolutionReason.Ambiguous
			},
			candidateCount = artifactMatches.size
		)
	}
}

internal fun BinderyBookSync.wordSyncReferenceForLaunch(
	bookId: String,
	attachment: ReaderWhispersyncLaunchAttachment
): BinderyWordSyncReference? = wordSyncReferenceResolutionForLaunch(
	bookId = bookId,
	attachment = attachment
).reference

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
