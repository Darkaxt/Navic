package paige.navic.di

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

class DownloadOwnershipMigrationTest {
	@Test
	fun readsLegacyDownloadRowsFromARealCacheDatabaseFixture() {
		val file = temporaryDatabaseFile()
		val driver = JdbcSQLiteDriver()
		driver.open(file.absolutePath).use { connection ->
			connection.execute(
				"CREATE TABLE DownloadEntity (" +
					"songId TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, " +
					"progress REAL NOT NULL, filePath TEXT)"
			)
			connection.execute(
				"INSERT INTO DownloadEntity VALUES " +
					"('cached-song', 'DOWNLOADED', 1.0, '/music/cached.flac'), " +
					"('queued-song', 'QUEUED', 0.25, NULL)"
			)
		}

		val rows = readLegacyDownloadRegistry(file.absolutePath, driver)

		assertEquals(
			listOf(
				DownloadEntity("cached-song", DownloadStatus.DOWNLOADED, 1f, "/music/cached.flac"),
				DownloadEntity("queued-song", DownloadStatus.QUEUED, 0.25f, null)
			),
			rows
		)
		file.delete()
	}

	@Test
	fun currentDownloadDatabaseRowsWinOverLegacyRows() {
		val legacy = listOf(
			DownloadEntity("same", DownloadStatus.FAILED, 0f, null),
			DownloadEntity("legacy-only", DownloadStatus.DOWNLOADED, 1f, "/legacy.flac")
		)
		val current = listOf(
			DownloadEntity("same", DownloadStatus.DOWNLOADED, 1f, "/current.flac")
		)

		assertEquals(
			listOf(legacy[1]),
			downloadsMissingFromDestination(legacy, current)
		)
	}

	@Test
	fun cacheMigrationDropsOnlyTheDuplicateDownloadTable() {
		runBlocking {
			val file = temporaryDatabaseFile()
			val driver = JdbcSQLiteDriver()
			driver.open(file.absolutePath).use { connection ->
				connection.execute("CREATE TABLE DownloadEntity (songId TEXT NOT NULL PRIMARY KEY)")
				connection.execute("CREATE TABLE SongEntity (songId TEXT NOT NULL PRIMARY KEY)")

				CacheDatabaseMigration20To21.migrate(connection)

				assertFalse(connection.hasTable("DownloadEntity"))
				assertTrue(connection.hasTable("SongEntity"))
			}
			file.delete()
		}
	}

	@Test
	fun downloadMigrationAddsDurableIntentColumnsAndPreservesRows() {
		runBlocking {
			val file = temporaryDatabaseFile()
			val driver = JdbcSQLiteDriver()
			driver.open(file.absolutePath).use { connection ->
				connection.execute(
					"CREATE TABLE DownloadEntity (" +
						"songId TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, " +
						"progress REAL NOT NULL, filePath TEXT)"
				)
				connection.execute(
					"INSERT INTO DownloadEntity VALUES ('queued', 'QUEUED', 0.5, NULL)"
				)

				DownloadDatabaseMigration4To5.migrate(connection)

				connection.prepare(
					"SELECT status, progress, intentGeneration, queuedAtEpochMs, cancelled " +
						"FROM DownloadEntity WHERE songId = 'queued'"
				).use { statement ->
					assertTrue(statement.step())
					assertEquals("QUEUED", statement.getText(0))
					assertEquals(0.5, statement.getDouble(1))
					assertEquals(0L, statement.getLong(2))
					assertEquals(0L, statement.getLong(3))
					assertEquals(0L, statement.getLong(4))
				}
				assertTrue(connection.hasIndex("index_DownloadEntity_status_cancelled_queuedAtEpochMs"))
			}
			file.delete()
		}
	}

