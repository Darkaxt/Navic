package paige.navic.androidApp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class CrashDiagnosticsSourceTest {
	@Test
	fun uncaughtExceptionHandlerLogsThrowableBeforeLaunchingCrashActivity() {
		val source = androidApplicationSource().readText()
		val logIndex = source.indexOf("Log.e(\"Application\", \"Uncaught exception\", throwable)")
		val startActivityIndex = source.indexOf("startActivity(intent)")

		assertTrue(logIndex >= 0, "Uncaught exceptions must be written to logcat before Navic exits.")
		assertTrue(startActivityIndex >= 0, "CrashActivity must still be launched for the visible crash popup.")
		assertTrue(
			logIndex < startActivityIndex,
			"The original throwable must be logged before CrashActivity starts so logcat retains the stack even if the popup is gone."
		)
	}

	@Test
	fun uncaughtExceptionHandlerPersistsCrashReportBeforeProcessExit() {
		val source = androidApplicationSource().readText()
		val writeIndex = source.indexOf("writeCrashReport(stackTrace)")
		val exitIndex = source.indexOf("exitProcess(1)")

		assertContains(source, "private fun writeCrashReport(stackTrace: String): String?")
		assertContains(source, "getExternalFilesDir(null)")
		assertContains(source, "last-crash.txt")
		assertTrue(writeIndex >= 0, "The uncaught exception handler must persist the stack trace.")
		assertTrue(exitIndex >= 0, "The crash process should still terminate after reporting.")
		assertTrue(
			writeIndex < exitIndex,
			"The crash report must be written before exitProcess(1), otherwise ADB cannot recover the stack later."
		)
	}

	private fun androidApplicationSource(): File =
		listOf(
			File("../androidApp/src/main/kotlin/paige/navic/androidApp/Application.kt"),
			File("androidApp/src/main/kotlin/paige/navic/androidApp/Application.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate androidApp Application.kt")
}
