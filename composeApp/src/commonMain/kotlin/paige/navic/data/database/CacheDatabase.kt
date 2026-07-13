package paige.navic.data.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtworkColorDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.dao.AurralMetadataCacheDao
import paige.navic.data.database.dao.BinderyMetadataCacheDao
import paige.navic.data.database.dao.GenreDao
import paige.navic.data.database.dao.LyricDao
import paige.navic.data.database.dao.PlaybackOriginDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.RadioDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.dao.SyncActionDao
import paige.navic.data.database.entities.AlbumEntity
import paige.navic.data.database.entities.ArtworkColorEntity
import paige.navic.data.database.entities.ArtistEntity
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.data.database.entities.AurralMetadataCacheEntity
import paige.navic.data.database.entities.BinderyMetadataCacheEntity
import paige.navic.data.database.entities.GenreEntity
import paige.navic.data.database.entities.LyricEntity
import paige.navic.data.database.entities.PlaybackOriginEntity
import paige.navic.data.database.entities.PlaylistEntity
import paige.navic.data.database.entities.PlaylistSongCrossRef
import paige.navic.data.database.entities.RadioEntity
import paige.navic.data.database.entities.SongEntity
import paige.navic.data.database.entities.SyncActionEntity

@Database(
	version = 22,
	entities = [
		AlbumEntity::class,
		GenreEntity::class,
		PlaylistEntity::class,
		PlaylistSongCrossRef::class,
		SongEntity::class,
		ArtistEntity::class,
		RadioEntity::class,
		LyricEntity::class,
		SyncActionEntity::class,
		PlaybackOriginEntity::class,
		ArtistPhotoCacheEntity::class,
		AurralMetadataCacheEntity::class,
		BinderyMetadataCacheEntity::class,
		ArtworkColorEntity::class
	]
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun genreDao(): GenreDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun songDao(): SongDao
	abstract fun artistDao(): ArtistDao
	abstract fun radioDao(): RadioDao
	abstract fun lyricDao(): LyricDao
	abstract fun syncActionDao(): SyncActionDao
	abstract fun playbackOriginDao(): PlaybackOriginDao
	abstract fun artistPhotoCacheDao(): ArtistPhotoCacheDao
	abstract fun aurralMetadataCacheDao(): AurralMetadataCacheDao
	abstract fun binderyMetadataCacheDao(): BinderyMetadataCacheDao
	abstract fun artworkColorDao(): ArtworkColorDao
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
	override fun initialize(): CacheDatabase
}
