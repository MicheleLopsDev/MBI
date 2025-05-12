// File: model/AppDatabase.kt
package io.github.luposolitario.mbi.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RadioStation::class],
    version = 2, // <<-- INCREMENTA LA VERSIONE
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun radioStationDao(): RadioStationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database.db"
            ).fallbackToDestructiveMigration() // Gestisce l'aggiornamento dello schema
                .build()
    }
}