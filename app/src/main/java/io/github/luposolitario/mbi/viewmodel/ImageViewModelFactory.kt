package io.github.luposolitario.mbi.viewmodel

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.luposolitario.mbi.service.PixbayImageService

// Dentro MainActivity.kt o in un file separato

class ImageViewModelFactory(
    owner: androidx.savedstate.SavedStateRegistryOwner, // 1° arg: L'Activity/Fragment (corrisponde a 'this')
    private val service: PixbayImageService,          // 2° arg: Il servizio PixbayImageService
    defaultArgs: Bundle? = null                       // 3° arg: Argomenti opzionali
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        if (modelClass.isAssignableFrom(ImageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Passa handle e service al costruttore del ViewModel
            return ImageViewModel(
                handle,
                service
            ) as T // Assicurati che il costruttore di ImageViewModel sia (handle, service)
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}