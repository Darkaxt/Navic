package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.ui.navigation.Screen

@Composable
actual fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (String, String?, BinderyReadingProgress?) -> Unit,
	onError: (String) -> Unit
) {
	LaunchedEffect(reader.publicationUrl) {
		onPublicationReady(reader.publicationUrl, null, null)
	}
}
