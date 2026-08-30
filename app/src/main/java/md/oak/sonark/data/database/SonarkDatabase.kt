package md.oak.sonark.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SongEntity::class, AlbumEntity::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SonarkDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao

    companion object {
        private val instances = mutableMapOf<String, SonarkDatabase>()

        private fun getDatabaseName(email: String?): String {
            return if (email == null) "sonark_database" else "sonark_${email.hashCode()}.db"
        }

        fun getDatabase(context: Context, email: String? = null): SonarkDatabase {
            val name = getDatabaseName(email)
            return synchronized(this) {
                instances[name]?.takeIf { it.isOpen } ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonarkDatabase::class.java,
                    name
                )
                .fallbackToDestructiveMigration(true)
                .build()
                .also { instances[name] = it }
            }
        }

        /**
         * Closes the database instance for the given email only if it matches the provided [instance].
         * If [instance] is null, closes and removes whatever is currently cached.
         */
        fun closeAndRemoveInstance(email: String, instance: SonarkDatabase? = null) {
            val name = getDatabaseName(email)
            synchronized(this) {
                val current = instances[name]
                if (instance == null || current == instance) {
                    instances.remove(name)
                    if (current?.isOpen == true) {
                        current.close()
                    }
                }
            }
        }
    }
}
