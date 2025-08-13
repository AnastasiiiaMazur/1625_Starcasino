package com.stcs.oon.db

import androidx.room.Entity
import androidx.room.PrimaryKey

//@Entity(tableName = "rides")
//data class RideEntity(
//    @PrimaryKey(autoGenerate = true) val id: Long = 0,
//    val name: String = "",          // will update to "Route {id}" after insert
//    val polylineJson: String,       // store the route points as JSON
//    val distanceMeters: Int,        // distance when user pressed SAVE (start->current)
//    val durationSeconds: Long,      // moving time
//    val avgSpeedMps: Double,        // v = s / t  (m/s)
//    val difficulty: Int?,           // TBD later
//    val description: String = ""    // empty by default
//)


@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",                 // after insert, set to "Route {id}"
    val startLat: Double,
    val startLon: Double,
    val specLengthMeters: Int,
    val specProfile: String,
    val specSeed: Int,
    val specDir: String,
    val distanceMeters: Int,               // start -> current when user pressed Save
    val durationSeconds: Long,             // moving time (seconds)
    val avgSpeedKmh: Double? = null,    // Will be set later when user stops; km/h (v = s / t)
    val difficulty: Int? = null,
    val description: String? = null,
    val rating: Int? = null,
    val createdAt: Long,

    // Optional stored geometry (keep null if you don't want to persist polyline)
    val polylineJson: String? = null
)
