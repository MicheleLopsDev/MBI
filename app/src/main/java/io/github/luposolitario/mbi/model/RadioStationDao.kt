package io.github.luposolitario.mbi.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(station: RadioStation): Long

    @Query("SELECT * FROM radio_station")
    fun getAll(): Flow<List<RadioStation>>
}
