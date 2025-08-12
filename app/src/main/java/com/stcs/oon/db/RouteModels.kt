package com.stcs.oon.db


data class LatLngDto(val lat: Double, val lon: Double)

data class RouteDraft(
    val points: List<LatLngDto>,   // simplified list only
    val lengthMeters: Int,
    val profile: String,
    val seed: Int,
    val start: LatLngDto
)