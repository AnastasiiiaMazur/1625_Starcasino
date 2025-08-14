package com.stcs.oon.db

import androidx.room.*

@Dao
interface RideDao {
    @Insert
    suspend fun insert(entity: RideEntity): Long

    @Query("UPDATE rides SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("SELECT * FROM rides ORDER BY id DESC")
    suspend fun getAll(): List<RideEntity>

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: Long)


    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun getById(id: Long): RideEntity?

    @Query("""
        UPDATE rides
        SET name = :name,
            description = :description,
            rating = :rating,
            im1Uri = :im1,
            im2Uri = :im2,
            im3Uri = :im3
        WHERE id = :id
    """)
    suspend fun updateEditableFields(
        id: Long,
        name: String,
        description: String?,
        rating: Int?,
        im1: String?,
        im2: String?,
        im3: String?
    )

    @Query("SELECT * FROM rides ORDER BY createdAt ASC")
    suspend fun getAllasc(): List<RideEntity>

    @Query("SELECT * FROM rides WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt ASC")
    suspend fun getBetween(start: Long, end: Long): List<RideEntity>
}