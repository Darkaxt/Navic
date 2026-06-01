package paige.navic.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.LidaClipDownloadDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.LidaClipDownloadEntity

@Database(
	version = 4,
	entities = [DownloadEntity::class, LidaClipDownloadEntity::class]
)
@ConstructedBy(DownloadDatabaseConstructor::class)
abstract class DownloadDatabase : RoomDatabase() {
	abstract fun downloadDao(): DownloadDao
	abstract fun lidaClipDownloadDao(): LidaClipDownloadDao
}

@Suppress("KotlinNoActualForExpect")
expect object DownloadDatabaseConstructor : RoomDatabaseConstructor<DownloadDatabase> {
	override fun initialize(): DownloadDatabase
}
