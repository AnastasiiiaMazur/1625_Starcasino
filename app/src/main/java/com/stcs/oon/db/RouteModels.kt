package com.stcs.oon.db

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LatLngDto(val lat: Double, val lon: Double): Parcelable

@Parcelize
data class RouteDraft(
    val points: List<LatLngDto>,   // polyline you generated
    val lengthMeters: Int,         // from seekbar (km * 1000)
    val profile: String,           // ORS profile: cycling-regular / road / mountain
    val seed: Int,                  // for RANDOM shape (or 1)
    val start: LatLngDto           // where you started the route
): Parcelable