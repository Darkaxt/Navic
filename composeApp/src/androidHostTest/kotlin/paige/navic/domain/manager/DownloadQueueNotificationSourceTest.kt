package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class DownloadQueueNotificationSourceTest {
	@Test
	fun androidManifestRequestsNotificationPermissionForDownloadQueueNotifications() {
		val manifest = sourceFile("androidApp/src/main/AndroidManifest.xml").readText()

		assertContains(manifest, "android.permission.POST_NOTIFICATIONS")
	}

	@Test
	fun mainActivityRequestsNotificationPermissionFromActivityContext() {
		val source = sourceFile("androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt").readText()

		assertContains(source, "Manifest.permission.POST_NOTIFICATIONS")
		assertContains(source, "requestPermissions(")
		assertContains(source, "Build.VERSION_CODES.TIRAMISU")
	}

	@Test
	fun platformModulesRegisterQueueNotificationManager() {
		val androidModule = sourceFile("composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt").readText()
		val iosModule = sourceFile("composeApp/src/iosMain/kotlin/paige/navic/di/PlatformModule.ios.kt").readText()
		val managerModule = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt").readText()

		assertContains(androidModule, "singleOf(::QueueNotificationManager)")
		assertContains(iosModule, "singleOf(::QueueNotificationManager)")
		assertContains(managerModule, "DownloadQueueNotificationCoordinator")
		assertContains(managerModule, "start()")
	}

	@Test
	fun androidQueueNotificationManagerUsesLowPriorityProgressNotification() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/domain/manager/QueueNotificationManager.android.kt"
		).readText()

		assertContains(source, "NotificationChannel(")
		assertContains(source, "IMPORTANCE_LOW")
		assertContains(source, "NotificationCompat.Builder")
		assertContains(source, "setOnlyAlertOnce(true)")
		assertContains(source, "setOngoing(true)")
		assertContains(source, "setProgress(100,")
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
