package paige.navic.ui.components.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExternalPowerStateSourceTest {
	@Test
	fun commonSourceDeclaresNullableComposableExpectApi() {
		val source = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.kt"
		).readText()

		assertContains(source, "@Composable")
		assertContains(source, "expect fun rememberExternalPowerConnected(): Boolean?")
	}

	@Test
	fun androidSourceObservesBatteryChangesAndUnregistersItsReceiver() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.android.kt"
		).readText()

		assertContains(source, "DisposableEffect(context)")
		assertContains(source, "var receiverRegistered = false")
		assertContains(source, "receiverRegistered = true")
		assertContains(source, "if (receiverRegistered)")
		assertContains(source, "Intent.ACTION_BATTERY_CHANGED")
		assertContains(source, "externalPowerConnected = null")
		assertContains(
			source,
			"Logger.w(\"ExternalPowerState\", \"Unable to register battery state receiver\", error)"
		)
		assertContains(
			source,
			"Logger.w(\"ExternalPowerState\", \"Unable to unregister battery state receiver\", error)"
		)
		assertContains(source, "context.unregisterReceiver(receiver)")
		assertEquals(
			2,
			Regex(Regex.escape("catch (error: RuntimeException)")).findAll(source).count()
		)
	}

	@Test
	fun iosSourceReturnsUnknownWithoutNativePowerObservation() {
		val source = sourceFile(
			"composeApp/src/iosMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.ios.kt"
		).readText()

		assertContains(source, "actual fun rememberExternalPowerConnected(): Boolean? = null")
		listOf(
			"UIDevice",
			"NSNotificationCenter",
			"batteryMonitoringEnabled",
			"UIDeviceBatteryStateDidChangeNotification",
			"platform.UIKit",
			"addObserverForName"
		).forEach { forbiddenSymbol ->
			assertFalse(
				source.contains(forbiddenSymbol),
				"iOS must not observe external power through $forbiddenSymbol."
			)
		}
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path"),
		File(path.removePrefix("composeApp/"))
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
