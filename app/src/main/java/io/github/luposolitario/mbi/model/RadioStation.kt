package io.github.luposolitario.mbi.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "radio_station",
    indices = [
        Index(value = ["name", "radioUrl"], unique = true)
    ]
)
data class RadioStation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val icon: String?,
    val radioUrl: String?
)
