package io.github.luposolitario.mbi.service

import io.github.luposolitario.mbi.model.HitRadio
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface RadioInterfaceService {
    @Headers("User-Agent: MBI-Radio-Client/1.0")
    @GET("json/stations/search")
    fun searchStations(
        @Query("countrycode") countryCode: String,
        @Query("name") name: String? = null
    ): Call<List<HitRadio>>
}
