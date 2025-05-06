package io.github.luposolitario.mbi.service

import android.content.Context
import android.util.Log
import io.github.luposolitario.mbi.model.Hit
import io.github.luposolitario.mbi.model.PixabayRequest
import io.github.luposolitario.mbi.model.PixbayResponse
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

class PixbayVideoService(context: Context) : MediaService<Hit> {

    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private var apiKey: String
    private val baseUrl = "https://pixabay.com/api/"
    private var currentPage = 1
    private var currentIndex = -1
    private var currentQuery: String
    private var perPage: Int
    private var total = 0
    private var totalHits = 0
    private var videoList: LinkedList<Hit> = LinkedList()
    private val videoService: PixabayInterfaceVideoService

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
            Log.e("VideoService", "Errore caricamento properties: ${e.message}")
            apiKey = ""
            currentQuery = ""
            perPage = 200
        }

        if (apiKey.isEmpty()) {
            Log.e("VideoService", "Chiave API mancante!")
        }

        val logging = HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BODY) }

        val client = OkHttpClient.Builder().addInterceptor(logging).build()

        videoService = Retrofit.Builder()
            .client(client)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(PixabayInterfaceVideoService::class.java)
    }

    private fun createRequest(page: Int = currentPage): PixabayRequest {
        return PixabayRequest(
            key = apiKey,
            query = currentQuery,
            perPage = perPage,
            page = page,
            safeSearch = false
        )
    }

    fun fetchMedia(callback: (List<Hit>?) -> Unit) {
        if (apiKey.isEmpty()) {
            callback(null)
            return
        }

        val request = createRequest()
        Log.d("VideoService", "Fetching videos with query: $currentQuery, page: $currentPage")
        videoService.searchVideo(request.toMap()).enqueue(object : Callback<PixbayResponse> {
            override fun onFailure(call: Call<PixbayResponse>, t: Throwable) {
                Log.e("VideoService", "Errore API: ${t.message}")
                callback(null)
            }

            override fun onResponse(
                call: Call<PixbayResponse>,
                response: Response<PixbayResponse>
            ) {
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        totalHits = apiResponse.totalHits
                        total = apiResponse.total
                        val newVideoList = LinkedList<Hit>().apply { addAll(apiResponse.hits) }
                        videoList = mergeConcatenando(videoList, newVideoList)
                        currentIndex = if (videoList.isNotEmpty()) {
                            if (currentPage == 1) 0 else currentIndex
                        } else -1
                        callback(apiResponse.hits)
                    } else {
                        Log.e("VideoService", "Risposta vuota")
                        callback(null)
                    }
                } else {
                    Log.e("VideoService", "Errore ${response.code()} - ${response.message()}")
                    callback(null)
                }
            }
        })
    }

    override fun getCurrentIndex(): Int = currentIndex
    override fun getCurrentItem(): Hit? =
        if (currentIndex in videoList.indices) videoList[currentIndex] else null

    override fun getTotal(): Int = total
    override suspend fun moveTo(
        index: Int,
        onMediaChanged: (Hit?) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun getItemAtIndex(i: Int): Hit? {
        TODO("Not yet implemented")
    }

    override fun setCurrentIndex(i: Int) {
        TODO("Not yet implemented")
    }

//    fun loadInitialMedia(callback: (List<Hit>?) -> Unit) {
//        searchMedia(callback = callback)
//    }

    private fun <T> mergeConcatenando(list1: LinkedList<T>, list2: LinkedList<T>): LinkedList<T> {
        return LinkedList<T>().apply {
            addAll(list1)
            addAll(list2)
        }
    }

    override suspend fun moveNext(offset: Int, onMediaChanged: (Hit?) -> Unit) {
        if (videoList.isEmpty()) {
            onMediaChanged(null)
            return
        }

        if (currentIndex < videoList.size - 1) {
            currentIndex++
            onMediaChanged(videoList[currentIndex])
        } else if ((currentPage * perPage) < totalHits) {
            currentPage++
            fetchMedia { newItems ->
                if (!newItems.isNullOrEmpty()) {
                    onMediaChanged(videoList[currentIndex])
                } else {
                    onMediaChanged(videoList.lastOrNull())
                }
            }
        } else {
            onMediaChanged(videoList.lastOrNull())
        }
    }

    override suspend fun movePrevious(offset: Int, onMediaChanged: (Hit?) -> Unit) {
        if (videoList.isEmpty()) {
            onMediaChanged(null)
            return
        }

        if (currentIndex > 0) {
            currentIndex--
            onMediaChanged(videoList[currentIndex])
        } else if (currentPage > 1) {
            currentPage--
            fetchMedia { newItems ->
                if (!newItems.isNullOrEmpty()) {
                    currentIndex = videoList.size - 1
                    onMediaChanged(videoList[currentIndex])
                } else {
                    onMediaChanged(videoList.firstOrNull())
                }
            }
        } else {
            onMediaChanged(videoList.firstOrNull())
        }
    }

    override suspend fun moveToFirst(onMediaChanged: (Hit?) -> Unit) {
        if (videoList.isNotEmpty()) {
            currentIndex = 0
            onMediaChanged(videoList[currentIndex])
        } else {
            onMediaChanged(null)
        }
    }

    fun searchMedia(query: String = "", callback: (List<Hit>?) -> Unit) {
        currentPage = 1
        videoList.clear()
        if (!query.isEmpty()) {
            currentQuery = query
        }
        fetchMedia(callback = callback)
    }
}