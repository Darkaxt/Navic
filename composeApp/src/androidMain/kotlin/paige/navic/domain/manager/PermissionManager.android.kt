package paige.navic.domain.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

actual class PermissionManager(
	private val context: Context
) {
	private val requestMutex = Mutex()
	private var pendingContinuation: CancellableContinuation<Boolean>? = null
	private var permissionLauncher: ActivityResultLauncher<String>? = null

	fun registerLauncher(activity: ComponentActivity) {
		permissionLauncher = activity.registerForActivityResult(
			ActivityResultContracts.RequestPermission()
		) { granted ->
			val continuation = pendingContinuation
			pendingContinuation = null
			if (continuation?.isActive == true) {
				continuation.resume(granted)
			}
		}
	}

	actual fun openPermissionsSettings() {
		val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
			data = Uri.fromParts("package", context.packageName, null)
			flags = Intent.FLAG_ACTIVITY_NEW_TASK
		}
		context.startActivity(intent)
	}

	actual suspend fun requestLocalNetworkPermission(): Boolean {
		if (Build.VERSION.SDK_INT < 37) return true
		if (hasLocalNetworkPermission()) return true

		return requestMutex.withLock {
			if (hasLocalNetworkPermission()) return@withLock true
			val launcher = permissionLauncher ?: return@withLock false

			suspendCancellableCoroutine { continuation ->
				pendingContinuation = continuation
				continuation.invokeOnCancellation {
					if (pendingContinuation === continuation) {
						pendingContinuation = null
					}
				}
				try {
					launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
				} catch (_: Exception) {
					if (pendingContinuation === continuation) {
						pendingContinuation = null
					}
					if (continuation.isActive) continuation.resume(false)
				}
			}
		}
	}

	private fun hasLocalNetworkPermission(): Boolean =
		context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
			PackageManager.PERMISSION_GRANTED
}
