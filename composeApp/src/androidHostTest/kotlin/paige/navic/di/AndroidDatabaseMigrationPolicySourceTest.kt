package paige.navic.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidDatabaseMigrationPolicySourceTest {
	@Test
	fun androidDatabasesFailClosedWhenAnUpgradeMigrationIsMissing() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt"
		).readText()

		assertEquals(2, "databaseBuilder<".toRegex().findAll(source).count())
		assertFalse("fallbackToDestructiveMigration" in source)
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
