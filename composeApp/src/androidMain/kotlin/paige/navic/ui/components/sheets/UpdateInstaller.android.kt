package paige.navic.ui.components.sheets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

private class AndroidUpdateInstaller(
	context: Context
) : UpdateInstaller {
	private val appContext = context.applicationContext

	override val canInstallApk = true

	override suspend fun installApk(updateUrl: String): Result<Unit> = runCatching {
		val apkFile = withContext(Dispatchers.IO) {
			downloadApk(updateUrl)
		}
		withContext(Dispatchers.Main) {
			launchPackageInstaller(apkFile)
		}
	}

	private fun downloadApk(updateUrl: String): File {
		val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
		val target = File(updatesDir, "Navic-update.apk")
		val connection = URL(updateUrl).openConnection().apply {
			connectTimeout = 15_000
			readTimeout = 60_000
			setRequestProperty("Accept", "$APK_CONTENT_TYPE,*/*")
		}

		connection.getInputStream().use { input ->
			target.outputStream().use { output ->
				input.copyTo(output)
			}
		}
		require(target.length() > 0L) { "Downloaded update APK is empty." }
		return target
	}

	private fun launchPackageInstaller(apkFile: File) {
		val apkUri = FileProvider.getUriForFile(
			appContext,
			"${appContext.packageName}.fileprovider",
			apkFile
		)
		val installIntent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(apkUri, APK_CONTENT_TYPE)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		appContext.startActivity(installIntent)
	}
}

@Composable
actual fun rememberUpdateInstaller(): UpdateInstaller {
	val context = LocalContext.current
	return remember(context) {
		AndroidUpdateInstaller(context)
	}
}
