package paige.navic.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AndroidKeystoreCredentialStoreSourceTest {
	@Test
	fun androidStoreUsesKeystoreAesGcmAndVerifiesEncryptedPersistence() {
		val source = File(androidSourceRoot(), "domain/manager/AndroidKeystoreCredentialStore.kt").readText()

		assertContains(source, "AndroidKeyStore")
		assertContains(source, "AES/GCM/NoPadding")
		assertContains(source, "KeyProperties.BLOCK_MODE_GCM")
		assertContains(source, "KeyProperties.ENCRYPTION_PADDING_NONE")
		assertContains(source, "cipher.iv")
		assertContains(source, ".commit()")
		assertContains(source, "get(key) == value")
		assertFalse("putString(key, value)" in source)
	}

	@Test
	fun platformDiUsesSecureAndroidStoreAndCompatibilityIosStore() {
		val androidModule = File(androidSourceRoot(), "di/PlatformModule.android.kt").readText()
		val iosModule = File(iosSourceRoot(), "di/PlatformModule.ios.kt").readText()
		val managerModule = File(commonSourceRoot(), "di/ManagerModule.kt").readText()

		assertContains(androidModule, "AndroidKeystoreCredentialStore(androidApplication())")
		assertContains(iosModule, "SettingsCredentialStore(get())")
		assertContains(managerModule, "PreferenceManager(get(), get())")
	}

	private fun androidSourceRoot(): File = sourceRoot("androidMain")
	private fun iosSourceRoot(): File = sourceRoot("iosMain")
	private fun commonSourceRoot(): File = sourceRoot("commonMain")

	private fun sourceRoot(sourceSet: String): File = listOf(
		File("src/$sourceSet/kotlin/paige/navic"),
		File("composeApp/src/$sourceSet/kotlin/paige/navic")
	).firstOrNull(File::isDirectory)
		?: error("Could not find $sourceSet source root")
}