	@Test
	fun syncMigrationAddsRetryAndDeadLetterState() {
		runBlocking {
			val file = temporaryDatabaseFile()
			val driver = JdbcSQLiteDriver()
			driver.open(file.absolutePath).use { connection ->
				connection.execute(
					"CREATE TABLE SyncActionEntity (" +
						"id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
						"actionType TEXT NOT NULL, itemId TEXT NOT NULL)"
				)
				connection.execute(
					"INSERT INTO SyncActionEntity(actionType, itemId) VALUES ('STAR', 'song')"
				)

				CacheDatabaseMigration21To22.migrate(connection)

				connection.prepare(
					"SELECT attemptCount, nextAttemptAtEpochMs, lastError, deadLettered " +
						"FROM SyncActionEntity WHERE itemId = 'song'"
				).use { statement ->
					assertTrue(statement.step())
					assertEquals(0L, statement.getLong(0))
					assertEquals(0L, statement.getLong(1))
					assertTrue(statement.isNull(2))
					assertEquals(0L, statement.getLong(3))
				}
				assertTrue(connection.hasIndex("index_SyncActionEntity_deadLettered_nextAttemptAtEpochMs_id"))
			}
			file.delete()
		}
	}

	@Test
	fun artistIdentityMigrationMakesIdsNullableAndRemovesOnlyLegacySentinels() = runBlocking {
		val file = temporaryDatabaseFile()
		val driver = JdbcSQLiteDriver()
		driver.open(file.absolutePath).use { connection ->
			connection.execute(
				"CREATE TABLE AlbumEntity (albumId TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', " +
					"artistName TEXT NOT NULL DEFAULT '', artistId TEXT NOT NULL, year INTEGER, " +
					"coverArtId TEXT NOT NULL DEFAULT '', genre TEXT, genres TEXT NOT NULL DEFAULT '', " +
					"songCount INTEGER NOT NULL DEFAULT 0, duration INTEGER, createdAt INTEGER NOT NULL DEFAULT 0, " +
					"starredAt INTEGER, lastPlayedAt INTEGER, playCount INTEGER NOT NULL DEFAULT 0, " +
					"userRating INTEGER, version TEXT, musicBrainzId TEXT)"
			)
			connection.execute(
				"CREATE TABLE SongEntity (songId TEXT PRIMARY KEY, title TEXT NOT NULL DEFAULT '', " +
					"artistName TEXT NOT NULL DEFAULT '', artistId TEXT NOT NULL, albumTitle TEXT, " +
					"belongsToAlbumId TEXT, parentId TEXT, comment TEXT, trackNumber INTEGER, discNumber INTEGER, " +
					"isrc TEXT NOT NULL DEFAULT '', year INTEGER, genre TEXT, genres TEXT NOT NULL DEFAULT '', " +
					"moods TEXT NOT NULL DEFAULT '', duration INTEGER NOT NULL DEFAULT 0, bpm INTEGER, " +
					"contributors TEXT NOT NULL DEFAULT '', playCount INTEGER NOT NULL DEFAULT 0, userRating INTEGER, " +
					"averageRating REAL, bitRate INTEGER, bitDepth INTEGER, sampleRate INTEGER, " +
					"audioChannelCount INTEGER, replayGain TEXT, fileSize INTEGER NOT NULL DEFAULT 0, " +
					"fileExtension TEXT NOT NULL DEFAULT '', mimeType TEXT NOT NULL DEFAULT '', filePath TEXT, " +
					"starredAt INTEGER, coverArtId TEXT, musicBrainzId TEXT, explicitStatus INTEGER NOT NULL DEFAULT 0)"
			)
			connection.execute("INSERT INTO AlbumEntity(albumId, artistId) VALUES ('missing', 'unknown artist'), ('real', 'artist-1')")
			connection.execute("INSERT INTO SongEntity(songId, artistId) VALUES ('missing', ' UNKNOWN ARTIST '), ('real', 'artist-1')")

			CacheDatabaseMigration22To23.migrate(connection)

			listOf("AlbumEntity", "SongEntity").forEach { table ->
				connection.prepare("SELECT artistId FROM $table ORDER BY ${if (table == "AlbumEntity") "albumId" else "songId"}").use { statement ->
					assertTrue(statement.step())
					assertTrue(statement.isNull(0))
					assertTrue(statement.step())
					assertEquals("artist-1", statement.getText(0))
				}
				connection.prepare("PRAGMA table_info($table)").use { statement ->
					var nullableArtistId = false
					while (statement.step()) {
						if (statement.getText(1) == "artistId") {
							nullableArtistId = statement.getLong(3) == 0L
						}
					}
					assertTrue(nullableArtistId)
				}
			}
		}
		file.delete()
	}

