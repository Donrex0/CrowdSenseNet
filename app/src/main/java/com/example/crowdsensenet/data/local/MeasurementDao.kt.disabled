package com.example.crowdsensenet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: MeasurementEntity)

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMeasurement(): MeasurementEntity?

    @Query("SELECT * FROM measurements WHERE isUploaded = 0")
    suspend fun getPendingMeasurements(): List<MeasurementEntity>

    @Query("UPDATE measurements SET isUploaded = 1 WHERE id = :id")
    suspend fun markAsUploaded(id: Int)

    @Query("SELECT COUNT(*) FROM measurements WHERE isUploaded = 0")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM measurements WHERE isUploaded = 1")
    suspend fun getUploadedCount(): Int
}