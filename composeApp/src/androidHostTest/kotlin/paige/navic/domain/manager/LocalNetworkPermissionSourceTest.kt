package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkPermissionSourceTest {
	@Test
	fun activityRegistersPermissionLauncherBeforeComposeStarts() {
		val source = projectFile("androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt").readText()

		val registerIndex = source.indexOf("permissionManager.registerLauncher(this)")
		val contentIndex = source.indexOf("setContent {")
		assertTrue(registerIndex >= 0)
		assertTrue(registerIndex < contentIndex)
	}

	@Test
	fun loginAwaitsPermissionAndReturnsOnDenial() {
		val source = commonMainFile("ui/screens/login/pages/Content.kt").readText()
		val permissionIndex = source.indexOf("requestLocalNetworkPermission()")
		val loginIndex = source.indexOf("viewModel.login()")

		assertTrue(permissionIndex >= 0)
		assertTrue(permissionIndex < loginIndex)
		assertContains(source, "if (!permissionManager.requestLocalNetworkPermission())")
		assertContains(source, "return@launch")
	}

	@Test
	fun androidManagerSerializesRequestsAndFailsClosed() {
		val source = androidMainFile("domain/manager/PermissionManager.android.kt").readText()

		assertContains(source, "if (Build.VERSION.SDK_INT < 37) return true")
		assertContains(source, "requestMutex.withLock")
		assertContains(source, "permissionLauncher ?: return@withLock false")
		assertContains(source, "pendingContinuation === continuation")
		assertFalse("permissionLauncher!!" in source)
		assertFalse("withTimeout" in source)
	}

	@Test
	fun platformContextNoLongerOwnsPermissionRequests() {
		val common = commonMainFile("util/core/PlatformContext.kt").readText()
		val android = androidMainFile("util/core/PlatformContext.android.kt").readText()

		assertFalse("checkLocalNetworkPermission" in common)
		assertFalse("requestPermissions" in android)
	}

	@Test
	fun platformModuleRegistersPermissionManager() {
		val source = androidMainFile("di/PlatformModule.android.kt").readText()

		assertContains(source, "singleOf(::PermissionManager)")
	}

	private fun commonMainFile(relativePath: String): File = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")
	private fun androidMainFile(relativePath: String): File = sourceFile("composeApp/src/androidMain/kotlin/paige/navic/$relativePath")

	private fun projectFile(relativePath: String): File = listOf(
		File(relativePath),
		File("../$relativePath")
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")

	private fun sourceFile(relativePath: String): File = listOf(
		File(relativePath.removePrefix("composeApp/")),
		File(relativePath)
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")
}
