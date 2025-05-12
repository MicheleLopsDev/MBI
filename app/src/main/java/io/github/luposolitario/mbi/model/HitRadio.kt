package io.github.luposolitario.mbi.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class HitRadio(
    val changeuuid: String,
    val stationuuid: String,
    val serveruuid: String?,
    val name: String,
    val url: String,
    val url_resolved: String,
    val homepage: String,
    val favicon: String,
    val tags: String,
    val country: String,
    val countrycode: String,
    val iso_3166_2: String,
    val state: String,
    val language: String,
    val languagecodes: String,
    val votes: Int,
    val lastchangetime: String,
    val lastchangetime_iso8601: String,
    val codec: String,
    val bitrate: Int,
    val hls: Int,
    val lastcheckok: Int,
    val lastchecktime: String,
    val lastchecktime_iso8601: String,
    val lastcheckoktime: String,
    val lastcheckoktime_iso8601: String,
    val lastlocalchecktime: String,
    val lastlocalchecktime_iso8601: String,
    val clicktimestamp: String,
    val clicktimestamp_iso8601: String,
    val clickcount: Int,
    val clicktrend: Int,
    val ssl_error: Int,
    val geo_lat: Double?,        // può essere null
    val geo_long: Double?,       // può essere null
    val geo_distance: Double?,   // può essere null
    val has_extended_info: Boolean
) : Parcelable
