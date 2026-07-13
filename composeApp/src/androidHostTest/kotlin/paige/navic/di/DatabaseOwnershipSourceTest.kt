package paige.navic.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DatabaseOwnershipSourceTest {
	@Test
	fun downloadEntityBelongsOnlyToDownloadDatabase() {
		val cacheSource = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/data/database/CacheDatabase.kt").readText()
		val downloadSource = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/data/database/DownloadDatabase.kt").readText()

		assertFalse("DownloadEntity::class" in cacheSource)
		assertFalse("abstract fun downloadDao()" in cacheSource)
		assertContains(downloadSource, "DownloadEntity::class")
		assertContains(downloadSource, "abstract fun downloadDao()")
	}

	@Test
	fun playerStateRepositoryHasOnlyKoinOwnership() {
		val repositorySource = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/PlayerStateRepository.kt"
		).readText()
		val platformSource = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt"
		).readText()

		assertFalse("fun getInstance" in repositorySource)
		assertFalse("private var instance" in repositorySource)
		assertFalse("PlayerStateRepository.getInstance" in platformSource)
		assertContains(platformSource, "PreferenceDataStoreFactory.createWithPath")
	}

	@Test
	fun cacheOwnershipMigrationRunsEagerlyAndForcesRoomValidation() {
		val databaseSource = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/di/DatabaseModule.android.kt"
		).readText()
		val commonSource = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/di/DatabaseModule.kt"
		).readText()

		assertContains(commonSource, "expect val databaseModule: Module")
		assertFalse(commonSource.contains("albumDao()"))
		assertContains(databaseSource, "single<CacheDatabase>(createdAtStart = true)")
		assertContains(databaseSource, "cacheDatabase.albumDao().getAlbumCount()")
		assertContains(databaseSource, "registerDatabaseDaos()")
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
