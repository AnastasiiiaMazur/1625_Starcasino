package com.stcs.oon.db

import androidx.room.*

@Dao
interface RideDao {
    @Insert
    suspend fun insert(entity: RideEntity): Long

    @Query("UPDATE rides SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)
}