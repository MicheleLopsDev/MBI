package io.github.luposolitario.mbi.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.reflect.TypeToken      //  aggiungi se manca
import com.google.gson.stream.JsonReader      //  assicurati che sia *questo* import
import io.github.luposolitario.mbi.model.Hit
import io.github.luposolitario.mbi.model.HitRadio
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioBrowserService @Inject constructor(@ApplicationContext context: Context) :
    MediaService<HitRadio> {

    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private var apiKey: String
    private val baseUrl = "https://xx.api.radio-browser.info/"
    private var currentQuery: String
    private var currentIndex = -1

    private var perPage: Int
    private val radioService: RadioInterfaceService
    private var radioList: LinkedList<HitRadio> = LinkedList()
    private val radioContext = context

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hitRadioType = object : TypeToken<HitRadio>() {}.type


    init {
        val properties = Properties()
        try {
            apiKey = MutableStateFlow(prefs.getString("pixabay_api_key", "") ?: "").value

            val inputStream = context.assets.open("pixbay.properties")
            properties.load(inputStream)
            currentQuery = properties.getProperty("pixabay_query") ?: ""
            perPage = properties.getProperty("pixabay_per_page")?.toIntOrNull() ?: 200
            inputStream.close()
        } catch (e: Exception) {
            Log.e("ImageService", "Errore nel caricamento del file di properties: ${e.message}")
            apiKey = ""
            currentQuery = ""
            perPage = 200
        }

        if (apiKey.isEmpty()) {
            Log.e("ImageService", "Chiave API di Pixabay non trovata nel file di properties!")
        }

        val logging = HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BODY) }

        val client = OkHttpClient.Builder().addInterceptor(logging).build()

        radioService = Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(RadioInterfaceService::class.java)

    }

    override fun getCurrentIndex(): Int = currentIndex
    override fun getCurrentItem(): HitRadio? = radioList.getOrNull(currentIndex)
    override fun getTotal(): Int = radioList.size
    override suspend fun moveTo(
        index: Int,
        onMediaChanged: (HitRadio?) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun getItemAtIndex(i: Int): Hit? {
        TODO("Not yet implemented")
    }

    override fun setCurrentIndex(i: Int) {
        currentIndex = i
    }

    override fun setQuery(string: String) {
        currentQuery = string
    }

    override suspend fun moveNext(offset: Int, onMediaChanged: (HitRadio?) -> Unit) {
        if (radioList.isEmpty()) {
            onMediaChanged(null)
            return
        }
        if (currentIndex < radioList.size - 1) {
            currentIndex++
        }
        onMediaChanged(radioList.getOrNull(currentIndex))
    }


    override suspend fun movePrevious(offset: Int, onMediaChanged: (HitRadio?) -> Unit) {
        if (radioList.isEmpty()) {
            onMediaChanged(null)
            return
        }
        if (currentIndex > 0) {
            currentIndex--
        }
        onMediaChanged(radioList.getOrNull(currentIndex))
    }

    override suspend fun moveToFirst(onMediaChanged: (HitRadio?) -> Unit) {
        if (radioList.isNotEmpty()) {
            currentIndex = 0
            onMediaChanged(radioList[0])
        } else {
            onMediaChanged(null)
        }
    }

    fun cancelJobs() = ioScope.cancel()    // chiamalo dall’Activity se serve

    fun setradioList(_list: List<HitRadio>) {
        radioList.clear()
        radioList.addAll(_list)
        currentIndex = 0
    }

    fun searchMedia(
        query: String = "",
        countryCode: String = "IT",
        callback: (List<HitRadio>?) -> Unit
    ) {
        radioService.searchStations(countryCode, query).enqueue(object : Callback<List<HitRadio>> {
            override fun onResponse(
                call: Call<List<HitRadio>>,
                response: Response<List<HitRadio>>
            ) {
                if (response.isSuccessful) {
                    val result = response.body() ?: emptyList()
                    radioList.clear()
                    radioList.addAll(result)
                    currentIndex = if (radioList.isNotEmpty()) 0 else -1
                    callback(result)
                } else {
                    Log.e("RadioService", "Errore API: ${response.code()}")
                    fallback(query, countryCode)
                }
            }

            override fun onFailure(call: Call<List<HitRadio>>, t: Throwable) {
                Log.e("RadioService", "Errore di rete: ${t.message}")
                fallback(query, countryCode)
            }

            private suspend fun loadFallbackStations(
                name: String?
            ): List<HitRadio> = withContext(Dispatchers.IO) {
                val query = name.orEmpty().trim().lowercase()
                var cc = if (countryCode.isBlank()) "IT" else countryCode

                radioContext.assets.open(cc + ".json").use { input ->
                    JsonReader(InputStreamReader(input)).use { reader ->
                        val gson = Gson()
                        val list = mutableListOf<HitRadio>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val hit: HitRadio = gson.fromJson(reader, hitRadioType)
                            if (hit.name.contains(query, true)) list.add(hit)
                        }
                        reader.endArray()
                        list
                    }
                }
            }


            /** Lettura‑streaming del JSON – va sempre chiamato su Dispatchers.IO */
            fun fallback(name: String?, countryCode1: String) {
                ioScope.launch {
                    val list = loadFallbackStations(name)
                    withContext(Dispatchers.Main) {
                        if (list.isNotEmpty()) {
                            radioList.clear()
                            radioList.addAll(list)
                            currentIndex = if (radioList.isNotEmpty()) 0 else -1
                            callback(list)          // stesso callback passato a searchMedia
                        } else {
                            callback(null)
                        }
                    }
                }
            }
        })
    }

}