package paige.navic.domain.manager

actual class PermissionManager {
	actual fun openPermissionsSettings() = Unit

	actual suspend fun requestLocalNetworkPermission(): Boolean = true
}
