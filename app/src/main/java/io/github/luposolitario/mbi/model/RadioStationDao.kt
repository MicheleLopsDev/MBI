// File: model/RadioStationDao.kt
package io.github.luposolitario.mbi.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RadioStationDao {
    // insertOrIgnore è veloce perché non fa controlli preliminari,
    // il conflitto sulla Primary Key (stationuuid) viene ignorato.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(station: RadioStation): Long // Restituisce -1 se ignorato

    // Rimuovi getAll se non ti serve più osservare tutte le stazioni
    // @Query("SELECT * FROM radio_station")
    // fun getAll(): Flow<List<RadioStation>>

    // Nuovo metodo per cercare stazioni per nome nel DB (per fallback)
    // Usa LIKE per una ricerca parziale (case-insensitive implicito con COLLATE NOCASE)
    @Query("SELECT * FROM radio_station WHERE name LIKE '%' || :query || '%' COLLATE NOCASE")
    suspend fun searchByName(query: String): List<RadioStation>

    // Metodo opzionale per recuperare una stazione specifica se necessario
    @Query("SELECT * FROM radio_station WHERE stationuuid = :uuid LIMIT 1")
    suspend fun getByStationUuid(uuid: String): RadioStation?
}