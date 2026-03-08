package com.example.crowdsensenet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val deviceId: String,
    val timestamp: Long,

    val rsrp: Double,
    val rsrq: Double,
    val pci: Double,

    val cellId: String,
    val networkTechnology: String,

    val latitude: Double,
    val longitude: Double,

    val isUploaded: Boolean = false
)