package io.github.luposolitario.mbi.model

data class PixabayRequest(
    val key: String,
    val query: String,
    val perPage: Int = 200,
    val page: Int = 1,
    val safeSearch: Boolean = false,
    val pretty: Boolean = false
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "key" to key,
            "q" to query,
            "per_page" to perPage.toString(),
            "page" to page.toString(),
            "safesearch" to safeSearch.toString(),
            "pretty" to pretty.toString()
        )
    }
}
