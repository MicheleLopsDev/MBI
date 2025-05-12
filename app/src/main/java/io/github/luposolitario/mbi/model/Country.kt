package io.github.luposolitario.mbi.model


import com.google.gson.annotations.SerializedName

data class Country(
    @SerializedName("iso_3166_1") val code: String,
    val name: String,
    @SerializedName("stationcount") val stationCount: Int
)

