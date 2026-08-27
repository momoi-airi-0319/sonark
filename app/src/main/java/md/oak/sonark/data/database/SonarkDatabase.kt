package md.oak.sonark.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SongEntity::class, AlbumEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SonarkDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao

    companion object {
        @Volatile
        private var INSTANCE: SonarkDatabase? = null

        fun getDatabase(context: Context): SonarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonarkDatabase::class.java,
                    "sonark_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
