// ImageViewModel.kt
package io.github.luposolitario.mbi.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luposolitario.mbi.model.Hit // Assicurati che Hit sia Parcelable
import io.github.luposolitario.mbi.service.PixbayImageService
import javax.inject.Inject


@HiltViewModel
class ImageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val service: PixbayImageService
) : ViewModel() {

    companion object {
        private const val KEY_QUERY = "KEY_QUERY"
        private const val KEY_CURRENT_INDEX = "KEY_CURRENT_INDEX"

        // Chiave per l'oggetto Hit corrente
        private const val KEY_CURRENT_HIT = "KEY_CURRENT_HIT" // Modificata per chiarezza
        private const val KEY_TOTAL_RESULTS = "KEY_TOTAL_RESULTS"
    }

    // LiveData gestito da SavedStateHandle per l'oggetto Hit corrente
    private val _currentImage = savedStateHandle.getLiveData<Hit?>(KEY_CURRENT_HIT)
    val currentImage: LiveData<Hit?> = _currentImage // Espone LiveData<Hit?> [Source 173]

    init {
        // Se non c'è Hit salvato e nessuna query, carica i dati iniziali
        if (savedStateHandle.get<Hit>(KEY_CURRENT_HIT) == null &&
            savedStateHandle.get<String>(KEY_QUERY) == null
        ) {
            loadInitial()
        }
        // Se c'è query ma non Hit (es. processo terminato durante la ricerca), riesegui ricerca
        else if (savedStateHandle.get<String>(KEY_QUERY) != null &&
            savedStateHandle.get<Hit>(KEY_CURRENT_HIT) == null
        ) {
            savedStateHandle.get<String>(KEY_QUERY)?.let { query ->
                search(query, savedStateHandle.get<Int>(KEY_CURRENT_INDEX) ?: 0)
            }
        }
        // Potrebbe essere utile risincronizzare lo stato del service qui se necessario,
        // ma le azioni (search, next, ecc.) aggiorneranno lo stato salvato.
    }

    // Modifica le funzioni per salvare l'oggetto Hit
    fun search(query: String, restoreIndex: Int = 0) {
        savedStateHandle[KEY_QUERY] = query
        service.searchMedia(query) { list ->
            val total = service.getTotal()
            savedStateHandle[KEY_TOTAL_RESULTS] = total
            // Determina l'indice e ottieni Hit dal service
            // Nota: Il service deve ancora poter fornire Hit per indice/posizione
            val indexToSet =
                if (list != null && restoreIndex >= 0 && restoreIndex < list.size) restoreIndex else 0
            service.setCurrentIndex(indexToSet) // Assumiamo che il service possa impostare l'indice
            val imageToShow =
                service.getItemAtIndex(indexToSet) // Assumiamo che il service possa dare l'Hit per indice

            savedStateHandle[KEY_CURRENT_INDEX] = indexToSet
            savedStateHandle[KEY_CURRENT_HIT] = imageToShow // Salva l'oggetto Hit intero
        }
    }

    suspend fun next(offset: Int = 1) {
        service.moveNext(offset) { hit -> // [Source 174]
            savedStateHandle[KEY_CURRENT_HIT] = hit // Salva il nuovo Hit
            savedStateHandle[KEY_CURRENT_INDEX] = service.getCurrentIndex() // Aggiorna indice
        }
    }

    suspend fun previous(offset: Int = 1) {
        service.movePrevious(offset) { hit ->
            savedStateHandle[KEY_CURRENT_HIT] = hit // Salva il nuovo Hit
            savedStateHandle[KEY_CURRENT_INDEX] = service.getCurrentIndex() // Aggiorna indice
        }
    }

    suspend fun first() {
        service.moveToFirst { hit -> // [Source 175]
            savedStateHandle[KEY_CURRENT_HIT] = hit // Salva il nuovo Hit
            savedStateHandle[KEY_CURRENT_INDEX] = service.getCurrentIndex() // Aggiorna indice
        }
    }

    fun loadInitial() {
        savedStateHandle[KEY_QUERY] = "" // Resetta query salvata
        service.loadInitialMedia { list ->
            val total = service.getTotal()
            savedStateHandle[KEY_TOTAL_RESULTS] = total // Salva totale
            val first = list?.firstOrNull()
            val initialIndex = service.getCurrentIndex()
            savedStateHandle[KEY_CURRENT_HIT] = first // Salva l'Hit iniziale
            savedStateHandle[KEY_CURRENT_INDEX] = initialIndex // Salva indice iniziale
        }
    }

    // Metodi helper
    fun getTotal(): Int = savedStateHandle.get<Int>(KEY_TOTAL_RESULTS) ?: 0
    fun getCurrentIndex(): Int = savedStateHandle.get<Int>(KEY_CURRENT_INDEX) ?: -1

    // Ora puoi ottenere l'Hit corrente direttamente dal LiveData
    fun getCurrentItem(): Hit? = currentImage.value // [Source 176] - Ottiene da LiveData

}