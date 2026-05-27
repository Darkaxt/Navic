package paige.navic.ui.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object UnsupportedUpdateInstaller : UpdateInstaller {
	override val canInstallApk = false

	override suspend fun installApk(updateUrl: String): Result<Unit> =
		Result.failure(UnsupportedOperationException("APK install is only available on Android."))
}

@Composable
actual fun rememberUpdateInstaller(): UpdateInstaller =
	remember { UnsupportedUpdateInstaller }
