// File: model/RadioStation.kt
package io.github.luposolitario.mbi.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_station") // Rimosso l'indice precedente, stationuuid è la chiave
data class RadioStation(
    @PrimaryKey val stationuuid: String, // Chiave primaria
    val changeuuid: String?, // Reso nullable se può mancare nel DB
    val serveruuid: String?,
    val name: String?, // Reso nullable per sicurezza
    val url: String?, // Reso nullable per sicurezza
    val url_resolved: String?,
    val homepage: String?,
    val favicon: String?,
    val tags: String?,
    val country: String?,
    val countrycode: String?,
    val iso_3166_2: String?,
    val state: String?,
    val language: String?,
    val languagecodes: String?,
    val votes: Int?,
    val lastchangetime: String?,
    val lastchangetime_iso8601: String?,
    val codec: String?,
    val bitrate: Int?,
    val hls: Int?,
    val lastcheckok: Int?,
    val lastchecktime: String?,
    val lastchecktime_iso8601: String?,
    val lastcheckoktime: String?,
    val lastcheckoktime_iso8601: String?, // Nome corretto dalla tua HitRadio
    val lastlocalchecktime: String?,
    val lastlocalchecktime_iso8601: String?, // Nome corretto dalla tua HitRadio
    val clicktimestamp: String?,
    val clicktimestamp_iso8601: String?, // Nome corretto dalla tua HitRadio
    val clickcount: Int?,
    val clicktrend: Int?,
    val ssl_error: Int?,
    val geo_lat: Double?,
    val geo_long: Double?,
    // geo_distance non ha senso salvarlo nel DB locale, dipende dalla posizione della ricerca
    val has_extended_info: Boolean?
) {
    constructor(hitRadio: HitRadio?) : this(
        stationuuid = hitRadio?.stationuuid ?: "", // Aggiunto controllo null e default
        changeuuid = hitRadio?.changeuuid,
        serveruuid = hitRadio?.serveruuid,
        name = hitRadio?.name,
        url = hitRadio?.url,
        url_resolved = hitRadio?.url_resolved,
        homepage = hitRadio?.homepage,
        favicon = hitRadio?.favicon,
        tags = hitRadio?.tags,
        country = hitRadio?.country,
        countrycode = hitRadio?.countrycode,
        iso_3166_2 = hitRadio?.iso_3166_2,
        state = hitRadio?.state,
        language = hitRadio?.language,
        languagecodes = hitRadio?.languagecodes,
        votes = hitRadio?.votes,
        lastchangetime = hitRadio?.lastchangetime,
        lastchangetime_iso8601 = hitRadio?.lastchangetime_iso8601,
        codec = hitRadio?.codec,
        bitrate = hitRadio?.bitrate,
        hls = hitRadio?.hls,
        lastcheckok = hitRadio?.lastcheckok,
        lastchecktime = hitRadio?.lastchecktime,
        lastchecktime_iso8601 = hitRadio?.lastchecktime_iso8601,
        lastcheckoktime = hitRadio?.lastcheckoktime,
        lastcheckoktime_iso8601 = hitRadio?.lastcheckoktime_iso8601,
        lastlocalchecktime = hitRadio?.lastlocalchecktime,
        lastlocalchecktime_iso8601 = hitRadio?.lastlocalchecktime_iso8601,
        clicktimestamp = hitRadio?.clicktimestamp,
        clicktimestamp_iso8601 = hitRadio?.clicktimestamp_iso8601,
        clickcount = hitRadio?.clickcount,
        clicktrend = hitRadio?.clicktrend,
        ssl_error = hitRadio?.ssl_error,
        geo_lat = hitRadio?.geo_lat,
        geo_long = hitRadio?.geo_long,
        has_extended_info = hitRadio?.has_extended_info
    )
}

// Funzione di estensione opzionale per mappare da RadioStation a HitRadio
// Utile per la funzione di fallback
fun RadioStation.toHitRadio(): HitRadio {
    return HitRadio(
        changeuuid = this.changeuuid ?: "",
        stationuuid = this.stationuuid, // Non può essere null qui
        serveruuid = this.serveruuid,
        name = this.name ?: "N/A",
        url = this.url ?: "",
        url_resolved = this.url_resolved ?: "",
        homepage = this.homepage ?: "",
        favicon = this.favicon ?: "",
        tags = this.tags ?: "",
        country = this.country ?: "",
        countrycode = this.countrycode ?: "",
        iso_3166_2 = this.iso_3166_2 ?: "",
        state = this.state ?: "",
        language = this.language ?: "",
        languagecodes = this.languagecodes ?: "",
        votes = this.votes ?: 0,
        lastchangetime = this.lastchangetime ?: "",
        lastchangetime_iso8601 = this.lastchangetime_iso8601 ?: "",
        codec = this.codec ?: "",
        bitrate = this.bitrate ?: 0,
        hls = this.hls ?: 0,
        lastcheckok = this.lastcheckok ?: 0,
        lastchecktime = this.lastchecktime ?: "",
        lastchecktime_iso8601 = this.lastchecktime_iso8601 ?: "",
        lastcheckoktime = this.lastcheckoktime ?: "",
        lastcheckoktime_iso8601 = this.lastcheckoktime_iso8601 ?: "",
        lastlocalchecktime = this.lastlocalchecktime ?: "",
        lastlocalchecktime_iso8601 = this.lastlocalchecktime_iso8601 ?: "",
        clicktimestamp = this.clicktimestamp ?: "",
        clicktimestamp_iso8601 = this.clicktimestamp_iso8601 ?: "",
        clickcount = this.clickcount ?: 0,
        clicktrend = this.clicktrend ?: 0,
        ssl_error = this.ssl_error ?: 0,
        geo_lat = this.geo_lat,
        geo_long = this.geo_long,
        geo_distance = null, // Non salvato nel DB
        has_extended_info = this.has_extended_info ?: false
    )
}