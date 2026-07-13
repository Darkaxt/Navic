package paige.navic.di

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

internal object CacheDatabaseMigration20To21 : Migration(20, 21) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.execute("DROP TABLE IF EXISTS DownloadEntity")
	}
}

internal object DownloadDatabaseMigration4To5 : Migration(4, 5) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.execute(
			"ALTER TABLE DownloadEntity ADD COLUMN intentGeneration INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute(
			"ALTER TABLE DownloadEntity ADD COLUMN queuedAtEpochMs INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute(
			"ALTER TABLE DownloadEntity ADD COLUMN cancelled INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute(
			"CREATE INDEX IF NOT EXISTS index_DownloadEntity_status_cancelled_queuedAtEpochMs " +
				"ON DownloadEntity(status, cancelled, queuedAtEpochMs)"
		)
	}
}

internal suspend fun migrateLegacyDownloadRegistry(
	cacheDatabasePath: String,
	downloadDao: DownloadDao,
	driver: SQLiteDriver = BundledSQLiteDriver()
) {
	val legacyRows = readLegacyDownloadRegistry(cacheDatabasePath, driver)
	val currentRows = downloadDao.getAllDownloadsList()
	downloadsMissingFromDestination(legacyRows, currentRows).forEach { row ->
		downloadDao.insertDownload(row)
	}
}

internal fun downloadsMissingFromDestination(
	legacyRows: List<DownloadEntity>,
	currentRows: List<DownloadEntity>
): List<DownloadEntity> {
	val currentSongIds = currentRows.mapTo(mutableSetOf(), DownloadEntity::songId)
	return legacyRows.filter { row -> row.songId !in currentSongIds }
}

internal fun readLegacyDownloadRegistry(
	cacheDatabasePath: String,
	driver: SQLiteDriver = BundledSQLiteDriver()
): List<DownloadEntity> {
	if (!File(cacheDatabasePath).isFile) return emptyList()
	return driver.open(cacheDatabasePath).use { connection ->
		if (!connection.hasTable("DownloadEntity")) return@use emptyList()
		connection.prepare(
			"SELECT songId, status, progress, filePath FROM DownloadEntity ORDER BY songId"
		).use { statement ->
			buildList {
				while (statement.step()) {
					add(
						DownloadEntity(
							songId = statement.getText(0),
							status = DownloadStatus.valueOf(statement.getText(1)),
							progress = statement.getDouble(2).toFloat(),
							filePath = if (statement.isNull(3)) null else statement.getText(3)
						)
					)
				}
			}
		}
	}
}

private fun SQLiteConnection.execute(sql: String) {
	prepare(sql).use { statement -> statement.step() }
}

private fun SQLiteConnection.hasTable(name: String): Boolean =
	prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").use { statement ->
		statement.bindText(1, name)
		statement.step()
	}
