package io.github.luposolitario.mbi.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class HitRadio(
    val changeuuid: String?, // Reso nullable
    val stationuuid: String, // Questo probabilmente non è null (ID univoco)
    val serveruuid: String?,
    val name: String, // Reso nullable
    val url: String?, // Reso nullable
    val url_resolved: String?, // Reso nullable
    val homepage: String?, // Reso nullable
    val favicon: String?, // Reso nullable
    val tags: String?, // Reso nullable
    val country: String?, // Reso nullable
    val countrycode: String?, // Reso nullable
    val iso_3166_2: String?, // Reso nullable
    val state: String?, // Reso nullable
    val language: String?, // Reso nullable
    val languagecodes: String?, // Reso nullable
    val votes: Int?, // Reso nullable
    val lastchangetime: String?, // Reso nullable
    val lastchangetime_iso8601: String?, // Reso nullable
    val codec: String?, // Reso nullable
    val bitrate: Int?, // Reso nullable
    val hls: Int?, // Reso nullable
    val lastcheckok: Int?, // Reso nullable
    val lastchecktime: String?, // Reso nullable
    val lastchecktime_iso8601: String?, // Reso nullable
    val lastcheckoktime: String?, // Reso nullable
    val lastcheckoktime_iso8601: String?, // Reso nullable
    val lastlocalchecktime: String?, // Reso nullable
    val lastlocalchecktime_iso8601: String?, // Reso nullable
    val clicktimestamp: String?, // Reso nullable per risolvere l'errore
    val clicktimestamp_iso8601: String?, // Reso nullable per risolvere l'errore
    val clickcount: Int?, // Reso nullable
    val clicktrend: Int?, // Reso nullable
    val ssl_error: Int?, // Reso nullable
    val geo_lat: Double?,
    val geo_long: Double?,
    val geo_distance: Double?, // Reso nullable
    val has_extended_info: Boolean?, // Reso nullable

) : Parcelable {
    constructor(
        uuid: String,
        name: String,
        favicon: String?,
        url: String?,
    ) : this(
        changeuuid = null,
        stationuuid = uuid,
        serveruuid = null,
        name = name,
        url = url,
        url_resolved = null,
        homepage = null,
        favicon = favicon,
        tags = null,
        country = null,
        countrycode = null,
        iso_3166_2 = null,
        state = null,
        language = null,
        languagecodes = null,
        votes = null, // Inizializza gli altri campi con null o valori predefiniti
        lastchangetime = null, lastchangetime_iso8601 = null, codec = null, bitrate = null, hls = null, lastcheckok = null, lastchecktime = null, lastchecktime_iso8601 = null, lastcheckoktime = null, lastcheckoktime_iso8601 = null, lastlocalchecktime = null, lastlocalchecktime_iso8601 = null, clicktimestamp = null, clicktimestamp_iso8601 = null, clickcount = null, clicktrend = null, ssl_error = null, geo_lat = null, geo_long = null, geo_distance = null, has_extended_info = null
    )}