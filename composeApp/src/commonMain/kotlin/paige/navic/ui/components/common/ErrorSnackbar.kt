package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import paige.navic.ui.components.snackbars.ErrorSnackbar as NavicErrorSnackbar

@Composable
fun ErrorSnackbar(
	error: Throwable?,
	onClearError: () -> Unit
) {
	NavicErrorSnackbar(
		error = error,
		onClearError = onClearError
	)
}
