package io.github.luposolitario.mbi

import com.google.gson.Gson
import com.google.gson.JsonArray
import java.io.File

fun main() {
    // Specifica il nome del file JSON di input
    val inputFileName =
        "C:\\Users\\Michele\\AndroidStudioProjects\\MBI\\app\\src\\main\\assets\\fallback_stations.json"
    // Specifica la directory di output per i file divisi
    val outputDirName = "C:\\TMP\\assets\\"

    // Crea la directory di output se non esiste
    val outputDir = File(outputDirName)
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    try {
        // Leggi il contenuto del file JSON
        val jsonString = File(inputFileName).readText()

        // Usa Gson per parsare il JSON
        val gson = Gson()
        val jsonArray = gson.fromJson(jsonString, JsonArray::class.java)

        // Mappa per raggruppare gli oggetti per countryCode
        val dataByCountryCode = mutableMapOf<String, JsonArray>()

        // Itera sul JsonArray e raggruppa per countryCode
        for (element in jsonArray) {
            if (element.isJsonObject) {
                val jsonObject = element.getAsJsonObject()
                // >>> CAMBIAMENTO QUI <<<
                // Estrae il countryCode, usa "NoCode" come fallback se non presente
                val countrycode = jsonObject.get("countrycode")?.asString ?: "NoCode"

                // Aggiungi l'oggetto JSON all'array corrispondente al countryCode
                dataByCountryCode.computeIfAbsent(countrycode) { JsonArray() }.add(jsonObject)
            }
        }

        // Scrivi i file JSON separati per ogni countryCode
        // >>> CAMBIAMENTO QUI <<<
        dataByCountryCode.forEach { (code, data) ->
            val safeCodeName = code.toUpperCase().replace(
                Regex("[^a-zA-Z0-9.-]"),
                "_"
            ) // Assicura un nome file valido usando il codice
            val outputFileName = "$outputDirName/$safeCodeName.json"
            val outputFile = File(outputFileName)

            // Scrivi il JsonArray nel nuovo file
            outputFile.writeText(gson.toJson(data))
            // >>> CAMBIAMENTO QUI <<<
            println("Creato file per il countryCode: $code -> $outputFileName")
        }

    } catch (e: Exception) {
        println("Errore durante l'elaborazione del file JSON: ${e.message}")
        e.printStackTrace()
    }
}