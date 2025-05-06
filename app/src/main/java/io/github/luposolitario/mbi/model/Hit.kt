package io.github.luposolitario.mbi.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Hit(
    val id: Int,
    val pageURL: String,
    val type: String,
    val tags: String,
    val previewURL: String?, // Reso opzionale perché potrebbe non esistere per i video
    val previewWidth: Int?, // Reso opzionale
    val previewHeight: Int?, // Reso opzionale
    val webformatURL: String?, // Reso opzionale
    val webformatWidth: Int?, // Reso opzionale
    val webformatHeight: Int?, // Reso opzionale
    val largeImageURL: String?, // Reso opzionale
    val imageWidth: Int?, // Reso opzionale
    val imageHeight: Int?, // Reso opzionale
    val imageSize: Int?, // Reso opzionale
    // Campi specifici per i video
    val duration: Int?, // Durata in secondi
    val videos: Videos?, // Oggetto che contiene le diverse risoluzioni del video
    val views: Int,
    val downloads: Int,
    val collections: Int?, // Non presente nel JSON video, reso opzionale
    val likes: Int,
    val comments: Int,
    val user_id: Int,
    val user: String,
    val userImageURL: String
) : Parcelable

// Classe per rappresentare le diverse risoluzioni del video
@Parcelize
data class Videos(
    val large: VideoResolution?,
    val medium: VideoResolution?,
    val small: VideoResolution?,
    val tiny: VideoResolution?
) : Parcelable

// Classe per rappresentare una singola risoluzione del video
@Parcelize
data class VideoResolution(
    val url: String,
    val width: Int,
    val height: Int,
    val size: Int,
    val thumbnail: String
) : Parcelable