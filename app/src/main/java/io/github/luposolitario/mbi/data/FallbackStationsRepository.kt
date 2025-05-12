package io.github.luposolitario.mbi.data                       // usa il tuo package radice

import android.content.Context
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import io.github.luposolitario.mbi.model.HitRadio
import java.io.InputStreamReader

/**
 * Singleton che carica l’asset `fallback_stations.json` una sola volta
 * e restituisce la lista delle stazioni già in memoria.
 */
object FallbackStationsRepository {

    @Volatile
    private var cache: List<HitRadio>? = null
    private val lock = Any()

    /** Restituisce la lista; al primo invocazione la carica dagli assets. */
    fun getAll(context: Context): List<HitRadio> {
        // fast‑path se la cache è già pronta
        cache?.let { return it }

        // sincronizzazione doppio‑check
        return synchronized(lock) {
            cache ?: loadFromAssets(context).also { cache = it }
        }
    }

    /** Lettura *streaming* dell’asset → lista in memoria (una tantum). */
    private fun loadFromAssets(context: Context): List<HitRadio> {
        context.assets.open("fallback_stations.json").use { input ->
            JsonReader(InputStreamReader(input)).use { reader ->
                val gson = Gson()
                val list = mutableListOf<HitRadio>()
                reader.beginArray()
                while (reader.hasNext()) {
                    list.add(gson.fromJson(reader, HitRadio::class.java))
                }
                reader.endArray()
                return list
            }
        }
    }
}
