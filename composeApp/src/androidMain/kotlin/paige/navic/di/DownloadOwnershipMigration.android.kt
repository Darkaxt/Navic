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

internal object CacheDatabaseMigration21To22 : Migration(21, 22) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.execute(
			"ALTER TABLE SyncActionEntity ADD COLUMN createdAtEpochMs INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute(
			"ALTER TABLE SyncActionEntity ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute(
			"ALTER TABLE SyncActionEntity ADD COLUMN nextAttemptAtEpochMs INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute("ALTER TABLE SyncActionEntity ADD COLUMN lastError TEXT")
		connection.execute(
			"ALTER TABLE SyncActionEntity ADD COLUMN deadLettered INTEGER NOT NULL DEFAULT 0"
		)
		connection.execute(
			"CREATE INDEX IF NOT EXISTS index_SyncActionEntity_deadLettered_nextAttemptAtEpochMs_id " +
				"ON SyncActionEntity(deadLettered, nextAttemptAtEpochMs, id)"
		)
	}
}

internal object CacheDatabaseMigration22To23 : Migration(22, 23) {
	override suspend fun migrate(connection: SQLiteConnection) {
		connection.execute(
			"CREATE TABLE AlbumEntity_new (" +
				"albumId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, artistName TEXT NOT NULL, " +
				"artistId TEXT, year INTEGER, coverArtId TEXT NOT NULL, genre TEXT, genres TEXT NOT NULL, " +
				"songCount INTEGER NOT NULL, duration INTEGER, createdAt INTEGER NOT NULL, starredAt INTEGER, " +
				"lastPlayedAt INTEGER, playCount INTEGER NOT NULL, userRating INTEGER, version TEXT, musicBrainzId TEXT)"
		)
		connection.execute(
			"INSERT INTO AlbumEntity_new SELECT albumId, name, artistName, " +
				"CASE WHEN lower(trim(artistId)) = 'unknown artist' THEN NULL ELSE artistId END, " +
				"year, coverArtId, genre, genres, songCount, duration, createdAt, starredAt, lastPlayedAt, " +
				"playCount, userRating, version, musicBrainzId FROM AlbumEntity"
		)
		connection.execute("DROP TABLE AlbumEntity")
		connection.execute("ALTER TABLE AlbumEntity_new RENAME TO AlbumEntity")

		connection.execute(
			"CREATE TABLE SongEntity_new (" +
				"songId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artistName TEXT NOT NULL, artistId TEXT, " +
				"albumTitle TEXT, belongsToAlbumId TEXT, parentId TEXT, comment TEXT, trackNumber INTEGER, " +
				"discNumber INTEGER, isrc TEXT NOT NULL, year INTEGER, genre TEXT, genres TEXT NOT NULL, " +
				"moods TEXT NOT NULL, duration INTEGER NOT NULL, bpm INTEGER, contributors TEXT NOT NULL, " +
				"playCount INTEGER NOT NULL, userRating INTEGER, averageRating REAL, bitRate INTEGER, " +
				"bitDepth INTEGER, sampleRate INTEGER, audioChannelCount INTEGER, replayGain TEXT, " +
				"fileSize INTEGER NOT NULL, fileExtension TEXT NOT NULL, mimeType TEXT NOT NULL, filePath TEXT, " +
				"starredAt INTEGER, coverArtId TEXT, musicBrainzId TEXT, explicitStatus INTEGER NOT NULL)"
		)
		connection.execute(
			"INSERT INTO SongEntity_new SELECT songId, title, artistName, " +
				"CASE WHEN lower(trim(artistId)) = 'unknown artist' THEN NULL ELSE artistId END, " +
				"albumTitle, belongsToAlbumId, parentId, comment, trackNumber, discNumber, isrc, year, genre, " +
				"genres, moods, duration, bpm, contributors, playCount, userRating, averageRating, bitRate, " +
				"bitDepth, sampleRate, audioChannelCount, replayGain, fileSize, fileExtension, mimeType, " +
				"filePath, starredAt, coverArtId, musicBrainzId, explicitStatus FROM SongEntity"
		)
		connection.execute("DROP TABLE SongEntity")
		connection.execute("ALTER TABLE SongEntity_new RENAME TO SongEntity")
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
