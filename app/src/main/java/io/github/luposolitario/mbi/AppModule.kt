package io.github.luposolitario.mbi

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.luposolitario.mbi.model.AppDatabase
import io.github.luposolitario.mbi.model.RadioStationDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideRadioStationDao(appDatabase: AppDatabase): RadioStationDao =
        appDatabase.radioStationDao()
}