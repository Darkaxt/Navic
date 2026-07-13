package paige.navic.ui.screens.reader

import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.reader.ReaderEngineOpenRequest
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderStartLocatorCandidate
import paige.navic.reader.ReaderStartLocatorSource
import paige.navic.reader.resolveReaderStartLocator
import paige.navic.reader.toReaderStartLocatorForReader
import paige.navic.ui.navigation.Screen

internal fun Screen.Reader.toReaderEngineOpenRequest(
	publicationUrl: String,
	shellCoverUrl: String?,
	settings: ReaderSettings,
	shellCoverTint: String? = null,
	savedProgress: BinderyReadingProgress? = null,
	localProgress: BinderyReadingProgress? = null,
	localStartLocator: ReaderLocator? = null
): ReaderEngineOpenRequest {
	val hasShellCover = !skipNativeShellCover && !shellCoverUrl.isNullOrBlank()
	val routeStartLocator = ReaderLocator(
		cfi = startCfi,
		href = startHref.normalizedReaderStartHref(),
		progress = startProgress?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)
	).takeIf { locator -> locator.cfi != null || locator.href != null || locator.progress != null }
	val savedStartLocator = savedProgress?.toReaderStartLocatorForReader(
		bookId = bookId,
		resourceHref = resourceHref,
		kind = kind
	)
	val localProgressStartLocator = localProgress?.toReaderStartLocatorForReader(
		bookId = bookId,
		resourceHref = resourceHref,
		kind = kind
	)
	val startLocatorDecision = resolveReaderStartLocator(
		remoteCandidate = savedStartLocator?.let { locator ->
			ReaderStartLocatorCandidate(
				source = ReaderStartLocatorSource.Remote,
				locator = locator,
				updatedAt = savedProgress.updatedAt
			)
		},
		localCandidate = (localProgressStartLocator ?: localStartLocator)?.let { locator ->
			ReaderStartLocatorCandidate(
				source = ReaderStartLocatorSource.Local,
				locator = locator,
				updatedAt = localProgress?.updatedAt
			)
		}
	)
	val fallbackStartLocator = startLocatorDecision.selectedLocator?.normalizedReaderStartLocator()
	return ReaderEngineOpenRequest(
		publication = ReaderPublicationIdentity(
			bookId = bookId,
			title = title,
			resourceHref = resourceHref,
			kind = kind,
			format = publicationFormat
		),
		url = publicationUrl,
		mediaOverlayEnabled = mediaOverlayEnabled,
		externalShellCover = hasShellCover,
		suppressWebShellCover = skipNativeShellCover,
		startLocator = routeStartLocator ?: fallbackStartLocator,
		settings = settings,
		nativeShellCoverUrl = shellCoverUrl.takeIf { hasShellCover },
		nativeShellCoverTint = shellCoverTint,
		canReturnToShellCover = hasShellCover,
		startLocatorConflict = startLocatorDecision.conflict.takeIf { routeStartLocator == null }
	)
}

private fun ReaderLocator.normalizedReaderStartLocator(): ReaderLocator =
	copy(href = href.normalizedReaderStartHref())

private fun String?.normalizedReaderStartHref(): String? =
	this
		?.trim()
		?.replace('\\', '/')
		?.takeIf { it.isNotEmpty() }
