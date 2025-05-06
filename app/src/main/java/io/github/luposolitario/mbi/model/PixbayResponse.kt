package io.github.luposolitario.mbi.model

data class PixbayResponse(
    val total: Int,
    val totalHits: Int,
    val hits: List<Hit>
)
