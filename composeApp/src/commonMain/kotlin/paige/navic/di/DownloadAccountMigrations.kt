package paige.navic.di

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

internal object DownloadDatabaseMigration4To5 : Migration(4, 5) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.executeDownloadMigration("ALTER TABLE DownloadEntity ADD COLUMN intentGeneration INTEGER NOT NULL DEFAULT 0")
		connection.executeDownloadMigration("ALTER TABLE DownloadEntity ADD COLUMN queuedAtEpochMs INTEGER NOT NULL DEFAULT 0")
		connection.executeDownloadMigration("ALTER TABLE DownloadEntity ADD COLUMN cancelled INTEGER NOT NULL DEFAULT 0")
		connection.executeDownloadMigration(
			"CREATE INDEX IF NOT EXISTS index_DownloadEntity_status_cancelled_queuedAtEpochMs " +
				"ON DownloadEntity(status, cancelled, queuedAtEpochMs)"
		)
	}
}

/** Old rows have no per-file account evidence. Bind only to the persisted upgrade snapshot. */
internal class DownloadDatabaseMigration5To6(private val legacyOwnerId: String?) : Migration(5, 6) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.executeDownloadMigration(
			"CREATE TABLE DownloadEntity_v6 (songId TEXT NOT NULL, status TEXT NOT NULL, " +
				"progress REAL NOT NULL, filePath TEXT, intentGeneration INTEGER NOT NULL, " +
				"queuedAtEpochMs INTEGER NOT NULL, cancelled INTEGER NOT NULL, ownerId TEXT NOT NULL, " +
				"PRIMARY KEY(ownerId, songId))"
		)
		connection.prepare(
			"INSERT INTO DownloadEntity_v6 SELECT songId, status, progress, filePath, " +
				"intentGeneration, queuedAtEpochMs, cancelled, ? FROM DownloadEntity"
		).use {
			it.bindText(1, legacyOwnerId.orEmpty())
			it.step()
		}
		connection.executeDownloadMigration("DROP TABLE DownloadEntity")
		connection.executeDownloadMigration("ALTER TABLE DownloadEntity_v6 RENAME TO DownloadEntity")
		connection.executeDownloadMigration(
			"CREATE INDEX index_DownloadEntity_ownerId_status_cancelled_queuedAtEpochMs " +
				"ON DownloadEntity(ownerId, status, cancelled, queuedAtEpochMs)"
		)
	}
}

private fun SQLiteConnection.executeDownloadMigration(sql: String) {
	prepare(sql).use { it.step() }
}
