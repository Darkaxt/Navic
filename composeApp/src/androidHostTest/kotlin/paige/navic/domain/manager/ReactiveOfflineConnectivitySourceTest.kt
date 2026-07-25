package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReactiveOfflineConnectivitySourceTest {
	@Test
	fun commonContractSeparatesRawNetworkFromEffectiveOnlineState() {
		val source = commonMainFile("domain/manager/ConnectivityManager.kt").readText()

		assertContains(source, "val isNetworkAvailable: StateFlow<Boolean>")
		assertContains(source, "val isOnline: StateFlow<Boolean>")
	}

	@Test
	fun platformManagersObserveEffectiveOfflineMode() {
		val android = androidMainFile("domain/manager/ConnectivityManager.android.kt").readText()
		val ios = iosMainFile("domain/manager/ConnectivityManager.ios.kt").readText()

		listOf(android, ios).forEach { source ->
			assertContains(source, "OfflineModeCoordinator")
			assertContains(source, "combine(networkStatus, offlineModeCoordinator.state)")
			assertContains(source, "actual val isNetworkAvailable")
			assertContains(source, "isOnlineForOfflineMode(")
		}
	}

	@Test
	fun androidTracksTheDefaultNetworkAcrossRoamingTransitions() {
		val source = androidMainFile("domain/manager/ConnectivityManager.android.kt").readText()
		val onLostBody = source.substringAfter("override fun onLost")
			.substringBefore("\n\t\t\t}")

		assertContains(source, "registerDefaultNetworkCallback(callback)")
		assertContains(onLostBody, "trySend(currentNetworkStatus())")
		assertFalse("trySend(NetworkStatus())" in onLostBody)
	}

	@Test
	fun settingsNoLongerRequireRestart() {
		val strings = composeResourceFile("values/strings.xml").readText()
		val offlineModeSubtitle = strings.lineSequence()
			.single { "name=\"subtitle_offline_mode\"" in it }

		assertFalse("restart" in offlineModeSubtitle.lowercase())
	}

	private fun commonMainFile(relativePath: String): File =
		sourceFile("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")

	private fun androidMainFile(relativePath: String): File =
		sourceFile("composeApp/src/androidMain/kotlin/paige/navic/$relativePath")

	private fun iosMainFile(relativePath: String): File =
		sourceFile("composeApp/src/iosMain/kotlin/paige/navic/$relativePath")

	private fun composeResourceFile(relativePath: String): File =
		sourceFile("composeApp/src/commonMain/composeResources/$relativePath")

	private fun sourceFile(relativePath: String): File = listOf(
		File(relativePath.removePrefix("composeApp/")),
		File(relativePath)
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")
}
