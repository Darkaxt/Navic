package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.BinderyReaderPublicationResolver
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.ui.navigation.Screen
import java.io.File

@Composable
actual fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (String) -> Unit,
	onError: (String) -> Unit
) {
	if (reader.kind == ReaderPublicationKind.Readaloud && reader.mediaOverlayEnabled) return

	val context = LocalContext.current
	val repository = koinInject<BinderyRepository>()
	val currentOnPublicationReady by rememberUpdatedState(onPublicationReady)
	val currentOnError by rememberUpdatedState(onError)

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.publicationUrl, reader.kind, reader.mediaOverlayEnabled) {
		val directUrl = reader.publicationUrl.takeIf {
			reader.resourceHref.isBlank() || it.isLocalReaderPublicationUrl()
		}
		if (directUrl != null) {
			currentOnPublicationReady(directUrl)
			return@LaunchedEffect
		}
		runCatching {
			BinderyReaderPublicationResolver(
				fetchResourceBytes = { path -> repository.getResourceBytes(path).getOrThrow() },
				cacheRoot = File(context.cacheDir, "reader")
			).resolve(
				ReaderPublicationResourceRequest(
					bookId = reader.bookId,
					title = reader.title,
					resourceHref = reader.resourceHref,
					sourceUrl = reader.publicationUrl,
					kind = reader.kind,
					mediaOverlayEnabled = reader.mediaOverlayEnabled
				)
			).publicationUrl
		}.fold(
			onSuccess = currentOnPublicationReady,
			onFailure = { error -> currentOnError(error.message ?: "Unable to load reader publication.") }
		)
	}
}

private fun String.isLocalReaderPublicationUrl(): Boolean =
	startsWith("file:", ignoreCase = true) ||
		startsWith("content:", ignoreCase = true)
