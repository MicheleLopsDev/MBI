package io.github.luposolitario.mbi.service

import io.github.luposolitario.mbi.model.Hit

interface MediaService<T> {
    //    suspend fun loadInitialMedia(callback: (List<T>?) -> Unit)
//    suspend fun searchMedia(query: String? = null, callback: (List<T>?) -> Unit)
    suspend fun moveNext(offset: Int = 1, onMediaChanged: (T?) -> Unit)
    suspend fun movePrevious(offset: Int = 1, onMediaChanged: (T?) -> Unit)
    suspend fun moveToFirst(onMediaChanged: (T?) -> Unit)
    fun getCurrentItem(): T?
    fun getCurrentIndex(): Int
    fun getTotal(): Int
    suspend fun moveTo(index: Int, onMediaChanged: (T?) -> Unit)
    fun getItemAtIndex(i: Int): Hit?
    fun setCurrentIndex(i: Int)
}
