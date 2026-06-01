package paige.navic.ui.screens.aurral

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_aurral_monitor_confirmed
import navic.composeapp.generated.resources.info_aurral_monitor_failed
import navic.composeapp.generated.resources.info_aurral_monitor_stopped
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalSnackbarState
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralRepository

@Composable
fun AurralConfirmationQueueSnackbar(
	repository: AurralRepository = koinInject()
) {
	val queue by repository.confirmationQueue.collectAsStateWithLifecycle()
	val snackbarState = LocalSnackbarState.current
	var shownKeys by remember { mutableStateOf(emptySet<String>()) }
	val item = queue.lastOrNull { queued ->
		queued.status != AurralConfirmationStatus.Pending &&
			"${queued.id}:${queued.status}:${queued.updatedAtMillis}" !in shownKeys
	}
	val confirmationMessage = when {
		item == null -> null
		item.status == AurralConfirmationStatus.Confirmed && item.expectedMonitored == false ->
			stringResource(Res.string.info_aurral_monitor_stopped)
		item.status == AurralConfirmationStatus.Confirmed ->
			stringResource(Res.string.info_aurral_monitor_confirmed)
		item.status == AurralConfirmationStatus.Failed ->
			stringResource(
				Res.string.info_aurral_monitor_failed,
				item.message ?: "Unknown error"
			)
		else -> null
	}

	LaunchedEffect(item?.id, item?.status, item?.updatedAtMillis) {
		val message = confirmationMessage ?: return@LaunchedEffect
		val shownItem = item ?: return@LaunchedEffect
		val key = "${shownItem.id}:${shownItem.status}:${shownItem.updatedAtMillis}"
		snackbarState.showSnackbar(message)
		shownKeys = shownKeys + key
	}
}
