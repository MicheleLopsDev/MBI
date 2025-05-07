package io.github.luposolitario.mbi.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixbayImageService @Inject constructor(@ApplicationContext context: Context) :
    MediaService<Hit> {

    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private var apiKey: String
    private val apiService: PixabayInterfaceImageService
    private val baseUrl = "https://pixabay.com/api/"
    private var currentIndex = -1
    private var currentPage = 1
    private var currentQuery: String
    private var imageList: LinkedList<Hit> = LinkedList()
    private var perPage: Int
    private var total = 0
    private var totalHits = 0
    private var currentItem: Hit? = null

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
        apiService = Retrofit.Builder().client(client).baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(PixabayInterfaceImageService::class.java)
    }

    private fun createRequest(page: Int = currentPage): PixabayRequest {
        return PixabayRequest(
            key = apiKey, query = currentQuery, perPage = perPage, page = page, safeSearch = false
        )
    }

    private fun fetchMedia(callback: (List<Hit>?) -> Unit) {
        if (apiKey.isEmpty()) {
            callback(null)
            return
        }

        val request = createRequest()
        Log.d("ImageService", "Fetching images with query: $currentQuery, page: $currentPage")
        apiService.searchImages(request.toMap()).enqueue(object : Callback<PixbayResponse> {
            override fun onFailure(call: Call<PixbayResponse>, t: Throwable) {
                Log.e("ImageService", "Errore nella chiamata API: ${t.message}")
                callback(null)
            }

            override fun onResponse(
                call: Call<PixbayResponse>, response: Response<PixbayResponse>
            ) {
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        totalHits = apiResponse.totalHits
                        total = apiResponse.total
                        val newImageList =
                            LinkedList<Hit>().apply { addAll(apiResponse.hits) }
                        imageList = mergeConcatenando(imageList, newImageList)
                        currentIndex = if (imageList.isNotEmpty()) {
                            if (currentPage == 1) 0 else currentIndex
                        } else -1
                        callback(apiResponse.hits)
                    } else {
                        Log.e("ImageService", "Risposta API vuota")
                        callback(null)
                    }
                } else {
                    Log.e(
                        "ImageService",
                        "Richiesta fallita: ${response.code()} - ${response.message()}"
                    )
                    callback(null)
                }
            }
        })
    }

    override fun getCurrentIndex(): Int = currentIndex

    override fun getCurrentItem(): Hit? {
        return if (currentIndex in imageList.indices) imageList[currentIndex] else null
    }

    override fun getTotal(): Int = total
    override suspend fun moveTo(
        index: Int,
        onMediaChanged: (Hit?) -> Unit
    ) {
        // Se non ho ancora nessuna immagine caricata, restituisco null
        if (imageList.isEmpty()) {
            onMediaChanged(null)
            return
        }
        // Se l'indice è valido, aggiorno currentIndex e restituisco l'elemento
        if (index in imageList.indices) {
            currentIndex = index
            onMediaChanged(imageList[currentIndex])
        } else {
            // Indice fuori dai limiti → restituisco null (o potresti decidere di restituire il primo/ultimo)
            onMediaChanged(null)
        }
    }

    override fun getItemAtIndex(i: Int): Hit? {
        return if (currentIndex in imageList.indices) imageList[i] else null
    }

    override fun setCurrentIndex(i: Int) {
        currentIndex = i
        currentItem = if (currentIndex in imageList.indices) imageList[i] else null
    }

    fun loadInitialMedia(callback: (List<Hit>?) -> Unit) {
        searchMedia(callback = callback)
    }

    private fun <T> mergeConcatenando(list1: LinkedList<T>, list2: LinkedList<T>): LinkedList<T> {
        return LinkedList<T>().apply {
            addAll(list1)
            addAll(list2)
        }
    }

    override suspend fun moveNext(offset: Int, onMediaChanged: (Hit?) -> Unit) {
        if (imageList.isEmpty()) {
            onMediaChanged(null)
            return
        }

        val nextIndex = currentIndex + offset
        if (nextIndex < imageList.size) {
            currentIndex = nextIndex
            onMediaChanged(imageList[currentIndex])
        } else {
            // Calculate how many more items are needed to fulfill the offset
            val remainingInCurrentList = imageList.size - 1 - currentIndex
            val neededFromNextPage = offset - remainingInCurrentList

            // Calculate how many full pages are needed
            val pagesToFetch = (neededFromNextPage + perPage - 1) / perPage

            if ((currentPage * perPage) < totalHits) {
                currentPage += pagesToFetch
                fetchMedia { newItems ->
                    if (!newItems.isNullOrEmpty()) {
                        // After fetching, try to move to the desired index again
                        val newIndex =
                            currentIndex + offset // This should now be within the extended list size if successful
                        if (newIndex < imageList.size) {
                            currentIndex = newIndex
                            onMediaChanged(imageList[currentIndex])
                        } else {
                            // If still out of bounds after fetching (e.g., not enough new items),
                            // move to the last available item in the list
                            currentIndex = imageList.size - 1
                            onMediaChanged(imageList[currentIndex])
                        }
                    } else {
                        // If fetching new items failed, move to the last item in the current list
                        onMediaChanged(imageList.lastOrNull())
                        currentIndex = imageList.size - 1
                    }
                }
            } else {
                // Reached the end of all available items
                onMediaChanged(imageList.lastOrNull())
                currentIndex = imageList.size - 1
            }
        }
    }

    override suspend fun movePrevious(offset: Int, onMediaChanged: (Hit?) -> Unit) {
        if (imageList.isEmpty()) {
            onMediaChanged(null)
            return
        }

        val previousIndex = currentIndex - offset
        if (previousIndex >= 0) {
            currentIndex = previousIndex
            onMediaChanged(imageList[currentIndex])
        } else {
            // Calculate how many items back we need to go beyond the start of the current list
            val neededFromPreviousPage = -previousIndex

            // Calculate how many full pages are needed
            val pagesToFetch = (neededFromPreviousPage + perPage - 1) / perPage

            if (currentPage > 1) {
                currentPage -= pagesToFetch
                fetchMedia { newItems ->
                    if (!newItems.isNullOrEmpty()) {
                        // After fetching, try to move to the desired index again
                        val newIndex =
                            currentIndex - offset // This should now be within the extended list size if successful
                        if (newIndex >= 0) {
                            currentIndex = newIndex
                            onMediaChanged(imageList[currentIndex])
                        } else {
                            // If still out of bounds after fetching (e.g., not enough previous items),
                            // move to the first available item in the list
                            currentIndex = 0
                            onMediaChanged(imageList[currentIndex])
                        }
                    } else {
                        // If fetching new items failed, move to the first item in the current list
                        onMediaChanged(imageList.firstOrNull())
                        currentIndex = 0
                    }
                }
            } else {
                // Reached the beginning of all available items
                onMediaChanged(imageList.firstOrNull())
                currentIndex = 0
            }
        }
    }

    override suspend fun moveToFirst(onMediaChanged: (Hit?) -> Unit) {
        if (imageList.isNotEmpty()) {
            currentIndex = 0
            onMediaChanged(imageList[currentIndex])
        } else {
            onMediaChanged(null)
        }
    }

    fun searchMedia(query: String = "", callback: (List<Hit>?) -> Unit) {
        Log.d("API‑KEY", prefs.getString("pixabay_api_key", "") ?: "vuota")
        currentPage = 1
        imageList.clear()
        if (!query.isEmpty()) {
            currentQuery = query
        }
        fetchMedia(callback)
    }
}
