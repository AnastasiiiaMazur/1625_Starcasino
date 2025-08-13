package com.stcs.oon.db

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.stcs.oon.fragments.helpers.OrsRoundTrip
import kotlinx.parcelize.Parcelize


//data class LatLngDto(val lat: Double, val lon: Double)
//
//data class RouteSpec(
//    val start: LatLngDto,     // startCenter
//    val lengthMeters: Int,    // distanceKm * 1000
//    val profile: String,      // "cycling-regular" | "cycling-road" | "cycling-mountain"
//    val seed: Int,            // randomness control (1 if not RANDOM)
//    val dir: String           // "CLOCKWISE" | "COUNTERCLOCKWISE" | "RANDOM"
//)

@Parcelize
data class LatLngDto(val lat: Double, val lon: Double) : Parcelable

@Parcelize
data class RouteSpec(
    val start: LatLngDto,    // startCenter
    val lengthMeters: Int,   // distanceKm * 1000
    val profile: String,     // "cycling-regular" | "cycling-road" | "cycling-mountain"
    val seed: Int,           // 1 if not RANDOM
    val dir: String          // "CLOCKWISE" | "COUNTERCLOCKWISE" | "RANDOM"
) : Parcelable

