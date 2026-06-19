package paige.navic.ui.screens.reader

import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.reader.ReaderEngineOpenRequest
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderSettings
import paige.navic.reader.bestReaderStartLocator
import paige.navic.reader.toReaderStartLocatorForReader
import paige.navic.ui.navigation.Screen

internal fun Screen.Reader.toReaderEngineOpenRequest(
	publicationUrl: String,
	shellCoverUrl: String?,
	settings: ReaderSettings,
	savedProgress: BinderyReadingProgress? = null
): ReaderEngineOpenRequest {
	val hasShellCover = !shellCoverUrl.isNullOrBlank()
	val routeStartLocator = ReaderLocator(
		cfi = startCfi,
		href = startHref,
		progress = startProgress?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)
	).takeIf { locator -> locator.cfi != null || locator.href != null || locator.progress != null }
	val savedStartLocator = savedProgress?.toReaderStartLocatorForReader(
		bookId = bookId,
		resourceHref = resourceHref,
		kind = kind
	)
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
		startLocator = bestReaderStartLocator(
			remoteStartLocator = routeStartLocator,
			localStartLocator = savedStartLocator
		),
		settings = settings,
		nativeShellCoverUrl = shellCoverUrl,
		canReturnToShellCover = hasShellCover
	)
}
