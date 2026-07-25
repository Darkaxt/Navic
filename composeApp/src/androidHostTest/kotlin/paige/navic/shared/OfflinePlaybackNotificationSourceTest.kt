package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class OfflinePlaybackNotificationSourceTest {
	@Test
	fun appClaimsOneConnectionLostSnackbarPerOutage() {
		val app = commonSource("App.kt").readText()
		val strings = commonResource("values/strings.xml").readText()

		assertContains(app, "navidromeAvailabilityManager.state.collect")
		assertContains(app, "claimConnectionLostNotice()")
		assertContains(app, "snackBarManager.notifyConnectionLost()")
		assertContains(
			strings,
			"<string name=\"notice_connection_lost_offline\">Connection lost - Switching to Offline mode</string>"
		)
	}

	@Test
	fun mediaNotificationShowsOfflineSubtextAndRefreshesOnStateChanges() {
		val provider = androidSharedSource("OfflineAwareMediaNotificationProvider.android.kt").readText()
		val service = androidSharedSource("MediaPlayer.android.kt").readText()

		assertContains(provider, "NotificationCompat.Builder(context, base.notification)")
		assertContains(provider, ".setSubText(CONNECTION_LOST_OFFLINE_MESSAGE)")
		assertContains(provider, "callback.onNotificationChanged(decorate(base))")
		assertContains(service, "OfflineAwareMediaNotificationProvider(")
		assertContains(service, "navidromeAvailabilityManager.state.collectLatest")
		assertContains(service, "notificationProvider.setConnectionLost(")
	}

	private fun commonSource(relativePath: String): File = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")

	private fun androidSharedSource(fileName: String): File =
		sourceFile("composeApp/src/androidMain/kotlin/paige/navic/shared/$fileName")

	private fun commonResource(relativePath: String): File =
		sourceFile("composeApp/src/commonMain/composeResources/$relativePath")

	private fun sourceFile(relativePath: String): File = listOf(
		File(relativePath.removePrefix("composeApp/")),
		File(relativePath)
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")
}
