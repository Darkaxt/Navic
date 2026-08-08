package paige.navic.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidDatabaseMigrationPolicySourceTest {
	@Test
	fun androidDatabasesFailClosedWhenAnUpgradeMigrationIsMissing() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/di/DatabaseModule.android.kt"
		).readText()

		assertEquals(2, source.split("Room.databaseBuilder<").size - 1)
		assertFalse("fallbackToDestructiveMigration" in source)
		assertTrue(".addMigrations(DownloadDatabaseMigration4To5)" in source)
		listOf(
			"CacheDatabaseMigration20To21",
			"CacheDatabaseMigration21To22",
			"CacheDatabaseMigration22To23",
			"CacheDatabaseMigration23To24"
		).forEach { migration ->
			assertTrue(migration in source, "$migration must remain registered")
		}
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
