package paige.navic.ui.components.sheets

import androidx.compose.runtime.Composable

interface UpdateInstaller {
	val canInstallApk: Boolean
	suspend fun installApk(
		updateUrl: String,
		expectedSha256Digest: String?,
		onProgress: (Float?) -> Unit = {}
	): Result<Unit>
}

@Composable
expect fun rememberUpdateInstaller(): UpdateInstaller
