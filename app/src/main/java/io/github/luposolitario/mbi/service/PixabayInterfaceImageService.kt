package io.github.luposolitario.mbi.service

import io.github.luposolitario.mbi.model.PixbayResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.QueryMap

interface PixabayInterfaceImageService {
    @GET(".")
    fun searchImages(@QueryMap params: Map<String, String>): Call<PixbayResponse>
}