	@Test
	fun artworkColorMigrationAddsIdentityAndTimestampWithoutTrustingLegacyRows() = runBlocking {
		val file = temporaryDatabaseFile()
		val driver = JdbcSQLiteDriver()
		driver.open(file.absolutePath).use { connection ->
			connection.execute(
				"CREATE TABLE artwork_colors (artworkKey TEXT NOT NULL PRIMARY KEY, color INTEGER NOT NULL)"
			)
			connection.execute("INSERT INTO artwork_colors VALUES ('album:1', 123)")

			CacheDatabaseMigration23To24.migrate(connection)

			connection.prepare(
				"SELECT sourceIdentity, updatedAtEpochMillis FROM artwork_colors WHERE artworkKey = 'album:1'"
			).use { statement ->
				assertTrue(statement.step())
				assertEquals("", statement.getText(0))
				assertEquals(0L, statement.getLong(1))
			}
		}
		file.delete()
	}

	private fun temporaryDatabaseFile(): File =
		File.createTempFile("navic-download-migration-", ".db").apply { delete() }
}

private fun androidx.sqlite.SQLiteConnection.execute(sql: String) {
	prepare(sql).use { statement -> statement.step() }
}

private fun androidx.sqlite.SQLiteConnection.hasTable(name: String): Boolean =
	prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").use { statement ->
		statement.bindText(1, name)
		statement.step()
	}

private fun androidx.sqlite.SQLiteConnection.hasIndex(name: String): Boolean =
	prepare("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?").use { statement ->
		statement.bindText(1, name)
		statement.step()
	}

private class JdbcSQLiteDriver : SQLiteDriver {
	override fun open(fileName: String): SQLiteConnection =
		JdbcSQLiteConnection(DriverManager.getConnection("jdbc:sqlite:$fileName"))
}

private class JdbcSQLiteConnection(
	private val connection: Connection
) : SQLiteConnection {
	override fun prepare(sql: String): SQLiteStatement =
		JdbcSQLiteStatement(connection.prepareStatement(sql))

	override fun close() {
		connection.close()
	}
}

private class JdbcSQLiteStatement(
	private val statement: PreparedStatement
) : SQLiteStatement {
	private var executed = false
	private var resultSet: ResultSet? = null

	override fun bindBlob(index: Int, value: ByteArray) = statement.setBytes(index, value)
	override fun bindDouble(index: Int, value: Double) = statement.setDouble(index, value)
	override fun bindLong(index: Int, value: Long) = statement.setLong(index, value)
	override fun bindText(index: Int, value: String) = statement.setString(index, value)
	override fun bindNull(index: Int) = statement.setObject(index, null)
	override fun getBlob(index: Int): ByteArray = result().getBytes(index + 1)
	override fun getDouble(index: Int): Double = result().getDouble(index + 1)
	override fun getLong(index: Int): Long = result().getLong(index + 1)
	override fun getText(index: Int): String = result().getString(index + 1)
	override fun isNull(index: Int): Boolean {
		result().getObject(index + 1)
		return result().wasNull()
	}

	override fun getColumnCount(): Int = result().metaData.columnCount
	override fun getColumnName(index: Int): String = result().metaData.getColumnName(index + 1)
	override fun getColumnType(index: Int): Int = result().metaData.getColumnType(index + 1)

	override fun step(): Boolean {
		if (!executed) {
			executed = true
			if (!statement.execute()) return false
			resultSet = statement.resultSet
		}
		return resultSet?.next() == true
	}

	override fun reset() {
		resultSet?.close()
		resultSet = null
		executed = false
	}

	override fun clearBindings() = statement.clearParameters()

	override fun close() {
		resultSet?.close()
		statement.close()
	}

	private fun result(): ResultSet = checkNotNull(resultSet) { "Statement has no active result row" }
}
