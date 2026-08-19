package md.oak.sonark.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
abstract class SonarkDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: SonarkDatabase? = null

        fun getDatabase(context: Context): SonarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SonarkDatabase::class.java,
                    "sonark_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
