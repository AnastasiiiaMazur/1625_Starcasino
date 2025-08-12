package com.stcs.oon.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",          // will update to "Route {id}" after insert
    val polylineJson: String,       // store the route points as JSON
    val distanceMeters: Int,        // distance when user pressed SAVE (start->current)
    val durationSeconds: Long,      // moving time
    val avgSpeedMps: Double,        // v = s / t  (m/s)
    val difficulty: Int?,           // TBD later
    val description: String = ""    // empty by default
)