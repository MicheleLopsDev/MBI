package io.github.luposolitario.mbi.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.LinkedList
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioBrowserService @Inject constructor(@ApplicationContext context: Context) :
    MediaService<HitRadio> {

    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private var apiKey: String
    private val baseUrl = "https://fi1.api.radio-browser.info/"
    private var currentQuery: String
    private var currentIndex = -1

    private var perPage: Int
    private val radioService: RadioInterfaceService
    private var radioList: LinkedList<HitRadio> = LinkedList()
    private val radioContext = context

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

    fun loadFallbackStations(name: String?, context: Context): List<HitRadio>? {
        return try {
            val inputStream = context.assets.open("fallback_stations.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<HitRadio>>() {}.type
            val fullList = Gson().fromJson<List<HitRadio>>(json, type)

            // Applica filtro
            fullList.filter { hit ->
                hit.name.contains(name.toString(), ignoreCase = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun setradioList(_list: List<HitRadio>) {
        radioList.clear()
        radioList.addAll(_list)
        currentIndex = 0
    }

    fun searchMedia(query: String = "", callback: (List<HitRadio>?) -> Unit) {
        val countryCode = "IT"
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
                    fallback(query)
                }
            }

            override fun onFailure(call: Call<List<HitRadio>>, t: Throwable) {
                Log.e("RadioService", "Errore di rete: ${t.message}")
                fallback(query)
            }

            fun fallback(name: String?) {
                val fallbackList = loadFallbackStations(name, radioContext)
                if (!fallbackList.isNullOrEmpty()) {
                    radioList.clear()
                    radioList.addAll(fallbackList)
                    currentIndex = if (radioList.isNotEmpty()) 0 else -1
                    callback(fallbackList)
                } else {
                    callback(null)
                }
            }
        })
    }

}